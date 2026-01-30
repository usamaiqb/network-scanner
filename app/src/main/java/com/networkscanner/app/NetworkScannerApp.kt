package com.networkscanner.app

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.networkscanner.app.theme.ThemeManager

/**
 * Application class for NetworkScanner app.
 */
class NetworkScannerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize theme manager
        ThemeManager.initialize(this)

        // Apply dynamic colors for Material You (Android 12+)
        if (ThemeManager.supportsDynamicColors() && ThemeManager.isDynamicColorsEnabled(this)) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }
}
