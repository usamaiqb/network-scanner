package com.networkscanner.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.concurrent.ConcurrentHashMap

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
        icon = Icons.Rounded.Router,
        keywords = listOf("router", "gateway", "netgear", "linksys", "asus", "tp-link", "d-link", "cisco")
    ),
    SMARTPHONE(
        displayName = "Smartphone",
        icon = Icons.Rounded.Smartphone,
        keywords = listOf("iphone", "android", "pixel", "samsung", "oneplus", "xiaomi", "huawei", "mobile", "oppo", "vivo", "redmi", "poco", "motorola", "nokia", "zte", "meizu", "realme", "galaxy")
    ),
    TABLET(
        displayName = "Tablet",
        icon = Icons.Rounded.Tablet,
        keywords = listOf("ipad", "tablet", "galaxy tab", "surface")
    ),
    LAPTOP(
        displayName = "Laptop",
        icon = Icons.Rounded.Laptop,
        // Note: NIC-vendor names (intel, realtek) and the too-generic "hp" are
        // deliberately excluded — they appear on desktops, servers and printers
        // just as often as laptops and caused systematic misclassification.
        keywords = listOf("macbook", "laptop", "notebook", "thinkpad", "dell", "lenovo", "acer", "asus", "msi", "surface", "chromebook")
    ),
    DESKTOP(
        displayName = "Desktop",
        icon = Icons.Rounded.DesktopWindows,
        keywords = listOf("desktop", "pc", "imac", "workstation", "microsoft")
    ),
    TV(
        displayName = "Smart TV",
        icon = Icons.Rounded.Tv,
        keywords = listOf("tv", "television", "roku", "firetv", "chromecast", "appletv", "samsung tv", "lg tv", "sony tv")
    ),
    GAME_CONSOLE(
        displayName = "Game Console",
        icon = Icons.Rounded.SportsEsports,
        keywords = listOf("playstation", "xbox", "nintendo", "switch", "ps4", "ps5")
    ),
    SMART_SPEAKER(
        displayName = "Smart Speaker",
        icon = Icons.Rounded.Speaker,
        keywords = listOf("alexa", "echo", "google home", "homepod", "sonos")
    ),
    SMART_HOME(
        displayName = "Smart Home Device",
        icon = Icons.Rounded.Home,
        keywords = listOf("nest", "hue", "smart", "iot", "thermostat", "camera", "ring", "wyze")
    ),
    PRINTER(
        displayName = "Printer",
        icon = Icons.Rounded.Print,
        keywords = listOf("printer", "epson", "hp", "canon", "brother")
    ),
    NAS(
        displayName = "NAS/Storage",
        icon = Icons.Rounded.Storage,
        keywords = listOf("nas", "synology", "qnap", "storage", "diskstation")
    ),
    SERVER(
        displayName = "Server",
        icon = Icons.Rounded.Dns,
        keywords = listOf("server", "linux", "ubuntu", "debian", "centos", "raspberry")
    ),
    WEARABLE(
        displayName = "Wearable",
        icon = Icons.Rounded.Watch,
        keywords = listOf("watch", "fitbit", "garmin", "wearable")
    ),
    UNKNOWN(
        displayName = "Unknown Device",
        icon = Icons.Rounded.DevicesOther,
        keywords = emptyList()
    );

    companion object {
        // Compiled word-boundary matchers, cached per keyword. Matching on word
        // boundaries (rather than raw substrings) prevents short keywords like
        // "hp"/"pc"/"tv" from hitting inside unrelated words (e.g. "sharp").
        private val keywordRegexCache = ConcurrentHashMap<String, Regex>()

        private fun wordMatch(text: String, keyword: String): Boolean {
            val regex = keywordRegexCache.getOrPut(keyword) {
                Regex("\\b" + Regex.escape(keyword) + "\\b")
            }
            return regex.containsMatchIn(text)
        }

        /**
         * Map a single mDNS service type to a device type, or null if the service
         * doesn't identify one on its own.
         *
         * Note: _googlecast is intentionally omitted — it's advertised by both
         * dedicated Chromecasts (TV) and Android phones running Google Home.
         * probePortHeuristics() disambiguates using ports 8008/8009.
         */
        private fun classifyMdns(service: String): DeviceType? {
            val s = service.lowercase()
            return when {
                s.contains("_airplay") -> TV
                s.contains("_androidtvremote2") -> TV
                s.contains("_raop") || s.contains("_spotify-connect") -> SMART_SPEAKER
                s.contains("_homekit") || s.contains("_matter") -> SMART_HOME
                s.contains("_printer") || s.contains("_ipp") || s.contains("_pdl") -> PRINTER
                s.contains("_smb") || s.contains("_afpovertcp") -> NAS
                s.contains("_ssh") || s.contains("_sftp") -> SERVER
                else -> null
            }
        }

        /** Map an SSDP/UPnP device type URN to a device type, or null. */
        private fun classifySsdp(ssdpDeviceType: String?): DeviceType? {
            val s = ssdpDeviceType ?: return null
            return when {
                s.contains("MediaRenderer") -> TV
                s.contains("MediaServer") -> NAS
                s.contains("InternetGatewayDevice") || s.contains("WANDevice") ||
                    s.contains("WANConnectionDevice") -> ROUTER
                s.contains("Printer") -> PRINTER
                else -> null
            }
        }

        /**
         * Identify device type from the available signals.
         *
         * Specific, reliable signals (mDNS service types, then SSDP device type)
         * are checked first so they aren't shadowed by generic hostname/vendor
         * keywords. Only if those are inconclusive do we fall back to scoring
         * hostname/vendor keywords, picking the type with the most matches.
         */
        fun identify(
            hostname: String? = null,
            vendor: String? = null,
            mdnsServices: List<String> = emptyList(),
            ssdpDeviceType: String? = null
        ): DeviceType {
            // 1) mDNS service types — most reliable identity signal.
            for (service in mdnsServices) {
                classifyMdns(service)?.let { return it }
            }

            // 2) SSDP/UPnP device type.
            classifySsdp(ssdpDeviceType)?.let { return it }

            // 3) Keyword scoring over hostname + vendor.
            val text = listOfNotNull(hostname, vendor).joinToString(" ").lowercase()
            if (text.isBlank()) return UNKNOWN

            val best = entries
                .filter { it != UNKNOWN }
                .map { type -> type to type.keywords.count { kw -> wordMatch(text, kw) } }
                .filter { (_, score) -> score > 0 }
                .maxByOrNull { (_, score) -> score }

            return best?.first ?: UNKNOWN
        }
    }
}
