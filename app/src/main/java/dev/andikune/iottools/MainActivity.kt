package dev.andikune.iottools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.DeviceCommandCenterScreen
import dev.andikune.iottools.builder.WebToApkScreen
import dev.andikune.iottools.keystore.KeystoreGuideScreen
import dev.andikune.iottools.tools.data.DataHashTools
import dev.andikune.iottools.tools.image.ImageTools
import dev.andikune.iottools.tools.media.MediaTools
import dev.andikune.iottools.tools.network.NetworkTools
import dev.andikune.iottools.tools.pdf.PdfTools
import dev.andikune.iottools.tools.privacy.PrivacyTools
import dev.andikune.iottools.tools.qr.QrBarcodeTools
import dev.andikune.iottools.tools.instagram.InstagramDownloaderScreen
import dev.andikune.iottools.tools.terabox.TeraBoxDownloaderScreen
import dev.andikune.iottools.tools.tiktok.TikTokDownloaderScreen
import dev.andikune.iottools.tools.ucbrowser.UcMediaSnifferScreen
import dev.andikune.iottools.tools.youtube.YouTubeDownloaderScreen
import dev.andikune.iottools.tools.linkbypass.LinkBypassScreen
import dev.andikune.iottools.ui.components.CyberTerminalHeader
import dev.andikune.iottools.ui.theme.*

class MainActivity : ComponentActivity() {
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Izin penyimpanan ditolak. Simpan file mungkin tidak berfungsi pada Android 9 ke bawah.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // yt-dlp and FFmpeg ship native runtimes; initialize before any downloader screen is used.
        try {
            YoutubeDL.getInstance().init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
        } catch (e: Exception) {
            Log.e("IO-Tools", "Gagal menginisialisasi yt-dlp/FFmpeg", e)
            Toast.makeText(this, "Engine downloader gagal dimuat: ${e.message}", Toast.LENGTH_LONG).show()
        }
        if (Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        enableEdgeToEdge()
        setContent {
            IDToolsTheme {
                MainAppScreen()
            }
        }
    }

}

enum class MainNavigationTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.Home),
    WEB_TO_APK("Web-to-APK", Icons.Default.Build),
    ALL_TOOLS("Toolkit Pro", Icons.Default.GridView),
    KEYSTORE("Keystore & CI", Icons.Default.VpnKey),
    TERMINAL("Console", Icons.Default.Terminal)
}

enum class ToolCategory(val label: String, val description: String, val icon: ImageVector, val color: Color) {
    TIKTOK("TikTok Downloader", "Unduh Video HD Tanpa Watermark & MP3", Icons.Default.CloudDownload, AccentRose),
    YOUTUBE("YouTube Downloader", "Video MP4 720p/360p & Audio MP3", Icons.Default.VideoLibrary, AccentRose),
    INSTAGRAM("Instagram Reels", "Unduh Reels & Foto Resolusi Asli", Icons.Default.CameraAlt, AccentPurple),
    TERABOX("TeraBox Direct", "Bypass Limit & Download Cloud File", Icons.Default.CloudSync, AccentAmber),
    UC_SNIFFER("UC Media Sniffer", "Ekstrak Link Video (MP4/M3U8) dari Web", Icons.Default.TravelExplore, AccentCyan),
    QR_BARCODE("QR & Barcode", "Generator, Code128, Scanner", Icons.Default.QrCode, AccentCyan),
    IMAGE("Studio Gambar", "Compress, Resize, Convert, EXIF", Icons.Default.Image, AccentCyan),
    PDF("Dokumen PDF", "Merge, Split, Protect, Rotate", Icons.Default.PictureAsPdf, AccentAmber),
    DATA_HASH("File & Hash", "SHA-256, MD5, Base64, Rename", Icons.Default.DataObject, AccentEmerald),
    NETWORK("Network Recon", "IP Lookup, DNS, Live Ping, SSL", Icons.Default.Wifi, PrimaryIndigoLight),
    PRIVACY("Privacy Vault", "Password, Notes, 2FA Authenticator", Icons.Default.Security, AccentPurple),
    MEDIA("Audio & Video", "Trim, Extract MP3, Video ke GIF", Icons.Default.VideoLibrary, AccentAmber),
    LINK_BYPASS("Short Link Bypass", "Temukan URL tujuan tautan pendek", Icons.Default.Link, AccentEmerald)
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(MainNavigationTab.DASHBOARD) }
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }

    BackHandler(enabled = selectedCategory != null || currentTab != MainNavigationTab.WEB_TO_APK) {
        if (selectedCategory != null) {
            selectedCategory = null
        } else {
            currentTab = MainNavigationTab.DASHBOARD
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas),
        containerColor = SlateCanvas,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ModernAppTopBar(
                currentTab = currentTab,
                selectedCategory = selectedCategory,
                onBackClicked = { selectedCategory = null }
            )
        },
        bottomBar = {
            ModernBottomNavigation(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    triggerHaptic(context)
                    selectedCategory = null
                    currentTab = tab
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SlateCanvas)
        ) {
            when (currentTab) {
                MainNavigationTab.DASHBOARD -> {
                    DeviceCommandCenterScreen()
                }

                MainNavigationTab.WEB_TO_APK -> {
                    WebToApkScreen()
                }

                MainNavigationTab.ALL_TOOLS -> {
                    val category = selectedCategory
                    if (category == null) {
                        ModernToolsGridScreen(onSelectCategory = { cat ->
                            triggerHaptic(context)
                            selectedCategory = cat
                        })
                    } else {
                        when (category) {
                            ToolCategory.TIKTOK -> TikTokDownloaderScreen()
                            ToolCategory.YOUTUBE -> YouTubeDownloaderScreen()
                            ToolCategory.INSTAGRAM -> InstagramDownloaderScreen()
                            ToolCategory.TERABOX -> TeraBoxDownloaderScreen()
                            ToolCategory.UC_SNIFFER -> UcMediaSnifferScreen()
                            ToolCategory.QR_BARCODE -> QrBarcodeTools()
                            ToolCategory.IMAGE -> ImageTools()
                            ToolCategory.PDF -> PdfTools()
                            ToolCategory.DATA_HASH -> DataHashTools()
                            ToolCategory.NETWORK -> NetworkTools()
                            ToolCategory.PRIVACY -> PrivacyTools()
                            ToolCategory.MEDIA -> MediaTools()
                            ToolCategory.LINK_BYPASS -> LinkBypassScreen()
                        }
                    }
                }

                MainNavigationTab.KEYSTORE -> {
                    KeystoreGuideScreen()
                }

                MainNavigationTab.TERMINAL -> {
                    ModernTerminalScreen()
                }
            }
        }
    }
}

@Composable
fun ModernAppTopBar(
    currentTab: MainNavigationTab,
    selectedCategory: ToolCategory?,
    onBackClicked: () -> Unit
) {
    Surface(
        color = SlateCanvas,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedCategory != null) {
                    IconButton(onClick = onBackClicked, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateSurface)
                            .border(1.dp, SlateBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "App Icon",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column {
                    Text(
                        text = selectedCategory?.label ?: "iD-Tools Dev14",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "dev.andikune.iottools",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AccentEmeraldSoft)
                    .border(1.dp, AccentEmerald.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AccentEmerald)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "v1.4 Pro",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentEmerald,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ModernToolsGridScreen(onSelectCategory: (ToolCategory) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas)
            .padding(16.dp)
    ) {
        CyberTerminalHeader(
            title = "All-in-One Toolkit",
            subtitle = "13 Modul Utilitas & Media Downloader Pro",
            statusText = "READY",
            statusColor = AccentEmerald
        )

        Spacer(modifier = Modifier.height(14.dp))

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(ToolCategory.values().size) { idx ->
                val cat = ToolCategory.values()[idx]
                Surface(
                    onClick = { onSelectCategory(cat) },
                    shape = RoundedCornerShape(16.dp),
                    color = SlateElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cat.color.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = cat.label,
                                    tint = cat.color,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = cat.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernTerminalScreen() {
    var commandInput by remember { mutableStateOf("help") }
    var terminalLogs by remember {
        mutableStateOf(
            listOf(
                "[SYSTEM] iD-Tools Dev14 Pro Environment Loaded.",
                "[IDENTITY] Package: dev.andikune.iottools | Keystore: Alias 'AndikUne'",
                "[WORKFLOW] GitHub Actions CI/CD Configured (.github/workflows/build-apk.yml)",
                "[STATUS] All 7 Tool Engines Active. Type 'help' to view commands."
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas)
            .padding(16.dp)
    ) {
        CyberTerminalHeader(
            title = "Developer Console",
            subtitle = "Status Kernel & CLI Emulator | dev.andikune.iottools",
            statusText = "ONLINE",
            statusColor = PrimaryIndigoLight
        )

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SlateElevated)
                .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            androidx.compose.foundation.lazy.LazyColumn(reverseLayout = true) {
                items(terminalLogs.size) { index ->
                    val line = terminalLogs[terminalLogs.size - 1 - index]
                    val color = when {
                        line.startsWith(">") -> AccentCyan
                        line.contains("[STATUS]") || line.contains("[WORKFLOW]") -> AccentEmerald
                        line.contains("[SYSTEM]") -> PrimaryIndigoLight
                        else -> TextPrimary
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontSize = 12.sp),
                        color = color,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dev.andikune.iottools.ui.components.CyberTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                label = "Terminal Command",
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            dev.andikune.iottools.ui.components.CyberButton(
                text = "KIRIM",
                modifier = Modifier.width(90.dp),
                onClick = {
                    val cmd = commandInput.trim().lowercase()
                    val reply = when (cmd) {
                        "help" -> "Commands: help, status, workflow, keystore, clear, ping"
                        "status" -> "System OK. 7 Tools Standby. Web-to-APK Compiler Ready."
                        "workflow" -> "GitHub Actions: .github/workflows/build-apk.yml (Auto Build & Release)"
                        "keystore" -> "Keystore: andikune-release.jks | Alias: AndikUne (2048-bit RSA)"
                        "clear" -> {
                            terminalLogs = emptyList()
                            ""
                        }
                        else -> "Perintah tidak ditemukan: $cmd. Ketik 'help'."
                    }
                    if (reply.isNotEmpty()) {
                        terminalLogs = terminalLogs + "> $commandInput" + reply
                    }
                    commandInput = ""
                }
            )
        }
    }
}

@Composable
fun ModernBottomNavigation(
    currentTab: MainNavigationTab,
    onTabSelected: (MainNavigationTab) -> Unit
) {
    Surface(
        color = SlateElevated,
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainNavigationTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                val color = if (isSelected) PrimaryIndigoLight else TextMuted

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) PrimaryIndigo.copy(alpha = 0.15f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) PrimaryIndigo else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun triggerHaptic(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(15)
        }
    } catch (_: Exception) {}
}
