package dev.andikune.iottools.tools.common

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

    /**
     * Probes the first bytes and Content-Type so an HTML error page can never
     * be presented as a downloaded video.
     */
    suspend fun verifyDirectVideoStream(url: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Range", "bytes=0-1024")
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    return@withContext Result.failure(
                        IllegalStateException("Server mengembalikan HTTP ${response.code}.")
                    )
                }

                val contentType = response.header("Content-Type", "").orEmpty().lowercase()
                if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                    return@withContext Result.failure(
                        IllegalStateException("Tautan mengembalikan halaman web HTML, bukan stream media.")
                    )
                }

                val initialBytes = response.body?.byteStream()?.readNBytes(256) ?: byteArrayOf()
                val head = String(initialBytes, Charsets.ISO_8859_1)
                if (head.contains("<!DOCTYPE", ignoreCase = true) ||
                    head.contains("<html", ignoreCase = true) ||
                    head.contains("<script", ignoreCase = true)
                ) {
                    return@withContext Result.failure(
                        IllegalStateException("Respon server berisi HTML, bukan media biner.")
                    )
                }

                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun enqueueNativeVideoDownload(
        context: Context,
        directUrl: String,
        suggestedFileName: String,
        title: String,
        description: String = "Mengunduh file video...",
        mimeType: String = "video/mp4"
    ): Long {
        AppDownloadManager.startDownload(
            context = context,
            directUrl = directUrl,
            suggestedFileName = suggestedFileName,
            title = title,
            mimeType = mimeType
        )
        return 1L
    }
}
