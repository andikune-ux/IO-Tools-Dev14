package dev.andikune.iottools.tools.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
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
                        onValueChange = { hashInput = it },
                        label = "Teks Input untuk Hash",
                        placeholder = "Masukkan string untuk dihitung hash checksum..."
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    HashResultRow(context, "SHA-256 (Keystore / APK)", sha256Hash, NeonRed)
                    HashResultRow(context, "MD5 (Legacy Fingerprint)", md5Hash, NeonCyan)
                    HashResultRow(context, "SHA-1 (Legacy Signatures)", sha1Hash, NeonYellow)
                    HashResultRow(context, "CRC-32 (File Checksum)", crc32Hash, MatrixGreen)
                    HashResultRow(context, "SHA-512 (High Security)", sha512Hash.take(32) + "...", NeonPurple)
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
                        onValueChange = { base64Input = it },
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
                        onValueChange = { urlInput = it },
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

        // TOOL 3: Batch Rename
        if (activeSubTool == 3) {
            item {
                var prefix by remember { mutableStateOf("IDTOOLS_") }
                var suffix by remember { mutableStateOf("_2026") }
                var startNumber by remember { mutableStateOf("1") }
                val sampleFiles = listOf("photo1.jpg", "document.pdf", "scan_qr.png", "release.apk")

                CyberCard(borderColor = NeonRed) {
                    Text(
                        text = "[BATCH FILE RENAMER SIMULATOR]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(value = prefix, onValueChange = { prefix = it }, label = "Awalan (Prefix)")
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(value = suffix, onValueChange = { suffix = it }, label = "Akhiran (Suffix)")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Hasil Pratinjau Rename Berurutan:", style = MaterialTheme.typography.labelSmall, color = TerminalDim)
                    Spacer(modifier = Modifier.height(6.dp))

                    sampleFiles.forEachIndexed { index, original ->
                        val ext = original.substringAfterLast('.', "")
                        val newName = "$prefix${String.format("%03d", (startNumber.toIntOrNull() ?: 1) + index)}$suffix.$ext"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(CyberDark, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(original, style = MaterialTheme.typography.bodyMedium, color = TerminalDim)
                            Text("➔ $newName", style = MaterialTheme.typography.bodyMedium, color = MatrixGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
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
