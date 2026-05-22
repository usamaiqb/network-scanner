package com.networkscanner.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.networkscanner.app.ui.CustomPortsViewModel
import com.networkscanner.app.ui.DeviceDetailViewModel
import com.networkscanner.app.ui.MainViewModel
import com.networkscanner.app.ui.SettingsViewModel
import com.networkscanner.app.ui.screens.detail.DeviceDetailScreen
import com.networkscanner.app.ui.screens.home.HomeScreen
import com.networkscanner.app.ui.screens.settings.CustomPortsScreen
import com.networkscanner.app.ui.screens.settings.SettingsScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()
    val motionScheme = MaterialTheme.motionScheme

    NavHost(
        navController = navController,
        startDestination = Home,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = motionScheme.defaultSpatialSpec()
            ) + fadeIn(motionScheme.defaultEffectsSpec())
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = motionScheme.defaultSpatialSpec()
            ) + fadeOut(motionScheme.fastEffectsSpec())
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = motionScheme.defaultSpatialSpec()
            ) + fadeIn(motionScheme.defaultEffectsSpec())
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = motionScheme.defaultSpatialSpec()
            ) + fadeOut(motionScheme.fastEffectsSpec())
        }
    ) {
        composable<Home> {
            HomeScreen(
                viewModel = mainViewModel,
                onDeviceClick = { device ->
                    navController.navigate(DeviceDetail(deviceId = device.uniqueId))
                },
                onSettingsClick = {
                    navController.navigate(Settings)
                }
            )
        }

        composable<DeviceDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<DeviceDetail>()
            val detailViewModel: DeviceDetailViewModel = viewModel()
            DeviceDetailScreen(
                deviceId = route.deviceId,
                mainViewModel = mainViewModel,
                viewModel = detailViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Settings> {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCustomPorts = { navController.navigate(CustomPorts) }
            )
        }

        composable<CustomPorts> {
            val customPortsViewModel: CustomPortsViewModel = viewModel()
            val ports by customPortsViewModel.ports.collectAsState()
            CustomPortsScreen(
                ports = ports,
                onNavigateBack = { navController.popBackStack() },
                onAddPort = { port, serviceName ->
                    customPortsViewModel.addPort(port, serviceName)
                },
                onDeletePort = { id ->
                    customPortsViewModel.deletePort(id)
                },
                onTogglePort = { id, enabled ->
                    customPortsViewModel.togglePort(id, enabled)
                }
            )
        }
    }
}
