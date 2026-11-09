package dev.andikune.iottools.tools.linkbypass

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkBypassResolverTest {
    @Test
    fun acceptsOnlySafeHttpAndHttpsUrls() {
        assertEquals("https://example.com/path?q=1", normalizeHttpUrl("https://Example.com/path?q=1"))
        assertEquals("http://example.com/", normalizeHttpUrl("http://example.com"))
        assertNull(normalizeHttpUrl("javascript:alert(1)"))
        assertNull(normalizeHttpUrl("file:///etc/passwd"))
        assertNull(normalizeHttpUrl("https://user:pass@example.com"))
        assertNull(normalizeHttpUrl("ftp://example.com/file"))
        assertNull(normalizeHttpUrl("https://example.com:8443"))
        assertNull(normalizeHttpUrl("https://127.0.0.1/"))
        assertNull(normalizeHttpUrl("http://10.0.0.1/"))
        assertNull(normalizeHttpUrl("http://192.168.1.1/"))
        assertNull(normalizeHttpUrl("http://[::1]/"))
        assertNull(normalizeHttpUrl("http://169.254.169.254/latest/meta-data/"))
    }

    @Test
    fun permitsPublicIpLiterals() {
        assertEquals("https://8.8.8.8/", normalizeHttpUrl("https://8.8.8.8/"))
        assertTrue(isPublicDestinationAddress(InetAddress.getByName("1.1.1.1")))
        assertTrue(isPublicDestinationAddress(InetAddress.getByName("2001:4860:4860::8888")))
    }

    @Test
    fun blocksPrivateReservedAndSpecialAddresses() {
        listOf(
            "0.0.0.0", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.1.1",
            "172.16.0.1", "192.168.1.1", "198.18.0.1", "224.0.0.1",
            "::", "::1", "fc00::1", "fe80::1", "2001:db8::1", "::ffff:127.0.0.1"
        ).forEach { assertFalse("$it must be blocked", isPublicDestinationAddress(InetAddress.getByName(it))) }
    }

    @Test
    fun extractsKnownRedirectQueryParameters() {
        assertEquals(
            "https://destination.example/file",
            extractRedirectParameter("https://short.example/go?url=https%3A%2F%2Fdestination.example%2Ffile")
        )
        assertNull(extractRedirectParameter("https://short.example/go?campaign=spring"))
        assertNull(extractRedirectParameter("https://short.example/go?url=javascript%3Aalert(1)"))
    }

    @Test
    fun followsMetaRefreshTargetsOnlyWhenHttpBased() {
        val html = """<html><head><meta http-equiv="refresh" content="0; url=https://destination.example/final"></head></html>"""
        assertEquals("https://destination.example/final", extractMetaRefresh(html, "https://short.example/start"))
        val unsafe = """<meta http-equiv="refresh" content="0; url=javascript:alert(1)">"""
        assertNull(extractMetaRefresh(unsafe, "https://short.example/"))
        assertNull(extractMetaRefresh("<html>no redirect</html>", "https://short.example/"))
    }

    @Test
    fun enforcesRedirectAndResponseLimits() {
        assertEquals(10, MAX_REDIRECTS)
        assertEquals(512 * 1024, MAX_RESPONSE_BYTES)
    }
}
