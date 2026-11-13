package com.networkscanner.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedNeighborSourceParseTest {

    private val procArpHeader = "IP address       HW type     Flags       HW address            Mask     Device"

    @Test
    fun `proc arp row carries its mac`() {
        val entry = ArpReader.parseNeighborLine(
            "192.168.1.20     0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0"
        )!!
        assertEquals("192.168.1.20", entry.ipAddress)
        assertEquals("AA:BB:CC:DD:EE:FF", entry.normalizedMac)
        assertEquals("wlan0", entry.device)
        assertTrue(entry.isValid)
    }

    @Test
    fun `incomplete proc arp row is not valid`() {
        val entry = ArpReader.parseNeighborLine(
            "192.168.1.21     0x1         0x0         00:00:00:00:00:00     *        wlan0"
        )!!
        assertFalse(entry.isValid)
    }

    @Test
    fun `ip neigh line still goes to its own parser`() {
        val entry = ArpReader.parseNeighborLine("192.168.1.10 dev wlan0 lladdr aa:bb:cc:dd:ee:ff REACHABLE")!!
        assertEquals("AA:BB:CC:DD:EE:FF", entry.normalizedMac)
        assertTrue(entry.isValid)
    }

    @Test
    fun `denied ip neigh falls back to proc arp rows`() {
        val entries = PrivilegedNeighborSource.parse(
            listOf(
                "Cannot send dump request: Permission denied",
                procArpHeader,
                "192.168.1.1      0x1         0x2         11:22:33:44:55:66     *        wlan0",
                "192.168.1.20     0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0",
                "192.168.1.21     0x1         0x0         00:00:00:00:00:00     *        wlan0"
            )
        )
        assertEquals(listOf("192.168.1.1", "192.168.1.20"), entries.map { it.ipAddress })
        assertEquals("AA:BB:CC:DD:EE:FF", entries[1].normalizedMac)
    }

    @Test
    fun `both sources merge to one entry per ip`() {
        val entries = PrivilegedNeighborSource.parse(
            listOf(
                "192.168.1.1 dev wlan0 lladdr 11:22:33:44:55:66 REACHABLE",
                "192.168.1.30 dev wlan0  FAILED",
                procArpHeader,
                "192.168.1.1      0x1         0x2         11:22:33:44:55:66     *        wlan0",
                "192.168.1.40     0x1         0x2         de:ad:be:ef:00:01     *        wlan0"
            )
        )
        assertEquals(listOf("192.168.1.1", "192.168.1.40"), entries.map { it.ipAddress })
    }

    @Test
    fun `no usable lines yields nothing`() {
        assertTrue(PrivilegedNeighborSource.parse(listOf("", procArpHeader, "sh: ip: not found")).isEmpty())
    }
}
