package dev.andikune.iottools.builder

/**
 * Pipeline generator for Web-to-APK engine.
 * Produces config.json, patched AndroidManifest.xml, custom MainActivity.kt,
 * and Gradle release configurations with alias "AndikUne".
 */
object WebToApkPipeline {

    /**
     * Serializes WebToApkConfig to clean JSON string.
     */
    fun generateConfigJson(config: WebToApkConfig): String {
        return """
        {
          "appName": "${escapeJson(config.appName)}",
          "webUrl": "${escapeJson(config.webUrl)}",
          "packageName": "${escapeJson(config.packageName)}",
          "versionName": "${config.versionName}",
          "versionCode": ${config.versionCode},
          "themeColorHex": "${config.themeColorHex}",
          "navBarColorHex": "${config.navBarColorHex}",
          "enablePullToRefresh": ${config.enablePullToRefresh},
          "enableProgressBar": ${config.enableProgressBar},
          "enableDesktopMode": ${config.enableDesktopMode},
          "enableZoomControls": ${config.enableZoomControls},
          "clearCacheOnExit": ${config.clearCacheOnExit},
          "userAgentOverride": "${escapeJson(config.userAgentOverride)}",
          "permissions": {
            "media": {
              "CAMERA": ${config.permCamera},
              "RECORD_AUDIO": ${config.permRecordAudio},
              "READ_MEDIA_IMAGES": ${config.permReadMediaImages},
              "READ_MEDIA_VIDEO": ${config.permReadMediaVideo},
              "READ_MEDIA_AUDIO": ${config.permReadMediaAudio}
            },
            "location": {
              "ACCESS_COARSE_LOCATION": ${config.permCoarseLocation},
              "ACCESS_FINE_LOCATION": ${config.permFineLocation}
            },
            "network": {
              "INTERNET": ${config.permInternet},
              "ACCESS_NETWORK_STATE": ${config.permAccessNetworkState},
              "ACCESS_WIFI_STATE": ${config.permAccessWifiState},
              "BLUETOOTH_CONNECT": ${config.permBluetoothConnect},
              "NFC": ${config.permNfc}
            },
            "system": {
              "POST_NOTIFICATIONS": ${config.permPostNotifications},
              "VIBRATE": ${config.permVibrate},
              "WAKE_LOCK": ${config.permWakeLock},
              "HIGH_SAMPLING_RATE_SENSORS": ${config.permHighSamplingRateSensors}
            },
            "biometric": {
              "USE_BIOMETRIC": ${config.permUseBiometric}
            }
          }
        }
        """.trimIndent()
    }

    /**
     * Generates AndroidManifest.xml dynamically based on permission toggles.
     */
    fun generatePatchedManifest(config: WebToApkConfig): String {
        val permList = mutableListOf<String>()

        // Jaringan
        if (config.permInternet) permList.add("android.permission.INTERNET")
        if (config.permAccessNetworkState) permList.add("android.permission.ACCESS_NETWORK_STATE")
        if (config.permAccessWifiState) permList.add("android.permission.ACCESS_WIFI_STATE")
        if (config.permBluetoothConnect) permList.add("android.permission.BLUETOOTH_CONNECT")
        if (config.permNfc) permList.add("android.permission.NFC")

        // Media
        if (config.permCamera) permList.add("android.permission.CAMERA")
        if (config.permRecordAudio) permList.add("android.permission.RECORD_AUDIO")
        if (config.permReadMediaImages) permList.add("android.permission.READ_MEDIA_IMAGES")
        if (config.permReadMediaVideo) permList.add("android.permission.READ_MEDIA_VIDEO")
        if (config.permReadMediaAudio) permList.add("android.permission.READ_MEDIA_AUDIO")

        // Lokasi
        if (config.permCoarseLocation) permList.add("android.permission.ACCESS_COARSE_LOCATION")
        if (config.permFineLocation) permList.add("android.permission.ACCESS_FINE_LOCATION")

        // Sistem
        if (config.permPostNotifications) permList.add("android.permission.POST_NOTIFICATIONS")
        if (config.permVibrate) permList.add("android.permission.VIBRATE")
        if (config.permWakeLock) permList.add("android.permission.WAKE_LOCK")
        if (config.permHighSamplingRateSensors) permList.add("android.permission.HIGH_SAMPLING_RATE_SENSORS")

        // Biometrik
        if (config.permUseBiometric) permList.add("android.permission.USE_BIOMETRIC")

        val permissionTags = permList.joinToString("\n    ") {
            """<uses-permission android:name="$it" />"""
        }

        val cameraFeatures = if (config.permCamera) {
            "\n    <uses-feature android:name=\"android.hardware.camera\" android:required=\"false\" />"
        } else ""

        val locationFeatures = if (config.permFineLocation || config.permCoarseLocation) {
            "\n    <uses-feature android:name=\"android.hardware.location.gps\" android:required=\"false\" />"
        } else ""

        return """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Auto-patched Permissions from config.json -->
    $permissionTags$cameraFeatures$locationFeatures

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="${config.appName}"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@android:style/Theme.NoTitleBar.Fullscreen">

        <activity
            android:name="${config.packageName}.MainActivity"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
        """.trimIndent()
    }

    /**
     * Generates MainActivity.kt for the Web-to-APK output.
     * Contains the strict config.json parser & WebChromeClient permission interceptor.
     */
    fun generateStandaloneMainActivity(config: WebToApkConfig): String = generateTemplateMainActivity(config)

    fun generateTemplateMainActivity(config: WebToApkConfig): String {
        return """
package ${config.packageName}

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.InputStream

/**
 * Generated by iD-Tools Web-to-APK Builder
 * Package: ${config.packageName}
 * Signature: Alias "AndikUne"
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var progressBar: ProgressBar? = null
    private lateinit var config: JSONObject
    
    // Permission state cached from config.json
    private var isCameraAllowed = false
    private var isAudioAllowed = false
    private var isLocationAllowed = false
    private var isStorageAllowed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load configuration from assets/config.json
        loadConfigFromAssets()
        
        // Window UI setup
        window.statusBarColor = Color.parseColor("${config.themeColorHex}")
        window.navigationBarColor = Color.parseColor("${config.navBarColorHex}")

        setupLayout()
        configureWebView()
        setupBackNavigation()
        
        val targetUrl = config.optString("webUrl", "${config.webUrl}")
        webView.loadUrl(targetUrl)
    }

    private fun loadConfigFromAssets() {
        try {
            val inputStream: InputStream = assets.open("config.json")
            val jsonStr = inputStream.bufferedReader().use { it.readText() }
            config = JSONObject(jsonStr)
            
            val perms = config.optJSONObject("permissions")
            val media = perms?.optJSONObject("media")
            val location = perms?.optJSONObject("location")
            
            // Check toggles strictly
            isCameraAllowed = media?.optBoolean("CAMERA", false) == true
            isAudioAllowed = media?.optBoolean("RECORD_AUDIO", false) == true
            isLocationAllowed = location?.optBoolean("ACCESS_FINE_LOCATION", false) == true ||
                                location?.optBoolean("ACCESS_COARSE_LOCATION", false) == true
            isStorageAllowed = media?.optBoolean("READ_MEDIA_IMAGES", false) == true
        } catch (e: Exception) {
            // Fallback default
            config = JSONObject()
            isCameraAllowed = ${config.permCamera}
            isAudioAllowed = ${config.permRecordAudio}
            isLocationAllowed = ${config.permFineLocation || config.permCoarseLocation}
        }
    }

    private fun setupLayout() {
        val rootLayout = android.widget.RelativeLayout(this).apply {
            setBackgroundColor(Color.parseColor("${config.navBarColorHex}"))
        }

        webView = WebView(this)
        rootLayout.addView(
            webView,
            android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
            )
        )

        if (${config.enableProgressBar}) {
            progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = false
                max = 100
                progressDrawable.setColorFilter(
                    Color.parseColor("${config.themeColorHex}"),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
            val progressParams = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                8
            ).apply {
                addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
            }
            rootLayout.addView(progressBar, progressParams)
        }

        setContentView(rootLayout)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportZoom(${config.enableZoomControls})
        settings.builtInZoomControls = ${config.enableZoomControls}
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        if (${config.enableDesktopMode}) {
            settings.userAgentString = settings.userAgentString.replace("Mobile", "eliboM")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                // Show offline cyberpunk fallback HTML
                val offlineHtml = "${escapeForStringLiteral(config.offlineFallbackHtml)}"
                view?.loadDataWithBaseURL(null, offlineHtml, "text/html", "UTF-8", null)
            }
        }

        // Strict WebChromeClient enforcing config.json permissions
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar?.visibility = View.GONE
                } else {
                    progressBar?.visibility = View.VISIBLE
                    progressBar?.progress = newProgress
                }
            }

            // CRITICAL: WebRTC / Camera & Microphone Permission Enforcement
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return

                val requestedResources = request.resources
                val grantedList = mutableListOf<String>()

                for (res in requestedResources) {
                    if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                        // Strict check against config.json toggle
                        if (!isCameraAllowed) {
                            // FORCED DENY
                            request.deny()
                            Toast.makeText(this@MainActivity, "Izin Kamera dinonaktifkan di config.json", Toast.LENGTH_SHORT).show()
                            return
                        } else {
                            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                grantedList.add(res)
                            } else {
                                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA), 101)
                            }
                        }
                    } else if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        if (!isAudioAllowed) {
                            request.deny()
                            Toast.makeText(this@MainActivity, "Izin Audio dinonaktifkan di config.json", Toast.LENGTH_SHORT).show()
                            return
                        } else {
                            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                grantedList.add(res)
                            } else {
                                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), 102)
                            }
                        }
                    }
                }

                if (grantedList.isNotEmpty()) {
                    request.grant(grantedList.toTypedArray())
                } else {
                    request.deny()
                }
            }

            // Geolocation Enforcement
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                if (!isLocationAllowed) {
                    // FORCED DENY
                    callback?.invoke(origin, false, false)
                    Toast.makeText(this@MainActivity, "Izin Lokasi dinonaktifkan di config.json", Toast.LENGTH_SHORT).show()
                } else {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        callback?.invoke(origin, true, false)
                    } else {
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 103)
                        callback?.invoke(origin, true, false)
                    }
                }
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }
}
        """.trimIndent()
    }

    /**
     * Generates build.gradle.kts with Keystore Alias "AndikUne".
     */
    fun generateGradleScript(config: WebToApkConfig): String {
        return """
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "${config.packageName}"
    compileSdk = 36

    defaultConfig {
        applicationId = "${config.packageName}"
        minSdk = 24
        targetSdk = 36
        versionCode = ${config.versionCode}
        versionName = "${config.versionName}"
    }

    // Keystore configuration for dev.andikune.iottools
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
            signingConfig = signingConfigs.getByName("andikuneRelease")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
        """.trimIndent()
    }

    /**
     * Generates Keystore setup and signing script instructions.
     */
    fun generateKeystoreScript(config: WebToApkConfig): String {
        return """
#!/bin/bash
# ==============================================================================
# PANDUAN SETUP KEYSTORE & SIGNING IDENTITAS: "AndikUne"
# Package Name: ${config.packageName}
# ==============================================================================

# 1. Generate Keystore Resmi AndikUne (RSA 2048, 10000 hari validity)
keytool -genkey -v \
  -keystore andikune-release.jks \
  -alias AndikUne \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Andik Une, OU=Dev14, O=AndikUne Dev, L=Jakarta, ST=DKI, C=ID" \
  -storepass AndikUne123456 \
  -keypass AndikUne123456

echo "[+] Keystore 'andikune-release.jks' berhasil dibuat dengan alias 'AndikUne'!"

# 2. Periksa fingerprint SHA-256 dan MD5 untuk memastikan konsistensi update:
keytool -list -v -keystore andikune-release.jks -alias AndikUne -storepass AndikUne123456

# 3. Compile dan Sign APK tanpa bentrok tanda tangan (Prevent 'App Not Installed'):
# ./gradlew assembleRelease
# apksigner sign --ks andikune-release.jks --ks-key-alias AndikUne --ks-pass pass:AndikUne123456 app-release-unsigned.apk
        """.trimIndent()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }

    private fun escapeForStringLiteral(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\$", "\\$")
            .replace("\n", "")
            .replace("\r", "")
    }
}
