package com.terminalide.packages

/**
 * pip 镜像源。切换后运行期 pip 命令自动带 `--index-url`；
 * 也支持「全局默认源」与「单次临时源」两种模式（见 PackageManager）。
 */
enum class MirrorSource(
    val id: String,
    val displayName: String,
    val indexUrl: String,
) {
    OFFICIAL("official", "官方源 (pypi.org)", "https://pypi.org/simple"),
    TSINGHUA("tsinghua", "清华源", "https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple"),
    ALIYUN("aliyun", "阿里源", "https://mirrors.aliyun.com/pypi/simple"),
    USTC("ustc", "中科大源", "https://mirrors.ustc.edu.cn/pypi/simple"),
    ;

    /** 简洁标签（用于 UI 列表）。 */
    val shortName: String get() = id

    companion object {
        /** Chaquopy 官方预编译原生 wheel 仓库（Chaquopy 13.1+）。 */
        const val CHAQOUPY_REPO = "https://chaquo.com/pypi-13.1/"

        fun byId(id: String): MirrorSource =
            entries.firstOrNull { it.id == id } ?: OFFICIAL
    }
}
