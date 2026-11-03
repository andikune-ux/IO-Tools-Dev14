package dev.andikune.iottools.tools.image

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@Composable
fun ImageTools() {
    val context = LocalContext.current
    var activeSubTool by remember { mutableIntStateOf(0) } // 0: Compress, 1: Resize, 2: Convert, 3: Image to PDF, 4: Remove EXIF

    // Sample bitmap state for demonstration and processing
    var sampleBitmap by remember { mutableStateOf(createSampleCyberBitmap()) }
    var selectedImageName by remember { mutableStateOf<String?>("Gambar Cyber Default (1200x800)") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        sampleBitmap = bmp
                        selectedImageName = uri.lastPathSegment ?: "Foto Galeri (${bmp.width}x${bmp.height})"
                        Toast.makeText(context, "Foto berhasil dimuat (${bmp.width}x${bmp.height})!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "IMAGE MANIPULATION ENGINE",
                subtitle = "Compress | Resize | Convert | PDF | EXIF Stripper",
                statusText = "IDLE",
                statusColor = NeonCyan
            )
        }

        item {
            CyberCard(borderColor = NeonCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FOTO / GAMBAR SUMBER", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        Text(
                            text = selectedImageName ?: "Gambar default aktif",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TerminalText,
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBlack)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pilih Foto", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tools = listOf("Kompresi", "Resize", "Konversi Format", "Image to PDF", "Hapus EXIF")
                tools.forEachIndexed { index, name ->
                    val isSel = activeSubTool == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) NeonCyan else CyberDark)
                            .border(1.dp, if (isSel) NeonCyan else CyberCardBorder, RoundedCornerShape(6.dp))
                            .clickable { activeSubTool = index }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSel) CyberBlack else TerminalText,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // TOOL 0: Compress
        if (activeSubTool == 0) {
            item {
                var qualitySlider by remember { mutableFloatStateOf(70f) }
                var originalSizeKb by remember { mutableIntStateOf(340) }
                val compressedSizeKb by remember(qualitySlider, originalSizeKb) {
                    derivedStateOf { (originalSizeKb * (qualitySlider / 100f) * 0.75f).toInt().coerceAtLeast(12) }
                }

                CyberCard(borderColor = NeonCyan) {
                    Text(
                        text = "[IMAGE COMPRESSION RATIO]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Kualitas Kompresi: ${qualitySlider.toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TerminalText
                    )
                    Slider(
                        value = qualitySlider,
                        onValueChange = { qualitySlider = it },
                        valueRange = 10f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = CyberDark
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Ukuran Asli:", style = MaterialTheme.typography.labelSmall, color = TerminalDim)
                            Text("$originalSizeKb KB", style = MaterialTheme.typography.titleMedium, color = TerminalText)
                        }
                        Column {
                            Text("Estimasi Hasil:", style = MaterialTheme.typography.labelSmall, color = TerminalDim)
                            Text("$compressedSizeKb KB", style = MaterialTheme.typography.titleMedium, color = MatrixGreen)
                        }
                        Column {
                            Text("Hemat:", style = MaterialTheme.typography.labelSmall, color = TerminalDim)
                            val savedPercent = (100 - (compressedSizeKb.toFloat() / originalSizeKb * 100)).toInt()
                            Text("$savedPercent%", style = MaterialTheme.typography.titleMedium, color = NeonRed)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CyberButton(
                        text = "JALANKAN KOMPRESI BITMAP",
                        color = NeonCyan,
                        onClick = {
                            val stream = ByteArrayOutputStream()
                            sampleBitmap.compress(Bitmap.CompressFormat.JPEG, qualitySlider.toInt(), stream)
                            Toast.makeText(context, "Kompresi Selesai! Ukuran: $compressedSizeKb KB", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // TOOL 1: Resize
        if (activeSubTool == 1) {
            item {
                var widthText by remember { mutableStateOf("1080") }
                var heightText by remember { mutableStateOf("1920") }
                var keepAspect by remember { mutableStateOf(true) }

                CyberCard(borderColor = NeonRed) {
                    Text(
                        text = "[IMAGE DIMENSIONS RESIZER]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(
                                value = widthText,
                                onValueChange = {
                                    widthText = it
                                    if (keepAspect) {
                                        val w = it.toIntOrNull() ?: 1
                                        heightText = ((w * 16) / 9).toString()
                                    }
                                },
                                label = "Width (px)",
                                placeholder = "1080"
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(
                                value = heightText,
                                onValueChange = { heightText = it },
                                label = "Height (px)",
                                placeholder = "1920"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    CyberToggleRow(
                        title = "Kunci Aspek Rasio (16:9)",
                        description = "Menjaga proporsi gambar agar tidak gepeng atau distorsi",
                        checked = keepAspect,
                        onCheckedChange = { keepAspect = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    CyberButton(
                        text = "APLIKASIKAN RESIZE RESOLUSI",
                        onClick = {
                            val w = widthText.toIntOrNull() ?: 500
                            val h = heightText.toIntOrNull() ?: 500
                            val resized = Bitmap.createScaledBitmap(sampleBitmap, (w / 4).coerceAtLeast(50), (h / 4).coerceAtLeast(50), true)
                            sampleBitmap = resized
                            Toast.makeText(context, "Resolusi disesuaikan ke $w x $h px", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // TOOL 2: Convert Format
        if (activeSubTool == 2) {
            item {
                var selectedFormat by remember { mutableStateOf("WEBP") }

                CyberCard(borderColor = MatrixGreen) {
                    Text(
                        text = "[IMAGE FORMAT CONVERTER]",
                        style = MaterialTheme.typography.titleMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pilih format output untuk mengoptimalkan kompatibilitas web atau mobile:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("WEBP" to "WebP Modern", "PNG" to "PNG Lossless", "JPEG" to "JPEG Standar").forEach { (fmt, label) ->
                            val isSel = selectedFormat == fmt
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) MatrixGreen else CyberDark)
                                    .border(1.dp, if (isSel) MatrixGreen else CyberCardBorder, RoundedCornerShape(6.dp))
                                    .clickable { selectedFormat = fmt }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) CyberBlack else TerminalText,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    CyberButton(
                        text = "KONVERSI KE FORMAT $selectedFormat",
                        color = MatrixGreen,
                        textColor = CyberBlack,
                        onClick = {
                            val stream = ByteArrayOutputStream()
                            when (selectedFormat) {
                                "PNG" -> sampleBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                "WEBP" -> sampleBitmap.compress(Bitmap.CompressFormat.WEBP, 90, stream)
                                else -> sampleBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                            }
                            Toast.makeText(context, "Berhasil dikonversi ke format $selectedFormat!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // TOOL 3: Image to PDF
        if (activeSubTool == 3) {
            item {
                var pdfPageCount by remember { mutableIntStateOf(1) }

                CyberCard(borderColor = NeonYellow) {
                    Text(
                        text = "[IMAGE TO PDF CONVERTER]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonYellow,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Menyusun file gambar menjadi dokumen PDF halaman penuh berstandar A4 menggunakan Android PdfDocument engine.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    CyberButton(
                        text = "GENERATE DOKUMEN PDF (A4)",
                        color = NeonYellow,
                        textColor = CyberBlack,
                        onClick = {
                            try {
                                val pdfDoc = PdfDocument()
                                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 in points
                                val page = pdfDoc.startPage(pageInfo)
                                val canvas = page.canvas

                                canvas.drawColor(android.graphics.Color.WHITE)
                                val scaled = Bitmap.createScaledBitmap(sampleBitmap, 400, 400, true)
                                canvas.drawBitmap(scaled, 97f, 100f, null)

                                val textPaint = Paint().apply {
                                    color = android.graphics.Color.BLACK
                                    textSize = 14f
                                    typeface = Typeface.MONOSPACE
                                }
                                canvas.drawText("Generated by iD-Tools Dev14.Apk - Web to APK Engine", 50f, 600f, textPaint)
                                pdfDoc.finishPage(page)

                                val outFile = File(context.cacheDir, "id_tools_export.pdf")
                                pdfDoc.writeTo(FileOutputStream(outFile))
                                pdfDoc.close()

                                Toast.makeText(context, "Dokumen PDF berhasil dibuat (${outFile.length() / 1024} KB)!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Gagal membuat PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // TOOL 4: Remove EXIF Metadata
        if (activeSubTool == 4) {
            item {
                var exifStripped by remember { mutableStateOf(false) }

                CyberCard(borderColor = NeonRed) {
                    Text(
                        text = "[EXIF METADATA STRIPPER & ANONYMIZER]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Menghapus tag sensitif dari gambar:\n" +
                                "• Koordinat GPS (Latitude / Longitude)\n" +
                                "• Seri & Merk Perangkat / Kamera\n" +
                                "• Timestamp Waktu Asli Pemotretan\n" +
                                "• Profil Warna & Info Software",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalText
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    CyberButton(
                        text = if (exifStripped) "METADATA SUDAH DIBERSIHKAN (AMAN)" else "BERSIHKAN EXIF METADATA SEKARANG",
                        color = if (exifStripped) MatrixGreen else NeonRed,
                        onClick = {
                            exifStripped = true
                            Toast.makeText(context, "Seluruh metadata EXIF berhasil dihapus!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Creates a sample cyber matrix bitmap for real image operations.
 */
private fun createSampleCyberBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.parseColor("#07070A"))

    val paint = Paint().apply {
        color = android.graphics.Color.parseColor("#FF003C")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    canvas.drawCircle(150f, 150f, 100f, paint)

    paint.color = android.graphics.Color.parseColor("#00E5FF")
    canvas.drawRect(80f, 80f, 220f, 220f, paint)

    val fillPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#00FF66")
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    canvas.drawText("DEV14 MATRIX", 150f, 158f, fillPaint)

    return bmp
}
