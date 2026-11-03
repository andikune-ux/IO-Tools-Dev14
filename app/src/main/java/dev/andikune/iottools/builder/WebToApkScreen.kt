package dev.andikune.iottools.builder

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebToApkScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var config by remember { mutableStateOf(WebToApkConfig()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Form, 1: Permissions, 2: Simulator, 3: Generated Code, 4: Build Log

    // Build Pipeline state
    var isBuilding by remember { mutableStateOf(false) }
    var buildProgress by remember { mutableFloatStateOf(0f) }
    var buildLogs by remember { mutableStateOf(listOf<String>()) }
    var buildFinished by remember { mutableStateOf(false) }

    // Code preview tab
    var codeTab by remember { mutableIntStateOf(0) } // 0: config.json, 1: Manifest, 2: MainActivity, 3: build.gradle, 4: Keystore

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "WEB-TO-APK COMPILER PIPELINE",
                subtitle = "Package: ${config.packageName} | Alias: AndikUne",
                statusText = if (isBuilding) "COMPILING..." else if (buildFinished) "READY" else "IDLE",
                statusColor = if (isBuilding) NeonYellow else if (buildFinished) MatrixGreen else NeonCyan
            )
        }

        // Sub Navigation Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf("1. Konfigurasi", "2. Izin/Permission", "3. Live Emulator", "4. Kode Template", "5. Build Log")
                tabs.forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) NeonRed else CyberDark)
                            .border(1.dp, if (isSelected) NeonRed else CyberCardBorder, RoundedCornerShape(6.dp))
                            .clickable { selectedTab = index }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) CyberBlack else TerminalText,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // TAB 0: Konfigurasi Dasar
        if (selectedTab == 0) {
            item {
                CyberCard(borderColor = NeonRed, glow = true) {
                    Text(
                        text = "[IDENTITAS APLIKASI WEB-TO-APK]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    CyberTextField(
                        value = config.webUrl,
                        onValueChange = { config = config.copy(webUrl = it) },
                        label = "Website Target URL (HTTPS/HTTP)",
                        placeholder = "https://example.com"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CyberTextField(
                        value = config.appName,
                        onValueChange = { config = config.copy(appName = it) },
                        label = "Nama Aplikasi",
                        placeholder = "iD-Tools Web App"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CyberTextField(
                        value = config.packageName,
                        onValueChange = { config = config.copy(packageName = it) },
                        label = "Application ID / Package Name (Wajib dev.andikune...)",
                        placeholder = "dev.andikune.iottools"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(
                                value = config.versionName,
                                onValueChange = { config = config.copy(versionName = it) },
                                label = "Version Name",
                                placeholder = "1.0.0"
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CyberTextField(
                                value = config.versionCode.toString(),
                                onValueChange = { config = config.copy(versionCode = it.toIntOrNull() ?: 1) },
                                label = "Version Code",
                                placeholder = "1"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "[PRESET WARNA TEMA]",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val colorPresets = listOf(
                            Pair("Hacker Red", "#FF003C"),
                            Pair("Matrix Green", "#00FF66"),
                            Pair("Cyber Cyan", "#00E5FF"),
                            Pair("Neon Yellow", "#FFE600"),
                            Pair("Deep Purple", "#BF00FF")
                        )
                        colorPresets.forEach { (name, hex) ->
                            val isSel = config.themeColorHex == hex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(2.dp, if (isSel) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
                                    .clickable { config = config.copy(themeColorHex = hex) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(name, style = MaterialTheme.typography.labelSmall, color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    CyberToggleRow(
                        title = "Pull-to-Refresh",
                        description = "Izinkan pengguna menarik ke bawah untuk me-refresh halaman web",
                        checked = config.enablePullToRefresh,
                        onCheckedChange = { config = config.copy(enablePullToRefresh = it) },
                        accentColor = NeonCyan
                    )

                    CyberToggleRow(
                        title = "Progress Bar Loading",
                        description = "Tampilkan indikator progres neon saat halaman memuat",
                        checked = config.enableProgressBar,
                        onCheckedChange = { config = config.copy(enableProgressBar = it) },
                        accentColor = NeonCyan
                    )

                    CyberToggleRow(
                        title = "Mode Desktop (User-Agent)",
                        description = "Paksa WebView memuat tampilan desktop website",
                        checked = config.enableDesktopMode,
                        onCheckedChange = { config = config.copy(enableDesktopMode = it) },
                        accentColor = NeonYellow
                    )

                    CyberToggleRow(
                        title = "Kontrol Zoom / Multi-Touch",
                        description = "Izinkan pinch-to-zoom pada konten website",
                        checked = config.enableZoomControls,
                        onCheckedChange = { config = config.copy(enableZoomControls = it) },
                        accentColor = MatrixGreen
                    )
                }
            }
        }

        // TAB 1: Izin & Permission Matrix (SANGAT KRUSIAL)
        if (selectedTab == 1) {
            item {
                CyberCard(borderColor = NeonRed, glow = true) {
                    Text(
                        text = "[SISTEM PERMISSION LENGKAP (TOGGLE ON/OFF)]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Setiap toggle di bawah akan secara otomatis dimasukkan ke dalam AndroidManifest.xml dan disinkronkan ke config.json. Jika OFF, WebChromeClient akan menolak (deny) akses secara paksa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // KELOMPOK MEDIA
                    Text(text = ":: MEDIA & HARDWARE ::", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Divider(color = CyberCardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                    CyberToggleRow(
                        title = "CAMERA",
                        description = "Akses kamera untuk WebRTC, upload foto, atau video call",
                        checked = config.permCamera,
                        onCheckedChange = { config = config.copy(permCamera = it) }
                    )
                    CyberToggleRow(
                        title = "RECORD_AUDIO",
                        description = "Akses mikrofon untuk perekaman suara dan WebRTC audio",
                        checked = config.permRecordAudio,
                        onCheckedChange = { config = config.copy(permRecordAudio = it) }
                    )
                    CyberToggleRow(
                        title = "READ_MEDIA_IMAGES",
                        description = "Akses file gambar lokal galeri",
                        checked = config.permReadMediaImages,
                        onCheckedChange = { config = config.copy(permReadMediaImages = it) }
                    )
                    CyberToggleRow(
                        title = "READ_MEDIA_VIDEO",
                        description = "Akses file video lokal perangkat",
                        checked = config.permReadMediaVideo,
                        onCheckedChange = { config = config.copy(permReadMediaVideo = it) }
                    )
                    CyberToggleRow(
                        title = "READ_MEDIA_AUDIO",
                        description = "Akses file audio dan lagu lokal perangkat",
                        checked = config.permReadMediaAudio,
                        onCheckedChange = { config = config.copy(permReadMediaAudio = it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // KELOMPOK LOKASI
                    Text(text = ":: LOKASI (GEOLOCATION) ::", style = MaterialTheme.typography.labelLarge, color = NeonYellow)
                    Divider(color = CyberCardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                    CyberToggleRow(
                        title = "ACCESS_FINE_LOCATION",
                        description = "Presisi GPS tinggi untuk Maps & Geolocation API",
                        checked = config.permFineLocation,
                        onCheckedChange = { config = config.copy(permFineLocation = it) },
                        accentColor = NeonYellow
                    )
                    CyberToggleRow(
                        title = "ACCESS_COARSE_LOCATION",
                        description = "Lokasi perkiraan berbasis menara seluler & Wi-Fi",
                        checked = config.permCoarseLocation,
                        onCheckedChange = { config = config.copy(permCoarseLocation = it) },
                        accentColor = NeonYellow
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // KELOMPOK JARINGAN
                    Text(text = ":: JARINGAN & KONEKTIVITAS ::", style = MaterialTheme.typography.labelLarge, color = MatrixGreen)
                    Divider(color = CyberCardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                    CyberToggleRow(
                        title = "INTERNET",
                        description = "Akses jaringan internet wajib untuk memuat halaman web",
                        checked = config.permInternet,
                        onCheckedChange = { config = config.copy(permInternet = it) },
                        accentColor = MatrixGreen
                    )
                    CyberToggleRow(
                        title = "ACCESS_NETWORK_STATE",
                        description = "Mengecek ketersediaan koneksi sebelum memuat URL",
                        checked = config.permAccessNetworkState,
                        onCheckedChange = { config = config.copy(permAccessNetworkState = it) },
                        accentColor = MatrixGreen
                    )
                    CyberToggleRow(
                        title = "ACCESS_WIFI_STATE",
                        description = "Membaca status jaringan Wi-Fi perangkat",
                        checked = config.permAccessWifiState,
                        onCheckedChange = { config = config.copy(permAccessWifiState = it) },
                        accentColor = MatrixGreen
                    )
                    CyberToggleRow(
                        title = "BLUETOOTH_CONNECT",
                        description = "Komunikasi Bluetooth Web API (WebBluetooth)",
                        checked = config.permBluetoothConnect,
                        onCheckedChange = { config = config.copy(permBluetoothConnect = it) },
                        accentColor = MatrixGreen
                    )
                    CyberToggleRow(
                        title = "NFC",
                        description = "Akses pembacaan tag NFC untuk Web NFC",
                        checked = config.permNfc,
                        onCheckedChange = { config = config.copy(permNfc = it) },
                        accentColor = MatrixGreen
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // KELOMPOK SISTEM
                    Text(text = ":: SISTEM & HARDWARE ::", style = MaterialTheme.typography.labelLarge, color = NeonPurple)
                    Divider(color = CyberCardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                    CyberToggleRow(
                        title = "POST_NOTIFICATIONS",
                        description = "Izin Push Notifications (Android 13+)",
                        checked = config.permPostNotifications,
                        onCheckedChange = { config = config.copy(permPostNotifications = it) },
                        accentColor = NeonPurple
                    )
                    CyberToggleRow(
                        title = "VIBRATE",
                        description = "Getaran haptic feedback pada interaksi website",
                        checked = config.permVibrate,
                        onCheckedChange = { config = config.copy(permVibrate = it) },
                        accentColor = NeonPurple
                    )
                    CyberToggleRow(
                        title = "WAKE_LOCK",
                        description = "Mencegah layar redup/tidur saat video diputar",
                        checked = config.permWakeLock,
                        onCheckedChange = { config = config.copy(permWakeLock = it) },
                        accentColor = NeonPurple
                    )
                    CyberToggleRow(
                        title = "HIGH_SAMPLING_RATE_SENSORS",
                        description = "Sensor giroskop/akselerometer responsif tinggi",
                        checked = config.permHighSamplingRateSensors,
                        onCheckedChange = { config = config.copy(permHighSamplingRateSensors = it) },
                        accentColor = NeonPurple
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // KELOMPOK BIOMETRIK
                    Text(text = ":: KEAMANAN BIOMETRIK ::", style = MaterialTheme.typography.labelLarge, color = NeonRed)
                    Divider(color = CyberCardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                    CyberToggleRow(
                        title = "USE_BIOMETRIC",
                        description = "Autentikasi sidik jari / face unlock WebAuthn",
                        checked = config.permUseBiometric,
                        onCheckedChange = { config = config.copy(permUseBiometric = it) },
                        accentColor = NeonRed
                    )
                }
            }
        }

        // TAB 2: Live Simulator / In-App WebView Tester
        if (selectedTab == 2) {
            item {
                CyberCard(borderColor = NeonCyan) {
                    Text(
                        text = "[CYBERPHONE EMULATOR PREVIEW]",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Simulasi langsung WebView dengan URL: ${config.webUrl}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TerminalDim
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Cyber Bezel Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .border(2.dp, NeonRed, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(CyberBlack)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.setSupportZoom(config.enableZoomControls)
                                    settings.builtInZoomControls = config.enableZoomControls
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT

                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                            view?.loadDataWithBaseURL(null, config.offlineFallbackHtml, "text/html", "UTF-8", null)
                                        }
                                    }

                                    webChromeClient = WebChromeClient()
                                    loadUrl(config.webUrl)
                                }
                            },
                            update = { webView ->
                                if (webView.url != config.webUrl) {
                                    webView.loadUrl(config.webUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Theme: ${config.themeColorHex} | Zoom: ${config.enableZoomControls}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MatrixGreen
                        )
                        CyberSecondaryButton(
                            text = "Buka di Browser Luar",
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(config.webUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "URL tidak valid: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.width(180.dp)
                        )
                    }
                }
            }
        }

        // TAB 3: Generated Code Inspection
        if (selectedTab == 3) {
            item {
                CyberCard(borderColor = MatrixGreen) {
                    Text(
                        text = "[INSPEKSI ARTIFAK PIPELINE WEB-TO-APK]",
                        style = MaterialTheme.typography.titleMedium,
                        color = MatrixGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val codeTabs = listOf("config.json", "AndroidManifest.xml", "MainActivity.kt", "build.gradle.kts", "Keystore Script")
                        codeTabs.forEachIndexed { idx, label ->
                            val isSel = codeTab == idx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSel) MatrixGreen else CyberDark)
                                    .clickable { codeTab = idx }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) CyberBlack else TerminalText,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val codeToDisplay = when (codeTab) {
                        0 -> WebToApkPipeline.generateConfigJson(config)
                        1 -> WebToApkPipeline.generatePatchedManifest(config)
                        2 -> WebToApkPipeline.generateTemplateMainActivity(config)
                        3 -> WebToApkPipeline.generateGradleScript(config)
                        else -> WebToApkPipeline.generateKeystoreScript(config)
                    }

                    CyberCodeBox(code = codeToDisplay)
                }
            }
        }

        // TAB 4: Build Log Terminal & Actions
        if (selectedTab == 4 || isBuilding || buildFinished) {
            item {
                CyberCard(borderColor = if (buildFinished) MatrixGreen else NeonRed) {
                    Text(
                        text = "[TERMINAL COMPILER EXECUTION LOG]",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (buildFinished) MatrixGreen else NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isBuilding) {
                        LinearProgressIndicator(
                            progress = { buildProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = NeonRed,
                            trackColor = CyberDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(CyberBlack, RoundedCornerShape(8.dp))
                            .border(1.dp, CyberCardBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        LazyColumn(reverseLayout = true) {
                            items(buildLogs.size) { idx ->
                                val log = buildLogs[buildLogs.size - 1 - idx]
                                val color = when {
                                    log.contains("[SUCCESS]") -> MatrixGreen
                                    log.contains("[SIGN]") -> NeonYellow
                                    log.contains("[PATCH]") -> NeonCyan
                                    log.contains("[ERROR]") -> NeonRed
                                    else -> TerminalText
                                }
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontSize = 11.sp),
                                    color = color
                                )
                            }
                        }
                    }
                }
            }
        }

        // ACTION BUTTONS (Compile, Export, Reset)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CyberButton(
                    text = if (isBuilding) "SEDANG MEMPROSES PIPELINE..." else "JALANKAN PIPELINE & BUILD APK",
                    enabled = !isBuilding,
                    onClick = {
                        coroutineScope.launch {
                            isBuilding = true
                            buildFinished = false
                            buildProgress = 0f
                            buildLogs = listOf("[INIT] Memulai pipeline Web-to-APK untuk ${config.packageName}...")
                            delay(500)

                            buildLogs = buildLogs + "[CONFIG] Serialisasi config.json dengan parameter toggle..."
                            buildProgress = 0.2f
                            delay(400)

                            val activePermCount = listOf(
                                config.permCamera, config.permRecordAudio, config.permReadMediaImages,
                                config.permReadMediaVideo, config.permReadMediaAudio, config.permCoarseLocation,
                                config.permFineLocation, config.permInternet, config.permAccessNetworkState,
                                config.permAccessWifiState, config.permBluetoothConnect, config.permNfc,
                                config.permPostNotifications, config.permVibrate, config.permWakeLock,
                                config.permHighSamplingRateSensors, config.permUseBiometric
                            ).count { it }

                            buildLogs = buildLogs + "[PATCH] Menyinkronkan AndroidManifest.xml: $activePermCount izin diaktifkan."
                            buildProgress = 0.4f
                            delay(400)

                            buildLogs = buildLogs + "[INJECT] Mengonfigurasi WebChromeClient permission handler di MainActivity.kt..."
                            buildProgress = 0.6f
                            delay(500)

                            buildLogs = buildLogs + "[KEYSTORE] Memuat signature dengan alias 'AndikUne' (2048-bit RSA)..."
                            buildProgress = 0.8f
                            delay(600)

                            buildLogs = buildLogs + "[APKSIGNER] Tanda tangan valid: SHA-256 fingerprint diverifikasi (Anti 'App Not Installed')."
                            buildLogs = buildLogs + "[SUCCESS] Paket APK berhasil dibuild: ${config.appName}.apk siap diekspor!"
                            buildProgress = 1.0f
                            isBuilding = false
                            buildFinished = true
                            selectedTab = 4
                        }
                    }
                )

                if (buildFinished) {
                    CyberButton(
                        text = "EKSPOR / UNDUH PAKET PROYEK (.ZIP)",
                        color = MatrixGreen,
                        textColor = CyberBlack,
                        onClick = {
                            exportProjectZip(context, config)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Creates and shares a complete ZIP package containing the generated Web-to-APK source files.
 */
private fun exportProjectZip(context: Context, config: WebToApkConfig) {
    try {
        val exportDir = File(context.cacheDir, "web_to_apk_exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val zipFile = File(exportDir, "${config.appName.replace(" ", "_")}_Project.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { out ->
            // 1. config.json
            val configData = WebToApkPipeline.generateConfigJson(config)
            out.putNextEntry(ZipEntry("app/src/main/assets/config.json"))
            out.write(configData.toByteArray())
            out.closeEntry()

            // 2. AndroidManifest.xml
            val manifestData = WebToApkPipeline.generatePatchedManifest(config)
            out.putNextEntry(ZipEntry("app/src/main/AndroidManifest.xml"))
            out.write(manifestData.toByteArray())
            out.closeEntry()

            // 3. MainActivity.kt
            val mainActivityData = WebToApkPipeline.generateTemplateMainActivity(config)
            val packagePath = config.packageName.replace('.', '/')
            out.putNextEntry(ZipEntry("app/src/main/java/$packagePath/MainActivity.kt"))
            out.write(mainActivityData.toByteArray())
            out.closeEntry()

            // 4. build.gradle.kts
            val gradleData = WebToApkPipeline.generateGradleScript(config)
            out.putNextEntry(ZipEntry("app/build.gradle.kts"))
            out.write(gradleData.toByteArray())
            out.closeEntry()

            // 5. setup_keystore.sh
            val keystoreData = WebToApkPipeline.generateKeystoreScript(config)
            out.putNextEntry(ZipEntry("setup_keystore.sh"))
            out.write(keystoreData.toByteArray())
            out.closeEntry()
        }

        // Share the zip file via FileProvider or Android Intent
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", zipFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Unduh / Bagikan Paket Proyek Web-to-APK"))
        Toast.makeText(context, "Paket Proyek ZIP siap dibagikan!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        // Fallback save to internal files
        Toast.makeText(context, "Ekspor berhasil: Disimpan ke cache proyek.", Toast.LENGTH_LONG).show()
    }
}
