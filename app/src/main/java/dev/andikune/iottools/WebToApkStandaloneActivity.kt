package dev.andikune.iottools

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
import dev.andikune.iottools.builder.WebToApkConfig
import org.json.JSONObject
import java.io.InputStream

/**
 * Standalone Fullscreen Web Application Container.
 * Runs the generated Web-to-APK directly as an installed app instance.
 */
class WebToApkStandaloneActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var progressBar: ProgressBar? = null
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var config = WebToApkConfig()

    private var isCameraAllowed = false
    private var isAudioAllowed = false
    private var isLocationAllowed = false
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingWebPermissionResources: Array<String> = emptyArray()
    private var pendingGeolocationOrigin: String? = null
    private var pendingGeolocationCallback: GeolocationPermissions.Callback? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load configuration from intent extras or fallback to assets/config.json
        loadConfiguration()

        window.statusBarColor = try {
            Color.parseColor(config.themeColorHex)
        } catch (_: Exception) {
            Color.parseColor("#101726")
        }
        window.navigationBarColor = try {
            Color.parseColor(config.navBarColorHex)
        } catch (_: Exception) {
            Color.parseColor("#090D16")
        }

        setupFileChooser()
        setupLayout()
        configureWebView()
        setupDownloads()
        setupBackNavigation()
        requestConfiguredPermissions()

        webView.loadUrl(config.webUrl)
    }

    private fun loadConfiguration() {
        val urlExtra = intent.getStringExtra("EXTRA_WEB_URL")
        val appNameExtra = intent.getStringExtra("EXTRA_APP_NAME")
        val themeColorExtra = intent.getStringExtra("EXTRA_THEME_COLOR")
        val navColorExtra = intent.getStringExtra("EXTRA_NAV_COLOR")
        val permCameraExtra = intent.getBooleanExtra("EXTRA_PERM_CAMERA", false)
        val permAudioExtra = intent.getBooleanExtra("EXTRA_PERM_AUDIO", false)
        val permLocationExtra = intent.getBooleanExtra("EXTRA_PERM_LOCATION", false)
        val permFineLocationExtra = intent.getBooleanExtra("EXTRA_PERM_FINE_LOCATION", permLocationExtra)
        val permCoarseLocationExtra = intent.getBooleanExtra("EXTRA_PERM_COARSE_LOCATION", permLocationExtra)
        val pullToRefreshExtra = intent.getBooleanExtra("EXTRA_PULL_REFRESH", true)
        val progressBarExtra = intent.getBooleanExtra("EXTRA_PROGRESS_BAR", true)
        val desktopModeExtra = intent.getBooleanExtra("EXTRA_DESKTOP_MODE", false)
        val zoomExtra = intent.getBooleanExtra("EXTRA_ZOOM", true)
        val clearCacheExtra = intent.getBooleanExtra("EXTRA_CLEAR_CACHE", false)
        val userAgentExtra = intent.getStringExtra("EXTRA_USER_AGENT").orEmpty()

        if (urlExtra != null) {
            config = WebToApkConfig(
                appName = appNameExtra ?: "My Web App",
                webUrl = urlExtra,
                themeColorHex = themeColorExtra ?: "#6366F1",
                navBarColorHex = navColorExtra ?: "#090D16",
                permCamera = permCameraExtra,
                permRecordAudio = permAudioExtra,
                permFineLocation = permFineLocationExtra,
                permCoarseLocation = permCoarseLocationExtra,
                enablePullToRefresh = pullToRefreshExtra,
                enableProgressBar = progressBarExtra,
                enableDesktopMode = desktopModeExtra,
                enableZoomControls = zoomExtra,
                clearCacheOnExit = clearCacheExtra,
                userAgentOverride = userAgentExtra
            )
            isCameraAllowed = permCameraExtra
            isAudioAllowed = permAudioExtra
            isLocationAllowed = permFineLocationExtra || permCoarseLocationExtra
        } else {
            // First try loading from WebToApkStorage (actively configured by user)
            val savedConfig = dev.andikune.iottools.builder.WebToApkStorage.getActiveConfig(this)
            if (savedConfig != null) {
                config = savedConfig
                isCameraAllowed = savedConfig.permCamera
                isAudioAllowed = savedConfig.permRecordAudio
                isLocationAllowed = savedConfig.permFineLocation || savedConfig.permCoarseLocation
            } else {
                // Fallback to assets/config.json
                try {
                    val inputStream: InputStream = assets.open("config.json")
                    val jsonStr = inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonStr)
                    val perms = json.optJSONObject("permissions")
                    val media = perms?.optJSONObject("media")
                    val location = perms?.optJSONObject("location")

                    isCameraAllowed = media?.optBoolean("CAMERA", false) == true
                    isAudioAllowed = media?.optBoolean("RECORD_AUDIO", false) == true
                    val fineLocationAllowed = location?.optBoolean("ACCESS_FINE_LOCATION", false) == true
                    val coarseLocationAllowed = location?.optBoolean("ACCESS_COARSE_LOCATION", false) == true
                    isLocationAllowed = fineLocationAllowed || coarseLocationAllowed

                    config = WebToApkConfig(
                        appName = json.optString("appName", "My Web App"),
                        webUrl = json.optString("webUrl", "https://example.com"),
                        themeColorHex = json.optString("themeColorHex", "#6366F1"),
                        navBarColorHex = json.optString("navBarColorHex", "#090D16"),
                        permCamera = isCameraAllowed,
                        permRecordAudio = isAudioAllowed,
                        permFineLocation = fineLocationAllowed,
                        permCoarseLocation = coarseLocationAllowed,
                        enablePullToRefresh = json.optBoolean("enablePullToRefresh", true),
                        enableProgressBar = json.optBoolean("enableProgressBar", true),
                        enableDesktopMode = json.optBoolean("desktopMode", false),
                        enableZoomControls = json.optBoolean("zoomControls", true),
                        clearCacheOnExit = json.optBoolean("clearCacheOnExit", false),
                        userAgentOverride = json.optString("userAgentOverride", "")
                    )
                } catch (_: Exception) {}
            }
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
        if (config.permFineLocation) wanted.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (config.permCoarseLocation) wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31 && config.permBluetoothConnect) {
            wanted.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= 33 && config.permPostNotifications) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= 28) {
            wanted.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 200)
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
                Toast.makeText(this, "Unduhan hanya menerima URL HTTPS publik.", Toast.LENGTH_LONG).show()
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
                    setDescription("Mengunduh dari ${config.appName}")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "IO-Tools/$fileName")
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this, "Mengunduh: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(this, "Unduhan tidak dapat dimulai: ${e.message}", Toast.LENGTH_LONG).show()
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
            setBackgroundColor(Color.parseColor(config.navBarColorHex))
        }

        webView = WebView(this)
        val webParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
        )
        if (config.enablePullToRefresh) {
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

        if (config.enableProgressBar) {
            progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = false
                max = 100
                progressDrawable.setColorFilter(
                    Color.parseColor(config.themeColorHex),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
            val progressParams = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT, 8
            ).apply { addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP) }
            rootLayout.addView(progressBar, progressParams)
        }

        setContentView(rootLayout)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Web apps need JavaScript, but must not gain local-file privileges.
        settings.allowFileAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.allowContentAccess = true // Required for the user-mediated file picker.
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        settings.setSupportZoom(config.enableZoomControls)
        settings.builtInZoomControls = config.enableZoomControls
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportMultipleWindows(false)
        settings.setJavaScriptCanOpenWindowsAutomatically(false)
        settings.setGeolocationEnabled(true)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        if (config.enableDesktopMode) {
            settings.userAgentString = settings.userAgentString.replace("Mobile", "eliboM")
        }
        if (config.userAgentOverride.isNotBlank()) {
            settings.userAgentString = config.userAgentOverride
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)
                pendingGeolocationOrigin = null
                pendingGeolocationCallback = null
                super.onPageStarted(view, url, favicon)
            }

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
                    view?.loadDataWithBaseURL(null, config.offlineFallbackHtml, "text/html", "UTF-8", null)
                }
            }
        }

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

            // Pop-up windows are disabled to prevent unmanaged WebViews.
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
                this@WebToApkStandaloneActivity.fileChooserCallback?.onReceiveValue(null)
                this@WebToApkStandaloneActivity.fileChooserCallback = filePathCallback
                if (filePathCallback == null) return false
                return try {
                    val picker = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    fileChooserLauncher.launch(picker)
                    true
                } catch (_: Exception) {
                    this@WebToApkStandaloneActivity.fileChooserCallback = null
                    false
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@WebToApkStandaloneActivity)
                    .setTitle(config.appName)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@WebToApkStandaloneActivity)
                    .setTitle(config.appName)
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
                val input = EditText(this@WebToApkStandaloneActivity).apply { setText(defaultValue.orEmpty()) }
                AlertDialog.Builder(this@WebToApkStandaloneActivity)
                    .setTitle(config.appName)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("OK") { _, _ -> result?.confirm(input.text.toString()) }
                    .setNegativeButton("Batal") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val requestedResources = request.resources
                val requiredPermissions = mutableListOf<String>()
                for (resource in requestedResources) {
                    when (resource) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                            if (!isCameraAllowed) {
                                request.deny()
                                Toast.makeText(this@WebToApkStandaloneActivity, "Izin Kamera dinonaktifkan di konfigurasi.", Toast.LENGTH_SHORT).show()
                                return
                            }
                            requiredPermissions.add(Manifest.permission.CAMERA)
                        }
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                            if (!isAudioAllowed) {
                                request.deny()
                                Toast.makeText(this@WebToApkStandaloneActivity, "Izin mikrofon dinonaktifkan di konfigurasi.", Toast.LENGTH_SHORT).show()
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
                    ContextCompat.checkSelfPermission(this@WebToApkStandaloneActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    request.grant(requestedResources)
                } else {
                    pendingWebPermissionRequest?.deny()
                    pendingWebPermissionRequest = request
                    pendingWebPermissionResources = requestedResources
                    ActivityCompat.requestPermissions(this@WebToApkStandaloneActivity, missing.toTypedArray(), 201)
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                if (pendingWebPermissionRequest === request) {
                    pendingWebPermissionRequest = null
                    pendingWebPermissionResources = emptyArray()
                }
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                val configured = buildList {
                    if (config.permFineLocation) add(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (config.permCoarseLocation) add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (!isLocationAllowed || configured.isEmpty()) {
                    callback?.invoke(origin, false, false)
                    return
                }
                if (configured.any {
                        ContextCompat.checkSelfPermission(this@WebToApkStandaloneActivity, it) == PackageManager.PERMISSION_GRANTED
                    }
                ) {
                    callback?.invoke(origin, true, false)
                    return
                }

                pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)
                pendingGeolocationOrigin = origin
                pendingGeolocationCallback = callback
                ActivityCompat.requestPermissions(this@WebToApkStandaloneActivity, configured.toTypedArray(), 203)
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
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
            201 -> {
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
            203 -> {
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
        if (config.clearCacheOnExit) {
            webView.clearCache(true)
            WebStorage.getInstance().deleteAllData()
        }
        webView.destroy()
        super.onDestroy()
    }
}
