package com.example.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.presentation.screens.analytics.AnalyticsScreen
import com.example.presentation.screens.custommessage.CustomMessageScreen
import com.example.presentation.screens.history.HistoryScreen
import com.example.presentation.screens.home.HomeScreen
import com.example.presentation.screens.kiosk.KioskScreen
import com.example.presentation.screens.ledger.LedgerScreen
import com.example.presentation.screens.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val ANALYTICS = "analytics"
    const val LEDGER = "ledger"
    const val SETTINGS = "settings"
    const val CUSTOM_MESSAGE = "custom_message"
    const val KIOSK = "kiosk"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        Triple(Routes.HOME, "Home", Icons.Default.Home),
        Triple(Routes.HISTORY, "History", Icons.Default.List),
        Triple(Routes.ANALYTICS, "Analytics", Icons.Default.TrendingUp),
        Triple(Routes.LEDGER, "Cashbook", Icons.Default.Edit),
        Triple(Routes.SETTINGS, "Settings", Icons.Default.Settings)
    )

    Scaffold(
        bottomBar = {
            if (currentDestination?.route in items.map { it.first }) {
                NavigationBar {
                    items.forEach { (route, title, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = title) },
                            label = { Text(title) },
                            selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToKiosk = { navController.navigate(Routes.KIOSK) },
                    onNavigateToAnalytics = { navController.navigate(Routes.ANALYTICS) }
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen()
            }
            composable(Routes.ANALYTICS) {
                AnalyticsScreen()
            }
            composable(Routes.LEDGER) {
                LedgerScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateToCustomMessage = { navController.navigate(Routes.CUSTOM_MESSAGE) }
                )
            }
            composable(Routes.CUSTOM_MESSAGE) {
                CustomMessageScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.KIOSK) {
                KioskScreen(
                    onExit = { navController.popBackStack() }
                )
            }
        }
    }
}
