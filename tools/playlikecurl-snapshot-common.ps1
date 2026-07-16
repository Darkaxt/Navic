$script:PlayLikeCurlTextExtensions = @(
	".gradle",
	".java",
	".json",
	".md",
	".pro",
	".properties",
	".txt",
	".xml"
)

function Get-PlayLikeCurlSha256 {
	param([byte[]] $Bytes)

	$sha256 = [System.Security.Cryptography.SHA256]::Create()
	try {
		return [Convert]::ToHexString($sha256.ComputeHash($Bytes)).ToLowerInvariant()
	} finally {
		$sha256.Dispose()
	}
}

function Get-PlayLikeCurlNormalizedFileBytes {
	param([System.IO.FileInfo] $File)

	$isText = $File.Name -eq ".gitignore" -or
		$script:PlayLikeCurlTextExtensions.Contains($File.Extension.ToLowerInvariant())
	if (-not $isText) {
		return [System.IO.File]::ReadAllBytes($File.FullName)
	}

	$text = [System.IO.File]::ReadAllText($File.FullName)
	$normalized = $text.Replace("`r`n", "`n").Replace("`r", "`n")
	return [System.Text.UTF8Encoding]::new($false).GetBytes($normalized)
}

function Test-PlayLikeCurlReparsePoint {
	param([System.IO.FileSystemInfo] $Entry)

	return $null -ne $Entry.LinkType -or
		(($Entry.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
}

function Get-PlayLikeCurlSnapshotFiles {
	param([string] $ModuleRoot)

	$resolvedModuleRoot = (Resolve-Path -LiteralPath $ModuleRoot).Path
	$entries = @(Get-ChildItem -LiteralPath $resolvedModuleRoot -Force -Recurse)
	$reparsePoints = @($entries | Where-Object { Test-PlayLikeCurlReparsePoint $_ })
	if ($reparsePoints.Count -gt 0) {
		throw "PlayLikeCurl snapshot contains symlinks or reparse points: $($reparsePoints[0].FullName)"
	}

	$files = @()
	foreach ($file in @($entries | Where-Object { -not $_.PSIsContainer })) {
		$relativePath = [System.IO.Path]::GetRelativePath(
			$resolvedModuleRoot,
			$file.FullName
		).Replace("\", "/")

		$isAllowed = $relativePath -in @(
			".gitignore",
			"build.gradle",
			"PRODUCTION_API.md",
			"proguard-rules.pro"
		) -or
			$relativePath.StartsWith("src/main/", [System.StringComparison]::Ordinal) -or
			$relativePath.StartsWith("src/test/", [System.StringComparison]::Ordinal)

		if (-not $isAllowed) {
			throw "Unexpected file in PlayLikeCurl snapshot: $relativePath"
		}
		if (
			$relativePath -match "(^|/)(build|\.gradle|\.idea)(/|$)" -or
			$relativePath.EndsWith(".iml", [System.StringComparison]::OrdinalIgnoreCase) -or
			$relativePath.EndsWith(".apk", [System.StringComparison]::OrdinalIgnoreCase) -or
			$relativePath.EndsWith(".aar", [System.StringComparison]::OrdinalIgnoreCase)
		) {
			throw "Generated file in PlayLikeCurl snapshot: $relativePath"
		}

		[byte[]] $bytes = Get-PlayLikeCurlNormalizedFileBytes $file
		$files += [pscustomobject]@{
			path = $relativePath
			sha256 = Get-PlayLikeCurlSha256 $bytes
		}
	}

	return @($files | Sort-Object path)
}

function Get-PlayLikeCurlSourceMetadata {
	param([string] $ModuleRoot)

	$files = @(Get-PlayLikeCurlSnapshotFiles $ModuleRoot)
	$manifestText = [string]::Join(
		"",
		@($files | ForEach-Object { "$($_.sha256)  $($_.path)`n" })
	)
	$manifestBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($manifestText)

	return [pscustomobject]@{
		digest = "sha256:$(Get-PlayLikeCurlSha256 $manifestBytes)"
		fileCount = $files.Count
		files = $files
	}
}

function Get-PlayLikeCurlFileDigest {
	param([string] $Path)

	$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
	return "sha256:$(Get-PlayLikeCurlSha256 ([System.IO.File]::ReadAllBytes($resolvedPath)))"
}

function Assert-PlayLikeCurlRemovalTarget {
	param(
		[string] $RepositoryRoot,
		[string] $TargetPath
	)

	$resolvedRepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd(
		[System.IO.Path]::DirectorySeparatorChar,
		[System.IO.Path]::AltDirectorySeparatorChar
	)
	$resolvedTargetPath = [System.IO.Path]::GetFullPath($TargetPath).TrimEnd(
		[System.IO.Path]::DirectorySeparatorChar,
		[System.IO.Path]::AltDirectorySeparatorChar
	)
	$expectedTargetPath = [System.IO.Path]::GetFullPath(
		(Join-Path $resolvedRepositoryRoot "third_party/playlikecurl")
	).TrimEnd(
		[System.IO.Path]::DirectorySeparatorChar,
		[System.IO.Path]::AltDirectorySeparatorChar
	)

	if (-not $resolvedTargetPath.Equals(
		$expectedTargetPath,
		[System.StringComparison]::OrdinalIgnoreCase
	)) {
		throw "Refusing to remove unexpected PlayLikeCurl path: $resolvedTargetPath"
	}
}
