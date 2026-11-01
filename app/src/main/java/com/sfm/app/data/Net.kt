package com.sfm.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

class SiteException(message: String, val code: Int = 0) : IOException(message)

class Resp(val code: Int, val body: String, val headers: Headers, val finalUrl: String)

class Net(cacheDir: File?) {
    val userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply { if (cacheDir != null) cache(okhttp3.Cache(File(cacheDir, "http"), 30L shl 20)) }
        .addInterceptor { chain ->
            val r = chain.request()
            val b = r.newBuilder()
            if (r.header("User-Agent") == null) b.header("User-Agent", userAgent)
            if (r.header("Referer") == null && r.url.host.endsWith("sfmcompile.club")) b.header("Referer", SITE)
            chain.proceed(b.build())
        }
        .build()

    private val noRedirect by lazy { client.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    suspend fun get(url: String, json: Boolean = false, follow: Boolean = true, tries: Int = 3): Resp {
        val req = Request.Builder().url(url)
            .header("Accept", if (json) "application/json" else "text/html,application/xhtml+xml")
            .build()
        var last: IOException? = null
        repeat(tries) { attempt ->
            try {
                val resp = (if (follow) client else noRedirect).newCall(req).await()
                resp.use {
                    val body = withContext(Dispatchers.IO) { it.body?.string().orEmpty() }
                    if (it.code in 500..599) throw SiteException("сайт ответил ${it.code}", it.code)
                    if (it.code == 403 && body.contains("Sucuri", true)) throw SiteException("Защита сайта отклонила запрос, попробуйте позже", 403)
                    return Resp(it.code, body, it.headers, it.request.url.toString())
                }
            } catch (e: IOException) {
                last = e
                if (e is SiteException && e.code in 400..499) throw e
                if (attempt < tries - 1) delay(700L * (attempt + 1))
            }
        }
        throw last ?: IOException("Нет сети или сайт не отвечает")
    }

    suspend fun post(url: String, form: Map<String, String>, referer: String): Resp {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(url).post(body)
            .header("X-Requested-With", "XMLHttpRequest").header("Referer", referer).build()
        client.newCall(req).await().use {
            return Resp(it.code, withContext(Dispatchers.IO) { it.body?.string().orEmpty() }, it.headers, it.request.url.toString())
        }
    }

    /** Content-Length of a remote file via a 1-byte range request. */
    suspend fun size(url: String): Long? = runCatching {
        val req = Request.Builder().url(url).header("Range", "bytes=0-0").build()
        client.newCall(req).await().use { r ->
            r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: r.header("Content-Length")?.toLongOrNull()?.takeIf { r.code == 200 }
        }
    }.getOrNull()
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) { _, _, _ -> response.close() } }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
