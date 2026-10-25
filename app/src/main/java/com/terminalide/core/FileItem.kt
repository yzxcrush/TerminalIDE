package com.terminalide.core

/**
 * 文件树节点。
 */
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val language: Language? = null,
)
