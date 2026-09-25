package com.yuji.app.ui.trend

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.data.db.SnapshotEntity
import com.yuji.app.data.db.SnapshotReason
import com.yuji.app.domain.Money
import com.yuji.app.ui.Format
import com.yuji.app.ui.components.ChartPoint
import com.yuji.app.ui.components.CnyText
import com.yuji.app.ui.components.DeltaText
import com.yuji.app.ui.components.EmptyState
import com.yuji.app.ui.components.LineChart
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.nav.Routes
import com.yuji.app.ui.theme.Amount
import com.yuji.app.ui.theme.LocalTrend
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.theme.YujiColors
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
    else -> "历史记录"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrendScreen(nav: NavController) {
    val c = LocalContainer.current
    val snapshots by c.repository.snapshots.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val trend = LocalTrend.current
    var range by rememberSaveable { mutableStateOf(Range.M6) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var pinchAccum by remember { mutableFloatStateOf(1f) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("资产趋势", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (snapshots.size >= 2) {
                        TextButton(onClick = { selecting = !selecting; selected = emptyList() }) {
                            Text(if (selecting) "取消" else "对比", color = LocalYujiColors.current.Mint)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Range.entries.forEach { r ->
                        FilterChip(
                            selected = r == range,
                            onClick = { range = r },
                            label = { Text(r.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalYujiColors.current.Mint.copy(alpha = 0.16f),
                                selectedLabelColor = LocalYujiColors.current.Mint,
                                containerColor = LocalYujiColors.current.Card,
                                labelColor = LocalYujiColors.current.TextMuted,
                            ),
                            border = null,
                        )
                    }
                }
            }
            item {
                YujiCard(
                    padding = PaddingValues(16.dp),
                    modifier = Modifier.pointerInput(range) {
                        detectTransformGestures { _, _, zoom, _ ->
                            pinchAccum *= zoom
                            val idx = Range.entries.indexOf(range)
                            when {
                                pinchAccum < 0.65f -> {
                                    range = Range.entries.getOrElse(idx + 1) { Range.ALL }
                                    pinchAccum = 1f
                                }
                                pinchAccum > 1.55f -> {
                                    range = Range.entries.getOrElse(idx - 1) { Range.M1 }
                                    pinchAccum = 1f
                                }
                            }
                        }
                    },
                ) {
                    if (inRange.size < 2) {
                        EmptyState(
                            Icons.Rounded.ShowChart,
                            "数据还不够",
                            "这个时间范围内至少需要 2 次资产快照。每次更新余额都会自动记录一次。",
                        )
                    } else {
                        Text("区间变化", style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.TextMuted)
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
                        style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.Mint, modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            val byMonth = inRange.asReversed().groupBy { Format.yearMonth(it.createdAt) }
            byMonth.forEach { (month, list) ->
                item(key = "h-$month") {
                    Text(month, style = MaterialTheme.typography.labelLarge, color = LocalYujiColors.current.TextMuted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                }
                item(key = "m-$month") {
                    YujiCard(padding = PaddingValues(vertical = 4.dp)) {
                        list.forEach { s ->
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
                containerColor = LocalYujiColors.current.Mint,
                contentColor = LocalYujiColors.current.Background,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
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
                tint = if (selected) LocalYujiColors.current.Mint else LocalYujiColors.current.TextFaint,
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(LocalYujiColors.current.Mint.copy(alpha = 0.6f)))
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
