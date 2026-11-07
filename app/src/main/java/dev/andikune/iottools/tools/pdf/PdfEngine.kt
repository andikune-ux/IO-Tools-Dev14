package dev.andikune.iottools.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
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

/**
 * Real PDF operations powered by PDFBox-Android plus Android's PdfRenderer.
 * No placeholder/cached sample files are produced here.
 */
object PdfEngine {

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

    suspend fun pageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        ensureInit(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { it.numberOfPages }
        } ?: throw IllegalStateException("PDF tidak dapat dibuka.")
    }

    suspend fun merge(context: Context, uris: List<Uri>, outputName: String): Uri = withContext(Dispatchers.IO) {
        if (uris.size < 2) throw IllegalArgumentException("Pilih minimal dua file PDF.")
        ensureInit(context)
        PDDocument().use { output ->
            uris.forEach { uri ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    PDDocument.load(input).use { source ->
                        for (page in source.pages) {
                            output.importPage(page)
                        }
                    }
                } ?: throw IllegalStateException("Salah satu PDF tidak dapat dibuka.")
            }
            val bytes = ByteArrayOutputStream().use { out ->
                output.save(out)
                out.toByteArray()
            }
            savePdf(context, outputName, bytes)
        }
    }

    suspend fun split(context: Context, uri: Uri, ranges: String, outputName: String): List<Uri> =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            val sourceBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("PDF tidak dapat dibuka.")
            PDDocument.load(sourceBytes).use { source ->
                val total = source.numberOfPages
                if (total <= 0) throw IllegalStateException("PDF tidak memiliki halaman.")
                val parts = parseRanges(ranges, total)
                val base = outputName.substringBeforeLast('.', outputName)
                parts.mapIndexed { index, part ->
                    PDDocument().use { target ->
                        for (pageIndex in part) {
                            target.importPage(source.getPage(pageIndex))
                        }
                        val bytes = ByteArrayOutputStream().use { out ->
                            target.save(out)
                            out.toByteArray()
                        }
                        val label = if (part.first == part.last) {
                            "${part.first + 1}"
                        } else {
                            "${part.first + 1}-${part.last + 1}"
                        }
                        savePdf(context, "${base}_$label.pdf", bytes)
                    }
                }
            }
        }

    suspend fun rotate(context: Context, uri: Uri, degrees: Int, outputName: String): Uri =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { document ->
                    for (page in document.pages) {
                        val next = (page.rotation + degrees) % 360
                        page.rotation = if (next < 0) next + 360 else next
                    }
                    val bytes = ByteArrayOutputStream().use { out ->
                        document.save(out)
                        out.toByteArray()
                    }
                    savePdf(context, outputName, bytes)
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
        if (userPassword.isBlank()) throw IllegalArgumentException("Password pengguna tidak boleh kosong.")
        ensureInit(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
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
                val bytes = ByteArrayOutputStream().use { out ->
                    document.save(out)
                    out.toByteArray()
                }
                savePdf(context, outputName, bytes)
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
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("PDF tidak dapat dibuka.")
        val sourceSize = runCatching { descriptor.statSize }.getOrDefault(0L)
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) throw IllegalStateException("PDF tidak memiliki halaman.")
                PDDocument().use { output ->
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val scale = dpi / 72f
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val jpeg = ByteArrayOutputStream().use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(20, 95), out)
                                out.toByteArray()
                            }
                            bitmap.recycle()

                            val pdfPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                            output.addPage(pdfPage)
                            val image = PDImageXObject.createFromByteArray(output, jpeg, "page_${index + 1}")
                            PDPageContentStream(output, pdfPage).use { content ->
                                content.drawImage(
                                    image,
                                    0f,
                                    0f,
                                    pdfPage.mediaBox.width,
                                    pdfPage.mediaBox.height
                                )
                            }
                        }
                    }
                    val bytes = ByteArrayOutputStream().use { out ->
                        output.save(out)
                        out.toByteArray()
                    }
                    val saved = savePdf(context, outputName, bytes)
                    saved to sourceSize
                }
            }
        }
    }

    fun parseRanges(text: String, totalPages: Int): List<IntRange> {
        if (totalPages <= 0) throw IllegalArgumentException("PDF tidak memiliki halaman.")
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return (0 until totalPages).map { it..it }

        val ranges = mutableListOf<IntRange>()
        cleaned.split(',').forEach { rawPart ->
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
        return ranges
    }

    private fun savePdf(context: Context, fileName: String, bytes: ByteArray): Uri {
        return MediaStoreSaver.saveBytes(
            context,
            MediaStoreSaver.sanitizeFileName(fileName, "dokumen.pdf"),
            "application/pdf",
            bytes
        ) ?: throw IllegalStateException("Gagal menyimpan PDF ke folder Download.")
    }
}
