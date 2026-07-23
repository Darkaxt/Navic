param(
	[string] $TagOrCommit = "1.2.0",
	[string] $ExpectedCommit = "116ea75f86cff26199ab3e7180285e5b728913fa",
	[string] $ReleaseTag = "1.2.0",
	[int] $ApiVersion = 2,
	[string] $ReleaseArtifact = "karackencurllib-release.aar",
	[string] $ReleaseArtifactDigest =
		"sha256:eeead972edb3e7727399e05f380c03bf14118c16d3b8ac25679df10910e0721c",
	[string] $Repository = "https://github.com/Darkaxt/PlayLikeCurl",
	[string] $RepositoryRoot = (Join-Path $PSScriptRoot ".."),
	[string] $VerifiedReleaseArtifactPath,
	[string] $CandidateSourcePath
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
	if ($VerifiedReleaseArtifactPath) {
		$verifiedArtifact = (Resolve-Path -LiteralPath $VerifiedReleaseArtifactPath).Path
		Copy-Item -LiteralPath $verifiedArtifact -Destination $releaseArtifactPath
	} else {
		Invoke-WebRequest -Uri $releaseArtifactUrl -OutFile $releaseArtifactPath
	}
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

	if ($CandidateSourcePath) {
		$candidateSourceResolved = (Resolve-Path -LiteralPath $CandidateSourcePath).Path
		$candidateSourceBytes = [IO.File]::ReadAllBytes($candidateSourceResolved)
		if ($candidateSourceBytes.Length -eq 0) {
			throw "CandidateSourcePath is empty."
		}
		[IO.File]::WriteAllBytes(
			(Join-Path $stagingRoot "candidate-source.json"),
			$candidateSourceBytes
		)
	}

  Assert-PlayLikeCurlRemovalTarget `
    -RepositoryRoot $repositoryRootPath `
    -TargetPath $targetRoot
  $targetParent = Split-Path -Parent $targetRoot
  New-Item -ItemType Directory -Path $targetParent -Force | Out-Null
  $swapJournalPath = Join-Path $repositoryRootPath ".codex-validation/playlikecurl-import-swap.json"
  if (Test-Path -LiteralPath $swapJournalPath -PathType Leaf) {
    $journal = Get-Content -LiteralPath $swapJournalPath -Raw | ConvertFrom-Json
    $expectedTarget = [IO.Path]::GetFullPath($targetRoot)
    $recordedTarget = [IO.Path]::GetFullPath([string]$journal.Target)
    $recordedPrepared = [IO.Path]::GetFullPath([string]$journal.Prepared)
    $recordedBackup = [IO.Path]::GetFullPath([string]$journal.Backup)
    $expectedParent = [IO.Path]::GetFullPath($targetParent) + [IO.Path]::DirectorySeparatorChar
    if ($journal.SchemaVersion -ne 1 -or $recordedTarget -ne $expectedTarget -or
        -not $recordedPrepared.StartsWith($expectedParent, [StringComparison]::OrdinalIgnoreCase) -or
        -not $recordedBackup.StartsWith($expectedParent, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($recordedPrepared) -notmatch '^\.playlikecurl-prepared-[0-9a-f]{32}$' -or
        [IO.Path]::GetFileName($recordedBackup) -notmatch '^\.playlikecurl-backup-[0-9a-f]{32}$') {
      throw "PlayLikeCurl swap journal contains an unsafe path."
    }
    $targetExists = Test-Path -LiteralPath $recordedTarget
    $preparedExists = Test-Path -LiteralPath $recordedPrepared
    $backupExists = Test-Path -LiteralPath $recordedBackup
    if (-not $targetExists -and $backupExists) {
      Move-Item -LiteralPath $recordedBackup -Destination $recordedTarget
      if ($preparedExists) {
        Remove-Item -LiteralPath $recordedPrepared -Recurse -Force
      }
    } elseif ($targetExists -and $backupExists -and -not $preparedExists) {
      Remove-Item -LiteralPath $recordedBackup -Recurse -Force
    } elseif ($targetExists -and -not $backupExists) {
      if ($preparedExists) {
        Remove-Item -LiteralPath $recordedPrepared -Recurse -Force
      }
    } else {
      throw "PlayLikeCurl swap journal state is ambiguous; preserve it for review."
    }
    Remove-Item -LiteralPath $swapJournalPath -Force
  }
  $swapId = [guid]::NewGuid().ToString("N")
  $preparedTarget = Join-Path $targetParent ".playlikecurl-prepared-$swapId"
  $backupTarget = Join-Path $targetParent ".playlikecurl-backup-$swapId"
  foreach ($path in @($preparedTarget, $backupTarget)) {
    if (Test-Path -LiteralPath $path) {
      throw "PlayLikeCurl swap path unexpectedly exists: $path"
    }
  }
  $journalTemporary = "$swapJournalPath.tmp"
  [ordered]@{
    SchemaVersion = 1
    Target = [IO.Path]::GetFullPath($targetRoot)
    Prepared = [IO.Path]::GetFullPath($preparedTarget)
    Backup = [IO.Path]::GetFullPath($backupTarget)
  } | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $journalTemporary
  Move-Item -LiteralPath $journalTemporary -Destination $swapJournalPath -Force
  try {
    Copy-Item -LiteralPath $stagingRoot -Destination $preparedTarget -Recurse
    if (Test-Path -LiteralPath $targetRoot) {
      Move-Item -LiteralPath $targetRoot -Destination $backupTarget
    }
    Move-Item -LiteralPath $preparedTarget -Destination $targetRoot
    $verifierPath = Join-Path $repositoryRootPath (
      "scripts/verify-playlikecurl-snapshot.ps1"
    )
    & pwsh -NoProfile -File $verifierPath -RepositoryRoot $repositoryRootPath
    if ($LASTEXITCODE -ne 0) {
      throw "Imported PlayLikeCurl snapshot failed verification."
    }
    if (Test-Path -LiteralPath $backupTarget) {
      Remove-Item -LiteralPath $backupTarget -Recurse -Force
    }
    Remove-Item -LiteralPath $swapJournalPath -Force
  } catch {
    if ((Test-Path -LiteralPath $targetRoot) -and
        (Test-Path -LiteralPath $backupTarget)) {
      Remove-Item -LiteralPath $targetRoot -Recurse -Force
      Move-Item -LiteralPath $backupTarget -Destination $targetRoot
    } elseif (-not (Test-Path -LiteralPath $targetRoot) -and
        (Test-Path -LiteralPath $backupTarget)) {
      Move-Item -LiteralPath $backupTarget -Destination $targetRoot
    }
    throw
  } finally {
    if (Test-Path -LiteralPath $preparedTarget) {
      Remove-Item -LiteralPath $preparedTarget -Recurse -Force
    }
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
