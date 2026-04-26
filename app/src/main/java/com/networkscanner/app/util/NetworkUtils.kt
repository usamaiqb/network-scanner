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
/**
 * Result of a ping/reachability check.
 */
data class PingResult(
    val reachable: Boolean,
    val latencyMs: Int? = null,
    val ttl: Int? = null
)

data class NetworkInterfaceOption(
    val name: String,
    val ipAddress: String,
    val type: InterfaceType
)

enum class InterfaceType {
    WIFI,
    ETHERNET,
    VPN,
    CELLULAR,
    OTHER
}

object NetworkUtils {

    private val IP_PATTERN = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
    private val PING_RTT_PATTERN = Regex("""time[=<]([\d.]+)\s*ms""")
    private val PING_TTL_PATTERN = Regex("""ttl=(\d+)""", RegexOption.IGNORE_CASE)

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
     * If interfaceName is null, uses the first active non-loopback IPv4 interface.
     */
    fun getNetworkInfo(context: Context, interfaceName: String? = null): NetworkInfo? {
        val selectedInterface = if (interfaceName != null) {
            getInterfaceByName(interfaceName)
        } else {
            getAvailableInterfaces().firstOrNull()?.let { getInterfaceByName(it.name) }
        } ?: return null

        val interfaceIpv4 = selectedInterface.inetAddresses.toList()
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?: return null

        val ipAddress = interfaceIpv4.hostAddress ?: return null
        val prefixLength = selectedInterface.interfaceAddresses
            .firstOrNull { it.address == interfaceIpv4 }
            ?.networkPrefixLength
            ?.toInt()
            ?.coerceIn(0, 32)
            ?: 24
        val subnetMask = prefixLengthToSubnetMask(prefixLength)

        var ssid: String? = null
        var bssid: String? = null
        var frequency: Int? = null
        var linkSpeed: Int? = null
        var signalStrength: Int? = null
        var gateway: String? = null

        // For Wi-Fi interfaces, enrich with DHCP and Wi-Fi details when available.
        if (inferInterfaceType(selectedInterface.name) == InterfaceType.WIFI && isWifiConnected(context)) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager.connectionInfo
            @Suppress("DEPRECATION")
            val dhcpInfo = wifiManager.dhcpInfo

            ssid = getSSID(context)
            bssid = wifiInfo?.bssid
            frequency = wifiInfo?.frequency
            linkSpeed = wifiInfo?.linkSpeed
            signalStrength = wifiInfo?.rssi

            gateway = if (dhcpInfo != null && dhcpInfo.gateway != 0) {
                intToIpAddress(dhcpInfo.gateway)
            } else {
                val parts = ipAddress.split(".")
                if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.1" else null
            }
        }

        return NetworkInfo(
            interfaceName = selectedInterface.name,
            ssid = ssid,
            bssid = bssid,
            ipAddress = ipAddress,
            subnetMask = subnetMask,
            gateway = gateway,
            networkPrefix = prefixLength,
            frequency = frequency,
            linkSpeed = linkSpeed,
            signalStrength = signalStrength
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
     * Get active non-loopback IPv4 interfaces that can be scanned.
     */
    fun getAvailableInterfaces(): List<NetworkInterfaceOption> {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            interfaces
                .filter { isEligibleInterface(it) }
                .mapNotNull { networkInterface ->
                    val ipv4 = networkInterface.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .firstOrNull { !it.isLoopbackAddress }
                        ?: return@mapNotNull null

                    NetworkInterfaceOption(
                        name = networkInterface.name,
                        ipAddress = ipv4.hostAddress ?: return@mapNotNull null,
                        type = inferInterfaceType(networkInterface.name)
                    )
                }
                .sortedWith(compareBy<NetworkInterfaceOption> { interfaceTypePriority(it.type) }
                    .thenBy { it.name })
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get device's IP address on the selected local interface.
     */
    fun getLocalIpAddress(interfaceName: String? = null): String? {
        try {
            if (interfaceName != null) {
                val networkInterface = getInterfaceByName(interfaceName) ?: return null
                val address = networkInterface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress }
                return address?.hostAddress
            }

            return getAvailableInterfaces().firstOrNull()?.ipAddress
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Get local device's MAC address for the selected interface.
     */
    fun getLocalMacAddress(interfaceName: String? = null): String? {
        try {
            val networkInterface = if (interfaceName != null) {
                getInterfaceByName(interfaceName)
            } else {
                getAvailableInterfaces().firstOrNull()?.let { getInterfaceByName(it.name) }
            } ?: return null

            val mac = networkInterface.hardwareAddress ?: return null
            return mac.joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getInterfaceByName(interfaceName: String): NetworkInterface? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .firstOrNull { it.name.equals(interfaceName, ignoreCase = true) && isEligibleInterface(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun isEligibleInterface(networkInterface: NetworkInterface): Boolean {
        if (!networkInterface.isUp || networkInterface.isLoopback) return false
        if (inferInterfaceType(networkInterface.name) == InterfaceType.CELLULAR) return false
        val hasIpv4 = networkInterface.inetAddresses.toList().any { address ->
            address is Inet4Address && !address.isLoopbackAddress
        }
        return hasIpv4
    }

    private fun inferInterfaceType(interfaceName: String): InterfaceType {
        val name = interfaceName.lowercase()
        return when {
            name.startsWith("wlan") || name.startsWith("wifi") -> InterfaceType.WIFI
            name.startsWith("eth") || name.startsWith("en") -> InterfaceType.ETHERNET
            name.startsWith("tun") || name.startsWith("tap") || name.startsWith("ppp") -> InterfaceType.VPN
            name.startsWith("rmnet") || name.startsWith("ccmni") -> InterfaceType.CELLULAR
            else -> InterfaceType.OTHER
        }
    }

    private fun interfaceTypePriority(type: InterfaceType): Int {
        return when (type) {
            InterfaceType.WIFI -> 0
            InterfaceType.ETHERNET -> 1
            InterfaceType.VPN -> 2
            InterfaceType.CELLULAR -> 3
            else -> 4
        }
    }

    private fun prefixLengthToSubnetMask(prefixLength: Int): String {
        if (prefixLength <= 0) return "0.0.0.0"
        if (prefixLength >= 32) return "255.255.255.255"

        val mask = -1 shl (32 - prefixLength)
        return "${(mask ushr 24) and 0xFF}.${(mask ushr 16) and 0xFF}.${(mask ushr 8) and 0xFF}.${mask and 0xFF}"
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

        // For larger subnets (< /24), scan the /24 segment the device is actually in
        val deviceParts = networkInfo.ipAddress.split(".")
        val localPrefix = if (deviceParts.size == 4) {
            deviceParts.take(3).joinToString(".")
        } else {
            parts.take(3).joinToString(".")
        }
        return (1..254).map { "$localPrefix.$it" }
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
    suspend fun isReachable(ipAddress: String, timeoutMs: Int = 1000): PingResult {
        if (!isValidIpAddress(ipAddress)) return PingResult(false)

        val startTime = System.currentTimeMillis()
        val timeoutSec = maxOf(1, timeoutMs / 1000)

        // Method 1: Try ping first (fastest for responsive devices)
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("/system/bin/ping", "-c", "1", "-W", "$timeoutSec", ipAddress)
            )
            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(timeoutMs.toLong() + 500, TimeUnit.MILLISECONDS)
                    && process.exitValue() == 0
            process.destroyForcibly()
            if (completed) {
                // Require a parsed RTT to confirm a real echo reply. A missing RTT means
                // the router sent ICMP "Destination Unreachable" (exits 0 on some Android
                // kernels) rather than an actual reply from the host.
                val latency = PING_RTT_PATTERN.find(output)
                    ?.groupValues?.get(1)?.toFloatOrNull()?.toInt()
                if (latency != null) {
                    val ttl = PING_TTL_PATTERN.find(output)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    return PingResult(true, latency, ttl)
                }
            }
        } catch (e: Exception) {
            // Continue to TCP probe
        }

        // Method 2: TCP port probe in parallel for devices that block ping
        // Uses CompletableDeferred to short-circuit as soon as any port responds
        val commonPorts = intArrayOf(445, 139, 22, 80, 443, 8080, 5000, 3389, 62078)
        return withContext(Dispatchers.IO) {
            val firstSuccess = CompletableDeferred<Boolean>()
            val jobs = commonPorts.map { port ->
                async {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(ipAddress, port), 200)
                            firstSuccess.complete(true)
                        }
                    } catch (e: Exception) {
                        // Port closed or unreachable
                    }
                }
            }

            val reachable = withTimeoutOrNull(timeoutMs.toLong()) {
                firstSuccess.await()
            } == true

            jobs.forEach { it.cancel() }

            if (reachable) {
                val latency = (System.currentTimeMillis() - startTime).toInt()
                PingResult(true, latency)
            } else {
                PingResult(false)
            }
        }
    }

    /**
     * Check if a MAC address is locally administered (randomized).
     * Android 10+ and iOS 14+ randomize MACs per-network, setting the
     * locally-administered bit (bit 1 of the first octet).
     */
    fun isLocallyAdministeredMac(mac: String?): Boolean {
        if (mac == null) return false
        val firstOctet = mac.split(":").firstOrNull()
            ?.toIntOrNull(16) ?: return false
        return (firstOctet and 0x02) != 0
    }

    /**
     * Resolve hostname for an IP address.
     * Rejects the result if it equals the IP (unresolved), is blank, or contains
     * non-hostname characters (e.g. binary garbage from broken mDNS/DNS responses).
     */
    fun resolveHostname(ipAddress: String): String? {
        return try {
            val address = InetAddress.getByName(ipAddress)
            val hostname = address.canonicalHostName
            if (hostname != ipAddress && isValidHostname(hostname)) hostname else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidHostname(hostname: String): Boolean {
        if (hostname.isBlank() || hostname.length > 253) return false
        return hostname.all { it.isLetterOrDigit() || it == '-' || it == '.' || it == '_' }
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
