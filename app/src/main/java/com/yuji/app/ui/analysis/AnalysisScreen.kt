package com.yuji.app.ui.analysis

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.data.db.SnapshotItemEntity
import com.yuji.app.domain.Currency
import com.yuji.app.domain.Money
import com.yuji.app.ui.Format
import com.yuji.app.ui.components.AccountIcon
import com.yuji.app.ui.components.CnyText
import com.yuji.app.ui.components.DeltaText
import com.yuji.app.ui.components.DonutChart
import com.yuji.app.ui.components.EmptyState
import com.yuji.app.ui.components.NativeAmountText
import com.yuji.app.ui.components.SectionHeader
import com.yuji.app.ui.components.Slice
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.components.PageTitle
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.nav.Routes
import com.yuji.app.ui.theme.Amount
import com.yuji.app.ui.theme.LocalYujiColors
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun AnalysisScreen(nav: NavController) {
    val c = LocalContainer.current
    val portfolio by c.repository.portfolio.collectAsStateWithLifecycle()
    val snapshots by c.repository.snapshots.collectAsStateWithLifecycle()
    val latestItems by produceState(emptyList<SnapshotItemEntity>(), snapshots.lastOrNull()?.id) {
        value = snapshots.lastOrNull()?.let { c.repository.snapshotItemsOnce(it.id) }.orEmpty()
    }
    val previousItems by produceState(emptyList<SnapshotItemEntity>(), snapshots.getOrNull(snapshots.size - 2)?.id) {
        value = snapshots.getOrNull(snapshots.size - 2)?.let { c.repository.snapshotItemsOnce(it.id) }.orEmpty()
    }

    val counted = portfolio.accounts.filter { it.counted }
    val assets = portfolio.assets
    val palette = LocalYujiColors.current.Palette

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("资产分析", Modifier.padding(vertical = 6.dp)) }

        if (counted.none { it.valueCny!!.signum() > 0 }) {
            item { EmptyState(Icons.Rounded.DonutLarge, "暂无可分析的资产", "添加账户并填写余额后，这里会显示资产结构。") }
            return@LazyColumn
        }

        // ---- currency ----
        val byCurrency = counted.filter { it.valueCny!!.signum() > 0 }
            .groupBy { it.account.currency }
            .map { (cur, list) -> Triple(cur, list.fold(BigDecimal.ZERO) { s, v -> s + v.valueCny!! }, list.fold(BigDecimal.ZERO) { s, v -> s + v.account.balance }) }
            .sortedByDescending { it.second }
        val colorOf = byCurrency.mapIndexed { i, t -> t.first to palette[i % palette.size] }.toMap()

        item { SectionHeader("币种分布") }
        item {
            YujiCard(padding = PaddingValues(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        byCurrency.map { Slice(it.first, it.second.toDouble(), colorOf.getValue(it.first)) },
                        Modifier.size(140.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("总资产", style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextMuted)
                            CnyText(assets, MaterialTheme.typography.titleSmall, compact = true)
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        byCurrency.forEach { (cur, value, _) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf.getValue(cur)))
                                Spacer(Modifier.width(8.dp))
                                Text(cur, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(Money.percent(value, assets), style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = LocalYujiColors.current.Outline)
                byCurrency.forEach { (cur, value, native) ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${Currency.of(cur)?.label ?: cur} · $cur", style = MaterialTheme.typography.bodyMedium)
                            if (cur != Currency.BASE) NativeAmountText(native, cur, Amount.small, color = LocalYujiColors.current.TextFaint)
                        }
                        CnyText(value, Amount.row)
                    }
                }
                val crypto = byCurrency.filter { Currency.of(it.first)?.isCrypto == true }.fold(BigDecimal.ZERO) { s, t -> s + t.second }
                val foreign = byCurrency.filter { it.first != Currency.BASE && Currency.of(it.first)?.isCrypto != true }.fold(BigDecimal.ZERO) { s, t -> s + t.second }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill("人民币", Money.percent(assets - crypto - foreign, assets), Modifier.weight(1f))
                    StatPill("外币", Money.percent(foreign, assets), Modifier.weight(1f))
                    StatPill("加密资产", Money.percent(crypto, assets), Modifier.weight(1f))
                }
            }
        }

        // ---- groups ----
        val groups = portfolio.groups.filter { it.totalCny.signum() > 0 }.sortedByDescending { it.totalCny }
        if (groups.isNotEmpty()) {
            item { SectionHeader("分组分布") }
            item {
                YujiCard {
                    val max = groups.first().totalCny
                    groups.forEachIndexed { i, g ->
                        if (i > 0) Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.group.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            CnyText(g.totalCny, MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted)
                            Text("  " + Money.percent(g.totalCny, assets), style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.AccentText)
                        }
                        Spacer(Modifier.height(6.dp))
                        Bar(fraction(g.totalCny, max), palette[i % palette.size])
                    }
                }
            }
        }

        // ---- top accounts ----
        val top = counted.filter { it.valueCny!!.signum() > 0 }.sortedByDescending { it.valueCny }.take(10)
        item { SectionHeader("账户占比 Top ${top.size}") }
        item {
            YujiCard(padding = PaddingValues(vertical = 4.dp)) {
                top.forEachIndexed { i, v ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalYujiColors.current.Outline)
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate(Routes.account(v.account.id)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = LocalYujiColors.current.TextFaint, modifier = Modifier.width(24.dp))
                        AccountIcon(v.account.iconType, v.account.iconValue, v.account.name, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.account.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            CnyText(v.valueCny, Amount.small, color = LocalYujiColors.current.TextFaint)
                        }
                        Text(Money.percent(v.valueCny!!, assets), style = MaterialTheme.typography.titleSmall, color = LocalYujiColors.current.AccentText)
                    }
                }
            }
        }

        // ---- liabilities ----
        val debts = counted.filter { it.valueCny!!.signum() < 0 }.sortedBy { it.valueCny }
        if (debts.isNotEmpty()) {
            item { SectionHeader("负债") }
            item {
                YujiCard(padding = PaddingValues(vertical = 4.dp)) {
                    debts.forEachIndexed { i, v ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalYujiColors.current.Outline)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AccountIcon(v.account.iconType, v.account.iconValue, v.account.name, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(v.account.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            CnyText(v.valueCny, Amount.row, color = LocalYujiColors.current.Coral)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("负债合计", style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted, modifier = Modifier.weight(1f))
                        CnyText(portfolio.liabilities.negate(), Amount.row, color = LocalYujiColors.current.Coral)
                    }
                }
            }
        }

        // ---- FX impact ----
        item { SectionHeader("汇率影响") }
        item { FxImpactCard(latestItems, previousItems, snapshots.lastOrNull()?.createdAt, snapshots.getOrNull(snapshots.size - 2)?.createdAt) }
    }
}

@Composable
private fun FxImpactCard(
    latestItems: List<SnapshotItemEntity>,
    previousItems: List<SnapshotItemEntity>,
    at: Long?,
    previousAt: Long?,
) {
    val newRates = latestItems.groupBy { it.currency }.mapValues { (_, l) -> l.first().rate }
    val oldRates = previousItems.groupBy { it.currency }.mapValues { (_, l) -> l.first().rate }
    val rows = newRates.keys
        .filter { it != Currency.BASE }
        .mapNotNull { cur ->
            val old = oldRates[cur]?.takeIf { it.signum() > 0 } ?: return@mapNotNull null
            val now = newRates[cur]?.takeIf { it.signum() > 0 } ?: return@mapNotNull null
            if (old.compareTo(now) == 0) return@mapNotNull null
            val amount = latestItems.filter { it.currency == cur }.fold(BigDecimal.ZERO) { s, v -> s + v.balance }
            Triple(cur, old to now, amount.multiply(now - old).setScale(2, RoundingMode.HALF_UP))
        }
    YujiCard {
        if (rows.isEmpty() || at == null || previousAt == null) {
            Text(
                "暂无可比较的汇率。持有外币或加密资产并产生至少两次快照后，这里会显示汇率波动带来的人民币增减。",
                style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted,
            )
            return@YujiCard
        }
        Text(
            "${Format.dateTime(previousAt)} → ${Format.dateTime(at)}，仅汇率变化带来的影响",
            style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint,
        )
        rows.forEach { (cur, rates, impact) ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cur, style = MaterialTheme.typography.bodyMedium)
                    Text("${Money.rate(rates.first)} → ${Money.rate(rates.second)}", style = Amount.small, color = LocalYujiColors.current.TextFaint)
                }
                DeltaText(impact, Amount.row)
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = LocalYujiColors.current.Outline)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("合计", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            DeltaText(rows.fold(BigDecimal.ZERO) { s, r -> s + r.third }, Amount.row)
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier.clip(shape).background(LocalYujiColors.current.Muted)
            .border(1.dp, LocalYujiColors.current.Outline, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextMuted)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(LocalYujiColors.current.Muted)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color))),
        )
    }
}

private fun fraction(part: BigDecimal, whole: BigDecimal): Float =
    if (whole.signum() == 0) 0f else part.divide(whole, 4, RoundingMode.HALF_UP).toFloat()
