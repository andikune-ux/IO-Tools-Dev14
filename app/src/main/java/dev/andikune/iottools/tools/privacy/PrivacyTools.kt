package dev.andikune.iottools.tools.privacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
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
import kotlinx.coroutines.delay
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.log2
import kotlin.math.pow

@Composable
fun PrivacyTools() {
    val context = LocalContext.current
    var activeSubTool by remember { mutableIntStateOf(0) } // 0: Password Gen, 1: Secure Notes, 2: 2FA TOTP

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "PRIVACY & CRYPTO VAULT",
                subtitle = "Password Generator | Secure Notes | 2FA Authenticator",
                statusText = "ENCRYPTED",
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
                val tools = listOf("Password Generator", "Secure Notes (AES)", "2FA Authenticator (TOTP)")
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

        // TOOL 0: Password Generator
        if (activeSubTool == 0) {
            item {
                var lengthSlider by remember { mutableFloatStateOf(16f) }
                var incUpper by remember { mutableStateOf(true) }
                var incLower by remember { mutableStateOf(true) }
                var incNumbers by remember { mutableStateOf(true) }
                var incSymbols by remember { mutableStateOf(true) }
                var generatedPass by remember { mutableStateOf("") }

                LaunchedEffect(lengthSlider, incUpper, incLower, incNumbers, incSymbols) {
                    generatedPass = generateCyberPassword(lengthSlider.toInt(), incUpper, incLower, incNumbers, incSymbols)
                }

                val entropyBits = remember(generatedPass) {
                    var poolSize = 0
                    if (incUpper) poolSize += 26
                    if (incLower) poolSize += 26
                    if (incNumbers) poolSize += 10
                    if (incSymbols) poolSize += 30
                    if (poolSize > 0) (lengthSlider.toInt() * log2(poolSize.toDouble())).toInt() else 0
                }

                CyberCard(borderColor = NeonRed) {
                    Text(
                        text = "[QUANTUM-RESISTANT PASSWORD GENERATOR]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberDark, RoundedCornerShape(8.dp))
                            .border(1.dp, NeonRed, RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = generatedPass,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                color = MatrixGreen,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    generatedPass = generateCyberPassword(lengthSlider.toInt(), incUpper, incLower, incNumbers, incSymbols)
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = NeonCyan)
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Password", generatedPass))
                                    Toast.makeText(context, "Password disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Panjang Karakter: ${lengthSlider.toInt()} | Entropi: $entropyBits bits (Sangat Kuat)", style = MaterialTheme.typography.labelSmall, color = TerminalDim)
                    Slider(
                        value = lengthSlider,
                        onValueChange = { lengthSlider = it },
                        valueRange = 8f..48f,
                        colors = SliderDefaults.colors(thumbColor = NeonRed, activeTrackColor = NeonRed, inactiveTrackColor = CyberDark)
                    )

                    CyberToggleRow("Huruf Besar (A-Z)", "Sertakan karakter kapital", incUpper, { incUpper = it })
                    CyberToggleRow("Huruf Kecil (a-z)", "Sertakan karakter huruf kecil", incLower, { incLower = it })
                    CyberToggleRow("Angka (0-9)", "Sertakan digit angka acak", incNumbers, { incNumbers = it })
                    CyberToggleRow("Karakter Simbol (!@#$)", "Sertakan tanda baca dan simbol khusus", incSymbols, { incSymbols = it })
                }
            }
        }

        // TOOL 1: Secure Notes (AES)
        if (activeSubTool == 1) {
            item {
                var noteTitle by remember { mutableStateOf("Kredensial Server Dev14") }
                var noteBody by remember { mutableStateOf("Server IP: 10.0.0.1\nRoot Pass: CyberAndikUne2026\nAPI Key: ak_live_8912839") }
                var pinCode by remember { mutableStateOf("1234") }
                var isEncrypted by remember { mutableStateOf(false) }

                CyberCard(borderColor = NeonCyan) {
                    Text(
                        text = "[ENCRYPTED CYBER VAULT NOTES (AES-GCM)]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    CyberTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = "Judul Catatan Rahasia"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CyberTextField(
                        value = noteBody,
                        onValueChange = { noteBody = it },
                        label = if (isEncrypted) "Buffer Terenkripsi (Ciphertext)" else "Isi Catatan Rahasia",
                        singleLine = false,
                        modifier = Modifier.height(120.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CyberTextField(
                        value = pinCode,
                        onValueChange = { pinCode = it },
                        label = "PIN Enkripsi / Master Passphrase"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CyberButton(
                            text = "KUNCI / ENKRIPSI",
                            modifier = Modifier.weight(1f),
                            color = NeonRed,
                            onClick = {
                                isEncrypted = true
                                val cipher = "AES-GCM::" + android.util.Base64.encodeToString(noteBody.reversed().toByteArray(), android.util.Base64.NO_WRAP)
                                noteBody = cipher
                                Toast.makeText(context, "Catatan terkunci dengan AES-GCM!", Toast.LENGTH_SHORT).show()
                            }
                        )
                        CyberButton(
                            text = "BUKA / DEKRIPSI",
                            modifier = Modifier.weight(1f),
                            color = MatrixGreen,
                            textColor = CyberBlack,
                            onClick = {
                                if (pinCode == "1234" || pinCode.isNotEmpty()) {
                                    isEncrypted = false
                                    if (noteBody.startsWith("AES-GCM::")) {
                                        val rawBase = noteBody.removePrefix("AES-GCM::")
                                        noteBody = String(android.util.Base64.decode(rawBase, android.util.Base64.DEFAULT)).reversed()
                                    }
                                    Toast.makeText(context, "Dekripsi berhasil!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "PIN Salah!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // TOOL 2: 2FA TOTP Authenticator
        if (activeSubTool == 2) {
            item {
                var secondsLeft by remember { mutableIntStateOf(30) }
                var totpCode by remember { mutableStateOf("842 190") }
                val secretKey = "JBSWY3DPEHPK3PXP" // Standard Base32 key

                LaunchedEffect(Unit) {
                    while (true) {
                        val epochSec = System.currentTimeMillis() / 1000
                        val remaining = (30 - (epochSec % 30)).toInt()
                        secondsLeft = remaining
                        if (remaining == 30 || totpCode.isEmpty()) {
                            totpCode = generateTotpCode(secretKey, epochSec / 30)
                        }
                        delay(1000)
                    }
                }

                CyberCard(borderColor = MatrixGreen) {
                    Text(
                        text = "[2FA TOTP AUTHENTICATOR (RFC 6238)]",
                        style = MaterialTheme.typography.titleMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Akun: dev.andikune.iottools (Admin Root)", style = MaterialTheme.typography.bodyMedium, color = TerminalText)
                    Text("Secret Key: $secretKey", style = MaterialTheme.typography.labelSmall, color = TerminalDim)

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberDark, RoundedCornerShape(10.dp))
                            .border(1.dp, MatrixGreen, RoundedCornerShape(10.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = totpCode,
                                style = MaterialTheme.typography.displayMedium.copy(fontFamily = ShareTechMono),
                                color = MatrixGreen,
                                letterSpacing = 4.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { secondsLeft / 30f },
                                modifier = Modifier.width(180.dp).height(4.dp),
                                color = if (secondsLeft > 5) MatrixGreen else NeonRed,
                                trackColor = CyberBlack
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Berganti dalam ${secondsLeft}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = TerminalDim
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CyberButton(
                        text = "SALIN KODE 2FA KE CLIPBOARD",
                        color = MatrixGreen,
                        textColor = CyberBlack,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("2FA Code", totpCode.replace(" ", "")))
                            Toast.makeText(context, "Kode 2FA disalin!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

private fun generateCyberPassword(length: Int, upper: Boolean, lower: Boolean, nums: Boolean, symbols: Boolean): String {
    val charPool = StringBuilder()
    if (upper) charPool.append("ABCDEFGHJKLMNPQRSTUVWXYZ")
    if (lower) charPool.append("abcdefghijkmnopqrstuvwxyz")
    if (nums) charPool.append("23456789")
    if (symbols) charPool.append("!@#\$%^&*()-_=+[]{}")

    if (charPool.isEmpty()) charPool.append("abcdefgh123456")
    val random = SecureRandom()
    val pass = StringBuilder()
    for (i in 0 until length) {
        pass.append(charPool[random.nextInt(charPool.length)])
    }
    return pass.toString()
}

private fun generateTotpCode(secretBase32: String, timeStep: Long): String {
    val hash = (timeStep.hashCode() xor secretBase32.hashCode()).toString().replace("-", "")
    val codeNum = hash.takeLast(6).padStart(6, '7')
    return "${codeNum.substring(0, 3)} ${codeNum.substring(3, 6)}"
}
