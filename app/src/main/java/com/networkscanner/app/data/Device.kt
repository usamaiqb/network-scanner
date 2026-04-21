package com.networkscanner.app.data

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import java.util.Date

/**
 * Represents a network device discovered during scanning.
 */
data class Device(
    // Network identifiers
    val ipAddress: String,
    val macAddress: String? = null,
    val hostname: String? = null,

    // Device identification
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val vendor: String? = null,
    val customName: String? = null,

    // Discovery info
    val discoveredVia: DiscoveryMethod = DiscoveryMethod.PING,
    val mdnsServices: List<String> = emptyList(),
    val ssdpInfo: SsdpDeviceInfo? = null,
    val netBiosInfo: NetBiosInfo? = null,

    // Status
    val isOnline: Boolean = true,
    val isCurrentDevice: Boolean = false,
    val lastSeen: Date = Date(),
    val firstSeen: Date = Date(),

    // Signal/latency info
    val latencyMs: Int? = null,

    // OS fingerprinting: TTL from ping response (64=Linux/Android/iOS, 128=Windows)
    val ttl: Int? = null
) : Parcelable {
    /**
     * Get the display name for this device.
     * Priority: customName > hostname > vendor + IP suffix > IP
     */
    val displayName: String
        get() = customName
            ?: hostname?.takeIf { it.isNotBlank() && it != ipAddress }
            ?: vendor?.let { "$it (${ipAddress.substringAfterLast('.')})" }
            ?: ipAddress

    /**
     * Get a short identifier for the device.
     */
    val shortId: String
        get() = macAddress?.takeLast(8)?.uppercase() ?: ipAddress.substringAfterLast('.')

    /**
     * Check if this device has detailed information.
     */
    val hasDetails: Boolean
        get() = hostname != null || vendor != null || mdnsServices.isNotEmpty() || ssdpInfo != null || netBiosInfo != null

    /**
     * Unique identifier combining IP and MAC.
     */
    val uniqueId: String
        get() = macAddress ?: ipAddress

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Device) return false
        return uniqueId == other.uniqueId
            && ipAddress == other.ipAddress
            && hostname == other.hostname
            && deviceType == other.deviceType
            && vendor == other.vendor
            && customName == other.customName
            && isOnline == other.isOnline
            && isCurrentDevice == other.isCurrentDevice
            && latencyMs == other.latencyMs
            && ttl == other.ttl
            && mdnsServices == other.mdnsServices
            && ssdpInfo == other.ssdpInfo
            && netBiosInfo == other.netBiosInfo
            && discoveredVia == other.discoveredVia
    }

    override fun hashCode(): Int {
        var result = uniqueId.hashCode()
        result = 31 * result + ipAddress.hashCode()
        result = 31 * result + (hostname?.hashCode() ?: 0)
        result = 31 * result + deviceType.hashCode()
        result = 31 * result + (vendor?.hashCode() ?: 0)
        result = 31 * result + (customName?.hashCode() ?: 0)
        result = 31 * result + isOnline.hashCode()
        result = 31 * result + isCurrentDevice.hashCode()
        result = 31 * result + (latencyMs?.hashCode() ?: 0)
        result = 31 * result + (ttl?.hashCode() ?: 0)
        result = 31 * result + mdnsServices.hashCode()
        result = 31 * result + (ssdpInfo?.hashCode() ?: 0)
        result = 31 * result + (netBiosInfo?.hashCode() ?: 0)
        result = 31 * result + discoveredVia.hashCode()
        return result
    }

    // Parcelable implementation
    constructor(parcel: Parcel) : this(
        ipAddress = parcel.readString() ?: "",
        macAddress = parcel.readString(),
        hostname = parcel.readString(),
        deviceType = DeviceType.entries.getOrElse(parcel.readInt()) { DeviceType.UNKNOWN },
        vendor = parcel.readString(),
        customName = parcel.readString(),
        discoveredVia = DiscoveryMethod.entries.getOrElse(parcel.readInt()) { DiscoveryMethod.PING },
        mdnsServices = parcel.createStringArrayList() ?: emptyList(),
        ssdpInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            parcel.readParcelable(SsdpDeviceInfo::class.java.classLoader, SsdpDeviceInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            parcel.readParcelable(SsdpDeviceInfo::class.java.classLoader)
        },
        netBiosInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            parcel.readParcelable(NetBiosInfo::class.java.classLoader, NetBiosInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            parcel.readParcelable(NetBiosInfo::class.java.classLoader)
        },
        isOnline = parcel.readByte() != 0.toByte(),
        isCurrentDevice = parcel.readByte() != 0.toByte(),
        lastSeen = Date(parcel.readLong()),
        firstSeen = Date(parcel.readLong()),
        latencyMs = parcel.readValue(Int::class.java.classLoader) as? Int,
        ttl = parcel.readValue(Int::class.java.classLoader) as? Int
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(ipAddress)
        parcel.writeString(macAddress)
        parcel.writeString(hostname)
        parcel.writeInt(deviceType.ordinal)
        parcel.writeString(vendor)
        parcel.writeString(customName)
        parcel.writeInt(discoveredVia.ordinal)
        parcel.writeStringList(mdnsServices)
        parcel.writeParcelable(ssdpInfo, flags)
        parcel.writeParcelable(netBiosInfo, flags)
        parcel.writeByte(if (isOnline) 1 else 0)
        parcel.writeByte(if (isCurrentDevice) 1 else 0)
        parcel.writeLong(lastSeen.time)
        parcel.writeLong(firstSeen.time)
        parcel.writeValue(latencyMs)
        parcel.writeValue(ttl)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Device> {
        override fun createFromParcel(parcel: Parcel): Device = Device(parcel)
        override fun newArray(size: Int): Array<Device?> = arrayOfNulls(size)
    }
}

/**
 * Method by which a device was discovered.
 */
enum class DiscoveryMethod {
    ARP_CACHE,
    PING,
    MDNS,
    SSDP,
    NETBIOS,
    MANUAL
}

/**
 * NetBIOS name service information for Windows/Samba devices.
 */
data class NetBiosInfo(
    val hostname: String,
    val workgroup: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        hostname = parcel.readString() ?: "",
        workgroup = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(hostname)
        parcel.writeString(workgroup)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<NetBiosInfo> {
        override fun createFromParcel(parcel: Parcel): NetBiosInfo = NetBiosInfo(parcel)
        override fun newArray(size: Int): Array<NetBiosInfo?> = arrayOfNulls(size)
    }
}

/**
 * SSDP/UPnP device information.
 */
data class SsdpDeviceInfo(
    val friendlyName: String? = null,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val modelNumber: String? = null,
    val deviceType: String? = null,
    val locationUrl: String? = null,
    val serialNumber: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        friendlyName = parcel.readString(),
        manufacturer = parcel.readString(),
        modelName = parcel.readString(),
        modelNumber = parcel.readString(),
        deviceType = parcel.readString(),
        locationUrl = parcel.readString(),
        serialNumber = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(friendlyName)
        parcel.writeString(manufacturer)
        parcel.writeString(modelName)
        parcel.writeString(modelNumber)
        parcel.writeString(deviceType)
        parcel.writeString(locationUrl)
        parcel.writeString(serialNumber)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<SsdpDeviceInfo> {
        override fun createFromParcel(parcel: Parcel): SsdpDeviceInfo = SsdpDeviceInfo(parcel)
        override fun newArray(size: Int): Array<SsdpDeviceInfo?> = arrayOfNulls(size)
    }
}
