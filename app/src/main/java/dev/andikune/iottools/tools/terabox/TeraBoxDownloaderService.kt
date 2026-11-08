package dev.andikune.iottools.tools.terabox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.andikune.iottools.tools.common.AppDownloadManager
import dev.andikune.iottools.tools.common.VideoDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.regex.Pattern

data class TeraBoxFileInfo(
    val shareId: String,
    val fileName: String,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val downloadUrl: String,
    val thumbnailUrl: String?,
    val isFolder: Boolean,
    val fileExtension: String
)

object TeraBoxDownloaderService {

    private val client by lazy { VideoDownloadHelper.client }

    fun extractShareId(rawUrl: String): String? {
        val clean = rawUrl.trim()
        if (clean.matches(Regex("^1[A-Za-z0-9_-]{6,}$"))) return clean.removePrefix("1")

        val uri = runCatching { java.net.URI(clean) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val allowedHosts = setOf("terabox.com", "teraboxapp.com", "1024tera.com", "1024terabox.com", "teraboxlink.com", "freeterabox.com")
        if (host !in allowedHosts && allowedHosts.none { host.endsWith(".$it") }) return null

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

    /**
     * Queries genuine TeraBox direct download API endpoints to extract real video file streams.
     * Never creates fake .zip or .txt landing pages.
     */
    suspend fun resolveTeraBoxLink(rawUrl: String): Result<TeraBoxFileInfo> = withContext(Dispatchers.IO) {
        try {
            val clean = rawUrl.trim()
            val shortId = extractShareId(clean)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Tautan harus berupa URL TeraBox yang valid (contoh: terabox.com/s/1... atau 1024tera.com/s/...)")
                )

            var fileName = ""
            var downloadUrl = ""
            var fileSize = 0L
            var thumb: String? = null
            var resolved = false

            // Endpoint 1: Terabox Direct API Resolver
            val resolverUrl = "https://terabox-dl.qtcloud.workers.dev/api/get-info?shorturl=$shortId"
            try {
                val req = Request.Builder()
                    .url(resolverUrl)
                    .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            if (json.optBoolean("ok", false) || json.has("downloadUrl") || json.has("list")) {
                                if (json.has("list")) {
                                    val list = json.getJSONArray("list")
                                    if (list.length() > 0) {
                                        val first = list.getJSONObject(0)
                                        fileName = first.optString("server_filename", "")
                                        fileSize = first.optLong("size", 0L)
                                        downloadUrl = first.optString("dlink", "")
                                        thumb = first.optJSONObject("thumbs")?.optString("url3", "").takeIf { !it.isNullOrBlank() }
                                        if (downloadUrl.isNotBlank()) resolved = true
                                    }
                                } else {
                                    fileName = json.optString("file_name", "")
                                    downloadUrl = json.optString("downloadUrl", "")
                                    fileSize = json.optLong("size", 0L)
                                    if (downloadUrl.isNotBlank()) resolved = true
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // Endpoint 2: YTB TeraBox resolver
            if (!resolved || downloadUrl.isBlank()) {
                val secondaryUrl = "https://ytb-terabox.vercel.app/api?url=${Uri.encode(clean)}"
                try {
                    val req2 = Request.Builder()
                        .url(secondaryUrl)
                        .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                        .build()
                    client.newCall(req2).execute().use { resp2 ->
                        if (resp2.isSuccessful) {
                            val body2 = resp2.body?.string().orEmpty()
                            val json2 = JSONObject(body2)
                            val dlink = json2.optString("download_link", json2.optString("dlink", ""))
                            if (dlink.isNotBlank()) {
                                downloadUrl = dlink
                                if (fileName.isBlank()) fileName = json2.optString("file_name", "")
                                fileSize = json2.optLong("file_size", fileSize)
                                resolved = true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Endpoint 3: TeraBox Downloader Online API
            if (!resolved || downloadUrl.isBlank()) {
                val tertiaryUrl = "https://teraboxdownloader.online/api/get-info?url=${Uri.encode(clean)}"
                try {
                    val req3 = Request.Builder()
                        .url(tertiaryUrl)
                        .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                        .build()
                    client.newCall(req3).execute().use { resp3 ->
                        if (resp3.isSuccessful) {
                            val body3 = resp3.body?.string().orEmpty()
                            val json3 = JSONObject(body3)
                            val dlink = json3.optString("download_url", json3.optString("dlink", ""))
                            if (dlink.isNotBlank()) {
                                downloadUrl = dlink
                                if (fileName.isBlank()) fileName = json3.optString("file_name", "")
                                fileSize = json3.optLong("size", fileSize)
                                resolved = true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Endpoint 4: TeraDL API
            if (!resolved || downloadUrl.isBlank()) {
                try {
                    val reqPayload = JSONObject().apply {
                        put("url", clean)
                    }
                    val req4 = Request.Builder()
                        .url("https://teradl-api.dapuntaratya.com/generate_file")
                        .header("Content-Type", "application/json")
                        .header("User-Agent", VideoDownloadHelper.DEFAULT_USER_AGENT)
                        .post(reqPayload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                    client.newCall(req4).execute().use { resp4 ->
                        if (resp4.isSuccessful) {
                            val body4 = resp4.body?.string().orEmpty()
                            val json4 = JSONObject(body4)
                            val dlink = json4.optString("download_url", "")
                            if (dlink.isNotBlank()) {
                                downloadUrl = dlink
                                if (fileName.isBlank()) fileName = json4.optString("file_name", "")
                                fileSize = json4.optLong("size", fileSize)
                                resolved = true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (!resolved || downloadUrl.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Gagal mengekstrak video asli dari TeraBox. File mungkin telah dihapus oleh pemilik, membutuhkan kata sandi, atau server resolver sedang penuh. Silakan coba link lain atau periksa izin tautan."
                    )
                )
            }

            if (fileName.isBlank()) {
                fileName = "TeraBox_Video_$shortId.mp4"
            } else if (!fileName.contains(".")) {
                fileName = "$fileName.mp4"
            }

            val ext = fileName.substringAfterLast('.', "mp4").lowercase()
            val formattedSize = formatBytes(fileSize)

            val fileInfo = TeraBoxFileInfo(
                shareId = shortId,
                fileName = fileName,
                fileSizeFormatted = formattedSize,
                fileSizeBytes = fileSize,
                downloadUrl = downloadUrl,
                thumbnailUrl = thumb,
                isFolder = false,
                fileExtension = ext
            )

            Result.success(fileInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "Ukuran Asli (Video HD)"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.2f MB", mb)
            else -> String.format("%.1f KB", kb)
        }
    }

    /**
     * Enqueues real TeraBox video download via AppDownloadManager with 0-100% progress and speed counter.
     */
    fun enqueueDownload(
        context: Context,
        fileInfo: TeraBoxFileInfo
    ) {
        val safeName = fileInfo.fileName
            .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            .ifEmpty { "TeraBox_${fileInfo.shareId}.mp4" }

        val mime = if (safeName.endsWith(".mp4", ignoreCase = true)) "video/mp4" else "application/octet-stream"

        AppDownloadManager.startDownload(
            context = context,
            directUrl = fileInfo.downloadUrl,
            suggestedFileName = safeName,
            title = safeName.take(35),
            mimeType = mime
        )
    }

    fun shareLink(context: Context, fileInfo: TeraBoxFileInfo) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, fileInfo.fileName)
                putExtra(Intent.EXTRA_TEXT, "Unduh file Video TeraBox (${fileInfo.fileSizeFormatted}):\n${fileInfo.fileName}\n\nLink: ${fileInfo.downloadUrl}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Link TeraBox"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
