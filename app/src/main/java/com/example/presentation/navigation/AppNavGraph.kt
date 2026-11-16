package com.example.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.presentation.screens.analytics.AnalyticsScreen
import com.example.presentation.screens.applock.AppLockSettingsScreen
import com.example.presentation.screens.applock.IntruderLogsScreen
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
    const val APP_LOCK_SETTINGS = "app_lock_settings"
    const val INTRUDER_LOGS = "intruder_logs"
}

private data class NavTabItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        NavTabItem(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
        NavTabItem(Routes.HISTORY, "History", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
        NavTabItem(Routes.ANALYTICS, "Reports", Icons.AutoMirrored.Filled.TrendingUp, Icons.AutoMirrored.Filled.TrendingUp),
        NavTabItem(Routes.LEDGER, "Khata", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
        NavTabItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    val navigateToTab: (String) -> Unit = { targetRoute ->
        if (currentDestination?.route != targetRoute) {
            if (targetRoute == Routes.HOME) {
                val popped = navController.popBackStack(Routes.HOME, inclusive = false)
                if (!popped) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            } else {
                navController.navigate(targetRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val showBottomBar = currentDestination?.route in items.map { it.route }
    val isKioskRoute = currentDestination?.route == Routes.KIOSK

    Scaffold(
        containerColor = if (isKioskRoute) Color.Black else MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, ambientColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        items.forEach { tab ->
                            val selected = currentDestination?.route == tab.route
                            NavigationBarItem(
                                icon = {
                                    Box(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                },
                                selected = selected,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                onClick = { navigateToTab(tab.route) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(220)) },
            exitTransition = { fadeOut(animationSpec = tween(180)) },
            popEnterTransition = { fadeIn(animationSpec = tween(220)) },
            popExitTransition = { fadeOut(animationSpec = tween(180)) }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToHistory = { navigateToTab(Routes.HISTORY) },
                    onNavigateToSettings = { navigateToTab(Routes.SETTINGS) },
                    onNavigateToKiosk = { navController.navigate(Routes.KIOSK) },
                    onNavigateToAnalytics = { navigateToTab(Routes.ANALYTICS) },
                    onNavigateToAppLock = { navController.navigate(Routes.APP_LOCK_SETTINGS) },
                    onNavigateToIntruderLogs = { navController.navigate(Routes.INTRUDER_LOGS) }
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
                    onNavigateToCustomMessage = { navController.navigate(Routes.CUSTOM_MESSAGE) },
                    onNavigateToAppLock = { navController.navigate(Routes.APP_LOCK_SETTINGS) },
                    onNavigateToIntruderLogs = { navController.navigate(Routes.INTRUDER_LOGS) }
                )
            }
            composable(
                route = Routes.CUSTOM_MESSAGE,
                enterTransition = { slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(180)) }
            ) {
                CustomMessageScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.KIOSK,
                enterTransition = { slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(180)) }
            ) {
                KioskScreen(
                    onExit = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.APP_LOCK_SETTINGS,
                enterTransition = { slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(180)) }
            ) {
                AppLockSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToIntruderLogs = { navController.navigate(Routes.INTRUDER_LOGS) }
                )
            }
            composable(
                route = Routes.INTRUDER_LOGS,
                enterTransition = { slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(180)) }
            ) {
                IntruderLogsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
