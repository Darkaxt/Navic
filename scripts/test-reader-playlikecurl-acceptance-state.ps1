$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
. (Join-Path $PSScriptRoot 'reader-playlikecurl-acceptance-state.ps1')

function Assert-Throws([scriptblock] $Action, [string] $Name) {
    $threw = $false
    try { & $Action } catch { $threw = $true }
    if (-not $threw) { throw "$Name did not throw" }
}

function Write-TestJson([string] $Path, $Value) {
    $Value | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $Path
}

function Get-TestArtifact([string] $Root, [string] $RelativePath) {
    $path = Join-Path $Root $RelativePath
    [pscustomobject][ordered]@{
        Path = $RelativePath.Replace('\', '/')
        Sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
        Bytes = (Get-Item -LiteralPath $path).Length
    }
}

function Update-TestRunManifest([string] $Root, [switch] $PinEmulator) {
    $path = Join-Path $Root 'run-complete.json'
    $run = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    $run.Artifacts = @($run.Artifacts | ForEach-Object {
        Get-TestArtifact $Root ([string]$_.Path)
    })
    Write-TestJson $path $run
    if ($PinEmulator) {
        $script:ReaderFrozenEmulatorRunCompleteSha256 = (
            Get-FileHash -Algorithm SHA256 -LiteralPath $path
        ).Hash
    }
}

$commit = '0123456789abcdef0123456789abcdef01234567'
$root = Join-Path ([IO.Path]::GetTempPath()) (
    "reader-acceptance-test-$([guid]::NewGuid().ToString('N'))"
)
$hostRoot = Join-Path $root 'host'
$apkRoot = Join-Path $root 'apk'
$emulatorRoot = Join-Path $root 'emulator'
$physicalRoot = Join-Path $root 'physical'
New-Item -ItemType Directory -Path @(
    $hostRoot,
    $apkRoot,
    (Join-Path $emulatorRoot 'post-stress'),
    (Join-Path $physicalRoot 'post-stress')
) -Force | Out-Null

try {
    [IO.File]::WriteAllBytes((Join-Path $apkRoot 'readerdev.apk'), [byte[]]::new(1000))
    $apkSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (
        Join-Path $apkRoot 'readerdev.apk'
    )).Hash
    $script:ReaderFrozenImplementationCommit = $commit
    $script:ReaderFrozenApkSha256 = $apkSha256
    $script:ReaderFrozenApkBytes = 1000L
    $script:ReaderFrozenVersionCode = 552L
    $script:ReaderFrozenVersionName = 'test-version'
    $script:ReaderFrozenHostTests = 100
    $script:ReaderFrozenHostFailures = 2

    Write-TestJson (Join-Path $hostRoot 'summary.json') ([ordered]@{
        SchemaVersion = 1
        GitCommit = $commit
        DeclaredTests = 100
        ParsedTests = 100
        Failures = 2
        Skipped = 0
        ReferenceFailures = 2
        NewFailures = 0
        MissingPriorTests = 0
        Acceptance = 'NoNewFailures'
    })
    Write-TestJson (Join-Path $apkRoot 'receipt.json') ([ordered]@{
        SchemaVersion = 1
        State = 'FrozenCommit'
        GitCommit = $commit
        RemoteBranch = 'fork/reader/playlikecurl-qa-remediation'
        Apk = 'readerdev.apk'
        ApkSha256 = $apkSha256
        ApkBytes = 1000
        VersionCode = 552
        VersionName = 'test-version'
    })

    foreach ($deviceRoot in @($emulatorRoot, $physicalRoot)) {
        $serial = if ($deviceRoot -eq $emulatorRoot) {
            'emulator-5554'
        } else {
            'physical-serial'
        }
        Write-TestJson (Join-Path $deviceRoot 'boundary-sweep-summary.json') ([ordered]@{
            SchemaVersion = 1
            MinimumCommittedTurns = 100
            ActualCommittedTurns = 100
            CompletedRelocations = 98
            RejectedRelocations = 2
            RecoveredRejectedRelocations = 2
            BoundaryTerminals = 1
            NextCommits = 53
            PreviousCommits = 47
            MinimumCommitsPerDirection = 10
            DistinctCommittedOrdinals = 20
            MinimumDistinctOrdinals = 20
        })
        Write-TestJson (Join-Path $deviceRoot 'post-stress/privacy-safe-smoke.json') ([ordered]@{
            SchemaVersion = 1
            Status = 'complete'
            Package = 'darkaxt.navic.readerdev'
            DeviceSerial = $serial
            Assertions = [ordered]@{
                ReaderForeground = $true
                ReaderPublicationReady = $true
                NoConsoleErrors = $true
                NativeLongTap = $true
                NativeSwipe = $true
                NeutralVisualState = $true
                SpreadGeometryValid = $true
                PositiveGutter = $true
                TextureStateValid = $true
            }
        })
        1..10 | ForEach-Object {
            Set-Content -LiteralPath (Join-Path $deviceRoot "ownership-after-close-$_.txt") -Value (
                "reader-ownership session=$_ phase=after-close residents=0 " +
                'adapterDecoded=0 cacheDecoded=0 staged=0 activeLeases=0 ' +
                'pendingLeases=0 releaseInFlightLeases=0 orphanLeases=0 ' +
                'textures=0 callbacks=0 relocationReservations=0 ' +
                'queuedRelocations=0 relocations=0 withinBounds=true'
            )
        }
        $artifactPaths = @(
            'boundary-sweep-summary.json',
            'post-stress/privacy-safe-smoke.json'
        ) + @(1..10 | ForEach-Object { "ownership-after-close-$_.txt" })
        $artifacts = @($artifactPaths | ForEach-Object {
            Get-TestArtifact $deviceRoot $_
        })
        Write-TestJson (Join-Path $deviceRoot 'run-complete.json') ([ordered]@{
            Status = 'complete'
            EvidenceMode = 'FrozenCommit'
            GitCommit = $commit
            RunId = "run-$serial"
            Package = 'darkaxt.navic.readerdev'
            DeviceSerial = $serial
            ApkSha256 = $apkSha256
            ApkBytes = 1000
            VersionCode = 552
            VersionName = 'test-version'
            Artifacts = $artifacts
        })
    }
    $script:ReaderFrozenEmulatorRunCompleteSha256 = (
        Get-FileHash -Algorithm SHA256 -LiteralPath (
            Join-Path $emulatorRoot 'run-complete.json'
        )
    ).Hash

    $automated = Assert-ReaderAutomatedAcceptanceEvidence `
        -ImplementationCommit $commit `
        -HostEvidenceRoot $hostRoot `
        -ApkEvidenceRoot $apkRoot `
        -EmulatorEvidenceRoot $emulatorRoot
    if ($automated.ImplementationCommit -cne $commit -or
        $automated.ApkSha256 -cne $apkSha256) {
        throw 'Automated evidence fixture returned the wrong identity'
    }
    Assert-Throws {
        Assert-ReaderAutomatedAcceptanceEvidence `
            -ImplementationCommit ('f' * 40) `
            -HostEvidenceRoot $hostRoot `
            -ApkEvidenceRoot $apkRoot `
            -EmulatorEvidenceRoot $emulatorRoot
    } 'unfrozen implementation commit fixture'

    $boundaryPath = Join-Path $emulatorRoot 'boundary-sweep-summary.json'
    $boundary = Get-Content -LiteralPath $boundaryPath -Raw | ConvertFrom-Json
    $boundary.CompletedRelocations = 0
    Write-TestJson $boundaryPath $boundary
    Update-TestRunManifest $emulatorRoot -PinEmulator
    Assert-Throws {
        Assert-ReaderAutomatedAcceptanceEvidence $commit $hostRoot $apkRoot $emulatorRoot
    } 'contradictory stress fixture'
    $boundary.CompletedRelocations = 98
    Write-TestJson $boundaryPath $boundary
    Update-TestRunManifest $emulatorRoot -PinEmulator

    $ownershipPath = Join-Path $emulatorRoot 'ownership-after-close-10.txt'
    (Get-Content -LiteralPath $ownershipPath -Raw).Replace(
        'session=10',
        'session=1'
    ) | Set-Content -LiteralPath $ownershipPath
    Update-TestRunManifest $emulatorRoot -PinEmulator
    Assert-Throws {
        Assert-ReaderAutomatedAcceptanceEvidence $commit $hostRoot $apkRoot $emulatorRoot
    } 'copied close ownership fixture'
    (Get-Content -LiteralPath $ownershipPath -Raw).Replace(
        'session=1',
        'session=10'
    ) | Set-Content -LiteralPath $ownershipPath
    Update-TestRunManifest $emulatorRoot -PinEmulator

    $smokePath = Join-Path $emulatorRoot 'post-stress/privacy-safe-smoke.json'
    $smoke = Get-Content -LiteralPath $smokePath -Raw | ConvertFrom-Json
    $smoke.Assertions.NativeSwipe = 'false'
    Write-TestJson $smokePath $smoke
    Update-TestRunManifest $emulatorRoot -PinEmulator
    Assert-Throws {
        Assert-ReaderAutomatedAcceptanceEvidence $commit $hostRoot $apkRoot $emulatorRoot
    } 'string-valued smoke assertion fixture'
    $smoke.Assertions.NativeSwipe = $true
    Write-TestJson $smokePath $smoke
    Update-TestRunManifest $emulatorRoot -PinEmulator

    $emulatorRunPath = Join-Path $emulatorRoot 'run-complete.json'
    $emulatorRun = Get-Content -LiteralPath $emulatorRunPath -Raw |
        ConvertFrom-Json
    $boundaryArtifact = @($emulatorRun.Artifacts | Where-Object {
        $_.Path -ceq 'boundary-sweep-summary.json'
    })[0]
    $emulatorRun.Artifacts += [pscustomobject][ordered]@{
        Path = 'BOUNDARY-SWEEP-SUMMARY.JSON'
        Sha256 = $boundaryArtifact.Sha256
        Bytes = $boundaryArtifact.Bytes
    }
    Write-TestJson $emulatorRunPath $emulatorRun
    $script:ReaderFrozenEmulatorRunCompleteSha256 = (
        Get-FileHash -Algorithm SHA256 -LiteralPath $emulatorRunPath
    ).Hash
    Assert-Throws {
        Assert-ReaderAutomatedAcceptanceEvidence $commit $hostRoot $apkRoot $emulatorRoot
    } 'case-variant duplicate artifact fixture'
    $emulatorRun.Artifacts = @($emulatorRun.Artifacts | Where-Object {
        $_.Path -cne 'BOUNDARY-SWEEP-SUMMARY.JSON'
    })
    Write-TestJson $emulatorRunPath $emulatorRun
    $script:ReaderFrozenEmulatorRunCompleteSha256 = (
        Get-FileHash -Algorithm SHA256 -LiteralPath $emulatorRunPath
    ).Hash

    $targets = @(Get-ReaderAcceptanceDeviceTargets `
        -AdbLines @(
            'List of devices attached',
            '',
            'emulator-5554 device product:sdk_phone model:sdk_phone device:emu64xa',
            '127.0.0.1:5555 device product:sdk_phone model:sdk_phone device:emu64xa',
            'physical-serial device product:phone model:Pixel device:phone'
        ) `
        -QemuBySerial @{
            'emulator-5554' = '1'
            '127.0.0.1:5555' = '1'
            'physical-serial' = ''
        })
    if (@($targets | Where-Object DeviceClass -ceq 'emulator').Count -ne 2 -or
        @($targets | Where-Object DeviceClass -ceq 'physical').Count -ne 1) {
        throw 'Device target fixture did not use ro.kernel.qemu identity'
    }
    Assert-Throws {
        Get-ReaderAcceptanceDeviceTargets `
            -AdbLines @(
                'List of devices attached',
                'emulator-5554 device product:sdk_phone model:sdk_phone device:emu64xa'
            ) `
            -QemuBySerial @{ 'emulator-5554' = '1' }
    } 'missing physical device fixture'

    Assert-ReaderAcceptanceSourceBoundary @(
        'scripts/reader-playlikecurl-acceptance-state.ps1',
        'scripts/test-reader-playlikecurl-acceptance-state.ps1',
        'docs/superpowers/reports/2026-07-18-reader-playlikecurl-qa-remediation-validation.md'
    )
    $headCommit = (
        git -C $script:ReaderRepositoryRoot rev-parse HEAD
    ).Trim()
    Assert-ReaderAcceptanceSourceBoundary @(
        Get-ReaderAcceptanceChangedPaths $headCommit
    )
    Assert-Throws {
        Assert-ReaderAcceptanceSourceBoundary @(
            'androidApp/src/main/kotlin/paige/navic/androidApp/MainActivity.kt'
        )
    } 'post-freeze production change fixture'

    $physicalRunComplete = Join-Path $physicalRoot 'run-complete.json'
    Write-TestJson (Join-Path $physicalRoot 'physical-manual-attestation.json') ([ordered]@{
        SchemaVersion = 1
        Status = 'complete'
        GitCommit = $commit
        ApkSha256 = $apkSha256
        RunCompleteSha256 = (
            Get-FileHash -Algorithm SHA256 -LiteralPath $physicalRunComplete
        ).Hash
        DeviceSerial = 'physical-serial'
        KernelQemu = $false
        Assertions = [ordered]@{
            Portrait = $true
            Landscape = $true
            Ltr = $true
            Rtl = $true
            Next = $true
            Previous = $true
            SnapBack = $true
            RapidTurns = $true
            NonCurlRollback = $true
            NoBlankOrStaleFrame = $true
            NoOrdinaryTurnLoadingCover = $true
        }
    })
    Assert-ReaderPhysicalAcceptanceEvidence `
        -ImplementationCommit $commit `
        -ApkSha256 $apkSha256 `
        -ApkBytes 1000 `
        -VersionCode 552 `
        -VersionName 'test-version' `
        -PhysicalEvidenceRoot $physicalRoot `
        -DeviceQemuValue '0'

    $attestationPath = Join-Path $physicalRoot 'physical-manual-attestation.json'
    $attestation = Get-Content -LiteralPath $attestationPath -Raw | ConvertFrom-Json
    $attestation.Assertions.SnapBack = 'false'
    Write-TestJson $attestationPath $attestation
    Assert-Throws {
        Assert-ReaderPhysicalAcceptanceEvidence `
            $commit $apkSha256 1000 552 'test-version' $physicalRoot '0'
    } 'string-valued manual assertion fixture'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host 'reader-playlikecurl-acceptance-state tests passed'
