package com.networkscanner.app.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import com.networkscanner.app.R
import com.networkscanner.app.data.Device
import com.networkscanner.app.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onDeviceClick: (Device) -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val onlineDevices by viewModel.onlineDevices.collectAsState()
    val offlineDevices by viewModel.offlineDevices.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val networkInfo by viewModel.networkInfo.collectAsState()
    val availableInterfaces by viewModel.availableInterfaces.collectAsState()
    val selectedInterfaceName by viewModel.selectedInterfaceName.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val motionScheme = MaterialTheme.motionScheme

    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val autoScan = prefs.getBoolean("auto_scan_on_start", true)
        if (autoScan && uiState is MainViewModel.UiState.Idle) {
            viewModel.startScan()
        }
    }

    // Refresh network info when app comes to foreground (e.g., returning from Wi-Fi settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshInterfaces()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isScanning = uiState is MainViewModel.UiState.Scanning
    val hasDevices = onlineDevices.isNotEmpty() || offlineDevices.isNotEmpty()
    val showFab = !isScanning

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.cd_settings_button)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (showFab) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startScan() },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Radar,
                            contentDescription = null
                        )
                    },
                    text = { Text(stringResource(R.string.action_scan_network)) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                NetworkInfoBar(
                    networkInfo = networkInfo,
                    interfaces = availableInterfaces,
                    selectedInterfaceName = selectedInterfaceName,
                    onInterfaceSelected = viewModel::onInterfaceSelected,
                    isScanning = isScanning
                )

                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        fadeIn(motionScheme.fastEffectsSpec()) togetherWith
                                fadeOut(motionScheme.fastEffectsSpec())
                    },
                    label = "homeContent",
                    modifier = Modifier.fillMaxSize()
                ) { state ->
                    when {
                        state is MainViewModel.UiState.NoWifi -> {
                            EmptyState(type = EmptyStateType.NO_WIFI)
                        }
                        state is MainViewModel.UiState.Empty -> {
                            EmptyState(type = EmptyStateType.EMPTY)
                        }
                        hasDevices -> {
                            DeviceList(
                                onlineDevices = onlineDevices,
                                offlineDevices = offlineDevices,
                                onDeviceClick = onDeviceClick,
                                getCustomIcon = viewModel::getCustomIcon
                            )
                        }
                        else -> {
                            EmptyState(type = EmptyStateType.IDLE)
                        }
                    }
                }
            }

            // Floating scan progress bar at the bottom
            AnimatedVisibility(
                visible = isScanning,
                enter = slideInVertically(motionScheme.defaultSpatialSpec()) { it } + fadeIn(motionScheme.defaultEffectsSpec()),
                exit = slideOutVertically(motionScheme.defaultSpatialSpec()) { it } + fadeOut(motionScheme.defaultEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                ScanProgressCard(
                    progress = scanProgress,
                    onCancel = { viewModel.cancelScan() }
                )
            }
        }
    }
}
