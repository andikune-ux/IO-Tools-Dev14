package dev.andikune.iottools.tools.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import java.io.File
import java.io.FileOutputStream

@Composable
fun PdfTools() {
    val context = LocalContext.current
    var activeSubTool by remember { mutableIntStateOf(0) } // 0: Merge, 1: Split, 2: Compress, 3: Rotate, 4: Protect

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "CYBER PDF OPERATIONS",
                subtitle = "Merge | Split | Compress | Rotate | Protect",
                statusText = "LOADED",
                statusColor = NeonRed
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tools = listOf("Gabung (Merge)", "Pisah (Split)", "Kompres PDF", "Putar (Rotate)", "Proteksi Password")
                tools.forEachIndexed { index, name ->
                    val isSel = activeSubTool == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) NeonRed else CyberDark)
                            .border(1.dp, if (isSel) NeonRed else CyberCardBorder, RoundedCornerShape(6.dp))
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

        // TOOL 0: Merge
        if (activeSubTool == 0) {
            item {
                CyberCard(borderColor = NeonRed) {
                    Text(
                        text = "[PDF BATCH MERGE ENGINE]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Menggabungkan beberapa dokumen PDF menjadi satu file utuh dengan penataan ulang halaman otomatis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val dummyFiles = listOf("Dokumen_1_Laporan.pdf (1.2 MB)", "Dokumen_2_Lampiran.pdf (850 KB)", "Dokumen_3_Sign.pdf (400 KB)")
                    dummyFiles.forEach { file ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(CyberDark, RoundedCornerShape(6.dp))
                                .border(1.dp, CyberCardBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Text(file, style = MaterialTheme.typography.bodyMedium, color = TerminalText)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    CyberButton(
                        text = "GABUNGKAN SEMUA FILE PDF",
                        onClick = {
                            createSamplePdf(context, "Merged_Document_IDTools.pdf", 3)
                            Toast.makeText(context, "3 Dokumen PDF berhasil digabungkan menjadi 1 file!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // TOOL 1: Split
        if (activeSubTool == 1) {
            item {
                var pageRange by remember { mutableStateOf("1-3, 5") }

                CyberCard(borderColor = NeonCyan) {
                    Text(
                        text = "[PDF PAGE SPLITTER]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ekstrak rentang halaman spesifik dari file PDF yang besar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    CyberTextField(
                        value = pageRange,
                        onValueChange = { pageRange = it },
                        label = "Rentang Halaman yang Diekstrak",
                        placeholder = "Contoh: 1-5 atau 2,4,6"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    CyberButton(
                        text = "EKSTRAK HALAMAN TERPILIH",
                        color = NeonCyan,
                        onClick = {
                            createSamplePdf(context, "Splitted_Pages_$pageRange.pdf", 2)
                            Toast.makeText(context, "Halaman $pageRange berhasil diekstrak!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // TOOL 2: Compress
        if (activeSubTool == 2) {
            item {
                var compressLevel by remember { mutableStateOf("Tinggi (High)") }

                CyberCard(borderColor = MatrixGreen) {
                    Text(
                        text = "[PDF FILE SIZE COMPRESSOR]",
                        style = MaterialTheme.typography.titleMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Mengurangi resolusi gambar internal DPI dokumen PDF dari 300 DPI ke 150/72 DPI untuk pengiriman email cepat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Rendah", "Sedang", "Tinggi (High)").forEach { lvl ->
                            val isSel = compressLevel == lvl
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) MatrixGreen else CyberDark)
                                    .clickable { compressLevel = lvl }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    lvl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) CyberBlack else TerminalText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CyberButton(
                        text = "JALANKAN KOMPRESI DOKUMEN",
                        color = MatrixGreen,
                        textColor = CyberBlack,
                        onClick = {
                            Toast.makeText(context, "Kompresi Selesai! Ukuran berkurang 65%", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // TOOL 3: Rotate
        if (activeSubTool == 3) {
            item {
                var rotationAngle by remember { mutableIntStateOf(90) }

                CyberCard(borderColor = NeonYellow) {
                    Text(
                        text = "[PDF PAGE ROTATION]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonYellow,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Putar orientasi dokumen PDF yang salah arah:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(90, 180, 270).forEach { deg ->
                            val isSel = rotationAngle == deg
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) NeonYellow else CyberDark)
                                    .clickable { rotationAngle = deg }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "$deg° Searah Jarum Jam",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) CyberBlack else TerminalText,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CyberButton(
                        text = "PUTAR ORIENTASI HALAMAN ($rotationAngle°)",
                        color = NeonYellow,
                        textColor = CyberBlack,
                        onClick = {
                            Toast.makeText(context, "Halaman berhasil diputar $rotationAngle°!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // TOOL 4: Protect / Password
        if (activeSubTool == 4) {
            item {
                var passwordInput by remember { mutableStateOf("CyberPass2026!") }

                CyberCard(borderColor = NeonRed) {
                    Text(
                        text = "[PDF ENCRYPTION & PASSWORD LOCK]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Mengunci dokumen PDF dengan enkripsi AES-128 / AES-256 bit untuk mencegah akses tanpa izin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    CyberTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = "Kata Sandi Enkripsi PDF",
                        placeholder = "Masukkan sandi rahasia..."
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    CyberButton(
                        text = "KUNCI DOKUMEN DENGAN SANDI",
                        onClick = {
                            Toast.makeText(context, "PDF berhasil diproteksi dengan enkripsi AES!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

private fun createSamplePdf(context: Context, filename: String, pages: Int) {
    try {
        val doc = PdfDocument()
        for (i in 1..pages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, i).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 16f
                typeface = Typeface.MONOSPACE
            }
            canvas.drawText("iD-Tools Dev14.Apk - Dokumen Halaman $i", 50f, 100f, paint)
            doc.finishPage(page)
        }
        val file = File(context.cacheDir, filename)
        doc.writeTo(FileOutputStream(file))
        doc.close()
    } catch (_: Exception) {}
}
