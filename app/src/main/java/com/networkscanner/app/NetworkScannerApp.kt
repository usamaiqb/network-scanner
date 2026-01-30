package com.networkscanner.app

import android.app.Application
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.networkscanner.app.theme.ThemeManager

/**
 * Application class for NetworkScanner app.
 */
class NetworkScannerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Set default values for preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        // Initialize theme manager
        ThemeManager.initialize(this)

        // Apply dynamic colors for Material You (Android 12+)
        if (ThemeManager.supportsDynamicColors() && ThemeManager.isDynamicColorsEnabled(this)) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }
}
