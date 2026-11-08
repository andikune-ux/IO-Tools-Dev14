package dev.andikune.iottools.tools.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.CRC32

@Composable
fun DataHashTools() {
    val context = LocalContext.current
    var activeSubTool by remember { mutableIntStateOf(0) } // 0: Hash, 1: Base64, 2: URL Encode/Decode, 3: Batch Rename

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "FILE & DATA HASH ENGINE",
                subtitle = "Cryptographic Hashes | Base64 | URL Encoders | Batch Renamer",
                statusText = "CRYPTO READY",
                statusColor = MatrixGreen
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tools = listOf("Hash Generator", "Base64 Encoder", "URL Encode/Decode", "Batch Rename")
                tools.forEachIndexed { index, name ->
                    val isSel = activeSubTool == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) MatrixGreen else CyberDark)
                            .border(1.dp, if (isSel) MatrixGreen else CyberCardBorder, RoundedCornerShape(6.dp))
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

        // TOOL 0: Hash Generator
        if (activeSubTool == 0) {
            item {
                var hashInput by remember { mutableStateOf("AndikUne-Dev14-Signature") }
                var hashedFileName by remember { mutableStateOf("") }
                var hashedFileSize by remember { mutableLongStateOf(0L) }
                var fileHashes by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                var isHashingFile by remember { mutableStateOf(false) }
                val hashScope = rememberCoroutineScope()

                val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        hashScope.launch {
                            isHashingFile = true
                            fileHashes = emptyList()
                            try {
                                val metadata = withContext(Dispatchers.IO) {
                                    val document = DocumentFile.fromSingleUri(context, uri)
                                    (document?.name ?: uri.lastPathSegment.orEmpty()) to (document?.length() ?: 0L)
                                }
                                hashedFileName = metadata.first
                                hashedFileSize = metadata.second
                                fileHashes = withContext(Dispatchers.IO) { calculateFileDigests(context, uri) }
                            } catch (error: Exception) {
                                fileHashes = emptyList()
                                Toast.makeText(context, "Gagal membaca file: ${error.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isHashingFile = false
                            }
                        }
                    }
                }

                val md5Hash = remember(hashInput) { calculateDigest("MD5", hashInput) }
                val sha1Hash = remember(hashInput) { calculateDigest("SHA-1", hashInput) }
                val sha256Hash = remember(hashInput) { calculateDigest("SHA-256", hashInput) }
                val sha512Hash = remember(hashInput) { calculateDigest("SHA-512", hashInput) }
                val crc32Hash = remember(hashInput) {
                    val crc = CRC32()
                    crc.update(hashInput.toByteArray())
                    java.lang.Long.toHexString(crc.value).uppercase()
                }

                CyberCard(borderColor = MatrixGreen) {
                    Text(
                        text = "[MULTI-ALGORITHM HASH CALCULATOR]",
                        style = MaterialTheme.typography.titleMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    CyberTextField(
                        value = hashInput,
                        onValueChange = { hashInput = it.take(100_000) },
                        label = "Teks Input untuk Hash",
                        placeholder = "Masukkan string untuk dihitung hash checksum..."
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    HashResultRow(context, "SHA-256 (Keystore / APK)", sha256Hash, NeonRed)
                    HashResultRow(context, "MD5 (Legacy Fingerprint)", md5Hash, NeonCyan)
                    HashResultRow(context, "SHA-1 (Legacy Signatures)", sha1Hash, NeonYellow)
                    HashResultRow(context, "CRC-32 (File Checksum)", crc32Hash, MatrixGreen)
                    HashResultRow(context, "SHA-512 (High Security)", sha512Hash.take(32) + "...", NeonPurple)

                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        "[HASH FILE STREAMING]",
                        style = MaterialTheme.typography.titleSmall,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Hitung checksum file nyata tanpa memuat seluruh isi ke RAM. Cocok untuk APK, ZIP, firmware, dan dokumen besar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    CyberButton(
                        text = if (isHashingFile) "MENGHITUNG CHECKSUM..." else "PILIH FILE UNTUK DI-HASH",
                        enabled = !isHashingFile,
                        color = NeonCyan,
                        onClick = { filePicker.launch(arrayOf("*/*")) }
                    )
                    if (hashedFileName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "File: $hashedFileName (${formatFileSize(hashedFileSize)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TerminalText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        fileHashes.forEachIndexed { index, (label, value) ->
                            val accent = when (index) {
                                0 -> NeonRed
                                1 -> NeonCyan
                                2 -> NeonYellow
                                3 -> MatrixGreen
                                else -> NeonPurple
                            }
                            HashResultRow(context, label, value, accent)
                        }
                    }
                }
            }
        }

        // TOOL 1: Base64
        if (activeSubTool == 1) {
            item {
                var base64Input by remember { mutableStateOf("CyberSecurity2026") }
                var base64Output by remember { mutableStateOf("") }
                var isDecoding by remember { mutableStateOf(false) }

                CyberCard(borderColor = NeonCyan) {
                    Text(
                        text = "[BASE64 ENCODER & DECODER]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    CyberTextField(
                        value = base64Input,
                        onValueChange = { base64Input = it.take(1_000_000) },
                        label = if (isDecoding) "Ciphertext Base64" else "Plaintext Teks",
                        singleLine = false,
                        modifier = Modifier.height(100.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CyberButton(
                            text = "ENCODE BASE64",
                            modifier = Modifier.weight(1f),
                            color = NeonCyan,
                            onClick = {
                                isDecoding = false
                                base64Output = Base64.encodeToString(base64Input.toByteArray(), Base64.NO_WRAP)
                            }
                        )
                        CyberButton(
                            text = "DECODE BASE64",
                            modifier = Modifier.weight(1f),
                            color = NeonYellow,
                            textColor = CyberBlack,
                            onClick = {
                                isDecoding = true
                                base64Output = try {
                                    String(Base64.decode(base64Input, Base64.DEFAULT))
                                } catch (e: Exception) {
                                    "Error: String Base64 tidak valid!"
                                }
                            }
                        )
                    }

                    if (base64Output.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Hasil Output:", style = MaterialTheme.typography.labelSmall, color = TerminalDim)
                        Spacer(modifier = Modifier.height(4.dp))
                        CyberCodeBox(code = base64Output)
                    }
                }
            }
        }

        // TOOL 2: URL Encode/Decode
        if (activeSubTool == 2) {
            item {
                var urlInput by remember { mutableStateOf("https://example.com/search?q=cyber toolkit & token=abc#123") }
                var urlOutput by remember { mutableStateOf("") }

                CyberCard(borderColor = NeonYellow) {
                    Text(
                        text = "[URL PERCENT-ENCODING ENGINE]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonYellow,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    CyberTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it.take(1_000_000) },
                        label = "URL atau URI Parameter Target"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CyberButton(
                            text = "URL ENCODE",
                            modifier = Modifier.weight(1f),
                            color = NeonYellow,
                            textColor = CyberBlack,
                            onClick = {
                                urlOutput = URLEncoder.encode(urlInput, "UTF-8")
                            }
                        )
                        CyberButton(
                            text = "URL DECODE",
                            modifier = Modifier.weight(1f),
                            color = NeonRed,
                            onClick = {
                                urlOutput = try {
                                    URLDecoder.decode(urlInput, "UTF-8")
                                } catch (e: Exception) {
                                    "Error: Gagal mendecode URL"
                                }
                            }
                        )
                    }

                    if (urlOutput.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        CyberCodeBox(code = urlOutput)
                    }
                }
            }
        }

        // TOOL 3: Batch Rename (real SAF operation)
        if (activeSubTool == 3) {
            item {
                val scope = rememberCoroutineScope()
                var prefix by remember { mutableStateOf("IDTOOLS_") }
                var suffix by remember { mutableStateOf("_2026") }
                var startNumber by remember { mutableStateOf("1") }
                var folderUri by remember { mutableStateOf<Uri?>(null) }
                var folderName by remember { mutableStateOf("") }
                var files by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
                var busy by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf<String?>(null) }
                var folderLoadToken by remember { mutableIntStateOf(0) }

                fun loadFolder(uri: Uri, showStatus: Boolean = true) {
                    val token = ++folderLoadToken
                    folderUri = uri
                    folderName = "Memuat..."
                    files = emptyList()
                    if (showStatus) status = "Memuat daftar file..."
                    scope.launch {
                        val folderData = withContext(Dispatchers.IO) {
                            val doc = DocumentFile.fromTreeUri(context, uri)
                            if (doc == null || !doc.isDirectory) null
                            else {
                                val listed = doc.listFiles().filter { it.isFile }
                                    .sortedBy { it.name?.lowercase() ?: "" }
                                (doc.name ?: "Folder") to listed
                            }
                        }
                        if (token != folderLoadToken) return@launch
                        if (folderData == null) {
                            folderUri = null
                            folderName = ""
                            files = emptyList()
                            if (showStatus) status = "Folder tidak dapat dibuka."
                            return@launch
                        }
                        folderUri = uri
                        folderName = folderData.first
                        files = folderData.second
                        if (showStatus) {
                            status = if (files.isEmpty()) "Folder tidak berisi file." else "${files.size} file ditemukan."
                        }
                    }
                }

                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }
                        loadFolder(uri)
                    }
                }

                fun targetName(original: String, index: Int): String {
                    val number = startNumber.toLongOrNull() ?: 1L
                    return buildBatchRenameName(original, prefix, suffix, number, index)
                }

                CyberCard(borderColor = AccentRose) {
                    Text("Batch File Renamer (SAF)", color = AccentRose, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Pilih folder melalui Storage Access Framework. Aplikasi hanya dapat mengubah file di folder yang Anda izinkan.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberSecondaryButton(
                        text = if (folderUri == null) "PILIH FOLDER" else "GANTI FOLDER: $folderName",
                        onClick = { if (!busy) picker.launch(null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(value = prefix, onValueChange = { if (!busy) prefix = it }, label = "Awalan")
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(value = suffix, onValueChange = { if (!busy) suffix = it }, label = "Akhiran")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    CyberTextField(value = startNumber, onValueChange = { if (!busy) startNumber = it.filter { c -> c.isDigit() }.take(9) }, label = "Nomor awal")
                    Spacer(modifier = Modifier.height(12.dp))

                    if (files.isNotEmpty()) {
                        Text("Pratinjau (${files.size} file)", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        files.take(25).forEachIndexed { index, file ->
                            val original = file.name ?: "(tanpa nama)"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(SlateSurface, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    original,
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "→ ${targetName(original, index)}",
                                    color = AccentEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                        if (files.size > 25) {
                            Text("... dan ${files.size - 25} file lainnya", color = TextMuted, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    CyberButton(
                        text = if (busy) "MENGUBAH NAMA..." else "UBAH NAMA SEMUA FILE",
                        enabled = !busy && files.isNotEmpty(),
                        color = AccentRose,
                        onClick = {
                            val currentUri = folderUri ?: return@CyberButton
                            val start = startNumber.toLongOrNull()
                            val filesSnapshot = files.toList()
                            if (start == null || start < 0L || start > 999_999_999L - filesSnapshot.size) {
                                status = "Nomor awal tidak valid atau terlalu besar."
                                return@CyberButton
                            }
                            val names = filesSnapshot.mapIndexed { index, file ->
                                val original = file.name ?: ""
                                buildBatchRenameName(original, prefix, suffix, start, index)
                            }
                            if (filesSnapshot.any { it.name.isNullOrBlank() }) {
                                status = "Ada file yang tidak memiliki nama dan tidak dapat diubah."
                                return@CyberButton
                            }
                            if (names.distinct().size != names.size) {
                                status = "Pola menghasilkan nama duplikat. Ubah awalan atau akhiran."
                                return@CyberButton
                            }
                            val existingNames = filesSnapshot.mapNotNull { it.name }.toSet()
                            val conflicts = names.zip(filesSnapshot).any { (newName, file) ->
                                newName != file.name && newName in existingNames
                            }
                            if (conflicts) {
                                status = "Nama tujuan sudah digunakan file lain di folder. Tidak ada file yang diubah."
                                return@CyberButton
                            }

                            val prefixSnapshot = prefix
                            val suffixSnapshot = suffix
                            busy = true
                            status = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    var success = 0
                                    var failed = 0
                                    filesSnapshot.forEachIndexed { index, file ->
                                        val original = file.name ?: ""
                                        val newName = buildBatchRenameName(
                                            original,
                                            prefixSnapshot,
                                            suffixSnapshot,
                                            start,
                                            index
                                        )
                                        if (newName == original) {
                                            success++
                                        } else if (runCatching { file.renameTo(newName) }.getOrDefault(false)) {
                                            success++
                                        } else {
                                            failed++
                                        }
                                    }
                                    success to failed
                                }
                                busy = false
                                loadFolder(currentUri, showStatus = false)
                                status = "Selesai: ${result.first} berhasil, ${result.second} gagal."
                            }
                        }
                    )

                    if (status != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(status!!, color = if (status!!.startsWith("Selesai")) AccentEmerald else TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

internal fun buildBatchRenameName(
    original: String,
    prefix: String,
    suffix: String,
    startNumber: Long,
    index: Int
): String {
    val extension = original.substringAfterLast('.', "")
        .takeIf { it.isNotBlank() && original.lastIndexOf('.') > 0 }
        ?.replace(Regex("[^A-Za-z0-9_-]"), "")
        ?.take(20)
    val number = startNumber + index.coerceAtLeast(0)
    val numberText = String.format(java.util.Locale.US, "%03d", number)
    val cleanPrefix = prefix.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("[\\u0000-\\u001F\\u007F]"), "_").trim()
    val cleanSuffix = suffix.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("[\\u0000-\\u001F\\u007F]"), "_").trim()
    val extensionPart = extension?.let { ".$it" }.orEmpty()
    val base = (cleanPrefix + numberText + cleanSuffix).trimEnd('.', ' ')
        .ifBlank { "file_$numberText" }
        .take((200 - extensionPart.length).coerceAtLeast(1))
    return base + extensionPart
}

@Composable
private fun HashResultRow(context: Context, label: String, hash: String, color: androidx.compose.ui.graphics.Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Text(
                "SALIN",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, hash))
                    Toast.makeText(context, "$label disalin!", Toast.LENGTH_SHORT).show()
                }
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = hash,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
            color = TerminalText,
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberDark, RoundedCornerShape(4.dp))
                .padding(6.dp)
        )
    }
}

private fun calculateDigest(algorithm: String, input: String): String {
    val md = MessageDigest.getInstance(algorithm)
    val bytes = md.digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun calculateFileDigests(context: Context, uri: Uri): List<Pair<String, String>> {
    val digests = linkedMapOf(
        "SHA-256 (File)" to MessageDigest.getInstance("SHA-256"),
        "MD5 (File)" to MessageDigest.getInstance("MD5"),
        "SHA-1 (File)" to MessageDigest.getInstance("SHA-1"),
        "SHA-512 (File)" to MessageDigest.getInstance("SHA-512")
    )
    val crc = CRC32()
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("File tidak dapat dibuka.")
    input.use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            digests.values.forEach { it.update(buffer, 0, read) }
            crc.update(buffer, 0, read)
        }
    }
    val rows = digests.map { (label, digest) ->
        label to digest.digest().joinToString("") { "%02x".format(it) }
    }
    return rows + ("CRC-32 (File)" to java.lang.Long.toHexString(crc.value).uppercase())
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format(java.util.Locale.US, "%.2f %s", value, units[unit])
}
