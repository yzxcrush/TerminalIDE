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
        streamReply(text, _messages.value, currentLanguage, currentCode)
    }

    /** 从最近一条助手回复里提取代码块，供一键插入编辑器。 */
    fun lastCodeBlock(): String? {
        val assistant = _messages.value.lastOrNull { it.role == AiMessage.Role.ASSISTANT } ?: return null
        return ai.extractCodeBlock(assistant.content)
    }

    /** AI 快捷操作：用预设指令把当前编辑器代码发给模型（解释/修复/优化/注释/测试）。 */
    fun sendQuick(label: String, prompt: String, currentLanguage: Language, currentCode: String) {
        if (_isThinking.value) return
        streamReply(prompt, _messages.value, currentLanguage, currentCode, displayUser = "【$label】$prompt")
    }

    /** 分析运行报错：把代码 + 错误输出交给 AI，给出修复方案。 */
    fun analyzeError(currentLanguage: Language, currentCode: String, errorText: String) {
        if (_isThinking.value) return
        streamReply(
            "程序运行报错了，请分析原因并给出可直接运行的修复后完整代码。\n\n错误输出：\n```\n" +
                errorText.takeLast(4000) + "\n```",
            _messages.value,
            currentLanguage,
            currentCode,
            displayUser = "【分析报错】",
        )
    }

    /** 统一流式对话：先插入用户消息 + 空助手气泡，再边生成边更新（含节流）。 */
    private fun streamReply(
        userText: String,
        history: List<AiMessage>,
        language: Language,
        code: String,
        displayUser: String = userText,
    ) {
        _messages.value = history +
            AiMessage(AiMessage.Role.USER, displayUser) +
            AiMessage(AiMessage.Role.ASSISTANT, "")
        _isThinking.value = true
        viewModelScope.launch {
            val acc = StringBuilder()
            var lastUi = 0L
            try {
                ai.chatStream(userText, history, language, code) { chunk ->
                    acc.append(chunk)
                    val now = System.currentTimeMillis()
                    if (now - lastUi > 60) {   // 节流：避免每个 token 都触发重组
                        lastUi = now
                        updateLastAssistant(acc.toString())
                    }
                }
            } catch (t: Throwable) {
                acc.append("⚠️ ${t.message ?: t.toString()}")
            } finally {
                updateLastAssistant(acc.toString())
                _isThinking.value = false
            }
        }
    }

    private fun updateLastAssistant(content: String) {
        val list = _messages.value.toMutableList()
        val idx = list.indexOfLast { it.role == AiMessage.Role.ASSISTANT }
        if (idx >= 0) {
            list[idx] = AiMessage(AiMessage.Role.ASSISTANT, content)
            _messages.value = list
        }
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
