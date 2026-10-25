[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory = $true)] [string] $Version,
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

$forward = @{}
foreach ($key in $PSBoundParameters.Keys) { $forward[$key] = $PSBoundParameters[$key] }
& (Join-Path $PSScriptRoot 'publish-github-assets.ps1') @forward
