package dev.andikune.iottools.tools.tiktok

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class TikTokVideoData(
    val id: String,
    val title: String,
    val authorName: String,
    val authorUniqueId: String,
    val authorAvatar: String,
    val coverUrl: String,
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
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Fetches genuine video stream metadata from TikTok using TikWM backend.
     * Returns direct MP4 link without watermark, MP3 link, author and thumbnail.
     */
    suspend fun fetchVideoDetails(rawUrl: String): Result<TikTokVideoData> = withContext(Dispatchers.IO) {
        try {
            val inputUrl = rawUrl.trim()
            val cleanUrl = if (inputUrl.startsWith("http://", true) || inputUrl.startsWith("https://", true)) {
                inputUrl
            } else {
                "https://$inputUrl"
            }
            val parsedUrl = runCatching { Uri.parse(cleanUrl) }.getOrNull()
            val host = parsedUrl?.host?.lowercase().orEmpty()
            val validHost = host == "tiktok.com" || host.endsWith(".tiktok.com") ||
                host == "douyin.com" || host.endsWith(".douyin.com")
            if (!validHost || parsedUrl?.scheme !in setOf("http", "https")) {
                return@withContext Result.failure(IllegalArgumentException("Tautan harus berasal dari domain TikTok atau Douyin yang valid."))
            }

            // Endpoint TikWM public API
            val endpoint = "https://www.tikwm.com/api/?url=${Uri.encode(cleanUrl)}&hd=1"
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gagal menghubungi server TikTok API (Kode HTTP: ${response.code})"))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Respon server kosong."))

            val json = JSONObject(body)
            val code = json.optInt("code", -1)
            val msg = json.optString("msg", "Gagal memproses link")

            if (code != 0) {
                return@withContext Result.failure(Exception("API Error: $msg. Pastikan video tidak private atau dihapus."))
            }

            val data = json.getJSONObject("data")
            val id = data.optString("id", System.currentTimeMillis().toString())
            val title = data.optString("title", "TikTok Video").ifBlank { "TikTok Video" }
            val cover = data.optString("cover", "")
            
            // Prefer HD no-watermark if available, fallback to standard play
            val play = data.optString("play", "")
            val hdPlay = data.optString("hdplay", "").ifBlank { play }
            val noWatermarkUrl = if (hdPlay.isNotBlank()) hdPlay else play
            val wmPlay = data.optString("wmplay", null)
            val music = data.optString("music", null)
            val duration = data.optInt("duration", 0)
            val size = data.optLong("size", 0L)
            val hdSize = data.optLong("hd_size", size)

            val sizeBytes = if (hdSize > 0) hdSize else size
            val sizeFormatted = if (sizeBytes > 0) {
                String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            } else {
                "Standard HD"
            }

            val authorObj = data.optJSONObject("author")
            val authorName = authorObj?.optString("nickname", "TikTok Creator") ?: "TikTok Creator"
            val authorUniqueId = authorObj?.optString("unique_id", "creator") ?: "creator"
            val authorAvatar = authorObj?.optString("avatar", "") ?: ""

            if (noWatermarkUrl.isBlank()) {
                return@withContext Result.failure(Exception("Tidak ditemukan link streaming video tanpa watermark."))
            }

            Result.success(
                TikTokVideoData(
                    id = id,
                    title = title,
                    authorName = authorName,
                    authorUniqueId = authorUniqueId,
                    authorAvatar = authorAvatar,
                    coverUrl = cover,
                    noWatermarkVideoUrl = noWatermarkUrl,
                    watermarkVideoUrl = wmPlay,
                    audioUrl = music,
                    durationSeconds = duration,
                    videoSizeFormatted = sizeFormatted
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads the real media file (Video MP4 without watermark or Audio MP3)
     * using AppDownloadManager with 0-100% progress and speed counter.
     */
    fun startDownload(
        context: Context,
        url: String,
        fileName: String,
        title: String,
        isAudio: Boolean
    ) {
        val mime = if (isAudio) "audio/mpeg" else "video/mp4"
        dev.andikune.iottools.tools.common.AppDownloadManager.startDownload(
            context = context,
            directUrl = url,
            suggestedFileName = fileName,
            title = title,
            mimeType = mime
        )
    }

    /**
     * Opens direct video preview in external video player or web browser.
     */
    fun openPreview(context: Context, directUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(directUrl), "video/mp4")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to browser
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(directUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    /**
     * Shares video download link to WhatsApp, Telegram, etc.
     */
    fun shareMediaLink(context: Context, directUrl: String, title: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Unduh Video TikTok Tanpa Watermark:\n$title\n\nLink: $directUrl")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Link Video TikTok"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
