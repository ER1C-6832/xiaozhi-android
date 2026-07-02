#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_NAME="sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
OLD_MODEL_NAME="sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/${MODEL_NAME}.tar.bz2"
ASSET_DIR="$ROOT_DIR/app/src/main/assets/$MODEL_NAME"
OLD_ASSET_DIR="$ROOT_DIR/app/src/main/assets/$OLD_MODEL_NAME"
TMP_DIR="$ROOT_DIR/.tmp_sherpa_kws_2025"

printf '[1/4] Download 2025 zh-en KWS model\n'
rm -rf "$TMP_DIR"
mkdir -p "$TMP_DIR" "$ASSET_DIR"
cd "$TMP_DIR"
curl -L --fail --retry 3 --retry-delay 2 -o "${MODEL_NAME}.tar.bz2" "$MODEL_URL"

printf '[2/4] Extract model\n'
tar jxvf "${MODEL_NAME}.tar.bz2"

printf '[3/4] Copy runtime files\n'
cp -v "$MODEL_NAME"/encoder-epoch-13-avg-2-chunk-16-left-64.onnx "$ASSET_DIR/"
cp -v "$MODEL_NAME"/decoder-epoch-13-avg-2-chunk-16-left-64.onnx "$ASSET_DIR/"
cp -v "$MODEL_NAME"/joiner-epoch-13-avg-2-chunk-16-left-64.onnx "$ASSET_DIR/"
cp -v "$MODEL_NAME"/tokens.txt "$ASSET_DIR/"
cat > "$ASSET_DIR/keywords_xiaozhi.txt" <<'KEYWORDS'
x iǎo zh ì @小智
x iǎo zh ī @小知
x iǎo zh ì t óng x ué @小智同学
KEYWORDS

if [ -d "$OLD_ASSET_DIR" ]; then
  echo "Removing old 2024 KWS assets to avoid packaging both models"
  rm -rf "$OLD_ASSET_DIR"
fi

printf '[4/4] Native JNI library\n'
cat <<MSG
2025 model assets are ready at:
  $ASSET_DIR

You still need sherpa-onnx native .so files under app/src/main/jniLibs/<ABI>/ before running.
For Pixel / most modern phones, arm64-v8a is the important ABI.
MSG
