[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9.-]+)?$')]
    [string] $Version,

    [string] $SourceRoot = 'C:\Users\hillm\OneDrive\Desktop\JKT Offiline',
    [string] $OutputRoot = (Join-Path $PSScriptRoot '..\build\assets'),
    [string] $RepoOwner,
    [string] $Repository = 'JKTV-Offline',
    [string] $SoundFontFilename = 'KaraOke_King_v2.sf2',
    [string] $PreviousManifestPath,
    [string] $PreviousIndexPath,
    [long] $MaxPackageBytes = 128MB,
    [switch] $Publish,
    [switch] $IConfirmRedistributionRights
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-Sha256([string] $Path) {
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-MidiFile([System.IO.FileInfo] $File) {
    if ($File.Length -lt 14) { throw "MIDI file is too short: $($File.FullName)" }
    $stream = [System.IO.File]::OpenRead($File.FullName)
    try {
        $header = [byte[]]::new(12)
        $read = $stream.Read($header, 0, $header.Length)
        $isMidi = $read -ge 4 -and [Text.Encoding]::ASCII.GetString($header, 0, 4) -eq 'MThd'
        $isRmid = $read -ge 12 -and [Text.Encoding]::ASCII.GetString($header, 0, 4) -eq 'RIFF' -and [Text.Encoding]::ASCII.GetString($header, 8, 4) -eq 'RMID'
        if (-not ($isMidi -or $isRmid)) { throw "Invalid MIDI/KAR header: $($File.FullName)" }
    } finally { $stream.Dispose() }
}

function Assert-SoundFont([System.IO.FileInfo] $File) {
    if ($File.Length -lt 12) { throw "SoundFont file is too short: $($File.FullName)" }
    $stream = [System.IO.File]::OpenRead($File.FullName)
    try {
        $header = [byte[]]::new(12)
        if ($stream.Read($header, 0, 12) -ne 12 -or
            [Text.Encoding]::ASCII.GetString($header, 0, 4) -ne 'RIFF' -or
            [Text.Encoding]::ASCII.GetString($header, 8, 4) -ne 'sfbk') {
            throw "Invalid SF2 SoundFont header: $($File.FullName)"
        }
    } finally { $stream.Dispose() }
}

function New-SongPackage([System.IO.FileInfo[]] $Files, [string] $SongsRoot, [string] $Destination) {
    $fileStream = [System.IO.File]::Create($Destination)
    try {
        $archive = [System.IO.Compression.ZipArchive]::new($fileStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
        try {
            foreach ($file in $Files) {
                $relative = $file.FullName.Substring($SongsRoot.Length).TrimStart('\').Replace('\', '/')
                $entry = $archive.CreateEntry($relative, [System.IO.Compression.CompressionLevel]::Optimal)
                $entry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
                $input = [System.IO.File]::OpenRead($file.FullName)
                $output = $entry.Open()
                try { $input.CopyTo($output) } finally { $output.Dispose(); $input.Dispose() }
            }
        } finally { $archive.Dispose() }
    } finally { $fileStream.Dispose() }
}

$repoSlug = if ($RepoOwner) { "$RepoOwner/$Repository" } else { '' }
$songsRoot = Join-Path $SourceRoot 'songs'
$soundFontRoot = Join-Path $SourceRoot 'SoundFont'
if (-not (Test-Path -LiteralPath $songsRoot -PathType Container)) { throw "Song directory not found: $songsRoot" }
if (-not (Test-Path -LiteralPath $soundFontRoot -PathType Container)) { throw "SoundFont directory not found: $soundFontRoot" }
if ($MaxPackageBytes -lt 1MB -or $MaxPackageBytes -ge 2GB) { throw "MaxPackageBytes must be at least 1 MiB and below GitHub Releases' per-file limit of 2 GiB." }

$songs = @(Get-ChildItem -LiteralPath $songsRoot -File -Recurse | Where-Object { $_.Extension -match '^\.(mid|midi|kar)$' } | Sort-Object FullName)
if ($songs.Count -eq 0) { throw "No MID, MIDI, or KAR files found under $songsRoot" }
$fonts = @(Get-ChildItem -LiteralPath $soundFontRoot -File | Where-Object { $_.Extension -ieq '.sf2' } | Sort-Object Name)
if ($fonts.Count -eq 0) { throw "No SF2 SoundFonts found under $soundFontRoot" }
if (-not ($fonts.Name -contains $SoundFontFilename)) { throw "Default SoundFont not found: $(Join-Path $soundFontRoot $SoundFontFilename)" }
foreach ($song in $songs) { Assert-MidiFile $song }
foreach ($font in $fonts) { Assert-SoundFont $font }

$previous = $null
if ($PreviousManifestPath) {
    if (-not (Test-Path -LiteralPath $PreviousManifestPath -PathType Leaf)) { throw "Previous manifest not found: $PreviousManifestPath" }
    $previous = Get-Content -LiteralPath $PreviousManifestPath -Raw | ConvertFrom-Json
}
$previousIndex = @{}
if ($PreviousIndexPath) {
    if (-not (Test-Path -LiteralPath $PreviousIndexPath -PathType Leaf)) { throw "Previous source index not found: $PreviousIndexPath" }
    $oldIndex = Get-Content -LiteralPath $PreviousIndexPath -Raw | ConvertFrom-Json
    foreach ($entry in @($oldIndex.sourceFiles)) { $previousIndex[[string]$entry.path] = $entry }
}

$releaseDirectory = Join-Path $OutputRoot "library-v$Version"
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
$indexRows = [System.Collections.Generic.List[object]]::new()
$changedSongs = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
foreach ($song in $songs) {
    $relative = $song.FullName.Substring($songsRoot.Length).TrimStart('\').Replace('\', '/')
    $sha = Get-Sha256 $song.FullName
    $songIdBytes = [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($relative.ToLowerInvariant()))
    $songId = [Convert]::ToHexString($songIdBytes).ToLowerInvariant()
    $row = [ordered]@{ path = $relative; songId = $songId; sizeBytes = $song.Length; sha256 = $sha }
    $indexRows.Add($row)
    if (-not $previousIndex.ContainsKey($relative) -or $previousIndex[$relative].sha256 -ne $sha) { $changedSongs.Add($song) }
}
$currentPaths = @{}; foreach ($row in $indexRows) { $currentPaths[$row.path] = $true }
$deletedFiles = [System.Collections.Generic.List[string]]::new()
if ($previous) { foreach ($path in @($previous.deletedFiles)) { if (-not $currentPaths.ContainsKey([string]$path)) { $deletedFiles.Add([string]$path) } } }
foreach ($path in $previousIndex.Keys) { if (-not $currentPaths.ContainsKey($path) -and -not $deletedFiles.Contains($path)) { $deletedFiles.Add($path) } }

$chunks = [System.Collections.Generic.List[object]]::new()
$chunk = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
$chunkBytes = 0L
foreach ($song in $changedSongs) {
    if ($chunk.Count -gt 0 -and $chunkBytes + $song.Length -gt $MaxPackageBytes) {
        $chunks.Add($chunk.ToArray()); $chunk = [System.Collections.Generic.List[System.IO.FileInfo]]::new(); $chunkBytes = 0L
    }
    $chunk.Add($song); $chunkBytes += $song.Length
}
if ($chunk.Count -gt 0) { $chunks.Add($chunk.ToArray()) }

$releaseTag = "library-v$Version"
$assetBase = if ($repoSlug) { "https://github.com/$repoSlug/releases/download/$releaseTag" } else { "https://github.com/OWNER/JKTV-Offline/releases/download/$releaseTag" }
$songPackages = [System.Collections.Generic.List[object]]::new()
if ($previous) { foreach ($item in @($previous.songPackages)) { $songPackages.Add($item) } }
$uploadPaths = [System.Collections.Generic.List[string]]::new()
for ($i = 0; $i -lt $chunks.Count; $i++) {
    $name = 'songs-v{0}-part-{1:D3}.zip' -f $Version, ($i + 1)
    $destination = Join-Path $releaseDirectory $name
    New-SongPackage -Files $chunks[$i] -SongsRoot $songsRoot -Destination $destination
    $record = [ordered]@{
        id = [IO.Path]::GetFileNameWithoutExtension($name)
        filename = $name
        url = "$assetBase/$name"
        sizeBytes = (Get-Item -LiteralPath $destination).Length
        sha256 = Get-Sha256 $destination
        expandedSizeBytes = ($chunks[$i] | Measure-Object -Property Length -Sum).Sum
        songCount = $chunks[$i].Count
    }
    if ($record.sizeBytes -ge 2GB) { throw "$name exceeds GitHub's 2 GiB per-asset limit." }
    $songPackages.Add($record); $uploadPaths.Add($destination)
}

$soundFonts = [System.Collections.Generic.List[object]]::new()
foreach ($font in $fonts) {
    $fontSha = Get-Sha256 $font.FullName
    $fontStem = [IO.Path]::GetFileNameWithoutExtension($font.Name).ToLowerInvariant() -replace '[^a-z0-9]+', '-'
    $fontId = if ($font.Name -eq $SoundFontFilename) { 'default-gm' } else { "soundfont-$($fontStem.Trim('-'))" }
    $oldFont = if ($previous) { @($previous.soundfonts) | Where-Object { $_.id -eq $fontId } | Select-Object -First 1 } else { $null }
    if ($oldFont -and $oldFont.sha256 -eq $fontSha) { $soundFonts.Add($oldFont); continue }
    $fontName = "$fontId-v$Version.sf2"
    $fontCopy = Join-Path $releaseDirectory $fontName
    Copy-Item -LiteralPath $font.FullName -Destination $fontCopy -Force
    $soundFonts.Add([ordered]@{ id = $fontId; filename = $fontName; url = "$assetBase/$fontName"; sizeBytes = (Get-Item -LiteralPath $fontCopy).Length; sha256 = $fontSha })
    $uploadPaths.Add($fontCopy)
}

$manifest = [ordered]@{
    formatVersion = 1
    app = 'JKTV Karaoke'
    libraryVersion = $Version
    minimumAppVersion = '0.1.0'
    generatedUtc = [DateTime]::UtcNow.ToString('o')
    songCount = $songs.Count
    soundfonts = @($soundFonts.ToArray())
    songPackages = @($songPackages.ToArray())
    deletedFiles = @($deletedFiles | Sort-Object -Unique)
}
$manifestPath = Join-Path $releaseDirectory 'library-manifest.json'
$manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
$uploadPaths.Add($manifestPath)
$indexPath = Join-Path $releaseDirectory 'source-index.json'
([ordered]@{ libraryVersion = $Version; sourceFiles = @($indexRows.ToArray()) } | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $indexPath -Encoding UTF8

Write-Output "Scanned $($songs.Count) MID/MIDI/KAR files. Changed or new songs: $($changedSongs.Count). Removed paths: $($deletedFiles.Count)."
Write-Output "Created $($chunks.Count) song archive(s) and included $($soundFonts.Count) SF2 SoundFonts: $(($fonts.Name) -join ', ')."
Write-Output "Local output: $releaseDirectory"
Write-Output "Manifest URL after publishing: https://github.com/$(if($repoSlug){$repoSlug}else{'OWNER/JKTV-Offline'})/releases/latest/download/library-manifest.json"

if ($Publish) {
    if (-not $IConfirmRedistributionRights) { throw 'Publishing requires -IConfirmRedistributionRights to confirm you may redistribute these assets.' }
    if (-not $RepoOwner) { throw 'Publishing requires -RepoOwner (the public GitHub account that owns JKTV-Offline).' }
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'GitHub CLI (gh) is not installed.' }
    gh auth status --hostname github.com 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Sign in to GitHub CLI first with gh auth login.' }
    gh repo view $repoSlug --json nameWithOwner | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "GitHub repository $repoSlug was not found or is not accessible." }
    $visibility = gh repo view $repoSlug --json visibility --jq .visibility
    if ($LASTEXITCODE -ne 0) { throw "Could not determine the visibility of $repoSlug." }
    if ($visibility.Trim() -ne 'PUBLIC') { throw 'The JKTV-Offline repository must be public so phones can download release assets without embedded credentials.' }
    gh release view $releaseTag --repo $repoSlug *> $null
    if ($LASTEXITCODE -eq 0) { throw "Release $releaseTag already exists; choose a new library version." }
    if ($PSCmdlet.ShouldProcess("$repoSlug $releaseTag", 'Publish the library manifest and new assets as a GitHub Release')) {
        $arguments = @('release', 'create', $releaseTag) + @($uploadPaths.ToArray()) + @('--repo', $repoSlug, '--title', "JKTV Library $Version", '--notes', "Incremental JKTV offline library update. Contains $($changedSongs.Count) new or updated songs and the versioned manifest.")
        & gh @arguments
        if ($LASTEXITCODE -ne 0) { throw "GitHub release upload failed for $releaseTag." }
        Write-Output "Published $releaseTag to https://github.com/$repoSlug/releases/tag/$releaseTag"
    }
}
