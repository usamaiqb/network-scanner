package com.networkscanner.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.networkscanner.app.data.NetworkInfo
import kotlinx.coroutines.*
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Utility class for network-related operations.
 */
object NetworkUtils {

    private val IP_PATTERN = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")

    /**
     * Check if device is connected to WiFi.
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Get current WiFi information.
     */
    @Suppress("DEPRECATION")
    fun getWifiInfo(context: Context): WifiInfo? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.connectionInfo
    }

    /**
     * Get complete network information.
     */
    fun getNetworkInfo(context: Context): NetworkInfo? {
        if (!isWifiConnected(context)) return null

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo ?: return null
        @Suppress("DEPRECATION")
        val dhcpInfo = wifiManager.dhcpInfo

        // Get IP address from DHCP info, fallback to NetworkInterface if 0 or null
        var ipAddress = if (dhcpInfo != null && dhcpInfo.ipAddress != 0) {
            intToIpAddress(dhcpInfo.ipAddress)
        } else {
            getLocalIpAddress() ?: return null
        }

        // Validate IP address is not 0.0.0.0
        if (ipAddress == "0.0.0.0") {
            ipAddress = getLocalIpAddress() ?: return null
        }

        val subnetMask = if (dhcpInfo != null && dhcpInfo.netmask != 0) {
            intToIpAddress(dhcpInfo.netmask)
        } else {
            "255.255.255.0" // Default to /24
        }

        val gateway = if (dhcpInfo != null && dhcpInfo.gateway != 0) {
            intToIpAddress(dhcpInfo.gateway)
        } else {
            // Attempt to derive gateway from IP (common pattern: x.x.x.1)
            val parts = ipAddress.split(".")
            if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.1" else null
        }

        val networkPrefix = calculateNetworkPrefix(subnetMask)

        return NetworkInfo(
            ssid = getSSID(context),
            bssid = wifiInfo.bssid,
            ipAddress = ipAddress,
            subnetMask = subnetMask,
            gateway = gateway,
            networkPrefix = networkPrefix,
            frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) wifiInfo.frequency else null,
            linkSpeed = wifiInfo.linkSpeed,
            signalStrength = wifiInfo.rssi
        )
    }

    /**
     * Get WiFi SSID.
     */
    @Suppress("DEPRECATION")
    fun getSSID(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        var ssid = wifiInfo?.ssid
        // Remove quotes if present
        if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        return if (ssid == "<unknown ssid>") null else ssid
    }

    /**
     * Get device's IP address on the local network.
     * Prefers the WiFi (wlan0) interface to avoid returning VPN or cellular IPs.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()

            // First pass: prefer wlan0 (WiFi interface)
            var fallbackAddress: String? = null
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        if (networkInterface.name.equals("wlan0", ignoreCase = true)) {
                            return address.hostAddress
                        }
                        if (fallbackAddress == null) {
                            fallbackAddress = address.hostAddress
                        }
                    }
                }
            }

            return fallbackAddress
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Get local device's MAC address.
     */
    fun getLocalMacAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.name.equals("wlan0", ignoreCase = true)) {
                    val mac = networkInterface.hardwareAddress ?: return null
                    return mac.joinToString(":") { String.format("%02X", it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Generate list of IP addresses in the subnet to scan.
     * Respects the actual subnet size instead of always scanning /24.
     */
    fun getIpRange(networkInfo: NetworkInfo): List<String> {
        val baseIp = networkInfo.networkAddress
        val parts = baseIp.split(".")
        if (parts.size != 4) return emptyList()

        val prefix = networkInfo.networkPrefix

        // For /24 or smaller subnets, scan the exact range
        if (prefix >= 24) {
            val ipPrefix = parts.take(3).joinToString(".")
            val hostBits = 32 - prefix
            val numHosts = (1 shl hostBits) - 2 // Exclude network and broadcast
            val startHost = 1
            val endHost = startHost + numHosts - 1

            return (startHost..endHost).map { "$ipPrefix.$it" }
        }

        // For larger subnets (< /24), cap at 254 hosts for performance
        val ipPrefix = parts.take(3).joinToString(".")
        return (1..254).map { "$ipPrefix.$it" }
    }

    /**
     * Convert integer IP address to string format.
     */
    fun intToIpAddress(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }

    /**
     * Convert string IP address to integer format.
     */
    fun ipAddressToInt(ip: String): Int {
        val parts = ip.split(".")
        if (parts.size != 4) return 0
        return parts.mapIndexed { index, part ->
            (part.toIntOrNull() ?: 0) shl (8 * index)
        }.sum()
    }

    /**
     * Calculate network prefix length from subnet mask.
     */
    fun calculateNetworkPrefix(subnetMask: String): Int {
        val parts = subnetMask.split(".")
        if (parts.size != 4) return 24

        var prefix = 0
        for (part in parts) {
            val value = part.toIntOrNull() ?: 0
            prefix += Integer.bitCount(value)
        }
        return prefix
    }

    /**
     * Check if an IP address is reachable using ping first, then TCP port probing as fallback.
     * This handles devices that block ICMP (like Windows laptops with firewall).
     */
    suspend fun isReachable(ipAddress: String, timeoutMs: Int = 1000): Pair<Boolean, Int?> {
        if (!isValidIpAddress(ipAddress)) return Pair(false, null)

        val startTime = System.currentTimeMillis()
        val timeoutSec = maxOf(1, timeoutMs / 1000)

        // Method 1: Try ping first (fastest for responsive devices)
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("/system/bin/ping", "-c", "1", "-W", "$timeoutSec", ipAddress)
            )
            val reachable = process.waitFor(timeoutMs.toLong() + 500, TimeUnit.MILLISECONDS)
                    && process.exitValue() == 0
            process.destroyForcibly()
            if (reachable) {
                val latency = (System.currentTimeMillis() - startTime).toInt()
                return Pair(true, latency)
            }
        } catch (e: Exception) {
            // Continue to TCP probe
        }

        // Method 2: TCP port probe in parallel for devices that block ping
        val commonPorts = intArrayOf(445, 139, 22, 80, 443, 8080, 5000, 3389, 62078)
        return withContext(Dispatchers.IO) {
            val result = commonPorts.map { port ->
                async {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(ipAddress, port), 200)
                            true
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            // Return as soon as any port responds
            for (deferred in result) {
                if (deferred.await()) {
                    result.forEach { it.cancel() }
                    val latency = (System.currentTimeMillis() - startTime).toInt()
                    return@withContext Pair(true, latency)
                }
            }
            Pair(false, null)
        }
    }

    /**
     * Resolve hostname for an IP address.
     */
    fun resolveHostname(ipAddress: String): String? {
        return try {
            val address = InetAddress.getByName(ipAddress)
            val hostname = address.canonicalHostName
            if (hostname != ipAddress) hostname else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validate that a string is a valid IPv4 address.
     */
    fun isValidIpAddress(ip: String): Boolean {
        return ip.matches(IP_PATTERN) &&
                ip.split(".").all { part ->
                    val num = part.toIntOrNull() ?: return false
                    num in 0..255
                }
    }
}
