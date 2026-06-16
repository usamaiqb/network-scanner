package com.networkscanner.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.networkscanner.app.ui.navigation.NavGraph
import com.networkscanner.app.ui.theme.NetworkScannerTheme

class MainActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        prefs.edit { putBoolean(KEY_PERMISSIONS_REQUESTED, true) }
    }
}
