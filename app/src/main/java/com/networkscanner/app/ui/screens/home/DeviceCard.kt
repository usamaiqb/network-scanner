package com.networkscanner.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.networkscanner.app.R
import com.networkscanner.app.data.Device
import com.networkscanner.app.ui.components.StatusIndicator
import com.networkscanner.app.ui.expressive.ValueBadge
import com.networkscanner.app.ui.screens.detail.iconKeyToVector

@Composable
fun DeviceCard(
    device: Device,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    customIconKey: String? = null
) {
    val displayIcon = iconKeyToVector(customIconKey) ?: device.deviceType.icon

    val description = buildString {
        append(device.displayName)
        append(", ${device.deviceType.displayName}")
        append(", IP ${device.ipAddress}")
        if (device.isCurrentDevice) append(", This is your current device")
    }

    // Laid out by hand rather than with material3 ListItem: ListItem picks its
    // two-/three-line geometry per layout pass by comparing the supporting
    // slot's first and last baselines. Once a row's vendor line went away (the
    // device lost it, or a recycled lazy-list slot had shown one), that check
    // kept reporting multi-line, leaving one-line rows stuck at the taller,
    // top-aligned three-line size. Here the variant follows the data instead.
    val threeLine = device.vendor != null
    val verticalPadding = if (threeLine) 12.dp else 8.dp

    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .heightIn(min = if (threeLine) 88.dp else 72.dp)
            .padding(start = 16.dp, end = 16.dp, top = verticalPadding, bottom = verticalPadding),
        verticalAlignment = if (threeLine) Alignment.Top else Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = displayIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            StatusIndicator(
                isOnline = device.isOnline,
                modifier = Modifier.padding(2.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (device.isCurrentDevice) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (device.isCurrentDevice) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.this_device),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
            Text(
                text = buildString {
                    append(device.ipAddress)
                    device.macAddress?.let { append("  \u2022  ${it.uppercase()}") }
                },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            device.vendor?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (device.isOnline) {
            device.latencyMs?.let { latency ->
                ValueBadge(
                    text = "${latency}ms",
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
