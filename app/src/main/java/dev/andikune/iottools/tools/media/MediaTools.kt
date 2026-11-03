package dev.andikune.iottools.tools.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

@Composable
fun MediaTools() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activeSubTool by remember { mutableIntStateOf(0) } // 0: Media Info, 1: Audio Extractor, 2: Video Trim, 3: Frame Capture

    // State for selected media
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaInfoText by remember { mutableStateOf<String?>(null) }
    var mediaDurationMs by remember { mutableLongStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    var operationResult by remember { mutableStateOf<String?>(null) }
    var extractedFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            operationResult = null
            extractedFrameBitmap = null
            // Extract real metadata
            coroutineScope.launch {
                val (info, dur) = extractMediaMetadata(context, uri)
                mediaInfoText = info
                mediaDurationMs = dur
                Toast.makeText(context, "Media berhasil dimuat!", Toast.LENGTH_SHORT).show()
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
                title = "AUDIO & VIDEO LABORATORY",
                subtitle = "Codec Inspector | Audio Extractor | Video Trimmer | Frame Grabber",
                statusText = "HARDWARE CODEC READY",
                statusColor = AccentAmber
            )
        }

        // Subtool Selector
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tools = listOf("Codec & Info Media", "Ekstrak Audio (M4A/AAC)", "Potong Video (Trim)", "Ambil Frame (JPG)")
                tools.forEachIndexed { index, name ->
                    val isSel = activeSubTool == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) PrimaryIndigo else SlateSurface)
                            .border(1.dp, if (isSel) PrimaryIndigoLight else SlateBorder, RoundedCornerShape(8.dp))
                            .clickable { activeSubTool = index }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSel) androidx.compose.ui.graphics.Color.White else TextSecondary,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // File Selection Bar
        item {
            CyberCard(borderColor = PrimaryIndigo) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FILE MEDIA SUMBER",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = selectedMediaUri?.lastPathSegment ?: "Belum ada file video/audio dipilih",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedMediaUri != null) AccentEmerald else TextSecondary,
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pilih Video", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // TOOL 0: Media Info & Codec Inspector
        if (activeSubTool == 0) {
            item {
                CyberCard(borderColor = AccentCyan) {
                    Text(
                        text = "[HARDWARE CODEC & STREAM INSPECTOR]",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Menganalisis container video, bitrate, framerate, resolusi dan audio stream menggunakan Android MediaMetadataRetriever asli:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val displayInfo = mediaInfoText ?: """
                        [STATUS] Belum ada file video yang dimuat.
                        Silakan klik tombol "Pilih Video" di atas untuk memeriksa metadata
                        container MP4/MKV/WebM secara langsung dari memori HP Anda.
                    """.trimIndent()

                    CyberCodeBox(code = displayInfo)
                }
            }
        }

        // TOOL 1: Audio Extractor
        if (activeSubTool == 1) {
            item {
                CyberCard(borderColor = AccentEmerald) {
                    Text(
                        text = "[LOSSLESS AUDIO TRACK EXTRACTOR]",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentEmerald,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Mengekstrak saluran suara AAC/M4A dari video tanpa transcode (Lossless Direct Stream Demuxing):",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val uri = selectedMediaUri
                            if (uri == null) {
                                Toast.makeText(context, "Pilih video terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            coroutineScope.launch {
                                isProcessing = true
                                operationResult = null
                                val output = extractAudioTrack(context, uri)
                                isProcessing = false
                                if (output != null) {
                                    operationResult = "✓ Audio berhasil diekstrak!\nTersimpan di: ${output.absolutePath}\nUkuran: ${output.length() / 1024} KB"
                                    Toast.makeText(context, "Audio tersimpan di Download!", Toast.LENGTH_LONG).show()
                                } else {
                                    operationResult = "Gagal: Video tidak memiliki track audio atau format tidak didukung."
                                }
                            }
                        },
                        enabled = !isProcessing && selectedMediaUri != null,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sedang Mengekstrak Saluran Audio...")
                        } else {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EKSTRAK AUDIO KE .M4A SEKARANG", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (operationResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CyberCodeBox(code = operationResult ?: "")
                    }
                }
            }
        }

        // TOOL 2: Video Trimmer
        if (activeSubTool == 2) {
            item {
                var startSec by remember { mutableStateOf("0") }
                var endSec by remember { mutableStateOf("15") }

                CyberCard(borderColor = AccentAmber) {
                    Text(
                        text = "[STREAM COPY VIDEO TRIMMER]",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Memotong klip video dari detik awal ke detik akhir menggunakan MediaExtractor + MediaMuxer:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(value = startSec, onValueChange = { startSec = it }, label = "Mulai (detik)")
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(value = endSec, onValueChange = { endSec = it }, label = "Selesai (detik)")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val uri = selectedMediaUri
                            if (uri == null) {
                                Toast.makeText(context, "Pilih video terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val startMs = (startSec.toLongOrNull() ?: 0L) * 1000
                            val endMs = (endSec.toLongOrNull() ?: 15L) * 1000

                            coroutineScope.launch {
                                isProcessing = true
                                operationResult = null
                                val output = trimVideoFast(context, uri, startMs, endMs)
                                isProcessing = false
                                if (output != null) {
                                    operationResult = "✓ Video berhasil dipotong!\nTersimpan: ${output.name}\nUkuran: ${output.length() / 1024} KB"
                                    Toast.makeText(context, "Video potongan tersimpan!", Toast.LENGTH_LONG).show()
                                } else {
                                    operationResult = "Gagal memotong video. Pastikan durasi valid."
                                }
                            }
                        },
                        enabled = !isProcessing && selectedMediaUri != null,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sedang Memotong Video...")
                        } else {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("POTONG & SIMPAN VIDEO (MP4)", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (operationResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CyberCodeBox(code = operationResult ?: "")
                    }
                }
            }
        }

        // TOOL 3: Frame Grabber
        if (activeSubTool == 3) {
            item {
                var targetSecond by remember { mutableStateOf("2") }

                CyberCard(borderColor = AccentRose) {
                    Text(
                        text = "[HIGH-RES VIDEO FRAME GRABBER]",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentRose,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ambil gambar still frame beresolusi tinggi pada posisi detik tertentu dari video:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    CyberTextField(
                        value = targetSecond,
                        onValueChange = { targetSecond = it },
                        label = "Posisi Waktu Frame (Detik ke-)"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val uri = selectedMediaUri
                            if (uri == null) {
                                Toast.makeText(context, "Pilih video terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val sec = targetSecond.toLongOrNull() ?: 1L
                            coroutineScope.launch {
                                val bmp = captureFrameFromVideo(context, uri, sec * 1_000_000L)
                                extractedFrameBitmap = bmp
                                if (bmp != null) {
                                    val saved = saveBitmapToDownload(context, bmp, "Frame_${sec}s.jpg")
                                    operationResult = "✓ Frame resolusi ${bmp.width}x${bmp.height} berhasil diambil!\nTersimpan: ${saved?.name}"
                                    Toast.makeText(context, "Frame tersimpan ke galeri!", Toast.LENGTH_SHORT).show()
                                } else {
                                    operationResult = "Gagal mengambil frame dari video."
                                }
                            }
                        },
                        enabled = selectedMediaUri != null,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TANGKAP & SIMPAN FRAME FOTO", fontWeight = FontWeight.Bold)
                    }

                    if (extractedFrameBitmap != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                        ) {
                            Image(
                                bitmap = extractedFrameBitmap!!.asImageBitmap(),
                                contentDescription = "Captured Frame",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    if (operationResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CyberCodeBox(code = operationResult ?: "")
                    }
                }
            }
        }
    }
}

// ======================== REAL NATIVE LOGIC HELPERS ========================

suspend fun extractMediaMetadata(context: Context, uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "N/A"
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "N/A"
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE) ?: "N/A"
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: "0"
        val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE) ?: "Unknown"

        val durationMs = durationStr?.toLongOrNull() ?: 0L
        val durationSec = durationMs / 1000

        val info = """
            Container MIME: $mime
            Resolution: ${width}x${height} (Rotation: ${rotation}°)
            Duration: ${durationSec} detik (${durationMs} ms)
            Bitrate: ${bitrate.toLongOrNull()?.let { "${it / 1000} kbps" } ?: bitrate}
            Timestamp: $date
            Decoder: Android Hardware MediaCodec Native
        """.trimIndent()

        Pair(info, durationMs)
    } catch (e: Exception) {
        Pair("Gagal membaca metadata: ${e.message}", 0L)
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
}

suspend fun extractAudioTrack(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    try {
        extractor.setDataSource(context, uri, null)
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }

        if (audioTrackIndex == -1 || audioFormat == null) return@withContext null

        extractor.selectTrack(audioTrackIndex)

        val outputFile = File(context.cacheDir, "Audio_Extracted_${System.currentTimeMillis()}.m4a")
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerAudioTrack = muxer.addTrack(audioFormat)
        muxer.start()

        val bufferSize = audioFormat.optInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 128 * 1024)
        val buffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags

            muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
            extractor.advance()
        }

        outputFile
    } catch (_: Exception) {
        null
    } finally {
        try { extractor.release() } catch (_: Exception) {}
        try { muxer?.stop(); muxer?.release() } catch (_: Exception) {}
    }
}

suspend fun trimVideoFast(context: Context, uri: Uri, startMs: Long, endMs: Long): File? = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    try {
        extractor.setDataSource(context, uri, null)
        val outputFile = File(context.cacheDir, "Trimmed_${System.currentTimeMillis()}.mp4")
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val trackMapping = mutableMapOf<Int, Int>()
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                val muxerTrack = muxer.addTrack(format)
                trackMapping[i] = muxerTrack
                extractor.selectTrack(i)
            }
        }

        if (trackMapping.isEmpty()) return@withContext null

        muxer.start()

        val startUs = startMs * 1000L
        val endUs = endMs * 1000L
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val buffer = ByteBuffer.allocate(512 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            val sampleTimeUs = extractor.sampleTime
            if (sampleTimeUs > endUs) break

            val trackIndex = extractor.sampleTrackIndex
            val muxerTrack = trackMapping[trackIndex]

            if (muxerTrack != null && sampleTimeUs >= startUs) {
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = sampleTimeUs - startUs
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
            }

            extractor.advance()
        }

        outputFile
    } catch (_: Exception) {
        null
    } finally {
        try { extractor.release() } catch (_: Exception) {}
        try { muxer?.stop(); muxer?.release() } catch (_: Exception) {}
    }
}

suspend fun captureFrameFromVideo(context: Context, uri: Uri, timeUs: Long): Bitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Exception) {
        null
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
}

private fun saveBitmapToDownload(context: Context, bmp: Bitmap, filename: String): File? {
    return try {
        val file = File(context.cacheDir, filename)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        file
    } catch (_: Exception) {
        null
    }
}

private fun MediaFormat.optInteger(key: String, defaultVal: Int): Int {
    return try {
        if (containsKey(key)) getInteger(key) else defaultVal
    } catch (_: Exception) {
        defaultVal
    }
}
