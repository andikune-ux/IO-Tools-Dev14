package dev.andikune.iottools.tools.terabox

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TeraBoxDownloaderScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var inputUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var fileResult by remember { mutableStateOf<TeraBoxFileInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusLogs by remember {
        mutableStateOf(
            listOf(
                "[INIT] TeraBox Direct Engine v1.8 siap.",
                "[ENGINE] Bypass limit & ekstrak file langsung dari link terabox / 1024tera."
            )
        )
    }

    fun pasteFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                if (text.isNotBlank()) {
                    inputUrl = text
                    Toast.makeText(context, "Link TeraBox berhasil ditempel!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Clipboard kosong!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {}
    }

    fun resolveTeraBox() {
        if (inputUrl.isBlank()) {
            Toast.makeText(context, "Masukkan tautan TeraBox terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            fileResult = null
            statusLogs = statusLogs + "[REQUEST] Mengekstrak ID link: ${inputUrl.take(45)}..."

            val startTime = System.currentTimeMillis()
            val result = TeraBoxDownloaderService.resolveTeraBoxLink(inputUrl)
            val elapsed = System.currentTimeMillis() - startTime

            isLoading = false
            result.onSuccess { data ->
                fileResult = data
                statusLogs = statusLogs + "[SUCCESS] File berhasil di-resolve dalam ${elapsed}ms."
                statusLogs = statusLogs + "[FILE] ${data.fileName} (${data.fileSizeFormatted})"
                statusLogs = statusLogs + "[STATUS] Link unduhan langsung siap ditransfer!"
            }.onFailure { error ->
                errorMessage = error.message ?: "Gagal memproses link TeraBox."
                statusLogs = statusLogs + "[ERROR] Gagal: ${error.message}"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "TERABOX DOWNLOADER",
                subtitle = "Ekstrak & Unduh Video Asli (MP4) dari Link TeraBox / 1024tera",
                statusText = "VIDEO READY",
                statusColor = AccentAmber
            )
        }

        // Live 0-100% In-App Download Progress & Speed Card
        item {
            DownloadProgressCard()
        }

        // Input Card
        item {
            CyberCard(borderColor = AccentAmber) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentAmber.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "URL / TAUTAN TERABOX",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Mendukung terabox.com, teraboxapp.com, 1024tera.com, 1024terabox.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                CyberTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = "https://terabox.com/s/1..., 1024tera.com/s/... atau 1024terabox.com/s/...",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CyberButton(
                        text = "TEMPEL CLIPBOARD",
                        modifier = Modifier.weight(1f),
                        onClick = { pasteFromClipboard() }
                    )

                    CyberButton(
                        text = if (isLoading) "MEMPROSES..." else "GENERATE LINK",
                        modifier = Modifier.weight(1f),
                        onClick = { resolveTeraBox() }
                    )
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentAmber,
                            trackColor = SlateBorder
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Menghubungi cloud server & mengekstrak direct stream...",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentAmber,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Error Banner
        if (errorMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentRoseSoft,
                    border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRose)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentRose
                        )
                    }
                }
            }
        }

        // Result Card
        val file = fileResult
        if (file != null) {
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically()
                ) {
                    CyberCard(borderColor = AccentEmerald) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentEmeraldSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "FILE TERABOX SIAP DIUNDUH",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentEmerald
                                )
                                Text(
                                    text = "Direct Fast-Download Link",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // File Detail Container
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SlateElevated,
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AccentAmber.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (file.fileExtension) {
                                            "mp4", "mkv", "avi" -> Icons.Default.Movie
                                            "zip", "rar", "7z" -> Icons.Default.FolderZip
                                            "pdf" -> Icons.Default.PictureAsPdf
                                            "mp3", "flac" -> Icons.Default.Audiotrack
                                            else -> Icons.Default.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        tint = AccentAmber,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AccentAmber.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = file.fileExtension.uppercase(),
                                                color = AccentAmber,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = file.fileSizeFormatted,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Download Button with Progress capability
                        Button(
                            onClick = {
                                TeraBoxDownloaderService.enqueueDownload(context, file)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentAmber,
                                contentColor = SlateCanvas
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("UNDUH VIDEO ASLI (MP4)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { TeraBoxDownloaderService.shareLink(context, file) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan Direct Link", fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                }
            }
        }

        // Terminal Log Console
        item {
            CyberCard(borderColor = SlateBorder) {
                Text(
                    text = "CONSOLE LOGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateCanvas)
                        .padding(10.dp)
                ) {
                    statusLogs.takeLast(6).forEach { log ->
                        val color = when {
                            log.startsWith("[SUCCESS]") -> AccentEmerald
                            log.startsWith("[ERROR]") -> AccentRose
                            log.startsWith("[REQUEST]") -> AccentCyan
                            else -> TextSecondary
                        }
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                            color = color,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
