package com.yuji.app.ui.account

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import com.yuji.app.ui.components.PrimaryButton
import com.yuji.app.ui.components.SecondaryButton
import com.yuji.app.ui.components.inputWell
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.yuji.app.data.db.IconType
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.theme.LocalYujiColors
import kotlinx.coroutines.launch

private val EMOJIS = listOf(
    "💳", "🏦", "💰", "💵", "💴", "💶", "💷", "🪙", "💎", "📈", "📊", "🏠",
    "🚗", "📱", "💼", "🐷", "🧧", "🎁", "🏧", "🧾", "🔐", "🌿", "⭐", "🔥",
    "₿", "🇨🇳", "🇺🇸", "🇪🇺", "🇬🇧", "🇯🇵", "🇭🇰", "🌏",
)

private val SUGGESTIONS = listOf("alipay", "wechat", "bank", "bitcoin", "ethereum", "binance", "paypal", "visa", "apple", "wallet")

/** Picks an account icon: emoji, Iconify search, gallery image, or none. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IconPickerSheet(onDismiss: () -> Unit, onPicked: (type: String, value: String) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun search(q: String) {
        if (q.isBlank()) return
        query = q
        searching = true
        error = null
        scope.launch {
            runCatching { c.icons.search(q.trim()) }
                .onSuccess { results = it; if (it.isEmpty()) error = "没有找到「$q」，试试英文品牌名" }
                .onFailure { error = "搜索失败，请检查网络"; results = emptyList() }
            searching = false
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            saving = true
            scope.launch {
                runCatching { c.icons.saveFromUri(uri) }
                    .onSuccess { onPicked(IconType.IMAGE, it.absolutePath); onDismiss() }
                    .onFailure { error = it.message ?: "图片读取失败" }
                saving = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LocalYujiColors.current.Card,
    ) {
        Column(Modifier.fillMaxWidth().height(520.dp)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = LocalYujiColors.current.Card) {
                listOf("表情", "搜索图标", "相册").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                0 -> Column(Modifier.padding(horizontal = 16.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(EMOJIS) { e ->
                            Box(
                                Modifier.aspectRatio(1f).inputWell()
                                    .clickable { onPicked(IconType.EMOJI, e); onDismiss() },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 24.sp) }
                        }
                    }
                    SecondaryButton(
                        "使用默认图标（名称首字）",
                        onClick = { onPicked(IconType.NONE, ""); onDismiss() },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                1 -> Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("英文名称，如 alipay、binance") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search(query) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    if (results.isEmpty() && !searching) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SUGGESTIONS.forEach { s ->
                                AssistChip(
                                    onClick = { search(s) }, label = { Text(s) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = LocalYujiColors.current.CardHigh),
                                )
                            }
                        }
                    }
                    error?.let { Text(it, color = LocalYujiColors.current.Amber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
                    if (searching || saving) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LocalYujiColors.current.AccentText) }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(results, key = { it }) { id ->
                            // Same dark plate the saved icon will sit on, so white glyphs stay visible.
                            Box(
                                Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.medium)
                                    .background(LocalYujiColors.current.InverseSurface)
                                    .clickable(enabled = !saving) {
                                        saving = true
                                        scope.launch {
                                            runCatching { c.icons.saveFromIconify(id) }
                                                .onSuccess { onPicked(IconType.IMAGE, it.absolutePath); onDismiss() }
                                                .onFailure { error = it.message ?: "图标下载失败" }
                                            saving = false
                                        }
                                    }
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(model = c.icons.previewUrl(id), contentDescription = id, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                    Text(
                        "图标来自 Iconify 开源图标库，仅供个人使用",
                        style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                else -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = LocalYujiColors.current.AccentText, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("从相册选择一张图片，会自动裁成正方形并保存在本机。", color = LocalYujiColors.current.TextMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(
                        if (saving) "处理中…" else "选择图片",
                        enabled = !saving,
                        onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        fillWidth = false,
                    )
                    error?.let { Text(it, color = LocalYujiColors.current.Coral, modifier = Modifier.padding(top = 12.dp)) }
                }
            }
        }
    }
}
