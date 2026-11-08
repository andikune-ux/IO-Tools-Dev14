package dev.andikune.iottools.tools.terabox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.andikune.iottools.tools.common.AppDownloadManager
import dev.andikune.iottools.tools.common.VideoDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
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
        val clean = rawUrl.trim()
        if (clean.matches(Regex("^1[A-Za-z0-9_-]{6,}$"))) return clean.removePrefix("1")

        val uri = runCatching { URI(clean) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (host !in allowedShareHosts && allowedShareHosts.none { host.endsWith(".$it") }) return null

        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val shareIndex = segments.indexOf("s")
        val pathId = if (shareIndex >= 0 && shareIndex + 1 < segments.size) segments[shareIndex + 1] else null
        val queryId = uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.substringBefore('=') == "surl" }
            ?.substringAfter('=', "")
            ?.let { runCatching { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrDefault(it) }
        val candidate = pathId ?: queryId ?: return null
        return candidate.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{6,}$")) }?.removePrefix("1")
    }

    /** Only HTTPS user-controlled Worker origins are accepted; never send a share URL to a public resolver. */
    fun normalizeResolverBaseUrl(rawUrl: String): String? {
        val clean = rawUrl.trim().trimEnd('/')
        val uri = runCatching { URI(clean) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        if (!uri.path.isNullOrEmpty() && uri.path != "/") return null
        if (host == "localhost" || !host.contains('.') ||
            host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        return clean
    }

    internal fun isWorkerResourceUrl(rawUrl: String, baseUrl: String): Boolean {
        val resource = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        val base = runCatching { URI(baseUrl) }.getOrNull() ?: return false
        return resource.scheme?.lowercase() == "https" &&
            resource.host?.lowercase() == base.host?.lowercase() &&
            effectivePort(resource) == effectivePort(base)
    }

    private fun effectivePort(uri: URI): Int = if (uri.port == -1) 443 else uri.port

    suspend fun resolveTeraBoxLink(
        rawUrl: String,
        resolverBaseUrl: String,
        apiKey: String? = null
    ): Result<TeraBoxFileInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = rawUrl.trim()
            val shortId = extractShareId(clean)
                ?: throw IllegalArgumentException(
                    "Tautan harus berupa URL TeraBox publik yang valid."
                )
            val baseUrl = normalizeResolverBaseUrl(resolverBaseUrl)
                ?: throw IllegalArgumentException(
                    "Endpoint Worker tidak valid. Gunakan URL HTTPS Cloudflare Worker milik sendiri."
                )

            val requestUrl = "$baseUrl/api/resolve?link=${Uri.encode(clean)}"
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
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty()
                    throw IllegalStateException(
                        detail.ifBlank { "Worker mengembalikan HTTP ${response.code}." }
                    )
                }
                if (body.isBlank()) throw IllegalStateException("Worker mengembalikan respons kosong.")
                JSONObject(body)
            }

            if (responseJson.optString("status") != "success") {
                val error = responseJson.optJSONObject("error")
                val code = error?.optString("code").orEmpty()
                val message = error?.optString("message").orEmpty()
                throw IllegalStateException(
                    when (code) {
                        "password_required" -> "Tautan TeraBox ini dilindungi kata sandi; fitur input kata sandi belum tersedia."
                        "unauthorized" -> "API key Worker tidak valid atau belum dikonfigurasi."
                        "rate_limited" -> "Worker membatasi permintaan. Coba lagi nanti."
                        "verification_required" -> "TeraBox meminta verifikasi tambahan. Coba lagi nanti."
                        "cookie_invalid" -> "Sesi TeraBox pada Worker kedaluwarsa. Perbarui secret Worker."
                        else -> message.ifBlank { "Worker gagal menyelesaikan tautan TeraBox (${code.ifBlank { "unknown_error" }})." }
                    }
                )
            }

            val files = responseJson.optJSONArray("files")
            val file = if (files != null && files.length() > 0) {
                files.optJSONObject(0)
            } else {
                responseJson
            } ?: throw IllegalStateException("Worker tidak menemukan file pada tautan tersebut.")

            val downloadUrl = file.optString("download_url")
                .ifBlank { file.optString("stream_url") }
                .takeIf { isWorkerResourceUrl(it, baseUrl) }
                ?: throw IllegalStateException("Worker tidak memberikan URL unduhan HTTPS dari origin Worker yang sama.")

            val fileName = file.optString("file_name")
                .ifBlank { responseJson.optString("file_name") }
                .ifBlank { "TeraBox_${shortId}.bin" }
            val sizeBytes = file.optLong("size_bytes", responseJson.optLong("size_bytes", 0L))
            val mimeType = file.optString("mime_type")
                .takeIf { it.matches(Regex("^[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+$")) }
                ?: "application/octet-stream"
            val thumbnail = file.optString("thumbnail")
                .takeIf { it.startsWith("https://") }

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
                workerApiKey = apiKey?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
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
        val safeName = fileInfo.fileName
            .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            .ifEmpty { "TeraBox_${fileInfo.shareId}.bin" }

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
