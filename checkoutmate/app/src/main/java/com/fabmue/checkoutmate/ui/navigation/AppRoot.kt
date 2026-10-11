package com.fabmue.checkoutmate.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fabmue.checkoutmate.AppContainer
import com.fabmue.checkoutmate.R
import com.fabmue.checkoutmate.data.Feature
import com.fabmue.checkoutmate.data.Settings
import com.fabmue.checkoutmate.domain.training.TrainingType
import com.fabmue.checkoutmate.ui.screens.HistoryScreen
import com.fabmue.checkoutmate.ui.screens.MatchScreen
import com.fabmue.checkoutmate.ui.screens.SettingsScreen
import com.fabmue.checkoutmate.ui.screens.SetupScreen
import com.fabmue.checkoutmate.ui.screens.StatsScreen
import com.fabmue.checkoutmate.ui.screens.StoreScreen
import com.fabmue.checkoutmate.ui.screens.TrainingGameScreen
import com.fabmue.checkoutmate.ui.screens.TrainingListScreen
import com.fabmue.checkoutmate.ui.theme.BoardThemes
import com.fabmue.checkoutmate.ui.theme.CheckoutMateTheme

private object Routes {
    const val PLAY = "play"
    const val MATCH = "match"
    const val TRAINING = "training"
    const val TRAINING_GAME = "trainingGame/{type}"
    const val STATS = "stats"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val STORE = "store"
}

private data class NavItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val BOTTOM_ITEMS = listOf(
    NavItem(Routes.PLAY, R.string.nav_play, Icons.Filled.PlayArrow),
    NavItem(Routes.TRAINING, R.string.nav_training, Icons.Filled.FitnessCenter),
    NavItem(Routes.STATS, R.string.nav_stats, Icons.Filled.BarChart),
    NavItem(Routes.HISTORY, R.string.nav_history, Icons.Filled.History),
    NavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(container: AppContainer, onRecreate: () -> Unit) {
    val settings by container.prefs.settings.collectAsState(initial = Settings())
    val entitlements by container.entitlements.entitlements.collectAsState()
    val ownsThemes = entitlements.contains(Feature.THEMES)
    val themeName = if (!ownsThemes && settings.theme != BoardThemes.DARK) BoardThemes.DARK else settings.theme

    CheckoutMateTheme(boardTheme = themeName) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBars = currentRoute in BOTTOM_ITEMS.map { it.route }

        Scaffold(
            topBar = {
                if (showBars) {
                    TopAppBar(
                        title = { Text(titleForRoute(currentRoute)) },
                        actions = {
                            IconButton(onClick = { navController.navigate(Routes.STORE) }) {
                                Icon(Icons.Filled.ShoppingCart, contentDescription = stringResource(R.string.nav_store))
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (showBars) {
                    NavigationBar {
                        BOTTOM_ITEMS.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = { navController.navigateTab(item.route) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.PLAY,
                modifier = Modifier.padding(padding),
            ) {
                graph(container, navController, onRecreate)
            }
        }
    }
}

private fun NavGraphBuilder.graph(
    container: AppContainer,
    navController: NavHostController,
    onRecreate: () -> Unit,
) {
    composable(Routes.PLAY) {
        SetupScreen(
            container = container,
            onStartMatch = { navController.navigate(Routes.MATCH) },
            onResumeMatch = { navController.navigate(Routes.MATCH) },
        )
    }
    composable(Routes.MATCH) {
        MatchScreen(
            container = container,
            onExit = { navController.popBackStack() },
            onOpenStore = { navController.navigate(Routes.STORE) },
        )
    }
    composable(Routes.TRAINING) {
        TrainingListScreen(
            container = container,
            onOpenGame = { type -> navController.navigate("trainingGame/${type.name}") },
            onOpenStore = { navController.navigate(Routes.STORE) },
        )
    }
    composable(Routes.TRAINING_GAME) { entry ->
        val type = runCatching { TrainingType.valueOf(entry.arguments?.getString("type") ?: "") }
            .getOrDefault(TrainingType.CRICKET)
        TrainingGameScreen(container, type, onExit = { navController.popBackStack() })
    }
    composable(Routes.STATS) {
        StatsScreen(container, onOpenStore = { navController.navigate(Routes.STORE) })
    }
    composable(Routes.HISTORY) {
        HistoryScreen(container, onOpenStore = { navController.navigate(Routes.STORE) })
    }
    composable(Routes.SETTINGS) {
        SettingsScreen(
            container,
            onOpenStore = { navController.navigate(Routes.STORE) },
            onRecreate = onRecreate,
        )
    }
    composable(Routes.STORE) {
        StoreScreen(container, onBack = { navController.popBackStack() })
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun titleForRoute(route: String?): String = when (route) {
    Routes.TRAINING -> stringResource(R.string.training_title)
    Routes.STATS -> stringResource(R.string.stats_title)
    Routes.HISTORY -> stringResource(R.string.history_title)
    Routes.SETTINGS -> stringResource(R.string.settings_title)
    else -> stringResource(R.string.app_name)
}
