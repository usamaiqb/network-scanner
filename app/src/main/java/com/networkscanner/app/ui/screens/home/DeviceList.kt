package com.networkscanner.app.ui.screens.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.networkscanner.app.R
import com.networkscanner.app.data.Device
import com.networkscanner.app.ui.components.SectionHeader
import com.networkscanner.app.ui.components.segmentShape

@Composable
fun DeviceList(
    onlineDevices: List<Device>,
    offlineDevices: List<Device>,
    onDeviceClick: (Device) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    getCustomIcon: (String) -> String? = { null }
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
    ) {
        if (header != null) {
            item(key = "list_header") { header() }
        }

        if (onlineDevices.isNotEmpty()) {
            deviceSection(
                sectionKey = "active",
                titleRes = R.string.section_active,
                devices = onlineDevices,
                isFirstSection = true,
                onDeviceClick = onDeviceClick,
                getCustomIcon = getCustomIcon
            )
        }

        if (offlineDevices.isNotEmpty()) {
            deviceSection(
                sectionKey = "offline",
                titleRes = R.string.section_offline,
                devices = offlineDevices,
                isFirstSection = onlineDevices.isEmpty(),
                onDeviceClick = onDeviceClick,
                getCustomIcon = getCustomIcon
            )
        }
    }
}

/**
 * One segmented device group: header item plus one lazy item per device, so
 * devices discovered mid-scan animate in individually via [Modifier.animateItem].
 */
private fun LazyListScope.deviceSection(
    sectionKey: String,
    titleRes: Int,
    devices: List<Device>,
    isFirstSection: Boolean,
    onDeviceClick: (Device) -> Unit,
    getCustomIcon: (String) -> String?
) {
    item(key = "header_$sectionKey") {
        SectionHeader(
            title = stringResource(titleRes),
            count = devices.size,
            modifier = Modifier
                .animateItem(
                    fadeInSpec = tween(180),
                    fadeOutSpec = tween(120),
                    placementSpec = tween(200)
                )
                .padding(
                    top = if (isFirstSection) 0.dp else 20.dp,
                    bottom = 8.dp
                )
        )
    }
    itemsIndexed(
        items = devices,
        key = { _, device -> device.uniqueId },
        contentType = { _, _ -> "device" }
    ) { index, device ->
        Surface(
            shape = segmentShape(index, devices.size),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .animateItem(
                    fadeInSpec = tween(180),
                    fadeOutSpec = tween(120),
                    placementSpec = tween(200)
                )
                .padding(bottom = if (index < devices.size - 1) 2.dp else 0.dp)
                .fillMaxWidth()
        ) {
            DeviceCard(
                device = device,
                onClick = { onDeviceClick(device) },
                customIconKey = getCustomIcon(device.uniqueId)
            )
        }
    }
}
