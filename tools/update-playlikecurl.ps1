param(
	[string] $TagOrCommit = "1.1.4",
	[string] $ExpectedCommit = "b885fc182f8e0c1c3a518c5bef23765eb44e1f31",
	[string] $ReleaseTag = "1.1.4",
	[int] $ApiVersion = 1,
	[string] $ReleaseArtifact = "karackencurllib-release.aar",
	[string] $ReleaseArtifactDigest =
		"sha256:9e31005cdf1768a89f7356f8519caefa80fd05fc84ca98e8b070fad009078ca8",
	[string] $Repository = "https://github.com/Darkaxt/PlayLikeCurl",
	[string] $RepositoryRoot = (Join-Path $PSScriptRoot "..")
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "playlikecurl-snapshot-common.ps1")

function Invoke-Git {
	param([string[]] $Arguments)

	$output = & git @Arguments 2>&1
	if ($LASTEXITCODE -ne 0) {
		throw "git $($Arguments -join ' ') failed:`n$($output -join "`n")"
	}
	return $output
}

if ($ExpectedCommit -notmatch "^[0-9a-fA-F]{40}$") {
	throw "ExpectedCommit must be a full 40-character commit."
}
if (
	$ReleaseArtifactDigest -notmatch "^sha256:[0-9a-f]{64}$"
) {
	throw "ReleaseArtifactDigest must be a lowercase SHA-256 digest."
}

$repositoryRootPath = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
	"navic-playlikecurl-import-" + [guid]::NewGuid().ToString("N")
)
$cloneRoot = Join-Path $temporaryRoot "source"
$stagingRoot = Join-Path $temporaryRoot "snapshot"
$releaseArtifactPath = Join-Path $temporaryRoot $ReleaseArtifact
$targetRoot = Join-Path $repositoryRootPath "third_party/playlikecurl"

try {
	New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null
	Invoke-Git @(
		"-c", "core.autocrlf=false",
		"clone", "--quiet", "--no-checkout", $Repository, $cloneRoot
	) | Out-Null
	Invoke-Git @("-C", $cloneRoot, "config", "core.autocrlf", "false") | Out-Null
	Invoke-Git @("-C", $cloneRoot, "config", "core.eol", "lf") | Out-Null

	$resolvedCommitLines = @(
		Invoke-Git @("-C", $cloneRoot, "rev-parse", "$TagOrCommit^{commit}")
	)
	$resolvedCommit = $resolvedCommitLines[-1].Trim().ToLowerInvariant()
	if ($resolvedCommit -ne $ExpectedCommit.ToLowerInvariant()) {
		throw (
			"PlayLikeCurl '$TagOrCommit' resolves to $resolvedCommit, not " +
			"$($ExpectedCommit.ToLowerInvariant())."
		)
	}
	$releaseCommitLines = @(
		Invoke-Git @("-C", $cloneRoot, "rev-parse", "$ReleaseTag^{commit}")
	)
	$releaseCommit = $releaseCommitLines[-1].Trim().ToLowerInvariant()
	if ($releaseCommit -ne $resolvedCommit) {
		throw (
			"PlayLikeCurl release '$ReleaseTag' resolves to $releaseCommit, not " +
			"$resolvedCommit."
		)
	}
	Invoke-Git @("-C", $cloneRoot, "checkout", "--detach", "--quiet", $resolvedCommit) |
		Out-Null
	$dirtyState = [string]::Join(
		"`n",
		@(Invoke-Git @("-C", $cloneRoot, "status", "--short"))
	).Trim()
	if ($dirtyState) {
		throw "Fresh PlayLikeCurl checkout is dirty:`n$dirtyState"
	}

	$releaseArtifactUrl =
		"$Repository/releases/download/$ReleaseTag/$ReleaseArtifact"
	Invoke-WebRequest -Uri $releaseArtifactUrl -OutFile $releaseArtifactPath
	$actualReleaseArtifactDigest = Get-PlayLikeCurlFileDigest $releaseArtifactPath
	if ($actualReleaseArtifactDigest -ne $ReleaseArtifactDigest) {
		throw (
			"PlayLikeCurl release artifact digest mismatch. Expected " +
			"'$ReleaseArtifactDigest', got '$actualReleaseArtifactDigest'."
		)
	}

	$sourceModuleRoot = Join-Path $cloneRoot "karackencurllib"
	$sourceLicensePath = Join-Path $cloneRoot "LICENSE.txt"
	Get-PlayLikeCurlSnapshotFiles $sourceModuleRoot | Out-Null

	New-Item -ItemType Directory -Path $stagingRoot -Force | Out-Null
	Copy-Item `
		-LiteralPath $sourceModuleRoot `
		-Destination (Join-Path $stagingRoot "karackencurllib") `
		-Recurse
	Copy-Item `
		-LiteralPath $sourceLicensePath `
		-Destination (Join-Path $stagingRoot "LICENSE.txt")

	$sourceMetadata = Get-PlayLikeCurlSourceMetadata (
		Join-Path $stagingRoot "karackencurllib"
	)
	$licenseDigest = Get-PlayLikeCurlFileDigest (
		Join-Path $stagingRoot "LICENSE.txt"
	)
	$provenance = [ordered]@{
		repository = $Repository
		commit = $resolvedCommit
		tag = $ReleaseTag
		apiVersion = $ApiVersion
		module = "karackencurllib"
		releaseArtifact = $ReleaseArtifact
		releaseArtifactUrl = $releaseArtifactUrl
		releaseArtifactDigest = $ReleaseArtifactDigest
		sourceDigest = $sourceMetadata.digest
		sourceFileCount = $sourceMetadata.fileCount
		licenseDigest = $licenseDigest
		files = @($sourceMetadata.files)
	}
	$provenanceJson = ($provenance | ConvertTo-Json -Depth 8).
		Replace("`r`n", "`n").
		Replace("`r", "`n")
	[System.IO.File]::WriteAllText(
		(Join-Path $stagingRoot "provenance.json"),
		"$provenanceJson`n",
		[System.Text.UTF8Encoding]::new($false)
	)

	Assert-PlayLikeCurlRemovalTarget `
		-RepositoryRoot $repositoryRootPath `
		-TargetPath $targetRoot
	if (Test-Path -LiteralPath $targetRoot) {
		Remove-Item -LiteralPath $targetRoot -Recurse -Force
	}
	New-Item -ItemType Directory -Path (Split-Path -Parent $targetRoot) -Force |
		Out-Null
	Copy-Item -LiteralPath $stagingRoot -Destination $targetRoot -Recurse

	$verifierPath = Join-Path $repositoryRootPath (
		"scripts/verify-playlikecurl-snapshot.ps1"
	)
	& pwsh -NoProfile -File $verifierPath -RepositoryRoot $repositoryRootPath
	if ($LASTEXITCODE -ne 0) {
		throw "Imported PlayLikeCurl snapshot failed verification."
	}
} finally {
	if (Test-Path -LiteralPath $temporaryRoot) {
		$resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
		$resolvedSystemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
		if (-not $resolvedTemporaryRoot.StartsWith(
			$resolvedSystemTemp,
			[System.StringComparison]::OrdinalIgnoreCase
		)) {
			throw "Refusing to remove unexpected import path: $resolvedTemporaryRoot"
		}
		Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
	}
}

Write-Host (
	"Imported PlayLikeCurl '$TagOrCommit' at $($ExpectedCommit.ToLowerInvariant()) " +
	"into third_party/playlikecurl."
)
