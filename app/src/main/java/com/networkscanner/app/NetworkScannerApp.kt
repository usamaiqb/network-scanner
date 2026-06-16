package com.networkscanner.app

import android.app.Application
import com.networkscanner.app.data.repository.CustomPortRepository
import com.networkscanner.app.data.repository.DeviceCustomizationRepository
import com.networkscanner.app.network.NetworkScanner
import com.networkscanner.app.theme.ThemeManager

/**
 * Application class for NetworkScanner app.
 *
 * The in-app language is handled by AppCompat's per-app locales
 * (see [com.networkscanner.app.ui.SettingsViewModel]); with autoStoreLocales
 * enabled in the manifest, AppCompat persists and restores the selected locale
 * automatically, so no manual Configuration/attachBaseContext handling is needed.
 */
class NetworkScannerApp : Application() {

    /** Shared NetworkScanner instance to avoid duplicate state across ViewModels. */
    val scanner: NetworkScanner by lazy { NetworkScanner(this) }

    /** Repositories backed by SharedPreferences */
    val deviceCustomizationRepository: DeviceCustomizationRepository by lazy {
        DeviceCustomizationRepository(this)
    }

    val customPortRepository: CustomPortRepository by lazy {
        CustomPortRepository(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize theme manager
        ThemeManager.initialize(this)
    }
}
