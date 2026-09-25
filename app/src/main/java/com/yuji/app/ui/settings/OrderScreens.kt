package com.yuji.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yuji.app.data.db.GroupEntity
import com.yuji.app.ui.account.NameDialog
import com.yuji.app.ui.components.ConfirmDialog
import com.yuji.app.ui.components.YujiTopBar
import com.yuji.app.ui.nav.LocalContainer
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.theme.YujiColors
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun GroupsScreen(nav: NavController) {
    val c = LocalContainer.current
    val groups by c.repository.groups.collectAsStateWithLifecycle()
    val portfolio by c.repository.portfolio.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var list by remember { mutableStateOf(groups) }
    var renaming by remember { mutableStateOf<GroupEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GroupEntity?>(null) }
    LaunchedEffect(groups) { list = groups }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        // Indices include the hint row, so move by key.
        val i = list.indexOfFirst { it.id == from.key }
        val j = list.indexOfFirst { it.id == to.key }
        if (i >= 0 && j >= 0) list = list.toMutableList().apply { add(j, removeAt(i)) }
    }

    Scaffold(
        containerColor = LocalYujiColors.current.Background,
        topBar = {
            YujiTopBar("分组管理", onBack = { nav.popBackStack() }) {
                IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, contentDescription = "新分组") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "hint") {
                Text("按住右侧把手拖动排序。有账户的分组需要先移走账户才能删除。", style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.TextMuted)
            }
            items(list, key = { it.id }) { g ->
                ReorderableItem(reorder, key = g.id) { dragging ->
                    val count = portfolio.accounts.count { it.account.groupId == g.id }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (dragging) LocalYujiColors.current.CardHigh else LocalYujiColors.current.Card,
                        modifier = Modifier.shadow(if (dragging) 8.dp else 0.dp, RoundedCornerShape(16.dp)),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(g.name, style = MaterialTheme.typography.bodyLarge)
                                Text("$count 个账户", style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextFaint)
                            }
                            IconButton(onClick = { renaming = g }) { Icon(Icons.Rounded.Edit, contentDescription = "重命名", tint = LocalYujiColors.current.TextMuted) }
                            IconButton(onClick = {
                                if (count > 0) scope.launch { snackbar.showSnackbar("「${g.name}」里还有 $count 个账户") } else deleting = g
                            }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除", tint = LocalYujiColors.current.TextMuted) }
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(onDragStopped = { scope.launch { c.repository.reorderGroups(list.map { it.id }) } }),
                            ) { Icon(Icons.Rounded.DragHandle, contentDescription = "拖动排序", tint = LocalYujiColors.current.TextFaint) }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { g ->
        NameDialog("重命名分组", g.name, onDismiss = { renaming = null }, onSave = { n -> scope.launch { c.repository.renameGroup(g.id, n) } })
    }
    if (adding) {
        NameDialog("新分组", "", onDismiss = { adding = false }, onSave = { n -> scope.launch { c.repository.addGroup(n) } })
    }
    deleting?.let { g ->
        ConfirmDialog(
            "删除分组「${g.name}」？", "分组内没有账户，可以安全删除。", "删除", destructive = true,
            onConfirm = { scope.launch { c.repository.deleteGroup(g.id) } },
            onDismiss = { deleting = null },
        )
    }
}
