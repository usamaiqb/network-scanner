package com.networkscanner.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * Permission policy for scanning, in one place so only a single component ever
 * launches a request (two concurrent requests cancel each other).
 *
 * Android 16 (API 36) gates local network access behind the Nearby devices
 * permission group: raw sockets to RFC1918/link-local addresses, mDNS via
 * NsdManager, and SSDP multicast all fail with EPERM once the restriction is
 * enforced. Every discovery path in [com.networkscanner.app.network.NetworkScanner]
 * falls under that gate, so on Android 16+ a denial means a scan returns nothing at
 * all rather than a partial result — the app has to be able to ask again.
 *
 * Below Android 16 nothing is gated, so every permission here stays optional: they
 * only enrich results, and are asked for once and never nagged about again.
 */
object ScanPermissions {

    private const val PREFS_NAME = "network_scanner_prefs"
    private const val KEY_OPTIONAL_REQUESTED = "permissions_requested"

    /**
     * The permission scanning genuinely depends on, or null where the platform places
     * no restriction on local network access. Only Android 16 (API 36) and above
     * enforce it — on Android 15 and below, sockets, mDNS and SSDP all reach the local
     * network with no permission at all.
     */
    private val requiredForScan: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            null
        }

    /**
     * Permissions that only enrich results, asked for once and never nagged about:
     * location for the SSID, and — on Android 13 through 15, where it reads Wi-Fi
     * details without needing location and does not gate scanning — nearby devices.
     */
    private val optional: List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA
        ) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** False only on Android 16+ with the nearby devices permission missing. */
    fun isLocalNetworkGranted(context: Context): Boolean {
        val permission = requiredForScan ?: return true
        return isGranted(context, permission)
    }

    /**
     * Permissions to request before a scan: the local network gate whenever it is
     * missing, plus a one-time ask for the optional ones.
     */
    fun pendingForScan(context: Context): Array<String> {
        val pending = mutableListOf<String>()

        requiredForScan?.let { permission ->
            if (!isGranted(context, permission)) pending += permission
        }

        if (!hasRequestedOptional(context)) {
            pending += optional.filterNot { isGranted(context, it) }
        }

        return pending.toTypedArray()
    }

    fun markOptionalRequested(context: Context) {
        prefs(context).edit { putBoolean(KEY_OPTIONAL_REQUESTED, true) }
    }

    /**
     * Opens this app's system settings page. Once a permission is permanently denied
     * the request dialog no longer appears, so app settings is the only remaining way
     * for the user to restore scanning.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasRequestedOptional(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OPTIONAL_REQUESTED, false)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
