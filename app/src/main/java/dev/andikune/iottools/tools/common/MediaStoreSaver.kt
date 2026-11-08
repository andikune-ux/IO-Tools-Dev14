package dev.andikune.iottools.tools.common

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralised output writer that always lands in a user-visible collection.
 *
 * Android 10+ uses MediaStore (scoped storage safe). Android 7-9 uses the
 * public Downloads/Pictures folders which are writable with the legacy
 * permission declared in the manifest.
 */
object MediaStoreSaver {

    data class PendingOutput(
        val uri: Uri,
        val output: OutputStream,
        val file: File?,
        val mediaStore: Boolean,
        private val context: Context
    ) {
        fun commit() {
            output.flush()
            output.close()
            if (mediaStore && Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                val updated = context.contentResolver.update(uri, values, null, null)
                if (updated <= 0) throw IOException("File gagal dipublikasikan ke MediaStore.")
            }
        }

        fun abort() {
            runCatching { output.close() }
            runCatching {
                if (mediaStore) {
                    context.contentResolver.delete(uri, null, null)
                } else {
                    file?.delete()
                }
            }
        }
    }

    fun sanitizeFileName(raw: String, fallback: String = "file"): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
            .trim()
            .trim('.')
        val safeFallback = fallback
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
            .trim()
            .trim('.')
            .take(180)
            .ifBlank { "file" }
        return if (cleaned.isBlank()) safeFallback else cleaned.take(180)
    }

    fun timestampName(prefix: String, extension: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return sanitizeFileName("${prefix}_$stamp.$extension")
    }

    fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    fun fileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") return uri.path?.let { File(it).length() } ?: 0L
        return runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
        }.getOrNull() ?: 0L
    }

    fun readBytes(
        context: Context,
        uri: Uri,
        maxBytes: Int = 32 * 1024 * 1024
    ): ByteArray? = runCatching {
        require(maxBytes > 0)
        context.contentResolver.openInputStream(uri)?.use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    if (total > maxBytes) throw IOException("File melebihi batas baca $maxBytes byte.")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        }
    }.getOrNull()

    fun openInputStream(context: Context, uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    /**
     * Creates an output suitable for a streaming download. Caller must call
     * [PendingOutput.commit] only after the whole stream has been written.
     */
    fun beginDownload(
        context: Context,
        fileName: String,
        mimeType: String,
        subDirectory: String = "IO-Tools"
    ): PendingOutput? {
        if (Build.VERSION.SDK_INT >= 29) {
            val safeName = uniqueName(
                context,
                fileName,
                Environment.DIRECTORY_DOWNLOADS,
                subDirectory,
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            )
            val relative = "${Environment.DIRECTORY_DOWNLOADS}/$subDirectory"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = runCatching { context.contentResolver.insert(collection, values) }.getOrNull() ?: return null
            val out = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
            if (out == null) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                return null
            }
            return PendingOutput(uri, out, null, true, context)
        }

        val file = createLegacyFile(
            Environment.DIRECTORY_DOWNLOADS,
            subDirectory,
            fileName
        ) ?: return null
        return runCatching {
            PendingOutput(Uri.fromFile(file), FileOutputStream(file), file, false, context)
        }.onFailure { file.delete() }.getOrNull()
    }

    fun beginImageOutput(
        context: Context,
        fileName: String,
        mimeType: String,
        subDirectory: String = "IO-Tools"
    ): PendingOutput? = beginCollectionOutput(
        context,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        Environment.DIRECTORY_PICTURES,
        fileName,
        mimeType,
        subDirectory
    )

    fun beginAudioOutput(
        context: Context,
        fileName: String,
        mimeType: String,
        subDirectory: String = "IO-Tools"
    ): PendingOutput? = beginCollectionOutput(
        context,
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        Environment.DIRECTORY_MUSIC,
        fileName,
        mimeType,
        subDirectory
    )

    fun beginVideoOutput(
        context: Context,
        fileName: String,
        mimeType: String,
        subDirectory: String = "IO-Tools"
    ): PendingOutput? = beginCollectionOutput(
        context,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        Environment.DIRECTORY_MOVIES,
        fileName,
        mimeType,
        subDirectory
    )

    private fun beginCollectionOutput(
        context: Context,
        collection: Uri,
        baseDirectory: String,
        fileName: String,
        mimeType: String,
        subDirectory: String
    ): PendingOutput? {
        if (Build.VERSION.SDK_INT >= 29) {
            val safeName = uniqueName(context, fileName, baseDirectory, subDirectory, collection)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDirectory/$subDirectory")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = runCatching { context.contentResolver.insert(collection, values) }.getOrNull() ?: return null
            val out = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
            if (out == null) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                return null
            }
            return PendingOutput(uri, out, null, true, context)
        }

        val file = createLegacyFile(baseDirectory, subDirectory, fileName) ?: return null
        return runCatching {
            PendingOutput(Uri.fromFile(file), FileOutputStream(file), file, false, context)
        }.onFailure { file.delete() }.getOrNull()
    }

    fun saveBytes(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        subDirectory: String = "IO-Tools"
    ): Uri? {
        if (bytes.isEmpty()) return null
        val pending = (if (mimeType.startsWith("image/")) {
            beginImageOutput(context, fileName, mimeType, subDirectory)
        } else if (mimeType.startsWith("audio/")) {
            beginAudioOutput(context, fileName, mimeType, subDirectory)
        } else if (mimeType.startsWith("video/")) {
            beginVideoOutput(context, fileName, mimeType, subDirectory)
        } else {
            beginDownload(context, fileName, mimeType, subDirectory)
        }) ?: return null

        return try {
            pending.output.write(bytes)
            pending.commit()
            pending.uri
        } catch (e: Exception) {
            pending.abort()
            null
        }
    }

    fun saveStream(
        context: Context,
        fileName: String,
        mimeType: String,
        input: InputStream,
        subDirectory: String = "IO-Tools"
    ): Uri? {
        val pending = beginDownload(context, fileName, mimeType, subDirectory) ?: return null
        return try {
            val copied = input.use { stream -> stream.copyTo(pending.output, 64 * 1024) }
            if (copied <= 0L) throw IOException("Stream sumber kosong.")
            pending.commit()
            pending.uri
        } catch (e: Exception) {
            pending.abort()
            null
        }
    }

    fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100,
        subDirectory: String = "IO-Tools"
    ): Uri? {
        val mime = when (format) {
            Bitmap.CompressFormat.PNG -> "image/png"
            Bitmap.CompressFormat.JPEG -> "image/jpeg"
            Bitmap.CompressFormat.WEBP -> "image/webp"
            else -> "image/png"
        }
        val pending = beginImageOutput(context, fileName, mime, subDirectory) ?: return null
        return try {
            if (!bitmap.compress(format, quality.coerceIn(1, 100), pending.output)) {
                throw IOException("Encoder bitmap gagal.")
            }
            pending.commit()
            pending.uri
        } catch (_: Exception) {
            pending.abort()
            null
        }
    }

    /** FileProvider URI is required for sharing legacy file:// outputs. */
    fun shareableUri(context: Context, uri: Uri): Uri {
        return if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) } ?: return uri
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else {
            uri
        }
    }

    private fun uniqueName(
        context: Context,
        original: String,
        baseDirectory: String,
        subDirectory: String,
        collection: Uri
    ): String {
        val safe = sanitizeFileName(original)
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var candidate = safe
        var index = 1
        while (index < 10_000) {
            val exists = mediaStoreNameExists(
                context,
                candidate,
                "$baseDirectory/$subDirectory",
                collection
            )
            if (exists == false) return candidate
            if (exists == null) {
                return sanitizeFileName("${stem}_${System.nanoTime()}$ext")
            }
            candidate = "$stem ($index)$ext"
            index++
        }
        return sanitizeFileName("${stem}_${System.nanoTime()}$ext")
    }

    private fun mediaStoreNameExists(
        context: Context,
        name: String,
        relativePath: String,
        collection: Uri
    ): Boolean? = runCatching {
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(name, "${relativePath.trimEnd('/')}/%")
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            args,
            null
        )?.use { it.count > 0 } ?: false
    }.getOrNull()

    private fun createLegacyFile(baseDirectory: String, subDirectory: String, original: String): File? {
        val dir = File(Environment.getExternalStoragePublicDirectory(baseDirectory), subDirectory)
        if (!dir.exists() && !dir.mkdirs()) return null
        if (!dir.isDirectory) return null

        val safe = sanitizeFileName(original)
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var candidate = safe
        var index = 1
        while (index < 10_000) {
            val file = File(dir, candidate)
            if (runCatching { file.createNewFile() }.getOrDefault(false)) return file
            if (!file.exists()) return null
            candidate = "$stem ($index)$ext"
            index++
        }
        return null
    }
}
