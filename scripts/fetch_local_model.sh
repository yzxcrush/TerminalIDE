#!/usr/bin/env bash
set -euo pipefail

# 端侧小模型下载助手。
#
# 模型「不打包进 APK」，运行期可选下载（App 内「设置 → 端侧模型 → 下载」即可）。
# 本脚本用于在宿主机预先下载一个 GGUF 模型到 models/ 目录，方便调试或离线分发。
#
# 用法：
#   MODEL_URL="https://example.com/model.gguf" ./scripts/fetch_local_model.sh

cd "$(dirname "$0")/.."

URL="${MODEL_URL:-}"
DEST="${MODEL_DEST:-models}"

if [ -z "$URL" ]; then
    echo "用法: MODEL_URL=<gguf 模型下载地址> ./scripts/fetch_local_model.sh"
    echo "说明：端侧模型不打包进 APK，运行期可选下载。此脚本仅用于宿主机预下载。"
    exit 1
fi

mkdir -p "$DEST"
FILENAME="$(basename "$URL" | cut -d'?' -f1)"
echo "下载: $URL -> $DEST/$FILENAME"
curl -L --fail "$URL" -o "$DEST/$FILENAME"
echo "✅ 模型已保存到 $DEST/$FILENAME"
echo "   推理运行时（如 llama-cli，aarch64）也需放到设备 files/models 并 chmod +x。"
