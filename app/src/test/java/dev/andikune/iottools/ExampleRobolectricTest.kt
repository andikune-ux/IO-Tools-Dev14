package dev.andikune.iottools

import dev.andikune.iottools.builder.WebToApkConfig
import dev.andikune.iottools.builder.WebToApkPipeline
import dev.andikune.iottools.tools.instagram.InstagramDownloaderService
import dev.andikune.iottools.tools.terabox.TeraBoxDownloaderService
import dev.andikune.iottools.tools.youtube.YouTubeDownloaderService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

  @Test
  fun testStandaloneMainActivityGeneration() {
    val config = WebToApkConfig(
      appName = "MB Sabang",
      packageName = "dev.andikune.iottools.mbsabang",
      webUrl = "https://mbsabang.com",
      permCamera = true,
      permRecordAudio = true
    )
    val activityCode = WebToApkPipeline.generateStandaloneMainActivity(config)
    assertTrue(activityCode.contains("package dev.andikune.iottools.mbsabang"))
    assertTrue(activityCode.contains("class MainActivity : ComponentActivity()"))
    assertTrue(activityCode.contains("https://mbsabang.com"))
  }

  @Test
  fun testYouTubeIdExtractor() {
    val id1 = YouTubeDownloaderService.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    assertEquals("dQw4w9WgXcQ", id1)

    val id2 = YouTubeDownloaderService.extractVideoId("https://youtu.be/dQw4w9WgXcQ")
    assertEquals("dQw4w9WgXcQ", id2)

    val id3 = YouTubeDownloaderService.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ?si=abcdef")
    assertEquals("dQw4w9WgXcQ", id3)
  }

  @Test
  fun testInstagramShortcodeExtractor() {
    val code1 = InstagramDownloaderService.extractShortcode("https://www.instagram.com/reel/C3_example123/?igsh=xyz")
    assertEquals("C3_example123", code1)

    val code2 = InstagramDownloaderService.extractShortcode("https://www.instagram.com/p/DF_testPhoto456/")
    assertEquals("DF_testPhoto456", code2)
  }

  @Test
  fun testTeraBoxShareIdExtractor() {
    val id1 = TeraBoxDownloaderService.extractShareId("https://terabox.com/s/1aB2cD3eF4g")
    assertNotNull(id1)

    val id2 = TeraBoxDownloaderService.extractShareId("https://1024tera.com/s/1xyz9876543")
    assertNotNull(id2)
  }
}
