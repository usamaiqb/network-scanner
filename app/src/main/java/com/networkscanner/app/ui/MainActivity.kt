package com.networkscanner.app.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.networkscanner.app.ui.navigation.NavGraph
import com.networkscanner.app.ui.theme.NetworkScannerTheme

/**
 * Permissions are requested from HomeScreen rather than here, so that a single
 * component owns the request (two concurrent requests cancel each other) and a
 * denial of local network access can be surfaced next to the scan that needs it.
 * See [com.networkscanner.app.util.ScanPermissions].
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            NetworkScannerTheme {
                NavGraph()
            }
        }
    }
}
