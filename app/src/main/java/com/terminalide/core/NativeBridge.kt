package com.terminalide.core

/**
 * NDK clang/clang++ 编译的 JNI 桥接库（app/src/main/cpp）。
 * 用于演示「Chaquopy + NDK 打进 APK」中的 NDK 原生部分。
 */
object NativeBridge {

    private val loaded: Boolean = try {
        System.loadLibrary("terminalide")
        true
    } catch (t: Throwable) {
        false
    }

    /** 返回桥接库状态串。 */
    fun version(): String = if (loaded) nativeVersion() else "native lib 未加载"

    private external fun nativeVersion(): String
}
