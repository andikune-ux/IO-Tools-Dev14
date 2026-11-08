package dev.andikune.iottools.tools.tiktok

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
import androidx.compose.ui.graphics.Brush
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
fun TikTokDownloaderScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var inputUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var videoResult by remember { mutableStateOf<TikTokVideoData?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusLogs by remember {
        mutableStateOf(
            listOf(
                "[INIT] TikTok local extractor siap.",
                "[ENGINE] yt-dlp + FFmpeg untuk video publik dan ekstraksi audio."
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
            Toast.makeText(context, "Silakan masukkan link video TikTok terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            videoResult = null
            statusLogs = statusLogs + "[REQUEST] Memproses URL dengan yt-dlp lokal: ${inputUrl.take(45)}..."

            val startTime = System.currentTimeMillis()
            val result = TikTokDownloaderService.fetchVideoDetails(inputUrl)
            val elapsed = System.currentTimeMillis() - startTime

            isLoading = false
            result.onSuccess { data ->
                videoResult = data
                statusLogs = statusLogs + "[SUCCESS] Resolusi stream selesai dalam ${elapsed}ms."
                statusLogs = statusLogs + "[STREAM] Video: ${data.title.take(30)}... (${data.videoSizeFormatted})"
                statusLogs = statusLogs + "[STATUS] Format tanpa watermark terverifikasi oleh extractor."
            }.onFailure { error ->
                errorMessage = error.message ?: "Terjadi kesalahan saat memproses tautan."
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
                title = "TIKTOK DOWNLOADER PRO",
                subtitle = "Download Video TikTok Tanpa Watermark (HD) & Audio MP3",
                statusText = "LOCAL ENGINE READY",
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
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = AccentRose, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "URL / TAUTAN VIDEO TIKTOK",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tempel tautan video dari aplikasi TikTok atau browser",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                CyberTextField(
                    value = inputUrl,
                    onValueChange = {
                        inputUrl = it
                        errorMessage = null
                    },
                    label = "https://www.tiktok.com/@... atau https://vt.tiktok.com/..."
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons for Input: Paste, Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { pasteFromClipboard() },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PrimaryIndigo)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = PrimaryIndigoLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tempel Link", color = PrimaryIndigoLight, style = MaterialTheme.typography.labelMedium)
                    }

                    if (inputUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                inputUrl = ""
                                videoResult = null
                                errorMessage = null
                            },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Main Fetch Button
                Button(
                    onClick = { fetchVideo() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentRose,
                        contentColor = Color.White
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Sedang Mengambil Stream Video...", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PROSES LINK & AMBIL VIDEO", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Error Banner
        if (errorMessage != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentRose.copy(alpha = 0.12f))
                        .border(1.dp, AccentRose.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRose, modifier = Modifier.size(24.dp))
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
        if (videoResult != null) {
            val data = videoResult!!
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically()
                ) {
                    CyberCard(borderColor = AccentEmerald) {
                        // Badge Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentEmeraldSoft)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "✓ NO WATERMARK HD READY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Ukuran: ${data.videoSizeFormatted}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Video Preview Thumbnail & Author info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            if (data.coverUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 100.dp, height = 135.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                                ) {
                                    AsyncImage(
                                        model = data.coverUrl,
                                        contentDescription = "Video Thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Duration badge
                                    if (data.durationSeconds > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${data.durationSeconds}s",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                // Author Row
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (data.authorAvatar.isNotBlank()) {
                                        AsyncImage(
                                            model = data.authorAvatar,
                                            contentDescription = "Author Avatar",
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column {
                                        Text(
                                            text = data.authorName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "@${data.authorUniqueId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = data.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    maxLines = 4
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Download Action 1: Download Video No Watermark (HD)
                        Button(
                            onClick = {
                                val sanitizedTitle = data.title.replace(Regex("[^a-zA-Z0-9_]"), "_").take(30)
                                val fileName = "TikTok_${sanitizedTitle}_${data.id}.mp4"
                                TikTokDownloaderService.startDownload(
                                    context = context,
                                    url = data.sourceUrl,
                                    fileName = fileName,
                                    title = data.title,
                                    isAudio = false,
                                    formatId = data.noWatermarkFormatId
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentEmerald,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "UNDUH VIDEO (TANPA WATERMARK)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Kualitas HD Asli • Disimpan ke Galeri & Download",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.5.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Download Action 2: Download Audio (MP3)
                        if (data.audioAvailable) {
                            Button(
                                onClick = {
                                    val sanitizedTitle = data.title.replace(Regex("[^a-zA-Z0-9_]"), "_").take(30)
                                    val fileName = "TikTok_Audio_${sanitizedTitle}_${data.id}.mp3"
                                    TikTokDownloaderService.startDownload(
                                        context = context,
                                        url = data.sourceUrl,
                                        fileName = fileName,
                                        title = "${data.title} (Audio)",
                                        isAudio = true
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryIndigo,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("UNDUH AUDIO MP3 SAJA", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Preview & Share row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    TikTokDownloaderService.openPreview(context, data.noWatermarkVideoUrl)
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AccentCyan)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Putar / Preview", color = AccentCyan, style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = {
                                    TikTokDownloaderService.shareMediaLink(context, data.sourceUrl, data.title)
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AccentAmber)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bagikan Link", color = AccentAmber, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // Live Console Terminal Log
        item {
            CyberCard(borderColor = SlateBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TERMINAL ACTIVITY LOG",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = "CDN STREAM STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentEmerald
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(SlateCanvas, RoundedCornerShape(10.dp))
                        .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    LazyColumn(reverseLayout = true) {
                        items(statusLogs.size) { idx ->
                            val log = statusLogs[statusLogs.size - 1 - idx]
                            val logColor = when {
                                log.contains("[SUCCESS]") -> AccentEmerald
                                log.contains("[ERROR]") -> AccentRose
                                log.contains("[REQUEST]") -> AccentCyan
                                else -> TextSecondary
                            }
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono, fontSize = 11.sp),
                                color = logColor,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
