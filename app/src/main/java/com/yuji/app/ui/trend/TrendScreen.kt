package com.yuji.app.ui.trend

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.data.db.SnapshotEntity
import com.yuji.app.data.db.SnapshotReason
import com.yuji.app.domain.Money
import com.yuji.app.ui.Format
import com.yuji.app.ui.components.ChartPoint
import com.yuji.app.ui.components.ConfirmDialog
import com.yuji.app.ui.components.CnyText
import com.yuji.app.ui.components.DeltaText
import com.yuji.app.ui.components.EmptyState
import com.yuji.app.ui.components.LineChart
import com.yuji.app.ui.components.YujiCard
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.ui.graphics.Color
import com.yuji.app.ui.components.PageTitle
import com.yuji.app.ui.components.SectionLabel
import com.yuji.app.ui.components.YujiChip
import com.yuji.app.ui.components.accentGlow
import com.yuji.app.ui.components.segmentBorder
import com.yuji.app.ui.theme.YujiType
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.nav.Routes
import com.yuji.app.ui.theme.Amount
import com.yuji.app.ui.theme.LocalTrend
import com.yuji.app.ui.theme.LocalYujiColors
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Calendar

enum class Range(val label: String) { M1("1月"), M6("6月"), YTD("今年"), Y1("1年"), ALL("全部") }

fun Range.start(now: Long): Long? {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    return when (this) {
        Range.M1 -> cal.apply { add(Calendar.MONTH, -1) }.timeInMillis
        Range.M6 -> cal.apply { add(Calendar.MONTH, -6) }.timeInMillis
        Range.Y1 -> cal.apply { add(Calendar.YEAR, -1) }.timeInMillis
        Range.YTD -> cal.apply {
            set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        Range.ALL -> null
    }
}

fun reasonLabel(reason: String): String = when (reason) {
    SnapshotReason.UPDATE -> "批量更新"
    SnapshotReason.EDIT -> "账户变更"
    SnapshotReason.TRANSFER -> "资产转移"
    SnapshotReason.DELETE -> "删除账户"
    SnapshotReason.IMPORT -> "导入"
    SnapshotReason.RATES_READY -> "汇率就绪"
    SnapshotReason.RECURRING -> "固定收支"
    else -> "历史记录"
}

private val ROW_R = 16.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrendScreen(nav: NavController) {
    val c = LocalContainer.current
    val snapshots by c.repository.snapshots.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val trend = LocalTrend.current
    val scope = rememberCoroutineScope()
    var range by rememberSaveable { mutableStateOf(Range.M6) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    var selected by rememberSaveable { mutableStateOf(listOf<Long>()) }

    val now = System.currentTimeMillis()
    val start = range.start(now)
    val inRange = snapshots.filter { start == null || it.createdAt >= start }
    val first = inRange.firstOrNull()
    val last = inRange.lastOrNull()
    val change = if (first != null && last != null) last.totalCny - first.totalCny else null
    val previousById = snapshots.zipWithNext().associate { (a, b) -> b.id to a }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                bottom = 96.dp,
            ),
        ) {
            item {
                Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PageTitle("资产趋势", Modifier.weight(1f))
                    if (snapshots.size >= 2) {
                        TextButton(onClick = { selecting = !selecting; selected = emptyList() }) {
                            Text(if (selecting) "取消" else "对比", color = LocalYujiColors.current.AccentText)
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Range.entries.forEach { r ->
                        YujiChip(r.label, selected = r == range, onClick = { range = r })
                    }
                }
            }
            item {
                YujiCard(padding = PaddingValues(16.dp)) {
                    if (inRange.size < 2) {
                        EmptyState(
                            Icons.Rounded.ShowChart,
                            "数据还不够",
                            "这个时间范围内至少需要 2 次资产快照。每次更新余额都会自动记录一次。",
                        )
                    } else {
                        Text("区间变化", style = YujiType.tag, color = LocalYujiColors.current.TextMuted)
                        Row(verticalAlignment = Alignment.Bottom) {
                            DeltaText(change, Amount.large)
                            if (first != null && first.totalCny.signum() > 0 && change != null) {
                                Text(
                                    "  " + (if (change.signum() >= 0) "+" else "") + Money.percent(change, first.totalCny),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = trend.of(change.signum()),
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        LineChart(
                            inRange.map { ChartPoint(it.createdAt, it.totalCny.toDouble()) },
                            Modifier.fillMaxWidth().height(220.dp),
                            goal = settings.goal?.toDouble(),
                            valueLabel = { Money.compactCny(BigDecimal(it)) },
                            dateLabel = Format::shortDate,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("按住图表拖动可查看每个时间点", style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint)
                    }
                }
            }

            if (selecting) {
                item {
                    Text(
                        "选择两条快照进行对比（已选 ${selected.size}/2）",
                        style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.AccentText, modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                    )
                }
            }

            val byMonth = inRange.asReversed().groupBy { Format.yearMonth(it.createdAt) }
            byMonth.forEach { (month, list) ->
                item(key = "h-$month") {
                    SectionLabel(month, Modifier.padding(top = 20.dp, bottom = 8.dp))
                }
                list.forEachIndexed { i, s ->
                    item(key = s.id) {
                        val isFirst = i == 0
                        val isLast = i == list.size - 1
                        val shape = when {
                            isFirst && isLast -> RoundedCornerShape(ROW_R)
                            isFirst -> RoundedCornerShape(topStart = ROW_R, topEnd = ROW_R)
                            isLast -> RoundedCornerShape(bottomStart = ROW_R, bottomEnd = ROW_R)
                            else -> RectangleShape
                        }
                        val swipe = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart && !selecting) {
                                    confirmDeleteId = s.id
                                }
                                false
                            }
                        )
                        SwipeToDismissBox(
                            state = swipe,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = !selecting,
                            backgroundContent = bg@{
                                if (swipe.dismissDirection == SwipeToDismissBoxValue.Settled) return@bg
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(LocalYujiColors.current.Coral.copy(alpha = 0.16f), shape)
                                        .padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除", tint = LocalYujiColors.current.Coral)
                                }
                            },
                        ) {
                            Surface(
                                color = LocalYujiColors.current.Surface,
                                shape = shape,
                                modifier = Modifier.segmentBorder(isFirst, isLast, ROW_R, LocalYujiColors.current.Outline),
                            ) {
                                Column {
                                    if (!isFirst) HorizontalDivider(
                                        Modifier.padding(horizontal = 16.dp),
                                        color = LocalYujiColors.current.Outline,
                                    )
                                    SnapshotRow(
                                        s = s,
                                        previous = previousById[s.id],
                                        selecting = selecting,
                                        selected = s.id in selected,
                                        onClick = {
                                            if (selecting) {
                                                selected = when {
                                                    s.id in selected -> selected - s.id
                                                    selected.size < 2 -> selected + s.id
                                                    else -> listOf(selected.last(), s.id)
                                                }
                                            } else {
                                                nav.navigate(Routes.snapshot(s.id))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        confirmDeleteId?.let { id ->
            val snap = snapshots.find { it.id == id }
            ConfirmDialog(
                title = "删除快照？",
                message = if (snap != null) "将删除 ${Format.monthDay(snap.createdAt)} ${Format.time(snap.createdAt)} 的快照，此操作不可撤销。" else "此操作不可撤销。",
                confirm = "删除",
                destructive = true,
                onConfirm = { scope.launch { c.repository.deleteSnapshot(id) } },
                onDismiss = { confirmDeleteId = null },
            )
        }

        if (selecting && selected.size == 2) {
            ExtendedFloatingActionButton(
                onClick = {
                    val (a, b) = selected.map { id -> snapshots.first { it.id == id } }.sortedBy { it.createdAt }
                    selecting = false
                    selected = emptyList()
                    nav.navigate(Routes.compare(a.id, b.id))
                },
                icon = { Icon(Icons.Rounded.CompareArrows, contentDescription = null) },
                text = { Text("对比所选快照") },
                containerColor = LocalYujiColors.current.Accent,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).accentGlow(MaterialTheme.shapes.large),
            )
        }
    }
}

@Composable
private fun SnapshotRow(s: SnapshotEntity, previous: SnapshotEntity?, selecting: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) LocalYujiColors.current.AccentText else LocalYujiColors.current.TextFaint,
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Box(Modifier.size(6.dp).clip(CircleShape).background(LocalYujiColors.current.AccentText.copy(alpha = 0.7f)))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(Format.monthDay(s.createdAt) + "  " + Format.time(s.createdAt), style = MaterialTheme.typography.bodyMedium)
            Text(
                listOf(reasonLabel(s.reason), s.note).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            CnyText(s.totalCny, Amount.row)
            if (previous != null) {
                val d = s.totalCny - previous.totalCny
                if (d.signum() != 0) DeltaText(d, Amount.small)
            }
        }
    }
}
