package com.company.admob.sample.compose.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.company.admob.sample.compose.R
import com.company.admob.sample.compose.details.DetailsScreen
import com.company.admob.sample.compose.feed.FeedScreen
import com.company.admob.sample.compose.home.CoinsViewModel
import com.company.admob.sample.compose.home.HomeScreen
import com.company.admob.sample.compose.settings.SettingsScreen
import com.company.admob.sample.compose.styles.NativeStylesScreen

/** The app's destinations. The first three are in the bottom bar. */
enum class Destination(val route: String, @param:StringRes val label: Int, @param:DrawableRes val icon: Int?) {
    Home("home", R.string.nav_home, R.drawable.ic_nav_home),
    Feed("feed", R.string.nav_feed, R.drawable.ic_nav_feed),
    Settings("settings", R.string.nav_settings, R.drawable.ic_nav_settings),
    Details("details", R.string.details_title, null),
    NativeStyles("native_styles", R.string.native_styles, null),
}

/**
 * The navigation graph. Each destination is its own `ViewModelStoreOwner` and lifecycle: its native ads are kept
 * across rotation in its ViewModel store and destroyed when it's popped, and its banners are destroyed when it leaves
 * the screen. [CoinsViewModel] is the Activity's, so a reward delivered after its screen was left is still granted.
 */
@Composable
fun SampleApp() {
    val navController = rememberNavController()
    val coins: CoinsViewModel = viewModel()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            if (Destination.entries.any { it.icon != null && it.route == route }) BottomBar(navController, route)
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Destination.Home.route) {
                HomeScreen(coins, onOpenDetails = { navController.navigate(Destination.Details.route) })
            }
            composable(Destination.Feed.route) { FeedScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(onOpenNativeStyles = { navController.navigate(Destination.NativeStyles.route) })
            }
            composable(Destination.Details.route) { DetailsScreen() }
            composable(Destination.NativeStyles.route) { NativeStylesScreen() }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, route: String?) {
    NavigationBar {
        Destination.entries.filter { it.icon != null }.forEach { destination ->
            NavigationBarItem(
                selected = route == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        // One copy of each tab; a tab left keeps its state (and its native ads) for its return.
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(painterResource(checkNotNull(destination.icon)), contentDescription = null) },
                label = { Text(stringResource(destination.label)) },
                modifier = Modifier.testTag("nav_${destination.route}"),
            )
        }
    }
}
