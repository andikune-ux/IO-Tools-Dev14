package dev.andikune.iottools.tools.common

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    IDLE,
    CONNECTING,
    DOWNLOADING,
    COMPLETED,
    ERROR
}

data class AppDownloadState(
    val isDownloading: Boolean = false,
    val url: String = "",
    val fileName: String = "",
    val title: String = "",
    val progress: Float = 0f, // 0.0f .. 1.0f
    val progressPercent: Int = 0, // 0 .. 100
    val speedFormatted: String = "0 KB/s",
    val bytesDownloadedFormatted: String = "0 B",
    val totalBytesFormatted: String = "Video HD",
    val status: DownloadStatus = DownloadStatus.IDLE,
    val statusMessage: String = "",
    val downloadedFile: File? = null,
    val errorMessage: String? = null
)

/**
 * Universal In-App Video & Media Download Engine.
 * Features:
 * - Real-time 0-100% progress and download speed (e.g. 2.4 MB/s)
 * - Directly streams to public Downloads folder with MediaScanner notification
 * - Enforces authentic binary playable video (.mp4) output
 * - Provides direct Play Video & Share File intents
 */
object AppDownloadManager {

    private val _downloadState = MutableStateFlow(AppDownloadState())
    val downloadState = _downloadState.asStateFlow()

    private var activeJob: Job? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun startDownload(
        context: Context,
        directUrl: String,
        suggestedFileName: String,
        title: String,
        mimeType: String = "video/mp4",
        customHeaders: Map<String, String> = emptyMap(),
        onSuccess: (File) -> Unit = {}
    ) {
        cancelDownload()

        val safeName = sanitizeFileName(suggestedFileName)

        _downloadState.value = AppDownloadState(
            isDownloading = true,
            url = directUrl,
            fileName = safeName,
            title = title,
            progress = 0f,
            progressPercent = 0,
            speedFormatted = "Menghubungkan...",
            bytesDownloadedFormatted = "0 B",
            totalBytesFormatted = "Menghitung ukuran...",
            status = DownloadStatus.CONNECTING,
            statusMessage = "Menghubungkan ke server video...",
            downloadedFile = null,
            errorMessage = null
        )

        activeJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val reqBuilder = Request.Builder()
                    .url(directUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")

                customHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

                val response = client.newCall(reqBuilder.build()).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Server mengembalikan kode error HTTP ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("Respon server kosong.")
                val contentType = response.header("Content-Type", "").orEmpty().lowercase()
                if (contentType.contains("text/html") && !directUrl.contains(".mp4", ignoreCase = true)) {
                    throw IllegalStateException("Tautan mengembalikan halaman HTML web bukan video asli.")
                }

                val totalLength = body.contentLength()
                val totalFormatted = if (totalLength > 0) formatBytes(totalLength) else "Stream Asli"

                _downloadState.value = _downloadState.value.copy(
                    status = DownloadStatus.DOWNLOADING,
                    statusMessage = "Mengunduh file video asli...",
                    totalBytesFormatted = totalFormatted
                )

                // Select target directory: prefer public Downloads folder, fallback to app external media
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                var targetFile = File(downloadsDir, safeName)
                if (targetFile.exists()) {
                    val baseName = safeName.substringBeforeLast('.')
                    val extension = safeName.substringAfterLast('.', "mp4")
                    targetFile = File(downloadsDir, "${baseName}_${System.currentTimeMillis() % 10000}.$extension")
                }

                var downloadedBytes = 0L
                var lastProgressTime = System.currentTimeMillis()
                var bytesSinceLastSample = 0L
                var currentSpeed = "0 KB/s"

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16384) // 16KB chunk
                        var bytesRead = input.read(buffer)

                        while (isActive && bytesRead != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            bytesSinceLastSample += bytesRead

                            val now = System.currentTimeMillis()
                            val delta = now - lastProgressTime
                            if (delta >= 400) { // update UI speed every 400ms
                                val speedBytesPerSec = (bytesSinceLastSample * 1000.0) / delta
                                currentSpeed = formatSpeed(speedBytesPerSec.toLong())
                                lastProgressTime = now
                                bytesSinceLastSample = 0L

                                val progressFloat = if (totalLength > 0) {
                                    (downloadedBytes.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                                } else 0.5f

                                val progressPct = if (totalLength > 0) {
                                    ((downloadedBytes * 100) / totalLength).toInt().coerceIn(0, 100)
                                } else {
                                    ((downloadedBytes / 1024 / 1024) % 100).toInt()
                                }

                                _downloadState.value = _downloadState.value.copy(
                                    progress = progressFloat,
                                    progressPercent = progressPct,
                                    speedFormatted = currentSpeed,
                                    bytesDownloadedFormatted = formatBytes(downloadedBytes),
                                    statusMessage = "Sedang mengunduh video (${progressPct}%)..."
                                )
                            }
                            bytesRead = input.read(buffer)
                        }
                        output.flush()
                    }
                }

                if (!isActive) {
                    if (targetFile.exists()) targetFile.delete()
                    return@launch
                }

                // Verify downloaded file is not corrupted or empty
                if (targetFile.length() < 50 * 1024) { // Under 50KB for video is usually an error page
                    // Check if file content is HTML
                    val textPreview = try { targetFile.readText().take(500) } catch (_: Exception) { "" }
                    if (textPreview.contains("<!DOCTYPE", ignoreCase = true) || textPreview.contains("<html", ignoreCase = true)) {
                        targetFile.delete()
                        throw IllegalStateException("File yang terunduh berisi halaman web error, bukan biner video asli.")
                    }
                }

                // Notify Android MediaScanner so file is instantly available in phone's Gallery / Video Player
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf(mimeType),
                        null
                    )
                } catch (_: Exception) {}

                _downloadState.value = _downloadState.value.copy(
                    isDownloading = false,
                    progress = 1f,
                    progressPercent = 100,
                    speedFormatted = "Selesai",
                    bytesDownloadedFormatted = formatBytes(targetFile.length()),
                    status = DownloadStatus.COMPLETED,
                    statusMessage = "✓ Video berhasil diunduh dan tersimpan di Galeri & folder Download!",
                    downloadedFile = targetFile
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "✓ Sukses! Video tersimpan di folder Download:\n${targetFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                    onSuccess(targetFile)
                }

            } catch (e: Exception) {
                if (e is CancellationException) {
                    _downloadState.value = _downloadState.value.copy(
                        isDownloading = false,
                        status = DownloadStatus.IDLE,
                        statusMessage = "Unduhan dibatalkan."
                    )
                } else {
                    _downloadState.value = _downloadState.value.copy(
                        isDownloading = false,
                        status = DownloadStatus.ERROR,
                        statusMessage = "Gagal mengunduh: ${e.message}",
                        errorMessage = e.message ?: "Terjadi kesalahan saat mengunduh."
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Gagal mengunduh: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        activeJob?.cancel()
        activeJob = null
    }

    fun resetState() {
        cancelDownload()
        _downloadState.value = AppDownloadState()
    }

    fun playDownloadedVideo(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val extension = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "video/mp4"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Putar Video Dengan..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Tidak dapat membuka player: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareDownloadedVideo(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val extension = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "video/mp4"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan File Video"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
        val defaultName = "Video_${System.currentTimeMillis()}"
        val finalBase = if (cleaned.isBlank()) defaultName else cleaned
        return if (finalBase.endsWith(".mp4", ignoreCase = true) ||
            finalBase.endsWith(".mp3", ignoreCase = true) ||
            finalBase.endsWith(".mkv", ignoreCase = true) ||
            finalBase.endsWith(".webm", ignoreCase = true) ||
            finalBase.endsWith(".jpg", ignoreCase = true)
        ) {
            finalBase
        } else {
            "$finalBase.mp4"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format("%.1f MB/s", mb)
        } else {
            String.format("%.0f KB/s", kb)
        }
    }
}
