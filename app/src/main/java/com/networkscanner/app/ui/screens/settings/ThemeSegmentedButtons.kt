package com.networkscanner.app.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.networkscanner.app.R
import com.networkscanner.app.theme.ThemeManager

@Composable
fun ThemeSegmentedButtons(
    selectedMode: ThemeManager.ThemeMode,
    onModeSelected: (ThemeManager.ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        Triple(ThemeManager.ThemeMode.SYSTEM, stringResource(R.string.theme_system), Icons.Outlined.BrightnessAuto),
        Triple(ThemeManager.ThemeMode.LIGHT, stringResource(R.string.theme_light), Icons.Outlined.LightMode),
        Triple(ThemeManager.ThemeMode.DARK, stringResource(R.string.theme_dark), Icons.Outlined.DarkMode)
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (mode, label, icon) ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = { Icon(icon, contentDescription = null) }
            ) {
                Text(label)
            }
        }
    }
}
