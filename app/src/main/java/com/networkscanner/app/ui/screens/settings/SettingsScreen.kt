package com.networkscanner.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import com.networkscanner.app.BuildConfig
import com.networkscanner.app.R
import com.networkscanner.app.ui.SettingsViewModel
import com.networkscanner.app.ui.components.SegmentSurface

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

    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val supportsDynamic = remember { viewModel.supportsDynamicColors() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Appearance section
            item {
                SettingsCategoryHeader(stringResource(R.string.pref_category_appearance))
            }
            item {
                val appearanceCount = if (supportsDynamic) 3 else 2
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SegmentSurface(index = 0, count = appearanceCount) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.pref_theme_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            ThemeSegmentedButtons(
                                selectedMode = themeMode,
                                onModeSelected = { viewModel.setThemeMode(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            )
                        }
                    }
                    if (supportsDynamic) {
                        SegmentSurface(index = 1, count = appearanceCount) {
                            SwitchSettingItem(
                                title = stringResource(R.string.pref_dynamic_colors_title),
                                summary = stringResource(R.string.pref_dynamic_colors_summary),
                                checked = dynamicColors,
                                onCheckedChange = { viewModel.setDynamicColors(it) }
                            )
                        }
                    }
                    SegmentSurface(index = appearanceCount - 1, count = appearanceCount) {
                        ClickableSettingItem(
                            title = stringResource(R.string.pref_language_title),
                            summary = currentLanguageLabel(language),
                            onClick = { showLanguageDialog = true }
                        )
                    }
                }
            }

            // Scanning section
            item {
                SettingsCategoryHeader(stringResource(R.string.pref_category_scanning))
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SegmentSurface(index = 0, count = 2) {
                        SwitchSettingItem(
                            title = stringResource(R.string.pref_auto_scan_title),
                            summary = stringResource(R.string.pref_auto_scan_summary),
                            checked = autoScan,
                            onCheckedChange = { viewModel.setAutoScan(it) }
                        )
                    }
                    SegmentSurface(index = 1, count = 2) {
                        ClickableSettingItem(
                            title = stringResource(R.string.pref_custom_ports_title),
                            summary = stringResource(R.string.pref_custom_ports_summary),
                            onClick = onNavigateToCustomPorts
                        )
                    }
                }
            }

            // About section
            item {
                SettingsCategoryHeader(stringResource(R.string.pref_category_about))
            }
            item {
                val versionSummary = buildString {
                    append(BuildConfig.VERSION_NAME)
                    if (BuildConfig.DEBUG) {
                        append(" (${BuildConfig.VERSION_CODE}) - Debug")
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SegmentSurface(index = 0, count = 4) {
                        ClickableSettingItem(
                            title = stringResource(R.string.pref_version_title),
                            summary = versionSummary,
                            onClick = {}
                        )
                    }
                    SegmentSurface(index = 1, count = 4) {
                        ClickableSettingItem(
                            title = stringResource(R.string.pref_about_title),
                            summary = stringResource(R.string.pref_about_summary),
                            onClick = { showAboutDialog = true }
                        )
                    }
                    SegmentSurface(index = 2, count = 4) {
                        val githubUrl = stringResource(R.string.github_url)
                        ClickableSettingItem(
                            title = stringResource(R.string.view_on_github),
                            summary = githubUrl,
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, githubUrl.toUri())
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    // No browser available
                                }
                            }
                        )
                    }
                    SegmentSurface(index = 3, count = 4) {
                        ClickableSettingItem(
                            title = stringResource(R.string.pref_privacy_title),
                            summary = stringResource(R.string.pref_privacy_summary),
                            onClick = { showPrivacyDialog = true }
                        )
                    }
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
    if (showLanguageDialog) {
        LanguageDialog(
            selectedLanguage = language,
            onLanguageSelected = { newLanguage ->
                showLanguageDialog = false
                if (newLanguage != language) {
                    // AppCompat reloads resources and recreates the activity once;
                    // no manual recreate() needed.
                    viewModel.setLanguage(newLanguage)
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}
