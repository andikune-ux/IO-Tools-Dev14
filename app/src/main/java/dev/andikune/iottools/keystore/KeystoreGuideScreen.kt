package dev.andikune.iottools.keystore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andikune.iottools.ui.components.*
import dev.andikune.iottools.ui.theme.*

@Composable
fun KeystoreGuideScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "Keystore & CI/CD Workflow",
                subtitle = "Alias: 'AndikUne' | GitHub Actions Auto-Build & Install",
                statusText = "VERIFIED",
                statusColor = AccentEmerald
            )
        }

        // Section 1: GitHub Actions CI/CD Workflow
        item {
            CyberCard(borderColor = SlateBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = PrimaryIndigoLight, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "1. GitHub Actions Auto-Build (.github/workflows/build-apk.yml)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Workflow CI/CD otomatis telah disiapkan di dalam proyek:\n" +
                            "• Otomatis berjalan setiap ada git push ke branch 'main' atau 'master'.\n" +
                            "• Mengompilasi kode sumber Android dan menandatangani APK dengan alias 'AndikUne'.\n" +
                            "• Menghasilkan file APK siap instal yang langsung dapat diunduh dari menu Artifacts atau GitHub Releases!\n\n" +
                            "Langkah Push ke GitHub:\n" +
                            "1. Di pojok kanan atas AI Studio, klik tombol 'Push to GitHub' atau 'Export to GitHub'.\n" +
                            "2. Masuk ke tab 'Actions' di repositori GitHub Anda.\n" +
                            "3. Workflow 'Build and Sign iD-Tools APK' akan berjalan otomatis.\n" +
                            "4. Setelah selesai (centang hijau), unduh 'iD-Tools-Dev14-Release.apk' dan pasang langsung di HP Anda!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Section 2: Mengapa App Not Installed Terjadi
        item {
            CyberCard(borderColor = SlateBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AccentRose, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "2. Anti 'App Not Installed' Protection",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Android OS memeriksa Package Signature sebelum mengizinkan pembaruan aplikasi. Kesalahan 'App not installed' terjadi jika:\n" +
                            "• Menggunakan keystore berbeda di versi update berikutnya.\n" +
                            "• Menggunakan debug key bawaan IDE untuk rilis publik.\n" +
                            "• Alias sertifikat berubah.\n\n" +
                            "Solusi: Gunakan keystore permanen dengan alias 'AndikUne' secara konsisten pada setiap build.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Section 3: Perintah Keytool
        item {
            CyberCard(borderColor = SlateBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "3. Pembuatan Keystore Lokal (Alias: AndikUne)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                val keytoolCommand = """
keytool -genkey -v \
  -keystore andikune-release.jks \
  -alias AndikUne \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Andik Une, OU=Dev14, O=AndikUne Dev, L=Jakarta, ST=DKI, C=ID" \
  -storepass AndikUne123456 \
  -keypass AndikUne123456
                """.trimIndent()

                CyberCodeBox(code = keytoolCommand)
            }
        }

        // Section 4: Gradle Config
        item {
            CyberCard(borderColor = SlateBorder) {
                Text(
                    text = "4. Konfigurasi signingConfig di Gradle",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                val gradleConfig = """
android {
    namespace = "dev.andikune.iottools"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.andikune.iottools"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("andikuneRelease") {
            storeFile = file("andikune-release.jks")
            storePassword = System.getenv("ANDIKUNE_STORE_PASSWORD") ?: "AndikUne123456"
            keyAlias = "AndikUne"
            keyPassword = System.getenv("ANDIKUNE_KEY_PASSWORD") ?: "AndikUne123456"
            v1SigningEnabled = true
            v2SigningEnabled = true
        }
    }
}
                """.trimIndent()

                CyberCodeBox(code = gradleConfig)
            }
        }
    }
}
