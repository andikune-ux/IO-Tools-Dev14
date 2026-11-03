package dev.andikune.iottools.tools.ucbrowser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
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

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; U; Android 14; id-id; Pixel 8 Pro Build/UQ1A) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0 Mobile Safari/537.36 UCBrowser/13.4.0.1306 Mobile"

    /**
     * Crawls target webpage and sniffs out hidden media URLs (.mp4, .m3u8, .webm, audio).
     * Replicates the classic UC Browser Sniffer engine by parsing HTML, <video> tags,
     * JavaScript sources, JSON payloads, and embedded iframes.
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
                        approximateSize = "Direct Stream",
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
                    if (absoluteUrl != null && isMediaUrl(absoluteUrl)) {
                        detectedUrls.add(absoluteUrl)
                    }
                }
            }

            // Also check for embedded YouTube / Vimeo / DailyMotion iframes
            val iframeMatcher = Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
            while (iframeMatcher.find()) {
                val rawSrc = iframeMatcher.group(1) ?: continue
                val cleanSrc = rawSrc.replace("\\/", "/").trim()
                val absSrc = resolveAbsoluteUrl(urlString, cleanSrc)
                if (absSrc != null && (absSrc.contains("youtube.com") || absSrc.contains("player.vimeo.com") || absSrc.contains("dailymotion.com"))) {
                    detectedUrls.add(absSrc)
                }
            }

            val mediaList = detectedUrls.mapIndexed { index, mediaUrl ->
                val format = when {
                    mediaUrl.contains(".m3u8") -> "M3U8 (HLS)"
                    mediaUrl.contains(".mp4") -> "MP4"
                    mediaUrl.contains(".webm") -> "WEBM"
                    mediaUrl.contains("youtube.com") -> "YouTube Embed"
                    else -> "VIDEO STREAM"
                }

                val mediaHost = try { URI(mediaUrl).host ?: host } catch (_: Exception) { host }
                val guessedName = URLUtil.guessFileName(mediaUrl, null, null)

                SniffedMediaItem(
                    url = mediaUrl,
                    format = format,
                    title = if (guessedName.endsWith(".bin") || guessedName.length < 5) "Media Stream #${index + 1}" else guessedName,
                    isStreamHls = mediaUrl.contains(".m3u8"),
                    approximateSize = if (format.contains("M3U8")) "Live HLS Stream" else "Direct Video",
                    sourceDomain = mediaHost
                )
            }

            if (mediaList.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Tidak ditemukan media (.mp4, .m3u8, video tag) pada halaman ini. Pastikan halaman memiliki player video aktif atau coba link streaming langsung."
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

    private fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
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
     * Downloads sniffed media via Android's DownloadManager.
     */
    fun enqueueDownload(context: Context, item: SniffedMediaItem): Long {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: throw IllegalStateException("DownloadManager tidak tersedia.")

            val ext = if (item.isStreamHls) "m3u8" else "mp4"
            val safeName = item.title
                .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                .ifEmpty { "Media_${System.currentTimeMillis()}" }
            val finalFileName = if (safeName.endsWith(".$ext")) safeName else "$safeName.$ext"

            val uri = Uri.parse(item.url)
            val request = DownloadManager.Request(uri).apply {
                setTitle(finalFileName.take(35))
                setDescription("Mengunduh dari ${item.sourceDomain} (${item.format})...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName)
            }

            val id = downloadManager.enqueue(request)
            Toast.makeText(
                context,
                "✓ Memulai unduhan: $finalFileName\nDisimpan di folder Download.",
                Toast.LENGTH_LONG
            ).show()
            return id
        } catch (e: Exception) {
            // Fallback: Open in external browser/player
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Membuka link media via browser...", Toast.LENGTH_SHORT).show()
                return 0L
            } catch (err: Exception) {
                Toast.makeText(context, "Gagal mengunduh: ${err.message}", Toast.LENGTH_SHORT).show()
                return -1L
            }
        }
    }
}
