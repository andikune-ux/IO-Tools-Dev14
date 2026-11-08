package dev.andikune.iottools.tools.tiktok

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.andikune.iottools.tools.common.AppDownloadManager
import dev.andikune.iottools.tools.common.MediaStoreSaver
import dev.andikune.iottools.tools.common.VideoDownloadHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URI

data class TikTokVideoData(
    val id: String,
    val title: String,
    val authorName: String,
    val authorUniqueId: String,
    val authorAvatar: String,
    val coverUrl: String,
    val sourceUrl: String,
    val noWatermarkVideoUrl: String,
    val noWatermarkFormatId: String,
    val noWatermarkFormatHasAudio: Boolean,
    val watermarkVideoUrl: String?,
    val audioAvailable: Boolean,
    val durationSeconds: Int,
    val videoSizeFormatted: String
)

object TikTokDownloaderService {

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

    internal fun buildVideoFormatSelector(formatId: String, hasAudio: Boolean = false): String? {
        val safeId = formatId.trim()
        if (!safeId.matches(Regex("^[A-Za-z0-9_.-]{1,80}$"))) return null
        return if (hasAudio) safeId else "$safeId+bestaudio/$safeId"
    }

    fun isAllowedMediaUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank() || rawUrl.length > 8192) return false
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null ||
            (uri.port != -1 && uri.port != 443) || host.length > 253 ||
            host.split('.').any { label ->
                label.isBlank() || label.length > 63 ||
                    !label.matches(Regex("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$"))
            } || host.matches(Regex("^[0-9.]+$"))
        ) return false
        return mediaCdnSuffixes.any { host.endsWith(it) }
    }

    /**
     * Uses the app's local yt-dlp extractor. No TikWM or other anonymous
     * third-party resolver endpoint is contacted.
     */
    suspend fun fetchVideoDetails(rawUrl: String): Result<TikTokVideoData> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = normalizeTikTokUrl(rawUrl)
                ?: throw IllegalArgumentException("Tautan harus berasal dari domain TikTok atau Douyin yang valid.")

            val request = YoutubeDLRequest(cleanUrl).apply {
                addOption("--dump-single-json")
                addOption("--no-warnings")
                addOption("--skip-download")
                addOption("--no-playlist")
            }
            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out.trim()
            if (output.isBlank()) throw IllegalStateException("yt-dlp tidak mengembalikan metadata video TikTok.")

            val data = JSONObject(output)
            val formatsJson = data.optJSONArray("formats")
                ?: throw IllegalStateException("Extractor tidak mengembalikan daftar format video.")
            val videoFormats = mutableListOf<JSONObject>()
            val audioFormats = mutableListOf<JSONObject>()

            for (index in 0 until formatsJson.length()) {
                val format = formatsJson.optJSONObject(index) ?: continue
                val url = format.optString("url")
                if (!isAllowedMediaUrl(url)) continue
                val vcodec = format.optString("vcodec", "none")
                val acodec = format.optString("acodec", "none")
                if (vcodec != "none") videoFormats += format
                if (vcodec == "none" && acodec != "none") audioFormats += format
            }

            val noWatermarkFormat = videoFormats
                .filter { format ->
                    val note = format.optString("format_note").lowercase()
                    val id = format.optString("format_id").lowercase()
                    !note.contains("watermark") && id != "download_addr"
                }
                .maxByOrNull { format ->
                    format.optInt("height", 0) * 1_000_000L +
                        format.optLong("tbr", 0L).coerceAtMost(999_999L)
                } ?: throw IllegalStateException(
                    "Tidak ditemukan format video tanpa watermark dari extractor. Tidak akan mengklaim file watermarked sebagai no-watermark."
                )

            val noWatermarkUrl = noWatermarkFormat.optString("url")
                .takeIf(::isAllowedMediaUrl)
                ?: throw IllegalStateException("URL video TikTok bukan berasal dari CDN yang diizinkan.")

            val watermarkFormat = videoFormats.firstOrNull { format ->
                format.optString("format_note").contains("watermark", ignoreCase = true) ||
                    format.optString("format_id").equals("download_addr", ignoreCase = true)
            }
            val watermarkUrl = watermarkFormat?.optString("url")?.takeIf(::isAllowedMediaUrl)

            val id = data.optString("id").ifBlank { "tiktok_${System.currentTimeMillis()}" }
            val title = data.optString("title").ifBlank { "TikTok Video" }
            val authorName = data.optString("uploader")
                .ifBlank { data.optString("channel") }
                .ifBlank { "TikTok Creator" }
            val authorId = data.optString("uploader_id")
                .ifBlank { data.optString("channel_id") }
                .ifBlank { "creator" }
                .removePrefix("@")
            val thumbnail = data.optString("thumbnail").takeIf(::isAllowedMediaUrl).orEmpty()
            val duration = data.optInt("duration", 0)
            val sizeBytes = noWatermarkFormat.optLong(
                "filesize",
                noWatermarkFormat.optLong("filesize_approx", 0L)
            )
            val sizeFormatted = if (sizeBytes > 0L) {
                String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            } else {
                "Ukuran dinamis"
            }

            TikTokVideoData(
                id = id,
                title = title,
                authorName = authorName,
                authorUniqueId = authorId,
                authorAvatar = "",
                coverUrl = thumbnail,
                sourceUrl = cleanUrl,
                noWatermarkVideoUrl = noWatermarkUrl,
                noWatermarkFormatId = noWatermarkFormat.optString("format_id")
                    .takeIf { buildVideoFormatSelector(it) != null }
                    ?: throw IllegalStateException("Extractor mengembalikan ID format video yang tidak valid."),
                noWatermarkFormatHasAudio = noWatermarkFormat.optString("acodec", "none") != "none",
                watermarkVideoUrl = watermarkUrl,
                audioAvailable = audioFormats.isNotEmpty() ||
                    videoFormats.any { it.optString("acodec", "none") != "none" },
                durationSeconds = duration,
                videoSizeFormatted = sizeFormatted
            )
        }.recoverCatching { error ->
            val message = error.message.orEmpty()
            when {
                message.contains("login", ignoreCase = true) ||
                    message.contains("private", ignoreCase = true) ||
                    message.contains("authentication", ignoreCase = true) ->
                    throw IllegalStateException("Video TikTok memerlukan login atau tidak publik. Tool ini hanya mendukung konten publik yang boleh diakses.")
                else -> throw error
            }
        }
    }

    /**
     * Video uses the direct extractor-selected no-watermark stream. Audio is
     * extracted from the original public TikTok URL and converted to MP3 by FFmpeg.
     */
    fun startDownload(
        context: Context,
        url: String,
        fileName: String,
        title: String,
        isAudio: Boolean,
        formatId: String? = null,
        formatHasAudio: Boolean = false
    ) {
        if (!isAudio) {
            val sourceUrl = normalizeTikTokUrl(url)
            val formatSelector = buildVideoFormatSelector(formatId.orEmpty(), formatHasAudio)
            if (sourceUrl == null || formatSelector == null) {
                Toast.makeText(context, "URL sumber atau format video TikTok tidak valid.", Toast.LENGTH_LONG).show()
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                val processId = "tiktok-video-${System.currentTimeMillis()}"
                val outputDir = File(context.cacheDir, "tiktok-video/$processId").apply { mkdirs() }
                if (!outputDir.isDirectory) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Tidak dapat membuat folder sementara video TikTok.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val result = runCatching {
                    val outputTemplate = File(outputDir, "video.%(ext)s").absolutePath
                    val request = YoutubeDLRequest(sourceUrl).apply {
                        addOption("-o", outputTemplate)
                        addOption("-f", formatSelector)
                        addOption("--no-warnings")
                        addOption("--no-playlist")
                        addOption("--newline")
                        addOption("--remux-video", "mp4")
                        addOption("--no-write-thumbnail")
                        addOption("--no-write-info-json")
                        addOption("--no-write-subs")
                        addOption("--no-write-auto-subs")
                    }
                    YoutubeDL.getInstance().execute(request, processId)
                    val downloaded = outputDir.listFiles()
                        ?.filter { it.isFile && it.length() > 0L && it.extension.equals("mp4", true) }
                        ?.maxByOrNull { it.lastModified() }
                        ?: throw IllegalStateException("Engine tidak menghasilkan MP4 gabungan dengan audio.")
                    val uri = downloaded.inputStream().use { input ->
                        MediaStoreSaver.saveStream(context, fileName, "video/mp4", input)
                    } ?: throw IllegalStateException("Gagal menyimpan video TikTok ke Download/IO-Tools.")
                    downloaded.delete()
                    uri
                }
                outputDir.deleteRecursively()
                withContext(Dispatchers.Main) {
                    result.onSuccess {
                        Toast.makeText(context, "Video TikTok tersimpan di Download/IO-Tools.", Toast.LENGTH_LONG).show()
                    }.onFailure {
                        Toast.makeText(context, "Gagal mengunduh video TikTok: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }

        val sourceUrl = normalizeTikTokUrl(url)
        if (sourceUrl == null) {
            Toast.makeText(context, "URL sumber TikTok tidak valid.", Toast.LENGTH_LONG).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val processId = "tiktok-audio-${System.currentTimeMillis()}"
            val outputDir = File(context.cacheDir, "tiktok-audio/$processId").apply { mkdirs() }
            if (!outputDir.isDirectory) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Tidak dapat membuat folder sementara audio TikTok.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val result = runCatching {
                val outputTemplate = File(outputDir, "audio.%(ext)s").absolutePath
                val request = YoutubeDLRequest(sourceUrl).apply {
                    addOption("-o", outputTemplate)
                    addOption("--no-warnings")
                    addOption("--no-playlist")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                    addOption("--no-write-thumbnail")
                    addOption("--no-write-info-json")
                    addOption("--no-write-subs")
                    addOption("--no-write-auto-subs")
                }
                YoutubeDL.getInstance().execute(request, processId)
                val downloaded = outputDir.listFiles()
                    ?.filter { it.isFile && it.length() > 0L && it.extension.equals("mp3", true) }
                    ?.maxByOrNull { it.lastModified() }
                    ?: throw IllegalStateException("FFmpeg tidak menghasilkan file MP3.")
                val uri = downloaded.inputStream().use { input ->
                    MediaStoreSaver.saveStream(context, fileName, "audio/mpeg", input)
                } ?: throw IllegalStateException("Gagal menyimpan MP3 ke Download/IO-Tools.")
                downloaded.delete()
                uri
            }
            outputDir.deleteRecursively()
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    Toast.makeText(context, "Audio MP3 tersimpan di Download/IO-Tools.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(context, "Gagal mengekstrak audio: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun openPreview(context: Context, directUrl: String) {
        if (!isAllowedMediaUrl(directUrl)) {
            Toast.makeText(context, "URL preview bukan berasal dari CDN TikTok yang valid.", Toast.LENGTH_SHORT).show()
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
                putExtra(Intent.EXTRA_TEXT, "Video TikTok: $title\n$safeSourceUrl")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Link Video TikTok"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
