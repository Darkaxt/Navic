param(
    [Parameter(Mandatory = $true)][string] $DeviceSerial,
    [Parameter(Mandatory = $true)][string] $EnvFile,
    [Parameter(Mandatory = $true)][string] $SealedApkPath,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9A-Fa-f]{64}$')][string] $ExpectedApkSha256,
    [Parameter(Mandatory = $true)][ValidateRange(1, [long]::MaxValue)]
    [long] $ExpectedApkBytes,
    [Parameter(Mandatory = $true)][ValidateRange(1, [long]::MaxValue)]
    [long] $ExpectedVersionCode,
    [Parameter(Mandatory = $true)][AllowEmptyString()][string] $ExpectedVersionName,
    [switch] $NoInstall,
    [string] $ArtifactRoot = ".codex-validation\reader-playlikecurl",
    [int] $StressTurns = 100,
    [int] $OpenCloseCycles = 10,
    [ValidateSet('FrozenCommit', 'PrecommitCandidate')]
    [string] $EvidenceMode = 'FrozenCommit',
    [string] $CandidateTreeSha256 = ''
)

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false
$readerLaunchTimeoutSeconds = 120
if ($PSVersionTable.PSEdition -ne 'Core' -or
    $PSVersionTable.PSVersion -lt [version]'7.3') {
    throw 'Reader QA requires PowerShell Core 7.3 or newer; invoke it with pwsh.'
}
if ($StressTurns -lt 1) { throw "StressTurns must be positive" }
if ($OpenCloseCycles -lt 1) { throw "OpenCloseCycles must be positive" }

function Get-RunnerOutsideValidationStatus {
    $statusLines = @(git status --porcelain --untracked-files=all)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect runner worktree' }
    return @(
        $statusLines | Where-Object { $_ -notmatch '^\?\? \.codex-validation/' }
    )
}

function Get-PrecommitCandidateTreeSha256 {
    $trackedStatus = @(git diff HEAD --name-status --no-renames)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect candidate tracked status' }
    foreach ($line in $trackedStatus) {
        if ($line -notmatch '^(?<Status>[AM])\s+(?<Path>.+)$') {
            throw "Precommit candidate contains a non-add/modify status: $line"
        }
    }
    $trackedPaths = @($trackedStatus | ForEach-Object {
        ([regex]::Match($_, '^[AM]\s+(.+)$')).Groups[1].Value
    })
    $untrackedPaths = @(git ls-files --others --exclude-standard | Where-Object {
        -not $_.StartsWith('.codex-validation/')
    })
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect candidate untracked paths' }
    $candidatePaths = @($trackedPaths + $untrackedPaths | Sort-Object -Unique)
    if ($candidatePaths.Count -eq 0) {
        throw 'PrecommitCandidate mode requires source changes'
    }
    $candidateRecords = @(
        foreach ($relative in $candidatePaths) {
            if (-not (Test-Path -LiteralPath $relative -PathType Leaf)) {
                throw "Candidate path is absent or not a file: $relative"
            }
            $normalized = $relative.Replace('\', '/')
            $sha = (Get-FileHash -Algorithm SHA256 -LiteralPath $relative).Hash
            "$normalized=$sha"
        }
    )
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString(
            $hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes(
                ($candidateRecords -join "`n") + "`n"
            ))
        ).Replace('-', '')
    } finally {
        $hasher.Dispose()
    }
}

$ArtifactRoot = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ArtifactRoot)
if (Test-Path -LiteralPath $ArtifactRoot) {
    throw "ArtifactRoot already exists; use a fresh run directory: $ArtifactRoot"
}
New-Item -ItemType Directory -Path $ArtifactRoot | Out-Null
$runId = [guid]::NewGuid().ToString('N')
$gitCommit = (git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $gitCommit -notmatch '^[0-9a-f]{40}$') {
    throw 'Unable to resolve runner Git commit'
}
$outsideValidation = @(Get-RunnerOutsideValidationStatus)
$candidateTreeSha256Actual = $null
if ($EvidenceMode -eq 'FrozenCommit') {
    if ($outsideValidation.Count -ne 0) {
        throw 'FrozenCommit evidence requires a clean tracked tree and no untracked source paths'
    }
    if (-not [string]::IsNullOrWhiteSpace($CandidateTreeSha256)) {
        throw 'FrozenCommit evidence cannot carry a candidate-tree digest'
    }
} else {
    if ($CandidateTreeSha256 -notmatch '^[0-9A-Fa-f]{64}$') {
        throw 'PrecommitCandidate evidence requires a candidate-tree SHA-256'
    }
    $candidateTreeSha256Actual = Get-PrecommitCandidateTreeSha256
    if ($candidateTreeSha256Actual -ne $CandidateTreeSha256) {
        throw 'Precommit candidate tree changed after its digest was supplied'
    }
}

function Assert-RunnerSourceIdentity([string] $Context) {
    $currentGitCommit = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $currentGitCommit -cne $gitCommit) {
        throw "Runner Git commit changed while $Context was executing"
    }
    $currentOutsideValidation = @(Get-RunnerOutsideValidationStatus)
    if ($EvidenceMode -eq 'FrozenCommit') {
        if ($currentOutsideValidation.Count -ne 0) {
            throw "FrozenCommit source tree changed while $Context was executing"
        }
        return
    }
    $currentCandidateTreeSha256 = Get-PrecommitCandidateTreeSha256
    if ($currentCandidateTreeSha256 -cne $candidateTreeSha256Actual) {
        throw "Precommit candidate tree changed while $Context was executing"
    }
}

$apkPath = (Resolve-Path -LiteralPath $SealedApkPath).Path
$apkSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $apkPath).Hash
$apkBytes = (Get-Item -LiteralPath $apkPath).Length
if ($apkSha256 -cne $ExpectedApkSha256.ToUpperInvariant() -or
    $apkBytes -ne $ExpectedApkBytes) {
    throw 'Sealed ReaderDev APK path does not match the exact supplied identity'
}
$apkDisplayPath = [IO.Path]::GetRelativePath(
    (Get-Location).Path,
    $apkPath
).Replace('\', '/')
$runStartedUtc = [DateTime]::UtcNow.ToString('o')
@{
    Status = 'started'
    RunId = $runId
    DeviceSerial = $DeviceSerial
    GitCommit = $gitCommit
    EvidenceMode = $EvidenceMode
    CandidateTreeSha256 = $candidateTreeSha256Actual
    ApkPath = $apkDisplayPath
    ApkSha256 = $apkSha256
    ApkBytes = $apkBytes
    VersionCode = $ExpectedVersionCode
    VersionName = $ExpectedVersionName
    NoInstall = [bool]$NoInstall
    StartedUtc = $runStartedUtc
    StressTurns = $StressTurns
    OpenCloseCycles = $OpenCloseCycles
} | ConvertTo-Json -Depth 3 |
    Set-Content (Join-Path $ArtifactRoot 'run-start.json')

. (Join-Path $PSScriptRoot 'reader-playlikecurl-qa-parser.ps1')
. (Join-Path $PSScriptRoot 'reader-privacy-safe-evidence.ps1')

function Invoke-Adb([string[]] $Arguments) {
    & adb -s $DeviceSerial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')" }
}

function Assert-InstalledReaderDevIdentity([string] $Context) {
    $package = 'darkaxt.navic.readerdev'
    $packagePaths = @(
        Invoke-Adb @('shell', 'pm', 'path', $package) |
            Where-Object { $_ -match '^package:(?<Path>.+)$' } |
            ForEach-Object { $Matches['Path'].Trim() }
    )
    if ($packagePaths.Count -ne 1) {
        throw "$Context expected one installed ReaderDev base APK"
    }
    $temporaryApk = Join-Path ([IO.Path]::GetTempPath()) (
        "navic-readerdev-$([guid]::NewGuid().ToString('N')).apk"
    )
    try {
        Invoke-Adb @('pull', $packagePaths[0], $temporaryApk) | Out-Null
        $installedSha256 = (
            Get-FileHash -Algorithm SHA256 -LiteralPath $temporaryApk
        ).Hash
        $installedBytes = (Get-Item -LiteralPath $temporaryApk).Length
    } finally {
        Remove-Item -LiteralPath $temporaryApk -Force -ErrorAction SilentlyContinue
    }
    $packageDump = Invoke-Adb @('shell', 'dumpsys', 'package', $package) |
        Out-String
    $versionCode = [regex]::Match($packageDump, 'versionCode=(?<Value>\d+)')
    $versionName = [regex]::Match(
        $packageDump,
        '(?m)^\s*versionName=(?<Value>[^\r\n]*)\r?$'
    )
    $identityMismatches = @()
    if (-not $versionCode.Success) {
        $identityMismatches += 'missing-version-code'
    } elseif ([long]$versionCode.Groups['Value'].Value -ne $ExpectedVersionCode) {
        $identityMismatches +=
            "version-code expected=$ExpectedVersionCode actual=$($versionCode.Groups['Value'].Value)"
    }
    if (-not $versionName.Success) {
        $identityMismatches += 'missing-version-name'
    } elseif ($versionName.Groups['Value'].Value.Trim() -cne $ExpectedVersionName) {
        $identityMismatches +=
            "version-name expected=$ExpectedVersionName actual=$($versionName.Groups['Value'].Value.Trim())"
    }
    if ($installedSha256 -cne $ExpectedApkSha256.ToUpperInvariant()) {
        $identityMismatches +=
            "sha256 expected=$($ExpectedApkSha256.ToUpperInvariant()) actual=$installedSha256"
    }
    if ($installedBytes -ne $ExpectedApkBytes) {
        $identityMismatches +=
            "bytes expected=$ExpectedApkBytes actual=$installedBytes"
    }
    if ($identityMismatches.Count -ne 0) {
        throw "$Context installed package identity mismatch: $($identityMismatches -join '; ')"
    }
}

$script:ReaderPid = $null
$script:ReaderLogcatCursor = $null
$script:ReaderAccumulatedLogLineSet = [Collections.Generic.HashSet[string]]::new()
$script:ReaderAccumulatedLogLines = [Collections.Generic.List[string]]::new()
$script:ReaderAccumulatedDiagnosticLogLines = [Collections.Generic.List[string]]::new()
$recentDiagnosticWindow = 1024

function Reset-ReaderLogAccumulator {
    $script:ReaderLogcatCursor = $null
    $script:ReaderAccumulatedLogLineSet.Clear()
    $script:ReaderAccumulatedLogLines.Clear()
    $script:ReaderAccumulatedDiagnosticLogLines.Clear()
}

function Get-ReaderPid {
    $pidText = (Invoke-Adb @(
        "shell", "pidof", "darkaxt.navic.readerdev"
    ) | Out-String).Trim()
    $pids = @($pidText -split '\s+' | Where-Object { $_ -match '^\d+$' })
    if ($pids.Count -ne 1) {
        throw "Expected exactly one ReaderDev PID, found: $pidText"
    }
    return [int]$pids[0]
}

function Wait-ReaderPid(
    [string] $Context,
    [int] $WaitSeconds = 30
) {
    $deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
    do {
        $pidText = (
            & adb -s $DeviceSerial shell pidof darkaxt.navic.readerdev `
                2>$null | Out-String
        ).Trim()
        if ($LASTEXITCODE -eq 0) {
            $pids = @(
                $pidText -split '\s+' |
                    Where-Object { $_ -match '^\d+$' }
            )
            if ($pids.Count -eq 1) { return [int]$pids[0] }
            if ($pids.Count -gt 1) {
                throw "$Context observed multiple ReaderDev processes"
            }
        }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Context did not start one ReaderDev process"
}

function Read-ReaderPidLog(
    [string] $Context,
    [switch] $Full
) {
    $observedPid = Get-ReaderPid
    if ($null -eq $script:ReaderPid) {
        $script:ReaderPid = $observedPid
    } elseif ($observedPid -ne $script:ReaderPid) {
        throw "$Context ReaderDev PID changed from $($script:ReaderPid) to $observedPid"
    }
    $arguments = [Collections.Generic.List[string]]::new()
    @('logcat', '-d', "--pid=$observedPid", '-v', 'threadtime') |
        ForEach-Object { $arguments.Add($_) }
    if ($null -ne $script:ReaderLogcatCursor) {
        $arguments.Add('-T')
        $arguments.Add($script:ReaderLogcatCursor)
    }
    $snapshot = Invoke-Adb $arguments.ToArray() | Out-String
    $newRawLines = [Collections.Generic.List[string]]::new()
    $latestCursor = $script:ReaderLogcatCursor
    foreach ($line in @($snapshot -split "`r?`n")) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $cursor = [regex]::Match(
            $line,
            '^(?<Value>\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s'
        )
        if ($cursor.Success) {
            $latestCursor = $cursor.Groups['Value'].Value
        }
        if ($script:ReaderAccumulatedLogLineSet.Add($line)) {
            $script:ReaderAccumulatedLogLines.Add($line)
            $newRawLines.Add($line)
        }
    }
    if ($null -ne $latestCursor) {
        $script:ReaderLogcatCursor = $latestCursor
    }

    $firstNewDiagnosticIndex =
        $script:ReaderAccumulatedDiagnosticLogLines.Count
    if ($newRawLines.Count -gt 0) {
        $newRawLog = $newRawLines -join "`n"
        Assert-ReaderRuntimeLogSafe -Log $newRawLog -Context $Context
        if ($ReaderDiagnosticIntroducerPattern.IsMatch($newRawLog)) {
            $newDiagnostics = @(
                ConvertTo-ReaderDiagnosticRecordSet `
                    -Log $newRawLog `
                    -Context $Context
            )
            foreach ($diagnostic in $newDiagnostics) {
                $script:ReaderAccumulatedDiagnosticLogLines.Add($diagnostic)
            }
        }
    }

    $diagnosticCount = $script:ReaderAccumulatedDiagnosticLogLines.Count
    if ($diagnosticCount -eq 0) { return '' }
    $diagnosticStart = if ($Full) {
        0
    } else {
        [Math]::Min(
            $firstNewDiagnosticIndex,
            [Math]::Max(0, $diagnosticCount - $recentDiagnosticWindow)
        )
    }
    return $script:ReaderAccumulatedDiagnosticLogLines.GetRange(
        $diagnosticStart,
        $diagnosticCount - $diagnosticStart
    ) -join "`n"
}

$intervalEvidence = @()
function Save-ReaderDiagnosticInterval(
    [string] $Log,
    [string] $ArtifactName,
    [string] $Context
) {
    $rawLog = $script:ReaderAccumulatedLogLines -join "`n"
    $completeDiagnosticLog =
        $script:ReaderAccumulatedDiagnosticLogLines -join "`n"
    if ($Log -cne $completeDiagnosticLog) {
        throw "$Context was not supplied the complete accumulated diagnostic log"
    }
    Assert-ReaderRuntimeLogSafe -Log $rawLog -Context $Context
    $diagnosticLines = @(
        ConvertTo-ReaderDiagnosticRecordSet -Log $Log -Context $Context
    )
    $artifactPath = Join-Path $ArtifactRoot $ArtifactName
    $diagnosticLines | Set-Content -LiteralPath $artifactPath
    Assert-ReaderDiagnosticRecordSet `
        -Records @(Get-Content -LiteralPath $artifactPath) `
        -Context "$Context persisted artifact"
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try {
        $rawSha256 = [BitConverter]::ToString(
            $hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($rawLog))
        ).Replace('-', '')
    } finally {
        $hasher.Dispose()
    }
    $script:intervalEvidence += [pscustomobject]@{
        Context = $Context
        RawPidIntervalSha256 = $rawSha256
        PersistedArtifact = $ArtifactName
        PersistedSha256 = (
            Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath
        ).Hash
        PersistedBytes = (Get-Item -LiteralPath $artifactPath).Length
    }
    @($script:intervalEvidence) | ConvertTo-Json -Depth 5 |
        Set-Content (Join-Path $ArtifactRoot 'diagnostic-interval-manifest.json')
}

function Save-ReaderDiagnosticRecords(
    [string[]] $Records,
    [string] $Name,
    [string] $Context
) {
    Assert-ReaderDiagnosticRecordSet -Records $Records -Context $Context
    $path = Join-Path $ArtifactRoot $Name
    $Records | Set-Content -LiteralPath $path
    Assert-ReaderDiagnosticRecordSet `
        -Records @(Get-Content -LiteralPath $path) `
        -Context "$Context persisted artifact"
}

function Save-OwnershipEvidence(
    [object[]] $Snapshots,
    [string] $Name
) {
    Save-ReaderDiagnosticRecords `
        -Records @($Snapshots.LogLine) `
        -Name $Name `
        -Context "ownership evidence $Name"
}

function Wait-ReaderWarmupOwnership([string] $Context) {
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    do {
        $log = Read-ReaderPidLog $Context
        $prepared = @(
            ConvertFrom-ReaderOwnershipLog $log |
                Where-Object {
                    $_.Phase -eq 'peak-preparation' -or
                        $_.Phase -eq 'steady-state'
                }
        )
        if ($prepared.Count -gt 0) { return $prepared[$prepared.Count - 1] }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Context did not reach a prepared-deck ownership snapshot"
}

function Wait-ReaderPreparedDeckOwnership(
    [long] $ReaderSession,
    [string] $Context,
    [int] $WaitSeconds = 60
) {
    $deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
    do {
        $log = Read-ReaderPidLog $Context
        $prepared = @(
            ConvertFrom-ReaderDeckLog $log | Where-Object {
                $_.Session -eq $ReaderSession -and
                    $_.Role -eq 'Active' -and
                    $_.Prepared
            }
        )
        if ($prepared.Count -gt 0) { return $prepared[$prepared.Count - 1] }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Context did not reach prepared active-deck ownership"
}

function Wait-ReaderQaWorkingSetReady(
    [long] $ReaderSession,
    [int] $AfterIndex,
    [string] $Context,
    [int] $WaitSeconds = 60
) {
    $deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
    do {
        $log = Read-ReaderPidLog $Context
        $ready = @(
            ConvertFrom-ReaderPreparationLog $log | Where-Object {
                $_.Session -eq $ReaderSession -and
                    $_.State -eq 'Ready' -and
                    $_.Index -gt $AfterIndex
            }
        )
        if ($ready.Count -gt 0) { return $ready[$ready.Count - 1] }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Context did not complete working-set preparation"
}

function Open-ReaderDev {
    pwsh -NoProfile -ExecutionPolicy Bypass -File `
        .\scripts\install-reader-dev.ps1 `
        -DeviceSerial $DeviceSerial `
        -EnvFile $EnvFile `
        -NoBuild `
        -NoInstall `
        -RequireReaderLaunch `
        -SkipNativeShellCover `
        -NoForceStopLaunch `
        -PreserveLogcat `
        -WaitTimeoutSeconds $readerLaunchTimeoutSeconds
    if ($LASTEXITCODE -ne 0) { throw "ReaderDev relaunch failed" }
    $script:ReaderPid = Wait-ReaderPid 'ReaderDev relaunch'
}

function Wait-ClosedOwnershipBaseline(
    [int] $Cycle,
    [long] $ReaderSession
) {
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        $log = Read-ReaderPidLog "reader close cycle $Cycle"
        $closed = @(
            ConvertFrom-ReaderOwnershipLog $log |
                Where-Object {
                    $_.Session -eq $ReaderSession -and
                    $_.Phase -eq 'after-close'
                }
        )
        if ($closed.Count -gt 0) {
            $latest = $closed[$closed.Count - 1]
            Assert-OwnershipWithinBounds @($latest) "reader close cycle $Cycle"
            Assert-ZeroOwnership $latest "reader close cycle $Cycle"
            Save-ReaderDiagnosticRecords `
                -Records @($latest.LogLine) `
                -Name "ownership-after-close-$Cycle.txt" `
                -Context "reader close cycle $Cycle ownership"
            $cache = @(
                ConvertFrom-ReaderRasterCacheLog $log |
                    Where-Object {
                        $_.Session -eq $ReaderSession -and
                        $_.Phase -eq 'after-close'
                    }
            )
            if ($cache.Count -gt 0) {
                Assert-RasterCacheWithinByteLimit `
                    -Snapshots $cache `
                    -Context "reader close cycle $Cycle"
                Save-ReaderDiagnosticRecords `
                    -Records @($cache[-1].LogLine) `
                    -Name "raster-cache-after-close-$Cycle.txt" `
                    -Context "reader close cycle $Cycle raster cache"
                return
            }
        }
        $unavailable = @(
            $OwnershipUnavailablePattern.Matches($log) |
                Where-Object {
                    [long]$_.Groups['Session'].Value -eq $ReaderSession -and
                    $_.Groups['Phase'].Value -eq 'after-close'
                }
        )
        if ($unavailable.Count -gt 0) {
            $latestUnavailable = $unavailable[$unavailable.Count - 1]
            throw "reader close cycle $Cycle ownership unavailable: $($latestUnavailable.Value)"
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Reader close cycle $Cycle did not emit an after-close ownership snapshot"
}

function Invoke-ReaderQaFaultCommand(
    [string] $RequestId,
    [string] $Command,
    [string] $Fault = ''
) {
    $arguments = [Collections.Generic.List[string]]::new()
    @(
        'shell', 'am', 'broadcast',
        '-a', 'darkaxt.navic.readerdev.READER_QA_FAULT',
        '-n', 'darkaxt.navic.readerdev/paige.navic.androidApp.ReaderPageQaFaultReceiver',
        '--es', 'requestId', $RequestId,
        '--es', 'command', $Command
    ) | ForEach-Object { $arguments.Add($_) }
    if (-not [string]::IsNullOrWhiteSpace($Fault)) {
        @('--es', 'fault', $Fault) |
            ForEach-Object { $arguments.Add($_) }
    }
    Invoke-Adb $arguments.ToArray() | Out-Null
}

function Wait-ReaderQaCondition(
    [string] $Context,
    [scriptblock] $Select,
    [int] $WaitSeconds = 30,
    [switch] $Full
) {
    $deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
    do {
        $log = Read-ReaderPidLog $Context -Full:$Full
        $matches = @(& $Select $log)
        if ($matches.Count -gt 0) {
            return [pscustomobject]@{
                Log = $log
                Match = $matches[$matches.Count - 1]
            }
        }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Context did not emit its required diagnostic"
}

function Wait-ReaderQaFaultState(
    [string] $RequestId,
    [string] $State,
    [string] $Context,
    [int] $WaitSeconds = 30
) {
    return Wait-ReaderQaCondition -Context $Context -WaitSeconds $WaitSeconds -Select {
        param($log)
        ConvertFrom-ReaderQaFaultLog $log | Where-Object {
            $_.RequestId -eq $RequestId -and $_.State -eq $State
        }
    }
}

function Add-ReaderQaFault(
    [string] $RequestId,
    [string] $Fault
) {
    Invoke-ReaderQaFaultCommand `
        -RequestId $RequestId `
        -Command 'enqueue' `
        -Fault $Fault
    [void](Wait-ReaderQaFaultState `
        -RequestId $RequestId `
        -State 'Enqueued' `
        -Context "ReaderDev enqueue $Fault")
}

function Release-ReaderQaFault(
    [string] $FaultRequestId,
    [string] $ReleaseRequestId,
    [string] $Command
) {
    Invoke-ReaderQaFaultCommand `
        -RequestId $ReleaseRequestId `
        -Command $Command
    $released = Wait-ReaderQaFaultState `
        -RequestId $FaultRequestId `
        -State 'Released' `
        -Context "ReaderDev release $FaultRequestId"
    if ($released.Match.ReleaseRequestId -ne $ReleaseRequestId) {
        throw "ReaderDev release identity mismatch for $FaultRequestId"
    }
}

function Invoke-ReaderQaCommittedTurn(
    [Collections.Generic.HashSet[long]] $SeenGestureIds,
    [long] $ReaderSession,
    [int] $StartX,
    [int] $EndX,
    [int] $Y,
    [string] $Context
) {
    $retryOutcomes = @(
        'CancelledByUser',
        'RejectedPreparing',
        'RejectedSettling',
        'RejectedRendererUnavailable'
    )
    for ($attempt = 1; $attempt -le 20; $attempt += 1) {
        Invoke-Adb @(
            'shell', 'input', 'swipe',
            "$StartX", "$Y", "$EndX", "$Y", '400'
        )
        $terminal = Wait-ReaderQaCondition `
            -Context "$Context attempt $attempt" `
            -WaitSeconds 10 `
            -Select {
                param($log)
                ConvertFrom-ReaderGestureLog $log | Where-Object {
                    $_.Session -eq $ReaderSession -and
                        -not $SeenGestureIds.Contains($_.GestureId)
                }
            }
        if (-not $SeenGestureIds.Add([long]$terminal.Match.GestureId)) {
            throw "$Context reused a gesture identity"
        }
        if ($terminal.Match.Outcome -in @('CommittedForward', 'CommittedBackward')) {
            return $terminal.Match
        }
        if ($terminal.Match.Outcome -notin $retryOutcomes) {
            throw "$Context produced a non-retryable terminal: $($terminal.Match.LogLine)"
        }
        Start-Sleep -Milliseconds 250
    }
    throw "$Context did not commit within its bounded retry count"
}

function Wait-ReaderQaRelocationTerminal(
    [long] $ReaderSession,
    [long] $GestureId,
    [ValidateSet('Completed', 'Rejected')]
    [string[]] $States,
    [string] $Context
) {
    return Wait-ReaderQaCondition -Context $Context -WaitSeconds 30 -Select {
        param($log)
        ConvertFrom-ReaderRelocationLog $log | Where-Object {
            $_.Session -eq $ReaderSession -and
                $_.GestureId -eq $GestureId -and
                $_.State -in $States
        }
    }
}

function Wait-ReaderQaRelocationCompleted(
    [long] $ReaderSession,
    [long] $GestureId,
    [string] $Context
) {
    return Wait-ReaderQaRelocationTerminal `
        -ReaderSession $ReaderSession `
        -GestureId $GestureId `
        -States @('Completed') `
        -Context $Context
}

function Get-ReaderQaDownstreamEvents([string] $Log) {
    @(
        @(ConvertFrom-ReaderRasterAcquisitionLog $Log) +
        @(ConvertFrom-ReaderPreparationLog $Log) +
        @(ConvertFrom-ReaderRepairLog $Log) +
        @(ConvertFrom-ReaderPublicationLog $Log) +
        @(ConvertFrom-ReaderDeckLog $Log) +
        @(ConvertFrom-ReaderRelocationLog $Log) +
        @(ConvertFrom-ReaderHandoffLog $Log)
    )
}

function Assert-ReaderQaFaultSet(
    [string] $Log,
    [string[]] $RequestIds,
    [string] $Context
) {
    $faultEvents = @(
        ConvertFrom-ReaderQaFaultLog $Log | Where-Object {
            $_.RequestId -in $RequestIds
        }
    )
    foreach ($requestId in $RequestIds) {
        if (@($faultEvents | Where-Object {
            $_.RequestId -eq $requestId -and $_.State -eq 'Applied'
        }).Count -ne 1) {
            throw "$Context did not apply $requestId exactly once"
        }
    }
    $downstream = @(
        Get-ReaderQaDownstreamEvents $Log | Where-Object {
            $_.QaFaultRequestId -in $RequestIds
        }
    )
    Assert-ReaderQaFaultCorrelation `
        -FaultEvents $faultEvents `
        -DownstreamEvents $downstream `
        -Context $Context
    return $downstream
}

function Invoke-ReaderQaUiHierarchy(
    [DateTime] $DeadlineUtc
) {
    if ([DateTime]::UtcNow -ge $DeadlineUtc) { return '' }
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'adb'
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    @(
        '-s', $DeviceSerial,
        'exec-out', 'uiautomator', 'dump', '/dev/tty'
    ) | ForEach-Object { [void]$startInfo.ArgumentList.Add($_) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if ([DateTime]::UtcNow -ge $DeadlineUtc) { return '' }
        if (-not $process.Start()) {
            throw 'ReaderDev UiAutomator hierarchy process did not start'
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $remainingMilliseconds = [int][Math]::Floor(
            ($DeadlineUtc - [DateTime]::UtcNow).TotalMilliseconds
        )
        if ($remainingMilliseconds -le 0) {
            try {
                $process.Kill($true)
            } catch {
                # The process may exit between the deadline and termination.
            }
            return ''
        }
        if (-not $process.WaitForExit($remainingMilliseconds)) {
            try {
                $process.Kill($true)
            } catch {
                # The process may exit between the timeout and termination.
            }
            return ''
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        [void]$stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            throw 'ReaderDev UiAutomator hierarchy command failed'
        }
        return $stdout
    } finally {
        $process.Dispose()
    }
}

function Invoke-ReaderQaPreparationRetry {
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    $retryNode = $null
    do {
        $remainingMilliseconds = [int][Math]::Floor(
            ($deadline - [DateTime]::UtcNow).TotalMilliseconds
        )
        if ($remainingMilliseconds -le 0) { break }
        [string] $dump = Invoke-ReaderQaUiHierarchy `
            -DeadlineUtc $deadline
        $xmlStart = $dump.IndexOf('<?xml')
        $xmlEnd = $dump.IndexOf('</hierarchy>')
        if ($xmlStart -ge 0 -and $xmlEnd -ge $xmlStart) {
            $hierarchy = $null
            try {
                $hierarchy = [xml]$dump.Substring(
                    $xmlStart,
                    $xmlEnd + '</hierarchy>'.Length - $xmlStart
                )
            } catch {
                # UiAutomator can expose a partially changing hierarchy.
            }
            if ($null -ne $hierarchy) {
                $retryNodes = @(
                    $hierarchy.SelectNodes(
                        "//node[@text='Retry' or @content-desc='Retry']"
                    )
                )
                if ($retryNodes.Count -gt 1) {
                    throw 'ReaderDev failure overlay exposed multiple Retry controls'
                }
                if ($retryNodes.Count -eq 1) {
                    $retryNode = $retryNodes[0]
                    break
                }
            }
        }
        $remainingMilliseconds = [int][Math]::Floor(
            ($deadline - [DateTime]::UtcNow).TotalMilliseconds
        )
        if ($remainingMilliseconds -gt 0) {
            $sleepMilliseconds = [Math]::Min(250, $remainingMilliseconds)
            Start-Sleep -Milliseconds $sleepMilliseconds
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($null -eq $retryNode) {
        throw 'ReaderDev failure overlay did not expose one Retry control'
    }
    $bounds = [regex]::Match(
        [string]$retryNode.bounds,
        '^\[(?<Left>\d+),(?<Top>\d+)\]\[(?<Right>\d+),(?<Bottom>\d+)\]$'
    )
    if (-not $bounds.Success) {
        throw 'ReaderDev Retry control bounds were malformed'
    }
    $retryX = [int]((
        [int]$bounds.Groups['Left'].Value +
        [int]$bounds.Groups['Right'].Value
    ) / 2)
    $retryY = [int]((
        [int]$bounds.Groups['Top'].Value +
        [int]$bounds.Groups['Bottom'].Value
    ) / 2)
    Invoke-Adb @('shell', 'input', 'tap', "$retryX", "$retryY")
}

function Invoke-ReaderPersistenceFault(
    [string] $RequestId
) {
    $enqueued = Wait-ReaderQaFaultState `
        $RequestId 'Enqueued' 'ReaderDev prearmed persistence fault'
    $readerSession = [long]$enqueued.Match.Session
    $failedPublication = Wait-ReaderQaCondition `
        -Context 'ReaderDev injected persistence failure' `
        -WaitSeconds 30 `
        -Full `
        -Select {
            param($log)
            ConvertFrom-ReaderPublicationLog $log | Where-Object {
                $_.Session -eq $readerSession -and
                    $_.QaFaultRequestId -eq $RequestId -and
                    $_.QaFaultRelation -eq 'AppliedOperation' -and
                    $_.Result -eq 'Failed'
            }
        }
    $preparationFailure = Wait-ReaderQaCondition `
        -Context 'ReaderDev persistence preparation failure' `
        -WaitSeconds 30 `
        -Full `
        -Select {
            param($log)
            ConvertFrom-ReaderPreparationLog $log | Where-Object {
                $_.Session -eq $readerSession -and
                    $_.State -eq 'Failed' -and
                    $_.Index -gt $failedPublication.Match.Index
            }
        }
    Invoke-ReaderQaPreparationRetry
    [void](Wait-ReaderQaCondition `
        -Context 'ReaderDev durable persistence retry' `
        -WaitSeconds 60 `
        -Select {
            param($log)
            ConvertFrom-ReaderPublicationLog $log | Where-Object {
                $_.Session -eq $readerSession -and
                    $_.QaFaultRequestId -eq $RequestId -and
                    $_.QaFaultRelation -eq 'Retry' -and
                    $_.Result -eq 'Durable'
            }
        })
    [void](Wait-ReaderQaCondition `
        -Context 'ReaderDev persistence preparation retry' `
        -WaitSeconds 60 `
        -Full `
        -Select {
            param($log)
            ConvertFrom-ReaderPreparationLog $log | Where-Object {
                $_.Session -eq $readerSession -and
                    $_.State -eq 'Ready' -and
                    $_.Attempt -ne $preparationFailure.Match.Attempt -and
                    $_.Index -gt $preparationFailure.Match.Index
            }
        })
    $evidenceLog = Read-ReaderPidLog `
        -Context 'ReaderDev persistence fault evidence' `
        -Full
    [void](Assert-ReaderQaFaultSet `
        -Log $evidenceLog `
        -RequestIds @($RequestId) `
        -Context 'ReaderDev persistence fault correlation')
    return [pscustomobject]@{
        ReaderSession = $readerSession
    }
}

function Invoke-ReaderQaFaultMatrix(
    [long] $ReaderSession,
    [int] $PhysicalRight,
    [int] $PhysicalLeft,
    [int] $Y
) {
    $suffix = $runId.Substring(0, 8)
    $pauseId = "pause-$suffix"
    $relocationId = "reloc-$suffix"
    $visualId = "visual-$suffix"
    $repairId = "repair-$suffix"
    $missIds = [Collections.Generic.List[string]]::new()
    $seen = [Collections.Generic.HashSet[long]]::new()
    ConvertFrom-ReaderGestureLog (
        Read-ReaderPidLog -Context 'fault matrix baseline' -Full
    ) |
        Where-Object Session -eq $ReaderSession |
        ForEach-Object { [void]$seen.Add($_.GestureId) }

    Add-ReaderQaFault $pauseId 'PauseNextPublication'
    $pauseTurn = Invoke-ReaderQaCommittedTurn `
        -SeenGestureIds $seen -ReaderSession $ReaderSession `
        -StartX $PhysicalRight -EndX $PhysicalLeft -Y $Y `
        -Context 'ReaderDev publication pause turn'
    [void](Wait-ReaderQaFaultState $pauseId 'Applied' 'ReaderDev publication pause')
    Release-ReaderQaFault `
        -FaultRequestId $pauseId `
        -ReleaseRequestId "release-pause-$suffix" `
        -Command 'release-publication'
    [void](Wait-ReaderQaCondition `
        -Context 'ReaderDev publication pause completion' `
        -WaitSeconds 30 `
        -Select {
            param($log)
            ConvertFrom-ReaderPublicationLog $log | Where-Object {
                $_.QaFaultRequestId -eq $pauseId -and $_.Result -eq 'Durable'
            }
        })
    [void](Wait-ReaderQaRelocationCompleted $ReaderSession $pauseTurn.GestureId 'ReaderDev pause relocation')

    Add-ReaderQaFault $relocationId 'DelayNextRelocationAcknowledgement'
    $relocationTurn = Invoke-ReaderQaCommittedTurn `
        -SeenGestureIds $seen -ReaderSession $ReaderSession `
        -StartX $PhysicalRight -EndX $PhysicalLeft -Y $Y `
        -Context 'ReaderDev relocation delay turn'
    [void](Wait-ReaderQaFaultState $relocationId 'Applied' 'ReaderDev relocation delay')
    Release-ReaderQaFault `
        -FaultRequestId $relocationId `
        -ReleaseRequestId "release-reloc-$suffix" `
        -Command 'release-relocation'
    [void](Wait-ReaderQaRelocationCompleted $ReaderSession $relocationTurn.GestureId 'ReaderDev delayed relocation')

    Add-ReaderQaFault $visualId 'DelayNextVisualStateCallback'
    $visualTurn = Invoke-ReaderQaCommittedTurn `
        -SeenGestureIds $seen -ReaderSession $ReaderSession `
        -StartX $PhysicalRight -EndX $PhysicalLeft -Y $Y `
        -Context 'ReaderDev visual delay turn'
    [void](Wait-ReaderQaFaultState $visualId 'Applied' 'ReaderDev visual delay' 10)
    Release-ReaderQaFault `
        -FaultRequestId $visualId `
        -ReleaseRequestId "release-visual-$suffix" `
        -Command 'release-visual-state'
    [void](Wait-ReaderQaCondition `
        -Context 'ReaderDev visual handoff completion' `
        -WaitSeconds 30 `
        -Select {
            param($log)
            ConvertFrom-ReaderHandoffLog $log | Where-Object {
                $_.QaFaultRequestId -eq $visualId -and $_.Result -eq 'Ready'
            }
        })
    [void](Wait-ReaderQaRelocationCompleted $ReaderSession $visualTurn.GestureId 'ReaderDev visual relocation')
    [void](Wait-ReaderQaWorkingSetReady `
        -ReaderSession $ReaderSession `
        -AfterIndex $visualTurn.Index `
        -Context 'ReaderDev visual refill isolation')

    Add-ReaderQaFault $repairId 'ForceRepairWithoutPreparedDeck'
    $maximumRepairFaultAttempts = 5
    $repairTurn = $null
    $successfulRepairMissId = $null
    for (
        $repairFaultAttempt = 1;
        $repairFaultAttempt -le $maximumRepairFaultAttempts;
        $repairFaultAttempt += 1
    ) {
        $missId = "miss-$suffix-$repairFaultAttempt"
        $missIds.Add($missId)
        Add-ReaderQaFault $missId 'MissNextRasterLoad'
        $turnRightToLeft = $repairFaultAttempt % 2 -eq 0
        $repairTurn = Invoke-ReaderQaCommittedTurn `
            -SeenGestureIds $seen -ReaderSession $ReaderSession `
            -StartX $(if ($turnRightToLeft) { $PhysicalRight } else { $PhysicalLeft }) `
            -EndX $(if ($turnRightToLeft) { $PhysicalLeft } else { $PhysicalRight }) `
            -Y $Y `
            -Context "ReaderDev repair fault turn $repairFaultAttempt"
        [void](Wait-ReaderQaFaultState `
            $missId `
            'Applied' `
            "ReaderDev raster miss $repairFaultAttempt" `
            60)
        $resolution = Wait-ReaderQaCondition `
            -Context "ReaderDev forced repair attempt $repairFaultAttempt" `
            -WaitSeconds 60 `
            -Select {
                param($log)
                $applied = @(
                    ConvertFrom-ReaderQaFaultLog $log | Where-Object {
                        $_.RequestId -eq $repairId -and $_.State -eq 'Applied'
                    }
                )
                if ($applied.Count -gt 0) {
                    return [pscustomobject]@{ Kind = 'ForceApplied' }
                }
                $cancelled = @(
                    ConvertFrom-ReaderRepairLog $log | Where-Object {
                        $_.QaFaultRequestId -eq $missId -and
                            $_.State -eq 'Cancelled'
                    }
                )
                if ($cancelled.Count -gt 0) {
                    return [pscustomobject]@{ Kind = 'RepairTerminated' }
                }
            }
        if ($resolution.Match.Kind -eq 'ForceApplied') {
            $successfulRepairMissId = $missId
            break
        }
        [void](Wait-ReaderQaRelocationTerminal `
            -ReaderSession $ReaderSession `
            -GestureId $repairTurn.GestureId `
            -States @('Completed', 'Rejected') `
            -Context "ReaderDev superseded repair relocation $repairFaultAttempt")
        [void](Wait-ReaderQaWorkingSetReady `
            -ReaderSession $ReaderSession `
            -AfterIndex $repairTurn.Index `
            -Context "ReaderDev superseded repair refill $repairFaultAttempt")
    }
    if ($null -eq $successfulRepairMissId) {
        throw 'ReaderDev forced repair exhausted its bounded attempts'
    }
    $repairComplete = Wait-ReaderQaCondition `
        -Context 'ReaderDev repair completion' `
        -WaitSeconds 60 `
        -Select {
            param($log)
            ConvertFrom-ReaderRepairLog $log | Where-Object {
                $_.QaFaultRequestId -in @($successfulRepairMissId, $repairId) -and
                    $_.State -eq 'Completed'
            }
        }
    [void](Wait-ReaderQaRelocationTerminal `
        -ReaderSession $ReaderSession `
        -GestureId $repairTurn.GestureId `
        -States @('Completed', 'Rejected') `
        -Context 'ReaderDev forced active repair relocation')
    [void](Wait-ReaderPreparedDeckOwnership `
        -ReaderSession $ReaderSession `
        -Context 'ReaderDev repaired active deck')
    $proofTurn = Invoke-ReaderQaCommittedTurn `
        -SeenGestureIds $seen -ReaderSession $ReaderSession `
        -StartX $PhysicalRight -EndX $PhysicalLeft -Y $Y `
        -Context 'ReaderDev repaired active deck proof turn'
    [void](Wait-ReaderQaRelocationCompleted `
        -ReaderSession $ReaderSession `
        -GestureId $proofTurn.GestureId `
        -Context 'ReaderDev repaired active deck proof relocation')

    $requestIds = @(
        @($pauseId, $relocationId, $visualId) +
            @($missIds) +
            @($repairId)
    )
    $finalLog = Read-ReaderPidLog `
        -Context 'ReaderDev fault matrix evidence' `
        -Full
    [void](Assert-ReaderQaFaultSet `
        -Log $finalLog `
        -RequestIds $requestIds `
        -Context 'ReaderDev fault matrix correlation')
    Assert-RepairAttemptReachesSubmission `
        -Log $finalLog `
        -ReaderSession $ReaderSession `
        -Context 'ReaderDev fault matrix repair'
    return $finalLog
}

$runSucceeded = $false
try {
if (-not $NoInstall) {
    Invoke-Adb @('install', '-r', '-t', $apkPath) | Out-Null
}
Assert-InstalledReaderDevIdentity 'ReaderDev pre-launch'
$clearResult = (
    Invoke-Adb @('shell', 'pm', 'clear', 'darkaxt.navic.readerdev') |
        Out-String
).Trim()
if ($clearResult -cne 'Success') {
    throw 'ReaderDev app-data reset failed before deterministic cold preparation'
}
Invoke-Adb @('logcat', '-c')
Reset-ReaderLogAccumulator
$persistenceRequestId = "persist-$($runId.Substring(0, 8))"
pwsh -NoProfile -ExecutionPolicy Bypass -File `
    .\scripts\install-reader-dev.ps1 `
    -DeviceSerial $DeviceSerial `
    -EnvFile $EnvFile `
    -NoBuild `
    -NoInstall `
    -RequireReaderLaunch `
    -EnableCanvasPageTurn `
    -SkipNativeShellCover `
    -ReaderQaFaultRequestId $persistenceRequestId `
    -ReaderQaFault 'FailNextPersistence' `
    -WaitTimeoutSeconds $readerLaunchTimeoutSeconds
if ($LASTEXITCODE -ne 0) { throw "ReaderDev launch failed" }
$script:ReaderPid = Wait-ReaderPid 'ReaderDev initial launch'
$persistenceFault = Invoke-ReaderPersistenceFault `
    -RequestId $persistenceRequestId
$initialPreparationSnapshot = Wait-ReaderWarmupOwnership 'ReaderDev initial preparation'
Assert-OwnershipWithinBounds @($initialPreparationSnapshot) 'ReaderDev initial preparation'
$initialReaderSession = [long]$initialPreparationSnapshot.Session
if ($initialReaderSession -ne [long]$persistenceFault.ReaderSession) {
    throw 'ReaderDev persistence recovery changed the initial reader session'
}
[void](Wait-ReaderPreparedDeckOwnership `
    -ReaderSession $initialReaderSession `
    -Context 'ReaderDev initial prepared deck')
$persistenceFaultLog = Read-ReaderPidLog `
    -Context 'ReaderDev completed persistence recovery' `
    -Full
Save-ReaderDiagnosticInterval `
    -Log $persistenceFaultLog `
    -ArtifactName 'logcat-fault-persistence.txt' `
    -Context 'ReaderDev persistence fault evidence'
Invoke-Adb @("shell", "am", "force-stop", "darkaxt.navic.readerdev")
$script:ReaderPid = $null
Invoke-Adb @("logcat", "-c")
Reset-ReaderLogAccumulator
Open-ReaderDev
Invoke-Adb @("shell", "dumpsys", "meminfo", "darkaxt.navic.readerdev") |
    Set-Content (Join-Path $ArtifactRoot "meminfo-before.txt")
Invoke-Adb @("shell", "dumpsys", "gfxinfo", "darkaxt.navic.readerdev", "reset")

$warmupSnapshot = Wait-ReaderWarmupOwnership 'ReaderDev warmup'
$readerSession = [long]$warmupSnapshot.Session
$baselineLog = Read-ReaderPidLog -Context 'ReaderDev warmup baseline' -Full
Assert-ReaderOwnershipUnavailablePolicy `
    -Log $baselineLog `
    -ReaderSession $readerSession `
    -Context 'ReaderDev warmup'
Assert-WarmReopenUsesPersistentHydration `
    -Log $baselineLog `
    -ReaderSession $readerSession `
    -Context 'ReaderDev warm reopen'
$sizeText = (Invoke-Adb @("shell", "wm", "size") | Out-String)
$match = [regex]::Matches($sizeText, '(\d+)x(\d+)') | Select-Object -Last 1
if ($null -eq $match) { throw 'ReaderDev device dimensions were unavailable' }
$width = [int]$match.Groups[1].Value
$height = [int]$match.Groups[2].Value
$physicalRight = [int]($width * 0.82)
$physicalLeft = [int]($width * 0.18)
$y = [int]($height * 0.50)
$faultMatrixLog = Invoke-ReaderQaFaultMatrix `
    -ReaderSession $readerSession `
    -PhysicalRight $physicalRight `
    -PhysicalLeft $physicalLeft `
    -Y $y
Save-ReaderDiagnosticInterval `
    -Log $faultMatrixLog `
    -ArtifactName 'logcat-fault-injection.txt' `
    -Context 'ReaderDev fault injection'
$baselineLog = Read-ReaderPidLog `
    -Context 'ReaderDev post-fault stress baseline' `
    -Full
[void](Get-CommittedTurnCount $baselineLog $readerSession)
$initialSteadyCount = @(
    ConvertFrom-ReaderOwnershipLog $baselineLog |
        Where-Object {
            $_.Session -eq $readerSession -and $_.Phase -eq 'steady-state'
        }
).Count

$initialGestures = @(
    ConvertFrom-ReaderGestureLog $baselineLog | Where-Object {
        $_.Session -eq $readerSession
    }
)
$seenGestureIds = [Collections.Generic.HashSet[long]]::new()
foreach ($gesture in $initialGestures) {
    if (-not $seenGestureIds.Add($gesture.GestureId)) {
        throw "ReaderDev baseline contains duplicate gesture $($gesture.GestureId)"
    }
}
$stressTerminals = [Collections.Generic.List[object]]::new()
$committedGestureIds = [Collections.Generic.HashSet[long]]::new()
$directionCommitCounts = @{
    Next = 0
    Previous = 0
}
$minimumCommitsPerDirection = [Math]::Min(
    10,
    [Math]::Max(2, [int][Math]::Floor($StressTurns / 10))
)
$minimumDistinctOrdinals = [Math]::Min(
    20,
    [Math]::Max(3, [int][Math]::Floor($StressTurns / 5))
)
$maximumCommittedTurns = $StressTurns * 3
$maximumAttempts = ($maximumCommittedTurns * 2) + 40
$transientRetryOutcomes = @(
    'CancelledByUser',
    'RejectedPreparing',
    'RejectedSettling',
    'RejectedRendererUnavailable'
)
$attempt = 0
$committedTurns = 0
$boundaryCount = 0
$dragRightToLeft = $true
$currentSweepLogicalDirection = $null
$requiredNextSweepDirection = $null
$consecutiveNoTerminalAttempts = 0
$maximumConsecutiveNoTerminalAttempts = 3

while ($attempt -lt $maximumAttempts -and
    $committedTurns -lt $maximumCommittedTurns) {
    $hasMinimumCoverage =
        $committedTurns -ge $StressTurns -and
        $boundaryCount -ge 1 -and
        $directionCommitCounts.Next -ge $minimumCommitsPerDirection -and
        $directionCommitCounts.Previous -ge $minimumCommitsPerDirection
    if ($hasMinimumCoverage) { break }

    if ($dragRightToLeft) {
        Invoke-Adb @(
            "shell", "input", "swipe",
            "$physicalRight", "$y", "$physicalLeft", "$y", "180"
        )
    } else {
        Invoke-Adb @(
            "shell", "input", "swipe",
            "$physicalLeft", "$y", "$physicalRight", "$y", "180"
        )
    }
    $attempt += 1

    $terminalDeadline = [DateTime]::UtcNow.AddSeconds(10)
    $newTerminal = $null
    do {
        Start-Sleep -Milliseconds 100
        $log = Read-ReaderPidLog "ReaderDev stress attempt $attempt"
        Assert-ReaderOwnershipUnavailablePolicy `
            -Log $log `
            -ReaderSession $readerSession `
            -Context "ReaderDev stress attempt $attempt" `
            -AllowPendingRecovery
        [void](Get-CommittedTurnCount $log $readerSession)
        $newTerminals = @(
            ConvertFrom-ReaderGestureLog $log | Where-Object {
                $_.Session -eq $readerSession -and
                -not $seenGestureIds.Contains($_.GestureId)
            }
        )
        if ($newTerminals.Count -gt 1) {
            throw "ReaderDev stress attempt $attempt emitted multiple gesture terminals"
        }
        if ($newTerminals.Count -eq 1) {
            $newTerminal = $newTerminals[0]
            break
        }
    } while ([DateTime]::UtcNow -lt $terminalDeadline)
    if ($null -eq $newTerminal) {
        $consecutiveNoTerminalAttempts += 1
        if ($consecutiveNoTerminalAttempts -gt
            $maximumConsecutiveNoTerminalAttempts) {
            throw "ReaderDev stress emitted no gesture terminal for " +
                "$consecutiveNoTerminalAttempts consecutive attempts"
        }
        Start-Sleep -Milliseconds 250
        continue
    }
    $consecutiveNoTerminalAttempts = 0
    if (-not $seenGestureIds.Add($newTerminal.GestureId)) {
        throw "ReaderDev stress reused gesture $($newTerminal.GestureId)"
    }
    $stressTerminals.Add($newTerminal)

    if ($newTerminal.Outcome -in $transientRetryOutcomes) {
        Start-Sleep -Milliseconds 250
        continue
    }

    $logicalDirection = [string]$newTerminal.LogicalDirection
    if ($logicalDirection -notin @('Next', 'Previous')) {
        throw "ReaderDev swipe terminal lacks a logical direction: $($newTerminal.LogLine)"
    }
    if ($newTerminal.PhysicalDirection -notin @('Left', 'Right')) {
        throw "ReaderDev swipe terminal lacks a physical direction: $($newTerminal.LogLine)"
    }

    if ($newTerminal.Outcome -in @('CommittedForward', 'CommittedBackward')) {
        $expectedDirection = if (
            $newTerminal.Outcome -eq 'CommittedForward'
        ) { 'Next' } else { 'Previous' }
        if ($logicalDirection -ne $expectedDirection) {
            throw "ReaderDev commit outcome and logical direction disagree: $($newTerminal.LogLine)"
        }
        if ($null -eq $currentSweepLogicalDirection) {
            if ($null -ne $requiredNextSweepDirection -and
                $logicalDirection -ne $requiredNextSweepDirection) {
                throw 'ReaderDev boundary reversal did not reverse logical direction'
            }
            $currentSweepLogicalDirection = $logicalDirection
            $requiredNextSweepDirection = $null
        } elseif ($logicalDirection -ne $currentSweepLogicalDirection) {
            throw 'ReaderDev changed logical direction without an explicit boundary'
        }
        if (-not $committedGestureIds.Add($newTerminal.GestureId)) {
            throw "ReaderDev stress duplicated committed gesture $($newTerminal.GestureId)"
        }
        $directionCommitCounts[$logicalDirection] += 1
        $committedTurns += 1
        continue
    }

    if ($newTerminal.Outcome -ne 'RejectedBoundary') {
        throw "ReaderDev stress emitted an unexpected terminal: $($newTerminal.LogLine)"
    }
    if ($newTerminal.Outcome -eq 'RejectedBoundary') {
        if ($null -ne $currentSweepLogicalDirection -and
            $logicalDirection -ne $currentSweepLogicalDirection) {
            throw 'ReaderDev boundary terminal differs from its active logical sweep'
        }
        $boundaryCount += 1
        $requiredNextSweepDirection = if ($logicalDirection -eq 'Next') {
            'Previous'
        } else {
            'Next'
        }
        $dragRightToLeft = -not $dragRightToLeft
        $currentSweepLogicalDirection = $null
    }
}

if ($committedTurns -lt $StressTurns -or
    $boundaryCount -lt 1 -or
    $directionCommitCounts.Next -lt $minimumCommitsPerDirection -or
    $directionCommitCounts.Previous -lt $minimumCommitsPerDirection) {
    throw "ReaderDev boundary sweep coverage failed commits=$committedTurns " +
        "next=$($directionCommitCounts.Next) previous=$($directionCommitCounts.Previous) " +
        "boundaries=$boundaryCount attempts=$attempt maximumCommitted=$maximumCommittedTurns"
}

$stressDeadline = [DateTime]::UtcNow.AddSeconds(60)
do {
    $log = Read-ReaderPidLog `
        -Context 'ReaderDev steady-state drain' `
        -Full
    Assert-ReaderOwnershipUnavailablePolicy `
        -Log $log `
        -ReaderSession $readerSession `
        -Context 'ReaderDev steady-state drain' `
        -AllowPendingRecovery
    $ownership = @(
        ConvertFrom-ReaderOwnershipLog $log |
            Where-Object Session -eq $readerSession
    )
    $steadyTurns = @(
        $ownership | Where-Object Phase -eq 'steady-state'
    ).Count - $initialSteadyCount
    $completedRelocations = @(
        ConvertFrom-ReaderRelocationLog $log | Where-Object {
            $_.Session -eq $readerSession -and
            $_.State -eq 'Completed' -and
            $committedGestureIds.Contains($_.GestureId)
        }
    )
    if ($steadyTurns -ge $committedTurns -and
        $completedRelocations.Count -ge $committedTurns) {
        break
    }
    Start-Sleep -Milliseconds 250
} while ([DateTime]::UtcNow -lt $stressDeadline)
if ($steadyTurns -lt $committedTurns -or
    $completedRelocations.Count -ne $committedTurns) {
    throw "ReaderDev stress drain failed commits=$committedTurns " +
        "steadySnapshots=$steadyTurns completedRelocations=$($completedRelocations.Count) " +
        "session=$readerSession"
}
Assert-ReaderOwnershipUnavailablePolicy `
    -Log $log `
    -ReaderSession $readerSession `
    -Context 'ReaderDev completed stress interval'

$duplicateCompletedRelocations = @(
    $completedRelocations | Group-Object GestureId | Where-Object Count -ne 1
)
if ($duplicateCompletedRelocations.Count -ne 0) {
    throw 'ReaderDev emitted duplicate completed relocation evidence'
}
$orderedRelocations = @($completedRelocations | Sort-Object Index)
foreach ($relocation in $orderedRelocations) {
    if ($relocation.Direction -eq 'Next' -and
        $relocation.Target -le $relocation.Source) {
        throw "ReaderDev Next relocation did not advance: $($relocation.LogLine)"
    }
    if ($relocation.Direction -eq 'Previous' -and
        $relocation.Target -ge $relocation.Source) {
        throw "ReaderDev Previous relocation did not retreat: $($relocation.LogLine)"
    }
}
$committedOrdinals = @(
    @($orderedRelocations | Select-Object -ExpandProperty Source) +
    @($orderedRelocations | Select-Object -ExpandProperty Target) |
        Sort-Object -Unique
)
if ($committedOrdinals.Count -lt $minimumDistinctOrdinals) {
    throw "ReaderDev stress traversed only $($committedOrdinals.Count) distinct ordinals; required=$minimumDistinctOrdinals"
}

Save-ReaderDiagnosticRecords `
    -Records @($stressTerminals.LogLine) `
    -Name 'gesture-boundary-sweep.txt' `
    -Context 'ReaderDev boundary-sweep gesture evidence'
Save-ReaderDiagnosticRecords `
    -Records @($orderedRelocations.LogLine) `
    -Name 'relocation-boundary-sweep.txt' `
    -Context 'ReaderDev boundary-sweep relocation evidence'
[ordered]@{
    SchemaVersion = 1
    MinimumCommittedTurns = $StressTurns
    ActualCommittedTurns = $committedTurns
    BoundaryTerminals = $boundaryCount
    NextCommits = $directionCommitCounts.Next
    PreviousCommits = $directionCommitCounts.Previous
    MinimumCommitsPerDirection = $minimumCommitsPerDirection
    DistinctCommittedOrdinals = $committedOrdinals.Count
    MinimumDistinctOrdinals = $minimumDistinctOrdinals
    Attempts = $attempt
} | ConvertTo-Json -Depth 3 |
    Set-Content (Join-Path $ArtifactRoot 'boundary-sweep-summary.json')

Save-ReaderDiagnosticInterval `
    -Log $log `
    -ArtifactName 'logcat-stress.txt' `
    -Context 'ReaderDev stress'
Invoke-Adb @("shell", "dumpsys", "meminfo", "darkaxt.navic.readerdev") |
    Set-Content (Join-Path $ArtifactRoot "meminfo-after-stress.txt")
Invoke-Adb @("shell", "dumpsys", "gfxinfo", "darkaxt.navic.readerdev") |
    Set-Content (Join-Path $ArtifactRoot "gfxinfo-after-stress.txt")

pwsh -NoProfile -ExecutionPolicy Bypass -File `
    .\scripts\adb-reader-smoke.ps1 `
    -Package darkaxt.navic.readerdev `
    -DeviceSerial $DeviceSerial `
    -ArtifactDir (Join-Path $ArtifactRoot "post-stress") `
    -NoLaunch `
    -RequireNoReaderConsoleErrors `
    -RequireNeutralReaderVisualState `
    -CaptureReaderDiagnostics `
    -PrivacySafeEvidence `
    -PreserveLogcat
if ($LASTEXITCODE -ne 0) { throw "Post-stress reader smoke failed" }
Assert-ReaderPrivacySafeArtifactTree `
    -Root (Join-Path $ArtifactRoot 'post-stress') `
    -Kind smoke

Assert-ReaderRuntimeLogSafe -Log $log -Context 'ReaderDev stress'

$coldStart = @($ownership | Where-Object Phase -eq 'cold-start')
$peak = @($ownership | Where-Object Phase -eq 'peak-preparation')
$steady = @($ownership | Where-Object Phase -eq 'steady-state')
Assert-OwnershipWithinBounds $coldStart 'cold-start ownership'
Assert-OwnershipWithinBounds $peak 'peak-preparation ownership'
Assert-OwnershipWithinBounds $steady 'steady-state ownership'
Assert-ZeroOwnership $coldStart[0] 'cold-start ownership'
Assert-NoPostWarmupOwnershipGrowth $peak 10 'peak-preparation ownership'
Assert-NoPostWarmupOwnershipGrowth $steady 10 'steady-state ownership'
Save-OwnershipEvidence $coldStart 'ownership-cold-start.txt'
Save-OwnershipEvidence $peak 'ownership-peak-preparation.txt'
Save-OwnershipEvidence $steady 'ownership-steady-state.txt'

$rasterCache = @(
    ConvertFrom-ReaderRasterCacheLog $log |
        Where-Object Session -eq $readerSession
)
foreach ($phase in @('cold-start', 'peak-preparation', 'steady-state')) {
    if (@($rasterCache | Where-Object Phase -eq $phase).Count -eq 0) {
        throw "ReaderDev stress emitted no raster-cache snapshot for $phase"
    }
}
Assert-RasterCacheWithinByteLimit $rasterCache 'ReaderDev raster cache'
Save-ReaderDiagnosticRecords `
    -Records @($rasterCache.LogLine) `
    -Name 'raster-cache-stress.txt' `
    -Context 'ReaderDev raster-cache stress evidence'

$residency = @(
    ConvertFrom-ReaderResidencyLog $log |
        Where-Object Session -eq $readerSession
)
Assert-ReaderResidencyWithinBounds `
    -Snapshots $residency `
    -Context 'ReaderDev stress residency'
Save-ReaderDiagnosticRecords `
    -Records @($residency.LogLine) `
    -Name 'residency-stress.txt' `
    -Context 'ReaderDev residency stress evidence'

$currentReaderSession = $readerSession
for ($cycle = 1; $cycle -le $OpenCloseCycles; $cycle += 1) {
    Invoke-Adb @("logcat", "-c")
    Reset-ReaderLogAccumulator
    Invoke-Adb @("shell", "input", "keyevent", "KEYCODE_BACK")
    Wait-ClosedOwnershipBaseline `
        -Cycle $cycle `
        -ReaderSession $currentReaderSession
    if ($cycle -lt $OpenCloseCycles) {
        Open-ReaderDev
        $reopened = Wait-ReaderWarmupOwnership "reader reopen cycle $cycle"
        $currentReaderSession = [long]$reopened.Session
    }
    $intervalLog = Read-ReaderPidLog `
        -Context "reader complete close/reopen interval $cycle" `
        -Full
    Save-ReaderDiagnosticInterval `
        -Log $intervalLog `
        -ArtifactName "logcat-close-reopen-cycle-$cycle.txt" `
        -Context "reader complete close/reopen interval $cycle"
}

Invoke-Adb @("shell", "dumpsys", "meminfo", "darkaxt.navic.readerdev") |
    Set-Content (Join-Path $ArtifactRoot "meminfo-after-close.txt")
$finalCloseLog = Read-ReaderPidLog -Context 'reader final close' -Full
Save-ReaderDiagnosticInterval `
    -Log $finalCloseLog `
    -ArtifactName 'logcat-final-close.txt' `
    -Context 'reader final close'
Assert-InstalledReaderDevIdentity 'ReaderDev post-run'
Assert-RunnerSourceIdentity 'ReaderDev post-run'
$finalApkSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $apkPath).Hash
$finalApkBytes = (Get-Item -LiteralPath $apkPath).Length
if ($finalApkSha256 -cne $ExpectedApkSha256.ToUpperInvariant() -or
    $finalApkBytes -ne $ExpectedApkBytes) {
    throw 'Sealed ReaderDev APK changed while the device run was executing'
}
$resolvedArtifactRoot = (Resolve-Path -LiteralPath $ArtifactRoot).Path.TrimEnd('\')
$artifactHashes = @(
    Get-ChildItem -LiteralPath $resolvedArtifactRoot -Recurse -File |
        Sort-Object FullName |
        ForEach-Object {
            @{
                Path = $_.FullName.Substring(
                    $resolvedArtifactRoot.Length + 1
                ).Replace('\', '/')
                Sha256 = (
                    Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName
                ).Hash
                Bytes = $_.Length
            }
        }
)
@{
    Status = 'complete'
    RunId = $runId
    DeviceSerial = $DeviceSerial
    ReaderPid = $script:ReaderPid
    GitCommit = $gitCommit
    EvidenceMode = $EvidenceMode
    CandidateTreeSha256 = $candidateTreeSha256Actual
    ApkPath = $apkDisplayPath
    ApkSha256 = $apkSha256
    ApkBytes = $apkBytes
    VersionCode = $ExpectedVersionCode
    VersionName = $ExpectedVersionName
    NoInstall = [bool]$NoInstall
    StartedUtc = $runStartedUtc
    CompletedUtc = [DateTime]::UtcNow.ToString('o')
    Artifacts = $artifactHashes
} | ConvertTo-Json -Depth 5 |
    Set-Content (Join-Path $ArtifactRoot 'run-complete.json')
$runSucceeded = $true
} catch {
    if ($script:ReaderAccumulatedDiagnosticLogLines.Count -gt 0) {
        Save-ReaderDiagnosticRecords `
            -Records @($script:ReaderAccumulatedDiagnosticLogLines) `
            -Name 'failure-diagnostics.txt' `
            -Context 'ReaderDev failure diagnostics'
    }
    [ordered]@{
        Status = 'failed'
        RunId = $runId
        DeviceSerial = $DeviceSerial
        GitCommit = $gitCommit
        EvidenceMode = $EvidenceMode
        CandidateTreeSha256 = $candidateTreeSha256Actual
        ApkSha256 = $apkSha256
        ApkBytes = $apkBytes
        FailureClass = $_.Exception.GetType().FullName
        CompletedUtc = [DateTime]::UtcNow.ToString('o')
    } | ConvertTo-Json -Depth 3 |
        Set-Content (Join-Path $ArtifactRoot 'run-failed.json')
    throw
} finally {
    $cleanupRequestId = "cleanup-$($runId.Substring(0, 8))"
    & adb -s $DeviceSerial shell am broadcast `
        -f 0x20 `
        -a darkaxt.navic.readerdev.READER_QA_FAULT `
        -n darkaxt.navic.readerdev/paige.navic.androidApp.ReaderPageQaFaultReceiver `
        --es requestId $cleanupRequestId `
        --es command clear 2>&1 | Out-Null
    & adb -s $DeviceSerial shell am force-stop darkaxt.navic.readerdev `
        2>&1 | Out-Null
    if ($runSucceeded -and
        -not (Test-Path -LiteralPath (Join-Path $ArtifactRoot 'run-complete.json'))) {
        throw 'ReaderDev successful run did not persist run-complete.json'
    }
}
