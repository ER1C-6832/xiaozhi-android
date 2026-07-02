$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ModelName = "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
$OldModelName = "sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
$ModelUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/$ModelName.tar.bz2"
$AssetDir = Join-Path $Root "app/src/main/assets/$ModelName"
$OldAssetDir = Join-Path $Root "app/src/main/assets/$OldModelName"
$TmpDir = Join-Path $Root ".tmp_sherpa_kws_2025"
$Archive = Join-Path $TmpDir "$ModelName.tar.bz2"

Write-Host "[1/4] Download 2025 zh-en KWS model"
if (Test-Path $TmpDir) { Remove-Item -Recurse -Force $TmpDir }
New-Item -ItemType Directory -Force $TmpDir | Out-Null
New-Item -ItemType Directory -Force $AssetDir | Out-Null

curl.exe -L --fail --retry 3 --retry-delay 2 -o $Archive $ModelUrl

Write-Host "[2/4] Extract model"
# GNU tar on Windows treats C:\... as a remote host unless --force-local is used.
tar --force-local -xjf $Archive -C $TmpDir
$ModelDir = Join-Path $TmpDir $ModelName

Write-Host "[3/4] Copy runtime files"
Copy-Item (Join-Path $ModelDir "encoder-epoch-13-avg-2-chunk-16-left-64.onnx") $AssetDir -Force
Copy-Item (Join-Path $ModelDir "decoder-epoch-13-avg-2-chunk-16-left-64.onnx") $AssetDir -Force
Copy-Item (Join-Path $ModelDir "joiner-epoch-13-avg-2-chunk-16-left-64.onnx") $AssetDir -Force
Copy-Item (Join-Path $ModelDir "tokens.txt") $AssetDir -Force

# 2025 model does not ship a top-level keywords.txt for XiaoZhi. Generate the
# dedicated phone+ppinyin keyword list used by the Android demo.
@"
x iǎo zh ì @小智
x iǎo zh ī @小知
x iǎo zh ì t óng x ué @小智同学
"@ | Set-Content -Encoding UTF8 (Join-Path $AssetDir "keywords_xiaozhi.txt")

if (Test-Path $OldAssetDir) {
    Write-Host "Removing old 2024 KWS assets to avoid packaging both models"
    Remove-Item -Recurse -Force $OldAssetDir
}

Write-Host "[4/4] Native JNI library"
Write-Host "2025 model assets are ready at: $AssetDir"
Write-Host "You still need sherpa-onnx native .so files under app/src/main/jniLibs/<ABI>/ before running."
Write-Host "For Pixel / most modern phones, arm64-v8a is the important ABI."
