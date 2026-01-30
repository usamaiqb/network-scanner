package com.networkscanner.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.networkscanner.app.data.NetworkInfo
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Utility class for network-related operations.
 */
object NetworkUtils {

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
        val dhcpInfo = wifiManager.dhcpInfo ?: return null

        val ipAddress = intToIpAddress(dhcpInfo.ipAddress)
        val subnetMask = intToIpAddress(dhcpInfo.netmask)
        val gateway = intToIpAddress(dhcpInfo.gateway)

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
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
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
     */
    fun getIpRange(networkInfo: NetworkInfo): List<String> {
        val baseIp = networkInfo.networkAddress
        val parts = baseIp.split(".")
        if (parts.size != 4) return emptyList()

        val ipList = mutableListOf<String>()
        val numHosts = minOf(254, networkInfo.maxHosts)  // Limit to /24 equivalent for performance

        // For typical /24 networks
        if (networkInfo.networkPrefix >= 24) {
            val prefix = parts.take(3).joinToString(".")
            for (i in 1..254) {
                ipList.add("$prefix.$i")
            }
        } else {
            // For larger networks, still scan only first 254 addresses
            val prefix = parts.take(3).joinToString(".")
            for (i in 1..254) {
                ipList.add("$prefix.$i")
            }
        }

        return ipList
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
     * Check if an IP address is reachable.
     */
    suspend fun isReachable(ipAddress: String, timeoutMs: Int = 100): Pair<Boolean, Int?> {
        return try {
            val startTime = System.currentTimeMillis()
            val address = InetAddress.getByName(ipAddress)
            val reachable = address.isReachable(timeoutMs)
            val latency = if (reachable) (System.currentTimeMillis() - startTime).toInt() else null
            Pair(reachable, latency)
        } catch (e: Exception) {
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
}
