package dev.andikune.iottools.tools.youtube

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun YouTubeDownloaderScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var inputUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var activeDownloadProcessId by remember { mutableStateOf<String?>(null) }
    var activeDownloadLabel by remember { mutableStateOf("") }
    var videoResult by remember { mutableStateOf<YouTubeVideoData?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusLogs by remember {
        mutableStateOf(
            listOf(
                "[INIT] YouTube Downloader Engine v2.0 siap digunakan.",
                "[ENGINE] Mendukung YouTube Video, Shorts, dan konversi MP3 Audio."
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
                    Toast.makeText(context, "Link berhasil ditempel dari clipboard!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Clipboard kosong!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {}
    }

    fun fetchVideo() {
        if (inputUrl.isBlank()) {
            Toast.makeText(context, "Masukkan URL YouTube terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            videoResult = null
            statusLogs = statusLogs + "[REQUEST] Memeriksa Video ID: ${inputUrl.take(45)}..."

            val startTime = System.currentTimeMillis()
            val result = YouTubeDownloaderService.fetchVideoDetails(inputUrl)
            val elapsed = System.currentTimeMillis() - startTime

            isLoading = false
            result.onSuccess { data ->
                videoResult = data
                statusLogs = statusLogs + "[SUCCESS] Metadata video ditemukan dalam ${elapsed}ms."
                statusLogs = statusLogs + "[TITLE] ${data.title.take(40)}..."
                statusLogs = statusLogs + "[STREAM] ${data.formats.size} format unduhan siap (MP4 HD, SD & MP3 Audio)."
            }.onFailure { error ->
                errorMessage = error.message ?: "Gagal memproses URL YouTube."
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
                title = "YOUTUBE DOWNLOADER",
                subtitle = "Unduh Video MP4 720p/360p & Audio MP3 YouTube / Shorts",
                statusText = "PARSER READY",
                statusColor = AccentRose
            )
        }

        // Live 0-100% In-App Download Progress & Speed Card
        item {
            DownloadProgressCard()
        }

        // Input Card
        item {
            CyberCard(borderColor = AccentRose) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentRose.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = AccentRose,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "URL / TAUTAN YOUTUBE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tempel link video, Shorts, atau m.youtube.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                CyberTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = "https://youtu.be/... atau youtube.com/watch?v=...",
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
                        text = if (isLoading) "MEMPROSES..." else "AMBIL VIDEO",
                        modifier = Modifier.weight(1f),
                        onClick = { fetchVideo() }
                    )
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentRose,
                        trackColor = SlateBorder
                    )
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

        // Result Card & Format Selectors
        val result = videoResult
        if (result != null) {
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
                                    text = "VIDEO BERHASIL DIEKSTRAK",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentEmerald
                                )
                                Text(
                                    text = "Channel: ${result.author}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Thumbnail Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                                .background(SlateElevated)
                        ) {
                            AsyncImage(
                                model = result.thumbnailUrl,
                                contentDescription = "YouTube Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Overlay Play Icon
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }

                            // YouTube ID Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "ID: ${result.videoId}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { YouTubeDownloaderService.openInYouTube(context, result.videoId) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tonton", fontSize = 12.sp, color = TextPrimary)
                            }

                            OutlinedButton(
                                onClick = { YouTubeDownloaderService.shareVideo(context, result.videoId, result.title) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bagikan", fontSize = 12.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            }

            if (activeDownloadProcessId != null) {
                item {
                    CyberCard(borderColor = AccentAmber) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "UNDUHAN YOUTUBE AKTIF",
                                    color = AccentAmber,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = activeDownloadLabel,
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    activeDownloadProcessId?.let(YouTubeDownloaderService::cancelDownload)
                                    activeDownloadProcessId = null
                                    activeDownloadLabel = ""
                                    statusLogs = statusLogs + "[INFO] Permintaan pembatalan unduhan dikirim."
                                },
                                border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = AccentRose)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("BATALKAN", color = AccentRose)
                            }
                        }
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentAmber,
                            trackColor = SlateBorder
                        )
                    }
                }
            }

            // Available Download Formats Header
            item {
                Text(
                    text = "PILIHAN FORMAT UNDUHAN",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            // Format List
            items(result.formats) { format ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SlateElevated,
                    border = BorderStroke(1.dp, if (format.isAudioOnly) AccentAmber.copy(alpha = 0.4f) else AccentCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (format.isAudioOnly) AccentAmber.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (format.isAudioOnly) Icons.Default.Audiotrack else Icons.Default.VideoFile,
                                    contentDescription = null,
                                    tint = if (format.isAudioOnly) AccentAmber else AccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = format.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Ukuran: ${format.approximateSize} | Res: ${format.resolution}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Button(
                            enabled = activeDownloadProcessId == null,
                            onClick = {
                                YouTubeDownloaderService.enqueueDownload(
                                    context = context,
                                    format = format,
                                    videoTitle = result.title,
                                    videoId = result.videoId,
                                    onStarted = { processId ->
                                        activeDownloadProcessId = processId
                                        activeDownloadLabel = format.label
                                    },
                                    onFinished = { processId, outcome ->
                                        if (activeDownloadProcessId == processId) {
                                            activeDownloadProcessId = null
                                            activeDownloadLabel = ""
                                            statusLogs = statusLogs + if (outcome.isSuccess) {
                                                "[SUCCESS] Unduhan YouTube selesai."
                                            } else {
                                                "[ERROR] Unduhan YouTube gagal atau dibatalkan."
                                            }
                                        }
                                    }
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (format.isAudioOnly) AccentAmber else AccentCyan,
                                contentColor = SlateCanvas
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unduh", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
