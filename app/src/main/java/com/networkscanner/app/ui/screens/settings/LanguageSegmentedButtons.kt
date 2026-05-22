package com.networkscanner.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LanguageSegmentedButtons(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val languages = listOf(
        "system" to "System",
        "en" to "English",
        "ru" to "Русский"
    )

    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
        space = 8.dp
    ) {
        languages.forEachIndexed { index, (code, label) ->
            SegmentedButton(
                selected = selectedLanguage == code,
                onClick = { onLanguageSelected(code) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = languages.size
                )
            ) {
                Text(label)
            }
        }
    }
}
