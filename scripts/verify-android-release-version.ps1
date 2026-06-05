param(
    [Parameter(Mandatory = $true)]
    [string] $ExpectedVersionName
)

$buildFile = Join-Path $PSScriptRoot "..\androidApp\build.gradle.kts"
$content = Get-Content -LiteralPath $buildFile -Raw

$versionNameMatch = [regex]::Match($content, 'versionName\s*=\s*"([^"]+)"')
if (-not $versionNameMatch.Success) {
    Write-Error "Could not find androidApp versionName in $buildFile"
    exit 1
}

$actualVersionName = $versionNameMatch.Groups[1].Value
if ($actualVersionName -ne $ExpectedVersionName) {
    Write-Error "Android versionName mismatch. Expected $ExpectedVersionName but found $actualVersionName."
    exit 1
}

Write-Host "Android versionName matches $ExpectedVersionName"
