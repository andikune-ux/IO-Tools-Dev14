package dev.andikune.iottools.tools.terabox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.andikune.iottools.tools.common.AppDownloadManager
import dev.andikune.iottools.tools.common.MediaStoreSaver
import dev.andikune.iottools.tools.common.VideoDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI

data class TeraBoxFileInfo(
    val shareId: String,
    val fileName: String,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val downloadUrl: String,
    val shareUrl: String,
    val thumbnailUrl: String?,
    val isFolder: Boolean,
    val fileExtension: String,
    val mimeType: String = "application/octet-stream",
    val workerApiKey: String? = null
)

data class TeraBoxShareData(
    val shareId: String,
    val shareUrl: String,
    val files: List<TeraBoxFileInfo>,
    val passwordProtected: Boolean,
    val truncated: Boolean,
    val strategy: String
)

object TeraBoxDownloaderService {

    private val client by lazy { VideoDownloadHelper.client }

    private val allowedShareHosts = setOf(
        "terabox.com",
        "teraboxapp.com",
        "1024tera.com",
        "1024terabox.com",
        "teraboxlink.com",
        "freeterabox.com"
    )

    fun extractShareId(rawUrl: String): String? {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.userInfo != null) return null
        if (host !in allowedShareHosts && allowedShareHosts.none { host.endsWith(".$it") }) return null

        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val shareIndex = segments.indexOf("s")
        val pathId = if (shareIndex >= 0 && shareIndex + 1 < segments.size) segments[shareIndex + 1] else null
        val queryId = uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.substringBefore('=') == "surl" }
            ?.substringAfter('=', "")
            ?.let { runCatching { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrDefault(it) }
        val candidate = pathId ?: queryId ?: return null
        // Preserve the leading "1": it is part of TeraBox's share ID (surl).
        return candidate.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{6,}$")) }
    }

    /** Only HTTPS user-controlled Worker origins are accepted; never send a share URL to a public resolver. */
    fun normalizeResolverBaseUrl(rawUrl: String): String? {
        val clean = rawUrl.trim().trimEnd('/')
        val uri = runCatching { URI(clean) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        if (!uri.path.isNullOrEmpty() && uri.path != "/") return null
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal") ||
            host.endsWith(".lan") || host.endsWith(".home.arpa") || !host.contains('.') || host.contains(':') ||
            host.matches(Regex("^[0-9.]+$"))
        ) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        return "https://$host" + if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    }

    internal fun isWorkerResourceUrl(rawUrl: String, baseUrl: String): Boolean {
        val resource = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        val base = runCatching { URI(baseUrl) }.getOrNull() ?: return false
        return resource.scheme?.lowercase() == "https" &&
            resource.userInfo == null &&
            resource.fragment == null &&
            resource.path in setOf("/stream", "/hls") &&
            resource.host?.lowercase() == base.host?.lowercase() &&
            effectivePort(resource) == effectivePort(base)
    }

    private fun effectivePort(uri: URI): Int = if (uri.port == -1) 443 else uri.port

    private fun appendSharePassword(url: String, password: String?): String {
        val cleanPassword = password?.takeIf { it.isNotBlank() } ?: return url
        return Uri.parse(url).buildUpon().appendQueryParameter("password", cleanPassword).build().toString()
    }

    suspend fun resolveTeraBoxLink(
        rawUrl: String,
        resolverBaseUrl: String,
        apiKey: String? = null,
        sharePassword: String? = null
    ): Result<TeraBoxShareData> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = rawUrl.trim()
            val shortId = extractShareId(clean)
                ?: throw IllegalArgumentException("Tautan harus berupa URL TeraBox publik yang valid.")
            val baseUrl = normalizeResolverBaseUrl(resolverBaseUrl)
                ?: throw IllegalArgumentException("Endpoint Worker tidak valid. Gunakan URL HTTPS Cloudflare Worker milik sendiri.")

            val requestUrl = "$baseUrl/api/resolve?link=${Uri.encode(clean)}" +
                (sharePassword?.takeIf { it.isNotBlank() }?.let { "&password=${Uri.encode(it)}" } ?: "")
            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                .header("Accept", "application/json")
                .apply {
                    apiKey?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        header("Authorization", "Bearer $it")
                    }
                }
                .build()

            val responseJson = client.newCall(request).execute().use { response ->
                val body = response.body?.byteStream()?.use { readLimited(it, 2 * 1024 * 1024) }
                    ?: throw IllegalStateException("Worker mengembalikan respons kosong (HTTP ${response.code}).")
                if (body.isEmpty()) throw IllegalStateException("Worker mengembalikan respons kosong (HTTP ${response.code}).")
                val parsed = runCatching { JSONObject(String(body, Charsets.UTF_8)) }.getOrElse {
                    throw IllegalStateException(
                        if (response.isSuccessful) "Worker mengembalikan JSON yang tidak valid."
                        else "Worker mengembalikan HTTP ${response.code} dengan respons yang tidak valid."
                    )
                }
                if (!response.isSuccessful && parsed.optString("status") != "error") {
                    throw IllegalStateException("Worker mengembalikan HTTP ${response.code}.")
                }
                parsed
            }

            if (responseJson.optString("status") != "success") {
                val error = responseJson.optJSONObject("error")
                val code = error?.optString("code").orEmpty()
                val message = error?.optString("message").orEmpty()
                throw IllegalStateException(
                    when (code) {
                        "password_required" -> "Share ini memerlukan kata sandi. Masukkan password share yang benar."
                        "unauthorized" -> "API key Worker tidak valid atau belum dikonfigurasi."
                        "rate_limited" -> "Worker membatasi permintaan. Coba lagi nanti."
                        "verification_required" -> "TeraBox meminta verifikasi tambahan. Coba lagi nanti."
                        "cookie_invalid" -> "Sesi TeraBox pada Worker kedaluwarsa. Perbarui secret Worker."
                        "unsupported_host" -> "Host tautan tidak didukung oleh Worker."
                        "not_found", "empty_share" -> "Share tidak ditemukan atau tidak berisi file yang dapat diakses."
                        "budget_exhausted" -> "Folder terlalu besar untuk diproses sekaligus. Coba gunakan tautan file tertentu."
                        "upstream_unavailable", "upstream_unexpected" ->
                            "Server TeraBox sedang bermasalah atau berubah. Coba lagi beberapa saat."
                        "invalid_parameter", "missing_parameter" ->
                            "Worker menolak parameter tautan. Periksa kembali URL share TeraBox."
                        else -> message.ifBlank { "Worker gagal menyelesaikan tautan TeraBox (${code.ifBlank { "unknown_error" }})." }
                    }
                )
            }

            val filesJson = responseJson.optJSONArray("files")
            val fileObjects = if (filesJson != null && filesJson.length() > 0) {
                (0 until filesJson.length()).mapNotNull { filesJson.optJSONObject(it) }
            } else {
                listOf(responseJson)
            }
            if (fileObjects.isEmpty()) throw IllegalStateException("Worker tidak menemukan file pada tautan tersebut.")

            val key = apiKey?.trim()?.takeIf { it.isNotEmpty() }
            val files = fileObjects.map { file ->
                val rawDownloadUrl = file.optString("download_url")
                    .ifBlank { file.optString("stream_url") }
                if (!isWorkerResourceUrl(rawDownloadUrl, baseUrl)) {
                    throw IllegalStateException("Worker tidak memberikan URL unduhan HTTPS dari origin Worker yang sama.")
                }
                val downloadUrl = appendSharePassword(rawDownloadUrl, sharePassword)
                val fileName = file.optString("file_name")
                    .ifBlank { responseJson.optString("file_name") }
                    .ifBlank { "TeraBox_${shortId}.bin" }
                val sizeBytes = file.optLong("size_bytes", responseJson.optLong("size_bytes", 0L))
                    .coerceAtLeast(0L)
                val mimeType = file.optString("mime_type")
                    .takeIf { it.matches(Regex("^[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+$")) }
                    ?: "application/octet-stream"
                val thumbnail = file.optString("thumbnail")
                    .takeIf { VideoDownloadHelper.isSecurePublicMediaUrl(it) }

                TeraBoxFileInfo(
                    shareId = shortId,
                    fileName = fileName,
                    fileSizeFormatted = file.optString("file_size")
                        .ifBlank { responseJson.optString("file_size") }
                        .ifBlank { formatBytes(sizeBytes) },
                    fileSizeBytes = sizeBytes,
                    downloadUrl = downloadUrl,
                    shareUrl = clean,
                    thumbnailUrl = thumbnail,
                    isFolder = false,
                    fileExtension = fileName.substringAfterLast('.', "").lowercase()
                        .ifBlank { mimeType.substringAfter('/', "file").lowercase() },
                    mimeType = mimeType,
                    workerApiKey = key
                )
            }

            val share = responseJson.optJSONObject("share")
            TeraBoxShareData(
                shareId = shortId,
                shareUrl = clean,
                files = files,
                passwordProtected = share?.optBoolean("password_protected", false) ?: false,
                truncated = share?.optBoolean("truncated", false) ?: false,
                strategy = share?.optString("strategy").orEmpty()
            )
        }
    }

    internal fun readLimited(input: java.io.InputStream, maxBytes: Int): ByteArray {
        require(maxBytes > 0) { "Batas baca harus lebih besar dari nol." }
        val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
        val buffer = ByteArray(8192)
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
        if (output.size() == maxBytes) {
            // Distinguish a response exactly at the cap from one that exceeds it.
            if (input.read() >= 0) throw IllegalStateException("Respons Worker melebihi batas 2 MB.")
        }
        return output.toByteArray()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "Ukuran tidak diketahui"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.2f MB", mb)
            else -> String.format("%.1f KB", kb)
        }
    }

    fun enqueueDownload(context: Context, fileInfo: TeraBoxFileInfo) {
        val safeName = MediaStoreSaver.sanitizeFileName(fileInfo.fileName, "TeraBox_${fileInfo.shareId}.bin")
        AppDownloadManager.startDownload(
            context = context,
            directUrl = fileInfo.downloadUrl,
            suggestedFileName = safeName,
            title = safeName.take(35),
            mimeType = fileInfo.mimeType,
            customHeaders = fileInfo.workerApiKey?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
        )
    }

    fun shareLink(context: Context, fileInfo: TeraBoxFileInfo) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, fileInfo.fileName)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Link share TeraBox: ${fileInfo.shareUrl}\nFile: ${fileInfo.fileName} (${fileInfo.fileSizeFormatted})"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Link TeraBox"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
