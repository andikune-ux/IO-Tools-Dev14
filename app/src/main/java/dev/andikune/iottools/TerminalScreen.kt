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
            "Ketik 'help' untuk daftar perintah. Perintah berjalan sebagai aplikasi, bukan root."
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
                val result = executeTerminalCommand(input, workingDirectory, home, downloadDirectory)
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
                "Built-in: help, clear, cd [path], download <https-url> [filename], pkg",
                "cd mengubah direktori kerja terminal. Perintah shell dijalankan oleh /system/bin/sh.",
                "download mengambil file HTTPS ke: ${downloads.absolutePath}",
                "pkg install tidak tersedia di Android shell standar. Untuk paket Linux lengkap diperlukan environment terpisah seperti Termux/proot."
            ), 0
        )
        normalized == "clear" -> TerminalCommandResult(emptyList(), 0)
        normalized == "pwd" -> TerminalCommandResult(listOf(cwd.absolutePath), 0)
        normalized == "whoami" -> {
            val process = ProcessBuilder("/system/bin/id").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readLines()
            TerminalCommandResult(output, process.waitFor())
        }
        normalized == "pkg" || normalized.startsWith("pkg ") -> TerminalCommandResult(
            listOf("Package manager apt/pkg tidak disertakan Android. Gunakan Termux untuk repositori paket Linux; download hanya menyimpan file ke sandbox aplikasi."), 127
        )
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
            val process = ProcessBuilder("/system/bin/sh", "-c", normalized)
                .directory(cwd)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = home.absolutePath
                    environment()["TMPDIR"] = File(home, "tmp").apply { mkdirs() }.absolutePath
                    environment()["PATH"] = "/system/bin:/system/xbin:${File(home, "bin").absolutePath}"
                }
                .start()
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
            TerminalCommandResult(output.toList(), if (finished) process.exitValue() else 124, !finished)
        }
    }
}
