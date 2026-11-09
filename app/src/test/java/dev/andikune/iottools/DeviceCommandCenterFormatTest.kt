package dev.andikune.iottools

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCommandCenterFormatTest {
    @Test
    fun formatsByteValuesWithoutLosingMagnitude() {
        assertEquals("0.0 B", dashboardFormatBytes(0))
        assertEquals("1.0 KB", dashboardFormatBytes(1024))
        assertEquals("1.0 MB", dashboardFormatBytes(1024L * 1024L))
    }

    @Test
    fun mapsOpenMeteoWeatherCodesToIndonesianDescriptions() {
        assertEquals("Cerah", dashboardWeatherDescription(0))
        assertEquals("Mendung", dashboardWeatherDescription(3))
        assertEquals("Hujan", dashboardWeatherDescription(63))
        assertEquals("Badai petir", dashboardWeatherDescription(95))
        assertEquals("Kondisi tidak diketahui", dashboardWeatherDescription(-1))
    }
}
