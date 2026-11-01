package com.sfm.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@Serializable
data class Settings(
    // security
    val biometric: Boolean = false,
    /** Seconds in background before the password is asked again. */
    val lockDelaySec: Int = 0,
    val hideInRecents: Boolean = true,
    // playback
    // videos always loop (site feature): no auto-next / loop settings
    val speed: Float = 1f,
    val resume: Boolean = true,
    val background: Boolean = false,
    val autoPip: Boolean = true,
    val muted: Boolean = false,
    /** 0 fit, 1 fill (crop), 2 stretch. */
    val resizeMode: Int = 0,
    // downloads
    val wifiOnly: Boolean = false,
    val showInGallery: Boolean = false,
    // view
    /** 0 = auto by screen width. */
    val columns: Int = 0,
    val hideWatched: Boolean = false,
    val duration: Duration = Duration.ANY,
    val sort: SortMode = SortMode.SITE,
    // subscriptions
    /** 0 = off. */
    val subsCheckHours: Int = 6,
)

@Serializable
data class HistoryEntry(val video: Video, val at: Long, val positionMs: Long = 0, val durationMs: Long = 0) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Serializable
data class DownloadEntry(val video: Video, val dmId: Long, val addedAt: Long, val fileName: String, val uri: String? = null, val size: Long = 0)

@Serializable
data class Security(val hash: String? = null, val salt: String? = null, val failCount: Int = 0, val lockedUntil: Long = 0)

@Serializable
data class LibraryData(
    val favorites: List<Video> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val hidden: List<Video> = emptyList(),
    val blacklist: List<Term> = emptyList(),
    val subs: List<Term> = emptyList(),
    val subsSeenMaxId: Long = 0,
    val subsNotifiedMaxId: Long = 0,
    val downloads: List<DownloadEntry> = emptyList(),
    val searches: List<String> = emptyList(),
    val settings: Settings = Settings(),
    val security: Security = Security(),
) {
    @Transient val favIds: Set<Long> = favorites.mapTo(HashSet()) { it.id }
    @Transient val hiddenIds: Set<Long> = hidden.mapTo(HashSet()) { it.id }
    @Transient val historyById: Map<Long, HistoryEntry> = history.associateBy { it.video.id }
    @Transient val downloadsById: Map<Long, DownloadEntry> = downloads.associateBy { it.video.id }
    @Transient val subIds: Set<String> = subs.mapTo(HashSet()) { "${it.isCategory}:${it.id}" }
    @Transient val blackCats: Set<Int> = blacklist.filter { it.isCategory }.mapTo(HashSet()) { it.id }
    @Transient val blackTags: Set<Int> = blacklist.filter { !it.isCategory }.mapTo(HashSet()) { it.id }
    @Transient val blackSlugs: Set<String> = blacklist.filter { !it.isCategory }.mapTo(HashSet()) { it.slug }

    fun isSubscribed(t: Term) = "${t.isCategory}:${t.id}" in subIds
    fun isBlacklisted(t: Term) = blacklist.any { it.id == t.id && it.isCategory == t.isCategory }

    /** Applies hidden videos, blacklist, "hide watched" and sort to a feed. */
    fun visible(items: List<Video>, sort: SortMode = settings.sort, hideWatched: Boolean = settings.hideWatched): List<Video> {
        val f = items.filter { v ->
            v.id !in hiddenIds &&
                v.allCatIds.none { it in blackCats } &&
                v.tagIds.none { it in blackTags } && v.tags.none { it in blackSlugs } &&
                (!hideWatched || (historyById[v.id]?.progress ?: 0f) < 0.9f)
        }
        return when (sort) {
            SortMode.SITE -> f
            SortMode.VIEWS -> f.sortedByDescending { it.views ?: -1 }
            SortMode.SCORE -> f.sortedByDescending { it.score ?: Int.MIN_VALUE }
            SortMode.NEWEST -> f.sortedByDescending { it.date }
            SortMode.LONGEST -> f.sortedByDescending { it.durationMs ?: ((it.tagDurationSec ?: 0) * 1000L) }
        }
    }
}

/** Persistent user library: one JSON file, loaded off the main thread, saved debounced. */
class Library(private val file: File, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _data = MutableStateFlow(LibraryData())
    val data: StateFlow<LibraryData> = _data
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded
    private var saveJob: Job? = null

    val value get() = _data.value
    val settings get() = _data.value.settings

    suspend fun load() = withContext(Dispatchers.IO) {
        val f = if (file.exists()) file else File(file.path + ".bak")
        if (f.exists()) runCatching { _data.value = json.decodeFromString<LibraryData>(f.readText()) }
        _loaded.value = true
    }

    fun update(block: (LibraryData) -> LibraryData) {
        _data.update(block)
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) { delay(800); saveNow() }
    }

    fun saveNow() {
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(LibraryData.serializer(), _data.value))
            if (file.exists()) file.copyTo(File(file.path + ".bak"), overwrite = true)
            tmp.renameTo(file) || run { file.writeText(tmp.readText()); tmp.delete() }
        }
    }

    fun exportJson(): String = json.encodeToString(LibraryData.serializer(), _data.value.copy(security = Security()))

    /** Merges an exported library into the current one (keeps password). */
    fun importJson(text: String): Boolean = runCatching {
        val o = json.decodeFromString<LibraryData>(text)
        update { d ->
            d.copy(
                favorites = (d.favorites + o.favorites).distinctBy { it.id },
                history = (d.history + o.history).sortedByDescending { it.at }.distinctBy { it.video.id }.take(HISTORY_MAX),
                hidden = (d.hidden + o.hidden).distinctBy { it.id },
                blacklist = (d.blacklist + o.blacklist).distinctBy { "${it.isCategory}:${it.id}" },
                subs = (d.subs + o.subs).distinctBy { "${it.isCategory}:${it.id}" },
                searches = (d.searches + o.searches).distinct().take(30),
            )
        }
    }.isSuccess

    fun settings(block: (Settings) -> Settings) = update { it.copy(settings = block(it.settings)) }

    fun toggleFavorite(v: Video): Boolean {
        var added = false
        update { d ->
            if (v.id in d.favIds) d.copy(favorites = d.favorites.filter { it.id != v.id })
            else { added = true; d.copy(favorites = listOf(v) + d.favorites) }
        }
        return added
    }

    fun recordHistory(v: Video, positionMs: Long, durationMs: Long) = update { d ->
        val e = HistoryEntry(v.copy(durationMs = durationMs.takeIf { it > 0 } ?: v.durationMs), System.currentTimeMillis(), positionMs, durationMs)
        d.copy(history = (listOf(e) + d.history.filter { it.video.id != v.id }).take(HISTORY_MAX))
    }

    fun removeHistory(ids: Set<Long>) = update { d -> d.copy(history = d.history.filter { it.video.id !in ids }) }
    fun clearHistory() = update { it.copy(history = emptyList()) }

    fun hide(v: Video) = update { d -> if (v.id in d.hiddenIds) d else d.copy(hidden = listOf(v) + d.hidden) }
    fun unhide(ids: Set<Long>) = update { d -> d.copy(hidden = d.hidden.filter { it.id !in ids }) }

    fun toggleBlacklist(t: Term): Boolean {
        var added = false
        update { d ->
            if (d.isBlacklisted(t)) d.copy(blacklist = d.blacklist.filterNot { it.id == t.id && it.isCategory == t.isCategory })
            else { added = true; d.copy(blacklist = d.blacklist + t) }
        }
        return added
    }

    fun toggleSub(t: Term): Boolean {
        var added = false
        update { d ->
            if (d.isSubscribed(t)) d.copy(subs = d.subs.filterNot { it.id == t.id && it.isCategory == t.isCategory })
            else { added = true; d.copy(subs = d.subs + t) }
        }
        return added
    }

    fun addSearch(q: String) {
        val s = q.trim(); if (s.isEmpty()) return
        update { d -> d.copy(searches = (listOf(s) + d.searches.filterNot { it.equals(s, true) }).take(30)) }
    }
    fun clearSearches() = update { it.copy(searches = emptyList()) }

    // ---- password ----
    val hasPassword get() = _data.value.security.hash != null

    fun setPassword(pw: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        update { it.copy(security = Security(hash(pw, salt), Base64.getEncoder().encodeToString(salt))) }
        saveNow()
    }

    sealed interface Check { data object Ok : Check; data object Wrong : Check; data class Wait(val seconds: Int) : Check }

    /** Verifies password with growing lockout after 5 failures. */
    fun checkPassword(pw: String, now: Long = System.currentTimeMillis()): Check {
        val s = _data.value.security
        if (s.lockedUntil > now) return Check.Wait(((s.lockedUntil - now + 999) / 1000).toInt())
        val ok = s.hash != null && s.salt != null && hash(pw, Base64.getDecoder().decode(s.salt)) == s.hash
        update { d ->
            if (ok) d.copy(security = d.security.copy(failCount = 0, lockedUntil = 0))
            else {
                val fails = d.security.failCount + 1
                val lock = if (fails >= 5) now + 30_000L * (1 shl (fails - 5).coerceAtMost(5)) else 0
                d.copy(security = d.security.copy(failCount = fails, lockedUntil = lock))
            }
        }
        return if (ok) Check.Ok else _data.value.security.lockedUntil.takeIf { it > now }?.let { Check.Wait(((it - now) / 1000).toInt()) } ?: Check.Wrong
    }

    private fun hash(pw: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pw.toCharArray(), salt, 60_000, 256)
        return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
    }

    companion object { const val HISTORY_MAX = 3000 }
}
