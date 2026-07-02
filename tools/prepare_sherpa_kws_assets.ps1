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

# Avoid PowerShell 5.1 source-codepage issues by constructing all non-ASCII
# characters from Unicode code points, then writing UTF-8 bytes explicitly.
$a3 = [char]0x01CE # ǎ
$i1 = [char]0x012B # ī
$i2 = [char]0x00ED # í
$i3 = [char]0x01D0 # ǐ
$i4 = [char]0x00EC # ì
$o2 = [char]0x00F3 # ó
$e2 = [char]0x00E9 # é
$xiao = [string]([char]0x5C0F)
$zhi = [string]([char]0x667A)
$zhiKnow = [string]([char]0x77E5)
$tong = [string]([char]0x540C)
$xue = [string]([char]0x5B66)
$kwXiaoZhi = "$xiao$zhi"
$kwXiaoZhi2 = "$xiao$zhi$xiao$zhi"
$kwXiaoZhiClassmate = "$xiao$zhi$tong$xue"

$keywordLines = @(
    "x i$a3o zh $i4 @$kwXiaoZhi",
    "x i$a3o zh $i1 @$kwXiaoZhi",
    "x i$a3o zh $i2 @$kwXiaoZhi",
    "x i$a3o zh $i3 @$kwXiaoZhi",
    "x i$a3o z $i4 @$kwXiaoZhi",
    "x i$a3o z $i1 @$kwXiaoZhi",
    "x i$a3o zh $i4 x i$a3o zh $i4 @$kwXiaoZhi2",
    "x i$a3o zh $i4 x i$a3o zh $i1 @$kwXiaoZhi2",
    "x i$a3o zh $i1 x i$a3o zh $i1 @$kwXiaoZhi2",
    "x i$a3o z $i4 x i$a3o z $i4 @$kwXiaoZhi2",
    "x i$a3o zh $i4 t $o2ng x u$e2 @$kwXiaoZhiClassmate",
    "x i$a3o zh $i1 t $o2ng x u$e2 @$kwXiaoZhiClassmate",
    "x i$a3o z $i4 t $o2ng x u$e2 @$kwXiaoZhiClassmate"
)
$keywordFile = Join-Path $AssetDir "keywords_xiaozhi.txt"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($keywordFile, $keywordLines, $utf8NoBom)

if (Test-Path $OldAssetDir) {
    Write-Host "Removing old 2024 KWS assets to avoid packaging both models"
    Remove-Item -Recurse -Force $OldAssetDir
}

Write-Host "[4/4] Native JNI library"
Write-Host "2025 model assets are ready at: $AssetDir"
Write-Host "You still need sherpa-onnx native .so files under app/src/main/jniLibs/<ABI>/ before running."
Write-Host "For Pixel / most modern phones, arm64-v8a is the important ABI."
