package com.terminalide.core

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * 把 APK assets 下的一个子树递归解压到 App 私有目录（files/）。
 * 用于将打包进 APK 的 NDK 工具链（assets/toolchain）解压到 sandbox.toolchain。
 */
object AssetInstaller {

    fun install(context: Context, assetPath: String, targetDir: File) {
        val am = context.assets
        val names = am.list(assetPath) ?: return
        targetDir.mkdirs()
        for (name in names) {
            val full = if (assetPath.isEmpty()) name else "$assetPath/$name"
            val target = File(targetDir, name)
            val sub = am.list(full)
            if (sub == null) {
                // 文件
                target.parentFile?.mkdirs()
                am.open(full).use { input ->
                    FileOutputStream(target).use { out -> input.copyTo(out) }
                }
            } else {
                // 目录：递归
                install(context, full, target)
            }
        }
    }
}
