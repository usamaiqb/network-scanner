package com.networkscanner.app.ui.screens.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.networkscanner.app.R
import com.networkscanner.app.data.CommonPorts
import com.networkscanner.app.data.PortInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortItem(
    portInfo: PortInfo,
    ipAddress: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val copy = rememberCopyAction()
    val copyActionLabel = stringResource(R.string.action_copy)

    // Same interaction as the IP row, but only for ports a browser can actually open,
    // so other ports don't get an affordance that leads nowhere.
    val webUrl = CommonPorts.webUrlFor(ipAddress, portInfo.port)
    val serviceName = portInfo.serviceNameOrNull
        ?: stringResource(R.string.port_service_unknown)
    val portDescription = stringResource(
        R.string.cd_port_status,
        portInfo.port,
        portInfo.state.name.lowercase()
    )
    // "192.168.1.10:8080" is the pasteable form, not the bare port number.
    val copyLabel = serviceName
    val copyValue = "$ipAddress:${portInfo.port}"

    val interaction = if (webUrl != null) {
        Modifier.combinedClickable(
            onClick = { openUrl(context, webUrl) },
            onLongClick = { copy(copyLabel, copyValue) }
        )
    } else {
        Modifier.pointerInput(copyValue) {
            detectTapGestures(onLongPress = { copy(copyLabel, copyValue) })
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(interaction)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics {
                contentDescription = portDescription
                customActions = listOf(
                    CustomAccessibilityAction(copyActionLabel) {
                        copy(copyLabel, copyValue)
                        true
                    }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Port number badge
        Badge(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Text(
                text = portInfo.port.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // Service name, version and raw banner
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = serviceName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (webUrl != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            portInfo.version?.let { version ->
                Text(
                    text = version,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // The banner is the raw fingerprint; previously it was dropped whenever a
            // version had been parsed out of it.
            portInfo.banner?.takeIf { it != portInfo.version }?.let { banner ->
                Text(
                    text = banner,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Protocol badge
        Badge(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                text = portInfo.protocol,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }

        if (webUrl != null) {
            Icon(
                imageVector = Icons.Rounded.OpenInBrowser,
                contentDescription = stringResource(R.string.cd_open_in_browser),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
