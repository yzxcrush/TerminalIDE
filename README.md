# TerminalIDE

> 手机上真的能写代码的 IDE —— Python 运行 + 本地 AI 助手 + 移动端代码编辑器

一个 **Telegram 风格**的 Android 移动端 IDE，内置 **CPython 3.13** 与 **llama.cpp 推理引擎**，
让你在手机上写、跑、调试 Python，还能和**本地离线 / 在线 AI** 边聊边写代码。

**作者：@惜白**

---

## ✨ 功能特性

### 🐍 Python 运行
- 内置 **CPython 3.13**（Chaquopy 嵌入式），开箱即用，无需外部环境
- **交互式控制台**：程序运行时可实时输入（`input()` 生效），输出流式显示
- 「预填输入」批量喂数据、输出自动滚动到最新
- 运行期 **pip 安装任意 PyPI 包**（官方 / 清华 / 阿里 / 中科大镜像一键切换）
- 预装常用库：`requests` `beautifulsoup4` `tqdm` `colorama` `rich`

### 🤖 AI 助手
- **本地离线模型**：内置 llama.cpp 推理引擎，可下载 DeepSeek-R1 蒸馏模型（1.5B / 7B）
- **DeepSeek 官方 API**：V3（`deepseek-chat`）/ R1（`deepseek-reasoner`）
- **AI 快捷操作**：解释代码 · 修复错误 · 优化 · 加注释 · 生成测试
- AI 回复中的代码块可**一键插入编辑器**
- API Key 使用 `EncryptedSharedPreferences`（AES256）加密存储

### ✏️ 编辑器
- Python / C / C++ 语法高亮 + 行号 + 圆角卡片
- **代码符号工具条**：手机键盘缺失的 `( ) [ ] { } : " ' = + - * / #` 等一键输入
- **括号自动配对** + **回车自动缩进**（Python `:` 后自动缩进 4 空格）
- **编辑器 / 控制台可拖动分隔条**，自由调整比例
- 文件树、保存、新建文件、`.py` 文件关联（打开方式）

### 🎨 界面
- Telegram 风格深色主题、圆角渐变顶栏、胶囊式语言切换
- 底部导航（运行 / 保存 / 包管理）
- 鲸鱼娘自适应图标、`@惜白` 水印

---

## 📥 下载安装

前往 **[Releases](https://github.com/yzxcrush/TerminalIDE/releases)** 下载最新 APK：

| 项目 | 说明 |
|---|---|
| 架构 | **arm64-v8a**（64 位 ARM） |
| 最低系统 | **Android 7.0（API 24）** |
| 目标系统 | Android 15（API 35） |
| 大小 | 约 38MB |

> 首次使用 AI：在「设置 → AI 后端」填入 DeepSeek API Key，或下载本地模型离线使用。

---

## 🛠 技术栈

| 组件 | 选型 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Python 运行时 | Chaquopy（CPython 3.13） |
| 本地推理 | llama.cpp（随 APK 打包在 jniLibs） |
| AI 服务 | DeepSeek API（OpenAI 兼容协议） |
| 包管理 | 运行期 pip（`--target` 沙箱 site-packages） |
| 签名 | 固定 PKCS12 证书（支持就地升级、不丢数据） |
| CI/CD | GitHub Actions（自动构建 + 签名 + Release） |

---

## 🔧 从源码构建

前置：JDK 17、Android SDK（compileSdk 35）、Python 3.13（Chaquopy 构建期用）

```bash
# 1) 克隆
git clone https://github.com/yzxcrush/TerminalIDE.git
cd TerminalIDE

# 2) 一键构建 + 签名（自动生成/使用固定证书）
chmod +x scripts/*.sh
./scripts/build_release.sh

# 3) 产物
# release/TerminalIDE-arm64-v8a-release.apk
```

也可以直接推送到 `main`，GitHub Actions 会自动构建并上传产物。

---

## 📁 目录结构

```
TerminalIDE/
├── app/src/main/
│   ├── java/com/terminalide/
│   │   ├── ai/          # AI 后端：本地 llama.cpp + DeepSeek API
│   │   ├── backend/     # 语言后端：Python/C/C++ 执行
│   │   ├── core/        # 沙箱/设置/语言定义
│   │   ├── packages/    # 镜像源 + pip 包管理
│   │   └── ui/          # Compose 界面 + ViewModel
│   ├── python/          # ide_runner.py（交互执行）+ pip_runner.py
│   ├── jniLibs/         # llama.cpp 推理引擎
│   └── res/             # 鲸鱼娘图标等资源
├── scripts/build_release.sh   # 构建 + zipalign + apksigner
├── signing/terminalide.p12    # 固定签名证书
└── .github/workflows/         # GitHub Actions 自动打包
```

---

## ⚠️ 已知限制

- **C/C++ 编译**：受 Android 10+ SELinux 限制（禁止 App 执行私有目录中的二进制），
  需将 clang 工具链打入 APK（+135MB）才能启用，当前暂未开启。
- **文件关联**：`content://` 形式可用；`file://` 形式受分区存储限制。
- **本地模型**：7B 模型约 4.7GB，下载耗时较长，建议在 WiFi 下进行。

---

## 📄 License

MIT License

---

Made with ❤️ by **@惜白**
