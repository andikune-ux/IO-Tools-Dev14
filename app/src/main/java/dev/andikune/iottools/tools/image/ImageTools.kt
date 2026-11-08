package dev.andikune.iottools.tools.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import dev.andikune.iottools.tools.common.MediaStoreSaver
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ImageTools() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableIntStateOf(0) }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var sourceSize by remember { mutableStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var outputName by remember { mutableStateOf("") }
    var outputSize by remember { mutableStateOf(0L) }
    var exifSummary by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var imageLoadToken by remember { mutableIntStateOf(0) }

    var quality by remember { mutableIntStateOf(70) }
    var targetWidth by remember { mutableStateOf("") }
    var targetHeight by remember { mutableStateOf("") }
    var keepAspect by remember { mutableStateOf(true) }
    var convertFormat by remember { mutableIntStateOf(0) } // 0 JPEG, 1 PNG, 2 WEBP

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val token = ++imageLoadToken
            sourceUri = uri
            bitmap?.recycle()
            bitmap = null
            sourceName = ""
            sourceSize = 0L
            exifSummary = emptyList()
            message = null
            error = null
            outputName = ""
            outputSize = 0L
            scope.launch {
                val (loaded, metadata) = withContext(Dispatchers.IO) {
                    val decoded = loadImage(context, uri)
                    val details = if (decoded == null) null else Triple(
                        MediaStoreSaver.displayName(context, uri) ?: "gambar",
                        MediaStoreSaver.fileSize(context, uri),
                        readExif(context, uri)
                    )
                    decoded to details
                }
                if (token != imageLoadToken) {
                    loaded?.recycle()
                    return@launch
                }
                if (loaded == null || metadata == null) {
                    error = "Gambar tidak dapat dibaca."
                } else {
                    bitmap = loaded
                    sourceName = metadata.first
                    sourceSize = metadata.second
                    targetWidth = loaded.width.toString()
                    targetHeight = loaded.height.toString()
                    exifSummary = metadata.third
                }
            }
        }
    }

    fun perform(block: suspend () -> Pair<Uri?, String>) {
        if (busy) return
        busy = true
        error = null
        message = null
        outputName = ""
        outputSize = 0L
        scope.launch {
            try {
                val (saved, note) = withContext(Dispatchers.IO) { block() }
                if (saved == null) {
                    error = note
                } else {
                    val (name, size) = withContext(Dispatchers.IO) {
                        (MediaStoreSaver.displayName(context, saved) ?: "hasil") to
                            MediaStoreSaver.fileSize(context, saved)
                    }
                    outputName = name
                    outputSize = size
                    message = "$note Tersimpan sebagai $name (${formatBytes(outputSize)})."
                    Toast.makeText(context, "Tersimpan di Pictures/IO-Tools", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                error = e.message ?: "Operasi gambar gagal."
            } finally {
                busy = false
            }
        }
    }

    val tabs = listOf("Kompres", "Resize", "Konversi", "Ke PDF", "EXIF")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "Studio Gambar",
                subtitle = "Kompres, resize, konversi, PDF, dan pembersih EXIF",
                statusText = "REAL",
                statusColor = AccentCyan
            )
        }

        item {
            CyberCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (sourceName.isBlank()) "Belum ada gambar" else sourceName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            if (bitmap != null) "${bitmap!!.width} x ${bitmap!!.height} px • ${formatBytes(sourceSize)}" else "Pilih gambar dari galeri atau file",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                CyberButton(
                    text = if (sourceUri == null) "PILIH GAMBAR" else "GANTI GAMBAR",
                    enabled = !busy,
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
            }
        }

        val image = bitmap
        if (image != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(SlateElevated, RoundedCornerShape(14.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "Pratinjau gambar",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { index, label ->
                        FilterChip(
                            selected = activeTab == index,
                            onClick = { activeTab = index },
                            label = { Text(label) }
                        )
                    }
                }
            }

            item {
                CyberCard {
                    when (activeTab) {
                        0 -> {
                            Text("Kompres JPEG", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Kualitas $quality%. Semakin rendah, semakin kecil ukuran file.", color = TextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = quality.toFloat(),
                                onValueChange = { quality = it.toInt().coerceIn(10, 95) },
                                valueRange = 10f..95f,
                                steps = 16
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            CyberButton(
                                text = if (busy) "MEMPROSES..." else "KOMPRES & SIMPAN",
                                enabled = !busy,
                                onClick = {
                                    perform {
                                        val bytes = compressBitmap(image, Bitmap.CompressFormat.JPEG, quality)
                                        val saved = MediaStoreSaver.saveBytes(
                                            context,
                                            MediaStoreSaver.timestampName("compressed", "jpg"),
                                            "image/jpeg",
                                            bytes
                                        )
                                        saved to "Kompresi selesai."
                                    }
                                }
                            )
                        }

                        1 -> {
                            Text("Resize", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Sumber ${image.width} x ${image.height} px", color = TextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CyberTextField(
                                        value = targetWidth,
                                        onValueChange = { value ->
                                            targetWidth = value.filter { it.isDigit() }
                                            if (keepAspect) {
                                                val w = targetWidth.toIntOrNull()
                                                if (w != null && w > 0) {
                                                    targetHeight = ((w.toLong() * image.height) / image.width).toString()
                                                }
                                            }
                                        },
                                        label = "Lebar (px)"
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    CyberTextField(
                                        value = targetHeight,
                                        onValueChange = { value ->
                                            targetHeight = value.filter { it.isDigit() }
                                            if (keepAspect) {
                                                val h = targetHeight.toIntOrNull()
                                                if (h != null && h > 0) {
                                                    targetWidth = ((h.toLong() * image.width) / image.height).toString()
                                                }
                                            }
                                        },
                                        label = "Tinggi (px)"
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = keepAspect, onCheckedChange = { keepAspect = it })
                                Text("Jaga rasio aspek", color = TextSecondary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            CyberButton(
                                text = if (busy) "MEMPROSES..." else "RESIZE & SIMPAN",
                                enabled = !busy,
                                onClick = {
                                    val w = targetWidth.toIntOrNull()
                                    val h = targetHeight.toIntOrNull()
                                    val dimensionError = if (w == null || h == null) {
                                        "Lebar dan tinggi harus berupa angka yang valid."
                                    } else {
                                        validateResizeDimensions(w, h)
                                    }
                                    if (dimensionError != null) {
                                        error = dimensionError
                                        return@CyberButton
                                    }
                                    perform {
                                        val resized = Bitmap.createScaledBitmap(image, w, h, true)
                                        val bytes = try {
                                            compressBitmap(resized, Bitmap.CompressFormat.PNG, 100)
                                        } finally {
                                            if (resized !== image) resized.recycle()
                                        }
                                        val saved = MediaStoreSaver.saveBytes(
                                            context,
                                            MediaStoreSaver.timestampName("resized", "png"),
                                            "image/png",
                                            bytes
                                        )
                                        saved to "Resize ke ${w}x$h selesai."
                                    }
                                }
                            )
                        }

                        2 -> {
                            Text("Konversi format", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("JPEG", "PNG", "WEBP").forEachIndexed { index, label ->
                                    FilterChip(
                                        selected = convertFormat == index,
                                        onClick = { convertFormat = index },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            CyberButton(
                                text = if (busy) "MEMPROSES..." else "KONVERSI & SIMPAN",
                                enabled = !busy,
                                onClick = {
                                    perform {
                                        val (format, mime, ext) = when (convertFormat) {
                                            1 -> Triple(Bitmap.CompressFormat.PNG, "image/png", "png")
                                            2 -> Triple(Bitmap.CompressFormat.WEBP, "image/webp", "webp")
                                            else -> Triple(Bitmap.CompressFormat.JPEG, "image/jpeg", "jpg")
                                        }
                                        val bytes = compressBitmap(image, format, 92)
                                        val saved = MediaStoreSaver.saveBytes(
                                            context,
                                            MediaStoreSaver.timestampName("converted", ext),
                                            mime,
                                            bytes
                                        )
                                        saved to "Konversi ke $mime selesai."
                                    }
                                }
                            )
                        }

                        3 -> {
                            Text("Gambar menjadi PDF", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Satu halaman A4, gambar dipusatkan tanpa distorsi.", color = TextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            CyberButton(
                                text = if (busy) "MEMPROSES..." else "BUAT PDF",
                                enabled = !busy,
                                onClick = {
                                    perform {
                                        val bytes = imageToPdf(image)
                                        val saved = MediaStoreSaver.saveBytes(
                                            context,
                                            MediaStoreSaver.timestampName("image", "pdf"),
                                            "application/pdf",
                                            bytes
                                        )
                                        saved to "PDF dibuat."
                                    }
                                }
                            )
                        }

                        else -> {
                            Text("EXIF & privasi", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Metadata yang ditemukan dibaca dari file asli. Tombol di bawah menulis ulang gambar tanpa EXIF/GPS.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            if (exifSummary.isEmpty()) {
                                Text("Tidak ada metadata EXIF terbaca.", color = TextMuted, fontSize = 12.sp)
                            } else {
                                exifSummary.take(10).forEach { (key, value) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(key, color = TextMuted, fontSize = 11.sp)
                                        Text(value, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            CyberButton(
                                text = if (busy) "MEMPROSES..." else "HAPUS METADATA & SIMPAN",
                                enabled = !busy,
                                onClick = {
                                    perform {
                                        val bytes = compressBitmap(image, Bitmap.CompressFormat.JPEG, 95)
                                        val saved = MediaStoreSaver.saveBytes(
                                            context,
                                            MediaStoreSaver.timestampName("clean", "jpg"),
                                            "image/jpeg",
                                            bytes
                                        )
                                        saved to "EXIF dan GPS dihapus dengan encoding ulang."
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (message != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentEmeraldSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(message!!, color = AccentEmerald, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
                }
            }
        }

        if (error != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentRoseSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(error!!, color = AccentRose, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
                }
            }
        }

        if (outputName.isNotBlank()) {
            item {
                CyberCard(borderColor = AccentCyan) {
                    Text("Hasil terakhir", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Pictures/IO-Tools/$outputName", color = AccentCyan, fontSize = 12.sp)
                    Text(formatBytes(outputSize), color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

private suspend fun loadImage(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val sample = calculateImageSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@withContext null

        val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
            runCatching {
                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        applyOrientation(decoded, orientation)
    } catch (_: Exception) {
        null
    }
}

private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }
    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    } catch (_: Exception) {
        bitmap
    }
}

internal fun calculateImageSampleSize(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (
        width / sample > 4096 ||
        height / sample > 4096 ||
        (width.toLong() / sample) * (height.toLong() / sample) > 16_000_000L
    ) {
        sample *= 2
    }
    return sample
}

internal fun validateResizeDimensions(width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return "Lebar dan tinggi harus lebih dari 0."
    if (width > 8192 || height > 8192) return "Ukuran maksimal per sisi adalah 8192 piksel."
    if (width.toLong() * height.toLong() > 16_000_000L) {
        return "Ukuran hasil terlalu besar. Maksimal 16 megapiksel untuk mencegah kehabisan memori."
    }
    return null
}

private fun compressBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
    val source = if (format == Bitmap.CompressFormat.JPEG) {
        Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565).also { flattened ->
            Canvas(flattened).apply {
                drawColor(Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
        }
    } else bitmap

    return try {
        ByteArrayOutputStream().use { out ->
            if (!source.compress(format, quality.coerceIn(1, 100), out)) {
                throw IllegalStateException("Encoder gambar gagal.")
            }
            out.toByteArray()
        }
    } finally {
        if (source !== bitmap) source.recycle()
    }
}

private fun imageToPdf(bitmap: Bitmap): ByteArray {
    val document = PdfDocument()
    try {
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

        val margin = 24f
        val availableWidth = pageWidth - margin * 2
        val availableHeight = pageHeight - margin * 2
        val scale = minOf(availableWidth / bitmap.width, availableHeight / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (pageWidth - drawWidth) / 2f
        val top = (pageHeight - drawHeight) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawWidth, top + drawHeight), paint)
        document.finishPage(page)

        return ByteArrayOutputStream().use { out ->
            document.writeTo(out)
            out.toByteArray()
        }
    } finally {
        document.close()
    }
}

private suspend fun readExif(context: Context, uri: Uri): List<Pair<String, String>> = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val keys = listOf(
                "Make" to ExifInterface.TAG_MAKE,
                "Model" to ExifInterface.TAG_MODEL,
                "DateTime" to ExifInterface.TAG_DATETIME,
                "Lens" to ExifInterface.TAG_LENS_MODEL,
                "ISO" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                "Aperture" to ExifInterface.TAG_F_NUMBER,
                "Exposure" to ExifInterface.TAG_EXPOSURE_TIME,
                "GPS" to ExifInterface.TAG_GPS_LATITUDE
            )
            keys.mapNotNull { (label, tag) ->
                exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { label to it }
            }
        } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
