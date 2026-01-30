package com.networkscanner.app.data

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

    // Status
    val isOnline: Boolean = true,
    val isCurrentDevice: Boolean = false,
    val lastSeen: Date = Date(),
    val firstSeen: Date = Date(),

    // Signal/latency info
    val latencyMs: Int? = null,
    val signalStrength: Int? = null
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
        get() = hostname != null || vendor != null || mdnsServices.isNotEmpty() || ssdpInfo != null

    /**
     * Unique identifier combining IP and MAC.
     */
    val uniqueId: String
        get() = macAddress ?: ipAddress

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Device) return false
        return uniqueId == other.uniqueId
    }

    override fun hashCode(): Int {
        return uniqueId.hashCode()
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
        ssdpInfo = parcel.readParcelable(SsdpDeviceInfo::class.java.classLoader),
        isOnline = parcel.readByte() != 0.toByte(),
        isCurrentDevice = parcel.readByte() != 0.toByte(),
        lastSeen = Date(parcel.readLong()),
        firstSeen = Date(parcel.readLong()),
        latencyMs = parcel.readValue(Int::class.java.classLoader) as? Int,
        signalStrength = parcel.readValue(Int::class.java.classLoader) as? Int
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
        parcel.writeByte(if (isOnline) 1 else 0)
        parcel.writeByte(if (isCurrentDevice) 1 else 0)
        parcel.writeLong(lastSeen.time)
        parcel.writeLong(firstSeen.time)
        parcel.writeValue(latencyMs)
        parcel.writeValue(signalStrength)
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
