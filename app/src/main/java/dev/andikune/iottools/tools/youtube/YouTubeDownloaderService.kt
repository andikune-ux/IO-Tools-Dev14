package dev.andikune.iottools.tools.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.andikune.iottools.tools.common.AppDownloadManager
import dev.andikune.iottools.tools.common.VideoDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.regex.Pattern

data class YouTubeVideoFormat(
    val formatId: String,
    val label: String,
    val resolution: String,
    val ext: String,
    val isAudioOnly: Boolean,
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

object YouTubeDownloaderService {

    private val client by lazy { VideoDownloadHelper.client }

    /**
     * Extracts YouTube Video ID from various link formats:
     * - https://www.youtube.com/watch?v=VIDEO_ID
     * - https://youtu.be/VIDEO_ID
     * - https://youtube.com/shorts/VIDEO_ID
     * - https://m.youtube.com/watch?v=VIDEO_ID
     */
    fun extractVideoId(rawUrl: String): String? {
        val clean = rawUrl.trim()
        val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
        if (idPattern.matches(clean)) return clean

        val uri = runCatching { Uri.parse(clean) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme !in setOf("http", "https")) return null
        if (host != "youtu.be" && host != "youtube.com" && !host.endsWith(".youtube.com")) return null

        val candidate = when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull()
            uri.path == "/watch" -> uri.getQueryParameter("v")
            else -> {
                val segments = uri.pathSegments
                if (segments.size >= 2 && segments[0] in setOf("embed", "shorts", "v", "live")) {
                    segments[1]
                } else null
            }
        } ?: return null
        return candidate.takeIf { idPattern.matches(it) }
    }

    /**
     * Fetches metadata and resolves direct playable video streams using multiple cascading endpoints:
     * 1. Official YouTube oEmbed (metadata)
     * 2. Piped API (high reliability direct CDN streams)
     * 3. Invidious Instances
     * 4. Cobalt API
     * 5. Loader.to converter polling
     */
    suspend fun fetchVideoDetails(rawUrl: String): Result<YouTubeVideoData> = withContext(Dispatchers.IO) {
        try {
            val videoId = extractVideoId(rawUrl)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Tautan tidak valid. Masukkan URL YouTube atau Shorts yang valid (contoh: youtu.be/... atau youtube.com/watch?v=...)")
                )

            // Step 1: Query YouTube Official oEmbed for clean metadata
            var videoTitle = "YouTube Video - $videoId"
            var authorName = "YouTube Creator"
            var authorUrl = "https://www.youtube.com"
            val fallbackThumbnail = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
            var resolvedThumbnail = fallbackThumbnail

            try {
                val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
                val oembedReq = Request.Builder()
                    .url(oembedUrl)
                    .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                    .build()
                client.newCall(oembedReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            videoTitle = json.optString("title", videoTitle)
                            authorName = json.optString("author_name", authorName)
                            authorUrl = json.optString("author_url", authorUrl)
                            val thumb = json.optString("thumbnail_url", "")
                            if (thumb.isNotBlank()) resolvedThumbnail = thumb
                        }
                    }
                }
            } catch (_: Exception) {}

            val formats = mutableListOf<YouTubeVideoFormat>()

            // Step 2: Try Piped API instances
            val pipedInstances = listOf(
                "https://pipedapi.tokhmi.xyz",
                "https://pipedapi.kavin.rocks",
                "https://api.piped.private.coffee"
            )

            for (instance in pipedInstances) {
                try {
                    val pipedUrl = "$instance/streams/$videoId"
                    val req = Request.Builder()
                        .url(pipedUrl)
                        .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val json = JSONObject(body)
                            videoTitle = json.optString("title", videoTitle)
                            authorName = json.optString("uploader", authorName)

                            val videoStreams = json.optJSONArray("videoStreams")
                            if (videoStreams != null && videoStreams.length() > 0) {
                                for (i in 0 until videoStreams.length()) {
                                    val item = videoStreams.getJSONObject(i)
                                    val streamUrl = item.optString("url", "")
                                    val quality = item.optString("quality", "720p")
                                    val format = item.optString("format", "mp4").lowercase()
                                    val isVideoOnly = item.optBoolean("videoOnly", false)

                                    if (streamUrl.isNotBlank() && !isVideoOnly && format.contains("mp4")) {
                                        val clen = item.optLong("contentLength", 0L)
                                        val sizeStr = if (clen > 0) String.format("%.1f MB", clen / (1024.0 * 1024.0)) else "Video Asli"
                                        formats.add(
                                            YouTubeVideoFormat(
                                                formatId = quality.replace(" ", "_"),
                                                label = "Video Asli MP4 ($quality)",
                                                resolution = quality,
                                                ext = "mp4",
                                                isAudioOnly = false,
                                                approximateSize = sizeStr,
                                                directUrl = streamUrl
                                            )
                                        )
                                    }
                                }
                            }

                            // Also extract audio
                            val audioStreams = json.optJSONArray("audioStreams")
                            if (audioStreams != null && audioStreams.length() > 0) {
                                val firstAudio = audioStreams.getJSONObject(0)
                                val aUrl = firstAudio.optString("url", "")
                                val aExt = firstAudio.optString("format", "m4a").lowercase()
                                if (aUrl.isNotBlank()) {
                                    formats.add(
                                        YouTubeVideoFormat(
                                            formatId = "audio",
                                            label = "Audio Asli (High Quality)",
                                            resolution = "Audio Stream",
                                            ext = if (aExt.contains("mp3")) "mp3" else "m4a",
                                            isAudioOnly = true,
                                            approximateSize = "Musik Jernih",
                                            directUrl = aUrl
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                if (formats.isNotEmpty()) break
            }

            // Step 3: Try Invidious instances if Piped returned empty
            if (formats.isEmpty()) {
                val invidiousInstances = listOf(
                    "https://inv.nadeko.net",
                    "https://invidious.jing.rocks",
                    "https://invidious.nerdvpn.de",
                    "https://iv.melmac.space"
                )

                for (instance in invidiousInstances) {
                    try {
                        val apiReq = Request.Builder()
                            .url("$instance/api/v1/videos/$videoId")
                            .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                            .build()
                        client.newCall(apiReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val respStr = resp.body?.string().orEmpty()
                                val json = JSONObject(respStr)
                                val formatStreams = json.optJSONArray("formatStreams")
                                if (formatStreams != null && formatStreams.length() > 0) {
                                    for (i in 0 until formatStreams.length()) {
                                        val item = formatStreams.getJSONObject(i)
                                        val streamUrl = item.optString("url", "")
                                        val quality = item.optString("qualityLabel", "720p")
                                        val container = item.optString("container", "mp4")
                                        if (streamUrl.isNotBlank() && container.contains("mp4", ignoreCase = true)) {
                                            val fullUrl = if (streamUrl.startsWith("/")) "$instance$streamUrl" else streamUrl
                                            formats.add(
                                                YouTubeVideoFormat(
                                                    formatId = quality.replace(" ", "_"),
                                                    label = "MP4 Video ($quality)",
                                                    resolution = quality,
                                                    ext = "mp4",
                                                    isAudioOnly = false,
                                                    approximateSize = item.optString("size", "Video HD"),
                                                    directUrl = fullUrl
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    if (formats.isNotEmpty()) break
                }
            }

            // Step 4: Try Cobalt API
            if (formats.isEmpty()) {
                val cobaltEndpoints = listOf(
                    "https://api.cobalt.tools",
                    "https://co.wuk.sh"
                )
                for (endpoint in cobaltEndpoints) {
                    try {
                        val payload = JSONObject().apply {
                            put("url", "https://www.youtube.com/watch?v=$videoId")
                            put("videoQuality", "720")
                        }
                        val cobaltReq = Request.Builder()
                            .url("$endpoint/")
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                            .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                            .build()

                        client.newCall(cobaltReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string().orEmpty()
                                val json = JSONObject(body)
                                val streamUrl = json.optString("url", "")
                                if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                                    formats.add(
                                        YouTubeVideoFormat(
                                            formatId = "720p",
                                            label = "MP4 Video HD (720p)",
                                            resolution = "1280x720",
                                            ext = "mp4",
                                            isAudioOnly = false,
                                            approximateSize = "Video HD",
                                            directUrl = streamUrl
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    if (formats.isNotEmpty()) break
                }
            }

            // Step 5: Try loader.to conversion polling
            if (formats.isEmpty()) {
                val direct720 = resolveLoaderToDirectUrl(videoId, "720")
                val direct360 = resolveLoaderToDirectUrl(videoId, "360")
                val directMp3 = resolveLoaderToDirectUrl(videoId, "mp3")

                if (!direct720.isNullOrBlank()) {
                    formats.add(
                        YouTubeVideoFormat(
                            formatId = "720p",
                            label = "MP4 Video HD (720p)",
                            resolution = "1280x720",
                            ext = "mp4",
                            isAudioOnly = false,
                            approximateSize = "18 ~ 35 MB",
                            directUrl = direct720
                        )
                    )
                }

                if (!direct360.isNullOrBlank()) {
                    formats.add(
                        YouTubeVideoFormat(
                            formatId = "360p",
                            label = "MP4 Video SD (360p)",
                            resolution = "640x360",
                            ext = "mp4",
                            isAudioOnly = false,
                            approximateSize = "8 ~ 15 MB",
                            directUrl = direct360
                        )
                    )
                }

                if (!directMp3.isNullOrBlank()) {
                    formats.add(
                        YouTubeVideoFormat(
                            formatId = "mp3",
                            label = "Audio MP3 (320kbps High Quality)",
                            resolution = "Audio 320kbps",
                            ext = "mp3",
                            isAudioOnly = true,
                            approximateSize = "4 ~ 9 MB",
                            directUrl = directMp3
                        )
                    )
                }
            }

            // If no resolver returned real streams, fail honestly instead of
            // fabricating URLs that will 404 or hang.
            if (formats.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Tidak ada stream langsung yang dapat diverifikasi. YouTube sering membatasi akses dari aplikasi pihak ketiga. Coba lagi nanti atau gunakan tautan lain."
                    )
                )
            }

            val videoData = YouTubeVideoData(
                videoId = videoId,
                title = videoTitle,
                author = authorName,
                authorUrl = authorUrl,
                thumbnailUrl = resolvedThumbnail,
                durationSeconds = 0,
                formats = formats,
                directStreamUrl = formats.first().directUrl
            )

            Result.success(videoData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun resolveLoaderToDirectUrl(videoId: String, format: String): String? = withContext(Dispatchers.IO) {
        try {
            val startUrl = "https://loader.to/ajax/download.php?button=1&start=1&end=1&format=$format&url=${Uri.encode("https://www.youtube.com/watch?v=$videoId")}"
            val req = Request.Builder()
                .url(startUrl)
                .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                .build()

            var taskId: String? = null
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    if (json.optBoolean("success", false) || json.has("id")) {
                        taskId = json.optString("id", "").takeIf { it.isNotBlank() }
                    }
                }
            }

            if (taskId.isNullOrBlank()) return@withContext null

            for (i in 0 until 4) {
                delay(1000)
                val pollUrl = "https://loader.to/ajax/progress.php?id=$taskId"
                val pollReq = Request.Builder()
                    .url(pollUrl)
                    .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                    .build()
                client.newCall(pollReq).execute().use { pollResp ->
                    if (pollResp.isSuccessful) {
                        val body = pollResp.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val downloadUrl = json.optString("download_url", "")
                        if (downloadUrl.isNotBlank() && !downloadUrl.contains("null")) {
                            return@withContext downloadUrl
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Enqueues real-time in-app streaming download with 0-100% progress and speed counter.
     */
    fun enqueueDownload(
        context: Context,
        format: YouTubeVideoFormat,
        videoTitle: String,
        videoId: String
    ) {
        val safeTitle = videoTitle
            .replace("[^a-zA-Z0-9\\-_\\s]".toRegex(), "")
            .trim()
            .replace("\\s+".toRegex(), "_")
            .take(28)
            .ifEmpty { "YT_$videoId" }

        val mime = if (format.isAudioOnly) "audio/mpeg" else "video/mp4"
        val fileName = "${safeTitle}_${format.formatId}.${format.ext}"

        AppDownloadManager.startDownload(
            context = context,
            directUrl = format.directUrl,
            suggestedFileName = fileName,
            title = videoTitle.take(35),
            mimeType = mime
        )
    }

    fun openInYouTube(context: Context, videoId: String) {
        try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    fun shareVideo(context: Context, videoId: String, title: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Tonton video YouTube: $title\nhttps://youtu.be/$videoId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Video YouTube"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
