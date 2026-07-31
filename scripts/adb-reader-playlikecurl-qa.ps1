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
    [string] $ImplementationCommit = '',
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
$preparationRecoveryTimeoutSeconds = 180
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
$acceptanceToolingCommit = (git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or
    $acceptanceToolingCommit -notmatch '^[0-9a-f]{40}$') {
    throw 'Unable to resolve runner Git commit'
}
$implementationGitCommit = if ([string]::IsNullOrWhiteSpace($ImplementationCommit)) {
    $acceptanceToolingCommit
} else {
    $ImplementationCommit.Trim().ToLowerInvariant()
}
if ($implementationGitCommit -notmatch '^[0-9a-f]{40}$') {
    throw 'Reader QA implementation commit is invalid'
}

function Assert-RunnerPostImplementationPaths {
    param(
        [Parameter(Mandatory = $true)][string] $ImplementationGitCommit,
        [Parameter(Mandatory = $true)][string] $ToolingCommit
    )
    if ($ImplementationGitCommit -ceq $ToolingCommit) { return }
    git merge-base --is-ancestor $ImplementationGitCommit $ToolingCommit
    if ($LASTEXITCODE -ne 0) {
        throw 'Acceptance tooling commit does not descend from the implementation commit'
    }
    $allowed = @(
        'scripts/adb-reader-playlikecurl-qa.ps1',
        'scripts/install-reader-dev.ps1',
        'scripts/reader-playlikecurl-qa-parser.ps1',
        'scripts/test-reader-playlikecurl-qa-parser.ps1',
        'scripts/reader-playlikecurl-acceptance-state.ps1',
        'scripts/test-reader-playlikecurl-acceptance-state.ps1',
        'composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderDevEnvironmentContractTest.kt',
        'docs/superpowers/reports/2026-07-18-reader-playlikecurl-qa-remediation-validation.md'
    )
    $changedPaths = @(git diff --name-only "$ImplementationGitCommit..$ToolingCommit")
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to inspect post-implementation acceptance changes'
    }
    foreach ($path in $changedPaths) {
        if ($path.Replace('\', '/') -cnotin $allowed) {
            throw "Production source changed after the Reader QA implementation commit: $path"
        }
    }
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
    Assert-RunnerPostImplementationPaths `
        $implementationGitCommit $acceptanceToolingCommit
} else {
    if (-not [string]::IsNullOrWhiteSpace($ImplementationCommit)) {
        throw 'PrecommitCandidate evidence cannot override the implementation commit'
    }
    if ($CandidateTreeSha256 -notmatch '^[0-9A-Fa-f]{64}$') {
        throw 'PrecommitCandidate evidence requires a candidate-tree SHA-256'
    }
    $candidateTreeSha256Actual = Get-PrecommitCandidateTreeSha256
    if ($candidateTreeSha256Actual -ne $CandidateTreeSha256) {
        throw 'Precommit candidate tree changed after its digest was supplied'
    }
}

function Assert-RunnerSourceIdentity([string] $Context) {
    $currentToolingCommit = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or
        $currentToolingCommit -cne $acceptanceToolingCommit) {
        throw "Runner Git commit changed while $Context was executing"
    }
    $currentOutsideValidation = @(Get-RunnerOutsideValidationStatus)
    if ($EvidenceMode -eq 'FrozenCommit') {
        if ($currentOutsideValidation.Count -ne 0) {
            throw "FrozenCommit source tree changed while $Context was executing"
        }
        Assert-RunnerPostImplementationPaths `
            $implementationGitCommit $acceptanceToolingCommit
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
    GitCommit = $implementationGitCommit
    AcceptanceToolingCommit = $acceptanceToolingCommit
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
$script:ReaderAccumulatedQaInputLogLines = [Collections.Generic.List[string]]::new()
$recentDiagnosticWindow = 1024

function Reset-ReaderLogAccumulator {
    $script:ReaderLogcatCursor = $null
    $script:ReaderAccumulatedLogLineSet.Clear()
    $script:ReaderAccumulatedLogLines.Clear()
    $script:ReaderAccumulatedDiagnosticLogLines.Clear()
    $script:ReaderAccumulatedQaInputLogLines.Clear()
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
        foreach ($qaInput in @(ConvertFrom-ReaderQaInputLog $newRawLog)) {
            $script:ReaderAccumulatedQaInputLogLines.Add($qaInput.LogLine)
        }
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
    [int] $AfterIndex = -1,
    [long] $AtOrAfterAttempt = -1,
    [string] $Context,
    [int] $WaitSeconds = 60
) {
    if ($AfterIndex -lt 0 -and $AtOrAfterAttempt -lt 0) {
        throw "$Context requires a preparation ordering boundary"
    }
    $deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
    do {
        $log = Read-ReaderPidLog `
            -Context $Context `
            -Full:($AfterIndex -ge 0)
        $ready = @(
            ConvertFrom-ReaderPreparationLog $log | Where-Object {
                $afterBoundary = if ($AtOrAfterAttempt -ge 0) {
                    $_.Attempt -ge $AtOrAfterAttempt
                } else {
                    $_.Index -gt $AfterIndex
                }
                $_.Session -eq $ReaderSession -and
                    $_.State -eq 'Ready' -and
                    $afterBoundary
            }
        )
        if ($ready.Count -gt 0) { return $ready[$ready.Count - 1] }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Context did not complete working-set preparation"
}

function Wait-ReaderQaPreparationAttemptTerminal(
    [long] $ReaderSession,
    [long] $Attempt,
    [long] $RasterGeneration,
    [int] $AfterIndex,
    [string] $Context,
    [int] $WaitSeconds = 60
) {
    return Wait-ReaderQaCondition `
        -Context $Context `
        -WaitSeconds $WaitSeconds `
        -Full `
        -Select {
            param($log)
            ConvertFrom-ReaderPreparationLog $log | Where-Object {
                $_.Session -eq $ReaderSession -and
                    $_.Attempt -eq $Attempt -and
                    $_.RasterGeneration -eq $RasterGeneration -and
                    $_.Index -gt $AfterIndex -and
                    $_.State -in @('Ready', 'Failed', 'Cancelled')
            }
        }
}

function Wait-ReaderQaPreparedTextureGeneration(
    [long] $ReaderSession,
    [long] $GestureId,
    [long] $TextureGeneration,
    [string] $Context,
    [int] $WaitSeconds = 60
) {
    if ($TextureGeneration -lt 0) {
        throw "$Context requires a promoted texture generation"
    }
    return Wait-ReaderQaCondition `
        -Context $Context `
        -WaitSeconds $WaitSeconds `
        -Full `
        -Select {
            param($log)
            Get-ReaderPreparedPromotedTexture `
                -Log $log `
                -ReaderSession $ReaderSession `
                -GestureId $GestureId `
                -TextureGeneration $TextureGeneration `
                -Context $Context
        }
}

function Open-ReaderDev([switch] $AtPublicationStart) {
    $launchArguments = [Collections.Generic.List[string]]@(
        '-NoProfile',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        '.\scripts\install-reader-dev.ps1',
        '-DeviceSerial',
        $DeviceSerial,
        '-EnvFile',
        $EnvFile,
        '-NoBuild',
        '-NoInstall',
        '-RequireReaderLaunch',
        '-SkipNativeShellCover',
        '-NoForceStopLaunch',
        '-PreserveLogcat',
        '-WaitTimeoutSeconds',
        "$readerLaunchTimeoutSeconds"
    )
    if ($AtPublicationStart) {
        $launchArguments.Add('-StartAtBeginning')
    }
    & pwsh @launchArguments
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
    [switch] $Full,
    [switch] $ReturnNullOnTimeout
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
    if ($ReturnNullOnTimeout) { return $null }
    throw "$Context did not emit its required diagnostic"
}

function Wait-ReaderQaInputState(
    [string] $RequestId,
    [string] $State,
    [string] $Context,
    [int] $WaitSeconds = 10,
    [switch] $ReturnNullOnTimeout
) {
    $deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
    do {
        [void](Read-ReaderPidLog $Context)
        $log = $script:ReaderAccumulatedQaInputLogLines -join "`n"
        $matches = @(
            ConvertFrom-ReaderQaInputLog $log | Where-Object {
                $_.RequestId -eq $RequestId -and $_.State -eq $State
            }
        )
        if ($matches.Count -gt 0) {
            return [pscustomobject]@{
                Log = $log
                Match = $matches[$matches.Count - 1]
            }
        }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($ReturnNullOnTimeout) { return $null }
    throw "$Context did not emit its required QA-input state"
}

function Wait-ReaderQaFaultState(
    [string] $RequestId,
    [string] $State,
    [string] $Context,
    [int] $WaitSeconds = 30
) {
    return Wait-ReaderQaCondition -Context $Context -WaitSeconds $WaitSeconds -Full -Select {
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

function Get-ReaderAnimatorDurationScale {
    $scale = (@(
        Invoke-Adb @(
            'shell', 'settings', 'get', 'global', 'animator_duration_scale'
        )
    ) -join '').Trim()
    if ($scale -notmatch '^(?:null|\d+(?:\.\d+)?)$') {
        throw 'ReaderDev animator duration scale is invalid'
    }
    return $scale
}

function Set-ReaderAnimatorDurationScale([string] $Scale) {
    if ($Scale -notmatch '^(?:null|\d+(?:\.\d+)?)$') {
        throw 'ReaderDev animator duration scale request is invalid'
    }
    if ($Scale -ceq 'null') {
        [void](Invoke-Adb @(
            'shell', 'settings', 'delete', 'global', 'animator_duration_scale'
        ))
    } else {
        [void](Invoke-Adb @(
            'shell', 'settings', 'put', 'global', 'animator_duration_scale', $Scale
        ))
    }
    $observed = Get-ReaderAnimatorDurationScale
    $scaleMatches = if ($Scale -ceq 'null' -or $observed -ceq 'null') {
        $Scale -ceq $observed
    } else {
        [decimal]::Parse($Scale, [Globalization.CultureInfo]::InvariantCulture) -eq
            [decimal]::Parse($observed, [Globalization.CultureInfo]::InvariantCulture)
    }
    if (-not $scaleMatches) {
        throw 'ReaderDev animator duration scale did not reach its requested value'
    }
}

function Restore-ReaderAnimatorDurationScale([string] $Scale) {
    foreach ($attempt in 1..3) {
        try {
            Set-ReaderAnimatorDurationScale $Scale
            return $true
        } catch {
            if ($attempt -lt 3) {
                Start-Sleep -Milliseconds 250
            }
        }
    }
    return $false
}

function Test-ReaderQaFaultRequestsRemainEnqueued(
    [string[]] $RequestIds,
    [string] $Context
) {
    $events = @(
        ConvertFrom-ReaderQaFaultLog (
            Read-ReaderPidLog -Context $Context -Full
        )
    )
    foreach ($requestId in $RequestIds) {
        $latest = @(
            $events | Where-Object RequestId -eq $requestId
        ) | Select-Object -Last 1
        if ($null -eq $latest -or $latest.State -ne 'Enqueued') {
            return $false
        }
    }
    return $true
}

function Invoke-ReaderQaCorrelatedSwipeTerminal(
    [Collections.Generic.HashSet[long]] $SeenGestureIds,
    [long] $ReaderSession,
    [int] $StartX,
    [int] $EndX,
    [int] $Y,
    [int] $DurationMs,
    [string] $Context,
    [ValidateRange(1, 5)]
    [int] $MaximumInputAttempts = 3
) {
    for ($inputAttempt = 1; $inputAttempt -le $MaximumInputAttempts; $inputAttempt += 1) {
        $inputRequestId = "input-$([guid]::NewGuid().ToString('N'))"
        Invoke-ReaderQaFaultCommand `
            -RequestId $inputRequestId `
            -Command 'arm-input'
        $armed = Wait-ReaderQaInputState `
            -RequestId $inputRequestId `
            -State 'Armed' `
            -Context "$Context input arm $inputAttempt"
        if (-not $armed.Match.Accepted) {
            throw "$Context could not arm input correlation $inputRequestId"
        }

        Invoke-Adb @(
            'shell', 'input', 'swipe',
            "$StartX", "$Y", "$EndX", "$Y", "$DurationMs"
        )
        $admitted = Wait-ReaderQaInputState `
            -RequestId $inputRequestId `
            -State 'Admitted' `
            -Context "$Context input admission $inputAttempt" `
            -WaitSeconds 3 `
            -ReturnNullOnTimeout
        if ($null -eq $admitted) {
            Invoke-ReaderQaFaultCommand `
                -RequestId $inputRequestId `
                -Command 'clear-input'
            $cleared = Wait-ReaderQaInputState `
                -RequestId $inputRequestId `
                -State 'Cleared' `
                -Context "$Context input clear $inputAttempt"
            if (-not $cleared.Match.Accepted) {
                $admitted = Wait-ReaderQaInputState `
                    -RequestId $inputRequestId `
                    -State 'Admitted' `
                    -Context "$Context delayed input admission $inputAttempt" `
                    -WaitSeconds 10 `
                    -ReturnNullOnTimeout
                if ($null -eq $admitted) {
                    throw "$Context consumed input correlation without an admission record"
                }
            } elseif ($inputAttempt -lt $MaximumInputAttempts) {
                $silentInputRecoveryLog = Read-ReaderPidLog `
                    -Context "$Context silent input recovery $inputAttempt" `
                    -Full
                $preparationRecords = @(
                    ConvertFrom-ReaderPreparationLog $silentInputRecoveryLog |
                        Where-Object Session -eq $ReaderSession
                )
                if ($preparationRecords.Count -eq 0) {
                    Start-Sleep -Milliseconds 250
                    continue
                }
                $latestPreparation = $preparationRecords[-1]
                $recoveryAction = Get-ReaderPreparationRecoveryAction `
                    -State $latestPreparation.State `
                    -PreparationIndex $latestPreparation.Index `
                    -TerminalIndex ([int]::MaxValue)
                switch ($recoveryAction) {
                    'QuiesceReadyBeforeTerminal' {
                        Start-Sleep -Milliseconds 750
                    }
                    'AwaitCurrentAttempt' {
                        [void](Wait-ReaderQaPreparationAttemptTerminal `
                            -ReaderSession $ReaderSession `
                            -Attempt $latestPreparation.Attempt `
                            -RasterGeneration $latestPreparation.RasterGeneration `
                            -AfterIndex $latestPreparation.Index `
                            -Context "$Context silent input recovery $inputAttempt" `
                            -WaitSeconds $preparationRecoveryTimeoutSeconds)
                        Start-Sleep -Milliseconds 750
                    }
                    'AwaitNextAttempt' {
                        Start-Sleep -Milliseconds 750
                    }
                    default {
                        throw "$Context silent input recovery chose an unknown action: $recoveryAction"
                    }
                }
                continue
            } else {
                throw "$Context input was dropped within its bounded retry count"
            }
        }
        if ($admitted.Match.Session -ne $ReaderSession) {
            throw "$Context input was admitted by a different reader session"
        }
        $gestureId = [long]$admitted.Match.GestureId
        if ($SeenGestureIds.Contains($gestureId)) {
            throw "$Context input reused a gesture identity"
        }
        $terminal = Wait-ReaderQaCondition `
            -Context "$Context gesture $gestureId terminal" `
            -WaitSeconds 60 `
            -Select {
                param($log)
                ConvertFrom-ReaderGestureLog $log | Where-Object {
                    $_.Session -eq $ReaderSession -and
                        $_.GestureId -eq $gestureId
                }
            }
        return $terminal.Match
    }
    throw "$Context input was dropped within its bounded retry count"
}

function Invoke-ReaderQaCommittedTurn(
    [Collections.Generic.HashSet[long]] $SeenGestureIds,
    [long] $ReaderSession,
    [int] $StartX,
    [int] $EndX,
    [int] $Y,
    [string] $Context,
    [ValidateRange(1, 20)]
    [int] $MaximumAttempts = 20,
    [ValidateRange(1, 5)]
    [int] $MaximumInputAttempts = 3,
    [string[]] $RetryOnlyWhileFaultsRemainEnqueued = @()
) {
    $retryOutcomes = @(
        'CancelledByUser',
        'RejectedPreparing',
        'RejectedSettling',
        'RejectedRendererUnavailable'
    )
    for ($attempt = 1; $attempt -le $MaximumAttempts; $attempt += 1) {
        $terminal = Invoke-ReaderQaCorrelatedSwipeTerminal `
            -SeenGestureIds $SeenGestureIds `
            -ReaderSession $ReaderSession `
            -StartX $StartX `
            -EndX $EndX `
            -Y $Y `
            -DurationMs 400 `
            -Context "$Context attempt $attempt" `
            -MaximumInputAttempts $MaximumInputAttempts
        if (-not $SeenGestureIds.Add([long]$terminal.GestureId)) {
            throw "$Context reused a gesture identity"
        }
        if ($terminal.Outcome -in @('CommittedForward', 'CommittedBackward')) {
            return $terminal
        }
        if ($terminal.Outcome -notin $retryOutcomes) {
            throw "$Context produced a non-retryable terminal: $($terminal.LogLine)"
        }
        if ($RetryOnlyWhileFaultsRemainEnqueued.Count -gt 0) {
            if (
                $terminal.Outcome -ne 'CancelledByUser' -or
                -not (Test-ReaderQaFaultRequestsRemainEnqueued `
                    -RequestIds $RetryOnlyWhileFaultsRemainEnqueued `
                    -Context "$Context fault retry guard")
            ) {
                throw "$Context cannot retry after fault consumption: $($terminal.LogLine)"
            }
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
    [string] $Context,
    [switch] $Full
) {
    return Wait-ReaderQaCondition `
        -Context $Context `
        -WaitSeconds 30 `
        -Full:$Full `
        -Select {
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
    [string] $Context,
    [switch] $Full
) {
    return Wait-ReaderQaRelocationTerminal `
        -ReaderSession $ReaderSession `
        -GestureId $GestureId `
        -States @('Completed') `
        -Context $Context `
        -Full:$Full
}

function Get-ReaderQaSwipeCoordinates(
    [ValidateSet('Next', 'Previous')]
    [string] $LogicalDirection,
    [hashtable] $PhysicalDirectionByLogical,
    [int] $PhysicalRight,
    [int] $PhysicalLeft
) {
    $physicalDirection = $PhysicalDirectionByLogical[$LogicalDirection]
    if ($physicalDirection -notin @('Left', 'Right')) {
        throw "ReaderDev has no physical direction for $LogicalDirection"
    }
    return [pscustomobject]@{
        PhysicalDirection = $physicalDirection
        StartX = if ($physicalDirection -eq 'Left') {
            $PhysicalRight
        } else {
            $PhysicalLeft
        }
        EndX = if ($physicalDirection -eq 'Left') {
            $PhysicalLeft
        } else {
            $PhysicalRight
        }
    }
}

function Resolve-ReaderQaPhysicalDirections(
    [long] $ReaderSession,
    [int] $PhysicalRight,
    [int] $PhysicalLeft,
    [int] $Y
) {
    $seen = [Collections.Generic.HashSet[long]]::new()
    ConvertFrom-ReaderGestureLog (
        Read-ReaderPidLog -Context 'ReaderDev direction probe baseline' -Full
    ) |
        Where-Object Session -eq $ReaderSession |
        ForEach-Object { [void]$seen.Add($_.GestureId) }

    Invoke-Adb @(
        'shell', 'input', 'swipe',
        "$PhysicalRight", "$Y", "$PhysicalLeft", "$Y", '400'
    )
    $terminal = Wait-ReaderQaCondition `
        -Context 'ReaderDev physical direction probe' `
        -WaitSeconds 10 `
        -Select {
            param($log)
            ConvertFrom-ReaderGestureLog $log | Where-Object {
                $_.Session -eq $ReaderSession -and
                    -not $seen.Contains($_.GestureId)
            }
        }
    if (-not $seen.Add([long]$terminal.Match.GestureId)) {
        throw 'ReaderDev physical direction probe reused a gesture identity'
    }
    if ($terminal.Match.Outcome -notin @(
        'CommittedForward',
        'CommittedBackward',
        'RejectedBoundary'
    )) {
        throw "ReaderDev physical direction probe was inconclusive: $($terminal.Match.LogLine)"
    }
    if ($terminal.Match.PhysicalDirection -ne 'Left' -or
        $terminal.Match.LogicalDirection -notin @('Next', 'Previous')) {
        throw "ReaderDev physical direction probe was malformed: $($terminal.Match.LogLine)"
    }
    if ($terminal.Match.Outcome -in @('CommittedForward', 'CommittedBackward')) {
        $expectedLogicalDirection = if (
            $terminal.Match.Outcome -eq 'CommittedForward'
        ) { 'Next' } else { 'Previous' }
        if ($terminal.Match.LogicalDirection -ne $expectedLogicalDirection) {
            throw "ReaderDev physical direction probe outcome disagreed with its direction: $($terminal.Match.LogLine)"
        }
        [void](Wait-ReaderQaRelocationCompleted `
            -ReaderSession $ReaderSession `
            -GestureId $terminal.Match.GestureId `
            -Context 'ReaderDev physical direction probe relocation')
        $preparationRecords = @(
            ConvertFrom-ReaderPreparationLog (
                Read-ReaderPidLog `
                    -Context 'ReaderDev physical direction probe preparation' `
                    -Full
            ) | Where-Object Session -eq $ReaderSession
        )
        if ($preparationRecords.Count -eq 0) {
            throw 'ReaderDev physical direction probe emitted no preparation record'
        }
        [void](Wait-ReaderQaWorkingSetReady `
            -ReaderSession $ReaderSession `
            -AtOrAfterAttempt $preparationRecords[-1].Attempt `
            -Context 'ReaderDev physical direction probe recovery')
    }

    $oppositeLogicalDirection = if (
        $terminal.Match.LogicalDirection -eq 'Next'
    ) { 'Previous' } else { 'Next' }
    $physicalDirectionByLogical = @{
        Next = $null
        Previous = $null
    }
    $physicalDirectionByLogical[$terminal.Match.LogicalDirection] = 'Left'
    $physicalDirectionByLogical[$oppositeLogicalDirection] = 'Right'
    return $physicalDirectionByLogical
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
            $_.RequestId -ceq $requestId -and $_.State -eq 'Applied'
        }).Count -ne 1) {
            throw "$Context did not apply $requestId exactly once"
        }
    }
    $allDownstream = @(Get-ReaderQaDownstreamEvents $Log)
    $downstream = @(
        $allDownstream | Where-Object {
            $_.QaFaultRequestId -in $RequestIds
        }
    )
    Assert-ReaderQaFaultCorrelation `
        -FaultEvents $faultEvents `
        -DownstreamEvents $downstream `
        -EvidenceEvents $allDownstream `
        -Context $Context
    return $downstream
}

function Invoke-ReaderQaBoundedAdb(
    [string[]] $Arguments,
    [DateTime] $DeadlineUtc
) {
    if ([DateTime]::UtcNow -ge $DeadlineUtc) { return '' }
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'adb'
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    @('-s', $DeviceSerial) + $Arguments | ForEach-Object {
        [void]$startInfo.ArgumentList.Add($_)
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if ([DateTime]::UtcNow -ge $DeadlineUtc) { return '' }
        if (-not $process.Start()) {
            throw 'ReaderDev bounded ADB process did not start'
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
            throw 'ReaderDev bounded ADB command failed'
        }
        return $stdout
    } finally {
        $process.Dispose()
    }
}

function Get-ReaderQaRasterStorageState(
    [string] $ArtifactName,
    [DateTime] $DeadlineUtc
) {
    $storageCommand = @'
root=files/reader/reader-page-rasters/v1
count=$(ls "$root"/*.png 2>/dev/null | wc -l)
bytes=$(wc -c < "$root"/manifest.json 2>/dev/null || printf 0)
hash=$(sha256sum "$root"/manifest.json 2>/dev/null | cut -d" " -f1)
printf "RasterFileCount=%s ManifestBytes=%s ManifestSha256=%s\n" "$count" "$bytes" "$hash"
'@
    $quotedStorageCommand = "'$storageCommand'"
    $storageText = Invoke-ReaderQaBoundedAdb `
        -Arguments @(
            'shell',
            'run-as',
            'darkaxt.navic.readerdev',
            'sh',
            '-c',
            $quotedStorageCommand
        ) `
        -DeadlineUtc $DeadlineUtc
    $storageMatch = [regex]::Match(
        $storageText,
        'RasterFileCount=(?<Count>\d+)\s+' +
            'ManifestBytes=(?<Bytes>\d+)\s+' +
            'ManifestSha256=(?<Sha256>[0-9a-f]{64})'
    )
    if (-not $storageMatch.Success) {
        throw 'ReaderDev raster storage state was unavailable'
    }
    $state = [pscustomobject][ordered]@{
        SchemaVersion = 1
        RasterFileCount = [int]$storageMatch.Groups['Count'].Value
        ManifestBytes = [long]$storageMatch.Groups['Bytes'].Value
        ManifestSha256 = $storageMatch.Groups['Sha256'].Value.ToUpperInvariant()
    }
    if ($state.RasterFileCount -lt 1 -or $state.ManifestBytes -lt 1) {
        throw 'ReaderDev raster storage was empty after durable preparation'
    }
    $state | ConvertTo-Json -Depth 2 |
        Set-Content (Join-Path $ArtifactRoot $ArtifactName)
    return $state
}

function Get-ReaderQaDisplayGeometry([DateTime] $DeadlineUtc) {
    $displayText = Invoke-ReaderQaBoundedAdb `
        -Arguments @('shell', 'dumpsys', 'window', 'displays') `
        -DeadlineUtc $DeadlineUtc
    $displayMatch = [regex]::Matches(
        $displayText,
        'cur=(?<Width>\d+)x(?<Height>\d+)'
    ) | Select-Object -First 1
    if ($null -eq $displayMatch) {
        throw 'ReaderDev current display geometry was unavailable'
    }
    [pscustomobject]@{
        Width = [int]$displayMatch.Groups['Width'].Value
        Height = [int]$displayMatch.Groups['Height'].Value
    }
}

function Invoke-ReaderQaPreparationRetry(
    [long] $ReaderSession,
    [long] $AfterIndex
) {
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    $geometry = Get-ReaderQaDisplayGeometry -DeadlineUtc $deadline
    $densityText = Invoke-ReaderQaBoundedAdb `
        -Arguments @('shell', 'wm', 'density') `
        -DeadlineUtc $deadline
    $densityMatch = [regex]::Matches(
        $densityText,
        'density:\s*(?<Value>\d+)'
    ) | Select-Object -Last 1
    if ($null -eq $densityMatch) {
        throw 'ReaderDev display density was unavailable for Retry'
    }
    $width = [int]$geometry.Width
    $height = [int]$geometry.Height
    $density = [int]$densityMatch.Groups['Value'].Value / 160.0
    $horizontalOffsetsDp = @(120, 160, 200, 220, 240)
    $bottomOffsetsDp = @(55, 70, 85)
    $attemptedCoordinates = [Collections.Generic.HashSet[string]]::new()
    foreach ($bottomOffsetDp in $bottomOffsetsDp) {
        foreach ($horizontalOffsetDp in $horizontalOffsetsDp) {
            if ([DateTime]::UtcNow -ge $deadline) { break }
            $tapX = [Math]::Min(
                $width - 1,
                [Math]::Max(
                    0,
                    [int][Math]::Round(
                        ($width / 2.0) + ($horizontalOffsetDp * $density)
                    )
                )
            )
            $tapY = [Math]::Min(
                $height - 1,
                [Math]::Max(
                    0,
                    [int][Math]::Round(
                        $height - ($bottomOffsetDp * $density)
                    )
                )
            )
            if (-not $attemptedCoordinates.Add("$tapX,$tapY")) { continue }
            [void](Invoke-ReaderQaBoundedAdb `
                -Arguments @('shell', 'input', 'tap', "$tapX", "$tapY") `
                -DeadlineUtc $deadline)
            $tapDeadline = [DateTime]::UtcNow.AddSeconds(1)
            if ($tapDeadline -gt $deadline) { $tapDeadline = $deadline }
            do {
                $log = Read-ReaderPidLog `
                    -Context 'ReaderDev preparation Retry action' `
                    -Full
                $retryAttempt = ConvertFrom-ReaderPreparationLog $log |
                    Where-Object {
                        $_.Session -eq $ReaderSession -and
                            $_.State -eq 'Attempted' -and
                            $_.Index -gt $AfterIndex
                    } |
                    Select-Object -First 1
                if ($null -ne $retryAttempt) { return }
                $remainingMilliseconds = [int][Math]::Floor(
                    ($tapDeadline - [DateTime]::UtcNow).TotalMilliseconds
                )
                if ($remainingMilliseconds -gt 0) {
                    Start-Sleep -Milliseconds (
                        [Math]::Min(100, $remainingMilliseconds)
                    )
                }
            } while ([DateTime]::UtcNow -lt $tapDeadline)
        }
        if ([DateTime]::UtcNow -ge $deadline) { break }
    }
    throw 'ReaderDev failure overlay Retry action was not reachable'
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
    Invoke-ReaderQaPreparationRetry `
        -ReaderSession $readerSession `
        -AfterIndex $preparationFailure.Match.Index
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
    [int] $Y,
    [hashtable] $PhysicalDirectionByLogical
) {
    $suffix = $runId.Substring(0, 8)
    $nextSwipe = Get-ReaderQaSwipeCoordinates `
        -LogicalDirection Next `
        -PhysicalDirectionByLogical $PhysicalDirectionByLogical `
        -PhysicalRight $PhysicalRight `
        -PhysicalLeft $PhysicalLeft
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
        -StartX $nextSwipe.StartX -EndX $nextSwipe.EndX -Y $Y `
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
        -StartX $nextSwipe.StartX -EndX $nextSwipe.EndX -Y $Y `
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
        -StartX $nextSwipe.StartX -EndX $nextSwipe.EndX -Y $Y `
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
    $visualRelocation = Wait-ReaderQaRelocationCompleted `
        $ReaderSession `
        $visualTurn.GestureId `
        'ReaderDev visual relocation'
    [void](Wait-ReaderQaPreparedTextureGeneration `
        -ReaderSession $ReaderSession `
        -GestureId $visualTurn.GestureId `
        -TextureGeneration $visualRelocation.Match.TextureGeneration `
        -Context 'ReaderDev visual promoted texture preparation')

    Add-ReaderQaFault $repairId 'ForceRepairWithoutPreparedDeck'
    $missId = "miss-$suffix"
    $missIds.Add($missId)
    Add-ReaderQaFault $missId 'MissNextRasterLoad'
    $repairSwipe = Get-ReaderQaSwipeCoordinates `
        -LogicalDirection 'Previous' `
        -PhysicalDirectionByLogical $PhysicalDirectionByLogical `
        -PhysicalRight $PhysicalRight `
        -PhysicalLeft $PhysicalLeft
    $repairTurn = Invoke-ReaderQaCommittedTurn `
        -SeenGestureIds $seen -ReaderSession $ReaderSession `
        -StartX $repairSwipe.StartX -EndX $repairSwipe.EndX `
        -Y $Y `
        -Context 'ReaderDev settlement-fenced repair fault turn' `
        -MaximumAttempts 3 `
        -RetryOnlyWhileFaultsRemainEnqueued @($missId, $repairId)
    [void](Wait-ReaderQaFaultState `
        $missId `
        'Applied' `
        'ReaderDev settlement-fenced raster miss' `
        60)
    $forcedRepairApplied = Wait-ReaderQaFaultState `
        $repairId `
        'Applied' `
        'ReaderDev settlement-fenced forced repair' `
        60
    $forcedRepairAttemptId = [long]$forcedRepairApplied.Match.RepairAttemptId
    if ($forcedRepairAttemptId -lt 0) {
        throw 'ReaderDev forced repair omitted its repair attempt identity'
    }
    $forcedRepairTerminal = Wait-ReaderQaCondition `
        -Context 'ReaderDev forced repair terminal' `
        -WaitSeconds 60 `
        -Full `
        -Select {
            param($log)
            ConvertFrom-ReaderRepairLog $log | Where-Object {
                $_.Session -eq $ReaderSession -and
                    $_.Attempt -eq $forcedRepairAttemptId -and
                    $_.State -in @('Completed', 'Cancelled')
            }
        }
    $repairRelocation = $null
    $repairDeck = $null
    if ($forcedRepairTerminal.Match.State -eq 'Cancelled') {
        $repairRelocation = Wait-ReaderQaRelocationTerminal `
            -ReaderSession $ReaderSession `
            -GestureId $repairTurn.GestureId `
            -States @('Completed') `
            -Context 'ReaderDev superseding repair relocation' `
            -Full
        [void](Wait-ReaderQaPreparedTextureGeneration `
            -ReaderSession $ReaderSession `
            -GestureId $repairTurn.GestureId `
            -TextureGeneration $repairRelocation.Match.TextureGeneration `
            -Context 'ReaderDev superseding repair texture preparation')
    } else {
        $repairRelocation = Wait-ReaderQaRelocationTerminal `
            -ReaderSession $ReaderSession `
            -GestureId $repairTurn.GestureId `
            -States @('Completed', 'Rejected') `
            -Context 'ReaderDev completed active repair relocation' `
            -Full
        $repairDeck = Wait-ReaderQaCondition `
            -Context 'ReaderDev completed active repair deck' `
            -WaitSeconds 60 `
            -Full `
            -Select {
                param($log)
                ConvertFrom-ReaderDeckLog $log | Where-Object {
                    $_.Session -eq $ReaderSession -and
                        $_.RepairAttempt -eq $forcedRepairAttemptId -and
                        $_.Role -eq 'Active' -and
                        $_.Prepared -and
                        $_.Active -eq $_.Generation -and
                        $null -eq $_.Pending -and
                        $_.QaFaultRequestId -eq $repairId -and
                        $_.QaFaultRelation -eq 'AppliedOperation' -and
                        $_.QaFaultRepairAttemptId -eq $forcedRepairAttemptId
                }
            }
    }
    $repairResolutionIndex = [Math]::Max(
        $forcedRepairTerminal.Match.Index,
        $repairRelocation.Match.Index
    )
    if ($null -ne $repairDeck) {
        $repairResolutionIndex = [Math]::Max(
            $repairResolutionIndex,
            $repairDeck.Match.Index
        )
    }
    [void](Wait-ReaderQaCondition `
        -Context 'ReaderDev fault matrix ownership drain' `
        -WaitSeconds 60 `
        -Full `
        -Select {
            param($log)
            Get-ReaderFaultMatrixOwnershipDrainProof `
                -Log $log `
                -ReaderSession $ReaderSession `
                -ResolutionBoundary $repairResolutionIndex `
                -RepairTerminalIndex $forcedRepairTerminal.Match.Index `
                -RasterGeneration $forcedRepairTerminal.Match.RasterGeneration
        })

    $proofTurn = Invoke-ReaderQaCommittedTurn `
        -SeenGestureIds $seen -ReaderSession $ReaderSession `
        -StartX $nextSwipe.StartX -EndX $nextSwipe.EndX -Y $Y `
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
    [void](Assert-ForcedRepairAttemptResolution `
        -Log $finalLog `
        -ReaderSession $ReaderSession `
        -RepairFaultRequestId $repairId `
        -RasterMissRequestId $missId `
        -GestureId $repairTurn.GestureId `
        -TextureGeneration $repairTurn.TextureGeneration `
        -Context 'ReaderDev fault matrix repair')
    return $finalLog
}

$runSucceeded = $false
$originalAnimatorDurationScale = $null
$animatorDurationScaleOverridePending = $false
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
$originalAnimatorDurationScale = Get-ReaderAnimatorDurationScale
$animatorDurationScaleOverridePending = $true
Set-ReaderAnimatorDurationScale '20.0'
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
    -StartAtBeginning `
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
$rasterStorageBeforeForceStop = Get-ReaderQaRasterStorageState `
    -ArtifactName 'raster-storage-before-force-stop.json' `
    -DeadlineUtc ([DateTime]::UtcNow.AddSeconds(10))
Invoke-Adb @("shell", "am", "force-stop", "darkaxt.navic.readerdev")
$script:ReaderPid = $null
$rasterStorageAfterForceStop = Get-ReaderQaRasterStorageState `
    -ArtifactName 'raster-storage-after-force-stop.json' `
    -DeadlineUtc ([DateTime]::UtcNow.AddSeconds(10))
if (
    $rasterStorageAfterForceStop.RasterFileCount -ne
        $rasterStorageBeforeForceStop.RasterFileCount -or
    $rasterStorageAfterForceStop.ManifestBytes -ne
        $rasterStorageBeforeForceStop.ManifestBytes -or
    $rasterStorageAfterForceStop.ManifestSha256 -cne
        $rasterStorageBeforeForceStop.ManifestSha256
) {
    throw 'ReaderDev raster storage changed during force-stop'
}
Invoke-Adb @("logcat", "-c")
Reset-ReaderLogAccumulator
Open-ReaderDev -AtPublicationStart
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
$displayGeometry = Get-ReaderQaDisplayGeometry `
    -DeadlineUtc ([DateTime]::UtcNow.AddSeconds(5))
$width = [int]$displayGeometry.Width
$height = [int]$displayGeometry.Height
$physicalRight = [int]($width * 0.82)
$physicalLeft = [int]($width * 0.18)
$y = [int]($height * 0.50)
$physicalDirectionByLogical = Resolve-ReaderQaPhysicalDirections `
    -ReaderSession $readerSession `
    -PhysicalRight $physicalRight `
    -PhysicalLeft $physicalLeft `
    -Y $y
$faultMatrixLog = Invoke-ReaderQaFaultMatrix `
    -ReaderSession $readerSession `
    -PhysicalRight $physicalRight `
    -PhysicalLeft $physicalLeft `
    -Y $y `
    -PhysicalDirectionByLogical $physicalDirectionByLogical
if (-not (Restore-ReaderAnimatorDurationScale $originalAnimatorDurationScale)) {
    throw 'ReaderDev animator duration scale restoration failed'
}
$animatorDurationScaleOverridePending = $false
Start-Sleep -Milliseconds 500
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
$minimumNextCommitsAfterBoundary = [Math]::Max(
    $minimumCommitsPerDirection,
    $minimumDistinctOrdinals - 1
)
$maximumBoundarySeekCommits = 40
$maximumCommittedTurns = [Math]::Max(
    $StressTurns,
    $maximumBoundarySeekCommits +
        $minimumNextCommitsAfterBoundary +
        $minimumCommitsPerDirection
)
$maximumAttempts = ($maximumCommittedTurns * 3) + 40
$transientRetryOutcomes = @(
    'CancelledByUser',
    'RejectedPreparing',
    'RejectedSettling',
    'RejectedRendererUnavailable'
)
$attempt = 0
$committedTurns = 0
$lastCommittedGestureId = 0L
$boundaryCount = 0
$boundarySeekCommits = 0
$nextCommitsAfterBoundary = 0
$previousCommitsAfterExpansion = 0
$stressPhase = 'SeekPreviousBoundary'
$requestedLogicalDirection = 'Previous'

while ($attempt -lt $maximumAttempts) {
    $hasMinimumCoverage =
        $committedTurns -ge $StressTurns -and
        $boundaryCount -ge 1 -and
        $directionCommitCounts.Next -ge $minimumCommitsPerDirection -and
        $directionCommitCounts.Previous -ge $minimumCommitsPerDirection -and
        $stressPhase -eq 'Alternating'
    if ($hasMinimumCoverage) { break }

    $requestedPhysicalDirection =
        $physicalDirectionByLogical[$requestedLogicalDirection]
    $startX = if ($requestedPhysicalDirection -eq 'Left') {
        $physicalRight
    } else {
        $physicalLeft
    }
    $endX = if ($requestedPhysicalDirection -eq 'Left') {
        $physicalLeft
    } else {
        $physicalRight
    }
    $attempt += 1
    $newTerminal = Invoke-ReaderQaCorrelatedSwipeTerminal `
        -SeenGestureIds $seenGestureIds `
        -ReaderSession $readerSession `
        -StartX $startX `
        -EndX $endX `
        -Y $y `
        -DurationMs 180 `
        -Context "ReaderDev stress attempt $attempt"
    $log = Read-ReaderPidLog "ReaderDev stress attempt $attempt"
    Assert-ReaderOwnershipUnavailablePolicy `
        -Log $log `
        -ReaderSession $readerSession `
        -Context "ReaderDev stress attempt $attempt" `
        -AllowPendingRecovery
    [void](Get-CommittedTurnCount $log $readerSession)
    if (-not $seenGestureIds.Add($newTerminal.GestureId)) {
        throw "ReaderDev stress reused gesture $($newTerminal.GestureId)"
    }
    $stressTerminals.Add($newTerminal)

    if ($newTerminal.Outcome -in $transientRetryOutcomes) {
        if ($newTerminal.Outcome -in @(
            'RejectedPreparing',
            'RejectedRendererUnavailable'
        )) {
            $fullRecoveryLog = Read-ReaderPidLog `
                -Context 'ReaderDev stress preparing boundary' `
                -Full
            $terminalInFullLog = @(
                ConvertFrom-ReaderGestureLog $fullRecoveryLog | Where-Object {
                    $_.Session -eq $readerSession -and
                        $_.GestureId -eq $newTerminal.GestureId
                }
            )
            if ($terminalInFullLog.Count -ne 1) {
                throw 'ReaderDev stress preparing recovery lost its gesture terminal'
            }
            $relocationGroups = @(
                ConvertFrom-ReaderRelocationLog $fullRecoveryLog |
                    Where-Object {
                        $_.Session -eq $readerSession -and
                            $committedGestureIds.Contains($_.GestureId)
                    } |
                    Group-Object GestureId
            )
            $overlappingRelocationGroups = @(
                foreach ($group in $relocationGroups) {
                    $relocationStateAtTerminal = @(
                        $group.Group | Where-Object {
                            $_.Index -le $terminalInFullLog.Index
                        }
                    )[-1]
                    if ($null -ne $relocationStateAtTerminal -and
                        $relocationStateAtTerminal.State -notin @(
                            'Completed',
                            'Rejected'
                        )) {
                        $group
                    }
                }
            )
            $inFlightRelocationGestureIds = @(
                foreach ($group in $overlappingRelocationGroups) {
                    $latestRelocation = $group.Group[-1]
                    if ($latestRelocation.State -notin @('Completed', 'Rejected')) {
                        [long]$group.Name
                    }
                }
            )
            foreach ($gestureId in $inFlightRelocationGestureIds) {
                [void](Wait-ReaderQaRelocationTerminal `
                    -ReaderSession $readerSession `
                    -GestureId $gestureId `
                    -States @('Completed', 'Rejected') `
                    -Context 'ReaderDev stress relocation recovery')
            }
            if ($overlappingRelocationGroups.Count -eq 0) {
                $preparationRecords = @(
                    ConvertFrom-ReaderPreparationLog $fullRecoveryLog |
                        Where-Object Session -eq $readerSession
                )
                if ($preparationRecords.Count -eq 0) {
                    throw 'ReaderDev stress preparing recovery has no preparation record'
                }
                $latestPreparation = $preparationRecords[-1]
                $recoveryAction = Get-ReaderPreparationRecoveryAction `
                    -State $latestPreparation.State `
                    -PreparationIndex $latestPreparation.Index `
                    -TerminalIndex $terminalInFullLog.Index
                switch ($recoveryAction) {
                    'ReadyAfterTerminal' {}
                    'QuiesceReadyBeforeTerminal' {
                        Start-Sleep -Milliseconds 750
                    }
                    'AwaitCurrentAttempt' {
                        [void](Wait-ReaderQaWorkingSetReady `
                            -ReaderSession $readerSession `
                            -AtOrAfterAttempt $latestPreparation.Attempt `
                            -Context 'ReaderDev stress preparing recovery' `
                            -WaitSeconds $preparationRecoveryTimeoutSeconds)
                    }
                    'AwaitNextAttempt' {
                        [void](Wait-ReaderQaWorkingSetReady `
                            -ReaderSession $readerSession `
                            -AtOrAfterAttempt ($latestPreparation.Attempt + 1) `
                            -Context 'ReaderDev stress preparing recovery' `
                            -WaitSeconds $preparationRecoveryTimeoutSeconds)
                    }
                    default {
                        throw "ReaderDev stress preparing recovery chose an unknown action: $recoveryAction"
                    }
                }
            }
        } elseif ($newTerminal.Outcome -eq 'RejectedSettling') {
            Start-Sleep -Milliseconds 750
        } else {
            Start-Sleep -Milliseconds 250
        }
        continue
    }

    $logicalDirection = [string]$newTerminal.LogicalDirection
    if ($logicalDirection -notin @('Next', 'Previous')) {
        throw "ReaderDev swipe terminal lacks a logical direction: $($newTerminal.LogLine)"
    }
    if ($newTerminal.PhysicalDirection -notin @('Left', 'Right')) {
        throw "ReaderDev swipe terminal lacks a physical direction: $($newTerminal.LogLine)"
    }
    if ($logicalDirection -ne $requestedLogicalDirection -or
        $newTerminal.PhysicalDirection -ne $requestedPhysicalDirection) {
        throw "ReaderDev swipe terminal disagrees with its requested direction: $($newTerminal.LogLine)"
    }

    if ($newTerminal.Outcome -in @('CommittedForward', 'CommittedBackward')) {
        $expectedDirection = if (
            $newTerminal.Outcome -eq 'CommittedForward'
        ) { 'Next' } else { 'Previous' }
        if ($logicalDirection -ne $expectedDirection) {
            throw "ReaderDev commit outcome and logical direction disagree: $($newTerminal.LogLine)"
        }
        if (-not $committedGestureIds.Add($newTerminal.GestureId)) {
            throw "ReaderDev stress duplicated committed gesture $($newTerminal.GestureId)"
        }
        $directionCommitCounts[$logicalDirection] += 1
        $committedTurns += 1
        $lastCommittedGestureId = [long]$newTerminal.GestureId
        $phaseRelocationCompleted = $true
        if ($stressPhase -in @('ExpandNext', 'BacktrackPrevious')) {
            $phaseRelocation = Wait-ReaderQaRelocationTerminal `
                -ReaderSession $readerSession `
                -GestureId $newTerminal.GestureId `
                -States @('Completed', 'Rejected') `
                -Context "ReaderDev stress $stressPhase relocation"
            $phaseRelocationCompleted = $phaseRelocation.Match.State -eq 'Completed'
        }
        switch ($stressPhase) {
            'SeekPreviousBoundary' {
                $boundarySeekCommits += 1
                if ($boundarySeekCommits -gt $maximumBoundarySeekCommits) {
                    throw 'ReaderDev did not reach the deterministic previous boundary within its bounded probe'
                }
            }
            'ExpandNext' {
                if ($phaseRelocationCompleted) {
                    $nextCommitsAfterBoundary += 1
                    if ($nextCommitsAfterBoundary -ge
                        $minimumNextCommitsAfterBoundary) {
                        $stressPhase = 'BacktrackPrevious'
                        $requestedLogicalDirection = 'Previous'
                    }
                }
            }
            'BacktrackPrevious' {
                if ($phaseRelocationCompleted) {
                    $previousCommitsAfterExpansion += 1
                    if ($previousCommitsAfterExpansion -ge
                        $minimumCommitsPerDirection) {
                        $stressPhase = 'Alternating'
                        $requestedLogicalDirection = 'Next'
                    }
                }
            }
            'Alternating' {
                $requestedLogicalDirection = if (
                    $requestedLogicalDirection -eq 'Next'
                ) { 'Previous' } else { 'Next' }
            }
            default {
                throw "ReaderDev stress entered an unknown phase: $stressPhase"
            }
        }
        continue
    }

    if ($newTerminal.Outcome -ne 'RejectedBoundary') {
        throw "ReaderDev stress emitted an unexpected terminal: $($newTerminal.LogLine)"
    }
    $boundaryCount += 1
    switch ($stressPhase) {
        'SeekPreviousBoundary' {
            if ($logicalDirection -ne 'Previous') {
                throw 'ReaderDev initial boundary probe was not Previous'
            }
            $stressPhase = 'ExpandNext'
            $requestedLogicalDirection = 'Next'
        }
        'ExpandNext' {
            throw 'ReaderDev publication is too short for bounded distinct-ordinal coverage'
        }
        'BacktrackPrevious' {
            throw 'ReaderDev reached the previous boundary before backward coverage completed'
        }
        'Alternating' {
            $requestedLogicalDirection = if (
                $requestedLogicalDirection -eq 'Next'
            ) { 'Previous' } else { 'Next' }
        }
        default {
            throw "ReaderDev stress entered an unknown phase: $stressPhase"
        }
    }
}

if ($committedTurns -lt $StressTurns -or
    $boundaryCount -lt 1 -or
    $directionCommitCounts.Next -lt $minimumCommitsPerDirection -or
    $directionCommitCounts.Previous -lt $minimumCommitsPerDirection -or
    $stressPhase -ne 'Alternating') {
    throw "ReaderDev bounded stress coverage failed commits=$committedTurns " +
        "next=$($directionCommitCounts.Next) previous=$($directionCommitCounts.Previous) " +
        "boundaries=$boundaryCount attempts=$attempt maximumCommitted=$maximumCommittedTurns " +
        "phase=$stressPhase"
}
[void](Wait-ReaderQaRelocationTerminal `
    -ReaderSession $readerSession `
    -GestureId $lastCommittedGestureId `
    -States @('Completed', 'Rejected') `
    -Context 'ReaderDev final stress relocation')

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
    $relocationDrain = Get-ReaderCommittedRelocationDrainStatus `
        -Log $log `
        -ReaderSession $readerSession `
        -CommittedGestureIds @($committedGestureIds) `
        -Context 'ReaderDev stress drain'
    $completedRelocations = @($relocationDrain.CompletedRelocations)
    $rejectedRelocations = @($relocationDrain.RejectedRelocations)
    $recoveredRejectedRelocations = @(
        $relocationDrain.RecoveredRejectedRelocations
    )
    $lastTerminalRelocationIndex = [long](
        @($relocationDrain.TerminalRelocations |
            Measure-Object -Property Index -Maximum)[0].Maximum
    )
    $drainedOwnership = @(
        $ownership | Where-Object {
            $_.Phase -eq 'steady-state' -and
                $_.Index -gt $lastTerminalRelocationIndex -and
                $_.PendingLeases -eq 0 -and
                $_.ReleaseInFlightLeases -eq 0 -and
                $_.OrphanLeases -eq 0 -and
                $_.RelocationReservations -eq 0 -and
                $_.QueuedRelocations -eq 0 -and
                $_.Relocations -eq 0
        }
    )
    if ($relocationDrain.PendingCount -eq 0 -and
        $drainedOwnership.Count -gt 0 -and
        $recoveredRejectedRelocations.Count -eq $rejectedRelocations.Count) {
        break
    }
    Start-Sleep -Milliseconds 250
} while ([DateTime]::UtcNow -lt $stressDeadline)
if ($relocationDrain.PendingCount -ne 0 -or
    $drainedOwnership.Count -eq 0 -or
    $recoveredRejectedRelocations.Count -ne $rejectedRelocations.Count) {
    throw "ReaderDev stress drain failed commits=$committedTurns " +
        "steadySnapshots=$steadyTurns completedRelocations=$($completedRelocations.Count) " +
        "rejectedRelocations=$($rejectedRelocations.Count) " +
        "recoveredRejectedRelocations=$($recoveredRejectedRelocations.Count) " +
        "pendingRelocations=$($relocationDrain.PendingCount) session=$readerSession"
}
Assert-ReaderOwnershipUnavailablePolicy `
    -Log $log `
    -ReaderSession $readerSession `
    -Context 'ReaderDev completed stress interval'

$orderedTerminalRelocations = @(
    $relocationDrain.TerminalRelocations | Sort-Object Index
)
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
    -Records @($orderedTerminalRelocations.LogLine) `
    -Name 'relocation-boundary-sweep.txt' `
    -Context 'ReaderDev boundary-sweep relocation evidence'
[ordered]@{
    SchemaVersion = 1
    MinimumCommittedTurns = $StressTurns
    ActualCommittedTurns = $committedTurns
    CompletedRelocations = $completedRelocations.Count
    RejectedRelocations = $rejectedRelocations.Count
    RecoveredRejectedRelocations = $recoveredRejectedRelocations.Count
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
if ($StressTurns -ge 20) {
    Assert-NoPostWarmupOwnershipGrowth $peak 10 'peak-preparation ownership'
    Assert-NoPostWarmupOwnershipGrowth $steady 10 'steady-state ownership'
}
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
    GitCommit = $implementationGitCommit
    AcceptanceToolingCommit = $acceptanceToolingCommit
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
        GitCommit = $implementationGitCommit
        AcceptanceToolingCommit = $acceptanceToolingCommit
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
    if ($animatorDurationScaleOverridePending) {
        $animatorDurationScaleRestored =
            Restore-ReaderAnimatorDurationScale $originalAnimatorDurationScale
        if ($animatorDurationScaleRestored) {
            $animatorDurationScaleOverridePending = $false
        } elseif ($runSucceeded) {
            throw 'ReaderDev successful run did not restore animator duration scale'
        } else {
            Write-Warning 'ReaderDev failed and animator duration scale restoration also failed'
        }
    }
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
