# TerminalIDE

Telegram 风格的移动端**三语言 IDE**（Python / C / C++），可打包成可安装的 Android APK（arm64-v8a, release 签名）。

| 组件 | 选型 |
| --- | --- |
| UI | Jetpack Compose + Material 3（深色主题、动态取色、Telegram 风格） |
| Python 运行时 | Chaquopy 嵌入式 CPython（Python 3.12） |
| C/C++ 编译 | NDK/LLVM clang / clang++（`aarch64-linux-android` 目标，`-static` 静态链接） |
| 包管理 | 运行期 pip（嵌入式解释器内跑 pip，`--target` 到沙箱 site-packages） |
| 镜像源 | 官方 / 清华 / 阿里 / 中科大，支持全局默认源 + 临时源 |
| AI | 本地端侧小模型（离线，默认）+ DeepSeek 官方 API（第三方客户端） |
| 密钥存储 | `EncryptedSharedPreferences`（AES256） |

---

## 目录结构

```
TerminalIDE/
├── settings.gradle.kts
├── build.gradle.kts                 # 插件版本声明（AGP/Kotlin/Compose/Chaquopy）
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml           # 版本目录
│   └── wrapper/gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts             # Chaquopy + NDK(CMake) + Compose 配置
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/                     # NDK JNI 桥接（native-lib.cpp + CMakeLists.txt）
│       ├── python/                  # 随 APK 打包的 Python 助手
│       │   ├── ide_runner.py        #   用户代码执行 + IO 捕获
│       │   └── pip_runner.py        #   运行期 pip 驱动（镜像源/only-binary）
│       ├── assets/toolchain/        # （fetch_toolchain.sh 放置）设备端 clang 工具链
│       ├── res/
│       └── java/com/terminalide/
│           ├── TerminalIdeApp.kt    # 启动 CPython、初始化服务、解压工具链
│           ├── MainActivity.kt
│           ├── core/                # Language/RunResult/Sandbox/SettingsStore/...
│           ├── backend/             # LanguageBackend + Python/C/C++ + NdkToolchain
│           ├── packages/            # MirrorSource + PackageManager + PipRunner
│           ├── ai/                  # AiBackend + Local/DeepSeek + ApiKeyStore + AiService
│           └── ui/                  # ViewModel + theme + 各 Compose 界面
└── scripts/
    ├── build_release.sh             # assembleRelease + zipalign + apksigner 签名
    ├── fetch_toolchain.sh           # 下载设备端 clang 工具链到 assets
    └── fetch_local_model.sh         # 宿主机预下载端侧模型
```

---

## 构建前置要求

- JDK 17
- Android SDK（`compileSdk 35`、`build-tools`，含 `zipalign`、`apksigner`）
- NDK（构建期编译 JNI 桥接库；Chaquopy 自身原生库已预编译，无需额外 NDK）
- 本机 Python 3.12（Chaquopy 构建期安装 pip/setuptools/wheel/requests 时使用，`buildPython` 自动探测）
- Gradle 8.11+（或用 Android Studio 打开自动生成 wrapper）

> 说明：构建期预装的 `pip/setuptools/wheel/requests` 都是纯 Python 包，**不会现场编译任何 CPython 扩展**。

## 构建与打包

**方式一：Android Studio**

1. 用 Android Studio 打开本目录，等 Gradle Sync 完成（会自动生成 wrapper）。
2. 菜单 *Build → Build APK(s)*，或 *Generate Signed Bundle/APK* 自选签名。

**方式二：命令行（一键出签名 APK）**

```bash
./scripts/build_release.sh
# 产物：release/TerminalIDE-arm64-v8a-release.apk
# 安装：adb install -r release/TerminalIDE-arm64-v8a-release.apk
```

脚本流程：`assembleRelease`（产出 `app-release-unsigned.apk`）→ 自动生成 keystore（如无）→ `zipalign` → `apksigner` 签名。
签名信息可用环境变量覆盖：`KEYSTORE / KEYSTORE_PASS / KEY_ALIAS / KEY_PASS`。

> 正式发布请替换为自己的 keystore，切勿使用默认密码。

---

## 镜像源切换（重点）

镜像源定义在 `packages/MirrorSource.kt`：

| id | 名称 | index URL |
| --- | --- | --- |
| official | 官方源 (pypi.org) | `https://pypi.org/simple` |
| tsinghua | 清华源 | `https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple` |
| aliyun | 阿里源 | `https://mirrors.aliyun.com/pypi/simple` |
| ustc | 中科大源 | `https://mirrors.ustc.edu.cn/pypi/simple` |

两种模式：

1. **全局默认源**：`PackageViewModel.setMirror()` → `SettingsStore.globalMirrorId` 持久化；`install()` 不传 mirror 时使用。
2. **临时指定源**：`PackageViewModel.install(name, mirror)` 仅本次生效，不改全局设置。

运行期 pip 命令会自动带 `--index-url <镜像>`，原生包另带 `--extra-index-url https://chaquo.com/pypi-13.1/`（Chaquopy 官方预编译 wheel 仓库）。

## 运行期装包原理

- 沙箱目录：`files/sandbox/site-packages`（App 私有目录，卸载即清理，不污染系统）。
- `pip_runner.py` 在嵌入式 CPython 内调用 `pip._internal.cli.main`，参数强制：
  - `--target <site-packages>`
  - `--index-url <镜像>` + `--extra-index-url <Chaquopy wheel 仓库>`
  - `--only-binary :all:`（**不现场编译任何 C 扩展**）
- 装好后 `ide_runner.py` 会把沙箱 site-packages 插到 `sys.path` 最前，`import` 即可用。
- 纯 Python 包从镜像直接装 wheel；原生包走 Chaquopy 预编译 wheel 仓库。无对应预编译 wheel 的原生包会报错（不会尝试源码编译）。

## C/C++ 工具链

`NdkToolchain` 负责「源码写沙箱 → clang/clang++ 编译 → 运行 → 实时捕获 stdout/stderr」。

- 编译命令：`<clang|clang++> --target=aarch64-linux-android24 -std=c11|c++17 -O2 -static -s ...`，静态链接、不依赖系统动态库。
- 运行：`ProcessBuilder` 启动可执行文件，逐行实时回调到 UI；30s 超时强杀。

> 官方 NDK 的 clang 运行在宿主机上，不能直接在设备里执行。要在**设备端**编译，需要针对 Android/aarch64 的 clang 发行包：

```bash
TOOLCHAIN_URL="https://example.com/clang-aarch64-android.tar.xz" ./scripts/fetch_toolchain.sh
```

放置到 `app/src/main/assets/toolchain`（含 `bin/aarch64-linux-android24-clang`、`-clang++`，可选 `sysroot/`）后打进 APK，运行期由 App 解压到 `files/toolchain` 并 `chmod +x`。

## AI 双后端

- **本地端侧小模型（默认，离线）**：`LocalModelBackend` 通过子进程运行 llama.cpp 风格的 `llama-cli`（`files/models` 下，`chmod +x`）。模型与运行时都**不打包进 APK**，运行期可选下载（App 内「设置 → 端侧模型 → 下载」，或 `scripts/fetch_local_model.sh` 预下载）。
- **DeepSeek 官方 API**：`DeepSeekBackend` 走 OpenAI 兼容协议 `POST <base>/chat/completions`（模型 `deepseek-chat`）。流程：填 Key → 探测验证（`probeWithKey`）→ 通过后写 `EncryptedSharedPreferences` 加密存储。
- 上下文：每次请求自动织入「当前语言 + 当前编辑器代码」；回复里的代码块可**一键插入编辑器**。

## 已知限制 / 注意

- 运行期原生包依赖 Chaquopy 预编译 wheel 仓库覆盖范围；无对应 wheel 的原生包无法安装（符合「不现场编译 C 扩展」约束）。
- Python 输出为运行结束后一次性回显；C/C++ 为真·实时逐行回显。
- `multiprocessing` 在 Android 上受限（用 `multiprocessing.dummy` 代替）。
- 仅打 `arm64-v8a`（Python 3.12+ 只支持 64 位）；minSdk 24。
