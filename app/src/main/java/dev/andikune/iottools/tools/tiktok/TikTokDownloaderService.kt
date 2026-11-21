package dev.andikune.iottools.tools.tiktok

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.andikune.iottools.tools.common.AppDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

data class TikTokVideoData(
    val id: String,
    val title: String,
    val authorName: String,
    val authorUniqueId: String,
    val authorAvatar: String,
    val coverUrl: String,
    val sourceUrl: String,
    val noWatermarkVideoUrl: String,
    val watermarkVideoUrl: String?,
    val audioUrl: String?,
    val durationSeconds: Int,
    val videoSizeFormatted: String
)

object TikTokDownloaderService {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val mediaCdnSuffixes = setOf(
        ".tiktokcdn.com",
        ".tiktokcdn-us.com",
        ".tiktokcdn-eu.com",
        ".tiktokv.com",
        ".byteoversea.com",
        ".ibytedtos.com",
        ".ibyteimg.com",
        ".muscdn.com",
        ".douyinvod.com",
        ".douyinpic.com",
        ".byteimg.com"
    )

    fun normalizeTikTokUrl(rawUrl: String): String? {
        val clean = rawUrl.trim()
        if (clean.isBlank() || clean.length > 2048 || clean.any { it.isISOControl() }) return null
        val candidate = if ("://" in clean) clean else "https://$clean"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.userInfo != null ||
            (uri.port != -1 && uri.port !in setOf(80, 443))
        ) return null
        val allowedHosts = setOf(
            "tiktok.com", "www.tiktok.com", "m.tiktok.com",
            "vt.tiktok.com", "vm.tiktok.com", "v.tiktok.com",
            "douyin.com", "www.douyin.com", "v.douyin.com", "iesdouyin.com"
        )
        return uri.toASCIIString().takeIf { host in allowedHosts }
    }

    fun isAllowedMediaUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank() || rawUrl.length > 8192) return false
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null ||
            (uri.port != -1 && uri.port != 443)
        ) return false
        val isTikTokCdn = mediaCdnSuffixes.any { host.endsWith(it) }
        val isTikwmCdn = host == "tikwm.com" || host.endsWith(".tikwm.com")
        return isTikTokCdn || isTikwmCdn
    }

    /**
     * Restored legacy TikWM resolver flow. TikWM supplies the direct media URLs;
     * yt-dlp is deliberately not involved in TikTok video metadata extraction.
     */
    suspend fun fetchVideoDetails(
        @Suppress("UNUSED_PARAMETER") context: Context,
        rawUrl: String
    ): Result<TikTokVideoData> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = normalizeTikTokUrl(rawUrl)
                ?: throw IllegalArgumentException("Tautan harus berasal dari domain TikTok atau Douyin yang valid.")

            val endpoint = "https://www.tikwm.com/api/?url=${Uri.encode(cleanUrl)}&hd=1"
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.use {
                if (!it.isSuccessful) {
                    throw IllegalStateException("Gagal menghubungi server TikWM (HTTP ${it.code}).")
                }
                it.body?.string() ?: throw IllegalStateException("Respon server TikWM kosong.")
            }

            val json = JSONObject(body)
            val code = json.optInt("code", -1)
            val message = json.optString("msg", "Gagal memproses link")
            if (code != 0) {
                throw IllegalStateException("TikWM API: $message. Pastikan video bersifat publik dan belum dihapus.")
            }

            val data = json.optJSONObject("data")
                ?: throw IllegalStateException("Data video tidak ditemukan pada respons TikWM.")
            val id = data.optString("id").ifBlank { System.currentTimeMillis().toString() }
            val title = data.optString("title").ifBlank { "TikTok Video" }
            val cover = data.optString("cover")
            val play = data.optString("play")
            val hdPlay = data.optString("hdplay").ifBlank { play }
            val noWatermarkUrl = hdPlay.ifBlank { play }
            val watermarkUrl = data.optString("wmplay").takeIf { it.isNotBlank() }
            val audioUrl = data.optString("music").takeIf { it.isNotBlank() }

            if (noWatermarkUrl.isBlank() || !isAllowedMediaUrl(noWatermarkUrl)) {
                throw IllegalStateException("TikWM tidak memberikan URL video tanpa watermark yang valid.")
            }
            if (audioUrl != null && !isAllowedMediaUrl(audioUrl)) {
                throw IllegalStateException("TikWM memberikan URL audio dari domain yang tidak diizinkan.")
            }

            val size = data.optLong("size", 0L)
            val hdSize = data.optLong("hd_size", size)
            val sizeBytes = if (hdSize > 0L) hdSize else size
            val sizeFormatted = if (sizeBytes > 0L) {
                String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            } else {
                "Ukuran dinamis"
            }

            val author = data.optJSONObject("author")
            TikTokVideoData(
                id = id,
                title = title,
                authorName = author?.optString("nickname").orEmpty().ifBlank { "TikTok Creator" },
                authorUniqueId = author?.optString("unique_id").orEmpty().ifBlank { "creator" },
                authorAvatar = author?.optString("avatar").orEmpty(),
                coverUrl = cover,
                sourceUrl = cleanUrl,
                noWatermarkVideoUrl = noWatermarkUrl,
                watermarkVideoUrl = watermarkUrl?.takeIf(::isAllowedMediaUrl),
                audioUrl = audioUrl,
                durationSeconds = data.optInt("duration", 0),
                videoSizeFormatted = sizeFormatted
            )
        }
    }

    /**
     * Uses the original direct-download mechanism: Android DownloadManager
     * wrapper with in-app progress, now receiving TikWM's resolved media URL.
     */
    fun startDownload(
        context: Context,
        url: String,
        fileName: String,
        title: String,
        isAudio: Boolean,
        @Suppress("UNUSED_PARAMETER") formatId: String? = null,
        @Suppress("UNUSED_PARAMETER") formatHasAudio: Boolean = false
    ) {
        if (!isAllowedMediaUrl(url)) {
            Toast.makeText(context, "URL media TikTok tidak valid atau bukan dari CDN yang diizinkan.", Toast.LENGTH_LONG).show()
            return
        }
        AppDownloadManager.startDownload(
            context = context,
            directUrl = url,
            suggestedFileName = fileName,
            title = title,
            mimeType = if (isAudio) "audio/mpeg" else "video/mp4",
            // Some TikWM video CDN responses are mislabeled as audio/mpeg.
            // The MP4 file-signature check still rejects an actual MP3 stream.
            acceptedContentTypes = if (isAudio) emptySet() else setOf("audio/mpeg")
        )
    }

    fun openPreview(context: Context, directUrl: String) {
        if (!isAllowedMediaUrl(directUrl)) {
            Toast.makeText(context, "URL preview bukan berasal dari CDN TikTok/TikWM yang valid.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(directUrl), "video/mp4")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(directUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure {
                Toast.makeText(context, "Tidak dapat membuka preview video.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareMediaLink(context: Context, sourceUrl: String, title: String) {
        val safeSourceUrl = normalizeTikTokUrl(sourceUrl)
        if (safeSourceUrl == null) {
            Toast.makeText(context, "URL TikTok tidak valid.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Unduh Video TikTok Tanpa Watermark:\n$title\n\nLink: $safeSourceUrl")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Link Video TikTok"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
