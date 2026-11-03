package dev.andikune.iottools

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private var config = WebToApkConfig()

    private var isCameraAllowed = false
    private var isAudioAllowed = false
    private var isLocationAllowed = false

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

        setupLayout()
        configureWebView()
        setupBackNavigation()

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
        val pullToRefreshExtra = intent.getBooleanExtra("EXTRA_PULL_REFRESH", true)
        val progressBarExtra = intent.getBooleanExtra("EXTRA_PROGRESS_BAR", true)
        val desktopModeExtra = intent.getBooleanExtra("EXTRA_DESKTOP_MODE", false)
        val zoomExtra = intent.getBooleanExtra("EXTRA_ZOOM", true)

        if (urlExtra != null) {
            config = WebToApkConfig(
                appName = appNameExtra ?: "My Web App",
                webUrl = urlExtra,
                themeColorHex = themeColorExtra ?: "#6366F1",
                navBarColorHex = navColorExtra ?: "#090D16",
                permCamera = permCameraExtra,
                permRecordAudio = permAudioExtra,
                permFineLocation = permLocationExtra,
                permCoarseLocation = permLocationExtra,
                enablePullToRefresh = pullToRefreshExtra,
                enableProgressBar = progressBarExtra,
                enableDesktopMode = desktopModeExtra,
                enableZoomControls = zoomExtra
            )
            isCameraAllowed = permCameraExtra
            isAudioAllowed = permAudioExtra
            isLocationAllowed = permLocationExtra
        } else {
            // Load from assets/config.json
            try {
                val inputStream: InputStream = assets.open("config.json")
                val jsonStr = inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val perms = json.optJSONObject("permissions")
                val media = perms?.optJSONObject("media")
                val location = perms?.optJSONObject("location")

                isCameraAllowed = media?.optBoolean("CAMERA", false) == true
                isAudioAllowed = media?.optBoolean("RECORD_AUDIO", false) == true
                isLocationAllowed = location?.optBoolean("ACCESS_FINE_LOCATION", false) == true

                config = WebToApkConfig(
                    appName = json.optString("appName", "My Web App"),
                    webUrl = json.optString("webUrl", "https://example.com"),
                    themeColorHex = json.optString("themeColorHex", "#6366F1"),
                    navBarColorHex = json.optString("navBarColorHex", "#090D16"),
                    permCamera = isCameraAllowed,
                    permRecordAudio = isAudioAllowed,
                    permFineLocation = isLocationAllowed,
                    enablePullToRefresh = json.optBoolean("enablePullToRefresh", true),
                    enableProgressBar = json.optBoolean("enableProgressBar", true),
                    enableDesktopMode = json.optBoolean("enableDesktopMode", false),
                    enableZoomControls = json.optBoolean("enableZoomControls", true)
                )
            } catch (_: Exception) {}
        }
    }

    private fun setupLayout() {
        val rootLayout = android.widget.RelativeLayout(this).apply {
            setBackgroundColor(Color.parseColor(config.navBarColorHex))
        }

        webView = WebView(this)
        rootLayout.addView(
            webView,
            android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
            )
        )

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
        settings.setSupportZoom(config.enableZoomControls)
        settings.builtInZoomControls = config.enableZoomControls
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        if (config.enableDesktopMode) {
            settings.userAgentString = settings.userAgentString.replace("Mobile", "eliboM")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                view?.loadDataWithBaseURL(null, config.offlineFallbackHtml, "text/html", "UTF-8", null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) progressBar?.visibility = View.GONE
                else {
                    progressBar?.visibility = View.VISIBLE
                    progressBar?.progress = newProgress
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val granted = mutableListOf<String>()

                for (res in request.resources) {
                    if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                        if (!isCameraAllowed) {
                            request.deny()
                            Toast.makeText(this@WebToApkStandaloneActivity, "Izin Kamera dinonaktifkan di konfigurasi", Toast.LENGTH_SHORT).show()
                            return
                        } else {
                            if (ContextCompat.checkSelfPermission(this@WebToApkStandaloneActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                granted.add(res)
                            } else {
                                ActivityCompat.requestPermissions(this@WebToApkStandaloneActivity, arrayOf(Manifest.permission.CAMERA), 201)
                            }
                        }
                    } else if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        if (!isAudioAllowed) {
                            request.deny()
                            return
                        } else {
                            if (ContextCompat.checkSelfPermission(this@WebToApkStandaloneActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                granted.add(res)
                            } else {
                                ActivityCompat.requestPermissions(this@WebToApkStandaloneActivity, arrayOf(Manifest.permission.RECORD_AUDIO), 202)
                            }
                        }
                    }
                }

                if (granted.isNotEmpty()) request.grant(granted.toTypedArray()) else request.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                if (!isLocationAllowed) {
                    callback?.invoke(origin, false, false)
                } else {
                    if (ContextCompat.checkSelfPermission(this@WebToApkStandaloneActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        callback?.invoke(origin, true, false)
                    } else {
                        ActivityCompat.requestPermissions(this@WebToApkStandaloneActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 203)
                        callback?.invoke(origin, true, false)
                    }
                }
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
}
