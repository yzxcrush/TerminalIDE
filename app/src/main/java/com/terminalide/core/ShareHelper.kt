package com.terminalide.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 代码导出 / 分享：
 *  - 分享为文件（ACTION_SEND + FileProvider，可发到微信/QQ/邮件/云盘）
 *  - 复制到剪贴板
 *
 * 代码原本只存在 App 私有目录，这里提供了「把代码拿出来」的出口。
 */
object ShareHelper {

    /** 根据语言生成带扩展名的文件名。 */
    fun fileNameFor(language: Language): String = "code.${language.fileExtension}"

    /** 分享源码文件。 */
    fun share(context: Context, fileName: String, code: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(code)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeFor(fileName)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, code)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, fileName))
    }

    /** 复制代码到剪贴板。 */
    fun copy(context: Context, code: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("code", code))
    }

    private fun mimeFor(fileName: String): String = when {
        fileName.endsWith(".py", true) -> "text/x-python"
        fileName.endsWith(".c", true) -> "text/x-csrc"
        fileName.endsWith(".cpp", true) || fileName.endsWith(".cc", true) -> "text/x-c++src"
        else -> "text/plain"
    }
}
