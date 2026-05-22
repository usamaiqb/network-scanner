package com.networkscanner.app.ui

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceManager
import com.networkscanner.app.ui.navigation.NavGraph
import com.networkscanner.app.ui.theme.NetworkScannerTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "network_scanner_prefs"
        private const val KEY_PERMISSIONS_REQUESTED = "permissions_requested"
    }

    private val optionalPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        markPermissionsRequested()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContextLocale(newBase))
    }

    private fun updateBaseContextLocale(context: Context): Context {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val languageCode = prefs.getString(SettingsViewModel.KEY_LANGUAGE, "system") ?: "system"
        
        if (languageCode == "system") {
            return context
        }

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved language preference on app start
        val savedLanguage = SettingsViewModel.getCurrentLanguage(this)
        SettingsViewModel.applyLanguage(this, savedLanguage)

        enableEdgeToEdge()

        // Request permissions on first launch
        if (!hasRequestedPermissions()) {
            permissionLauncher.launch(optionalPermissions)
        }

        setContent {
            NetworkScannerTheme {
                NavGraph()
            }
        }
    }

    private fun hasRequestedPermissions(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(KEY_PERMISSIONS_REQUESTED, false)
    }

    private fun markPermissionsRequested() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PERMISSIONS_REQUESTED, true).apply()
    }
}
