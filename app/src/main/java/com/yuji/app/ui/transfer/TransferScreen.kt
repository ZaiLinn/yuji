package com.yuji.app.ui.transfer

import com.yuji.app.ui.components.CardVariant
import com.yuji.app.ui.components.bottomBarSurface
import com.yuji.app.ui.components.inputWell
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.domain.AccountValue
import com.yuji.app.domain.Currency
import com.yuji.app.domain.Money
import com.yuji.app.ui.account.fieldColors
import com.yuji.app.ui.components.AccountIcon
import com.yuji.app.ui.components.EmptyState
import com.yuji.app.ui.components.NativeAmountText
import com.yuji.app.ui.components.PrimaryButton
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.components.YujiTopBar
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.theme.Amount
import com.yuji.app.ui.theme.LocalYujiColors
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

@Composable
fun TransferScreen(nav: NavController, presetFrom: Long?) {
    val c = LocalContainer.current
    val portfolio by c.repository.portfolio.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val accounts = portfolio.accounts

    var fromId by remember { mutableStateOf(presetFrom ?: accounts.firstOrNull()?.account?.id) }
    var toId by remember { mutableStateOf(accounts.firstOrNull { it.account.id != fromId }?.account?.id) }
    var outText by remember { mutableStateOf("") }
    var inText by remember { mutableStateOf("") }
    var inTouched by remember { mutableStateOf(false) }
    var feeText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val from = accounts.firstOrNull { it.account.id == fromId }
    val to = accounts.firstOrNull { it.account.id == toId }
    val out = Money.parse(outText)
    val fee = if (feeText.isBlank()) BigDecimal.ZERO else Money.parse(feeText)
    val sameCurrency = from != null && to != null && from.account.currency == to.account.currency

    // Suggested received amount: identical for the same currency, converted through CNY otherwise.
    val suggested: BigDecimal? = if (out == null || from == null || to == null) null
    else if (sameCurrency) out
    else {
        val rf = portfolio.rateOf(from.account.currency)
        val rt = portfolio.rateOf(to.account.currency)
        if (rf == null || rt == null) null
        else out.multiply(rf).divide(rt, MathContext.DECIMAL64).setScale(Currency.decimalsOf(to.account.currency), RoundingMode.HALF_UP)
    }
    val received = if (inTouched) Money.parse(inText) else suggested

    val valid = from != null && to != null && from.account.id != to.account.id &&
        out != null && out.signum() > 0 && received != null && received.signum() > 0 && fee != null && fee.signum() >= 0

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { YujiTopBar("资产转移", onBack = { nav.popBackStack() }) },
        bottomBar = {
            if (accounts.size >= 2) {
                Column(Modifier.bottomBarSurface().navigationBarsPadding().imePadding().padding(16.dp)) {
                    error?.let { Text(it, color = LocalYujiColors.current.Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
                    PrimaryButton("确认转移", enabled = valid && !saving, onClick = {
                        saving = true
                        scope.launch {
                            runCatching { c.repository.transfer(from!!.account.id, to!!.account.id, out!!, received!!, fee!!, note.trim()) }
                                .onSuccess { nav.popBackStack() }
                                .onFailure { error = it.message; saving = false }
                        }
                    })
                }
            }
        },
    ) { padding ->
        if (accounts.size < 2) {
            EmptyState(Icons.Rounded.ArrowDownward, "至少需要两个账户", "资产转移用于记录钱从一个账户移到另一个账户。", Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "只记录资产位置的变化，不算收入或支出。",
                style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.TextMuted, modifier = Modifier.padding(horizontal = 4.dp),
            )
            YujiCard {
                AccountSelector("转出", from) { picking = "from" }
                Spacer(Modifier.height(10.dp))
                AmountField(outText, { outText = it }, "转出金额", from?.account?.currency)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { val t = fromId; fromId = toId; toId = t; inTouched = false }) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "交换", tint = LocalYujiColors.current.AccentText)
                    }
                }
                AccountSelector("转入", to) { picking = "to" }
                Spacer(Modifier.height(10.dp))
                AmountField(
                    value = if (inTouched) inText else suggested?.let(Money::toInput).orEmpty(),
                    onChange = { inText = it; inTouched = true },
                    label = if (sameCurrency) "到账金额" else "实际到账（按当前汇率预估，可修改）",
                    currency = to?.account?.currency,
                )
            }
            YujiCard {
                AmountField(feeText, { feeText = it }, "手续费（可选，从转出账户扣除）", from?.account?.currency)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("备注（可选）") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }
            if (valid) {
                YujiCard(variant = CardVariant.Raised) {
                    Text("转移后", style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    PreviewRow(from!!.account.name, from.account.balance - out!! - fee!!, from.account.currency)
                    PreviewRow(to!!.account.name, to.account.balance + received!!, to.account.currency)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    picking?.let { which ->
        AccountPickerSheet(
            accounts = accounts,
            exclude = if (which == "from") toId else fromId,
            onDismiss = { picking = null },
            onPick = { id -> if (which == "from") fromId = id else toId = id; inTouched = false },
        )
    }
}

@Composable
private fun AccountSelector(label: String, value: AccountValue?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().inputWell().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (value != null) AccountIcon(value.account.iconType, value.account.iconValue, value.account.name, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextMuted)
            Text(value?.account?.name ?: "选择账户", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) NativeAmountText(value.account.balance, value.account.currency, Amount.small)
        Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = LocalYujiColors.current.TextMuted)
    }
}

@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, label: String, currency: String?) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        suffix = { currency?.let { Text(it) } },
        isError = value.isNotBlank() && Money.parse(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = Amount.row, shape = MaterialTheme.shapes.medium, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PreviewRow(name: String, balance: BigDecimal, currency: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        NativeAmountText(balance, currency, Amount.row, color = if (balance.signum() < 0) LocalYujiColors.current.Amber else LocalYujiColors.current.Text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(accounts: List<AccountValue>, exclude: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalYujiColors.current.Card) {
        Text("选择账户", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(accounts.filter { it.account.id != exclude }, key = { it.account.id }) { v ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(v.account.id); onDismiss() }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountIcon(v.account.iconType, v.account.iconValue, v.account.name, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(v.account.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    NativeAmountText(v.account.balance, v.account.currency, Amount.small)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
