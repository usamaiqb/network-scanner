package com.networkscanner.app.ui.screens.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.networkscanner.app.R
import com.networkscanner.app.data.DeepScanProgress
import com.networkscanner.app.data.DeepScanResult
import com.networkscanner.app.ui.DeviceDetailViewModel
import com.networkscanner.app.ui.components.SectionHeader
import com.networkscanner.app.ui.components.SegmentSurface

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeepScanSection(
    state: DeviceDetailViewModel.DeepScanState,
    progress: DeepScanProgress,
    result: DeepScanResult?,
    isDeviceOnline: Boolean,
    onCancelScan: () -> Unit,
    onFullScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(
                title = stringResource(R.string.deep_scan_results),
                count = result?.openPorts?.size ?: 0
            )
            if (state !is DeviceDetailViewModel.DeepScanState.Scanning && isDeviceOnline) {
                TextButton(onClick = onFullScan) {
                    Text(stringResource(R.string.full_scan))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.deep_scan_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val motionScheme = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(motionScheme.fastEffectsSpec()) togetherWith
                        fadeOut(motionScheme.fastEffectsSpec())
            },
            label = "deepScanState"
        ) { scanState ->
            when (scanState) {
                is DeviceDetailViewModel.DeepScanState.Idle -> {
                    SegmentSurface(index = 0, count = 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isDeviceOnline) stringResource(R.string.tap_to_deep_scan)
                                else stringResource(R.string.device_offline),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is DeviceDetailViewModel.DeepScanState.Scanning -> {
                    SegmentSurface(index = 0, count = 1) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearWavyProgressIndicator(
                                progress = { progress.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = progress.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = buildString {
                                        append(stringResource(R.string.deep_scan_port_progress, progress.portsScanned, progress.portsTotal))
                                        if (progress.openPortsFound > 0) {
                                            append(" \u2022 ")
                                            append(stringResource(R.string.deep_scan_open_count, progress.openPortsFound))
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = onCancelScan) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }
                        }
                    }
                }
                is DeviceDetailViewModel.DeepScanState.Completed -> {
                    DeepScanResults(result = result)
                }
                is DeviceDetailViewModel.DeepScanState.Error -> {
                    SegmentSurface(index = 0, count = 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = scanState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeepScanResults(
    result: DeepScanResult?
) {
    if (result == null) return

    DetailRows {
        // Fixed-size rows first: open ports are unbounded (a full scan can return dozens),
        // so anything placed after them is pushed off-screen.
        result.detectedOs?.let { os ->
            custom {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.label_detected_os),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = os.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.confidence_percent, os.confidence),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        row(
            stringResource(R.string.label_scan_duration),
            stringResource(R.string.duration_seconds, result.scanDurationMs / 1000.0)
        )

        if (result.openPorts.isEmpty()) {
            custom {
                Text(
                    text = stringResource(R.string.no_open_ports),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        } else {
            result.openPorts.forEach { port ->
                custom { PortItem(portInfo = port) }
            }
        }
    }
}
