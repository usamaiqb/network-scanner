package com.networkscanner.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tablet
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Enumeration of device types with associated icons and display names.
 */
enum class DeviceType(
    val displayName: String,
    val icon: ImageVector,
    val keywords: List<String> = emptyList()
) {
    ROUTER(
        displayName = "Router",
        icon = Icons.Outlined.Router,
        keywords = listOf("router", "gateway", "netgear", "linksys", "asus", "tp-link", "d-link", "cisco")
    ),
    SMARTPHONE(
        displayName = "Smartphone",
        icon = Icons.Outlined.Smartphone,
        keywords = listOf("iphone", "android", "pixel", "samsung", "oneplus", "xiaomi", "huawei", "mobile", "oppo", "vivo", "redmi", "poco", "motorola", "nokia", "zte", "meizu", "realme", "galaxy")
    ),
    TABLET(
        displayName = "Tablet",
        icon = Icons.Outlined.Tablet,
        keywords = listOf("ipad", "tablet", "galaxy tab", "surface")
    ),
    LAPTOP(
        displayName = "Laptop",
        icon = Icons.Outlined.Laptop,
        keywords = listOf("macbook", "laptop", "notebook", "thinkpad", "dell", "hp", "lenovo", "acer", "asus", "msi", "surface", "chromebook", "intel", "realtek")
    ),
    DESKTOP(
        displayName = "Desktop",
        icon = Icons.Outlined.DesktopWindows,
        keywords = listOf("desktop", "pc", "imac", "workstation", "microsoft")
    ),
    TV(
        displayName = "Smart TV",
        icon = Icons.Outlined.Tv,
        keywords = listOf("tv", "television", "roku", "firetv", "chromecast", "appletv", "samsung tv", "lg tv", "sony tv")
    ),
    GAME_CONSOLE(
        displayName = "Game Console",
        icon = Icons.Outlined.SportsEsports,
        keywords = listOf("playstation", "xbox", "nintendo", "switch", "ps4", "ps5")
    ),
    SMART_SPEAKER(
        displayName = "Smart Speaker",
        icon = Icons.Outlined.Speaker,
        keywords = listOf("alexa", "echo", "google home", "homepod", "sonos")
    ),
    SMART_HOME(
        displayName = "Smart Home Device",
        icon = Icons.Outlined.Home,
        keywords = listOf("nest", "hue", "smart", "iot", "thermostat", "camera", "ring", "wyze")
    ),
    PRINTER(
        displayName = "Printer",
        icon = Icons.Outlined.Print,
        keywords = listOf("printer", "epson", "hp", "canon", "brother")
    ),
    NAS(
        displayName = "NAS/Storage",
        icon = Icons.Outlined.Storage,
        keywords = listOf("nas", "synology", "qnap", "storage", "diskstation")
    ),
    SERVER(
        displayName = "Server",
        icon = Icons.Outlined.Dns,
        keywords = listOf("server", "linux", "ubuntu", "debian", "centos", "raspberry")
    ),
    WEARABLE(
        displayName = "Wearable",
        icon = Icons.Outlined.Watch,
        keywords = listOf("watch", "fitbit", "garmin", "wearable")
    ),
    UNKNOWN(
        displayName = "Unknown Device",
        icon = Icons.Outlined.DevicesOther,
        keywords = emptyList()
    );

    companion object {
        /**
         * Identify device type based on hostname, vendor, or mDNS service type.
         */
        fun identify(
            hostname: String? = null,
            vendor: String? = null,
            mdnsServiceType: String? = null,
            ssdpDeviceType: String? = null
        ): DeviceType {
            val searchTerms = listOfNotNull(
                hostname?.lowercase(),
                vendor?.lowercase(),
                mdnsServiceType?.lowercase(),
                ssdpDeviceType?.lowercase()
            ).joinToString(" ")

            if (searchTerms.isEmpty()) return UNKNOWN

            // Check each device type's keywords
            for (type in entries) {
                if (type == UNKNOWN) continue
                for (keyword in type.keywords) {
                    if (searchTerms.contains(keyword)) {
                        return type
                    }
                }
            }

            // Special cases based on mDNS service types
            // Note: _googlecast is intentionally omitted — it's advertised by both
            // dedicated Chromecasts (TV) and Android phones running Google Home.
            // probePortHeuristics() disambiguates using port 8008/8009.
            mdnsServiceType?.let {
                return when {
                    it.contains("_airplay") -> TV
                    it.contains("_raop") -> SMART_SPEAKER
                    it.contains("_androidtvremote2") -> TV
                    it.contains("_printer") || it.contains("_ipp") -> PRINTER
                    it.contains("_smb") || it.contains("_afpovertcp") -> NAS
                    it.contains("_ssh") || it.contains("_sftp") -> SERVER
                    else -> UNKNOWN
                }
            }

            // SSDP device type detection
            ssdpDeviceType?.let {
                return when {
                    it.contains("MediaRenderer") -> TV
                    it.contains("MediaServer") -> NAS
                    it.contains("InternetGatewayDevice") -> ROUTER
                    it.contains("Printer") -> PRINTER
                    else -> UNKNOWN
                }
            }

            return UNKNOWN
        }
    }
}
