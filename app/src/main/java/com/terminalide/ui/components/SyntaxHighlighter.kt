package com.terminalide.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.terminalide.core.Language

/**
 * 轻量级语法高亮：Python / C / C++ 的关键字、字符串、注释、数字、预处理指令、
 * 类型与函数调用着色。用正则交替 + 命名分组实现，无第三方依赖。
 */
object SyntaxHighlighter {

    data class Palette(
        val keyword: Color,
        val string: Color,
        val comment: Color,
        val number: Color,
        val preprocessor: Color,
        val function: Color,
        val type: Color,
        val plain: Color,
    )

    /** 深色主题默认调色板（深蓝底上可读性好）。 */
    val defaultPalette: Palette = Palette(
        keyword = Color(0xFFCC7832),
        string = Color(0xFF6A8759),
        comment = Color(0xFF7F7F7F),
        number = Color(0xFF6897BB),
        preprocessor = Color(0xFF9876AA),
        function = Color(0xFFFFC66D),
        type = Color(0xFF4EC9B0),
        plain = Color(0xFFD4D4D4),
    )

    private val PY_KEYWORDS = setOf(
        "def", "class", "if", "elif", "else", "for", "while", "return", "import", "from",
        "as", "try", "except", "finally", "with", "lambda", "pass", "break", "continue",
        "raise", "yield", "global", "nonlocal", "assert", "del", "not", "and", "or", "in",
        "is", "None", "True", "False", "async", "await",
    )

    private val C_KEYWORDS = setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
        "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
        "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct",
        "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
        // C++
        "alignas", "alignof", "asm", "bool", "catch", "class", "concept", "constexpr",
        "const_cast", "decltype", "delete", "dynamic_cast", "explicit", "export", "false",
        "friend", "mutable", "namespace", "new", "noexcept", "nullptr", "operator",
        "private", "protected", "public", "reinterpret_cast", "static_assert", "static_cast",
        "template", "this", "thread_local", "throw", "true", "try", "typeid", "typename",
        "using", "virtual", "wchar_t",
    )

    private val C_TYPES = setOf(
        "size_t", "ssize_t", "int8_t", "uint8_t", "int16_t", "uint16_t", "int32_t",
        "uint32_t", "int64_t", "uint64_t", "string", "vector", "map", "unordered_map",
        "set", "cout", "cin", "endl",
    )

    private val regexCache = mutableMapOf<Language, Regex>()

    fun highlight(code: String, language: Language, palette: Palette): AnnotatedString {
        val regex = regexCache.getOrPut(language) { patternFor(language) }
        // PREPROCESSOR / TYPE 分组只在 C/C++ 的正则里存在；对 Python 访问这些名字会抛
        // IllegalArgumentException（No capturing group ...），因此必须按语言门控。
        val isCCpp = language != Language.PYTHON
        return buildAnnotatedString {
            var last = 0
            for (match in regex.findAll(code)) {
                val start = match.range.first
                if (start > last) append(code.substring(last, start))

                val style = when {
                    match.groups["STRING"] != null -> SpanStyle(color = palette.string)
                    match.groups["COMMENT"] != null -> SpanStyle(color = palette.comment)
                    isCCpp && match.groups["PREPROCESSOR"] != null ->
                        SpanStyle(color = palette.preprocessor)
                    match.groups["NUMBER"] != null -> SpanStyle(color = palette.number)
                    match.groups["KEYWORD"] != null ->
                        SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)
                    isCCpp && match.groups["TYPE"] != null -> SpanStyle(color = palette.type)
                    match.groups["FUNCTION"] != null -> SpanStyle(color = palette.function)
                    else -> SpanStyle(color = palette.plain)
                }
                pushStyle(style)
                append(match.value)
                pop()
                last = match.range.last + 1
            }
            if (last < code.length) append(code.substring(last))
        }
    }

    private fun patternFor(language: Language): Regex {
        val kwAlt = (if (language == Language.PYTHON) PY_KEYWORDS else C_KEYWORDS)
            .joinToString("|")
        val isPython = language == Language.PYTHON

        val sb = StringBuilder()
        sb.append("(?s)")
        // 1) 字符串（含三引号多行）
        sb.append("(?<STRING>'''.*?'''|\"\"\".*?\"\"\"|'(?:\\\\.|[^'\\\\\\n])*'|\"(?:\\\\.|[^\"\\\\\\n])*\")|")
        // 2) 注释 / 预处理指令
        if (isPython) {
            sb.append("(?<COMMENT>#[^\\n]*)|")
        } else {
            sb.append("(?<PREPROCESSOR>#[^\\n]*)|")
            sb.append("(?<COMMENT>//[^\\n]*|/\\*.*?\\*/)|")
        }
        // 3) 数字
        sb.append("(?<NUMBER>\\b(?:0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\b)|")
        // 4) 关键字
        sb.append("(?<KEYWORD>\\b(?:$kwAlt)\\b)|")
        // 5) 类型（仅 C/C++）
        if (!isPython) {
            sb.append("(?<TYPE>\\b(?:${C_TYPES.joinToString("|")})\\b)|")
        }
        // 6) 函数调用
        sb.append("(?<FUNCTION>\\b[A-Za-z_]\\w*(?=\\s*\\())")
        return Regex(sb.toString())
    }
}
