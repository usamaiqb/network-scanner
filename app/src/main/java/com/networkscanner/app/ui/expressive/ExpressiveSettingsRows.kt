package com.networkscanner.app.ui.expressive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
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
 *  - [ActionSettingsRow]   navigates or performs an action, no persistent value
 *  - [InfoSettingsRow]     displays only; no interaction at all (a version string, a total)
 */

/** Material's standard disabled-content opacity. */
private const val DisabledAlpha = 0.38f

private val RowPadding = 16.dp
private val LeadingIconSize = 24.dp
private val LeadingIconGap = 16.dp

/**
 * Base row container. The click lives on an inner Box rather than on Surface(onClick) so
 * the ripple stays inside the segment's asymmetric shape.
 *
 * There is deliberately one content slot and one Surface: branching to call content()
 * from two call sites (clickable vs not) makes Compose treat them as different positions
 * and throws away any state the content holds whenever a row gains or loses its onClick.
 */
@Composable
fun SettingsSurface(
    position: SegmentPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = segmentShape(position),
        color = color
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(RowPadding)
        ) {
            content()
        }
    }
}

/** 24.dp leading glyph tinted `secondary`, with the standard trailing gap. */
@Composable
fun RowLeadingIcon(icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .padding(end = LeadingIconGap)
            .size(LeadingIconSize),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 1f else DisabledAlpha)
        )
    }
}

/** 48.dp circular `secondaryContainer` chip. The list-item and About-card alternative to
 *  [RowLeadingIcon] — heavier, so reserve it for content rows rather than settings. */
@Composable
fun RowLeadingChip(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

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

/** Boolean row. The whole row is the click target, not just the switch. */
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

    SettingsSurface(
        position = position,
        modifier = modifier,
        onClick = if (enabled) {
            { toggle(!checked) }
        } else {
            null
        }
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
            // Row-level click already handles the toggle and its haptic; a null callback
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
    optionIcon: (T) -> ImageVector? = { null }
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
            optionIcon = optionIcon
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
 */
@Composable
fun ActionSettingsRow(
    position: SegmentPosition,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
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
            icon?.let { RowLeadingIcon(it, enabled = enabled) }
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
