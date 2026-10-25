package com.terminalide.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terminalide.core.Language
import com.terminalide.ui.components.AiSidebar
import com.terminalide.ui.components.CodeEditor
import com.terminalide.ui.components.FileTreeDrawer
import com.terminalide.ui.components.OutputPanel
import com.terminalide.ui.components.PackageManagerPanel
import com.terminalide.ui.components.SettingsPanel
import com.terminalide.ui.theme.TopBarGradient
import kotlinx.coroutines.launch

/**
 * 主界面：左滑抽屉（文件树）+ 渐变顶栏（语言 Tab）+ 底部操作栏 +
 * 编辑器/输出面板 + 右侧 AI 侧边栏 + 包管理/设置底部弹层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(editorVm: EditorViewModel, packageVm: PackageViewModel, aiVm: AiViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showAi by remember { mutableStateOf(false) }
    var showPackages by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val language by editorVm.language.collectAsStateWithLifecycle()
    val code by editorVm.code.collectAsStateWithLifecycle()
    val output by editorVm.output.collectAsStateWithLifecycle()
    val isRunning by editorVm.isRunning.collectAsStateWithLifecycle()
    val files by editorVm.files.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                FileTreeDrawer(
                    files = files,
                    onSelect = { path ->
                        editorVm.loadFile(path)
                        scope.launch { drawerState.close() }
                    },
                    onRefresh = editorVm::refreshFiles,
                    onNewFile = editorVm::newFile,
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        // 渐变顶栏（底部圆角）
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                                .background(Brush.horizontalGradient(TopBarGradient))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        Icons.Filled.Menu,
                                        contentDescription = "菜单",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                                Text(
                                    "Terminal IDE",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = "设置",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                                IconButton(onClick = { showAi = true }) {
                                    Icon(
                                        Icons.Filled.SmartToy,
                                        contentDescription = "AI 助手",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                        // 胶囊式语言切换
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Language.entries.forEach { lang ->
                                val selected = language == lang
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                        )
                                        .clickable { editorVm.switchLanguage(lang) }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        lang.displayName,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                    ) {
                        NavigationBarItem(
                            selected = false,
                            onClick = { if (isRunning) editorVm.stop() else editorVm.run() },
                            icon = {
                                Icon(
                                    if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = if (isRunning) "停止" else "运行",
                                )
                            },
                            label = { Text(if (isRunning) "停止" else "运行") },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = editorVm::save,
                            icon = { Icon(Icons.Filled.Save, contentDescription = "保存") },
                            label = { Text("保存") },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { showPackages = true },
                            icon = { Icon(Icons.Filled.Inventory2, contentDescription = "包管理") },
                            label = { Text("包管理") },
                        )
                    }
                },
            ) { innerPadding ->
                Column(Modifier.padding(innerPadding).fillMaxSize()) {
                    CodeEditor(
                        code = code,
                        language = language,
                        onCodeChange = editorVm::updateCode,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    OutputPanel(
                        output = output,
                        isRunning = isRunning,
                        onStop = editorVm::stop,
                        onClear = editorVm::clearOutput,
                        modifier = Modifier.weight(0.4f).fillMaxWidth(),
                    )
                }
            }

            // 右侧 AI 覆盖层：scrim 淡入淡出 + 面板右滑滑入
            AnimatedVisibility(
                visible = showAi,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim)
                            .clickable { showAi = false },
                    )
                    AnimatedVisibility(
                        visible = showAi,
                        enter = slideInHorizontally(initialOffsetX = { it }),
                        exit = slideOutHorizontally(targetOffsetX = { it }),
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Box(Modifier.fillMaxHeight().width(320.dp)) {
                            AiSidebar(
                                aiVm = aiVm,
                                currentLanguage = language,
                                currentCode = code,
                                onInsertCode = { newCode -> editorVm.updateCode(newCode) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPackages) {
        ModalBottomSheet(
            onDismissRequest = { showPackages = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            PackageManagerPanel(packageVm)
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            SettingsPanel(packageVm = packageVm, aiVm = aiVm)
        }
    }
}
