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
if (!(Test-Path $Archive)) {
    throw "KWS model archive was not downloaded: $Archive"
}

Write-Host "[2/4] Extract model"
# GNU tar on Windows treats C:\... as a remote host unless --force-local is used.
tar --force-local -xjf $Archive -C $TmpDir
$ModelDir = Join-Path $TmpDir $ModelName
if (!(Test-Path $ModelDir)) {
    throw "KWS model directory was not extracted: $ModelDir"
}

Write-Host "[3/4] Copy runtime files"
Copy-Item (Join-Path $ModelDir "encoder-epoch-13-avg-2-chunk-16-left-64.onnx") $AssetDir -Force
Copy-Item (Join-Path $ModelDir "decoder-epoch-13-avg-2-chunk-16-left-64.onnx") $AssetDir -Force
Copy-Item (Join-Path $ModelDir "joiner-epoch-13-avg-2-chunk-16-left-64.onnx") $AssetDir -Force
Copy-Item (Join-Path $ModelDir "tokens.txt") $AssetDir -Force

# Windows PowerShell 5.1 can parse non-BOM UTF-8 .ps1 files as the system ANSI
# code page, which corrupts non-ASCII pinyin/Chinese text. Build the keyword
# lines from Unicode code points so this script stays ASCII-safe.
$kw1 = "x i$([char]0x01CE)o zh $([char]0x00EC) @$([char]0x5C0F)$([char]0x667A)"
$kw2 = "x i$([char]0x01CE)o zh $([char]0x012B) @$([char]0x5C0F)$([char]0x77E5)"
$kw3 = "x i$([char]0x01CE)o zh $([char]0x00EC) t $([char]0x00F3)ng x u$([char]0x00E9) @$([char]0x5C0F)$([char]0x667A)$([char]0x540C)$([char]0x5B66)"
$keywordFile = Join-Path $AssetDir "keywords_xiaozhi.txt"
[System.IO.File]::WriteAllLines($keywordFile, @($kw1, $kw2, $kw3), [System.Text.UTF8Encoding]::new($false))

if (Test-Path $OldAssetDir) {
    Write-Host "Removing old 2024 KWS assets to avoid packaging both models"
    Remove-Item -Recurse -Force $OldAssetDir
}

Write-Host "[4/4] Native JNI library"
Write-Host "2025 model assets are ready at: $AssetDir"
Write-Host "You still need sherpa-onnx native .so files under app/src/main/jniLibs/<ABI>/ before running."
Write-Host "For Pixel / most modern phones, arm64-v8a is the important ABI."
