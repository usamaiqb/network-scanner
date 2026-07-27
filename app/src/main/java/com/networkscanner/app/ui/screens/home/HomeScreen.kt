package com.networkscanner.app.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.networkscanner.app.util.ScanPermissions
import kotlinx.coroutines.launch

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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val motionScheme = MaterialTheme.motionScheme

    val scope = rememberCoroutineScope()
    val permissionDeniedMessage = stringResource(R.string.permission_local_network_denied)
    val openSettingsLabel = stringResource(R.string.action_open_settings)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // The optional permissions are asked for at most once, whatever the answer.
        ScanPermissions.markOptionalRequested(context)

        if (ScanPermissions.isLocalNetworkGranted(context)) {
            viewModel.startScan()
        } else {
            // Once the permission is permanently denied the system stops showing the
            // dialog, so point the user at app settings instead of silently scanning
            // into a wall of EPERM failures.
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = permissionDeniedMessage,
                    actionLabel = openSettingsLabel
                )
                if (result == SnackbarResult.ActionPerformed) {
                    ScanPermissions.openAppSettings(context)
                }
            }
        }
    }

    // Android 16+ gates local network access, so re-check the grant on every scan
    // rather than assuming an earlier request succeeded. Below 16 nothing is gated and
    // pendingForScan only asks for the optional permissions, once.
    val startScan: () -> Unit = {
        val pending = ScanPermissions.pendingForScan(context)
        if (pending.isEmpty()) {
            viewModel.startScan()
        } else {
            permissionLauncher.launch(pending)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val autoScan = prefs.getBoolean("auto_scan_on_start", true)
        if (autoScan && uiState is MainViewModel.UiState.Idle) {
            startScan()
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
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.cd_settings_button)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (showFab) {
                ExtendedFloatingActionButton(
                    onClick = startScan,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Radar,
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
