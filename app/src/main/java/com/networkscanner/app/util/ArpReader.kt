package com.networkscanner.app.util

import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Reads ARP cache from /proc/net/arp to discover devices on the network.
 */
object ArpReader {

    private const val ARP_FILE = "/proc/net/arp"

    // Cached ARP entries to avoid re-reading the file hundreds of times per scan
    @Volatile
    private var cachedEntries: List<ArpEntry> = emptyList()
    @Volatile
    private var cacheTimestamp: Long = 0
    private const val CACHE_TTL_MS = 2000L // Cache valid for 2 seconds

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
     * Uses a short-lived cache to avoid re-reading the file excessively during scans.
     */
    fun readArpCache(): List<ArpEntry> {
        val now = System.currentTimeMillis()
        if (now - cacheTimestamp < CACHE_TTL_MS && cachedEntries.isNotEmpty()) {
            return cachedEntries
        }

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

        cachedEntries = entries
        cacheTimestamp = now
        return entries
    }

    /**
     * Read only valid entries from the ARP cache.
     */
    fun readValidEntries(): List<ArpEntry> {
        return readArpCache().filter { it.isValid }
    }

    /**
     * Invalidate the cache, forcing the next read to hit the file.
     */
    fun invalidateCache() {
        cacheTimestamp = 0
        cachedEntries = emptyList()
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
        if (!isValidIpAddress(ipAddress)) return false
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("ip", "neigh", "del", ipAddress, "dev", "wlan0"))
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
        if (!isValidIpAddress(ipAddress)) return
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", "1", "-W", "1", ipAddress))
            process.waitFor()
            invalidateCache()
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    /**
     * Validate that a string is a valid IPv4 address to prevent command injection.
     */
    private fun isValidIpAddress(ip: String): Boolean {
        return ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) &&
                ip.split(".").all { part ->
                    val num = part.toIntOrNull() ?: return false
                    num in 0..255
                }
    }
}
