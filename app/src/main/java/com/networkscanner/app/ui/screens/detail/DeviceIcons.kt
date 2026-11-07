package com.networkscanner.app.ui.screens.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopMac
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.ui.graphics.vector.ImageVector

data class IconOption(val key: String, val icon: ImageVector, val label: String)

val DEVICE_ICON_OPTIONS = listOf(
    IconOption("SMARTPHONE",   Icons.Rounded.Smartphone,     "Phone"),
    IconOption("LAPTOP",       Icons.Rounded.Laptop,         "Laptop"),
    IconOption("DESKTOP",      Icons.Rounded.DesktopWindows, "Desktop"),
    IconOption("MAC_DESKTOP",  Icons.Rounded.DesktopMac,     "Mac"),
    IconOption("MAC_LAPTOP",   Icons.Rounded.LaptopMac,      "Macbook"),
    IconOption("TABLET",       Icons.Rounded.Tablet,         "Tablet"),
    IconOption("ROUTER",       Icons.Rounded.Router,         "Router"),
    IconOption("TV",           Icons.Rounded.Tv,             "TV"),
    IconOption("PRINTER",      Icons.Rounded.Print,          "Printer"),
    IconOption("SERVER",       Icons.Rounded.Dns,            "Server"),
    IconOption("NAS",          Icons.Rounded.Storage,        "NAS"),
    IconOption("CAMERA",       Icons.Rounded.Videocam,       "Camera"),
    IconOption("SPEAKER",      Icons.Rounded.Speaker,        "Speaker"),
    IconOption("GAME_CONSOLE", Icons.Rounded.SportsEsports,  "Console"),
    IconOption("SMART_HOME",   Icons.Rounded.Home,           "Smart Home"),
    IconOption("WEARABLE",     Icons.Rounded.Watch,          "Wearable"),
    IconOption("UNKNOWN",      Icons.Rounded.DevicesOther,   "Other"),
)

fun iconKeyToVector(key: String?): ImageVector? =
    DEVICE_ICON_OPTIONS.find { it.key == key }?.icon
