param(
	[string] $RepositoryRoot = (Join-Path $PSScriptRoot "..")
)

$ErrorActionPreference = "Stop"

$expectedRepository = "https://github.com/Darkaxt/PlayLikeCurl"
$expectedCommit = "f13eb7a4cb75761a6d329cc3d221faa2aeb47431"
$expectedTag = "1.1.3"
$expectedApiVersion = 1
$expectedModule = "karackencurllib"
$expectedReleaseArtifact = "karackencurllib-release.aar"
$expectedReleaseArtifactUrl =
	"https://github.com/Darkaxt/PlayLikeCurl/releases/download/1.1.3/karackencurllib-release.aar"
$expectedReleaseArtifactDigest =
	"sha256:67340fcc2d325883b9b033bf356b87c782f938490bdbc67eed3c3be75f1af957"

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
