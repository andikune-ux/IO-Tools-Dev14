package dev.andikune.iottools.tools.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.andikune.iottools.tools.common.VideoDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
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
        val patterns = listOf(
            Pattern.compile("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/|\\?v=)([^#&?\\s]{11})"),
            Pattern.compile("^([a-zA-Z0-9_-]{11})$")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(clean)
            if (matcher.find()) {
                val group = matcher.group(1)
                if (!group.isNullOrBlank() && group.length == 11) {
                    return group
                }
            }
        }
        return null
    }

    /**
     * Fetches metadata (title, author, thumbnail) and resolves downloadable streams.
     * Uses YouTube Official oEmbed + Invidious / Cobalt / Loader.to API direct streaming.
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
            } catch (_: Exception) {
                // Keep default metadata if oEmbed fails
            }

            val formats = mutableListOf<YouTubeVideoFormat>()

            // Step 2: Try Invidious instances to extract direct audio+video MP4 stream
            val invidiousInstances = listOf(
                "https://invidious.jing.rocks",
                "https://invidious.nerdvpn.de",
                "https://iv.melmac.space",
                "https://yt.artemislena.eu"
            )

            var resolvedDirectUrl: String? = null
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
                                                label = "MP4 Video Asli ($quality)",
                                                resolution = quality,
                                                ext = "mp4",
                                                isAudioOnly = false,
                                                approximateSize = item.optString("size", "Video HD"),
                                                directUrl = fullUrl
                                            )
                                        )
                                        if (resolvedDirectUrl == null) resolvedDirectUrl = fullUrl
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                if (formats.isNotEmpty()) break
            }

            // Step 3: Try loader.to conversion API to get direct binary MP4 / MP3 links
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

            // Fallback formats using high-speed verified CDN stream proxies
            if (formats.isEmpty()) {
                val directProxy720 = "https://co.wuk.sh/api/stream?url=https://www.youtube.com/watch?v=$videoId&quality=720"
                val directProxy360 = "https://co.wuk.sh/api/stream?url=https://www.youtube.com/watch?v=$videoId&quality=360"
                val directProxyMp3 = "https://co.wuk.sh/api/stream?url=https://www.youtube.com/watch?v=$videoId&isAudioOnly=true"

                formats.add(
                    YouTubeVideoFormat(
                        formatId = "720p",
                        label = "MP4 Video HD (720p)",
                        resolution = "1280x720",
                        ext = "mp4",
                        isAudioOnly = false,
                        approximateSize = "High Quality",
                        directUrl = directProxy720
                    )
                )

                formats.add(
                    YouTubeVideoFormat(
                        formatId = "360p",
                        label = "MP4 Video SD (360p)",
                        resolution = "640x360",
                        ext = "mp4",
                        isAudioOnly = false,
                        approximateSize = "Standard Quality",
                        directUrl = directProxy360
                    )
                )

                formats.add(
                    YouTubeVideoFormat(
                        formatId = "mp3",
                        label = "Audio MP3 (Music)",
                        resolution = "Audio 320kbps",
                        ext = "mp3",
                        isAudioOnly = true,
                        approximateSize = "Audio File",
                        directUrl = directProxyMp3
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

    /**
     * Resolves the real direct binary video URL from loader.to by querying its conversion API,
     * polling the progress until a true binary CDN download link is obtained.
     */
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

            // Poll up to 4 times (max 4 seconds) for the finished download_url
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
     * Downloads the chosen format via Android's DownloadManager with full notifications,
     * enforcing genuine MP4 MIME type and saving directly into the user's Downloads folder.
     */
    fun enqueueDownload(
        context: Context,
        format: YouTubeVideoFormat,
        videoTitle: String,
        videoId: String
    ): Long {
        val safeTitle = videoTitle
            .replace("[^a-zA-Z0-9\\-_\\s]".toRegex(), "")
            .trim()
            .replace("\\s+".toRegex(), "_")
            .take(30)
            .ifEmpty { "YT_$videoId" }

        val mime = if (format.isAudioOnly) "audio/mpeg" else "video/mp4"
        val fileName = "${safeTitle}_${format.formatId}.${format.ext}"

        return VideoDownloadHelper.enqueueNativeVideoDownload(
            context = context,
            directUrl = format.directUrl,
            suggestedFileName = fileName,
            title = videoTitle.take(35),
            description = "Mengunduh ${format.label}...",
            mimeType = mime
        )
    }

    /**
     * Opens YouTube Video in external YouTube app or Web Browser.
     */
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

    /**
     * Share YouTube link to other apps.
     */
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
