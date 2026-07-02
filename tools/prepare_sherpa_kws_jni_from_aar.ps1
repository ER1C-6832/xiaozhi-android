$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$Version = "1.13.3"
$TmpDir = Join-Path $Root ".tmp_sherpa_aar"
$Aar = Join-Path $TmpDir "sherpa-onnx-$Version.aar"
$Zip = Join-Path $TmpDir "sherpa-onnx-$Version.zip"
$Unzip = Join-Path $TmpDir "aar"
$JniDst = Join-Path $Root "app/src/main/jniLibs"
$Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$Version/sherpa-onnx-$Version.aar"

Write-Host "[1/3] Download sherpa-onnx Android AAR"
New-Item -ItemType Directory -Force $TmpDir | Out-Null
curl.exe -L --fail --retry 3 --retry-delay 2 -o $Aar $Url

Write-Host "[2/3] Extract AAR"
Remove-Item $Unzip -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item $Aar $Zip -Force
Expand-Archive -Path $Zip -DestinationPath $Unzip -Force

Write-Host "[3/3] Copy native libraries"
$JniSrc = Join-Path $Unzip "jni"
if (!(Test-Path $JniSrc)) {
    throw "AAR does not contain jni directory: $JniSrc"
}
Get-ChildItem $JniSrc -Directory | ForEach-Object {
    $abi = $_.Name
    $target = Join-Path $JniDst $abi
    New-Item -ItemType Directory -Force $target | Out-Null
    Copy-Item "$($_.FullName)\*.so" $target -Force
    Write-Host "Copied native libs for $abi"
}

Write-Host "JNI libraries are ready under: $JniDst"
