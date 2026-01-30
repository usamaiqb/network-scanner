package com.networkscanner.app.data

import androidx.annotation.DrawableRes
import com.networkscanner.app.R

/**
 * Enumeration of device types with associated icons and display names.
 */
enum class DeviceType(
    val displayName: String,
    @DrawableRes val iconRes: Int,
    val keywords: List<String> = emptyList()
) {
    ROUTER(
        displayName = "Router",
        iconRes = R.drawable.ic_router,
        keywords = listOf("router", "gateway", "netgear", "linksys", "asus", "tp-link", "d-link", "cisco")
    ),
    SMARTPHONE(
        displayName = "Smartphone",
        iconRes = R.drawable.ic_smartphone,
        keywords = listOf("iphone", "android", "pixel", "samsung", "oneplus", "xiaomi", "huawei", "mobile")
    ),
    TABLET(
        displayName = "Tablet",
        iconRes = R.drawable.ic_tablet,
        keywords = listOf("ipad", "tablet", "galaxy tab", "surface")
    ),
    LAPTOP(
        displayName = "Laptop",
        iconRes = R.drawable.ic_laptop,
        keywords = listOf("macbook", "laptop", "notebook", "thinkpad", "dell", "hp", "lenovo")
    ),
    DESKTOP(
        displayName = "Desktop",
        iconRes = R.drawable.ic_desktop,
        keywords = listOf("desktop", "pc", "imac", "workstation")
    ),
    TV(
        displayName = "Smart TV",
        iconRes = R.drawable.ic_tv,
        keywords = listOf("tv", "television", "roku", "firetv", "chromecast", "appletv", "samsung tv", "lg tv", "sony tv")
    ),
    GAME_CONSOLE(
        displayName = "Game Console",
        iconRes = R.drawable.ic_game_console,
        keywords = listOf("playstation", "xbox", "nintendo", "switch", "ps4", "ps5")
    ),
    SMART_SPEAKER(
        displayName = "Smart Speaker",
        iconRes = R.drawable.ic_smart_speaker,
        keywords = listOf("alexa", "echo", "google home", "homepod", "sonos")
    ),
    SMART_HOME(
        displayName = "Smart Home Device",
        iconRes = R.drawable.ic_smart_home,
        keywords = listOf("nest", "hue", "smart", "iot", "thermostat", "camera", "ring", "wyze")
    ),
    PRINTER(
        displayName = "Printer",
        iconRes = R.drawable.ic_printer,
        keywords = listOf("printer", "epson", "hp", "canon", "brother")
    ),
    NAS(
        displayName = "NAS/Storage",
        iconRes = R.drawable.ic_storage,
        keywords = listOf("nas", "synology", "qnap", "storage", "diskstation")
    ),
    SERVER(
        displayName = "Server",
        iconRes = R.drawable.ic_server,
        keywords = listOf("server", "linux", "ubuntu", "debian", "centos", "raspberry")
    ),
    WEARABLE(
        displayName = "Wearable",
        iconRes = R.drawable.ic_wearable,
        keywords = listOf("watch", "fitbit", "garmin", "wearable")
    ),
    UNKNOWN(
        displayName = "Unknown Device",
        iconRes = R.drawable.ic_device_unknown,
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
            mdnsServiceType?.let {
                return when {
                    it.contains("_airplay") -> TV
                    it.contains("_raop") -> SMART_SPEAKER
                    it.contains("_googlecast") -> TV
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
