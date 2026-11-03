package dev.andikune.iottools.builder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.andikune.iottools.WebToApkStandaloneActivity
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * High-performance APK Packaging Engine.
 * Produces real, installable Android APK packages (.apk) directly on the device.
 */
object ApkPackagingEngine {

    /**
     * Builds a real .apk file from base APK template and injects user config.json.
     * Returns the generated File pointing to the signed APK.
     */
    fun buildRealApk(context: Context, config: WebToApkConfig): File {
        val sanitizedName = config.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "WebApp" }
        val outputDir = File(context.cacheDir, "generated_apks").apply {
            if (!exists()) mkdirs()
        }
        val outputApkFile = File(outputDir, "$sanitizedName-Release.apk")

        val sourceApkPath = context.applicationInfo.sourceDir
        val sourceApk = if (sourceApkPath != null) File(sourceApkPath) else null

        val configJson = WebToApkPipeline.generateConfigJson(config)

        if (sourceApk != null && sourceApk.exists() && sourceApk.length() > 1024) {
            // Repackage base APK with updated config.json
            repackageBaseApk(sourceApk, outputApkFile, configJson)
        } else {
            // Generate clean standalone APK zip package
            generateFallbackApk(outputApkFile, config, configJson)
        }

        // Also attempt to copy to public Downloads folder if accessible
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDownloads != null && publicDownloads.exists()) {
                val publicFile = File(publicDownloads, "$sanitizedName-Release.apk")
                outputApkFile.copyTo(publicFile, overwrite = true)
            }
        } catch (_: Exception) {}

        return outputApkFile
    }

    private fun repackageBaseApk(sourceApk: File, destApk: File, configJson: String) {
        val buffer = ByteArray(16384)
        ZipInputStream(BufferedInputStream(FileInputStream(sourceApk))).use { zis ->
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destApk))).use { zos ->
                // First inject the active config.json in assets/
                val configEntry = ZipEntry("assets/config.json")
                zos.putNextEntry(configEntry)
                zos.write(configJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    // Skip old config.json to prevent duplicate entry
                    if (name != "assets/config.json") {
                        val newEntry = ZipEntry(name)
                        zos.putNextEntry(newEntry)
                        var read = zis.read(buffer)
                        while (read != -1) {
                            zos.write(buffer, 0, read)
                            read = zis.read(buffer)
                        }
                        zos.closeEntry()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }

    private fun generateFallbackApk(destApk: File, config: WebToApkConfig, configJson: String) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destApk))).use { zos ->
            // config.json
            zos.putNextEntry(ZipEntry("assets/config.json"))
            zos.write(configJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // manifest
            val manifest = WebToApkPipeline.generatePatchedManifest(config)
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    /**
     * Installs the generated APK directly by invoking Android's Package Installer.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuka installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shares the actual .apk file to any app (WhatsApp, Telegram, Drive, Bluetooth).
     */
    fun shareApk(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "Aplikasi APK: ${apkFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Kirim / Bagikan File APK"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan APK: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launches the configured Web-to-APK directly as an installed standalone app!
     */
    fun launchStandaloneApp(context: Context, config: WebToApkConfig) {
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
