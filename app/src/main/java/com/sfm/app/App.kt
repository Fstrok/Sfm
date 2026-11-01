package com.sfm.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.sfm.app.data.Catalog
import com.sfm.app.data.Downloads
import com.sfm.app.data.Feeds
import com.sfm.app.data.Library
import com.sfm.app.data.Net
import com.sfm.app.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class Graph(val ctx: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val net = Net(ctx.cacheDir)
    val catalog = Catalog(File(ctx.filesDir, "catalog.json"), net)
    val repo = Repo(net, catalog)
    val library = Library(File(ctx.filesDir, "library.json"), scope)
    val downloads = Downloads(ctx, library)
    val feeds = Feeds(repo, scope)

    init {
        scope.launch { library.load() }
        scope.launch { catalog.loadCache() }
    }
}

class App : Application(), SingletonImageLoader.Factory {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        graph = Graph(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.net.client })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
        .diskCache { DiskCache.Builder().directory(File(context.cacheDir, "images")).maxSizeBytes(300L shl 20).build() }
        .crossfade(true)
        .build()
}

val Context.graph: Graph get() = (applicationContext as App).graph

/** Saves uncaught exceptions to a file; shown with a "copy" button on next launch. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "crash.txt")

    fun install(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                file(ctx).writeText(buildString {
                    appendLine("SFM ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Thread: ${t.name}")
                    appendLine(e.stackTraceToString())
                })
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun take(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.let { f -> runCatching { f.readText() }.getOrNull().also { f.delete() } }
}
