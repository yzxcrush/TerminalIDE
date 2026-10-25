package com.terminalide.backend

import com.terminalide.core.Language
import com.terminalide.core.Sandbox

/**
 * 语言 → 后端 的注册表，供 ViewModel / UI 按当前语言取后端。
 */
class BackendRegistry(sandbox: Sandbox, toolchain: NdkToolchain) {

    private val backends: Map<Language, LanguageBackend> = mapOf(
        Language.PYTHON to PythonBackend(sandbox),
        Language.C to CBackend(sandbox, toolchain),
        Language.CPP to CppBackend(sandbox, toolchain),
    )

    fun backendFor(language: Language): LanguageBackend = backends.getValue(language)
}
