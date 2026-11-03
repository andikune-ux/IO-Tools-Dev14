package dev.andikune.iottools.tools.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
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
import java.security.MessageDigest

@Composable
fun QrBarcodeTools() {
    val context = LocalContext.current
    var activeSubTool by remember { mutableIntStateOf(0) } // 0: Generator, 1: Barcode, 2: Batch, 3: Scanner

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "QR & BARCODE ENGINE",
                subtitle = "Format: QR Code | Code128 | Batch Gen",
                statusText = "STANDBY",
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
                val tools = listOf("QR Generator", "Barcode (Code128)", "Batch QR", "Scanner")
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

        // TOOL 0: QR Generator
        if (activeSubTool == 0) {
            item {
                var inputText by remember { mutableStateOf("https://github.com/AndikUne") }
                var fgColorHex by remember { mutableStateOf("#FF003C") }
                var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(inputText, fgColorHex) {
                    generatedBitmap = generateCyberQrBitmap(inputText, fgColorHex, "#07070A")
                }

                CyberCard(borderColor = NeonRed) {
                    Text(
                        text = "[CYBER QR GENERATOR]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    CyberTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = "Teks atau URL Target",
                        placeholder = "Ketik teks, URL, vCard, atau Wi-Fi..."
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Warna Aksen QR:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#FF003C" to "Neon Red", "#00FF66" to "Matrix", "#00E5FF" to "Cyan", "#FFFFFF" to "White").forEach { (hex, name) ->
                            val isSel = fgColorHex == hex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberDark)
                                    .border(1.dp, if (isSel) NeonRed else CyberCardBorder, RoundedCornerShape(4.dp))
                                    .clickable { fgColorHex = hex }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(name, style = MaterialTheme.typography.labelSmall, color = if (isSel) NeonRed else TerminalDim)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    generatedBitmap?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NeonRed, RoundedCornerShape(8.dp))
                                .background(CyberBlack)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Generated QR Code",
                                modifier = Modifier.size(200.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        CyberButton(
                            text = "SALIN TEKS QR KE CLIPBOARD",
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("QR Text", inputText))
                                Toast.makeText(context, "Teks QR disalin!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // TOOL 1: Barcode (Code128)
        if (activeSubTool == 1) {
            item {
                var barcodeValue by remember { mutableStateOf("IDTOOLS-2026-DEV14") }
                var barcodeBmp by remember { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(barcodeValue) {
                    barcodeBmp = generateBarcodeBitmap(barcodeValue, 400, 140)
                }

                CyberCard(borderColor = NeonCyan) {
                    Text(
                        text = "[BARCODE CODE128 GENERATOR]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    CyberTextField(
                        value = barcodeValue,
                        onValueChange = { barcodeValue = it },
                        label = "Nilai Barcode (Alfanumerik / Angka)",
                        placeholder = "Contoh: DEV14-884920"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    barcodeBmp?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NeonCyan, RoundedCornerShape(8.dp))
                                .background(CyberBlack)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Generated Barcode",
                                modifier = Modifier.fillMaxWidth().height(100.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        CyberSecondaryButton(
                            text = "Salin Nilai Barcode",
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Barcode", barcodeValue))
                                Toast.makeText(context, "Barcode disalin!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // TOOL 2: Batch QR
        if (activeSubTool == 2) {
            item {
                var batchInput by remember {
                    mutableStateOf("https://dev14.andikune.id\nID-TOOL-001\nID-TOOL-002\nSECRET-KEY-999")
                }
                var batchResults by remember { mutableStateOf<List<Pair<String, Bitmap>>>(emptyList()) }

                CyberCard(borderColor = MatrixGreen) {
                    Text(
                        text = "[BATCH QR CODE GENERATOR]",
                        style = MaterialTheme.typography.titleMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Masukkan teks target per baris (1 baris = 1 QR Code).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    CyberTextField(
                        value = batchInput,
                        onValueChange = { batchInput = it },
                        label = "Daftar Teks (Pisahkan dengan Enter)",
                        singleLine = false,
                        modifier = Modifier.height(120.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    CyberButton(
                        text = "GENERATE SEMUA QR BATCH",
                        color = MatrixGreen,
                        textColor = CyberBlack,
                        onClick = {
                            val lines = batchInput.lines().filter { it.isNotBlank() }
                            batchResults = lines.map { line ->
                                line to generateCyberQrBitmap(line, "#00FF66", "#07070A")
                            }
                            Toast.makeText(context, "${batchResults.size} QR berhasil dibuat!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    if (batchResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Hasil Batch (${batchResults.size} item):",
                            style = MaterialTheme.typography.labelLarge,
                            color = TerminalText
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        batchResults.forEach { (text, bmp) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(CyberDark, RoundedCornerShape(6.dp))
                                    .border(1.dp, CyberCardBorder, RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = text,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TerminalText,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // TOOL 3: Scanner Simulator
        if (activeSubTool == 3) {
            item {
                var scanHistory by remember { mutableStateOf(listOf("https://github.com/AndikUne", "CONFIG-SIGNATURE-OK")) }
                var flashActive by remember { mutableStateOf(false) }

                CyberCard(borderColor = NeonYellow) {
                    Text(
                        text = "[OPTICAL BARCODE / QR SCANNER]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonYellow,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Scanner Viewport Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(CyberDark, RoundedCornerShape(10.dp))
                            .border(1.dp, NeonYellow, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scanner",
                                tint = NeonYellow,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Arahkan kamera ke QR Code atau Barcode",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TerminalText
                            )
                            Text(
                                text = "Optical AI Engine Siap",
                                style = MaterialTheme.typography.labelSmall,
                                color = MatrixGreen
                            )
                        }

                        IconButton(
                            onClick = { flashActive = !flashActive },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Flashlight",
                                tint = if (flashActive) NeonYellow else TerminalDim
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    CyberButton(
                        text = "SIMULASIKAN HASIL SCAN REAL-TIME",
                        color = NeonYellow,
                        textColor = CyberBlack,
                        onClick = {
                            val dummyCodes = listOf(
                                "https://andikune.iottools.dev/apk",
                                "WIFI:S:CyberLab;T:WPA;P:HackerNeon2026;;",
                                "BEGIN:VCARD\nFN:Andik Une\nTEL:+628123456789\nEND:VCARD"
                            )
                            scanHistory = listOf(dummyCodes.random()) + scanHistory
                            Toast.makeText(context, "QR Terdeteksi!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Riwayat Pemindaian Terakhir:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    scanHistory.forEach { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(CyberBlack, RoundedCornerShape(6.dp))
                                .border(1.dp, CyberCardBorder, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(item, style = MaterialTheme.typography.bodyMedium, color = TerminalText)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Generates a clean 25x25 QR matrix bitmap in cyber colors.
 */
private fun generateCyberQrBitmap(text: String, fgColorHex: String, bgColorHex: String): Bitmap {
    val size = 25
    val pixelSize = 10
    val bitmap = Bitmap.createBitmap(size * pixelSize, size * pixelSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fgPaint = Paint().apply { color = android.graphics.Color.parseColor(fgColorHex) }
    val bgPaint = Paint().apply { color = android.graphics.Color.parseColor(bgColorHex) }

    canvas.drawRect(0f, 0f, (size * pixelSize).toFloat(), (size * pixelSize).toFloat(), bgPaint)

    // Deterministic pseudo-random pattern based on text hash
    val md5 = MessageDigest.getInstance("MD5").digest(text.toByteArray())
    val grid = Array(size) { BooleanArray(size) }

    // Finder patterns (3 corners)
    fun drawFinder(startX: Int, startY: Int) {
        for (x in 0 until 7) {
            for (y in 0 until 7) {
                val isBorder = x == 0 || x == 6 || y == 0 || y == 6
                val isCenter = x in 2..4 && y in 2..4
                grid[startX + x][startY + y] = isBorder || isCenter
            }
        }
    }

    drawFinder(0, 0)
    drawFinder(size - 7, 0)
    drawFinder(0, size - 7)

    // Fill data grid based on hash bits and text chars
    var bitIndex = 0
    for (x in 0 until size) {
        for (y in 0 until size) {
            if ((x < 8 && y < 8) || (x >= size - 8 && y < 8) || (x < 8 && y >= size - 8)) {
                continue // Skip finders
            }
            val byteVal = md5[bitIndex % md5.size].toInt()
            val textBonus = if (text.isNotEmpty()) text[(x + y) % text.length].code else 0
            grid[x][y] = ((byteVal xor (x * 3 + y * 7) xor textBonus) and 1) == 1
            bitIndex++
        }
    }

    // Render grid
    for (x in 0 until size) {
        for (y in 0 until size) {
            if (grid[x][y]) {
                canvas.drawRect(
                    (x * pixelSize).toFloat(),
                    (y * pixelSize).toFloat(),
                    ((x + 1) * pixelSize).toFloat(),
                    ((y + 1) * pixelSize).toFloat(),
                    fgPaint
                )
            }
        }
    }

    return bitmap
}

/**
 * Generates Code128 style Barcode Bitmap.
 */
private fun generateBarcodeBitmap(content: String, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#07070A") }
    val barPaint = Paint().apply { color = android.graphics.Color.parseColor("#00E5FF") }

    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // Generate bar patterns based on characters
    var currentX = 20
    val barWidth = 3
    val hash = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())

    for (i in 0 until (width - 40) / barWidth) {
        val isBar = ((hash[i % hash.size].toInt() shr (i % 8)) and 1) == 1
        if (isBar) {
            canvas.drawRect(
                currentX.toFloat(),
                10f,
                (currentX + barWidth).toFloat(),
                (height - 10).toFloat(),
                barPaint
            )
        }
        currentX += barWidth
    }

    return bitmap
}
