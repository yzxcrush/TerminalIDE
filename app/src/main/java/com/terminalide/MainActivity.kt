package com.terminalide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent {
            TerminalIdeTheme {
                val editorVm: EditorViewModel = viewModel()
                val packageVm: PackageViewModel = viewModel()
                val aiVm: AiViewModel = viewModel()
                MainScreen(
                    editorVm = editorVm,
                    packageVm = packageVm,
                    aiVm = aiVm,
                )
            }
        }
    }
}
