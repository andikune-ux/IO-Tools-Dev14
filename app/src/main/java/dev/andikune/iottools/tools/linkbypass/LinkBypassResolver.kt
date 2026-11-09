package dev.andikune.iottools.tools.linkbypass

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

internal const val MAX_REDIRECTS = 10
internal const val MAX_RESPONSE_BYTES = 512 * 1024
data class LinkResolution(val originalUrl: String, val destinationUrl: String, val redirectsFollowed: Int, val detail: String)
class LinkResolutionException(message: String) : Exception(message)

internal fun normalizeHttpUrl(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty() || value.length > 8192 || value.any { it.isISOControl() || it.isWhitespace() }) return null
    if (listOf("javascript:", "file:", "intent:", "content:").any { value.startsWith(it, true) }) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    if (scheme !in setOf("http", "https") || uri.rawUserInfo != null) return null
    val rawHost = uri.host?.removePrefix("[")?.removeSuffix("]") ?: return null
    val host = if (rawHost.contains(':')) rawHost.lowercase(Locale.ROOT) else
        runCatching { IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT) }.getOrNull() ?: return null
    if (host.isBlank() || host.length > 253 || (uri.port != -1 && uri.port !in setOf(80, 443))) return null
    // IP literals bypass OkHttp's DNS callback, so validate them before constructing a request.
    if (host.contains(':') || host.matches(Regex("[0-9.]+"))) {
        val literal = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
        if (!isPublicDestinationAddress(literal)) return null
    }
    val authorityHost = if (host.contains(':')) "[$host]" else host
    val port = if (uri.port == -1 || (scheme == "http" && uri.port == 80) || (scheme == "https" && uri.port == 443)) "" else ":${uri.port}"
    val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
    return URI("$scheme://$authorityHost$port$path" + (uri.rawQuery?.let { "?$it" } ?: "")).toASCIIString()
}

internal fun isPublicDestinationAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress) return false
    val bytes = address.address
    if (address is Inet4Address || bytes.size == 4) {
        val a = bytes[0].toInt() and 255
        val b = bytes[1].toInt() and 255
        val c = bytes[2].toInt() and 255
        return when {
            a == 0 || a == 10 || a == 127 || a >= 224 -> false
            a == 100 && b in 64..127 -> false
            a == 169 && b == 254 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 168 -> false
            a == 192 && b == 0 -> false
            a == 192 && b == 88 && c == 99 -> false
            a == 198 && b in 18..19 -> false
            a == 198 && b == 51 && c == 100 -> false
            a == 203 && b == 0 && c == 113 -> false
            else -> true
        }
    }
    if (address is Inet6Address || bytes.size == 16) {
        val mapped = bytes.take(10).all { it.toInt() == 0 } &&
            (bytes[10].toInt() and 255) == 255 && (bytes[11].toInt() and 255) == 255
        if (mapped) return isPublicDestinationAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
        val first = bytes[0].toInt() and 255
        val second = bytes[1].toInt() and 255
        if (first !in 0x20..0x3f) return false
        if (first == 0x20 && second == 0x01) {
            val third = bytes[2].toInt() and 255
            val fourth = bytes[3].toInt() and 255
            if ((third == 0 && fourth <= 1) || (third == 0x0d && fourth == 0xb8)) return false
        }
        return true
    }
    return false
}

internal fun extractRedirectParameter(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val accepted = setOf("url", "u", "target", "dest", "destination", "redirect", "redirect_url", "redirect_uri", "continue", "next", "out", "link", "to")
    for (part in (uri.rawQuery ?: return null).split("&")) {
        val keyRaw = part.substringBefore("=")
        val key = runCatching { URLDecoder.decode(keyRaw, "UTF-8") }.getOrDefault(keyRaw).lowercase(Locale.ROOT)
        if (key !in accepted) continue
        val raw = part.substringAfter("=", "")
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        val candidate = normalizeHttpUrl(decoded) ?: continue
        if (candidate != url) return candidate
    }
    return null
}

private fun metaAttribute(tag: String, name: String): String? {
    val pattern = Regex("(?is)\\b" + Regex.escape(name) + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
    val match = pattern.find(tag) ?: return null
    return (1..3).firstNotNullOfOrNull { index -> match.groups[index]?.value }
}

internal fun extractMetaRefresh(html: String, baseUrl: String): String? {
    if (html.length > MAX_RESPONSE_BYTES) return null
    val tag = Regex("""(?is)<meta\b[^>]*>""").findAll(html).firstOrNull { candidate ->
        metaAttribute(candidate.value, "http-equiv").equals("refresh", ignoreCase = true)
    }?.value ?: return null
    val content = metaAttribute(tag, "content")?.takeIf { it.length in 1..4096 } ?: return null
    val match = Regex("""(?is)(?:^|;)\s*url\s*=\s*(?:"([^"]*)"|'([^']*)'|(.+?))\\s*$""").find(content)
        ?: return null
    val target = (1..3).firstNotNullOfOrNull { index -> match.groups[index]?.value?.trim()?.takeIf(String::isNotEmpty) }
        ?: return null
    val resolved = runCatching { URI(baseUrl).resolve(target).toString() }.getOrNull() ?: return null
    return normalizeHttpUrl(resolved)
}

class LinkBypassResolver {
    suspend fun resolve(input: String): LinkResolution {
        val original = normalizeHttpUrl(input)
            ?: throw LinkResolutionException("URL tidak valid. Gunakan tautan HTTP atau HTTPS yang lengkap.")
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (addresses.isEmpty() || addresses.any { !isPublicDestinationAddress(it) }) {
                    throw java.net.UnknownHostException("Host mengarah ke IP privat, lokal, atau jaringan khusus.")
                }
                return addresses
            }
        }
        val client = OkHttpClient.Builder().dns(dns).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
        val visited = linkedSetOf<String>()
        var current = original
        var hops = 0
        while (true) {
            if (!visited.add(current)) throw LinkResolutionException("Loop redirect terdeteksi. Proses dihentikan.")
            val parameterTarget = extractRedirectParameter(current)
            if (parameterTarget != null) {
                current = parameterTarget
                hops++
                if (hops > MAX_REDIRECTS) throw LinkResolutionException("Melebihi batas $MAX_REDIRECTS redirect.")
                continue
            }
            val request = Request.Builder().url(current)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36 IO-Tools/1.4")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.5")
                .header("Range", "bytes=0-${MAX_RESPONSE_BYTES - 1}").get().build()
            val response = try {
                client.newCall(request).execute()
            } catch (error: Exception) {
                throw LinkResolutionException("Gagal mengakses tautan: ${error.message ?: error.javaClass.simpleName}")
            }
            response.use { result ->
                if (result.code in setOf(301, 302, 303, 307, 308)) {
                    val location = result.header("Location")
                        ?: throw LinkResolutionException("Server memberi redirect tanpa alamat tujuan.")
                    current = normalizeHttpUrl(runCatching { URI(current).resolve(location).toString() }.getOrNull().orEmpty())
                        ?: throw LinkResolutionException("Redirect menuju URL atau skema yang tidak diizinkan.")
                    hops++
                    if (hops > MAX_REDIRECTS) throw LinkResolutionException("Melebihi batas $MAX_REDIRECTS redirect.")
                    continue
                }
                if (result.code == 404 || result.code == 410) throw LinkResolutionException("Tautan tidak ditemukan atau sudah kedaluwarsa (HTTP ${result.code}).")
                if (result.code !in 200..299) throw LinkResolutionException("Server menolak permintaan (HTTP ${result.code}).")
                val type = result.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                if (type.contains("html") || type.contains("xhtml") || type.isBlank()) {
                    val body = result.body
                    if (body != null) {
                        if (body.contentLength() > MAX_RESPONSE_BYTES) throw LinkResolutionException("Halaman terlalu besar untuk diperiksa dengan aman.")
                        val source = body.source()
                        val buffer = okio.Buffer()
                        while (buffer.size < MAX_RESPONSE_BYTES && source.read(buffer, minOf(8192L, MAX_RESPONSE_BYTES - buffer.size)) != -1L) { }
                        if (!source.exhausted()) throw LinkResolutionException("Halaman melebihi batas $MAX_RESPONSE_BYTES byte.")
                        val metaTarget = extractMetaRefresh(buffer.readString(StandardCharsets.UTF_8), current)
                        if (metaTarget != null) {
                            current = metaTarget
                            hops++
                            if (hops > MAX_REDIRECTS) throw LinkResolutionException("Melebihi batas $MAX_REDIRECTS redirect.")
                            continue
                        }
                    }
                }
                return LinkResolution(original, current, hops, "Tujuan ditemukan. Redirect diperiksa: $hops")
            }
        }
    }
}
