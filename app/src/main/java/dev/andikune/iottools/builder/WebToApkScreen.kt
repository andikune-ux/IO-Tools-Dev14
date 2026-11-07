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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import dev.andikune.iottools.R
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebToApkScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var config by remember { mutableStateOf(WebToApkConfig(themeColorHex = "#6366F1", navBarColorHex = "#090D16")) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Form, 1: Permissions, 2: Simulator, 3: Generated Code, 4: Build Log

    // Build Pipeline state
    var isBuilding by remember { mutableStateOf(false) }
    var buildProgress by remember { mutableFloatStateOf(0f) }
    var buildLogs by remember { mutableStateOf(listOf<String>()) }
    var buildFinished by remember { mutableStateOf(false) }
    var generatedBundle by remember { mutableStateOf<ApkPackagingEngine.ProjectBundle?>(null) }

    // Code preview tab
    var codeTab by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Modern Hero Banner with transformation artwork
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hero_banner),
                    contentDescription = "Web-to-APK Studio Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Dark gradient overlay to keep text ultra legible
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, SlateCanvas.copy(alpha = 0.85f))
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PrimaryIndigo)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("BUILDER PRO", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("dev.andikune.iottools", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Web App Project Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Modern Segmented Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    "Konfigurasi" to Icons.Default.Tune,
                    "Izin Granular" to Icons.Default.Security,
                    "Live Preview" to Icons.Default.PhoneAndroid,
                    "Source Code" to Icons.Default.Code,
                    "Build Project" to Icons.Default.PlayArrow
                )
                tabs.forEachIndexed { index, (label, icon) ->
                    val isSelected = selectedTab == index
                    Surface(
                        onClick = { selectedTab = index },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) PrimaryIndigo else SlateElevated,
                        border = BorderStroke(1.dp, if (isSelected) PrimaryIndigoLight else SlateBorder),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) Color.White else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // TAB 0: Konfigurasi Dasar
        if (selectedTab == 0) {
            item {
                CyberCard(borderColor = SlateBorder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AppSettingsAlt, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Identitas Aplikasi & Pengaturan Web",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    CyberTextField(
                        value = config.webUrl,
                        onValueChange = { config = config.copy(webUrl = it) },
                        label = "Website Target URL (HTTPS / HTTP)",
                        placeholder = "https://example.com"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    CyberTextField(
                        value = config.appName,
                        onValueChange = { config = config.copy(appName = it) },
                        label = "Nama Aplikasi",
                        placeholder = "iD-Tools Web App"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    CyberTextField(
                        value = config.packageName,
                        onValueChange = { config = config.copy(packageName = it) },
                        label = "Application ID / Package Name",
                        placeholder = "dev.andikune.iottools"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Aksen Warna Tema Aplikasi:",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val colorPresets = listOf(
                            Pair("Indigo Pro", "#6366F1"),
                            Pair("Emerald Tech", "#10B981"),
                            Pair("Sky Cyan", "#38BDF8"),
                            Pair("Royal Violet", "#8B5CF6"),
                            Pair("Coral Rose", "#F43F5E"),
                            Pair("Amber Gold", "#F59E0B")
                        )
                        colorPresets.forEach { (name, hex) ->
                            val isSel = config.themeColorHex == hex
                            Surface(
                                onClick = { config = config.copy(themeColorHex = hex) },
                                shape = RoundedCornerShape(10.dp),
                                color = SlateSurface,
                                border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) Color(android.graphics.Color.parseColor(hex)) else SlateBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text("Opsi Tampilan & Interaksi:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))

                    CyberToggleRow(
                        title = "Pull-to-Refresh",
                        description = "Tarik ke bawah untuk memuat ulang halaman website",
                        checked = config.enablePullToRefresh,
                        onCheckedChange = { config = config.copy(enablePullToRefresh = it) },
                        accentColor = PrimaryIndigo
                    )

                    CyberToggleRow(
                        title = "Indikator Progress Bar",
                        description = "Garis progress bar modern saat memuat halaman web",
                        checked = config.enableProgressBar,
                        onCheckedChange = { config = config.copy(enableProgressBar = it) },
                        accentColor = PrimaryIndigo
                    )

                    CyberToggleRow(
                        title = "Mode Desktop (User-Agent)",
                        description = "Paksa WebView memuat antarmuka desktop",
                        checked = config.enableDesktopMode,
                        onCheckedChange = { config = config.copy(enableDesktopMode = it) },
                        accentColor = AccentAmber
                    )

                    CyberToggleRow(
                        title = "Kontrol Zoom Multi-Touch",
                        description = "Dukungan pinch-to-zoom pada konten website",
                        checked = config.enableZoomControls,
                        onCheckedChange = { config = config.copy(enableZoomControls = it) },
                        accentColor = AccentEmerald
                    )

                    CyberToggleRow(
                        title = "Hapus Cache Saat Keluar",
                        description = "Bersihkan cache dan data WebStorage ketika aplikasi ditutup",
                        checked = config.clearCacheOnExit,
                        onCheckedChange = { config = config.copy(clearCacheOnExit = it) },
                        accentColor = AccentRose
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    CyberTextField(
                        value = config.userAgentOverride,
                        onValueChange = { config = config.copy(userAgentOverride = it) },
                        label = "Custom User-Agent (opsional)",
                        placeholder = "Kosongkan untuk User-Agent default perangkat"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Jika diisi, nilai ini menggantikan Mode Desktop. Berguna untuk situs yang memblokir WebView.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }

        // TAB 1: Izin Granular
        if (selectedTab == 1) {
            item {
                CyberCard(borderColor = SlateBorder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Matriks Izin Granular (Toggle ON/OFF)",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sinkron dengan AndroidManifest.xml & config.json. Jika OFF, akses ditolak secara paksa.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Media
                    Text(text = "Media & Perangkat Keras", style = MaterialTheme.typography.titleMedium, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                    Divider(color = SlateBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                    CyberToggleRow("CAMERA", "Akses kamera untuk upload foto, scan, dan WebRTC video", config.permCamera, { config = config.copy(permCamera = it) })
                    CyberToggleRow("RECORD_AUDIO", "Akses mikrofon untuk rekaman suara dan WebRTC audio", config.permRecordAudio, { config = config.copy(permRecordAudio = it) })
                    CyberToggleRow("READ_MEDIA_IMAGES", "Akses file gambar dan galeri foto", config.permReadMediaImages, { config = config.copy(permReadMediaImages = it) })
                    CyberToggleRow("READ_MEDIA_VIDEO", "Akses file video lokal", config.permReadMediaVideo, { config = config.copy(permReadMediaVideo = it) })
                    CyberToggleRow("READ_MEDIA_AUDIO", "Akses file audio lokal", config.permReadMediaAudio, { config = config.copy(permReadMediaAudio = it) })

                    Spacer(modifier = Modifier.height(18.dp))

                    // Lokasi
                    Text(text = "Lokasi Geolocation", style = MaterialTheme.typography.titleMedium, color = AccentAmber, fontWeight = FontWeight.SemiBold)
                    Divider(color = SlateBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                    CyberToggleRow("ACCESS_FINE_LOCATION", "GPS presisi tinggi untuk peta dan navigasi", config.permFineLocation, { config = config.copy(permFineLocation = it) }, AccentAmber)
                    CyberToggleRow("ACCESS_COARSE_LOCATION", "Lokasi perkiraan berbasis Wi-Fi / seluler", config.permCoarseLocation, { config = config.copy(permCoarseLocation = it) }, AccentAmber)

                    Spacer(modifier = Modifier.height(18.dp))

                    // Jaringan
                    Text(text = "Jaringan & Konektivitas", style = MaterialTheme.typography.titleMedium, color = AccentEmerald, fontWeight = FontWeight.SemiBold)
                    Divider(color = SlateBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                    CyberToggleRow("INTERNET", "Koneksi internet wajib untuk memuat halaman web", config.permInternet, { config = config.copy(permInternet = it) }, AccentEmerald)
                    CyberToggleRow("ACCESS_NETWORK_STATE", "Deteksi online/offline sebelum memuat URL", config.permAccessNetworkState, { config = config.copy(permAccessNetworkState = it) }, AccentEmerald)
                    CyberToggleRow("ACCESS_WIFI_STATE", "Status jaringan Wi-Fi perangkat", config.permAccessWifiState, { config = config.copy(permAccessWifiState = it) }, AccentEmerald)
                    CyberToggleRow("BLUETOOTH_CONNECT", "Komunikasi WebBluetooth API", config.permBluetoothConnect, { config = config.copy(permBluetoothConnect = it) }, AccentEmerald)
                    CyberToggleRow("NFC", "Akses Web NFC untuk pembacaan tag pintar", config.permNfc, { config = config.copy(permNfc = it) }, AccentEmerald)

                    Spacer(modifier = Modifier.height(18.dp))

                    // Sistem & Biometrik
                    Text(text = "Sistem & Keamanan", style = MaterialTheme.typography.titleMedium, color = AccentPurple, fontWeight = FontWeight.SemiBold)
                    Divider(color = SlateBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                    CyberToggleRow("POST_NOTIFICATIONS", "Push Notifications (Android 13+)", config.permPostNotifications, { config = config.copy(permPostNotifications = it) }, AccentPurple)
                    CyberToggleRow("VIBRATE", "Getaran respon haptic interaktif", config.permVibrate, { config = config.copy(permVibrate = it) }, AccentPurple)
                    CyberToggleRow("WAKE_LOCK", "Mencegah layar padam saat streaming video", config.permWakeLock, { config = config.copy(permWakeLock = it) }, AccentPurple)
                    CyberToggleRow("HIGH_SAMPLING_RATE_SENSORS", "Sensor akselerometer responsif tinggi", config.permHighSamplingRateSensors, { config = config.copy(permHighSamplingRateSensors = it) }, AccentPurple)
                    CyberToggleRow("USE_BIOMETRIC", "Autentikasi sidik jari / face unlock WebAuthn", config.permUseBiometric, { config = config.copy(permUseBiometric = it) }, AccentRose)
                }
            }
        }

        // TAB 2: Live Emulator
        if (selectedTab == 2) {
            item {
                CyberCard(borderColor = SlateBorder) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneIphone, contentDescription = null, tint = PrimaryIndigoLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pratinjau Smartphone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        // URL Chip
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = SlateSurface,
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Text(
                                text = config.webUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentCyan,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sleek Device Frame
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(430.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(2.dp, SlateBorder, RoundedCornerShape(20.dp))
                            .background(SlateCanvas)
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

                    Spacer(modifier = Modifier.height(12.dp))

                    CyberSecondaryButton(
                        text = "Buka di Browser Eksternal",
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(config.webUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "URL tidak valid", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // TAB 3: Source Code Inspector
        if (selectedTab == 3) {
            item {
                CyberCard(borderColor = SlateBorder) {
                    Text(
                        text = "Artifak Source Code Generator",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val codeTabs = listOf("config.json", "AndroidManifest.xml", "MainActivity.kt", "build.gradle.kts", "setup_keystore.sh")
                        codeTabs.forEachIndexed { idx, label ->
                            val isSel = codeTab == idx
                            Surface(
                                onClick = { codeTab = idx },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) PrimaryIndigo else SlateSurface,
                                border = BorderStroke(1.dp, if (isSel) PrimaryIndigoLight else SlateBorder)
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) Color.White else TextSecondary,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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

        // TAB 4: Build Log & Actions
        if (selectedTab == 4 || isBuilding || buildFinished) {
            item {
                CyberCard(borderColor = if (buildFinished) AccentEmerald else SlateBorder) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pipeline Compiler Execution",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (buildFinished) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AccentEmeraldSoft)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("SUCCESS", style = MaterialTheme.typography.labelSmall, color = AccentEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isBuilding) {
                        LinearProgressIndicator(
                            progress = { buildProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = PrimaryIndigo,
                            trackColor = SlateSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(SlateCanvas, RoundedCornerShape(12.dp))
                            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        LazyColumn(reverseLayout = true) {
                            items(buildLogs.size) { idx ->
                                val log = buildLogs[buildLogs.size - 1 - idx]
                                val color = when {
                                    log.contains("[SUCCESS]") -> AccentEmerald
                                    log.contains("[SIGN]") -> AccentAmber
                                    log.contains("[PATCH]") -> AccentCyan
                                    log.contains("[ERROR]") -> AccentRose
                                    else -> TextSecondary
                                }
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
                                    color = color,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CyberButton(
                    text = if (isBuilding) "Menyiapkan project..." else "BUILD PROJECT & BUAT SHORTCUT",
                    enabled = !isBuilding,
                    onClick = {
                        coroutineScope.launch {
                            isBuilding = true
                            buildFinished = false
                            buildProgress = 0f
                            generatedBundle = null
                            buildLogs = listOf("[INIT] Memvalidasi konfigurasi ${config.packageName}...")
                            delay(250)
                            try {
                                buildLogs = buildLogs + "[VALIDATE] URL, package name, nama aplikasi..."
                                buildProgress = 0.2f
                                delay(150)
                                buildLogs = buildLogs + "[PATCH] Menyiapkan manifest, izin, dan config.json..."
                                buildProgress = 0.45f
                                delay(150)
                                buildLogs = buildLogs + "[GENERATE] Menulis source code, Gradle, ikon, dan workflow CI..."
                                buildProgress = 0.7f
                                val bundle = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    ApkPackagingEngine.exportStandaloneProjectZip(context, config)
                                }
                                generatedBundle = bundle
                                val size = String.format("%.1f", bundle.sizeBytes / (1024.0 * 1024.0).coerceAtLeast(0.1))
                                buildLogs = buildLogs + "[PACKAGE] ${bundle.displayName} ($size MB)"
                                buildLogs = buildLogs + "[DOWNLOAD] ZIP juga disimpan di Download/IO-Tools bila penyimpanan mengizinkan."
                                buildLogs = buildLogs + "[SUCCESS] Project nyata siap di-build menjadi APK via Gradle/Android Studio/CI."
                                buildProgress = 1f
                                buildFinished = true
                                selectedTab = 4
                            } catch (e: Exception) {
                                buildLogs = buildLogs + "[ERROR] ${e.message ?: "Gagal membuat project"}"
                                buildProgress = 1f
                            } finally {
                                isBuilding = false
                            }
                        }
                    }
                )

                if (buildFinished && generatedBundle != null) {
                    val bundle = generatedBundle!!
                    val sizeMb = String.format("%.1f", bundle.sizeBytes / (1024.0 * 1024.0).coerceAtLeast(0.1))

                    CyberCard(borderColor = AccentEmerald) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AccentEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(26.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bundle.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Project ZIP nyata • $sizeMb MB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentEmerald
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PrimaryIndigo.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryIndigoLight, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "APK tidak dibuat di dalam HP karena compiler Android (aapt2, D8, signing tool) tidak tersedia. Project ZIP ini benar-benar bisa di-build menjadi APK installable dengan Gradle, Android Studio, atau GitHub Actions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                ApkPackagingEngine.installTerimaBersih(context, config)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("BUAT IKON LAYAR UTAMA & BUKA", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Shortcut nyata ke WebView fullscreen, bukan APK palsu", style = MaterialTheme.typography.labelSmall, fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.92f))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { ApkPackagingEngine.shareBundle(context, bundle) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("BAGIKAN / SIMPAN PROJECT ZIP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Buka di Android Studio atau kirim ke PC/Termux/CI", style = MaterialTheme.typography.labelSmall, fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.92f))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { ApkPackagingEngine.launchStandaloneApp(context, config) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, AccentCyan),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Icon(Icons.Default.Launch, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buka WebView", color = AccentCyan, style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(config.webUrl))) }
                                        .onFailure { Toast.makeText(context, "URL tidak valid", Toast.LENGTH_SHORT).show() }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PrimaryIndigoLight),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = PrimaryIndigoLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buka Browser", color = PrimaryIndigoLight, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Cara build APK: ekstrak ZIP → buka di Android Studio → Build APK, atau jalankan `gradle assembleRelease`, atau push ke GitHub dan unduh artifact dari Actions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun exportProjectZip(context: Context, config: WebToApkConfig) {
    try {
        val bundle = ApkPackagingEngine.exportStandaloneProjectZip(context, config)
        ApkPackagingEngine.shareBundle(context, bundle)
        Toast.makeText(context, "Project ZIP siap: ${bundle.displayName}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal membuat ZIP: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
