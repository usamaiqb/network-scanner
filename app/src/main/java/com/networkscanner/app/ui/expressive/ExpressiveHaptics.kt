package com.networkscanner.app.ui.expressive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Semantic haptics for the expressive component set.
 *
 * Named by *intent* rather than by platform constant, for two reasons: call sites read
 * better, and the Confirm / ToggleOn / ToggleOff / SegmentTick constants used below only
 * exist on Compose UI 1.8+ (Compose BOM 2025.04.00 and newer). Keeping every reference
 * behind this one file means a project on an older BOM has exactly one place to adapt
 * instead of a dozen scattered call sites.
 *
 * Requires Compose UI 1.8+.
 */
class ExpressiveHaptics(private val haptics: HapticFeedback) {

    /** A selection was committed: picker option, button press, confirmed action. */
    fun confirm() = haptics.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A switch or toggle moved. A distinct feel per direction is deliberate. */
    fun toggle(on: Boolean) = haptics.performHapticFeedback(
        if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
    )

    /**
     * A slider crossed a step boundary. Fire only when the *snapped* value changes:
     * firing per frame turns the whole gesture into one continuous buzz.
     */
    fun step() = haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** A long-press gesture was recognised. */
    fun longPress() = haptics.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
@ReadOnlyComposable
fun rememberExpressiveHaptics(): ExpressiveHaptics = ExpressiveHaptics(LocalHapticFeedback.current)
