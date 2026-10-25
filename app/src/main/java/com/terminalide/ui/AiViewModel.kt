package com.terminalide.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminalide.TerminalIdeApp
import com.terminalide.ai.AiBackendKind
import com.terminalide.ai.AiMessage
import com.terminalide.ai.ProbeResult
import com.terminalide.core.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AI 侧边栏 UI 状态：后端切换、对话、Key 验证/加密存储、模型下载。
 */
class AiViewModel : ViewModel() {

    private val ai get() = TerminalIdeApp.instance.aiService

    private val _kind = MutableStateFlow(ai.currentKind())
    val kind: StateFlow<AiBackendKind> = _kind.asStateFlow()

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _apiKeyInput = MutableStateFlow("")
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _hasApiKey = MutableStateFlow(ai.hasApiKey())
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _probeResult = MutableStateFlow<ProbeResult?>(null)
    val probeResult: StateFlow<ProbeResult?> = _probeResult.asStateFlow()

    private val _modelUrl = MutableStateFlow("")
    val modelUrl: StateFlow<String> = _modelUrl.asStateFlow()

    private val _downloadProgress = MutableStateFlow("")
    val downloadProgress: StateFlow<String> = _downloadProgress.asStateFlow()

    private val _modelPath = MutableStateFlow(ai.modelPath())
    val modelPath: StateFlow<String> = _modelPath.asStateFlow()

    init {
        refreshReady()
    }

    // ---- 后端切换 ----

    fun setKind(kind: AiBackendKind) {
        ai.setKind(kind)
        _kind.value = kind
        refreshReady()
    }

    fun refreshReady() {
        viewModelScope.launch {
            _ready.value = ai.isReady()
            _hasApiKey.value = ai.hasApiKey()
            _modelPath.value = ai.modelPath()
        }
    }

    // ---- 对话 ----

    fun updateInput(text: String) {
        _input.value = text
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun send(currentLanguage: Language, currentCode: String) {
        val text = _input.value.trim()
        if (text.isEmpty() || _isThinking.value) return
        _input.value = ""
        val history = _messages.value
        _messages.value = history + AiMessage(AiMessage.Role.USER, text)
        _isThinking.value = true
        viewModelScope.launch {
            try {
                val reply = ai.chat(text, history, currentLanguage, currentCode)
                _messages.value = _messages.value + AiMessage(AiMessage.Role.ASSISTANT, reply)
            } catch (t: Throwable) {
                _messages.value = _messages.value +
                    AiMessage(AiMessage.Role.ASSISTANT, "⚠️ ${t.message ?: t.toString()}")
            } finally {
                _isThinking.value = false
            }
        }
    }

    /** 从最近一条助手回复里提取代码块，供一键插入编辑器。 */
    fun lastCodeBlock(): String? {
        val assistant = _messages.value.lastOrNull { it.role == AiMessage.Role.ASSISTANT } ?: return null
        return ai.extractCodeBlock(assistant.content)
    }

    // ---- DeepSeek API Key ----

    fun updateApiKeyInput(text: String) {
        _apiKeyInput.value = text
    }

    fun saveApiKeyAndProbe() {
        val key = _apiKeyInput.value.trim()
        if (key.isEmpty()) return
        _probeResult.value = null
        _status.value = "正在探测验证 API Key ..."
        viewModelScope.launch {
            val r = ai.probeDeepSeek(key)
            _probeResult.value = r
            if (r.ok) {
                ai.saveApiKey(key)
                _apiKeyInput.value = ""
                _status.value = "✅ ${r.message}，已加密存储"
            } else {
                _status.value = "❌ ${r.message}"
            }
            refreshReady()
        }
    }

    fun clearApiKey() {
        ai.clearApiKey()
        _status.value = "已清除 API Key"
        refreshReady()
    }

    // ---- 端侧模型下载 ----

    fun updateModelUrl(text: String) {
        _modelUrl.value = text
    }

    fun downloadModel() {
        val url = _modelUrl.value.trim()
        if (url.isEmpty()) return
        viewModelScope.launch {
            _downloadProgress.value = "开始下载..."
            try {
                val path = ai.downloadModel(url) { read, total ->
                    _downloadProgress.value =
                        if (total > 0) {
                            "下载中 ${read * 100 / total}%"
                        } else {
                            "下载中 ${read / (1024 * 1024)} MB"
                        }
                }
                _status.value = "模型已保存：$path"
            } catch (t: Throwable) {
                _status.value = "下载失败：${t.message ?: t.toString()}"
            } finally {
                _downloadProgress.value = ""
                refreshReady()
            }
        }
    }
}
