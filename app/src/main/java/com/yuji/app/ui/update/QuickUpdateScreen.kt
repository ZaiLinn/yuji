package com.yuji.app.ui.update

import com.yuji.app.ui.components.YujiChip
import com.yuji.app.ui.components.bottomBarSurface
import com.yuji.app.ui.components.inputWell
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.domain.Money
import com.yuji.app.ui.Format
import com.yuji.app.ui.components.AccountIcon
import com.yuji.app.ui.components.EmptyState
import com.yuji.app.ui.components.MASK
import com.yuji.app.ui.components.PrimaryButton
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.components.YujiTopBar
import com.yuji.app.ui.home.OVERDUE_DAYS
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.theme.Amount
import com.yuji.app.ui.theme.LocalHideAmounts
import com.yuji.app.ui.theme.LocalTrend
import com.yuji.app.ui.theme.LocalYujiColors
import kotlinx.coroutines.launch

@Composable
fun QuickUpdateScreen(nav: NavController, overdueOnlyInitially: Boolean) {
    val c = LocalContainer.current
    val portfolio by c.repository.portfolio.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val hidden = LocalHideAmounts.current
    val trend = LocalTrend.current
    val now = remember { System.currentTimeMillis() }

    // Order is frozen on entry so rows don't jump while the user is typing.
    val order = remember { portfolio.accounts.sortedBy { it.account.updatedAt }.map { it.account.id } }
    val inputs = remember {
        mutableStateMapOf<Long, TextFieldValue>().apply { portfolio.accounts.forEach { put(it.account.id, TextFieldValue(Money.toInput(it.account.balance))) } }
    }
    var overdueOnly by remember { mutableStateOf(overdueOnlyInitially) }
    var saving by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val byId = portfolio.accounts.associateBy { it.account.id }
    val visible = order.mapNotNull { byId[it] }
        .filter { !overdueOnly || Format.ageDays(it.account.updatedAt, now) >= OVERDUE_DAYS }
    val parsed = visible.associate { it.account.id to Money.parse(inputs[it.account.id]?.text) }
    val invalid = parsed.count { it.value == null }
    val changed = visible.count { v -> parsed[v.account.id]?.let { it.compareTo(v.account.balance) != 0 } == true }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { YujiTopBar("更新余额", onBack = { nav.popBackStack() }) },
        bottomBar = {
            Column(
                Modifier
                    .bottomBarSurface()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    when {
                        invalid > 0 -> "$invalid 个余额格式有误"
                        visible.isEmpty() -> "没有需要更新的账户"
                        changed == 0 -> "余额没有变化，保存会把 ${visible.size} 个账户标记为今天已确认"
                        else -> "${visible.size} 个账户将被确认，其中 $changed 个余额有变化"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (invalid > 0) LocalYujiColors.current.Coral else LocalYujiColors.current.TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                )
                PrimaryButton(
                    text = if (changed > 0) "保存 · $changed 项变化" else "确认余额",
                    enabled = !saving && invalid == 0 && visible.isNotEmpty(),
                    onClick = {
                        saving = true
                        scope.launch {
                            c.repository.confirmBalances(visible.associate { it.account.id to parsed.getValue(it.account.id)!! })
                            nav.popBackStack()
                        }
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    YujiChip("全部 ${order.size}", selected = !overdueOnly, onClick = { overdueOnly = false })
                    YujiChip("超过 $OVERDUE_DAYS 天未更新", selected = overdueOnly, onClick = { overdueOnly = true })
                }
            }
            if (visible.isEmpty()) {
                item { EmptyState(Icons.Rounded.CheckCircle, "都是最新的", "所有账户都在 $OVERDUE_DAYS 天内确认过。") }
            }
            items(visible, key = { it.account.id }) { v ->
                val a = v.account
                val text = inputs[a.id]?.text.orEmpty()
                val value = Money.parse(text)
                val delta = value?.subtract(a.balance)
                YujiCard(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountIcon(a.iconType, a.iconValue, a.name, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val age = Format.ageDays(a.updatedAt, now)
                            Text(
                                "${a.currency} · ${Format.age(a.updatedAt, now)}更新",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (age >= OVERDUE_DAYS) LocalYujiColors.current.Amber else LocalYujiColors.current.TextFaint,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(150.dp)) {
                            val displayValue = if (hidden && text == Money.toInput(a.balance))
                                TextFieldValue(MASK)
                            else
                                inputs[a.id] ?: TextFieldValue("")
                            BasicTextField(
                                value = displayValue,
                                onValueChange = { v -> inputs[a.id] = v.copy(text = v.text.replace(MASK, "")) },
                                singleLine = true,
                                textStyle = Amount.row.copy(color = LocalYujiColors.current.Text, textAlign = TextAlign.End),
                                cursorBrush = SolidColor(LocalYujiColors.current.AccentText),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier
                                    .onFocusChanged { fs ->
                                        if (fs.isFocused) {
                                            val cur = inputs[a.id] ?: return@onFocusChanged
                                            inputs[a.id] = cur.copy(selection = TextRange(cur.text.length))
                                            val idx = visible.indexOfFirst { it.account.id == a.id }
                                            if (idx >= 0) scope.launch { listState.animateScrollToItem(idx + 1) }
                                        }
                                    }
                                    .fillMaxWidth()
                                    .inputWell()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            when {
                                value == null -> Text("格式有误", style = Amount.small, color = LocalYujiColors.current.Coral)
                                delta != null && delta.signum() != 0 -> Text(
                                    if (hidden) MASK else (if (delta.signum() > 0) "+" else "−") + Money.amount(delta.abs(), a.currency),
                                    style = Amount.small,
                                    color = trend.of(delta.signum()),
                                )
                                else -> Text("未变化", style = Amount.small, color = LocalYujiColors.current.TextFaint)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

