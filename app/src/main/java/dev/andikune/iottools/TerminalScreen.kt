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
import kotlinx.coroutines.flow.MutableSharedFlow
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
    val results = MutableSharedFlow<String>(extraBufferCapacity = 8)
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: Intent) {
        val result = intent.getBundleExtra("com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE")
        if (result == null) {
            TermuxCommandEvents.results.tryEmit("[Termux] Tidak ada data hasil dari Termux.")
            return
        }
        val stdout = result.getString("com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT").orEmpty()
        val stderr = result.getString("com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR").orEmpty()
        val exitCode = result.getInt("com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE", -1)
        val error = result.getString("com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG").orEmpty()
        val message = buildString {
            if (stdout.isNotBlank()) append(stdout)
            if (stderr.isNotBlank()) append("\n").append(stderr)
            if (error.isNotBlank()) append("\n[Termux] ").append(error)
            append("\n[Termux exit: ").append(exitCode).append("]")
        }
        TermuxCommandEvents.results.tryEmit(message)
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
                val result = executeTerminalCommand(context, input, workingDirectory, home, downloadDirectory)
                val cwdUpdate = result.output.firstOrNull { it.startsWith("__IO_TERMINAL_CWD__") }
                if (cwdUpdate != null) {
                    workingDirectory = cwdUpdate.removePrefix("__IO_TERMINAL_CWD__")
                    append(workingDirectory)
                } else if (result.output.isNotEmpty()) {
                    append(result.output.joinToString("\n"))
                }
                if (result.timedOut) append("[timeout] Proses dihentikan setelah 30 detik.")
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

    LaunchedEffect(Unit) {
        TermuxCommandEvents.results.collect { append(it) }
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
                Text(if (running) "…" else "RUN", fontSize = 11.sp)
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
    downloads: File
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
        normalized == "whoami" -> {
            val process = ProcessBuilder("/system/bin/id").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readLines()
            TerminalCommandResult(output, process.waitFor())
        }
        normalized == "pkg" || normalized.startsWith("pkg ") ||
            normalized == "apt" || normalized.startsWith("apt ") -> {
            try {
                val intent = Intent().apply {
                    component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                    action = "com.termux.RUN_COMMAND"
                    putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", normalized))
                    putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                    putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                    val resultIntent = Intent(context, TermuxResultReceiver::class.java)
                    val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
                        if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    val resultPendingIntent = PendingIntent.getBroadcast(
                        context,
                        (System.nanoTime() and 0x7fffffff).toInt(),
                        resultIntent,
                        pendingFlags
                    )
                    putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", resultPendingIntent)
                }
                context.startService(intent)
                TerminalCommandResult(
                    listOf("Perintah dikirim ke Termux: $normalized", "Output instalasi akan tampil di sesi Termux.", "Aktifkan Allow external apps di Termux Settings jika akses ditolak."),
                    0
                )
            } catch (error: Exception) {
                TerminalCommandResult(
                    listOf("Gagal membuka Termux: ${error.message ?: error.javaClass.simpleName}", "Pastikan Termux terpasang dan Allow external apps diaktifkan."),
                    127
                )
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
            val connection = url.openConnection().apply { connectTimeout = 15000; readTimeout = 30000 }
            connection.connect()
            if (connection.url.protocol != "https") {
                connection.getInputStream().close()
                return@withContext TerminalCommandResult(listOf("Redirect non-HTTPS ditolak."), 2)
            }
            connection.getInputStream().use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 100L * 1024L * 1024L) {
                            target.delete()
                            return@withContext TerminalCommandResult(listOf("Download dibatasi maksimal 100 MB."), 1)
                        }
                        output.write(buffer, 0, count)
                    }
                }
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
        }
    }
}
