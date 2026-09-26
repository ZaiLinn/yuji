package com.yuji.app.ui.account

import com.yuji.app.ui.components.SecondaryButton
import com.yuji.app.ui.theme.LocalTrend
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.domain.Currency
import com.yuji.app.domain.Money
import com.yuji.app.ui.Format
import com.yuji.app.ui.components.AccountIcon
import com.yuji.app.ui.components.ChartPoint
import com.yuji.app.ui.components.CnyText
import com.yuji.app.ui.components.ConfirmDialog
import com.yuji.app.ui.components.LineChart
import com.yuji.app.ui.components.MASK
import com.yuji.app.ui.components.NativeAmountText
import com.yuji.app.ui.components.SectionHeader
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.components.YujiTopBar
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.nav.Routes
import com.yuji.app.ui.theme.Amount
import com.yuji.app.ui.theme.LocalHideAmounts
import com.yuji.app.ui.theme.LocalYujiColors
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun AccountDetailScreen(nav: NavController, id: Long) {
    val c = LocalContainer.current
    val portfolio by c.repository.portfolio.collectAsStateWithLifecycle()
    val history by remember(id) { c.repository.history(id) }.collectAsStateWithLifecycle(emptyList())
    val transfers by remember(id) { c.repository.transfersOf(id) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val hidden = LocalHideAmounts.current
    val trend = LocalTrend.current
    var updating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val v = portfolio.account(id)
    if (v == null) return
    val a = v.account
    val group = portfolio.groups.firstOrNull { it.group.id == a.groupId }?.group?.name.orEmpty()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            YujiTopBar(a.name, onBack = { nav.popBackStack() }) {
                IconButton(onClick = { nav.navigate(Routes.edit(id)) }) { Icon(Icons.Rounded.Edit, contentDescription = "编辑") }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                YujiCard(padding = PaddingValues(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountIcon(a.iconType, a.iconValue, a.name, size = 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(group, style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.TextMuted)
                            Text(
                                "${Currency.of(a.currency)?.label ?: a.currency} · ${Format.age(a.updatedAt)}更新",
                                style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    NativeAmountText(a.balance, a.currency, Amount.large, color = LocalYujiColors.current.Text)
                    if (a.currency != Currency.BASE) {
                        Spacer(Modifier.height(4.dp))
                        if (v.valueCny != null) {
                            Row {
                                Text("≈ ", style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted)
                                CnyText(v.valueCny, MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted)
                                v.rate?.let {
                                    Text("  ·  汇率 ${Money.rate(it)}", style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextFaint)
                                }
                            }
                        } else {
                            Text("汇率尚未获取，暂无法折算人民币", style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.Amber)
                        }
                    }
                    if (a.note.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(a.note, style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted)
                    }
                    if (!a.includeInTotal) {
                        Spacer(Modifier.height(8.dp))
                        Text("此账户不计入总资产", style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.Amber)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("更新余额", onClick = { updating = true }, modifier = Modifier.weight(1f), icon = Icons.Rounded.EditNote)
                        SecondaryButton("转移", onClick = { nav.navigate(Routes.transfer(id)) }, modifier = Modifier.weight(1f), icon = Icons.Rounded.SwapHoriz)
                    }
                }
            }

            if (history.size >= 2) {
                item {
                    YujiCard {
                        Text("余额走势", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(12.dp))
                        LineChart(
                            history.map { ChartPoint(it.at, it.balance.toDouble()) },
                            Modifier.fillMaxWidth().height(180.dp),
                            valueLabel = { Money.amount(BigDecimal(it).setScale(0, java.math.RoundingMode.HALF_UP), a.currency, withCode = false) },
                            dateLabel = Format::shortDate,
                        )
                    }
                }
            }

            item { SectionHeader("余额记录") }
            if (history.isEmpty()) {
                item { Text("暂无记录", color = LocalYujiColors.current.TextFaint, modifier = Modifier.padding(start = 4.dp)) }
            } else {
                item {
                    YujiCard(padding = PaddingValues(vertical = 4.dp)) {
                        val rows = history.reversed()
                        rows.forEachIndexed { i, h ->
                            val prev = rows.getOrNull(i + 1)?.balance
                            val delta = prev?.let { h.balance - it }
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalYujiColors.current.Outline)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Format.dateTime(h.at), style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.TextMuted, modifier = Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    NativeAmountText(h.balance, a.currency, Amount.row, color = LocalYujiColors.current.Text)
                                    if (delta != null && delta.signum() != 0) {
                                        Text(
                                            if (hidden) MASK else (if (delta.signum() > 0) "+" else "−") + Money.amount(delta.abs(), a.currency, withCode = false),
                                            style = Amount.small, color = trend.of(delta.signum()),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (transfers.isNotEmpty()) {
                item { SectionHeader("转移记录") }
                item {
                    YujiCard(padding = PaddingValues(vertical = 4.dp)) {
                        transfers.forEachIndexed { i, t ->
                            val isOut = t.fromId == id
                            val otherId = if (isOut) t.toId else t.fromId
                            val otherName = otherId?.let { portfolio.account(it)?.account?.name } ?: "已删除账户"
                            val amount = if (isOut) t.outAmount else t.inAmount
                            val currency = if (isOut) a.currency else portfolio.account(otherId ?: -1L)?.account?.currency ?: a.currency
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalYujiColors.current.Outline)
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (isOut) "转出 → $otherName" else "转入 ← $otherName",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    val sub = buildList {
                                        add(Format.dateTime(t.at))
                                        if (t.note.isNotBlank()) add(t.note)
                                        if (isOut && t.fee.signum() > 0) add("手续费 ${Money.amount(t.fee, a.currency, withCode = false)}")
                                    }.joinToString(" · ")
                                    Text(sub, style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint)
                                }
                                NativeAmountText(
                                    amount, currency, Amount.row,
                                    color = LocalTrend.current.of(if (isOut) -1 else 1),
                                )
                            }
                        }
                    }
                }
            }

            item {
                TextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除账户", color = LocalYujiColors.current.Coral)
                }
            }
        }
    }

    if (updating) {
        BalanceDialog(
            initial = a.balance,
            currency = a.currency,
            onDismiss = { updating = false },
            onSave = { value -> scope.launch { c.repository.confirmBalances(mapOf(id to value)) } },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "删除「${a.name}」？",
            message = "账户和它的余额历史会被删除，已有的资产快照不受影响。",
            confirm = "删除",
            destructive = true,
            onConfirm = { scope.launch { c.repository.deleteAccount(id); nav.popBackStack() } },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
fun BalanceDialog(initial: BigDecimal, currency: String, onDismiss: () -> Unit, onSave: (BigDecimal) -> Unit) {
    val inputStr = Money.toInput(initial)
    var text by remember { mutableStateOf(TextFieldValue(inputStr, selection = TextRange(0, inputStr.length))) }
    val value = Money.parse(text.text)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalYujiColors.current.CardHigh,
        title = { Text("更新余额") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                suffix = { Text(currency) },
                singleLine = true,
                isError = value == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
                textStyle = Amount.row,
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(enabled = value != null, onClick = { onSave(value!!); onDismiss() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = LocalYujiColors.current.TextMuted) } },
    )
}
