package com.yuji.app.ui.account

import androidx.compose.foundation.border
import com.yuji.app.ui.components.YujiChip
import com.yuji.app.ui.components.bottomBarSurface
import com.yuji.app.ui.components.inputWell
import com.yuji.app.ui.components.yujiSwitchColors
import com.yuji.app.ui.theme.YujiType
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.data.AccountDraft
import com.yuji.app.data.db.IconType
import com.yuji.app.domain.Currency
import com.yuji.app.domain.Money
import com.yuji.app.ui.components.AccountIcon
import com.yuji.app.ui.components.PrimaryButton
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.components.YujiTopBar
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.theme.Amount
import com.yuji.app.ui.theme.LocalYujiColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditScreen(nav: NavController, id: Long?, presetGroup: Long?) {
    val c = LocalContainer.current
    val portfolio by c.repository.portfolio.collectAsStateWithLifecycle()
    val groups by c.repository.groups.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val existing = remember(id) { id?.let { portfolio.account(it)?.account } }

    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var balance by rememberSaveable { mutableStateOf(existing?.balance?.let(Money::toInput).orEmpty()) }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: Currency.BASE) }
    var groupId by rememberSaveable {
        mutableLongStateOf(existing?.groupId ?: presetGroup ?: groups.firstOrNull()?.id ?: -1L)
    }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var include by rememberSaveable { mutableStateOf(existing?.includeInTotal ?: true) }
    var iconType by rememberSaveable { mutableStateOf(existing?.iconType ?: IconType.NONE) }
    var iconValue by rememberSaveable { mutableStateOf(existing?.iconValue.orEmpty()) }
    var pickIcon by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    var newGroup by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (groupId == -1L && groups.isNotEmpty()) groupId = groups.first().id
    val parsed = Money.parse(balance)
    val valid = name.isNotBlank() && parsed != null && groups.any { it.id == groupId }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { YujiTopBar(if (existing == null) "新建账户" else "编辑账户", onBack = { nav.popBackStack() }) },
        bottomBar = {
            Column(Modifier.bottomBarSurface().navigationBarsPadding().imePadding().padding(16.dp)) {
                error?.let { Text(it, color = LocalYujiColors.current.Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
                PrimaryButton(
                    text = if (existing == null) "添加账户" else "保存",
                    enabled = valid && !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            runCatching {
                                c.repository.saveAccount(
                                    AccountDraft(
                                        id = existing?.id, groupId = groupId, name = name.trim(), currency = currency,
                                        balance = parsed!!, iconType = iconType, iconValue = iconValue,
                                        note = note.trim(), includeInTotal = include,
                                    ),
                                )
                            }.onSuccess { nav.popBackStack() }
                                .onFailure { error = "保存失败：${it.message}"; saving = false }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            YujiCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { pickIcon = true }) {
                        AccountIcon(iconType, iconValue, name.ifBlank { "账" }, size = 56.dp)
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(LocalYujiColors.current.Accent)
                                .border(1.5.dp, LocalYujiColors.current.Background, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Edit, contentDescription = "更换图标", tint = Color.White, modifier = Modifier.size(12.dp)) }
                    }
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("账户名称") },
                        placeholder = { Text("如：支付宝、招商银行") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            YujiCard {
                Text("当前余额", style = YujiType.tag, color = LocalYujiColors.current.TextMuted)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.inputWell()
                            .clickable { pickCurrency = true }.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(currency, style = MaterialTheme.typography.titleSmall)
                        Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = LocalYujiColors.current.TextMuted, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = balance,
                        onValueChange = { balance = it },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        isError = balance.isNotEmpty() && parsed == null,
                        textStyle = Amount.large,
                        shape = MaterialTheme.shapes.medium,
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "负数表示负债，例如信用卡欠款",
                    style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            YujiCard {
                Text("所属分组", style = YujiType.tag, color = LocalYujiColors.current.TextMuted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g ->
                        YujiChip(
                            g.name,
                            selected = g.id == groupId,
                            onClick = { groupId = g.id },
                            leadingIcon = if (g.id == groupId) Icons.Rounded.Check else null,
                        )
                    }
                    YujiChip("＋ 新分组", selected = false, onClick = { newGroup = true })
                }
            }

            YujiCard {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("计入总资产", style = MaterialTheme.typography.bodyLarge)
                        Text("关闭后仍会显示，但不参与净资产计算", style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint)
                    }
                    Switch(
                        checked = include, onCheckedChange = { include = it },
                        colors = yujiSwitchColors(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (pickIcon) {
        IconPickerSheet(onDismiss = { pickIcon = false }, onPicked = { t, v -> iconType = t; iconValue = v })
    }
    if (pickCurrency) {
        CurrencySheet(currency, onDismiss = { pickCurrency = false }, onPick = { currency = it })
    }
    if (newGroup) {
        NameDialog(
            title = "新分组",
            initial = "",
            onDismiss = { newGroup = false },
            onSave = { n -> scope.launch { groupId = c.repository.addGroup(n) } },
        )
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalYujiColors.current.Accent,
    unfocusedBorderColor = LocalYujiColors.current.OutlineStrong,
    focusedContainerColor = LocalYujiColors.current.Card,
    unfocusedContainerColor = LocalYujiColors.current.Card,
    cursorColor = LocalYujiColors.current.AccentText,
    focusedLabelColor = LocalYujiColors.current.AccentText,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySheet(selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalYujiColors.current.Card) {
        Text("选择币种", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Currency.entries.forEach { cur ->
            ListItem(
                headlineContent = { Text("${cur.code}  ${cur.label}") },
                leadingContent = {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(LocalYujiColors.current.CardHigh), contentAlignment = Alignment.Center) {
                        Text(cur.symbol, style = MaterialTheme.typography.titleSmall)
                    }
                },
                trailingContent = { if (cur.code == selected) Icon(Icons.Rounded.Check, null, tint = LocalYujiColors.current.AccentText) },
                colors = ListItemDefaults.colors(containerColor = LocalYujiColors.current.Card),
                modifier = Modifier.clickable { onPick(cur.code); onDismiss() },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalYujiColors.current.CardHigh,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                shape = MaterialTheme.shapes.medium, placeholder = { Text("名称") },
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim()); onDismiss() }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = LocalYujiColors.current.TextMuted) } },
    )
}
