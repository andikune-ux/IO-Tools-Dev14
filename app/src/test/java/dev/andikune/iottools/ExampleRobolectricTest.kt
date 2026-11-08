package dev.andikune.iottools

import dev.andikune.iottools.builder.WebToApkConfig
import dev.andikune.iottools.builder.WebToApkPipeline
import dev.andikune.iottools.tools.instagram.InstagramDownloaderService
import dev.andikune.iottools.tools.pdf.PdfEngine
import dev.andikune.iottools.tools.terabox.TeraBoxDownloaderService
import dev.andikune.iottools.tools.youtube.YouTubeDownloaderService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    assertEquals("aB2cD3eF4g", id1)

    val id2 = TeraBoxDownloaderService.extractShareId("https://1024tera.com/s/1xyz9876543")
    assertEquals("xyz9876543", id2)
  }

  @Test
  fun testVideoDownloadHelperValidation() {
    val helper = dev.andikune.iottools.tools.common.VideoDownloadHelper
    assertNotNull(helper)
    assertNotNull(helper.DEFAULT_USER_AGENT)
    assertTrue(helper.DEFAULT_USER_AGENT.contains("Android"))
  }
  @Test
  fun generatedWebToApkUsesSecureWebViewDefaults() {
    val config = WebToApkConfig(
      appName = "Secure Sample",
      packageName = "dev.andikune.securesample",
      webUrl = "https://example.com"
    )
    val manifest = WebToApkPipeline.generatePatchedManifest(config)
    val activity = WebToApkPipeline.generateStandaloneMainActivity(config)

    assertTrue(manifest.contains("android:allowBackup=\"false\""))
    assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
    assertTrue(activity.contains("settings.allowFileAccess = false"))
    assertTrue(activity.contains("settings.allowUniversalAccessFromFileURLs = false"))
    assertTrue(activity.contains("WebSettings.MIXED_CONTENT_NEVER_ALLOW"))
    assertTrue(activity.contains("settings.safeBrowsingEnabled = true"))
    assertTrue(activity.contains("setAcceptThirdPartyCookies(webView, false)"))
  }

  @Test
  fun mediaLinkExtractorsRejectUntrustedHosts() {
    assertNull(YouTubeDownloaderService.extractVideoId("https://youtube.com.evil.test/watch?v=dQw4w9WgXcQ"))
    assertNull(InstagramDownloaderService.extractShortcode("https://instagram.com.evil.test/reel/ABC123/"))
    assertNull(TeraBoxDownloaderService.extractShareId("https://terabox.com.evil.test/s/1abcdefg"))
  }

  @Test
  fun pdfPageRangeParserHandlesRangesAndRejectsInvalidInput() {
    assertEquals(listOf(0..0, 2..4, 6..6), PdfEngine.parseRanges("1,3-5,7", 7))
    assertEquals((0..2).toList().map { it..it }, PdfEngine.parseRanges("", 3))
    assertThrows(IllegalArgumentException::class.java) {
      PdfEngine.parseRanges("0-2", 5)
    }
    assertThrows(IllegalArgumentException::class.java) {
      PdfEngine.parseRanges("4-2", 5)
    }
    assertThrows(IllegalArgumentException::class.java) {
      PdfEngine.parseRanges("1,8", 5)
    }
  }

  @Test
  fun mediaExtractorsAcceptSupportedUrlFormsAndRejectInvalidSchemes() {
    assertEquals("dQw4w9WgXcQ", YouTubeDownloaderService.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    assertNull(YouTubeDownloaderService.extractVideoId("javascript:alert(1)"))
    assertNull(InstagramDownloaderService.extractShortcode("ftp://instagram.com/p/ABC123"))
    assertNull(TeraBoxDownloaderService.extractShareId("https://example.com/s/1abcdefg"))
  }

}
