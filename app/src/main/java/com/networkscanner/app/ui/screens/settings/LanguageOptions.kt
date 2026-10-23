package com.networkscanner.app.ui.screens.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.networkscanner.app.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/** The "follow the device" option, which is not a real locale and is localized separately. */
private const val SYSTEM = "system"

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * A selectable language: the locale tag persisted in settings, plus what to show for it.
 *
 * The label is resolved up front rather than on demand because the picker is now a
 * [com.networkscanner.app.ui.expressive.PickerSettingsRow], whose `optionLabel` is a plain
 * lambda and so cannot call `stringResource`.
 */
data class LanguageOption(val code: String, val label: String)

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
private fun languageLabel(code: String, systemLabel: String): String =
    if (code == SYSTEM) {
        systemLabel
    } else {
        val locale = Locale.forLanguageTag(code)
        locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

/** Every selectable language, labelled and in display order. */
@Composable
fun rememberLanguageOptions(): List<LanguageOption> {
    val context = LocalContext.current
    val systemLabel = stringResource(R.string.language_system_default)
    return remember(context, systemLabel) {
        languageCodes(context).map { LanguageOption(it, languageLabel(it, systemLabel)) }
    }
}

/**
 * The option matching [code], falling back to the first (the system default) for a tag that is
 * no longer shipped — which is what a locale removed from locales_config leaves behind in
 * saved settings.
 */
fun List<LanguageOption>.optionFor(code: String): LanguageOption =
    firstOrNull { it.code == code } ?: first()
