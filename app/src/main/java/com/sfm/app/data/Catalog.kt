package com.sfm.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CatalogData(
    val categories: List<Term> = emptyList(),
    val tags: List<Term> = emptyList(),
    val categoriesAt: Long = 0,
    val tagsAt: Long = 0,
) {
    @kotlinx.serialization.Transient
    val byCat: Map<Int, Term> = categories.associateBy { it.id }
    @kotlinx.serialization.Transient
    val byTag: Map<Int, Term> = tags.associateBy { it.id }
    @kotlinx.serialization.Transient
    val bySlug: Map<String, Term> = tags.associateBy { it.slug }

    val games get() = categories.filter { it.type == TermType.GAME }
    val characters get() = categories.filter { it.type == TermType.CHARACTER }
    val artists get() = tags.filter { it.type == TermType.ARTIST }
    val plainTags get() = tags.filter { it.type == TermType.TAG && it.id !in Duration.TAG_IDS }
    fun charactersOf(gameId: Int) = categories.filter { it.parent == gameId }
}

/** Site taxonomy (games, characters, artists, tags) cached on disk; loaded only on demand. */
class Catalog(private val file: File?, private val net: Net) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _data = MutableStateFlow(CatalogData())
    val data: StateFlow<CatalogData> = _data
    private val mutex = Mutex()
    private val maxAge = 3L * 24 * 3600_000

    suspend fun loadCache() = withContext(Dispatchers.IO) {
        if (file?.exists() == true) runCatching { _data.value = json.decodeFromString<CatalogData>(file.readText()) }
    }

    /** Categories only (≈4 requests): names for REST-sourced cards. */
    suspend fun ensureCategories(force: Boolean = false) = mutex.withLock {
        val d = _data.value
        if (!force && d.categories.isNotEmpty() && System.currentTimeMillis() - d.categoriesAt < maxAge) return@withLock
        val cats = fetchAll("categories") { o -> if ((o["parent"]?.toString()?.toIntOrNull() ?: 0) == 0) TermType.GAME else TermType.CHARACTER }
        update(_data.value.copy(categories = cats, categoriesAt = System.currentTimeMillis()))
    }

    /** Full catalog including ~1200 tags and the artist group. */
    suspend fun ensureAll(force: Boolean = false) {
        ensureCategories(force)
        mutex.withLock {
            val d = _data.value
            if (!force && d.tags.isNotEmpty() && System.currentTimeMillis() - d.tagsAt < maxAge) return@withLock
            val artists = runCatching { Parser.artistIds(net.get("${SITE}wp-json/tag-groups/v1/groups", json = true).body) }.getOrDefault(emptySet())
            val tags = fetchAll("tags") { o -> if ((o["id"]?.toString()?.toIntOrNull() ?: 0) in artists) TermType.ARTIST else TermType.TAG }
            update(_data.value.copy(tags = tags, tagsAt = System.currentTimeMillis()))
        }
    }

    private suspend fun fetchAll(tax: String, type: (kotlinx.serialization.json.JsonObject) -> TermType): List<Term> = coroutineScope {
        fun url(p: Int) = "${SITE}wp-json/wp/v2/$tax?per_page=100&page=$p&hide_empty=true&orderby=count&order=desc&_fields=id,name,slug,count,link,parent"
        val first = net.get(url(1), json = true)
        val pages = first.headers["X-WP-TotalPages"]?.toIntOrNull() ?: 1
        val rest = (2..pages).map { p -> async { Parser.terms(net.get(url(p), json = true).body, type) } }.awaitAll()
        (Parser.terms(first.body, type) + rest.flatten()).distinctBy { it.id }
    }

    private suspend fun update(d: CatalogData) {
        _data.value = d
        withContext(Dispatchers.IO) { runCatching { file?.writeText(json.encodeToString(CatalogData.serializer(), d)) } }
    }

    /** Fills category names and tag slugs from cached taxonomy. */
    fun enrich(v: Video): Video {
        val d = _data.value
        val cats = if (v.cats.isEmpty() && v.catIds.isNotEmpty()) v.catIds.mapNotNull { d.byCat[it]?.ref } else v.cats
        val tags = if (v.tags.isEmpty() && v.tagIds.isNotEmpty()) v.tagIds.mapNotNull { d.byTag[it]?.slug } else v.tags
        return if (cats === v.cats && tags === v.tags) v else v.copy(cats = cats, tags = tags)
    }

    fun term(ref: TermRef): Term? = _data.value.byCat[ref.id] ?: _data.value.byTag[ref.id]

    /** Fuzzy term search: all significant words must match a word start or be within 1 typo for long words. */
    fun search(q: String, limit: Int = 30): List<Term> {
        val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val d = _data.value
        return (d.categories + d.tags.filter { it.id !in Duration.TAG_IDS })
            .filter { t -> val n = t.name.lowercase(); words.all { w -> matches(n, w) } }
            .sortedWith(compareByDescending<Term> { it.name.lowercase().startsWith(words.first()) }.thenByDescending { it.count })
            .take(limit)
    }

    private fun matches(name: String, w: String): Boolean {
        if (name.contains(w)) return true
        if (w.length < 7) return false
        return name.split(Regex("[^\\p{L}\\p{N}]+")).any { it.isNotEmpty() && it[0] == w[0] && typo1(it, w) }
    }

    private fun typo1(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var diff = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++diff > 1) return false
            when { a.length > b.length -> i++; a.length < b.length -> j++; else -> { i++; j++ } }
        }
        return diff + (a.length - i) + (b.length - j) <= 1
    }
}
