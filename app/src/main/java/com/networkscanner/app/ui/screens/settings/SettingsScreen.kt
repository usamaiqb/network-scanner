package com.networkscanner.app.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.networkscanner.app.BuildConfig
import com.networkscanner.app.R
import com.networkscanner.app.ui.SettingsViewModel
import com.networkscanner.app.ui.expressive.ActionSettingsRow
import com.networkscanner.app.ui.expressive.CollapsingLargeTopBar
import com.networkscanner.app.ui.expressive.ControlSettingsRow
import com.networkscanner.app.ui.expressive.InfoSettingsRow
import com.networkscanner.app.ui.expressive.PickerSettingsRow
import com.networkscanner.app.ui.expressive.SectionGap
import com.networkscanner.app.ui.expressive.SectionHeader
import com.networkscanner.app.ui.expressive.SegmentPosition
import com.networkscanner.app.ui.expressive.SegmentedGroup
import com.networkscanner.app.ui.expressive.SwitchSettingsRow
import com.networkscanner.app.ui.expressive.ValueBadge
import com.networkscanner.app.ui.expressive.rememberExpressiveScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCustomPorts: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColors by viewModel.dynamicColors.collectAsState()
    val autoScan by viewModel.autoScan.collectAsState()
    val language by viewModel.language.collectAsState()
    val neighborSource by viewModel.neighborSource.collectAsState()
    val neighborAvailability by viewModel.neighborAvailability.collectAsState()
    val neighborProbe by viewModel.neighborProbe.collectAsState()

    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showNeighborSourceSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollBehavior = rememberExpressiveScrollBehavior()
    val supportsDynamic = remember { viewModel.supportsDynamicColors() }
    val languageOptions = rememberLanguageOptions()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CollapsingLargeTopBar(
                title = stringResource(R.string.settings_title),
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                navigateBackDescription = stringResource(R.string.cd_navigate_back)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            // Header and rows live in one item so the header's own 8.dp keeps them together;
            // this arrangement is then the gap *between* sections, not inside them.
            verticalArrangement = Arrangement.spacedBy(SectionGap),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "appearance") {
                SettingsSection(stringResource(R.string.pref_category_appearance)) {
                    ControlSettingsRow(
                        position = SegmentPosition.First,
                        title = stringResource(R.string.pref_theme_title),
                        icon = Icons.Rounded.Palette
                    ) {
                        ThemeSegmentedButtons(
                            selectedMode = themeMode,
                            onModeSelected = { viewModel.setThemeMode(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Dynamic colour is an API 31+ feature, so this row comes and goes and the
                    // rows around it cannot assume a fixed position.
                    if (supportsDynamic) {
                        SwitchSettingsRow(
                            position = SegmentPosition.Middle,
                            title = stringResource(R.string.pref_dynamic_colors_title),
                            subtitle = stringResource(R.string.pref_dynamic_colors_summary),
                            icon = Icons.Rounded.AutoAwesome,
                            checked = dynamicColors,
                            onCheckedChange = { viewModel.setDynamicColors(it) }
                        )
                    }
                    PickerSettingsRow(
                        position = SegmentPosition.Last,
                        title = stringResource(R.string.pref_language_title),
                        icon = Icons.Rounded.Language,
                        options = languageOptions,
                        optionLabel = { it.label },
                        key = { it.code },
                        selected = languageOptions.optionFor(language),
                        onSelect = { option ->
                            if (option.code != language) {
                                // AppCompat reloads resources and recreates the activity once;
                                // no manual recreate() needed.
                                viewModel.setLanguage(option.code)
                            }
                        }
                    )
                }
            }

            item(key = "scanning") {
                SettingsSection(stringResource(R.string.pref_category_scanning)) {
                    SwitchSettingsRow(
                        position = SegmentPosition.First,
                        title = stringResource(R.string.pref_auto_scan_title),
                        subtitle = stringResource(R.string.pref_auto_scan_summary),
                        icon = Icons.Rounded.Autorenew,
                        checked = autoScan,
                        onCheckedChange = { viewModel.setAutoScan(it) }
                    )
                    // Not a PickerSettingsRow: choosing a source asks its backend for access,
                    // so the sheet has to stay open and report a refusal on the option itself.
                    ActionSettingsRow(
                        position = SegmentPosition.Middle,
                        title = stringResource(R.string.pref_neighbor_source_title),
                        subtitle = stringResource(R.string.pref_neighbor_source_summary),
                        icon = Icons.Rounded.Key,
                        onClick = {
                            viewModel.onNeighborPickerOpened()
                            showNeighborSourceSheet = true
                        },
                        trailing = { ValueBadge(text = neighborSourceLabel(neighborSource)) }
                    )
                    ActionSettingsRow(
                        position = SegmentPosition.Last,
                        title = stringResource(R.string.pref_custom_ports_title),
                        subtitle = stringResource(R.string.pref_custom_ports_summary),
                        icon = Icons.Rounded.Tune,
                        onClick = onNavigateToCustomPorts
                    )
                }
            }

            item(key = "about") {
                val versionSummary = buildString {
                    append(BuildConfig.VERSION_NAME)
                    if (BuildConfig.DEBUG) {
                        append(" (${BuildConfig.VERSION_CODE})")
                    }
                }
                val githubUrl = stringResource(R.string.github_url)
                SettingsSection(stringResource(R.string.pref_category_about)) {
                    // States the version and does nothing else, so it takes no click and
                    // shows no ripple the user cannot explain.
                    InfoSettingsRow(
                        position = SegmentPosition.First,
                        title = stringResource(R.string.pref_version_title),
                        subtitle = versionSummary,
                        icon = Icons.Rounded.Info
                    )
                    ActionSettingsRow(
                        position = SegmentPosition.Middle,
                        title = stringResource(R.string.pref_about_title),
                        subtitle = stringResource(R.string.pref_about_summary),
                        icon = Icons.Rounded.Description,
                        onClick = { showAboutDialog = true }
                    )
                    ActionSettingsRow(
                        position = SegmentPosition.Middle,
                        title = stringResource(R.string.view_on_github),
                        subtitle = githubUrl,
                        icon = Icons.Rounded.Code,
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, githubUrl.toUri()))
                            } catch (_: ActivityNotFoundException) {
                                // No browser available
                            }
                        }
                    )
                    ActionSettingsRow(
                        position = SegmentPosition.Last,
                        title = stringResource(R.string.pref_privacy_title),
                        subtitle = stringResource(R.string.pref_privacy_summary),
                        icon = Icons.Rounded.Shield,
                        onClick = { showPrivacyDialog = true }
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
    if (showPrivacyDialog) {
        PrivacyDialog(onDismiss = { showPrivacyDialog = false })
    }
    if (showNeighborSourceSheet) {
        // The sheet stays open while access is requested and closes once the new source is saved.
        val openedWith = remember { neighborSource }
        LaunchedEffect(neighborSource) {
            if (neighborSource != openedWith) showNeighborSourceSheet = false
        }
        NeighborSourcePickerSheet(
            selected = neighborSource,
            availability = neighborAvailability,
            probe = neighborProbe,
            onSelected = { mode ->
                if (mode == neighborSource) {
                    viewModel.cancelNeighborProbe()
                    showNeighborSourceSheet = false
                } else {
                    viewModel.setNeighborSource(mode)
                }
            },
            onDismiss = { showNeighborSourceSheet = false }
        )
    }
}

/** A titled run of connected rows: the header and the group it labels, as one unit. */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = title)
        SegmentedGroup(content = content)
    }
}
