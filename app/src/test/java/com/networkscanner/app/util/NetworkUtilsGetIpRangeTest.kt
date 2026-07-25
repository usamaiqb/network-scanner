package com.networkscanner.app.util

import com.networkscanner.app.data.NetworkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [NetworkUtils.getIpRange].
 *
 * Regression coverage for issue #18: subnets smaller than /24 (e.g. /23) were
 * collapsed to the device's own /24, so hosts — including a gateway in the
 * sibling /24 — were never scanned.
 */
class NetworkUtilsGetIpRangeTest {

    /** Build a NetworkInfo with a subnet mask derived from the prefix. */
    private fun netInfo(ip: String, prefix: Int): NetworkInfo = NetworkInfo(
        interfaceName = "wlan0",
        ssid = null,
        bssid = null,
        ipAddress = ip,
        subnetMask = maskFor(prefix),
        gateway = null,
        networkPrefix = prefix
    )

    private fun maskFor(prefix: Int): String {
        if (prefix <= 0) return "0.0.0.0"
        if (prefix >= 32) return "255.255.255.255"
        val mask = -1 shl (32 - prefix)
        return "${(mask ushr 24) and 0xFF}.${(mask ushr 16) and 0xFF}." +
            "${(mask ushr 8) and 0xFF}.${mask and 0xFF}"
    }

    @Test
    fun slash24_scansFullSingleSubnet() {
        val range = NetworkUtils.getIpRange(netInfo("192.168.1.50", 24))
        assertEquals(254, range.size)
        assertEquals("192.168.1.1", range.first())
        assertEquals("192.168.1.254", range.last())
    }

    @Test
    fun slash23_scansBothHalvesIncludingSiblingSubnet() {
        // The reporter's core case: device in the upper /24, gateway in the lower.
        val range = NetworkUtils.getIpRange(netInfo("10.0.1.50", 23))
        assertEquals(510, range.size)
        assertEquals("10.0.0.1", range.first())
        assertEquals("10.0.1.254", range.last())
        // A gateway at 10.0.0.1 (sibling /24) must be covered — this regressed before.
        assertTrue("10.0.0.1" in range)
        assertTrue("10.0.1.1" in range)
    }

    @Test
    fun slash22_atCap_scansFull() {
        val range = NetworkUtils.getIpRange(netInfo("172.16.5.10", 22))
        assertEquals(1022, range.size)
        assertEquals("172.16.4.1", range.first())
        assertEquals("172.16.7.254", range.last())
    }

    @Test
    fun slash21_overCap_fallsBackToDeviceLocalSlash24() {
        val range = NetworkUtils.getIpRange(netInfo("172.16.20.5", 21))
        assertEquals(254, range.size)
        assertEquals("172.16.20.1", range.first())
        assertEquals("172.16.20.254", range.last())
    }

    @Test
    fun slash8_fallsBackToDeviceLocalSlash24_notMillions() {
        val range = NetworkUtils.getIpRange(netInfo("10.5.3.20", 8))
        assertEquals(254, range.size)
        assertEquals("10.5.3.1", range.first())
        assertEquals("10.5.3.254", range.last())
        // Must NOT attempt to materialize the full /8.
        assertTrue(range.size <= NetworkUtils.MAX_SCAN_HOSTS)
    }

    @Test
    fun slash30_scansTwoUsableHosts() {
        val range = NetworkUtils.getIpRange(netInfo("192.168.1.5", 30))
        assertEquals(2, range.size)
        assertEquals("192.168.1.5", range.first())
        assertEquals("192.168.1.6", range.last())
    }

    @Test
    fun slash32_scansOnlyTheHost() {
        val range = NetworkUtils.getIpRange(netInfo("192.168.1.5", 32))
        assertEquals(listOf("192.168.1.5"), range)
    }

    @Test
    fun slash31_scansOnlyTheHost() {
        val range = NetworkUtils.getIpRange(netInfo("192.168.1.4", 31))
        assertEquals(listOf("192.168.1.4"), range)
    }

    @Test
    fun neverExceedsScanCap() {
        for (prefix in 8..30) {
            val size = NetworkUtils.getIpRange(netInfo("10.20.30.40", prefix)).size
            assertTrue(
                "prefix /$prefix produced $size addresses",
                size <= NetworkUtils.MAX_SCAN_HOSTS
            )
        }
    }

    @Test
    fun malformedIp_returnsEmptyGracefully() {
        // A malformed interface address can't be scanned — return empty, don't crash.
        val range = NetworkUtils.getIpRange(netInfo("not.an.ip", 24))
        assertTrue(range.isEmpty())
    }
}
