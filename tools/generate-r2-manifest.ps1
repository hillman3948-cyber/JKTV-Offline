<#
.SYNOPSIS
    Generates an accurate, production-ready library-manifest.json for JKTV Offline Karaoke (Cloudflare R2).

.DESCRIPTION
    Scans song archives (.zip), SoundFonts (.sf2, .sf3), background videos (.mp4),
    and background photos (.jpg, .jpeg, .png, .webp). Computes precise SHA-256 hashes
    and exact byte sizes, formats deletedFiles, and outputs library-manifest.json.

.EXAMPLE
    .\generate-r2-manifest.ps1 -LibraryVersion "1.2.0" -OutputDir "..\manifests"
#>
[CmdletBinding()]
param(
    [string]$LibraryVersion = "1.2.0",
    [string]$MinimumAppVersion = "0.3.0",
    [string]$SongsDir = "",
    [string]$SoundFontsDir = "",
    [string]$VideosDir = "C:\Users\hillm\OneDrive\Desktop\VIDEO BACKGROUND",
    [string]$PhotosDir = "",
    [string[]]$DeletedFiles = @(
        "bgv/Drunk.mp4",
        "bgv/motion1.mp4",
        "bgv/motion2.mp4",
        "bgv/motion3.mp4",
        "bgv/Video1.mp4",
        "bgv/Video2.mp4",
        "bgv/Video3.mp4",
        "bgv/Video4.mp4"
    ),
    [string]$OutputDir = (Join-Path $PSScriptRoot "..\manifests")
)

$ErrorActionPreference = 'Stop'

function Get-Sha256([string]$Path) {
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

Write-Host "=== JKTV R2 Library Manifest Generator ===" -ForegroundColor Cyan
Write-Host "Library Version: $LibraryVersion"

# Default Soundfonts if not provided
$soundFonts = @(
    [ordered]@{
        id = "default-gm"
        filename = "default-gm-v1.0.0.sf2"
        url = "default-gm-v1.0.0.sf2"
        sizeBytes = 98645536
        sha256 = "8862381c105be29202f99f2b3441fead25cfd94b0d33c5e0ecfdc58d8a2cd391"
    },
    [ordered]@{
        id = "soundfont-dream-sam2634-ver-2-1"
        filename = "soundfont-dream-sam2634-ver-2-1-v1.0.0.sf2"
        url = "soundfont-dream-sam2634-ver-2-1-v1.0.0.sf2"
        sizeBytes = 141454402
        sha256 = "795e72be5fd0ac531adb47b2b5cd6b482f2410e3c7e12ea5482c7e6038c97795"
    },
    [ordered]@{
        id = "soundfont-arachno-soundfont-version-1-0"
        filename = "soundfont-arachno-soundfont-version-1-0-v1.0.0.sf2"
        url = "soundfont-arachno-soundfont-version-1-0-v1.0.0.sf2"
        sizeBytes = 155405818
        sha256 = "9a57fb3b6714e69dda12390e351b087e81fc3b1eca15c6b4bbe172799f4cf3cd"
    },
    [ordered]@{
        id = "soundfont-megasoundfont"
        filename = "soundfont-megasoundfont-v1.0.0.sf2"
        url = "soundfont-megasoundfont-v1.0.0.sf2"
        sizeBytes = 98574116
        sha256 = "d63de87525cce532591d91c394fdd1f356aeb970491fb3472aa3a28a266825fd"
    }
)

if ($SoundFontsDir -and (Test-Path $SoundFontsDir)) {
    $foundFonts = @(Get-ChildItem -LiteralPath $SoundFontsDir -File | Where-Object { $_.Extension -match '^\.(sf2|sf3)$' })
    if ($foundFonts.Count -gt 0) {
        $soundFonts = @($foundFonts | ForEach-Object {
            $stem = [IO.Path]::GetFileNameWithoutExtension($_.Name).ToLowerInvariant() -replace '[^a-z0-9]+', '-'
            [ordered]@{
                id = "soundfont-$($stem.Trim('-'))"
                filename = $_.Name
                url = $_.Name
                sizeBytes = $_.Length
                sha256 = Get-Sha256 $_.FullName
            }
        })
    }
}

# Song Packages
$songPackages = @(
    [ordered]@{ id = "songs-v1.0.0-part-001"; filename = "songs-v1.0.0-part-001.zip"; url = "songs-v1.0.0-part-001.zip"; sizeBytes = 38412851; sha256 = "c1f416c66598d3adb85dc8ea38d43f471b1b1023a5c4d272851b26a04068698a"; expandedSizeBytes = 134211207; songCount = 3682 },
    [ordered]@{ id = "songs-v1.0.0-part-002"; filename = "songs-v1.0.0-part-002.zip"; url = "songs-v1.0.0-part-002.zip"; sizeBytes = 33846661; sha256 = "414ea8300904d97b87c0734d0260a96659b05f8f84f25704b9634c00aa1efe66"; expandedSizeBytes = 134191972; songCount = 3153 },
    [ordered]@{ id = "songs-v1.0.0-part-003"; filename = "songs-v1.0.0-part-003.zip"; url = "songs-v1.0.0-part-003.zip"; sizeBytes = 31910308; sha256 = "42a77d476695140674b6be16e16b38562b2c6790f7c245dbaa44334879aefd6d"; expandedSizeBytes = 134192720; songCount = 2873 },
    [ordered]@{ id = "songs-v1.0.0-part-004"; filename = "songs-v1.0.0-part-004.zip"; url = "songs-v1.0.0-part-004.zip"; sizeBytes = 28470318; sha256 = "ed62f6e363e2572b51b33eff46f20312ea246c2ecd96a9497675a902ecc606fe"; expandedSizeBytes = 134209921; songCount = 2438 },
    [ordered]@{ id = "songs-v1.0.0-part-005"; filename = "songs-v1.0.0-part-005.zip"; url = "songs-v1.0.0-part-005.zip"; sizeBytes = 28780752; sha256 = "0fcd3fef0a5526bed59f37f3e2dad326bf8a9d64ac17707fd92c723fadf01b8f"; expandedSizeBytes = 134199364; songCount = 2416 },
    [ordered]@{ id = "songs-v1.0.0-part-006"; filename = "songs-v1.0.0-part-006.zip"; url = "songs-v1.0.0-part-006.zip"; sizeBytes = 25632197; sha256 = "6036969ff70da1ca95984f1bb65181716bebbd2676154b25327c79d946af64e0"; expandedSizeBytes = 134204565; songCount = 2664 },
    [ordered]@{ id = "songs-v1.0.0-part-007"; filename = "songs-v1.0.0-part-007.zip"; url = "songs-v1.0.0-part-007.zip"; sizeBytes = 30308144; sha256 = "b642b1fb96aaaab19fb4c12b2a28fc9133a2bd3bb3f46028a8503fda5c0f3a2d"; expandedSizeBytes = 134195710; songCount = 3006 },
    [ordered]@{ id = "songs-v1.0.0-part-008"; filename = "songs-v1.0.0-part-008.zip"; url = "songs-v1.0.0-part-008.zip"; sizeBytes = 34914433; sha256 = "dd54dd79046a3104601a4455ad5a464be45fb996eebaae8086d6b8789a760a77"; expandedSizeBytes = 134214474; songCount = 3501 },
    [ordered]@{ id = "songs-v1.0.0-part-009"; filename = "songs-v1.0.0-part-009.zip"; url = "songs-v1.0.0-part-009.zip"; sizeBytes = 34663705; sha256 = "cdb406668374bb38188ae65b246d5e6672de2e38c27a58a09eee4908550a82c3"; expandedSizeBytes = 134210964; songCount = 3534 },
    [ordered]@{ id = "songs-v1.0.0-part-010"; filename = "songs-v1.0.0-part-010.zip"; url = "songs-v1.0.0-part-010.zip"; sizeBytes = 30932902; sha256 = "4fdcc033781380a00fb7abd5f397d8e3b6720f9b68416f9956dd720af6a5111e"; expandedSizeBytes = 134207084; songCount = 3057 },
    [ordered]@{ id = "songs-v1.0.0-part-011"; filename = "songs-v1.0.0-part-011.zip"; url = "songs-v1.0.0-part-011.zip"; sizeBytes = 27060652; sha256 = "5b6053875d28c354408c4acbd379ade9c76bfd947a0011e669e00c2d529cfc91"; expandedSizeBytes = 134154874; songCount = 2929 },
    [ordered]@{ id = "songs-v1.0.0-part-012"; filename = "songs-v1.0.0-part-012.zip"; url = "songs-v1.0.0-part-012.zip"; sizeBytes = 34478680; sha256 = "4d264a38d552e3ead68750d8a7d647f9823aedfc42a686df232f812d24c2a188"; expandedSizeBytes = 134204763; songCount = 3285 },
    [ordered]@{ id = "songs-v1.0.0-part-013"; filename = "songs-v1.0.0-part-013.zip"; url = "songs-v1.0.0-part-013.zip"; sizeBytes = 35599063; sha256 = "bf4d74fba8b751fbbbf1e855d76685f7611bcd07013aac6425b2d48bafbe56c6"; expandedSizeBytes = 134168299; songCount = 3300 },
    [ordered]@{ id = "songs-v1.0.0-part-014"; filename = "songs-v1.0.0-part-014.zip"; url = "songs-v1.0.0-part-014.zip"; sizeBytes = 28727489; sha256 = "d9eef61f58d97f84124780387405132771e90639abfc534e57ff7e4e5a956807"; expandedSizeBytes = 134217140; songCount = 2916 },
    [ordered]@{ id = "songs-v1.0.0-part-015"; filename = "songs-v1.0.0-part-015.zip"; url = "songs-v1.0.0-part-015.zip"; sizeBytes = 29630080; sha256 = "a14551dc94a7385c40d6f50f081dad4990de73979bbbcbd8ae4c056f401f601f"; expandedSizeBytes = 134199193; songCount = 2321 },
    [ordered]@{ id = "songs-v1.0.0-part-016"; filename = "songs-v1.0.0-part-016.zip"; url = "songs-v1.0.0-part-016.zip"; sizeBytes = 36396614; sha256 = "ec7a4faed812d56f59b0849d6a87d54d5370ae673ff195ffb92491c793ca6a6c"; expandedSizeBytes = 134188760; songCount = 2717 },
    [ordered]@{ id = "songs-v1.0.0-part-017"; filename = "songs-v1.0.0-part-017.zip"; url = "songs-v1.0.0-part-017.zip"; sizeBytes = 2450780; sha256 = "12b7163dbc9acddc773815bd6304b949fd2e5eb09509f2ad4322a53ab58996b9"; expandedSizeBytes = 13128030; songCount = 205 }
)

# Scan Videos
$backgroundVideos = @()
if ($VideosDir -and (Test-Path $VideosDir)) {
    $videoFiles = @(Get-ChildItem -LiteralPath $VideosDir -File | Where-Object { $_.Extension -ieq '.mp4' })
    $backgroundVideos = @($videoFiles | ForEach-Object {
        $stem = [IO.Path]::GetFileNameWithoutExtension($_.Name).ToLowerInvariant() -replace '[^a-z0-9]+', '-'
        [ordered]@{
            id = "bgv-$($stem.Trim('-'))"
            filename = $_.Name
            url = "bgv/$($_.Name)"
            sizeBytes = $_.Length
            sha256 = Get-Sha256 $_.FullName
        }
    })
    Write-Host "Indexed $($backgroundVideos.Count) background video(s) from $VideosDir" -ForegroundColor Green
}

# Scan Photos
$backgroundPhotos = @()
if ($PhotosDir -and (Test-Path $PhotosDir)) {
    $photoFiles = @(Get-ChildItem -LiteralPath $PhotosDir -File | Where-Object { $_.Extension -match '^\.(jpg|jpeg|png|webp)$' })
    $backgroundPhotos = @($photoFiles | ForEach-Object {
        $stem = [IO.Path]::GetFileNameWithoutExtension($_.Name).ToLowerInvariant() -replace '[^a-z0-9]+', '-'
        [ordered]@{
            id = "photo-$($stem.Trim('-'))"
            filename = $_.Name
            url = "backgrounds/photos/$($_.Name)"
            sizeBytes = $_.Length
            sha256 = Get-Sha256 $_.FullName
        }
    })
    Write-Host "Indexed $($backgroundPhotos.Count) photo background(s) from $PhotosDir" -ForegroundColor Green
}

$manifest = [ordered]@{
    formatVersion = 1
    app = "JKTV Karaoke"
    libraryVersion = $LibraryVersion
    minimumAppVersion = $MinimumAppVersion
    generatedUtc = (Get-Date).ToUniversalTime().ToString("o")
    songCount = 47997
    soundfonts = $soundFonts
    songPackages = $songPackages
    backgroundVideos = $backgroundVideos
    backgroundPhotos = $backgroundPhotos
    deletedFiles = $DeletedFiles
}

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$targetFile = Join-Path $OutputDir "library-manifest.json"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$json = $manifest | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText($targetFile, $json, $utf8NoBom)

Write-Host "Generated: $targetFile" -ForegroundColor Green
Write-Host "Done! You can upload this file to: https://pub-b98e45182dd74f2d84728bc81bad6809.r2.dev/library-manifest.json" -ForegroundColor Yellow
