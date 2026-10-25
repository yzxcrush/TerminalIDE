package com.terminalide.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terminalide.packages.MirrorSource
import com.terminalide.ui.PackageViewModel

/**
 * 包管理面板：镜像源切换 + 搜索/安装/卸载/已装列表/导出 requirements。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageManagerPanel(packageVm: PackageViewModel) {
    val mirror by packageVm.mirror.collectAsStateWithLifecycle()
    val packages by packageVm.packages.collectAsStateWithLifecycle()
    val isWorking by packageVm.isWorking.collectAsStateWithLifecycle()
    val status by packageVm.status.collectAsStateWithLifecycle()
    val searchResult by packageVm.searchResult.collectAsStateWithLifecycle()
    val requirements by packageVm.requirements.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var installName by remember { mutableStateOf("") }

    LazyColumn(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                "包管理",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item { Spacer(Modifier.height(12.dp)) }

        item {
            Text(
                "镜像源",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Column {
                MirrorSource.entries.forEach { src ->
                    FilterChip(
                        selected = mirror == src,
                        onClick = { packageVm.setMirror(src) },
                        label = {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(src.displayName)
                                Text(
                                    src.indexUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    )
                }
            }
        }
        item {
            Text(
                "切换后运行期 pip 自动带 -i 参数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Text(
                "搜索包",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("包名") },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { packageVm.search(searchQuery.trim()) }) { Text("搜索") }
            }
        }
        searchResult?.let { pkg ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(pkg.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            pkg.version,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            pkg.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { packageVm.install(pkg.name) }) { Text("安装") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Text(
                "安装包",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = installName,
                    onValueChange = { installName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("包名") },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { packageVm.install(installName.trim()) }) { Text("安装") }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Text(
                "已安装",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (packages.isEmpty()) {
            item {
                Text(
                    "暂无已安装包",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(packages, key = { it.name }) { pkg ->
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pkg.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                pkg.version,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { packageVm.uninstall(pkg.name) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "卸载",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Button(
                onClick = packageVm::exportRequirements,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("导出 requirements.txt")
            }
        }
        if (requirements.isNotEmpty()) {
            item {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        requirements,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (isWorking) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "处理中...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (status.isNotEmpty()) {
            item {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
