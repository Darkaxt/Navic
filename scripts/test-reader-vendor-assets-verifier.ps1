[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$verifierPath = Join-Path $PSScriptRoot "verify-reader-vendor-assets.ps1"
$vendorRoot = Join-Path $repoRoot "composeApp/src/androidMain/assets/reader/vendor"
$manifestPath = Join-Path $vendorRoot "manifest.json"

if (-not (Test-Path -LiteralPath $verifierPath -PathType Leaf)) {
	throw "Missing reader vendor verifier: $verifierPath"
}
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
	throw "Missing reader vendor manifest: $manifestPath"
}

function Assert-VerificationFails {
	param(
		[Parameter(Mandatory)]
		[string] $FixtureRoot,
		[Parameter(Mandatory)]
		[string] $ExpectedMessage
	)

	try {
		& $verifierPath -VendorRoot $FixtureRoot -ManifestPath (Join-Path $FixtureRoot "manifest.json")
	} catch {
		if ($_.Exception.Message -notlike "*$ExpectedMessage*") {
			throw "Expected failure containing '$ExpectedMessage', got: $($_.Exception.Message)"
		}
		return
	}

	throw "Expected reader vendor verification to fail with '$ExpectedMessage'."
}

& $verifierPath

$tempParent = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$fixtureParent = Join-Path $tempParent ("navic-reader-vendor-verifier-" + [Guid]::NewGuid().ToString("N"))

try {
	$hashFixture = Join-Path $fixtureParent "hash-tamper"
	Copy-Item -LiteralPath $vendorRoot -Destination $hashFixture -Recurse
	$manifest = Get-Content -LiteralPath (Join-Path $hashFixture "manifest.json") -Raw | ConvertFrom-Json
	$tamperPath = Join-Path $hashFixture ([string] $manifest.files[0].path).Replace("/", [System.IO.Path]::DirectorySeparatorChar)
	[System.IO.File]::AppendAllText($tamperPath, "`nNAVIC_VERIFIER_TAMPER")
	Assert-VerificationFails -FixtureRoot $hashFixture -ExpectedMessage "Hash mismatch"

	$extraFixture = Join-Path $fixtureParent "extra-file"
	Copy-Item -LiteralPath $vendorRoot -Destination $extraFixture -Recurse
	[System.IO.File]::WriteAllText((Join-Path $extraFixture "unexpected.js"), "unexpected")
	Assert-VerificationFails -FixtureRoot $extraFixture -ExpectedMessage "Unmanifested vendor files"
} finally {
	$resolvedFixture = [System.IO.Path]::GetFullPath($fixtureParent)
	if ($resolvedFixture.StartsWith($tempParent, [System.StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $resolvedFixture)) {
		Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
	}
}

Write-Output "Reader vendor verifier self-test passed."
