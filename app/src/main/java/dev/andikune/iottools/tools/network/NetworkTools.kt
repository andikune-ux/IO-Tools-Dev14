package dev.andikune.iottools.tools.network

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.IDN
import java.net.URI
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection

internal data class SslTarget(val host: String, val port: Int)

internal fun normalizeNetworkHost(input: String): String? {
    val clean = input.trim()
    if (clean.isBlank() || clean.any { it.isWhitespace() || it.isISOControl() }) return null
    val candidate = if ("://" in clean) clean else "https://$clean"
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.userInfo != null) return null
    val rawHost = uri.host?.removePrefix("[")?.removeSuffix("]") ?: return null
    if (rawHost.contains(':')) {
        return rawHost.takeIf { it.matches(Regex("^[0-9a-fA-F:.]+$")) }
    }
    val host = runCatching { IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase() }.getOrNull()
        ?: return null
    if (host.length > 253 || host.isBlank()) return null
    val labels = host.trimEnd('.').split('.')
    if (labels.any { label ->
            label.isBlank() || label.length > 63 ||
                !label.matches(Regex("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$"))
        }
    ) return null
    return host.trimEnd('.')
}

internal fun normalizeWhoisDomain(input: String): String? {
    val host = normalizeNetworkHost(input) ?: return null
    if (!host.contains('.') || host.contains(':') ||
        host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))
    ) return null
    return host
}

internal fun parseSslTarget(input: String): SslTarget? {
    val clean = input.trim()
    if (clean.isBlank() || clean.any { it.isWhitespace() || it.isISOControl() }) return null
    val candidate = if ("://" in clean) clean else "https://$clean"
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.userInfo != null) return null
    val host = normalizeNetworkHost(uri.host ?: return null) ?: return null
    val port = if (uri.port == -1) 443 else uri.port
    if (port !in 1..65535) return null
    return SslTarget(host, port)
}

@Composable
fun NetworkTools() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
                title = "Network Recon",
                subtitle = "IP Lookup • DNS • TCP Latency • SSL • WHOIS",
                statusText = "LIVE",
                statusColor = AccentCyan
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("IP Lookup", "DNS", "Latency", "SSL", "WHOIS").forEachIndexed { index, name ->
                    FilterChip(
                        selected = activeSubTool == index,
                        onClick = { activeSubTool = index },
                        label = { Text(name) }
                    )
                }
            }
        }

        if (activeSubTool == 0) {
            item {
                var localIps by remember { mutableStateOf(listOf("Memuat interface jaringan...")) }
                var publicInfo by remember { mutableStateOf("Tekan tombol untuk melakukan lookup nyata.") }
                var isLoading by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    localIps = withContext(Dispatchers.IO) { getLocalIpAddresses(context) }
                }

                CyberCard(borderColor = AccentCyan) {
                    Text("Adapter & IP Publik", color = AccentCyan, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Interface lokal:", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    localIps.forEach { line ->
                        Text("• $line", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Lookup publik:", color = TextSecondary, fontSize = 12.sp)
                    Text(publicInfo, color = AccentEmerald, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberButton(
                        text = if (isLoading) "MENGHUBUNGI LAYANAN IP..." else "LOOKUP IP PUBLIK",
                        enabled = !isLoading,
                        color = AccentCyan,
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                try {
                                    localIps = withContext(Dispatchers.IO) { getLocalIpAddresses(context) }
                                    publicInfo = withContext(Dispatchers.IO) { lookupPublicIp() }
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    )
                }
            }
        }

        if (activeSubTool == 1) {
            item {
                var targetHost by remember { mutableStateOf("google.com") }
                var dnsResults by remember { mutableStateOf(listOf<String>()) }
                var isResolving by remember { mutableStateOf(false) }

                CyberCard(borderColor = AccentEmerald) {
                    Text("DNS Resolver", color = AccentEmerald, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    CyberTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = "Hostname",
                        placeholder = "contoh: github.com"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberButton(
                        text = if (isResolving) "ME-RESOLVE..." else "RESOLVE A / AAAA",
                        enabled = !isResolving,
                        color = AccentEmerald,
                        onClick = {
                            coroutineScope.launch {
                                isResolving = true
                                val host = normalizeNetworkHost(targetHost)
                                if (host == null) {
                                    dnsResults = listOf("Hostname tidak valid.")
                                    isResolving = false
                                    return@launch
                                }
                                try {
                                    dnsResults = withContext(Dispatchers.IO) {
                                        InetAddress.getAllByName(host).map { address ->
                                            val type = if (address.address.size == 4) "A" else "AAAA"
                                            "$type  ${address.hostAddress}"
                                        }
                                    }
                                } catch (e: Exception) {
                                    dnsResults = listOf("Gagal: ${e.message ?: "host tidak ditemukan"}")
                                } finally {
                                    isResolving = false
                                }
                            }
                        }
                    )
                    if (dnsResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CyberCodeBox(code = dnsResults.joinToString("\n"), maxHeight = 260.dp)
                    }
                }
            }
        }

        if (activeSubTool == 2) {
            item {
                var pingHost by remember { mutableStateOf("1.1.1.1") }
                var pingOutput by remember { mutableStateOf(listOf<String>()) }
                var isPinging by remember { mutableStateOf(false) }

                CyberCard(borderColor = AccentRose) {
                    Text("TCP Latency (bukan ICMP)", color = AccentRose, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Android tidak mengizinkan ping ICMP tanpa root. Pengujian ini membuka koneksi TCP nyata ke port 443/80 dan mengukur latensi.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    CyberTextField(
                        value = pingHost,
                        onValueChange = { pingHost = it },
                        label = "Host / IP",
                        placeholder = "1.1.1.1"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberButton(
                        text = if (isPinging) "MENGUJI KONEKSI..." else "UJI 4 KONEKSI TCP",
                        enabled = !isPinging,
                        color = AccentRose,
                        onClick = {
                            coroutineScope.launch {
                                isPinging = true
                                pingOutput = emptyList()
                                val host = normalizeNetworkHost(pingHost)
                                if (host == null) {
                                    pingOutput = listOf("Host tidak valid.")
                                    isPinging = false
                                    return@launch
                                }
                                val rtts = mutableListOf<Long>()
                                val sent = 4
                                try {
                                    for (i in 1..sent) {
                                        val line = withContext(Dispatchers.IO) {
                                            fun attempt(port: Int): Long? {
                                                val start = System.nanoTime()
                                                return runCatching {
                                                    Socket().use { socket ->
                                                        socket.connect(InetSocketAddress(host, port), 2500)
                                                    }
                                                    (System.nanoTime() - start) / 1_000_000
                                                }.getOrNull()
                                            }

                                            val rtt = attempt(443) ?: attempt(80)
                                            if (rtt != null) {
                                                rtts.add(rtt)
                                                "seq=$i  ${rtt}ms  TCP connected"
                                            } else {
                                                "seq=$i  timeout / refused"
                                            }
                                        }
                                        pingOutput = pingOutput + line
                                        delay(250)
                                    }
                                } finally {
                                    isPinging = false
                                }
                                val received = rtts.size
                                val loss = if (sent == 0) 100 else ((sent - received) * 100) / sent
                                val stats = if (rtts.isEmpty()) {
                                    "\n--- $sent dikirim, $received berhasil, loss $loss% ---"
                                } else {
                                    "\n--- $sent dikirim, $received berhasil, loss $loss% | min ${rtts.min()}ms, avg ${rtts.average().toLong()}ms, max ${rtts.max()}ms ---"
                                }
                                pingOutput = pingOutput + stats
                            }
                        }
                    )
                    if (pingOutput.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CyberCodeBox(code = pingOutput.joinToString("\n"), maxHeight = 280.dp)
                    }
                }
            }
        }

        if (activeSubTool == 3) {
            item {
                var sslHost by remember { mutableStateOf("google.com") }
                var sslInfo by remember { mutableStateOf("") }
                var isChecking by remember { mutableStateOf(false) }

                CyberCard(borderColor = AccentAmber) {
                    Text("SSL / TLS Inspector", color = AccentAmber, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    CyberTextField(
                        value = sslHost,
                        onValueChange = { sslHost = it },
                        label = "Domain (host[:port])",
                        placeholder = "google.com"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberButton(
                        text = if (isChecking) "MEMERIKSA SERTIFIKAT..." else "PERIKSA SERTIFIKAT",
                        enabled = !isChecking,
                        color = AccentAmber,
                        onClick = {
                            coroutineScope.launch {
                                isChecking = true
                                sslInfo = withContext(Dispatchers.IO) { inspectSsl(sslHost) }
                                isChecking = false
                            }
                        }
                    )
                    if (sslInfo.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CyberCodeBox(code = sslInfo, maxHeight = 360.dp)
                    }
                }
            }
        }

        if (activeSubTool == 4) {
            item {
                var whoisDomain by remember { mutableStateOf("example.com") }
                var whoisResult by remember { mutableStateOf("") }
                var isQuerying by remember { mutableStateOf(false) }

                CyberCard(borderColor = AccentPurple) {
                    Text("WHOIS Client (TCP 43)", color = AccentPurple, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    CyberTextField(
                        value = whoisDomain,
                        onValueChange = { whoisDomain = it },
                        label = "Domain",
                        placeholder = "example.com"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CyberButton(
                        text = if (isQuerying) "MENGIRIM QUERY WHOIS..." else "QUERY WHOIS",
                        enabled = !isQuerying,
                        color = AccentPurple,
                        onClick = {
                            coroutineScope.launch {
                                isQuerying = true
                                whoisResult = withContext(Dispatchers.IO) {
                                    runCatching { WhoisClient.query(whoisDomain) }
                                        .getOrElse { "WHOIS gagal: ${it.message}" }
                                }
                                isQuerying = false
                            }
                        }
                    )
                    if (whoisResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CyberCodeBox(code = whoisResult, maxHeight = 420.dp)
                    }
                }
            }
        }
    }
}

private fun getLocalIpAddresses(context: android.content.Context): List<String> {
    val results = mutableListOf<String>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            if (!iface.isUp || iface.isLoopback) continue
            val addresses = iface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    results.add("${iface.name}: ${address.hostAddress}")
                }
            }
        }
    } catch (_: Exception) {
    }
    if (results.isEmpty()) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm?.getNetworkCapabilities(it) }
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        results.add(if (connected) "Tidak ada alamat lokal yang dapat dibaca" else "Tidak ada koneksi jaringan")
    }
    return results
}

private fun lookupPublicIp(): String {
    fun fetch(url: String): JSONObject? = runCatching {
        val connection = URL(url).openConnection().apply {
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("User-Agent", "IO-Tools/1.4")
            setRequestProperty("Accept", "application/json")
        }
        (connection.getInputStream().bufferedReader().use { it.readText() })
            .let { JSONObject(it) }
    }.getOrNull()

    fetch("https://ipwho.is/")?.let { json ->
        if (json.optBoolean("success", false) || json.has("ip")) {
            val ip = json.optString("ip")
            val city = json.optString("city")
            val region = json.optString("region")
            val country = json.optString("country")
            val isp = json.optJSONObject("connection")?.optString("isp").orEmpty()
            val asn = json.optJSONObject("connection")?.optString("asn").orEmpty()
            return buildString {
                append("IP: $ip")
                if (city.isNotBlank()) append("\nLokasi: $city, $region, $country")
                if (isp.isNotBlank()) append("\nISP: $isp")
                if (asn.isNotBlank()) append("\nASN: AS$asn")
            }
        }
    }

    fetch("https://ipapi.co/json/")?.let { json ->
        if (!json.has("error")) {
            return buildString {
                append("IP: ${json.optString("ip")}")
                val city = json.optString("city")
                val region = json.optString("region")
                val country = json.optString("country_name")
                if (city.isNotBlank()) append("\nLokasi: $city, $region, $country")
                val org = json.optString("org")
                if (org.isNotBlank()) append("\nISP/Org: $org")
            }
        }
    }

    return "Lookup gagal: layanan IP publik tidak dapat dijangkau. Periksa koneksi internet."
}

private fun inspectSsl(input: String): String {
    val target = parseSslTarget(input)
        ?: return "Masukkan host dan port TLS yang valid (port 1–65535)."
    val urlHost = if (target.host.contains(':')) "[${target.host}]" else target.host
    val url = URL("https://$urlHost:${target.port}")

    return try {
        val connection = url.openConnection() as HttpsURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.requestMethod = "HEAD"
        try {
            connection.connect()
            val cert = connection.serverCertificates.firstOrNull() as? X509Certificate
                ?: return "TLS terhubung tetapi sertifikat tidak tersedia."
            val remaining = (cert.notAfter.time - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)
            buildString {
                appendLine("Host: ${target.host}:${target.port}")
                appendLine("Cipher: ${connection.cipherSuite}")
                appendLine("Subject: ${cert.subjectX500Principal.name}")
                appendLine("Issuer: ${cert.issuerX500Principal.name}")
                appendLine("Valid from: ${cert.notBefore}")
                appendLine("Valid until: ${cert.notAfter} (${remaining.coerceAtLeast(0)} hari lagi)")
                appendLine("Signature: ${cert.sigAlgName}")
                appendLine("Serial: ${cert.serialNumber}")
                appendLine("Status: VALID & TRUSTED")
            }.trimEnd()
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        "Pemeriksaan TLS gagal: ${e.message ?: e.javaClass.simpleName}\nSertifikat yang tidak tervalidasi tidak ditampilkan sebagai tepercaya."
    }
}

private object WhoisClient {
    fun query(input: String): String {
        val domain = normalizeWhoisDomain(input)
            ?: throw IllegalArgumentException("Domain WHOIS tidak valid.")

        val iana = runCatching { rawQuery("whois.iana.org", domain, 8000) }.getOrElse { "" }
        val referral = Regex("(?im)^refer:\\s*(\\S+)").find(iana)?.groupValues?.get(1)
        val server = referral?.let { normalizeWhoisDomain(it) }
            ?: fallbackServer(domain)

        val result = if (server != null && server != "whois.iana.org") {
            runCatching { rawQuery(server, domain, 12000) }
                .getOrElse { "WHOIS server $server tidak dapat dijangkau: ${it.message}\n\n$iana" }
        } else {
            iana
        }

        if (result.isBlank()) {
            throw IllegalStateException("Server WHOIS tidak mengembalikan data.")
        }
        return result.trim()
    }



    private fun fallbackServer(domain: String): String? = when {
        domain.endsWith(".com") || domain.endsWith(".net") -> "whois.verisign-grs.com"
        domain.endsWith(".org") -> "whois.pir.org"
        domain.endsWith(".id") -> "whois.pandi.or.id"
        domain.endsWith(".io") -> "whois.nic.io"
        domain.endsWith(".dev") -> "whois.nic.google"
        domain.endsWith(".xyz") -> "whois.nic.xyz"
        else -> null
    }

    private fun rawQuery(server: String, domain: String, timeoutMs: Int): String {
        val safeServer = normalizeWhoisDomain(server)
            ?: throw IllegalArgumentException("WHOIS referral server tidak valid.")
        val safeDomain = normalizeWhoisDomain(domain)
            ?: throw IllegalArgumentException("Domain WHOIS tidak valid.")
        Socket().use { socket ->
            socket.connect(InetSocketAddress(safeServer, 43), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.getOutputStream().write("$domain\r\n".toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val builder = StringBuilder()
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                builder.append(line).append('\n')
                if (builder.length > 120_000) break
            }
            return builder.toString()
        }
    }
}
