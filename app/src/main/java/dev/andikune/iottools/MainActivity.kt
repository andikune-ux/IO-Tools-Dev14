package dev.andikune.iottools

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.builder.WebToApkScreen
import dev.andikune.iottools.keystore.KeystoreGuideScreen
import dev.andikune.iottools.tools.data.DataHashTools
import dev.andikune.iottools.tools.image.ImageTools
import dev.andikune.iottools.tools.media.MediaTools
import dev.andikune.iottools.tools.network.NetworkTools
import dev.andikune.iottools.tools.pdf.PdfTools
import dev.andikune.iottools.tools.privacy.PrivacyTools
import dev.andikune.iottools.tools.qr.QrBarcodeTools
import dev.andikune.iottools.ui.components.CyberTerminalHeader
import dev.andikune.iottools.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IDToolsTheme {
                MainAppScreen()
            }
        }
    }
}

enum class MainNavigationTab(val label: String, val icon: ImageVector) {
    WEB_TO_APK("Web-to-APK", Icons.Default.Build),
    ALL_TOOLS("All Tools", Icons.Default.Apps),
    KEYSTORE("Keystore Guide", Icons.Default.VpnKey),
    TERMINAL("Terminal", Icons.Default.Terminal)
}

enum class ToolCategory(val label: String, val description: String, val icon: ImageVector, val color: Color) {
    QR_BARCODE("QR & Barcode", "Generator, Scanner, Batch Gen", Icons.Default.QrCode, NeonRed),
    IMAGE("Gambar (Image)", "Compress, Resize, Convert, EXIF", Icons.Default.Image, NeonCyan),
    PDF("Dokumen PDF", "Merge, Split, Protect, Rotate", Icons.Default.PictureAsPdf, NeonRed),
    DATA_HASH("File & Hash", "MD5, SHA-256, Base64, URL", Icons.Default.DataObject, MatrixGreen),
    NETWORK("Network Recon", "IP, DNS, Live Ping, SSL", Icons.Default.Wifi, NeonCyan),
    PRIVACY("Privacy & 2FA", "Password, Notes, Authenticator", Icons.Default.Security, NeonPurple),
    MEDIA("Audio / Video", "Trim, Extract Audio, GIF", Icons.Default.VideoLibrary, NeonYellow)
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(MainNavigationTab.WEB_TO_APK) }
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }

    // Handle back button when inside a specific tool
    BackHandler(enabled = selectedCategory != null || currentTab != MainNavigationTab.WEB_TO_APK) {
        if (selectedCategory != null) {
            selectedCategory = null
        } else {
            currentTab = MainNavigationTab.WEB_TO_APK
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack),
        containerColor = CyberBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            CyberBottomNavigation(
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
                .background(CyberBlack)
        ) {
            when (currentTab) {
                MainNavigationTab.WEB_TO_APK -> {
                    WebToApkScreen()
                }

                MainNavigationTab.ALL_TOOLS -> {
                    if (selectedCategory == null) {
                        ToolsGridScreen(onSelectCategory = { cat ->
                            triggerHaptic(context)
                            selectedCategory = cat
                        })
                    } else {
                        when (selectedCategory) {
                            ToolCategory.QR_BARCODE -> QrBarcodeTools()
                            ToolCategory.IMAGE -> ImageTools()
                            ToolCategory.PDF -> PdfTools()
                            ToolCategory.DATA_HASH -> DataHashTools()
                            ToolCategory.NETWORK -> NetworkTools()
                            ToolCategory.PRIVACY -> PrivacyTools()
                            ToolCategory.MEDIA -> MediaTools()
                            null -> {}
                        }
                    }
                }

                MainNavigationTab.KEYSTORE -> {
                    KeystoreGuideScreen()
                }

                MainNavigationTab.TERMINAL -> {
                    TerminalConsoleScreen()
                }
            }
        }
    }
}

@Composable
fun ToolsGridScreen(onSelectCategory: (ToolCategory) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
            .padding(16.dp)
    ) {
        CyberTerminalHeader(
            title = "ALL-IN-ONE CYBER TOOLKIT",
            subtitle = "7 Modul Utilitas Utama | Offline-First Engine",
            statusText = "7 MODULES",
            statusColor = MatrixGreen
        )

        Spacer(modifier = Modifier.height(14.dp))

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(ToolCategory.values().size) { idx ->
                val cat = ToolCategory.values()[idx]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberCard)
                        .border(1.dp, cat.color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { onSelectCategory(cat) }
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = cat.label,
                                tint = cat.color,
                                modifier = Modifier.size(28.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(cat.color)
                            )
                        }

                        Column {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = TerminalText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = cat.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TerminalDim,
                                fontSize = 10.sp,
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
fun TerminalConsoleScreen() {
    var commandInput by remember { mutableStateOf("help") }
    var terminalLogs by remember {
        mutableStateOf(
            listOf(
                "[BOOT] iD-Tools Dev14.Apk Initialized.",
                "[KERNEL] Linux Android Sandbox v36.0",
                "[IDENTITY] Package: dev.andikune.iottools",
                "[SIGNATURE] Alias: AndikUne (Verified)",
                "[READY] Type 'help', 'status', 'ping', or 'clear' to execute."
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
            .padding(16.dp)
    ) {
        CyberTerminalHeader(
            title = "ROOT TERMINAL CLI",
            subtitle = "Console Shell Emulator | dev.andikune.iottools",
            statusText = "ROOT ACCESS",
            statusColor = NeonRed
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(CyberDark, RoundedCornerShape(8.dp))
                .border(1.dp, NeonRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            androidx.compose.foundation.lazy.LazyColumn(reverseLayout = true) {
                items(terminalLogs.size) { index ->
                    val line = terminalLogs[terminalLogs.size - 1 - index]
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontSize = 12.sp),
                        color = if (line.startsWith(">")) NeonCyan else if (line.contains("[READY]") || line.contains("OK")) MatrixGreen else TerminalText,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("> ", style = MaterialTheme.typography.titleMedium, color = NeonRed, fontWeight = FontWeight.Bold)
            dev.andikune.iottools.ui.components.CyberTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                label = "Perintah Terminal",
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            dev.andikune.iottools.ui.components.CyberButton(
                text = "EXEC",
                modifier = Modifier.width(80.dp),
                onClick = {
                    val cmd = commandInput.trim().lowercase()
                    val reply = when (cmd) {
                        "help" -> "Commands: help, status, config, keystore, clear, uname, ping"
                        "status" -> "System OK. 7 Tools Loaded. Web-to-APK Compiler Standby."
                        "config" -> "Target Package: dev.andikune.iottools | Permissions: Granular"
                        "keystore" -> "Keystore: andikune-release.jks | Alias: AndikUne | RSA 2048"
                        "uname" -> "Linux dev14-android 6.1.0 #1 SMP PREEMPT"
                        "clear" -> {
                            terminalLogs = emptyList()
                            ""
                        }
                        else -> "Command not found: $cmd. Ketik 'help'."
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
fun CyberBottomNavigation(
    currentTab: MainNavigationTab,
    onTabSelected: (MainNavigationTab) -> Unit
) {
    Surface(
        color = CyberDark,
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainNavigationTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                val color = if (isSelected) NeonRed else TerminalDim

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
            vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(20)
        }
    } catch (_: Exception) {}
}
