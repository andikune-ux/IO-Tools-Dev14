package dev.andikune.iottools.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dev.andikune.iottools.tools.common.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.ceil

/**
 * Real PDF operations powered by PDFBox-Android plus Android's PdfRenderer.
 * Intermediate PDF output is streamed through a cache file to avoid duplicating
 * large documents in ByteArrayOutputStream.
 */
object PdfEngine {

    private const val MAX_SPLIT_OUTPUTS = 200
    private const val MAX_SPLIT_RANGE_TEXT_LENGTH = 2048
    private const val MAX_SPLIT_PAGE_REFERENCES = 5000L
    private const val MAX_MERGE_INPUTS = 50
    private const val MAX_COMPRESS_PAGES = 300
    private const val MAX_RENDER_SIDE = 8192
    private const val MAX_RENDER_PIXELS = 16_000_000L

    @Volatile
    private var initialised = false

    private fun ensureInit(context: Context) {
        if (!initialised) {
            synchronized(this) {
                if (!initialised) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    initialised = true
                }
            }
        }
    }

    private fun memoryUsage(context: Context): MemoryUsageSetting =
        MemoryUsageSetting.setupMixed(8L * 1024L * 1024L).setTempDir(context.cacheDir)

    internal fun validateRotationDegrees(degrees: Int): String? =
        if (degrees in setOf(90, 180, 270)) null else "Rotasi harus 90, 180, atau 270 derajat."

    internal fun validateProtectionPasswords(userPassword: String, ownerPassword: String): String? {
        if (userPassword.length !in 8..128) {
            return "Password pengguna harus berisi 8–128 karakter."
        }
        if (ownerPassword.length > 128) return "Password owner maksimal 128 karakter."
        return null
    }

    internal fun validateCompressionSettings(dpi: Int, quality: Int): String? {
        if (dpi !in 36..300) return "DPI kompresi harus berada di antara 36 dan 300."
        if (quality !in 20..95) return "Kualitas JPEG harus berada di antara 20 dan 95."
        return null
    }

    internal fun calculateRenderDimensions(pageWidthPoints: Int, pageHeightPoints: Int, dpi: Int): Pair<Int, Int> {
        val settingError = validateCompressionSettings(dpi, 45)
        if (settingError != null) throw IllegalArgumentException(settingError)
        if (pageWidthPoints <= 0 || pageHeightPoints <= 0) {
            throw IllegalArgumentException("Dimensi halaman PDF tidak valid.")
        }
        val width = ceil(pageWidthPoints.toDouble() * dpi / 72.0).toLong()
        val height = ceil(pageHeightPoints.toDouble() * dpi / 72.0).toLong()
        if (width > MAX_RENDER_SIDE || height > MAX_RENDER_SIDE ||
            width * height > MAX_RENDER_PIXELS
        ) {
            throw IllegalArgumentException("Halaman terlalu besar untuk dirender dengan aman pada DPI yang dipilih.")
        }
        return width.toInt().coerceAtLeast(1) to height.toInt().coerceAtLeast(1)
    }

    suspend fun pageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        ensureInit(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input, memoryUsage(context)).use { it.numberOfPages }
        } ?: throw IllegalStateException("PDF tidak dapat dibuka.")
    }

    suspend fun merge(context: Context, uris: List<Uri>, outputName: String): Uri = withContext(Dispatchers.IO) {
        if (uris.size < 2) throw IllegalArgumentException("Pilih minimal dua file PDF.")
        if (uris.size > MAX_MERGE_INPUTS) {
            throw IllegalArgumentException("Maksimal $MAX_MERGE_INPUTS file PDF dapat digabung sekaligus.")
        }
        ensureInit(context)
        PDDocument(memoryUsage(context)).use { output ->
            uris.forEach { uri ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    PDDocument.load(input, memoryUsage(context)).use { source ->
                        for (page in source.pages) output.importPage(page)
                    }
                } ?: throw IllegalStateException("Salah satu PDF tidak dapat dibuka.")
            }
            savePdf(context, outputName) { output.save(it) }
        }
    }

    suspend fun split(context: Context, uri: Uri, ranges: String, outputName: String): List<Uri> =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input, memoryUsage(context)).use { source ->
                    val total = source.numberOfPages
                    if (total <= 0) throw IllegalStateException("PDF tidak memiliki halaman.")
                    val parts = parseRanges(ranges, total)
                    if (parts.size > MAX_SPLIT_OUTPUTS) {
                        throw IllegalArgumentException("Maksimal $MAX_SPLIT_OUTPUTS output per operasi pemisahan.")
                    }
                    val base = outputName.substringBeforeLast('.', outputName)
                    val createdOutputs = mutableListOf<Uri>()
                    try {
                        parts.map { part ->
                            PDDocument(memoryUsage(context)).use { target ->
                                for (pageIndex in part) target.importPage(source.getPage(pageIndex))
                                val label = if (part.first == part.last) {
                                    "${part.first + 1}"
                                } else {
                                    "${part.first + 1}-${part.last + 1}"
                                }
                                savePdf(context, "${base}_$label.pdf") { target.save(it) }
                                    .also(createdOutputs::add)
                            }
                        }
                    } catch (error: Exception) {
                        // A failed multi-output split must not leave a misleading partial result set.
                        createdOutputs.forEach { uri -> deleteOutput(context, uri) }
                        throw error
                    }
                }
            } ?: throw IllegalStateException("PDF tidak dapat dibuka.")
        }

    suspend fun rotate(context: Context, uri: Uri, degrees: Int, outputName: String): Uri =
        withContext(Dispatchers.IO) {
            validateRotationDegrees(degrees)?.let { throw IllegalArgumentException(it) }
            ensureInit(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input, memoryUsage(context)).use { document ->
                    for (page in document.pages) {
                        val next = (page.rotation + degrees) % 360
                        page.rotation = if (next < 0) next + 360 else next
                    }
                    savePdf(context, outputName) { document.save(it) }
                }
            } ?: throw IllegalStateException("PDF tidak dapat dibuka.")
        }

    suspend fun protect(
        context: Context,
        uri: Uri,
        userPassword: String,
        ownerPassword: String,
        outputName: String
    ): Uri = withContext(Dispatchers.IO) {
        validateProtectionPasswords(userPassword, ownerPassword)?.let {
            throw IllegalArgumentException(it)
        }
        ensureInit(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input, memoryUsage(context)).use { document ->
                val permission = AccessPermission().apply {
                    setCanPrint(false)
                    setCanExtractContent(false)
                    setCanModify(false)
                    setCanModifyAnnotations(false)
                    setCanFillInForm(false)
                    setCanAssembleDocument(false)
                    setCanExtractForAccessibility(false)
                }
                val policy = StandardProtectionPolicy(
                    ownerPassword.ifBlank { userPassword },
                    userPassword,
                    permission
                ).apply {
                    encryptionKeyLength = 256
                    setPreferAES(true)
                }
                document.protect(policy)
                savePdf(context, outputName) { document.save(it) }
            }
        } ?: throw IllegalStateException("PDF tidak dapat dibuka.")
    }

    /**
     * Rasterises each page at [dpi], stores it as JPEG at [quality], and rebuilds
     * a PDF containing those JPEGs. This is a genuine recompression pass.
     */
    suspend fun compress(
        context: Context,
        uri: Uri,
        dpi: Int = 120,
        quality: Int = 45,
        outputName: String
    ): Pair<Uri, Long> = withContext(Dispatchers.IO) {
        validateCompressionSettings(dpi, quality)?.let { throw IllegalArgumentException(it) }
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("PDF tidak dapat dibuka.")
        val sourceSize = descriptor.statSize.coerceAtLeast(0L)
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) throw IllegalStateException("PDF tidak memiliki halaman.")
                if (renderer.pageCount > MAX_COMPRESS_PAGES) {
                    throw IllegalArgumentException("Kompresi dibatasi hingga $MAX_COMPRESS_PAGES halaman per operasi.")
                }
                PDDocument(memoryUsage(context)).use { output ->
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val (width, height) = calculateRenderDimensions(page.width, page.height, dpi)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val jpeg = try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                ByteArrayOutputStream().use { out ->
                                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                                        throw IllegalStateException("Encoder JPEG gagal pada halaman ${index + 1}.")
                                    }
                                    out.toByteArray()
                                }
                            } finally {
                                bitmap.recycle()
                            }

                            val pdfPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                            output.addPage(pdfPage)
                            val image = PDImageXObject.createFromByteArray(output, jpeg, "page_${index + 1}")
                            PDPageContentStream(output, pdfPage).use { content ->
                                content.drawImage(image, 0f, 0f, pdfPage.mediaBox.width, pdfPage.mediaBox.height)
                            }
                        }
                    }
                    val saved = savePdf(context, outputName) { output.save(it) }
                    saved to sourceSize
                }
            }
        }
    }

    fun parseRanges(text: String, totalPages: Int): List<IntRange> {
        if (totalPages <= 0) throw IllegalArgumentException("PDF tidak memiliki halaman.")
        val cleaned = text.trim()
        if (cleaned.length > MAX_SPLIT_RANGE_TEXT_LENGTH) {
            throw IllegalArgumentException("Teks rentang halaman terlalu panjang.")
        }
        if (cleaned.isEmpty()) {
            if (totalPages > MAX_SPLIT_OUTPUTS) {
                throw IllegalArgumentException("Pemisahan semua halaman dibatasi hingga $MAX_SPLIT_OUTPUTS output.")
            }
            return (0 until totalPages).map { it..it }
        }

        val rawParts = cleaned.split(',')
        if (rawParts.size > MAX_SPLIT_OUTPUTS) {
            throw IllegalArgumentException("Maksimal $MAX_SPLIT_OUTPUTS rentang halaman per operasi.")
        }
        val ranges = mutableListOf<IntRange>()
        rawParts.forEach { rawPart ->
            val part = rawPart.trim()
            if (part.isEmpty()) return@forEach
            val pieces = part.split('-', limit = 2)
            val start = pieces[0].trim().toIntOrNull()
                ?: throw IllegalArgumentException("Rentang '$part' tidak valid.")
            val end = if (pieces.size == 2 && pieces[1].trim().isNotEmpty()) {
                pieces[1].trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Rentang '$part' tidak valid.")
            } else {
                start
            }
            if (start < 1 || end > totalPages || start > end) {
                throw IllegalArgumentException("Halaman harus di antara 1 dan $totalPages.")
            }
            ranges.add((start - 1)..(end - 1))
        }
        if (ranges.isEmpty()) throw IllegalArgumentException("Rentang halaman kosong.")
        val referencedPages = ranges.sumOf { (it.last.toLong() - it.first.toLong() + 1L) }
        if (referencedPages > MAX_SPLIT_PAGE_REFERENCES) {
            throw IllegalArgumentException("Maksimal $MAX_SPLIT_PAGE_REFERENCES halaman dapat dipisah dalam satu operasi.")
        }
        return ranges
    }

    private fun deleteOutput(context: Context, uri: Uri) {
        runCatching {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        }
    }

    private fun savePdf(context: Context, fileName: String, writer: (OutputStream) -> Unit): Uri {
        val temp = File.createTempFile("pdf_export_", ".pdf", context.cacheDir)
        try {
            FileOutputStream(temp).use(writer)
            val safeName = MediaStoreSaver.sanitizeFileName(fileName, "dokumen.pdf")
            return temp.inputStream().use { input ->
                MediaStoreSaver.saveStream(context, safeName, "application/pdf", input)
            } ?: throw IllegalStateException("Gagal menyimpan PDF ke folder Download.")
        } finally {
            temp.delete()
        }
    }
}
