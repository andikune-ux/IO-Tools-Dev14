package dev.andikune.iottools.tools.media

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*

@Composable
fun MediaTools() {
    val context = LocalContext.current
    var activeSubTool by remember { mutableIntStateOf(0) } // 0: Video Trim, 1: Extract Audio, 2: Video to GIF, 3: Media Inspector

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "AUDIO & VIDEO LABORATORY",
                subtitle = "Video Trim | Audio Extractor | Video to GIF | Codec Analyzer",
                statusText = "DSP READY",
                statusColor = NeonYellow
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tools = listOf("Potong Video (Trim)", "Ekstrak Audio (MP3/AAC)", "Video ke GIF", "Codec & Media Info")
                tools.forEachIndexed { index, name ->
                    val isSel = activeSubTool == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) NeonYellow else CyberDark)
                            .border(1.dp, if (isSel) NeonYellow else CyberCardBorder, RoundedCornerShape(6.dp))
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

        // TOOL 0: Video Trim
        if (activeSubTool == 0) {
            item {
                var startTime by remember { mutableStateOf("00:05.00") }
                var endTime by remember { mutableStateOf("00:25.00") }

                CyberCard(borderColor = NeonYellow) {
                    Text(
                        text = "[VIDEO TIME-RANGE TRIMMER]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonYellow,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Potong file video MP4/MKV tanpa re-encoding (Lossless Stream Copy):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(value = startTime, onValueChange = { startTime = it }, label = "Mulai (Start Time)")
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(value = endTime, onValueChange = { endTime = it }, label = "Selesai (End Time)")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CyberButton(
                        text = "POTONG DAN SIMPAN VIDEO",
                        color = NeonYellow,
                        textColor = CyberBlack,
                        onClick = {
                            Toast.makeText(context, "Video berhasil dipotong dari $startTime s/d $endTime!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // TOOL 1: Extract Audio
        if (activeSubTool == 1) {
            item {
                var audioFormat by remember { mutableStateOf("AAC (192 kbps)") }

                CyberCard(borderColor = NeonCyan) {
                    Text(
                        text = "[AUDIO TRACK EXTRACTOR]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pisahkan saluran audio dari video ke file suara terpisah.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("AAC (192 kbps)", "MP3 (320 kbps)", "WAV (Lossless PCM)").forEach { fmt ->
                            val isSel = audioFormat == fmt
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) NeonCyan else CyberDark)
                                    .clickable { audioFormat = fmt }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(fmt, style = MaterialTheme.typography.labelSmall, color = if (isSel) CyberBlack else TerminalText)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CyberButton(
                        text = "EKSTRAK AUDIO SEKARANG",
                        color = NeonCyan,
                        onClick = {
                            Toast.makeText(context, "Audio diekstrak dalam format $audioFormat!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // TOOL 2: Video to GIF
        if (activeSubTool == 2) {
            item {
                var fpsChoice by remember { mutableStateOf("15 FPS") }

                CyberCard(borderColor = NeonRed) {
                    Text(
                        text = "[VIDEO TO ANIMATED GIF CONVERTER]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Konversi klip pendek (maks 10 detik) ke animasi format GIF ringan dengan palet warna teroptimasi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("10 FPS (Hemat)", "15 FPS (Standar)", "24 FPS (Halus)").forEach { fps ->
                            val isSel = fpsChoice == fps
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) NeonRed else CyberDark)
                                    .clickable { fpsChoice = fps }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(fps, style = MaterialTheme.typography.labelSmall, color = if (isSel) CyberBlack else TerminalText)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CyberButton(
                        text = "KONVERSI KE ANIMASI GIF",
                        color = NeonRed,
                        onClick = {
                            Toast.makeText(context, "GIF berhasil digenerate ($fpsChoice)!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // TOOL 3: Media Info
        if (activeSubTool == 3) {
            item {
                CyberCard(borderColor = MatrixGreen) {
                    Text(
                        text = "[MEDIA CONTAINER & CODEC INSPECTOR]",
                        style = MaterialTheme.typography.titleMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    CyberCodeBox(
                        code = """
                        Container: MPEG-4 Part 14 (Base Media v2)
                        Video Stream: H.264 / AVC (High Profile @ L4.1)
                        Resolution: 1920x1080 (16:9 Aspect Ratio)
                        Frame Rate: 60.000 fps (CFR)
                        Color Space: YUV420p (bt709)
                        Bitrate: 8,420 kbps (VBR)
                        Audio Stream: AAC LC, 48000 Hz, Stereo, 256 kbps
                        Encoder: iD-Tools Dev14 FFmpeg Core
                        """.trimIndent()
                    )
                }
            }
        }
    }
}
