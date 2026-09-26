package com.yuji.app.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import com.yuji.app.ui.theme.ambientBackground
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

    val c = LocalYujiColors.current
    Scaffold(
        containerColor = c.Chrome,
        bottomBar = {
            if (showBar) {
                NavigationBar(
                    containerColor = c.Chrome,
                    tonalElevation = 0.dp,
                    modifier = Modifier.drawBehind { drawLine(c.Outline, Offset(0f, 0f), Offset(size.width, 0f), 1f) },
                ) {
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
                                selectedIconColor = c.AccentText,
                                selectedTextColor = c.Text,
                                indicatorColor = c.AccentSoft,
                                unselectedIconColor = c.TextFaint,
                                unselectedTextColor = c.TextFaint,
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
            screen(Routes.HOME) { HomeScreen(nav) }
            screen(Routes.TREND) { TrendScreen(nav) }
            screen(Routes.ANALYSIS) { AnalysisScreen(nav) }
            screen(Routes.SETTINGS) { SettingsScreen(nav) }
            screen(
                Routes.UPDATE,
                arguments = listOf(navArgument("overdue") { type = NavType.BoolType; defaultValue = false }),
            ) { QuickUpdateScreen(nav, it.arguments?.getBoolean("overdue") ?: false) }
            screen(Routes.ACCOUNT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                AccountDetailScreen(nav, it.arguments!!.getLong("id"))
            }
            screen(
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
            screen(Routes.TRANSFER, arguments = listOf(navArgument("from") { type = NavType.LongType; defaultValue = -1L })) {
                TransferScreen(nav, it.arguments!!.getLong("from").takeIf { id -> id >= 0 })
            }
            screen(Routes.SNAPSHOT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                SnapshotScreen(nav, it.arguments!!.getLong("id"))
            }
            screen(
                Routes.COMPARE,
                arguments = listOf(navArgument("a") { type = NavType.LongType }, navArgument("b") { type = NavType.LongType }),
            ) { CompareScreen(nav, it.arguments!!.getLong("a"), it.arguments!!.getLong("b")) }
            screen(Routes.RATES) { RatesScreen(nav) }
            screen(Routes.GROUPS) { GroupsScreen(nav) }
            screen(Routes.BACKUP) { BackupScreen(nav) }
            screen(Routes.ICONS) { IconCacheScreen(nav) }
            screen(Routes.ABOUT) { AboutScreen(nav) }
        }
        // Tab pages draw edge to edge; fade scrolled content out under the status bar.
        if (showBar) {
            Spacer(
                Modifier.fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Brush.verticalGradient(listOf(c.Background.copy(alpha = 0.82f), c.Background.copy(alpha = 0.45f)))),
            )
        }
    }
}

private fun AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.route
    val to = targetState.destination.route
    return tabs.any { it.route == from } && tabs.any { it.route == to }
}

/** Every destination draws the ambient background itself, so screens stay opaque during transitions. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    Box(Modifier.fillMaxSize().ambientBackground()) { content(entry) }
}
