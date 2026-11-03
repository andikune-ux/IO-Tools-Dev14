package dev.andikune.iottools.builder

/**
 * Configuration data model for the Web-to-APK Builder.
 * Serialized to `config.json` inside the built APK assets.
 */
data class WebToApkConfig(
    // App Basics
    val appName: String = "My Web App",
    val webUrl: String = "https://example.com",
    val packageName: String = "dev.andikune.iottools",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    
    // UI & Styling
    val themeColorHex: String = "#FF003C",
    val navBarColorHex: String = "#07070A",
    val enablePullToRefresh: Boolean = true,
    val enableProgressBar: Boolean = true,
    val enableDesktopMode: Boolean = false,
    val enableZoomControls: Boolean = true,
    val clearCacheOnExit: Boolean = false,
    val userAgentOverride: String = "",
    val offlineFallbackHtml: String = defaultOfflineHtml,

    // Granular Permissions Toggle System (Media)
    val permCamera: Boolean = false,
    val permRecordAudio: Boolean = false,
    val permReadMediaImages: Boolean = false,
    val permReadMediaVideo: Boolean = false,
    val permReadMediaAudio: Boolean = false,

    // Lokasi
    val permCoarseLocation: Boolean = false,
    val permFineLocation: Boolean = false,

    // Jaringan
    val permInternet: Boolean = true, // Default ON for WebView
    val permAccessNetworkState: Boolean = true,
    val permAccessWifiState: Boolean = false,
    val permBluetoothConnect: Boolean = false,
    val permNfc: Boolean = false,

    // Sistem
    val permPostNotifications: Boolean = false,
    val permVibrate: Boolean = true,
    val permWakeLock: Boolean = false,
    val permHighSamplingRateSensors: Boolean = false,

    // Biometrik
    val permUseBiometric: Boolean = false
) {
    companion object {
        val defaultOfflineHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { background: #07070A; color: #E2E8F0; font-family: monospace; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 90vh; text-align: center; padding: 20px; }
                    h1 { color: #FF003C; text-shadow: 0 0 10px #FF003C; font-size: 24px; }
                    p { color: #94A3B8; font-size: 14px; }
                    .box { border: 1px solid #FF003C; padding: 20px; border-radius: 8px; box-shadow: 0 0 15px rgba(255,0,60,0.3); max-width: 400px; }
                    button { background: #FF003C; color: #000; border: none; padding: 10px 20px; font-weight: bold; border-radius: 4px; cursor: pointer; margin-top: 15px; font-family: monospace; }
                </style>
            </head>
            <body>
                <div class="box">
                    <h1>[OFFLINE DETECTED]</h1>
                    <p>Sambungan internet terputus atau URL tidak dapat diakses.</p>
                    <button onclick="location.reload()">COBA LAGI</button>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
