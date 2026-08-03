package com.networkscanner.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

/** Display label for the currently-selected language, used for the settings row value badge. */
@Composable
fun currentLanguageLabel(code: String): String = languageLabel(code)

/**
 * Single-choice language picker rendered as an M3 modal bottom sheet: rounded option
 * surfaces, the selected one filled with primaryContainer and a trailing check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val languageCodes = remember(context) { languageCodes(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = stringResource(R.string.pref_language_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(24.dp)
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            languageCodes.forEach { code ->
                val selected = code == selectedLanguage
                val shape = RoundedCornerShape(20.dp)
                Surface(
                    shape = shape,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onLanguageSelected(code)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = languageLabel(code),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
