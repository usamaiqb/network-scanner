package com.networkscanner.app.ui.screens.detail

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Radar
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.networkscanner.app.R
import com.networkscanner.app.ui.DeviceDetailViewModel
import com.networkscanner.app.ui.MainViewModel
import com.networkscanner.app.ui.components.SectionHeader
import com.networkscanner.app.ui.components.SegmentSurface
import com.networkscanner.app.ui.theme.StatusColors

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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isScanning = deepScanState is DeviceDetailViewModel.DeepScanState.Scanning
    val isOnline = device?.isOnline == true

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
                floatingActionButton = {
            if (!isScanning && isOnline) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startDeepScan() },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Radar,
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

                // Device identity info
                item(key = "identity") {
                    val context = LocalContext.current
                    val identityRows = buildList {
                        // IP Address is special - it's clickable
                        add(Triple(stringResource(R.string.label_ip_address), dev.ipAddress, true))

                        // Always show MAC address row (even if unknown)
                        val macValue = dev.macAddress?.let { mac ->
                            val macLabel = if (com.networkscanner.app.util.NetworkUtils.isLocallyAdministeredMac(mac))
                                "${stringResource(R.string.label_mac_address)} (randomized)"
                            else
                                stringResource(R.string.label_mac_address)
                            Triple(macLabel, mac.uppercase(), false)
                        } ?: Triple(stringResource(R.string.label_mac_address), "Unknown", false)
                        add(macValue)

                        dev.vendor?.let {
                            add(Triple(stringResource(R.string.label_vendor), it, false))
                        }
                        if (dev.latencyMs != null && dev.isOnline) {
                            add(Triple(stringResource(R.string.label_latency), "${dev.latencyMs}ms", false))
                        }
                        dev.ttl?.let {
                            val osHint = when (it) {
                                64 -> Triple("TTL", "$it (Linux/Android/iOS)", false)
                                128 -> Triple("TTL", "$it (Windows)", false)
                                255 -> Triple("TTL", "$it (Router/Switch)", false)
                                else -> Triple("TTL", "$it", false)
                            }
                            add(osHint)
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = stringResource(R.string.basic_info),
                            count = identityRows.size
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            identityRows.forEachIndexed { index, (label, value, isClickable) ->
                                SegmentSurface(index = index, count = identityRows.size) {
                                    if (isClickable) {
                                        ClickableInfoRow(
                                            label = label,
                                            value = value,
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, "http://$value".toUri())
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    // Browser not available
                                                }
                                            }
                                        )
                                    } else {
                                        InfoRow(label = label, value = value)
                                    }
                                }
                            }
                        }
                    }
                }

                // Network info
                item(key = "network") {
                    val networkRows = buildList {
                        dev.hostname?.let {
                            add(stringResource(R.string.label_hostname) to it)
                        }
                        add(
                            stringResource(R.string.label_discovered_via) to
                                    dev.discoveredVia.name.replace("_", " ")
                                        .lowercase()
                                        .replaceFirstChar { it.uppercase() }
                        )
                    }
                    val hasServices = dev.mdnsServices.isNotEmpty()
                    val totalCount = networkRows.size + (if (hasServices) 1 else 0)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = stringResource(R.string.network_info),
                            count = totalCount
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            networkRows.forEachIndexed { index, (label, value) ->
                                SegmentSurface(index = index, count = totalCount) {
                                    InfoRow(label = label, value = value)
                                }
                            }
                            if (hasServices) {
                                SegmentSurface(index = totalCount - 1, count = totalCount) {
                                    ServicesRow(services = dev.mdnsServices)
                                }
                            }
                        }
                    }
                }

                // SSDP / UPnP device info section
                val ssdp = dev.ssdpInfo
                if (ssdp != null) {
                    item(key = "device_info") {
                        val deviceInfoRows = buildList {
                            ssdp.friendlyName
                                ?.takeIf { it != dev.hostname }
                                ?.let { add(stringResource(R.string.label_friendly_name) to it) }
                            ssdp.manufacturer
                                ?.takeIf { it != dev.vendor }
                                ?.let { add(stringResource(R.string.label_vendor) to it) }
                            val model = listOfNotNull(ssdp.modelName, ssdp.modelNumber)
                                .joinToString(" ")
                                .takeIf { it.isNotBlank() }
                            model?.let { add(stringResource(R.string.label_model) to it) }
                        }
                        if (deviceInfoRows.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SectionHeader(
                                    title = stringResource(R.string.device_info),
                                    count = deviceInfoRows.size
                                )
                                Spacer(Modifier.height(8.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    deviceInfoRows.forEachIndexed { index, (label, value) ->
                                        SegmentSurface(index = index, count = deviceInfoRows.size) {
                                            InfoRow(label = label, value = value)
                                        }
                                    }
                                }
                            }
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
                    imageVector = Icons.Outlined.Edit,
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
                    imageVector = Icons.Outlined.Edit,
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
