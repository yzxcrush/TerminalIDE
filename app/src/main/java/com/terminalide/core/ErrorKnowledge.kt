package com.terminalide.core

/**
 * 内置 Python 常见错误知识库（离线，不依赖 AI）。
 * 运行报错后从 traceback 里匹配异常类型，给出「原因 + 怎么修」。
 */
object ErrorKnowledge {

    data class Entry(
        val keyword: String,
        val title: String,
        val cause: String,
        val fix: String,
    )

    private val entries = listOf(
        Entry(
            "NameError", "变量名未定义",
            "用了一个没有定义过的变量或函数名。",
            "检查拼写；确认变量在使用前已经赋值；检查 import 是否成功。",
        ),
        Entry(
            "UnboundLocalError", "局部变量未赋值",
            "函数里使用了还没赋过值的局部变量。",
            "先给变量赋值再使用，或检查是不是想用全局变量（global）。",
        ),
        Entry(
            "ZeroDivisionError", "除数为零",
            "除法或取模运算的除数是 0。",
            "运算前先判断除数是否为 0，或捕获异常处理。",
        ),
        Entry(
            "IndexError", "索引越界",
            "访问了列表/字符串/元组中不存在的位置。",
            "检查索引范围（0 到 len-1）；用 len() 先判断长度。",
        ),
        Entry(
            "KeyError", "字典键不存在",
            "访问了字典里没有的键。",
            "用 dict.get(key, 默认值)，或先 if key in dict 判断。",
        ),
        Entry(
            "TypeError", "类型不匹配",
            "对错误类型的对象做了操作，比如数字和字符串相加。",
            "用 int()/str()/float() 做类型转换；检查函数参数类型。",
        ),
        Entry(
            "ValueError", "值不合法",
            "类型正确但值不合法，例如 int('abc')。",
            "检查输入内容；用 try/except 捕获处理。",
        ),
        Entry(
            "IndentationError", "缩进错误",
            "Python 对缩进敏感，这一行的缩进不对。",
            "统一用 4 个空格，不要混用 Tab 和空格。",
        ),
        Entry(
            "TabError", "Tab 与空格混用",
            "代码里同时用了 Tab 和空格缩进。",
            "把缩进统一成 4 个空格。",
        ),
        Entry(
            "SyntaxError", "语法错误",
            "代码不符合 Python 语法规则。",
            "检查括号/引号是否配对；检查 if/for/def 行末是否漏了冒号。",
        ),
        Entry(
            "AttributeError", "属性/方法不存在",
            "对象没有这个属性或方法。",
            "检查方法名拼写；确认对象类型是否正确（比如列表没有 append 以外的名字）。",
        ),
        Entry(
            "ImportError", "导入失败",
            "找不到要导入的模块或模块里的名字。",
            "在「包管理」面板安装该包；检查模块名拼写。",
        ),
        Entry(
            "ModuleNotFoundError", "模块未安装",
            "找不到指定的第三方模块。",
            "在「包管理」面板搜索并安装该包。",
        ),
        Entry(
            "FileNotFoundError", "文件不存在",
            "要打开的文件路径不对，或文件不在沙箱里。",
            "检查路径是否正确；用 os.path.exists() 先判断；绝对路径要用 App 沙箱内的路径。",
        ),
        Entry(
            "PermissionError", "没有权限",
            "没有权限访问该文件或目录。",
            "Android 下无法直接读写 /sdcard 外的目录，请使用 App 沙箱路径。",
        ),
        Entry(
            "RecursionError", "递归过深",
            "递归函数缺少正确的终止条件。",
            "检查递归的终止条件，或改成循环实现。",
        ),
        Entry(
            "MemoryError", "内存不足",
            "数据量太大，内存装不下。",
            "分批处理数据；或减小数据规模。",
        ),
        Entry(
            "StopIteration", "迭代已结束",
            "迭代器已经耗尽还在继续取值。",
            "用 for 循环代替手动的 next()。",
        ),
        Entry(
            "AssertionError", "断言失败",
            "assert 语句的条件不成立。",
            "检查 assert 的条件是否符合预期。",
        ),
        Entry(
            "OverflowError", "数值溢出",
            "数值超出可表示的范围（常见于 float 运算）。",
            "检查数值范围；用 decimal 模块处理高精度。",
        ),
    )

    /** 根据 traceback 文本匹配错误条目。 */
    fun lookup(tracebackText: String): Entry? =
        entries.firstOrNull { tracebackText.contains(it.keyword) }

    /** 生成一段人类可读的提示文本（追加到控制台）。 */
    fun hintFor(tracebackText: String): String? {
        val e = lookup(tracebackText) ?: return null
        return buildString {
            append("\n💡 ${e.title}（${e.keyword}）\n")
            append("   原因：${e.cause}\n")
            append("   解决：${e.fix}\n")
        }
    }
}
