package dev.andikune.iottools.builder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.andikune.iottools.R
import dev.andikune.iottools.WebToApkStandaloneActivity
import dev.andikune.iottools.tools.common.MediaStoreSaver
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds honest, genuinely usable outputs for the Web-to-APK studio:
 *  1. A complete standalone Android Studio/Gradle project ZIP.
 *  2. A working pinned home-screen shortcut that opens the configured site
 *     in the local fullscreen WebView container.
 *
 * This app does not pretend to compile an APK on-device: doing that correctly
 * requires aapt2, D8/R8, a signing tool and the Android SDK. The exported
 * project builds a real APK with Gradle/Android Studio/CI.
 */
object ApkPackagingEngine {

    data class ProjectBundle(
        val file: File,
        val downloadUri: Uri?,
        val displayName: String,
        val sizeBytes: Long
    )

    fun exportStandaloneProjectZip(context: Context, config: WebToApkConfig): ProjectBundle {
        validate(config)
        WebToApkStorage.saveActiveConfig(context, config)

        val sanitizedName = sanitize(config.appName).ifBlank { "WebApp" }
        val outputDir = File(context.cacheDir, "project_zips").apply { mkdirs() }
        val outputZipFile = File(outputDir, "$sanitizedName-Standalone-Project.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
            fun add(path: String, content: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            add(
                "settings.gradle.kts",
                """
                import org.gradle.api.initialization.resolve.RepositoriesMode

                pluginManagement {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories {
                        google()
                        mavenCentral()
                    }
                }
                rootProject.name = "${escapeGradle(config.appName)}"
                include(":app")
                """.trimIndent()
            )

            add(
                "build.gradle.kts",
                """
                plugins {
                    id("com.android.application") version "8.7.2" apply false
                    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
                }
                """.trimIndent()
            )

            add(
                "gradle.properties",
                """
                org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
                android.useAndroidX=true
                android.nonTransitiveRClass=true
                kotlin.code.style=official
                """.trimIndent()
            )

            add(
                "app/build.gradle.kts",
                """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }

                android {
                    namespace = "${escapeGradle(config.packageName)}"
                    compileSdk = 35

                    defaultConfig {
                        applicationId = "${escapeGradle(config.packageName)}"
                        minSdk = 24
                        targetSdk = 35
                        versionCode = ${config.versionCode}
                        versionName = "${escapeGradle(config.versionName)}"
                    }

                    buildTypes {
                        release {
                            isMinifyEnabled = false
                            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                            // Debug signing makes the exported project produce an installable APK
                            // immediately. Replace this with your own release keystore for publishing.
                            signingConfig = signingConfigs.getByName("debug")
                        }
                    }

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                    kotlinOptions {
                        jvmTarget = "17"
                    }
                }

                dependencies {
                    implementation("androidx.core:core-ktx:1.15.0")
                    implementation("androidx.activity:activity-ktx:1.9.3")
                    implementation("androidx.webkit:webkit:1.12.1")
                    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
                }
                """.trimIndent()
            )

            add(
                "app/proguard-rules.pro",
                """
                # Generated project uses minify=false by default. Keep rules here if
                # you enable R8/ProGuard later.
                """.trimIndent()
            )

            add("app/src/main/AndroidManifest.xml", WebToApkPipeline.generatePatchedManifest(config))
            add("app/src/main/assets/config.json", WebToApkPipeline.generateConfigJson(config))

            val packagePath = config.packageName.replace('.', '/')
            add(
                "app/src/main/java/$packagePath/MainActivity.kt",
                WebToApkPipeline.generateStandaloneMainActivity(config)
            )

            add(
                "app/src/main/res/values/strings.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name">${escapeXml(config.appName)}</string>
                </resources>""".trimIndent()
            )

            add(
                "app/src/main/res/values/colors.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <color name="primary_color">${escapeXml(config.themeColorHex)}</color>
                    <color name="nav_bar_color">${escapeXml(config.navBarColorHex)}</color>
                </resources>""".trimIndent()
            )

            add(
                "app/src/main/res/drawable/ic_launcher.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <path
                        android:fillColor="#6366F1"
                        android:pathData="M0,0h108v108h-108z" />
                    <path
                        android:fillColor="#FFFFFF"
                        android:pathData="M54,22a32,32 0,1 0,0.1 0zM54,32a22,22 0,1 1,-0.1 0z" />
                    <path
                        android:fillColor="#FFFFFF"
                        android:pathData="M50,42h8v28h-8zM38,54h32v8h-32z" />
                </vector>""".trimIndent()
            )

            add(
                ".github/workflows/build_apk.yml",
                """
                name: Build Standalone APK
                on: [push, workflow_dispatch]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/setup-java@v4
                        with:
                          distribution: 'zulu'
                          java-version: '17'
                      - uses: gradle/actions/setup-gradle@v4
                        with:
                          gradle-version: '8.10.2'
                      - name: Assemble APK
                        run: gradle assembleRelease --stacktrace
                      - name: Upload APK
                        uses: actions/upload-artifact@v4
                        with:
                          name: ${sanitize(config.appName)}-APK
                          path: app/build/outputs/apk/**/*.apk
                """.trimIndent()
            )

            add(
                "README.md",
                """
                # ${config.appName} — Standalone Android Project

                Generated by iD-Tools Dev14 Web-to-APK Studio.

                - Package: `${config.packageName}`
                - Target URL: `${config.webUrl}`
                - Version: `${config.versionName}` (${config.versionCode})
                - Theme: `${config.themeColorHex}` / `${config.navBarColorHex}`

                Ini adalah project Android Studio/Gradle yang lengkap. APK dibuat
                oleh compiler Gradle di komputer/Termux/CI, bukan disimulasikan.

                ## Build dengan Android Studio
                1. Ekstrak ZIP ini.
                2. **Open an Existing Project** lalu pilih folder hasil ekstrak.
                3. Tunggu Gradle sync.
                4. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

                ## Build lewat terminal
                ```bash
                gradle assembleRelease
                ```
                APK ada di `app/build/outputs/apk/release/`.

                ## Build lewat GitHub Actions
                Push folder ini ke repository GitHub. Workflow
                `.github/workflows/build_apk.yml` akan membuat artifact APK.

                Release memakai debug signing agar APK langsung dapat dipasang.
                Ganti dengan keystore produksi Anda sebelum publikasi.
                """.trimIndent()
            )
        }

        val downloadUri = outputZipFile.inputStream().use { input ->
            MediaStoreSaver.saveStream(
                context,
                outputZipFile.name,
                "application/zip",
                input
            )
        }

        return ProjectBundle(
            file = outputZipFile,
            downloadUri = downloadUri,
            displayName = outputZipFile.name,
            sizeBytes = outputZipFile.length()
        )
    }

    fun shareBundle(context: Context, bundle: ProjectBundle) {
        val uri = bundle.downloadUri ?: FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            bundle.file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, bundle.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan project ZIP"))
    }

    /**
     * Pins a real launcher shortcut. The shortcut opens the same local
     * fullscreen WebView app with the current configuration.
     */
    fun installTerimaBersih(context: Context, config: WebToApkConfig): Boolean {
        validate(config)
        WebToApkStorage.saveActiveConfig(context, config)
        val pinned = pinToHomeScreen(context, config)
        launchStandaloneApp(context, config)
        return pinned
    }

    fun pinToHomeScreen(context: Context, config: WebToApkConfig): Boolean {
        return try {
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                Toast.makeText(
                    context,
                    "Peluncur ini tidak mendukung pemasangan ikon otomatis. Gunakan 'Buka Langsung'.",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }

            val launchIntent = Intent(context, WebToApkStandaloneActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("EXTRA_WEB_URL", config.webUrl)
                putExtra("EXTRA_APP_NAME", config.appName)
                putExtra("EXTRA_THEME_COLOR", config.themeColorHex)
                putExtra("EXTRA_NAV_COLOR", config.navBarColorHex)
                putExtra("EXTRA_PERM_CAMERA", config.permCamera)
                putExtra("EXTRA_PERM_AUDIO", config.permRecordAudio)
                putExtra("EXTRA_PERM_LOCATION", config.permFineLocation || config.permCoarseLocation)
                putExtra("EXTRA_PULL_REFRESH", config.enablePullToRefresh)
                putExtra("EXTRA_PROGRESS_BAR", config.enableProgressBar)
                putExtra("EXTRA_DESKTOP_MODE", config.enableDesktopMode)
                putExtra("EXTRA_ZOOM", config.enableZoomControls)
                putExtra("EXTRA_CLEAR_CACHE", config.clearCacheOnExit)
                putExtra("EXTRA_USER_AGENT", config.userAgentOverride)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val shortcut = ShortcutInfoCompat.Builder(context, "webapp_${config.packageName}_${config.appName.hashCode()}")
                .setShortLabel(config.appName.take(25).ifBlank { "Web App" })
                .setLongLabel(config.appName.ifBlank { "Web App" })
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_app_logo))
                .setIntent(launchIntent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            Toast.makeText(context, "Permintaan ikon '${config.appName}' dikirim ke peluncur.", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuat shortcut: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun launchStandaloneApp(context: Context, config: WebToApkConfig) {
        WebToApkStorage.saveActiveConfig(context, config)
        val intent = Intent(context, WebToApkStandaloneActivity::class.java).apply {
            putExtra("EXTRA_WEB_URL", config.webUrl)
            putExtra("EXTRA_APP_NAME", config.appName)
            putExtra("EXTRA_THEME_COLOR", config.themeColorHex)
            putExtra("EXTRA_NAV_COLOR", config.navBarColorHex)
            putExtra("EXTRA_PERM_CAMERA", config.permCamera)
            putExtra("EXTRA_PERM_AUDIO", config.permRecordAudio)
            putExtra("EXTRA_PERM_LOCATION", config.permFineLocation || config.permCoarseLocation)
            putExtra("EXTRA_PULL_REFRESH", config.enablePullToRefresh)
            putExtra("EXTRA_PROGRESS_BAR", config.enableProgressBar)
            putExtra("EXTRA_DESKTOP_MODE", config.enableDesktopMode)
            putExtra("EXTRA_ZOOM", config.enableZoomControls)
            putExtra("EXTRA_CLEAR_CACHE", config.clearCacheOnExit)
            putExtra("EXTRA_USER_AGENT", config.userAgentOverride)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun validate(config: WebToApkConfig) {
        val url = config.webUrl.trim()
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "URL harus dimulai dengan http:// atau https://"
        }
        require(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$").matches(config.packageName)) {
            "Package name tidak valid. Contoh: com.nama.app"
        }
        require(config.appName.isNotBlank()) { "Nama aplikasi wajib diisi." }
        require(config.versionCode > 0) { "Version code harus lebih dari 0." }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_').take(60)

    private fun escapeGradle(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
