param(
	[string] $RepositoryRoot = (Join-Path $PSScriptRoot "..")
)

$ErrorActionPreference = "Stop"

$expectedRepository = "https://github.com/Darkaxt/PlayLikeCurl"
$expectedCommit = "a16ea9aa46484f3068242577e1189af66fb1fa9d"
$expectedTag = "1.2.1"
$expectedApiVersion = 2
$expectedModule = "karackencurllib"
$expectedReleaseArtifact = "karackencurllib-release.aar"
$expectedReleaseArtifactUrl =
	"https://github.com/Darkaxt/PlayLikeCurl/releases/download/1.2.1/karackencurllib-release.aar"
$expectedReleaseArtifactDigest =
	"sha256:4c356f44443b5a1abcd70851f062d38f136dcdcc67d72eb3a699a12126584bcd"
$expectedCandidateCommit = "ae11967ba2342c1d4a770f81907a3c161e23ef94"
$expectedCandidateManifestSha256 =
	"f0796d48c98016de526895c2de01b0bba0ee33a5c4ec0af341724d5a17610f28"
$expectedCandidateLicenseBlob = "8aa26455d23acf904be3ed9dfb3a3efe3e49245a"

$repositoryRootPath = (Resolve-Path -LiteralPath $RepositoryRoot).Path
. (Join-Path $PSScriptRoot "../tools/playlikecurl-snapshot-common.ps1")

function Assert-Equal {
	param(
		[object] $Actual,
		[object] $Expected,
		[string] $Message
	)

	if ($Actual -ne $Expected) {
		throw "$Message Expected '$Expected', got '$Actual'."
	}
}

function Assert-ExactString {
	param(
		[object] $Actual,
		[string] $Expected,
		[string] $Message
	)

	if ($Actual -isnot [string] -or $Actual -cne $Expected) {
		throw "$Message Expected '$Expected', got '$Actual'."
	}
}

function Assert-JsonInteger {
	param(
		[object] $Actual,
		[long] $Expected,
		[string] $Message
	)

	$isInteger = $Actual -is [int] -or $Actual -is [long]
	if (-not $isInteger -or [long]$Actual -ne $Expected) {
		throw "$Message Expected integer '$Expected', got '$Actual'."
	}
}

function Assert-ExactProperties {
	param(
		[object] $Value,
		[string[]] $Expected,
		[string] $Message
	)

	if ($null -eq $Value) {
		throw "$Message Value is null."
	}
	$expectedNames = @($Expected | Sort-Object -CaseSensitive)
	$actualNames = @($Value.PSObject.Properties.Name | Sort-Object -CaseSensitive)
	if (@(Compare-Object `
			-ReferenceObject $expectedNames `
			-DifferenceObject $actualNames `
			-CaseSensitive).Count -ne 0) {
		throw "$Message Expected '$($expectedNames -join ",")', got '$($actualNames -join ",")'."
	}
}

function Assert-JsonStructure {
	param(
		[string] $Json,
		[string] $Description
	)

	$document = [System.Text.Json.JsonDocument]::Parse($Json)
	try {
		if ($document.RootElement.ValueKind -ne
			[System.Text.Json.JsonValueKind]::Object) {
			throw "$Description root must be a JSON object."
		}
		$pending = [System.Collections.Generic.Stack[System.Text.Json.JsonElement]]::new()
		$pending.Push($document.RootElement)
		while ($pending.Count -gt 0) {
			$element = $pending.Pop()
			if ($element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
				$names = [System.Collections.Generic.HashSet[string]]::new(
					[System.StringComparer]::Ordinal
				)
				foreach ($property in $element.EnumerateObject()) {
					if (-not $names.Add($property.Name)) {
						throw "$Description contains duplicate property '$($property.Name)'."
					}
					$pending.Push($property.Value)
				}
			} elseif ($element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
				foreach ($item in $element.EnumerateArray()) {
					$pending.Push($item)
				}
			}
		}
	} finally {
		$document.Dispose()
	}
}

function Invoke-SnapshotGitLines {
	param(
		[string[]] $Arguments,
		[string] $Description
	)

	$output = @(& git -C $repositoryRootPath @Arguments)
	$exitCode = $LASTEXITCODE
	if ($exitCode -ne 0) {
		throw "$Description (exit=$exitCode)."
	}
	return $output
}

function Get-CandidateMirrorEntries {
	param([object[]] $CandidateFiles)

	$modulePrefix = "third_party/playlikecurl/karackencurllib"
	$gitPaths = @(
		Invoke-SnapshotGitLines `
			@("ls-files", "--cached", "--others", "--exclude-standard", "--", $modulePrefix) `
			"Unable to enumerate the Git-backed PlayLikeCurl mirror" |
			ForEach-Object { $_.Replace("\", "/") } |
			Sort-Object -CaseSensitive
	)
	$physicalPaths = @(
		Get-PlayLikeCurlSnapshotFiles $moduleRoot |
			ForEach-Object { "karackencurllib/$($_.path)" } |
			Sort-Object -CaseSensitive
	)
	$expectedPaths = @(
		$CandidateFiles |
			ForEach-Object { [string]$_.path } |
			Sort-Object -CaseSensitive
	)
	$expectedMirrorPaths = @(
		$expectedPaths |
			ForEach-Object { "third_party/playlikecurl/$_" }
	)
	if (@(Compare-Object `
			-ReferenceObject $expectedPaths `
			-DifferenceObject $physicalPaths `
			-CaseSensitive).Count -ne 0) {
		throw "Candidate physical source inventory does not match the closed manifest."
	}
	if (@(Compare-Object `
			-ReferenceObject $expectedMirrorPaths `
			-DifferenceObject $gitPaths `
			-CaseSensitive).Count -ne 0) {
		throw "Candidate Git-backed source inventory does not match the closed manifest."
	}

	$entries = @()
	foreach ($mirrorPath in $gitPaths) {
		$fullPath = Join-Path $repositoryRootPath $mirrorPath
		if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
			throw "Candidate source file is missing: $mirrorPath"
		}
		$stageLines = @(
			Invoke-SnapshotGitLines `
				@("ls-files", "--stage", "--", $mirrorPath) `
				"Unable to inspect candidate source mode: $mirrorPath"
		)
		if ($stageLines.Count -gt 1) {
			throw "Candidate source has ambiguous Git stages: $mirrorPath"
		}
		if ($stageLines.Count -eq 1) {
			if ($stageLines[0] -notmatch "^(?<Mode>\d+)\s+[0-9a-f]{40}\s+\d+\t") {
				throw "Candidate source has a malformed Git index record: $mirrorPath"
			}
			$mode = $Matches["Mode"]
		} else {
			$mode = "100644"
		}
		$blobLines = @(
			Invoke-SnapshotGitLines `
				@("hash-object", "--", $mirrorPath) `
				"Unable to hash candidate source: $mirrorPath"
		)
		if ($blobLines.Count -ne 1 -or $blobLines[0] -notmatch "^[0-9a-f]{40}$") {
			throw "Candidate source has an invalid Git blob identity: $mirrorPath"
		}
		$entries += [pscustomobject]@{
			mode = $mode
			blob = $blobLines[0]
			path = $mirrorPath.Substring("third_party/playlikecurl/".Length)
		}
	}
	return $entries
}

function Assert-CandidateSource {
	param([string] $CandidatePath)

	if (-not (Test-Path -LiteralPath $CandidatePath -PathType Leaf)) {
		throw "API-3 candidate source record is missing: $CandidatePath"
	}
	$candidateJson = Get-Content -LiteralPath $CandidatePath -Raw
	Assert-JsonStructure $candidateJson "Candidate source record"
	$candidate = $candidateJson | ConvertFrom-Json
	Assert-ExactProperties `
		$candidate `
		@(
			"schemaVersion",
			"repository",
			"commit",
			"apiVersion",
			"sourceManifestSha256",
			"licenseBlob",
			"sourceFiles"
		) `
		"Candidate source record has unknown, missing, or mis-cased fields."
	Assert-JsonInteger $candidate.schemaVersion 1 "Wrong candidate schema version."
	Assert-ExactString $candidate.repository $expectedRepository "Wrong candidate repository."
	Assert-ExactString $candidate.commit $expectedCandidateCommit "Wrong candidate commit."
	Assert-JsonInteger $candidate.apiVersion 3 "Wrong candidate API version."
	Assert-ExactString `
		$candidate.sourceManifestSha256 `
		$expectedCandidateManifestSha256 `
		"Wrong candidate source manifest digest."
	Assert-ExactString `
		$candidate.licenseBlob `
		$expectedCandidateLicenseBlob `
		"Wrong candidate license blob."

	$candidateFiles = @($candidate.sourceFiles)
	if ($candidateFiles.Count -eq 0) {
		throw "Candidate source manifest is empty."
	}
	$seenPaths = [System.Collections.Generic.HashSet[string]]::new(
		[System.StringComparer]::Ordinal
	)
	$manifestLines = @()
	foreach ($file in $candidateFiles) {
		Assert-ExactProperties `
			$file `
			@("mode", "blob", "path") `
			"Candidate source entry has unknown, missing, or mis-cased fields."
		if ($file.mode -isnot [string] -or $file.mode -cnotmatch "^\d{6}$") {
			throw "Candidate source entry has an invalid Git mode."
		}
		if ($file.blob -isnot [string] -or $file.blob -cnotmatch "^[0-9a-f]{40}$") {
			throw "Candidate source entry has an invalid Git blob."
		}
		if ($file.path -isnot [string] -or
			$file.path -cnotmatch "^karackencurllib/[A-Za-z0-9._/-]+$" -or
			$file.path.Contains("//") -or
			@($file.path.Split("/") | Where-Object { $_ -in @(".", "..") }).Count -ne 0) {
			throw "Candidate source entry has an invalid path."
		}
		if (-not $seenPaths.Add($file.path)) {
			throw "Candidate source entry is duplicated: $($file.path)"
		}
		$manifestLines += "$($file.mode) blob $($file.blob)`t$($file.path)"
	}
	$manifestText = (@($manifestLines | Sort-Object -CaseSensitive) -join "`n") + "`n"
	$manifestDigest = Get-PlayLikeCurlSha256 (
		[System.Text.UTF8Encoding]::new($false).GetBytes($manifestText)
	)
	Assert-ExactString `
		$manifestDigest `
		$expectedCandidateManifestSha256 `
		"Candidate source entries do not reconstruct the canonical manifest."

	$mirrorEntries = @(Get-CandidateMirrorEntries $candidateFiles)
	Assert-Equal `
		$mirrorEntries.Count `
		$candidateFiles.Count `
		"Candidate source entry count drift."
	$candidateByPath = @{}
	foreach ($file in $candidateFiles) {
		$candidateByPath.Add([string]$file.path, $file)
	}
	foreach ($entry in $mirrorEntries) {
		$file = $candidateByPath[$entry.path]
		if ($null -eq $file) {
			throw "Candidate source path is not recorded: $($entry.path)"
		}
		Assert-ExactString $entry.mode $file.mode "Candidate source mode drift for '$($entry.path)'."
		Assert-ExactString $entry.blob $file.blob "Candidate source blob drift for '$($entry.path)'."
	}

	$licenseRelativePath = "third_party/playlikecurl/LICENSE.txt"
	$licenseBlobLines = @(
		Invoke-SnapshotGitLines `
			@("hash-object", "--", $licenseRelativePath) `
			"Unable to hash the candidate PlayLikeCurl license"
	)
	if ($licenseBlobLines.Count -ne 1) {
		throw "Candidate PlayLikeCurl license has no unique Git blob identity."
	}
	Assert-ExactString `
		$licenseBlobLines[0] `
		$expectedCandidateLicenseBlob `
		"Candidate PlayLikeCurl license blob drift."
}

$snapshotRoot = Join-Path $repositoryRootPath "third_party/playlikecurl"
$moduleRoot = Join-Path $snapshotRoot "karackencurllib"
$licensePath = Join-Path $snapshotRoot "LICENSE.txt"
$provenancePath = Join-Path $snapshotRoot "provenance.json"
$candidatePath = Join-Path $snapshotRoot "candidate-source.json"

foreach ($requiredPath in @($moduleRoot, $licensePath, $provenancePath)) {
	if (-not (Test-Path -LiteralPath $requiredPath)) {
		throw "PlayLikeCurl snapshot is incomplete: $requiredPath"
	}
}

$provenance = Get-Content -LiteralPath $provenancePath -Raw | ConvertFrom-Json
Assert-Equal $provenance.repository $expectedRepository "Wrong PlayLikeCurl repository."
Assert-Equal $provenance.commit $expectedCommit "Wrong PlayLikeCurl commit."
Assert-Equal $provenance.tag $expectedTag "Wrong PlayLikeCurl tag."
Assert-JsonInteger `
	$provenance.apiVersion `
	$expectedApiVersion `
	"Wrong PlayLikeCurl API version."
Assert-Equal $provenance.module $expectedModule "Wrong PlayLikeCurl module."
Assert-Equal $provenance.releaseArtifact $expectedReleaseArtifact "Wrong release artifact."
Assert-Equal `
	$provenance.releaseArtifactUrl `
	$expectedReleaseArtifactUrl `
	"Wrong PlayLikeCurl release artifact URL."
Assert-Equal `
	$provenance.releaseArtifactDigest `
	$expectedReleaseArtifactDigest `
	"Wrong PlayLikeCurl release artifact digest."

$apiSourcePath = Join-Path $moduleRoot (
	"src/main/java/karacken/curl/PlayLikeCurlApi.java"
)
$apiSourceLines = @(Get-Content -LiteralPath $apiSourcePath)
$apiDeclarationPattern =
	"^    public static final int PRODUCTION_API_VERSION = (?<Version>\d+);$"
$apiDeclarationLines = @($apiSourceLines | Where-Object {
	$_ -cmatch $apiDeclarationPattern
})
if ($apiDeclarationLines.Count -ne 1) {
	throw "PlayLikeCurlApi.PRODUCTION_API_VERSION must have one exact declaration."
}
$apiDeclaration = [regex]::Match(
	$apiDeclarationLines[0],
	$apiDeclarationPattern
)
$importedApiVersion = [int]$apiDeclaration.Groups["Version"].Value
$productionApiPath = Join-Path $moduleRoot "PRODUCTION_API.md"
$productionApiLines = @(Get-Content -LiteralPath $productionApiPath)
$expectedProductionApiLine =
	'Production API version `{0}` accepts client-prepared bitmap page decks through' -f
		$importedApiVersion
if (@($productionApiLines | Where-Object {
		$_ -ceq $expectedProductionApiLine
	}).Count -ne 1) {
	throw "PlayLikeCurl production document does not contain the exact API declaration."
}
$candidateActive = $provenance.apiVersion -eq 2 -and $importedApiVersion -eq 3

if ($candidateActive) {
	Assert-CandidateSource $candidatePath
	Write-Host (
		"PlayLikeCurl source candidate verified: commit $expectedCandidateCommit, " +
		"API $importedApiVersion; immutable release provenance remains tag $expectedTag."
	)
	exit 0
}

$sourceMetadata = Get-PlayLikeCurlSourceMetadata $moduleRoot
Assert-Equal $sourceMetadata.digest $provenance.sourceDigest "PlayLikeCurl source digest drift."
Assert-Equal $sourceMetadata.fileCount $provenance.sourceFileCount "PlayLikeCurl file count drift."

$expectedFiles = @($provenance.files)
Assert-Equal $expectedFiles.Count $sourceMetadata.files.Count "PlayLikeCurl file manifest drift."
for ($index = 0; $index -lt $sourceMetadata.files.Count; $index++) {
	Assert-Equal `
		$sourceMetadata.files[$index].path `
		$expectedFiles[$index].path `
		"PlayLikeCurl file path drift at index $index."
	Assert-Equal `
		$sourceMetadata.files[$index].sha256 `
		$expectedFiles[$index].sha256 `
		"PlayLikeCurl file digest drift for '$($sourceMetadata.files[$index].path)'."
}

Assert-Equal `
	(Get-PlayLikeCurlFileDigest $licensePath) `
	$provenance.licenseDigest `
	"PlayLikeCurl license digest drift."
Assert-Equal `
	$importedApiVersion `
	$expectedApiVersion `
	"Imported PlayLikeCurl API source drift."

Write-Host (
	"PlayLikeCurl snapshot verified: tag $expectedTag, commit $expectedCommit, " +
	"$($sourceMetadata.fileCount) files, API $expectedApiVersion."
)
