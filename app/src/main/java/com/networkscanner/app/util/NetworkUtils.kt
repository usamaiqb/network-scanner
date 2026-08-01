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
     * Largest subnet, measured in usable host addresses, that a scan will sweep
     * in full automatically. A /22 (1022 hosts) fits within this cap; anything
     * wider (/21 and up) falls back to the device's local /24, because sweeping
     * tens of thousands to millions of addresses is infeasible on a phone.
     *
     * NOTE: letting the user opt into scanning a larger range (whole subnet or a
     * custom range) on wide networks is planned separately — see
     * docs/large-subnet-handling.md.
     */
    const val MAX_SCAN_HOSTS = 1024

    /**
     * Generate the list of IP addresses to ping-sweep, based on the interface's
     * actual network prefix rather than assuming a /24.
     *
     * - Subnets with up to [MAX_SCAN_HOSTS] usable hosts (through /22) are swept
     *   in full, so /23 and /22 networks are covered — including the gateway,
     *   which may sit in a different /24 than the device.
     * - Wider subnets (/21 and up, e.g. a public-Wi-Fi /8) fall back to the /24
     *   the device is in, to keep the sweep feasible.
     */
    fun getIpRange(networkInfo: NetworkInfo): List<String> {
        val prefix = networkInfo.networkPrefix.coerceIn(0, 32)

        // /31 (point-to-point) and /32 (single host) have no conventional host range.
        if (prefix >= 31) return listOf(networkInfo.ipAddress)

        val deviceInt = ipv4ToLong(networkInfo.ipAddress)
        val networkInt = ipv4ToLong(networkInfo.networkAddress) ?: deviceInt ?: return emptyList()

        val totalAddresses = 1L shl (32 - prefix)
        // Usable host range excludes the network address and the broadcast address.
        val firstHost = networkInt + 1
        val lastHost = networkInt + totalAddresses - 2
        val totalHosts = lastHost - firstHost + 1

        if (totalHosts <= MAX_SCAN_HOSTS) {
            return (firstHost..lastHost).map { longToIpv4(it) }
        }

        // Subnet too large to sweep in full — scan the /24 the device sits in.
        val base = (deviceInt ?: networkInt) and 0xFFFFFF00L
        return (1L..254L).map { longToIpv4(base + it) }
    }

    /**
     * Convert a dotted-quad IPv4 string to its big-endian numeric value
     * (0..2^32-1), or null if malformed. Unlike [ipAddressToInt] this preserves
     * octet order, so the result is safe to use for range arithmetic.
     */
    private fun ipv4ToLong(ip: String): Long? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        var result = 0L
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            result = (result shl 8) or octet.toLong()
        }
        return result
    }

    /** Convert a big-endian numeric IPv4 value back to dotted-quad form. */
    private fun longToIpv4(value: Long): String =
        "${(value ushr 24) and 0xFF}.${(value ushr 16) and 0xFF}." +
            "${(value ushr 8) and 0xFF}.${value and 0xFF}"

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
