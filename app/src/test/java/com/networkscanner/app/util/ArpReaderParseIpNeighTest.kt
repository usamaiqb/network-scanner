package com.networkscanner.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArpReaderParseIpNeighTest {

    @Test
    fun `reachable entry carries its mac`() {
        val entry = ArpReader.parseIpNeighLine("192.168.1.10 dev wlan0 lladdr aa:bb:cc:dd:ee:ff REACHABLE")!!
        assertEquals("192.168.1.10", entry.ipAddress)
        assertEquals("AA:BB:CC:DD:EE:FF", entry.normalizedMac)
        assertEquals("wlan0", entry.device)
        assertTrue(entry.isValid)
    }

    @Test
    fun `stale entry is still valid`() {
        assertTrue(ArpReader.parseIpNeighLine("192.168.1.11 dev wlan0 lladdr 00:11:22:33:44:55 STALE")!!.isValid)
    }

    @Test
    fun `failed entry has no usable mac`() {
        assertFalse(ArpReader.parseIpNeighLine("192.168.1.12 dev wlan0  FAILED")!!.isValid)
    }

    @Test
    fun `short lines are rejected`() {
        assertNull(ArpReader.parseIpNeighLine(""))
        assertNull(ArpReader.parseIpNeighLine("192.168.1.13 dev"))
    }

    @Test
    fun `locally administered bit marks randomized macs`() {
        assertTrue(NetworkUtils.isLocallyAdministeredMac("5A:11:22:33:44:55"))
        assertTrue(NetworkUtils.isLocallyAdministeredMac("02:00:00:00:00:00"))
        assertFalse(NetworkUtils.isLocallyAdministeredMac("58:11:22:33:44:55"))
        assertFalse(NetworkUtils.isLocallyAdministeredMac(null))
    }
}
