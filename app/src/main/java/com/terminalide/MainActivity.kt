package com.terminalide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.terminalide.ui.AiViewModel
import com.terminalide.ui.EditorViewModel
import com.terminalide.ui.MainScreen
import com.terminalide.ui.PackageViewModel
import com.terminalide.ui.theme.TerminalIdeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startupError = TerminalIdeApp.startupError
        setContent {
            TerminalIdeTheme {
                when {
                    startupError != null -> ErrorText("启动失败：\n$startupError")
                    else -> {
                        var uiError by remember { mutableStateOf<String?>(null) }
                        if (uiError != null) {
                            ErrorText("界面崩溃：\n$uiError")
                        } else {
                            try {
                                val editorVm: EditorViewModel = viewModel()
                                val packageVm: PackageViewModel = viewModel()
                                val aiVm: AiViewModel = viewModel()
                                MainScreen(
                                    editorVm = editorVm,
                                    packageVm = packageVm,
                                    aiVm = aiVm,
                                )
                            } catch (t: Throwable) {
                                uiError = t.toString() + "\n\n" + t.stackTraceToString().take(3000)
                            }
                        }
                    }
                }
            }
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
