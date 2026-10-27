package com.networkscanner.app.ui.expressive

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

/**
 * The two rows an About section is usually made of.
 *
 * These are here because two apps wrote them independently and arrived at almost the same
 * code: a `private const val GithubRepoUrl`, a `LocalUriHandler`, a 48.dp chip row whose
 * subtitle is the URL with its scheme stripped and whose trailing glyph is `OpenInNew`, and
 * an identity row with the app name, a version string and a one-line description.
 *
 * Both sit inside a [SegmentedGroup] like any other row pair.
 */

/**
 * A row that opens something outside the app — a repo, a privacy policy, a store listing.
 *
 * The trailing `OpenInNew` is not decoration: it is the only thing distinguishing a row that
 * leaves the app from a row that navigates within it, so it is not optional here.
 */
@Composable
fun ExternalLinkRow(
    position: SegmentPosition,
    label: String,
    url: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val uriHandler = LocalUriHandler.current
    val haptics = rememberExpressiveHaptics()

    SettingsSurface(
        position = position,
        modifier = modifier,
        onClick = {
            haptics.confirm()
            uriHandler.openUri(url)
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowLeadingChip(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    // The scheme is noise; the host and path are what identify the target.
                    text = subtitle ?: url.removePrefix("https://").removePrefix("http://"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The app's own identity: name, version, and optionally what it is in one line.
 *
 * Not clickable — it states rather than does. Pair [versionLabel] with
 * [rememberAppVersionLabel] rather than a hand-written constant.
 */
@Composable
fun AppIdentityRow(
    position: SegmentPosition,
    appName: String,
    versionLabel: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tagline: String? = null
) {
    SettingsSurface(position = position, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowLeadingChip(icon)
            Column {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                tagline?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * The app's version, read from the installed package:
 *
 *  - release: `"Version 1.2.3"`
 *  - debug:   `"Version 1.2.3 (45)"`, or `"Version 1.2.3-debug (45)"` where the project sets
 *    `versionNameSuffix`
 *
 * Read rather than hard-coded on purpose. A constant in the UI drifts from what shipped the
 * moment someone bumps `versionName` and forgets — the About dialog was still claiming 1.0.0
 * several releases later, in every locale.
 *
 * The build number earns its place only where someone reads it back off a screenshot in a bug
 * report, which is a debug build; in a release identity row it is noise next to a version the
 * user might actually recognise. That split is decided here from the installed package's
 * `FLAG_DEBUGGABLE`, because a shared component cannot see any app's `BuildConfig`.
 *
 * Naming the build type is deliberately *not* done here. Gradle's `versionNameSuffix` already
 * puts it in `versionName`, so a component that appends its own word produces
 * "Version 1.0.0-debug (1) - Debug" in any project using the standard mechanism. If you want a
 * debug build to say so, set `versionNameSuffix` on the build type.
 *
 * [prefix] is exposed for localisation; pass an empty string for the bare version.
 */
@Composable
fun rememberAppVersionLabel(
    prefix: String = "Version ",
    unknown: String = "unknown"
): String {
    val context = LocalContext.current
    return remember(context, prefix, unknown) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val name = info.versionName ?: unknown
            val debuggable =
                (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (!debuggable) {
                "$prefix$name"
            } else {
                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
                "$prefix$name ($code)"
            }
        }.getOrDefault("$prefix$unknown")
    }
}
