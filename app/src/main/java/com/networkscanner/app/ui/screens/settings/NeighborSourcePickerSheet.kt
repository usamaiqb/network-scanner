package com.networkscanner.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networkscanner.app.R
import com.networkscanner.app.ui.NeighborProbe
import com.networkscanner.app.util.PrivilegedNeighborSource.Availability
import com.networkscanner.app.util.PrivilegedNeighborSource.Mode

/** Display label for a MAC address source, used for the sheet options and the row value badge. */
@Composable
fun neighborSourceLabel(mode: Mode): String = when (mode) {
    Mode.NONE -> stringResource(R.string.neighbor_source_none)
    Mode.SHIZUKU -> stringResource(R.string.neighbor_source_shizuku)
    Mode.ROOT -> stringResource(R.string.neighbor_source_root)
}

/** One-line status under an option, or null when there is nothing worth saying. */
@Composable
private fun neighborSourceStatus(mode: Mode, availability: Availability?, probe: NeighborProbe): String? = when {
    probe is NeighborProbe.Running && probe.mode == mode ->
        stringResource(R.string.neighbor_status_waiting)
    probe is NeighborProbe.Failed && probe.mode == mode ->
        stringResource(
            if (mode == Mode.ROOT) R.string.neighbor_source_root_unavailable
            else R.string.neighbor_source_shizuku_unavailable
        )
    mode == Mode.NONE || availability == null -> null
    mode == Mode.ROOT -> stringResource(
        if (availability == Availability.UNAVAILABLE) R.string.neighbor_status_root_not_found
        else R.string.neighbor_status_root_will_prompt
    )
    else -> stringResource(
        when (availability) {
            Availability.READY -> R.string.neighbor_status_ready
            Availability.WILL_PROMPT -> R.string.neighbor_status_shizuku_will_prompt
            Availability.DENIED -> R.string.neighbor_status_shizuku_denied
            Availability.OUTDATED -> R.string.neighbor_status_shizuku_outdated
            Availability.UNAVAILABLE -> R.string.neighbor_status_shizuku_not_running
        }
    )
}

/**
 * Single-choice picker for where MAC addresses are read from, styled like [LanguagePickerSheet].
 * Unlike the language picker it stays open while the chosen backend is asked for access, so a
 * refusal is shown inline on that option; the caller dismisses it once the choice is applied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeighborSourcePickerSheet(
    selected: Mode,
    availability: Map<Mode, Availability>,
    probe: NeighborProbe,
    onSelected: (Mode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.pref_neighbor_source_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.pref_neighbor_source_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Mode.entries.forEach { mode ->
                val isSelected = mode == selected
                val probing = probe is NeighborProbe.Running && probe.mode == mode
                val failed = probe is NeighborProbe.Failed && probe.mode == mode
                val status = neighborSourceStatus(mode, availability[mode], probe)
                val shape = RoundedCornerShape(20.dp)
                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
                Surface(
                    shape = shape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .selectable(
                            selected = isSelected,
                            enabled = !probing,
                            role = Role.RadioButton
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onSelected(mode)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = neighborSourceLabel(mode),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = contentColor
                            )
                            status?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (failed) MaterialTheme.colorScheme.error
                                    else contentColor.copy(alpha = 0.72f)
                                )
                            }
                        }
                        when {
                            probing -> CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                            isSelected -> Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
