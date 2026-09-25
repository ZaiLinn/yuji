package com.yuji.app.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yuji.app.AppContainer
import com.yuji.app.ui.account.AccountDetailScreen
import com.yuji.app.ui.account.AccountEditScreen
import com.yuji.app.ui.analysis.AnalysisScreen
import com.yuji.app.ui.home.HomeScreen
import com.yuji.app.ui.settings.AboutScreen
import com.yuji.app.ui.settings.BackupScreen
import com.yuji.app.ui.settings.GroupsScreen
import com.yuji.app.ui.settings.IconCacheScreen
import com.yuji.app.ui.settings.RatesScreen
import com.yuji.app.ui.settings.SettingsScreen
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.theme.YujiColors
import com.yuji.app.ui.transfer.TransferScreen
import com.yuji.app.ui.trend.CompareScreen
import com.yuji.app.ui.trend.SnapshotScreen
import com.yuji.app.ui.trend.TrendScreen
import com.yuji.app.ui.update.QuickUpdateScreen

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("no container") }

object Routes {
    const val HOME = "home"
    const val TREND = "trend"
    const val ANALYSIS = "analysis"
    const val SETTINGS = "settings"
    const val UPDATE = "update?overdue={overdue}"
    const val ACCOUNT = "account/{id}"
    const val EDIT = "edit?id={id}&group={group}"
    const val TRANSFER = "transfer?from={from}"
    const val SNAPSHOT = "snapshot/{id}"
    const val COMPARE = "compare/{a}/{b}"
    const val RATES = "settings/rates"
    const val GROUPS = "settings/groups"
    const val BACKUP = "settings/backup"
    const val ICONS = "settings/icons"
    const val ABOUT = "settings/about"

    fun update(overdueOnly: Boolean = false) = "update?overdue=$overdueOnly"
    fun account(id: Long) = "account/$id"
    fun edit(id: Long? = null, group: Long? = null) = "edit?id=${id ?: -1}&group=${group ?: -1}"
    fun transfer(from: Long? = null) = "transfer?from=${from ?: -1}"
    fun snapshot(id: Long) = "snapshot/$id"
    fun compare(a: Long, b: Long) = "compare/$a/$b"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "总览", Icons.Rounded.AccountBalanceWallet),
    Tab(Routes.TREND, "趋势", Icons.Rounded.ShowChart),
    Tab(Routes.ANALYSIS, "分析", Icons.Rounded.DonutLarge),
    Tab(Routes.SETTINGS, "设置", Icons.Rounded.Settings),
)

@Composable
fun YujiNavHost(nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        containerColor = LocalYujiColors.current.Background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = LocalYujiColors.current.Background, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = LocalYujiColors.current.Mint,
                                selectedTextColor = LocalYujiColors.current.Mint,
                                indicatorColor = LocalYujiColors.current.Mint.copy(alpha = 0.12f),
                                unselectedIconColor = LocalYujiColors.current.TextMuted,
                                unselectedTextColor = LocalYujiColors.current.TextMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = {
                if (isTabSwitch()) EnterTransition.None
                else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) + fadeIn(tween(280))
            },
            exitTransition = {
                if (isTabSwitch()) ExitTransition.None
                else fadeOut(tween(200))
            },
            popEnterTransition = {
                if (isTabSwitch()) EnterTransition.None
                else fadeIn(tween(220))
            },
            popExitTransition = {
                if (isTabSwitch()) ExitTransition.None
                else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(260)) + fadeOut(tween(260))
            },
        ) {
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.TREND) { TrendScreen(nav) }
            composable(Routes.ANALYSIS) { AnalysisScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(
                Routes.UPDATE,
                arguments = listOf(navArgument("overdue") { type = NavType.BoolType; defaultValue = false }),
            ) { QuickUpdateScreen(nav, it.arguments?.getBoolean("overdue") ?: false) }
            composable(Routes.ACCOUNT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                AccountDetailScreen(nav, it.arguments!!.getLong("id"))
            }
            composable(
                Routes.EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("group") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                AccountEditScreen(
                    nav,
                    it.arguments!!.getLong("id").takeIf { id -> id >= 0 },
                    it.arguments!!.getLong("group").takeIf { g -> g >= 0 },
                )
            }
            composable(Routes.TRANSFER, arguments = listOf(navArgument("from") { type = NavType.LongType; defaultValue = -1L })) {
                TransferScreen(nav, it.arguments!!.getLong("from").takeIf { id -> id >= 0 })
            }
            composable(Routes.SNAPSHOT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                SnapshotScreen(nav, it.arguments!!.getLong("id"))
            }
            composable(
                Routes.COMPARE,
                arguments = listOf(navArgument("a") { type = NavType.LongType }, navArgument("b") { type = NavType.LongType }),
            ) { CompareScreen(nav, it.arguments!!.getLong("a"), it.arguments!!.getLong("b")) }
            composable(Routes.RATES) { RatesScreen(nav) }
            composable(Routes.GROUPS) { GroupsScreen(nav) }
            composable(Routes.BACKUP) { BackupScreen(nav) }
            composable(Routes.ICONS) { IconCacheScreen(nav) }
            composable(Routes.ABOUT) { AboutScreen(nav) }
        }
        // Tab pages draw edge to edge; keep scrolled content from running under the status bar.
        if (showBar) {
            Spacer(
                Modifier.fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(LocalYujiColors.current.Background.copy(alpha = 0.96f)),
            )
        }
    }
}

private fun AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.route
    val to = targetState.destination.route
    return tabs.any { it.route == from } && tabs.any { it.route == to }
}

