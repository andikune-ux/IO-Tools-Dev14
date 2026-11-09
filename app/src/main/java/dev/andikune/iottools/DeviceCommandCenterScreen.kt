package dev.andikune.iottools

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.NetworkInterface
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

private val DashboardCard = Color(0xFF121D2D)
private val DashboardBorder = Color(0xFF263B55)
private val DashboardCyan = Color(0xFF65D5F5)
private val DashboardGreen = Color(0xFF63D6A3)
private val DashboardAmber = Color(0xFFFFC46B)
private val DashboardPurple = Color(0xFFB9A0FF)

private data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val securityPatch: String,
    val kernel: String,
    val cpuCores: Int,
    val cpuArchitecture: String,
    val cpuMaxFrequency: String,
    val totalRam: Long,
    val availableRam: Long,
    val totalStorage: Long,
    val availableStorage: Long,
    val batteryPercent: Int,
    val batteryTemperature: String,
    val charging: Boolean,
    val screenResolution: String,
    val density: String,
    val refreshRate: String,
    val networkType: String,
    val localIp: String,
    val uptime: String
)

private data class WeatherSnapshot(
    val city: String,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val description: String,
    val isDay: Boolean
)

private val weatherClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .callTimeout(15, TimeUnit.SECONDS)
    .build()

@Composable
fun DeviceCommandCenterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(readDeviceSnapshot(context)) }
    var city by remember { mutableStateOf("Jakarta") }
    var weather by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var weatherLoading by remember { mutableStateOf(false) }
    var weatherError by remember { mutableStateOf<String?>(null) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    suspend fun loadWeather() {
        weatherLoading = true
        weatherError = null
        val result = withContext(Dispatchers.IO) { fetchWeather(city.trim()) }
        result.onSuccess { weather = it }
            .onFailure { weatherError = it.message ?: "Cuaca belum dapat dimuat." }
        weatherLoading = false
    }

    LaunchedEffect(Unit) {
        loadWeather()
    }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            snapshot = readDeviceSnapshot(context)
            delay(5_000)
        }
    }

    val dateText = remember(currentTime) {
        java.text.SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(java.util.Date(currentTime))
    }
    val timeText = remember(currentTime) {
        java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(currentTime))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0A1422), Color(0xFF080D16), Color(0xFF0C1725)))
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(DashboardGreen, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("DEVICE COMMAND CENTER", color = DashboardGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
                Text("Selamat datang", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Ringkasan perangkat dan kondisi terkini", color = Color(0xFF9BAEC3), fontSize = 13.sp)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF17283C)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, DashboardBorder, RoundedCornerShape(22.dp))
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dateText.uppercase(Locale("id", "ID")), color = Color(0xFFA8BCD0), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(timeText, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("Waktu perangkat", color = Color(0xFF9BAEC3), fontSize = 12.sp)
                    }
                    Box(Modifier.size(58.dp).background(Color(0xFF213B53), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Smartphone, null, tint = DashboardCyan, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14283A)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF31516B), RoundedCornerShape(20.dp))
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (weather?.isDay == false) Icons.Default.CloudQueue else Icons.Default.WbSunny, null, tint = DashboardAmber)
                        Spacer(Modifier.width(8.dp))
                        Text("CUACA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        if (weatherLoading) CircularProgressIndicator(Modifier.size(18.dp), color = DashboardCyan, strokeWidth = 2.dp)
                        else Button(
                            onClick = { scope.launch { loadWeather() } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24435C)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Muat ulang", fontSize = 11.sp)
                        }
                    }
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Kota untuk prakiraan cuaca") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = DashboardCyan, unfocusedBorderColor = DashboardBorder,
                            focusedLabelColor = DashboardCyan, unfocusedLabelColor = Color(0xFF9BAEC3)
                        )
                    )
                    Button(
                        onClick = { scope.launch { loadWeather() } },
                        enabled = !weatherLoading && city.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF286080)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cari cuaca") }
                    if (weather != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(weather!!.city, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(weather!!.description, color = Color(0xFFB5C8D9), fontSize = 13.sp)
                            }
                            Text(String.format(Locale.getDefault(), "%.1f°", weather!!.temperature), color = Color.White, fontSize = 35.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            WeatherMetric("Terasa", String.format(Locale.getDefault(), "%.1f°", weather!!.feelsLike))
                            WeatherMetric("Lembap", "${weather!!.humidity}%")
                            WeatherMetric("Angin", String.format(Locale.getDefault(), "%.1f km/j", weather!!.windSpeed))
                        }
                    } else if (weatherError != null) {
                        Text(weatherError!!, color = Color(0xFFFFB4A9), fontSize = 12.sp)
                    } else if (!weatherLoading) {
                        Text("Masukkan kota untuk memuat prakiraan cuaca.", color = Color(0xFF9BAEC3), fontSize = 12.sp)
                    }
                    Text("Sumber: Open-Meteo • lokasi kota diisi manual, tanpa izin lokasi perangkat", color = Color(0xFF8197AC), fontSize = 10.sp)
                }
            }
        }
        item {
            SectionHeading("STATUS PERANGKAT", "Diperbarui otomatis setiap 5 detik")
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(226.dp),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { MetricCard("Baterai", "${snapshot.batteryPercent}%", if (snapshot.charging) "Sedang mengisi" else snapshot.batteryTemperature, Icons.Default.BatteryChargingFull, DashboardGreen) }
                item { MetricCard("RAM tersedia", dashboardFormatBytes(snapshot.availableRam), "Dari ${dashboardFormatBytes(snapshot.totalRam)}", Icons.Default.Memory, DashboardPurple) }
                item { MetricCard("Penyimpanan kosong", dashboardFormatBytes(snapshot.availableStorage), "Dari ${dashboardFormatBytes(snapshot.totalStorage)}", Icons.Default.SdStorage, DashboardAmber) }
                item { MetricCard("Jaringan", snapshot.networkType, snapshot.localIp, Icons.Default.NetworkCheck, DashboardCyan) }
            }
        }
        item {
            SectionHeading("IDENTITAS PERANGKAT", "Informasi yang disediakan Android")
            InfoCard {
                InfoRow("Pabrikan", snapshot.manufacturer)
                InfoRow("Model", snapshot.model)
                InfoRow("Android", "Android ${snapshot.androidVersion} (API ${snapshot.apiLevel})")
                InfoRow("Security patch", snapshot.securityPatch)
                InfoRow("Kernel", snapshot.kernel)
                InfoRow("CPU", "${snapshot.cpuCores} core • ${snapshot.cpuArchitecture}")
                InfoRow("Frekuensi CPU maksimum", snapshot.cpuMaxFrequency)
                InfoRow("Resolusi layar", snapshot.screenResolution)
                InfoRow("Kepadatan layar", snapshot.density)
                InfoRow("Refresh rate", snapshot.refreshRate)
                InfoRow("Uptime", snapshot.uptime)
            }
        }
        item {
            Text("Data perangkat dibaca lokal dari Android. Sebagian informasi hardware dapat dibatasi oleh vendor atau versi Android.", color = Color(0xFF8296AA), fontSize = 11.sp)
        }
    }
}

@Composable
private fun WeatherMetric(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF9BAEC3), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(subtitle, color = Color(0xFF8296AA), fontSize = 11.sp)
    }
}

@Composable
private fun MetricCard(title: String, value: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DashboardCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxSize().border(1.dp, DashboardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Column {
                Text(title, color = Color(0xFF9BAEC3), fontSize = 10.sp)
                Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = Color(0xFF8296AA), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DashboardCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, DashboardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), content = content)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = Color(0xFF9BAEC3), fontSize = 12.sp, modifier = Modifier.weight(0.9f))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun readDeviceSnapshot(context: Context): DeviceSnapshot {
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 0
    val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    val stat = StatFs(Environment.getDataDirectory().absolutePath)
    val totalStorage = stat.totalBytes
    val availableStorage = stat.availableBytes
    val metrics = context.resources.displayMetrics
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
    val networkType = when {
        capabilities == null -> "Offline"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Seluler"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "Terhubung"
    }
    val ip = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
            ?.hostAddress ?: "Tidak tersedia"
    }.getOrDefault("Tidak tersedia")
    val maxFrequency = runCatching {
        File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000
    }.getOrNull()?.let { "${it} MHz" } ?: "Tidak tersedia"
    val uptimeMinutes = android.os.SystemClock.elapsedRealtime() / 60_000
    val uptime = "${uptimeMinutes / 1440}h ${(uptimeMinutes % 1440) / 60}j ${uptimeMinutes % 60}m"

    return DeviceSnapshot(
        manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() },
        model = Build.MODEL.orEmpty(),
        androidVersion = Build.VERSION.RELEASE.orEmpty(),
        apiLevel = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH ?: "Tidak tersedia",
        kernel = System.getProperty("os.version") ?: "Tidak tersedia",
        cpuCores = Runtime.getRuntime().availableProcessors(),
        cpuArchitecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "Tidak tersedia",
        cpuMaxFrequency = maxFrequency,
        totalRam = memoryInfo.totalMem,
        availableRam = memoryInfo.availMem,
        totalStorage = totalStorage,
        availableStorage = availableStorage,
        batteryPercent = batteryPercent,
        batteryTemperature = if (tempTenths >= 0) String.format(Locale.getDefault(), "%.1f°C", tempTenths / 10f) else "Suhu tidak tersedia",
        charging = charging,
        screenResolution = "${metrics.widthPixels} × ${metrics.heightPixels}",
        density = String.format(Locale.getDefault(), "%.1f dpi", metrics.densityDpi.toFloat()),
        refreshRate = runCatching {
            @Suppress("DEPRECATION")
            val display = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
            String.format(Locale.getDefault(), "%.0f Hz", display.refreshRate)
        }.getOrDefault("Tidak tersedia"),
        networkType = networkType,
        localIp = ip,
        uptime = uptime
    )
}

internal fun dashboardFormatBytes(bytes: Long): String {
    if (bytes < 0) return "Tidak tersedia"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

private suspend fun fetchWeather(city: String): Result<WeatherSnapshot> = runCatching {
    require(city.isNotBlank()) { "Nama kota wajib diisi." }
    val encodedCity = URLEncoder.encode(city, "UTF-8")
    val geocodeUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=id&format=json"
    val geocode = executeJson(geocodeUrl)
    val place = geocode.optJSONArray("results")?.optJSONObject(0)
        ?: error("Kota tidak ditemukan. Coba nama kota lain.")
    val latitude = place.getDouble("latitude")
    val longitude = place.getDouble("longitude")
    val displayCity = listOfNotNull(place.optString("name").takeIf { it.isNotBlank() }, place.optString("admin1").takeIf { it.isNotBlank() }).distinct().joinToString(", ")
    val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m&timezone=auto"
    val current = executeJson(forecastUrl).getJSONObject("current")
    WeatherSnapshot(
        city = displayCity.ifBlank { city },
        temperature = current.getDouble("temperature_2m"),
        feelsLike = current.getDouble("apparent_temperature"),
        humidity = current.getInt("relative_humidity_2m"),
        windSpeed = current.getDouble("wind_speed_10m"),
        description = dashboardWeatherDescription(current.getInt("weather_code")),
        isDay = current.optInt("is_day", 1) == 1
    )
}

private fun executeJson(url: String): JSONObject {
    val request = Request.Builder().url(url).header("Accept", "application/json").build()
    weatherClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("Layanan cuaca merespons HTTP ${response.code}.")
        val body = response.body?.string()?.takeIf { it.length <= 512_000 }
            ?: error("Respons cuaca kosong atau terlalu besar.")
        return JSONObject(body)
    }
}

internal fun dashboardWeatherDescription(code: Int): String = when (code) {
    0 -> "Cerah"
    1 -> "Cerah berawan"
    2 -> "Berawan sebagian"
    3 -> "Mendung"
    45, 48 -> "Berkabut"
    51, 53, 55 -> "Gerimis"
    56, 57 -> "Gerimis membeku"
    61, 63, 65 -> "Hujan"
    66, 67 -> "Hujan membeku"
    71, 73, 75, 77 -> "Salju"
    80, 81, 82 -> "Hujan lokal"
    85, 86 -> "Hujan salju"
    95 -> "Badai petir"
    96, 99 -> "Badai petir dan hujan es"
    else -> "Kondisi tidak diketahui"
}
