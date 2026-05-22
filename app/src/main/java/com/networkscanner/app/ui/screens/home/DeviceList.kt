package com.networkscanner.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.networkscanner.app.R
import com.networkscanner.app.data.Device
import com.networkscanner.app.ui.components.SectionHeader
import com.networkscanner.app.ui.components.SegmentedListColumn

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (header != null) {
            item { header() }
        }

        if (onlineDevices.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.section_active),
                    count = onlineDevices.size
                )
            }
            item {
                SegmentedListColumn(
                    items = onlineDevices,
                    modifier = Modifier.animateItem()
                ) { device, shape ->
                    DeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device) },
                        customIconKey = getCustomIcon(device.uniqueId)
                    )
                }
            }
        }

        if (offlineDevices.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.section_offline),
                    count = offlineDevices.size
                )
            }
            item {
                SegmentedListColumn(
                    items = offlineDevices,
                    modifier = Modifier.animateItem()
                ) { device, shape ->
                    DeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device) },
                        customIconKey = getCustomIcon(device.uniqueId)
                    )
                }
            }
        }
    }
}
