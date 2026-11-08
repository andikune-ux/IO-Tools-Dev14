package dev.andikune.iottools

import dev.andikune.iottools.builder.ApkPackagingEngine
import dev.andikune.iottools.builder.WebToApkConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkPackagingValidationTest {
    @Test
    fun acceptsValidPublicHttpsConfiguration() {
        assertTrue(runCatching { ApkPackagingEngine.validate(WebToApkConfig()) }.isSuccess)
    }

    @Test
    fun rejectsInsecureOrLocalWebTargets() {
        listOf(
            "http://example.com",
            "https://localhost",
            "https://127.0.0.1",
            "https://192.168.1.20",
            "https://user:pass@example.com",
            "https://example.com:70000",
            "https://bad..example.com",
            "javascript:alert(1)",
            "file:///sdcard/index.html"
        ).forEach { url ->
            assertFalse(url, runCatching {
                ApkPackagingEngine.validate(WebToApkConfig(webUrl = url))
            }.isSuccess)
        }
    }

    @Test
    fun rejectsInvalidPackageNamesVersionsAndColors() {
        assertFalse(runCatching {
            ApkPackagingEngine.validate(WebToApkConfig(packageName = "com.class.app"))
        }.isSuccess)
        assertFalse(runCatching {
            ApkPackagingEngine.validate(WebToApkConfig(versionCode = 0))
        }.isSuccess)
        assertFalse(runCatching {
            ApkPackagingEngine.validate(WebToApkConfig(themeColorHex = "red"))
        }.isSuccess)
        assertFalse(runCatching {
            ApkPackagingEngine.validate(WebToApkConfig(appName = ""))
        }.isSuccess)
    }
}
