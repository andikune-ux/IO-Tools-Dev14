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
        assertEquals("Abcdefgh", TeraBoxDownloaderService.extractShareId("https://1024tera.com/surl=Abcdefgh"))
    }

    @Test
    fun rejectsUntrustedShareHostsAndSchemes() {
        assertNull(TeraBoxDownloaderService.extractShareId("https://terabox.com.evil.example/s/1Abcdefgh"))
        assertNull(TeraBoxDownloaderService.extractShareId("https://evil.example/s/1Abcdefgh"))
        assertNull(TeraBoxDownloaderService.extractShareId("ftp://terabox.com/s/1Abcdefgh"))
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
        assertNull(TeraBoxDownloaderService.normalizeResolverBaseUrl("javascript://my-worker.workers.dev"))
    }
}
