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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import android.content.Context
import com.networkscanner.app.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/** The "follow the device" option, which is not a real locale and is localized separately. */
private const val SYSTEM = "system"

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Locale tags selectable in-app, in display order: the special "system" option followed by
 * the tags declared in res/xml/locales_config.xml — the single source of truth, shared with
 * the OS per-app language screen. Parsed directly (rather than via the API 33+
 * android.app.LocaleConfig) so it works down to minSdk 26.
 */
private fun languageCodes(context: Context): List<String> {
    val codes = mutableListOf(SYSTEM)
    context.resources.getXml(R.xml.locales_config).use { parser ->
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "locale") {
                parser.getAttributeValue(ANDROID_NS, "name")?.let(codes::add)
            }
            event = parser.next()
        }
    }
    return codes
}

/**
 * Display label for a language code. "system" is localized; every real locale is shown as its
 * own endonym (the language's name in that language, e.g. "Español"), derived from the tag and
 * capitalized — never hardcoded, so adding a language needs no change here.
 */
@Composable
private fun languageLabel(code: String): String =
    if (code == SYSTEM) {
        stringResource(R.string.language_system_default)
    } else {
        val locale = Locale.forLanguageTag(code)
        locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
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
    val context = LocalContext.current
    val languageCodes = remember(context) { languageCodes(context) }
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
                languageCodes.forEach { code ->
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
