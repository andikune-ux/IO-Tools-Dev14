package dev.andikune.iottools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.content.ComponentName
import android.content.Intent
import android.content.BroadcastReceiver
import android.app.PendingIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.components.CyberTerminalHeader
import dev.andikune.iottools.ui.theme.AccentCyan
import dev.andikune.iottools.ui.theme.AccentEmerald
import dev.andikune.iottools.ui.theme.PrimaryIndigoLight
import dev.andikune.iottools.ui.theme.SlateBorder
import dev.andikune.iottools.ui.theme.SlateCanvas
import dev.andikune.iottools.ui.theme.SlateElevated
import dev.andikune.iottools.ui.theme.TextMuted
import dev.andikune.iottools.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

private object TermuxCommandEvents {
    private val tracker = TermuxCommandTracker()

    fun register(requestId: String): CompletableDeferred<String> = tracker.register(requestId)
    fun complete(requestId: String?, message: String) = tracker.complete(requestId, message)
    fun remove(requestId: String) = tracker.remove(requestId)
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: Intent) {
        val requestId = intent.getStringExtra("iottools.termux.request_id")
        val result = intent.getBundleExtra("result")
        if (result == null) {
            TermuxCommandEvents.complete(requestId, "[Termux] Tidak ada data hasil dari Termux.")
            return
        }
        val stdout = result.getString("stdout").orEmpty()
        val stderr = result.getString("stderr").orEmpty()
        val exitCode = result.getInt("exitCode", -1)
        val error = result.getString("errmsg").orEmpty()
        val message = buildString {
            if (stdout.isNotBlank()) append(stdout.trimEnd())
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(stderr.trimEnd())
            }
            if (error.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("[Termux] ").append(error)
            }
            if (isNotEmpty()) append("\n")
            append("[Termux exit: ").append(exitCode).append("]")
        }
        TermuxCommandEvents.complete(requestId, message)
    }
}

private data class TerminalCommandResult(
    val output: List<String>,
    val exitCode: Int,
    val timedOut: Boolean = false
)

@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val home = remember { File(context.filesDir, "terminal").apply { mkdirs() } }
    val downloadDirectory = remember { File(home, "downloads").apply { mkdirs() } }
    var workingDirectory by remember { mutableStateOf(home.absolutePath) }
    var command by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var localProcessActive by remember { mutableStateOf(false) }
    val activeLocalProcess = remember { AtomicReference<Process?>(null) }
    val lines = remember {
        mutableStateListOf(
            "iD-Tools Android Shell",
            "Shell: /system/bin/sh  |  UID: app sandbox",
            "HOME: ${home.absolutePath}",
            "Ketik 'help'. Gunakan 'su <perintah>' untuk meminta root atau 'pkg install' untuk Termux."
        )
    }
    val listState = rememberLazyListState()

    fun append(value: String) {
        lines.addAll(value.lines().takeLast(2000))
        while (lines.size > 4000) lines.removeAt(0)
    }

    fun runCommand(raw: String) {
        val input = raw.trim()
        if (input.isEmpty() || running) return
        append("$ $input")
        command = ""
        running = true
        scope.launch {
            try {
                val result = executeTerminalCommand(
                    context, input, workingDirectory, home, downloadDirectory, activeLocalProcess
                ) { active ->
                    scope.launch(Dispatchers.Main) { localProcessActive = active }
                }
                val cwdUpdate = result.output.firstOrNull { it.startsWith("__IO_TERMINAL_CWD__") }
                if (cwdUpdate != null) {
                    workingDirectory = cwdUpdate.removePrefix("__IO_TERMINAL_CWD__")
                    append(workingDirectory)
                } else if (result.output.isNotEmpty()) {
                    append(result.output.joinToString("\n"))
                }
                if (result.timedOut) {
                    if (input.startsWith("pkg ") || input.startsWith("apt ")) {
                        append("[timeout] Termux belum mengirim hasil dalam 30 menit. Periksa sesi Termux; proses mungkin masih berjalan.")
                    } else {
                        append("[timeout] Proses lokal dihentikan setelah 30 detik.")
                    }
                }
                else if (result.exitCode != 0) append("[exit ${result.exitCode}]")
                if (input == "clear") lines.clear()
                if (input == "clear") append("Terminal dibersihkan.")
            } catch (error: Exception) {
                append("[error] ${error.message ?: error.javaClass.simpleName}")
            } finally {
                running = false
            }
        }
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        CyberTerminalHeader(
            title = "Android Terminal",
            subtitle = "Real shell • ${workingDirectory}",
            statusText = if (running) "RUNNING" else "READY",
            statusColor = if (running) AccentCyan else AccentEmerald
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = Color(0xFF080D12),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        color = when {
                            line.startsWith("$ ") -> AccentCyan
                            line.startsWith("[error]") || line.startsWith("[exit") || line.startsWith("[timeout]") -> Color(0xFFFF8A80)
                            line.startsWith("iD-Tools") -> PrimaryIndigoLight
                            else -> TextPrimary
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Working directory: $workingDirectory",
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 1
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !running,
                placeholder = { Text("command…", fontSize = 12.sp) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { runCommand(command) })
            )
            Button(
                onClick = { runCommand(command) },
                enabled = !running && command.isNotBlank(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("RUN", fontSize = 11.sp)
            }
            Button(
                onClick = {
                    val process = activeLocalProcess.getAndSet(null)
                    if (process != null) {
                        process.destroyForcibly()
                        localProcessActive = false
                        append("[stop] Sinyal penghentian dikirim ke proses lokal.")
                    }
                },
                enabled = running && localProcessActive,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9B2C2C),
                    disabledContainerColor = Color(0xFF39434D)
                )
            ) {
                Text("STOP", fontSize = 11.sp)
            }
        }
        Text(
            text = "Download: download https://example.com/file.zip • File tersimpan di app/terminal/downloads",
            color = TextMuted,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private suspend fun executeTerminalCommand(
    context: android.content.Context,
    command: String,
    currentDirectory: String,
    home: File,
    downloads: File,
    activeLocalProcess: AtomicReference<Process?>,
    onLocalProcessChanged: (Boolean) -> Unit
): TerminalCommandResult = withContext(Dispatchers.IO) {
    val cwd = File(currentDirectory).canonicalFile
    val normalized = command.trim()
    when {
        normalized == "help" -> return@withContext TerminalCommandResult(
            listOf(
                "Perintah shell Android: ls, pwd, cat, mkdir, cp, mv, rm, find, grep, id, uname, getprop, df, ps, ping, toybox, sh …",
                "Built-in: help, clear, cd [path], download <https-url> [filename], su <command> atau su -c <command>, pkg/apt <command>",
                "cd mengubah direktori kerja terminal. Perintah shell dijalankan oleh /system/bin/sh.",
                "download mengambil file HTTPS ke: ${downloads.absolutePath}",
                "pkg/apt diteruskan ke Termux jika terpasang. Aktifkan Allow external apps di Termux dan izinkan Run commands in Termux environment untuk iD-Tools."
            ), 0
        )
        normalized == "clear" -> TerminalCommandResult(emptyList(), 0)
        normalized == "pwd" -> TerminalCommandResult(listOf(cwd.absolutePath), 0)
        normalized == "pkg" || normalized.startsWith("pkg ") ||
            normalized == "apt" || normalized.startsWith("apt ") -> {
            val requestId = UUID.randomUUID().toString()
            val completion = TermuxCommandEvents.register(requestId)
            try {
                val resultIntent = Intent(context, TermuxResultReceiver::class.java).apply {
                    putExtra("iottools.termux.request_id", requestId)
                }
                val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
                    if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val resultPendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestId.hashCode(),
                    resultIntent,
                    pendingFlags
                )
                val intent = Intent().apply {
                    component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                    action = "com.termux.RUN_COMMAND"
                    putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", normalized))
                    putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                    putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                    putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", resultPendingIntent)
                }
                context.startService(intent)
                val result = withTimeoutOrNull(30L * 60L * 1000L) { completion.await() }
                if (result == null) {
                    TerminalCommandResult(
                        listOf("Tidak ada callback dari Termux setelah 30 menit.", "Periksa apakah Termux masih menjalankan perintah dan apakah Allow external apps aktif."),
                        124,
                        timedOut = true
                    )
                } else {
                    TerminalCommandResult(result.lines().takeLast(2000), if (result.endsWith("[Termux exit: 0]")) 0 else 1)
                }
            } catch (error: Exception) {
                TerminalCommandResult(
                    listOf("Gagal menjalankan perintah Termux: ${error.message ?: error.javaClass.simpleName}", "Pastikan Termux terpasang, Allow external apps aktif, dan izin Run commands diberikan."),
                    127
                )
            } finally {
                TermuxCommandEvents.remove(requestId)
            }
        }
        normalized == "cd" || normalized.startsWith("cd ") -> {
            val argument = normalized.removePrefix("cd").trim().trim('"', '\'')
            val destination = when {
                argument.isEmpty() || argument == "~" -> home
                argument.startsWith("~/") -> File(home, argument.removePrefix("~/"))
                else -> File(cwd, argument)
            }.canonicalFile
            if (destination.isDirectory) {
                // The UI reads this marker to update the prompt directory.
                TerminalCommandResult(listOf("__IO_TERMINAL_CWD__${destination.absolutePath}"), 0)
            } else TerminalCommandResult(listOf("cd: direktori tidak ditemukan: $destination"), 1)
        }
        normalized.startsWith("download ") -> {
            val parts = normalized.split(Regex("\\s+"), limit = 3)
            if (parts.size < 2) return@withContext TerminalCommandResult(listOf("Usage: download <https-url> [filename]"), 2)
            val url = URL(parts[1])
            if (url.protocol != "https") return@withContext TerminalCommandResult(listOf("Download hanya mengizinkan HTTPS."), 2)
            downloads.mkdirs()
            val suggested = parts.getOrNull(2)?.trim()?.trim('"', '\'')
                ?: url.path.substringAfterLast('/').ifBlank { "download-${System.currentTimeMillis()}" }
            val safeName = suggested.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "download.bin" }
            val target = File(downloads, safeName).canonicalFile
            if (target.parentFile != downloads.canonicalFile) return@withContext TerminalCommandResult(listOf("Nama file tidak valid."), 2)
            var currentUrl = url
            var connection: java.net.HttpURLConnection? = null
            var redirectCount = 0
            while (true) {
                if (!currentUrl.protocol.equals("https", ignoreCase = true)) {
                    connection?.disconnect()
                    return@withContext TerminalCommandResult(listOf("Redirect non-HTTPS ditolak."), 2)
                }
                val nextConnection = (currentUrl.openConnection() as? java.net.HttpURLConnection)
                    ?: return@withContext TerminalCommandResult(listOf("Jenis koneksi URL tidak didukung."), 2)
                nextConnection.instanceFollowRedirects = false
                nextConnection.connectTimeout = 15000
                nextConnection.readTimeout = 30000
                nextConnection.requestMethod = "GET"
                val responseCode = nextConnection.responseCode
                if (responseCode in 300..399) {
                    val location = nextConnection.getHeaderField("Location")
                    nextConnection.disconnect()
                    if (location.isNullOrBlank() || redirectCount >= 5) {
                        connection?.disconnect()
                        return@withContext TerminalCommandResult(listOf("Redirect tidak valid atau terlalu banyak."), 2)
                    }
                    currentUrl = URL(currentUrl, location)
                    redirectCount++
                    continue
                }
                if (responseCode !in 200..299) {
                    nextConnection.disconnect()
                    return@withContext TerminalCommandResult(listOf("Download gagal: HTTP $responseCode."), 1)
                }
                connection = nextConnection
                break
            }
            val activeConnection = connection
                ?: return@withContext TerminalCommandResult(listOf("Koneksi download gagal."), 1)
            val declaredLength = activeConnection.contentLengthLong
            if (declaredLength > 100L * 1024L * 1024L) {
                activeConnection.disconnect()
                return@withContext TerminalCommandResult(listOf("Download dibatasi maksimal 100 MB."), 1)
            }
            // Reserve the destination atomically so concurrent downloads cannot replace an existing file.
            if (!target.createNewFile()) {
                activeConnection.disconnect()
                return@withContext TerminalCommandResult(listOf("File tujuan sudah ada; file lama tidak ditimpa: ${target.absolutePath}"), 1)
            }
            val temporaryFile = try {
                File.createTempFile(".iottools-download-", ".part", downloads)
            } catch (error: Exception) {
                target.delete()
                activeConnection.disconnect()
                throw error
            }
            var committed = false
            try {
                activeConnection.inputStream.use { input ->
                    temporaryFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 100L * 1024L * 1024L) {
                                return@withContext TerminalCommandResult(listOf("Download dibatasi maksimal 100 MB."), 1)
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                // The placeholder was created by this process, so replacing it cannot overwrite a pre-existing user file.
                if (!temporaryFile.renameTo(target)) {
                    return@withContext TerminalCommandResult(listOf("Gagal menyimpan file hasil download."), 1)
                }
                committed = true
            } finally {
                temporaryFile.delete()
                if (!committed) target.delete()
                activeConnection.disconnect()
            }
            return@withContext TerminalCommandResult(listOf("Downloaded: ${target.absolutePath} (${target.length()} bytes)"), 0)
        }
        else -> {
            val rootRequested = normalized.startsWith("su ") || normalized.startsWith("sudo ")
            val rootPayload = if (rootRequested) normalized.substringAfter(' ').trim() else normalized
            val shellCommand = if (rootRequested && rootPayload.startsWith("-c ")) {
                rootPayload.removePrefix("-c ").trim().removeSurrounding("\"")
            } else rootPayload
            val processBuilder = if (rootRequested) {
                ProcessBuilder("su", "-c", shellCommand)
            } else {
                ProcessBuilder("/system/bin/sh", "-c", shellCommand)
            }
            processBuilder.directory(cwd)
            processBuilder.redirectErrorStream(true)
            processBuilder.environment().apply {
                put("HOME", home.absolutePath)
                put("TMPDIR", File(home, "tmp").apply { mkdirs() }.absolutePath)
                put("PATH", "/system/bin:/system/xbin:${File(home, "bin").absolutePath}")
            }
            val process = processBuilder.start()
            activeLocalProcess.set(process)
            onLocalProcessChanged(true)
            try {
                // No interactive stdin is exposed in this terminal UI; close it so commands waiting for input receive EOF.
                process.outputStream.close()
                val output = java.util.Collections.synchronizedList(mutableListOf<String>())
                val reader = Thread {
                    process.inputStream.bufferedReader().useLines { sequence ->
                        sequence.forEach { line -> if (output.size < 2000) output.add(line) }
                    }
                }.apply { isDaemon = true; start() }
                val finished = process.waitFor(30, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    process.waitFor(2, TimeUnit.SECONDS)
                }
                reader.join(1000)
                TerminalCommandResult(
                    (if (rootRequested) listOf("[Root requested: approve the Superuser/Magisk prompt]") else emptyList()) + output.toList(),
                    if (finished) process.exitValue() else 124,
                    !finished
                )
            } finally {
                activeLocalProcess.compareAndSet(process, null)
                onLocalProcessChanged(false)
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }
}
