package com.terminalide.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalide.R
import com.terminalide.capture.AppDomainMap

/**
 * 从已安装应用列表中选择：勾选 = 把该 App 的域名加入黑名单（不抓取），取消 = 移出。
 */
@Composable
fun AppBlacklistDialog(
    blacklist: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    // 只列出本机已安装、且有域名数据的 App
    val apps = remember {
        val pm = ctx.packageManager
        AppDomainMap.knownPackages()
            .mapNotNull { pkg ->
                runCatching {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pkg to pm.getApplicationLabel(ai).toString()
                }.getOrNull()
            }
            .sortedBy { it.second }
    }

    // 勾选状态：该 App 的所有域名都在黑名单里 = 已勾选
    val selected = remember {
        apps.filter { (_, _) -> true }
            .filter { (pkg, _) -> AppDomainMap.domainsOf(pkg).all { it in blacklist } }
            .map { it.first }
            .toMutableList()
    }

    var working by remember { mutableStateOf(selected.toList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.capture_pick_apps)) },
        text = {
            if (apps.isEmpty()) {
                Text(stringResource(R.string.capture_no_apps))
            } else {
                LazyColumn(Modifier.height(420.dp)) {
                    items(apps, key = { it.first }) { (pkg, label) ->
                        val checked = pkg in working
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    working = if (on) working + pkg else working - pkg
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(label, fontSize = 14.sp)
                                Text(
                                    AppDomainMap.domainsOf(pkg).take(3).joinToString(", "),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 重新计算黑名单：先移除所有已知 App 的域名，再把勾选的加回去
                val known = AppDomainMap.MAP.values.flatten().toSet()
                val result = blacklist.filterNot { it in known }.toMutableList()
                working.forEach { pkg ->
                    AppDomainMap.domainsOf(pkg).forEach { if (it !in result) result.add(it) }
                }
                onSave(result)
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
