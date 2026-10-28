package com.terminalide

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.terminalide.ui.AiViewModel
import com.terminalide.ui.EditorViewModel
import com.terminalide.ui.MainScreen
import com.terminalide.ui.PackageViewModel
import com.terminalide.ui.AutomationViewModel
import com.terminalide.ui.CaptureViewModel
import com.terminalide.ui.ToolsViewModel
import com.terminalide.ui.ReplViewModel
import com.terminalide.ui.TerminalViewModel
import com.terminalide.ui.theme.TerminalIdeTheme

class MainActivity : ComponentActivity() {

    /** 从「打开方式」收到、等待载入编辑器的文件内容（文本, 文件名）。 */
    private val pendingOpen = mutableStateOf<Pair<String, String?>?>(null)

    /** Android 13+ 通知权限请求。 */
    private val notifPermLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }

    /** 应用用户选择的 App 语言（system 时跟随系统）。 */
    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = com.terminalide.core.SettingsStore(newBase).appLanguage
        if (lang != "system") {
            val locale = java.util.Locale.forLanguageTag(lang)
            java.util.Locale.setDefault(locale)
            val config = android.content.res.Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 一次性的通知权限请求（运行完成提醒用）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !com.terminalide.core.NotificationHelper.hasPermission(this)
        ) {
            runCatching { notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        }
        handleOpenIntent(intent)
        setContent {
            TerminalIdeTheme {
                val startupError = TerminalIdeApp.startupError
                if (startupError != null) {
                    // 启动阶段（Application.onCreate）出错：直接显示错误，而不是闪退
                    ErrorText("启动失败：\n$startupError")
                } else {
                    val editorVm: EditorViewModel = viewModel()
                    val packageVm: PackageViewModel = viewModel()
                    val aiVm: AiViewModel = viewModel()
                    val replVm: ReplViewModel = viewModel()
                    val termVm: TerminalViewModel = viewModel()
                    val captureVm: CaptureViewModel = viewModel()
                    val toolsVm: ToolsViewModel = viewModel()
                    val automationVm: AutomationViewModel = viewModel()

                    val pending by pendingOpen
                    LaunchedEffect(pending) {
                        val p = pending ?: return@LaunchedEffect
                        editorVm.openExternal(p.first, p.second)
                        pendingOpen.value = null
                    }

                    MainScreen(
                        editorVm = editorVm,
                        packageVm = packageVm,
                        aiVm = aiVm,
                        replVm = replVm,
                        termVm = termVm,
                        captureVm = captureVm,
                        toolsVm = toolsVm,
                        automationVm = automationVm,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    /** 读取「打开方式」传入的文件（content:// 或 file://），交给编辑器载入。 */
    private fun handleOpenIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        try {
            val text = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
            if (text != null) {
                val name = uri.lastPathSegment ?: uri.path?.substringAfterLast('/')
                pendingOpen.value = text to name
            }
        } catch (t: Throwable) {
            // 权限不足或文件无法读取时忽略，不影响正常启动
        }
    }

    @Composable
    private fun ErrorText(message: String) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = message,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
    }
}
