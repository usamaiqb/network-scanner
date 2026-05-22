package com.networkscanner.app.ui.screens.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

data class IconOption(val key: String, val icon: ImageVector, val label: String)

val DEVICE_ICON_OPTIONS = listOf(
    IconOption("SMARTPHONE",   Icons.Outlined.Smartphone,     "Phone"),
    IconOption("LAPTOP",       Icons.Outlined.Laptop,         "Laptop"),
    IconOption("DESKTOP",      Icons.Outlined.DesktopWindows, "Desktop"),
    IconOption("TABLET",       Icons.Outlined.Tablet,         "Tablet"),
    IconOption("ROUTER",       Icons.Outlined.Router,         "Router"),
    IconOption("TV",           Icons.Outlined.Tv,             "TV"),
    IconOption("PRINTER",      Icons.Outlined.Print,          "Printer"),
    IconOption("SERVER",       Icons.Outlined.Dns,            "Server"),
    IconOption("NAS",          Icons.Outlined.Storage,        "NAS"),
    IconOption("CAMERA",       Icons.Outlined.Videocam,       "Camera"),
    IconOption("SPEAKER",      Icons.Outlined.Speaker,        "Speaker"),
    IconOption("GAME_CONSOLE", Icons.Outlined.SportsEsports,  "Console"),
    IconOption("SMART_HOME",   Icons.Outlined.Home,           "Smart Home"),
    IconOption("WEARABLE",     Icons.Outlined.Watch,          "Wearable"),
    IconOption("UNKNOWN",      Icons.Outlined.DevicesOther,   "Other"),
)

fun iconKeyToVector(key: String?): ImageVector? =
    DEVICE_ICON_OPTIONS.find { it.key == key }?.icon
