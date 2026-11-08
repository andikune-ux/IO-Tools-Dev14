package dev.andikune.iottools.tools.ucbrowser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.URLUtil
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
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONTokener
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

data class SniffedMediaItem(
    val url: String,
    val format: String,
    val title: String,
    val isStreamHls: Boolean,
    val approximateSize: String,
    val sourceDomain: String,
    val isDash: Boolean = false,
    val refererUrl: String = "",
    val fileExtension: String = "mp4"
)

data class SniffingResult(
    val targetUrl: String,
    val pageTitle: String,
    val mediaList: List<SniffedMediaItem>,
    val scanDurationMs: Long
)

object UcMediaSnifferService {

    private val client by lazy { VideoDownloadHelper.client }
    private val mainHandler = Handler(Looper.getMainLooper())

    const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private fun isPublicHost(rawHost: String): Boolean {
        val host = rawHost.lowercase()
        return host != "localhost" &&
            !host.endsWith(".local") &&
            !host.endsWith(".internal") &&
            !host.endsWith(".lan") &&
            host.contains('.') &&
            !host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) &&
            !host.contains(':')
    }

    fun normalizeTargetUrl(rawUrl: String): String? {
        val clean = rawUrl.trim()
        if (clean.isBlank()) return null
        val candidate = if ("://" in clean) clean else "https://$clean"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = uri.host ?: return null
        if (uri.userInfo != null || !isPublicHost(host)) return null
        return uri.toASCIIString()
    }

    fun classifyMediaUrl(rawUrl: String, refererUrl: String = "", contentType: String? = null): SniffedMediaItem? {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.userInfo != null || !isPublicHost(host)) return null
        val path = uri.path.orEmpty().lowercase()
        if (host.contains("doubleclick") || host.contains("googleads") ||
            host.contains("adservice") || path.contains("/analytics/") ||
            path.contains("/pixel/") || path.endsWith("pixel.gif")
        ) return null

        val normalizedMime = contentType.orEmpty().substringBefore(';').trim().lowercase()
        val extension = path.substringAfterLast('.', "").lowercase()
        val ext = when {
            extension in setOf("m3u8", "mpd", "mp4", "m4v", "mov", "webm", "flv") -> extension
            normalizedMime in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl") -> "m3u8"
            normalizedMime == "application/dash+xml" -> "mpd"
            normalizedMime == "video/mp4" -> "mp4"
            normalizedMime == "video/webm" -> "webm"
            normalizedMime == "video/quicktime" -> "mov"
            normalizedMime == "video/x-flv" -> "flv"
            normalizedMime == "video/x-m4v" -> "m4v"
            else -> return null
        }
        val (format, isHls, isDash) = when (ext) {
            "m3u8" -> Triple("M3U8 (HLS)", true, false)
            "mpd" -> Triple("MPD (DASH)", false, true)
            "mp4" -> Triple("MP4 Video", false, false)
            "m4v" -> Triple("M4V Video", false, false)
            "mov" -> Triple("MOV Video", false, false)
            "webm" -> Triple("WEBM Video", false, false)
            "flv" -> Triple("FLV Video", false, false)
            else -> return null
        }
        val name = URLUtil.guessFileName(rawUrl, null, null)
            .takeIf { it.isNotBlank() && !it.endsWith(".bin", true) }
            ?: "Video.$ext"

        return SniffedMediaItem(
            url = rawUrl,
            format = format,
            title = name,
            isStreamHls = isHls,
            approximateSize = when {
                isHls -> "HLS manifest"
                isDash -> "DASH manifest"
                else -> "Direct media file"
            },
            sourceDomain = host,
            isDash = isDash,
            refererUrl = refererUrl,
            fileExtension = ext
        )
    }

    /**
     * Static HTML scan. Dynamic/player requests are captured by createCaptureWebView().
     * Only direct progressive media and HLS/DASH manifest URLs are returned.
     */
    suspend fun sniffWebpage(rawUrl: String): Result<SniffingResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        runCatching {
            val urlString = normalizeTargetUrl(rawUrl)
                ?: throw IllegalArgumentException("URL tidak valid. Gunakan alamat HTTP atau HTTPS publik.")
            val request = Request.Builder()
                .url(urlString)
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: Gagal membuka website.")
                }
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (contentType.startsWith("video/") ||
                    contentType.contains("mpegurl") ||
                    contentType.contains("dash+xml")
                ) {
                    val direct = classifyMediaUrl(urlString, urlString)
                        ?: throw IllegalStateException("Respons berupa media, tetapi format URL tidak didukung.")
                    return@withContext Result.success(
                        SniffingResult(
                            targetUrl = urlString,
                            pageTitle = "Direct Media",
                            mediaList = listOf(direct),
                            scanDurationMs = System.currentTimeMillis() - startTime
                        )
                    )
                }
                response.body?.string().orEmpty()
            }

            val titleMatcher = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)
            val pageTitle = titleMatcher?.groupValues?.getOrNull(1)
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Web Video Source"

            val candidates = linkedSetOf<String>()
            val patterns = listOf(
                Regex("""<(?:video|source)[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""(https?://[^"'\s<>]+\.(?:mp4|m3u8|mpd|webm|flv|m4v|mov)(?:\?[^"'\s<>]*)?)""", RegexOption.IGNORE_CASE),
                Regex("""(https?://[^"'\s<>]+\.(?:mp4|m3u8|mpd|webm)(?:\?[^"'\s<>]*)?)""", RegexOption.IGNORE_CASE),
                Regex("""(?:file|url|source|video_url)["']?\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            )
            patterns.forEach { pattern ->
                pattern.findAll(html).forEach { match ->
                    val candidate = match.groupValues.getOrNull(1)
                        ?.replace("\\/", "/")
                        ?.replace("&amp;", "&")
                        ?.trim()
                        ?: return@forEach
                    resolveAbsoluteUrl(urlString, candidate)?.let(candidates::add)
                }
            }

            val media = candidates.mapNotNull { classifyMediaUrl(it, urlString) }.distinctBy { it.url }
            if (media.isEmpty()) {
                throw IllegalStateException(
                    "Belum ada URL media langsung di HTML awal. Buka player di browser tertanam, tekan Play, lalu tunggu permintaan media terdeteksi."
                )
            }

            SniffingResult(
                targetUrl = urlString,
                pageTitle = pageTitle,
                mediaList = media,
                scanDurationMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Visible WebView capture: catches media requests made after JavaScript/player activity.
     * It does not inspect blob contents, decrypt DRM, or export cookies.
     */
    fun createCaptureWebView(
        context: Context,
        targetUrl: String,
        onPageTitle: (String) -> Unit,
        onMediaDetected: (SniffedMediaItem) -> Unit
    ): WebView {
        val normalizedUrl = normalizeTargetUrl(targetUrl)
            ?: throw IllegalArgumentException("URL target tidak valid.")

        val activePageUrl = AtomicReference(normalizedUrl)
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = MOBILE_USER_AGENT
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webChromeClient = WebChromeClient()

            fun reportCandidate(candidate: String, referer: String) {
                val item = classifyMediaUrl(candidate, referer) ?: return
                mainHandler.post { onMediaDetected(item) }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url?.toString()
                    return target == null || normalizeTargetUrl(target) == null
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val response = super.shouldInterceptRequest(view, request)
                    request?.takeIf { it.method.equals("GET", true) }?.let {
                        val referer = it.requestHeaders["Referer"] ?: activePageUrl.get()
                        val item = classifyMediaUrl(
                            rawUrl = it.url.toString(),
                            refererUrl = referer,
                            contentType = response?.mimeType
                        )
                        if (item != null) mainHandler.post { onMediaDetected(item) }
                    }
                    return response
                }

                override fun onLoadResource(view: WebView?, url: String?) {
                    if (!url.isNullOrBlank()) reportCandidate(url, view?.url ?: normalizedUrl)
                    super.onLoadResource(view, url)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    if (!url.isNullOrBlank()) activePageUrl.set(url)
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    onPageTitle(view?.title?.takeIf { it.isNotBlank() } ?: "Web Video Source")
                    val currentPageUrl = activePageUrl.get()
                    view?.evaluateJavascript(
                        "JSON.stringify(Array.from(document.querySelectorAll('video,video source,source')).map(e => e.currentSrc || e.src).filter(Boolean))"
                    ) { value ->
                        val decoded = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull()
                            ?: return@evaluateJavascript
                        val urls = runCatching { JSONArray(decoded) }.getOrNull() ?: return@evaluateJavascript
                        for (index in 0 until urls.length()) {
                            val candidate = urls.optString(index)
                            if (candidate.isNotBlank() && !candidate.startsWith("blob:")) {
                                reportCandidate(candidate, currentPageUrl)
                            }
                        }
                    }
                    super.onPageFinished(view, url)
                }
            }
            loadUrl(normalizedUrl)
        }
    }

    private fun resolveAbsoluteUrl(baseUrl: String, candidate: String): String? {
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val resolved = runCatching { URI(baseUrl).resolve(uri) }.getOrNull() ?: return null
        return resolved.toASCIIString().takeIf { classifyMediaUrl(it, baseUrl) != null }
    }

    /**
     * Progressive files use the shared streaming downloader. HLS/DASH manifests
     * are passed to yt-dlp/FFmpeg so the manifest itself is never saved as an MP4.
     */
    fun enqueueDownload(context: Context, item: SniffedMediaItem) {
        if (!item.isStreamHls && !item.isDash) {
            val mime = when (item.fileExtension.lowercase()) {
                "mp4" -> "video/mp4"
                "m4v" -> "video/x-m4v"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                "flv" -> "video/x-flv"
                else -> {
                    Toast.makeText(context, "Format media tidak didukung.", Toast.LENGTH_LONG).show()
                    return
                }
            }
            val safeName = item.title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            AppDownloadManager.startDownload(
                context = context,
                directUrl = item.url,
                suggestedFileName = safeName,
                title = safeName.take(35),
                mimeType = mime,
                customHeaders = buildMap {
                    put("User-Agent", MOBILE_USER_AGENT)
                    if (item.refererUrl.isNotBlank()) put("Referer", item.refererUrl)
                }
            )
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val processId = "uc-media-${System.currentTimeMillis()}"
            val outputDir = File(context.cacheDir, "uc-media-downloads/$processId").apply { mkdirs() }
            val result = runCatching {
                val outputTemplate = File(outputDir, "%(title).100B [%(id)s].%(ext)s").absolutePath
                val request = YoutubeDLRequest(item.url).apply {
                    addOption("-o", outputTemplate)
                    addOption("--no-warnings")
                    addOption("--no-playlist")
                    addOption("--newline")
                    addOption("--no-write-thumbnail")
                    addOption("--no-write-info-json")
                    addOption("--no-write-subs")
                    addOption("--no-write-auto-subs")
                    addOption("--merge-output-format", "mp4")
                    addOption("--user-agent", MOBILE_USER_AGENT)
                    if (item.refererUrl.isNotBlank()) addOption("--referer", item.refererUrl)
                }
                YoutubeDL.getInstance().execute(request, processId)
                val downloaded = outputDir.listFiles()
                    ?.filter { it.isFile && it.length() > 0L && !it.name.endsWith(".part") }
                    ?.maxByOrNull { it.lastModified() }
                    ?: throw IllegalStateException("Engine tidak menghasilkan file media.")
                val mime = when (downloaded.extension.lowercase()) {
                    "mp4", "m4v" -> "video/mp4"
                    "webm" -> "video/webm"
                    "mkv" -> "video/x-matroska"
                    else -> throw IllegalStateException("Format hasil ${downloaded.extension} tidak didukung.")
                }
                val uri = downloaded.inputStream().use { input ->
                    MediaStoreSaver.saveStream(context, downloaded.name, mime, input)
                } ?: throw IllegalStateException("Gagal menyimpan media ke Download/IO-Tools.")
                downloaded.delete()
                uri
            }
            outputDir.deleteRecursively()
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    Toast.makeText(context, "Unduhan stream selesai.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(context, "Unduhan stream gagal: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
