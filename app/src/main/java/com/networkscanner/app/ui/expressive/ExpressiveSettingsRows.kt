package com.networkscanner.app.ui.expressive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The row vocabulary that sits inside a [SegmentedGroup]. Every row is a
 * [SegmentSurface] with 16.dp internal padding, a 24.dp leading icon tinted `secondary`,
 * a titleMedium title and a bodyMedium subtitle 4.dp below it.
 *
 * Pick the row by the control it carries:
 *  - [SwitchSettingsRow]   boolean
 *  - [PickerSettingsRow]   one-of-N, opens an [ExpressiveOptionSheet]
 *  - [SliderSettingsRow]   continuous or stepped numeric
 *  - [ControlSettingsRow]  anything else (segmented buttons, chips, a custom control)
 *  - [ActionRow]           navigates or performs an action, no persistent value
 *  - [InfoSettingsRow]     displays only; no interaction at all (a version string, a total)
 */

@Composable
private fun RowTitleBlock(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else DisabledAlpha
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor.copy(alpha = alpha)
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}

/**
 * Boolean row. The whole row is the target, not just the switch, and it reports itself as a
 * switch with its state — see [ToggleSettingsSurface] for why that needs `toggleable` rather
 * than `clickable`.
 */
@Composable
fun SwitchSettingsRow(
    position: SegmentPosition,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val haptics = rememberExpressiveHaptics()
    val toggle: (Boolean) -> Unit = { newValue ->
        haptics.toggle(newValue)
        onCheckedChange(newValue)
    }

    ToggleSettingsSurface(
        position = position,
        checked = checked,
        onCheckedChange = if (enabled) toggle else null,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { RowLeadingIcon(it, enabled = enabled) }
            RowTitleBlock(
                title = title,
                subtitle = subtitle,
                modifier = Modifier.weight(1f),
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(12.dp))
            // The row owns the gesture, the haptic and the reported state; a null callback
            // here keeps the switch from registering a second, competing click target.
            ExpressiveSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

/**
 * One-of-N row. Shows the current value as a [ValueBadge] and opens an
 * [ExpressiveOptionSheet] on tap.
 *
 * Every option-presentation parameter is forwarded to the sheet. That matters: the same
 * choice is often reachable both from a settings row and from a standalone sheet elsewhere
 * in an app, and if this wrapper cannot express what the sheet supports, the two entry
 * points drift into showing the same options differently.
 */
@Composable
fun <T> PickerSettingsRow(
    position: SegmentPosition,
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    key: (T) -> Any? = { optionLabel(it) },
    optionSupporting: (T) -> String? = { null },
    optionIcon: (T) -> ImageVector? = { null },
    selectedDescription: String = "Selected"
) {
    var showSheet by remember { mutableStateOf(false) }

    SettingsSurface(
        position = position,
        modifier = modifier,
        onClick = { showSheet = true }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { RowLeadingIcon(it) }
            Column(modifier = Modifier.weight(1f)) {
                RowTitleBlock(title = title, subtitle = subtitle)
                Spacer(modifier = Modifier.height(10.dp))
                ValueBadge(text = optionLabel(selected))
            }
        }
    }

    if (showSheet) {
        ExpressiveOptionSheet(
            title = title,
            options = options,
            optionLabel = optionLabel,
            selectedKey = key(selected),
            onSelect = onSelect,
            onDismiss = { showSheet = false },
            key = key,
            optionSupporting = optionSupporting,
            optionIcon = optionIcon,
            selectedDescription = selectedDescription
        )
    }
}

/**
 * Numeric row: title on the left, live value on the right, slider underneath.
 *
 * Draft-then-commit is the caller's job and is not optional: hold the in-flight value in
 * a local `rememberSaveable` float, pass it as [value], and persist only in
 * [onValueChangeFinished]. Writing to DataStore from [onValueChange] fires once per frame
 * of the drag.
 *
 * [steps] counts the *interior* stops, so N intervals means `steps = N - 1`.
 */
@Composable
fun SliderSettingsRow(
    position: SegmentPosition,
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    steps: Int = 0
) {
    val haptics = rememberExpressiveHaptics()

    SettingsSurface(position = position, modifier = modifier) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let { RowLeadingIcon(it) }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = { newValue ->
                    // Slider already snaps to steps, so an inequality here fires the tick
                    // once per crossed stop rather than once per frame.
                    if (newValue != value) haptics.step()
                    onValueChange(newValue)
                },
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    thumbColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * Row with an arbitrary control laid out below the title block: a
 * SingleChoiceSegmentedButtonRow, a chip row, a time picker, anything. The row itself is
 * not clickable — the control owns interaction.
 */
@Composable
fun ControlSettingsRow(
    position: SegmentPosition,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    control: @Composable () -> Unit
) {
    SettingsSurface(position = position, modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let { RowLeadingIcon(it) }
                RowTitleBlock(title = title, subtitle = subtitle)
            }
            Spacer(modifier = Modifier.height(12.dp))
            control()
        }
    }
}

/**
 * Row that navigates or performs an action rather than holding a value. [trailing] is the
 * affordance slot — a chevron, an external-link glyph, a [ValueBadge], or nothing.
 * [titleColor] carries destructive intent: pass `error` for a delete or clear-all row.
 *
 * [leading] replaces the [icon] when the row needs something other than the standard 24.dp
 * glyph — in practice a [RowLeadingChip], so an action row can sit in an About group beside
 * [ExternalLinkRow] and [AppIdentityRow] without the group's text edge stepping sideways.
 * Whatever you pass owns its own trailing gap; both [RowLeadingIcon] and [RowLeadingChip] do.
 *
 * It is the one row in this file without `Settings` in its name, because it is the one that
 * earns its keep outside a settings screen: any list whose items open something is a column
 * of these.
 */
@Composable
fun ActionRow(
    position: SegmentPosition,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val haptics = rememberExpressiveHaptics()

    SettingsSurface(
        position = position,
        modifier = modifier,
        onClick = if (enabled) {
            {
                haptics.confirm()
                onClick()
            }
        } else {
            null
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
            } else {
                icon?.let { RowLeadingIcon(it, enabled = enabled) }
            }
            RowTitleBlock(
                title = title,
                subtitle = subtitle,
                modifier = Modifier.weight(1f),
                titleColor = titleColor,
                enabled = enabled
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

/**
 * Display-only row: a version string, a computed total, a status line. Carries no click
 * and no control, so it takes no [SettingsSurface] onClick — which is what keeps it from
 * showing a ripple the user cannot explain.
 */
@Composable
fun InfoSettingsRow(
    position: SegmentPosition,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    SettingsSurface(position = position, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { RowLeadingIcon(it) }
            RowTitleBlock(
                title = title,
                subtitle = subtitle,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailing()
            }
        }
    }
}
