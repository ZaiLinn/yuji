package com.yuji.app.ui.trend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.data.db.SnapshotItemEntity
import com.yuji.app.domain.Currency
import com.yuji.app.domain.Money
import com.yuji.app.ui.Format
import com.yuji.app.ui.components.CnyText
import com.yuji.app.ui.components.ConfirmDialog
import com.yuji.app.ui.components.DeltaText
import com.yuji.app.ui.components.NativeAmountText
import com.yuji.app.ui.components.SectionHeader
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.components.YujiTopBar
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.theme.Amount
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.theme.YujiColors
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun SnapshotScreen(nav: NavController, id: Long) {
    val c = LocalContainer.current
    val snapshot by remember(id) { c.repository.snapshot(id) }.collectAsStateWithLifecycle(null)
    val items by remember(id) { c.repository.snapshotItems(id) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var editNote by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val s = snapshot

    Scaffold(
        containerColor = LocalYujiColors.current.Background,
        topBar = {
            YujiTopBar("快照详情", onBack = { nav.popBackStack() }) {
                IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除快照") }
            }
        },
    ) { padding ->
        if (s == null) return@Scaffold
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                YujiCard(padding = PaddingValues(20.dp)) {
                    Text(Format.dateTime(s.createdAt) + " · " + reasonLabel(s.reason), style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    CnyText(s.totalCny, Amount.hero)
                }
            }
            item {
                YujiCard(onClick = { editNote = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("备注", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(if (s.note.isBlank()) "添加" else "编辑", style = MaterialTheme.typography.labelLarge, color = LocalYujiColors.current.Mint)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        s.note.ifBlank { "记录这次盘点的背景，比如「发工资后」「年终奖到账」" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.note.isBlank()) LocalYujiColors.current.TextFaint else LocalYujiColors.current.TextMuted,
                    )
                }
            }
            item { SectionHeader("当时计入的账户") }
            if (items.isEmpty()) {
                item { Text("这条快照来自旧版本，没有保存账户明细。", color = LocalYujiColors.current.TextFaint, modifier = Modifier.padding(start = 4.dp)) }
            } else {
                item {
                    YujiCard(padding = PaddingValues(vertical = 4.dp)) {
                        items.sortedByDescending { it.valueCny }.forEachIndexed { i, it ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalYujiColors.current.Outline.copy(alpha = 0.5f))
                            SnapshotItemRow(it)
                        }
                    }
                }
            }
        }
    }

    if (editNote && s != null) {
        var text by remember { mutableStateOf(s.note) }
        AlertDialog(
            onDismissRequest = { editNote = false },
            containerColor = LocalYujiColors.current.CardHigh,
            title = { Text("快照备注") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 3, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { c.repository.updateSnapshotNote(id, text.trim()) }; editNote = false }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editNote = false }) { Text("取消", color = LocalYujiColors.current.TextMuted) } },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "删除这条快照？",
            message = "只删除这一个历史时间点，不影响账户当前余额。",
            confirm = "删除",
            destructive = true,
            onConfirm = { scope.launch { c.repository.deleteSnapshot(id); nav.popBackStack() } },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun SnapshotItemRow(item: SnapshotItemEntity) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.accountName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.currency != Currency.BASE) {
                Text("汇率 ${Money.rate(item.rate)}", style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            CnyText(item.valueCny, Amount.row)
            if (item.currency != Currency.BASE) NativeAmountText(item.balance, item.currency, Amount.small, color = LocalYujiColors.current.TextFaint)
        }
    }
}

private data class CompareRow(val name: String, val before: BigDecimal?, val after: BigDecimal?) {
    val delta: BigDecimal get() = (after ?: BigDecimal.ZERO) - (before ?: BigDecimal.ZERO)
}

@Composable
fun CompareScreen(nav: NavController, a: Long, b: Long) {
    val c = LocalContainer.current
    val snapshots by c.repository.snapshots.collectAsStateWithLifecycle()
    val first = snapshots.firstOrNull { it.id == a }
    val second = snapshots.firstOrNull { it.id == b }
    val rows by produceState<List<CompareRow>?>(null, a, b) {
        val before = c.repository.snapshotItemsOnce(a)
        val after = c.repository.snapshotItemsOnce(b)
        fun key(i: SnapshotItemEntity) = i.accountId?.toString() ?: "name:${i.accountName}"
        val keys = (before.map(::key) + after.map(::key)).distinct()
        val bm = before.associateBy(::key)
        val am = after.associateBy(::key)
        value = if (before.isEmpty() || after.isEmpty()) emptyList() else keys.map { k ->
            CompareRow(am[k]?.accountName ?: bm[k]!!.accountName, bm[k]?.valueCny, am[k]?.valueCny)
        }.filter { it.delta.abs() >= BigDecimal("0.005") }.sortedByDescending { it.delta.abs() }
    }

    Scaffold(containerColor = LocalYujiColors.current.Background, topBar = { YujiTopBar("快照对比", onBack = { nav.popBackStack() }) }) { padding ->
        if (first == null || second == null) return@Scaffold
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                YujiCard(padding = PaddingValues(20.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(Format.date(first.createdAt), style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.TextMuted)
                            CnyText(first.totalCny, Amount.row)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(Format.date(second.createdAt), style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.TextMuted)
                            CnyText(second.totalCny, Amount.row)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("净资产变化", style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.TextMuted)
                    DeltaText(second.totalCny - first.totalCny, Amount.large)
                }
            }
            item { SectionHeader("账户变化（按幅度排序）") }
            val list = rows
            when {
                list == null -> Unit
                list.isEmpty() -> item {
                    Text(
                        "所选快照缺少账户明细，或账户没有变化。",
                        color = LocalYujiColors.current.TextFaint, modifier = Modifier.padding(start = 4.dp),
                    )
                }
                else -> item {
                    YujiCard(padding = PaddingValues(vertical = 4.dp)) {
                        list.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalYujiColors.current.Outline.copy(alpha = 0.5f))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        when {
                                            r.before == null -> "新增账户"
                                            r.after == null -> "已移除"
                                            else -> "${Money.compactCny(r.before)} → ${Money.compactCny(r.after)}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (r.before == null || r.after == null) LocalYujiColors.current.Amber else LocalYujiColors.current.TextFaint,
                                    )
                                }
                                DeltaText(r.delta, Amount.row)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "只比较两个时间点的数值，不判断变化原因（可能来自存取、收益或汇率）。",
                    style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
