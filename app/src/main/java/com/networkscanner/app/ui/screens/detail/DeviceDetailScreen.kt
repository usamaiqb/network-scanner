package com.networkscanner.app.ui.screens.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.networkscanner.app.R
import com.networkscanner.app.ui.DeviceDetailViewModel
import com.networkscanner.app.ui.MainViewModel
import com.networkscanner.app.ui.theme.StatusColors
import com.networkscanner.app.util.NetworkUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    mainViewModel: MainViewModel,
    viewModel: DeviceDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val devices by mainViewModel.devices.collectAsState()
    val device by viewModel.device.collectAsState()
    val customName by viewModel.customName.collectAsState()
    val customIconKey by viewModel.customIconKey.collectAsState()
    val deepScanState by viewModel.deepScanState.collectAsState()
    val deepScanResult by viewModel.deepScanResult.collectAsState()
    val deepScanProgress by viewModel.deepScanProgress.collectAsState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditIconDialog by remember { mutableStateOf(false) }
    var showFullScanConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(deviceId, devices) {
        if (devices.isNotEmpty()) {
            viewModel.loadDevice(deviceId, devices)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = LocalHapticFeedback.current
    val isScanning = deepScanState is DeviceDetailViewModel.DeepScanState.Scanning
    val isOnline = device?.isOnline == true

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = device?.displayName ?: stringResource(R.string.device_details),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                scrollBehavior = scrollBehavior
            )
        },
                floatingActionButton = {
            if (!isScanning && isOnline) {
                ExtendedFloatingActionButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.startDeepScan()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Radar,
                            contentDescription = null
                        )
                    },
                    text = { Text(stringResource(R.string.start_deep_scan)) }
                )
            }
        }
    ) { innerPadding ->
        if (device == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            return@Scaffold
        }
        val dev = device!!
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                // Device header
                item {
                    DeviceHeaderCard(
                        device = dev,
                        customName = customName,
                        customIconKey = customIconKey,
                        onEditName = { showEditNameDialog = true },
                        onEditIcon = { showEditIconDialog = true }
                    )
                }

                // Identity - who this device is
                item(key = "identity") {
                    val context = LocalContext.current
                    val ssdp = dev.ssdpInfo
                    // SSDP usually carries the fuller manufacturer string ("NETGEAR, Inc." vs
                    // "Netgear"), so prefer it and render a single vendor row either way.
                    val vendor = ssdp?.manufacturer?.takeIf { it.isNotBlank() } ?: dev.vendor
                    val model = listOfNotNull(ssdp?.modelName, ssdp?.modelNumber)
                        .joinToString(" ")
                        .takeIf { it.isNotBlank() }

                    DetailSection(title = stringResource(R.string.section_identity)) {
                        // IP leads: it is the only always-present field, so the first row
                        // never shifts, and it is the one users act on. The device's name is
                        // already the screen header.
                        custom {
                            ClickableInfoRow(
                                label = stringResource(R.string.label_ip_address),
                                value = dev.ipAddress,
                                onClick = { openUrl(context, "http://${dev.ipAddress}") }
                            )
                        }
                        // Omitted entirely when unknown, like every other optional field.
                        // Android restricts ARP access, so this is null for most devices.
                        dev.macAddress?.let { mac ->
                            val label = if (NetworkUtils.isLocallyAdministeredMac(mac)) {
                                stringResource(R.string.label_mac_address_randomized)
                            } else {
                                stringResource(R.string.label_mac_address)
                            }
                            row(label, mac.uppercase())
                        }
                        rowIfPresent(stringResource(R.string.label_hostname), dev.hostname)
                        rowIfPresent(
                            stringResource(R.string.label_friendly_name),
                            ssdp?.friendlyName?.takeIf {
                                it != dev.hostname && it != dev.displayName
                            }
                        )
                        rowIfPresent(
                            stringResource(R.string.label_netbios_name),
                            dev.netBiosInfo?.hostname?.takeIf { it != dev.hostname }
                        )
                        rowIfPresent(stringResource(R.string.label_vendor), vendor)
                        rowIfPresent(stringResource(R.string.label_model), model)
                        rowIfPresent(
                            stringResource(R.string.label_serial_number),
                            ssdp?.serialNumber
                        )
                    }
                }

                // Network - reachability, how we found it, and what it advertises.
                // Online/offline is already the header badge, so it isn't repeated here.
                item(key = "network") {
                    val context = LocalContext.current
                    val ssdp = dev.ssdpInfo
                    DetailSection(title = stringResource(R.string.section_network)) {
                        if (dev.latencyMs != null && dev.isOnline) {
                            row(
                                stringResource(R.string.label_latency),
                                stringResource(R.string.latency_ms, dev.latencyMs)
                            )
                        }
                        // A cheap guess from any ping. The deep scan's "Detected OS" is the
                        // authoritative answer once a scan has been run.
                        dev.ttl?.let { ttl ->
                            row(stringResource(R.string.label_os_hint), ttlHint(ttl))
                        }
                        row(
                            stringResource(R.string.label_discovered_via),
                            dev.discoveredVia.name.replace("_", " ")
                                .lowercase()
                                .replaceFirstChar { it.uppercase() }
                        )
                        rowIfPresent(
                            stringResource(R.string.label_upnp_type),
                            ssdp?.deviceType?.let { shortUpnpType(it) }
                        )
                        rowIfPresent(
                            stringResource(R.string.label_workgroup),
                            dev.netBiosInfo?.workgroup
                        )
                        ssdp?.locationUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            custom {
                                ClickableInfoRow(
                                    label = stringResource(R.string.label_web_interface),
                                    value = url,
                                    onClick = { openUrl(context, url) }
                                )
                            }
                        }
                        // Services last - the only variable-length row in this section.
                        if (dev.mdnsServices.isNotEmpty()) {
                            custom { ServicesRow(services = dev.mdnsServices) }
                        }
                    }
                }

                // Deep scan section
                item {
                    DeepScanSection(
                        state = deepScanState,
                        progress = deepScanProgress,
                        result = deepScanResult,
                        isDeviceOnline = dev.isOnline,
                        onCancelScan = { viewModel.cancelDeepScan() },
                        onFullScan = { showFullScanConfirmation = true }
                    )
                }
            }
    }

    // Edit name dialog
    if (showEditNameDialog) {
        EditDeviceNameDialog(
            currentName = customName,
            onDismiss = { showEditNameDialog = false },
            onSave = { name ->
                viewModel.saveCustomName(name)
                showEditNameDialog = false
            }
        )
    }

    // Edit icon dialog
    if (showEditIconDialog) {
        EditDeviceIconDialog(
            currentIconKey = customIconKey,
            onDismiss = { showEditIconDialog = false },
            onSave = { iconKey ->
                viewModel.saveCustomIcon(iconKey)
                showEditIconDialog = false
            }
        )
    }

    // Full scan confirmation dialog
    if (showFullScanConfirmation) {
        AlertDialog(
            onDismissRequest = { showFullScanConfirmation = false },
            title = { Text(stringResource(R.string.full_port_scan)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.full_scan_warning))
                    Text(
                        text = stringResource(R.string.full_scan_details),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.startDeepScan(fullScan = true)
                        showFullScanConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.action_start))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullScanConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DeviceHeaderCard(
    device: com.networkscanner.app.data.Device,
    customName: String?,
    customIconKey: String?,
    onEditName: () -> Unit,
    onEditIcon: () -> Unit
) {
    val displayIcon = iconKeyToVector(customIconKey) ?: device.deviceType.icon

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Clickable device icon with extra padding so the edit pencil isn't clipped
        Box(
            modifier = Modifier
                .size(80.dp) // Increased from 72dp to 80dp
                .padding(4.dp), // Added padding so the edit pencil isn't clipped
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onEditIcon),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = displayIcon,
                    contentDescription = stringResource(R.string.choose_device_icon),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
            // Small "edit icon" indicator — no longer clipped
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Name + edit pencil — the pencil doesn't affect text centering
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = customName ?: device.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 32.dp) // room for the edit pencil
            )
            IconButton(
                onClick = onEditName,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.edit_device_name),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (customName != null) {
            Text(
                text = device.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = device.deviceType.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val statusColor = if (device.isOnline) StatusColors.online else StatusColors.offline
            val statusText = if (device.isOnline) stringResource(R.string.status_online)
            else stringResource(R.string.status_offline)
            Badge(
                containerColor = statusColor.copy(alpha = 0.15f),
                contentColor = statusColor
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (device.isCurrentDevice) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.this_device),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Opens [url] in the user's browser, ignoring the case where no browser is installed.
 */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser available - nothing useful to fall back to.
    }
}

/**
 * Renders a ping TTL as an OS hint. This is a cheap guess available from any ping; the
 * deep scan's "Detected OS" row is the authoritative answer when a scan has been run.
 */
@Composable
private fun ttlHint(ttl: Int): String = when (ttl) {
    64 -> stringResource(R.string.ttl_hint_unix, ttl)
    128 -> stringResource(R.string.ttl_hint_windows, ttl)
    255 -> stringResource(R.string.ttl_hint_network, ttl)
    else -> ttl.toString()
}

/**
 * Shortens a UPnP device URN ("urn:schemas-upnp-org:device:MediaRenderer:1") to its
 * device-type segment ("MediaRenderer"). Returns the input unchanged if it isn't a URN.
 */
private fun shortUpnpType(urn: String): String? {
    val parts = urn.split(":")
    val short = if (parts.size >= 4) parts[parts.size - 2] else urn
    return short.takeIf { it.isNotBlank() }
}
