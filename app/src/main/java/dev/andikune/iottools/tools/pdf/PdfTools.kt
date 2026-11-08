package dev.andikune.iottools.tools.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.tools.common.MediaStoreSaver
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PdfTools() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeSubTool by remember { mutableIntStateOf(0) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val mergeUris = remember { mutableStateListOf<Uri>() }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastOutputs by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var pageRange by remember { mutableStateOf("") }
    var rotateDegrees by remember { mutableIntStateOf(90) }
    var userPassword by remember { mutableStateOf("") }
    var ownerPassword by remember { mutableStateOf("") }
    var compressProfile by remember { mutableIntStateOf(1) }

    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            status = null
            error = null
            lastOutputs = emptyList()
        }
    }
    val mergePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            mergeUris.clear()
            mergeUris.addAll(uris)
            status = null
            error = null
            lastOutputs = emptyList()
        }
    }

    fun sourceName(uri: Uri?): String =
        uri?.let { MediaStoreSaver.displayName(context, it) } ?: "Belum ada file"

    fun outputBase(uri: Uri?): String =
        sourceName(uri).substringBeforeLast('.', "dokumen").ifBlank { "dokumen" }

    fun runOperation(block: suspend () -> List<Uri>) {
        if (busy) return
        busy = true
        error = null
        status = null
        lastOutputs = emptyList()
        scope.launch {
            try {
                val outputs = block()
                lastOutputs = outputs
                val detail = status
                val completion = if (outputs.size == 1) {
                    "Selesai. File disimpan di Download/IO-Tools/${MediaStoreSaver.displayName(context, outputs.first()) ?: ""}"
                } else {
                    "Selesai. ${outputs.size} file PDF disimpan di Download/IO-Tools."
                }
                status = if (detail.isNullOrBlank()) completion else "$completion\n$detail"
            } catch (e: Exception) {
                error = e.message ?: "Operasi PDF gagal."
            } finally {
                busy = false
            }
        }
    }

    val subtools = listOf(
        "Gabung" to Icons.Default.CallMerge,
        "Pisah" to Icons.Default.CallSplit,
        "Putar" to Icons.Default.RotateRight,
        "Kunci" to Icons.Default.Lock,
        "Kompres" to Icons.Default.Compress
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "Dokumen PDF",
                subtitle = "Pemrosesan lokal nyata dengan PDFBox-Android",
                statusText = "OFFLINE",
                statusColor = AccentAmber
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subtools.forEachIndexed { index, pair ->
                    FilterChip(
                        selected = activeSubTool == index,
                        onClick = {
                            activeSubTool = index
                            status = null
                            error = null
                            lastOutputs = emptyList()
                        },
                        label = { Text(pair.first) },
                        leadingIcon = { Icon(pair.second, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        item {
            CyberCard(borderColor = if (activeSubTool == 3) AccentAmber else SlateBorder) {
                if (activeSubTool == 0) {
                    Text("Gabungkan PDF", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Pilih dua file atau lebih. Urutan pilihan akan dipertahankan.", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberSecondaryButton(
                        text = if (mergeUris.isEmpty()) "PILIH FILE PDF" else "PILIH ULANG (${mergeUris.size} FILE)",
                        onClick = { if (!busy) mergePicker.launch(arrayOf("application/pdf")) }
                    )
                    if (mergeUris.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        mergeUris.forEachIndexed { index, uri ->
                            Text(
                                "${index + 1}. ${sourceName(uri)}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberButton(
                        text = if (busy) "MEMPROSES..." else "GABUNGKAN SEKARANG",
                        enabled = !busy && mergeUris.size >= 2,
                        onClick = {
                            runOperation {
                                listOf(PdfEngine.merge(context, mergeUris.toList(), "Gabungan_${System.currentTimeMillis()}.pdf"))
                            }
                        }
                    )
                } else {
                    Text(
                        when (activeSubTool) {
                            1 -> "Pisahkan PDF"
                            2 -> "Putar Halaman"
                            3 -> "Kunci PDF"
                            else -> "Kompres PDF"
                        },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        when (activeSubTool) {
                            1 -> "Kosongkan rentang untuk memisahkan setiap halaman menjadi file sendiri."
                            2 -> "Rotasi diterapkan ke seluruh halaman."
                            3 -> "PDF dienkripsi AES-256. Password tidak disimpan di perangkat."
                            else -> "Setiap halaman dirender ulang sebagai JPEG lalu disusun menjadi PDF baru."
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberSecondaryButton(
                        text = if (selectedUri == null) "PILIH FILE PDF" else sourceName(selectedUri),
                        onClick = { if (!busy) singlePicker.launch(arrayOf("application/pdf")) }
                    )

                    if (activeSubTool == 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CyberTextField(
                            value = pageRange,
                            onValueChange = { pageRange = it },
                            label = "Rentang halaman",
                            placeholder = "contoh: 1-3, 5, 8-10 (kosong = semua halaman)"
                        )
                    }

                    if (activeSubTool == 2) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(90, 180, 270).forEach { degree ->
                                FilterChip(
                                    selected = rotateDegrees == degree,
                                    onClick = { rotateDegrees = degree },
                                    label = { Text("$degree°") }
                                )
                            }
                        }
                    }

                    if (activeSubTool == 3) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CyberTextField(
                            value = userPassword,
                            onValueChange = { userPassword = it },
                            label = "Password pengguna (min. 8 karakter)",
                            placeholder = "Wajib diisi",
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CyberTextField(
                            value = ownerPassword,
                            onValueChange = { ownerPassword = it },
                            label = "Password owner (opsional)",
                            placeholder = "Kosongkan = sama dengan password pengguna",
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }

                    if (activeSubTool == 4) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Kecil" to 0, "Sedang" to 1, "Tinggi" to 2).forEach { profile ->
                                FilterChip(
                                    selected = compressProfile == profile.second,
                                    onClick = { compressProfile = profile.second },
                                    label = { Text(profile.first) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    CyberButton(
                        text = if (busy) "MEMPROSES..." else when (activeSubTool) {
                            1 -> "PISAHKAN PDF"
                            2 -> "PUTAR PDF"
                            3 -> "KUNCI PDF"
                            else -> "KOMPRES PDF"
                        },
                        enabled = !busy && selectedUri != null,
                        onClick = {
                            val uri = selectedUri ?: return@CyberButton
                            runOperation {
                                when (activeSubTool) {
                                    1 -> PdfEngine.split(
                                        context,
                                        uri,
                                        pageRange,
                                        "${outputBase(uri)}_split.pdf"
                                    )
                                    2 -> listOf(
                                        PdfEngine.rotate(
                                            context,
                                            uri,
                                            rotateDegrees,
                                            "${outputBase(uri)}_rotated.pdf"
                                        )
                                    )
                                    3 -> try {
                                        listOf(
                                            PdfEngine.protect(
                                                context,
                                                uri,
                                                userPassword,
                                                ownerPassword,
                                                "${outputBase(uri)}_locked.pdf"
                                            )
                                        )
                                    } finally {
                                        userPassword = ""
                                        ownerPassword = ""
                                    }
                                    else -> {
                                        val (dpi, quality) = when (compressProfile) {
                                            0 -> 72 to 35
                                            2 -> 160 to 60
                                            else -> 120 to 45
                                        }
                                        val result = PdfEngine.compress(
                                            context,
                                            uri,
                                            dpi,
                                            quality,
                                            "${outputBase(uri)}_compressed.pdf"
                                        )
                                        val before = result.second
                                        val after = withContext(Dispatchers.IO) {
                                            MediaStoreSaver.fileSize(context, result.first)
                                        }
                                        if (before > 0) {
                                            status = "Ukuran: ${formatBytes(before)} → ${formatBytes(after)}"
                                        }
                                        listOf(result.first)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        if (status != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentEmerald.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(status!!, color = AccentEmerald, fontSize = 12.sp)
                    }
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

        if (lastOutputs.isNotEmpty()) {
            item {
                CyberCard(borderColor = AccentCyan) {
                    Text("Hasil", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    lastOutputs.forEach { uri ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                MediaStoreSaver.displayName(context, uri) ?: uri.lastPathSegment ?: "PDF",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { sharePdf(context, uri) }) {
                                Icon(Icons.Default.Share, contentDescription = "Bagikan", tint = AccentCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sharePdf(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan PDF"))
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
