package dev.andikune.iottools.tools.ucbrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import android.widget.Toast
import dev.andikune.iottools.tools.common.VideoDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI
import java.net.URL
import java.util.regex.Pattern

data class SniffedMediaItem(
    val url: String,
    val format: String, // MP4, M3U8, WEBM, FLV, MP3
    val title: String,
    val isStreamHls: Boolean,
    val approximateSize: String,
    val sourceDomain: String
)

data class SniffingResult(
    val targetUrl: String,
    val pageTitle: String,
    val mediaList: List<SniffedMediaItem>,
    val scanDurationMs: Long
)

object UcMediaSnifferService {

    private val client by lazy { VideoDownloadHelper.client }

    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; U; Android 14; id-id; Pixel 8 Pro Build/UQ1A) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0 Mobile Safari/537.36 UCBrowser/13.4.0.1306 Mobile"

    /**
     * Crawls target webpage and sniffs out hidden genuine video streams (.mp4, .m3u8, .webm).
     * Filters out tracking scripts, ads, and HTML wrappers to ensure only playable videos are listed.
     */
    suspend fun sniffWebpage(rawUrl: String): Result<SniffingResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            var urlString = rawUrl.trim()
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "https://$urlString"
            }

            val parsedUri = try {
                URI(urlString)
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalArgumentException("Format URL tidak valid: $rawUrl"))
            }

            val host = parsedUri.host ?: "unknown"

            // 1. Fetch HTML page source with UC Browser mobile headers
            val request = Request.Builder()
                .url(urlString)
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,video/*;q=0.8,*/*;q=0.5")
                .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("HTTP ${response.code}: Gagal membuka website."))
            }

            val contentType = response.header("Content-Type", "") ?: ""
            // If the user entered a direct video URL directly into the sniffer
            if (contentType.startsWith("video/") || urlString.endsWith(".mp4") || urlString.endsWith(".m3u8")) {
                val directFormat = if (urlString.contains(".m3u8")) "M3U8" else "MP4"
                val singleList = listOf(
                    SniffedMediaItem(
                        url = urlString,
                        format = directFormat,
                        title = URLUtil.guessFileName(urlString, null, contentType),
                        isStreamHls = directFormat == "M3U8",
                        approximateSize = "Direct Stream Video",
                        sourceDomain = host
                    )
                )
                return@withContext Result.success(
                    SniffingResult(
                        targetUrl = urlString,
                        pageTitle = "Direct Video Stream",
                        mediaList = singleList,
                        scanDurationMs = System.currentTimeMillis() - startTime
                    )
                )
            }

            val html = response.body?.string().orEmpty()

            // 2. Extract Page Title
            var pageTitle = "Web Video Source"
            val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
            if (titleMatcher.find()) {
                pageTitle = titleMatcher.group(1)?.replace("\\s+".toRegex(), " ")?.trim() ?: pageTitle
            }

            // 3. Regex Sniffing Patterns for media streams
            val detectedUrls = mutableSetOf<String>()
            val patterns = listOf(
                // Video tags: <video src="...">, <source src="...">
                Pattern.compile("<(?:video|source)[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
                // Direct file extensions in JS or HTML
                Pattern.compile("(https?://[^\"'\\s<>]+\\.(?:mp4|m3u8|webm|flv|m4v)(?:\\?[^\"'\\s<>]*)?)", Pattern.CASE_INSENSITIVE),
                // Escaped JSON URLs (https:\/\/...mp4)
                Pattern.compile("(https?:\\\\/\\\\/[^\"'\\s<>]+\\.(?:mp4|m3u8|webm)(?:\\\\?[^\"'\\s<>]*)?)", Pattern.CASE_INSENSITIVE),
                // Common video player configs (file: "...", video_url: "...")
                Pattern.compile("(?:file|url|source|video_url)[\"']?\\s*:\\s*[\"'](https?://[^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            )

            for (pattern in patterns) {
                val matcher = pattern.matcher(html)
                while (matcher.find()) {
                    val rawMatch = matcher.group(1) ?: continue
                    val cleanMatch = rawMatch.replace("\\/", "/").trim()
                    val absoluteUrl = resolveAbsoluteUrl(urlString, cleanMatch)
                    if (absoluteUrl != null && isGenuineVideoUrl(absoluteUrl)) {
                        detectedUrls.add(absoluteUrl)
                    }
                }
            }

            // Also check for embedded video iframes
            val iframeMatcher = Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
            while (iframeMatcher.find()) {
                val rawSrc = iframeMatcher.group(1) ?: continue
                val cleanSrc = rawSrc.replace("\\/", "/").trim()
                val absSrc = resolveAbsoluteUrl(urlString, cleanSrc)
                if (absSrc != null && (absSrc.contains("youtube.com/embed") || absSrc.contains("player.vimeo.com") || absSrc.contains("dailymotion.com/embed"))) {
                    detectedUrls.add(absSrc)
                }
            }

            val mediaList = detectedUrls.mapIndexed { index, mediaUrl ->
                val format = when {
                    mediaUrl.contains(".m3u8") -> "M3U8 (HLS)"
                    mediaUrl.contains(".mp4") -> "MP4 Video"
                    mediaUrl.contains(".webm") -> "WEBM Video"
                    mediaUrl.contains("youtube.com") -> "YouTube Video Embed"
                    else -> "VIDEO STREAM"
                }

                val mediaHost = try { URI(mediaUrl).host ?: host } catch (_: Exception) { host }
                var guessedName = URLUtil.guessFileName(mediaUrl, null, null)
                if (guessedName.endsWith(".bin") || guessedName.length < 5) {
                    guessedName = "Video_${index + 1}.mp4"
                }

                SniffedMediaItem(
                    url = mediaUrl,
                    format = format,
                    title = guessedName,
                    isStreamHls = mediaUrl.contains(".m3u8"),
                    approximateSize = if (format.contains("M3U8")) "Live HLS Stream" else "Video Asli",
                    sourceDomain = mediaHost
                )
            }

            if (mediaList.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Tidak ditemukan stream video asli (.mp4, .m3u8) pada halaman ini. Pastikan halaman memiliki player video aktif atau coba link video langsung."
                    )
                )
            }

            val result = SniffingResult(
                targetUrl = urlString,
                pageTitle = pageTitle,
                mediaList = mediaList,
                scanDurationMs = System.currentTimeMillis() - startTime
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isGenuineVideoUrl(url: String): Boolean {
        val lower = url.lowercase()

        // Discard ad networks and tracking pixels
        if (lower.contains("doubleclick") ||
            lower.contains("googleads") ||
            lower.contains("facebook.com/tr") ||
            lower.contains("analytics") ||
            lower.contains("pixel") ||
            lower.contains("adservice")
        ) {
            return false
        }

        return lower.contains(".mp4") ||
                lower.contains(".m3u8") ||
                lower.contains(".webm") ||
                lower.contains(".flv") ||
                lower.contains(".m4v") ||
                lower.contains("googlevideo.com/videoplayback") ||
                lower.contains("youtube.com/embed")
    }

    private fun resolveAbsoluteUrl(baseUrl: String, candidate: String): String? {
        return try {
            when {
                candidate.startsWith("http://") || candidate.startsWith("https://") -> candidate
                candidate.startsWith("//") -> "https:$candidate"
                candidate.startsWith("/") -> {
                    val base = URL(baseUrl)
                    "${base.protocol}://${base.host}${if (base.port != -1) ":${base.port}" else ""}$candidate"
                }
                else -> {
                    val base = URL(baseUrl)
                    val basePath = base.path.substringBeforeLast('/', "")
                    "${base.protocol}://${base.host}${if (base.port != -1) ":${base.port}" else ""}$basePath/$candidate"
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Downloads genuine sniffed media via AppDownloadManager with 0-100% progress.
     */
    fun enqueueDownload(context: Context, item: SniffedMediaItem) {
        val ext = if (item.isStreamHls) "m3u8" else "mp4"
        val mime = if (item.isStreamHls) "application/x-mpegURL" else "video/mp4"

        val safeName = item.title
            .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            .ifEmpty { "Video_${System.currentTimeMillis()}" }
        val finalFileName = if (safeName.endsWith(".$ext", ignoreCase = true)) safeName else "$safeName.$ext"

        dev.andikune.iottools.tools.common.AppDownloadManager.startDownload(
            context = context,
            directUrl = item.url,
            suggestedFileName = finalFileName,
            title = finalFileName.take(35),
            mimeType = mime
        )
    }
}
