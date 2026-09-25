package com.yuji.app.ui.settings

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.domain.Currency
import com.yuji.app.domain.Money
import com.yuji.app.ui.Format
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.home.GoalDialog
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.nav.Routes
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.theme.ThemeMode
import com.yuji.app.ui.theme.YujiColors

@Composable
fun SettingsScreen(nav: NavController) {
    val c = LocalContainer.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var goal by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("设置", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 6.dp)) }

        item { GroupLabel("显示") }
        item {
            SettingsGroup {
                SettingRow(Icons.Rounded.VisibilityOff, "隐藏金额", "所有页面用 •••• 代替金额", trailing = {
                    Switch(
                        checked = settings.hideAmounts, onCheckedChange = c.settings::setHideAmounts,
                        colors = SwitchDefaults.colors(checkedTrackColor = LocalYujiColors.current.Mint, checkedThumbColor = LocalYujiColors.current.Background),
                    )
                })
                Divider()
                SettingRow(Icons.Rounded.Palette, "涨跌颜色", if (settings.greenUp) "绿涨红跌" else "红涨绿跌", onClick = {
                    c.settings.setGreenUp(!settings.greenUp)
                }, trailing = {
                    Row {
                        Dot(if (settings.greenUp) LocalYujiColors.current.Mint else LocalYujiColors.current.Coral)
                        Spacer(Modifier.width(4.dp))
                        Dot(if (settings.greenUp) LocalYujiColors.current.Coral else LocalYujiColors.current.Mint)
                    }
                })
                Divider()
                val (themeIcon, themeLabel) = when (settings.themeMode) {
                    ThemeMode.DARK  -> Icons.Rounded.DarkMode  to "深色"
                    ThemeMode.LIGHT -> Icons.Rounded.LightMode to "浅色"
                }
                SettingRow(themeIcon, "外观主题", themeLabel, onClick = {
                    c.settings.setThemeMode(
                        if (settings.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                    )
                })
                Divider()
                SettingRow(
                    Icons.Rounded.Flag, "资产目标",
                    settings.goal?.let { Money.compactCny(it) } ?: "未设置",
                    onClick = { goal = true },
                )
                Divider()
                SettingRow(
                    Icons.Rounded.CurrencyExchange, "显示货币",
                    Currency.of(settings.displayCurrency)?.let { "${it.code} ${it.label}" } ?: settings.displayCurrency,
                    onClick = { showCurrencyPicker = true },
                )
            }
        }

        item { GroupLabel("账户") }
        item {
            SettingsGroup {
                SettingRow(Icons.Rounded.Folder, "分组管理", "新建、重命名、排序分组；账户顺序在首页长按拖动", onClick = { nav.navigate(Routes.GROUPS) })
                Divider()
                SettingRow(Icons.Rounded.SwapHoriz, "资产转移", "记录账户之间的资金移动", onClick = { nav.navigate(Routes.transfer()) })
            }
        }

        item { GroupLabel("数据") }
        item {
            SettingsGroup {
                SettingRow(Icons.Rounded.CurrencyExchange, "汇率", "自动更新，可手动指定", onClick = { nav.navigate(Routes.RATES) })
                Divider()
                SettingRow(
                    Icons.Rounded.Save, "备份与恢复",
                    settings.lastBackupAt?.let { "上次备份：" + Format.dateTime(it) } ?: "还没有备份过",
                    onClick = { nav.navigate(Routes.BACKUP) },
                )
                Divider()
                SettingRow(Icons.Rounded.Image, "图标缓存", "清理不再使用的图标文件", onClick = { nav.navigate(Routes.ICONS) })
            }
        }

        item { GroupLabel("其他") }
        item {
            SettingsGroup {
                SettingRow(Icons.Rounded.Info, "关于余记", "版本与隐私说明", onClick = { nav.navigate(Routes.ABOUT) })
            }
        }
    }

    if (goal) GoalDialog(settings.goal, onDismiss = { goal = false }, onSave = c.settings::setGoal)

    if (showCurrencyPicker) {
        val fiatCurrencies = Currency.entries.filter { !it.isCrypto }
        AlertDialog(
            onDismissRequest = { showCurrencyPicker = false },
            containerColor = LocalYujiColors.current.CardHigh,
            title = { Text("显示货币") },
            text = {
                Column {
                    Text("净资产将以所选货币换算显示。", style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    fiatCurrencies.forEach { cur ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                c.settings.setDisplayCurrency(cur.code)
                                showCurrencyPicker = false
                            }.padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${cur.symbol}  ${cur.code}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(cur.label, style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted)
                            if (settings.displayCurrency == cur.code) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = LocalYujiColors.current.Mint, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCurrencyPicker = false }) { Text("取消", color = LocalYujiColors.current.TextMuted) } },
        )
    }
}

@Composable
fun GroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = LocalYujiColors.current.TextMuted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    YujiCard(padding = PaddingValues(vertical = 4.dp)) { content() }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 60.dp), color = LocalYujiColors.current.Outline.copy(alpha = 0.5f))

@Composable
private fun Dot(color: Color) = Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(color))

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(LocalYujiColors.current.CardHigh), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = LocalYujiColors.current.Mint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = LocalYujiColors.current.TextFaint)
        }
    }
}
