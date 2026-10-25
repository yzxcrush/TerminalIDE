#!/usr/bin/env bash
set -euo pipefail

# 下载「运行在 Android/aarch64 上」的 LLVM/Clang 工具链，打进 APK 的 assets。
#
# 背景：官方 NDK 的 clang 运行在宿主机（x86_64 等），不能在设备上直接执行。
# 要让「设备端」用 clang/clang++ 编译 C/C++（交叉编译 aarch64-linux-android 目标），
# 需要一个针对 Android aarch64 的 clang 发行包。请通过环境变量指定其下载地址。
#
# 用法：
#   TOOLCHAIN_URL="https://example.com/clang-aarch64-android.tar.xz" ./scripts/fetch_toolchain.sh
#
# 归档解压后应包含 bin/（内有 aarch64-linux-android24-clang 与 -clang++，
# 或 clang / clang++ 别名）以及可选的 sysroot/。
# 放置到 app/src/main/assets/toolchain 后，构建时打进 APK，运行期由 App 解压到 files/toolchain。

cd "$(dirname "$0")/.."

URL="${TOOLCHAIN_URL:-}"
DEST="app/src/main/assets/toolchain"

if [ -z "$URL" ]; then
    echo "用法: TOOLCHAIN_URL=<clang-aarch64-android 归档下载地址> ./scripts/fetch_toolchain.sh"
    echo ""
    echo "说明：把含 bin/（与可选 sysroot/）的 clang 工具链解压到 $DEST，"
    echo "构建时打进 APK，运行期由 App 解压到 files/toolchain 并赋予执行权限。"
    exit 1
fi

mkdir -p "$DEST"
echo "下载: $URL"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl -L --fail "$URL" -o "$TMP/toolchain.tar.xz"

echo "解压到 $DEST ..."
tar -xf "$TMP/toolchain.tar.xz" -C "$TMP"

# 找到含 bin/ 的目录，拷贝其内容到 assets/toolchain
SRC="$(dirname "$(find "$TMP" -type d -name bin | head -1)")"
[ -n "$SRC" ] && [ "$SRC" != "." ] || { echo "错误：归档中未找到 bin/ 目录"; exit 1; }

cp -r "$SRC"/. "$DEST"/
chmod +x "$DEST"/bin/* 2>/dev/null || true

echo "✅ 工具链已放置到 $DEST"
echo "   请重新运行 scripts/build_release.sh 打包。"
