package com.company.admob.sample.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.company.admob.sample.compose.AppContainer
import com.company.admob.sample.compose.ui.screens.AppOpenScreen
import com.company.admob.sample.compose.ui.screens.BannerScreen
import com.company.admob.sample.compose.ui.screens.ConfigurationScreen
import com.company.admob.sample.compose.ui.screens.ConsentScreen
import com.company.admob.sample.compose.ui.screens.DashboardScreen
import com.company.admob.sample.compose.ui.screens.DiagnosticsScreen
import com.company.admob.sample.compose.ui.screens.EventsScreen
import com.company.admob.sample.compose.ui.screens.InitializationScreen
import com.company.admob.sample.compose.ui.screens.InterstitialScreen
import com.company.admob.sample.compose.ui.screens.NativeScreen
import com.company.admob.sample.compose.ui.screens.RewardedInterstitialScreen
import com.company.admob.sample.compose.ui.screens.RewardedScreen
import com.company.admob.sample.compose.ui.theme.PlaygroundTheme
import kotlinx.coroutines.launch

/** The playground's destinations, in drawer order. [route] doubles as the test tag suffix. */
enum class Destination(val route: String, val title: String) {
    Dashboard("dashboard", "Dashboard"),
    Initialization("initialization", "Initialization"),
    Consent("consent", "Consent"),
    Banner("banner", "Banner"),
    Interstitial("interstitial", "Interstitial"),
    Rewarded("rewarded", "Rewarded"),
    RewardedInterstitial("rewarded_interstitial", "Rewarded interstitial"),
    AppOpen("app_open", "App open"),
    Native("native", "Native"),
    Diagnostics("diagnostics", "Diagnostics"),
    Configuration("configuration", "Configuration"),
    Events("events", "Events"),
}

/**
 * Navigation between the 12 screens. Every screen other than the Dashboard is popped when leaving it, so its
 * banners and native ads are disposed (visible as `Destroyed` on the Events screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundApp(container: AppContainer, onRestart: () -> Unit) {
    PlaygroundTheme {
        // Activity scope: shared by every destination, kept across rotation.
        val viewModel: PlaygroundViewModel = viewModel(
            factory = viewModelFactory { initializer { PlaygroundViewModel(container.ads) } },
        )
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val current = Destination.entries.firstOrNull { it.route == backStackEntry?.destination?.route }
            ?: Destination.Dashboard
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val navigate: (Destination) -> Unit = { destination ->
            scope.launch { drawerState.close() }
            navController.navigateTo(destination)
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "CompanyAdMob Playground",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                        Destination.entries.forEach { destination ->
                            NavigationDrawerItem(
                                label = { Text(destination.title) },
                                selected = destination == current,
                                onClick = { navigate(destination) },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .testTag("nav_${destination.route}"),
                            )
                        }
                    }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(current.title, Modifier.testTag("screen_title")) },
                        navigationIcon = {
                            TextButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier
                                    .testTag("open_drawer")
                                    .semantics { contentDescription = "Open navigation" },
                            ) { Text("☰", style = MaterialTheme.typography.titleLarge) }
                        },
                    )
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Destination.Dashboard.route,
                    modifier = Modifier.padding(padding),
                ) {
                    composable(Destination.Dashboard.route) {
                        DashboardScreen(container, viewModel, onNavigate = navigate)
                    }
                    composable(Destination.Initialization.route) { InitializationScreen(container) }
                    composable(Destination.Consent.route) { ConsentScreen(container) }
                    composable(Destination.Banner.route) { BannerScreen() }
                    composable(Destination.Interstitial.route) { InterstitialScreen(container, viewModel) }
                    composable(Destination.Rewarded.route) { RewardedScreen(container, viewModel) }
                    composable(Destination.RewardedInterstitial.route) {
                        RewardedInterstitialScreen(container, viewModel)
                    }
                    composable(Destination.AppOpen.route) { AppOpenScreen(container, viewModel) }
                    composable(Destination.Native.route) { NativeScreen(container) }
                    composable(Destination.Diagnostics.route) { DiagnosticsScreen(container) }
                    composable(Destination.Configuration.route) { ConfigurationScreen(container, onRestart) }
                    composable(Destination.Events.route) { EventsScreen(container) }
                }
            }
        }
    }
}

/** Opens [destination] on top of the Dashboard, popping whatever screen was open (its ads are destroyed). */
private fun NavHostController.navigateTo(destination: Destination) {
    navigate(destination.route) {
        popUpTo(Destination.Dashboard.route) { inclusive = destination == Destination.Dashboard }
        launchSingleTop = true
    }
}
