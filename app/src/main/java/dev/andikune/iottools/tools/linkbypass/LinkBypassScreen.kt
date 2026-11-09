package dev.andikune.iottools.tools.linkbypass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.components.CyberButton
import dev.andikune.iottools.ui.components.CyberCard
import dev.andikune.iottools.ui.components.CyberSecondaryButton
import dev.andikune.iottools.ui.components.CyberTerminalHeader
import dev.andikune.iottools.ui.components.CyberTextField
import dev.andikune.iottools.ui.theme.AccentCyan
import dev.andikune.iottools.ui.theme.AccentEmerald
import dev.andikune.iottools.ui.theme.AccentRose
import dev.andikune.iottools.ui.theme.SlateCanvas
import dev.andikune.iottools.ui.theme.TextPrimary
import dev.andikune.iottools.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LinkBypassScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<LinkResolution?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SlateCanvas).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "Universal Short Link Bypass",
                subtitle = "HTTP redirect • URL parameter • Meta refresh",
                statusText = if (loading) "SCANNING" else "READY",
                statusColor = if (loading) AccentCyan else AccentEmerald
            )
        }
        item {
            CyberCard(borderColor = AccentCyan) {
                Text("Masukkan tautan pendek", color = AccentCyan, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                CyberTextField(
                    value = input,
                    onValueChange = { input = it; error = ""; result = null },
                    label = "Short URL",
                    placeholder = "https://bit.ly/xxxxxx"
                )
                Spacer(Modifier.height(12.dp))
                CyberButton(
                    text = if (loading) "MEMERIKSA REDIRECT..." else "TEMUKAN LINK TUJUAN",
                    enabled = !loading && input.isNotBlank(),
                    color = AccentCyan,
                    onClick = {
                        scope.launch {
                            loading = true
                            error = ""
                            result = null
                            try {
                                result = withContext(Dispatchers.IO) { LinkBypassResolver().resolve(input) }
                            } catch (e: Exception) {
                                error = e.message ?: "Tautan tidak dapat diproses."
                            } finally {
                                loading = false
                            }
                        }
                    }
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = AccentRose, fontSize = 13.sp)
                }
            }
        }
        result?.let { resolved ->
            item {
                CyberCard(borderColor = AccentEmerald) {
                    Text("URL tujuan ditemukan", color = AccentEmerald, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(resolved.destinationUrl, color = TextPrimary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(resolved.detail, color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    CyberSecondaryButton(
                        text = "SALIN LINK TUJUAN",
                        color = AccentCyan,
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("Resolved URL", resolved.destinationUrl))
                            Toast.makeText(context, "Link tujuan disalin", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    CyberButton(
                        text = "BUKA LINK TUJUAN",
                        color = AccentEmerald,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resolved.destinationUrl)))
                            }.onFailure {
                                Toast.makeText(context, "Tidak ada aplikasi untuk membuka link ini", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
        item {
            CyberCard {
                Text("Batas keamanan", color = AccentCyan, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Resolver mengikuti redirect HTTP, parameter URL umum, dan meta refresh. Maksimal 10 lompatan dan 512 KB HTML. IP privat/lokal, skema non-HTTP(S), port nonstandar, loop, serta halaman yang meminta login/CAPTCHA tidak dilewati. DRM dan proteksi akses tidak dibypass.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
