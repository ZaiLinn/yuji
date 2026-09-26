package com.yuji.app.ui.recurring

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.data.RecurringDraft
import com.yuji.app.data.db.RecurringEntity
import com.yuji.app.data.db.RecurringPeriod
import com.yuji.app.domain.Money
import com.yuji.app.domain.Portfolio
import com.yuji.app.domain.Recurrence
import com.yuji.app.ui.account.fieldColors
import com.yuji.app.ui.components.CnyText
import com.yuji.app.ui.components.ConfirmDialog
import com.yuji.app.ui.components.EmptyState
import com.yuji.app.ui.components.KeyValueRow
import com.yuji.app.ui.components.NativeAmountText
import com.yuji.app.ui.components.PrimaryButton
import com.yuji.app.ui.components.SectionLabel
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.components.YujiChip
import com.yuji.app.ui.components.YujiTopBar
import com.yuji.app.ui.components.bottomBarSurface
import com.yuji.app.ui.components.yujiSwitchColors
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.nav.Routes
import com.yuji.app.ui.theme.Amount
import com.yuji.app.ui.theme.LocalTrend
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.transfer.AccountPickerSheet
import com.yuji.app.ui.transfer.AccountSelector
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

private val TWELVE = BigDecimal(12)

/** Monthly equivalent in CNY (yearly rules count 1/12), or null when the account's rate is unknown. */
private fun monthlyCny(rule: RecurringEntity, portfolio: Portfolio): BigDecimal? {
    val currency = portfolio.account(rule.accountId)?.account?.currency ?: return null
    val rate = portfolio.rateOf(currency) ?: return null
    val cny = Money.toCny(rule.amount, rate)
    return if (rule.period == RecurringPeriod.YEARLY) cny.divide(TWELVE, 2, RoundingMode.HALF_UP) else cny
}

@Composable
fun RecurringScreen(nav: NavController) {
    val c = LocalContainer.current
    val rules by c.repository.recurring.collectAsStateWithLifecycle()
    val portfolio by c.repository.portfolio.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            YujiTopBar("固定收支", onBack = { nav.popBackStack() }) {
                if (portfolio.accounts.isNotEmpty()) {
                    IconButton(onClick = { nav.navigate(Routes.recurringEdit()) }) { Icon(Icons.Rounded.Add, contentDescription = "添加") }
                }
            }
        },
    ) { padding ->
        if (rules.isEmpty()) {
            EmptyState(
                Icons.Rounded.Repeat, "还没有固定收支",
                if (portfolio.accounts.isEmpty()) "先添加一个账户，再设置工资、房租、会员费等固定收支。"
                else "工资、房租、会员费……到了设定的日期，会自动增减对应账户的余额。",
                Modifier.padding(padding),
                action = if (portfolio.accounts.isEmpty()) null else "添加固定收支" to { nav.navigate(Routes.recurringEdit()) },
            )
            return@Scaffold
        }
        val active = rules.filter { it.enabled }
        val income = active.filter { it.income }.mapNotNull { monthlyCny(it, portfolio) }.fold(BigDecimal.ZERO) { s, v -> s + v }
        val expense = active.filter { !it.income }.mapNotNull { monthlyCny(it, portfolio) }.fold(BigDecimal.ZERO) { s, v -> s + v }

        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                YujiCard {
                    Text("每月结余（估算）", style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.TextMuted)
                    CnyText(income - expense, Amount.large, signed = true, color = LocalTrend.current.of((income - expense).signum()))
                    Spacer(Modifier.height(8.dp))
                    KeyValueRow("每月固定收入") { CnyText(income, Amount.row) }
                    KeyValueRow("每月固定支出") { CnyText(expense, Amount.row) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "每年一次的收支按 1/12 折算进每月，外币按当前汇率折合人民币。已暂停的不计入。",
                        style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint,
                    )
                }
            }
            val sections = listOf(
                "每月收入" to rules.filter { it.income && it.period == RecurringPeriod.MONTHLY },
                "每月支出" to rules.filter { !it.income && it.period == RecurringPeriod.MONTHLY },
                "每年收入" to rules.filter { it.income && it.period == RecurringPeriod.YEARLY },
                "每年支出" to rules.filter { !it.income && it.period == RecurringPeriod.YEARLY },
            ).filter { it.second.isNotEmpty() }
            sections.forEach { (title, list) ->
                item(key = title) { SectionLabel(title, Modifier.padding(top = 4.dp)) }
                item(key = "$title-list") {
                    YujiCard(padding = PaddingValues(vertical = 4.dp)) {
                        list.forEachIndexed { i, rule ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalYujiColors.current.Outline)
                            RuleRow(
                                rule, portfolio,
                                onClick = { nav.navigate(Routes.recurringEdit(rule.id)) },
                                onToggle = { on -> scope.launch { c.repository.setRecurringEnabled(rule.id, on) } },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "到了设定日期，对应账户的余额会自动增加或减少，并记在账户的转移记录里。错过的日期会在下次打开余记时补上。",
                    style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RuleRow(rule: RecurringEntity, portfolio: Portfolio, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val account = portfolio.account(rule.accountId)?.account
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rule.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                add(Recurrence.describe(rule.period, rule.month, rule.day))
                add(account?.name ?: "账户已删除")
                add(if (rule.enabled) "下次 " + Recurrence.date(rule.nextAt) else "已暂停")
            }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            NativeAmountText(
                if (rule.income) rule.amount else rule.amount.negate(), account?.currency.orEmpty(), Amount.row,
                color = if (rule.enabled) LocalTrend.current.of(if (rule.income) 1 else -1) else LocalYujiColors.current.TextFaint,
            )
            Switch(checked = rule.enabled, onCheckedChange = onToggle, colors = yujiSwitchColors())
        }
    }
}

@Composable
fun RecurringEditScreen(nav: NavController, id: Long?) {
    val c = LocalContainer.current
    val portfolio by c.repository.portfolio.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val accounts = portfolio.accounts

    var loaded by remember { mutableStateOf(id == null) }
    var name by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.account?.id) }
    var amountText by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(RecurringPeriod.MONTHLY) }
    var monthText by remember { mutableStateOf("1") }
    var dayText by remember { mutableStateOf("1") }
    var enabled by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        val r = c.repository.recurringRule(id)
        if (r == null) {
            nav.popBackStack()
            return@LaunchedEffect
        }
        name = r.name
        income = r.income
        accountId = r.accountId
        amountText = Money.toInput(r.amount)
        period = r.period
        monthText = r.month.toString()
        dayText = r.day.toString()
        enabled = r.enabled
        loaded = true
    }

    val account = accounts.firstOrNull { it.account.id == accountId }
    val amount = Money.parse(amountText)
    val month = monthText.trim().toIntOrNull()?.takeIf { it in 1..12 }
    val day = dayText.trim().toIntOrNull()?.takeIf { it in 1..31 }
    val yearly = period == RecurringPeriod.YEARLY
    val valid = loaded && name.isNotBlank() && account != null && amount != null && amount.signum() > 0 &&
        day != null && (!yearly || month != null)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            YujiTopBar(if (id == null) "添加固定收支" else "编辑固定收支", onBack = { nav.popBackStack() }) {
                if (id != null) {
                    IconButton(onClick = { deleting = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除", tint = LocalYujiColors.current.TextMuted)
                    }
                }
            }
        },
        bottomBar = {
            Column(Modifier.bottomBarSurface().navigationBarsPadding().imePadding().padding(16.dp)) {
                error?.let { Text(it, color = LocalYujiColors.current.Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
                PrimaryButton("保存", enabled = valid && !saving, onClick = {
                    saving = true
                    scope.launch {
                        runCatching {
                            c.repository.saveRecurring(
                                RecurringDraft(
                                    id = id, accountId = account!!.account.id, name = name.trim(), amount = amount!!,
                                    income = income, period = period, month = month ?: 1, day = day!!, enabled = enabled,
                                ),
                            )
                        }
                            .onSuccess { nav.popBackStack() }
                            .onFailure { error = it.message; saving = false }
                    }
                })
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            YujiCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    YujiChip("支出", selected = !income, onClick = { income = false })
                    YujiChip("收入", selected = income, onClick = { income = true })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("名称") },
                    placeholder = { Text(if (income) "例如 工资" else "例如 房租") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }
            YujiCard {
                AccountSelector(if (income) "存入账户" else "扣款账户", account) { picking = true }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, label = { Text("金额") }, singleLine = true,
                    suffix = { account?.let { Text(it.account.currency) } },
                    isError = amountText.isNotBlank() && (amount == null || amount.signum() <= 0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = Amount.row, shape = MaterialTheme.shapes.medium, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }
            YujiCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    YujiChip("每月", selected = !yearly, onClick = { period = RecurringPeriod.MONTHLY })
                    YujiChip("每年", selected = yearly, onClick = { period = RecurringPeriod.YEARLY })
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (yearly) {
                        OutlinedTextField(
                            value = monthText, onValueChange = { monthText = it.filter(Char::isDigit).take(2) },
                            label = { Text("月份") }, suffix = { Text("月") }, singleLine = true,
                            isError = monthText.isNotBlank() && month == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.medium, colors = fieldColors(), modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = dayText, onValueChange = { dayText = it.filter(Char::isDigit).take(2) },
                        label = { Text("日期") }, suffix = { Text("日") }, singleLine = true,
                        isError = dayText.isNotBlank() && day == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.medium, colors = fieldColors(), modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (day != null && day > 28) "没有 $day 日的月份，会在当月最后一天执行。" else "在当天 0 点（北京时间）后执行；错过的日期会在下次打开余记时补上。",
                    style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint,
                )
            }
            YujiCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用", style = MaterialTheme.typography.bodyLarge)
                        Text("暂停后不会增减余额，恢复时从下一个日期开始", style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = yujiSwitchColors())
                }
            }
            if (id == null && valid && enabled) {
                val next = Recurrence.nextAfter(period, month ?: 1, day!!, System.currentTimeMillis())
                Text(
                    "首次执行：${Recurrence.date(next)}。今天之前的日期不会补记。",
                    style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (picking) {
        AccountPickerSheet(accounts = accounts, exclude = null, onDismiss = { picking = false }, onPick = { accountId = it })
    }
    if (deleting && id != null) {
        ConfirmDialog(
            "删除「$name」？", "已经执行过的余额变动会保留。", "删除", destructive = true,
            onConfirm = { scope.launch { c.repository.deleteRecurring(id); nav.popBackStack() } },
            onDismiss = { deleting = false },
        )
    }
}
