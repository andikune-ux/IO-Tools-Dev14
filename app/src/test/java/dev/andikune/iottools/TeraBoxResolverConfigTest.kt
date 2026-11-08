package dev.andikune.iottools

import dev.andikune.iottools.tools.terabox.TeraBoxDownloaderService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeraBoxResolverConfigTest {
    @Test
    fun extractsShareIdsFromSupportedDomains() {
        assertEquals("Abcdefgh", TeraBoxDownloaderService.extractShareId("https://terabox.com/s/1Abcdefgh"))
        assertEquals("Abcdefgh", TeraBoxDownloaderService.extractShareId("https://www.1024terabox.com/s/1Abcdefgh"))
        assertEquals("Abcdefgh", TeraBoxDownloaderService.extractShareId("https://1024tera.com/?surl=Abcdefgh"))
    }

    @Test
    fun rejectsUntrustedShareHostsAndSchemes() {
        assertNull(TeraBoxDownloaderService.extractShareId("https://terabox.com.evil.example/s/1Abcdefgh"))
        assertNull(TeraBoxDownloaderService.extractShareId("https://evil.example/s/1Abcdefgh"))
        assertNull(TeraBoxDownloaderService.extractShareId("ftp://terabox.com/s/1Abcdefgh"))
    }

    @Test
    fun onlyAcceptsDownloadUrlsFromConfiguredWorkerOrigin() {
        val base = "https://my-worker.workers.dev"
        org.junit.Assert.assertTrue(
            TeraBoxDownloaderService.isWorkerResourceUrl("https://my-worker.workers.dev/stream?link=abc", base)
        )
        org.junit.Assert.assertFalse(
            TeraBoxDownloaderService.isWorkerResourceUrl("https://evil.example/file.mp4", base)
        )
        org.junit.Assert.assertFalse(
            TeraBoxDownloaderService.isWorkerResourceUrl("http://my-worker.workers.dev/stream", base)
        )
        org.junit.Assert.assertFalse(
            TeraBoxDownloaderService.isWorkerResourceUrl("https://my-worker.workers.dev:444/stream", base)
        )
    }

    @Test
    fun requiresSecureWorkerEndpointWithoutCredentialsOrQuery() {
        assertEquals(
            "https://my-worker.workers.dev",
            TeraBoxDownloaderService.normalizeResolverBaseUrl("https://my-worker.workers.dev/")
        )
        assertNull(TeraBoxDownloaderService.normalizeResolverBaseUrl("http://my-worker.workers.dev"))
        assertNull(TeraBoxDownloaderService.normalizeResolverBaseUrl("https://user:pass@my-worker.workers.dev"))
        assertNull(TeraBoxDownloaderService.normalizeResolverBaseUrl("https://my-worker.workers.dev?key=secret"))
        assertNull(TeraBoxDownloaderService.normalizeResolverBaseUrl("https://127.0.0.1"))
        assertNull(TeraBoxDownloaderService.normalizeResolverBaseUrl("javascript://my-worker.workers.dev"))
    }
}
