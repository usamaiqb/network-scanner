package com.networkscanner.app.ui.expressive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The containers and leading glyphs every settings row is built from. The rows themselves are
 * in ExpressiveSettingsRows.kt; these are public on their own for one-off rows that don't fit
 * one of the six types (AboutRows builds its cards from them).
 *
 *  - [SettingsSurface]        a segment-shaped row container, optionally clickable
 *  - [ToggleSettingsSurface]  the same container for a row that *is* a switch
 *  - [RowLeadingIcon]         24.dp `secondary` glyph, the settings-row default
 *  - [RowLeadingChip]         48.dp `secondaryContainer` chip, for content rows
 */

/** Material's standard disabled-content opacity. Internal: the rows' title block reads it too. */
internal const val DisabledAlpha = 0.38f

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
private fun SegmentRowSurface(
    position: SegmentPosition,
    modifier: Modifier = Modifier,
    interactionModifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = segmentShape(position),
        color = color
    ) {
        Box(modifier = interactionModifier.padding(RowPadding)) {
            content()
        }
    }
}

@Composable
fun SettingsSurface(
    position: SegmentPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    SegmentRowSurface(
        position = position,
        modifier = modifier,
        color = color,
        interactionModifier = if (onClick != null) {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            Modifier
        },
        content = content
    )
}

/**
 * [SettingsSurface] for a row that *is* a switch: `Modifier.toggleable` with `Role.Switch`
 * rather than `clickable` with `Role.Button`.
 *
 * The distinction is not cosmetic. A `clickable` row wrapping a switch with a null callback
 * has the one click target it should have, but it announces itself as a button and reports no
 * on/off state at all — a screen reader reads "Use dynamic colors, button" and the user cannot
 * tell whether the setting is on. `toggleable` keeps the single target *and* the state.
 */
@Composable
fun ToggleSettingsSurface(
    position: SegmentPosition,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    SegmentRowSurface(
        position = position,
        modifier = modifier,
        color = color,
        interactionModifier = if (onCheckedChange != null) {
            Modifier.toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
        } else {
            Modifier
        },
        content = content
    )
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
 *  [RowLeadingIcon] — heavier, so reserve it for content rows rather than settings.
 *
 *  Carries the same trailing gap [RowLeadingIcon] does, so the two are interchangeable in a
 *  row's leading position and a group that mixes them still lines its text up. */
@Composable
fun RowLeadingChip(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .padding(end = LeadingIconGap)
            .size(48.dp),
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
