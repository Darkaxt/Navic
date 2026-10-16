package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import karacken.curl.PageChange
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import paige.navic.reader.*

/** Unapplied runtime candidate using the original owning ingress, producers and FIFO publication.
 * All exact custody is retained in memory only. No callback or sampled state fabricates acceptance.
 */
internal class Task407NormalRuntimeRoot(private val activity: Task407RuntimeActivity,
    private val epub: Task407PublicEpub,
    private val loggerObservation: Task407LoggerStartupObservation? = null) {
    private val compose = activity.readerComposeView
    val gateway = ReaderTransitionGateway()
    private val opened = ReaderController().open(ReaderEngineOpenRequest(
        publication = ReaderPublicationIdentity("task407-public-runtime", resourceHref = epub.resource.resourceHref),
        url = epub.resource.publicationUrl
    ))
    private var model by mutableStateOf(ReaderCoordinator(controller = opened.controller))
    val originalOpenedController: ReaderController get() = opened.controller
    val owningController: ReaderController get() = model.controller
    var initialIngressCount = 0
        private set
    var legacyIngressCount = 0
        private set
    var turnIngressCount = 0
        private set
    var constructionCount = 0
        private set
    var constructionInstalled: ReaderActivatedSessionSnapshot? = null
        private set
    var constructionRuntime: ReaderResumableTransitionCoordinator? = null
        private set
    var constructionFinalizationReturned = false
        private set
    var lastTurnReceipt: ReaderPresentationEventReceipt? = null
        private set
    var lastTurnEvent: ReaderBridgeEvent.LocationChanged? = null
        private set
    private var compositionCloseAttempted = false
    private var compositionClosed = false
    private var originalCleanupCustody: Task407OriginalCleanupCustody? = null
    var bootstrapPublicationCount = 0
        private set
    var turnPublicationCount = 0
        private set
    var retainedPublicationCount = 0
        private set
    private var observationFailure: Task407FixedFailure? = null
    private var turnAdmission: Task407TurnAdmission? = null
    private val acceptedTurnCustody = mutableListOf<Task407AcceptedTurnCustody>()
    private var observedStartupRuntime: ReaderPassiveRasterWebViewHost? = null
    private var observedStartupWork: ReaderActivatedRasterWork? = null
    private var observedStartupJavascriptReady = false
    private var observedStartupRuntimeReady = false
    private var observedStartupAttachment = false
    private var observedStartupReadinessPresent = false
    private var observedStartupReadinessDelivered = false
    private var observedStartupBatchPresent = false
    private var observedStartupBatchAtFirstTarget = false
    private var observedStartupBatchPastFirstTarget = false
    private var observedStartupSessionCaptureAttempt = false
    private var observedStartupSessionCaptureFailure = false
    private var observedStartupSessionActiveCapture = false
    private var observedStartupEngineRuntimeInitializationCompleted = false
    private var observedStartupEngineAtEntrypoint = false
    private var observedStartupEngineAttached = false
    private var observedStartupEngineSized = false
    private var observedStartupEngineProgressComplete = false
    private var observedStartupEngineRuntimeRetired = false
    private var observedStartupConstructionMounted = false
    private var observedStartupConstructionExecutorBound = false
    private var observedStartupConstructionWaiting = false
    private var observedStartupConstructionStarting = false
    private var observedStartupConstructionQuery = false
    private var observedStartupConstructionRetired = false
    private var observedMaterialTimeoutBeforeAnyEngineAck = false
    private var observedMaterialTimeoutBeforeEngineOpenDispatch = false
    private var observedMaterialTimeoutBeforeEngineReady = false
    private var startupObservationUnavailable = false

    /** The existing local is called ONLY by the authenticated original FIFO publication closure,
     * after original snapshot carry/validation and before the original predecessor release.
     * It projects through the owning pure reducer once. Read-only validation is not authority.
     * Observation failure is retained as a fixed label, never thrown into/delaying producer cleanup.
     */
    private val physicalPublicationIngress: ReaderInstalledPhysicalPublicationIngress = { command, applied ->
        try {
            model = model.onInstalledPhysicalPublication(command, applied.publishedOwner,
                applied.publishedBinding, applied.finalPhysicalInputLease)
            captureAcceptedPhysicalPublication(command, applied)
        } catch (safe: Task407FixedFailure) {
            if (observationFailure == null) observationFailure = safe.sanitizedCopy()
        } catch (_: Throwable) {
            if (observationFailure == null) observationFailure = Task407FixedFailure("E407.Runtime.AcceptedPublicationObservationFailed")
        }
    }

    private val initialIngress: ReaderInitialSemanticReceiptIngress = { event, origin ->
        initialIngressCount++
        val step = model.reduceSemanticDestination(event, origin)
        model = step.coordinator
        step.presentationReceipt
    }
    private val installedIngress: ReaderInstalledPageTurnModelIngress = { event, predecessor, origin ->
        // Original owning adapter/controller/progress/WordSync reduction, without generic replay.
        val step = model.onInstalledPageTurnRelocation(event, predecessor, origin)
        model = step.coordinator
        turnIngressCount++
        lastTurnEvent = event
        lastTurnReceipt = step.presentationReceipt
        step.presentationReceipt
    }

    fun mount() {
        startupEvent(Task407StartupPhaseEvent.FIXTURE_MOUNT_ENTERED)
        task407Require(Looper.myLooper() == Looper.getMainLooper(), "A407.Runtime.RealMainLooper")
        task407Require(activity.window.decorView.isHardwareAccelerated, "A407.Runtime.HardwareWindowRequired")
        compose.setContent {
            val controller = model.controller
            val state = controller.state
            CompositionLocalProvider(
                LocalReaderTransitionGateway provides gateway,
                LocalReaderSemanticReceiptIngress provides { _, _ -> legacyIngressCount++; null },
                LocalReaderInitialSemanticReceiptIngress provides initialIngress,
                LocalReaderInstalledPageTurnModelIngress provides installedIngress,
                LocalReaderInstalledPhysicalPublicationIngress provides physicalPublicationIngress
            ) {
                KomikkuReaderNativeFrameHost(
                    navigator = KomikkuReaderNavigator(KomikkuDisabledNavigation()),
                    navigationOverlayVisible = false,
                    chromeOverlayVisible = false,
                    presentationDecision = state.presentationDecision,
                    presentationState = state.presentation,
                    presentationVersion = controller.presentationVersion,
                    presentationShellCoverVisible = false,
                    legacyLiveCompatibilityContext = ReaderLegacyLiveCompatibilityContext.Denied(),
                    presentationEffects = emptyList(),
                    onPresentationEffectHandled = {},
                    onPresentationEvent = { event ->
                        gateway.dispatchBeforeLegacy(null, selectedResult = { null }) {
                            val step = model.onPresentationEvent(event)
                            model = step.coordinator
                            step.presentationReceipt
                        }
                    },
                    destinationCommitIdentity = state.destinationCommitIdentity,
                    shellCoverUrl = null,
                    shellCoverTitle = "Public reader runtime fixture",
                    coverBackdropEnabled = false,
                    viewerKey = ReaderViewerKey(ReaderViewerKind.WebViewPublication, "task407-public-runtime"),
                    grayscaleEnabled = false,
                    invertedColors = false,
                    verticalPageDragPreview = false,
                    pageTurnCanvasEnabled = true,
                    pageTurnReadingDirection = "ltr",
                    pageTurnBitmapQuality = "balanced",
                    pageTurnSnapshotKey = 0,
                    pageTurnContentReadyKey = readerPageTurnContentReadyKey(state.paginationProfile),
                    pageTurnPaginationStatus = state.paginationProfile.status,
                    pageTurnVisualPageIndex = state.chrome.currentLocator?.pageIndex,
                    pageTurnVisualLocationReason = state.chrome.currentLocator?.reason,
                    pageTurnFoliateSessionId = state.foliateSessionId,
                    pageTurnSettlementAck = state.pageTurnSettlementAck,
                    // A407-RUNTIME-WORDSYNC-PAIR remains unresolved. No fake highlight/audio.
                    whispersyncOverlayActive = false,
                    whispersyncAnchorReceipt = null,
                    whispersyncHighlightColorArgb = 0,
                    whispersyncCueMapState = ReaderWhispersyncCueMapState(),
                    onWhispersyncCueMapHoldOutcome = { _, _ -> },
                    onWhispersyncCueMapSeekRequested = {},
                    onStartupShellPrepared = {},
                    onViewerAction = { null },
                    onPageTurnBoundary = { null },
                    onReadableDragPreview = { _, _, _, _, _ -> },
                    onContentLongPress = { _, _, _, _ -> },
                    modifier = Modifier.fillMaxSize(),
                    viewerContent = {
                        remember {
                            constructionCount++
                            constructionInstalled = store().snapshot
                            constructionRuntime = runtime()
                            constructionFinalizationReturned = originalFinalizationReturned()
                            Unit
                        }
                        ReaderEngineWebViewHost(
                            publicationUrl = epub.resource.publicationUrl,
                            title = "Public reader runtime fixture",
                            kind = ReaderPublicationKind.Ebook,
                            mediaOverlayEnabled = false,
                            externalShellCover = false,
                            suppressWebShellCover = false,
                            nativeShellCoverTint = null,
                            settings = defaultReaderSettings().copy(maxColumnCount = 1,
                                fontSizePercent = 160, webContentsDebuggingEnabled = false, keepScreenOn = false),
                            startCfi = null,
                            startHref = null,
                            startProgress = null,
                            onEvent = { event -> model = model.onEngineHostEvent(event).coordinator },
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    composeOverlay = {}
                )
            }
        }
    }

    fun rootOrNull(): View? {
        fun visit(view: View): View? {
            if (view.javaClass.simpleName == "KomikkuReaderNativeFrameRoot") return view
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))?.let { return it }
            return null
        }
        return visit(compose)
    }
    private fun root(): View = task407Required(rootOrNull(), "A407.Root.OriginalRootRequired")
    private fun rootField(name: String): Any? = root().let { exact ->
        exact.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(exact)
    }
    private fun viewer(): ViewGroup = rootField("viewerContainer") as ViewGroup
    private fun viewerField(name: String): Any? = viewer().let { exact ->
        exact.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(exact)
    }
    fun store() = rootField("activatedSessionStore") as ReaderActivatedSessionSnapshotStore
    fun runtime() = rootField("activatedTransitionCoordinator") as? ReaderResumableTransitionCoordinator
    fun executor() = rootField("normalSemanticExecutor") as ReaderSemanticCommandExecutor
    fun requests() = rootField("normalSemanticRequests") as ReaderSemanticExecutableRequestRegistry
    fun material() = viewerField("currentFrameInstallation") as? ReaderFramePhysicalOwnershipAdapter.Lease
    fun curl() = viewerField("playLikeCurlController") as ReaderPlayLikeCurlFoliateController
    fun rasterOwner() = viewerField("pageRasterPreparationController") as ReaderPageRasterPreparationController
    fun probe() = viewerField("ownershipProbe") as ReaderPageOwnershipProbe
    fun passive() = viewerField("passiveRasterPreparationAdapter") as? ReaderPassiveRasterPreparationAdapter

    @Suppress("UNCHECKED_CAST")
    fun passiveRuntime(): ReaderPassiveRasterWebViewHost? {
        val adapter = passive() ?: return null
        val session = adapter.javaClass.getDeclaredField("session").apply { isAccessible = true }
            .get(adapter) as ReaderPassiveRasterPrototypeSession<Bitmap>
        return session.javaClass.getDeclaredField("runtime").apply { isAccessible = true }
            .get(session) as? ReaderPassiveRasterWebViewHost
    }
    private fun originalFinalizationReturned(): Boolean {
        val port = rootField("normalOriginalPort") as? ReaderOriginalLegacyFreezeAndInventoryPort ?: return false
        val exactClass = port.javaClass
        val finished = exactClass.getDeclaredField("finished").apply { isAccessible = true }.get(port) == true
        val busy = exactClass.getDeclaredField("physicalCommandInProgress").apply { isAccessible = true }.get(port) == true
        val checkpoint = exactClass.getDeclaredField("checkpoint").apply { isAccessible = true }.get(port)
        return finished && !busy && checkpoint == null
    }
    fun engine(): ReaderEngineWebView? {
        fun visit(view: View): ReaderEngineWebView? {
            if (view is ReaderEngineWebView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))?.let { return it }
            return null
        }
        return material()?.wrapper?.let(::visit)
    }
    fun journal(): ReaderTransitionJournal {
        val exact = task407Required(runtime(), "A407.Root.OriginalCoordinatorRequired")
        return exact.javaClass.getDeclaredField("journal").apply { isAccessible = true }.get(exact) as ReaderTransitionJournal
    }
    @Suppress("UNCHECKED_CAST")
    fun publication(): ReaderOwnerAndInputPublicationSnapshot? =
        (viewerField("readInstalledPublication") as? (() -> ReaderOwnerAndInputPublicationSnapshot?))?.invoke()
    fun canAcceptPointer(exactRoot: View = root()): Boolean = task407CanAcceptPointer(exactRoot)

    /** Exact keys from original active registrations, never manufactured or pending invocation data.
     * This may return null after lawful work completion. Readable custody below covers that interval.
     */
    fun registeredActiveWork(): ReaderActivatedRasterWork? {
        val active = journal().active ?: return null
        val key = active.ownedResourceKeys.singleOrNull { it.kind == ReaderTransitionResourceKind.Raster } ?: return null
        val work = rasterOwner().installedWorkFor(key) ?: curl().installedWorkFor(key) ?: return null
        return work.takeIf { it.lease.resourceKey == key && it.lease.transitionId == active.id }
    }

    /** Supported post-completion join. The sampled snapshot supplies a known key for READ-ONLY
     * validation only; it cannot authorize publication, ingress, draw or decoration. No producer call.
     */
    fun readableCustody(publication: ReaderOwnerAndInputPublicationSnapshot): Task407ReadableCustody {
        task407Require(Looper.myLooper() == Looper.getMainLooper(), "A407.Custody.OriginalMainLooper")
        val controller = curl()
        val deck = task407Required(controller.installedDeckFor(publication.resource.key), "E407.Custody.OriginalReadableDeck")
        val originalLease = originalDeckOwner(deck)
        task407Require(originalLease.installedDeck === deck, "E407.Custody.SameOriginalDeckLease")
        val work = task407Required(originalLease.installedWork, "E407.Custody.OriginalReadableWork")
        val association = task407Required(controller.installedDecorationAssociationFor(deck), "E407.Custody.OriginalReadableAssociation")
        task407Require(originalLease.installedPlan === association.plan, "E407.Custody.SameOriginalReadablePlan")
        task407Require(deck.resourceKey == publication.resource.key && deck.binding == publication.binding &&
            work.lease.transitionId == deck.transitionId && work.lease.binding == deck.binding &&
            work.lease.allocation == deck.allocation && association.deck == deck &&
            (publication.owner as? ReaderPresentationFrameOwner.NativePage)?.proof == association.displayedProof &&
            controller.installedWorkFor(work.lease.resourceKey) === work,
            "E407.Custody.ExactOriginalPhysicalAndReadableJoin")
        return Task407ReadableCustody(work, association.plan, deck, originalLease)
    }

    /** One bounded source-proven known-key read. No entries/values/cache traversal or mutation. */
    private fun originalDeckOwner(deck: ReaderDeckLease): ReaderDeckPhysicalOwnershipAdapter.Lease {
        task407Require(Looper.myLooper() == Looper.getMainLooper(), "A407.Custody.OriginalMainLooper")
        val controller = curl()
        val physical = controller.javaClass.getDeclaredField("physicalDeckLeases").apply { isAccessible = true }
            .get(controller) as Map<*, *>
        val owner = task407Required(physical[deck.textureGeneration] as? ReaderDeckPhysicalOwnershipAdapter.Lease,
            "E407.Custody.ExactOriginalDeckOwner")
        task407Require(owner.installedDeck === deck, "E407.Custody.SameOriginalDeckLease")
        return owner
    }

    fun requireNoObservationFailure() { observationFailure?.let { throw it.sanitizedCopy() } }
    fun loggerObservationLabels(): List<String> = loggerObservation?.labels().orEmpty()
    fun startupWitnessJson(): String? = loggerObservation?.startupWitnessJson()
    private fun startupEvent(event: Task407StartupPhaseEvent) { loggerObservation?.recordStartupObservation(event) }

    /** Sample only bounded original references/booleans before a producer can lawfully retire.
     * Diagnostic retention never delays close/release and cannot establish a successful boundary. */
    fun observeStartupBoundary() {
        try {
            task407Require(Looper.myLooper() == Looper.getMainLooper(), "A407.Runtime.RealMainLooper")
            loggerObservation?.observeSinkContinuity()
            if (rootOrNull() == null) return
            startupEvent(Task407StartupPhaseEvent.SAMPLE_ROOT_PRESENT)
            val coordinator = runtime() ?: return
            startupEvent(Task407StartupPhaseEvent.SAMPLE_COORDINATOR_PRESENT)
            if (material() != null) startupEvent(Task407StartupPhaseEvent.SAMPLE_MATERIAL_PRESENT)
            if (initialIngressCount > 0) startupEvent(Task407StartupPhaseEvent.SAMPLE_INITIAL_SEMANTIC_INGRESS)
            if (coordinator.snapshot().activeTransitionsRegistered > 0)
                startupEvent(Task407StartupPhaseEvent.SAMPLE_TRANSITION_REGISTERED)
            val journalSample = journal()
            if (journalSample.active != null) startupEvent(Task407StartupPhaseEvent.SAMPLE_TRANSITION_ACTIVE)
            if ((journalSample.lastOutcome as? ReaderTransitionOutcome.Failed)?.reason ==
                ReaderTransitionFailureReason.MaterialTimeout)
                startupEvent(Task407StartupPhaseEvent.SAMPLE_TERMINAL_MATERIAL_TIMEOUT)
            // Failure-only sampling: the synchronous observer can affect timing. A positive label
            // means only this current observer recorded MaterialTimeout before any generic ACK;
            // it neither matches an ACK identity nor reconstructs earlier r105 chronology.
            if (!observedMaterialTimeoutBeforeAnyEngineAck &&
                (journal().lastOutcome as? ReaderTransitionOutcome.Failed)?.reason ==
                    ReaderTransitionFailureReason.MaterialTimeout &&
                loggerObservation?.commandAcknowledgementSeenOrNull() == false) {
                observedMaterialTimeoutBeforeAnyEngineAck = true
                val labels = loggerObservation.labels()
                if ("E407.Observation.EngineOpenPublicationDispatched" !in labels)
                    observedMaterialTimeoutBeforeEngineOpenDispatch = true
                if ("E407.Observation.EngineBridgeReady" !in labels)
                    observedMaterialTimeoutBeforeEngineReady = true
            }
            registeredActiveWork()?.let { work ->
                observedStartupWork = work
                startupEvent(Task407StartupPhaseEvent.SAMPLE_RASTER_WORK_REGISTERED)
                when (work.preparationFacts?.phase) {
                    ReaderPagePreparationPhase.Preparing -> startupEvent(Task407StartupPhaseEvent.SAMPLE_RASTER_PREPARING)
                    ReaderPagePreparationPhase.Ready -> startupEvent(Task407StartupPhaseEvent.SAMPLE_RASTER_READY)
                    ReaderPagePreparationPhase.Failed -> startupEvent(Task407StartupPhaseEvent.SAMPLE_RASTER_FAILED)
                    else -> Unit
                }
            }
            engine()?.let { original ->
                startupEvent(Task407StartupPhaseEvent.SAMPLE_ENGINE_PRESENT)
                val initialized = original.runtimeInitializationCompleted
                if (initialized) startupEvent(Task407StartupPhaseEvent.SAMPLE_ENGINE_INITIALIZATION_COMPLETED)
                val entrypoint = original.url == ReaderWebRuntime.entrypointUrl
                if (entrypoint) startupEvent(Task407StartupPhaseEvent.SAMPLE_ENGINE_ENTRYPOINT)
                val attached = original.isAttachedToWindow
                if (attached) startupEvent(Task407StartupPhaseEvent.SAMPLE_ENGINE_ATTACHED)
                val sized = original.width > 0 && original.height > 0
                if (sized) startupEvent(Task407StartupPhaseEvent.SAMPLE_ENGINE_SIZED)
                val complete = original.progress == 100
                if (complete) startupEvent(Task407StartupPhaseEvent.SAMPLE_ENGINE_PROGRESS_COMPLETE)
                val retired = original.runtimeRetired
                if (retired) startupEvent(Task407StartupPhaseEvent.SAMPLE_ENGINE_RETIRED)
                observedStartupEngineRuntimeInitializationCompleted =
                    observedStartupEngineRuntimeInitializationCompleted || initialized
                observedStartupEngineAtEntrypoint = observedStartupEngineAtEntrypoint || entrypoint
                observedStartupEngineAttached = observedStartupEngineAttached || attached
                observedStartupEngineSized = observedStartupEngineSized || sized
                observedStartupEngineProgressComplete = observedStartupEngineProgressComplete || complete
                observedStartupEngineRuntimeRetired = observedStartupEngineRuntimeRetired || retired
            }
            val adapter = passive()
            passiveRuntime()?.let { original ->
                observedStartupRuntime = original
                val ready = original.isReady
                if (ready) startupEvent(Task407StartupPhaseEvent.SAMPLE_PASSIVE_RUNTIME_READY)
                observedStartupRuntimeReady = observedStartupRuntimeReady || ready
                val type = original.javaClass
                observedStartupJavascriptReady = observedStartupJavascriptReady ||
                    type.getDeclaredField("runtimeReady").apply { isAccessible = true }.get(original) == true
                val webView = type.getDeclaredField("webView").apply { isAccessible = true }.get(original) as View
                observedStartupAttachment = observedStartupAttachment || webView.isAttachedToWindow
            }
            if (adapter != null) {
                val type = adapter.javaClass
                val session = type.getDeclaredField("session").apply { isAccessible = true }
                    .get(adapter) as ReaderPassiveRasterPrototypeSession<Bitmap>
                val readiness = type.getDeclaredField("installedReadiness").apply { isAccessible = true }
                    .get(adapter)
                observedStartupReadinessPresent = observedStartupReadinessPresent || readiness != null
                if (readiness != null) {
                    observedStartupReadinessDelivered = observedStartupReadinessDelivered ||
                        readiness.javaClass.getDeclaredField("delivered").apply { isAccessible = true }
                            .get(readiness) == true
                }
                val batch = type.getDeclaredField("activeBatch").apply { isAccessible = true }.get(adapter)
                observedStartupBatchPresent = observedStartupBatchPresent || batch != null
                if (batch != null) {
                    val targetIndex = batch.javaClass.getDeclaredField("targetIndex").apply { isAccessible = true }
                        .get(batch) as Int
                    observedStartupBatchAtFirstTarget = observedStartupBatchAtFirstTarget || targetIndex == 0
                    observedStartupBatchPastFirstTarget = observedStartupBatchPastFirstTarget || targetIndex > 0
                }
                val metrics = session.metrics()
                observedStartupSessionCaptureAttempt = observedStartupSessionCaptureAttempt || metrics.captureAttempts > 0
                observedStartupSessionCaptureFailure = observedStartupSessionCaptureFailure || metrics.captureFailures > 0
                observedStartupSessionActiveCapture = observedStartupSessionActiveCapture || metrics.activeCaptures > 0
            }
            val construction = viewerField("initialDestinationConstruction")
            if (construction != null) {
                val type = construction.javaClass
                val mounted = type.getDeclaredField("mounted").apply { isAccessible = true }.get(construction) == true
                if (mounted) startupEvent(Task407StartupPhaseEvent.SAMPLE_CONSTRUCTION_MOUNTED)
                observedStartupConstructionMounted = observedStartupConstructionMounted || mounted
                val bound = type.getDeclaredField("executorBound").apply { isAccessible = true }.get(construction) == true
                if (bound) startupEvent(Task407StartupPhaseEvent.SAMPLE_CONSTRUCTION_EXECUTOR_BOUND)
                observedStartupConstructionExecutorBound = observedStartupConstructionExecutorBound || bound
                when (type.getDeclaredField("phase").apply { isAccessible = true }.get(construction).javaClass.simpleName) {
                    "Waiting" -> {
                        startupEvent(Task407StartupPhaseEvent.SAMPLE_CONSTRUCTION_WAITING)
                        observedStartupConstructionWaiting = true
                    }
                    "Starting" -> {
                        startupEvent(Task407StartupPhaseEvent.SAMPLE_CONSTRUCTION_STARTING)
                        observedStartupConstructionStarting = true
                    }
                    "Query" -> {
                        startupEvent(Task407StartupPhaseEvent.SAMPLE_CONSTRUCTION_QUERY)
                        observedStartupConstructionQuery = true
                    }
                    "Retired" -> {
                        startupEvent(Task407StartupPhaseEvent.SAMPLE_CONSTRUCTION_RETIRED)
                        observedStartupConstructionRetired = true
                    }
                }
            }
        } catch (_: Throwable) {
            startupObservationUnavailable = true
            loggerObservation?.observationGap()
        }
    }

    /** Failure-only, read-only boundary observations. Presence labels grant no readiness, identity,
     * resource or publication authority; no producer is invoked and no payload is inspected. */
    fun startupObservationLabels(): List<String> = buildList {
        task407Require(Looper.myLooper() == Looper.getMainLooper(), "A407.Runtime.RealMainLooper")
        val exactRoot = rootOrNull() ?: return@buildList
        add("E407.Observation.RootPresent")
        if (store().snapshot != null) add("E407.Observation.InstallationPresent")
        val coordinator = runtime() ?: return@buildList
        add("E407.Observation.CoordinatorPresent")
        if (material() != null) add("E407.Observation.MaterialPresent")
        if (engine() != null) add("E407.Observation.EnginePresent")
        if (initialIngressCount > 0) add("E407.Observation.InitialSemanticIngressObserved")
        if (material()?.initialResolvedLayout != null) add("E407.Observation.InitialResolvedLayoutPresent")
        if (viewer().context is android.app.Activity) add("E407.Observation.ViewerContextIsActivity")
        if (startupObservationUnavailable) add("E407.Observation.EarlyObservationUnavailable")
        if (observedMaterialTimeoutBeforeAnyEngineAck)
            add("E407.Observation.MaterialTimeoutObservedBeforeAnyEngineAck")
        if (observedMaterialTimeoutBeforeEngineOpenDispatch)
            add("E407.Observation.MaterialTimeoutObservedBeforeEngineOpenDispatch")
        if (observedMaterialTimeoutBeforeEngineReady)
            add("E407.Observation.MaterialTimeoutObservedBeforeEngineReady")
        if (observedStartupEngineRuntimeInitializationCompleted)
            add("E407.Observation.EngineRuntimeInitializationCompletedObserved")
        if (observedStartupEngineAtEntrypoint) add("E407.Observation.EngineEntrypointUrlObserved")
        if (observedStartupEngineAttached) add("E407.Observation.EngineAttachmentObserved")
        if (observedStartupEngineSized) add("E407.Observation.EngineSizedObserved")
        if (observedStartupEngineProgressComplete) add("E407.Observation.EngineProgressCompleteObserved")
        if (observedStartupEngineRuntimeRetired) add("E407.Observation.EngineRuntimeRetiredObserved")
        if (observedStartupConstructionMounted) add("E407.Observation.InitialConstructionMountedObserved")
        if (observedStartupConstructionExecutorBound) add("E407.Observation.InitialConstructionExecutorBoundObserved")
        if (observedStartupConstructionWaiting) add("E407.Observation.InitialConstructionWaitingObserved")
        if (observedStartupConstructionStarting) add("E407.Observation.InitialConstructionStartingObserved")
        if (observedStartupConstructionQuery) add("E407.Observation.InitialConstructionQueryObserved")
        if (observedStartupConstructionRetired) add("E407.Observation.InitialConstructionRetiredObserved")
        if (observedStartupRuntimeReady) add("E407.Observation.PassiveRuntimeReadyObserved")
        if (observedStartupJavascriptReady) add("E407.Observation.PassiveJavascriptReadyObserved")
        if (observedStartupAttachment) add("E407.Observation.PassiveAttachmentObserved")
        if (observedStartupReadinessPresent) add("E407.Observation.PassiveReadinessPresent")
        if (observedStartupReadinessDelivered) add("E407.Observation.PassiveReadinessDelivered")
        if (observedStartupBatchPresent) add("E407.Observation.PassiveBatchPresent")
        if (observedStartupBatchAtFirstTarget) add("E407.Observation.PassiveBatchAtFirstTarget")
        if (observedStartupBatchPastFirstTarget) add("E407.Observation.PassiveBatchPastFirstTarget")
        if (observedStartupSessionCaptureAttempt) add("E407.Observation.PassiveSessionCaptureAttemptObserved")
        if (observedStartupSessionCaptureFailure) add("E407.Observation.PassiveSessionCaptureFailureObserved")
        if (observedStartupSessionActiveCapture) add("E407.Observation.PassiveSessionActiveCaptureObserved")
        observedStartupRuntime?.let { original ->
            add("E407.Observation.PassiveRuntimeObserved")
            if (original.isRetired) add("E407.Observation.ObservedPassiveRuntimeRetired")
            val capture = original.captureMetrics()
            if (capture.captureRequests > 0) add("E407.Observation.CaptureRequestObserved")
            if (capture.preconditionFailures > 0) add("E407.Observation.CapturePreconditionFailureObserved")
            if (capture.pixelCopyAttempts > 0) add("E407.Observation.PixelCopyAttemptObserved")
            if (capture.pixelCopySuccesses > 0) add("E407.Observation.PixelCopySuccessObserved")
            if (capture.pixelCopyFailures > 0) add("E407.Observation.PixelCopyFailureObserved")
        }
        observedStartupWork?.let { original ->
            add("E407.Observation.RasterWorkObserved")
            if (original.preparationFacts?.phase == ReaderPagePreparationPhase.Ready)
                add("E407.Observation.ObservedRasterTargetsReady")
        }
        when (val outcome = journal().lastOutcome) {
            is ReaderTransitionOutcome.Failed -> add(when (outcome.reason) {
                ReaderTransitionFailureReason.PortRejected -> "E407.Observation.TerminalPortRejected"
                ReaderTransitionFailureReason.MaterialTimeout -> "E407.Observation.TerminalMaterialTimeout"
                ReaderTransitionFailureReason.MaterialAllocationRejected -> "E407.Observation.TerminalMaterialAllocationRejected"
                ReaderTransitionFailureReason.AtomicPublicationRejected -> "E407.Observation.TerminalAtomicPublicationRejected"
                else -> "E407.Observation.TerminalOtherFailure"
            })
            is ReaderTransitionOutcome.Cancelled -> add("E407.Observation.TerminalCancelled")
            is ReaderTransitionOutcome.Deferred -> add("E407.Observation.TerminalDeferred")
            is ReaderTransitionOutcome.Succeeded -> add("E407.Observation.TerminalSucceeded")
            null -> Unit
        }
        val passiveRuntime = passiveRuntime()
        if (passiveRuntime != null) {
            add("E407.Observation.PassiveRuntimePresent")
            if (passiveRuntime.isReady) add("E407.Observation.PassiveRuntimeReady")
            val type = passiveRuntime.javaClass
            if (type.getDeclaredField("runtimeReady").apply { isAccessible = true }.get(passiveRuntime) == true)
                add("E407.Observation.PassiveJavascriptReady")
            val webView = type.getDeclaredField("webView").apply { isAccessible = true }.get(passiveRuntime) as View
            if (webView.isAttachedToWindow) add("E407.Observation.PassiveWebViewAttached")
            val capture = passiveRuntime.captureMetrics()
            if (capture.captureRequests > 0) add("E407.Observation.CaptureRequested")
            if (capture.pixelCopyAttempts > 0) add("E407.Observation.PixelCopyAttempted")
            if (capture.pixelCopySuccesses > 0) add("E407.Observation.PixelCopySucceeded")
        }
        if (passive()?.isAvailable == true) add("E407.Observation.PassiveAdapterAvailable")
        val active = journal().active
        if (active != null) {
            add("E407.Observation.TransitionActive")
            val work = registeredActiveWork()
            if (work != null) add("E407.Observation.RasterWorkPresent")
            if (work?.isCurrent() == true) add("E407.Observation.RasterWorkCurrent")
            if (work?.preparationFacts?.phase == ReaderPagePreparationPhase.Ready)
                add("E407.Observation.RasterTargetsReady")
            if (active.admittedDeckKey != null) add("E407.Observation.DeckAdmitted")
            if (active.pendingPreparedDeckKey != null) add("E407.Observation.DeckPreparedPending")
            if (active.frameTarget != null) add("E407.Observation.FrameTargetPresent")
            if (active.preparedFrameOwner != null) add("E407.Observation.PreparedFrameOwnerPresent")
        }
        if (journal().retryableTransition != null) add("E407.Observation.RetryableTransitionPresent")
        if (journal().lastOutcome != null) add("E407.Observation.TerminalOutcomePresent")
        if (publication() != null) add("E407.Observation.PublicationPresent")
        if (canAcceptPointer(exactRoot)) add("E407.Observation.PointerGranted")
        val surface = curl().surfaceView
        if (surface.isAttachedToWindow) add("E407.Observation.CurlSurfaceAttached")
        if (surface.width > 0 && surface.height > 0) add("E407.Observation.CurlSurfaceSized")
        if (surface.alpha > 0f) add("E407.Observation.CurlSurfaceNonzeroAlpha")
        add("E407.Observation.Completed")
    }

    /** Checks the ACTUAL bound owning callback reference, not symbol presence. */
    fun requireInstalledPageTurnModelIngressBinding() {
        task407Require(viewerField("installedPageTurnModelIngress") === installedIngress,
            "E407.OwningRoot.OriginalInstalledIngressActuallyBound")
    }

    /** Checks the ACTUAL bound callback reference, not reflective symbol discovery. */
    fun requireAuthenticatedPublicationObservationBinding() {
        requireNoObservationFailure()
        task407Require(viewerField("installedPhysicalPublicationIngress") === physicalPublicationIngress,
            "A407.Runtime.OriginalPhysicalPublicationIngressBound")
    }

    private fun captureAcceptedPhysicalPublication(command: ReaderTransitionCommand,
        applied: ReaderOwnerAndInputPublicationResult.Applied) {
        task407Require(Looper.myLooper() == Looper.getMainLooper(), "A407.Publication.OriginalMainLooper")
        val resource = when (command) {
            is ReaderTransitionCommand.CommitOwnerAndInputLease -> command.preparedFrameResource
            is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease -> command.retainedResource
            else -> throw Task407FixedFailure("E407.Publication.OriginalPhysicalCommandRequired")
        }
        val publication = task407Required(publication(), "E407.Publication.OriginalCommittedBarrier")
        task407Require(applied.transitionId === command.transitionId &&
            publication.owner === applied.publishedOwner && publication.binding === applied.publishedBinding &&
            publication.lease === applied.finalPhysicalInputLease && publication.resource === resource,
            "E407.Publication.ExactOriginalAcceptedCommandAndBarrier")
        task407Require(owningController.state.presentationDecision.frameOwner == applied.publishedOwner &&
            owningController.state.presentation.binding === applied.publishedBinding &&
            owningController.state.presentation.installedPhysicalInputLease === applied.finalPhysicalInputLease,
            "E407.Publication.PureOwningProjectionOfOriginalAcceptedPublication")
        when (command) {
            is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease -> {
                task407Require(applied.publicationIdentity == command.publicationIdentity &&
                    (applied.subject as? ReaderOwnerAndInputPublicationSubject.Retained)?.retainedResource === resource,
                    "E407.Publication.ExactOriginalRetainedSubject")
                retainedPublicationCount++
            }
            is ReaderTransitionCommand.CommitOwnerAndInputLease -> {
                val subject = task407Required(applied.subject as? ReaderOwnerAndInputPublicationSubject.Successor,
                    "E407.Publication.OriginalSuccessorSubject")
                task407Require(subject.targetHandle === command.targetHandle && subject.preparedFrameResource === resource &&
                    applied.publicationIdentity == command.publicationIdentity,
                    "E407.Publication.ExactOriginalTargetHandleAndResource")
                val committed = task407Required(journal().committed as? ReaderCommittedPresentation.Transition,
                    "E407.Publication.OriginalAcceptedJournalCommit").committed
                task407Require(committed.id === command.transitionId && committed.owner === applied.publishedOwner &&
                    committed.binding === applied.publishedBinding && committed.resourceRegistration === resource,
                    "E407.Publication.OriginalJournalAcceptsOriginalCommand")
                when (command.transitionId.operation) {
                    ReaderTransitionOperation.BootstrapNativePage -> {
                        bootstrapPublicationCount++
                        task407Require(bootstrapPublicationCount == 1, "E407.Publication.BootstrapPublishedOnce")
                    }
                    ReaderTransitionOperation.CurlClaimAndSettlement -> {
                        turnPublicationCount++
                        val admission = task407Required(turnAdmission, "E407.Turn.OriginalDownMoveAdmissionRequired")
                        task407Require(acceptedTurnCustody.size < 2 && acceptedTurnCustody.none {
                            it.command.transitionId == command.transitionId || it.command.publicationIdentity == command.publicationIdentity
                        }, "E407.Turn.OneOriginalPublicationPerAdmittedTransition")
                        acceptedTurnCustody += captureAcceptedTurnCustody(admission, command, applied, publication, committed)
                        turnAdmission = null
                    }
                    else -> throw Task407FixedFailure("E407.Publication.UnexpectedPhysicalSuccessor")
                }
            }
            else -> throw Task407FixedFailure("E407.Publication.OriginalPhysicalCommandRequired")
        }
    }

    private fun captureAcceptedTurnCustody(admission: Task407TurnAdmission,
        command: ReaderTransitionCommand.CommitOwnerAndInputLease,
        applied: ReaderOwnerAndInputPublicationResult.Applied,
        publication: ReaderOwnerAndInputPublicationSnapshot,
        committed: ReaderCommittedTransition): Task407AcceptedTurnCustody {
        val source = admission.custody
        val owner = originalDeckOwner(source.deck)
        val gesture = task407Required(owner.installedGesture, "E407.Turn.OriginalPreReleaseGestureCustody")
        val terminal = task407Required(gesture.terminal, "E407.Turn.OriginalPreReleaseTerminalCustody")
        val completion = task407Required(terminal.rendererCompletion, "E407.Turn.OriginalRendererCompletion")
        val semantic = task407Required(gesture.semanticReceipt, "E407.Turn.OriginalSourceSemanticReceipt")
        val relocated = task407Required(semantic.event as? ReaderPresentationEvent.FoliateRelocated,
            "E407.Turn.OriginalRelocationAcknowledgement")
        val settlement = task407Required(relocated.acknowledgement, "E407.Turn.OriginalOwningSettlementAcknowledgement")
        val semanticFact = task407Required(semantic.semanticReceipt, "E407.Turn.OriginalCommandSemanticFact")
        val target = readableCustody(publication)
        val event = task407Required(lastTurnEvent, "E407.Turn.RealFoliateRelocation")
        val expected = task407Required(command.transitionId.expectedBinding as? ReaderExpectedPresentationBinding.SemanticSuccessor,
            "E407.Turn.OriginalSemanticSuccessorDemand")
        val outcome = task407Required(journal().lastOutcome as? ReaderTransitionOutcome.Succeeded,
            "E407.Turn.OriginalAcceptedJournalSuccess")
        val native = task407Required(applied.publishedOwner as? ReaderPresentationFrameOwner.NativePage,
            "E407.Turn.OriginalDrawnNativeSuccessor")
        val input = task407Required(applied.finalPhysicalInputLease as? ReaderTransitionInputLease.NativePage,
            "E407.Turn.OriginalAtomicNativeInput")
        task407Require(owner === source.physicalOwner && gesture === admission.gesture &&
            gesture.source === admission.source && gesture.source.deck === source.deck && gesture.source.work === source.work &&
            !owner.deckReleaseRequested && owner.installedRendererOwned && !owner.installedRendererReturned.isCompleted &&
            gesture.started && gesture.rendererCommandIssued && gesture.semanticSettlementDelivered &&
            source.physicalOwner.installedPlan === source.plan && source.physicalOwner.installedWork === source.work &&
            expected.predecessor == admission.publication.binding && source.deck.resourceKey == admission.publication.resource.key &&
            command.transitionId.operation == ReaderTransitionOperation.CurlClaimAndSettlement &&
            source.deck.resourceKey != target.deck.resourceKey && source.work !== target.work && source.deck !== target.deck &&
            admission.transitionId === command.transitionId && semantic === lastTurnReceipt &&
            semantic.disposition == ReaderPresentationEventDisposition.Accepted &&
            semantic.semanticReceipt?.transitionId == command.transitionId &&
            terminal.source === gesture.source && terminal.stage == ReaderInstalledCurlSettlementStage.Completed &&
            completion.gestureId == gesture.source.intent.gestureId.value && completion.sequence > 0L &&
            completion.sourceGenerationId == source.deck.textureGeneration &&
            terminal.pageChange == PageChange.NEXT && completion.pageChange == terminal.pageChange &&
            admission.source.intent.direction == ReaderPageTurnDirection.Next &&
            completion.rendererGenerationAtStart == completion.rendererGenerationAtCompletion &&
            completion.rendererGenerationAtCompletion == curl().surfaceView.rendererGeneration &&
            curl().surfaceView.isCurlSettlementCompletionCurrent(completion) &&
            completion.currentOrdinal == settlement.pageIndex && terminal.settledOrdinal == settlement.pageIndex &&
            settlement.foliateSessionId == source.deck.binding.foliateSessionId &&
            settlement.rasterGeneration == source.deck.rasterGeneration && settlement.textureGeneration == source.deck.textureGeneration &&
            target.work.lease.transitionId == command.transitionId && target.deck.binding === command.binding &&
            target.deck.transitionRole == ReaderTransitionDeckRole.Settlement &&
            target.deck.allocation == target.work.lease.allocation && target.work.lease.allocation?.allocatedBinding == command.binding &&
            !target.physicalOwner.deckReleaseRequested && target.physicalOwner.installedRendererOwned &&
            !target.physicalOwner.installedRendererReturned.isCompleted &&
            target.physicalOwner.installedDisplayedProof === native.proof && native.proof.presentedFrame > 0L &&
            native.proof.binding === command.binding && native.proof.textureGeneration == target.deck.textureGeneration &&
            native.proof.rasterGeneration == target.deck.rasterGeneration &&
            input.binding == command.binding && input.textureGeneration == target.deck.textureGeneration &&
            semanticFact.binding == relocated.binding &&
            semanticFact.binding.destinationCommitIdentity == command.binding.destinationCommitIdentity &&
            event.destinationCommitIdentity == command.binding.destinationCommitIdentity &&
            event.pageTurnSettleToken == settlement.token && event.locator.pageIndex == settlement.pageIndex &&
            event.pageTurnSettleSessionId == settlement.foliateSessionId &&
            event.pageTurnSettleRasterGeneration == settlement.rasterGeneration &&
            event.pageTurnSettleTextureGeneration == settlement.textureGeneration &&
            settlement.pageIndex == source.plan.centerPageIndex + 1 && target.plan.centerPageIndex == settlement.pageIndex &&
            target.plan.resolvedLayout?.pageIndex == settlement.pageIndex &&
            committed.id === command.transitionId && committed.owner === applied.publishedOwner &&
            committed.binding === command.binding && committed.resourceRegistration === command.preparedFrameResource &&
            outcome.committedOwner === committed.owner && outcome.binding === committed.binding,
            "E407.Turn.ExactPreReleaseSourceTargetTerminalAndSemanticJoin")
        // DRAW provenance is the original typed producer -> publisher -> journal -> accepted
        // command chain, not a newly built FrameTarget/specification or a sampled owner. The
        // publisher and journal reject ordinary Native Curl targets; this authenticated original
        // callback cannot run before matching typed terminal DRAW and exact Applied acceptance.
        return Task407AcceptedTurnCustody(command, applied, admission, target, terminal, semantic, settlement,
            event, publication, committed, outcome)
    }

    fun acceptedTurnCustodyOrNull(transitionId: ReaderTransitionId): Task407AcceptedTurnCustody? {
        requireNoObservationFailure()
        return acceptedTurnCustody.singleOrNull { it.command.transitionId == transitionId }
    }

    fun requireOriginalAcceptedTurnEvidence(transitionId: ReaderTransitionId): Task407AcceptedTurnEvidence {
        task407Require(Looper.myLooper() == Looper.getMainLooper(), "A407.Custody.OriginalMainLooper")
        requireAuthenticatedPublicationObservationBinding()
        val custody = task407Required(acceptedTurnCustodyOrNull(transitionId),
            "E407.Turn.AuthenticatedPreReleaseCustodyRequired")
        val subject = task407Required(custody.applied.subject as? ReaderOwnerAndInputPublicationSubject.Successor,
            "E407.Turn.OriginalAcceptedSuccessorSubject")
        task407Require(custody.command.transitionId === custody.admission.transitionId &&
            custody.applied.transitionId === custody.command.transitionId &&
            custody.committed.id === custody.command.transitionId &&
            subject.targetHandle === custody.command.targetHandle &&
            subject.preparedFrameResource === custody.command.preparedFrameResource &&
            custody.applied.publicationIdentity == custody.command.publicationIdentity &&
            custody.publication.owner === custody.applied.publishedOwner &&
            custody.publication.binding === custody.applied.publishedBinding &&
            custody.publication.resource === custody.command.preparedFrameResource &&
            custody.publication.lease === custody.applied.finalPhysicalInputLease,
            "E407.Turn.OriginalAcceptedEvidenceCustodyUnchanged")
        // This reads retained TEST-owned original custody only. Source physical owners can already
        // have lawfully returned; neither postterminal active state nor a surviving target is needed.
        return Task407AcceptedTurnEvidence(custody)
    }

    fun owners(): Task407OriginalOwners = Task407OriginalOwners(root(),
        task407Required(store().snapshot, "A407.Root.OriginalInstallationRequired"),
        task407Required(runtime(), "A407.Root.OriginalCoordinatorRequired"),
        task407Required(material(), "A407.Root.OriginalMaterialRequired"),
        task407Required(engine(), "A407.Root.OriginalEngineRequired"), curl(), rasterOwner(), probe(),
        task407Required(passiveRuntime(), "A407.Root.OriginalPassiveRuntimeRequired"), executor(), requests())
    fun requireSameOwners(original: Task407OriginalOwners) {
        task407Require(root() === original.root && store().snapshot === original.installed &&
            runtime() === original.runtime && material() === original.material && engine() === original.engine &&
            curl() === original.curl && rasterOwner() === original.rasterOwner && passiveRuntime() === original.passiveRuntime,
            "E407.ContinuousRoot.OriginalPhysicalOwnersRetained")
        task407Require(original.installed.portAuthority is ReaderActivatedSessionPortAuthority.Production &&
            store().atomicWriteCount == 1 && constructionCount == 1 && constructionFinalizationReturned &&
            constructionInstalled === original.installed && constructionRuntime === original.runtime,
            "A407.Root.SingleOriginalProductionInstallation")
        task407Require(task407EngineDestructionConsumer(original.engine) != null,
            "A407.Root.OriginalEngineDestructionConsumerBound")
    }
    fun displayed(): ReaderOwnerAndInputPublicationSnapshot? {
        requireNoObservationFailure()
        val publication = publication() ?: return null
        if (publication.owner !is ReaderPresentationFrameOwner.NativePage ||
            publication.lease !is ReaderTransitionInputLease.NativePage || !canAcceptPointer()) return null
        if (runtime()?.snapshot()?.activeOperation != null) return null
        return publication
    }
    fun requireDisplayed(publication: ReaderOwnerAndInputPublicationSnapshot) {
        val owner = publication.owner as? ReaderPresentationFrameOwner.NativePage
        val lease = publication.lease as? ReaderTransitionInputLease.NativePage
        task407Require(owner != null && lease != null && lease.binding == publication.binding &&
            lease.textureGeneration == publication.binding.textureGeneration && canAcceptPointer(),
            "E407.Display.AtomicNativeOwnerAndInput")
        readableCustody(publication)
        requireNoObservationFailure()
        task407Require(owningController.state.presentationDecision.frameOwner == publication.owner &&
            owningController.state.presentation.binding === publication.binding &&
            owningController.state.presentation.installedPhysicalInputLease === publication.lease,
            "E407.Display.CommonProjectionOfOriginalAtomicPublication")
        task407Require(runtime()?.snapshot()?.activeOperation == null && executor().activeSlotCount == 0 &&
            requests().activeHandleCount == 0, "E407.Display.OriginalTransitionTerminal")
    }
    fun pointerTurn(source: ReaderOwnerAndInputPublicationSnapshot): ReaderTransitionId {
        requireNoObservationFailure()
        requireDisplayed(source)
        task407Require(publication() === source && turnAdmission == null,
            "A407.Pointer.ExactOriginalSourceAndNoPendingObservation")
        val sourceCustody = readableCustody(source)
        task407Require(canAcceptPointer(), "E407.ContinuousRoot.ImmediatePointerGranted")
        val exactRoot = root()
        task407Require(exactRoot.width > 0 && exactRoot.height > 0 && viewerField("pageTurnCanvasEnabled") == true &&
            viewerField("verticalPageDragPreview") == false, "A407.Pointer.OriginalCanvasRouting")
        val downTime = SystemClock.uptimeMillis()
        pointer(exactRoot, downTime, MotionEvent.ACTION_DOWN, 0.85f)
        task407Require(viewerField("physicalDispatchMode") == ReaderPagePhysicalDispatchMode.PlayLikeCurl,
            "A407.Pointer.DownUsesOriginalCurl")
        pointer(exactRoot, downTime, MotionEvent.ACTION_MOVE, 0.15f)
        task407Require(viewerField("physicalDispatchMode") == ReaderPagePhysicalDispatchMode.PlayLikeCurl,
            "A407.Pointer.MoveUsesOriginalCurl")
        val active = task407Required(journal().active, "A407.Pointer.OriginalMoveAdmittedTransition")
        val admitted = active.id
        task407Require(admitted.operation == ReaderTransitionOperation.CurlClaimAndSettlement &&
            active.predecessorResourceKey == source.resource.key,
            "A407.Pointer.OriginalInstalledCurlTransition")
        val intent = task407Required(active.semanticIntent as? ReaderPageTurnIntent, "A407.Pointer.OriginalMoveSemanticIntent")
        val originalSource = task407Required(curl().originalInstalledCurlGestureSource(sourceCustody.deck, intent),
            "A407.Pointer.OriginalDownMoveSource")
        val gesture = task407Required(sourceCustody.physicalOwner.installedGesture, "A407.Pointer.OriginalDownMoveGesture")
        task407Require(gesture.source === originalSource && originalSource.work === sourceCustody.work &&
            originalSource.deck === sourceCustody.deck && originalSource.intent === intent,
            "A407.Pointer.ExactOriginalDownMoveAdmissionBeforeUp")
        // Admission metadata is NOT publication/settlement authority. Capture before terminal UP
        // can execute renderer/semantic callbacks; never retain or delay their physical release.
        turnAdmission = Task407TurnAdmission(admitted, source, sourceCustody, originalSource, gesture)
        pointer(exactRoot, downTime, MotionEvent.ACTION_UP, 0.15f)
        requireNoObservationFailure()
        return admitted
    }
    fun immediateCancelledPointer(): ReaderTransitionId {
        requireNoObservationFailure()
        task407Require(canAcceptPointer(), "C407.PointerCancel.ImmediateNextActionGranted")
        val exactRoot = root()
        val downTime = SystemClock.uptimeMillis()
        pointer(exactRoot, downTime, MotionEvent.ACTION_DOWN, 0.85f)
        task407Require(viewerField("physicalDispatchMode") == ReaderPagePhysicalDispatchMode.PlayLikeCurl,
            "C407.PointerCancel.OriginalCurlOwnsNextAction")
        // Original admission is directional ClaimCurl on MOVE, not the retained content DOWN.
        pointer(exactRoot, downTime, MotionEvent.ACTION_MOVE, 0.65f)
        val id = task407Required(journal().active, "C407.PointerCancel.OriginalTransitionAdmitted").id
        task407Require(id.operation == ReaderTransitionOperation.CurlClaimAndSettlement,
            "C407.PointerCancel.OriginalInstalledCurlTransition")
        pointer(exactRoot, downTime, MotionEvent.ACTION_CANCEL, 0.65f)
        return id
    }
    private fun pointer(exactRoot: View, downTime: Long, action: Int, xFraction: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
            exactRoot.width * xFraction, exactRoot.height * 0.5f, 0)
        try { task407Require(exactRoot.dispatchTouchEvent(event), "A407.Pointer.OriginalRootHandlesPhysicalEvent") }
        finally { event.recycle() }
    }

    /** Capture original physical refs, including partial startup, not only successful test evidence.
     * Retention is cleanup custody, never proof of returned ownership. No registry/cache enumeration.
     */
    fun captureCleanupCustody(): Task407OriginalCleanupCustody? {
        originalCleanupCustody?.let { return it }
        val exactRoot = rootOrNull() ?: return null
        return Task407OriginalCleanupCustody(exactRoot, runtime(), material(), engine(), curl(), rasterOwner(),
            passiveRuntime(), probe(), rootField("normalSemanticExecutor") as? ReaderSemanticCommandExecutor,
            rootField("normalSemanticRequests") as? ReaderSemanticExecutableRequestRegistry)
            .also { originalCleanupCustody = it }
    }
    fun closeComposition() {
        if (compositionClosed) return
        task407Require(!compositionCloseAttempted, "C407.Close.PreviousDisposalUnreturned")
        compositionCloseAttempted = true // Attempt marker, NOT a disposal acknowledgement.
        val failures = Task407FailureAccount()
        var disposed = false
        var detached = false
        failures.attempt("C407.Close.OriginalCustodyCaptureFailed") { captureCleanupCustody() }
        failures.attempt("C407.Close.CompositionDisposeFailed") { compose.disposeComposition(); disposed = true }
        failures.attempt("C407.Close.ComposeRemovalFailed") {
            (compose.parent as? ViewGroup)?.removeView(compose)
            detached = compose.parent == null
            task407Require(detached, "C407.Close.ComposeRemovalUnreturned")
        }
        compositionClosed = disposed && detached
        failures.throwIfAny()
    }
    fun requirePhysicalClose(original: Task407OriginalOwners, works: List<ReaderActivatedRasterWork>,
        publications: List<ReaderOwnerAndInputPublicationSnapshot>): Boolean {
        val custody = task407Required(originalCleanupCustody, "C407.Close.OriginalCleanupCustodyRequired")
        task407Require(custody.root === original.root && custody.runtime === original.runtime &&
            custody.material === original.material && custody.engine === original.engine &&
            custody.curl === original.curl && custody.rasterOwner === original.rasterOwner &&
            custody.passiveRuntime === original.passiveRuntime, "C407.Close.SameExactOriginalPhysicalOwners")
        if (!custody.physicalReturns()) return false
        if (works.any { original.rasterOwner.installedWorkFor(it.lease.resourceKey) != null ||
                original.curl.installedWorkFor(it.lease.resourceKey) != null ||
                original.runtime.releaseStateOf(it.lease.resourceKey) != ReaderTransitionResourceState.Released }) return false
        return publications.all { original.curl.installedDeckFor(it.resource.key) == null &&
            original.runtime.releaseStateOf(it.resource.key) == ReaderTransitionResourceState.Released }
    }
}

internal data class Task407ReadableCustody(val work: ReaderActivatedRasterWork, val plan: ReaderPageRasterPreparationPlan,
    val deck: ReaderDeckLease, val physicalOwner: ReaderDeckPhysicalOwnershipAdapter.Lease)

internal class Task407TurnAdmission(val transitionId: ReaderTransitionId,
    val publication: ReaderOwnerAndInputPublicationSnapshot, val custody: Task407ReadableCustody,
    val source: ReaderInstalledCurlGestureSource, val gesture: ReaderInstalledCurlGestureObservationOwner)

/** In-memory ORIGINAL references, captured only inside the authenticated accepted callback before
 * predecessor release. No target/specification is reconstructed and no physical return is delayed. */
internal class Task407AcceptedTurnCustody(val command: ReaderTransitionCommand.CommitOwnerAndInputLease,
    val applied: ReaderOwnerAndInputPublicationResult.Applied, val admission: Task407TurnAdmission,
    val targetCustody: Task407ReadableCustody, val terminal: ReaderInstalledCurlSettlementObservation,
    val semanticReceipt: ReaderPresentationEventReceipt, val settlement: ReaderPageTurnSettlementAck,
    val event: ReaderBridgeEvent.LocationChanged, val publication: ReaderOwnerAndInputPublicationSnapshot,
    val committed: ReaderCommittedTransition, val outcome: ReaderTransitionOutcome.Succeeded)

/** Prospective r48 observation contract: the accepted ORIGINAL typed DRAW/Commit/Applied chain,
 * exact source/target physical custody and owning receipt/ACK are evidence. A surviving FrameTarget
 * object was an r45 preview-only assertion, not a normative protocol/lifetime requirement. This
 * wrapper grants nothing and never reconstructs a target. Once-only publication evidence is bounded
 * authenticated callback custody, not a postterminal active consumption/cumulative census. */
internal class Task407AcceptedTurnEvidence internal constructor(
    val custody: Task407AcceptedTurnCustody
) {
    val command get() = custody.command
    val applied get() = custody.applied
    val settlement get() = custody.settlement
}

internal data class Task407OriginalOwners(val root: View, val installed: ReaderActivatedSessionSnapshot,
    val runtime: ReaderResumableTransitionCoordinator, val material: ReaderFramePhysicalOwnershipAdapter.Lease,
    val engine: ReaderEngineWebView, val curl: ReaderPlayLikeCurlFoliateController,
    val rasterOwner: ReaderPageRasterPreparationController, val probe: ReaderPageOwnershipProbe,
    val passiveRuntime: ReaderPassiveRasterWebViewHost, val executor: ReaderSemanticCommandExecutor,
    val requests: ReaderSemanticExecutableRequestRegistry)

internal class Task407OriginalCleanupCustody(val root: View, val runtime: ReaderResumableTransitionCoordinator?,
    val material: ReaderFramePhysicalOwnershipAdapter.Lease?, val engine: ReaderEngineWebView?,
    val curl: ReaderPlayLikeCurlFoliateController, val rasterOwner: ReaderPageRasterPreparationController,
    val passiveRuntime: ReaderPassiveRasterWebViewHost?, val probe: ReaderPageOwnershipProbe,
    val executor: ReaderSemanticCommandExecutor?, val requests: ReaderSemanticExecutableRequestRegistry?) {
    fun physicalReturns(): Boolean {
        val snapshot = runtime?.snapshot()
        if (snapshot != null && (!snapshot.releaseOnlySink || snapshot.ownedResourceCount != 0 ||
                snapshot.releaseCommandIssuedCount != 0)) return false
        if (runtime != null) {
            // Counts alone omit failed/unresolved admissions. Read only the original ledger's
            // typed aggregate and empty-failure predicate; never dump resource identities.
            val ledger = runtime.javaClass.getDeclaredField("releaseLedger").apply { isAccessible = true }
                .get(runtime) as ReaderTransitionReleaseLedger
            if (ledger.retentionSnapshot().activeStateCount != 0 || ledger.failureFence().tombstones.isNotEmpty()) return false
        }
        if (material != null && (material.owned || material.wrapper.parent != null)) return false
        if (engine != null && (!engine.runtimeRetired || task407EngineDestructionConsumer(engine) != null)) return false
        if (passiveRuntime != null && !passiveRuntime.isRetired) return false
        return (executor?.activeSlotCount ?: 0) == 0 && (requests?.activeHandleCount ?: 0) == 0 &&
            !task407CanAcceptPointer(root)
        // The caller must ALSO await the original probe's closed baseline, which accounts for
        // unknown in-flight renderer/PixelCopy/bundle work without any raw owner/cache dump.
    }
}

private fun task407CanAcceptPointer(root: View): Boolean = root.javaClass.getDeclaredMethod("canAcceptNewPointer")
    .apply { isAccessible = true }.invoke(root) as Boolean
private fun task407EngineDestructionConsumer(engine: ReaderEngineWebView): Any? = engine.javaClass
    .getDeclaredField("physicalDestructionConfirmation").apply { isAccessible = true }.get(engine)

/** Only stable terminal/return predicates are frame-waited. Transient acceptance evidence is NOT
 * sampled here. No virtual time, producer invocation, registration hijack or timeout-to-success.
 */
internal fun task407AwaitOriginal(fixedLabel: String, timeoutMillis: Long = 120_000L, predicate: () -> Boolean) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val terminal = CountDownLatch(1)
    val stopped = AtomicBoolean(false)
    val failure = AtomicReference<Task407FixedFailure?>()
    val expires = SystemClock.uptimeMillis() + timeoutMillis
    instrumentation.runOnMainSync {
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (stopped.get()) return
                try {
                    if (predicate()) { stopped.set(true); terminal.countDown() }
                    else if (SystemClock.uptimeMillis() >= expires) {
                        failure.set(Task407FixedFailure(fixedLabel)); stopped.set(true); terminal.countDown()
                    } else choreographer.postFrameCallback(this)
                } catch (safe: Task407FixedFailure) {
                    failure.set(safe.sanitizedCopy()); stopped.set(true); terminal.countDown()
                } catch (_: Throwable) {
                    failure.set(Task407FixedFailure("E407.Runtime.OriginalObservationFailed")); stopped.set(true); terminal.countDown()
                }
            }
        }
        choreographer.postFrameCallback(callback)
    }
    val finished = try { terminal.await(timeoutMillis + 5_000L, TimeUnit.MILLISECONDS) }
        finally { stopped.set(true) }
    task407Require(finished, fixedLabel)
    failure.get()?.let { throw it }
}
internal fun task407AwaitOwnership(probe: ReaderPageOwnershipProbe, closed: Boolean) {
    var inFlight = false
    var accepted = false
    task407AwaitOriginal(if (closed) "C407.Close.OriginalOwnershipClosedBaselineRequired"
        else "A407.Display.OriginalOwnershipWithinBoundsRequired") {
        if (!accepted && !inFlight) {
            inFlight = true
            probe.request { result ->
                inFlight = false
                val original = result.getOrNull()
                accepted = original != null && original.withinBounds() && (!closed || original.isClosedBaseline())
            }
        }
        accepted
    }
}
internal fun <T> task407OnMain(block: () -> T): T {
    val result = AtomicReference<T>()
    val failure = AtomicReference<Task407FixedFailure?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        try { result.set(block()) }
        catch (safe: Task407FixedFailure) { failure.set(safe.sanitizedCopy()) }
        catch (_: Throwable) { failure.set(Task407FixedFailure("E407.Runtime.OriginalMainActionFailed")) }
    }
    failure.get()?.let { throw it }
    return result.get()
}
