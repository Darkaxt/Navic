$ReaderRelocationAcknowledgementTimeoutFloorMs = 9500L
$OwnershipPattern = [regex]::new(
    'reader-ownership session=(?<Session>\d+) ' +
    'phase=(?<Phase>cold-start|peak-preparation|steady-state|after-close) ' +
    'residents=(?<Residents>\d+) residentLimit=(?<ResidentLimit>\d+) ' +
    'adapterDecoded=(?<AdapterDecoded>\d+) ' +
    'adapterDecodedLimit=(?<AdapterDecodedLimit>\d+) ' +
    'cacheDecoded=(?<CacheDecoded>\d+) ' +
    'cacheDecodedLimit=(?<CacheDecodedLimit>\d+) ' +
    'staged=(?<Staged>\d+) stagedLimit=(?<StagedLimit>\d+) ' +
    'activeLeases=(?<ActiveLeases>\d+) activeLeaseLimit=(?<ActiveLeaseLimit>\d+) ' +
    'pendingLeases=(?<PendingLeases>\d+) pendingLeaseLimit=(?<PendingLeaseLimit>\d+) ' +
    'releaseInFlightLeases=(?<ReleaseInFlightLeases>\d+) ' +
    'releaseInFlightLeaseLimit=(?<ReleaseInFlightLeaseLimit>\d+) ' +
    'orphanLeases=(?<OrphanLeases>\d+) orphanLeaseLimit=(?<OrphanLeaseLimit>\d+) ' +
    'textures=(?<Textures>\d+) textureLimit=(?<TextureLimit>\d+) ' +
    'callbacks=(?<Callbacks>\d+) callbackLimit=(?<CallbackLimit>\d+) ' +
    'relocationReservations=(?<RelocationReservations>\d+) ' +
    'queuedRelocations=(?<QueuedRelocations>\d+) ' +
    'relocations=(?<Relocations>\d+) relocationLimit=(?<RelocationLimit>\d+) ' +
    'withinBounds=(?<WithinBounds>true|false)'
)
$OwnershipUnavailablePattern = [regex]::new(
    'reader-ownership-unavailable session=(?<Session>\d+) ' +
    'phase=(?<Phase>cold-start|peak-preparation|steady-state|after-close) ' +
    'status=(?<Status>APPLICATION_EPOCH_CHANGED|SURFACE_UNAVAILABLE|QUEUE_REJECTED|CALLBACK_CAPACITY)'
)
$RasterCachePattern = [regex]::new(
    'reader-raster-cache session=(?<Session>\d+) ' +
    'phase=(?<Phase>cold-start|peak-preparation|steady-state|after-close) ' +
    'diskEntries=(?<DiskEntries>\d+) diskBytes=(?<DiskBytes>\d+) ' +
    'diskByteLimit=(?<DiskByteLimit>\d+) decodedEntries=(?<DecodedEntries>\d+) ' +
    'decodedUnique=(?<DecodedUnique>\d+) ' +
    'decodedUniqueLimit=(?<DecodedUniqueLimit>\d+) ' +
    'pendingDecodedReleases=(?<PendingDecodedReleases>\d+) ' +
    'activeEncodePins=(?<ActiveEncodePins>\d+) ' +
    'encodePinnedIdentities=(?<EncodePinnedIdentities>\d+)'
)
$ResidencyPattern = [regex]::new(
    'reader-residency session=(?<Session>\d+) residents=(?<Residents>\d+) ' +
    'residentLimit=(?<ResidentLimit>\d+) decodedUnique=(?<DecodedUnique>\d+) ' +
    'decodedUniqueLimit=(?<DecodedUniqueLimit>\d+) peakResidents=(?<PeakResidents>\d+) ' +
    'peakDecodedUnique=(?<PeakDecodedUnique>\d+) pinned=(?<Pinned>\d+) ' +
    'pendingReleases=(?<PendingReleases>\d+) ' +
    'evicted=(?<Evicted>\d+) released=(?<Released>\d+)'
)
$RuntimeFailurePattern = [regex]::new(
    'FATAL EXCEPTION|OutOfMemoryError|released without ownership|' +
    'double[- ]release|trying to use a recycled bitmap|recycled bitmap|' +
    'GL_INVALID_(?:ENUM|VALUE|OPERATION|FRAMEBUFFER_OPERATION)|' +
    'GL_OUT_OF_MEMORY|EGL_BAD_(?:ACCESS|ALLOC|ATTRIBUTE|CONTEXT|' +
    'CURRENT_SURFACE|DISPLAY|MATCH|NATIVE_PIXMAP|NATIVE_WINDOW|' +
    'PARAMETER|SURFACE)',
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
)
$GestureLinePattern = [regex]::new(
    'reader-gesture session=(?<Session>\d+) gestureId=(?<Gesture>\d+)\s+[^\r\n]*'
)
$GestureTerminalPattern = [regex]::new(
    'reader-gesture session=(?<Session>\d+) ' +
    'gestureId=(?<Gesture>\d+) outcome=(?<Outcome>' +
      'CommittedForward|CommittedBackward|CancelledByUser|' +
      'RejectedPreparing|RejectedSettling|RejectedDirection|' +
      'RejectedBoundary|RejectedRendererUnavailable|FailedRenderer|' +
      'CompletedTapAction|CancelledLifecycle|FailedRecovery)\b'
)
$GesturePattern = [regex]::new(
    'reader-gesture session=(?<Session>\d+) gestureId=(?<Gesture>\d+) ' +
    'outcome=(?<Outcome>CommittedForward|CommittedBackward|CancelledByUser|' +
        'RejectedPreparing|RejectedSettling|RejectedDirection|' +
        'RejectedBoundary|RejectedRendererUnavailable|FailedRenderer|' +
        'CompletedTapAction|CancelledLifecycle|FailedRecovery) ' +
    'owner=(?<Owner>Pending|Curl|Content|Terminal) ' +
    'rasterGeneration=(?<RasterGeneration>\d+) ' +
    'textureGeneration=(?<TextureGeneration>-1|\d+) ' +
    'physicalDirection=(?<PhysicalDirection>null|Left|Right) ' +
    'logicalDirection=(?<LogicalDirection>null|Previous|Next) ' +
    'durationMs=(?<DurationMs>\d+)'
)
$LifecycleCancellationPattern = [regex]::new(
    'reader-lifecycle-cancellation session=(?<Session>\d+) ' +
    'gestureId=(?<Gesture>\d+) ' +
    'reason=(?<Reason>HostDetached|CanvasDisabled|HostDestroyed|ReaderExit|' +
        'RendererReplaced|RasterProfileInvalidated|UnsafeContextLoss|GlFailure)'
)
$TeardownFailurePattern = [regex]::new(
    'reader-teardown-failure session=(?<Session>\d+) ' +
    'stage=(?<Stage>CallbackFence|RasterInvalidation|RendererDisposal|' +
        'RendererOwnership|DeckGeneration|RasterDeck|RasterAdapter|' +
        'ControllerWorker|BundleOwners|PublicationWorker|PublicationLedger|' +
        'PublicationDispatch|RasterGenerationWorker|RasterHydrationWorker|' +
        'PersistentStore|DecodedCache|ReferenceClear) ' +
    'rendererStage=(?<RendererStage>null|NONE|SETTLEMENT_CANCEL_CALLBACK|' +
        'PRE_GL_SETUP|SURFACE_RESUME|GL_QUEUE_UNAVAILABLE|' +
        'GL_RENDERER_DISPOSE|MAIN_TERMINAL_EXECUTOR|' +
        'DECK_RELEASE_CALLBACK|SURFACE_PAUSE|OWNERSHIP_RETAINED) ' +
    'suppressed=(?<Suppressed>\d+)'
)
$PrefetchPattern = [regex]::new(
    'reader-prefetch session=(?<Session>\d+) ' +
    'prefetchSession=(?<PrefetchSession>\d+) rasterEpoch=(?<RasterEpoch>\d+) ' +
    'state=(?<State>Queued|Running|Completed|Cancelled|Failed) ' +
    'targetCount=(?<TargetCount>\d+) durationMs=(?<DurationMs>\d+)'
)
$QaCorrelationPattern =
    ' qaFaultRequestId=(?<QaFaultRequestId>none|[A-Za-z0-9_-]{1,64}) ' +
    'qaFaultRelation=(?<QaFaultRelation>None|AppliedOperation|Retry|Recovery) ' +
    'qaFaultPublicationEpoch=(?<QaFaultPublicationEpoch>-1|\d+) ' +
    'qaFaultPersistenceAttemptId=(?<QaFaultPersistenceAttemptId>-1|\d+) ' +
    'qaFaultRasterRequestEpoch=(?<QaFaultRasterRequestEpoch>-1|\d+) ' +
    'qaFaultRepairAttemptId=(?<QaFaultRepairAttemptId>-1|\d+) ' +
    'qaFaultPreparationAttemptId=(?<QaFaultPreparationAttemptId>-1|\d+) ' +
    'qaFaultRelocationToken=(?<QaFaultRelocationToken>none|[A-Za-z0-9_-]{1,64}) ' +
    'qaFaultHandoffAttemptId=(?<QaFaultHandoffAttemptId>-1|\d+)'
$NoQaCorrelationFields =
    ' qaFaultRequestId=none qaFaultRelation=None ' +
    'qaFaultPublicationEpoch=-1 qaFaultPersistenceAttemptId=-1 ' +
    'qaFaultRasterRequestEpoch=-1 qaFaultRepairAttemptId=-1 ' +
    'qaFaultPreparationAttemptId=-1 qaFaultRelocationToken=none ' +
    'qaFaultHandoffAttemptId=-1'
$RasterAcquisitionPattern = [regex]::new(
    'reader-raster-acquisition session=(?<Session>\d+) ' +
    'attempt=(?<Attempt>\d+) rasterGeneration=(?<RasterGeneration>\d+) ' +
    'ordinal=(?<Ordinal>\d+) ' +
    'source=(?<Source>PersistentHydration|WebViewCapture) ' +
    'trigger=(?<Trigger>InitialPreparation|WarmReopen|WorkingSetRefill|Repair) ' +
    'result=(?<Result>Started|Hit|Miss|Durable|Failed|Stale|Cancelled) ' +
    'durationMs=(?<DurationMs>\d+)' +
    $QaCorrelationPattern
)
$PreparationPattern = [regex]::new(
    'reader-preparation session=(?<Session>\d+) attempt=(?<Attempt>\d+) ' +
    'rasterGeneration=(?<RasterGeneration>\d+) ' +
    'state=(?<State>Attempted|Deferred|Resumed|Ready|Failed|Cancelled) ' +
    'reason=(?<Reason>None|ContentNotReady|LayoutUnstable|PaginationNotReady|' +
        'WebViewDetached|ReaderPaused) eventVersion=(?<EventVersion>-1|\d+) ' +
    'durationMs=(?<DurationMs>\d+)' +
    $QaCorrelationPattern
)
$RepairPattern = [regex]::new(
    'reader-repair session=(?<Session>\d+) attempt=(?<Attempt>\d+) ' +
    'rasterGeneration=(?<RasterGeneration>\d+) centerOrdinal=(?<CenterOrdinal>\d+) ' +
    'state=(?<State>Started|Deferred|Ready|Submitted|Completed|Failed|Cancelled) ' +
    'reason=(?<Reason>None|ContentNotReady|LayoutUnstable|PaginationNotReady|' +
        'WebViewDetached|ReaderPaused) durationMs=(?<DurationMs>\d+)' +
    $QaCorrelationPattern
)
$QaFaultPattern = [regex]::new(
    'reader-qa-fault session=(?<Session>\d+) ' +
    'requestId=(?<RequestId>[A-Za-z0-9_-]{1,64}) ' +
    'fault=(?<Fault>FailNextPersistence|PauseNextPublication|' +
        'MissNextRasterLoad|ForceRepairWithoutPreparedDeck|' +
        'DeferContentNotReady|DeferLayoutUnstable|' +
        'DeferPaginationNotReady|DeferWebViewDetached|DeferReaderPaused|' +
        'DelayNextVisualStateCallback|' +
        'DelayNextRelocationAcknowledgement) ' +
    'seam=(?<Seam>queue|persistence|publication-worker|raster-resolver|' +
        'repair-role|deferred-retry|visual-state|relocation-ack) ' +
    'state=(?<State>Enqueued|Consumed|Applied|Released|Cleared) ' +
    'publicationEpoch=(?<PublicationEpoch>-1|\d+) ' +
    'persistenceAttemptId=(?<PersistenceAttemptId>-1|\d+) ' +
    'rasterRequestEpoch=(?<RasterRequestEpoch>-1|\d+) ' +
    'repairAttemptId=(?<RepairAttemptId>-1|\d+) ' +
    'preparationAttemptId=(?<PreparationAttemptId>-1|\d+) ' +
    'relocationToken=(?<RelocationToken>none|[A-Za-z0-9_-]{1,64}) ' +
    'handoffAttemptId=(?<HandoffAttemptId>-1|\d+) ' +
    'releaseRequestId=(?<ReleaseRequestId>none|[A-Za-z0-9_-]{1,64}) ' +
    'result=(?<Result>accepted|matched|fault-applied|command-release|' +
        'command-clear|host-closed-discarded)'
)
$DeckPattern = [regex]::new(
    'reader-deck session=(?<Session>\d+) generation=(?<Generation>\d+) ' +
    'repairAttempt=(?<RepairAttempt>-1|\d+) ' +
    'role=(?<Role>Active|Pending) prepared=(?<Prepared>true|false) ' +
    'active=(?<Active>null|\d+) pending=(?<Pending>null|\d+) ' +
    'durationMs=(?<DurationMs>\d+)' +
    $QaCorrelationPattern
)
$RelocationPattern = [regex]::new(
    'reader-relocation session=(?<Session>\d+) ' +
    'token=(?<Token>[A-Za-z0-9_-]{1,64}) gestureId=(?<Gesture>\d+) ' +
    'source=(?<Source>-?\d+) target=(?<Target>-?\d+) ' +
    'logicalDirection=(?<Direction>Previous|Next) ' +
    'rasterGeneration=(?<Raster>\d+) textureGeneration=(?<Texture>\d+) ' +
    'state=(?<State>Queued|Dispatched|Acknowledged|' +
        'AwaitingVisualHandoff|Completed|Rejected) ' +
    'rejectionReason=(?<RejectionReason>None|CommitPublicationFailed|' +
        'QueueInvalidated|AcknowledgementTimeout|JavascriptDispatchFailed) ' +
    'queueDepth=(?<Depth>\d+) durationMs=(?<DurationMs>\d+)' +
    $QaCorrelationPattern
)
$HandoffPattern = [regex]::new(
    'reader-handoff session=(?<Session>\d+) ' +
    'token=(?<Token>[A-Za-z0-9_-]{1,64}) ' +
    'handoffAttemptId=(?<HandoffAttemptId>\d+) target=(?<Target>-?\d+) ' +
    'visualState=(?<Visual>true|false) nextFrame=(?<Frame>true|false) ' +
    'result=(?<Result>Ready|Detached|TimedOut|Invalidated|' +
        'CallbackCapacity|Cancelled|StalePhysicalCallbackReleased) ' +
    'durationMs=(?<DurationMs>\d+)' +
    $QaCorrelationPattern
)
$PublicationPattern = [regex]::new(
    'reader-raster-publication session=(?<Session>\d+) ' +
    'digestPrefix=(?<DigestPrefix>[0-9a-f]{12}) ' +
    'rasterEpoch=(?<RasterEpoch>\d+) ' +
    'persistenceAttemptId=(?<PersistenceAttemptId>\d+) ' +
    'result=(?<Result>Durable|Failed|Stale|Cancelled) ' +
    'durationMs=(?<DurationMs>\d+)' +
    $QaCorrelationPattern
)
$ReaderDiagnosticSchemas = @(
    $OwnershipPattern,
    $OwnershipUnavailablePattern,
    $RasterCachePattern,
    $ResidencyPattern,
    $GesturePattern,
    $LifecycleCancellationPattern,
    $TeardownFailurePattern,
    $PrefetchPattern,
    $RasterAcquisitionPattern,
    $PreparationPattern,
    $RepairPattern,
    $QaFaultPattern,
    $DeckPattern,
    $RelocationPattern,
    $HandoffPattern,
    $PublicationPattern
)
if ($ReaderDiagnosticSchemas.Count -ne 16) {
    throw 'Persisted reader diagnostic schema inventory must contain sixteen kinds'
}
$ReaderDiagnosticIntroducers = @(
    'reader-ownership',
    'reader-ownership-unavailable',
    'reader-raster-cache',
    'reader-residency',
    'reader-gesture',
    'reader-lifecycle-cancellation',
    'reader-teardown-failure',
    'reader-prefetch',
    'reader-raster-acquisition',
    'reader-preparation',
    'reader-repair',
    'reader-qa-fault',
    'reader-deck',
    'reader-relocation',
    'reader-handoff',
    'reader-raster-publication'
) | Sort-Object -Unique
if ($ReaderDiagnosticIntroducers.Count -ne $ReaderDiagnosticSchemas.Count) {
    throw 'Reader diagnostic introducers must map one-to-one to schemas'
}
$ReaderDiagnosticIntroducerPattern = [regex]::new(
    '(?<![A-Za-z0-9_-])(?<Introducer>' +
    (($ReaderDiagnosticIntroducers | ForEach-Object {
        [regex]::Escape($_)
    }) -join '|') + ')(?=\s)'
)
$OwnershipBoundFields = @(
    @{ Count = 'Residents'; Limit = 'ResidentLimit' },
    @{ Count = 'AdapterDecoded'; Limit = 'AdapterDecodedLimit' },
    @{ Count = 'CacheDecoded'; Limit = 'CacheDecodedLimit' },
    @{ Count = 'Staged'; Limit = 'StagedLimit' },
    @{ Count = 'ActiveLeases'; Limit = 'ActiveLeaseLimit' },
    @{ Count = 'PendingLeases'; Limit = 'PendingLeaseLimit' },
    @{ Count = 'ReleaseInFlightLeases'; Limit = 'ReleaseInFlightLeaseLimit' },
    @{ Count = 'OrphanLeases'; Limit = 'OrphanLeaseLimit' },
    @{ Count = 'Textures'; Limit = 'TextureLimit' },
    @{ Count = 'Callbacks'; Limit = 'CallbackLimit' },
    @{ Count = 'Relocations'; Limit = 'RelocationLimit' }
)
if ($OwnershipBoundFields.Count -ne 11) {
    throw 'Ownership parser must enforce exactly eleven owner categories'
}
$ownerNames = @($OwnershipBoundFields | ForEach-Object { $_['Count'] })
if (@($ownerNames | Sort-Object -Unique).Count -ne 11) {
    throw 'Ownership parser contains duplicate owner categories'
}
$OwnershipPlateauCountFields = @(
    'Residents',
    'AdapterDecoded',
    'CacheDecoded',
    'Staged',
    'Textures'
)
if ($OwnershipPlateauCountFields.Count -ne 5 -or
    @($OwnershipPlateauCountFields | Where-Object { $_ -notin $ownerNames }).Count -ne 0) {
    throw 'Ownership plateau fields must be stable owner categories'
}

function ConvertFrom-ReaderOwnershipLog([string] $Log) {
    foreach ($match in $OwnershipPattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            Phase = $match.Groups['Phase'].Value
            Residents = [int]$match.Groups['Residents'].Value
            ResidentLimit = [int]$match.Groups['ResidentLimit'].Value
            AdapterDecoded = [int]$match.Groups['AdapterDecoded'].Value
            AdapterDecodedLimit = [int]$match.Groups['AdapterDecodedLimit'].Value
            CacheDecoded = [int]$match.Groups['CacheDecoded'].Value
            CacheDecodedLimit = [int]$match.Groups['CacheDecodedLimit'].Value
            Staged = [int]$match.Groups['Staged'].Value
            StagedLimit = [int]$match.Groups['StagedLimit'].Value
            ActiveLeases = [int]$match.Groups['ActiveLeases'].Value
            ActiveLeaseLimit = [int]$match.Groups['ActiveLeaseLimit'].Value
            PendingLeases = [int]$match.Groups['PendingLeases'].Value
            PendingLeaseLimit = [int]$match.Groups['PendingLeaseLimit'].Value
            ReleaseInFlightLeases = [int]$match.Groups['ReleaseInFlightLeases'].Value
            ReleaseInFlightLeaseLimit = [int]$match.Groups['ReleaseInFlightLeaseLimit'].Value
            OrphanLeases = [int]$match.Groups['OrphanLeases'].Value
            OrphanLeaseLimit = [int]$match.Groups['OrphanLeaseLimit'].Value
            Textures = [int]$match.Groups['Textures'].Value
            TextureLimit = [int]$match.Groups['TextureLimit'].Value
            Callbacks = [int]$match.Groups['Callbacks'].Value
            CallbackLimit = [int]$match.Groups['CallbackLimit'].Value
            RelocationReservations =
                [int]$match.Groups['RelocationReservations'].Value
            QueuedRelocations =
                [int]$match.Groups['QueuedRelocations'].Value
            Relocations = [int]$match.Groups['Relocations'].Value
            RelocationLimit = [int]$match.Groups['RelocationLimit'].Value
            WithinBounds = $match.Groups['WithinBounds'].Value -eq 'true'
            LogLine = $match.Value
        }
    }
}

function ConvertFrom-ReaderRasterCacheLog([string] $Log) {
    foreach ($match in $RasterCachePattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            Phase = $match.Groups['Phase'].Value
            DiskEntries = [int]$match.Groups['DiskEntries'].Value
            DiskBytes = [long]$match.Groups['DiskBytes'].Value
            DiskByteLimit = [long]$match.Groups['DiskByteLimit'].Value
            DecodedEntries = [int]$match.Groups['DecodedEntries'].Value
            DecodedUnique = [int]$match.Groups['DecodedUnique'].Value
            DecodedUniqueLimit = [int]$match.Groups['DecodedUniqueLimit'].Value
            PendingDecodedReleases =
                [int]$match.Groups['PendingDecodedReleases'].Value
            ActiveEncodePins =
                [int]$match.Groups['ActiveEncodePins'].Value
            EncodePinnedIdentities =
                [int]$match.Groups['EncodePinnedIdentities'].Value
            LogLine = $match.Value
        }
    }
}

function ConvertFrom-ReaderResidencyLog([string] $Log) {
    foreach ($match in $ResidencyPattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            Residents = [int]$match.Groups['Residents'].Value
            ResidentLimit = [int]$match.Groups['ResidentLimit'].Value
            DecodedUnique = [int]$match.Groups['DecodedUnique'].Value
            DecodedUniqueLimit =
                [int]$match.Groups['DecodedUniqueLimit'].Value
            PeakResidents = [int]$match.Groups['PeakResidents'].Value
            PeakDecodedUnique =
                [int]$match.Groups['PeakDecodedUnique'].Value
            Pinned = [int]$match.Groups['Pinned'].Value
            PendingReleases = [int]$match.Groups['PendingReleases'].Value
            Evicted = [int]$match.Groups['Evicted'].Value
            Released = [int]$match.Groups['Released'].Value
            LogLine = $match.Value
        }
    }
}

function ConvertFrom-ReaderRasterAcquisitionLog([string] $Log) {
    foreach ($match in $RasterAcquisitionPattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            Attempt = [long]$match.Groups['Attempt'].Value
            RasterGeneration = [long]$match.Groups['RasterGeneration'].Value
            Ordinal = [int]$match.Groups['Ordinal'].Value
            Source = $match.Groups['Source'].Value
            Trigger = $match.Groups['Trigger'].Value
            Result = $match.Groups['Result'].Value
            DurationMs = [long]$match.Groups['DurationMs'].Value
            QaFaultRequestId = $match.Groups['QaFaultRequestId'].Value
            QaFaultRelation = $match.Groups['QaFaultRelation'].Value
            QaFaultPublicationEpoch =
                [long]$match.Groups['QaFaultPublicationEpoch'].Value
            QaFaultPersistenceAttemptId =
                [long]$match.Groups['QaFaultPersistenceAttemptId'].Value
            QaFaultRasterRequestEpoch =
                [long]$match.Groups['QaFaultRasterRequestEpoch'].Value
            QaFaultRepairAttemptId =
                [long]$match.Groups['QaFaultRepairAttemptId'].Value
            QaFaultPreparationAttemptId =
                [long]$match.Groups['QaFaultPreparationAttemptId'].Value
            QaFaultRelocationToken =
                $match.Groups['QaFaultRelocationToken'].Value
            QaFaultHandoffAttemptId =
                [long]$match.Groups['QaFaultHandoffAttemptId'].Value
            Index = $match.Index
            LogLine = $match.Value
        }
    }
}

function ConvertFrom-ReaderPreparationLog([string] $Log) {
    foreach ($match in $PreparationPattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            Attempt = [long]$match.Groups['Attempt'].Value
            RasterGeneration = [long]$match.Groups['RasterGeneration'].Value
            State = $match.Groups['State'].Value
            Reason = $match.Groups['Reason'].Value
            EventVersion = [long]$match.Groups['EventVersion'].Value
            DurationMs = [long]$match.Groups['DurationMs'].Value
            QaFaultRequestId = $match.Groups['QaFaultRequestId'].Value
            QaFaultRelation = $match.Groups['QaFaultRelation'].Value
            QaFaultPublicationEpoch =
                [long]$match.Groups['QaFaultPublicationEpoch'].Value
            QaFaultPersistenceAttemptId =
                [long]$match.Groups['QaFaultPersistenceAttemptId'].Value
            QaFaultRasterRequestEpoch =
                [long]$match.Groups['QaFaultRasterRequestEpoch'].Value
            QaFaultRepairAttemptId =
                [long]$match.Groups['QaFaultRepairAttemptId'].Value
            QaFaultPreparationAttemptId =
                [long]$match.Groups['QaFaultPreparationAttemptId'].Value
            QaFaultRelocationToken =
                $match.Groups['QaFaultRelocationToken'].Value
            QaFaultHandoffAttemptId =
                [long]$match.Groups['QaFaultHandoffAttemptId'].Value
            Index = $match.Index
            LogLine = $match.Value
        }
    }
}

function Get-ReaderPreparationRecoveryAction(
    [ValidateSet('Attempted', 'Deferred', 'Resumed', 'Ready', 'Failed', 'Cancelled')]
    [string] $State,
    [int] $PreparationIndex,
    [int] $TerminalIndex
) {
    if ($PreparationIndex -lt 0 -or $TerminalIndex -lt 0) {
        throw 'Preparation recovery requires nonnegative diagnostic indices'
    }
    if ($State -eq 'Ready') {
        if ($PreparationIndex -gt $TerminalIndex) {
            return 'ReadyAfterTerminal'
        }
        return 'QuiesceReadyBeforeTerminal'
    }
    if ($State -in @('Attempted', 'Deferred', 'Resumed')) {
        return 'AwaitCurrentAttempt'
    }
    'AwaitNextAttempt'
}

function ConvertFrom-ReaderRepairLog([string] $Log) {
    foreach ($match in $RepairPattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            Attempt = [long]$match.Groups['Attempt'].Value
            RasterGeneration = [long]$match.Groups['RasterGeneration'].Value
            CenterOrdinal = [int]$match.Groups['CenterOrdinal'].Value
            State = $match.Groups['State'].Value
            Reason = $match.Groups['Reason'].Value
            DurationMs = [long]$match.Groups['DurationMs'].Value
            QaFaultRequestId = $match.Groups['QaFaultRequestId'].Value
            QaFaultRelation = $match.Groups['QaFaultRelation'].Value
            QaFaultPublicationEpoch =
                [long]$match.Groups['QaFaultPublicationEpoch'].Value
            QaFaultPersistenceAttemptId =
                [long]$match.Groups['QaFaultPersistenceAttemptId'].Value
            QaFaultRasterRequestEpoch =
                [long]$match.Groups['QaFaultRasterRequestEpoch'].Value
            QaFaultRepairAttemptId =
                [long]$match.Groups['QaFaultRepairAttemptId'].Value
            QaFaultPreparationAttemptId =
                [long]$match.Groups['QaFaultPreparationAttemptId'].Value
            QaFaultRelocationToken =
                $match.Groups['QaFaultRelocationToken'].Value
            QaFaultHandoffAttemptId =
                [long]$match.Groups['QaFaultHandoffAttemptId'].Value
            Index = $match.Index
            LogLine = $match.Value
        }
    }
}

function ConvertFrom-ReaderPublicationLog([string] $Log) {
    foreach ($match in $PublicationPattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            DigestPrefix = $match.Groups['DigestPrefix'].Value
            RasterEpoch = [long]$match.Groups['RasterEpoch'].Value
            PersistenceAttemptId =
                [long]$match.Groups['PersistenceAttemptId'].Value
            Result = $match.Groups['Result'].Value
            DurationMs = [long]$match.Groups['DurationMs'].Value
            QaFaultRequestId = $match.Groups['QaFaultRequestId'].Value
            QaFaultRelation = $match.Groups['QaFaultRelation'].Value
            QaFaultPublicationEpoch =
                [long]$match.Groups['QaFaultPublicationEpoch'].Value
            QaFaultPersistenceAttemptId =
                [long]$match.Groups['QaFaultPersistenceAttemptId'].Value
            QaFaultRasterRequestEpoch =
                [long]$match.Groups['QaFaultRasterRequestEpoch'].Value
            QaFaultRepairAttemptId =
                [long]$match.Groups['QaFaultRepairAttemptId'].Value
            QaFaultPreparationAttemptId =
                [long]$match.Groups['QaFaultPreparationAttemptId'].Value
            QaFaultRelocationToken =
                $match.Groups['QaFaultRelocationToken'].Value
            QaFaultHandoffAttemptId =
                [long]$match.Groups['QaFaultHandoffAttemptId'].Value
            Index = $match.Index
            LogLine = $match.Value
        }
    }
}

function ConvertFrom-ReaderDeckLog([string] $Log) {
    foreach ($match in $DeckPattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            Generation = [long]$match.Groups['Generation'].Value
            RepairAttempt = [long]$match.Groups['RepairAttempt'].Value
            Role = $match.Groups['Role'].Value
            Prepared = $match.Groups['Prepared'].Value -eq 'true'
            Active = if ($match.Groups['Active'].Value -eq 'null') {
                $null
            } else { [long]$match.Groups['Active'].Value }
            Pending = if ($match.Groups['Pending'].Value -eq 'null') {
                $null
            } else { [long]$match.Groups['Pending'].Value }
            DurationMs = [long]$match.Groups['DurationMs'].Value
            QaFaultRequestId = $match.Groups['QaFaultRequestId'].Value
            QaFaultRelation = $match.Groups['QaFaultRelation'].Value
            QaFaultPublicationEpoch =
                [long]$match.Groups['QaFaultPublicationEpoch'].Value
            QaFaultPersistenceAttemptId =
                [long]$match.Groups['QaFaultPersistenceAttemptId'].Value
            QaFaultRasterRequestEpoch =
                [long]$match.Groups['QaFaultRasterRequestEpoch'].Value
            QaFaultRepairAttemptId =
                [long]$match.Groups['QaFaultRepairAttemptId'].Value
            QaFaultPreparationAttemptId =
                [long]$match.Groups['QaFaultPreparationAttemptId'].Value
            QaFaultRelocationToken =
                $match.Groups['QaFaultRelocationToken'].Value
            QaFaultHandoffAttemptId =
                [long]$match.Groups['QaFaultHandoffAttemptId'].Value
            Index = $match.Index
            LogLine = $match.Value
        }
    }
}

function ConvertFrom-ReaderRelocationLog([string] $Log) {
    foreach ($match in $RelocationPattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            Token = $match.Groups['Token'].Value
            GestureId = [long]$match.Groups['Gesture'].Value
            Source = [int]$match.Groups['Source'].Value
            Target = [int]$match.Groups['Target'].Value
            Direction = $match.Groups['Direction'].Value
            RasterGeneration = [long]$match.Groups['Raster'].Value
            TextureGeneration = [long]$match.Groups['Texture'].Value
            State = $match.Groups['State'].Value
            RejectionReason = $match.Groups['RejectionReason'].Value
            QueueDepth = [int]$match.Groups['Depth'].Value
            DurationMs = [long]$match.Groups['DurationMs'].Value
            QaFaultRequestId = $match.Groups['QaFaultRequestId'].Value
            QaFaultRelation = $match.Groups['QaFaultRelation'].Value
            QaFaultPublicationEpoch =
                [long]$match.Groups['QaFaultPublicationEpoch'].Value
            QaFaultPersistenceAttemptId =
                [long]$match.Groups['QaFaultPersistenceAttemptId'].Value
            QaFaultRasterRequestEpoch =
                [long]$match.Groups['QaFaultRasterRequestEpoch'].Value
            QaFaultRepairAttemptId =
                [long]$match.Groups['QaFaultRepairAttemptId'].Value
            QaFaultPreparationAttemptId =
                [long]$match.Groups['QaFaultPreparationAttemptId'].Value
            QaFaultRelocationToken =
                $match.Groups['QaFaultRelocationToken'].Value
            QaFaultHandoffAttemptId =
                [long]$match.Groups['QaFaultHandoffAttemptId'].Value
            Index = $match.Index
            LogLine = $match.Value
        }
    }
}

function Get-ReaderCommittedRelocationDrainStatus(
    [string] $Log,
    [long] $ReaderSession,
    [long[]] $CommittedGestureIds,
    [string] $Context
) {
    $committed = @($CommittedGestureIds | Sort-Object -Unique)
    if ($committed.Count -ne $CommittedGestureIds.Count) {
        throw "$Context contains duplicate committed gesture IDs"
    }
    $relocations = @(
        ConvertFrom-ReaderRelocationLog $Log | Where-Object {
            $_.Session -eq $ReaderSession -and
            $_.GestureId -in $committed
        }
    )
    $preparations = @(
        ConvertFrom-ReaderPreparationLog $Log | Where-Object {
            $_.Session -eq $ReaderSession
        }
    )
    $terminalRelocations = [Collections.Generic.List[object]]::new()
    $completedRelocations = [Collections.Generic.List[object]]::new()
    $rejectedRelocations = [Collections.Generic.List[object]]::new()
    $recoveredRejectedRelocations = [Collections.Generic.List[object]]::new()

    foreach ($gestureId in $committed) {
        $records = @(
            $relocations | Where-Object GestureId -eq $gestureId |
                Sort-Object Index
        )
        $terminals = @(
            $records | Where-Object State -in @('Completed', 'Rejected')
        )
        if ($terminals.Count -gt 1) {
            throw "$Context emitted duplicate relocation terminals for gesture $gestureId"
        }
        if ($terminals.Count -eq 0) { continue }

        $terminal = $terminals[0]
        if (@($records | Where-Object Index -gt $terminal.Index).Count -ne 0) {
            throw "$Context emitted relocation state after terminal for gesture $gestureId"
        }
        $terminalRelocations.Add($terminal)
        if ($terminal.State -eq 'Completed') {
            if ($terminal.RejectionReason -ne 'None') {
                throw "$Context completed gesture $gestureId has a rejection reason"
            }
            $completedRelocations.Add($terminal)
            continue
        }

        if ($terminal.RejectionReason -ne 'AcknowledgementTimeout') {
            throw "$Context rejected gesture $gestureId for $($terminal.RejectionReason)"
        }
        $rejectedRelocations.Add($terminal)
        $dispatches = @(
            $records | Where-Object {
                $_.State -eq 'Dispatched' -and
                $_.Index -lt $terminal.Index -and
                $_.Token -ceq $terminal.Token -and
                $_.RasterGeneration -eq $terminal.RasterGeneration -and
                $_.TextureGeneration -eq $terminal.TextureGeneration
            }
        )
        if ($dispatches.Count -ne 1) {
            throw "$Context rejected gesture $gestureId without one exact dispatch"
        }
        if (@(
                $records | Where-Object {
                    $_.Index -lt $terminal.Index -and
                    $_.State -in @('Acknowledged', 'AwaitingVisualHandoff')
                }
            ).Count -ne 0) {
            throw "$Context rejected gesture $gestureId after acknowledgement"
        }
        if ($terminal.DurationMs -lt
            $ReaderRelocationAcknowledgementTimeoutFloorMs) {
            throw "$Context rejected gesture $gestureId before the acknowledgement timeout"
        }
        $recoveryAttempts = @(
            $preparations | Where-Object {
                $_.State -eq 'Attempted' -and $_.Index -gt $terminal.Index
            } | Sort-Object Index
        )
        foreach ($attempt in $recoveryAttempts) {
            $ready = @(
                $preparations | Where-Object {
                    $_.Attempt -eq $attempt.Attempt -and
                    $_.RasterGeneration -eq $attempt.RasterGeneration -and
                    $_.State -eq 'Ready' -and $_.Index -gt $attempt.Index
                }
            )
            if ($ready.Count -eq 1) {
                $recoveredRejectedRelocations.Add($terminal)
                break
            }
            if ($ready.Count -gt 1) {
                throw "$Context emitted duplicate recovery readiness for attempt $($attempt.Attempt)"
            }
        }
    }

    [pscustomobject]@{
        TerminalRelocations = @($terminalRelocations)
        CompletedRelocations = @($completedRelocations)
        RejectedRelocations = @($rejectedRelocations)
        RecoveredRejectedRelocations = @($recoveredRejectedRelocations)
        PendingCount = $committed.Count - $terminalRelocations.Count
    }
}

function ConvertFrom-ReaderHandoffLog([string] $Log) {
    foreach ($match in $HandoffPattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            Token = $match.Groups['Token'].Value
            HandoffAttemptId = [long]$match.Groups['HandoffAttemptId'].Value
            Target = [int]$match.Groups['Target'].Value
            VisualState = $match.Groups['Visual'].Value -eq 'true'
            NextFrame = $match.Groups['Frame'].Value -eq 'true'
            Result = $match.Groups['Result'].Value
            DurationMs = [long]$match.Groups['DurationMs'].Value
            QaFaultRequestId = $match.Groups['QaFaultRequestId'].Value
            QaFaultRelation = $match.Groups['QaFaultRelation'].Value
            QaFaultPublicationEpoch =
                [long]$match.Groups['QaFaultPublicationEpoch'].Value
            QaFaultPersistenceAttemptId =
                [long]$match.Groups['QaFaultPersistenceAttemptId'].Value
            QaFaultRasterRequestEpoch =
                [long]$match.Groups['QaFaultRasterRequestEpoch'].Value
            QaFaultRepairAttemptId =
                [long]$match.Groups['QaFaultRepairAttemptId'].Value
            QaFaultPreparationAttemptId =
                [long]$match.Groups['QaFaultPreparationAttemptId'].Value
            QaFaultRelocationToken =
                $match.Groups['QaFaultRelocationToken'].Value
            QaFaultHandoffAttemptId =
                [long]$match.Groups['QaFaultHandoffAttemptId'].Value
            Index = $match.Index
            LogLine = $match.Value
        }
    }
}

function ConvertFrom-ReaderQaFaultLog([string] $Log) {
    foreach ($match in $QaFaultPattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            RequestId = $match.Groups['RequestId'].Value
            Fault = $match.Groups['Fault'].Value
            Seam = $match.Groups['Seam'].Value
            State = $match.Groups['State'].Value
            PublicationEpoch = [long]$match.Groups['PublicationEpoch'].Value
            PersistenceAttemptId =
                [long]$match.Groups['PersistenceAttemptId'].Value
            RasterRequestEpoch =
                [long]$match.Groups['RasterRequestEpoch'].Value
            RepairAttemptId = [long]$match.Groups['RepairAttemptId'].Value
            PreparationAttemptId =
                [long]$match.Groups['PreparationAttemptId'].Value
            RelocationToken = $match.Groups['RelocationToken'].Value
            HandoffAttemptId =
                [long]$match.Groups['HandoffAttemptId'].Value
            ReleaseRequestId = $match.Groups['ReleaseRequestId'].Value
            Result = $match.Groups['Result'].Value
            Index = $match.Index
            LogLine = $match.Value
        }
    }
}

function Test-ReaderQaFaultOperationAbsent([object] $Event) {
    return $Event.PublicationEpoch -eq -1 -and
        $Event.PersistenceAttemptId -eq -1 -and
        $Event.RasterRequestEpoch -eq -1 -and
        $Event.RepairAttemptId -eq -1 -and
        $Event.PreparationAttemptId -eq -1 -and
        $Event.RelocationToken -eq 'none' -and
        $Event.HandoffAttemptId -eq -1
}

function Test-ReaderQaFaultOperationEqual([object] $Left, [object] $Right) {
    return $Left.PublicationEpoch -eq $Right.PublicationEpoch -and
        $Left.PersistenceAttemptId -eq $Right.PersistenceAttemptId -and
        $Left.RasterRequestEpoch -eq $Right.RasterRequestEpoch -and
        $Left.RepairAttemptId -eq $Right.RepairAttemptId -and
        $Left.PreparationAttemptId -eq $Right.PreparationAttemptId -and
        $Left.RelocationToken -eq $Right.RelocationToken -and
        $Left.HandoffAttemptId -eq $Right.HandoffAttemptId
}

function Assert-ReaderQaFaultAppliedContext(
    [object] $Event,
    [string] $Context
) {
    if ($Event.State -ne 'Applied') {
        throw "$Context is not an Applied fault event"
    }
    $present = @{
        PublicationEpoch = $Event.PublicationEpoch -ge 0
        PersistenceAttemptId = $Event.PersistenceAttemptId -ge 0
        RasterRequestEpoch = $Event.RasterRequestEpoch -ge 0
        RepairAttemptId = $Event.RepairAttemptId -ge 0
        PreparationAttemptId = $Event.PreparationAttemptId -ge 0
        RelocationToken = $Event.RelocationToken -ne 'none'
        HandoffAttemptId = $Event.HandoffAttemptId -ge 0
    }
    $expected = switch ($Event.Fault) {
        'FailNextPersistence' {
            @('PublicationEpoch', 'PersistenceAttemptId')
        }
        'PauseNextPublication' { @('PublicationEpoch') }
        'MissNextRasterLoad' { @('RasterRequestEpoch') }
        'ForceRepairWithoutPreparedDeck' { @('RepairAttemptId') }
        { $_ -in @(
            'DeferContentNotReady', 'DeferLayoutUnstable',
            'DeferPaginationNotReady', 'DeferWebViewDetached',
            'DeferReaderPaused'
        ) } { @('PreparationAttemptId') }
        'DelayNextVisualStateCallback' {
            @('RelocationToken', 'HandoffAttemptId')
        }
        'DelayNextRelocationAcknowledgement' { @('RelocationToken') }
        default { throw "$Context has an unknown fault enum" }
    }
    foreach ($name in $present.Keys) {
        if ($present[$name] -ne ($name -in $expected)) {
            throw "$Context has a non-canonical operation context"
        }
    }
}

function Assert-ReaderQaFaultCorrelation(
    [object[]] $FaultEvents,
    [object[]] $DownstreamEvents,
    [string] $Context
) {
    $applied = @($FaultEvents | Where-Object State -eq 'Applied')
    foreach ($event in $DownstreamEvents) {
        $hasRequest = $event.QaFaultRequestId -ne 'none'
        if (($hasRequest -and $event.QaFaultRelation -eq 'None') -or
            (-not $hasRequest -and $event.QaFaultRelation -ne 'None')) {
            throw "$Context has an invalid request/relation pair: $($event.LogLine)"
        }
        if (-not $hasRequest) { continue }

        $root = @($applied | Where-Object RequestId -eq $event.QaFaultRequestId)
        if ($root.Count -ne 1) {
            throw "$Context downstream event lacks one Applied root: $($event.LogLine)"
        }
        $root = $root[0]
        Assert-ReaderQaFaultAppliedContext `
            -Event $root `
            -Context "$Context Applied root"
        $rootEqual =
            $event.QaFaultPublicationEpoch -eq $root.PublicationEpoch -and
            $event.QaFaultPersistenceAttemptId -eq $root.PersistenceAttemptId -and
            $event.QaFaultRasterRequestEpoch -eq $root.RasterRequestEpoch -and
            $event.QaFaultRepairAttemptId -eq $root.RepairAttemptId -and
            $event.QaFaultPreparationAttemptId -eq $root.PreparationAttemptId -and
            $event.QaFaultRelocationToken -eq $root.RelocationToken -and
            $event.QaFaultHandoffAttemptId -eq $root.HandoffAttemptId
        if (-not $rootEqual) {
            throw "$Context downstream event changed its Applied root: $($event.LogLine)"
        }

        $kind = if ($null -ne $event.PSObject.Properties['DigestPrefix']) {
            'Publication'
        } elseif ($null -ne $event.PSObject.Properties['EventVersion']) {
            'Preparation'
        } elseif ($null -ne $event.PSObject.Properties['CenterOrdinal']) {
            'Repair'
        } elseif ($null -ne $event.PSObject.Properties['Role']) {
            'Deck'
        } elseif ($null -ne $event.PSObject.Properties['QueueDepth']) {
            'Relocation'
        } elseif ($null -ne $event.PSObject.Properties['VisualState']) {
            'Handoff'
        } elseif ($null -ne $event.PSObject.Properties['Trigger']) {
            'RasterAcquisition'
        } else {
            throw "$Context has an unsupported downstream schema: $($event.LogLine)"
        }
        $relationKind = "$($event.QaFaultRelation)/$kind"
        $allowedRelationKinds = switch ($root.Fault) {
            'FailNextPersistence' {
                @('AppliedOperation/Publication', 'Retry/Publication')
            }
            'PauseNextPublication' { @('AppliedOperation/Publication') }
            'MissNextRasterLoad' {
                @(
                    'AppliedOperation/RasterAcquisition',
                    'Recovery/Repair',
                    'Recovery/Deck'
                )
            }
            'ForceRepairWithoutPreparedDeck' {
                @(
                    'AppliedOperation/Repair', 'AppliedOperation/Deck',
                    'Retry/Repair', 'Retry/Deck',
                    'Recovery/Repair', 'Recovery/Deck'
                )
            }
            { $_ -in @(
                'DeferContentNotReady', 'DeferLayoutUnstable',
                'DeferPaginationNotReady', 'DeferWebViewDetached',
                'DeferReaderPaused'
            ) } {
                @('AppliedOperation/Preparation', 'Retry/Preparation')
            }
            'DelayNextVisualStateCallback' {
                @(
                    'AppliedOperation/Handoff',
                    'AppliedOperation/Relocation',
                    'Recovery/Handoff',
                    'Recovery/Relocation'
                )
            }
            'DelayNextRelocationAcknowledgement' {
                @(
                    'AppliedOperation/Relocation',
                    'AppliedOperation/Handoff',
                    'Recovery/Handoff',
                    'Recovery/Relocation'
                )
            }
            default { @() }
        }
        if ($relationKind -notin $allowedRelationKinds) {
            throw "$Context has an invalid fault/relation/schema combination: $($event.LogLine)"
        }

        $currentIdentities = @{}
        switch ($kind) {
            'Publication' {
                $currentIdentities.PublicationEpoch = [long]$event.RasterEpoch
                $currentIdentities.PersistenceAttemptId =
                    [long]$event.PersistenceAttemptId
            }
            'RasterAcquisition' {
                $currentIdentities.RasterRequestEpoch = [long]$event.Attempt
            }
            'Preparation' {
                $currentIdentities.PreparationAttemptId = [long]$event.Attempt
            }
            'Repair' {
                $currentIdentities.RepairAttemptId = [long]$event.Attempt
            }
            'Deck' {
                $currentIdentities.RepairAttemptId = [long]$event.RepairAttempt
            }
            'Relocation' {
                $currentIdentities.RelocationToken = [string]$event.Token
            }
            'Handoff' {
                $currentIdentities.RelocationToken = [string]$event.Token
                $currentIdentities.HandoffAttemptId =
                    [long]$event.HandoffAttemptId
            }
        }
        $rootIdentities = [ordered]@{
            PublicationEpoch = [long]$root.PublicationEpoch
            PersistenceAttemptId = [long]$root.PersistenceAttemptId
            RasterRequestEpoch = [long]$root.RasterRequestEpoch
            RepairAttemptId = [long]$root.RepairAttemptId
            PreparationAttemptId = [long]$root.PreparationAttemptId
            RelocationToken = [string]$root.RelocationToken
            HandoffAttemptId = [long]$root.HandoffAttemptId
        }
        $stableIdentityNames = @('PublicationEpoch', 'RelocationToken')
        $mappedIdentityCount = 0
        $mappedAttemptCount = 0
        foreach ($identity in $rootIdentities.GetEnumerator()) {
            $rootPresent = if ($identity.Key -eq 'RelocationToken') {
                $identity.Value -ne 'none'
            } else {
                [long]$identity.Value -ge 0
            }
            if (-not $rootPresent -or
                -not $currentIdentities.ContainsKey($identity.Key)) {
                continue
            }
            $mappedIdentityCount += 1
            $current = $currentIdentities[$identity.Key]
            if ($identity.Key -ne 'RelocationToken' -and [long]$current -lt 0) {
                throw "$Context has a negative current $($identity.Key): $($event.LogLine)"
            }
            $stable = $identity.Key -in $stableIdentityNames
            if ($stable) {
                if ($current -cne $identity.Value) {
                    throw "$Context changed stable $($identity.Key): $($event.LogLine)"
                }
                continue
            }
            $mappedAttemptCount += 1
            if ($event.QaFaultRelation -eq 'AppliedOperation') {
                if ([long]$current -ne [long]$identity.Value) {
                    throw "$Context direct event changed $($identity.Key): $($event.LogLine)"
                }
            } elseif ([long]$current -eq [long]$identity.Value) {
                throw "$Context retry/recovery reused $($identity.Key): $($event.LogLine)"
            }
        }
        if ($event.QaFaultRelation -eq 'AppliedOperation' -and
            $mappedIdentityCount -eq 0) {
            throw "$Context direct event has no typed root identity: $($event.LogLine)"
        }
        $relocationScopedRecovery =
            $root.RelocationToken -ne 'none' -and
            $kind -in @('Handoff', 'Relocation') -and
            $event.QaFaultRelation -eq 'Recovery' -and
            $mappedIdentityCount -gt 0
        if ($event.QaFaultRelation -in @('Retry', 'Recovery') -and
            $mappedAttemptCount -eq 0 -and
            -not $relocationScopedRecovery) {
            $crossNamespaceIdentity = switch ($kind) {
                'Repair' { [long]$event.Attempt }
                'Deck' { [long]$event.RepairAttempt }
                default { -1L }
            }
            if ($crossNamespaceIdentity -lt 0) {
                throw "$Context retry/recovery lacks a typed downstream identity: $($event.LogLine)"
            }
        }
    }
}

function ConvertFrom-ReaderOwnershipUnavailableLog([string] $Log) {
    foreach ($match in $OwnershipUnavailablePattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            Phase = $match.Groups['Phase'].Value
            Status = $match.Groups['Status'].Value
            Index = $match.Index
            LogLine = $match.Value
        }
    }
}

function Assert-RasterCacheWithinByteLimit(
    [object[]] $Snapshots,
    [string] $Context
) {
    if ($Snapshots.Count -eq 0) {
        throw "$Context emitted no raster-cache snapshots"
    }
    foreach ($snapshot in $Snapshots) {
        if ($snapshot.DiskBytes -gt $snapshot.DiskByteLimit) {
            throw "$Context exceeded disk cache byte limit: $($snapshot.LogLine)"
        }
        if ($snapshot.DecodedUnique -gt $snapshot.DecodedUniqueLimit) {
            throw "$Context exceeded decoded cache identity limit: $($snapshot.LogLine)"
        }
        if ($snapshot.PendingDecodedReleases -gt $snapshot.DecodedUnique) {
            throw "$Context reported impossible decoded release ownership: $($snapshot.LogLine)"
        }
        if ($snapshot.EncodePinnedIdentities -gt $snapshot.ActiveEncodePins) {
            throw "$Context reported impossible encode-pin ownership: $($snapshot.LogLine)"
        }
        if ($snapshot.Phase -eq 'after-close' -and
            ($snapshot.ActiveEncodePins -ne 0 -or
             $snapshot.EncodePinnedIdentities -ne 0)) {
            throw "$Context retained encode pins after close: $($snapshot.LogLine)"
        }
    }
}

function Assert-ReaderResidencyWithinBounds(
    [object[]] $Snapshots,
    [string] $Context
) {
    if ($Snapshots.Count -eq 0) {
        throw "$Context emitted no residency snapshots"
    }
    foreach ($sample in $Snapshots) {
        if ($sample.Residents -gt $sample.ResidentLimit -or
            $sample.PeakResidents -gt $sample.ResidentLimit -or
            $sample.Pinned -gt $sample.ResidentLimit -or
            $sample.DecodedUnique -gt $sample.DecodedUniqueLimit -or
            $sample.PeakDecodedUnique -gt $sample.DecodedUniqueLimit -or
            $sample.PendingReleases -gt $sample.DecodedUnique) {
            throw "$Context exceeded owner-provided residency limits: $($sample.LogLine)"
        }
    }
}

function Assert-ReaderRuntimeLogSafe(
    [string] $Log,
    [string] $Context
) {
    $failure = $RuntimeFailurePattern.Match($Log)
    if ($failure.Success) {
        throw "$Context found prohibited runtime marker=$($failure.Value)"
    }
}

function Assert-ReaderDiagnosticRecordSet(
    [string[]] $Records,
    [string] $Context
) {
    if ($Records.Count -eq 0) {
        throw "$Context contains no reader diagnostic records"
    }
    $forbidden = [regex]::new(
        '://|(?:href|cfi|bookId|userId|credential|transcript|annotation|' +
            'selectedText|rasterPayload)=' +
            '|PRIVATE_EPUB_TEXT|PRIVATE_ANNOTATION|SECRET_TOKEN',
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    foreach ($record in $Records) {
        if ([string]::IsNullOrWhiteSpace($record) -or
            $record.Contains("`r") -or $record.Contains("`n") -or
            $forbidden.IsMatch($record)) {
            throw "$Context contains a prohibited diagnostic record"
        }
        $matches = @(
            $ReaderDiagnosticSchemas | Where-Object {
                $candidate = $_.Match($record)
                $candidate.Success -and $candidate.Index -eq 0 -and
                    $candidate.Length -eq $record.Length
            }
        )
        if ($matches.Count -ne 1) {
            throw "$Context contains an unknown, partial, or ambiguous schema: $record"
        }
    }
}

function ConvertTo-ReaderDiagnosticRecordSet(
    [string] $Log,
    [string] $Context
) {
    $records = @(
        foreach ($line in ($Log -split "`r?`n")) {
            $introducer = $ReaderDiagnosticIntroducerPattern.Match($line)
            if ($introducer.Success) {
                $line.Substring($introducer.Groups['Introducer'].Index)
            }
        }
    )
    Assert-ReaderDiagnosticRecordSet -Records $records -Context $Context
    return $records
}

function Assert-ReaderOwnershipUnavailablePolicy(
    [string] $Log,
    [long] $ReaderSession,
    [string] $Context,
    [switch] $AllowPendingRecovery
) {
    $unavailable = @(
        ConvertFrom-ReaderOwnershipUnavailableLog $Log |
            Where-Object Session -eq $ReaderSession
    )
    foreach ($event in $unavailable) {
        if ($event.Status -in @('CALLBACK_CAPACITY', 'QUEUE_REJECTED')) {
            throw "$Context observed ownership admission failure: $($event.LogLine)"
        }
        if ($event.Phase -eq 'after-close') {
            throw "$Context observed unavailable after-close ownership: $($event.LogLine)"
        }
        if ($event.Status -eq 'SURFACE_UNAVAILABLE' -and
            $event.Phase -ne 'cold-start') {
            throw "$Context observed unexpected surface unavailability: $($event.LogLine)"
        }
        $laterSuccess = @(
            $OwnershipPattern.Matches($Log) |
                Where-Object {
                    $_.Index -gt $event.Index -and
                    [long]$_.Groups['Session'].Value -eq $ReaderSession -and
                    $_.Groups['Phase'].Value -eq $event.Phase
                }
        )
        if ($laterSuccess.Count -eq 0 -and -not $AllowPendingRecovery) {
            throw "$Context never recovered unavailable ownership phase: $($event.LogLine)"
        }
    }
}

function Assert-WarmReopenUsesPersistentHydration(
    [string] $Log,
    [long] $ReaderSession,
    [string] $Context
) {
    $events = @(
        ConvertFrom-ReaderRasterAcquisitionLog $Log |
            Where-Object {
                $_.Session -eq $ReaderSession -and
                $_.Trigger -eq 'WarmReopen'
            }
    )
    $hits = @(
        $events | Where-Object {
            $_.Source -eq 'PersistentHydration' -and $_.Result -eq 'Hit'
        }
    )
    if ($hits.Count -eq 0) {
        throw "$Context emitted no warm-reopen persistent hydration hit"
    }
    $captures = @(
        $events | Where-Object {
            $_.Source -eq 'WebViewCapture' -and $_.Result -eq 'Started'
        }
    )
    if ($captures.Count -ne 0) {
        throw "$Context recaptured a valid warm-reopen raster: $($captures[0].LogLine)"
    }
}

function Assert-TypedDeferralsResumeOnNewerEvent(
    [string] $Log,
    [long] $ReaderSession,
    [string] $Context
) {
    $events = @(
        ConvertFrom-ReaderPreparationLog $Log |
            Where-Object Session -eq $ReaderSession
    )
    $reasons = @(
        'ContentNotReady',
        'LayoutUnstable',
        'PaginationNotReady',
        'WebViewDetached',
        'ReaderPaused'
    )
    foreach ($reason in $reasons) {
        $deferred = @(
            $events | Where-Object {
                $_.State -eq 'Deferred' -and $_.Reason -eq $reason
            }
        )
        if ($deferred.Count -eq 0) {
            throw "$Context emitted no deferred event for $reason"
        }
        foreach ($wait in $deferred) {
            $resumed = @(
                $events | Where-Object {
                    $_.Index -gt $wait.Index -and
                    $_.Attempt -eq $wait.Attempt -and
                    $_.RasterGeneration -eq $wait.RasterGeneration -and
                    $_.State -eq 'Resumed' -and
                    $_.Reason -eq $reason -and
                    $_.EventVersion -gt $wait.EventVersion
                }
            )
            if ($resumed.Count -ne 1) {
                throw "$Context did not resume $reason exactly once on a newer event"
            }
        }
    }
}

function Assert-RepairAttemptReachesSubmission(
    [string] $Log,
    [long] $ReaderSession,
    [string] $Context
) {
    $events = @(
        ConvertFrom-ReaderRepairLog $Log |
            Where-Object Session -eq $ReaderSession
    )
    $started = @($events | Where-Object State -eq 'Started')
    if ($started.Count -eq 0) {
        throw "$Context emitted no repair attempt"
    }
    $completedAttemptCount = 0
    foreach ($start in $started) {
        $later = @(
            $events | Where-Object {
                $_.Index -gt $start.Index -and
                $_.Attempt -eq $start.Attempt -and
                $_.RasterGeneration -eq $start.RasterGeneration
            }
        )
        $terminals = @(
            $later | Where-Object State -in @('Completed', 'Failed', 'Cancelled')
        )
        if ($terminals.Count -ne 1) {
            throw "$Context repair attempt did not reach exactly one terminal"
        }
        $terminal = $terminals[0]
        if (@($later | Where-Object Index -gt $terminal.Index).Count -ne 0) {
            throw "$Context repair attempt emitted events after its terminal"
        }
        $ready = @($later | Where-Object State -eq 'Ready')
        $submitted = @($later | Where-Object State -eq 'Submitted')
        if ($terminal.State -eq 'Failed') {
            throw "$Context repair attempt failed"
        }
        if ($terminal.State -eq 'Cancelled') {
            if ($ready.Count -gt 1 -or $submitted.Count -gt 1 -or
                ($ready.Count -eq 1 -and $ready[0].Index -ge $terminal.Index) -or
                ($submitted.Count -eq 1 -and
                    ($ready.Count -ne 1 -or
                        $ready[0].Index -ge $submitted[0].Index -or
                        $submitted[0].Index -ge $terminal.Index))) {
                throw "$Context cancelled repair attempt has invalid ordering"
            }
            continue
        }
        if ($ready.Count -ne 1 -or
            $submitted.Count -ne 1 -or
            $ready[0].Index -ge $submitted[0].Index -or
            $submitted[0].Index -ge $terminal.Index) {
            throw "$Context completed repair attempt did not order Started < Ready < Submitted < Completed exactly once"
        }
        $completedAttemptCount += 1
    }
    if ($completedAttemptCount -eq 0) {
        throw "$Context emitted no submitted and completed repair attempt"
    }
}

function ConvertFrom-ReaderGestureLog([string] $Log) {
    foreach ($match in $GesturePattern.Matches($Log)) {
        [pscustomobject]@{
            Session = [long]$match.Groups['Session'].Value
            GestureId = [long]$match.Groups['Gesture'].Value
            Outcome = $match.Groups['Outcome'].Value
            Owner = $match.Groups['Owner'].Value
            RasterGeneration =
                [long]$match.Groups['RasterGeneration'].Value
            TextureGeneration =
                [long]$match.Groups['TextureGeneration'].Value
            PhysicalDirection =
                $match.Groups['PhysicalDirection'].Value
            LogicalDirection =
                $match.Groups['LogicalDirection'].Value
            DurationMs = [long]$match.Groups['DurationMs'].Value
            Index = $match.Index
            LogLine = $match.Value
        }
    }
}

function Get-CommittedTurnCount(
    [string] $Log,
    [long] $ReaderSession
) {
    $lines = @(
        $GestureLinePattern.Matches($Log) | Where-Object {
            [long]$_.Groups['Session'].Value -eq $ReaderSession
        }
    )
    $terminals = @(
        ConvertFrom-ReaderGestureLog $Log | Where-Object {
            $_.Session -eq $ReaderSession
        }
    )
    if ($lines.Count -ne $terminals.Count) {
        throw "Reader session $ReaderSession emitted an unknown or malformed gesture terminal"
    }
    $duplicates = @(
        $terminals | Group-Object GestureId | Where-Object Count -ne 1
    )
    if ($duplicates.Count -ne 0) {
        throw "Reader session $ReaderSession emitted duplicate gesture terminals"
    }
    @(
        $terminals | Where-Object {
            $_.Outcome -in @('CommittedForward', 'CommittedBackward')
        } | Select-Object -ExpandProperty GestureId -Unique
    ).Count
}

function Assert-OwnershipWithinBounds(
    [object[]] $Snapshots,
    [string] $Context
) {
    if ($Snapshots.Count -eq 0) {
        throw "$Context emitted no ownership snapshots"
    }
    foreach ($snapshot in $Snapshots) {
        foreach ($field in $OwnershipBoundFields) {
            $count = [int]$snapshot.PSObject.Properties[$field['Count']].Value
            $limit = [int]$snapshot.PSObject.Properties[$field['Limit']].Value
            if ($count -gt $limit) {
                throw "$Context exceeded $($field['Count']): $($snapshot.LogLine)"
            }
        }
        if ($snapshot.Relocations -ne
            ($snapshot.RelocationReservations + $snapshot.QueuedRelocations)) {
            throw "$Context reported inconsistent relocation ownership: $($snapshot.LogLine)"
        }
        if (-not $snapshot.WithinBounds) {
            throw "$Context owner reported withinBounds=false: $($snapshot.LogLine)"
        }
        if ($snapshot.OrphanLeases -ne 0) {
            throw "$Context observed an orphan deck lease: $($snapshot.LogLine)"
        }
    }
}

function Assert-ZeroOwnership([object] $Snapshot, [string] $Context) {
    foreach ($field in $OwnershipBoundFields) {
        $count = [int]$Snapshot.PSObject.Properties[$field['Count']].Value
        if ($count -ne 0) {
            throw "$Context retained $($field['Count']): $($Snapshot.LogLine)"
        }
    }
}

function Assert-NoPostWarmupOwnershipGrowth(
    [object[]] $Snapshots,
    [int] $WarmupCount,
    [string] $Context
) {
    $minimumSnapshotCount = $WarmupCount * 2
    if ($Snapshots.Count -le $minimumSnapshotCount) {
        throw "$Context needs more than $minimumSnapshotCount snapshots"
    }
    $baseline = @(
        $Snapshots |
            Select-Object -Last ($WarmupCount * 2) |
            Select-Object -First $WarmupCount
    )
    $plateau = @($Snapshots | Select-Object -Last $WarmupCount)
    foreach ($countField in $OwnershipPlateauCountFields) {
        $ceiling = (
            $baseline | Measure-Object -Property $countField -Maximum
        ).Maximum
        foreach ($snapshot in $plateau) {
            $value = [int]$snapshot.PSObject.Properties[$countField].Value
            if ($value -gt $ceiling) {
                throw "$Context grew during its final plateau for ${countField}: $($snapshot.LogLine)"
            }
        }
    }
}
