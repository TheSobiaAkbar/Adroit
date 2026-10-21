package io.github.thesobiaakbar.adroit.sample.compose.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.compose.Paywall
import io.github.thesobiaakbar.adroit.sample.compose.R
import io.github.thesobiaakbar.adroit.sample.compose.billing.CreditsScreen
import io.github.thesobiaakbar.adroit.sample.compose.billing.SamplePaywalls
import io.github.thesobiaakbar.adroit.update.AdroitUpdate
import kotlinx.coroutines.flow.first
import io.github.thesobiaakbar.adroit.sample.compose.details.DetailsScreen
import io.github.thesobiaakbar.adroit.sample.compose.feed.FeedScreen
import io.github.thesobiaakbar.adroit.sample.compose.home.CoinsViewModel
import io.github.thesobiaakbar.adroit.sample.compose.home.HomeScreen
import io.github.thesobiaakbar.adroit.sample.compose.settings.SettingsScreen
import io.github.thesobiaakbar.adroit.sample.compose.styles.NativeStylesScreen

/** The app's destinations. The first three are in the bottom bar. */
enum class Destination(val route: String, @param:StringRes val label: Int, @param:DrawableRes val icon: Int?) {
    Home("home", R.string.nav_home, R.drawable.ic_nav_home),
    Feed("feed", R.string.nav_feed, R.drawable.ic_nav_feed),
    Settings("settings", R.string.nav_settings, R.drawable.ic_nav_settings),
    Details("details", R.string.details_title, null),
    NativeStyles("native_styles", R.string.native_styles, null),
    Credits("credits", R.string.credits_store, null),
}

/** The paywall destination's route: [paywall] opened at [placement]. */
fun paywallRoute(paywall: PaywallDefinition, placement: String): String = "paywall/${paywall.name}/$placement"

private const val PAYWALL_ROUTE = "paywall/{name}/{placement}"

/**
 * The navigation graph. Each destination is its own `ViewModelStoreOwner` and lifecycle: its native ads are kept
 * across rotation in its ViewModel store and destroyed when it's popped, and its banners are destroyed when it leaves
 * the screen. [CoinsViewModel] is the Activity's, so a reward delivered after its screen was left is still granted.
 *
 * A paywall is the `paywall/{name}/{placement}` destination: its controller lives in the destination's ViewModel
 * store, so a rotation keeps its selection and close countdown. The launch paywall (Onboarding) is the splash's.
 *
 * A flexible update that finished downloading in the background (`AdroitUpdate`) gets a "Restart" snackbar.
 */
@Composable
fun SampleApp() {
    val navController = rememberNavController()
    val coins: CoinsViewModel = viewModel()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbars = remember { SnackbarHostState() }
    val updateReady = stringResource(R.string.update_ready)
    val restart = stringResource(R.string.update_restart)
    LaunchedEffect(Unit) {
        AdroitUpdate.readyToInstall.first { it }
        val result = snackbars.showSnackbar(updateReady, actionLabel = restart, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) AdroitUpdate.install()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
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
                HomeScreen(
                    coins,
                    onOpenDetails = { navController.navigate(Destination.Details.route) },
                    onOpenCredits = { navController.navigate(Destination.Credits.route) },
                )
            }
            composable(Destination.Feed.route) { FeedScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onOpenNativeStyles = { navController.navigate(Destination.NativeStyles.route) },
                    onUpgrade = { navController.navigate(paywallRoute(SamplePaywalls.SettingsUpgrade, "settings")) },
                )
            }
            composable(Destination.Details.route) { DetailsScreen() }
            composable(Destination.NativeStyles.route) { NativeStylesScreen() }
            composable(Destination.Credits.route) { CreditsScreen() }
            composable(PAYWALL_ROUTE) { paywallEntry ->
                val arguments = checkNotNull(paywallEntry.arguments)
                val placement = checkNotNull(arguments.getString("placement"))
                Paywall(
                    SamplePaywalls.valueOf(checkNotNull(arguments.getString("name"))),
                    placement,
                    // By route, not popBackStack(): a no-op if this paywall is already gone.
                    onClose = { navController.popBackStack(PAYWALL_ROUTE, inclusive = true) },
                    // Closing Onboarding can offer ExitDiscount: it replaces the closed paywall, same placement.
                    onExitPaywall = { exit -> navController.navigate(paywallRoute(exit, placement)) },
                )
            }
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
