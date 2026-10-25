package com.terminalide.core

import android.content.Context
import java.io.File

/**
 * 依赖沙箱：所有文件都写入 App 私有目录（files/），不污染系统，卸载即清理。
 *
 * 目录结构：
 * ```
 * files/
 * ├── sandbox/
 * │   ├── workspace/      用户源码与项目文件
 * │   ├── site-packages/  Python 运行期安装的第三方包（本地沙箱 site-packages）
 * │   ├── build/          C/C++ 编译产物与可执行文件
 * │   ├── tmp/            临时文件
 * │   └── pip.conf        pip 镜像源配置（写入沙箱）
 * ├── toolchain/          NDK/LLVM 交叉编译工具链（运行期解压自 assets）
 * └── models/             端侧小模型（运行期可选下载）
 * ```
 */
class Sandbox(context: Context) {

    private val root = File(context.filesDir, "sandbox").apply { mkdirs() }

    val workspace: File = File(root, "workspace").apply { mkdirs() }
    val sitePackages: File = File(root, "site-packages").apply { mkdirs() }
    val build: File = File(root, "build").apply { mkdirs() }
    val tmp: File = File(root, "tmp").apply { mkdirs() }
    val pipConf: File = File(root, "pip.conf")

    /** NDK/LLVM 工具链根目录（含 bin/ 与 sysroot/）。 */
    val toolchain: File = File(context.filesDir, "toolchain").apply { mkdirs() }

    /** 端侧小模型目录。 */
    val models: File = File(context.filesDir, "models").apply { mkdirs() }

    /** 语言工作目录（workspace/<ext>），按语言隔离。 */
    fun languageDir(ext: String): File = File(workspace, ext).apply { mkdirs() }

    /** 新建临时文件。 */
    fun newTmpFile(prefix: String, suffix: String): File =
        File(tmp, "$prefix-${System.currentTimeMillis()}$suffix")
}
