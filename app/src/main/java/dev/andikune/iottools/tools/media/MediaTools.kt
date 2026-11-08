package dev.andikune.iottools.tools.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import dev.andikune.iottools.tools.common.MediaStoreSaver
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

@Composable
fun MediaTools() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeSubTool by remember { mutableIntStateOf(0) }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var selectedSize by remember { mutableLongStateOf(0L) }
    var metadataLoadToken by remember { mutableIntStateOf(0) }
    var mediaInfoText by remember { mutableStateOf<String?>(null) }
    var mediaDurationMs by remember { mutableLongStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    var operationResult by remember { mutableStateOf<String?>(null) }
    var extractedFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val token = ++metadataLoadToken
            selectedMediaUri = uri
            selectedName = "Membaca metadata..."
            selectedSize = 0L
            mediaInfoText = "Membaca metadata dan track..."
            mediaDurationMs = 0L
            operationResult = null
            extractedFrameBitmap?.recycle()
            extractedFrameBitmap = null
            scope.launch {
                val (info, duration) = extractMediaMetadata(context, uri)
                val (name, size) = withContext(Dispatchers.IO) {
                    (MediaStoreSaver.displayName(context, uri) ?: "media") to MediaStoreSaver.fileSize(context, uri)
                }
                if (token == metadataLoadToken) {
                    selectedName = name
                    selectedSize = size
                    mediaInfoText = info
                    mediaDurationMs = duration
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "Audio & Video Lab",
                subtitle = "Info codec • Ekstrak audio • Trim • Ambil frame",
                statusText = "MEDIACODEC",
                statusColor = AccentAmber
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Info", "Audio", "Trim", "Frame").forEachIndexed { index, label ->
                    FilterChip(
                        selected = activeSubTool == index,
                        onClick = { activeSubTool = index },
                        label = { Text(label) }
                    )
                }
            }
        }

        item {
            CyberCard(borderColor = PrimaryIndigo) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("File media", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            selectedName.ifBlank { "Belum ada file dipilih" },
                            color = if (selectedMediaUri != null) AccentEmerald else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (selectedMediaUri != null) {
                            Text(
                                "Durasi ${mediaDurationMs / 1000} detik • ${formatBytes(selectedSize)}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Button(
                        enabled = !isProcessing,
                        onClick = { picker.launch(arrayOf("video/*", "audio/*")) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pilih")
                    }
                }
            }
        }

        if (activeSubTool == 0) {
            item {
                CyberCard(borderColor = AccentCyan) {
                    Text("Codec & container", color = AccentCyan, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    CyberCodeBox(
                        code = mediaInfoText ?: "Pilih video atau audio untuk membaca metadata dan daftar track nyata dari MediaExtractor.",
                        maxHeight = 360.dp
                    )
                }
            }
        }

        if (activeSubTool == 1) {
            item {
                CyberCard(borderColor = AccentEmerald) {
                    Text("Ekstrak audio lossless", color = AccentEmerald, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Demux track audio tanpa transcode. Cocok untuk MP4/M4A dengan codec AAC/AMR. Jika codec tidak didukung muxer MP4, proses akan menampilkan error yang jelas.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberButton(
                        text = if (isProcessing) "MEMPROSES..." else "EKSTRAK AUDIO KE DOWNLOAD",
                        enabled = !isProcessing && selectedMediaUri != null,
                        color = AccentEmerald,
                        onClick = {
                            val uri = selectedMediaUri ?: return@CyberButton
                            scope.launch {
                                isProcessing = true
                                operationResult = null
                                val result = extractAudioTrack(context, uri)
                                operationResult = result.fold(
                                    onSuccess = { saved ->
                                        "Audio tersimpan di Download/IO-Tools/${MediaStoreSaver.displayName(context, saved)}\nUkuran: ${formatBytes(MediaStoreSaver.fileSize(context, saved))}"
                                    },
                                    onFailure = { "Gagal: ${it.message}" }
                                )
                                isProcessing = false
                            }
                        }
                    )
                    if (operationResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        CyberCodeBox(code = operationResult!!)
                    }
                }
            }
        }

        if (activeSubTool == 2) {
            item {
                var startSec by remember { mutableStateOf("0") }
                var endSec by remember { mutableStateOf("15") }
                CyberCard(borderColor = AccentAmber) {
                    Text("Trim video (stream copy)", color = AccentAmber, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Memotong video dengan MediaExtractor + MediaMuxer. Keyframe terdekat digunakan di awal; output MP4 disimpan ke Download/IO-Tools.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(value = startSec, onValueChange = { startSec = it.filter { c -> c.isDigit() } }, label = "Mulai (detik)")
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(value = endSec, onValueChange = { endSec = it.filter { c -> c.isDigit() } }, label = "Selesai (detik)")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberButton(
                        text = if (isProcessing) "MEMOTONG..." else "POTONG & SIMPAN",
                        enabled = !isProcessing && selectedMediaUri != null,
                        color = AccentAmber,
                        onClick = {
                            val uri = selectedMediaUri ?: return@CyberButton
                            val start = startSec.toLongOrNull() ?: 0L
                            val end = endSec.toLongOrNull() ?: 0L
                            val timeError = validateMediaTimeRange(start * 1000, end * 1000, mediaDurationMs)
                            if (timeError != null) {
                                operationResult = timeError
                                return@CyberButton
                            }
                            scope.launch {
                                isProcessing = true
                                operationResult = null
                                val result = trimVideoFast(context, uri, start * 1000, end * 1000)
                                operationResult = result.fold(
                                    onSuccess = { saved ->
                                        "Video tersimpan di Download/IO-Tools/${MediaStoreSaver.displayName(context, saved)}\nUkuran: ${formatBytes(MediaStoreSaver.fileSize(context, saved))}"
                                    },
                                    onFailure = { "Gagal: ${it.message}" }
                                )
                                isProcessing = false
                            }
                        }
                    )
                    if (operationResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        CyberCodeBox(code = operationResult!!)
                    }
                }
            }
        }

        if (activeSubTool == 3) {
            item {
                var targetSecond by remember { mutableStateOf("2") }
                CyberCard(borderColor = AccentRose) {
                    Text("Ambil frame video", color = AccentRose, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Mengambil frame pada detik yang dipilih dengan MediaMetadataRetriever (OPTION_CLOSEST).", color = TextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    CyberTextField(
                        value = targetSecond,
                        onValueChange = { targetSecond = it.filter { c -> c.isDigit() } },
                        label = "Detik ke-"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberButton(
                        text = if (isProcessing) "MENGAMBIL FRAME..." else "TANGKAP & SIMPAN JPG",
                        enabled = !isProcessing && selectedMediaUri != null,
                        color = AccentRose,
                        onClick = {
                            val uri = selectedMediaUri ?: return@CyberButton
                            val second = targetSecond.toLongOrNull()
                            val frameError = if (second == null) {
                                "Masukkan waktu frame yang valid."
                            } else {
                                validateFrameTime(second, mediaDurationMs)
                            }
                            if (frameError != null) {
                                operationResult = frameError
                                return@CyberButton
                            }
                            val validSecond = second ?: return@CyberButton
                            scope.launch {
                                isProcessing = true
                                operationResult = null
                                val bitmap = captureFrameFromVideo(context, uri, validSecond * 1_000_000L)
                                extractedFrameBitmap = bitmap
                                if (bitmap == null) {
                                    operationResult = "Gagal mengambil frame."
                                } else {
                                    val saved = MediaStoreSaver.saveBitmap(
                                        context,
                                        bitmap,
                                        MediaStoreSaver.timestampName("frame_${validSecond}s", "jpg"),
                                        Bitmap.CompressFormat.JPEG,
                                        92
                                    )
                                    operationResult = if (saved != null) {
                                        "Frame ${bitmap.width}x${bitmap.height} tersimpan sebagai ${MediaStoreSaver.displayName(context, saved)} di Pictures/IO-Tools."
                                    } else {
                                        "Frame diambil tetapi gagal disimpan."
                                    }
                                }
                                isProcessing = false
                            }
                        }
                    )
                    if (extractedFrameBitmap != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SlateCanvas)
                        ) {
                            Image(
                                bitmap = extractedFrameBitmap!!.asImageBitmap(),
                                contentDescription = "Frame",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    if (operationResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(operationResult!!, color = AccentEmerald, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

internal fun validateMediaTimeRange(startMs: Long, endMs: Long, durationMs: Long): String? {
    if (startMs < 0L || endMs <= startMs) return "Rentang waktu tidak valid."
    if (durationMs <= 0L) return "Durasi media tidak dapat dibaca."
    if (startMs >= durationMs || endMs > durationMs) {
        return "Rentang trim harus berada di dalam durasi media (${durationMs / 1000} detik)."
    }
    return null
}

internal fun validateFrameTime(second: Long, durationMs: Long): String? {
    if (second < 0L) return "Waktu frame tidak boleh negatif."
    if (durationMs <= 0L) return "Durasi media tidak dapat dibaca."
    if (second > (durationMs - 1L) / 1000L) {
        return "Waktu frame berada di luar durasi media."
    }
    return null
}

suspend fun extractMediaMetadata(context: Context, uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    val extractor = MediaExtractor()
    try {
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "-"
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "-"
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: "0"
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "unknown"
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)

        extractor.setDataSource(context, uri, null)
        val tracks = buildString {
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: "unknown"
                append("\n  #${i + 1}  $trackMime")
                format.optInt(MediaFormat.KEY_WIDTH)?.let { append("  ${it}x${format.optInt(MediaFormat.KEY_HEIGHT) ?: 0}") }
                format.optInt(MediaFormat.KEY_SAMPLE_RATE)?.let { append("  ${it}Hz ${format.optInt(MediaFormat.KEY_CHANNEL_COUNT) ?: 0}ch") }
                format.optInt(MediaFormat.KEY_BIT_RATE)?.let { append("  ${it / 1000}kbps") }
            }
        }

        val info = buildString {
            appendLine("File: ${MediaStoreSaver.displayName(context, uri) ?: uri.lastPathSegment}")
            appendLine("Ukuran: ${formatBytes(MediaStoreSaver.fileSize(context, uri))}")
            appendLine("Container: $mime")
            appendLine("Durasi: ${durationMs / 1000} detik (${durationMs} ms)")
            appendLine("Video: ${width}x$height  rotasi ${rotation}°")
            appendLine("Bitrate total: ${bitrate?.let { "${it / 1000} kbps" } ?: "-"}")
            if (!title.isNullOrBlank()) appendLine("Judul: $title")
            if (!artist.isNullOrBlank()) appendLine("Artis: $artist")
            appendLine("Track MediaExtractor:")
            append(tracks.ifBlank { "\n  Tidak ada track terbaca." })
        }
        info to durationMs
    } catch (e: Exception) {
        "Gagal membaca metadata: ${e.message}" to 0L
    } finally {
        runCatching { retriever.release() }
        runCatching { extractor.release() }
    }
}

suspend fun extractAudioTrack(context: Context, uri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    val temp = File.createTempFile("audio_", ".m4a", context.cacheDir)
    try {
        extractor.setDataSource(context, uri, null)
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if ((format.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }
        if (audioTrackIndex < 0 || audioFormat == null) {
            throw IllegalStateException("Video tidak memiliki track audio.")
        }
        extractor.selectTrack(audioTrackIndex)
        val mux = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = mux
        val muxTrack = mux.addTrack(audioFormat)
        mux.start()

        val buffer = ByteBuffer.allocate((audioFormat.optInt(MediaFormat.KEY_MAX_INPUT_SIZE) ?: 512 * 1024).coerceAtLeast(64 * 1024))
        val info = MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            mux.writeSampleData(muxTrack, buffer, info)
            if (!extractor.advance()) break
        }
        mux.stop()
        mux.release()
        muxer = null

        val saved = temp.inputStream().use { input ->
            MediaStoreSaver.saveStream(
                context,
                MediaStoreSaver.timestampName("audio", "m4a"),
                "audio/mp4",
                input
            )
        } ?: throw IllegalStateException("Gagal menyimpan audio ke penyimpanan publik.")
        Result.success(saved)
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        runCatching { extractor.release() }
        runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        temp.delete()
    }
}

suspend fun trimVideoFast(context: Context, uri: Uri, startMs: Long, endMs: Long): Result<Uri> = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    val temp = File.createTempFile("trim_", ".mp4", context.cacheDir)
    try {
        if (startMs < 0L || endMs <= startMs) throw IllegalArgumentException("Rentang waktu tidak valid.")
        extractor.setDataSource(context, uri, null)
        val mux = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = mux
        val mapping = mutableMapOf<Int, Int>()
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                mapping[i] = mux.addTrack(format)
                extractor.selectTrack(i)
            }
        }
        if (mapping.isEmpty()) throw IllegalStateException("Tidak ada track video/audio yang dapat di-mux.")
        mux.start()

        val startUs = startMs * 1000
        val endUs = endMs * 1000
        // A fast trim is keyframe-accurate, not frame-accurate. Start at the
        // previous sync sample so the output always contains a decodable video GOP.
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val outputStartUs = extractor.sampleTime.takeIf { it >= 0L } ?: startUs
        val maxInputSize = mapping.keys.mapNotNull { track ->
            extractor.getTrackFormat(track).optInt(MediaFormat.KEY_MAX_INPUT_SIZE)
        }.maxOrNull() ?: (1024 * 1024)
        val buffer = ByteBuffer.allocate(maxInputSize.coerceIn(256 * 1024, 32 * 1024 * 1024))
        val info = MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val timeUs = extractor.sampleTime
            if (timeUs > endUs) break
            if (timeUs >= outputStartUs) {
                val muxTrack = mapping[extractor.sampleTrackIndex] ?: continue
                info.offset = 0
                info.size = size
                info.presentationTimeUs = timeUs - outputStartUs
                info.flags = extractor.sampleFlags
                mux.writeSampleData(muxTrack, buffer, info)
            }
            if (!extractor.advance()) break
        }
        mux.stop()
        mux.release()
        muxer = null

        val saved = temp.inputStream().use { input ->
            MediaStoreSaver.saveStream(
                context,
                MediaStoreSaver.timestampName("trim", "mp4"),
                "video/mp4",
                input
            )
        } ?: throw IllegalStateException("Gagal menyimpan video ke penyimpanan publik.")
        Result.success(saved)
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        runCatching { extractor.release() }
        runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        temp.delete()
    }
}

suspend fun captureFrameFromVideo(context: Context, uri: Uri, timeUs: Long): Bitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun MediaFormat.optInt(key: String): Int? = runCatching {
    if (containsKey(key)) getInteger(key) else null
}.getOrNull()

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
