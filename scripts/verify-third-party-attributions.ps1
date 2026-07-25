param(
	[string] $GeneratedAcknowledgementsPath = (Join-Path $PSScriptRoot "../composeApp/src/commonMain/composeResources/files/acknowledgements.json"),
	[string] $ApkPath
)

$ErrorActionPreference = "Stop"

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

function Normalize-Notice {
	param([string] $Text)
	return [regex]::Replace($Text, "\s+", " ").Trim()
}

function Read-AcknowledgementsFromApk {
	param([string] $Path)

	Add-Type -AssemblyName System.IO.Compression.FileSystem
	$archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path).Path)
	try {
		$entries = @($archive.Entries | Where-Object { $_.FullName.EndsWith("/acknowledgements.json") })
		Assert-Equal $entries.Count 1 "APK must contain exactly one generated acknowledgements resource."
		$reader = [System.IO.StreamReader]::new($entries[0].Open())
		try {
			return $reader.ReadToEnd()
		} finally {
			$reader.Dispose()
		}
	} finally {
		$archive.Dispose()
	}
}

function Assert-Acknowledgements {
	param(
		[hashtable] $Acknowledgements,
		[string] $SourceLabel,
		[string] $RepositoryRoot
	)

	$expectedLibraries = @(
		@{
			id = "anx-reader"
			version = "107f4fa74db0e7247c846c49d6211df3edf9887c"
			website = "https://github.com/Anxcye/anx-reader/commit/107f4fa74db0e7247c846c49d6211df3edf9887c"
			license = "anx-reader-mit"
			copyright = "Copyright (c) 2025 Anxcye"
			licenseFile = "third_party/licenses/Anx-Reader-MIT.txt"
		},
		@{
			id = "foliate-js"
			version = "1.0.1"
			website = "https://github.com/johnfactotum/foliate-js/commit/f52d42c6127d0ad981a2c67634113541b17ae01e"
			license = "foliate-js-mit"
			copyright = "Copyright (c) 2022 John Factotum"
			licenseFile = "third_party/licenses/foliate-js-MIT.txt"
		},
		@{
			id = "pdfjs-dist"
			version = "3.11.174"
			website = "https://github.com/mozilla/pdf.js/commit/ce87167432819f85df49b6b16c7a78556e9a4ee0"
			license = "Apache-2.0"
			copyright = "Copyright 2023 Mozilla Foundation"
			licenseFile = "third_party/licenses/PDF.js-Apache-2.0.txt"
		},
		@{
			id = "playlikecurl"
			version = "1.2.1"
			website = "https://github.com/Darkaxt/PlayLikeCurl/releases/tag/1.2.1"
			license = "playlikecurl-mit"
			copyright = "Originally created by Karan Kalsi; maintained fork by Darkaxt"
			licenseFile = "third_party/playlikecurl/LICENSE.txt"
		}
	)

	foreach ($expected in $expectedLibraries) {
		$matches = @($Acknowledgements.libraries | Where-Object { $_.uniqueId -eq $expected.id })
		Assert-Equal $matches.Count 1 "$SourceLabel must contain exactly one '$($expected.id)' library."
		$library = $matches[0]
		Assert-Equal $library.artifactVersion $expected.version "$SourceLabel has the wrong '$($expected.id)' version."
		Assert-Equal $library.website $expected.website "$SourceLabel has the wrong '$($expected.id)' source URL."
		Assert-Equal @($library.licenses).Count 1 "$SourceLabel must assign exactly one license to '$($expected.id)'."
		Assert-Equal $library.licenses[0] $expected.license "$SourceLabel has the wrong '$($expected.id)' license."
		if (-not $library.description.Contains($expected.copyright)) {
			throw "$SourceLabel omits the '$($expected.id)' copyright notice."
		}

		$license = $Acknowledgements.licenses[$expected.license]
		if ($null -eq $license) {
			throw "$SourceLabel omits license '$($expected.license)'."
		}
		$canonicalPath = Join-Path $RepositoryRoot $expected.licenseFile
		$canonical = Get-Content -LiteralPath $canonicalPath -Raw
		if ($expected.license -eq "Apache-2.0") {
			if (-not $canonical.Contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION") -or
				-not $license.content.Contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION")) {
				throw "$SourceLabel or the repository omits the Apache License 2.0 terms for PDF.js."
			}
		} elseif ((Normalize-Notice $license.content) -ne (Normalize-Notice $canonical)) {
			throw "$SourceLabel does not contain the exact '$($expected.id)' MIT notice."
		}
	}
}

$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$expectedPlayLikeCurlCommit = "a16ea9aa46484f3068242577e1189af66fb1fa9d"
$expectedPlayLikeCurlArtifactDigest = "sha256:4c356f44443b5a1abcd70851f062d38f136dcdcc67d72eb3a699a12126584bcd"
$script:playLikeCurlIdentity = "production API 2; source commit a16ea9aa46484f3068242577e1189af66fb1fa9d; release AAR SHA-256 4c356f44443b5a1abcd70851f062d38f136dcdcc67d72eb3a699a12126584bcd"
$provenancePath = Join-Path $repositoryRoot "third_party/playlikecurl/provenance.json"
$provenance = Get-Content -LiteralPath $provenancePath -Raw | ConvertFrom-Json
if ($provenance.tag -ne "1.2.1" -or $provenance.apiVersion -ne 2 -or
    $provenance.commit -ne $expectedPlayLikeCurlCommit -or
    $provenance.releaseArtifactDigest -ne $expectedPlayLikeCurlArtifactDigest) {
  throw "PlayLikeCurl provenance does not match the exact API-2 release identity."
}
$libraryMetadataPath = Join-Path $repositoryRoot "composeApp/aboutlibraries/libraries/playlikecurl.json"
$libraryMetadataText = Get-Content -LiteralPath $libraryMetadataPath -Raw
$noticesPath = Join-Path $repositoryRoot "THIRD_PARTY.md"
if (-not (Test-Path -LiteralPath $noticesPath -PathType Leaf)) {
	throw "THIRD_PARTY.md is missing."
}

$notices = Get-Content -LiteralPath $noticesPath -Raw
foreach ($requiredText in @(
	"GNU General Public License version 3",
	"107f4fa74db0e7247c846c49d6211df3edf9887c",
	"f52d42c6127d0ad981a2c67634113541b17ae01e",
	"ce87167432819f85df49b6b16c7a78556e9a4ee0",
	"a16ea9aa46484f3068242577e1189af66fb1fa9d",
	"https://github.com/Darkaxt/PlayLikeCurl/releases/tag/1.2.1"
)) {
	if (-not $notices.Contains($requiredText)) {
		throw "THIRD_PARTY.md omits '$requiredText'."
	}
}

if ($ApkPath) {
	$sourceLabel = "Packaged acknowledgements"
	$jsonText = Read-AcknowledgementsFromApk $ApkPath
} else {
	$sourceLabel = "Generated acknowledgements"
	$jsonText = Get-Content -LiteralPath $GeneratedAcknowledgementsPath -Raw
}

$acknowledgements = $jsonText | ConvertFrom-Json -AsHashtable
Assert-Acknowledgements $acknowledgements $sourceLabel $repositoryRoot
$playLikeCurlMatches = @($acknowledgements.libraries | Where-Object {
  $_.uniqueId -eq "playlikecurl"
})
Assert-Equal $playLikeCurlMatches.Count 1 "$sourceLabel must contain one PlayLikeCurl record."
$playLikeCurlNotice = [regex]::Match(
  $notices,
  "(?ms)^## PlayLikeCurl\r?$.*?(?=^## |\z)"
)
if (-not $playLikeCurlNotice.Success) {
  throw "THIRD_PARTY.md omits the PlayLikeCurl section."
}
foreach ($representation in @(
    @{ Label = "PlayLikeCurl metadata"; Text = $libraryMetadataText },
    @{ Label = "THIRD_PARTY.md PlayLikeCurl section"; Text = $playLikeCurlNotice.Value },
    @{ Label = "$SourceLabel PlayLikeCurl description"; Text = [string]$playLikeCurlMatches[0].description }
  )) {
  $identityCount = [regex]::Matches(
    $representation.Text,
    [regex]::Escape($script:playLikeCurlIdentity)
  ).Count
  Assert-Equal $identityCount 1 "$($representation.Label) has the wrong API/commit/AAR identity count."
}
Write-Host "$sourceLabel verified: Anx Reader, foliate-js, PDF.js, and PlayLikeCurl notices are complete."
