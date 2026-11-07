package dev.andikune.iottools.tools.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.andikune.iottools.tools.common.MediaStoreSaver
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*

private data class BarcodeKind(val label: String, val format: BarcodeFormat, val placeholder: String)

private val barcodeKinds = listOf(
    BarcodeKind("QR Code", BarcodeFormat.QR_CODE, "https://contoh.com atau teks apa saja"),
    BarcodeKind("Code 128", BarcodeFormat.CODE_128, "ABC-12345"),
    BarcodeKind("Code 39", BarcodeFormat.CODE_39, "CODE39"),
    BarcodeKind("EAN-13", BarcodeFormat.EAN_13, "8991234567890"),
    BarcodeKind("Data Matrix", BarcodeFormat.DATA_MATRIX, "DATA-MATRIX-123")
)

@Composable
fun QrBarcodeTools() {
    val context = LocalContext.current
    var activeTab by remember { mutableIntStateOf(0) }
    var content by remember { mutableStateOf("") }
    var kindIndex by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var generatedFor by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    var scanResult by remember { mutableStateOf<String?>(null) }
    var scanFormat by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text.isNullOrBlank()) {
            Toast.makeText(context, "Pemindaian dibatalkan atau tidak ada kode terbaca.", Toast.LENGTH_SHORT).show()
        } else {
            scanResult = text
            scanFormat = result.formatName ?: "KODE"
            history.add(0, text)
            if (history.size > 20) history.removeAt(history.lastIndex)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanLauncher.launch(buildScanOptions())
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk memindai kode.", Toast.LENGTH_LONG).show()
        }
    }

    fun launchScanner() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) scanLauncher.launch(buildScanOptions()) else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "QR & Barcode Studio",
                subtitle = "Generator ZXing asli + pemindai kamera perangkat",
                statusText = "ZXING",
                statusColor = AccentCyan
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Generator" to Icons.Default.QrCode2, "Scanner" to Icons.Default.QrCodeScanner).forEachIndexed { index, pair ->
                    FilterChip(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        label = { Text(pair.first) },
                        leadingIcon = { Icon(pair.second, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        if (activeTab == 0) {
            item {
                CyberCard {
                    Text("Jenis kode", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        barcodeKinds.forEachIndexed { index, kind ->
                            FilterChip(
                                selected = kindIndex == index,
                                onClick = { kindIndex = index },
                                label = { Text(kind.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    CyberTextField(
                        value = content,
                        onValueChange = {
                            content = it
                            error = null
                        },
                        label = "Isi kode",
                        placeholder = barcodeKinds[kindIndex].placeholder,
                        singleLine = false
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CyberButton(
                            text = "BUAT KODE",
                            onClick = {
                                val value = content.trim()
                                if (value.isEmpty()) {
                                    error = "Isi teks atau tautan terlebih dahulu."
                                    return@CyberButton
                                }
                                val result = runCatching {
                                    generateBarcode(value, barcodeKinds[kindIndex].format, 1024)
                                }
                                result.onSuccess {
                                    bitmap = it
                                    generatedFor = value
                                    error = null
                                }.onFailure {
                                    bitmap = null
                                    error = it.message ?: "Gagal membuat kode."
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (bitmap != null) {
                            OutlinedButton(
                                onClick = {
                                    val image = bitmap ?: return@OutlinedButton
                                    val saved = MediaStoreSaver.saveBitmap(
                                        context,
                                        image,
                                        MediaStoreSaver.timestampName("QR_Barcode", "png"),
                                        Bitmap.CompressFormat.PNG
                                    )
                                    Toast.makeText(
                                        context,
                                        if (saved != null) "Gambar tersimpan di Pictures/IO-Tools." else "Gagal menyimpan gambar.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SIMPAN")
                            }
                        }
                    }

                    if (error != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(error!!, color = AccentRose, fontSize = 12.sp)
                    }
                }
            }

            item {
                val image = bitmap
                if (image != null) {
                    CyberCard(borderColor = AccentCyan) {
                        Text(
                            text = generatedFor,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = "Kode yang dihasilkan",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, generatedFor)
                                    Toast.makeText(context, "Isi kode disalin.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salin isi")
                            }
                            OutlinedButton(
                                onClick = {
                                    val saved = MediaStoreSaver.saveBitmap(
                                        context,
                                        image,
                                        MediaStoreSaver.timestampName("QR_Barcode", "png"),
                                        Bitmap.CompressFormat.PNG
                                    )
                                    if (saved != null) shareUri(context, saved, "image/png")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bagikan")
                            }
                        }
                    }
                }
            }
        } else {
            item {
                CyberCard(borderColor = AccentEmerald) {
                    Text(
                        "Pemindai menggunakan kamera dan decoder ZXing. Mendukung QR, Code 128/39, EAN, UPC, Data Matrix, PDF417, dan Aztec.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    CyberButton(
                        text = "BUKA KAMERA & PINDAI",
                        onClick = { launchScanner() },
                        color = AccentEmerald
                    )
                }
            }

            item {
                val text = scanResult
                if (text != null) {
                    CyberCard(borderColor = AccentCyan) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Hasil pemindaian", color = TextPrimary, fontWeight = FontWeight.Bold)
                            AssistChip(onClick = {}, label = { Text(scanFormat, fontSize = 11.sp) })
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text, color = AccentCyan, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, text)
                                    Toast.makeText(context, "Hasil disalin.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salin")
                            }
                            if (text.startsWith("http://", true) || text.startsWith("https://", true)) {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text)))
                                        }.onFailure {
                                            Toast.makeText(context, "Tautan tidak dapat dibuka.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Buka")
                                }
                            }
                            OutlinedButton(
                                onClick = { scanResult = null },
                                modifier = Modifier.weight(0.8f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Riwayat", color = TextPrimary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { history.clear() }) { Text("Hapus") }
                    }
                }
                items(history.size) { index ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SlateElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                history[index],
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { copyToClipboard(context, history[index]) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Salin", tint = AccentCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildScanOptions(): ScanOptions = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
    setPrompt("Arahkan kamera ke QR / barcode")
    setBeepEnabled(false)
    setOrientationLocked(false)
    setBarcodeImageEnabled(false)
}

private fun generateBarcode(value: String, format: BarcodeFormat, size: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 2,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val matrix = MultiFormatWriter().encode(value, format, size, size, hints)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            pixels[y * width + x] = if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kode", text))
}

private fun shareUri(context: Context, uri: Uri, mime: String) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "Bagikan kode"))
}
