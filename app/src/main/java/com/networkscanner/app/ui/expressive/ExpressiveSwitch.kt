package com.networkscanner.app.ui.expressive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * M3 switch whose thumb cross-fades a Check/Close icon with the checked state.
 *
 * It fires no haptics of its own: inside a [SwitchSettingsRow] the whole row is the target and
 * the row owns the haptic, so doing it here as well would double-buzz. A `null`
 * [onCheckedChange] also removes this switch from the accessibility tree, which is why the row
 * must carry the toggle state itself — see [ToggleSettingsSurface].
 * Standalone callers should pair it with [ExpressiveHaptics.toggle].
 *
 * **The checked icon is tinted `primary`, not left to the M3 default.** Material's
 * `SelectedIconColor` token is `onPrimaryContainer` while `SelectedHandleColor` is `onPrimary`,
 * a pair that only contrasts in schemes where `onPrimaryContainer` happens to be dark. Where it
 * is light — a dynamic-colour scheme, or any custom palette with a mid-tone `primaryContainer` —
 * the two resolve to the same white and the Check becomes invisible: measured at **1.00:1** on a
 * light dynamic scheme, where the thumb and its glyph were both `#FFFFFF`. `primary` is the
 * defined counterpart of the `onPrimary` handle, so it is guaranteed to contrast in every
 * scheme (9.97:1 on that same one) instead of only in the baseline ones.
 *
 * Only that one entry is overridden. The unchecked pair (`outline` handle,
 * `surfaceContainerHighest` icon) and the disabled pair (`surface` handle, `onSurface` icon) are
 * both sound, so they keep their defaults.
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedIconColor = MaterialTheme.colorScheme.primary
        ),
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "switchThumbIcon"
            ) { isChecked ->
                Icon(
                    imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        }
    )
}
