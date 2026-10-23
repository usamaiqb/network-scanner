package com.networkscanner.app.ui.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Single-choice picker sheet. The expressive alternative to a dropdown or an
 * AlertDialog radio list: options are full-width 20.dp surfaces, and the selected one is
 * a filled `primaryContainer` chip with a trailing check rather than a radio button.
 *
 * Selection is compared through [selectedKey] / [key] rather than by option equality, so
 * it works with enums, data classes and plain strings alike without demanding equals().
 *
 * [optionIcon] is optional and worth supplying when the glyph carries meaning the label
 * repeats slowly - sort directions, theme modes, text alignment.
 *
 * The option list scrolls. A sheet is bounded by the screen, and an unscrollable column
 * silently truncates its tail: network-scanner's language picker grows a row per locale
 * shipped, so the options that fall off the bottom are exactly the ones a user who needs
 * them cannot reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExpressiveOptionSheet(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    selectedKey: Any?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    key: (T) -> Any? = { optionLabel(it) },
    optionSupporting: (T) -> String? = { null },
    optionIcon: (T) -> ImageVector? = { null }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberExpressiveHaptics()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                ExpressiveOptionRow(
                    label = optionLabel(option),
                    selected = key(option) == selectedKey,
                    onClick = {
                        haptics.confirm()
                        onSelect(option)
                        onDismiss()
                    },
                    supporting = optionSupporting(option),
                    icon = optionIcon(option)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * One option in a single-choice list: a full-width 20.dp surface, filled `primaryContainer`
 * with a trailing check when selected and `surfaceContainer` when not.
 *
 * Lives apart from [ExpressiveOptionSheet] because the same treatment is needed inside
 * AlertDialogs, where a bottom sheet is not an option. Reach for it instead of a
 * `RadioButton` row: the filled surface reads as a choice at a glance, where a radio dot
 * makes the reader parse the control before the content.
 *
 * It fires no haptics of its own, so the caller decides - the sheet confirms and dismisses
 * in one gesture, while a dialog usually just updates its own state.
 *
 * The click is a `selectable` with `Role.RadioButton`, not a plain button: the filled surface
 * is the only thing conveying selection visually, so without it a screen reader reads every
 * option identically and the current choice becomes unreachable information.
 */
@Composable
fun ExpressiveOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = modifier
            .fillMaxWidth()
            // Clip before selectable so the ripple honours the 20.dp corners; the Surface
            // clips its own content but not a modifier applied outside it.
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else null,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                supporting?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
