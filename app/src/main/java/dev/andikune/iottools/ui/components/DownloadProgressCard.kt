package dev.andikune.iottools.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.tools.common.AppDownloadManager
import dev.andikune.iottools.tools.common.DownloadStatus
import dev.andikune.iottools.ui.theme.*

@Composable
fun DownloadProgressCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadState by AppDownloadManager.downloadState.collectAsState()

    val isVisible = downloadState.status != DownloadStatus.IDLE

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        val borderColor = when (downloadState.status) {
            DownloadStatus.COMPLETED -> AccentEmerald
            DownloadStatus.ERROR -> AccentRose
            DownloadStatus.CONNECTING -> AccentAmber
            DownloadStatus.DOWNLOADING -> AccentCyan
            else -> SlateBorder
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SlateElevated,
            border = BorderStroke(1.5.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(borderColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (downloadState.status) {
                                    DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                                    DownloadStatus.ERROR -> Icons.Default.ErrorOutline
                                    DownloadStatus.CONNECTING -> Icons.Default.Sync
                                    else -> Icons.Default.Download
                                },
                                contentDescription = null,
                                tint = borderColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = when (downloadState.status) {
                                    DownloadStatus.COMPLETED -> "UNDUHAN BERHASIL"
                                    DownloadStatus.ERROR -> "UNDUHAN GAGAL"
                                    DownloadStatus.CONNECTING -> "MENGHUBUNGKAN..."
                                    DownloadStatus.DOWNLOADING -> "SEDANG MENGUNDUH VIDEO"
                                    else -> "DOWNLOAD MANAGER"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = borderColor
                            )
                            Text(
                                text = downloadState.fileName.ifEmpty { "Video Asli" },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                maxLines = 1,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (downloadState.status == DownloadStatus.DOWNLOADING || downloadState.status == DownloadStatus.CONNECTING) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${downloadState.progressPercent}%",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar (0 - 100%)
                if (downloadState.status == DownloadStatus.DOWNLOADING || downloadState.status == DownloadStatus.CONNECTING) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (downloadState.indeterminate || downloadState.totalBytesFormatted.contains("tidak diketahui", ignoreCase = true)) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = AccentCyan,
                                trackColor = SlateCanvas
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { downloadState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = AccentCyan,
                                trackColor = SlateCanvas
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stats: Speed & Size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Kelajuan: ${downloadState.speedFormatted}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "${downloadState.bytesDownloadedFormatted} / ${downloadState.totalBytesFormatted}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = downloadState.statusMessage,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                            color = TextMuted,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { AppDownloadManager.cancelDownload() },
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SlateBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Batalkan", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }

                // Completed Card
                if (downloadState.status == DownloadStatus.COMPLETED) {
                    val downloadedUri = downloadState.downloadedUri
                    val downloadedFile = downloadState.downloadedFile
                    val displayName = downloadState.downloadedName.ifEmpty { downloadedFile?.name ?: downloadState.fileName }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentEmerald.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tersimpan di Download/IO-Tools/$displayName\nFile sudah terdaftar di MediaStore dan siap diputar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentEmerald,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (downloadedUri != null || downloadedFile != null) {
                                Button(
                                    onClick = {
                                        if (downloadedUri != null) {
                                            AppDownloadManager.playDownloadedVideo(context, downloadedUri)
                                        } else if (downloadedFile != null) {
                                            AppDownloadManager.playDownloadedVideo(context, downloadedFile)
                                        }
                                    },
                                    modifier = Modifier.weight(1.3f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentEmerald,
                                        contentColor = SlateCanvas
                                    )
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PUTAR FILE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (downloadedUri != null) {
                                            AppDownloadManager.shareDownloadedVideo(context, downloadedUri)
                                        } else if (downloadedFile != null) {
                                            AppDownloadManager.shareDownloadedVideo(context, downloadedFile)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, SlateBorder)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bagikan", fontSize = 11.sp, color = TextPrimary)
                                }
                            }

                            IconButton(
                                onClick = { AppDownloadManager.resetState() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                            }
                        }
                    }
                }

                // Error Card
                if (downloadState.status == DownloadStatus.ERROR) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = downloadState.errorMessage ?: "Terjadi kesalahan saat mengunduh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentRose,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { AppDownloadManager.resetState() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentRoseSoft,
                                contentColor = AccentRose
                            ),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Tutup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
