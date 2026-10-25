[CmdletBinding()]
param(
    [string] $SourceRoot = 'C:\Users\hillm\OneDrive\Desktop\JKT Offiline',
    [string] $OutputJson
)

$ErrorActionPreference = 'Stop'
$extensions = @('.mid', '.midi', '.kar', '.sf2', '.sf3')
$files = @(Get-ChildItem -LiteralPath $SourceRoot -File -Recurse | Where-Object { $extensions -contains $_.Extension.ToLowerInvariant() })
$rows = [System.Collections.Generic.List[object]]::new()
foreach ($file in $files) {
    $extension = $file.Extension.ToLowerInvariant()
    $stream = [System.IO.File]::OpenRead($file.FullName)
    try {
        $header = [byte[]]::new(12)
        $length = $stream.Read($header, 0, $header.Length)
    } finally { $stream.Dispose() }
    $signature = [Text.Encoding]::ASCII.GetString($header, 0, [Math]::Min(12, $length))
    $valid = $false
    if ($extension -in '.mid', '.midi', '.kar') {
        $valid = $signature.StartsWith('MThd') -or ($length -ge 12 -and $signature.StartsWith('RIFF') -and $signature.Substring(8, 4) -eq 'RMID')
    } else {
        $valid = $length -ge 12 -and $signature.StartsWith('RIFF') -and $signature.Substring(8, 4) -eq 'sfbk'
    }
    $relative = $file.FullName.Substring($SourceRoot.Length).TrimStart('\').Replace('\', '/')
    $rows.Add([pscustomobject]@{
        path = $relative
        extension = $extension
        sizeBytes = $file.Length
        sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        headerValid = [bool]$valid
    })
}

$duplicateGroups = @($rows | Group-Object sha256 | Where-Object Count -gt 1 | ForEach-Object {
    [pscustomobject]@{ sha256 = $_.Name; paths = @($_.Group.path) }
})
$byType = @($rows | Group-Object extension | Sort-Object Name | ForEach-Object {
    [pscustomobject]@{ extension = $_.Name; count = $_.Count; bytes = [long](($_.Group | Measure-Object sizeBytes -Sum).Sum) }
})
$report = [ordered]@{
    scannedUtc = [DateTime]::UtcNow.ToString('o')
    sourceRoot = (Resolve-Path -LiteralPath $SourceRoot).Path
    supportedFileCount = $rows.Count
    totalBytes = [long](($rows | Measure-Object sizeBytes -Sum).Sum)
    invalidHeaderCount = @($rows | Where-Object { -not $_.headerValid }).Count
    duplicateHashGroupCount = $duplicateGroups.Count
    duplicateFileCount = [int](($duplicateGroups | ForEach-Object { $_.paths.Count } | Measure-Object -Sum).Sum)
    byExtension = $byType
    duplicateGroups = $duplicateGroups
    files = @($rows)
}

if ($OutputJson) { $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputJson -Encoding UTF8 }
$byType | Format-Table -AutoSize
Write-Output "Files: $($report.supportedFileCount); bytes: $($report.totalBytes); invalid headers: $($report.invalidHeaderCount); duplicate groups: $($report.duplicateHashGroupCount)."
if ($OutputJson) { Write-Output "JSON report: $OutputJson" }
