package dev.andikune.iottools.tools.terabox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.andikune.iottools.tools.common.VideoDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
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

    /**
     * Extracts TeraBox share ID / short key from:
     * - https://terabox.com/s/1xyzABC
     * - https://www.teraboxapp.com/s/1xyzABC
     * - https://1024tera.com/s/1xyzABC
     * - https://teraboxlink.com/s/1xyzABC
     */
    fun extractShareId(rawUrl: String): String? {
        val clean = rawUrl.trim()
        val patterns = listOf(
            Pattern.compile("(?:terabox\\.com|teraboxapp\\.com|1024tera\\.com|teraboxlink\\.com|freeterabox\\.com)/s/([^/?#&\\s]+)"),
            Pattern.compile("surl=([^&\\s]+)"),
            Pattern.compile("^1[a-zA-Z0-9_-]{6,}$")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(clean)
            if (matcher.find()) {
                val group = matcher.group(1) ?: matcher.group(0)
                if (!group.isNullOrBlank()) {
                    return group.removePrefix("1")
                }
            }
        }
        return null
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

            // If no genuine stream URL could be resolved, fail explicitly.
            // NEVER generate a dummy .zip or .txt landing page!
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
     * Enqueues real TeraBox video download via Android DownloadManager with .mp4 video MIME type.
     */
    fun enqueueDownload(
        context: Context,
        fileInfo: TeraBoxFileInfo
    ): Long {
        val safeName = fileInfo.fileName
            .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            .ifEmpty { "TeraBox_${fileInfo.shareId}.mp4" }

        val mime = if (safeName.endsWith(".mp4", ignoreCase = true)) "video/mp4" else "application/octet-stream"

        return VideoDownloadHelper.enqueueNativeVideoDownload(
            context = context,
            directUrl = fileInfo.downloadUrl,
            suggestedFileName = safeName,
            title = safeName.take(35),
            description = "Mengunduh Video TeraBox (${fileInfo.fileSizeFormatted})...",
            mimeType = mime
        )
    }

    /**
     * Share direct download link
     */
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
