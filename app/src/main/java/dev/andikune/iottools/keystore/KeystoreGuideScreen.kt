package dev.andikune.iottools.keystore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            .background(CyberBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CyberTerminalHeader(
                title = "KEYSTORE SETUP & SIGNATURE GUIDE",
                subtitle = "Alias: 'AndikUne' | Anti-'App Not Installed' Protection",
                statusText = "SECURED",
                statusColor = MatrixGreen
            )
        }

        item {
            CyberCard(borderColor = NeonRed, glow = true) {
                Text(
                    text = "[1. MENGAPA BISA TERJADI 'APP NOT INSTALLED'?]",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonRed,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Android OS memiliki sistem proteksi ketat (Package Signature Verification). Ketika Anda meng-update aplikasi, Android membandingkan sertifikat digital APK baru dengan APK yang sudah terpasang.\n\n" +
                            "Masalah terjadi jika:\n" +
                            "• Keystore berbeda atau alias berubah di versi berikutnya.\n" +
                            "• Menggunakan debug key bawaan IDE untuk versi rilis.\n" +
                            "• Skema tanda tangan V1/V2 tidak konsisten.\n\n" +
                            "Solusi: Buat keystore permanen dengan alias 'AndikUne' dan gunakan signingConfig yang konsisten pada setiap build.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TerminalText
                )
            }
        }

        item {
            CyberCard(borderColor = NeonCyan) {
                Text(
                    text = "[2. PERINTAH PEMBUATAN KEYSTORE RESMI]",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Jalankan perintah keytool ini di terminal komputer Anda untuk membuat keystore dengan alias 'AndikUne':",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TerminalDim
                )
                Spacer(modifier = Modifier.height(8.dp))

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

        item {
            CyberCard(borderColor = MatrixGreen) {
                Text(
                    text = "[3. KONFIGURASI SIGNING DI BUILD.GRADLE.KTS]",
                    style = MaterialTheme.typography.titleMedium,
                    color = MatrixGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Masukkan blok signingConfig berikut ke dalam app/build.gradle.kts proyek Anda agar Gradle otomatis menandatangani APK saat proses build:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TerminalDim
                )
                Spacer(modifier = Modifier.height(8.dp))

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

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("andikuneRelease")
        }
        debug {
            // Gunakan signingConfig yang sama untuk update debug -> release tanpa uninstal
            signingConfig = signingConfigs.getByName("andikuneRelease")
        }
    }
}
                """.trimIndent()

                CyberCodeBox(code = gradleConfig)
            }
        }

        item {
            CyberCard(borderColor = NeonYellow) {
                Text(
                    text = "[4. CARA VERIFIKASI TANDA TANGAN (FINGERPRINT)]",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonYellow,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Untuk memeriksa apakah APK memiliki tanda tangan yang sesuai:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TerminalDim
                )
                Spacer(modifier = Modifier.height(8.dp))

                val verifyCommand = """
# Periksa sertifikat di dalam Keystore:
keytool -list -v -keystore andikune-release.jks -alias AndikUne

# Periksa signature pada APK hasil build:
apksigner verify --verbose --print-certs app-release.apk
                """.trimIndent()

                CyberCodeBox(code = verifyCommand)
            }
        }
    }
}
