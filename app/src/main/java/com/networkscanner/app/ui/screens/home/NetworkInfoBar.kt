package com.networkscanner.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.SignalCellular4Bar
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.networkscanner.app.R
import com.networkscanner.app.data.NetworkInfo
import com.networkscanner.app.util.InterfaceType
import com.networkscanner.app.util.NetworkInterfaceOption

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NetworkInfoBar(
    networkInfo: NetworkInfo?,
    interfaces: List<NetworkInterfaceOption>,
    selectedInterfaceName: String?,
    onInterfaceSelected: (String) -> Unit,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    if (interfaces.isEmpty() && networkInfo == null) return

    val motionScheme = MaterialTheme.motionScheme
    var menuExpanded by remember { mutableStateOf(false) }

    val selectedInterface = interfaces.firstOrNull { it.name == selectedInterfaceName }
    val selectedType = selectedInterface?.type
    val selectedNetworkInfo = networkInfo?.takeIf { it.interfaceName == selectedInterfaceName }

    val interfaceIcon = when (selectedType) {
        InterfaceType.WIFI -> Icons.Outlined.Wifi
        InterfaceType.ETHERNET -> Icons.Outlined.SettingsEthernet
        InterfaceType.VPN -> Icons.Outlined.VpnKey
        InterfaceType.CELLULAR -> Icons.Outlined.SignalCellular4Bar
        InterfaceType.OTHER, null -> Icons.Outlined.Public
    }

    val networkName = selectedNetworkInfo?.ssid
        ?: selectedType?.let { interfaceTypeLabel(it) }
        ?: stringResource(R.string.unknown_device)

    val cidrText = selectedNetworkInfo?.cidrNotation
        ?: selectedInterface?.ipAddress
        ?: stringResource(R.string.no_active_interfaces)

    AnimatedVisibility(
        visible = networkInfo != null || interfaces.isNotEmpty(),
        enter = expandVertically(motionScheme.defaultSpatialSpec()),
        exit = shrinkVertically(motionScheme.defaultSpatialSpec()),
        modifier = modifier
    ) {
        val description = stringResource(R.string.cd_network_info)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.semantics { contentDescription = description }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = interfaceIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = networkName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    Row(
                        modifier = if (interfaces.size > 1 && !isScanning) {
                            Modifier.clickable { menuExpanded = true }
                        } else Modifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cidrText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        if (interfaces.size > 1) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDropDown,
                                contentDescription = stringResource(R.string.cd_interface_selector),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        interfaces.forEach { option ->
                            val optionIcon = when (option.type) {
                                InterfaceType.WIFI -> Icons.Outlined.Wifi
                                InterfaceType.ETHERNET -> Icons.Outlined.SettingsEthernet
                                InterfaceType.VPN -> Icons.Outlined.VpnKey
                                InterfaceType.CELLULAR -> Icons.Outlined.SignalCellular4Bar
                                InterfaceType.OTHER -> Icons.Outlined.Public
                            }
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = optionIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                text = {
                                    Text("${interfaceTypeLabel(option.type)} — ${option.ipAddress}")
                                },
                                onClick = {
                                    onInterfaceSelected(option.name)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun interfaceTypeLabel(type: InterfaceType): String {
    val labelResId = when (type) {
        InterfaceType.WIFI -> R.string.interface_type_wifi
        InterfaceType.ETHERNET -> R.string.interface_type_ethernet
        InterfaceType.VPN -> R.string.interface_type_vpn
        InterfaceType.CELLULAR -> R.string.interface_type_cellular
        InterfaceType.OTHER -> R.string.interface_type_network
    }
    return stringResource(labelResId)
}
