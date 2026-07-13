[CmdletBinding()]
param(
	[string] $VendorRoot = (Join-Path $PSScriptRoot "../composeApp/src/androidMain/assets/reader/vendor"),
	[string] $ManifestPath = (Join-Path $PSScriptRoot "../composeApp/src/androidMain/assets/reader/vendor/manifest.json"),
	[string] $ApkPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-Sha256FromStream {
	param([Parameter(Mandatory)] [System.IO.Stream] $Stream)

	$algorithm = [System.Security.Cryptography.SHA256]::Create()
	try {
		return [Convert]::ToHexString($algorithm.ComputeHash($Stream)).ToLowerInvariant()
	} finally {
		$algorithm.Dispose()
	}
}

function Assert-ExactPathSet {
	param(
		[Parameter(Mandatory)] [string[]] $Expected,
		[Parameter(Mandatory)] [string[]] $Actual,
		[Parameter(Mandatory)] [string] $MissingLabel,
		[Parameter(Mandatory)] [string] $ExtraLabel
	)

	$missing = @($Expected | Where-Object { $_ -notin $Actual })
	$extra = @($Actual | Where-Object { $_ -notin $Expected })
	if ($missing.Count -gt 0) {
		throw "$MissingLabel $($missing -join ', ')"
	}
	if ($extra.Count -gt 0) {
		throw "$ExtraLabel $($extra -join ', ')"
	}
}

$resolvedVendorRoot = (Resolve-Path -LiteralPath $VendorRoot).Path
$resolvedManifestPath = (Resolve-Path -LiteralPath $ManifestPath).Path
$rootWithSeparator = $resolvedVendorRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$manifest = Get-Content -LiteralPath $resolvedManifestPath -Raw | ConvertFrom-Json

if ([int] $manifest.schemaVersion -ne 1) {
	throw "Unsupported reader vendor manifest schema: $($manifest.schemaVersion)"
}
if ([string] $manifest.generatedBy -ne "scripts/update-reader-vendor-manifest.ps1") {
	throw "Reader vendor manifest must identify its deterministic updater."
}

$componentIds = @{}
$componentPrefixes = @()
foreach ($component in @($manifest.components)) {
	$id = [string] $component.id
	if ([string]::IsNullOrWhiteSpace($id) -or $componentIds.ContainsKey($id)) {
		throw "Reader vendor component ids must be non-empty and unique: '$id'"
	}
	$componentIds[$id] = $true
	foreach ($field in @("name", "version", "sourceUrl", "sourceCommit", "packageUrl", "packageIntegrity", "license")) {
		if ([string]::IsNullOrWhiteSpace([string] $component.$field)) {
			throw "Reader vendor component '$id' is missing '$field'."
		}
	}
	if ([string] $component.sourceCommit -cnotmatch '^[0-9a-f]{40}$') {
		throw "Reader vendor component '$id' must pin a lowercase 40-character source commit."
	}
	if ([string] $component.sourceUrl -notmatch '^https://') {
		throw "Reader vendor component '$id' source URL must use HTTPS."
	}
	if ([string] $component.packageUrl -notmatch '^https://') {
		throw "Reader vendor component '$id' package URL must use HTTPS."
	}
	if ([string] $component.packageIntegrity -notmatch '^sha512-[A-Za-z0-9+/]+={0,2}$') {
		throw "Reader vendor component '$id' must pin an npm SHA-512 integrity value."
	}
	if (
		$component.PSObject.Properties.Name -contains "packageGitHead" -and
		[string] $component.packageGitHead -cnotmatch '^[0-9a-f]{40}$'
	) {
		throw "Reader vendor component '$id' package Git head must be a lowercase 40-character commit."
	}
	if (@($component.assetPaths).Count -eq 0) {
		throw "Reader vendor component '$id' must own at least one asset path."
	}
	foreach ($assetPathValue in @($component.assetPaths)) {
		$assetPath = [string] $assetPathValue
		$assetSegments = @($assetPath.Split('/'))
		if (
			[string]::IsNullOrWhiteSpace($assetPath) -or
			$assetPath.Contains("\") -or
			[System.IO.Path]::IsPathRooted($assetPath) -or
			@($assetSegments | Where-Object { $_ -in @("", ".", "..") }).Count -gt 0
		) {
			throw "Unsafe reader vendor component asset path: '$assetPath'"
		}
		if (@($componentPrefixes | Where-Object { $_.Prefix -ceq $assetPath }).Count -gt 0) {
			throw "Duplicate reader vendor component asset path: '$assetPath'"
		}
		$componentPrefixes += [PSCustomObject]@{ Component = $id; Prefix = $assetPath }
	}
}
if ($componentIds.Count -eq 0) {
	throw "Reader vendor manifest must contain at least one component."
}
$componentPrefixes = @($componentPrefixes | Sort-Object { $_.Prefix.Length } -Descending)

$fileByPath = @{}
foreach ($file in @($manifest.files)) {
	$relativePath = [string] $file.path
	$pathSegments = @($relativePath.Split('/'))
	if (
		[string]::IsNullOrWhiteSpace($relativePath) -or
		$relativePath.Contains("\") -or
		[System.IO.Path]::IsPathRooted($relativePath) -or
		@($pathSegments | Where-Object { $_ -in @("", ".", "..") }).Count -gt 0
	) {
		throw "Unsafe reader vendor manifest path: '$relativePath'"
	}
	if ($fileByPath.ContainsKey($relativePath)) {
		throw "Duplicate reader vendor manifest path: '$relativePath'"
	}
	if (-not $componentIds.ContainsKey([string] $file.component)) {
		throw "Reader vendor file '$relativePath' references an unknown component."
	}
	$owner = $componentPrefixes | Where-Object {
		$relativePath -eq $_.Prefix -or $relativePath.StartsWith("$($_.Prefix)/", [System.StringComparison]::Ordinal)
	} | Select-Object -First 1
	if ($null -eq $owner) {
		throw "No component owns reader vendor file: '$relativePath'"
	}
	if ([string] $file.component -cne [string] $owner.Component) {
		throw "Reader vendor file '$relativePath' must belong to component '$($owner.Component)'."
	}
	$expectedHash = [string] $file.sha256
	if ($expectedHash -cnotmatch '^[0-9a-f]{64}$') {
		throw "Reader vendor file '$relativePath' must have a lowercase SHA-256 hash."
	}

	$nativeRelativePath = $relativePath.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
	$fullPath = [System.IO.Path]::GetFullPath((Join-Path $resolvedVendorRoot $nativeRelativePath))
	if (-not $fullPath.StartsWith($rootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)) {
		throw "Reader vendor path escapes its root: '$relativePath'"
	}
	if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
		throw "Missing reader vendor file: $relativePath"
	}
	$actualHash = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant()
	if ($actualHash -cne $expectedHash) {
		throw "Hash mismatch for reader vendor file '$relativePath': expected $expectedHash, found $actualHash"
	}
	$fileByPath[$relativePath] = $expectedHash
}
if ($fileByPath.Count -eq 0) {
	throw "Reader vendor manifest must contain at least one file."
}

$actualSourcePaths = @(
	Get-ChildItem -LiteralPath $resolvedVendorRoot -Recurse -File |
		Where-Object { $_.FullName -ne $resolvedManifestPath } |
		ForEach-Object { [System.IO.Path]::GetRelativePath($resolvedVendorRoot, $_.FullName).Replace("\", "/") }
)
Assert-ExactPathSet -Expected @($fileByPath.Keys) -Actual $actualSourcePaths `
	-MissingLabel "Missing reader vendor files:" -ExtraLabel "Unmanifested vendor files:"

Write-Output "Verified $($fileByPath.Count) reader vendor source files."

if (-not [string]::IsNullOrWhiteSpace($ApkPath)) {
	$resolvedApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
	Add-Type -AssemblyName System.IO.Compression.FileSystem
	$archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedApkPath)
	try {
		$entryPrefix = "assets/reader/vendor/"
		$manifestEntryName = "${entryPrefix}manifest.json"
		$manifestEntry = $archive.GetEntry($manifestEntryName)
		if ($null -eq $manifestEntry) {
			throw "Built APK is missing $manifestEntryName"
		}
		$sourceManifestHash = (Get-FileHash -LiteralPath $resolvedManifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
		$manifestStream = $manifestEntry.Open()
		try {
			$packagedManifestHash = Get-Sha256FromStream -Stream $manifestStream
		} finally {
			$manifestStream.Dispose()
		}
		if ($packagedManifestHash -cne $sourceManifestHash) {
			throw "Built APK reader vendor manifest does not match the source manifest."
		}

		$packagedEntries = @{}
		foreach ($entry in $archive.Entries) {
			if (
				$entry.FullName.StartsWith($entryPrefix, [System.StringComparison]::Ordinal) -and
				-not $entry.FullName.EndsWith("/", [System.StringComparison]::Ordinal) -and
				$entry.FullName -ne $manifestEntryName
			) {
				$relativePath = $entry.FullName.Substring($entryPrefix.Length)
				$packagedEntries[$relativePath] = $entry
			}
		}
		Assert-ExactPathSet -Expected @($fileByPath.Keys) -Actual @($packagedEntries.Keys) `
			-MissingLabel "Built APK is missing reader vendor files:" -ExtraLabel "Built APK has unmanifested reader vendor files:"

		foreach ($relativePath in $fileByPath.Keys) {
			$stream = $packagedEntries[$relativePath].Open()
			try {
				$packagedHash = Get-Sha256FromStream -Stream $stream
			} finally {
				$stream.Dispose()
			}
			if ($packagedHash -cne $fileByPath[$relativePath]) {
				throw "Hash mismatch for packaged reader vendor file '$relativePath'."
			}
		}
	} finally {
		$archive.Dispose()
	}
	Write-Output "Verified $($fileByPath.Count) packaged reader vendor files in $resolvedApkPath."
}
