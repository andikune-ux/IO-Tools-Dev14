package dev.andikune.iottools.tools.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
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
    val progress: Float = 0f,
    val progressPercent: Int = 0,
    val speedFormatted: String = "0 KB/s",
    val bytesDownloadedFormatted: String = "0 B",
    val totalBytesFormatted: String = "-",
    val status: DownloadStatus = DownloadStatus.IDLE,
    val statusMessage: String = "",
    val downloadedFile: File? = null,
    val downloadedUri: Uri? = null,
    val downloadedName: String = "",
    val indeterminate: Boolean = false,
    val errorMessage: String? = null
)

/**
 * In-app streaming downloader. Files are written through MediaStore on
 * Android 10+ (scoped storage) or the public Downloads folder on Android 7-9.
 */
object AppDownloadManager {

    private val _downloadState = MutableStateFlow(AppDownloadState())
    val downloadState = _downloadState.asStateFlow()

    private var activeJob: Job? = null
    @Volatile private var activeCall: Call? = null
    private val downloadSequence = AtomicLong(0L)
    private val downloadLock = Any()
    @Volatile private var activeDownloadId = 0L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .build()
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    internal fun isSecureDownloadUrl(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null) return false
        return host != "localhost" &&
            !host.endsWith(".local") &&
            !host.endsWith(".internal") &&
            !host.endsWith(".lan") &&
            host.contains('.') &&
            !host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) &&
            !host.contains(':')
    }

    internal fun isCompatibleContentType(expectedMime: String, actualMime: String): Boolean {
        val expected = expectedMime.substringBefore(';').trim().lowercase()
        val actual = actualMime.substringBefore(';').trim().lowercase()
        if (actual.isBlank() || actual == "application/octet-stream" || actual == "binary/octet-stream") return true
        if (expected.isBlank() || expected == "application/octet-stream") return true
        return actual == expected
    }

    internal fun matchesFileSignature(mimeType: String, header: ByteArray): Boolean {
        if (header.isEmpty()) return false
        fun startsWith(vararg bytes: Int): Boolean =
            header.size >= bytes.size && bytes.indices.all { (header[it].toInt() and 0xff) == bytes[it] }
        fun asciiAt(offset: Int, value: String): Boolean =
            header.size >= offset + value.length &&
                value.indices.all { header[offset + it].toInt().toChar() == value[it] }

        return when (mimeType.substringBefore(';').lowercase()) {
            "video/mp4", "video/quicktime", "video/x-m4v", "audio/mp4" ->
                asciiAt(4, "ftyp") || asciiAt(4, "styp")
            "video/webm", "video/x-matroska" -> startsWith(0x1A, 0x45, 0xDF, 0xA3)
            "video/x-flv" -> asciiAt(0, "FLV")
            "audio/mpeg" -> asciiAt(0, "ID3") ||
                (header.size >= 2 && (header[0].toInt() and 0xff) == 0xff &&
                    ((header[1].toInt() and 0xe0) == 0xe0))
            "audio/ogg" -> asciiAt(0, "OggS")
            "audio/wav", "audio/wave" -> asciiAt(0, "RIFF") && asciiAt(8, "WAVE")
            "audio/aac" -> startsWith(0xff, 0xf1) || startsWith(0xff, 0xf9)
            "image/jpeg" -> startsWith(0xff, 0xd8, 0xff)
            "image/png" -> startsWith(0x89, 0x50, 0x4e, 0x47)
            "image/webp" -> asciiAt(0, "RIFF") && asciiAt(8, "WEBP")
            "image/gif" -> asciiAt(0, "GIF87a") || asciiAt(0, "GIF89a")
            "application/pdf" -> asciiAt(0, "%PDF-")
            "application/zip", "application/x-zip-compressed" ->
                startsWith(0x50, 0x4b, 0x03, 0x04) || startsWith(0x50, 0x4b, 0x05, 0x06) ||
                    startsWith(0x50, 0x4b, 0x07, 0x08)
            else -> true
        }
    }

    private fun executeWithSecureRedirects(
        initialRequest: Request,
        shouldContinue: () -> Boolean,
        onCallCreated: (Call) -> Unit
    ): okhttp3.Response {
        var request = initialRequest
        val redirectCodes = setOf(300, 301, 302, 303, 307, 308)

        for (redirectCount in 0..5) {
            if (!shouldContinue()) throw CancellationException("Unduhan dibatalkan.")
            val call = client.newCall(request)
            onCallCreated(call)
            val response = call.execute()

            if (response.code !in redirectCodes) return response

            val location = response.header("Location")
            val nextUrl = location?.let { response.request.url.resolve(it) }
            if (nextUrl == null || !isSecureDownloadUrl(nextUrl.toString())) {
                response.close()
                throw IllegalStateException("Redirect mengarah ke URL yang tidak aman atau tidak valid.")
            }
            if (redirectCount == 5) {
                response.close()
                throw IllegalStateException("Terlalu banyak redirect saat mengunduh file.")
            }

            val previousUrl = response.request.url
            val changedOrigin = previousUrl.scheme != nextUrl.scheme ||
                previousUrl.host != nextUrl.host ||
                previousUrl.port != nextUrl.port
            response.close()

            request = request.newBuilder()
                .url(nextUrl)
                .apply {
                    if (changedOrigin) {
                        request.headers.names()
                            .filterNot { it.equals("User-Agent", true) || it.equals("Accept", true) }
                            .forEach(::removeHeader)
                    }
                }
                .build()
        }

        throw IllegalStateException("Gagal mengikuti redirect unduhan.")
    }

    fun startDownload(
        context: Context,
        directUrl: String,
        suggestedFileName: String,
        title: String,
        mimeType: String = "video/mp4",
        customHeaders: Map<String, String> = emptyMap(),
        onSuccess: (Uri) -> Unit = {}
    ) {
        cancelDownload()
        val downloadId = synchronized(downloadLock) {
            downloadSequence.incrementAndGet().also { activeDownloadId = it }
        }

        val safeName = ensureExtension(
            MediaStoreSaver.sanitizeFileName(suggestedFileName, "media_${System.currentTimeMillis()}.mp4"),
            mimeType
        )

        if (!isSecureDownloadUrl(directUrl)) {
            _downloadState.value = AppDownloadState(
                url = directUrl,
                fileName = safeName,
                title = title,
                status = DownloadStatus.ERROR,
                statusMessage = "URL unduhan tidak aman atau tidak valid.",
                errorMessage = "Unduhan hanya menerima URL HTTPS publik tanpa kredensial atau alamat lokal."
            )
            return
        }

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
            statusMessage = "Menghubungkan ke server...",
            indeterminate = true,
            errorMessage = null
        )

        val job = CoroutineScope(Dispatchers.IO).launch {
            var pending: MediaStoreSaver.PendingOutput? = null
            var currentCall: Call? = null

            fun publish(transform: (AppDownloadState) -> AppDownloadState) {
                if (activeDownloadId == downloadId) {
                    _downloadState.value = transform(_downloadState.value)
                }
            }

            try {
                if (activeDownloadId != downloadId || !isActive) return@launch

                val reqBuilder = Request.Builder()
                    .url(directUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                customHeaders.forEach { (key, value) ->
                    if (key.isNotBlank() && value.isNotBlank()) reqBuilder.header(key, value)
                }

                executeWithSecureRedirects(
                    initialRequest = reqBuilder.build(),
                    shouldContinue = { activeDownloadId == downloadId && isActive },
                    onCallCreated = { call ->
                        currentCall = call
                        val registered = synchronized(downloadLock) {
                            if (activeDownloadId == downloadId && isActive) {
                                activeCall = call
                                true
                            } else {
                                false
                            }
                        }
                        if (!registered) call.cancel()
                    }
                ).use { response ->
                    if (activeDownloadId != downloadId || !isActive) {
                        throw CancellationException("Unduhan dibatalkan.")
                    }
                    if (!isSecureDownloadUrl(response.request.url.toString())) {
                        throw IllegalStateException("Server mengalihkan unduhan ke URL yang tidak aman.")
                    }
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Server mengembalikan HTTP ${response.code}.")
                    }

                    val body = response.body ?: throw IllegalStateException("Respon server kosong.")
                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    if (contentType.contains("text/html")) {
                        throw IllegalStateException("Tautan mengembalikan halaman HTML, bukan file media.")
                    }
                    if (contentType.contains("application/json") && !mimeType.startsWith("application/json")) {
                        throw IllegalStateException("Server mengembalikan JSON error, bukan file yang diminta.")
                    }
                    if (!isCompatibleContentType(mimeType, contentType)) {
                        throw IllegalStateException(
                            "Tipe konten server (${contentType.substringBefore(';')}) tidak cocok dengan file yang diminta ($mimeType)."
                        )
                    }

                    val totalLength = body.contentLength()
                    val hasLength = totalLength > 0
                    publish {
                        it.copy(
                            status = DownloadStatus.DOWNLOADING,
                            statusMessage = "Mengunduh file...",
                            totalBytesFormatted = if (hasLength) formatBytes(totalLength) else "Ukuran tidak diketahui",
                            indeterminate = !hasLength
                        )
                    }

                    val output = MediaStoreSaver.beginDownload(
                        context = context,
                        fileName = safeName,
                        mimeType = mimeType.ifBlank { guessMime(safeName) }
                    ) ?: throw IllegalStateException(
                        "Tidak dapat membuat file tujuan. Periksa izin penyimpanan dan ruang kosong."
                    )
                    pending = output

                    var downloadedBytes = 0L
                    val signatureBuffer = ByteArray(512)
                    var signatureSize = 0
                    var lastProgressTime = System.currentTimeMillis()
                    var bytesSinceLastSample = 0L

                    body.byteStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead = input.read(buffer)
                        while (isActive && activeDownloadId == downloadId && bytesRead != -1) {
                            val signatureCopy = minOf(bytesRead, signatureBuffer.size - signatureSize)
                            if (signatureCopy > 0) {
                                System.arraycopy(buffer, 0, signatureBuffer, signatureSize, signatureCopy)
                                signatureSize += signatureCopy
                            }

                            output.output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            bytesSinceLastSample += bytesRead

                            val now = System.currentTimeMillis()
                            val delta = now - lastProgressTime
                            if (delta >= 400) {
                                val speedBytesPerSec = (bytesSinceLastSample * 1000.0 / delta).toLong()
                                lastProgressTime = now
                                bytesSinceLastSample = 0L

                                val pct = if (hasLength) {
                                    ((downloadedBytes * 100) / totalLength).toInt().coerceIn(0, 100)
                                } else 0

                                publish {
                                    it.copy(
                                        progress = if (hasLength) (downloadedBytes.toFloat() / totalLength).coerceIn(0f, 1f) else 0f,
                                        progressPercent = pct,
                                        speedFormatted = formatSpeed(speedBytesPerSec),
                                        bytesDownloadedFormatted = formatBytes(downloadedBytes),
                                        statusMessage = if (hasLength) "Mengunduh... $pct%" else "Mengunduh... ${formatBytes(downloadedBytes)}"
                                    )
                                }
                            }
                            bytesRead = input.read(buffer)
                        }
                        output.output.flush()
                    }

                    if (!isActive || activeDownloadId != downloadId) {
                        output.abort()
                        pending = null
                        return@launch
                    }
                    if (downloadedBytes <= 0L) {
                        throw IllegalStateException("Server tidak mengirim data file.")
                    }
                    if (hasLength && downloadedBytes != totalLength) {
                        throw IllegalStateException(
                            "Unduhan tidak lengkap: diterima ${formatBytes(downloadedBytes)} dari ${formatBytes(totalLength)}."
                        )
                    }

                    val firstBytes = String(signatureBuffer, 0, signatureSize, Charsets.ISO_8859_1)
                        .trimStart()
                        .lowercase()
                    if (firstBytes.startsWith("<html") || firstBytes.startsWith("<!doctype html") ||
                        firstBytes.contains("<html", ignoreCase = true)
                    ) {
                        throw IllegalStateException("File yang diterima berisi halaman HTML, bukan file unduhan.")
                    }
                    if (!matchesFileSignature(mimeType, signatureBuffer.copyOf(signatureSize))) {
                        throw IllegalStateException("Isi file tidak sesuai dengan format $mimeType.")
                    }

                    output.commit()
                    pending = null
                    val finalUri = output.uri
                    val finalName = MediaStoreSaver.displayName(context, finalUri) ?: safeName
                    val finalSize = MediaStoreSaver.fileSize(context, finalUri)

                    publish {
                        it.copy(
                            isDownloading = false,
                            progress = 1f,
                            progressPercent = 100,
                            speedFormatted = "Selesai",
                            bytesDownloadedFormatted = formatBytes(finalSize),
                            indeterminate = false,
                            status = DownloadStatus.COMPLETED,
                            statusMessage = "File tersimpan di folder Download/IO-Tools.",
                            downloadedFile = output.file,
                            downloadedUri = finalUri,
                            downloadedName = finalName
                        )
                    }

                    withContext(Dispatchers.Main) {
                        if (activeDownloadId == downloadId) {
                            Toast.makeText(
                                context,
                                "Tersimpan: Download/IO-Tools/$finalName",
                                Toast.LENGTH_LONG
                            ).show()
                            onSuccess(finalUri)
                        }
                    }
                }
            } catch (e: Exception) {
                pending?.abort()
                val cancelled = e is CancellationException || !isActive || activeDownloadId != downloadId
                if (cancelled) {
                    publish {
                        it.copy(
                            isDownloading = false,
                            status = DownloadStatus.IDLE,
                            indeterminate = false,
                            statusMessage = "Unduhan dibatalkan."
                        )
                    }
                } else {
                    publish {
                        it.copy(
                            isDownloading = false,
                            indeterminate = false,
                            status = DownloadStatus.ERROR,
                            statusMessage = "Gagal mengunduh: ${e.message}",
                            errorMessage = e.message ?: "Terjadi kesalahan saat mengunduh."
                        )
                    }
                    withContext(Dispatchers.Main) {
                        if (activeDownloadId == downloadId) {
                            Toast.makeText(context, "Gagal mengunduh: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } finally {
                if (activeCall === currentCall) activeCall = null
            }
        }
        synchronized(downloadLock) {
            if (activeDownloadId == downloadId) activeJob = job else job.cancel()
        }
    }

    fun cancelDownload() {
        synchronized(downloadLock) {
            activeDownloadId = downloadSequence.incrementAndGet()
            if (_downloadState.value.isDownloading) {
                _downloadState.value = _downloadState.value.copy(
                    isDownloading = false,
                    indeterminate = false,
                    status = DownloadStatus.IDLE,
                    statusMessage = "Unduhan dibatalkan."
                )
            }
            activeCall?.cancel()
            activeCall = null
            activeJob?.cancel()
            activeJob = null
        }
    }

    fun resetState() {
        cancelDownload()
        _downloadState.value = AppDownloadState()
    }

    fun playDownloadedVideo(context: Context, uri: Uri, mimeType: String? = null) {
        openUri(context, uri, mimeType, Intent.ACTION_VIEW, "Putar file dengan...")
    }

    fun shareDownloadedVideo(context: Context, uri: Uri, mimeType: String? = null) {
        val mime = mimeType ?: guessMime(MediaStoreSaver.displayName(context, uri) ?: "")
        val shareUri = MediaStoreSaver.shareableUri(context, uri)
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, MediaStoreSaver.displayName(context, uri) ?: "File")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan file"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Compatibility overloads for callers that still hold a legacy File.
    fun playDownloadedVideo(context: Context, file: File) {
        openUri(context, Uri.fromFile(file), guessMime(file.name), Intent.ACTION_VIEW, "Putar file dengan...")
    }

    fun shareDownloadedVideo(context: Context, file: File) {
        val authority = "${context.packageName}.provider"
        val uri = runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull() ?: return
        shareDownloadedVideo(context, uri, guessMime(file.name))
    }

    private fun openUri(context: Context, uri: Uri, mimeType: String?, action: String, chooser: String) {
        try {
            val finalUri = if (uri.scheme == "file") MediaStoreSaver.shareableUri(context, uri) else uri
            val mime = mimeType ?: guessMime(uri.lastPathSegment ?: "")
            val intent = Intent(action).apply {
                setDataAndType(finalUri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, chooser))
        } catch (e: Exception) {
            Toast.makeText(context, "Tidak dapat membuka file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    internal fun ensureExtension(name: String, mimeType: String): String {
        val normalizedMime = mimeType.substringBefore(';').trim().lowercase()
        val currentExtension = name.substringAfterLast('.', "").lowercase()
        val currentMime = currentExtension.takeIf { it.isNotBlank() }
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)?.lowercase() }

        if (currentExtension.isNotBlank() &&
            (normalizedMime.isBlank() || normalizedMime == "application/octet-stream" || currentMime == normalizedMime)
        ) return name

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(normalizedMime)
            ?: when {
                normalizedMime.startsWith("audio/") -> "mp3"
                normalizedMime == "video/webm" -> "webm"
                normalizedMime == "video/x-matroska" -> "mkv"
                normalizedMime.startsWith("video/") -> "mp4"
                normalizedMime.startsWith("image/") -> "jpg"
                normalizedMime == "application/pdf" -> "pdf"
                else -> "bin"
            }

        val baseName = if (currentExtension.isNotBlank()) name.substringBeforeLast('.') else name
        return "$baseName.$extension"
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "pdf" -> "application/pdf"
                "zip" -> "application/zip"
                else -> "application/octet-stream"
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
        return if (mb >= 1.0) String.format("%.1f MB/s", mb) else String.format("%.0f KB/s", kb)
    }
}
