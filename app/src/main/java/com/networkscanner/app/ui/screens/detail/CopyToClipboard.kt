package com.networkscanner.app.ui.screens.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import com.networkscanner.app.R

/**
 * Copies a labelled value to the clipboard, with a haptic tick and a confirmation.
 *
 * Android 13 shows its own clipboard preview, so a toast there would report the copy
 * twice; below that the app has to say so itself.
 */
@Composable
fun rememberCopyAction(): (String, String) -> Unit {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    return remember(context, haptics) {
        { label: String, value: String ->
            val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.copied_to_clipboard),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
