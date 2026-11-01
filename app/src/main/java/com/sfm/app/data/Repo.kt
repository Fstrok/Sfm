package com.sfm.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class Page(val items: List<Video>, val hasMore: Boolean)

/** All site access. Every loaded video goes through [remember] so fields from different sources merge. */
class Repo(private val net: Net, val catalog: Catalog) {
    private val known = ConcurrentHashMap<Long, Video>()
    @Volatile private var nonce: String? = null
    private val fields = "id,date_gmt,link,title,content,categories,tags,featured_media"
    private val api = "${SITE}wp-json/wp/v2/"

    fun cached(id: Long): Video? = known[id]

    fun remember(v: Video): Video {
        val merged = known[v.id]?.merge(v) ?: v
        val enriched = catalog.enrich(merged)
        known[v.id] = enriched
        return enriched
    }

    suspend fun feed(src: FeedSource, page: Int): Page {
        val dur = src.duration.tagIds.joinToString(",")
        return when (src.kind) {
            FeedKind.LATEST -> rest("orderby=date" + (if (dur.isNotEmpty()) "&tags=$dur" else ""), page)
            FeedKind.LONG -> rest("orderby=date&tags=" + (src.duration.takeIf { it.minSec >= 60 } ?: Duration.M1).tagIds.joinToString(","), page)
            FeedKind.TRENDING -> html(SITE, "?filter-by=trending", page)
            FeedKind.HOT -> html(SITE, "?filter-by=hot", page)
            FeedKind.TOP -> html(SITE, "?filter-by=popular", page)
            FeedKind.TERM_NEW -> {
                val t = src.term!!
                if (t.isCategory) rest("orderby=date&categories=${t.id}" + (if (dur.isNotEmpty()) "&tags=$dur" else ""), page)
                else rest("orderby=date&tags=${t.id}", page)
            }
            FeedKind.TERM_TOP -> html(src.term!!.link.ifBlank { SITE }, "?order=most_views", page)
            FeedKind.SEARCH -> rest("search=" + URLEncoder.encode(src.query.orEmpty(), "UTF-8") + (if (dur.isNotEmpty()) "&tags=$dur" else ""), page)
            FeedKind.SUBS -> {
                val cats = src.terms.filter { it.isCategory }.joinToString(",") { it.id.toString() }
                val tags = src.terms.filter { !it.isCategory }.joinToString(",") { it.id.toString() }
                if (cats.isEmpty() && tags.isEmpty()) Page(emptyList(), false)
                else rest(buildString {
                    append("orderby=date")
                    if (cats.isNotEmpty()) append("&categories=$cats")
                    if (tags.isNotEmpty()) append("&tags=$tags")
                    if (cats.isNotEmpty() && tags.isNotEmpty()) append("&tax_relation=OR")
                }, page)
            }
        }.let { p -> if (src.duration != Duration.ANY && src.kind in setOf(FeedKind.TRENDING, FeedKind.HOT, FeedKind.TOP, FeedKind.TERM_TOP, FeedKind.SUBS) || (src.kind == FeedKind.TERM_NEW && src.term?.isCategory == false && src.duration != Duration.ANY))
            p.copy(items = p.items.filter { v -> (v.tagDurationSec ?: 0) >= src.duration.minSec || (v.durationMs ?: 0) >= src.duration.minSec * 1000L })
            else p
        }
    }

    private suspend fun rest(query: String, page: Int, perPage: Int = 30): Page {
        if (catalog.data.value.categories.isEmpty()) runCatching { catalog.ensureCategories() }
        val r = try {
            net.get("${api}posts?$query&per_page=$perPage&page=$page&_fields=$fields", json = true)
        } catch (e: SiteException) {
            if (e.code == 400) return Page(emptyList(), false) else throw e
        }
        if (r.code == 400) return Page(emptyList(), false)
        if (r.code !in 200..299) throw SiteException("сайт ответил ${r.code}", r.code)
        val posts = Parser.restPosts(r.body)
        val thumbs = mediaThumbs(posts.filter { known[it.video.id]?.thumb == null && it.mediaId > 0 }.map { it.mediaId })
        val items = posts.map { p -> remember(p.video.copy(thumb = thumbs[p.mediaId])) }
        val total = r.headers["X-WP-TotalPages"]?.toIntOrNull() ?: page
        return Page(items, page < total && items.isNotEmpty())
    }

    private suspend fun mediaThumbs(ids: List<Long>): Map<Long, String> = coroutineScope {
        ids.distinct().chunked(100).map { chunk ->
            async { runCatching { Parser.media(net.get("${api}media?include=${chunk.joinToString(",")}&per_page=100&_fields=id,source_url,media_details", json = true).body) }.getOrDefault(emptyMap()) }
        }.awaitAll().fold(emptyMap()) { a, b -> a + b }
    }

    /** HTML listing: two site pages (8 items each) per app page; the query (?filter-by=…) is kept on every page. */
    private suspend fun html(base: String, query: String, page: Int): Page = coroutineScope {
        val b = if (base.endsWith("/")) base else "$base/"
        val pages = listOf(page * 2 - 1, page * 2).map { p ->
            async {
                val url = if (p == 1) "$b$query" else "${b}page/$p/$query"
                val r = net.get(url)
                if (r.code == 404) ListingPage(emptyList(), false, null)
                else if (r.code !in 200..299) throw SiteException("сайт ответил ${r.code}", r.code)
                else Parser.listing(r.body)
            }
        }.awaitAll()
        pages.firstNotNullOfOrNull { it.nonce }?.let { nonce = it }
        val items = pages.flatMap { it.items }.distinctBy { it.id }.map { remember(it) }
        Page(resolve(items), pages.last().hasNext && items.isNotEmpty())
    }

    /** Fills missing video urls / dates via one batched REST request. */
    suspend fun resolve(items: List<Video>): List<Video> {
        val need = items.filter { it.videoUrl == null || it.date == 0L }.map { it.id }
        if (need.isEmpty()) return items
        runCatching {
            need.chunked(100).forEach { chunk ->
                val r = net.get("${api}posts?include=${chunk.joinToString(",")}&per_page=100&_fields=$fields", json = true)
                Parser.restPosts(r.body).forEach { remember(it.video) }
            }
        }
        return items.map { known[it.id] ?: it }
    }

    suspend fun post(id: Long): Video? {
        known[id]?.takeIf { it.videoUrl != null && it.artist != null }?.let { return it }
        val r = net.get("${api}posts/$id?_fields=$fields", json = true)
        if (r.code !in 200..299) return known[id]
        val p = Parser.restPosts("[${r.body}]").firstOrNull() ?: return null
        val thumb = if (known[id]?.thumb == null && p.mediaId > 0) mediaThumbs(listOf(p.mediaId))[p.mediaId] else null
        return remember(p.video.copy(thumb = thumb))
    }

    /** A site URL from a deep link or share: post page → video. */
    suspend fun byUrl(url: String): Video? {
        val slug = url.substringAfter("sfmcompile.club/").trim('/').substringBefore('?')
        if (slug.isBlank() || slug.contains('/')) return null
        val r = net.get("${api}posts?slug=${URLEncoder.encode(slug, "UTF-8")}&_fields=$fields", json = true)
        val p = runCatching { Parser.restPosts(r.body).firstOrNull() }.getOrNull() ?: return null
        val thumb = if (p.mediaId > 0) mediaThumbs(listOf(p.mediaId))[p.mediaId] else null
        return remember(p.video.copy(thumb = thumb))
    }

    suspend fun random(): Video? {
        val r = net.get("$SITE?redirect_to=random", follow = false)
        val loc = r.headers["Location"] ?: return null
        val page = net.get(loc)
        return Parser.postPage(page.body, loc)?.let { remember(it) }?.let { resolve(listOf(it)).first() }
    }

    /** Guest up/down vote (Snax). Nonce comes from any listing page. */
    suspend fun vote(v: Video, up: Boolean): Boolean {
        val n = nonce ?: runCatching { Parser.nonce(net.get(v.link.ifBlank { SITE }).body) }.getOrNull()?.also { nonce = it } ?: return false
        val r = net.post("${SITE}wp-admin/admin-ajax.php", mapOf(
            "action" to "snax_vote_item", "security" to n, "snax_item_id" to v.id.toString(),
            "snax_author_id" to "0", "snax_vote_type" to if (up) "upvote" else "downvote", "snax_user_voted" to "",
        ), v.link.ifBlank { SITE })
        val ok = r.code in 200..299 && Parser.voteOk(r.body)
        if (ok) known[v.id]?.let { known[v.id] = it.copy(score = (it.score ?: 0) + if (up) 1 else -1) }
        return ok
    }

    /** Tags of a video as terms (from catalog, or fetched by ids). */
    suspend fun tagsOf(v: Video): List<Term> {
        val d = catalog.data.value
        val local = v.tagIds.mapNotNull { d.byTag[it] } + v.tags.mapNotNull { d.bySlug[it] }
        if (local.isNotEmpty()) return local.distinctBy { it.id }.filter { it.id !in Duration.TAG_IDS }
        if (v.tagIds.isEmpty()) return emptyList()
        return runCatching {
            Parser.terms(net.get("${api}tags?include=${v.tagIds.joinToString(",")}&per_page=100&_fields=id,name,slug,count,link,parent", json = true).body) { TermType.TAG }
        }.getOrDefault(emptyList()).filter { it.id !in Duration.TAG_IDS }
    }

    suspend fun fileSize(url: String): Long? = net.size(url)
}
