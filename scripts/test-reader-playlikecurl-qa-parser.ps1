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
$expectedOwnershipPlateauFields = @('CacheDecoded')
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
$dynamicWorkingSetSnapshots = @(1, 1, 1, 1, 2, 2) | ForEach-Object {
    $snapshotLine = $success.Replace(
        'phase=cold-start',
        'phase=peak-preparation'
    ).Replace('residents=1', "residents=$_").Replace(
        'adapterDecoded=1',
        "adapterDecoded=$_"
    )
    ConvertFrom-ReaderOwnershipLog $snapshotLine
}
Assert-NoPostWarmupOwnershipGrowth `
    -Snapshots $dynamicWorkingSetSnapshots `
    -WarmupCount 2 `
    -Context 'dynamic pinned working-set plateau fixture'
$transientOwnershipSnapshots = @(
    @{ Callbacks = 0; Reservations = 0; Queued = 0; Staged = 0; Textures = 2 },
    @{ Callbacks = 1; Reservations = 1; Queued = 0; Staged = 0; Textures = 2 },
    @{ Callbacks = 1; Reservations = 1; Queued = 0; Staged = 0; Textures = 2 },
    @{ Callbacks = 1; Reservations = 0; Queued = 1; Staged = 0; Textures = 2 },
    @{ Callbacks = 2; Reservations = 1; Queued = 1; Staged = 1; Textures = 4 },
    @{ Callbacks = 2; Reservations = 1; Queued = 1; Staged = 1; Textures = 4 }
) | ForEach-Object {
    $relocations = $_.Reservations + $_.Queued
    $snapshotLine = $success.Replace(
        'phase=cold-start',
        'phase=peak-preparation'
    ).Replace(
        'callbacks=1',
        "callbacks=$($_.Callbacks)"
    ).Replace(
        'staged=0',
        "staged=$($_.Staged)"
    ).Replace(
        'textures=2',
        "textures=$($_.Textures)"
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

$qaInputLog = @(
    'reader-qa-input requestId=input-1 state=Armed accepted=true',
    'reader-qa-input requestId=input-1 state=Admitted accepted=true session=7 gestureId=101',
    'reader-qa-input requestId=input-2 state=Cleared accepted=true'
) -join "`n"
$qaInputs = @(ConvertFrom-ReaderQaInputLog $qaInputLog)
if ($qaInputs.Count -ne 3 -or
    $qaInputs[1].Session -ne 7 -or
    $qaInputs[1].GestureId -ne 101 -or
    $qaInputs[0].GestureId -ne -1) {
    throw 'QA input admission fixture did not preserve exact correlation'
}

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

@(
    @{ State = 'Ready'; PreparationIndex = 20; Expected = 'ReadyAfterTerminal' },
    @{ State = 'Ready'; PreparationIndex = 19; Expected = 'QuiesceReadyBeforeTerminal' },
    @{ State = 'Attempted'; PreparationIndex = 19; Expected = 'AwaitCurrentAttempt' },
    @{ State = 'Deferred'; PreparationIndex = 19; Expected = 'AwaitCurrentAttempt' },
    @{ State = 'Resumed'; PreparationIndex = 19; Expected = 'AwaitCurrentAttempt' },
    @{ State = 'Failed'; PreparationIndex = 19; Expected = 'AwaitNextAttempt' },
    @{ State = 'Cancelled'; PreparationIndex = 19; Expected = 'AwaitNextAttempt' }
) | ForEach-Object {
    $actual = Get-ReaderPreparationRecoveryAction `
        -State $_.State `
        -PreparationIndex $_.PreparationIndex `
        -TerminalIndex 19
    if ($actual -ne $_.Expected) {
        throw "Preparation recovery action for $($_.State) was $actual"
    }
}
Assert-Throws {
    Get-ReaderPreparationRecoveryAction `
        -State 'Ready' `
        -PreparationIndex -1 `
        -TerminalIndex 19 | Out-Null
} 'negative preparation recovery index fixture'

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

$forcedRepairFault =
    'reader-qa-fault session=7 requestId=fault-repair ' +
    'fault=ForceRepairWithoutPreparedDeck seam=repair-role state=Applied ' +
    'publicationEpoch=-1 persistenceAttemptId=-1 rasterRequestEpoch=-1 ' +
    'repairAttemptId=30 preparationAttemptId=-1 relocationToken=none ' +
    'handoffAttemptId=-1 releaseRequestId=none result=fault-applied'
$forcedRepairStarted =
    'reader-repair session=7 attempt=30 rasterGeneration=2 centerOrdinal=3 ' +
    'state=Started reason=None durationMs=0 ' +
    'qaFaultRequestId=fault-raster qaFaultRelation=Recovery ' +
    'qaFaultPublicationEpoch=-1 qaFaultPersistenceAttemptId=-1 ' +
    'qaFaultRasterRequestEpoch=31 qaFaultRepairAttemptId=-1 ' +
    'qaFaultPreparationAttemptId=-1 qaFaultRelocationToken=none ' +
    'qaFaultHandoffAttemptId=-1'
$forcedRepairReady = $forcedRepairStarted.Replace(
    'state=Started reason=None durationMs=0',
    'state=Ready reason=None durationMs=4'
)
$forcedRepairCancelled =
    'reader-repair session=7 attempt=30 rasterGeneration=2 centerOrdinal=3 ' +
    'state=Cancelled reason=None durationMs=9 ' +
    'qaFaultRequestId=fault-repair qaFaultRelation=AppliedOperation ' +
    'qaFaultPublicationEpoch=-1 qaFaultPersistenceAttemptId=-1 ' +
    'qaFaultRasterRequestEpoch=-1 qaFaultRepairAttemptId=30 ' +
    'qaFaultPreparationAttemptId=-1 qaFaultRelocationToken=none ' +
    'qaFaultHandoffAttemptId=-1'
$forcedRepairQueued =
    'reader-relocation session=7 token=move-repair gestureId=101 source=3 target=4 ' +
    'logicalDirection=Next rasterGeneration=2 textureGeneration=9 ' +
    'state=Queued rejectionReason=None queueDepth=1 durationMs=0' +
    $NoQaCorrelationFields
$forcedRepairDispatched = $forcedRepairQueued.Replace(
    'state=Queued rejectionReason=None queueDepth=1 durationMs=0',
    'state=Dispatched rejectionReason=None queueDepth=1 durationMs=5'
)
$forcedRepairAcknowledged = $forcedRepairQueued.Replace(
    'state=Queued rejectionReason=None queueDepth=1 durationMs=0',
    'state=Acknowledged rejectionReason=None queueDepth=1 durationMs=10'
)
$forcedRepairAwaitingHandoff = $forcedRepairQueued.Replace(
    'state=Queued rejectionReason=None queueDepth=1 durationMs=0',
    'state=AwaitingVisualHandoff rejectionReason=None queueDepth=1 durationMs=11'
)
$forcedRepairRelocationCompleted = $forcedRepairQueued.Replace(
    'state=Queued rejectionReason=None queueDepth=1 durationMs=0',
    'state=Completed rejectionReason=None queueDepth=0 durationMs=12'
)
$promotedNormalDeck =
    'reader-deck session=7 generation=9 repairAttempt=-1 role=Active ' +
    'prepared=true active=9 pending=null durationMs=8' +
    $NoQaCorrelationFields
$drainedRepairOwnership = $success.Replace(
    'phase=cold-start',
    'phase=steady-state'
).Replace(
    'callbacks=1',
    'callbacks=0'
)
$forcedRepairTurnTail = @(
    $forcedRepairQueued,
    $gestureCommit,
    $forcedRepairDispatched,
    $promotedNormalDeck,
    $forcedRepairAcknowledged,
    $forcedRepairAwaitingHandoff,
    $forcedRepairRelocationCompleted,
    $drainedRepairOwnership
)
$safelySupersededRepairLog = @(
    $forcedRepairStarted,
    $forcedRepairReady,
    $forcedRepairFault,
    $forcedRepairTurnTail[0],
    $forcedRepairTurnTail[1],
    $forcedRepairTurnTail[2],
    $forcedRepairCancelled,
    $forcedRepairTurnTail[3],
    $forcedRepairTurnTail[4],
    $forcedRepairTurnTail[5],
    $forcedRepairTurnTail[6],
    $forcedRepairTurnTail[7]
) -join "`n"
$preparedAfterInitialDispatch = Assert-ForcedRepairAttemptResolution `
    -Log $safelySupersededRepairLog `
    -ReaderSession 7 `
    -RepairFaultRequestId 'fault-repair' `
    -RasterMissRequestId 'fault-raster' `
    -GestureId 101 `
    -TextureGeneration 9 `
    -Context 'initial preparation after dispatch fixture'
if ($preparedAfterInitialDispatch.Kind -ne 'Superseded') {
    throw 'Initial preparation after dispatch did not prove safe repair supersession'
}
$promotedTexture = @(Get-ReaderPreparedPromotedTexture `
    -Log $safelySupersededRepairLog `
    -ReaderSession 7 `
    -GestureId 101 `
    -TextureGeneration 9 `
    -Context 'already-promoted texture fixture')
if ($promotedTexture.Count -ne 1) {
    throw 'Already-promoted texture preparation was not recognized'
}
foreach ($invalidActiveDeckState in @(
        'prepared=true active=8 pending=9',
        'prepared=true active=9 pending=10'
    )) {
    $invalidActiveDeck = @(Get-ReaderPreparedPromotedTexture `
        -Log $safelySupersededRepairLog.Replace(
            'prepared=true active=9 pending=null',
            $invalidActiveDeckState
        ) `
        -ReaderSession 7 `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context "invalid active deck state $invalidActiveDeckState fixture")
    if ($invalidActiveDeck.Count -ne 0) {
        throw "Invalid active texture deck state was accepted: $invalidActiveDeckState"
    }
}
$preparedWhilePendingDeck = $promotedNormalDeck.Replace(
    'role=Active prepared=true active=9 pending=null',
    'role=Pending prepared=true active=8 pending=9'
)
$preparedWhilePendingLog = $safelySupersededRepairLog.Replace(
    $promotedNormalDeck,
    $preparedWhilePendingDeck
)
$preparedWhilePending = @(Get-ReaderPreparedPromotedTexture `
    -Log $preparedWhilePendingLog `
    -ReaderSession 7 `
    -GestureId 101 `
    -TextureGeneration 9 `
    -Context 'prepared-while-pending texture fixture')
if ($preparedWhilePending.Count -ne 1) {
    throw 'Texture prepared before promotion was not recognized after relocation completion'
}
foreach ($invalidActiveGeneration in @('null', '10')) {
    $invalidPreparedPending = @(Get-ReaderPreparedPromotedTexture `
        -Log $preparedWhilePendingLog.Replace(
            'prepared=true active=8 pending=9',
            "prepared=true active=$invalidActiveGeneration pending=9"
        ) `
        -ReaderSession 7 `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context "invalid pending active generation $invalidActiveGeneration fixture")
    if ($invalidPreparedPending.Count -ne 0) {
        throw "Invalid pending active generation $invalidActiveGeneration proved promotion"
    }
}
$replacementDispatched = $forcedRepairDispatched.Replace(
    'token=move-repair',
    'token=move-repair-replacement'
).Replace('textureGeneration=9', 'textureGeneration=10')
$replacementAcknowledged = $forcedRepairAcknowledged.Replace(
    'token=move-repair',
    'token=move-repair-replacement'
).Replace('textureGeneration=9', 'textureGeneration=10')
$replacementAwaitingHandoff = $forcedRepairAwaitingHandoff.Replace(
    'token=move-repair',
    'token=move-repair-replacement'
).Replace('textureGeneration=9', 'textureGeneration=10')
$replacementRelocationCompleted = $forcedRepairRelocationCompleted.Replace(
    'token=move-repair',
    'token=move-repair-replacement'
).Replace('textureGeneration=9', 'textureGeneration=10')
$replacementNormalDeck = $promotedNormalDeck.Replace(
    'generation=9',
    'generation=10'
).Replace('active=9', 'active=10')
$replacementSupersededRepairLog = @(
    $forcedRepairStarted,
    $forcedRepairReady,
    $forcedRepairFault,
    $forcedRepairQueued,
    $gestureCommit,
    $preparedWhilePendingDeck,
    $forcedRepairDispatched,
    $forcedRepairCancelled,
    $forcedRepairAcknowledged,
    $forcedRepairAwaitingHandoff,
    $replacementNormalDeck,
    $replacementDispatched,
    $replacementAcknowledged,
    $replacementAwaitingHandoff,
    $replacementRelocationCompleted,
    $drainedRepairOwnership
) -join "`n"
$replacementSupersession = Assert-ForcedRepairAttemptResolution `
    -Log $replacementSupersededRepairLog `
    -ReaderSession 7 `
    -RepairFaultRequestId 'fault-repair' `
    -RasterMissRequestId 'fault-raster' `
    -GestureId 101 `
    -TextureGeneration 9 `
    -Context 'replacement superseded repair fixture'
if ($replacementSupersession.Kind -ne 'Superseded' -or
    $replacementSupersession.TextureGeneration -ne 10) {
    throw 'Replacement relocation did not prove safe repair supersession'
}
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log $replacementSupersededRepairLog.Replace(
            $replacementNormalDeck + "`n" + $replacementDispatched,
            $replacementDispatched + "`n" + $replacementNormalDeck
        ) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'late replacement preparation fixture' | Out-Null
} 'late replacement preparation fixture'
$secondReplacementDispatched = $replacementDispatched.Replace(
    'token=move-repair-replacement',
    'token=move-repair-replacement-2'
).Replace('textureGeneration=10', 'textureGeneration=11')
$secondReplacementAcknowledged = $replacementAcknowledged.Replace(
    'token=move-repair-replacement',
    'token=move-repair-replacement-2'
).Replace('textureGeneration=10', 'textureGeneration=11')
$secondReplacementAwaitingHandoff = $replacementAwaitingHandoff.Replace(
    'token=move-repair-replacement',
    'token=move-repair-replacement-2'
).Replace('textureGeneration=10', 'textureGeneration=11')
$secondReplacementCompleted = $replacementRelocationCompleted.Replace(
    'token=move-repair-replacement',
    'token=move-repair-replacement-2'
).Replace('textureGeneration=10', 'textureGeneration=11')
$secondReplacementNormalDeck = $replacementNormalDeck.Replace(
    'generation=10',
    'generation=11'
).Replace('active=10', 'active=11')
$twiceReplacedRepairLog = $replacementSupersededRepairLog.Replace(
    $replacementRelocationCompleted,
    @(
        $secondReplacementNormalDeck,
        $secondReplacementDispatched,
        $secondReplacementAcknowledged,
        $secondReplacementAwaitingHandoff,
        $secondReplacementCompleted
    ) -join "`n"
)
$twiceReplacedSupersession = Assert-ForcedRepairAttemptResolution `
    -Log $twiceReplacedRepairLog `
    -ReaderSession 7 `
    -RepairFaultRequestId 'fault-repair' `
    -RasterMissRequestId 'fault-raster' `
    -GestureId 101 `
    -TextureGeneration 9 `
    -Context 'twice-replaced repair fixture'
if ($twiceReplacedSupersession.Kind -ne 'Superseded' -or
    $twiceReplacedSupersession.TextureGeneration -ne 11) {
    throw 'Two replacement relocations did not prove safe repair supersession'
}
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log $twiceReplacedRepairLog.Replace(
            $replacementNormalDeck + "`n",
            ''
        ) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'missing intermediate preparation fixture' | Out-Null
} 'missing intermediate preparation fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log $replacementSupersededRepairLog.Replace(
            'token=move-repair-replacement gestureId=101 source=3 target=4',
            'token=move-repair-replacement gestureId=101 source=3 target=5'
        ) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'changed replacement destination fixture' | Out-Null
} 'changed replacement destination fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log $replacementSupersededRepairLog.Replace(
            'textureGeneration=10',
            'textureGeneration=8'
        ) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'regressed replacement generation fixture' | Out-Null
} 'regressed replacement generation fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log $replacementSupersededRepairLog.Replace(
            $replacementNormalDeck + "`n",
            ''
        ) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'unprepared replacement generation fixture' | Out-Null
} 'unprepared replacement generation fixture'
$preparedBeforeForcedRepairLog = @(
    $forcedRepairStarted,
    $forcedRepairReady,
    $preparedWhilePendingDeck,
    $forcedRepairFault,
    $forcedRepairQueued,
    $gestureCommit,
    $forcedRepairDispatched,
    $forcedRepairCancelled,
    $forcedRepairAcknowledged,
    $forcedRepairAwaitingHandoff,
    $forcedRepairRelocationCompleted,
    $drainedRepairOwnership
) -join "`n"
$preparedBeforeForcedRepair = Assert-ForcedRepairAttemptResolution `
    -Log $preparedBeforeForcedRepairLog `
    -ReaderSession 7 `
    -RepairFaultRequestId 'fault-repair' `
    -RasterMissRequestId 'fault-raster' `
    -GestureId 101 `
    -TextureGeneration 9 `
    -Context 'prepared-before-forced-repair fixture'
if ($preparedBeforeForcedRepair.Kind -ne 'Superseded') {
    throw 'Prepared-before-forced-repair fixture did not prove safe supersession'
}
$preparedBeforeRepairStartLog = @(
    $preparedWhilePendingDeck,
    $forcedRepairStarted,
    $forcedRepairReady,
    $forcedRepairFault,
    $forcedRepairQueued,
    $gestureCommit,
    $forcedRepairDispatched,
    $forcedRepairCancelled,
    $forcedRepairAcknowledged,
    $forcedRepairAwaitingHandoff,
    $forcedRepairRelocationCompleted,
    $drainedRepairOwnership
) -join "`n"
$preparedBeforeRepairStart = Assert-ForcedRepairAttemptResolution `
    -Log $preparedBeforeRepairStartLog `
    -ReaderSession 7 `
    -RepairFaultRequestId 'fault-repair' `
    -RasterMissRequestId 'fault-raster' `
    -GestureId 101 `
    -TextureGeneration 9 `
    -Context 'prepared-before-repair-start fixture'
if ($preparedBeforeRepairStart.Kind -ne 'Superseded') {
    throw 'Prepared-before-repair-start fixture did not prove safe supersession'
}
$foregroundRefillAttempted =
    'reader-preparation session=7 attempt=31 rasterGeneration=2 ' +
    'state=Attempted reason=None eventVersion=-1 durationMs=0' +
    $NoQaCorrelationFields
$foregroundRefillReady = $foregroundRefillAttempted.Replace(
    'state=Attempted reason=None eventVersion=-1 durationMs=0',
    'state=Ready reason=None eventVersion=-1 durationMs=3200'
)
$transientForegroundRefillOwnership = $drainedRepairOwnership.Replace(
    'staged=0',
    'staged=1'
).Replace(
    'callbacks=0',
    'callbacks=1'
)
$foregroundRefillDrainLog = $safelySupersededRepairLog.Replace(
    $forcedRepairCancelled + "`n",
    $forcedRepairCancelled + "`n" + $foregroundRefillAttempted + "`n"
).Replace(
    $drainedRepairOwnership,
    $transientForegroundRefillOwnership + "`n" + $foregroundRefillReady
)
$foregroundRefillResolution = Assert-ForcedRepairAttemptResolution `
    -Log $foregroundRefillDrainLog `
    -ReaderSession 7 `
    -RepairFaultRequestId 'fault-repair' `
    -RasterMissRequestId 'fault-raster' `
    -GestureId 101 `
    -TextureGeneration 9 `
    -Context 'foreground-refill ownership drain fixture'
if ($foregroundRefillResolution.Kind -ne 'Superseded') {
    throw 'Foreground-refill readiness did not prove safe supersession ownership drain'
}
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log $foregroundRefillDrainLog.Replace(
            "`n" + $foregroundRefillReady,
            ''
        ) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'unterminated foreground-refill ownership fixture' | Out-Null
} 'unterminated foreground-refill ownership fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log $foregroundRefillDrainLog.Replace(
            $foregroundRefillReady,
            $foregroundRefillReady.Replace('rasterGeneration=2', 'rasterGeneration=3')
        ) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'wrong-generation foreground-refill ownership fixture' | Out-Null
} 'wrong-generation foreground-refill ownership fixture'
$unrelatedForegroundRefillAttempted = $foregroundRefillAttempted.Replace(
    'attempt=31',
    'attempt=32'
)
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log $foregroundRefillDrainLog.Replace(
            $foregroundRefillAttempted + "`n",
            $foregroundRefillAttempted + "`n" +
                $unrelatedForegroundRefillAttempted + "`n"
        ) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'ambiguous foreground-refill ownership fixture' | Out-Null
} 'ambiguous foreground-refill ownership fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log $foregroundRefillDrainLog.Replace(
            $transientForegroundRefillOwnership,
            $transientForegroundRefillOwnership.Replace(
                'pendingLeases=0',
                'pendingLeases=1'
            )
        ) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'owned foreground-refill drain fixture' | Out-Null
} 'owned foreground-refill drain fixture'
$uncompletedPreparedTexture = @(Get-ReaderPreparedPromotedTexture `
    -Log $preparedWhilePendingLog.Replace($forcedRepairRelocationCompleted, '') `
    -ReaderSession 7 `
    -GestureId 101 `
    -TextureGeneration 9 `
    -Context 'uncompleted prepared texture fixture')
if ($uncompletedPreparedTexture.Count -ne 0) {
    throw 'Uncompleted relocation was accepted as texture promotion'
}
[void](Assert-ForcedRepairAttemptResolution `
    -Log $safelySupersededRepairLog `
    -ReaderSession 7 `
    -RepairFaultRequestId 'fault-repair' `
    -RasterMissRequestId 'fault-raster' `
    -GestureId 101 `
    -TextureGeneration 9 `
    -Context 'safely superseded forced repair fixture')
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log ($safelySupersededRepairLog.Replace(
            $forcedRepairCancelled,
            $forcedRepairCancelled.Replace('state=Cancelled', 'state=Submitted') +
                "`n" + $forcedRepairCancelled
        )) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'submitted superseded repair fixture'
} 'submitted superseded repair fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log ($safelySupersededRepairLog.Replace('prepared=true', 'prepared=false')) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'unprepared promoted repair texture fixture'
} 'unprepared promoted repair texture fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log ($safelySupersededRepairLog + "`n" +
            $gestureRejectedPreparing.Replace(
                'gestureId=102 outcome=RejectedPreparing',
                'gestureId=102 outcome=FailedRecovery'
            )) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'failed recovery terminal fixture'
} 'failed recovery terminal fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log ($safelySupersededRepairLog.Replace(
            'state=Completed rejectionReason=None queueDepth=0 durationMs=12',
            'state=Rejected rejectionReason=QueueInvalidated queueDepth=0 durationMs=12'
        )) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'rejected supersession relocation fixture'
} 'rejected supersession relocation fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log ($safelySupersededRepairLog.Replace('callbacks=0', 'callbacks=1')) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'undrained supersession ownership fixture'
} 'undrained supersession ownership fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log ($safelySupersededRepairLog.Replace(
            'generation=9 repairAttempt=-1',
            'generation=9 repairAttempt=30'
        )) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'repair pending overwrite fixture'
} 'repair pending overwrite fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log ($safelySupersededRepairLog.Replace(
            'prepared=true active=9 pending=null durationMs=8',
            'prepared=true active=8 pending=10 durationMs=8'
        )) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'unpromoted normal repair deck fixture'
} 'unpromoted normal repair deck fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log (($safelySupersededRepairLog.Replace(
            $forcedRepairCancelled + "`n",
            ''
        )) + "`n" + $forcedRepairCancelled) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'ownership sampled before repair cancellation fixture'
} 'ownership sampled before repair cancellation fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log ($safelySupersededRepairLog.Replace(
            'physicalDirection=Left logicalDirection=Next',
            'physicalDirection=Left logicalDirection=Previous'
        )) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'mismatched committed direction fixture'
} 'mismatched committed direction fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log ($safelySupersededRepairLog.Replace('source=3 target=4', 'source=3 target=2')) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'opposite relocation movement fixture'
} 'opposite relocation movement fixture'

$completedForcedRepairSubmitted = $forcedRepairCancelled.Replace(
    'state=Cancelled reason=None durationMs=9',
    'state=Submitted reason=None durationMs=6'
)
$completedForcedRepairTerminal = $forcedRepairCancelled.Replace(
    'state=Cancelled reason=None durationMs=9',
    'state=Completed reason=None durationMs=8'
)
$completedRepairDeckUnprepared = $promotedNormalDeck.Replace(
    'generation=9 repairAttempt=-1 role=Active prepared=true active=9',
    'generation=8 repairAttempt=30 role=Active prepared=false active=8'
).Replace(
    $NoQaCorrelationFields,
    ' qaFaultRequestId=fault-repair qaFaultRelation=AppliedOperation ' +
        'qaFaultPublicationEpoch=-1 qaFaultPersistenceAttemptId=-1 ' +
        'qaFaultRasterRequestEpoch=-1 qaFaultRepairAttemptId=30 ' +
        'qaFaultPreparationAttemptId=-1 qaFaultRelocationToken=none ' +
        'qaFaultHandoffAttemptId=-1'
).Replace('durationMs=8', 'durationMs=7')
$completedRepairDeckPrepared = $completedRepairDeckUnprepared.Replace(
    'prepared=false',
    'prepared=true'
).Replace('durationMs=7', 'durationMs=9')
$completedForcedRepairLog = @(
    $forcedRepairStarted,
    $forcedRepairReady,
    $forcedRepairFault,
    $completedForcedRepairSubmitted,
    $completedRepairDeckUnprepared,
    $completedForcedRepairTerminal,
    $completedRepairDeckPrepared,
    $drainedRepairOwnership
) -join "`n"
[void](Assert-ForcedRepairAttemptResolution `
    -Log $completedForcedRepairLog `
    -ReaderSession 7 `
    -RepairFaultRequestId 'fault-repair' `
    -RasterMissRequestId 'fault-raster' `
    -GestureId 101 `
    -TextureGeneration 9 `
    -Context 'completed forced repair fixture')
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log ($completedForcedRepairLog.Replace(
            'centerOrdinal=3 state=Submitted reason=None durationMs=6',
            'centerOrdinal=4 state=Submitted reason=None durationMs=6'
        )) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'changed submitted repair window fixture'
} 'changed submitted repair window fixture'
Assert-Throws {
    Assert-ForcedRepairAttemptResolution `
        -Log ($completedForcedRepairLog.Replace(
            'prepared=true active=8 pending=null durationMs=9',
            'prepared=true active=9 pending=null durationMs=9'
        )) `
        -ReaderSession 7 `
        -RepairFaultRequestId 'fault-repair' `
        -RasterMissRequestId 'fault-raster' `
        -GestureId 101 `
        -TextureGeneration 9 `
        -Context 'mismatched active repair deck fixture'
} 'mismatched active repair deck fixture'

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
$contentRejectedHandoff = $relocationHandoffRecovery.Replace(
    'result=Ready',
    'result=ContentRejected'
)
$parsedContentRejectedHandoff = @(
    ConvertFrom-ReaderHandoffLog $contentRejectedHandoff
)
if ($parsedContentRejectedHandoff.Count -ne 1 -or
    $parsedContentRejectedHandoff[0].Result -cne 'ContentRejected') {
    throw 'Content-rejected handoff diagnostic did not parse'
}
Assert-ReaderDiagnosticRecordSet `
    -Records @($contentRejectedHandoff) `
    -Context 'content-rejected handoff schema fixture'
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
$preparationAttemptRecord =
    'reader-preparation session=7 attempt=40 rasterGeneration=2 ' +
    'state=Attempted reason=None eventVersion=-1 durationMs=0' +
    $NoQaCorrelationFields
$preparationReadyRecord =
    'reader-preparation session=7 attempt=40 rasterGeneration=2 ' +
    'state=Ready reason=None eventVersion=-1 durationMs=200' +
    $NoQaCorrelationFields
$activeDeckAttemptRecord =
    'reader-deck session=7 generation=5 repairAttempt=-1 role=Active ' +
    'prepared=false active=5 pending=null durationMs=0' +
    $NoQaCorrelationFields
$activeDeckReadyRecord =
    'reader-deck session=7 generation=5 repairAttempt=-1 role=Active ' +
    'prepared=true active=5 pending=null durationMs=200' +
    $NoQaCorrelationFields
$activeDeckRecoveryLog = $relocationRecoveryLog.
    Replace($preparationAttemptRecord, $activeDeckAttemptRecord).
    Replace($preparationReadyRecord, $activeDeckReadyRecord)
if ($activeDeckRecoveryLog -ceq $relocationRecoveryLog -or
    $activeDeckRecoveryLog.Contains('reader-preparation session=7 attempt=40')) {
    throw 'Active deck timeout recovery fixture did not replace preparation records'
}
$activeDeckRecoveryDrain = Get-ReaderCommittedRelocationDrainStatus `
    -Log $activeDeckRecoveryLog `
    -ReaderSession 7 `
    -CommittedGestureIds @(3, 4) `
    -Context 'active deck acknowledgement timeout recovery fixture'
if ($activeDeckRecoveryDrain.RecoveredRejectedRelocations.Count -ne 1) {
    throw 'Prepared active deck did not recover an acknowledgement timeout'
}
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $activeDeckRecoveryLog.Replace('prepared=true', 'prepared=false') `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context 'unprepared active deck timeout recovery fixture' | Out-Null
} 'unprepared active deck timeout recovery fixture'
foreach ($staleGeneration in @(3, 1)) {
    $staleDeckLog = $activeDeckRecoveryLog.
        Replace('generation=5 repairAttempt=-1', "generation=$staleGeneration repairAttempt=-1").
        Replace('active=5 pending=null', "active=$staleGeneration pending=null")
    $staleDeckDrain = Get-ReaderCommittedRelocationDrainStatus `
        -Log $staleDeckLog `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context "stale active deck generation $staleGeneration fixture"
    if ($staleDeckDrain.RecoveredRejectedRelocations.Count -ne 0) {
        throw "Stale active deck generation $staleGeneration was accepted as timeout recovery"
    }
}
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $activeDeckRecoveryLog.Replace(
            $activeDeckAttemptRecord,
            $activeDeckAttemptRecord + "`n" + $activeDeckAttemptRecord
        ) `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context 'duplicate active deck attempt fixture' | Out-Null
} 'duplicate active deck attempt fixture'
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $activeDeckRecoveryLog.Replace(
            $activeDeckReadyRecord,
            $activeDeckReadyRecord + "`n" + $activeDeckAttemptRecord
        ) `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context 'active deck post-ready regression fixture' | Out-Null
} 'active deck post-ready regression fixture'
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $activeDeckRecoveryLog.Replace(
            $activeDeckReadyRecord,
            $activeDeckReadyRecord.Replace('repairAttempt=-1', 'repairAttempt=9')
        ) `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context 'active deck changed identity fixture' | Out-Null
} 'active deck changed identity fixture'
$leaderQueued = @(
    ConvertFrom-ReaderRelocationLog $relocationRecoveryLog | Where-Object {
        $_.GestureId -eq 3 -and $_.State -eq 'Queued'
    }
)[0].LogLine
$leaderDispatch = @(
    ConvertFrom-ReaderRelocationLog $relocationRecoveryLog | Where-Object {
        $_.GestureId -eq 3 -and $_.State -eq 'Dispatched'
    }
)[0].LogLine
$leaderRejection = @(
    ConvertFrom-ReaderRelocationLog $relocationRecoveryLog | Where-Object {
        $_.GestureId -eq 3 -and $_.State -eq 'Rejected'
    }
)[0].LogLine
$followerQueued =
    'reader-relocation session=7 token=move-follower gestureId=5 source=2 target=3 ' +
    'logicalDirection=Next rasterGeneration=2 textureGeneration=5 ' +
    'state=Queued rejectionReason=None queueDepth=2 durationMs=0' +
    $NoQaCorrelationFields
$followerRejected = $followerQueued.Replace(
    'state=Queued rejectionReason=None queueDepth=2 durationMs=0',
    'state=Rejected rejectionReason=AcknowledgementTimeout queueDepth=0 durationMs=6000'
)
$timeoutCascadeLog = $relocationRecoveryLog.Replace(
    $leaderDispatch,
    $leaderDispatch + "`n" + $followerQueued
).Replace(
    $leaderRejection,
    $leaderRejection + "`n" + $followerRejected
)
$timeoutCascadeDrain = Get-ReaderCommittedRelocationDrainStatus `
    -Log $timeoutCascadeLog `
    -ReaderSession 7 `
    -CommittedGestureIds @(3, 5, 4) `
    -Context 'acknowledgement timeout cascade fixture'
if ($timeoutCascadeDrain.TerminalRelocations.Count -ne 3 -or
    $timeoutCascadeDrain.CompletedRelocations.Count -ne 1 -or
    $timeoutCascadeDrain.RejectedRelocations.Count -ne 2 -or
    $timeoutCascadeDrain.RecoveredRejectedRelocations.Count -ne 2 -or
    $timeoutCascadeDrain.PendingCount -ne 0) {
    throw 'Acknowledgement timeout cascade fixture did not drain queued followers'
}
$followerTwoQueued = $followerQueued.Replace(
    'token=move-follower gestureId=5 source=2 target=3',
    'token=move-follower-2 gestureId=6 source=3 target=4'
).Replace(
    'textureGeneration=5',
    'textureGeneration=6'
).Replace(
    'queueDepth=2',
    'queueDepth=3'
)
$followerTwoRejected = $followerTwoQueued.Replace(
    'state=Queued rejectionReason=None queueDepth=3 durationMs=0',
    'state=Rejected rejectionReason=AcknowledgementTimeout queueDepth=0 durationMs=4000'
)
$multiFollowerCascadeLog = $timeoutCascadeLog.Replace(
    $followerQueued,
    $followerQueued + "`n" + $followerTwoQueued
).Replace(
    $followerRejected,
    $followerRejected + "`n" + $followerTwoRejected
)
$multiFollowerCascadeDrain = Get-ReaderCommittedRelocationDrainStatus `
    -Log $multiFollowerCascadeLog `
    -ReaderSession 7 `
    -CommittedGestureIds @(3, 5, 6, 4) `
    -Context 'multi-follower acknowledgement timeout cascade fixture'
if ($multiFollowerCascadeDrain.TerminalRelocations.Count -ne 4 -or
    $multiFollowerCascadeDrain.CompletedRelocations.Count -ne 1 -or
    $multiFollowerCascadeDrain.RejectedRelocations.Count -ne 3 -or
    $multiFollowerCascadeDrain.RecoveredRejectedRelocations.Count -ne 3 -or
    $multiFollowerCascadeDrain.PendingCount -ne 0) {
    throw 'Multi-follower timeout cascade fixture did not drain in queue order'
}
$equalDepthCascadeLog = $multiFollowerCascadeLog.Replace(
    'state=Queued rejectionReason=None queueDepth=3 durationMs=0',
    'state=Queued rejectionReason=None queueDepth=2 durationMs=0'
)
$equalDepthCascadeDrain = Get-ReaderCommittedRelocationDrainStatus `
    -Log $equalDepthCascadeLog `
    -ReaderSession 7 `
    -CommittedGestureIds @(3, 5, 6, 4) `
    -Context 'equal-depth acknowledgement timeout cascade fixture'
if ($equalDepthCascadeDrain.RejectedRelocations.Count -ne 3 -or
    $equalDepthCascadeDrain.RecoveredRejectedRelocations.Count -ne 3) {
    throw 'Equal-depth timeout followers did not preserve queue-index ordering'
}
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $multiFollowerCascadeLog.Replace(
            $followerRejected + "`n" + $followerTwoRejected,
            $followerTwoRejected + "`n" + $followerRejected
        ) `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 5, 6, 4) `
        -Context 'reversed timeout follower fixture' | Out-Null
} 'reversed timeout follower fixture'
$unqueuedLeaderLog = $timeoutCascadeLog.Replace($leaderQueued, '')
if (@(
        ConvertFrom-ReaderRelocationLog $unqueuedLeaderLog | Where-Object {
            $_.GestureId -eq 3 -and $_.State -eq 'Queued'
        }
    ).Count -ne 0) {
    throw 'Unqueued timeout leader fixture retained its queue admission'
}
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $unqueuedLeaderLog `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 5, 4) `
        -Context 'unqueued timeout leader fixture' | Out-Null
} 'unqueued timeout leader fixture'
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $timeoutCascadeLog.Replace(
            'state=Rejected rejectionReason=AcknowledgementTimeout queueDepth=0 durationMs=10001',
            'state=Rejected rejectionReason=AcknowledgementTimeout queueDepth=1 durationMs=10001'
        ) `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 5, 4) `
        -Context 'owned timeout leader terminal fixture' | Out-Null
} 'owned timeout leader terminal fixture'
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $unqueuedLeaderLog `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context 'direct unqueued timeout leader fixture' | Out-Null
} 'direct unqueued timeout leader fixture'
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $relocationRecoveryLog.Replace(
            'state=Rejected rejectionReason=AcknowledgementTimeout queueDepth=0 durationMs=10001',
            'state=Rejected rejectionReason=AcknowledgementTimeout queueDepth=1 durationMs=10001'
        ) `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 4) `
        -Context 'direct owned timeout terminal fixture' | Out-Null
} 'direct owned timeout terminal fixture'
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $timeoutCascadeLog.Replace('durationMs=6000', 'durationMs=12000') `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 5, 4) `
        -Context 'late timeout follower fixture' | Out-Null
} 'late timeout follower fixture'
$followerMalformedDispatch = $followerQueued.Replace(
    'textureGeneration=5 state=Queued rejectionReason=None queueDepth=2 durationMs=0',
    'textureGeneration=6 state=Dispatched rejectionReason=None queueDepth=2 durationMs=1'
)
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $timeoutCascadeLog.Replace(
            $followerQueued,
            $followerQueued + "`n" + $followerMalformedDispatch
        ) `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 5, 4) `
        -Context 'malformed follower dispatch fixture' | Out-Null
} 'malformed follower dispatch fixture'
$unrelatedQueued =
    'reader-relocation session=7 token=move-unrelated gestureId=7 source=4 target=5 ' +
    'logicalDirection=Next rasterGeneration=3 textureGeneration=7 ' +
    'state=Queued rejectionReason=None queueDepth=1 durationMs=0' +
    $NoQaCorrelationFields
$unrelatedDispatched = $unrelatedQueued.Replace(
    'state=Queued rejectionReason=None queueDepth=1 durationMs=0',
    'state=Dispatched rejectionReason=None queueDepth=1 durationMs=1'
)
$unrelatedRejected = $unrelatedQueued.Replace(
    'state=Queued rejectionReason=None queueDepth=1 durationMs=0',
    'state=Rejected rejectionReason=AcknowledgementTimeout queueDepth=0 durationMs=10001'
)
$interleavedTimeoutLog = $timeoutCascadeLog.Replace(
    $followerQueued,
    $followerQueued + "`n" + $unrelatedQueued + "`n" + $unrelatedDispatched
).Replace(
    $leaderRejection + "`n" + $followerRejected,
    $leaderRejection + "`n" + $unrelatedRejected + "`n" + $followerRejected
)
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $interleavedTimeoutLog `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 5, 7, 4) `
        -Context 'interleaved unrelated timeout fixture' | Out-Null
} 'interleaved unrelated timeout fixture'
Assert-Throws {
    Get-ReaderCommittedRelocationDrainStatus `
        -Log $timeoutCascadeLog.Replace('queueDepth=2', 'queueDepth=1') `
        -ReaderSession 7 `
        -CommittedGestureIds @(3, 5, 4) `
        -Context 'unowned timeout follower fixture' | Out-Null
} 'unowned timeout follower fixture'
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
    $pidLogFlow = $candidateTreeText.Substring(
        $candidateTreeText.IndexOf('function Read-ReaderPidLog('),
        $candidateTreeText.IndexOf('$intervalEvidence = @()') -
            $candidateTreeText.IndexOf('function Read-ReaderPidLog(')
    )
    $qaInputWaitFlow = $candidateTreeText.Substring(
        $candidateTreeText.IndexOf('function Wait-ReaderQaInputState('),
        $candidateTreeText.IndexOf('function Wait-ReaderQaFaultState(') -
            $candidateTreeText.IndexOf('function Wait-ReaderQaInputState(')
    )
    if ($candidateTreeText -notmatch
            '\$script:ReaderAccumulatedQaInputLogLines\s*=\s*\[Collections\.Generic\.List\[string\]\]::new\(\)' -or
        $candidateTreeText -notmatch
            '\$script:ReaderAccumulatedQaInputLogLines\.Clear\(\)' -or
        $pidLogFlow -notmatch
            'ConvertFrom-ReaderQaInputLog\s+\$newRawLog' -or
        $pidLogFlow -notmatch
            '\$script:ReaderAccumulatedQaInputLogLines\.Add\(' -or
        $qaInputWaitFlow -notmatch 'Read-ReaderPidLog\s+\$Context' -or
        $qaInputWaitFlow -notmatch
            '\$script:ReaderAccumulatedQaInputLogLines\s+-join\s+"`n"' -or
        $qaInputWaitFlow -match 'Wait-ReaderQaCondition') {
        throw "Runner does not preserve transient QA-input records from raw PID ingestion: $candidateTreeSource"
    }
    if ($candidateTreeText -notmatch
        'git diff HEAD --name-status --no-renames') {
        throw "Candidate-tree contract omits staged-only bytes: $candidateTreeSource"
    }
    if ($candidateTreeText -notmatch 'Get-ReaderPreparationRecoveryAction') {
        throw "Runner omits typed preparation recovery policy: $candidateTreeSource"
    }
    if ($candidateTreeText -notmatch
        "'QuiesceReadyBeforeTerminal'\s*\{\s*Start-Sleep -Milliseconds 750") {
        throw "Runner omits bounded ready-state quiescence: $candidateTreeSource"
    }
    $preparationTerminalFlow = $candidateTreeText.Substring(
        $candidateTreeText.IndexOf('function Wait-ReaderQaPreparationAttemptTerminal('),
        $candidateTreeText.IndexOf('function Wait-ReaderQaPreparedTextureGeneration(') -
            $candidateTreeText.IndexOf('function Wait-ReaderQaPreparationAttemptTerminal(')
    )
    $correlatedInputFlow = $candidateTreeText.Substring(
        $candidateTreeText.IndexOf('function Invoke-ReaderQaCorrelatedSwipeTerminal('),
        $candidateTreeText.IndexOf('function Invoke-ReaderQaCommittedTurn(') -
            $candidateTreeText.IndexOf('function Invoke-ReaderQaCorrelatedSwipeTerminal(')
    )
    $committedTurnFlow = $candidateTreeText.Substring(
        $candidateTreeText.IndexOf('function Invoke-ReaderQaCommittedTurn('),
        $candidateTreeText.IndexOf('function Wait-ReaderQaRelocationTerminal(') -
            $candidateTreeText.IndexOf('function Invoke-ReaderQaCommittedTurn(')
    )
    if ($committedTurnFlow -notmatch '\$MaximumAttempts = 20' -or
        $committedTurnFlow -notmatch '\$attempt -le \$MaximumAttempts') {
        throw "Runner does not enforce a configurable committed-turn attempt bound: $candidateTreeSource"
    }
    if ($committedTurnFlow -notmatch '\$MaximumInputAttempts = 3' -or
        $correlatedInputFlow -notmatch "-Command 'arm-input'" -or
        $correlatedInputFlow -notmatch "-Command 'clear-input'" -or
        $correlatedInputFlow -notmatch '\$silentInputRecoveryLog = Read-ReaderPidLog' -or
        $correlatedInputFlow -notmatch 'Get-ReaderPreparationRecoveryAction' -or
        $correlatedInputFlow -notmatch 'Wait-ReaderQaPreparationAttemptTerminal' -or
        $correlatedInputFlow -match 'Wait-ReaderQaWorkingSetReady' -or
        $correlatedInputFlow -notmatch
            "'AwaitNextAttempt'\s*\{\s*Start-Sleep -Milliseconds 750" -or
        $correlatedInputFlow -notmatch '-WaitSeconds \$preparationRecoveryTimeoutSeconds' -or
        $preparationTerminalFlow -notmatch '\$_.Attempt -eq \$Attempt' -or
        $preparationTerminalFlow -notmatch '\$_.RasterGeneration -eq \$RasterGeneration' -or
        $preparationTerminalFlow -notmatch '\$_.Index -gt \$AfterIndex' -or
        $preparationTerminalFlow -notmatch
            '\$_.State -in @\(''Ready'', ''Failed'', ''Cancelled''\)' -or
        $correlatedInputFlow -notmatch '\$_.GestureId -eq \$gestureId' -or
        $correlatedInputFlow -match '-not \$SeenGestureIds.Contains') {
        throw "Runner does not correlate silent-input retries before terminal waiting: $candidateTreeSource"
    }
    $visualFaultFlow = $candidateTreeText.Substring(
        $candidateTreeText.IndexOf(
            "Add-ReaderQaFault `$visualId 'DelayNextVisualStateCallback'"
        ),
        $candidateTreeText.IndexOf(
            "Add-ReaderQaFault `$repairId 'ForceRepairWithoutPreparedDeck'"
        ) - $candidateTreeText.IndexOf(
            "Add-ReaderQaFault `$visualId 'DelayNextVisualStateCallback'"
        )
    )
    if ($visualFaultFlow -notmatch
            '\$visualRelocation\s*=\s*Wait-ReaderQaRelocationCompleted' -or
        $visualFaultFlow -notmatch
            '-TextureGeneration \$visualRelocation\.Match\.TextureGeneration' -or
        $visualFaultFlow -match
            '-TextureGeneration \$visualTurn\.TextureGeneration') {
        throw "Runner does not validate the completed replacement texture generation: $candidateTreeSource"
    }
    $forcedRepairFlow = $candidateTreeText.Substring(
        $candidateTreeText.IndexOf(
            "Add-ReaderQaFault `$repairId 'ForceRepairWithoutPreparedDeck'"
        ),
        $candidateTreeText.IndexOf('$requestIds = @(') -
            $candidateTreeText.IndexOf(
                "Add-ReaderQaFault `$repairId 'ForceRepairWithoutPreparedDeck'"
            )
    )
    if ($forcedRepairFlow -notmatch
        "State\s+-in\s+@\('Completed',\s*'Cancelled'\)") {
        throw "Runner omits the forced-repair supersession terminal: $candidateTreeSource"
    }
    $forcedRepairTriggerFlow = $candidateTreeText.Substring(
        $candidateTreeText.IndexOf(
            "Add-ReaderQaFault `$repairId 'ForceRepairWithoutPreparedDeck'"
        ),
        $candidateTreeText.IndexOf('$forcedRepairTerminal =') -
            $candidateTreeText.IndexOf(
                "Add-ReaderQaFault `$repairId 'ForceRepairWithoutPreparedDeck'"
            )
    )
    if ($forcedRepairTriggerFlow -notmatch (
            '(?s)' +
                '\$repairTurn\s*=\s*Invoke-ReaderQaCommittedTurn.*' +
                '-MaximumAttempts 3.*' +
                '-RetryOnlyWhileFaultsRemainEnqueued @\(\$missId, \$repairId\).*' +
                '\$forcedRepairApplied\s*=\s*Wait-ReaderQaFaultState\s+`.*' +
                '\$repairId\s+`.*' +
                "'Applied'"
        )) {
        throw "Runner does not wait for one settlement-fenced repair-role application: $candidateTreeSource"
    }
    if ($forcedRepairTriggerFlow -match
        'maximumRepairFaultAttempts|repairFaultAttempt|SwipeDurationMs') {
        throw "Runner still retries or delays input instead of settlement: $candidateTreeSource"
    }
    $outerRunStart = $candidateTreeText.IndexOf('$runSucceeded = $false')
    $pmClear = $candidateTreeText.IndexOf('$clearResult = (', $outerRunStart)
    $scaleCapture = $candidateTreeText.IndexOf(
        '$originalAnimatorDurationScale = Get-ReaderAnimatorDurationScale',
        $pmClear
    )
    $scaleSet = $candidateTreeText.IndexOf(
        "Set-ReaderAnimatorDurationScale '20.0'",
        $scaleCapture
    )
    $readerLaunch = $candidateTreeText.IndexOf(
        'pwsh -NoProfile -ExecutionPolicy Bypass -File',
        $outerRunStart
    )
    $faultMatrix = $candidateTreeText.IndexOf(
        '$faultMatrixLog = Invoke-ReaderQaFaultMatrix',
        $readerLaunch
    )
    $scaleRestore = $candidateTreeText.IndexOf(
        'Restore-ReaderAnimatorDurationScale $originalAnimatorDurationScale',
        $faultMatrix
    )
    $stressBaseline = $candidateTreeText.IndexOf(
        "-Context 'ReaderDev post-fault stress baseline'",
        $scaleRestore
    )
    if ($outerRunStart -lt 0 -or $pmClear -le $outerRunStart -or
        $scaleCapture -le $pmClear -or $scaleSet -le $scaleCapture -or
        $readerLaunch -le $scaleSet -or $faultMatrix -le $readerLaunch -or
        $scaleRestore -le $faultMatrix -or $stressBaseline -le $scaleRestore) {
        throw "Runner does not launch ReaderDev under the bounded settlement scale: $candidateTreeSource"
    }
    if ($candidateTreeText -notmatch (
            '(?s)' +
                'finally\s*\{\s*if \(\$animatorDurationScaleOverridePending\).*' +
                'Restore-ReaderAnimatorDurationScale \$originalAnimatorDurationScale'
        )) {
        throw "Runner omits final animator-scale restoration: $candidateTreeSource"
    }
    if ($forcedRepairFlow -notmatch 'Get-ReaderFaultMatrixOwnershipDrainProof') {
        throw "Runner does not accept exact foreground-refill ownership drain proof: $candidateTreeSource"
    }
    if ($forcedRepairFlow -notmatch 'Wait-ReaderQaPreparedTextureGeneration') {
        throw "Runner omits promoted normal-deck preparation: $candidateTreeSource"
    }
    if ($forcedRepairFlow -notmatch
            '-TextureGeneration \$repairRelocation\.Match\.TextureGeneration' -or
        $forcedRepairFlow -match
            '-TextureGeneration \$repairTurn\.TextureGeneration') {
        throw "Runner does not validate the completed repair replacement texture: $candidateTreeSource"
    }
    if ($forcedRepairFlow -match 'ReaderDev repair completion') {
        throw "Runner still requires completion-only repair semantics: $candidateTreeSource"
    }
    if ($forcedRepairFlow -match 'Wait-ReaderPreparedDeckOwnership') {
        throw "Runner accepts a stale prepared deck for forced repair: $candidateTreeSource"
    }
    if ($forcedRepairFlow -notmatch
        'RepairAttempt\s+-eq\s+\$forcedRepairAttemptId') {
        throw "Runner omits exact completed repair-deck ownership: $candidateTreeSource"
    }
    if ($candidateTreeText -notmatch 'Assert-ForcedRepairAttemptResolution') {
        throw "Runner omits exact forced-repair resolution proof: $candidateTreeSource"
    }
    if ($candidateTreeText -match 'Assert-RepairAttemptReachesSubmission') {
        throw "Runner still accepts an unrelated completed repair: $candidateTreeSource"
    }
    if ($candidateTreeText -notmatch (
            '(?s)' +
                'function Get-ReaderAnimatorDurationScale.*' +
                "'settings', 'get', 'global', 'animator_duration_scale'.*" +
                'function Set-ReaderAnimatorDurationScale.*' +
                "'settings', 'put', 'global', 'animator_duration_scale'.*" +
                'function Restore-ReaderAnimatorDurationScale.*' +
                'foreach \(\$attempt in 1\.\.3\)'
        )) {
        throw "Runner omits authenticated animator-scale control: $candidateTreeSource"
    }
}

$rendererSource = Join-Path $PSScriptRoot (
    '../third_party/playlikecurl/karackencurllib/src/main/java/' +
        'karacken/curl/PageSurfaceView.java'
)
if (-not (Test-Path -LiteralPath $rendererSource -PathType Leaf)) {
    throw "Settlement renderer contract is absent: $rendererSource"
}
$rendererText = Get-Content -LiteralPath $rendererSource -Raw
$settleFlow = $rendererText.Substring(
    $rendererText.IndexOf('private void settle(Settlement settlement) {'),
    $rendererText.IndexOf('private void startBoundaryRestoration(') -
        $rendererText.IndexOf('private void settle(Settlement settlement) {')
)
$settlementAnimationFlow = $rendererText.Substring(
    $rendererText.IndexOf('private void startSettlementAnimation('),
    $rendererText.IndexOf('private void completeBoundaryRestorationAnimation()') -
        $rendererText.IndexOf('private void startSettlementAnimation(')
)
if ($settleFlow.IndexOf('onSettlementStarted(') -lt 0 -or
    $settleFlow.IndexOf('startSettlementAnimation(') -le
        $settleFlow.IndexOf('onSettlementStarted(') -or
    $settlementAnimationFlow -notmatch 'ValueAnimator\.ofFloat\(' -or
    $settlementAnimationFlow -notmatch
        'settlementAnimator\.setDuration\(settlement\.getDurationMillis\(\)\)' -or
    $settlementAnimationFlow -notmatch 'settlementAnimator\.start\(\)') {
    throw 'Animator slowdown no longer fences the repair-triggering settlement interval'
}

Write-Output 'Reader PlayLikeCurl QA parser PASS'
