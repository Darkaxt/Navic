[CmdletBinding()]
param(
    [ValidateSet('Library', 'Status', 'Preflight')]
    [string] $Operation = 'Library',
    [string] $ImplementationCommit,
    [string] $HostEvidenceRoot,
    [string] $ApkEvidenceRoot,
    [string] $EmulatorEvidenceRoot,
    [string] $PhysicalEvidenceRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

$script:ReaderRepositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:ReaderPackage = 'darkaxt.navic.readerdev'
$script:ReaderFrozenImplementationCommit = '118a001a7adb05c8a0b650a9433c45b5fbb6f80c'
$script:ReaderFrozenApkSha256 = '89F0740C0CE9419AFF6E08713ECD9C7F1423C673A64E491CC450DC80CBB3882A'
$script:ReaderFrozenApkBytes = 90852012L
$script:ReaderFrozenVersionCode = 555L
$script:ReaderFrozenVersionName = 'v1.0.11-iota28'
$script:ReaderFrozenHostTests = 2983
$script:ReaderFrozenHostFailures = 65
$script:ReaderFrozenEmulatorRunCompleteSha256 = 'AE12BE4E8808A49EE5B13EC777DC9245E4AEB799B8352C75C2DF1DCE39CE9D2E'

function Assert-ReaderAcceptancePwshCore {
    if ($PSVersionTable.PSEdition -ne 'Core' -or
        $PSVersionTable.PSVersion -lt [version]'7.3') {
        throw 'Reader acceptance requires PowerShell Core 7.3 or newer; invoke it with pwsh.'
    }
}

function Assert-ReaderAcceptanceCommit([string] $Commit) {
    if ($Commit -cne $script:ReaderFrozenImplementationCommit) {
        throw 'Reader acceptance must use the frozen implementation commit'
    }
}

function Resolve-ReaderAcceptancePath([string] $Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw 'Reader acceptance evidence path is empty'
    }
    if ([IO.Path]::IsPathRooted($Path)) {
        return [IO.Path]::GetFullPath($Path)
    }
    [IO.Path]::GetFullPath((Join-Path $script:ReaderRepositoryRoot $Path))
}

function Get-ReaderAcceptanceJson([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required reader acceptance evidence is absent: $Path"
    }
    Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Test-ReaderStrictTrue($Value) {
    $Value -is [bool] -and $Value -eq $true
}

function Get-ReaderAcceptanceChangedPaths([string] $BaseCommit) {
    $paths = [Collections.Generic.List[string]]::new()
    $queries = @(
        @('diff', '--name-only', "$BaseCommit..HEAD"),
        @('diff', '--name-only'),
        @('diff', '--name-only', '--cached'),
        @('ls-files', '--others', '--exclude-standard')
    )
    foreach ($query in $queries) {
        $result = @(git -C $script:ReaderRepositoryRoot @query)
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to inspect changes after the frozen implementation commit'
        }
        foreach ($path in $result) {
            if (-not [string]::IsNullOrWhiteSpace($path)) {
                $paths.Add($path)
            }
        }
    }
    @($paths | Sort-Object -Unique)
}

function Assert-ReaderAcceptanceSourceBoundary([string[]] $ChangedPaths) {
    $allowed = @(
        'scripts/adb-reader-playlikecurl-qa.ps1',
        'scripts/reader-playlikecurl-acceptance-state.ps1',
        'scripts/test-reader-playlikecurl-acceptance-state.ps1',
        'composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDevEnvironmentContractTest.kt',
        'docs/superpowers/reports/2026-07-18-reader-playlikecurl-qa-remediation-validation.md'
    )
    foreach ($path in $ChangedPaths) {
        $normalized = $path.Replace('\', '/')
        if ($normalized -cnotin $allowed) {
            throw "Production source changed after the frozen implementation commit: $normalized"
        }
    }
}

function Assert-ReaderRunArtifacts {
    param(
        [Parameter(Mandatory = $true)] $Run,
        [Parameter(Mandatory = $true)][string] $EvidenceRoot,
        [Parameter(Mandatory = $true)][string[]] $RequiredPaths
    )
    $resolvedRoot = [IO.Path]::GetFullPath($EvidenceRoot).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $rootPrefix = $resolvedRoot + [IO.Path]::DirectorySeparatorChar
    $seen = [Collections.Generic.HashSet[string]]::new(
        [StringComparer]::OrdinalIgnoreCase
    )
    $artifacts = @($Run.Artifacts)
    if ($artifacts.Count -eq 0) {
        throw 'Reader completion manifest contains no authenticated artifacts'
    }
    foreach ($artifact in $artifacts) {
        $relative = [string]$artifact.Path
        if ([string]::IsNullOrWhiteSpace($relative) -or
            [IO.Path]::IsPathRooted($relative) -or
            $relative.Contains('\') -or
            @($relative.Split('/') | Where-Object { $_ -ceq '..' }).Count -ne 0 -or
            -not $seen.Add($relative)) {
            throw "Reader completion artifact path is invalid: $relative"
        }
        $path = [IO.Path]::GetFullPath((Join-Path $resolvedRoot $relative))
        if (-not $path.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-Path -LiteralPath $path -PathType Leaf) -or
            (Get-Item -LiteralPath $path).Length -ne [long]$artifact.Bytes -or
            (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash -cne
                [string]$artifact.Sha256) {
            throw "Reader completion artifact differs from its manifest: $relative"
        }
    }
    foreach ($requiredPath in $RequiredPaths) {
        if (-not $seen.Contains($requiredPath)) {
            throw "Reader completion manifest omits required artifact: $requiredPath"
        }
    }
}

function Assert-ReaderRunEvidence {
    param(
        [Parameter(Mandatory = $true)][string] $ImplementationCommit,
        [Parameter(Mandatory = $true)][string] $ApkSha256,
        [Parameter(Mandatory = $true)][long] $ApkBytes,
        [Parameter(Mandatory = $true)][long] $VersionCode,
        [Parameter(Mandatory = $true)][string] $VersionName,
        [Parameter(Mandatory = $true)][string] $EvidenceRoot,
        [Parameter(Mandatory = $true)]
        [ValidateSet('emulator', 'physical')][string] $DeviceClass
    )
    if (Test-Path -LiteralPath (Join-Path $EvidenceRoot 'run-failed.json')) {
        throw "$DeviceClass acceptance contains run-failed.json"
    }
    $runCompletePath = Join-Path $EvidenceRoot 'run-complete.json'
    $run = Get-ReaderAcceptanceJson $runCompletePath
    if ($run.Status -cne 'complete' -or
        $run.EvidenceMode -cne 'FrozenCommit' -or
        $run.GitCommit -cne $ImplementationCommit -or
        $run.ApkSha256 -cne $ApkSha256 -or
        [long]$run.ApkBytes -ne $ApkBytes -or
        [long]$run.VersionCode -ne $VersionCode -or
        $run.VersionName -cne $VersionName -or
        [string]::IsNullOrWhiteSpace([string]$run.DeviceSerial)) {
        throw "$DeviceClass completion identity is invalid"
    }
    if ($DeviceClass -ceq 'emulator' -and
        (Get-FileHash -Algorithm SHA256 -LiteralPath $runCompletePath).Hash -cne
            $script:ReaderFrozenEmulatorRunCompleteSha256) {
        throw 'Emulator completion manifest differs from the frozen acceptance run'
    }

    $requiredArtifactPaths = @(
        'boundary-sweep-summary.json',
        'post-stress/privacy-safe-smoke.json'
    ) + @(1..10 | ForEach-Object { "ownership-after-close-$_.txt" })
    Assert-ReaderRunArtifacts $run $EvidenceRoot $requiredArtifactPaths

    $boundary = Get-ReaderAcceptanceJson (
        Join-Path $EvidenceRoot 'boundary-sweep-summary.json'
    )
    $counts = @(
        [int]$boundary.ActualCommittedTurns,
        [int]$boundary.CompletedRelocations,
        [int]$boundary.RejectedRelocations,
        [int]$boundary.RecoveredRejectedRelocations,
        [int]$boundary.BoundaryTerminals,
        [int]$boundary.NextCommits,
        [int]$boundary.PreviousCommits,
        [int]$boundary.DistinctCommittedOrdinals
    )
    if ($boundary.SchemaVersion -ne 1 -or
        @($counts | Where-Object { $_ -lt 0 }).Count -ne 0 -or
        [int]$boundary.ActualCommittedTurns -lt 100 -or
        [int]$boundary.ActualCommittedTurns -ne
            ([int]$boundary.NextCommits + [int]$boundary.PreviousCommits) -or
        [int]$boundary.ActualCommittedTurns -ne
            ([int]$boundary.CompletedRelocations + [int]$boundary.RejectedRelocations) -or
        [int]$boundary.NextCommits -lt 10 -or
        [int]$boundary.PreviousCommits -lt 10 -or
        [int]$boundary.DistinctCommittedOrdinals -lt 20 -or
        [int]$boundary.BoundaryTerminals -lt 1 -or
        [int]$boundary.RejectedRelocations -ne
            [int]$boundary.RecoveredRejectedRelocations) {
        throw "$DeviceClass boundary and stress coverage is incomplete or contradictory"
    }

    $smoke = Get-ReaderAcceptanceJson (
        Join-Path $EvidenceRoot 'post-stress/privacy-safe-smoke.json'
    )
    $requiredSmokeAssertions = @(
        'ReaderForeground',
        'ReaderPublicationReady',
        'NoConsoleErrors',
        'NativeLongTap',
        'NativeSwipe',
        'NeutralVisualState',
        'SpreadGeometryValid',
        'PositiveGutter',
        'TextureStateValid'
    )
    if ($smoke.SchemaVersion -ne 1 -or
        $smoke.Status -cne 'complete' -or
        $smoke.Package -cne $script:ReaderPackage -or
        $smoke.DeviceSerial -cne $run.DeviceSerial) {
        throw "$DeviceClass privacy-safe smoke identity is invalid"
    }
    foreach ($name in $requiredSmokeAssertions) {
        $property = $smoke.Assertions.PSObject.Properties[$name]
        if ($null -eq $property -or -not (Test-ReaderStrictTrue $property.Value)) {
            throw "$DeviceClass privacy-safe smoke assertion failed: $name"
        }
    }

    $zeroFields = @(
        'residents', 'adapterDecoded', 'cacheDecoded', 'staged',
        'activeLeases', 'pendingLeases', 'releaseInFlightLeases',
        'orphanLeases', 'textures', 'callbacks',
        'relocationReservations', 'queuedRelocations', 'relocations'
    )
    foreach ($cycle in 1..10) {
        $path = Join-Path $EvidenceRoot "ownership-after-close-$cycle.txt"
        $lines = @(
            Get-Content -LiteralPath $path |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        )
        if ($lines.Count -ne 1 -or
            $lines[0] -notmatch "(?:^|\s)session=$cycle(?:\s|$)" -or
            $lines[0] -notmatch '(?:^|\s)phase=after-close(?:\s|$)' -or
            $lines[0] -notmatch '(?:^|\s)withinBounds=true(?:\s|$)') {
            throw "$DeviceClass close cycle $cycle ownership snapshot is invalid"
        }
        foreach ($field in $zeroFields) {
            if ($lines[0] -notmatch "(?:^|\s)$field=0(?:\s|$)") {
                throw "$DeviceClass close cycle $cycle retained ownership for $field"
            }
        }
    }
    $run
}

function Assert-ReaderAutomatedAcceptanceEvidence {
    param(
        [Parameter(Mandatory = $true, Position = 0)][string] $ImplementationCommit,
        [Parameter(Mandatory = $true, Position = 1)][string] $HostEvidenceRoot,
        [Parameter(Mandatory = $true, Position = 2)][string] $ApkEvidenceRoot,
        [Parameter(Mandatory = $true, Position = 3)][string] $EmulatorEvidenceRoot
    )
    Assert-ReaderAcceptanceCommit $ImplementationCommit
    $hostEvidence = Get-ReaderAcceptanceJson (Join-Path $HostEvidenceRoot 'summary.json')
    if ($hostEvidence.SchemaVersion -ne 1 -or
        $hostEvidence.GitCommit -cne $ImplementationCommit -or
        [int]$hostEvidence.DeclaredTests -ne $script:ReaderFrozenHostTests -or
        [int]$hostEvidence.ParsedTests -ne $script:ReaderFrozenHostTests -or
        [int]$hostEvidence.Failures -ne $script:ReaderFrozenHostFailures -or
        [int]$hostEvidence.ReferenceFailures -ne $script:ReaderFrozenHostFailures -or
        [int]$hostEvidence.Skipped -ne 0 -or
        [int]$hostEvidence.NewFailures -ne 0 -or
        [int]$hostEvidence.MissingPriorTests -ne 0 -or
        $hostEvidence.Acceptance -cne 'NoNewFailures') {
        throw 'Frozen host evidence is not the accepted implementation baseline'
    }

    $apk = Get-ReaderAcceptanceJson (Join-Path $ApkEvidenceRoot 'receipt.json')
    $apkPath = Join-Path $ApkEvidenceRoot ([string]$apk.Apk)
    if ($apk.SchemaVersion -ne 1 -or
        $apk.State -cne 'FrozenCommit' -or
        $apk.GitCommit -cne $ImplementationCommit -or
        $apk.ApkSha256 -cne $script:ReaderFrozenApkSha256 -or
        [long]$apk.ApkBytes -ne $script:ReaderFrozenApkBytes -or
        [long]$apk.VersionCode -ne $script:ReaderFrozenVersionCode -or
        $apk.VersionName -cne $script:ReaderFrozenVersionName -or
        -not (Test-Path -LiteralPath $apkPath -PathType Leaf) -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $apkPath).Hash -cne
            $script:ReaderFrozenApkSha256 -or
        (Get-Item -LiteralPath $apkPath).Length -ne $script:ReaderFrozenApkBytes) {
        throw 'Frozen ReaderDev APK evidence is not the pinned implementation artifact'
    }

    [void](Assert-ReaderRunEvidence `
        -ImplementationCommit $ImplementationCommit `
        -ApkSha256 $script:ReaderFrozenApkSha256 `
        -ApkBytes $script:ReaderFrozenApkBytes `
        -VersionCode $script:ReaderFrozenVersionCode `
        -VersionName $script:ReaderFrozenVersionName `
        -EvidenceRoot $EmulatorEvidenceRoot `
        -DeviceClass emulator)

    [pscustomobject][ordered]@{
        ImplementationCommit = $ImplementationCommit
        ApkSha256 = $script:ReaderFrozenApkSha256
        ApkBytes = $script:ReaderFrozenApkBytes
        VersionCode = $script:ReaderFrozenVersionCode
        VersionName = $script:ReaderFrozenVersionName
        EmulatorStatus = 'Passed'
    }
}

function ConvertTo-ReaderKernelQemuValue {
    param(
        [AllowEmptyString()]
        [string] $Value,
        [Parameter(Mandatory = $true)]
        [string] $DeviceSerial
    )
    $normalized = $Value.Trim()
    if ($normalized -ceq '1') {
        return '1'
    }
    if ($normalized -ceq '' -or $normalized -ceq '0') {
        return '0'
    }
    throw "Invalid emulator identity for $DeviceSerial"
}

function Get-ReaderDeviceQemuValue([string] $DeviceSerial) {
    if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
        throw 'Reader acceptance device serial is empty'
    }
    $value = (@(
        adb -s $DeviceSerial shell getprop ro.kernel.qemu
    ) -join '').Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to determine emulator identity for $DeviceSerial"
    }
    ConvertTo-ReaderKernelQemuValue $value $DeviceSerial
}

function Assert-ReaderPhysicalAcceptanceEvidence {
    param(
        [Parameter(Mandatory = $true, Position = 0)][string] $ImplementationCommit,
        [Parameter(Mandatory = $true, Position = 1)][string] $ApkSha256,
        [Parameter(Mandatory = $true, Position = 2)][long] $ApkBytes,
        [Parameter(Mandatory = $true, Position = 3)][long] $VersionCode,
        [Parameter(Mandatory = $true, Position = 4)][string] $VersionName,
        [Parameter(Mandatory = $true, Position = 5)][string] $PhysicalEvidenceRoot,
        [Parameter(Position = 6)][string] $DeviceQemuValue
    )
    $run = Get-ReaderAcceptanceJson (Join-Path $PhysicalEvidenceRoot 'run-complete.json')
    [void](Assert-ReaderRunEvidence `
        -ImplementationCommit $ImplementationCommit `
        -ApkSha256 $ApkSha256 `
        -ApkBytes $ApkBytes `
        -VersionCode $VersionCode `
        -VersionName $VersionName `
        -EvidenceRoot $PhysicalEvidenceRoot `
        -DeviceClass physical)
    $qemu = if ([string]::IsNullOrWhiteSpace($DeviceQemuValue)) {
        Get-ReaderDeviceQemuValue ([string]$run.DeviceSerial)
    } else {
        $DeviceQemuValue
    }
    if ($qemu -cne '0') {
        throw 'Physical acceptance evidence was not captured on physical hardware'
    }

    $runCompletePath = Join-Path $PhysicalEvidenceRoot 'run-complete.json'
    $attestation = Get-ReaderAcceptanceJson (
        Join-Path $PhysicalEvidenceRoot 'physical-manual-attestation.json'
    )
    if ($attestation.SchemaVersion -ne 1 -or
        $attestation.Status -cne 'complete' -or
        $attestation.GitCommit -cne $ImplementationCommit -or
        $attestation.ApkSha256 -cne $ApkSha256 -or
        $attestation.RunCompleteSha256 -cne
            (Get-FileHash -Algorithm SHA256 -LiteralPath $runCompletePath).Hash -or
        $attestation.DeviceSerial -cne $run.DeviceSerial -or
        -not ($attestation.KernelQemu -is [bool]) -or
        $attestation.KernelQemu -ne $false) {
        throw 'Physical manual attestation identity is invalid'
    }
    $requiredManualAssertions = @(
        'Portrait', 'Landscape', 'Ltr', 'Rtl', 'Next', 'Previous',
        'SnapBack', 'RapidTurns', 'NonCurlRollback',
        'NoBlankOrStaleFrame', 'NoOrdinaryTurnLoadingCover'
    )
    foreach ($name in $requiredManualAssertions) {
        $property = $attestation.Assertions.PSObject.Properties[$name]
        if ($null -eq $property -or -not (Test-ReaderStrictTrue $property.Value)) {
            throw "Physical manual assertion failed: $name"
        }
    }
}

function Get-ReaderAcceptanceDeviceTargets {
    param(
        [Parameter(Mandatory = $true, Position = 0)]
        [AllowEmptyString()]
        [string[]] $AdbLines,
        [hashtable] $QemuBySerial
    )
    $online = @(
        $AdbLines | ForEach-Object {
            if ($_ -match '^(?<Serial>\S+)\s+device(?:\s+.*)?$') {
                $Matches.Serial
            }
        }
    )
    $targets = @(
        $online | ForEach-Object {
            $serial = $_
            $qemu = if ($null -ne $QemuBySerial) {
                if (-not $QemuBySerial.ContainsKey($serial)) {
                    throw "Missing emulator identity for $serial"
                }
                ConvertTo-ReaderKernelQemuValue `
                    ([string]$QemuBySerial[$serial]) `
                    $serial
            } else {
                Get-ReaderDeviceQemuValue $serial
            }
            [pscustomobject][ordered]@{
                DeviceSerial = $serial
                DeviceClass = if ($qemu -ceq '1') { 'emulator' } else { 'physical' }
                KernelQemu = $qemu -ceq '1'
            }
        }
    )
    foreach ($requiredClass in @('emulator', 'physical')) {
        if (@($targets | Where-Object DeviceClass -ceq $requiredClass).Count -lt 1) {
            throw "Reader acceptance requires an online $requiredClass device"
        }
    }
    $targets
}

Assert-ReaderAcceptancePwshCore

if ($Operation -ne 'Library') {
    if ([string]::IsNullOrWhiteSpace($ImplementationCommit) -or
        [string]::IsNullOrWhiteSpace($HostEvidenceRoot) -or
        [string]::IsNullOrWhiteSpace($ApkEvidenceRoot) -or
        [string]::IsNullOrWhiteSpace($EmulatorEvidenceRoot)) {
        throw 'Status and preflight require frozen implementation evidence inputs'
    }
    $hostRoot = Resolve-ReaderAcceptancePath $HostEvidenceRoot
    $apkRoot = Resolve-ReaderAcceptancePath $ApkEvidenceRoot
    $emulatorRoot = Resolve-ReaderAcceptancePath $EmulatorEvidenceRoot
    $automated = Assert-ReaderAutomatedAcceptanceEvidence `
        $ImplementationCommit $hostRoot $apkRoot $emulatorRoot
    $changedPaths = @(
        Get-ReaderAcceptanceChangedPaths $ImplementationCommit
    )
    Assert-ReaderAcceptanceSourceBoundary $changedPaths

    if ($Operation -eq 'Preflight') {
        $targets = @(Get-ReaderAcceptanceDeviceTargets @(adb devices -l))
        $targets | ConvertTo-Json -Depth 4
    } else {
        $physicalStatus = 'BlockedPhysicalDevice'
        if (-not [string]::IsNullOrWhiteSpace($PhysicalEvidenceRoot)) {
            $physicalRoot = Resolve-ReaderAcceptancePath $PhysicalEvidenceRoot
            Assert-ReaderPhysicalAcceptanceEvidence `
                -ImplementationCommit $ImplementationCommit `
                -ApkSha256 $automated.ApkSha256 `
                -ApkBytes $automated.ApkBytes `
                -VersionCode $automated.VersionCode `
                -VersionName $automated.VersionName `
                -PhysicalEvidenceRoot $physicalRoot
            $physicalStatus = 'Passed'
        }
        [ordered]@{
            SchemaVersion = 1
            ImplementationCommit = $ImplementationCommit
            AcceptanceToolingCommit = (
                git -C $script:ReaderRepositoryRoot rev-parse HEAD
            ).Trim()
            ApkSha256 = $automated.ApkSha256
            EmulatorStatus = 'Passed'
            PhysicalStatus = $physicalStatus
            ReleaseReady = $physicalStatus -ceq 'Passed'
        } | ConvertTo-Json
    }
}
