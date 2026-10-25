package com.terminalide.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminalide.TerminalIdeApp
import com.terminalide.packages.InstalledPackage
import com.terminalide.packages.MirrorSource
import com.terminalide.packages.PackageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 包管理面板 UI 状态：镜像源切换 + 搜索/安装/卸载/已装列表/导出。
 */
class PackageViewModel : ViewModel() {

    private val app get() = TerminalIdeApp.instance
    private val pm get() = app.packageManager

    private val _mirror = MutableStateFlow(pm.globalMirror())
    val mirror: StateFlow<MirrorSource> = _mirror.asStateFlow()

    private val _packages = MutableStateFlow<List<InstalledPackage>>(emptyList())
    val packages: StateFlow<List<InstalledPackage>> = _packages.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _searchResult = MutableStateFlow<PackageInfo?>(null)
    val searchResult: StateFlow<PackageInfo?> = _searchResult.asStateFlow()

    private val _requirements = MutableStateFlow("")
    val requirements: StateFlow<String> = _requirements.asStateFlow()

    init {
        refreshPackages()
    }

    fun setMirror(source: MirrorSource) {
        pm.setGlobalMirror(source)
        _mirror.value = source
        _status.value = "已切换到 ${source.displayName}"
    }

    fun refreshPackages() {
        viewModelScope.launch {
            _packages.value = pm.installed()
        }
    }

    fun search(name: String) {
        viewModelScope.launch {
            _isWorking.value = true
            _searchResult.value = pm.search(name)
            _isWorking.value = false
        }
    }

    fun clearSearch() {
        _searchResult.value = null
    }

    /** 安装包；mirror 为 null 用全局默认源。 */
    fun install(name: String, mirror: MirrorSource? = null) {
        viewModelScope.launch {
            _isWorking.value = true
            val src = mirror ?: _mirror.value
            _status.value = "正在安装 $name（${src.displayName}）..."
            val r = pm.install(name, mirror)
            _status.value = if (r.isSuccess) "✅ 安装成功：$name" else "❌ 安装失败：\n${r.message()}"
            _isWorking.value = false
            refreshPackages()
        }
    }

    fun uninstall(name: String) {
        viewModelScope.launch {
            _isWorking.value = true
            _status.value = "正在卸载 $name ..."
            val r = pm.uninstall(name)
            _status.value = if (r.isSuccess) "✅ 已卸载：$name" else "❌ 卸载失败：\n${r.message()}"
            _isWorking.value = false
            refreshPackages()
        }
    }

    fun exportRequirements() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { pm.exportRequirements() }
            withContext(Dispatchers.IO) {
                File(app.sandbox.workspace, "requirements.txt").writeText(text)
            }
            _requirements.value = text
            _status.value = "已导出到 workspace/requirements.txt"
        }
    }
}
