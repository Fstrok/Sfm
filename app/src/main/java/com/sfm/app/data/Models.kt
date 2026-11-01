package com.sfm.app.data

import kotlinx.serialization.Serializable

const val SITE = "https://sfmcompile.club/"

@Serializable
data class TermRef(val id: Int, val name: String, val link: String = "")

@Serializable
data class Video(
    val id: Long,
    val title: String = "",
    val link: String = "",
    val videoUrl: String? = null,
    val thumb: String? = null,
    /** Publish time, epoch millis (GMT). 0 = unknown. */
    val date: Long = 0L,
    val cats: List<TermRef> = emptyList(),
    val catIds: List<Int> = emptyList(),
    val tagIds: List<Int> = emptyList(),
    /** Tag slugs (from listing markup or resolved via catalog). */
    val tags: List<String> = emptyList(),
    val views: Long? = null,
    val score: Int? = null,
    val artist: String? = null,
    val description: String? = null,
    val sourceUrl: String? = null,
    /** Real duration verified by the player, ms. */
    val durationMs: Long? = null,
) {
    /** Lower duration bound from site duration tags, seconds. */
    val tagDurationSec: Int?
        get() = Duration.TAGS.filter { (slug, id, _) -> slug in tags || id in tagIds }.maxOfOrNull { it.third }

    val durationLabel: String?
        get() = durationMs?.let { formatDuration(it) } ?: tagDurationSec?.let { s -> if (s >= 60) "${s / 60} мин+" else "$s сек+" }

    val allCatIds: List<Int> get() = (catIds + cats.map { it.id }).distinct()

    /** Merges fresh data into existing: never drops known values, keeps player-verified duration. */
    fun merge(o: Video): Video = copy(
        title = o.title.ifBlank { title },
        link = o.link.ifBlank { link },
        videoUrl = o.videoUrl ?: videoUrl,
        thumb = o.thumb ?: thumb,
        date = if (o.date > 0) o.date else date,
        cats = o.cats.ifEmpty { cats },
        catIds = o.catIds.ifEmpty { catIds },
        tagIds = o.tagIds.ifEmpty { tagIds },
        tags = o.tags.ifEmpty { tags },
        views = o.views ?: views,
        score = o.score ?: score,
        artist = o.artist ?: artist,
        description = o.description ?: description,
        sourceUrl = o.sourceUrl ?: sourceUrl,
        durationMs = durationMs ?: o.durationMs,
    )
}

enum class TermType(val title: String) { GAME("Игра"), CHARACTER("Персонаж"), ARTIST("Автор"), TAG("Тег") }

@Serializable
data class Term(
    val id: Int,
    val name: String,
    val slug: String = "",
    val count: Int = 0,
    val link: String = "",
    val parent: Int = 0,
    val type: TermType = TermType.TAG,
) {
    val isCategory get() = type == TermType.GAME || type == TermType.CHARACTER
    val ref get() = TermRef(id, name, link)
}

/** Duration filter based on the site's duration tags ("20secs+", "1min+" …). */
enum class Duration(val title: String, val minSec: Int) {
    ANY("Любая", 0), S20("20 сек+", 20), S30("30 сек+", 30), M1("1 мин+", 60), M2("2 мин+", 120), M3("3 мин+", 180);

    /** Tag ids whose bound is at or above this filter (posts tagged with any of them qualify). */
    val tagIds: List<Int> get() = if (this == ANY) emptyList() else TAGS.filter { it.third >= minSec }.map { it.second }
    val tagSlugs: Set<String> get() = TAGS.filter { it.third >= minSec }.map { it.first }.toSet()

    companion object {
        /** slug, id, seconds — verified against /wp-json/wp/v2/tags. */
        val TAGS = listOf(
            Triple("20secs", 1013, 20), Triple("30secs", 365, 30), Triple("1min", 359, 60),
            Triple("2mins", 372, 120), Triple("3mins", 376, 180), Triple("4mins", 375, 240),
            Triple("5mins", 360, 300), Triple("9mins", 361, 540),
        )
        val TAG_IDS = TAGS.map { it.second }.toSet()
        val TAG_SLUGS = TAGS.map { it.first }.toSet()
    }
}

enum class SortMode(val title: String) { SITE("Как на сайте"), VIEWS("Просмотры"), SCORE("Рейтинг"), NEWEST("Свежие"), LONGEST("Длинные") }

enum class FeedKind { LATEST, TRENDING, HOT, TOP, LONG, TERM_NEW, TERM_TOP, SEARCH, SUBS }

data class FeedSource(
    val kind: FeedKind,
    val term: Term? = null,
    val query: String? = null,
    val terms: List<Term> = emptyList(),
    val duration: Duration = Duration.ANY,
) {
    val key: String
        get() = listOf(kind.name, term?.id, query, terms.joinToString(",") { "${it.type}:${it.id}" }, duration.name).joinToString("|")
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun formatCount(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1f млрд".format(n / 1e9)
    n >= 1_000_000 -> "%.1f млн".format(n / 1e6)
    n >= 1_000 -> "%.1f тыс.".format(n / 1e3)
    else -> n.toString()
}.replace(".0 ", " ")

fun timeAgo(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    if (epochMs <= 0) return ""
    val m = (now - epochMs) / 60_000
    return when {
        m < 1 -> "только что"
        m < 60 -> "$m мин назад"
        m < 60 * 24 -> "${m / 60} ч назад"
        m < 60 * 48 -> "вчера"
        m < 60 * 24 * 7 -> "${m / 1440} дн. назад"
        m < 60 * 24 * 30 -> "${m / 10080} нед. назад"
        m < 60 * 24 * 365 -> "${m / 43200} мес. назад"
        else -> "${m / 525600} г. назад"
    }
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f ГБ".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f МБ".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "${b shr 10} КБ"
    else -> "$b Б"
}
