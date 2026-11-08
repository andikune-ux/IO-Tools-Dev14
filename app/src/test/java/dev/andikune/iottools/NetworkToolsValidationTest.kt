package dev.andikune.iottools

import dev.andikune.iottools.tools.network.normalizeNetworkHost
import dev.andikune.iottools.tools.network.normalizeWhoisDomain
import dev.andikune.iottools.tools.network.parseSslTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkToolsValidationTest {
    @Test
    fun normalizesDnsAndNetworkHosts() {
        assertEquals("example.com", normalizeNetworkHost("HTTPS://Example.COM/path"))
        assertEquals("xn--bcher-kva.example", normalizeNetworkHost("bücher.example"))
        assertEquals("1.1.1.1", normalizeNetworkHost("1.1.1.1"))
        assertNull(normalizeNetworkHost("ftp://example.com"))
        assertNull(normalizeNetworkHost("https://user:pass@example.com"))
        assertNull(normalizeNetworkHost("example.com\r\nwhois.iana.org"))
    }

    @Test
    fun restrictsWhoisToValidDomainNames() {
        assertEquals("example.com", normalizeWhoisDomain("https://example.com/path"))
        assertNull(normalizeWhoisDomain("localhost"))
        assertNull(normalizeWhoisDomain("192.168.1.1"))
        assertNull(normalizeWhoisDomain("example.com\r\ncommand"))
    }

    @Test
    fun parsesTlsHostAndPortSafely() {
        assertEquals("example.com", parseSslTarget("example.com:8443")?.host)
        assertEquals(8443, parseSslTarget("example.com:8443")?.port)
        assertEquals(443, parseSslTarget("https://example.com/path")?.port)
        assertNull(parseSslTarget("example.com:70000"))
        assertNull(parseSslTarget("example.com:abc"))
        assertNull(parseSslTarget("https://user@example.com"))
    }
}
