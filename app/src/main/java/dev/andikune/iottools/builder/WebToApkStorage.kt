package dev.andikune.iottools.builder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Storage manager for Web-to-APK configurations.
 * Saves active and historical Web-to-APK projects so they can be launched
 * directly as standalone native apps or managed anytime.
 */
object WebToApkStorage {

    private const val PREFS_NAME = "web_to_apk_prefs"
    private const val KEY_ACTIVE_CONFIG = "key_active_config"
    private const val KEY_SAVED_APPS = "key_saved_apps"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves the currently active configuration.
     */
    fun saveActiveConfig(context: Context, config: WebToApkConfig) {
        val jsonStr = WebToApkPipeline.generateConfigJson(config)
        getPrefs(context).edit().putString(KEY_ACTIVE_CONFIG, jsonStr).apply()
        saveAppToHistory(context, config)
    }

    /**
     * Retrieves the currently active configuration.
     */
    fun getActiveConfig(context: Context): WebToApkConfig? {
        val jsonStr = getPrefs(context).getString(KEY_ACTIVE_CONFIG, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val perms = json.optJSONObject("permissions")
            val media = perms?.optJSONObject("media")
            val location = perms?.optJSONObject("location")
            val network = perms?.optJSONObject("network")
            val system = perms?.optJSONObject("system")
            val biometric = perms?.optJSONObject("biometric")
            val display = json.optJSONObject("displayOptions")

            WebToApkConfig(
                appName = json.optString("appName", "My Web App"),
                packageName = json.optString("packageName", "dev.andikune.iottools.webapp"),
                webUrl = json.optString("webUrl", "https://google.com"),
                versionName = json.optString("versionName", "1.0.0"),
                themeColorHex = json.optString("themeColorHex", "#6366F1"),
                navBarColorHex = json.optString("navBarColorHex", "#090D16"),
                permCamera = media?.optBoolean("CAMERA", false) == true,
                permRecordAudio = media?.optBoolean("RECORD_AUDIO", false) == true,
                permReadMediaImages = media?.optBoolean("READ_MEDIA_IMAGES", false) == true,
                permReadMediaVideo = media?.optBoolean("READ_MEDIA_VIDEO", false) == true,
                permReadMediaAudio = media?.optBoolean("READ_MEDIA_AUDIO", false) == true,
                permCoarseLocation = location?.optBoolean("ACCESS_COARSE_LOCATION", false) == true,
                permFineLocation = location?.optBoolean("ACCESS_FINE_LOCATION", false) == true,
                permInternet = network?.optBoolean("INTERNET", true) ?: true,
                permAccessNetworkState = network?.optBoolean("ACCESS_NETWORK_STATE", true) ?: true,
                permAccessWifiState = network?.optBoolean("ACCESS_WIFI_STATE", true) ?: true,
                permBluetoothConnect = network?.optBoolean("BLUETOOTH_CONNECT", false) == true,
                permNfc = network?.optBoolean("NFC", false) == true,
                permPostNotifications = system?.optBoolean("POST_NOTIFICATIONS", true) ?: true,
                permVibrate = system?.optBoolean("VIBRATE", true) ?: true,
                permWakeLock = system?.optBoolean("WAKE_LOCK", false) == true,
                permHighSamplingRateSensors = system?.optBoolean("HIGH_SAMPLING_RATE_SENSORS", false) == true,
                permUseBiometric = biometric?.optBoolean("USE_BIOMETRIC", false) == true,
                enablePullToRefresh = display?.optBoolean("pullToRefresh", json.optBoolean("enablePullToRefresh", true)) ?: true,
                enableProgressBar = display?.optBoolean("progressBar", json.optBoolean("enableProgressBar", true)) ?: true,
                enableDesktopMode = display?.optBoolean("desktopMode", json.optBoolean("desktopMode", false)) == true,
                enableZoomControls = display?.optBoolean("zoomControls", json.optBoolean("zoomControls", true)) ?: true,
                clearCacheOnExit = display?.optBoolean("clearCacheOnExit", json.optBoolean("clearCacheOnExit", false)) == true,
                userAgentOverride = json.optString("userAgentOverride", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Stores the app in history list.
     */
    private fun saveAppToHistory(context: Context, config: WebToApkConfig) {
        try {
            val prefs = getPrefs(context)
            val historyJson = prefs.getString(KEY_SAVED_APPS, "[]") ?: "[]"
            val array = JSONArray(historyJson)

            // Avoid duplicate by appName
            val list = mutableListOf<JSONObject>()
            val newObj = JSONObject(WebToApkPipeline.generateConfigJson(config))
            list.add(newObj)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (item.optString("appName") != config.appName && list.size < 10) {
                    list.add(item)
                }
            }

            val finalArray = JSONArray()
            list.forEach { finalArray.put(it) }
            prefs.edit().putString(KEY_SAVED_APPS, finalArray.toString()).apply()
        } catch (_: Exception) {}
    }
}
