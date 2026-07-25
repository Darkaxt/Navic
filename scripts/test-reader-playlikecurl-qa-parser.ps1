$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
. (Join-Path $PSScriptRoot 'reader-playlikecurl-qa-parser.ps1')

function Assert-Throws([scriptblock] $Action, [string] $Name) {
    $threw = $false
    try { & $Action } catch { $threw = $true }
    if (-not $threw) { throw "$Name did not throw" }
}

$success =
    'reader-ownership session=7 phase=cold-start ' +
    'residents=1 residentLimit=2 adapterDecoded=1 adapterDecodedLimit=2 ' +
    'cacheDecoded=0 cacheDecodedLimit=2 staged=0 stagedLimit=4 ' +
    'activeLeases=1 activeLeaseLimit=1 pendingLeases=0 pendingLeaseLimit=1 ' +
    'releaseInFlightLeases=0 releaseInFlightLeaseLimit=4 ' +
    'orphanLeases=0 orphanLeaseLimit=0 textures=2 textureLimit=24 ' +
    'callbacks=1 callbackLimit=16 relocationReservations=0 ' +
    'queuedRelocations=0 relocations=0 relocationLimit=8 withinBounds=true'
$parsed = @(ConvertFrom-ReaderOwnershipLog $success)
if ($parsed.Count -ne 1) { throw 'Valid ownership fixture did not parse' }
if ($OwnershipBoundFields.Count -ne 11) {
    throw 'Ownership fixture did not enforce all eleven owner categories'
}
$expectedOwnershipPlateauFields = @(
    'Residents',
    'AdapterDecoded',
    'CacheDecoded',
    'Staged',
    'Textures'
)
if (@(
        Compare-Object `
            ($expectedOwnershipPlateauFields | Sort-Object) `
            ($OwnershipPlateauCountFields | Sort-Object)
    ).Count -ne 0) {
    throw 'Ownership plateau fixture did not enforce the stable resource categories'
}
foreach ($field in $OwnershipBoundFields) {
    if ($null -eq $parsed[0].PSObject.Properties[$field['Count']]) {
        throw "Missing parsed count field $($field['Count'])"
    }
    if ($null -eq $parsed[0].PSObject.Properties[$field['Limit']]) {
        throw "Missing parsed limit field $($field['Limit'])"
    }
}
Assert-Throws {
    $inconsistentRelocations = $success.Replace(
        'queuedRelocations=0 relocations=0',
        'queuedRelocations=1 relocations=0'
    )
    Assert-OwnershipWithinBounds `
        @(ConvertFrom-ReaderOwnershipLog $inconsistentRelocations) `
        'inconsistent relocation fixture'
} 'relocation ownership breakdown fixture'

$plateauSnapshots = @(0, 1, 2, 2, 1, 2) | ForEach-Object {
    $snapshotLine = $success.Replace(
        'phase=cold-start',
        'phase=peak-preparation'
    ).Replace('cacheDecoded=0', "cacheDecoded=$_")
    ConvertFrom-ReaderOwnershipLog $snapshotLine
}
Assert-NoPostWarmupOwnershipGrowth `
    -Snapshots $plateauSnapshots `
    -WarmupCount 2 `
    -Context 'eventual ownership plateau fixture'
$transientOwnershipSnapshots = @(
    @{ Callbacks = 0; Reservations = 0; Queued = 0 },
    @{ Callbacks = 1; Reservations = 1; Queued = 0 },
    @{ Callbacks = 1; Reservations = 1; Queued = 0 },
    @{ Callbacks = 1; Reservations = 0; Queued = 1 },
    @{ Callbacks = 2; Reservations = 1; Queued = 1 },
    @{ Callbacks = 2; Reservations = 1; Queued = 1 }
) | ForEach-Object {
    $relocations = $_.Reservations + $_.Queued
    $snapshotLine = $success.Replace(
        'phase=cold-start',
        'phase=peak-preparation'
    ).Replace(
        'callbacks=1',
        "callbacks=$($_.Callbacks)"
    ).Replace(
        'relocationReservations=0 queuedRelocations=0 relocations=0',
        "relocationReservations=$($_.Reservations) " +
            "queuedRelocations=$($_.Queued) relocations=$relocations"
    )
    ConvertFrom-ReaderOwnershipLog $snapshotLine
}
Assert-OwnershipWithinBounds `
    -Snapshots $transientOwnershipSnapshots `
    -Context 'transient ownership fixture'
Assert-NoPostWarmupOwnershipGrowth `
    -Snapshots $transientOwnershipSnapshots `
    -WarmupCount 2 `
    -Context 'transient ownership plateau fixture'
Assert-Throws {
    $growingSnapshots = @(0, 1, 1, 1, 2, 3) | ForEach-Object {
        $snapshotLine = $success.Replace(
            'phase=cold-start',
            'phase=peak-preparation'
        ).Replace('cacheDecoded=0', "cacheDecoded=$_")
        ConvertFrom-ReaderOwnershipLog $snapshotLine
    }
    Assert-NoPostWarmupOwnershipGrowth `
        -Snapshots $growingSnapshots `
        -WarmupCount 2 `
        -Context 'late ownership growth fixture'
} 'late ownership growth fixture'
Assert-Throws {
    $earlyPeakLateGrowthSnapshots = @(5, 4, 1, 1, 2, 3) | ForEach-Object {
        $snapshotLine = $success.Replace(
            'phase=cold-start',
            'phase=peak-preparation'
        ).Replace('cacheDecoded=0', "cacheDecoded=$_")
        ConvertFrom-ReaderOwnershipLog $snapshotLine
    }
    Assert-NoPostWarmupOwnershipGrowth `
        -Snapshots $earlyPeakLateGrowthSnapshots `
        -WarmupCount 2 `
        -Context 'early peak and late ownership growth fixture'
} 'early peak and late ownership growth fixture'

$gestureCommit =
    'reader-gesture session=7 gestureId=101 outcome=CommittedForward ' +
    'owner=Curl rasterGeneration=2 textureGeneration=9 ' +
    'physicalDirection=Left logicalDirection=Next durationMs=8'
if ((Get-CommittedTurnCount $gestureCommit 7) -ne 1) {
    throw 'One committed gesture fixture did not count exactly once'
}
$gestureRejectedPreparing =
    'reader-gesture session=7 gestureId=102 outcome=RejectedPreparing ' +
    'owner=Pending rasterGeneration=2 textureGeneration=-1 ' +
    'physicalDirection=null logicalDirection=null durationMs=0'
$parsedRejectedPreparing = @(
    ConvertFrom-ReaderGestureLog $gestureRejectedPreparing
)
if ($parsedRejectedPreparing.Count -ne 1 -or
    $parsedRejectedPreparing[0].TextureGeneration -ne -1 -or
    (Get-CommittedTurnCount $gestureRejectedPreparing 7) -ne 0) {
    throw 'Rejected preparing sentinel fixture did not parse as a terminal'
}
Assert-Throws {
    Get-CommittedTurnCount ($gestureCommit + "`n" + $gestureCommit) 7 |
        Out-Null
} 'duplicate gesture terminal fixture'
Assert-Throws {
    Get-CommittedTurnCount (
        $gestureCommit.Replace('CommittedForward', 'UnrecognizedTerminal')
    ) 7 | Out-Null
} 'unknown gesture terminal fixture'

$warmHydration =
    'reader-raster-acquisition session=7 attempt=1 rasterGeneration=2 ' +
    'ordinal=3 source=PersistentHydration trigger=WarmReopen result=Hit durationMs=4' +
    $NoQaCorrelationFields
Assert-WarmReopenUsesPersistentHydration `
    -Log $warmHydration `
    -ReaderSession 7 `
    -Context 'warm hydration fixture'
Assert-Throws {
    $recapture = $warmHydration + "`n" +
        'reader-raster-acquisition session=7 attempt=1 rasterGeneration=2 ' +
        'ordinal=3 source=WebViewCapture trigger=WarmReopen result=Started durationMs=1' +
        $NoQaCorrelationFields
    Assert-WarmReopenUsesPersistentHydration `
        -Log $recapture `
        -ReaderSession 7 `
        -Context 'warm recapture fixture'
} 'warm recapture fixture'

$deferralLog = @(
    'ContentNotReady',
    'LayoutUnstable',
    'PaginationNotReady',
    'WebViewDetached',
    'ReaderPaused'
) | ForEach-Object -Begin { $attempt = 10 } -Process {
    $deferred =
        "reader-preparation session=7 attempt=$attempt rasterGeneration=2 " +
        "state=Deferred reason=$_ eventVersion=4 durationMs=1" +
        $NoQaCorrelationFields
    $resumed =
        "reader-preparation session=7 attempt=$attempt rasterGeneration=2 " +
        "state=Resumed reason=$_ eventVersion=5 durationMs=2" +
        $NoQaCorrelationFields
    $attempt += 1
    $deferred
    $resumed
}
Assert-TypedDeferralsResumeOnNewerEvent `
    -Log ($deferralLog -join "`n") `
    -ReaderSession 7 `
    -Context 'typed deferral fixture'
Assert-Throws {
    Assert-TypedDeferralsResumeOnNewerEvent `
        -Log (($deferralLog -join "`n").Replace(
            'state=Resumed reason=LayoutUnstable eventVersion=5',
            'state=Resumed reason=LayoutUnstable eventVersion=4'
        )) `
        -ReaderSession 7 `
        -Context 'stale deferral resume fixture'
} 'stale deferral resume fixture'

$repairLog = @(
    ('reader-repair session=7 attempt=30 rasterGeneration=2 centerOrdinal=3 ' +
        'state=Started reason=None durationMs=0' + $NoQaCorrelationFields),
    ('reader-repair session=7 attempt=30 rasterGeneration=2 centerOrdinal=3 ' +
        'state=Ready reason=None durationMs=4' + $NoQaCorrelationFields),
    ('reader-repair session=7 attempt=30 rasterGeneration=2 centerOrdinal=3 ' +
        'state=Submitted reason=None durationMs=5' + $NoQaCorrelationFields),
    ('reader-repair session=7 attempt=30 rasterGeneration=2 centerOrdinal=3 ' +
        'state=Completed reason=None durationMs=6' + $NoQaCorrelationFields)
) -join "`n"
Assert-RepairAttemptReachesSubmission `
    -Log $repairLog `
    -ReaderSession 7 `
    -Context 'repair fixture'
$cancelledRepairLog = @(
    ('reader-repair session=7 attempt=29 rasterGeneration=2 centerOrdinal=2 ' +
        'state=Started reason=None durationMs=0' + $NoQaCorrelationFields),
    ('reader-repair session=7 attempt=29 rasterGeneration=2 centerOrdinal=2 ' +
        'state=Ready reason=None durationMs=3' + $NoQaCorrelationFields),
    ('reader-repair session=7 attempt=29 rasterGeneration=2 centerOrdinal=2 ' +
        'state=Cancelled reason=None durationMs=4' + $NoQaCorrelationFields)
) -join "`n"
Assert-RepairAttemptReachesSubmission `
    -Log ($cancelledRepairLog + "`n" + $repairLog) `
    -ReaderSession 7 `
    -Context 'superseded then completed repair fixture'
Assert-Throws {
    Assert-RepairAttemptReachesSubmission `
        -Log $cancelledRepairLog `
        -ReaderSession 7 `
        -Context 'cancelled-only repair fixture'
} 'cancelled-only repair fixture'

$qaFaultApplied =
    'reader-qa-fault session=7 requestId=fault-repair ' +
    'fault=ForceRepairWithoutPreparedDeck seam=repair-role state=Applied ' +
    'publicationEpoch=-1 persistenceAttemptId=-1 rasterRequestEpoch=-1 ' +
    'repairAttemptId=30 preparationAttemptId=-1 relocationToken=none ' +
    'handoffAttemptId=-1 releaseRequestId=none result=fault-applied'
$qaEvents = @(ConvertFrom-ReaderQaFaultLog $qaFaultApplied)
if ($qaEvents.Count -ne 1 -or $qaEvents[0].RepairAttemptId -ne 30) {
    throw 'Operation-bearing QA fault fixture did not parse exactly once'
}
$repairWithFault =
    'reader-repair session=7 attempt=30 rasterGeneration=2 centerOrdinal=3 ' +
    'state=Ready reason=None durationMs=4 ' +
    'qaFaultRequestId=fault-repair qaFaultRelation=AppliedOperation ' +
    'qaFaultPublicationEpoch=-1 qaFaultPersistenceAttemptId=-1 ' +
    'qaFaultRasterRequestEpoch=-1 qaFaultRepairAttemptId=30 ' +
    'qaFaultPreparationAttemptId=-1 qaFaultRelocationToken=none ' +
    'qaFaultHandoffAttemptId=-1'
$repairFaultEvents = @(ConvertFrom-ReaderRepairLog $repairWithFault)
Assert-ReaderQaFaultCorrelation `
    -FaultEvents $qaEvents `
    -DownstreamEvents $repairFaultEvents `
    -Context 'direct repair fault fixture'
$retryWithFault = $repairWithFault.Replace(
    'attempt=30',
    'attempt=31'
).Replace('qaFaultRelation=AppliedOperation', 'qaFaultRelation=Retry')
Assert-ReaderQaFaultCorrelation `
    -FaultEvents $qaEvents `
    -DownstreamEvents @(ConvertFrom-ReaderRepairLog $retryWithFault) `
    -Context 'fresh repair retry fixture'
$recoveryWithFault = $retryWithFault.Replace(
    'qaFaultRelation=Retry',
    'qaFaultRelation=Recovery'
)
Assert-ReaderQaFaultCorrelation `
    -FaultEvents $qaEvents `
    -DownstreamEvents @(ConvertFrom-ReaderRepairLog $recoveryWithFault) `
    -Context 'fresh repair recovery fixture'
Assert-Throws {
    $staleRetry = $repairWithFault.Replace(
        'qaFaultRelation=AppliedOperation',
        'qaFaultRelation=Retry'
    )
    Assert-ReaderQaFaultCorrelation `
        -FaultEvents $qaEvents `
        -DownstreamEvents @(ConvertFrom-ReaderRepairLog $staleRetry) `
        -Context 'stale retry identity fixture'
} 'stale retry identity fixture'
Assert-Throws {
    $missingRelation = $repairWithFault.Replace(
        'qaFaultRelation=AppliedOperation',
        'qaFaultRelation=None'
    )
    Assert-ReaderQaFaultCorrelation `
        -FaultEvents $qaEvents `
        -DownstreamEvents @(ConvertFrom-ReaderRepairLog $missingRelation) `
        -Context 'missing relation fixture'
} 'missing request relation fixture'
Assert-Throws {
    $mismatch = $repairWithFault.Replace(
        'qaFaultRepairAttemptId=30',
        'qaFaultRepairAttemptId=31'
    )
    Assert-ReaderQaFaultCorrelation `
        -FaultEvents $qaEvents `
        -DownstreamEvents @(ConvertFrom-ReaderRepairLog $mismatch) `
        -Context 'mismatched repair fault fixture'
} 'mismatched repair fault identity fixture'
if (@(ConvertFrom-ReaderQaFaultLog (
    $qaFaultApplied.Replace(' repairAttemptId=30', '')
)).Count -ne 0) {
    throw 'Partial QA fault schema was silently accepted'
}

$qaPersistenceApplied =
    'reader-qa-fault session=7 requestId=fault-persistence ' +
    'fault=FailNextPersistence seam=persistence state=Applied ' +
    'publicationEpoch=7 persistenceAttemptId=11 rasterRequestEpoch=-1 ' +
    'repairAttemptId=-1 preparationAttemptId=-1 relocationToken=none ' +
    'handoffAttemptId=-1 releaseRequestId=none result=fault-applied'
$qaPersistenceEvents = @(ConvertFrom-ReaderQaFaultLog $qaPersistenceApplied)
$publicationRetry =
    'reader-raster-publication session=7 digestPrefix=0123456789ab ' +
    'rasterEpoch=7 persistenceAttemptId=7 result=Durable durationMs=3 ' +
    'qaFaultRequestId=fault-persistence qaFaultRelation=Retry ' +
    'qaFaultPublicationEpoch=7 qaFaultPersistenceAttemptId=11 ' +
    'qaFaultRasterRequestEpoch=-1 qaFaultRepairAttemptId=-1 ' +
    'qaFaultPreparationAttemptId=-1 qaFaultRelocationToken=none ' +
    'qaFaultHandoffAttemptId=-1'
Assert-ReaderQaFaultCorrelation `
    -FaultEvents $qaPersistenceEvents `
    -DownstreamEvents @(ConvertFrom-ReaderPublicationLog $publicationRetry) `
    -Context 'typed persistence retry fixture'
Assert-Throws {
    $changedEpoch = $publicationRetry.Replace(
        'rasterEpoch=7 persistenceAttemptId=7',
        'rasterEpoch=8 persistenceAttemptId=7'
    )
    Assert-ReaderQaFaultCorrelation `
        -FaultEvents $qaPersistenceEvents `
        -DownstreamEvents @(ConvertFrom-ReaderPublicationLog $changedEpoch) `
        -Context 'changed publication epoch fixture'
} 'changed publication epoch fixture'
Assert-Throws {
    $reusedAttempt = $publicationRetry.Replace(
        'rasterEpoch=7 persistenceAttemptId=7',
        'rasterEpoch=7 persistenceAttemptId=11'
    )
    Assert-ReaderQaFaultCorrelation `
        -FaultEvents $qaPersistenceEvents `
        -DownstreamEvents @(ConvertFrom-ReaderPublicationLog $reusedAttempt) `
        -Context 'reused persistence attempt fixture'
} 'reused persistence attempt fixture'

$qaPreparationApplied =
    'reader-qa-fault session=7 requestId=fault-preparation ' +
    'fault=DeferContentNotReady seam=deferred-retry state=Applied ' +
    'publicationEpoch=-1 persistenceAttemptId=-1 rasterRequestEpoch=-1 ' +
    'repairAttemptId=-1 preparationAttemptId=40 relocationToken=none ' +
    'handoffAttemptId=-1 releaseRequestId=none result=fault-applied'
$preparationRootFields =
    ' qaFaultRequestId=fault-preparation ' +
    'qaFaultRelation=AppliedOperation qaFaultPublicationEpoch=-1 ' +
    'qaFaultPersistenceAttemptId=-1 qaFaultRasterRequestEpoch=-1 ' +
    'qaFaultRepairAttemptId=-1 qaFaultPreparationAttemptId=40 ' +
    'qaFaultRelocationToken=none qaFaultHandoffAttemptId=-1'
$preparationResumed =
    'reader-preparation session=7 attempt=40 rasterGeneration=2 ' +
    'state=Resumed reason=ContentNotReady eventVersion=5 durationMs=2' +
    $preparationRootFields
$preparationRetry =
    'reader-preparation session=7 attempt=41 rasterGeneration=2 ' +
    'state=Failed reason=ContentNotReady eventVersion=-1 durationMs=3' +
    $preparationRootFields.Replace(
        'qaFaultRelation=AppliedOperation',
        'qaFaultRelation=Retry'
    )
Assert-ReaderQaFaultCorrelation `
    -FaultEvents @(ConvertFrom-ReaderQaFaultLog $qaPreparationApplied) `
    -DownstreamEvents @(
        @(ConvertFrom-ReaderPreparationLog $preparationResumed) +
        @(ConvertFrom-ReaderPreparationLog $preparationRetry)
    ) `
    -Context 'preparation resume and fresh retry fixture'

$qaRasterMissApplied =
    'reader-qa-fault session=7 requestId=fault-raster ' +
    'fault=MissNextRasterLoad seam=raster-resolver state=Applied ' +
    'publicationEpoch=-1 persistenceAttemptId=-1 rasterRequestEpoch=31 ' +
    'repairAttemptId=-1 preparationAttemptId=-1 relocationToken=none ' +
    'handoffAttemptId=-1 releaseRequestId=none result=fault-applied'
$rasterRecoveryRepair =
    'reader-repair session=7 attempt=31 rasterGeneration=2 centerOrdinal=3 ' +
    'state=Started reason=None durationMs=0 ' +
    'qaFaultRequestId=fault-raster qaFaultRelation=Recovery ' +
    'qaFaultPublicationEpoch=-1 qaFaultPersistenceAttemptId=-1 ' +
    'qaFaultRasterRequestEpoch=31 qaFaultRepairAttemptId=-1 ' +
    'qaFaultPreparationAttemptId=-1 qaFaultRelocationToken=none ' +
    'qaFaultHandoffAttemptId=-1'
Assert-ReaderQaFaultCorrelation `
    -FaultEvents @(ConvertFrom-ReaderQaFaultLog $qaRasterMissApplied) `
    -DownstreamEvents @(ConvertFrom-ReaderRepairLog $rasterRecoveryRepair) `
    -Context 'cross-namespace raster recovery fixture'

$qaRelocationApplied =
    'reader-qa-fault session=7 requestId=fault-relocation ' +
    'fault=DelayNextRelocationAcknowledgement seam=relocation-ack state=Applied ' +
    'publicationEpoch=-1 persistenceAttemptId=-1 rasterRequestEpoch=-1 ' +
    'repairAttemptId=-1 preparationAttemptId=-1 relocationToken=move-1 ' +
    'handoffAttemptId=-1 releaseRequestId=none result=fault-applied'
$relocationWithFault =
    'reader-relocation session=7 token=move-1 gestureId=3 source=1 target=2 ' +
    'logicalDirection=Next rasterGeneration=2 textureGeneration=3 ' +
    'state=Acknowledged rejectionReason=None queueDepth=0 durationMs=2 ' +
    'qaFaultRequestId=fault-relocation qaFaultRelation=AppliedOperation ' +
    'qaFaultPublicationEpoch=-1 qaFaultPersistenceAttemptId=-1 ' +
    'qaFaultRasterRequestEpoch=-1 qaFaultRepairAttemptId=-1 ' +
    'qaFaultPreparationAttemptId=-1 qaFaultRelocationToken=move-1 ' +
    'qaFaultHandoffAttemptId=-1'
Assert-ReaderQaFaultCorrelation `
    -FaultEvents @(ConvertFrom-ReaderQaFaultLog $qaRelocationApplied) `
    -DownstreamEvents @(ConvertFrom-ReaderRelocationLog $relocationWithFault) `
    -Context 'typed relocation schema fixture'
$relocationRecoveryRootFields =
    ' qaFaultRequestId=fault-relocation qaFaultRelation=Recovery ' +
    'qaFaultPublicationEpoch=-1 qaFaultPersistenceAttemptId=-1 ' +
    'qaFaultRasterRequestEpoch=-1 qaFaultRepairAttemptId=-1 ' +
    'qaFaultPreparationAttemptId=-1 qaFaultRelocationToken=move-1 ' +
    'qaFaultHandoffAttemptId=-1'
$relocationHandoffRecovery =
    'reader-handoff session=7 token=move-1 handoffAttemptId=4 target=2 ' +
    'visualState=true nextFrame=true result=Ready durationMs=3' +
    $relocationRecoveryRootFields
$relocationCompletedRecovery =
    'reader-relocation session=7 token=move-1 gestureId=3 source=1 target=2 ' +
    'logicalDirection=Next rasterGeneration=2 textureGeneration=3 ' +
    'state=Completed rejectionReason=None queueDepth=0 durationMs=4' +
    $relocationRecoveryRootFields
Assert-ReaderQaFaultCorrelation `
    -FaultEvents @(ConvertFrom-ReaderQaFaultLog $qaRelocationApplied) `
    -DownstreamEvents @(
        @(ConvertFrom-ReaderHandoffLog $relocationHandoffRecovery) +
        @(ConvertFrom-ReaderRelocationLog $relocationCompletedRecovery)
    ) `
    -Context 'delayed relocation acknowledgement recovery fixture'

$qaVisualApplied =
    'reader-qa-fault session=7 requestId=fault-visual ' +
    'fault=DelayNextVisualStateCallback seam=visual-state state=Applied ' +
    'publicationEpoch=-1 persistenceAttemptId=-1 rasterRequestEpoch=-1 ' +
    'repairAttemptId=-1 preparationAttemptId=-1 relocationToken=move-2 ' +
    'handoffAttemptId=3 releaseRequestId=none result=fault-applied'
$visualRootFields =
    ' qaFaultRequestId=fault-visual qaFaultRelation=Recovery ' +
    'qaFaultPublicationEpoch=-1 qaFaultPersistenceAttemptId=-1 ' +
    'qaFaultRasterRequestEpoch=-1 qaFaultRepairAttemptId=-1 ' +
    'qaFaultPreparationAttemptId=-1 qaFaultRelocationToken=move-2 ' +
    'qaFaultHandoffAttemptId=3'
$visualHandoffRecovery =
    'reader-handoff session=7 token=move-2 handoffAttemptId=4 target=3 ' +
    'visualState=true nextFrame=true result=Ready durationMs=3' +
    $visualRootFields
$visualRelocationRecovery =
    'reader-relocation session=7 token=move-2 gestureId=4 source=2 target=3 ' +
    'logicalDirection=Next rasterGeneration=2 textureGeneration=4 ' +
    'state=Completed rejectionReason=None queueDepth=0 durationMs=4' +
    $visualRootFields
Assert-ReaderQaFaultCorrelation `
    -FaultEvents @(ConvertFrom-ReaderQaFaultLog $qaVisualApplied) `
    -DownstreamEvents @(
        @(ConvertFrom-ReaderHandoffLog $visualHandoffRecovery) +
        @(ConvertFrom-ReaderRelocationLog $visualRelocationRecovery)
    ) `
    -Context 'visual handoff and relocation recovery fixture'
$visualRelocationApplied =
    'reader-relocation session=7 token=move-2 gestureId=3 source=2 target=3 ' +
    'logicalDirection=Next rasterGeneration=2 textureGeneration=3 ' +
    'state=Completed rejectionReason=None queueDepth=0 durationMs=4' +
    $visualRootFields.Replace('qaFaultRelation=Recovery',
        'qaFaultRelation=AppliedOperation')
Assert-ReaderQaFaultCorrelation `
    -FaultEvents @(ConvertFrom-ReaderQaFaultLog $qaVisualApplied) `
    -DownstreamEvents @(ConvertFrom-ReaderRelocationLog $visualRelocationApplied) `
    -Context 'visual direct relocation aggregate fixture'

$residencySuccess =
    'reader-residency session=7 residents=3 residentLimit=6 ' +
    'decodedUnique=2 decodedUniqueLimit=4 peakResidents=5 ' +
    'peakDecodedUnique=3 pinned=2 pendingReleases=1 evicted=8 released=9'
Assert-ReaderResidencyWithinBounds `
    @(ConvertFrom-ReaderResidencyLog $residencySuccess) `
    'valid residency fixture'
Assert-Throws {
    Assert-ReaderResidencyWithinBounds `
        @(ConvertFrom-ReaderResidencyLog (
            $residencySuccess.Replace('peakResidents=5', 'peakResidents=7')
        )) `
        'over-limit residency fixture'
} 'residency bound fixture'
Assert-ReaderRuntimeLogSafe 'reader normal=true' 'safe runtime fixture'
Assert-Throws {
    Assert-ReaderRuntimeLogSafe `
        'E/Reader: trying to use a recycled bitmap' `
        'unsafe runtime fixture'
} 'runtime marker fixture'

$cacheSuccess =
    'reader-raster-cache session=7 phase=steady-state diskEntries=4 ' +
    'diskBytes=4096 diskByteLimit=8192 decodedEntries=2 decodedUnique=2 ' +
    'decodedUniqueLimit=3 pendingDecodedReleases=1 activeEncodePins=2 ' +
    'encodePinnedIdentities=1'
$parsedCache = @(ConvertFrom-ReaderRasterCacheLog $cacheSuccess)
if ($parsedCache.Count -ne 1) { throw 'Valid raster-cache fixture did not parse' }
Assert-RasterCacheWithinByteLimit $parsedCache 'valid raster-cache fixture'
Assert-Throws {
    $overLimit = $cacheSuccess.Replace('diskBytes=4096', 'diskBytes=8193')
    Assert-RasterCacheWithinByteLimit `
        @(ConvertFrom-ReaderRasterCacheLog $overLimit) `
        'over-limit raster-cache fixture'
} 'disk cache byte-limit fixture'
Assert-Throws {
    $impossiblePins = $cacheSuccess.Replace(
        'activeEncodePins=2 encodePinnedIdentities=1',
        'activeEncodePins=1 encodePinnedIdentities=2'
    )
    Assert-RasterCacheWithinByteLimit `
        @(ConvertFrom-ReaderRasterCacheLog $impossiblePins) `
        'impossible encode-pin fixture'
} 'encode-pin identity fixture'
Assert-Throws {
    $afterClosePins = $cacheSuccess.Replace(
        'phase=steady-state',
        'phase=after-close'
    )
    Assert-RasterCacheWithinByteLimit `
        @(ConvertFrom-ReaderRasterCacheLog $afterClosePins) `
        'after-close encode-pin fixture'
} 'after-close encode-pin fixture'
$afterCloseCache =
    'reader-raster-cache session=7 phase=after-close diskEntries=4 ' +
    'diskBytes=4096 diskByteLimit=8192 decodedEntries=0 decodedUnique=0 ' +
    'decodedUniqueLimit=3 pendingDecodedReleases=0 activeEncodePins=0 ' +
    'encodePinnedIdentities=0'
Assert-RasterCacheWithinByteLimit `
    @(ConvertFrom-ReaderRasterCacheLog $afterCloseCache) `
    'zero after-close encode-pin fixture'

$coldUnavailable =
    'reader-ownership-unavailable session=7 phase=cold-start status=SURFACE_UNAVAILABLE'
Assert-ReaderOwnershipUnavailablePolicy `
    -Log ($coldUnavailable + "`n" + $success) `
    -ReaderSession 7 `
    -Context 'recovering cold fixture'
$applicationDrift =
    'reader-ownership-unavailable session=7 phase=steady-state ' +
    'status=APPLICATION_EPOCH_CHANGED'
$steadySuccess = $success.Replace(
    'phase=cold-start',
    'phase=steady-state'
)
Assert-ReaderOwnershipUnavailablePolicy `
    -Log ($applicationDrift + "`n" + $steadySuccess) `
    -ReaderSession 7 `
    -Context 'recovering application-epoch fixture'
Assert-ReaderOwnershipUnavailablePolicy `
    -Log $applicationDrift `
    -ReaderSession 7 `
    -Context 'pending application-epoch fixture' `
    -AllowPendingRecovery

Assert-Throws {
    Assert-ReaderOwnershipUnavailablePolicy `
        -Log 'reader-ownership-unavailable session=7 phase=steady-state status=CALLBACK_CAPACITY' `
        -ReaderSession 7 `
        -Context 'callback fixture' `
        -AllowPendingRecovery
} 'CALLBACK_CAPACITY fixture'
Assert-Throws {
    Assert-ReaderOwnershipUnavailablePolicy `
        -Log 'reader-ownership-unavailable session=7 phase=peak-preparation status=QUEUE_REJECTED' `
        -ReaderSession 7 `
        -Context 'queue fixture'
} 'QUEUE_REJECTED fixture'
Assert-Throws {
    Assert-ReaderOwnershipUnavailablePolicy `
        -Log 'reader-ownership-unavailable session=7 phase=after-close status=SURFACE_UNAVAILABLE' `
        -ReaderSession 7 `
        -Context 'after-close fixture'
} 'after-close unavailable fixture'
Assert-Throws {
    Assert-ReaderOwnershipUnavailablePolicy `
        -Log 'reader-ownership-unavailable session=7 phase=after-close status=APPLICATION_EPOCH_CHANGED' `
        -ReaderSession 7 `
        -Context 'after-close application-epoch fixture'
} 'after-close application-epoch fixture'

Assert-ReaderOwnershipUnavailablePolicy `
    -Log 'reader-ownership-unavailable session=8 phase=steady-state status=CALLBACK_CAPACITY' `
    -ReaderSession 7 `
    -Context 'mixed-session fixture'
if (@(ConvertFrom-ReaderOwnershipLog 'reader-ownership malformed').Count -ne 0) {
    throw 'Malformed ownership fixture was silently accepted'
}
Assert-ReaderDiagnosticRecordSet `
    -Records @($repairWithFault, $qaFaultApplied) `
    -Context 'closed diagnostic schema fixture'
Assert-Throws {
    Assert-ReaderDiagnosticRecordSet `
        -Records @($repairWithFault + ' unknownField=1') `
        -Context 'unknown diagnostic field fixture'
} 'unknown diagnostic field fixture'
Assert-Throws {
    Assert-ReaderDiagnosticRecordSet `
        -Records @($repairWithFault.Replace(
            'qaFaultRelocationToken=none',
            'qaFaultRelocationToken=href=secret'
        )) `
        -Context 'forbidden diagnostic value fixture'
} 'forbidden diagnostic value fixture'

$extracted = @(ConvertTo-ReaderDiagnosticRecordSet `
    -Log (
        "prefix-myreader-repair session=7 attempt=30 rasterGeneration=2 " +
        "centerOrdinal=3 state=Completed reason=None durationMs=6" +
        $NoQaCorrelationFields + "`n" +
        "I/Navic: $repairWithFault" + "`n" +
        'I/Navic: reader-unsupported session=7 value=1'
    ) `
    -Context 'exact diagnostic introducer fixture')
if ($extracted.Count -ne 1 -or $extracted[0] -cne $repairWithFault) {
    throw 'Diagnostic extraction admitted a substring or unsupported introducer'
}
Assert-Throws {
    ConvertTo-ReaderDiagnosticRecordSet `
        -Log 'reader-repair malformed=true' `
        -Context 'malformed supported introducer fixture' | Out-Null
} 'malformed supported introducer fixture'

$relocationRecoveryLog = @(
    'reader-relocation session=7 token=move-1 gestureId=3 source=2 target=1 ' +
        'logicalDirection=Previous rasterGeneration=2 textureGeneration=3 ' +
        'state=Queued rejectionReason=None queueDepth=1 durationMs=0' + $NoQaCorrelationFields,
    'reader-relocation session=7 token=move-1 gestureId=3 source=2 target=1 ' +
        'logicalDirection=Previous rasterGeneration=2 textureGeneration=3 ' +
        'state=Dispatched rejectionReason=None queueDepth=1 durationMs=1' + $NoQaCorrelationFields,
    'reader-relocation session=7 token=move-1 gestureId=3 source=2 target=1 ' +
        'logicalDirection=Previous rasterGeneration=2 textureGeneration=3 ' +
        'state=Rejected rejectionReason=AcknowledgementTimeout queueDepth=0 durationMs=10001' + $NoQaCorrelationFields,
    'reader-preparation session=7 attempt=40 rasterGeneration=2 ' +
        'state=Attempted reason=None eventVersion=-1 durationMs=0' +
        $NoQaCorrelationFields,
    'reader-preparation session=7 attempt=40 rasterGeneration=2 ' +
        'state=Ready reason=None eventVersion=-1 durationMs=200' +
        $NoQaCorrelationFields,
    'reader-relocation session=7 token=move-2 gestureId=4 source=1 target=2 ' +
        'logicalDirection=Next rasterGeneration=2 textureGeneration=4 ' +
        'state=Dispatched rejectionReason=None queueDepth=1 durationMs=1' + $NoQaCorrelationFields,
    'reader-relocation session=7 token=move-2 gestureId=4 source=1 target=2 ' +
        'logicalDirection=Next rasterGeneration=2 textureGeneration=4 ' +
        'state=Completed rejectionReason=None queueDepth=0 durationMs=400' + $NoQaCorrelationFields
) -join "`n"
$relocationDrain = Get-ReaderCommittedRelocationDrainStatus `
    -Log $relocationRecoveryLog `
    -ReaderSession 7 `
    -CommittedGestureIds @(3, 4) `
    -Context 'acknowledgement timeout recovery fixture'
if ($relocationDrain.TerminalRelocations.Count -ne 2 -or
    $relocationDrain.CompletedRelocations.Count -ne 1 -or
    $relocationDrain.RejectedRelocations.Count -ne 1 -or
    $relocationDrain.RecoveredRejectedRelocations.Count -ne 1 -or
    $relocationDrain.PendingCount -ne 0) {
    throw 'Acknowledgement timeout recovery fixture did not drain exactly'
}
$pendingRecovery = Get-ReaderCommittedRelocationDrainStatus `
    -Log $relocationRecoveryLog.Replace(
        'state=Ready reason=None eventVersion=-1 durationMs=200',
        'state=Attempted reason=None eventVersion=-1 durationMs=200'
    ) `
    -ReaderSession 7 `
    -CommittedGestureIds @(3, 4) `
    -Context 'pending timeout recovery fixture'
if ($pendingRecovery.RecoveredRejectedRelocations.Count -ne 0) {
    throw 'Pending acknowledgement timeout recovery was accepted as ready'
}
$staleDispatch =
    'reader-relocation session=7 token=move-1 gestureId=3 source=2 target=1 ' +
    'logicalDirection=Previous rasterGeneration=2 textureGeneration=3 ' +
    'state=Dispatched rejectionReason=None queueDepth=1 durationMs=10002' +
    $NoQaCorrelationFields
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log ($relocationRecoveryLog + "`n" + $staleDispatch) `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context 'stale post-terminal callback fixture' | Out-Null
} 'stale post-terminal callback fixture'
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $relocationRecoveryLog.Replace('durationMs=10001', 'durationMs=100') `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context 'premature rejection fixture' | Out-Null
} 'premature rejection fixture'
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $relocationRecoveryLog.Replace(
            'rejectionReason=AcknowledgementTimeout',
            'rejectionReason=QueueInvalidated'
        ) `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context 'non-timeout rejection fixture' | Out-Null
} 'non-timeout rejection fixture'
$rejectedFixtureLine = @(
    ConvertFrom-ReaderRelocationLog $relocationRecoveryLog | Where-Object {
        $_.GestureId -eq 3 -and $_.State -eq 'Rejected'
    }
)[0].LogLine
$acknowledgedFixtureLine = $rejectedFixtureLine.Replace(
    'state=Rejected rejectionReason=AcknowledgementTimeout queueDepth=0 durationMs=10001',
    'state=Acknowledged rejectionReason=None queueDepth=1 durationMs=500'
)
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $relocationRecoveryLog.Replace(
            $rejectedFixtureLine,
            $acknowledgedFixtureLine + "`n" + $rejectedFixtureLine
        ) `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context 'post-acknowledgement rejection fixture' | Out-Null
} 'post-acknowledgement rejection fixture'
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $relocationRecoveryLog.Replace('state=Dispatched', 'state=Queued') `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context 'rejected without dispatch fixture' | Out-Null
} 'rejected without dispatch fixture'

$candidateTreeSources = @(
    (Join-Path $PSScriptRoot 'adb-reader-playlikecurl-qa.ps1')
)
foreach ($candidateTreeSource in $candidateTreeSources) {
    if (-not (Test-Path -LiteralPath $candidateTreeSource -PathType Leaf)) {
        throw "Candidate-tree contract is absent: $candidateTreeSource"
    }
    $candidateTreeText = Get-Content -LiteralPath $candidateTreeSource -Raw
    if ($candidateTreeText -notmatch
        'git diff HEAD --name-status --no-renames') {
        throw "Candidate-tree contract omits staged-only bytes: $candidateTreeSource"
    }
}

Write-Output 'Reader PlayLikeCurl QA parser PASS'
