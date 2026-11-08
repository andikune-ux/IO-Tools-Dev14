package dev.andikune.iottools.tools.privacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.math.log2

@Composable
fun PrivacyTools() {
    val context = LocalContext.current
    var activeSubTool by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "Privacy Vault",
                subtitle = "Password generator • Secure notes • TOTP 2FA",
                statusText = "LOCAL",
                statusColor = AccentPurple
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Password", "Catatan AES", "TOTP 2FA").forEachIndexed { index, label ->
                    FilterChip(
                        selected = activeSubTool == index,
                        onClick = { activeSubTool = index },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (activeSubTool == 0) {
            item {
                PasswordGeneratorCard(context)
            }
        }

        if (activeSubTool == 1) {
            item {
                SecureNotesCard(context)
            }
        }

        if (activeSubTool == 2) {
            item {
                TotpCard(context)
            }
        }
    }
}

@Composable
private fun PasswordGeneratorCard(context: Context) {
    var length by remember { mutableFloatStateOf(20f) }
    var upper by remember { mutableStateOf(true) }
    var lower by remember { mutableStateOf(true) }
    var numbers by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }

    fun regenerate() {
        password = generatePassword(length.toInt(), upper, lower, numbers, symbols)
    }

    LaunchedEffect(length, upper, lower, numbers, symbols) {
        regenerate()
    }

    val poolSize = (if (upper) UPPER_CHARS.length else 0) +
        (if (lower) LOWER_CHARS.length else 0) +
        (if (numbers) NUMBER_CHARS.length else 0) +
        (if (symbols) SYMBOL_CHARS.length else 0)
    val entropy = if (poolSize > 1) (length * log2(poolSize.toDouble())).toInt() else 0

    CyberCard(borderColor = AccentRose) {
        Text("Password Generator", color = AccentRose, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateCanvas, RoundedCornerShape(10.dp))
                .border(1.dp, AccentRose.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    password,
                    color = AccentEmerald,
                    fontFamily = JetBrainsMono,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { regenerate() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Buat ulang", tint = AccentCyan)
                }
                IconButton(onClick = { copyText(context, password, "Password") }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin", tint = AccentRose)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text("Panjang ${length.toInt()} • Entropi $entropy bit", color = TextSecondary, fontSize = 12.sp)
        if (poolSize == 0) {
            Text("Pilih minimal satu kelompok karakter.", color = AccentRose, fontSize = 11.sp)
        }
        Slider(
            value = length,
            onValueChange = { length = it },
            valueRange = 8f..64f,
            steps = 55
        )
        CyberToggleRow("Huruf besar (A-Z)", "Tambahkan karakter kapital", upper, onCheckedChange = { upper = it })
        CyberToggleRow("Huruf kecil (a-z)", "Tambahkan karakter kecil", lower, onCheckedChange = { lower = it })
        CyberToggleRow("Angka (0-9)", "Tambahkan digit", numbers, onCheckedChange = { numbers = it })
        CyberToggleRow("Simbol", "Tambahkan !@#\$%^&* dan lainnya", symbols, onCheckedChange = { symbols = it })
    }
}

@Composable
private fun SecureNotesCard(context: Context) {
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("io_tools_vault", Context.MODE_PRIVATE) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var hasSaved by remember { mutableStateOf(prefs.contains("note_cipher")) }
    var cipherLength by remember { mutableIntStateOf(prefs.getString("note_cipher", "").orEmpty().length) }

    fun encryptAndSave() {
        if (title.isBlank() || body.isBlank()) {
            status = "Judul dan isi catatan wajib diisi."
            return
        }
        if (passphrase.length !in 12..128) {
            status = "Passphrase harus berisi 12–128 karakter."
            return
        }
        busy = true
        status = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val payload = JSONObject().apply {
                        put("title", title)
                        put("body", body)
                    }.toString()
                    VaultCrypto.encrypt(payload, passphrase)
                }
            }
            result.onSuccess { cipher ->
                prefs.edit().putString("note_cipher", cipher).apply()
                hasSaved = true
                cipherLength = cipher.length
                title = ""
                body = ""
                passphrase = ""
                status = "Catatan dienkripsi AES-256-GCM dan disimpan lokal."
                Toast.makeText(context, "Catatan terkunci.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                status = "Enkripsi gagal: ${it.message}"
            }
            busy = false
        }
    }

    fun decryptSaved() {
        val cipher = prefs.getString("note_cipher", null)
        if (cipher.isNullOrBlank()) {
            status = "Belum ada catatan terenkripsi."
            return
        }
        if (passphrase.isBlank()) {
            status = "Masukkan passphrase untuk membuka catatan."
            return
        }
        busy = true
        status = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val json = JSONObject(VaultCrypto.decrypt(cipher, passphrase))
                    json.optString("title") to json.optString("body")
                }
            }
            result.onSuccess { (savedTitle, savedBody) ->
                title = savedTitle
                body = savedBody
                status = "Catatan berhasil didekripsi."
            }.onFailure {
                status = "Dekripsi gagal: passphrase salah atau data rusak."
            }
            busy = false
        }
    }

    CyberCard(borderColor = AccentCyan) {
        Text("Secure Notes (AES-256-GCM)", color = AccentCyan, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Enkripsi dilakukan di perangkat. Passphrase tidak disimpan; catatan hanya dapat dibuka dengan passphrase yang sama.",
            color = TextSecondary,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        CyberTextField(value = title, onValueChange = { title = it.take(120) }, label = "Judul catatan")
        Spacer(modifier = Modifier.height(8.dp))
        CyberTextField(value = body, onValueChange = { body = it.take(20_000) }, label = "Isi catatan", singleLine = false)
        Spacer(modifier = Modifier.height(8.dp))
        CyberTextField(
            value = passphrase,
            onValueChange = { passphrase = it.take(128) },
            label = "Passphrase",
            placeholder = "12–128 karakter",
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = ::encryptAndSave,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRose, contentColor = SlateCanvas)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (busy) "..." else "ENKRIPSI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = ::decryptSaved,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald, contentColor = SlateCanvas)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (busy) "..." else "DEKRIPSI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        if (hasSaved) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tersimpan: $cipherLength karakter ciphertext", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        prefs.edit().remove("note_cipher").apply()
                        hasSaved = false
                        cipherLength = 0
                        title = ""
                        body = ""
                        passphrase = ""
                        status = "Catatan terenkripsi dihapus."
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus catatan", tint = AccentRose, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (status != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(status!!, color = if (status!!.startsWith("Catatan berhasil") || status!!.startsWith("Catatan dienkripsi")) AccentEmerald else AccentRose, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TotpCard(context: Context) {
    val prefs = remember { context.getSharedPreferences("io_tools_vault", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var account by remember { mutableStateOf(prefs.getString("totp_account", "Akun saya").orEmpty()) }
    var code by remember { mutableStateOf("------") }
    var remaining by remember { mutableIntStateOf(30) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { TotpSecretStorage.load(context) }
        secret = loaded.secret
        isSaved = loaded.secret.isNotBlank()
        error = loaded.error
        isLoading = false
    }

    LaunchedEffect(secret, isLoading) {
        if (isLoading || secret.isBlank()) {
            code = "------"
            return@LaunchedEffect
        }
        while (true) {
            val epoch = System.currentTimeMillis() / 1000
            val step = epoch / 30
            remaining = (30 - (epoch % 30)).toInt()
            val result = runCatching { generateTotp(secret, step) }
            result.onSuccess {
                code = it
                error = null
            }.onFailure {
                code = "------"
                error = it.message ?: "Secret Base32 tidak valid."
            }
            delay(1000)
        }
    }

    fun saveSecret() {
        if (isLoading || isSaving) return
        val decoded = runCatching { Base32.decode(secret) }.getOrNull()
        if (decoded == null || decoded.size < 10) {
            error = "Secret Base32 tidak valid atau terlalu pendek (minimal 80 bit)."
            return
        }
        val value = secret
        isSaving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { TotpSecretStorage.save(context, value) }
            }
            result.onSuccess {
                isSaved = true
                status = "Secret disimpan terenkripsi di Android Keystore."
                error = null
            }.onFailure {
                error = "Gagal menyimpan secret: ${it.message}"
            }
            isSaving = false
        }
    }

    fun deleteSecret() {
        if (isLoading || isSaving) return
        isSaving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { TotpSecretStorage.delete(context) }
            }
            result.onSuccess {
                secret = ""
                isSaved = false
                code = "------"
                status = "Secret TOTP dihapus dari penyimpanan lokal."
                error = null
            }.onFailure {
                error = "Gagal menghapus secret: ${it.message}"
            }
            isSaving = false
        }
    }

    CyberCard(borderColor = AccentEmerald) {
        Text("TOTP Authenticator (RFC 6238)", color = AccentEmerald, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("HMAC-SHA1, 6 digit, periode 30 detik. Secret disimpan terenkripsi menggunakan Android Keystore.", color = TextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(12.dp))
        CyberTextField(
            value = account,
            onValueChange = {
                account = it.take(100)
                prefs.edit().putString("totp_account", account).apply()
            },
            label = "Label akun"
        )
        Spacer(modifier = Modifier.height(8.dp))
        CyberTextField(
            value = secret,
            onValueChange = {
                secret = it.take(128).trim()
                isSaved = false
                status = null
                error = null
            },
            label = "Secret Base32",
            placeholder = "Tempel secret dari layanan akun",
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { secret = Base32.encode(ByteArray(20).also { SecureRandom().nextBytes(it) }); isSaved = false; status = null; error = null },
                enabled = !isLoading && !isSaving,
                modifier = Modifier.weight(1f)
            ) { Text("BUAT SECRET BARU") }
            OutlinedButton(
                onClick = ::saveSecret,
                enabled = !isLoading && !isSaving && secret.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text(if (isSaving) "MENYIMPAN..." else "SIMPAN SECRET") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val label = Uri.encode(account.ifBlank { "Akun" })
                    val issuer = Uri.encode("IO Tools")
                    val uri = "otpauth://totp/$issuer:$label?secret=$secret&issuer=$issuer&algorithm=SHA1&digits=6&period=30"
                    copyText(context, uri, "TOTP setup URI")
                },
                enabled = !isLoading && secret.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("SALIN SETUP URI") }
            OutlinedButton(
                onClick = ::deleteSecret,
                enabled = !isLoading && !isSaving && (isSaved || secret.isNotBlank()),
                modifier = Modifier.weight(1f)
            ) { Text("HAPUS SECRET") }
        }
        if (isLoading) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (isSaved) {
            Text("Secret tersimpan terenkripsi.", color = AccentEmerald, fontSize = 11.sp)
        } else if (secret.isNotBlank()) {
            Text("Perubahan belum disimpan.", color = AccentAmber, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateCanvas, RoundedCornerShape(12.dp))
                .border(1.dp, AccentEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = code.chunked(3).joinToString(" "),
                    color = AccentEmerald,
                    fontFamily = ShareTechMono,
                    fontSize = 38.sp,
                    letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { remaining / 30f },
                    modifier = Modifier.width(190.dp).height(4.dp),
                    color = if (remaining > 5) AccentEmerald else AccentRose,
                    trackColor = SlateSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Berganti dalam ${remaining}s", color = TextSecondary, fontSize = 11.sp)
            }
        }
        if (error != null || status != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                error ?: status.orEmpty(),
                color = if (error == null) AccentEmerald else AccentRose,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        CyberButton(
            text = "SALIN KODE",
            color = AccentEmerald,
            enabled = code != "------",
            onClick = { copyText(context, code.filter { it.isDigit() }, "Kode TOTP") }
        )
    }
}

private const val UPPER_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
private const val LOWER_CHARS = "abcdefghijkmnopqrstuvwxyz"
private const val NUMBER_CHARS = "23456789"
private const val SYMBOL_CHARS = "!@#\$%^&*()-_=+[]{}"

internal fun generatePassword(length: Int, upper: Boolean, lower: Boolean, numbers: Boolean, symbols: Boolean): String {
    val groups = mutableListOf<String>()
    if (upper) groups.add(UPPER_CHARS)
    if (lower) groups.add(LOWER_CHARS)
    if (numbers) groups.add(NUMBER_CHARS)
    if (symbols) groups.add(SYMBOL_CHARS)
    if (groups.isEmpty()) return ""

    val random = SecureRandom()
    val chars = mutableListOf<Char>()
    groups.forEach { chars.add(it[random.nextInt(it.length)]) }
    val all = groups.joinToString("")
    while (chars.size < length) {
        chars.add(all[random.nextInt(all.length)])
    }
    for (i in chars.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val tmp = chars[i]
        chars[i] = chars[j]
        chars[j] = tmp
    }
    return chars.take(length).joinToString("")
}

private fun generateTotp(secretBase32: String, timeStep: Long): String {
    val key = Base32.decode(secretBase32)
    if (key.isEmpty()) throw IllegalArgumentException("Secret Base32 tidak valid.")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key, "HmacSHA1"))
    val data = ByteArray(8)
    var value = timeStep
    for (i in 7 downTo 0) {
        data[i] = (value and 0xff).toByte()
        value = value shr 8
    }
    val hash = mac.doFinal(data)
    val offset = hash[hash.size - 1].toInt() and 0x0f
    val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
        ((hash[offset + 1].toInt() and 0xff) shl 16) or
        ((hash[offset + 2].toInt() and 0xff) shl 8) or
        (hash[offset + 3].toInt() and 0xff)
    return String.format("%06d", binary % 1_000_000)
}

private object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(input: String): ByteArray {
        val cleaned = input.uppercase().replace(Regex("[\\s-]"), "").trimEnd('=')
        if (cleaned.isEmpty()) throw IllegalArgumentException("Secret Base32 kosong.")
        val output = ByteArray((cleaned.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var index = 0
        for (ch in cleaned) {
            val value = ALPHABET.indexOf(ch)
            if (value < 0) throw IllegalArgumentException("Karakter Base32 tidak valid: $ch")
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output[index++] = ((buffer shr (bitsLeft - 8)) and 0xff).toByte()
                bitsLeft -= 8
            }
        }
        return if (index == output.size) output else output.copyOf(index)
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        input.forEach { byte ->
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                result.append(ALPHABET[(buffer shr (bitsLeft - 5)) and 31])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) result.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 31])
        return result.toString()
    }
}

private object VaultCrypto {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun encrypt(plain: String, passphrase: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return listOf(
            "v1",
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        ).joinToString(":")
    }

    fun decrypt(payload: String, passphrase: String): String {
        val parts = payload.split(":")
        if (parts.size != 4 || parts[0] != "v1") throw IllegalArgumentException("Format ciphertext tidak dikenal.")
        val salt = Base64.decode(parts[1], Base64.NO_WRAP)
        val iv = Base64.decode(parts[2], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[3], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val algorithm = runCatching { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256") }
            .getOrElse { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1") }
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val key = algorithm.generateSecret(spec).encoded
        return SecretKeySpec(key, "AES")
    }
}


private object TotpSecretStorage {
    private const val ALIAS = "io_tools_totp_secret_v1"
    private const val PREFS_NAME = "io_tools_vault"
    private const val CIPHER_KEY = "totp_secret_cipher"
    private const val LEGACY_KEY = "totp_secret"

    fun load(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val payload = prefs.getString(CIPHER_KEY, null)
        if (payload.isNullOrBlank()) {
            val legacy = prefs.getString(LEGACY_KEY, null).orEmpty()
            if (legacy == "JBSWY3DPEHPK3PXP") {
                prefs.edit().remove(LEGACY_KEY).apply()
                return ""
            }
            if (legacy.isNotBlank()) {
                val migrated = runCatching { save(context, legacy) }.isSuccess
                if (migrated) prefs.edit().remove(LEGACY_KEY).apply()
            }
            return legacy
        }
        return runCatching {
            val parts = payload.split(":")
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            require(iv.size == 12 && encrypted.size >= 16)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun save(context: Context, secret: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (secret.isBlank()) {
            prefs.edit().remove(CIPHER_KEY).remove(LEGACY_KEY).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(CIPHER_KEY, payload).remove(LEGACY_KEY).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

private fun copyText(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label disalin.", Toast.LENGTH_SHORT).show()
}
