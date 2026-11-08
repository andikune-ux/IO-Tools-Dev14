package dev.andikune.iottools.tools.common

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client and real stream validation for the downloader tools.
 */
object VideoDownloadHelper {

    val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    internal fun isSecurePublicMediaUrl(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null) return false
        if (host == "localhost" || host.endsWith(".local") ||
            host.endsWith(".internal") || host.endsWith(".lan") ||
            !host.contains('.') || host.contains(':') ||
            host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
        ) return false
        return true
    }

    /**
     * Probes a bounded prefix and validates media signatures. HTML/JSON error
     * pages and insecure or local targets are never reported as video streams.
     */
    suspend fun verifyDirectVideoStream(url: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            require(isSecurePublicMediaUrl(url)) {
                "URL stream harus HTTPS publik dan tidak boleh berisi kredensial atau alamat lokal."
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "video/*,application/vnd.apple.mpegurl,application/x-mpegURL,application/dash+xml,application/octet-stream;q=0.8")
                .header("Range", "bytes=0-1023")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Server mengembalikan HTTP ${response.code}.")
                }
                if (!isSecurePublicMediaUrl(response.request.url.toString())) {
                    throw IllegalStateException("Redirect mengarah ke URL yang tidak aman.")
                }

                val contentType = response.header("Content-Type").orEmpty()
                    .substringBefore(';')
                    .trim()
                    .lowercase()
                if (contentType.startsWith("text/") || contentType.contains("json") ||
                    contentType.contains("xhtml")
                ) {
                    throw IllegalStateException("Server mengembalikan teks/JSON/HTML, bukan stream video.")
                }

                val header = response.body?.byteStream()?.use { readAtMost(it, 512) }
                    ?: throw IllegalStateException("Server tidak mengirim body media.")
                if (header.isEmpty()) throw IllegalStateException("Body media kosong.")

                val headText = String(header, Charsets.ISO_8859_1).trimStart()
                val isHtml = headText.startsWith("<!doctype html", true) ||
                    headText.startsWith("<html", true) ||
                    headText.contains("<script", true)
                if (isHtml) throw IllegalStateException("Respon berisi HTML, bukan media.")

                val valid = when {
                    contentType == "video/mp4" || contentType == "video/quicktime" ||
                        contentType == "video/x-m4v" ->
                        AppDownloadManager.matchesFileSignature("video/mp4", header)
                    contentType == "video/webm" || contentType == "video/x-matroska" ->
                        AppDownloadManager.matchesFileSignature("video/webm", header)
                    contentType == "video/x-flv" ->
                        AppDownloadManager.matchesFileSignature("video/x-flv", header)
                    contentType.startsWith("video/") -> true
                    contentType.contains("mpegurl") ->
                        headText.startsWith("#EXTM3U", true)
                    contentType.contains("dash+xml") ->
                        headText.contains("<MPD", true)
                    contentType == "application/octet-stream" || contentType.isBlank() ->
                        AppDownloadManager.matchesFileSignature("video/mp4", header) ||
                            AppDownloadManager.matchesFileSignature("video/webm", header) ||
                            AppDownloadManager.matchesFileSignature("video/x-flv", header) ||
                            headText.startsWith("#EXTM3U", true) ||
                            headText.contains("<MPD", true)
                    else -> false
                }
                if (!valid) {
                    throw IllegalStateException("Signature body tidak cocok dengan format video/manifest yang didukung.")
                }
                true
            }
        }
    }

    private fun readAtMost(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        while (output.size() < maxBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
            if (read < 0) break
            if (read == 0) continue
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    fun enqueueNativeVideoDownload(
        context: Context,
        directUrl: String,
        suggestedFileName: String,
        title: String,
        mimeType: String = "video/mp4"
    ) {
        AppDownloadManager.startDownload(
            context = context,
            directUrl = directUrl,
            suggestedFileName = suggestedFileName,
            title = title,
            mimeType = mimeType
        )
    }
}
