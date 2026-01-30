package com.networkscanner.app.data

import java.util.Date

/**
 * Result of a network scan operation.
 */
data class ScanResult(
    val devices: List<Device>,
    val scanStartTime: Date,
    val scanEndTime: Date,
    val networkInfo: NetworkInfo,
    val scanStatus: ScanStatus,
    val error: String? = null
) {
    /**
     * Duration of the scan in milliseconds.
     */
    val durationMs: Long
        get() = scanEndTime.time - scanStartTime.time

    /**
     * Number of online devices.
     */
    val onlineCount: Int
        get() = devices.count { it.isOnline }

    /**
     * Number of offline devices.
     */
    val offlineCount: Int
        get() = devices.count { !it.isOnline }
}

/**
 * Network information for the current connection.
 */
data class NetworkInfo(
    val ssid: String?,
    val bssid: String?,
    val ipAddress: String,
    val subnetMask: String,
    val gateway: String?,
    val networkPrefix: Int,
    val frequency: Int? = null,
    val linkSpeed: Int? = null,
    val signalStrength: Int? = null
) {
    /**
     * Get the network address (e.g., "192.168.1.0").
     */
    val networkAddress: String
        get() {
            val parts = ipAddress.split(".")
            if (parts.size != 4) return ipAddress

            val maskParts = subnetMask.split(".")
            if (maskParts.size != 4) return ipAddress

            return parts.zip(maskParts) { ip, mask ->
                (ip.toIntOrNull() ?: 0) and (mask.toIntOrNull() ?: 0)
            }.joinToString(".")
        }

    /**
     * Get CIDR notation (e.g., "192.168.1.0/24").
     */
    val cidrNotation: String
        get() = "$networkAddress/$networkPrefix"

    /**
     * Number of possible hosts in this network.
     */
    val maxHosts: Int
        get() = (1 shl (32 - networkPrefix)) - 2  // Subtract network and broadcast addresses
}

/**
 * Status of a scan operation.
 */
enum class ScanStatus {
    IDLE,
    SCANNING,
    COMPLETED,
    CANCELLED,
    ERROR
}

/**
 * Scan progress information for UI updates.
 */
data class ScanProgress(
    val phase: ScanPhase,
    val progress: Float,  // 0.0 to 1.0
    val message: String,
    val devicesFound: Int,
    val currentTarget: String? = null
)

/**
 * Phases of the scanning process.
 */
enum class ScanPhase {
    INITIALIZING,
    READING_ARP_CACHE,
    PING_SWEEP,
    MDNS_DISCOVERY,
    SSDP_DISCOVERY,
    IDENTIFYING_DEVICES,
    FINALIZING
}
