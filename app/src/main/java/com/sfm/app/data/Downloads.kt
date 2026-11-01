package com.sfm.app.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class DlProgress(val status: Int, val done: Long, val total: Long) {
    val running get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
    val ok get() = status == DownloadManager.STATUS_SUCCESSFUL
    val failed get() = status == DownloadManager.STATUS_FAILED
    val fraction get() = if (total > 0) done.toFloat() / total else 0f
}

/** Downloads through the system DownloadManager (background, notification, resume) into Download/SFM. */
class Downloads(private val ctx: Context, private val library: Library) {
    private val dm = ctx.getSystemService(DownloadManager::class.java)
    val folderName = "SFM"

    fun isDownloaded(id: Long) = library.value.downloadsById[id] != null

    /** Returns null on success or an error message. */
    fun start(v: Video): String? {
        val url = v.videoUrl ?: return "нет ссылки на видео"
        library.value.downloadsById[v.id]?.let { e -> if (status(e.dmId)?.failed != true) return "Уже скачано" else remove(v.id, deleteFile = true) }
        val name = (v.title.ifBlank { "video" }.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().take(80).ifBlank { "video" }) + "_${v.id}.mp4"
        ensureNoMedia()
        return runCatching {
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(v.title.ifBlank { "SFM" })
                .setDescription("SFM")
                .setMimeType("video/mp4")
                .addRequestHeader("Referer", SITE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$folderName/$name")
                .setAllowedNetworkTypes(if (library.settings.wifiOnly) DownloadManager.Request.NETWORK_WIFI else DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .setAllowedOverMetered(!library.settings.wifiOnly)
            val id = dm.enqueue(req)
            library.update { d -> d.copy(downloads = listOf(DownloadEntry(v, id, System.currentTimeMillis(), name)) + d.downloads.filter { it.video.id != v.id }) }
            null
        }.getOrElse { it.message ?: "не удалось начать скачивание" }
    }

    fun remove(videoId: Long, deleteFile: Boolean = true) {
        val e = library.value.downloadsById[videoId] ?: return
        if (deleteFile) runCatching { dm.remove(e.dmId) }
        library.update { d -> d.copy(downloads = d.downloads.filter { it.video.id != videoId }) }
    }

    fun status(dmId: Long): DlProgress? = runCatching {
        dm.query(DownloadManager.Query().setFilterById(dmId)).use { c ->
            if (!c.moveToFirst()) return@use null
            DlProgress(
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }
    }.getOrNull()

    /** Polls all downloads once a second while collected: videoId → progress. */
    fun progress(): Flow<Map<Long, DlProgress>> = flow {
        while (true) {
            val map = library.value.downloads.mapNotNull { e -> status(e.dmId)?.let { e.video.id to it } }.toMap()
            emit(map)
            delay(if (map.values.any { it.running }) 1000 else 4000)
        }
    }.flowOn(Dispatchers.IO)

    /** Playable local uri for a finished download, or null. */
    fun localUri(videoId: Long): Uri? {
        val e = library.value.downloadsById[videoId] ?: return null
        val st = status(e.dmId)
        if (st?.ok == true) return runCatching { dm.getUriForDownloadedFile(e.dmId) }.getOrNull()
        if (st == null) {
            val f = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$folderName/${e.fileName}")
            if (f.canRead()) return Uri.fromFile(f)
        }
        return null
    }

    private fun ensureNoMedia() {
        runCatching {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folderName)
            dir.mkdirs()
            val nm = File(dir, ".nomedia")
            if (library.settings.showInGallery) nm.delete() else if (!nm.exists()) nm.createNewFile()
        }
    }
}
