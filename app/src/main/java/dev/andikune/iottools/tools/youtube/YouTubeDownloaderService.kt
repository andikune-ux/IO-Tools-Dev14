package dev.andikune.iottools.tools.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.andikune.iottools.tools.common.MediaStoreSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class YouTubeVideoFormat(
    val formatId: String,
    val label: String,
    val resolution: String,
    val ext: String,
    val isAudioOnly: Boolean,
    val hasAudio: Boolean,
    val approximateSize: String,
    val directUrl: String
)

data class YouTubeVideoData(
    val videoId: String,
    val title: String,
    val author: String,
    val authorUrl: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val formats: List<YouTubeVideoFormat>,
    val directStreamUrl: String
)

/**
 * YouTube extraction is performed locally by yt-dlp. No public Piped,
 * Invidious, Cobalt, or converter instance is trusted as a stream provider.
 * Downloadable media URLs are short-lived, so the selected format ID is
 * resolved again by yt-dlp at download time.
 */
object YouTubeDownloaderService {

    private val cancelledProcesses = ConcurrentHashMap.newKeySet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    internal fun isSupportedFormatExtension(extension: String, audioOnly: Boolean): Boolean {
        val ext = extension.trim().lowercase().removePrefix(".")
        return if (audioOnly) {
            ext in setOf("mp3", "m4a", "aac", "opus", "ogg", "oga", "wav", "webm")
        } else {
            ext in setOf("mp4", "m4v", "mov", "webm", "mkv")
        }
    }

    internal fun buildFormatSelector(formatId: String, hasAudio: Boolean): String? {
        val safeId = formatId.trim()
        if (!safeId.matches(Regex("^[A-Za-z0-9_.-]{1,80}$"))) return null
        return if (hasAudio) safeId else "$safeId+bestaudio/$safeId"
    }

    fun extractVideoId(rawUrl: String): String? {
        val clean = rawUrl.trim()
        val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
        if (idPattern.matches(clean)) return clean

        val uri = runCatching { java.net.URI(clean) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val allowedHosts = setOf("youtu.be", "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.userInfo != null) return null
        if (uri.port != -1 && uri.port !in setOf(80, 443)) return null
        if (host !in allowedHosts) return null

        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val queryId = uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.substringBefore('=') == "v" }
            ?.substringAfter('=', "")
        val candidate = when {
            host == "youtu.be" -> segments.firstOrNull()
            uri.path == "/watch" -> queryId
            else -> if (segments.size >= 2 && segments[0] in setOf("embed", "shorts", "v", "live")) segments[1] else null
        } ?: return null
        return candidate.takeIf { idPattern.matches(it) }
    }

    suspend fun fetchVideoDetails(rawUrl: String): Result<YouTubeVideoData> = withContext(Dispatchers.IO) {
        runCatching {
            val videoId = extractVideoId(rawUrl)
                ?: throw IllegalArgumentException("Tautan YouTube tidak valid.")
            val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"
            val request = YoutubeDLRequest(canonicalUrl).apply {
                addOption("--dump-single-json")
                addOption("--no-warnings")
                addOption("--no-playlist")
                addOption("--skip-download")
            }
            val response = YoutubeDL.getInstance().execute(request)
            val json = JSONObject(response.out.trim())
            val formatsJson = json.optJSONArray("formats")
                ?: throw IllegalStateException("yt-dlp tidak mengembalikan daftar format media.")
            val formats = mutableListOf<YouTubeVideoFormat>()
            for (index in 0 until formatsJson.length()) {
                val item = formatsJson.optJSONObject(index) ?: continue
                val formatId = item.optString("format_id").takeIf { it.isNotBlank() } ?: continue
                val ext = item.optString("ext", "mp4")
                val vcodec = item.optString("vcodec", "none")
                val acodec = item.optString("acodec", "none")
                val audioOnly = vcodec == "none" && acodec != "none"
                if (vcodec == "none" && !audioOnly) continue
                if (!isSupportedFormatExtension(ext, audioOnly)) continue
                val height = item.optInt("height", 0)
                val resolution = if (audioOnly) "Audio" else if (height > 0) "${height}p" else item.optString("resolution", "Video")
                val note = item.optString("format_note").takeIf { it.isNotBlank() }
                val size = item.optLong("filesize", item.optLong("filesize_approx", 0L))
                val sizeLabel = if (size > 0) String.format("%.1f MB", size / (1024.0 * 1024.0)) else "Ukuran dinamis"
                val audioLabel = if (audioOnly) "Audio" else "Video"
                formats += YouTubeVideoFormat(
                    formatId = formatId,
                    label = "$audioLabel ${note ?: resolution} ($ext)",
                    resolution = resolution,
                    ext = ext,
                    isAudioOnly = audioOnly,
                    hasAudio = acodec != "none",
                    approximateSize = sizeLabel,
                    // Intentionally empty: expiring direct URLs are resolved by yt-dlp at download time.
                    directUrl = ""
                )
            }
            if (formats.isEmpty()) throw IllegalStateException("Tidak ada format yang bisa diunduh untuk tautan ini.")
            val title = json.optString("title", "YouTube Video $videoId")
            YouTubeVideoData(
                videoId = videoId,
                title = title,
                author = json.optString("uploader", json.optString("channel", "YouTube")),
                authorUrl = json.optString("uploader_url", json.optString("channel_url", "https://www.youtube.com")),
                thumbnailUrl = json.optString("thumbnail", "https://img.youtube.com/vi/$videoId/hqdefault.jpg"),
                durationSeconds = json.optInt("duration", 0),
                formats = formats.distinctBy { it.formatId },
                directStreamUrl = ""
            )
        }
    }

    fun enqueueDownload(
        context: Context,
        format: YouTubeVideoFormat,
        videoTitle: String,
        videoId: String,
        onStarted: (String) -> Unit = {},
        onProgress: (Float, Long) -> Unit = { _, _ -> },
        onFinished: (String, Result<Uri>) -> Unit = { _, _ -> }
    ): String? {
        val safeTitle = MediaStoreSaver.sanitizeFileName(videoTitle, "YT_$videoId").take(100)
        val formatSelector = if (format.isAudioOnly) {
            format.formatId.takeIf { it.matches(Regex("^[A-Za-z0-9_.-]{1,80}$")) }
        } else {
            buildFormatSelector(format.formatId, format.hasAudio)
        }
        if (formatSelector == null) {
            Toast.makeText(context, "ID format YouTube tidak valid.", Toast.LENGTH_LONG).show()
            return null
        }
        val processId = "youtube-$videoId-${System.currentTimeMillis()}"
        val outputDir = File(context.cacheDir, "yt-dlp-downloads/$processId").apply { mkdirs() }
        if (!outputDir.isDirectory) {
            Toast.makeText(context, "Tidak dapat membuat folder sementara unduhan.", Toast.LENGTH_LONG).show()
            return null
        }
        onStarted(processId)
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                val sourceUrl = "https://www.youtube.com/watch?v=$videoId"
                // Use the sanitized title captured from metadata; never use an untrusted title as a path.
                val outputTemplate = File(outputDir, "$safeTitle [%(id)s].%(ext)s").absolutePath
                val request = YoutubeDLRequest(sourceUrl).apply {
                    addOption("-o", outputTemplate)
                    addOption("--no-playlist")
                    // Keep yt-dlp's default .part workflow: interrupted downloads must not
                    // be mistaken for completed output files.
                    addOption("--newline")
                    addOption("--no-write-thumbnail")
                    addOption("--no-write-info-json")
                    addOption("--no-write-subs")
                    addOption("--no-write-auto-subs")
                    if (format.isAudioOnly) {
                        addOption("-f", formatSelector)
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                    } else {
                        addOption("-f", formatSelector)
                        addOption("--merge-output-format", "mp4")
                    }
                }
                if (processId in cancelledProcesses) throw CancellationException("Unduhan dibatalkan.")
                YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ ->
                    mainHandler.post {
                        if (processId !in cancelledProcesses) onProgress(progress, eta)
                    }
                }
                if (processId in cancelledProcesses) throw CancellationException("Unduhan dibatalkan.")
                val downloaded = outputDir.listFiles()
                    ?.filter { it.isFile && it.length() > 0L && !it.name.endsWith(".part") }
                    ?.maxByOrNull { it.lastModified() }
                    ?: throw IllegalStateException("yt-dlp selesai tetapi file hasil tidak ditemukan.")
                val mime = when (downloaded.extension.lowercase()) {
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "aac" -> "audio/aac"
                    "opus" -> "audio/opus"
                    "ogg", "oga" -> "audio/ogg"
                    "wav" -> "audio/wav"
                    "webm" -> "video/webm"
                    "mkv" -> "video/x-matroska"
                    "m4v" -> "video/x-m4v"
                    "mov" -> "video/quicktime"
                    "mp4" -> "video/mp4"
                    else -> throw IllegalStateException(
                        "Format hasil (${downloaded.extension.ifBlank { "tanpa ekstensi" }}) tidak didukung untuk penyimpanan media."
                    )
                }
                val uri = downloaded.inputStream().use { input ->
                    MediaStoreSaver.saveStream(context, downloaded.name, mime, input)
                } ?: throw IllegalStateException("File gagal disimpan ke folder Download/IO-Tools.")
                downloaded.delete()
                uri
            }
            outputDir.deleteRecursively()
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    Toast.makeText(context, "Unduhan selesai: Download/IO-Tools", Toast.LENGTH_LONG).show()
                }.onFailure {
                    val message = if (processId in cancelledProcesses) {
                        "Unduhan YouTube dibatalkan."
                    } else {
                        "Unduhan gagal: ${it.message}"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                cancelledProcesses.remove(processId)
                onFinished(processId, result)
            }
        }
        return processId
    }

    fun cancelDownload(processId: String) {
        cancelledProcesses.add(processId)
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    fun openInYouTube(context: Context, videoId: String) {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(appIntent) }.onFailure {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun shareVideo(context: Context, videoId: String, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "Tonton video YouTube: $title\nhttps://youtu.be/$videoId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(Intent.createChooser(shareIntent, "Bagikan Video YouTube")) }
            .onFailure { Toast.makeText(context, "Gagal membagikan link: ${it.message}", Toast.LENGTH_SHORT).show() }
    }
}
