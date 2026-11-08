package dev.andikune.iottools.tools.ucbrowser

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun UcMediaSnifferScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var inputUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }
    var currentPageTitle by remember { mutableStateOf("Web Video Source") }
    var snifferResult by remember { mutableStateOf<SniffingResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusLogs by remember {
        mutableStateOf(
            listOf(
                "[INIT] UC Browser Sniffer Engine v3.2 aktif.",
                "[ENGINE] Siap mendeteksi media video tersembunyi (MP4, M3U8 HLS, WEBM) pada web."
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
                    Toast.makeText(context, "URL berhasil ditempel!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Clipboard kosong!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {}
    }

    fun startSniffing() {
        if (inputUrl.isBlank()) {
            Toast.makeText(context, "Masukkan URL website yang ingin di-sniff", Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            snifferResult = null
            statusLogs = statusLogs + "[SCAN] Merayapi halaman: ${inputUrl.take(45)}..."

            val startTime = System.currentTimeMillis()
            val normalizedUrl = UcMediaSnifferService.normalizeTargetUrl(inputUrl)
            if (normalizedUrl == null) {
                isLoading = false
                errorMessage = "URL tidak valid. Gunakan alamat HTTP atau HTTPS publik."
                return@launch
            }

            val result = UcMediaSnifferService.sniffWebpage(normalizedUrl)
            val elapsed = System.currentTimeMillis() - startTime

            isLoading = false
            showBrowser = true
            result.onSuccess { data ->
                snifferResult = data
                currentPageTitle = data.pageTitle
                statusLogs = statusLogs + "[SUCCESS] Scan HTML selesai dalam ${elapsed}ms."
                statusLogs = statusLogs + "[PAGE] '${data.pageTitle.take(35)}...'"
                statusLogs = statusLogs + "[SNIFFED] Ditemukan ${data.mediaList.size} media dari HTML awal."
            }.onFailure { error ->
                if (error.message.orEmpty().startsWith("Belum ada URL media langsung")) {
                    snifferResult = SniffingResult(normalizedUrl, currentPageTitle, emptyList(), elapsed)
                    statusLogs = statusLogs + "[INFO] Browser terbuka. Tekan Play untuk menangkap request media."
                } else {
                    errorMessage = error.message ?: "Gagal membuka halaman target."
                    statusLogs = statusLogs + "[ERROR] Sniffer: ${error.message}"
                }
            }
        }
    }

    fun copyMediaLink(url: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = android.content.ClipData.newPlainText("Media URL", url)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Tautan media berhasil disalin!", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    fun addCapturedMedia(item: SniffedMediaItem) {
        val current = snifferResult ?: SniffingResult(
            targetUrl = UcMediaSnifferService.normalizeTargetUrl(inputUrl) ?: inputUrl,
            pageTitle = currentPageTitle,
            mediaList = emptyList(),
            scanDurationMs = 0L
        )
        val merged = (current.mediaList + item).distinctBy { it.url }
        if (merged.size != current.mediaList.size) {
            snifferResult = current.copy(pageTitle = currentPageTitle, mediaList = merged)
            statusLogs = statusLogs + "[CAPTURE] ${item.format} terdeteksi dari ${item.sourceDomain}."
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
                title = "UC BROWSER MEDIA SNIFFER",
                subtitle = "Ekstrak & Unduh Tautan Video Tersembunyi (MP4/M3U8) dari Web",
                statusText = "SNIFFER READY",
                statusColor = AccentCyan
            )
        }

        // Live 0-100% In-App Download Progress & Speed Card
        item {
            DownloadProgressCard()
        }

        // Input Card
        item {
            CyberCard(borderColor = AccentCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.TravelExplore,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "TARGET WEBSITE URL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Masukkan web streaming/nonton film untuk scan video",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                CyberTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = "https://example.com/watch-video...",
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
                        text = if (isLoading) "SCANNING..." else "MULAI SNIFF",
                        modifier = Modifier.weight(1f),
                        onClick = { startSniffing() }
                    )
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentCyan,
                        trackColor = SlateBorder
                    )
                }
            }
        }

        if (showBrowser) {
            item {
                CyberCard(borderColor = AccentCyan) {
                    Text(
                        text = "BROWSER CAPTURE",
                        style = MaterialTheme.typography.titleSmall,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Putar media pada halaman ini. Request MP4, WebM, HLS, dan DASH yang terlihat akan ditambahkan otomatis. Blob dan DRM tidak didukung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    key(inputUrl) {
                        AndroidView(
                            factory = { webContext ->
                                UcMediaSnifferService.createCaptureWebView(
                                    context = webContext,
                                    targetUrl = inputUrl,
                                    onPageTitle = { title ->
                                        currentPageTitle = title
                                        snifferResult = snifferResult?.copy(pageTitle = title)
                                    },
                                    onMediaDetected = { item -> addCapturedMedia(item) }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
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

        // Result Overview Card
        val sniffed = snifferResult
        if (sniffed != null) {
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
                                    text = "MEDIA STREAM BERHASIL DIDETEKSI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentEmerald
                                )
                                Text(
                                    text = "${sniffed.mediaList.size} file video ditemukan (${sniffed.scanDurationMs}ms)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Halaman: ${sniffed.pageTitle}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Sniffed Media List Header
            item {
                Text(
                    text = "DAFTAR TAUTAN MEDIA TERDETEKSI",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            // Sniffed Items
            itemsIndexed(sniffed.mediaList) { index, item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SlateElevated,
                    border = BorderStroke(1.dp, if (item.isStreamHls || item.isDash) AccentAmber.copy(alpha = 0.4f) else AccentCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (item.isStreamHls || item.isDash) AccentAmber.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isStreamHls || item.isDash) Icons.Default.Stream else Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        tint = if (item.isStreamHls || item.isDash) AccentAmber else AccentCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Sumber: ${item.sourceDomain} • ${item.approximateSize}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (item.isStreamHls || item.isDash) AccentAmber.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = item.format,
                                    color = if (item.isStreamHls || item.isDash) AccentAmber else AccentCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // URL Snippet Preview
                        Text(
                            text = item.url,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SlateCanvas)
                                .padding(8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { copyMediaLink(item.url) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salin Link", fontSize = 11.sp, color = TextPrimary)
                            }

                            Button(
                                onClick = {
                                    UcMediaSnifferService.enqueueDownload(context, item)
                                },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (item.isStreamHls || item.isDash) AccentAmber else AccentCyan,
                                    contentColor = SlateCanvas
                                )
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Unduh", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
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
                            log.startsWith("[SCAN]") -> AccentCyan
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
