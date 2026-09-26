package com.yuji.app.ui.settings

import com.yuji.app.ui.components.SecondaryButton
import androidx.compose.ui.graphics.Color
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.IOException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.data.backup.BackupManager
import com.yuji.app.data.backup.BackupPreview
import com.yuji.app.data.db.IconType
import com.yuji.app.data.db.RateSource
import com.yuji.app.domain.Currency
import com.yuji.app.domain.Money
import com.yuji.app.ui.Format
import com.yuji.app.ui.components.PrimaryButton
import com.yuji.app.ui.components.YujiCard
import com.yuji.app.ui.components.YujiTopBar
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.theme.LocalYujiColors
import kotlinx.coroutines.launch

@Composable
fun RatesScreen(nav: NavController) {
    val c = LocalContainer.current
    val portfolio by c.repository.portfolio.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Currency?>(null) }
    val latest = portfolio.rates.values.filter { it.currency != Currency.BASE && it.source == RateSource.AUTO }.maxOfOrNull { it.updatedAt }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { YujiTopBar("汇率", onBack = { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                YujiCard {
                    Text("基准货币：CNY 人民币", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        latest?.let { "自动汇率更新于 " + Format.dateTime(it) } ?: "还没有获取过汇率",
                        style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.TextMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(if (refreshing) "正在更新…" else "立即更新汇率", enabled = !refreshing, onClick = {
                        refreshing = true
                        scope.launch {
                            val r = c.repository.refreshRates()
                            refreshing = false
                            snackbar.showSnackbar(if (r.isSuccess) "汇率已更新" else "更新失败：${r.exceptionOrNull()?.message}，继续使用上次汇率")
                        }
                    })
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "法币来自 open.er-api.com，BTC/ETH 来自 CoinGecko，USDT 按 1:1 美元计。每 12 小时自动更新一次。",
                        style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint,
                    )
                }
            }
            item {
                YujiCard(padding = PaddingValues(vertical = 4.dp)) {
                    Currency.entries.filter { it.code != Currency.BASE }.forEachIndexed { i, cur ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalYujiColors.current.Outline)
                        val r = portfolio.rates[cur.code]
                        val rate = portfolio.rateOf(cur.code)
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = cur }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${cur.code}  ${cur.label}", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    when {
                                        rate == null -> "尚未获取"
                                        r?.source == RateSource.MANUAL -> "手动设置 · 不会被自动覆盖"
                                        else -> "自动"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        rate == null -> LocalYujiColors.current.Amber
                                        r?.source == RateSource.MANUAL -> LocalYujiColors.current.Blue
                                        else -> LocalYujiColors.current.TextFaint
                                    },
                                )
                            }
                            Text(rate?.let { "¥" + Money.rate(it) } ?: "—", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    editing?.let { cur ->
        val current = portfolio.rates[cur.code]
        var text by remember(cur) { mutableStateOf(portfolio.rateOf(cur.code)?.let(Money::toInput).orEmpty()) }
        val value = Money.parse(text)
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = LocalYujiColors.current.CardHigh,
            title = { Text("1 ${cur.code} = ? CNY") },
            text = {
                Column {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, singleLine = true, prefix = { Text("¥ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("手动汇率会一直使用，直到你恢复自动。", style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.TextMuted)
                }
            },
            confirmButton = {
                TextButton(enabled = value != null && value.signum() > 0, onClick = {
                    scope.launch { c.repository.setManualRate(cur.code, value) }
                    editing = null
                }) { Text("使用手动汇率") }
            },
            dismissButton = {
                if (current?.source == RateSource.MANUAL) {
                    TextButton(onClick = {
                        scope.launch { c.repository.setManualRate(cur.code, null); c.repository.refreshRates() }
                        editing = null
                    }) { Text("恢复自动") }
                } else {
                    TextButton(onClick = { editing = null }) { Text("取消", color = LocalYujiColors.current.TextMuted) }
                }
            },
        )
    }
}

@Composable
fun BackupScreen(nav: NavController) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }

    val csvExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            busy = true
            scope.launch {
                val r = runCatching {
                    val csv = c.repository.exportCsv()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("无法写入文件")
                }
                busy = false
                snackbar.showSnackbar(if (r.isSuccess) "CSV 已导出" else "导出失败：${r.exceptionOrNull()?.message}")
            }
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri: Uri? ->
        if (uri != null) {
            busy = true
            scope.launch {
                val r = runCatching { c.backup.export(uri) }
                busy = false
                snackbar.showSnackbar(if (r.isSuccess) "备份已导出" else "导出失败：${r.exceptionOrNull()?.message}")
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching { c.backup.inspect(uri) }
                    .onSuccess { preview = it }
                    .onFailure { snackbar.showSnackbar("无法读取：${it.message}") }
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { YujiTopBar("备份与恢复", onBack = { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            YujiCard {
                Text("上次备份", style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.TextMuted)
                Text(settings.lastBackupAt?.let(Format::dateTime) ?: "从未备份", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    "备份文件（.yuji）包含账户、分组、图标、余额历史、快照、汇率、固定收支和资产目标，并附带 SHA-256 校验值用于发现文件损坏。文件未加密，请妥善保管。",
                    style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.TextMuted,
                )
            }
            PrimaryButton(if (busy) "处理中…" else "导出备份", enabled = !busy, onClick = { exporter.launch(c.backup.suggestedFileName()) })
            SecondaryButton("从备份恢复", onClick = { importer.launch(arrayOf("*/*")) }, enabled = !busy)
            SecondaryButton(
                "导出余额历史 CSV",
                onClick = {
                    val ts = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
                    csvExporter.launch("Yuji_$ts.csv")
                },
                enabled = !busy,
            )
        }
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { preview = null },
            containerColor = LocalYujiColors.current.CardHigh,
            title = { Text("恢复这个备份？") },
            text = {
                Column {
                    Text("备份时间：${p.createdAt}\n版本：v${p.version}\n${p.accounts} 个账户 · ${p.snapshots} 条快照")
                    Spacer(Modifier.height(10.dp))
                    if (!p.checksumOk) {
                        Text("⚠️ 校验值不一致，文件可能已损坏或被修改。", color = LocalYujiColors.current.Amber)
                        Spacer(Modifier.height(6.dp))
                    }
                    Text("当前所有数据会被备份内容替换，此操作不能撤销。", color = LocalYujiColors.current.Coral)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    busy = true
                    scope.launch {
                        val r = runCatching { c.backup.apply(p) }
                        busy = false
                        snackbar.showSnackbar(if (r.isSuccess) "已恢复备份" else "恢复失败：${r.exceptionOrNull()?.message}，数据未改动")
                    }
                }) { Text("恢复", color = LocalYujiColors.current.Coral) }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("取消", color = LocalYujiColors.current.TextMuted) } },
        )
    }
}

@Composable
fun IconCacheScreen(nav: NavController) {
    val c = LocalContainer.current
    val portfolio by c.repository.portfolio.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val referenced = portfolio.accounts.filter { it.account.iconType == IconType.IMAGE }.map { it.account.iconValue }.toSet()
    val unused = remember(referenced, refresh) { c.icons.unused(referenced) }
    val size = remember(refresh, referenced) { c.icons.totalSize() }

    Scaffold(containerColor = Color.Transparent, topBar = { YujiTopBar("图标缓存", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            YujiCard {
                Text("占用空间", style = MaterialTheme.typography.labelMedium, color = LocalYujiColors.current.TextMuted)
                Text(bytes(size), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${referenced.size} 个正在使用 · ${unused.size} 个未使用（${bytes(unused.sumOf { it.length() })}）",
                    style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.TextMuted,
                )
            }
            PrimaryButton("清理未使用的图标", enabled = unused.isNotEmpty(), onClick = {
                scope.launch { unused.forEach { it.delete() }; refresh++ }
            })
        }
    }
}

private fun bytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "%.2f MB".format(n / 1024.0 / 1024.0)
}

@Composable
fun AboutScreen(nav: NavController) {
    Scaffold(containerColor = Color.Transparent, topBar = { YujiTopBar("关于余记", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            YujiCard {
                Text("余记", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "不记录每一笔消费，只记录自己拥有的资产。隔一段时间更新一次账户余额，就能看清净资产和它的变化。",
                    style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted,
                )
            }
            YujiCard {
                Text("隐私", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• 所有数据只保存在本机，不上传任何服务器，也不参与系统云备份。\n" +
                        "• 联网只用于获取汇率和搜索图标，请求中不包含你的资产数据。\n" +
                        "• 备份文件由你自己保存，未加密，请放在安全的位置。",
                    style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted,
                )
            }
        }
    }
}
