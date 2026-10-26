package com.terminalide.ui

import com.terminalide.ui.theme.AppBackgroundBrush
import androidx.compose.foundation.border
import com.terminalide.ui.components.DiffDialog
import com.terminalide.core.ShareHelper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.Share
import com.terminalide.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terminalide.core.Language
import com.terminalide.ui.components.AiSidebar
import com.terminalide.ui.components.CodeEditor
import com.terminalide.ui.components.ConsoleInputBar
import com.terminalide.ui.components.FileTreeDrawer
import com.terminalide.ui.components.OutputPanel
import com.terminalide.ui.components.PackageManagerPanel
import com.terminalide.ui.components.ReplSheet
import com.terminalide.ui.components.SettingsPanel
import com.terminalide.ui.theme.TopBarGradient
import kotlinx.coroutines.launch

/**
 * 主界面：左滑抽屉（文件树）+ 渐变顶栏（语言 Tab）+ 底部操作栏 +
 * 编辑器/输出面板 + 右侧 AI 侧边栏 + 包管理/设置底部弹层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    editorVm: EditorViewModel,
    packageVm: PackageViewModel,
    aiVm: AiViewModel,
    replVm: ReplViewModel,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showAi by remember { mutableStateOf(false) }
    var showPackages by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showRepl by remember { mutableStateOf(false) }
    var pendingFix by remember { mutableStateOf<String?>(null) }

    val language by editorVm.language.collectAsStateWithLifecycle()
    val code by editorVm.code.collectAsStateWithLifecycle()
    val output by editorVm.output.collectAsStateWithLifecycle()
    val stdin by editorVm.stdin.collectAsStateWithLifecycle()
    val isRunning by editorVm.isRunning.collectAsStateWithLifecycle()
    val hasError by editorVm.hasError.collectAsStateWithLifecycle()
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
                    onDelete = editorVm::deleteFile,
                    onRename = editorVm::renameFile,
                )
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppBackgroundBrush)
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        // 渐变顶栏（底部圆角）
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                                .background(Brush.horizontalGradient(TopBarGradient))
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.22f),
                                            Color.White.copy(alpha = 0.02f),
                                        )
                                    ),
                                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                                )
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
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "Terminal IDE",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "@惜白 出品",
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                                        fontSize = 10.sp,
                                    )
                                }
                                val context = LocalContext.current
                                var showShareMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showShareMenu = true }) {
                                        Icon(
                                            Icons.Filled.Share,
                                            contentDescription = stringResource(R.string.action_share),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showShareMenu,
                                        onDismissRequest = { showShareMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_share_file)) },
                                            onClick = {
                                                showShareMenu = false
                                                ShareHelper.share(context, ShareHelper.fileNameFor(language), code)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_copy_code)) },
                                            onClick = {
                                                showShareMenu = false
                                                ShareHelper.copy(context, code)
                                            },
                                        )
                                    }
                                }
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
                                        contentDescription = stringResource(R.string.ai_title),
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
                    // 底部：交互输入条（运行时，Pydroid 风格）+ 导航栏
                    Column {
                        if (isRunning) {
                            ConsoleInputBar(onSend = editorVm::sendStdin)
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                        NavigationBar(
                            containerColor = Color(0xFF101827).copy(alpha = 0.96f),
                            tonalElevation = 0.dp,
                        ) {
                        NavigationBarItem(
                            selected = false,
                            onClick = { if (isRunning) editorVm.stop() else editorVm.run() },
                            icon = {
                                Icon(
                                    if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(if (isRunning) R.string.action_stop else R.string.action_run),
                                )
                            },
                            label = { Text(stringResource(if (isRunning) R.string.action_stop else R.string.action_run)) },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = editorVm::save,
                            icon = { Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.action_save)) },
                            label = { Text(stringResource(R.string.action_save)) },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { showPackages = true },
                            icon = { Icon(Icons.Filled.Inventory2, contentDescription = stringResource(R.string.action_packages)) },
                            label = { Text(stringResource(R.string.action_packages)) },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { showRepl = true },
                            icon = { Icon(Icons.Filled.Code, contentDescription = stringResource(R.string.action_repl)) },
                            label = { Text(stringResource(R.string.action_repl)) },
                        )
                        }
                    }
                },
            ) { innerPadding ->
                // 编辑器 / 控制台 的可调分割比例（拖动中间分隔条调整）
                var contentHeight by remember { mutableStateOf(1f) }
                var consoleFraction by remember { mutableStateOf(0.30f) }

                Column(
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .onSizeChanged { contentHeight = it.height.toFloat().coerceAtLeast(1f) }
                ) {
                    CodeEditor(
                        code = code,
                        language = language,
                        onCodeChange = editorVm::updateCode,
                        modifier = Modifier
                            .weight((1f - consoleFraction).coerceAtLeast(0.2f))
                            .fillMaxWidth(),
                    )
                    // 可拖动分隔条（向上拖 = 控制台变大；向下拖 = 编辑器变大）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    consoleFraction = (consoleFraction - dragAmount / contentHeight)
                                        .coerceIn(0.12f, 0.78f)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(52.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outline),
                        )
                    }
                    OutputPanel(
                        output = output,
                        isRunning = isRunning,
                        stdin = stdin,
                        onStdinChange = editorVm::updateStdin,
                        onStop = editorVm::stop,
                        onClear = editorVm::clearOutput,
                        hasError = hasError,
                        onAnalyzeError = {
                            showAi = true
                            aiVm.analyzeError(language, code, output)
                        },
                        modifier = Modifier.weight(consoleFraction).fillMaxWidth(),
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
                                onApplyAndRun = { newCode -> pendingFix = newCode },
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

    if (showRepl) {
        ModalBottomSheet(
            onDismissRequest = { showRepl = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ReplSheet(replVm)
        }
    }

    // AI 修复前先看 diff，确认后再替换并重跑
    val fix = pendingFix
    if (fix != null) {
        DiffDialog(
            oldCode = code,
            newCode = fix,
            onApply = {
                editorVm.updateCode(fix)
                editorVm.run()
                pendingFix = null
            },
            onDismiss = { pendingFix = null },
        )
    }
}
