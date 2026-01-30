package com.networkscanner.app.util

import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Reads ARP cache from /proc/net/arp to discover devices on the network.
 */
object ArpReader {

    private const val ARP_FILE = "/proc/net/arp"

    /**
     * Entry from the ARP cache.
     */
    data class ArpEntry(
        val ipAddress: String,
        val hwType: String,
        val flags: String,
        val macAddress: String,
        val mask: String,
        val device: String
    ) {
        /**
         * Check if this is a valid entry (not incomplete).
         */
        val isValid: Boolean
            get() = macAddress != "00:00:00:00:00:00" && flags != "0x0"

        /**
         * Get normalized MAC address (uppercase with colons).
         */
        val normalizedMac: String
            get() = macAddress.uppercase().replace("-", ":")
    }

    /**
     * Read all entries from the ARP cache.
     */
    fun readArpCache(): List<ArpEntry> {
        val entries = mutableListOf<ArpEntry>()

        try {
            val file = File(ARP_FILE)
            if (!file.exists() || !file.canRead()) {
                return entries
            }

            BufferedReader(FileReader(file)).use { reader ->
                // Skip header line
                reader.readLine()

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val entry = parseLine(line!!)
                    if (entry != null) {
                        entries.add(entry)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return entries
    }

    /**
     * Read only valid entries from the ARP cache.
     */
    fun readValidEntries(): List<ArpEntry> {
        return readArpCache().filter { it.isValid }
    }

    /**
     * Get MAC address for a specific IP address.
     */
    fun getMacForIp(ipAddress: String): String? {
        return readValidEntries().find { it.ipAddress == ipAddress }?.normalizedMac
    }

    /**
     * Get IP address for a specific MAC address.
     */
    fun getIpForMac(macAddress: String): String? {
        val normalizedMac = macAddress.uppercase().replace("-", ":")
        return readValidEntries().find { it.normalizedMac == normalizedMac }?.ipAddress
    }

    /**
     * Parse a single line from the ARP file.
     * Format: IP address       HW type     Flags       HW address            Mask     Device
     * Example: 192.168.1.1     0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0
     */
    private fun parseLine(line: String): ArpEntry? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 6) return null

        return try {
            ArpEntry(
                ipAddress = parts[0],
                hwType = parts[1],
                flags = parts[2],
                macAddress = parts[3],
                mask = parts[4],
                device = parts[5]
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clear the ARP cache entry for a specific IP (requires root).
     * This is typically not available on non-rooted devices.
     */
    fun clearArpEntry(ipAddress: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("ip neigh del $ipAddress dev wlan0")
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Refresh ARP entry by pinging the device.
     * This causes the system to update the ARP cache.
     */
    fun refreshArpEntry(ipAddress: String) {
        try {
            // A quick ping will refresh the ARP entry
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 $ipAddress")
            process.waitFor()
        } catch (e: Exception) {
            // Ignore errors
        }
    }
}
