package dev.andikune.iottools.tools.common

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.InetAddress
import java.net.UnknownHostException
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
            .dns { hostname ->
                val resolved = InetAddress.getAllByName(hostname).filter(::isPublicAddress)
                if (resolved.isEmpty()) throw UnknownHostException("Hostname tidak mengarah ke IP publik.")
                resolved
            }
            .build()
    }

    private val noRedirectClient by lazy {
        client.newBuilder()
            .followRedirects(false)
            .build()
    }

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    internal fun isSecurePublicMediaUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank() || rawUrl.length > 8192 || rawUrl.any { it.isISOControl() }) return false
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        val rawHost = uri.host?.lowercase() ?: return false
        if (rawHost.endsWith('.')) return false
        val host = rawHost
        if (uri.scheme?.lowercase() != "https" ||
            (uri.port != -1 && uri.port !in 1..65535) || host.length > 253
        ) return false
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host.endsWith(".internal") || host.endsWith(".lan") || host.endsWith(".home.arpa") ||
            host.contains(':') || host.matches(Regex("^[0-9.]+$")) ||
            host.split('.').any { label ->
                label.isBlank() || label.length > 63 ||
                    !label.matches(Regex("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$"))
            }
        ) return false
        return true
    }

    internal fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress
        ) return false

        val bytes = address.address
        if (bytes.size == 4) {
            val a = bytes[0].toInt() and 0xff
            val b = bytes[1].toInt() and 0xff
            return when {
                a == 0 || a == 10 || a == 127 || a >= 224 -> false
                a == 100 && b in 64..127 -> false
                a == 169 && b == 254 -> false
                a == 172 && b in 16..31 -> false
                a == 192 && b == 168 -> false
                a == 192 && b == 0 -> false
                a == 198 && b in 18..19 -> false
                a == 198 && b == 51 -> false
                a == 203 && b == 0 -> false
                else -> true
            }
        }
        if (bytes.size == 16) {
            val mapped = bytes.take(10).all { it.toInt() == 0 } &&
                (bytes[10].toInt() and 0xff) == 0xff &&
                (bytes[11].toInt() and 0xff) == 0xff
            if (mapped) return isPublicAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            // Only global-unicast 2000::/3 addresses are accepted for remote media.
            if (first !in 0x20..0x3f) return false
            if ((first and 0xfe) == 0xfc) return false
            if (first == 0xfe && (second and 0xc0) == 0x80) return false
            if (first == 0x20 && second == 0x01) {
                val third = bytes[2].toInt() and 0xff
                val fourth = bytes[3].toInt() and 0xff
                // IETF protocol assignments (2001:0000::/23) and documentation (2001:db8::/32).
                if ((third == 0x00 && fourth <= 0x01) ||
                    (third == 0x0d && fourth == 0xb8)
                ) return false
            }
        }
        return true
    }

    private fun executeWithSecureRedirects(initialRequest: Request): okhttp3.Response {
        var request = initialRequest
        val redirectCodes = setOf(300, 301, 302, 303, 307, 308)
        for (redirectCount in 0..5) {
            val response = noRedirectClient.newCall(request).execute()
            if (response.code !in redirectCodes) return response

            val location = response.header("Location")
            val nextUrl = location?.let { response.request.url.resolve(it) }
            if (nextUrl == null || !isSecurePublicMediaUrl(nextUrl.toString())) {
                response.close()
                throw IllegalStateException("Redirect media mengarah ke URL yang tidak aman.")
            }
            if (redirectCount == 5) {
                response.close()
                throw IllegalStateException("Terlalu banyak redirect saat memverifikasi media.")
            }

            val previous = response.request.url
            val changedOrigin = previous.scheme != nextUrl.scheme ||
                previous.host != nextUrl.host || previous.port != nextUrl.port
            response.close()
            request = request.newBuilder()
                .url(nextUrl)
                .apply {
                    if (changedOrigin) {
                        request.headers.names()
                            .filterNot { it.equals("User-Agent", true) || it.equals("Accept", true) || it.equals("Range", true) }
                            .forEach(::removeHeader)
                    }
                }
                .build()
        }
        throw IllegalStateException("Gagal mengikuti redirect media.")
    }

    /**
     * Probes a bounded prefix and validates media signatures. HTML/JSON error
     * pages and insecure or local targets are never reported as video streams.
     */
    suspend fun verifyDirectVideoStream(url: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            require(isSecurePublicMediaUrl(url)) {
                "URL stream harus HTTPS publik dan tidak boleh berisi kredensial atau alamat lokal."
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "video/*,application/vnd.apple.mpegurl,application/x-mpegURL,application/dash+xml,application/octet-stream;q=0.8")
                .header("Range", "bytes=0-1023")
                .build()

            executeWithSecureRedirects(request).use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Server mengembalikan HTTP ${response.code}.")
                }
                if (!isSecurePublicMediaUrl(response.request.url.toString())) {
                    throw IllegalStateException("Redirect mengarah ke URL yang tidak aman.")
                }

                val contentType = response.header("Content-Type").orEmpty()
                    .substringBefore(';')
                    .trim()
                    .lowercase()
                if (contentType.startsWith("text/") || contentType.contains("json") ||
                    contentType.contains("xhtml")
                ) {
                    throw IllegalStateException("Server mengembalikan teks/JSON/HTML, bukan stream video.")
                }

                val header = response.body?.byteStream()?.use { readAtMost(it, 512) }
                    ?: throw IllegalStateException("Server tidak mengirim body media.")
                if (header.isEmpty()) throw IllegalStateException("Body media kosong.")

                val headText = String(header, Charsets.ISO_8859_1).trimStart()
                val isHtml = headText.startsWith("<!doctype html", true) ||
                    headText.startsWith("<html", true) ||
                    headText.contains("<script", true)
                if (isHtml) throw IllegalStateException("Respon berisi HTML, bukan media.")

                val valid = isValidMediaSignature(contentType, header)
                if (!valid) {
                    throw IllegalStateException("Signature body tidak cocok dengan format video/manifest yang didukung.")
                }
                true
            }
        }
    }

    internal fun isValidMediaSignature(contentType: String, header: ByteArray): Boolean {
        if (header.isEmpty()) return false
        val normalizedType = contentType.substringBefore(';').trim().lowercase()
        val headText = String(header, Charsets.ISO_8859_1).trimStart()
        val isHtml = headText.startsWith("<!doctype html", true) ||
            headText.startsWith("<html", true) ||
            headText.contains("<script", true)
        if (isHtml || normalizedType.startsWith("text/") || normalizedType.contains("json") ||
            normalizedType.contains("xhtml")
        ) return false

        return when {
            normalizedType == "video/mp4" || normalizedType == "video/quicktime" ||
                normalizedType == "video/x-m4v" ->
                AppDownloadManager.matchesFileSignature("video/mp4", header)
            normalizedType == "video/webm" || normalizedType == "video/x-matroska" ->
                AppDownloadManager.matchesFileSignature("video/webm", header)
            normalizedType == "video/x-flv" ->
                AppDownloadManager.matchesFileSignature("video/x-flv", header)
            normalizedType.contains("mpegurl") -> headText.startsWith("#EXTM3U", true)
            normalizedType.contains("dash+xml") -> headText.contains("<MPD", true)
            normalizedType == "application/octet-stream" || normalizedType.isBlank() ->
                AppDownloadManager.matchesFileSignature("video/mp4", header) ||
                    AppDownloadManager.matchesFileSignature("video/webm", header) ||
                    AppDownloadManager.matchesFileSignature("video/x-flv", header) ||
                    headText.startsWith("#EXTM3U", true) ||
                    headText.contains("<MPD", true)
            else -> false
        }
    }

    private fun readAtMost(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        while (output.size() < maxBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
            if (read < 0) break
            if (read == 0) {
                val single = input.read()
                if (single < 0) break
                output.write(single)
                continue
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    fun enqueueNativeVideoDownload(
        context: Context,
        directUrl: String,
        suggestedFileName: String,
        title: String,
        mimeType: String = "video/mp4"
    ) {
        AppDownloadManager.startDownload(
            context = context,
            directUrl = directUrl,
            suggestedFileName = suggestedFileName,
            title = title,
            mimeType = mimeType
        )
    }
}
