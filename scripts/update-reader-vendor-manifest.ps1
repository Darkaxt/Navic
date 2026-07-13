[CmdletBinding()]
param(
	[string] $VendorRoot = (Join-Path $PSScriptRoot "../composeApp/src/androidMain/assets/reader/vendor"),
	[string] $ManifestPath = (Join-Path $PSScriptRoot "../composeApp/src/androidMain/assets/reader/vendor/manifest.json")
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$resolvedVendorRoot = (Resolve-Path -LiteralPath $VendorRoot).Path
$resolvedManifestPath = (Resolve-Path -LiteralPath $ManifestPath).Path
$manifest = Get-Content -LiteralPath $resolvedManifestPath -Raw | ConvertFrom-Json

if ([int] $manifest.schemaVersion -ne 1) {
	throw "Unsupported reader vendor manifest schema: $($manifest.schemaVersion)"
}

$componentPrefixes = foreach ($component in $manifest.components) {
	foreach ($assetPath in $component.assetPaths) {
		[PSCustomObject]@{
			Component = [string] $component.id
			Prefix = ([string] $assetPath).TrimEnd("/")
		}
	}
}
$componentPrefixes = @($componentPrefixes | Sort-Object { $_.Prefix.Length } -Descending)

$manifest.files = @(
	Get-ChildItem -LiteralPath $resolvedVendorRoot -Recurse -File |
		Where-Object { $_.FullName -ne $resolvedManifestPath } |
		ForEach-Object {
			$relativePath = [System.IO.Path]::GetRelativePath($resolvedVendorRoot, $_.FullName).Replace("\", "/")
			$owner = $componentPrefixes | Where-Object {
				$relativePath -eq $_.Prefix -or $relativePath.StartsWith("$($_.Prefix)/", [System.StringComparison]::Ordinal)
			} | Select-Object -First 1
			if ($null -eq $owner) {
				throw "No component owns reader vendor file: $relativePath"
			}
			[PSCustomObject]@{
				path = $relativePath
				component = $owner.Component
				sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
			}
		} |
		Sort-Object path
)

$json = $manifest | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText($resolvedManifestPath, "$json`n", [System.Text.UTF8Encoding]::new($false))
Write-Output "Updated reader vendor manifest with $($manifest.files.Count) files."
