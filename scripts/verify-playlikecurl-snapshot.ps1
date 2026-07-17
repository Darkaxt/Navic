param(
	[string] $RepositoryRoot = (Join-Path $PSScriptRoot "..")
)

$ErrorActionPreference = "Stop"

$expectedRepository = "https://github.com/Darkaxt/PlayLikeCurl"
$expectedCommit = "2555831fcca962b2089997c4f8ea21ff5bd226fc"
$expectedTag = "1.1.2"
$expectedApiVersion = 1
$expectedModule = "karackencurllib"
$expectedReleaseArtifact = "karackencurllib-release.aar"
$expectedReleaseArtifactUrl =
	"https://github.com/Darkaxt/PlayLikeCurl/releases/download/1.1.2/karackencurllib-release.aar"
$expectedReleaseArtifactDigest =
	"sha256:01ef07dcf19f52ce1cba37e9f9be3abcf15b228b9a7dd14d917036f85b4fe42b"

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

$snapshotRoot = Join-Path $repositoryRootPath "third_party/playlikecurl"
$moduleRoot = Join-Path $snapshotRoot "karackencurllib"
$licensePath = Join-Path $snapshotRoot "LICENSE.txt"
$provenancePath = Join-Path $snapshotRoot "provenance.json"

foreach ($requiredPath in @($moduleRoot, $licensePath, $provenancePath)) {
	if (-not (Test-Path -LiteralPath $requiredPath)) {
		throw "PlayLikeCurl snapshot is incomplete: $requiredPath"
	}
}

$provenance = Get-Content -LiteralPath $provenancePath -Raw | ConvertFrom-Json
Assert-Equal $provenance.repository $expectedRepository "Wrong PlayLikeCurl repository."
Assert-Equal $provenance.commit $expectedCommit "Wrong PlayLikeCurl commit."
Assert-Equal $provenance.tag $expectedTag "Wrong PlayLikeCurl tag."
Assert-Equal $provenance.apiVersion $expectedApiVersion "Wrong PlayLikeCurl API version."
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

$apiSourcePath = Join-Path $moduleRoot (
	"src/main/java/karacken/curl/PlayLikeCurlApi.java"
)
$apiSource = Get-Content -LiteralPath $apiSourcePath -Raw
$apiMatch = [regex]::Match(
	$apiSource,
	"PRODUCTION_API_VERSION\s*=\s*(\d+)"
)
if (-not $apiMatch.Success) {
	throw "PlayLikeCurlApi.PRODUCTION_API_VERSION is missing."
}
Assert-Equal `
	([int] $apiMatch.Groups[1].Value) `
	$expectedApiVersion `
	"Imported PlayLikeCurl API source drift."

Write-Host (
	"PlayLikeCurl snapshot verified: tag $expectedTag, commit $expectedCommit, " +
	"$($sourceMetadata.fileCount) files, API $expectedApiVersion."
)
