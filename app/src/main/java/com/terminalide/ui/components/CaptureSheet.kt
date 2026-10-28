package com.terminalide.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terminalide.R
import com.terminalide.capture.CaptureEntry
import com.terminalide.ui.CaptureViewModel

/** 单条抓包记录。 */
@Composable
private fun CaptureRow(vm: CaptureViewModel, rawEntry: CaptureEntry, onClick: (CaptureEntry) -> Unit) {
    val entry = vm.maskEntry(rawEntry)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onClick(entry) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            "${entry.method} ${entry.url}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            if (entry.error != null) "❌ ${entry.error}"
            else "${entry.statusCode} · ${entry.responseBody.length} 字节",
            style = MaterialTheme.typography.labelSmall,
            color = if (entry.error != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 抓包面板：启停、CA/代理设置、请求列表、详情、AI 解析与重放。 */
@Composable
fun CaptureSheet(vm: CaptureViewModel, onInsertCode: (String) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val running by vm.running.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val analyzing by vm.analyzing.collectAsStateWithLifecycle()
    val aiResult by vm.aiResult.collectAsStateWithLifecycle()
    val replayResult by vm.replayResult.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.onSheetOpened() }

    var showFilter by remember { mutableStateOf(false) }
    var showAppList by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var grouped by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<CaptureEntry?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.capture_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            Button(onClick = { if (running) vm.stop() else vm.start() }) {
                Text(stringResource(if (running) R.string.action_stop else R.string.capture_start))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { vm.clear() }) {
                Text(stringResource(R.string.action_clear))
            }
        }
        Text(
            status,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 工具按钮
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        ) {
            TextButton(onClick = { vm.toggleHidden() }) {
                Text(
                    stringResource(if (hidden) R.string.capture_hide_on else R.string.capture_hide),
                    fontSize = 11.sp,
                    color = if (hidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { vm.clearProxyAndStop { message = it } }) {
                Text(
                    stringResource(R.string.capture_clear_proxy_now),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = { showFilter = true }) { Text(stringResource(R.string.capture_filter), fontSize = 11.sp) }
            TextButton(onClick = {
                if (!com.terminalide.capture.CaptureBubble.canOverlay(context)) {
                    com.terminalide.capture.CaptureBubble.requestPermission(context)
                    message = context.getString(R.string.capture_overlay_need)
                } else {
                    message = "✅ 悬浮窗权限已开启\n开始抓包后会显示悬浮胶囊"
                }
            }) { Text(stringResource(R.string.capture_overlay), fontSize = 11.sp) }
            TextButton(onClick = { vm.generateApiClient() }) {
                Text(
                    if (analyzing) "生成中…" else stringResource(R.string.capture_gen_client),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = { showAppList = true }) { Text(stringResource(R.string.capture_pick_apps), fontSize = 11.sp) }
            TextButton(onClick = { vm.exportCa { message = it } }) { Text(stringResource(R.string.capture_export_ca), fontSize = 11.sp) }
            TextButton(onClick = { vm.installCaToSystem { message = it } }) { Text(stringResource(R.string.capture_install_ca), fontSize = 11.sp) }
            TextButton(onClick = { vm.setSystemProxy { message = it } }) { Text(stringResource(R.string.capture_set_proxy), fontSize = 11.sp) }
            TextButton(onClick = { vm.clearSystemProxy { message = it } }) { Text(stringResource(R.string.capture_clear_proxy), fontSize = 11.sp) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // 搜索 / 分组
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.capture_search_hint), fontSize = 12.sp) },
                textStyle = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { grouped = !grouped }) {
                Text(
                    stringResource(R.string.capture_group),
                    fontSize = 11.sp,
                    color = if (grouped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val filtered = if (query.isBlank()) items else items.filter {
            it.url.contains(query, true) || it.host.contains(query, true) || it.method.contains(query, true)
        }

        LazyColumn(Modifier.weight(1f)) {
            if (grouped) {
                val groups = filtered.groupBy { it.host }
                groups.forEach { (host, list) ->
                    item(key = "g_$host") {
                        Text(
                            "${host}（${list.size}）",
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(list, key = { it.id }) { rawEntry ->
                        CaptureRow(vm, rawEntry) { detail = it }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { rawEntry ->
                    CaptureRow(vm, rawEntry) { detail = it }
                }
            }
        }
    }

    // 详情
    val current = detail
    if (current != null) {
        val shown = vm.maskEntry(current)
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("${shown.method} ${shown.host}") },
            text = {
                Column(Modifier.height(420.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            shown.url + "\n\n【请求头】\n" + shown.requestHeaders +
                                "\n\n【请求体】\n" + shown.requestBody +
                                "\n\n【响应 ${shown.statusCode}】\n" + shown.responseHeaders +
                                "\n\n【响应体】\n" + shown.responseBody,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.analyze(current); detail = null }) {
                    Text(if (analyzing) "AI 解析中…" else stringResource(R.string.capture_ai_analyze))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.openDraft(current); detail = null }) {
                        Text(stringResource(R.string.capture_edit_replay))
                    }
                    TextButton(onClick = { vm.draftWithAi(current); detail = null }) {
                        Text(stringResource(R.string.capture_ai_draft))
                    }
                }
            },
        )
    }

    // 重放编辑器
    val d = vm.draft.collectAsStateWithLifecycle().value
    if (d != null) {
        var method by remember(d) { mutableStateOf(d.method) }
        var url by remember(d) { mutableStateOf(d.url) }
        var headers by remember(d) { mutableStateOf(d.headers) }
        var body by remember(d) { mutableStateOf(d.body) }
        AlertDialog(
            onDismissRequest = { vm.closeDraft() },
            title = { Text(stringResource(R.string.capture_edit_replay)) },
            text = {
                Column(Modifier.height(440.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(method, { method = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Method", fontSize = 11.sp) })
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("URL", fontSize = 11.sp) })
                    OutlinedTextField(headers, { headers = it }, Modifier.fillMaxWidth(), minLines = 4,
                        label = { Text("Headers", fontSize = 11.sp) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), minLines = 3,
                        label = { Text("Body", fontSize = 11.sp) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.sendDraft(com.terminalide.ui.CaptureViewModel.ReplayDraft(method, url, headers, body))
                    vm.closeDraft()
                }) { Text(stringResource(R.string.capture_send)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.closeDraft() }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // AI 生成的 API 客户端
    val gen = vm.generatedClient.collectAsStateWithLifecycle().value
    if (gen != null) {
        val clipboard2 = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { vm.closeGeneratedClient() },
            title = { Text(stringResource(R.string.capture_gen_done)) },
            text = {
                Column(Modifier.height(440.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(gen, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onInsertCode(gen)
                    vm.closeGeneratedClient()
                }) { Text(stringResource(R.string.capture_insert_editor)) }
            },
            dismissButton = {
                TextButton(onClick = { clipboard2.setText(AnnotatedString(gen)); vm.closeGeneratedClient() }) {
                    Text("复制")
                }
            },
        )
    }

    // AI 解析结果
    val ai = aiResult
    if (ai != null) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { vm.clearAiResult() },
            title = { Text(stringResource(R.string.capture_ai_analyze)) },
            text = {
                Column(Modifier.height(420.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(ai, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(ai)); vm.clearAiResult() }) {
                    Text("复制")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearAiResult() }) { Text("关闭") }
            },
        )
    }

    // 重放结果
    val rp = replayResult
    if (rp != null) {
        AlertDialog(
            onDismissRequest = { vm.clearReplayResult() },
            title = { Text(stringResource(R.string.capture_replay)) },
            text = {
                Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(rp, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.clearReplayResult() }) { Text("关闭") }
            },
            dismissButton = {},
        )
    }

    // 应用列表加入黑名单
    if (showAppList) {
        val black by vm.blacklist.collectAsStateWithLifecycle()
        AppBlacklistDialog(
            blacklist = com.terminalide.capture.CaptureFilter.parse(black),
            onSave = { list ->
                vm.saveFilter(com.terminalide.capture.CaptureFilter.format(list), vm.whitelist.value)
                message = "✅ 已更新黑名单（${list.size} 个域名）"
            },
            onDismiss = { showAppList = false },
        )
    }

    // 过滤规则
    if (showFilter) {
        val black by vm.blacklist.collectAsStateWithLifecycle()
        val white by vm.whitelist.collectAsStateWithLifecycle()
        var blackEdit by remember(black) { mutableStateOf(black) }
        var whiteEdit by remember(white) { mutableStateOf(white) }
        AlertDialog(
            onDismissRequest = { showFilter = false },
            title = { Text(stringResource(R.string.capture_filter_title)) },
            text = {
                Column(Modifier.height(420.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.capture_blacklist), style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = blackEdit,
                        onValueChange = { blackEdit = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    TextButton(onClick = {
                        val preset = com.terminalide.capture.CaptureFilter.format(
                            com.terminalide.capture.CaptureFilter.SENSITIVE_PRESET
                        )
                        blackEdit = if (blackEdit.isBlank()) preset else blackEdit.trimEnd() + "\n" + preset
                    }) { Text(stringResource(R.string.capture_filter_preset), fontSize = 11.sp) }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.capture_whitelist), style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = whiteEdit,
                        onValueChange = { whiteEdit = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    Text(
                        stringResource(R.string.capture_filter_tip),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveFilter(blackEdit, whiteEdit)
                    showFilter = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showFilter = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // 提示
    val msg = message
    if (msg != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(stringResource(R.string.capture_title)) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } },
            dismissButton = {},
        )
    }
}
