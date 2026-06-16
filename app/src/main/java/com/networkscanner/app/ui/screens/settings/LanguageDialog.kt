package com.networkscanner.app.ui.screens.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.networkscanner.app.R

/**
 * Supported in-app languages, in display order. Endonyms (the name of a language
 * in that language) are intentionally not translated; only the "system" option
 * is localized.
 */
private val LANGUAGE_CODES = listOf("system", "en", "ru")

@Composable
private fun languageLabel(code: String): String = when (code) {
    "system" -> stringResource(R.string.language_system_default)
    "en" -> "English"
    "ru" -> "Русский"
    else -> code
}

/** Display label for the currently-selected language, used for the settings row summary. */
@Composable
fun currentLanguageLabel(code: String): String = languageLabel(code)

/**
 * Single-choice language picker dialog (Material 3 radio-button list). Replaces the
 * segmented button so the list can grow beyond the 2–5 options segmented buttons support.
 */
@Composable
fun LanguageDialog(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.pref_language_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup()
            ) {
                LANGUAGE_CODES.forEach { code ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (code == selectedLanguage),
                                onClick = { onLanguageSelected(code) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (code == selectedLanguage),
                            onClick = null
                        )
                        Text(
                            text = languageLabel(code),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
