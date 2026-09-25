package com.yuji.app.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.data.db.SnapshotEntity
import com.yuji.app.domain.AccountValue
import com.yuji.app.domain.Currency
import com.yuji.app.domain.GroupValue
import com.yuji.app.domain.Money
import com.yuji.app.ui.Format
import com.yuji.app.ui.account.BalanceDialog
import com.yuji.app.ui.components.AccountIcon
import com.yuji.app.ui.components.Banner
import com.yuji.app.ui.components.ChartPoint
import com.yuji.app.ui.components.CnyText
import com.yuji.app.ui.components.DeltaChip
import com.yuji.app.ui.components.EmptyState
import com.yuji.app.ui.components.LineChart
import com.yuji.app.ui.components.NativeAmountText
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.nav.Routes
import com.yuji.app.ui.theme.Amount
import com.yuji.app.ui.theme.LocalDisplayCurrency
import com.yuji.app.ui.theme.LocalHideAmounts
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.theme.YujiColors
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.math.BigDecimal
import java.math.RoundingMode

private const val DAY = 86_400_000L
const val OVERDUE_DAYS = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val c = LocalContainer.current
    val portfolio by c.repository.portfolio.collectAsStateWithLifecycle()
    val snapshots by c.repository.snapshots.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val notice by c.notice.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var collapsed by remember { mutableStateOf(setOf<Long>()) }
    var editGoal by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var quickUpdateId by remember { mutableStateOf<Long?>(null) }
    val haptic = LocalHapticFeedback.current

    // Group headers and account rows as one flat list, so any account can be dragged
    // within its group or past a header into another group.
    var entries by remember { mutableStateOf(flatten(portfolio.groups, collapsed)) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(portfolio.groups, collapsed) { if (!dragging) entries = flatten(portfolio.groups, collapsed) }

    val listState = rememberLazyListState()
    val extended by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val i = entries.indexOfFirst { it.key == from.key }
        val j = entries.indexOfFirst { it.key == to.key }
        // Nothing may go above the first group header.
        if (i >= 0 && j > 0) {
            entries = entries.toMutableList().apply { add(j, removeAt(i)) }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun saveOrder() {
        val layout = mutableListOf<Pair<Long, MutableList<Long>>>()
        for (e in entries) {
            when (e) {
                is HomeEntry.Header -> layout += e.group.group.id to
                    // Accounts of a collapsed group are not in the list; keep them first.
                    (if (e.group.group.id in collapsed) e.group.accounts.map { it.account.id } else emptyList()).toMutableList()
                is HomeEntry.Item -> layout.last().second += e.value.account.id
            }
        }
        scope.launch { c.repository.arrangeAccounts(layout) }
    }

    val now = System.currentTimeMillis()
    val total = portfolio.total
    val overdue = portfolio.accounts.count { Format.ageDays(it.account.updatedAt, now) >= OVERDUE_DAYS }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (!refreshing) scope.launch {
                refreshing = true
                c.repository.refreshRates()
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                bottom = 104.dp,
            ),
        ) {
            item {
                Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("余记", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { c.settings.setHideAmounts(!settings.hideAmounts) }) {
                        Icon(
                            if (settings.hideAmounts) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "隐藏金额",
                            tint = LocalYujiColors.current.TextMuted,
                        )
                    }
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) searchQuery = ""
                    }) {
                        Icon(
                            if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "搜索账户",
                            tint = LocalYujiColors.current.TextMuted,
                        )
                    }
                    IconButton(onClick = { nav.navigate(Routes.edit()) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建账户")
                    }
                }
            }

            item {
                HeroCard(
                    modifier = Modifier.padding(bottom = 4.dp),
                    total = total,
                    assets = portfolio.assets,
                    liabilities = portfolio.liabilities,
                    snapshots = snapshots,
                    now = now,
                    onClick = { nav.navigate(Routes.TREND) },
                )
            }

            if (searching) {
                item(key = "search_bar") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索账户名称、备注、币种") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清除", tint = LocalYujiColors.current.TextMuted)
                            }
                        },
                    )
                }
            }

            notice?.let { text ->
                item { Banner(text, LocalYujiColors.current.Blue, Modifier.padding(bottom = 12.dp), onClick = { c.notice.value = null }, trailing = "知道了") }
            }
            if (portfolio.missingRates.isNotEmpty()) {
                item {
                    Banner(
                        text = portfolio.missingRates.joinToString("、") + " 汇率尚未获取，相关账户暂未计入总资产",
                        color = LocalYujiColors.current.Amber,
                        modifier = Modifier.padding(bottom = 12.dp),
                        trailing = if (refreshing) "刷新中…" else "刷新",
                        onClick = {
                            if (!refreshing) scope.launch {
                                refreshing = true
                                c.repository.refreshRates()
                                refreshing = false
                            }
                        },
                    )
                }
            }
            if (overdue > 0) {
                item {
                    Banner(
                        "$overdue 个账户超过 $OVERDUE_DAYS 天未更新",
                        LocalYujiColors.current.Mint,
                        Modifier.padding(bottom = 12.dp),
                        onClick = { nav.navigate(Routes.update(overdueOnly = true)) },
                        trailing = "去更新",
                    )
                }
            }

            item { GoalCard(settings.goal, total, onClick = { editGoal = true }) }

            if (portfolio.accounts.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Wallet,
                        "还没有账户",
                        "添加银行卡、支付宝、交易所等账户，只需要记录当前余额。",
                        action = "添加第一个账户" to { nav.navigate(Routes.edit()) },
                    )
                }
            } else if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                val filtered = portfolio.accounts.filter { v ->
                    v.account.name.lowercase().contains(q) ||
                    v.account.note.lowercase().contains(q) ||
                    v.account.currency.lowercase().contains(q)
                }
                if (filtered.isEmpty()) {
                    item { EmptyState(Icons.Rounded.Search, "没有匹配的账户", "试试其他关键词") }
                } else {
                    items(filtered, key = { "s${it.account.id}" }) { v ->
                        AccountRow(
                            v = v, now = now, first = false, last = false, dragging = false,
                            onOpen = { nav.navigate(Routes.account(it)) },
                            onQuickUpdate = { quickUpdateId = it },
                            handle = Modifier,
                        )
                    }
                }
            } else {
                items(entries, key = { it.key }) { e ->
                    val index = entries.indexOf(e)
                    val next = entries.getOrNull(index + 1)
                    val lastInGroup = next == null || next is HomeEntry.Header
                    // Headers are move targets (so accounts can pass them) but have no drag handle.
                    ReorderableItem(reorder, key = e.key) { isDragging ->
                        when (e) {
                            is HomeEntry.Header -> GroupHeader(
                                group = e.group,
                                collapsed = e.group.group.id in collapsed,
                                closed = lastInGroup,
                                onToggle = {
                                    val id = e.group.group.id
                                    collapsed = if (id in collapsed) collapsed - id else collapsed + id
                                },
                                onAdd = { nav.navigate(Routes.edit(group = e.group.group.id)) },
                                onDelete = { scope.launch { c.repository.deleteGroup(e.group.group.id) } },
                                onRename = { name -> scope.launch { c.repository.renameGroup(e.group.group.id, name) } },
                            )
                            is HomeEntry.Item -> AccountRow(
                                v = e.value,
                                now = now,
                                first = entries.getOrNull(index - 1) is HomeEntry.Header,
                                last = lastInGroup,
                                dragging = isDragging,
                                onOpen = { nav.navigate(Routes.account(it)) },
                                onQuickUpdate = { quickUpdateId = it },
                                handle = Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        dragging = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        dragging = false
                                        saveOrder()
                                    },
                                ),
                            )
                        }
                    }
                }
                item {
                    Text(
                        "长按账户可拖动排序，拖过分组标题即可移到该分组",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalYujiColors.current.TextFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
            }
        }

        if (portfolio.accounts.isNotEmpty()) {
            ExtendedFloatingActionButton(
                expanded = extended,
                onClick = { nav.navigate(Routes.update()) },
                icon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                text = { Text("更新余额") },
                containerColor = LocalYujiColors.current.Mint,
                contentColor = LocalYujiColors.current.Background,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            )
        }
    }

    if (editGoal) {
        GoalDialog(settings.goal, onDismiss = { editGoal = false }, onSave = { c.settings.setGoal(it) })
    }
    quickUpdateId?.let { id ->
        portfolio.account(id)?.let { av ->
            BalanceDialog(
                initial = av.account.balance,
                currency = av.account.currency,
                onDismiss = { quickUpdateId = null },
                onSave = { value -> scope.launch { c.repository.confirmBalances(mapOf(id to value)) } },
            )
        }
    }
}

/** Total at the latest snapshot taken at or before [at], or null if none exists. */
fun baselineAt(snapshots: List<SnapshotEntity>, at: Long): BigDecimal? =
    snapshots.lastOrNull { it.createdAt <= at }?.totalCny

@Composable
private fun HeroCard(
    modifier: Modifier = Modifier,
    total: BigDecimal,
    assets: BigDecimal,
    liabilities: BigDecimal,
    snapshots: List<SnapshotEntity>,
    now: Long,
    onClick: () -> Unit,
) {
    val today = baselineAt(snapshots, Format.startOfDay(now) - 1)?.let { total - it }
    val d30 = baselineAt(snapshots, now - 30 * DAY)?.let { total - it }
    val d90 = baselineAt(snapshots, now - 90 * DAY)?.let { total - it }
    val recent = snapshots.filter { it.createdAt >= now - 90 * DAY }
        .map { ChartPoint(it.createdAt, it.totalCny.toDouble()) } + ChartPoint(now, total.toDouble())

    YujiCard(modifier, padding = PaddingValues(20.dp), onClick = onClick) {
        Text("净资产 · ${LocalDisplayCurrency.current}", style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.TextMuted)
        Spacer(Modifier.height(8.dp))
        CnyText(total, Amount.hero)
        if (liabilities.signum() > 0) {
            Spacer(Modifier.height(4.dp))
            Row {
                Text("资产 ", style = Amount.small, color = LocalYujiColors.current.TextMuted)
                CnyText(assets, Amount.small, color = LocalYujiColors.current.TextMuted)
                Text("  ·  负债 ", style = Amount.small, color = LocalYujiColors.current.TextMuted)
                CnyText(liabilities, Amount.small, color = LocalYujiColors.current.TextMuted)
            }
        }
        if (recent.size >= 3) {
            Spacer(Modifier.height(14.dp))
            LineChart(
                recent,
                Modifier.fillMaxWidth().height(56.dp),
                axes = false,
                interactive = false,
                valueLabel = { Money.compactCny(BigDecimal(it)) },
                dateLabel = Format::shortDate,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeltaChip("今日", today, Modifier.weight(1f))
            DeltaChip("近 30 天", d30, Modifier.weight(1f))
            DeltaChip("近 90 天", d90, Modifier.weight(1f))
        }
    }
}

@Composable
private fun GoalCard(goal: BigDecimal?, total: BigDecimal, onClick: () -> Unit) {
    if (goal == null) {
        YujiCard(onClick = onClick, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Flag, contentDescription = null, tint = LocalYujiColors.current.TextMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("设置一个资产目标", style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted, modifier = Modifier.weight(1f))
                Text("设置", style = MaterialTheme.typography.labelLarge, color = LocalYujiColors.current.Mint)
            }
        }
        return
    }
    val ratio = if (goal.signum() > 0) total.divide(goal, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(ratio, label = "goal")
    val hidden = LocalHideAmounts.current
    YujiCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("资产目标", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(Money.percent(total.max(BigDecimal.ZERO), goal), style = MaterialTheme.typography.titleSmall, color = LocalYujiColors.current.Mint)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = LocalYujiColors.current.Mint,
            trackColor = LocalYujiColors.current.CardHigh,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(8.dp))
        val remaining = goal - total
        Text(
            when {
                hidden -> "目标 ••••••"
                remaining.signum() <= 0 -> "已达成目标 ${Money.compactCny(goal)} 🎉"
                else -> "目标 ${Money.compactCny(goal)} · 还差 ${Money.compactCny(remaining)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = LocalYujiColors.current.TextMuted,
        )
    }
}

sealed interface HomeEntry {
    val key: String

    data class Header(val group: GroupValue) : HomeEntry {
        override val key = "g${group.group.id}"
    }

    data class Item(val value: AccountValue) : HomeEntry {
        override val key = "a${value.account.id}"
    }
}

private fun flatten(groups: List<GroupValue>, collapsed: Set<Long>): List<HomeEntry> = groups.flatMap { g ->
    listOf<HomeEntry>(HomeEntry.Header(g)) +
        if (g.group.id in collapsed) emptyList() else g.accounts.map { HomeEntry.Item(it) }
}

private val CARD = 20.dp

/** Top of a group card; [closed] = no visible accounts below, so it rounds its bottom too. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(
    group: GroupValue,
    collapsed: Boolean,
    closed: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit = {},
    onRename: (String) -> Unit = {},
) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")
    val shape = if (closed) RoundedCornerShape(CARD) else RoundedCornerShape(topStart = CARD, topEnd = CARD)
    var showMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember(renaming) { mutableStateOf(group.group.name) }

    Column(Modifier.padding(top = 12.dp).clip(shape).background(LocalYujiColors.current.Card)) {
        Box {
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(onClick = onToggle, onLongClick = { showMenu = true })
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(group.group.name, style = MaterialTheme.typography.titleSmall)
                    Text("${group.accounts.size} 个账户", style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint)
                }
                CnyText(group.totalCny, MaterialTheme.typography.titleSmall, color = LocalYujiColors.current.TextMuted)
                Icon(
                    Icons.Rounded.ExpandMore, contentDescription = null, tint = LocalYujiColors.current.TextFaint,
                    modifier = Modifier.padding(start = 4.dp).rotate(rotation),
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = { showMenu = false; renaming = true },
                    leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "删除分组",
                            color = if (group.accounts.isEmpty()) LocalYujiColors.current.Coral else LocalYujiColors.current.TextFaint,
                        )
                    },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.DeleteOutline, contentDescription = null,
                            tint = if (group.accounts.isEmpty()) LocalYujiColors.current.Coral else LocalYujiColors.current.TextFaint,
                        )
                    },
                    enabled = group.accounts.isEmpty(),
                )
            }
        }
        if (closed && !collapsed) {
            if (group.accounts.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp)) {
                    TextButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
                        Text("＋ 添加账户", style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextFaint)
                    }
                    TextButton(onClick = onDelete) {
                        Text("删除分组", style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.Coral)
                    }
                }
            } else {
                Text(
                    "＋ 在此分组添加账户",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalYujiColors.current.TextFaint,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                )
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = LocalYujiColors.current.CardHigh,
            title = { Text("重命名分组") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank() && renameText != group.group.name,
                    onClick = { onRename(renameText.trim()); renaming = false },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("取消", color = LocalYujiColors.current.TextMuted) }
            },
        )
    }
}

@Composable
private fun AccountRow(
    v: AccountValue,
    now: Long,
    first: Boolean,
    last: Boolean,
    dragging: Boolean,
    onOpen: (Long) -> Unit,
    onQuickUpdate: (Long) -> Unit,
    handle: Modifier,
) {
    val a = v.account
    val age = Format.ageDays(a.updatedAt, now)
    val elevation by animateDpAsState(if (dragging) 12.dp else 0.dp, label = "lift")
    val shape = when {
        dragging -> RoundedCornerShape(16.dp)
        last -> RoundedCornerShape(bottomStart = CARD, bottomEnd = CARD)
        else -> RectangleShape
    }

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onQuickUpdate(a.id)
            false
        }
    )

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        gesturesEnabled = !dragging,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LocalYujiColors.current.Mint.copy(alpha = 0.14f), shape)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.EditNote, contentDescription = "更新余额", tint = LocalYujiColors.current.Mint)
            }
        },
    ) {
        Surface(
            shape = shape,
            color = if (dragging) LocalYujiColors.current.CardHigh else LocalYujiColors.current.Card,
            shadowElevation = elevation,
            modifier = Modifier.then(handle),
        ) {
            Column {
                if (!first && !dragging) HorizontalDivider(Modifier.padding(start = 68.dp), color = LocalYujiColors.current.Outline.copy(alpha = 0.5f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(a.id) }
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (last) 16.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountIcon(a.iconType, a.iconValue, a.name)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            a.name, style = MaterialTheme.typography.bodyLarge,
                            color = if (a.includeInTotal) LocalYujiColors.current.Text else LocalYujiColors.current.TextMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val sub = buildList {
                            if (a.note.isNotBlank()) add(a.note)
                            add(Format.age(a.updatedAt, now) + "更新")
                            if (!a.includeInTotal) add("不计入")
                        }.joinToString(" · ")
                        Text(
                            sub, style = MaterialTheme.typography.labelSmall,
                            color = if (age >= OVERDUE_DAYS) LocalYujiColors.current.Amber else LocalYujiColors.current.TextFaint,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        if (v.valueCny != null) {
                            CnyText(v.valueCny, Amount.row, color = if (a.includeInTotal) LocalYujiColors.current.Text else LocalYujiColors.current.TextMuted)
                        } else {
                            Text("汇率未就绪", style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.Amber)
                        }
                        if (a.currency != Currency.BASE) NativeAmountText(a.balance, a.currency, Amount.small, color = LocalYujiColors.current.TextFaint)
                    }
                }
            }
        }
    }
}

@Composable
fun GoalDialog(current: BigDecimal?, onDismiss: () -> Unit, onSave: (BigDecimal?) -> Unit) {
    var text by remember { mutableStateOf(current?.let(Money::toInput).orEmpty()) }
    val value = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalYujiColors.current.CardHigh,
        title = { Text("资产目标") },
        text = {
            Column {
                Text("首页会显示净资产距离目标的进度。", style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    prefix = { Text("¥ ") },
                    placeholder = { Text("例如 1000000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value.signum() > 0, onClick = { onSave(value); onDismiss() }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null); onDismiss() }) { Text("清除目标", color = LocalYujiColors.current.Coral) }
                TextButton(onClick = onDismiss) { Text("取消", color = LocalYujiColors.current.TextMuted) }
            }
        },
    )
}
