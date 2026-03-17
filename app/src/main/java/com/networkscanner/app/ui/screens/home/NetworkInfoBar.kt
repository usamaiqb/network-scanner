package com.networkscanner.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.networkscanner.app.R
import com.networkscanner.app.data.NetworkInfo

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NetworkInfoBar(
    networkInfo: NetworkInfo?,
    modifier: Modifier = Modifier
) {
    val motionScheme = MaterialTheme.motionScheme

    // Remember the last non-null info so exit animation doesn't show stale/null data
    val lastInfo = remember(networkInfo) { networkInfo } ?: return

    AnimatedVisibility(
        visible = networkInfo != null,
        enter = expandVertically(motionScheme.defaultSpatialSpec()),
        exit = shrinkVertically(motionScheme.defaultSpatialSpec()),
        modifier = modifier
    ) {
        val unknownNetwork = stringResource(R.string.unknown_device)
        val description = stringResource(
            R.string.cd_network_info
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.semantics {
                contentDescription = description
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = lastInfo.ssid ?: unknownNetwork,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = lastInfo.cidrNotation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
