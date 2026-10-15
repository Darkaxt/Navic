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
		[string] $Case,
		[string] $ExpectedMessage = "",
		[string] $Verifier = $verifierPath
	)

	$output = & pwsh -NoProfile -File $Verifier -RepositoryRoot $Root 2>&1
	$exitCode = $LASTEXITCODE
	Write-Host "$Case (exit=$exitCode)"
	if ($ShouldPass -and $exitCode -ne 0) {
		if (($output -join "`n") -like "*Candidate physical source inventory does not match the closed manifest.*") {
			Write-Host "Source diagnostic: closed physical inventory mismatch."
		}
		throw "$Case should pass, but failed with exit code $exitCode."
	}
	if (-not $ShouldPass -and $exitCode -eq 0) {
		throw "$Case should fail, but passed."
	}
	if ($ExpectedMessage -ne "" -and
		($output -join "`n").Replace("`r", "") -notlike "*$ExpectedMessage*") {
		throw "$Case did not reach the expected source-verification diagnostic."
	}
	$script:verifiedCaseCount++
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

$script:verifiedCaseCount = 0

function Write-Downstream {
	param([string] $Root, [object] $Descriptor)

	$path = Join-Path $Root "third_party/playlikecurl/downstream-source.json"
	$json = ($Descriptor | ConvertTo-Json -Depth 8).Replace("`r`n", "`n") + "`n"
	[System.IO.File]::WriteAllText($path, $json, [System.Text.UTF8Encoding]::new($false))
}

function Read-Downstream {
	param([string] $Root)

	return Get-Content -LiteralPath (
		Join-Path $Root "third_party/playlikecurl/downstream-source.json"
	) -Raw | ConvertFrom-Json
}

function New-DownstreamFixtureDescriptor {
	# Independently reconstruct the expected six-row delta; never change production trust.
	$candidate = Read-Candidate $repositoryRootPath
	$modifiedFiles = @()
	$addedFiles = @()
	foreach ($name in @("PageSurfaceListener", "PageSurfaceView", "PageRenderer")) {
		$path = "karackencurllib/src/main/java/karacken/curl/$name.java"
		$before = @($candidate.sourceFiles | Where-Object { $_.path -ceq $path })
		if ($before.Count -ne 1) { throw "Fixture modified path has no unique baseline." }
		$blob = & git -C $repositoryRootPath hash-object -- "third_party/playlikecurl/$path"
		if ($LASTEXITCODE -ne 0) { throw "Unable to hash fixture modified source." }
		$modifiedFiles += [ordered]@{
			mode = $before[0].mode
			beforeBlob = $before[0].blob
			blob = $blob
			path = $path
		}
	}
	foreach ($name in @(
		"CurlSettlementCompletionContractTest",
		"PageRendererContextGenerationTest",
		"PageRendererCurlSettlementDrawTest"
	)) {
		$path = "karackencurllib/src/test/java/karacken/curl/$name.java"
		$blob = & git -C $repositoryRootPath hash-object -- "third_party/playlikecurl/$path"
		if ($LASTEXITCODE -ne 0) { throw "Unable to hash fixture added source." }
		$addedFiles += [ordered]@{ mode = "100644"; blob = $blob; path = $path }
	}
	return [ordered]@{
		schemaVersion = 1
		downstream = "Navic"
		apiVersion = 3
		baseCommit = $candidate.commit
		baseSourceManifestSha256 = $candidate.sourceManifestSha256
		modifiedFiles = $modifiedFiles
		addedFiles = $addedFiles
	}
}

function Invoke-TrustedDescriptorRefusal {
	param([string] $Case, [string] $ExpectedMessage)

	# Exercise structural validation behind the digest gate using a private fixture verifier.
	# This test-only re-pin cannot affect the repository verifier or canonical descriptor.
	$fixtureScripts = Join-Path $temporaryRoot "scripts"
	$fixtureTools = Join-Path $temporaryRoot "tools"
	New-Item -ItemType Directory -Path $fixtureScripts, $fixtureTools -Force | Out-Null
	Copy-Item -LiteralPath (Join-Path $repositoryRootPath "tools/playlikecurl-snapshot-common.ps1") `
		-Destination $fixtureTools -Force
	$descriptorPath = Join-Path $temporarySnapshot "downstream-source.json"
	$digest = (Get-FileHash -LiteralPath $descriptorPath -Algorithm SHA256).Hash.ToLowerInvariant()
	$verifier = Get-Content -LiteralPath $verifierPath -Raw
	$pinPattern = '(?m)^\$expectedDownstreamSha256 = "[0-9a-f]{64}"\r?$'
	if ([regex]::Matches($verifier, $pinPattern).Count -ne 1) {
		throw "Private fixture verifier has no unique downstream digest pin."
	}
	$verifier = [regex]::Replace($verifier, $pinPattern, ('$$expectedDownstreamSha256 = "' + $digest + '"'))
	$fixtureVerifier = Join-Path $fixtureScripts "verify-playlikecurl-snapshot.ps1"
	[System.IO.File]::WriteAllText($fixtureVerifier, $verifier, [System.Text.UTF8Encoding]::new($false))
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false -Case $Case `
		-ExpectedMessage $ExpectedMessage -Verifier $fixtureVerifier
}

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
	Write-Downstream $temporaryRoot (New-DownstreamFixtureDescriptor)
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $true `
		-Case "Legitimate downstream patched reader" `
		-ExpectedMessage "PlayLikeCurl downstream patched reader verified"

	Invoke-Verifier -Root $repositoryRootPath -ShouldPass $true `
		-Case "Canonical downstream patched reader" `
		-ExpectedMessage "PlayLikeCurl downstream patched reader verified"

	Reset-Fixture
	$descriptor = Read-Downstream $temporaryRoot
	foreach ($file in $descriptor.modifiedFiles) {
		$content = @(& git -C $repositoryRootPath cat-file blob $file.beforeBlob)
		if ($LASTEXITCODE -ne 0) { throw "Unable to reconstruct fixture baseline source." }
		[System.IO.File]::WriteAllText(
			(Join-Path $temporarySnapshot $file.path),
			($content -join "`n") + "`n",
			[System.Text.UTF8Encoding]::new($false)
		)
	}
	foreach ($file in $descriptor.addedFiles) {
		Remove-Item -LiteralPath (Join-Path $temporarySnapshot $file.path) -Force
	}
	Remove-Item -LiteralPath (Join-Path $temporarySnapshot "downstream-source.json") -Force
	Invoke-TestGit $temporaryRoot @("add", "--all") "Unable to index baseline fixture"
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $true `
		-Case "Unmodified upstream API-3 baseline without downstream descriptor" `
		-ExpectedMessage "PlayLikeCurl source candidate verified"

	Reset-Fixture
	Remove-Item -LiteralPath (Join-Path $temporarySnapshot "downstream-source.json") -Force
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false `
		-Case "Missing downstream descriptor with patched source" `
		-ExpectedMessage "Candidate physical source inventory does not match the closed manifest."

	Reset-Fixture
	Add-Content -LiteralPath (Join-Path $temporarySnapshot "downstream-source.json") -Value " "
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false `
		-Case "Tampered downstream descriptor bytes" `
		-ExpectedMessage "Downstream source descriptor digest is not trusted."

	Reset-Fixture
	$descriptor = Read-Downstream $temporaryRoot
	$descriptor.modifiedFiles[0].blob = "0000000000000000000000000000000000000000"
	Write-Downstream $temporaryRoot $descriptor
	Invoke-Verifier -Root $temporaryRoot -ShouldPass $false `
		-Case "Unpinned downstream source revision" `
		-ExpectedMessage "Downstream source descriptor digest is not trusted."

	$structuralCases = @(
		@{ label = "Wrong downstream base commit"; message = "Wrong downstream base commit."; mutate = { param($d) $d.baseCommit = "0000000000000000000000000000000000000000" } },
		@{ label = "Wrong downstream base manifest"; message = "Wrong downstream base source manifest digest."; mutate = { param($d) $d.baseSourceManifestSha256 = "0" * 64 } },
		@{ label = "Wrong downstream before blob"; message = "Downstream modification does not match its baseline blob."; mutate = { param($d) $d.modifiedFiles[0].beforeBlob = "0000000000000000000000000000000000000000" } },
		@{ label = "Unknown downstream field"; message = "Downstream source descriptor has unknown, missing, or mis-cased fields."; mutate = { param($d) $d | Add-Member -NotePropertyName "release" -NotePropertyValue "1.2.1" } },
		@{ label = "Mis-cased downstream field"; message = "Downstream source descriptor has unknown, missing, or mis-cased fields."; mutate = { param($d) $v = $d.baseCommit; $d.PSObject.Properties.Remove("baseCommit"); $d | Add-Member -NotePropertyName "BaseCommit" -NotePropertyValue $v } },
		@{ label = "Non-integer downstream schema"; message = "Wrong downstream schema version."; mutate = { param($d) $d.schemaVersion = $true } },
		@{ label = "Wrong downstream API version"; message = "Wrong downstream API version."; mutate = { param($d) $d.apiVersion = 2 } },
		@{ label = "Downstream modifications are not an array"; message = "Downstream source delta must contain three modifications and three additions."; mutate = { param($d) $d.modifiedFiles = $d.modifiedFiles[0] } },
		@{ label = "Unknown modified row field"; message = "Downstream source entry has unknown, missing, or mis-cased fields."; mutate = { param($d) $d.modifiedFiles[0] | Add-Member -NotePropertyName "ignore" -NotePropertyValue $true } },
		@{ label = "Non-string downstream mode"; message = "Downstream source entry has an invalid Git mode."; mutate = { param($d) $d.addedFiles[0].mode = 100644 } },
		@{ label = "Invalid downstream blob"; message = "Downstream source entry has an invalid Git blob."; mutate = { param($d) $d.addedFiles[0].blob = "bad" } },
		@{ label = "Downstream path traversal"; message = "Downstream source entry has an invalid path."; mutate = { param($d) $d.addedFiles[0].path = "karackencurllib/src/test/../Unexpected.java" } },
		@{ label = "Modified path absent from baseline"; message = "Downstream modification has no exact baseline path."; mutate = { param($d) $d.modifiedFiles[0].path = "karackencurllib/src/main/java/karacken/curl/Unknown.java" } },
		@{ label = "Duplicate downstream modification"; message = "Downstream source path is duplicated or already present."; mutate = { param($d) $d.modifiedFiles[1] = $d.modifiedFiles[0] } },
		@{ label = "Downstream addition collides with baseline"; message = "Downstream source path is duplicated or already present."; mutate = { param($d) $d.addedFiles[0].path = $d.modifiedFiles[0].path } },
		@{ label = "Duplicate downstream addition"; message = "Downstream source path is duplicated or already present."; mutate = { param($d) $d.addedFiles[1] = $d.addedFiles[0] } }
	)
	foreach ($case in $structuralCases) {
		Reset-Fixture
		$descriptor = Read-Downstream $temporaryRoot
		& $case.mutate $descriptor
		Write-Downstream $temporaryRoot $descriptor
		Invoke-TrustedDescriptorRefusal $case.label $case.message
	}

	Reset-Fixture
	Set-Content -LiteralPath (Join-Path $temporarySnapshot "downstream-source.json") -Value "{"
	Invoke-TrustedDescriptorRefusal "Malformed downstream JSON" ""

	Reset-Fixture
	$descriptor = Read-Downstream $temporaryRoot
	ConvertTo-Json -InputObject @($descriptor) -Depth 8 |
		Set-Content -LiteralPath (Join-Path $temporarySnapshot "downstream-source.json")
	Invoke-TrustedDescriptorRefusal "Downstream root array" "Downstream source descriptor root must be a JSON object."

	Reset-Fixture
	$descriptorPath = Join-Path $temporarySnapshot "downstream-source.json"
	$json = Get-Content -LiteralPath $descriptorPath -Raw
	$json.Replace('"schemaVersion": 1,', '"schemaVersion": 1, "schemaVersion": 1,') |
		Set-Content -LiteralPath $descriptorPath
	Invoke-TrustedDescriptorRefusal "Duplicate downstream JSON property" "Downstream source descriptor contains duplicate property"

	foreach ($kind in @("modifiedFiles", "addedFiles")) {
		Reset-Fixture
		$descriptor = Read-Downstream $temporaryRoot
		Add-Content -LiteralPath (Join-Path $temporarySnapshot $descriptor.$kind[0].path) `
			-Value "// tampered downstream source"
		Invoke-Verifier -Root $temporaryRoot -ShouldPass $false `
			-Case "Downstream $kind source tampering" `
			-ExpectedMessage "Candidate source blob drift"
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

Write-Host "PlayLikeCurl snapshot verifier tests passed ($script:verifiedCaseCount cases)."
