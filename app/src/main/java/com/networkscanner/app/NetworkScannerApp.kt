package com.networkscanner.app

import android.app.Application
import com.networkscanner.app.network.NetworkScanner
import com.networkscanner.app.theme.ThemeManager

/**
 * Application class for NetworkScanner app.
 */
class NetworkScannerApp : Application() {

    /** Shared NetworkScanner instance to avoid duplicate state across ViewModels. */
    val scanner: NetworkScanner by lazy { NetworkScanner(this) }

    override fun onCreate() {
        super.onCreate()

        // Initialize theme manager
        ThemeManager.initialize(this)
    }
}
