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
          "versionName": "${escapeJson(config.versionName)}",
          "versionCode": ${config.versionCode},
          "themeColorHex": "${config.themeColorHex}",
          "navBarColorHex": "${config.navBarColorHex}",
          "enablePullToRefresh": ${config.enablePullToRefresh},
          "enableProgressBar": ${config.enableProgressBar},
          "desktopMode": ${config.enableDesktopMode},
          "zoomControls": ${config.enableZoomControls},
          "clearCacheOnExit": ${config.clearCacheOnExit},
          "userAgentOverride": "${escapeJson(config.userAgentOverride)}",
          "displayOptions": {
            "pullToRefresh": ${config.enablePullToRefresh},
            "progressBar": ${config.enableProgressBar},
            "desktopMode": ${config.enableDesktopMode},
            "zoomControls": ${config.enableZoomControls},
            "clearCacheOnExit": ${config.clearCacheOnExit}
          },
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
        if (config.permReadMediaImages || config.permReadMediaVideo || config.permReadMediaAudio) {
            permList.add("android.permission.READ_EXTERNAL_STORAGE")
        }
        // DownloadManager needs this on Android 7-9 to write into public Downloads.
        permList.add("android.permission.WRITE_EXTERNAL_STORAGE")

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

        val permissionTags = permList.joinToString("\n    ") { permission ->
            val maxSdk = when (permission) {
                "android.permission.WRITE_EXTERNAL_STORAGE" -> " android:maxSdkVersion=\"28\""
                "android.permission.READ_EXTERNAL_STORAGE" -> " android:maxSdkVersion=\"32\""
                else -> ""
            }
            """<uses-permission android:name="$permission"$maxSdk />"""
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
        android:allowBackup="false"
        android:icon="@drawable/ic_launcher"
        android:label="${escapeXml(config.appName)}"
        android:roundIcon="@drawable/ic_launcher"
        android:supportsRtl="true"
        android:usesCleartextTraffic="false"
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
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.View
import android.webkit.*
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONObject
import java.io.InputStream

/**
 * Generated by iD-Tools Web-to-APK Builder
 * Package: ${config.packageName}
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var progressBar: ProgressBar? = null
    private var swipeRefresh: SwipeRefreshLayout? = null
    private lateinit var config: JSONObject
    
    // Permission state cached from config.json
    private var isCameraAllowed = false
    private var isAudioAllowed = false
    private var isLocationAllowed = false
    private var isFineLocationAllowed = false
    private var isCoarseLocationAllowed = false
    private var isNotificationAllowed = false
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingWebPermissionResources: Array<String> = emptyArray()
    private var pendingGeolocationOrigin: String? = null
    private var pendingGeolocationCallback: GeolocationPermissions.Callback? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load configuration from assets/config.json
        loadConfigFromAssets()
        
        // Window UI setup
        window.statusBarColor = Color.parseColor("${config.themeColorHex}")
        window.navigationBarColor = Color.parseColor("${config.navBarColorHex}")

        setupFileChooser()
        setupLayout()
        configureWebView()
        setupDownloads()
        setupBackNavigation()
        requestConfiguredPermissions()
        
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
            val system = perms?.optJSONObject("system")
            
            // Check toggles strictly
            isCameraAllowed = media?.optBoolean("CAMERA", false) == true
            isAudioAllowed = media?.optBoolean("RECORD_AUDIO", false) == true
            isFineLocationAllowed = location?.optBoolean("ACCESS_FINE_LOCATION", false) == true
            isCoarseLocationAllowed = location?.optBoolean("ACCESS_COARSE_LOCATION", false) == true
            isLocationAllowed = isFineLocationAllowed || isCoarseLocationAllowed
            isNotificationAllowed = system?.optBoolean("POST_NOTIFICATIONS", false) == true
        } catch (e: Exception) {
            // Fallback default
            config = JSONObject()
            isCameraAllowed = ${config.permCamera}
            isAudioAllowed = ${config.permRecordAudio}
            isFineLocationAllowed = ${config.permFineLocation}
            isCoarseLocationAllowed = ${config.permCoarseLocation}
            isLocationAllowed = isFineLocationAllowed || isCoarseLocationAllowed
            isNotificationAllowed = ${config.permPostNotifications}
        }
    }

    private fun setupFileChooser() {
        fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback
            fileChooserCallback = null
            if (callback == null) return@registerForActivityResult
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data
                val uris = when {
                    data?.clipData != null -> {
                        val clip = data.clipData!!
                        Array(clip.itemCount) { clip.getItemAt(it).uri }
                    }
                    data?.data != null -> arrayOf(data.data!!)
                    else -> emptyArray()
                }
                callback.onReceiveValue(uris)
            } else {
                callback.onReceiveValue(null)
            }
        }
    }

    private fun requestConfiguredPermissions() {
        val wanted = mutableListOf<String>()
        if (isCameraAllowed) wanted.add(Manifest.permission.CAMERA)
        if (isAudioAllowed) wanted.add(Manifest.permission.RECORD_AUDIO)
        if (isFineLocationAllowed) wanted.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (isCoarseLocationAllowed) wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33 && isNotificationAllowed) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= 28) {
            wanted.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    private fun isSafeDownloadUrl(rawUrl: String): Boolean {
        val uri = runCatching { java.net.URI(rawUrl.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null) return false
        if (uri.port != -1 && uri.port !in 1..65535) return false
        return host != "localhost" && !host.endsWith(".local") &&
            !host.endsWith(".internal") && !host.endsWith(".lan") &&
            !host.endsWith(".home.arpa") && host.contains('.') &&
            !host.matches(Regex("^[0-9.]+$")) && !host.contains(':')
    }

    private fun sanitizeDownloadFileName(rawName: String): String =
        rawName.map { char ->
            if (char.isLetterOrDigit() || char in "._- ()") char else '_'
        }.joinToString("").trim().trim('.').take(180).ifBlank { "download.bin" }

    private fun setupDownloads() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (!isSafeDownloadUrl(url)) {
                Toast.makeText(this@MainActivity, "Unduhan hanya menerima URL HTTPS publik.", Toast.LENGTH_LONG).show()
                return@setDownloadListener
            }
            try {
                val fileName = sanitizeDownloadFileName(
                    URLUtil.guessFileName(url, contentDisposition, mimeType ?: "application/octet-stream")
                )
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType ?: "application/octet-stream")
                    if (userAgent.isNotBlank()) addRequestHeader("User-Agent", userAgent)
                    CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                    setTitle(fileName)
                    setDescription("Mengunduh dari aplikasi web")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "IO-Tools/${'$'}fileName")
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                Toast.makeText(this@MainActivity, "Mengunduh: ${'$'}fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Unduhan tidak dapat dimulai: ${'$'}{e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleExternalUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https") return false
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun setupLayout() {
        val rootLayout = android.widget.RelativeLayout(this).apply {
            setBackgroundColor(Color.parseColor("${config.navBarColorHex}"))
        }

        webView = WebView(this)
        val webParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
        )
        if (${config.enablePullToRefresh}) {
            swipeRefresh = SwipeRefreshLayout(this).apply {
                setOnRefreshListener { webView.reload() }
                addView(webView, android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }
            rootLayout.addView(swipeRefresh, webParams)
        } else {
            rootLayout.addView(webView, webParams)
        }

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
        // Keep JavaScript for web apps, but deny access to local file URLs.
        settings.allowFileAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.allowContentAccess = true // User-mediated file chooser only.
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        settings.setSupportZoom(${config.enableZoomControls})
        settings.builtInZoomControls = ${config.enableZoomControls}
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportMultipleWindows(false)
        settings.setJavaScriptCanOpenWindowsAutomatically(false)
        settings.setGeolocationEnabled(true)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        if (${config.enableDesktopMode}) {
            settings.userAgentString = settings.userAgentString.replace("Mobile", "eliboM")
        }
        val customUserAgent = "${escapeForStringLiteral(config.userAgentOverride)}"
        if (customUserAgent.isNotBlank()) {
            settings.userAgentString = customUserAgent
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return handleExternalUri(uri)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                swipeRefresh?.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    swipeRefresh?.isRefreshing = false
                    val offlineHtml = "${escapeForStringLiteral(config.offlineFallbackHtml)}"
                    view?.loadDataWithBaseURL(null, offlineHtml, "text/html", "UTF-8", null)
                }
            }
        }

        // Strict WebChromeClient enforcing config.json permissions
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar?.visibility = View.GONE
                    swipeRefresh?.isRefreshing = false
                } else {
                    progressBar?.visibility = View.VISIBLE
                    progressBar?.progress = newProgress
                }
            }

            // Pop-up windows are disabled to keep all content in the hardened WebView.
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean = false

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.fileChooserCallback?.onReceiveValue(null)
                this@MainActivity.fileChooserCallback = filePathCallback
                if (filePathCallback == null) return false
                return try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: Exception) {
                    this@MainActivity.fileChooserCallback = null
                    false
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(config.optString("appName", "Web App"))
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(config.optString("appName", "Web App"))
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setNegativeButton("Batal") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                val input = EditText(this@MainActivity).apply { setText(defaultValue.orEmpty()) }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(config.optString("appName", "Web App"))
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("OK") { _, _ -> result?.confirm(input.text.toString()) }
                    .setNegativeButton("Batal") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            // CRITICAL: WebRTC / Camera & Microphone Permission Enforcement
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val requestedResources = request.resources
                val requiredPermissions = mutableListOf<String>()
                for (resource in requestedResources) {
                    when (resource) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                            if (!isCameraAllowed) {
                                request.deny()
                                Toast.makeText(this@MainActivity, "Izin Kamera dinonaktifkan di config.json", Toast.LENGTH_SHORT).show()
                                return
                            }
                            requiredPermissions.add(Manifest.permission.CAMERA)
                        }
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                            if (!isAudioAllowed) {
                                request.deny()
                                Toast.makeText(this@MainActivity, "Izin mikrofon dinonaktifkan di config.json", Toast.LENGTH_SHORT).show()
                                return
                            }
                            requiredPermissions.add(Manifest.permission.RECORD_AUDIO)
                        }
                        else -> {
                            request.deny()
                            return
                        }
                    }
                }

                val missing = requiredPermissions.distinct().filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    request.grant(requestedResources)
                } else {
                    pendingWebPermissionRequest?.deny()
                    pendingWebPermissionRequest = request
                    pendingWebPermissionResources = requestedResources
                    ActivityCompat.requestPermissions(this@MainActivity, missing.toTypedArray(), 101)
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                if (pendingWebPermissionRequest === request) {
                    pendingWebPermissionRequest = null
                    pendingWebPermissionResources = emptyArray()
                }
                request?.deny()
            }

            // Geolocation is granted only after Android has granted a configured location permission.
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                val configured = buildList {
                    if (isFineLocationAllowed) add(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (isCoarseLocationAllowed) add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (!isLocationAllowed || configured.isEmpty()) {
                    callback?.invoke(origin, false, false)
                    Toast.makeText(this@MainActivity, "Izin Lokasi dinonaktifkan di config.json", Toast.LENGTH_SHORT).show()
                    return
                }
                if (configured.any {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED
                    }
                ) {
                    callback?.invoke(origin, true, false)
                    return
                }

                pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)
                pendingGeolocationOrigin = origin
                pendingGeolocationCallback = callback
                ActivityCompat.requestPermissions(this@MainActivity, configured.toTypedArray(), 103)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            101 -> {
                val request = pendingWebPermissionRequest
                val resources = pendingWebPermissionResources
                pendingWebPermissionRequest = null
                pendingWebPermissionResources = emptyArray()
                val granted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (granted && request != null && !isFinishing && !isDestroyed) {
                    request.grant(resources)
                } else {
                    request?.deny()
                }
            }
            103 -> {
                val origin = pendingGeolocationOrigin
                val callback = pendingGeolocationCallback
                pendingGeolocationOrigin = null
                pendingGeolocationCallback = null
                val granted = grantResults.isNotEmpty() &&
                    grantResults.any { it == PackageManager.PERMISSION_GRANTED }
                callback?.invoke(origin, granted && isLocationAllowed, false)
            }
        }
    }

    override fun onDestroy() {
        pendingWebPermissionRequest?.deny()
        pendingWebPermissionRequest = null
        pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)
        pendingGeolocationCallback = null
        if (${config.clearCacheOnExit}) {
            webView.clearCache(true)
            WebStorage.getInstance().deleteAllData()
        }
        webView.destroy()
        super.onDestroy()
    }
}
        """.trimIndent()
    }

    /**
     * Generates the same Gradle script used by the exported project.
     */
    fun generateGradleScript(config: WebToApkConfig): String {
        return """
plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "${config.packageName}"
    compileSdk = 35

    defaultConfig {
        applicationId = "${config.packageName}"
        minSdk = 24
        targetSdk = 35
        versionCode = ${config.versionCode}
        versionName = "${config.versionName}"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }

    /**
     * Generates Keystore setup and signing script instructions.
     */
    fun generateKeystoreScript(config: WebToApkConfig): String {
        return """
#!/bin/bash
# ==============================================================================
# PANDUAN SETUP KEYSTORE PRODUKSI
# Package Name: ${config.packageName}
# ==============================================================================
# Ganti nilai di bawah dengan password kuat milik Anda. Jangan commit keystore
# atau password ke repository publik.

keytool -genkey -v \
  -keystore release-keystore.jks \
  -alias release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Nama Anda, O=Organisasi, L=Kota, ST=Provinsi, C=ID"

# Build release (ubah signingConfig di app/build.gradle.kts ke keystore ini):
# gradle assembleRelease
# apksigner sign --ks release-keystore.jks --ks-key-alias release \
#   --out app-release-signed.apk app-release-unsigned.apk
        """.trimIndent()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeForStringLiteral(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\$", "\\$")
            .replace("\n", "")
            .replace("\r", "")
    }
}
