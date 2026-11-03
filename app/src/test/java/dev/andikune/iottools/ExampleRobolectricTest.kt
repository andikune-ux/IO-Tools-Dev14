package dev.andikune.iottools

import dev.andikune.iottools.builder.WebToApkConfig
import dev.andikune.iottools.builder.WebToApkPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

  @Test
  fun testConfigJsonGeneration() {
    val config = WebToApkConfig(
      appName = "MB Sabang",
      packageName = "dev.andikune.iottools.mbsabang",
      webUrl = "https://mbsabang.com",
      permCamera = true,
      permInternet = true
    )
    val json = WebToApkPipeline.generateConfigJson(config)
    assertTrue(json.contains("\"appName\": \"MB Sabang\""))
    assertTrue(json.contains("\"CAMERA\": true"))
    assertTrue(json.contains("\"INTERNET\": true"))
  }

  @Test
  fun testManifestGeneration() {
    val config = WebToApkConfig(
      appName = "MB Sabang",
      packageName = "dev.andikune.iottools.mbsabang",
      webUrl = "https://mbsabang.com",
      permCamera = true
    )
    val manifest = WebToApkPipeline.generatePatchedManifest(config)
    assertTrue(manifest.contains("android.permission.CAMERA"))
    assertTrue(manifest.contains("dev.andikune.iottools.mbsabang"))
  }
}
