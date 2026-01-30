package com.networkscanner.app.data

import android.os.Parcel
import android.os.Parcelable
import java.util.Date

/**
 * Result of a deep scan on a single device.
 */
data class DeepScanResult(
    val ipAddress: String,
    val scanTime: Date = Date(),
    val openPorts: List<PortInfo> = emptyList(),
    val detectedOs: OsInfo? = null,
    val scanDurationMs: Long = 0,
    val status: DeepScanStatus = DeepScanStatus.PENDING
) : Parcelable {

    val hasOpenPorts: Boolean get() = openPorts.isNotEmpty()
    val portCount: Int get() = openPorts.size

    constructor(parcel: Parcel) : this(
        ipAddress = parcel.readString() ?: "",
        scanTime = Date(parcel.readLong()),
        openPorts = parcel.createTypedArrayList(PortInfo.CREATOR) ?: emptyList(),
        detectedOs = parcel.readParcelable(OsInfo::class.java.classLoader),
        scanDurationMs = parcel.readLong(),
        status = DeepScanStatus.entries.getOrElse(parcel.readInt()) { DeepScanStatus.PENDING }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(ipAddress)
        parcel.writeLong(scanTime.time)
        parcel.writeTypedList(openPorts)
        parcel.writeParcelable(detectedOs, flags)
        parcel.writeLong(scanDurationMs)
        parcel.writeInt(status.ordinal)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<DeepScanResult> {
        override fun createFromParcel(parcel: Parcel): DeepScanResult = DeepScanResult(parcel)
        override fun newArray(size: Int): Array<DeepScanResult?> = arrayOfNulls(size)
    }
}

/**
 * Information about an open port.
 */
data class PortInfo(
    val port: Int,
    val protocol: String = "TCP",
    val serviceName: String? = null,
    val banner: String? = null,
    val version: String? = null,
    val state: PortState = PortState.OPEN
) : Parcelable {

    val displayName: String
        get() = serviceName ?: CommonPorts.getServiceName(port) ?: "Unknown"

    constructor(parcel: Parcel) : this(
        port = parcel.readInt(),
        protocol = parcel.readString() ?: "TCP",
        serviceName = parcel.readString(),
        banner = parcel.readString(),
        version = parcel.readString(),
        state = PortState.entries.getOrElse(parcel.readInt()) { PortState.OPEN }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(port)
        parcel.writeString(protocol)
        parcel.writeString(serviceName)
        parcel.writeString(banner)
        parcel.writeString(version)
        parcel.writeInt(state.ordinal)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<PortInfo> {
        override fun createFromParcel(parcel: Parcel): PortInfo = PortInfo(parcel)
        override fun newArray(size: Int): Array<PortInfo?> = arrayOfNulls(size)
    }
}

enum class PortState {
    OPEN,
    CLOSED,
    FILTERED
}

/**
 * Detected operating system information.
 */
data class OsInfo(
    val name: String,
    val family: OsFamily = OsFamily.UNKNOWN,
    val version: String? = null,
    val confidence: Int = 0 // 0-100
) : Parcelable {

    constructor(parcel: Parcel) : this(
        name = parcel.readString() ?: "Unknown",
        family = OsFamily.entries.getOrElse(parcel.readInt()) { OsFamily.UNKNOWN },
        version = parcel.readString(),
        confidence = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeInt(family.ordinal)
        parcel.writeString(version)
        parcel.writeInt(confidence)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<OsInfo> {
        override fun createFromParcel(parcel: Parcel): OsInfo = OsInfo(parcel)
        override fun newArray(size: Int): Array<OsInfo?> = arrayOfNulls(size)
    }
}

enum class OsFamily(val displayName: String) {
    WINDOWS("Windows"),
    LINUX("Linux"),
    MACOS("macOS"),
    IOS("iOS"),
    ANDROID("Android"),
    BSD("BSD"),
    ROUTER_OS("Router OS"),
    PRINTER_OS("Printer"),
    EMBEDDED("Embedded"),
    UNKNOWN("Unknown")
}

enum class DeepScanStatus {
    PENDING,
    SCANNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Progress information for deep scan.
 */
data class DeepScanProgress(
    val phase: DeepScanPhase = DeepScanPhase.INITIALIZING,
    val progress: Float = 0f, // 0.0 to 1.0
    val message: String = "",
    val currentPort: Int? = null,
    val portsScanned: Int = 0,
    val portsTotal: Int = 0,
    val openPortsFound: Int = 0
)

enum class DeepScanPhase {
    INITIALIZING,
    PORT_SCANNING,
    BANNER_GRABBING,
    OS_DETECTION,
    FINALIZING
}

/**
 * Common port definitions with service names.
 */
object CommonPorts {
    // Top 100 ports commonly scanned
    val TOP_PORTS = listOf(
        21,    // FTP
        22,    // SSH
        23,    // Telnet
        25,    // SMTP
        53,    // DNS
        80,    // HTTP
        110,   // POP3
        111,   // RPC
        135,   // MSRPC
        139,   // NetBIOS
        143,   // IMAP
        443,   // HTTPS
        445,   // SMB
        465,   // SMTPS
        587,   // SMTP Submission
        993,   // IMAPS
        995,   // POP3S
        1080,  // SOCKS
        1433,  // MSSQL
        1434,  // MSSQL Browser
        1521,  // Oracle
        1723,  // PPTP
        2049,  // NFS
        2082,  // cPanel
        2083,  // cPanel SSL
        2086,  // WHM
        2087,  // WHM SSL
        2121,  // FTP Proxy
        3306,  // MySQL
        3389,  // RDP
        3690,  // SVN
        4443,  // Pharos
        5000,  // UPnP
        5060,  // SIP
        5432,  // PostgreSQL
        5631,  // pcAnywhere
        5632,  // pcAnywhere
        5900,  // VNC
        5901,  // VNC-1
        5902,  // VNC-2
        6000,  // X11
        6379,  // Redis
        6443,  // Kubernetes
        6666,  // IRC
        6667,  // IRC
        7001,  // WebLogic
        7002,  // WebLogic SSL
        8000,  // HTTP Alt
        8008,  // HTTP Alt
        8080,  // HTTP Proxy
        8081,  // HTTP Alt
        8443,  // HTTPS Alt
        8888,  // HTTP Alt
        9000,  // Various
        9090,  // Various Web
        9200,  // Elasticsearch
        9418,  // Git
        10000, // Webmin
        11211, // Memcached
        27017, // MongoDB
        27018, // MongoDB
        28017, // MongoDB Web
        49152, // Dynamic
        49153, // Dynamic
        49154, // Dynamic
        49155, // Dynamic
        49156, // Dynamic
        49157  // Dynamic
    )

    private val PORT_SERVICES = mapOf(
        20 to "FTP Data",
        21 to "FTP",
        22 to "SSH",
        23 to "Telnet",
        25 to "SMTP",
        53 to "DNS",
        67 to "DHCP Server",
        68 to "DHCP Client",
        69 to "TFTP",
        80 to "HTTP",
        110 to "POP3",
        111 to "RPC",
        119 to "NNTP",
        123 to "NTP",
        135 to "MSRPC",
        137 to "NetBIOS Name",
        138 to "NetBIOS Datagram",
        139 to "NetBIOS Session",
        143 to "IMAP",
        161 to "SNMP",
        162 to "SNMP Trap",
        389 to "LDAP",
        443 to "HTTPS",
        445 to "SMB",
        465 to "SMTPS",
        514 to "Syslog",
        515 to "LPD",
        587 to "SMTP Submission",
        631 to "IPP/CUPS",
        636 to "LDAPS",
        993 to "IMAPS",
        995 to "POP3S",
        1080 to "SOCKS",
        1194 to "OpenVPN",
        1433 to "MSSQL",
        1434 to "MSSQL Browser",
        1521 to "Oracle DB",
        1701 to "L2TP",
        1723 to "PPTP",
        1812 to "RADIUS",
        1813 to "RADIUS Accounting",
        2049 to "NFS",
        2082 to "cPanel",
        2083 to "cPanel SSL",
        2086 to "WHM",
        2087 to "WHM SSL",
        3306 to "MySQL",
        3389 to "RDP",
        3690 to "SVN",
        4443 to "Pharos",
        5000 to "UPnP",
        5060 to "SIP",
        5061 to "SIP TLS",
        5432 to "PostgreSQL",
        5631 to "pcAnywhere Data",
        5632 to "pcAnywhere",
        5900 to "VNC",
        5901 to "VNC-1",
        5902 to "VNC-2",
        6000 to "X11",
        6379 to "Redis",
        6443 to "Kubernetes API",
        6667 to "IRC",
        7001 to "WebLogic",
        7002 to "WebLogic SSL",
        8000 to "HTTP Alt",
        8008 to "HTTP Alt",
        8080 to "HTTP Proxy",
        8081 to "HTTP Alt",
        8443 to "HTTPS Alt",
        8888 to "HTTP Alt",
        9000 to "SonarQube",
        9090 to "Prometheus",
        9200 to "Elasticsearch",
        9300 to "Elasticsearch",
        9418 to "Git",
        10000 to "Webmin",
        11211 to "Memcached",
        27017 to "MongoDB",
        27018 to "MongoDB",
        28017 to "MongoDB Web"
    )

    fun getServiceName(port: Int): String? = PORT_SERVICES[port]

    fun getServiceDescription(port: Int): String {
        return PORT_SERVICES[port] ?: "Port $port"
    }
}
