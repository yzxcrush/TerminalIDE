#!/usr/bin/env bash
set -euo pipefail

# 构建并签名可安装 APK（arm64-v8a, release）。
# 产物：release/TerminalIDE-arm64-v8a-release.apk
#
# 用法：
#   ./scripts/build_release.sh
# 可用环境变量覆盖签名信息：
#   KEYSTORE / KEYSTORE_PASS / KEY_ALIAS / KEY_PASS

cd "$(dirname "$0")/.."

KEYSTORE="${KEYSTORE:-release/terminalide.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-terminalide}"
KEY_ALIAS="${KEY_ALIAS:-terminalide}"
KEY_PASS="${KEY_PASS:-terminalide}"
DAYS=10000

# 1) 确保 Gradle Wrapper 可用（缺失时用本机 gradle 生成）
if [ ! -f "gradlew" ] || [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "[1/4] 生成 Gradle Wrapper ..."
    command -v gradle >/dev/null 2>&1 || { echo "错误：未找到 gradle，请先安装 Gradle 或用 Android Studio 打开本项目生成 wrapper"; exit 1; }
    gradle wrapper --gradle-version 8.11.1
fi

# 2) 编译 release（无签名）
echo "[2/4] gradle assembleRelease ..."
./gradlew assembleRelease

UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
[ -f "$UNSIGNED" ] || { echo "错误：未找到 $UNSIGNED"; exit 1; }

# 3) 生成签名 keystore（不存在时）
mkdir -p release
if [ ! -f "$KEYSTORE" ]; then
    echo "[3/4] 生成签名 keystore ..."
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 2048 -validity "$DAYS" \
        -storepass "$KEYSTORE_PASS" -keypass "$KEY_PASS" \
        -dname "CN=TerminalIDE, OU=Dev, O=TerminalIDE, C=CN"
fi

# 4) zipalign + apksigner 签名
echo "[4/4] zipalign + apksigner ..."
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
BUILD_TOOLS="$(find "$SDK/build-tools" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1)"
[ -n "$BUILD_TOOLS" ] || { echo "错误：未找到 Android SDK build-tools（$SDK/build-tools）"; exit 1; }

ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
ALIGNED="app/build/outputs/apk/release/app-release-aligned.apk"
OUT="release/TerminalIDE-arm64-v8a-release.apk"

"$ZIPALIGN" -f -p 4 "$UNSIGNED" "$ALIGNED"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$KEYSTORE_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --out "$OUT" \
    "$ALIGNED"

echo "✅ 完成：$OUT"
echo "安装：adb install -r $OUT"
