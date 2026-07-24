function Assert-ReaderExactPropertySet(
    [object] $Value,
    [string[]] $Expected,
    [string] $Context
) {
    if ($null -eq $Value) { throw "$Context is null" }
    $actual = @($Value.PSObject.Properties.Name)
    $expectedSet = [Collections.Generic.HashSet[string]]::new(
        [string[]]$Expected,
        [StringComparer]::Ordinal
    )
    $actualSet = [Collections.Generic.HashSet[string]]::new(
        [string[]]$actual,
        [StringComparer]::Ordinal
    )
    if ($actual.Count -ne $Expected.Count -or
        -not $expectedSet.SetEquals($actualSet)) {
        throw "$Context has an unknown or missing property"
    }
}

function Test-ReaderNonnegativeInteger([object] $Value) {
    if ($Value -isnot [byte] -and
        $Value -isnot [sbyte] -and
        $Value -isnot [int16] -and
        $Value -isnot [uint16] -and
        $Value -isnot [int32] -and
        $Value -isnot [uint32] -and
        $Value -isnot [int64] -and
        $Value -isnot [uint64]) {
        return $false
    }
    return [decimal]$Value -ge 0
}

function Assert-ReaderPrivacySafeStrings(
    [object] $Value,
    [string] $Context
) {
    $forbidden = [regex]::new(
        '://|href|cfi|bookId|publication|credential|transcript|' +
            'annotation|selectedText',
        [Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if ($Value -is [string]) {
        if ($forbidden.IsMatch($Value)) {
            throw "$Context contains a prohibited string"
        }
        return
    }
    if ($null -eq $Value -or
        $Value -is [ValueType]) {
        return
    }
    if ($Value -is [Collections.IDictionary]) {
        foreach ($entry in $Value.GetEnumerator()) {
            Assert-ReaderPrivacySafeStrings $entry.Value "$Context value"
        }
        return
    }
    if ($Value -is [Collections.IEnumerable]) {
        foreach ($entry in $Value) {
            Assert-ReaderPrivacySafeStrings $entry "$Context item"
        }
        return
    }
    foreach ($property in $Value.PSObject.Properties) {
        Assert-ReaderPrivacySafeStrings $property.Value `
            "$Context.$($property.Name)"
    }
}

function Assert-ReaderSmokeEvidence(
    [string] $Path,
    [string] $Context
) {
    try {
        $evidence = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        throw "$Context is not valid JSON"
    }
    Assert-ReaderExactPropertySet $evidence @(
        'SchemaVersion', 'Status', 'Package', 'DeviceSerial',
        'StartedUtc', 'CompletedUtc', 'Assertions', 'Counts', 'Geometry'
    ) $Context
    Assert-ReaderExactPropertySet $evidence.Assertions @(
        'ReaderForeground', 'ReaderPublicationReady', 'NoConsoleErrors',
        'NativeLongTap', 'NativeSwipe', 'NeutralVisualState',
        'SpreadGeometryValid', 'PositiveGutter', 'TextureStateValid'
    ) "$Context assertions"
    Assert-ReaderExactPropertySet $evidence.Counts @(
        'BridgeEvents', 'GestureEvents', 'GeometrySamples', 'TextureSamples'
    ) "$Context counts"
    Assert-ReaderExactPropertySet $evidence.Geometry @(
        'Mode', 'ViewportWidthPx', 'ViewportHeightPx', 'GutterPx'
    ) "$Context geometry"

    if (-not (Test-ReaderNonnegativeInteger $evidence.SchemaVersion) -or
        [long]$evidence.SchemaVersion -ne 1 -or
        $evidence.Status -cne 'complete' -or
        [string]::IsNullOrWhiteSpace([string]$evidence.Package) -or
        [string]::IsNullOrWhiteSpace([string]$evidence.DeviceSerial)) {
        throw "$Context has invalid identity or status"
    }
    $started = [DateTime]::MinValue
    $completed = [DateTime]::MinValue
    if (-not [DateTime]::TryParse(
            [string]$evidence.StartedUtc,
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind,
            [ref]$started
        ) -or
        -not [DateTime]::TryParse(
            [string]$evidence.CompletedUtc,
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind,
            [ref]$completed
        ) -or $completed -lt $started) {
        throw "$Context has invalid timestamps"
    }
    foreach ($property in $evidence.Assertions.PSObject.Properties) {
        if ($property.Value -isnot [bool] -or -not $property.Value) {
            throw "$Context assertion $($property.Name) is not true"
        }
    }
    foreach ($property in $evidence.Counts.PSObject.Properties) {
        if (-not (Test-ReaderNonnegativeInteger $property.Value)) {
            throw "$Context count $($property.Name) is not a nonnegative integer"
        }
    }
    if ($evidence.Geometry.Mode -cnotin @('single', 'spread') -or
        -not (Test-ReaderNonnegativeInteger $evidence.Geometry.ViewportWidthPx) -or
        -not (Test-ReaderNonnegativeInteger $evidence.Geometry.ViewportHeightPx) -or
        -not (Test-ReaderNonnegativeInteger $evidence.Geometry.GutterPx) -or
        [long]$evidence.Geometry.ViewportWidthPx -le 0 -or
        [long]$evidence.Geometry.ViewportHeightPx -le 0 -or
        ($evidence.Geometry.Mode -ceq 'spread' -and
            [long]$evidence.Geometry.GutterPx -le 0)) {
        throw "$Context has invalid geometry"
    }
    Assert-ReaderPrivacySafeStrings $evidence $Context
    return $evidence
}

function Get-ReaderRelativeArtifactPaths([string] $ResolvedRoot) {
    @(
        Get-ChildItem -LiteralPath $ResolvedRoot -Recurse -Force -File |
            ForEach-Object {
                [IO.Path]::GetRelativePath(
                    $ResolvedRoot,
                    $_.FullName
                ).Replace('\', '/')
            } |
            Sort-Object
    )
}

function Assert-ReaderPathSet(
    [string[]] $Actual,
    [string[]] $Expected,
    [string] $Context
) {
    $actualSet = [Collections.Generic.HashSet[string]]::new(
        [string[]]$Actual,
        [StringComparer]::Ordinal
    )
    $expectedSet = [Collections.Generic.HashSet[string]]::new(
        [string[]]$Expected,
        [StringComparer]::Ordinal
    )
    if ($Actual.Count -ne $actualSet.Count -or
        $Expected.Count -ne $expectedSet.Count -or
        -not $actualSet.SetEquals($expectedSet)) {
        throw "$Context artifact paths are not exact"
    }
}

function Assert-ReaderCompletionManifest(
    [string] $Root,
    [ValidateSet('smoke', 'komikku')][string] $Kind,
    [string[]] $ArtifactPaths
) {
    $path = Join-Path $Root 'run-complete.json'
    try {
        $completion = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    } catch {
        throw 'Reader completion manifest is not valid JSON'
    }
    Assert-ReaderExactPropertySet $completion @(
        'Status', 'AcceptanceId', 'GitCommit', 'ApkSha256', 'DeviceSerial',
        'DeviceClass', 'Kind', 'StartedUtc', 'CompletedUtc',
        'RequiredArtifact', 'Artifacts'
    ) 'Reader completion manifest'
    if ($completion.Status -cne 'complete' -or
        $completion.AcceptanceId -cnotmatch '^[0-9a-f]{32}$' -or
        $completion.GitCommit -cnotmatch '^[0-9a-f]{40}$' -or
        $completion.ApkSha256 -cnotmatch '^[0-9A-Fa-f]{64}$' -or
        [string]::IsNullOrWhiteSpace([string]$completion.DeviceSerial) -or
        $completion.DeviceClass -cnotin @('emulator', 'physical') -or
        $completion.Kind -cne $Kind) {
        throw 'Reader completion manifest identity is invalid'
    }
    $requiredArtifact = if ($Kind -eq 'smoke') {
        'privacy-safe-smoke.json'
    } else {
        'reader-matrix-summary.csv'
    }
    if ($completion.RequiredArtifact -cne $requiredArtifact) {
        throw 'Reader completion manifest required artifact is invalid'
    }
    $started = [DateTime]::MinValue
    $completed = [DateTime]::MinValue
    if (-not [DateTime]::TryParse(
            [string]$completion.StartedUtc,
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind,
            [ref]$started
        ) -or
        -not [DateTime]::TryParse(
            [string]$completion.CompletedUtc,
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind,
            [ref]$completed
        ) -or $completed -lt $started) {
        throw 'Reader completion manifest timestamps are invalid'
    }

    $artifacts = @($completion.Artifacts)
    if ($artifacts.Count -eq 0) {
        throw 'Reader completion manifest artifact inventory is empty'
    }
    $inventoryPaths = @()
    foreach ($artifact in $artifacts) {
        Assert-ReaderExactPropertySet $artifact @('Path', 'Sha256', 'Bytes') `
            'Reader completion artifact'
        $relative = [string]$artifact.Path
        if ([string]::IsNullOrWhiteSpace($relative) -or
            [IO.Path]::IsPathRooted($relative) -or
            $relative.Contains('\') -or
            $relative -match '(^|/)\.\.?(?:/|$)' -or
            $relative -ceq 'run-complete.json' -or
            $artifact.Sha256 -cnotmatch '^[0-9A-Fa-f]{64}$' -or
            -not (Test-ReaderNonnegativeInteger $artifact.Bytes)) {
            throw 'Reader completion artifact identity is invalid'
        }
        $inventoryPaths += $relative
        $artifactPath = Join-Path $Root $relative
        if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf) -or
            (Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath).Hash `
                -cne ([string]$artifact.Sha256).ToUpperInvariant() -or
            (Get-Item -LiteralPath $artifactPath).Length -ne
                [long]$artifact.Bytes) {
            throw "Reader completion artifact differs: $relative"
        }
    }
    Assert-ReaderPathSet `
        -Actual $inventoryPaths `
        -Expected @($ArtifactPaths | Where-Object { $_ -cne 'run-complete.json' }) `
        -Context 'Reader completion inventory'
    Assert-ReaderPrivacySafeStrings $completion 'Reader completion manifest'
}

function Assert-ReaderPrivacySafeArtifactTree {
    param(
        [Parameter(Mandatory = $true)][string] $Root,
        [Parameter(Mandatory = $true)]
        [ValidateSet('smoke', 'komikku')][string] $Kind,
        [switch] $AllowCompletionManifest
    )

    $resolvedRoot = (Resolve-Path -LiteralPath $Root).Path.TrimEnd('\', '/')
    if (-not (Test-Path -LiteralPath $resolvedRoot -PathType Container)) {
        throw "Privacy-safe artifact root is not a directory: $Root"
    }
    $nodes = @(
        Get-Item -LiteralPath $resolvedRoot -Force
        Get-ChildItem -LiteralPath $resolvedRoot -Recurse -Force
    )
    foreach ($node in $nodes) {
        if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Privacy-safe artifact tree contains a reparse point: $($node.FullName)"
        }
    }

    $actualPaths = @(Get-ReaderRelativeArtifactPaths $resolvedRoot)
    if (-not $AllowCompletionManifest -and
        $actualPaths -ccontains 'run-complete.json') {
        throw 'Completion manifest is permitted only while sealing evidence'
    }

    $expectedPaths = @()
    if ($Kind -eq 'smoke') {
        $expectedPaths = @('privacy-safe-smoke.json')
        [void](Assert-ReaderSmokeEvidence `
            (Join-Path $resolvedRoot 'privacy-safe-smoke.json') `
            'Standalone smoke evidence')
    } else {
        $summaryPath = Join-Path $resolvedRoot 'reader-matrix-summary.csv'
        if (-not (Test-Path -LiteralPath $summaryPath -PathType Leaf)) {
            throw 'Komikku privacy-safe summary is absent'
        }
        $rows = @(Import-Csv -LiteralPath $summaryPath)
        if ($rows.Count -eq 0) {
            throw 'Komikku privacy-safe summary has no rows'
        }
        $cases = [Collections.Generic.HashSet[string]]::new(
            [StringComparer]::Ordinal
        )
        $artifacts = [Collections.Generic.HashSet[string]]::new(
            [StringComparer]::Ordinal
        )
        $expectedPaths = @('reader-matrix-summary.csv')
        foreach ($row in $rows) {
            Assert-ReaderExactPropertySet $row @(
                'Package', 'DeviceSerial', 'Case', 'Status',
                'PrivacySafeArtifact'
            ) 'Komikku summary row'
            if ($row.Status -cne 'PASS' -or
                $row.Case -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$' -or
                -not $cases.Add([string]$row.Case)) {
                throw 'Komikku summary has an invalid or duplicate case'
            }
            $expectedArtifact = "$($row.Case)/privacy-safe-smoke.json"
            if ($row.PrivacySafeArtifact -cne $expectedArtifact -or
                -not $artifacts.Add([string]$row.PrivacySafeArtifact)) {
                throw 'Komikku summary has an invalid or duplicate artifact'
            }
            $expectedPaths += $expectedArtifact
            $smoke = Assert-ReaderSmokeEvidence `
                (Join-Path $resolvedRoot $expectedArtifact) `
                "Komikku case $($row.Case)"
            if ($smoke.Package -cne $row.Package -or
                $smoke.DeviceSerial -cne $row.DeviceSerial) {
                throw "Komikku case $($row.Case) identity differs from its summary"
            }
            Assert-ReaderPrivacySafeStrings $row "Komikku case $($row.Case)"
        }
    }
    if ($AllowCompletionManifest) {
        $expectedPaths += 'run-complete.json'
    }
    Assert-ReaderPathSet $actualPaths $expectedPaths `
        "Reader $Kind privacy-safe tree"
    if ($AllowCompletionManifest) {
        Assert-ReaderCompletionManifest `
            -Root $resolvedRoot `
            -Kind $Kind `
            -ArtifactPaths $actualPaths
    }
}
