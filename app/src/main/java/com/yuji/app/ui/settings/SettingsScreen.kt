package com.yuji.app.ui.settings

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import com.yuji.app.ui.theme.ThemeMode
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.yuji.app.domain.Money
import com.yuji.app.ui.Format
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.home.GoalDialog
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.nav.Routes
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.components.IconPlate
import com.yuji.app.ui.components.PageTitle
import com.yuji.app.ui.components.SectionLabel
import com.yuji.app.ui.components.yujiSwitchColors
import androidx.compose.foundation.shape.CircleShape

@Composable
fun SettingsScreen(nav: NavController) {
    val c = LocalContainer.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var goal by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("设置", Modifier.padding(vertical = 6.dp)) }

        item { GroupLabel("显示") }
        item {
            SettingsGroup {
                SettingRow(Icons.Rounded.VisibilityOff, "隐藏金额", "所有页面用 •••• 代替金额", trailing = {
                    Switch(
                        checked = settings.hideAmounts, onCheckedChange = c.settings::setHideAmounts,
                        colors = yujiSwitchColors(),
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
                val light = settings.themeMode == ThemeMode.LIGHT
                SettingRow(
                    if (light) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, "外观",
                    if (light) "浅色" else "深色",
                    onClick = { c.settings.setThemeMode(if (light) ThemeMode.DARK else ThemeMode.LIGHT) },
                    trailing = { ThemeToggle(light) { c.settings.setThemeMode(if (it) ThemeMode.LIGHT else ThemeMode.DARK) } },
                )
                Divider()
                SettingRow(
                    Icons.Rounded.Flag, "资产目标",
                    settings.goal?.let { Money.compactCny(it) } ?: "未设置",
                    onClick = { goal = true },
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
}

@Composable
fun GroupLabel(text: String) {
    SectionLabel(text, Modifier.padding(top = 12.dp))
}

@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    YujiCard(padding = PaddingValues(vertical = 4.dp)) { content() }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 60.dp), color = LocalYujiColors.current.Outline)

@Composable
private fun Dot(color: Color) = Box(Modifier.size(10.dp).clip(CircleShape).background(color))

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
        IconPlate(icon)
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

/** Two-segment 深色 / 浅色 picker. */
@Composable
private fun ThemeToggle(light: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalYujiColors.current
    Row(
        Modifier.clip(CircleShape).background(c.Muted).border(1.dp, c.Outline, CircleShape).padding(3.dp),
    ) {
        listOf(false to "深色", true to "浅色").forEach { (isLight, label) ->
            val selected = light == isLight
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else c.TextMuted,
                modifier = Modifier
                    .clip(CircleShape)
                    .then(if (selected) Modifier.background(Brush.horizontalGradient(listOf(c.Accent, c.AccentBright))) else Modifier)
                    .clickable { onChange(isLight) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
