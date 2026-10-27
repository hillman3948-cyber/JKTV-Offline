param([string]$Token = $env:GH_TOKEN)
$ErrorActionPreference = 'Stop'
$releaseRoot = Join-Path $PSScriptRoot 'GitHub-v0.4.4-release-copy'
$upload = Join-Path $releaseRoot 'upload'
$apk = Join-Path $upload 'JKTV-v0.4.4.apk'
$sha = "$apk.sha256"
$json = Join-Path $upload 'app-version.json'
$notes = Join-Path $releaseRoot 'release_notes_v0.4.4.md'
foreach ($file in @($apk, $sha, $json, $notes)) {
    if (!(Test-Path -LiteralPath $file)) { throw "Missing $file. Finish original-key signing with Finalize-Release.ps1 first." }
}
$metadata = Get-Content -LiteralPath $json -Raw | ConvertFrom-Json
$actual = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($metadata.packageName -ne 'com.jktv.offline' -or $metadata.versionName -ne '0.4.4' -or $metadata.versionCode -ne 20 -or $metadata.sha256 -ne $actual) {
    throw 'APK metadata is incompatible or its checksum is stale. Run Finalize-Release.ps1 again.'
}
if ($Token) { $env:GH_TOKEN = $Token }
$gh = (Get-Command gh -ErrorAction Stop).Source
# The user must push the reviewed source tag first. Do not create a tag on old source.
& $gh release create app-v0.4.4 $apk $sha $json --repo hillman3948-cyber/JKTV-Offline --verify-tag --title 'JKTV Offline Karaoke v0.4.4' --notes-file $notes
if ($LASTEXITCODE) { throw 'GitHub publication failed. No existing release assets were overwritten.' }
