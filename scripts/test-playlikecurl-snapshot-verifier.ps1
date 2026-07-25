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

function Invoke-TestGit {
	param(
		[string] $Root,
		[string[]] $Arguments,
		[string] $Description
	)

	& git -C $Root @Arguments | Out-Null
	if ($LASTEXITCODE -ne 0) {
		throw "$Description (exit=$LASTEXITCODE)."
	}
}

function Read-Candidate {
	param([string] $Root)

	$path = Join-Path $Root "third_party/playlikecurl/candidate-source.json"
	return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}

function Write-Candidate {
	param(
		[string] $Root,
		[object] $Candidate
	)

	$path = Join-Path $Root "third_party/playlikecurl/candidate-source.json"
	$Candidate | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $path
}

Invoke-Verifier `
	-Root $repositoryRootPath `
	-ShouldPass $true `
	-Case "Canonical API-3 source candidate"

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
	"navic-playlikecurl-verifier-" + [guid]::NewGuid().ToString("N")
)
$temporarySnapshot = Join-Path $temporaryRoot "third_party/playlikecurl"
$sourceSnapshot = Join-Path $repositoryRootPath "third_party/playlikecurl"

try {
	New-Item -ItemType Directory -Path (Join-Path $temporaryRoot "third_party") -Force |
		Out-Null
	Invoke-TestGit $temporaryRoot @("init", "--quiet") "Unable to initialize verifier fixture"

	function Reset-Fixture {
		if (Test-Path -LiteralPath $temporarySnapshot) {
			Remove-Item -LiteralPath $temporarySnapshot -Recurse -Force
		}
		Copy-Item -LiteralPath $sourceSnapshot -Destination $temporarySnapshot -Recurse
		Invoke-TestGit $temporaryRoot @("add", "--all") "Unable to index verifier fixture"
	}

	Reset-Fixture
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $true -Case "Clean candidate"

	Reset-Fixture
	$candidate = Read-Candidate $temporaryRoot
	$missingPath = Join-Path $temporarySnapshot ([string]$candidate.sourceFiles[0].path)
	Remove-Item -LiteralPath $missingPath -Force
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false -Case "Missing source file"

	Reset-Fixture
	$extraPath = Join-Path $temporarySnapshot (
		"karackencurllib/src/main/java/karacken/curl/UnexpectedCandidateSource.java"
	)
	Set-Content -LiteralPath $extraPath -Value "package karacken.curl;"
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false -Case "Extra source file"

	Reset-Fixture
	$candidate = Read-Candidate $temporaryRoot
	$candidate.sourceFiles[0].blob = "0000000000000000000000000000000000000000"
	Write-Candidate $temporaryRoot $candidate
	Invoke-Verifier `
		-Root $temporaryRoot `
		-ShouldPass $false `
		-Case "Wrong source blob"

	Reset-Fixture
	$candidate = Read-Candidate $temporaryRoot
	$candidate.licenseBlob = "0000000000000000000000000000000000000000"
	Write-Candidate $temporaryRoot $candidate
	Invoke-Verifier `
		-Root $temporaryRoot `
		-ShouldPass $false `
		-Case "Wrong license blob"

	Reset-Fixture
	$candidatePath = Join-Path $temporarySnapshot "candidate-source.json"
	Set-Content -LiteralPath $candidatePath -Value "{"
	Invoke-Verifier `
		-Root $temporaryRoot `
		-ShouldPass $false `
		-Case "Malformed candidate JSON"

	Reset-Fixture
	$candidate = Read-Candidate $temporaryRoot
	$candidatePath = Join-Path $temporarySnapshot "candidate-source.json"
	ConvertTo-Json -InputObject @($candidate) -Depth 8 |
		Set-Content -LiteralPath $candidatePath
	Invoke-Verifier `
		-Root $temporaryRoot `
		-ShouldPass $false `
		-Case "Candidate root array"

	Reset-Fixture
	$candidate = Read-Candidate $temporaryRoot
	$candidate | Add-Member -NotePropertyName "unexpected" -NotePropertyValue $true
	Write-Candidate $temporaryRoot $candidate
	Invoke-Verifier `
		-Root $temporaryRoot `
		-ShouldPass $false `
		-Case "Extra candidate JSON field"

	Reset-Fixture
	$candidate = Read-Candidate $temporaryRoot
	$candidate.apiVersion = 2
	Write-Candidate $temporaryRoot $candidate
	Invoke-Verifier `
		-Root $temporaryRoot `
		-ShouldPass $false `
		-Case "Candidate API mismatch"

	Reset-Fixture
	$candidate = Read-Candidate $temporaryRoot
	$candidate | Add-Member -NotePropertyName "tag" -NotePropertyValue "1.2.1"
	Write-Candidate $temporaryRoot $candidate
	Invoke-Verifier `
		-Root $temporaryRoot `
		-ShouldPass $false `
		-Case "Forged candidate release claim"

	Reset-Fixture
	$candidate = Read-Candidate $temporaryRoot
	$sourcePath = Join-Path $temporarySnapshot ([string]$candidate.sourceFiles[0].path)
	Add-Content -LiteralPath $sourcePath -Value "// tampered"
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false -Case "Source tampering"

	Reset-Fixture
	$generatedPath = Join-Path $temporarySnapshot (
		"karackencurllib/build/generated.bin"
	)
	New-Item -ItemType Directory -Path (Split-Path -Parent $generatedPath) -Force |
		Out-Null
	Set-Content -LiteralPath $generatedPath -Value "generated"
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false -Case "Generated build output"

	Reset-Fixture
	$provenancePath = Join-Path $temporarySnapshot "provenance.json"
	$provenance = Get-Content -LiteralPath $provenancePath -Raw | ConvertFrom-Json
	$provenance.apiVersion = $true
	$provenance | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $provenancePath
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false -Case "Non-integer immutable API version"

	Reset-Fixture
	$provenancePath = Join-Path $temporarySnapshot "provenance.json"
	$provenance = Get-Content -LiteralPath $provenancePath -Raw | ConvertFrom-Json
	$provenance.apiVersion = 3
	$provenance | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $provenancePath
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false -Case "Immutable API version drift"

	Reset-Fixture
	$provenancePath = Join-Path $temporarySnapshot "provenance.json"
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
