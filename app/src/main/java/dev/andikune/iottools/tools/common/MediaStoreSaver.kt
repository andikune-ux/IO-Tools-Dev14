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
            try {
                output.flush()
                output.close()
            } catch (_: Exception) {
            }
            if (mediaStore && Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                runCatching { context.contentResolver.update(uri, values, null, null) }
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
        return if (cleaned.isBlank()) fallback else cleaned.take(180)
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

    fun readBytes(context: Context, uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
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
        val safeName = uniqueName(context, fileName, subDirectory)
        if (Build.VERSION.SDK_INT >= 29) {
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

        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), subDirectory)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, safeName)
        return runCatching {
            PendingOutput(Uri.fromFile(file), FileOutputStream(file), file, false, context)
        }.getOrNull()
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
        val safeName = uniqueName(context, fileName, subDirectory)
        if (Build.VERSION.SDK_INT >= 29) {
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

        val dir = File(Environment.getExternalStoragePublicDirectory(baseDirectory), subDirectory)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, safeName)
        return runCatching {
            PendingOutput(Uri.fromFile(file), FileOutputStream(file), file, false, context)
        }.getOrNull()
    }

    fun saveBytes(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        subDirectory: String = "IO-Tools"
    ): Uri? {
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
            pending.output.use { it.write(bytes) }
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
            pending.output.use { output -> input.copyTo(output, 64 * 1024) }
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
        val bytes = ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(format, quality, out)) return null
            out.toByteArray()
        }
        return saveBytes(context, fileName, mime, bytes, subDirectory)
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

    private fun uniqueName(context: Context, original: String, subDirectory: String): String {
        val safe = sanitizeFileName(original)
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var candidate = safe
        var index = 1

        if (Build.VERSION.SDK_INT >= 29) {
            while (downloadNameExists(context, candidate, subDirectory) && index < 500) {
                candidate = "$stem ($index)$ext"
                index++
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), subDirectory)
            while (File(dir, candidate).exists() && index < 500) {
                candidate = "$stem ($index)$ext"
                index++
            }
        }
        return candidate
    }

    private fun downloadNameExists(context: Context, name: String, subDirectory: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            val args = arrayOf(name, "${Environment.DIRECTORY_DOWNLOADS}/$subDirectory")
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                args,
                null
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }
}
