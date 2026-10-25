package com.terminalide.core

/**
 * 三种语言及各自展示信息、文件后缀与起始模板。
 */
enum class Language(
    val displayName: String,
    val fileExtension: String,
    val template: String,
) {
    PYTHON("Python", "py", PYTHON_TEMPLATE),
    C("C", "c", C_TEMPLATE),
    CPP("C++", "cpp", CPP_TEMPLATE),
    ;

    companion object {
        const val PYTHON_TEMPLATE = """# 欢迎使用 Terminal IDE
# 已预装 requests；可用包管理面板安装任意 PyPI 包

def main():
    import sys
    print("Hello from CPython on Android!")
    print("Python", sys.version)

if __name__ == "__main__":
    main()
"""

        const val C_TEMPLATE = """#include <stdio.h>

int main(void) {
    printf("Hello from C on Android (aarch64, -static)\\n");
    return 0;
}
"""

        const val CPP_TEMPLATE = """#include <iostream>
#include <string>

int main() {
    std::string s = "Hello from C++ on Android (aarch64, -static)";
    std::cout << s << std::endl;
    return 0;
}
"""
    }
}
