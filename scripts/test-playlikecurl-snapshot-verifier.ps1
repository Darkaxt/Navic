param(
	[string] $RepositoryRoot = (Join-Path $PSScriptRoot "..")
)

$ErrorActionPreference = "Stop"

$repositoryRootPath = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$verifierPath = Join-Path $repositoryRootPath "scripts/verify-playlikecurl-snapshot.ps1"

if (-not (Test-Path -LiteralPath $verifierPath -PathType Leaf)) {
	throw "PlayLikeCurl snapshot verifier is missing: $verifierPath"
}

function Invoke-Verifier {
	param(
		[string] $Root,
		[bool] $ShouldPass,
		[string] $Case
	)

	$output = & pwsh -NoProfile -File $verifierPath -RepositoryRoot $Root 2>&1
	$exitCode = $LASTEXITCODE
	if ($ShouldPass -and $exitCode -ne 0) {
		throw "$Case should pass, but failed with exit code ${exitCode}:`n$($output -join "`n")"
	}
	if (-not $ShouldPass -and $exitCode -eq 0) {
		throw "$Case should fail, but passed."
	}
}

Invoke-Verifier -Root $repositoryRootPath -ShouldPass $true -Case "Canonical snapshot"

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
	"navic-playlikecurl-verifier-" + [guid]::NewGuid().ToString("N")
)

try {
	New-Item -ItemType Directory -Path (Join-Path $temporaryRoot "third_party") -Force | Out-Null
	Copy-Item `
		-LiteralPath (Join-Path $repositoryRootPath "third_party/playlikecurl") `
		-Destination (Join-Path $temporaryRoot "third_party/playlikecurl") `
		-Recurse

	$apiPath = Join-Path $temporaryRoot (
		"third_party/playlikecurl/karackencurllib/src/main/java/" +
		"karacken/curl/PlayLikeCurlApi.java"
	)
	Add-Content -LiteralPath $apiPath -Value "// tampered"
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false -Case "Source tampering"

	Remove-Item -LiteralPath (Join-Path $temporaryRoot "third_party/playlikecurl") -Recurse -Force
	Copy-Item `
		-LiteralPath (Join-Path $repositoryRootPath "third_party/playlikecurl") `
		-Destination (Join-Path $temporaryRoot "third_party/playlikecurl") `
		-Recurse
	$generatedPath = Join-Path $temporaryRoot (
		"third_party/playlikecurl/karackencurllib/build/generated.bin"
	)
	New-Item -ItemType Directory -Path (Split-Path -Parent $generatedPath) -Force | Out-Null
	Set-Content -LiteralPath $generatedPath -Value "generated"
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false -Case "Generated build output"

	Remove-Item -LiteralPath (Join-Path $temporaryRoot "third_party/playlikecurl") -Recurse -Force
	Copy-Item `
		-LiteralPath (Join-Path $repositoryRootPath "third_party/playlikecurl") `
		-Destination (Join-Path $temporaryRoot "third_party/playlikecurl") `
		-Recurse
	$provenancePath = Join-Path $temporaryRoot "third_party/playlikecurl/provenance.json"
	$provenance = Get-Content -LiteralPath $provenancePath -Raw | ConvertFrom-Json
	$provenance.apiVersion = 2
	$provenance | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $provenancePath
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false -Case "API version drift"

	Remove-Item -LiteralPath (Join-Path $temporaryRoot "third_party/playlikecurl") -Recurse -Force
	Copy-Item `
		-LiteralPath (Join-Path $repositoryRootPath "third_party/playlikecurl") `
		-Destination (Join-Path $temporaryRoot "third_party/playlikecurl") `
		-Recurse
	$provenancePath = Join-Path $temporaryRoot "third_party/playlikecurl/provenance.json"
	$provenance = Get-Content -LiteralPath $provenancePath -Raw | ConvertFrom-Json
	$provenance.releaseArtifactUrl =
		"https://github.com/Darkaxt/PlayLikeCurl/releases/download/invalid/karackencurllib-release.aar"
	$provenance | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $provenancePath
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false -Case "Release artifact URL drift"
} finally {
	if (Test-Path -LiteralPath $temporaryRoot) {
		$resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
		$resolvedSystemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
		if (-not $resolvedTemporaryRoot.StartsWith(
			$resolvedSystemTemp,
			[System.StringComparison]::OrdinalIgnoreCase
		)) {
			throw "Refusing to remove unexpected verifier test path: $resolvedTemporaryRoot"
		}
		Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
	}
}

Write-Host "PlayLikeCurl snapshot verifier tests passed."
