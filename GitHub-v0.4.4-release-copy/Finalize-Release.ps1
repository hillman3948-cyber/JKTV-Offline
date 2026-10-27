[CmdletBinding(DefaultParameterSetName='Sign')]
param(
    [Parameter(Mandatory, ParameterSetName='Sign')][string]$KeystorePath,
    [Parameter(Mandatory, ParameterSetName='Sign')][string]$KeyAlias,
    [Parameter(Mandatory, ParameterSetName='Verify')][string]$SignedApkPath,
    [string]$AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$tools = Join-Path $AndroidSdk 'build-tools\35.0.0'
$signer = Join-Path $tools 'apksigner.bat'
$align = Join-Path $tools 'zipalign.exe'
$aapt = Join-Path $tools 'aapt.exe'
foreach ($tool in @($signer, $align, $aapt)) { if (!(Test-Path -LiteralPath $tool)) { throw "Missing SDK tool: $tool" } }
$expectedCertificate = 'bf9e3568126ed487c039b3708c094b42ffa8b302fd4caafb255e30fabaa3dd33'
if ($PSCmdlet.ParameterSetName -eq 'Sign') {
    if (!$env:ANDROID_KEYSTORE_PASSWORD -or !$env:ANDROID_KEY_PASSWORD) {
        throw 'Set ANDROID_KEYSTORE_PASSWORD and ANDROID_KEY_PASSWORD locally; do not put them in the script.'
    }
    $unsigned = Join-Path $PSScriptRoot 'JKTV-v0.4.4-unsigned.apk'
    $aligned = Join-Path $PSScriptRoot 'JKTV-v0.4.4-aligned.apk'
    $SignedApkPath = Join-Path $PSScriptRoot 'JKTV-v0.4.4-signed-candidate.apk'
    & $align -f -p 4 $unsigned $aligned
    if ($LASTEXITCODE) { throw 'APK alignment failed.' }
    & $signer sign --ks $KeystorePath --ks-key-alias $KeyAlias --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEY_PASSWORD --out $SignedApkPath $aligned
    if ($LASTEXITCODE) { throw 'APK signing failed.' }
}
$certificates = & $signer verify --print-certs $SignedApkPath 2>&1
if ($LASTEXITCODE -or (($certificates -join "`n") -notmatch $expectedCertificate)) {
    throw 'Certificate does not match the existing GitHub release. No upload metadata was created. Use the original signing key.'
}
$badging = & $aapt dump badging $SignedApkPath
if ($LASTEXITCODE -or $badging[0] -notmatch "name='com.jktv.offline'.*versionCode='20'.*versionName='0.4.4'") {
    throw 'Expected package com.jktv.offline, version 0.4.4, build 20.'
}
$upload = Join-Path $PSScriptRoot 'upload'
New-Item -ItemType Directory -Path $upload -Force | Out-Null
$destination = Join-Path $upload 'JKTV-v0.4.4.apk'
if ([IO.Path]::GetFullPath($SignedApkPath) -ne [IO.Path]::GetFullPath($destination)) {
    Copy-Item -LiteralPath $SignedApkPath -Destination $destination
}
$hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$destination.sha256" -Value "$hash  JKTV-v0.4.4.apk" -Encoding ascii
$metadata = [ordered]@{packageName='com.jktv.offline';versionName='0.4.4';versionCode=20;apk='JKTV-v0.4.4.apk';sha256=$hash;sizeBytes=(Get-Item -LiteralPath $destination).Length}
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $upload 'app-version.json') -Encoding utf8
Write-Output "Verified release files are ready in $upload"
