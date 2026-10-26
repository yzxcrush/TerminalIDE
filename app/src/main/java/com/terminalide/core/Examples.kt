package com.terminalide.core

/** 一个可一键载入的示例。 */
data class CodeExample(
    val title: String,
    val description: String,
    val category: String,
    val code: String,
)

/**
 * 内置示例库：只保留「实用型」示例（爬虫 / 文件 / 数据 / 文本），不含游戏。
 * 优先使用 App 预装库（requests / bs4）或标准库，确保点开即能运行。
 */
object Examples {

    val all: List<CodeExample> = listOf(

        // ---------- 网络 · 爬虫 ----------
        CodeExample(
            title = "网页标题抓取",
            description = "用 requests + BeautifulSoup 抓取网页标题",
            category = "网络 · 爬虫",
            code = """import requests
from bs4 import BeautifulSoup

url = "https://www.python.org"
resp = requests.get(url, timeout=10)
soup = BeautifulSoup(resp.text, "html.parser")

print("状态码:", resp.status_code)
print("网页标题:", soup.title.string)
""",
        ),
        CodeExample(
            title = "提取页面所有链接",
            description = "抓取指定网页里的全部超链接",
            category = "网络 · 爬虫",
            code = """import requests
from bs4 import BeautifulSoup

url = "https://www.python.org"
html = requests.get(url, timeout=10).text
soup = BeautifulSoup(html, "html.parser")

links = [a.get("href") for a in soup.find_all("a") if a.get("href")]
print(f"共找到 {len(links)} 个链接：")
for link in links[:10]:
    print(" -", link)
""",
        ),
        CodeExample(
            title = "批量下载图片",
            description = "把一组图片链接批量下载到本地",
            category = "网络 · 爬虫",
            code = """import os
import requests

urls = [
    "https://www.python.org/static/img/python-logo.png",
]
os.makedirs("downloads", exist_ok=True)

for i, url in enumerate(urls, 1):
    resp = requests.get(url, timeout=15)
    path = f"downloads/img_{i}.png"
    with open(path, "wb") as f:
        f.write(resp.content)
    print(f"已保存 {path}（{len(resp.content)} 字节）")
""",
        ),
        CodeExample(
            title = "网页标题转 Markdown",
            description = "抓取网页的标题结构并输出成 Markdown",
            category = "网络 · 爬虫",
            code = """import requests
from bs4 import BeautifulSoup

html = requests.get("https://www.python.org", timeout=10).text
soup = BeautifulSoup(html, "html.parser")

for tag in soup.find_all(["h1", "h2"])[:8]:
    level = int(tag.name[1])
    print("#" * level, tag.get_text(strip=True))
""",
        ),

        // ---------- 文本 · 数据 ----------
        CodeExample(
            title = "文本词频统计",
            description = "统计一段文字里出现最多的词",
            category = "文本 · 数据",
            code = """from collections import Counter

text = ("Python is a programming language. Python is easy to learn. "
        "Python is powerful and Python is popular.")
words = text.lower().replace(".", "").split()

for word, count in Counter(words).most_common(5):
    print(f"{word:12s} {count}")
""",
        ),
        CodeExample(
            title = "正则提取邮箱",
            description = "从文本中批量提取邮箱地址",
            category = "文本 · 数据",
            code = """import re

text = "联系 a@example.com 或 b_c@test.org，无效的 @x"
emails = re.findall(r"[\w.+-]+@[\w-]+\.[\w.]+", text)

print("找到", len(emails), "个邮箱：")
for e in emails:
    print(" -", e)
""",
        ),
        CodeExample(
            title = "CSV 读写",
            description = "写入并读取 CSV 表格文件",
            category = "文本 · 数据",
            code = """import csv

rows = [
    ["姓名", "年龄", "城市"],
    ["小明", 18, "北京"],
    ["小红", 20, "上海"],
]
with open("people.csv", "w", newline="", encoding="utf-8") as f:
    csv.writer(f).writerows(rows)
print("已写入 people.csv")

with open("people.csv", encoding="utf-8") as f:
    for row in csv.reader(f):
        print(row)
""",
        ),
        CodeExample(
            title = "JSON 读写与美化",
            description = "把字典保存成格式化 JSON 再读回",
            category = "文本 · 数据",
            code = """import json

data = {
    "name": "Terminal IDE",
    "version": "1.23.2",
    "features": ["REPL", "AI 助手", "终端"],
}
with open("config.json", "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(open("config.json", encoding="utf-8").read())

loaded = json.load(open("config.json", encoding="utf-8"))
print("features:", loaded["features"])
""",
        ),
        CodeExample(
            title = "Base64 编解码",
            description = "把文本编码成 Base64 再解码回来",
            category = "文本 · 数据",
            code = """import base64

text = "Terminal IDE 中文测试"
encoded = base64.b64encode(text.encode()).decode()
print("编码后:", encoded)
print("解码后:", base64.b64decode(encoded).decode())
""",
        ),

        // ---------- 文件 · 系统 ----------
        CodeExample(
            title = "文件 MD5 校验",
            description = "计算文件的 MD5 指纹，用于校验完整性",
            category = "文件 · 系统",
            code = """import hashlib
import os

for path in ["main.py"]:
    if os.path.exists(path):
        digest = hashlib.md5(open(path, "rb").read()).hexdigest()
        print(f"{path}: {digest}")
    else:
        print(f"{path} 不存在")
""",
        ),
        CodeExample(
            title = "批量重命名文件",
            description = "给目录下所有 .txt 文件统一加前缀",
            category = "文件 · 系统",
            code = """import os

os.makedirs("files", exist_ok=True)
for i in range(3):
    with open(f"files/a{i}.txt", "w") as f:
        f.write(f"内容 {i}")

for name in os.listdir("files"):
    if name.endswith(".txt") and not name.startswith("renamed_"):
        os.rename(f"files/{name}", f"files/renamed_{name}")

print("重命名后:", sorted(os.listdir("files")))
""",
        ),
        CodeExample(
            title = "ZIP 压缩与查看",
            description = "创建 zip 压缩包并列出其中文件",
            category = "文件 · 系统",
            code = """import zipfile

with open("hello.txt", "w", encoding="utf-8") as f:
    f.write("Hello, ZIP!")

with zipfile.ZipFile("demo.zip", "w") as z:
    z.write("hello.txt")

with zipfile.ZipFile("demo.zip") as z:
    print("压缩包内容:", z.namelist())
""",
        ),
        CodeExample(
            title = "系统与运行环境",
            description = "查看 Python 版本、系统信息和路径",
            category = "文件 · 系统",
            code = """import os
import platform
import sys

print("Python 版本:", sys.version.split()[0])
print("平台:", platform.system(), platform.release())
print("架构:", platform.machine())
print("工作目录:", os.getcwd())
""",
        ),
        CodeExample(
            title = "时间与日期处理",
            description = "获取当前时间、日期计算与格式化",
            category = "文件 · 系统",
            code = """from datetime import datetime, timedelta

now = datetime.now()
print("现在:", now.strftime("%Y-%m-%d %H:%M:%S"))
print("7 天后:", (now + timedelta(days=7)).strftime("%Y-%m-%d"))
print("星期:", "一二三四五六日"[now.weekday()])
""",
        ),

        // ---------- 安全 · 工具 ----------
        CodeExample(
            title = "随机密码生成",
            description = "用 secrets 生成高强度随机密码",
            category = "安全 · 工具",
            code = """import secrets
import string

chars = string.ascii_letters + string.digits + "!@#$%^&*"
for i in range(3):
    pwd = "".join(secrets.choice(chars) for _ in range(16))
    print(f"密码 {i + 1}: {pwd}")
""",
        ),
        CodeExample(
            title = "目录体积统计",
            description = "递归统计目录大小并按大小排序",
            category = "安全 · 工具",
            code = """import os

def size_of(path):
    total = 0
    for root, _, files in os.walk(path):
        for name in files:
            try:
                total += os.path.getsize(os.path.join(root, name))
            except OSError:
                pass
    return total

for entry in sorted(os.listdir(".")):
    if os.path.isdir(entry):
        print(f"{entry:20s} {size_of(entry) / 1024:.1f} KB")
""",
        ),
    )

    /** 按分类分组，便于展示。 */
    fun grouped(): Map<String, List<CodeExample>> =
        all.groupBy { it.category }
}
