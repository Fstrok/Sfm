package com.sfm.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class ListingPage(val items: List<Video>, val hasNext: Boolean, val nonce: String?)

/** A REST post plus its featured media id (thumbnail is resolved by a second batched request). */
data class RestPost(val video: Video, val mediaId: Long)

object Parser {
    private val json = Json { ignoreUnknownKeys = true }
    private val opt = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private val reId = Regex("""post-(\d+)""")
    private val reClass = Regex("""^[^>]*?class="([^"]*)"""")
    private val reTitle = Regex("""entry-title[^>]*>\s*<a href="([^"]+)"[^>]*>(.*?)</a>""", opt)
    private val reH1 = Regex("""<h1[^>]*entry-title[^>]*>(.*?)</h1>""", opt)
    private val reCat = Regex("""<a href="([^"]+)" class="entry-category entry-category-item-(\d+)"[^>]*>(.*?)</a>""", opt)
    private val reViews = Regex("""entry-views[^"]*"\s*>\s*<strong>([^<]+)</strong>""")
    private val reScore = Regex("""data-snax-voting-score="(-?\d+)"""")
    private val reNonce = Regex("""snax-vote-up[^>]*?data-snax-nonce="(\w+)"""")
    private val reVideo = Regex("""(?:src|href)=["'](https?://[^"']+?\.(?:mp4|webm|m3u8)(?:\?[^"']*)?)["']""", RegexOption.IGNORE_CASE)
    private val rePoster = Regex("""(?:data-poster-image|poster)=["'](https?://[^"']+)["']""")
    private val reImg = Regex("""<img[^>]+?(?:data-src|src)=["'](https?://[^"']+\.(?:jpe?g|png|webp))["']""", RegexOption.IGNORE_CASE)
    private val reOg = Regex("""<meta property="og:image" content="([^"]+)"""")
    private val reDateTime = Regex("""datetime="([^"]+)"""")
    private val reIdSuffix = Regex("""\?id=\d+$""")
    private val reTags = Regex("""<[^>]+>""")
    private val reEntity = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")
    private val reArtist = Regex("""Artist:\s*(?:<a[^>]*>)?\s*([^<\r\n]+)""", RegexOption.IGNORE_CASE)
    private val reSource = Regex("""Source:.*?href="([^"]+)"""", opt)
    private val reDesc = Regex("""class="wpsm-content">(.*?)<p class="wpsm-hide"""", opt)
    private val entities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "hellip" to "…",
        "ndash" to "–", "mdash" to "—", "laquo" to "«", "raquo" to "»", "rsquo" to "’", "lsquo" to "‘",
        "rdquo" to "”", "ldquo" to "“", "times" to "×",
    )

    fun text(html: String): String = decode(reTags.replace(html, "")).replace(Regex("\\s+"), " ").trim()

    fun decode(s: String): String = reEntity.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> entities[e]
        } ?: m.value
    }

    /** "1.8k" → 1800, "6M" → 6_000_000. */
    fun parseCount(s: String): Long? {
        val t = s.trim().replace(",", "").lowercase()
        val mul = when { t.endsWith("k") -> 1e3; t.endsWith("m") -> 1e6; t.endsWith("b") -> 1e9; else -> 1.0 }
        return t.trimEnd('k', 'm', 'b').toDoubleOrNull()?.let { (it * mul).toLong() }
    }

    fun parseDate(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrDefault(0)
    }

    fun videoUrl(html: String): String? = reVideo.findAll(html).map { it.groupValues[1].replace("&amp;", "&") }
        .firstOrNull { !it.contains("preview", true) && !it.contains("thumb", true) }
        ?.let { reIdSuffix.replace(it, "") }

    /** Fields hidden in post content: video file, artist, original source, description. */
    fun content(html: String, base: Video): Video {
        val artist = reArtist.find(html)?.groupValues?.get(1)?.let { text(it) }?.takeIf { it.isNotBlank() }
        return base.copy(
            videoUrl = videoUrl(html) ?: base.videoUrl,
            artist = artist ?: base.artist,
            sourceUrl = reSource.find(html)?.groupValues?.get(1) ?: base.sourceUrl,
            description = reDesc.find(html)?.groupValues?.get(1)?.let { text(it) }?.takeIf { it.isNotBlank() } ?: base.description,
        )
    }

    /** Listing pages (home, ?filter-by=, /category/…, /tag/…): only main-stream articles, not sidebar widgets. */
    fun listing(html: String): ListingPage {
        val items = html.split("<article ").drop(1).mapNotNull { raw ->
            val cls = reClass.find(raw)?.groupValues?.get(1) ?: return@mapNotNull null
            if (!cls.contains("entry-tpl-stream") && !cls.contains("entry-tpl-classic")) return@mapNotNull null
            article(raw.substringBefore("</article>"), cls)
        }
        return ListingPage(items, html.contains("<link rel=\"next\""), reNonce.find(html)?.groupValues?.get(1))
    }

    private fun article(a: String, cls: String): Video? {
        val id = reId.find(cls)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val t = reTitle.find(a)
        val cats = reCat.findAll(a).map { TermRef(it.groupValues[2].toInt(), text(it.groupValues[3]), it.groupValues[1]) }.toList()
        val tags = cls.split(' ').filter { it.startsWith("tag-") }.map { it.removePrefix("tag-") }
        return Video(
            id = id,
            title = t?.groupValues?.get(2)?.let { text(it) } ?: reH1.find(a)?.groupValues?.get(1)?.let { text(it) } ?: "",
            link = t?.groupValues?.get(1) ?: "",
            videoUrl = videoUrl(a),
            thumb = rePoster.find(a)?.groupValues?.get(1) ?: reImg.find(a)?.groupValues?.get(1),
            cats = cats,
            catIds = cats.map { it.id },
            tags = tags,
            views = reViews.find(a)?.groupValues?.get(1)?.let { parseCount(it) },
            score = reScore.find(a)?.groupValues?.get(1)?.toIntOrNull(),
        )
    }

    /** Single post page (deep links, random). */
    fun postPage(html: String, url: String): Video? {
        val main = html.split("<article ").drop(1).firstOrNull { reClass.find(it)?.groupValues?.get(1)?.contains("entry-tpl-classic") == true }
            ?: return null
        val cls = reClass.find(main)!!.groupValues[1]
        val a = main.substringBefore("</article>")
        val v = article(a, cls) ?: return null
        return content(a, v).copy(
            link = v.link.ifBlank { url },
            thumb = v.thumb ?: reOg.find(html)?.groupValues?.get(1),
            date = parseDate(reDateTime.find(a)?.groupValues?.get(1)),
        )
    }

    fun nonce(html: String): String? = reNonce.find(html)?.groupValues?.get(1)

    private fun JsonElement?.str(): String? = (this as? JsonElement)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
    private fun JsonObject.rendered(key: String): String = (this[key] as? JsonObject)?.get("rendered").str().orEmpty()
    private fun JsonObject.ints(key: String): List<Int> = (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()

    fun restPosts(body: String): List<RestPost> = json.parseToJsonElement(body).jsonArray.map { e ->
        val o = e.jsonObject
        val base = Video(
            id = o["id"]!!.jsonPrimitive.longOrNull!!,
            title = text(o.rendered("title")),
            link = o["link"].str().orEmpty(),
            date = parseDate(o["date_gmt"].str()?.let { if (it.endsWith("Z")) it else it + "Z" }),
            catIds = o.ints("categories"),
            tagIds = o.ints("tags"),
        )
        RestPost(content(o.rendered("content"), base), o["featured_media"]?.jsonPrimitive?.longOrNull ?: 0)
    }

    /** media id → best thumbnail url (≈768px wide preferred). */
    fun media(body: String): Map<Long, String> = json.parseToJsonElement(body).jsonArray.mapNotNull { e ->
        val o = e.jsonObject
        val id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        val sizes = (o["media_details"] as? JsonObject)?.get("sizes") as? JsonObject
        val url = listOf("medium_large", "bimber-stream", "bimber-grid-standard", "medium")
            .firstNotNullOfOrNull { (sizes?.get(it) as? JsonObject)?.get("source_url").str() }
            ?: o["source_url"].str()
        url?.let { id to it }
    }.toMap()

    fun terms(body: String, type: (JsonObject) -> TermType): List<Term> = json.parseToJsonElement(body).jsonArray.map { e ->
        val o = e.jsonObject
        Term(
            id = o["id"]!!.jsonPrimitive.intOrNull!!,
            name = decode(o["name"].str().orEmpty()),
            slug = o["slug"].str().orEmpty(),
            count = o["count"]?.jsonPrimitive?.intOrNull ?: 0,
            link = o["link"].str().orEmpty().let { if (it.endsWith("/")) it else "$it/" },
            parent = o["parent"]?.jsonPrimitive?.intOrNull ?: 0,
            type = type(o),
        )
    }

    /** Ids of the "Artist" tag group from /wp-json/tag-groups/v1/groups. */
    fun artistIds(body: String): Set<Int> = runCatching {
        json.parseToJsonElement(body).jsonArray.map { it.jsonObject }
            .filter { it["label"].str().equals("Artist", true) }
            .flatMap { g ->
                when (val t = g["terms"]) {
                    is JsonArray -> t.mapNotNull { it.jsonPrimitive.intOrNull }
                    else -> t.str().orEmpty().trim('[', ']').split(',').mapNotNull { it.trim().toIntOrNull() }
                }
            }.toSet()
    }.getOrDefault(emptySet())

    fun voteOk(body: String): Boolean = runCatching {
        json.parseToJsonElement(body).jsonObject["status"].str() != "error"
    }.getOrDefault(false)
}
