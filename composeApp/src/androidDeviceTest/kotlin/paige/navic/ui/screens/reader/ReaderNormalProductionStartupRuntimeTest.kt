package paige.navic.ui.screens.reader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import paige.navic.reader.*
import paige.navic.util.core.AppLogLevel
import paige.navic.util.core.Logger
import paige.navic.util.core.LoggerEvent

internal enum class Task407OriginalStartupMethod(val wireName: String) {
    Passive("originalRootRequiresExecutableOriginalPassiveProducerBeforeBootstrapDisplay"),
    Turns("originalRootPublishesBootstrapThenTwoImmediateSettledTurns"),
    Cancel("cancelOriginalPendingBootstrapThenCloseReturnsExactPhysicalOwners")
}

/** Per-fixture projection of source-static logger markers and closed numeric phase messages.
 * Preserves the preexisting sink; never retains arbitrary messages, payloads or throwables. */
internal class Task407LoggerStartupObservation(private val method: Task407OriginalStartupMethod) {
    private val lock = Any()
    @Volatile private var startupWitness: Task407StartupPhaseWitness? = null
    private var priorSink: ((LoggerEvent) -> Unit)? = null
    private var issueLogSinkField: java.lang.reflect.Field? = null
    private var installed = false
    private var bitmapGeometryUnavailable = false
    private var bitmapAllocationFailed = false
    private var bitmapDrawFallbackFailed = false
    private var bitmapDrawSuccesses = 0
    private var bitmapStableSurfaceWaits = 0
    private var bitmapUnpaintedSurfaceRejections = 0
    private var liveCaptureUnavailable = false
    private var liveValidationAccepted = 0
    private var liveValidationContentRejected = 0
    private var liveValidationInvalidated = 0
    private val persistenceSkipCounts = linkedMapOf(
        "bundle-source-closed" to 0,
        "webview-unavailable" to 0,
        "physical-layout-stale" to 0,
        "generation-or-physical-layout-changed" to 0,
        "descriptor-unavailable" to 0,
        "descriptor-mismatch" to 0,
        "descriptor-request-failed" to 0,
        "durable-publication-failed" to 0,
        "publication-entrycapacity" to 0,
        "publication-callbackcapacity" to 0,
        "publication-activationfrozen" to 0,
        "publication-initialization-failed" to 0
    )
    // Static, source-qualified ReaderEngineWebViewHost.android.kt projections only. No event
    // message, payload, throwable, command ID, URL, location, identity or arbitrary key is retained.
    private val readerEngineStartupObservationLabels = linkedSetOf<String>()
    private val readerPhaseRecords = ArrayList<String>(128)
    private var readerPhaseDropped = 0

    private companion object {
        private val readerPhasePattern = Regex(
            "reader-webview-phase source=(Engine|PassiveRaster) " +
                "phase=(${ReaderWebViewDiagnosticPhase.entries.joinToString("|") { it.name }}) " +
                "state=(${ReaderWebViewDiagnosticState.entries.joinToString("|") { it.name }}) " +
                "reason=(${ReaderWebViewDiagnosticReason.entries.joinToString("|") { it.name }}) " +
                "elapsedMs=[0-9]{1,19}( code=-?[0-9]{1,10})?( mainFrame=(true|false))?" +
                "( didCrash=(true|false))?( priorityAtExit=-?[0-9]{1,10})?"
        )
        private val moduleLoadedConsoleMessages = setOf(
            "Reader console TIP: module-loaded",
            "Reader console LOG: module-loaded",
            "Reader console WARNING: module-loaded",
            "Reader console ERROR: module-loaded",
            "Reader console DEBUG: module-loaded"
        )
        private val redactedConsoleErrorMessages = setOf(
            "Reader console ERROR: module-loaded",
            "Reader console ERROR: dispatch",
            "Reader console ERROR: post",
            "Reader console ERROR: bridge-unavailable",
            "Reader console ERROR: post-failed",
            "Reader console ERROR: reportError",
            "Reader console ERROR: [redacted-reader-console]"
        )
    }

    private val sink: (LoggerEvent) -> Unit = { event ->
        val readerPhase = try { observe(event) } catch (_: Throwable) {
            startupWitness?.observerGapObserved()
            false
        }
        if (readerPhase) try {
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("E407.ReaderPhase.V1", "${method.wireName}|${event.message}")
            })
        } catch (_: Throwable) { startupWitness?.observerGapObserved() }
        try { priorSink?.invoke(event) } catch (_: Throwable) { }
    }

    @Suppress("UNCHECKED_CAST")
    fun install() {
        if (installed) return
        // Observation anchor only. The exact production timer-bind boundary is NOT hooked.
        startupWitness = Task407StartupPhaseWitness({ SystemClock.uptimeMillis() })
        try {
            val field = Logger::class.java.getDeclaredField("issueLogSink").apply { isAccessible = true }
            priorSink = field.get(Logger) as? ((LoggerEvent) -> Unit)
            issueLogSinkField = field
            Logger.configureIssueLogSink(sink)
            installed = true
        } catch (failure: Throwable) {
            startupWitness?.observerGapObserved()
            throw failure
        }
    }

    fun recordStartupObservation(event: Task407StartupPhaseEvent) {
        try { startupWitness?.recordFirst(event) }
        catch (_: Throwable) { startupWitness?.observerGapObserved() }
    }

    fun observeSinkContinuity() = synchronized(lock) {
        try {
            if (!installed || issueLogSinkField?.get(Logger) !== sink) startupWitness?.observerGapObserved()
        } catch (_: Throwable) { startupWitness?.observerGapObserved() }
    }

    fun observationGap() { startupWitness?.observerGapObserved() }

    /** Closed fixture-local wire format only; no raw event or identity is a serialization input. */
    fun startupWitnessJson(): String? {
        observeSinkContinuity()
        val snapshot = startupWitness?.snapshot() ?: return null
        return JSONObject().apply {
            put("version", 1)
            put("method", method.wireName)
            put("anchor", Task407StartupPhaseEvent.OBSERVATION_INSTALL_ATTEMPT.name)
            put("records", JSONArray().apply {
                snapshot.records.forEach { record -> put(JSONObject().apply {
                    put("sequence", record.sequence)
                    put("elapsed_ms", record.elapsedMillis)
                    put("event", record.event.name)
                }) }
            })
            put("dropped_records", snapshot.droppedRecords)
            put("clock_clamped", snapshot.clockClamped)
            put("observer_continuity", snapshot.observerContinuity.name)
        }.toString()
    }

    /** Only fixed formatter vocabulary reaches this bounded fixture transport. */
    fun readerPhasesJson(): String = synchronized(lock) {
        JSONObject().apply {
            put("version", 1)
            put("method", method.wireName)
            put("records", JSONArray(readerPhaseRecords.toList()))
            put("dropped_records", readerPhaseDropped)
        }.toString()
    }

    fun restore() {
        if (!installed) return
        val field = issueLogSinkField ?: return
        if (field.get(Logger) === sink) Logger.configureIssueLogSink(priorSink)
        installed = false
    }

    /** Tags are source-qualified to ReaderPageTurnBitmapSource.android.kt,
     * ReaderPageTurnBundleSource.android.kt and ReaderEngineWebViewHost.android.kt; message data
     * is compared but never retained. */
    private fun observe(event: LoggerEvent) = synchronized(lock) {
        val phaseSource = when (event.tag) {
            "ReaderEngineWebViewHost" -> "Engine"
            "ReaderPassiveRasterWebViewHost" -> "PassiveRaster"
            else -> null
        }
        val readerPhase = phaseSource != null && event.message.length <= 512 &&
            event.message.startsWith("reader-webview-phase source=$phaseSource ") &&
            readerPhasePattern.matches(event.message)
        if (readerPhase) {
            if (readerPhaseRecords.size < 128) readerPhaseRecords += event.message
            else readerPhaseDropped = (readerPhaseDropped + 1).coerceAtMost(65535)
        }
        when (event.tag) {
            "ReaderPageTurnBitmapSource" -> observeBitmapSource(event)
            "ReaderPageTurnBundleSource" -> observeBundleSource(event)
            "ReaderEngineWebViewHost" -> observeReaderEngineHost(event)
        }
        readerPhase
    }

    private fun observeBitmapSource(event: LoggerEvent) {
        when {
            event.level == AppLogLevel.Info &&
                event.message == "Page-turn capture unavailable reason=geometry-unavailable" ->
                bitmapGeometryUnavailable = true
            event.level == AppLogLevel.Warning && event.message == "Page-turn bitmap allocation failed" ->
                bitmapAllocationFailed = true
            event.level == AppLogLevel.Warning && event.message == "Page-turn WebView draw fallback failed" ->
                bitmapDrawFallbackFailed = true
            event.level == AppLogLevel.Info &&
                event.message.startsWith("Page-turn capture success method=webview-draw rect=") ->
                bitmapDrawSuccesses += 1
            event.level == AppLogLevel.Info &&
                event.message.startsWith("Page-turn capture awaiting stable ") ->
                bitmapStableSurfaceWaits += 1
            event.level == AppLogLevel.Info &&
                event.message.startsWith("Page-turn capture rejected unpainted surface rect=") ->
                bitmapUnpaintedSurfaceRejections += 1
        }
    }

    private fun observeBundleSource(event: LoggerEvent) {
        when {
            event.level == AppLogLevel.Info &&
                event.message == "Live page-turn validation capture result=Unavailable" ->
                liveCaptureUnavailable = true
            event.level == AppLogLevel.Info &&
                event.message == "Live page-turn validation comparison result=Accepted" ->
                liveValidationAccepted += 1
            event.level == AppLogLevel.Info &&
                event.message == "Live page-turn validation comparison result=ContentRejected" ->
                liveValidationContentRejected += 1
            event.level == AppLogLevel.Info &&
                event.message == "Live page-turn validation comparison result=Invalidated" ->
                liveValidationInvalidated += 1
            event.level == AppLogLevel.Warning &&
                event.message.startsWith("Page raster persistence skipped page=") ->
                persistenceSkipCounts.keys.firstOrNull { reason ->
                    event.message.contains(" reason=$reason requestGeneration=")
                }?.let { reason -> persistenceSkipCounts[reason] = persistenceSkipCounts.getValue(reason) + 1 }
        }
    }

    /** Each input is a complete source-static projection from ReaderEngineLogProjector or a
     * source-sanctioned scalar error prefix. The set receives only these fixed labels. */
    private fun observeReaderEngineHost(event: LoggerEvent) {
        when {
            event.level == AppLogLevel.Info && event.message in moduleLoadedConsoleMessages -> {
                recordStartupObservation(Task407StartupPhaseEvent.LOGGER_CONSOLE_MODULE_LOADED)
                readerEngineStartupObservationLabels += "E407.Observation.EngineConsoleModuleLoaded"
            }
            event.level == AppLogLevel.Info && event.message == "Reader bridge event: ready" -> {
                recordStartupObservation(Task407StartupPhaseEvent.LOGGER_BRIDGE_READY)
                readerEngineStartupObservationLabels += "E407.Observation.EngineBridgeReady"
            }
            event.level == AppLogLevel.Info && event.message == "Reader bridge event: publicationReady" -> {
                recordStartupObservation(Task407StartupPhaseEvent.LOGGER_BRIDGE_PUBLICATION_READY)
                readerEngineStartupObservationLabels += "E407.Observation.EngineBridgePublicationReady"
            }
            event.level == AppLogLevel.Info && event.message == "Reader bridge event: commandAck" -> {
                recordStartupObservation(Task407StartupPhaseEvent.LOGGER_BRIDGE_COMMAND_ACK)
                readerEngineStartupObservationLabels += "E407.Observation.EngineBridgeCommandAcknowledged"
            }
            event.level == AppLogLevel.Info && event.message == "Reader bridge event: commandFailed" -> {
                recordStartupObservation(Task407StartupPhaseEvent.LOGGER_BRIDGE_COMMAND_FAILED)
                readerEngineStartupObservationLabels += "E407.Observation.EngineBridgeCommandFailed"
            }
            event.level == AppLogLevel.Info && event.message == "Reader bridge event: locationChanged" ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineBridgeLocationChanged"
            event.level == AppLogLevel.Info && event.message == "Reader bridge event: currentDestinationResolved" -> {
                recordStartupObservation(Task407StartupPhaseEvent.LOGGER_CURRENT_DESTINATION_RESOLVED)
                readerEngineStartupObservationLabels += "E407.Observation.EngineBridgeCurrentDestinationResolved"
            }
            event.level == AppLogLevel.Info && event.message == "Reader bridge event: paginationProfileStatus" ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineBridgePaginationProfileStatus"
            event.level == AppLogLevel.Info && event.message == "Reader bridge event: error" ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineBridgeError"
            event.level == AppLogLevel.Info && event.message == "Dispatching reader engine command: openPublication" -> {
                recordStartupObservation(Task407StartupPhaseEvent.LOGGER_OPEN_PUBLICATION_DISPATCHED)
                readerEngineStartupObservationLabels += "E407.Observation.EngineOpenPublicationDispatched"
            }
            event.level == AppLogLevel.Info && event.message == "Dispatching reader engine command: resolveCurrentDestination" -> {
                recordStartupObservation(Task407StartupPhaseEvent.LOGGER_CURRENT_DESTINATION_QUERY_DISPATCHED)
                readerEngineStartupObservationLabels += "E407.Observation.EngineResolveCurrentDestinationDispatched"
            }
            event.level == AppLogLevel.Info &&
                event.message == "Skipping reader command dispatch: ready=false entrypoint=false" ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineDispatchSkippedNotReadyNotEntrypoint"
            event.level == AppLogLevel.Info &&
                event.message == "Skipping reader command dispatch: ready=false entrypoint=true" ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineDispatchSkippedNotReadyEntrypoint"
            event.level == AppLogLevel.Info &&
                event.message == "Skipping reader command dispatch: ready=true entrypoint=false" ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineDispatchSkippedReadyNotEntrypoint"
            event.level == AppLogLevel.Info &&
                event.message == "Skipping reader command dispatch: ready=true entrypoint=true" ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineDispatchSkippedReadyEntrypoint"
            event.level == AppLogLevel.Error &&
                event.message.startsWith("Reader engine WebView error main=true code=") ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineMainFrameWebViewError"
            event.level == AppLogLevel.Warning &&
                event.message.startsWith("Reader engine WebView HTTP error main=true status=") ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineMainFrameWebViewError"
            event.level == AppLogLevel.Error &&
                event.message.startsWith("Reader engine WebView error main=false code=") ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineResourceWebViewError"
            event.level == AppLogLevel.Warning &&
                event.message.startsWith("Reader engine WebView HTTP error main=false status=") ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineResourceWebViewError"
            event.level == AppLogLevel.Error &&
                event.message.startsWith("Reader engine WebView render process gone didCrash=") ->
                readerEngineStartupObservationLabels += "E407.Observation.EngineRendererRetired"
        }
        if (event.level == AppLogLevel.Error && event.message in redactedConsoleErrorMessages) {
            readerEngineStartupObservationLabels += "E407.Observation.EngineConsoleErrorObserved"
        }
    }

    fun labels(): List<String> = synchronized(lock) { buildList {
        if (bitmapGeometryUnavailable) add("E407.Observation.BitmapGeometryUnavailable")
        if (bitmapAllocationFailed) add("E407.Observation.BitmapAllocationFailed")
        if (bitmapDrawFallbackFailed) add("E407.Observation.BitmapDrawFallbackFailed")
        if (bitmapDrawSuccesses > 0) add("E407.Observation.BitmapWebViewDrawObserved")
        if (bitmapStableSurfaceWaits > 0) add("E407.Observation.BitmapStableSurfaceWaitObserved")
        if (bitmapUnpaintedSurfaceRejections > 0)
            add("E407.Observation.BitmapUnpaintedSurfaceRejectionObserved")
        if (liveCaptureUnavailable) add("E407.Observation.LiveValidationCaptureUnavailable")
        if (liveValidationAccepted > 0) add("E407.Observation.LiveValidationAccepted")
        if (liveValidationContentRejected > 0) add("E407.Observation.LiveValidationContentRejected")
        if (liveValidationInvalidated > 0) add("E407.Observation.LiveValidationInvalidated")
        persistenceSkipCounts.forEach { (reason, count) ->
            if (count > 0) add(when (reason) {
                "bundle-source-closed" -> "E407.Observation.PersistenceSourceClosed"
                "webview-unavailable" -> "E407.Observation.PersistenceWebViewUnavailable"
                "physical-layout-stale" -> "E407.Observation.PersistencePhysicalLayoutStale"
                "generation-or-physical-layout-changed" -> "E407.Observation.PersistenceGenerationOrLayoutChanged"
                "descriptor-unavailable" -> "E407.Observation.PersistenceDescriptorUnavailable"
                "descriptor-mismatch" -> "E407.Observation.PersistenceDescriptorMismatch"
                "descriptor-request-failed" -> "E407.Observation.PersistenceDescriptorRequestFailed"
                "durable-publication-failed" -> "E407.Observation.PersistenceDurablePublicationFailed"
                "publication-entrycapacity" -> "E407.Observation.PersistenceEntryCapacity"
                "publication-callbackcapacity" -> "E407.Observation.PersistenceCallbackCapacity"
                "publication-activationfrozen" -> "E407.Observation.PersistenceActivationFrozen"
                else -> "E407.Observation.PersistenceInitializationFailed"
            })
        }
        addAll(readerEngineStartupObservationLabels)
    } }

    /** Returns null unless this exact observer is currently installed; historical sink continuity
     * is not established. Exposes only a fixed ACK marker, retaining no event, identifier or message. */
    fun commandAcknowledgementSeenOrNull(): Boolean? = synchronized(lock) {
        if (!installed) return@synchronized null
        val field = issueLogSinkField ?: return@synchronized null
        if (field.get(Logger) !== sink) return@synchronized null
        "E407.Observation.EngineBridgeCommandAcknowledged" in readerEngineStartupObservationLabels
    }
}

/** Integrated, unexecuted Android runtime candidate. Authentic typed terminal DRAW is joined through
 * original accepted Commit/Applied, journal commit and pre-release source/target physical custody.
 * No surviving target/specification is reconstructed. Prospective r64 reclassifies only the
 * two original Chromium-dependent host declarations into their runtime counterparts, retaining
 * their original method names and exact pre-edit sources in history; this is not runtime acceptance.
 * A407-RUNTIME-WORDSYNC-PAIR is a mandatory distinct unresolved gate; this public fixture disables it.
 */
@RunWith(AndroidJUnit4::class)
class ReaderNormalProductionStartupRuntimeTest {
    @get:Rule val fixedDiagnostics: TestRule = Task407FixedDiagnosticsRule()

    @Test
    fun originalRootRequiresExecutableOriginalPassiveProducerBeforeBootstrapDisplay() = withOriginalRoot(Task407OriginalStartupMethod.Passive) { fixture ->
        val startup = awaitBootstrap(fixture)
        task407OnMain {
            fixture.requireSameOwners(startup.owners)
            fixture.requireDisplayed(startup.publication)
            task407Require(fixture.passive()?.isAvailable == true &&
                startup.owners.curl.installedWorkFor(startup.work.lease.resourceKey) === startup.work,
                "E407.Producer.OriginalPassiveAndCapturedDeck")
            val physicalCapture = startup.owners.passiveRuntime.captureMetrics()
            task407Require(physicalCapture.captureRequests > 0 && physicalCapture.pixelCopyAttempts > 0 &&
                physicalCapture.pixelCopySuccesses > 0, "E407.Producer.OriginalVisualStateAndPixelCopyExecuted")
            val facts = task407Required(startup.work.preparationFacts, "E407.Producer.OriginalPreparedTargets")
            task407Require(facts.phase == ReaderPagePreparationPhase.Ready && facts.requiredCount > 0 &&
                facts.completedCount == facts.requiredCount, "E407.Producer.ActualTargetPreparationCompleted")
            task407Require(startup.owners.runtime.snapshot().activeTransitionsRegistered == 1 &&
                fixture.initialIngressCount == 1 && fixture.bootstrapPublicationCount == 1 &&
                fixture.legacyIngressCount == 0 && fixture.turnIngressCount == 0 && fixture.turnPublicationCount == 0,
                "E407.Startup.OneOriginalSemanticBootstrap")
        }
        task407AwaitOwnership(startup.owners.probe, closed = false)
        closeAndRequireReturns(fixture, startup.owners, listOf(startup.work), listOf(startup.publication))
    }

    @Test
    fun originalRootPublishesBootstrapThenTwoImmediateSettledTurns() = withOriginalRoot(Task407OriginalStartupMethod.Turns) { fixture ->
        val startup = awaitBootstrap(fixture)
        task407OnMain { fixture.requireAuthenticatedPublicationObservationBinding() }
        val works = mutableListOf(startup.work)
        val publications = mutableListOf(startup.publication)
        var predecessor = startup.publication
        repeat(2) { turn ->
            val source = predecessor
            val sourceOrdinal = task407OnMain {
                fixture.requireSameOwners(startup.owners)
                fixture.requireDisplayed(source)
                fixture.readableCustody(source).plan.centerPageIndex
            }
            val beforePublication = task407OnMain { fixture.journal().lastPublicationSequence }
            val beforeCount = task407OnMain { startup.owners.runtime.snapshot().activeTransitionsRegistered }
            val admitted = task407OnMain {
                // Lease check and actual DOWN/MOVE/source capture/UP in the SAME main action.
                val id = fixture.pointerTurn(source)
                task407Require(startup.owners.runtime.snapshot().activeTransitionsRegistered == beforeCount + 1 &&
                    id.operation == ReaderTransitionOperation.CurlClaimAndSettlement,
                    "E407.Turn.PointerAdmitsOneOriginalInstalledTurn")
                id
            }
            // Stable TEST-owned reference capture from the authenticated original callback, not
            // frame polling of active consumption or a target declaration as publication authority.
            task407AwaitOriginal("E407.Turn.AuthenticatedPreReleaseSourceTargetCustodyRequired") {
                fixture.acceptedTurnCustodyOrNull(admitted) != null
            }
            val evidence = task407OnMain { fixture.requireOriginalAcceptedTurnEvidence(admitted) }
            var successor: ReaderOwnerAndInputPublicationSnapshot? = null
            task407AwaitOriginal("E407.Turn.RealSettlementSuccessorDrawAndPublicationRequired") {
                fixture.requireSameOwners(startup.owners)
                successor = fixture.displayed()?.takeIf { it.resource.key != source.resource.key }
                successor != null
            }
            task407OnMain {
                val publication = task407Required(successor, "E407.Turn.OriginalSuccessorPublication")
                val custody = fixture.readableCustody(publication)
                val work = custody.work
                val plan = custody.plan
                val accepted = evidence.custody
                val completion = task407Required(accepted.terminal.rendererCompletion,
                    "E407.Turn.OriginalRendererCompletionIdentity")
                val subject = task407Required(evidence.applied.subject as? ReaderOwnerAndInputPublicationSubject.Successor,
                    "E407.Turn.OriginalAcceptedSuccessorSubject")
                val event = evidence.custody.event
                val receipt = evidence.custody.semanticReceipt
                task407Require(evidence.custody.targetCustody.work === work && evidence.custody.targetCustody.plan === plan &&
                    evidence.custody.targetCustody.deck === custody.deck &&
                    evidence.custody.targetCustody.physicalOwner === custody.physicalOwner &&
                    evidence.custody.admission.publication === source && accepted.publication === publication &&
                    evidence.command.targetHandle === subject.targetHandle &&
                    evidence.command.transitionId === accepted.committed.id &&
                    evidence.command.preparedFrameResource === publication.resource &&
                    evidence.applied.publishedOwner === publication.owner &&
                    evidence.applied.publishedBinding === publication.binding &&
                    evidence.applied.finalPhysicalInputLease === publication.lease &&
                    accepted.admission.source.deck.resourceKey == source.resource.key && completion.sequence > 0L &&
                    completion.sourceGenerationId == source.binding.textureGeneration &&
                    completion.rendererGenerationAtCompletion == startup.owners.curl.surfaceView.rendererGeneration &&
                    completion.gestureId == accepted.admission.source.intent.gestureId.value &&
                    accepted.terminal.source === accepted.admission.source &&
                    accepted.terminal.stage == ReaderInstalledCurlSettlementStage.Completed &&
                    accepted.committed.owner === publication.owner && accepted.committed.binding === publication.binding &&
                    evidence.command.binding === publication.binding && evidence.settlement.pageIndex == sourceOrdinal + 1,
                    "E407.Turn.SourceLinkedOriginalRendererSuccessorDraw")
                // No post-terminal active consumedSettlement or cumulative consumed census:
                // original authenticated typed DRAW/Commit/Applied + exact source semantic ACK.
                val relocation = task407Required(receipt.event as? ReaderPresentationEvent.FoliateRelocated,
                    "E407.Turn.OriginalOwningRelocationReceipt")
                task407Require(evidence.settlement === relocation.acknowledgement &&
                    accepted.admission.gesture.semanticReceipt === receipt &&
                    accepted.admission.gesture.terminal === accepted.terminal &&
                    completion.currentOrdinal == evidence.settlement.pageIndex &&
                    fixture.turnPublicationCount == turn + 1 &&
                    fixture.turnIngressCount == turn + 1 && receipt.disposition == ReaderPresentationEventDisposition.Accepted &&
                    receipt.semanticReceipt?.transitionId == work.lease.transitionId &&
                    evidence.command.transitionId == work.lease.transitionId &&
                    event.destinationCommitIdentity == publication.binding.destinationCommitIdentity &&
                    event.pageTurnSettleSessionId == source.binding.foliateSessionId &&
                    event.pageTurnSettleRasterGeneration == source.binding.rasterGeneration &&
                    event.pageTurnSettleTextureGeneration == source.binding.textureGeneration,
                    "E407.Turn.ExactOriginalSettlementConsumedOnce")
                task407Require(work !== startup.work && work.lease.binding == publication.binding &&
                    work.lease.rasterGeneration != startup.work.lease.rasterGeneration &&
                    work.lease.binding.textureGeneration != source.binding.textureGeneration &&
                    plan.resolvedLayout?.pageIndex == sourceOrdinal + 1 &&
                    startup.owners.engine.captureResolvedLayoutCurrency() == null,
                    "E407.Turn.OriginalAllocationOwnedSuccessorLayout")
                task407Require(fixture.owningController.state.chrome.currentLocator?.pageIndex == sourceOrdinal + 1 &&
                    fixture.owningController.state.destinationCommitIdentity == event.destinationCommitIdentity &&
                    fixture.owningController.state.pageTurnSettlementAck?.pageIndex == sourceOrdinal + 1 &&
                    fixture.journal().lastPublicationSequence > beforePublication,
                    "E407.Turn.ContinuingOwningProgressAndAtomicPublication")
                fixture.requireDisplayed(publication)
                fixture.requireSameOwners(startup.owners)
                task407Require(fixture.initialIngressCount == 1 && fixture.legacyIngressCount == 0,
                    "E407.Turn.NoBootstrapOrLegacyReplay")
                works += work
                publications += publication
                predecessor = publication
            }
        }
        val lastPublished = predecessor
        task407OnMain {
            task407Require(startup.owners.runtime.snapshot().activeTransitionsRegistered == 3 &&
                fixture.turnIngressCount == 2 && fixture.turnPublicationCount == 2 && fixture.bootstrapPublicationCount == 1,
                "E407.ContinuousRoot.OneBootstrapTwoRealTurns")
            fixture.requireDisplayed(lastPublished)
            fixture.immediateCancelledPointer()
        }
        task407AwaitOriginal("C407.PointerCancel.OriginalPhysicalCancellationReturnsInput") {
            fixture.displayed()?.resource?.key == lastPublished.resource.key
        }
        task407OnMain {
            fixture.requireDisplayed(task407Required(fixture.displayed(), "C407.PointerCancel.OriginalRetainedPublication"))
            task407Require(fixture.turnIngressCount == 2 && fixture.turnPublicationCount == 2 &&
                fixture.bootstrapPublicationCount == 1 && fixture.initialIngressCount == 1 &&
                fixture.legacyIngressCount == 0, "C407.PointerCancel.NoInventedSettlementOrReplay")
        }
        task407AwaitOwnership(startup.owners.probe, closed = false)
        closeAndRequireReturns(fixture, startup.owners, works, publications)
    }

    @Test
    fun cancelOriginalPendingBootstrapThenCloseReturnsExactPhysicalOwners() = withOriginalRoot(Task407OriginalStartupMethod.Cancel) { fixture ->
        // Prospective bounded profile: an ACTUAL registered Raster owner, not a pending request,
        // is captured and canceled in this SAME main-looper callback. A missed fast interval FAILS;
        // no producer/release is delayed and no sampled declaration is treated as an ACK.
        var original: Task407OriginalOwners? = null
        var work: ReaderActivatedRasterWork? = null
        awaitStartupBoundary(fixture, "C407.BootstrapCancel.OriginalPendingPreparationRequired") {
            if (fixture.rootOrNull() == null || fixture.store().snapshot == null || fixture.material() == null ||
                fixture.engine() == null || fixture.passiveRuntime() == null) return@awaitStartupBoundary false
            val pending = fixture.registeredActiveWork() ?: return@awaitStartupBoundary false
            if (fixture.journal().active?.id != pending.lease.transitionId || !pending.isCurrent() ||
                pending.preparationFacts?.phase == ReaderPagePreparationPhase.Ready ||
                pending.lease.transitionId.operation != ReaderTransitionOperation.BootstrapNativePage) return@awaitStartupBoundary false
            original = fixture.owners()
            fixture.requireSameOwners(original!!)
            work = pending
            // Capture/check and cancellation do not yield the main looper between them.
            task407Require(fixture.registeredActiveWork() === pending, "C407.BootstrapCancel.SameActualRegisteredWork")
            val beforeCancel = fixture.journal()
            val issued = task407Required(beforeCancel.lastIssuedTransitionIdentity,
                "C407.BootstrapCancel.OriginalIssuedIdentity")
            task407Require(issued.readerSessionGeneration == pending.lease.transitionId.readerSessionGeneration &&
                issued.coordinatorEpoch == pending.lease.transitionId.coordinatorEpoch &&
                issued.sequence == pending.lease.transitionId.sequence,
                "C407.BootstrapCancel.IssuedIdentityMatchesOriginalWork")
            original!!.runtime.enqueue(ReaderTransitionFact.Intent(pending.lease.transitionId, ReaderCancelIntent))
            val afterCancel = fixture.journal()
            val outcome = task407Required(afterCancel.lastOutcome as? ReaderTransitionOutcome.Cancelled,
                "C407.BootstrapCancel.OriginalCancelledOutcomeRequired")
            task407Require(afterCancel.active == null && outcome.reason == ReaderTransitionCancellationReason.UserCancelled &&
                afterCancel.retryableTransition == null && afterCancel.lastIssuedTransitionIdentity === issued &&
                afterCancel.lastTransitionSequence == beforeCancel.lastTransitionSequence,
                "C407.BootstrapCancel.UserCancellationIsNotRetryOrMaterialFailure")
            true
        }
        val owners = task407Required(original, "C407.BootstrapCancel.OriginalOwners")
        val canceled = task407Required(work, "C407.BootstrapCancel.OriginalWork")
        task407AwaitOriginal("C407.BootstrapCancel.ExactRasterPhysicalReturnRequired") {
            !canceled.isCurrent() && owners.rasterOwner.installedWorkFor(canceled.lease.resourceKey) == null &&
                owners.runtime.releaseStateOf(canceled.lease.resourceKey) == ReaderTransitionResourceState.Released &&
                owners.executor.activeSlotCount == 0 && owners.requests.activeHandleCount == 0
        }
        task407OnMain {
            fixture.requireSameOwners(owners)
            task407Require(owners.material.owned && owners.material.wrapper.parent != null && !fixture.canAcceptPointer(),
                "C407.BootstrapCancel.LogicalCancelIsNotMaterialOrDisplayReturn")
        }
        closeAndRequireReturns(fixture, owners, listOf(canceled), emptyList())
    }

    private data class Startup(val owners: Task407OriginalOwners, val work: ReaderActivatedRasterWork,
        val publication: ReaderOwnerAndInputPublicationSnapshot)

    private fun awaitStartupBoundary(fixture: Task407NormalRuntimeRoot, fixedLabel: String,
        predicate: () -> Boolean) {
        try {
            task407AwaitOriginal(fixedLabel) {
                fixture.observeStartupBoundary()
                predicate()
            }
        } catch (failure: Task407FixedFailure) {
            // Failure-only source-static observations, not acceptance or producer facts. Keep the
            // original timeout/failure and independent cleanup unchanged even if diagnosis fails.
            val labels = buildList {
                try { addAll(task407OnMain { fixture.startupObservationLabels() }) }
                catch (_: Throwable) { add("E407.Observation.Unavailable") }
                try { addAll(fixture.loggerObservationLabels()) } catch (_: Throwable) { }
            }.distinct()
            try {
                val witness = try { fixture.startupWitnessJson() } catch (_: Throwable) { null }
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("stream", labels.joinToString("\n"))
                    // Explicit field only; never embed JSON in stream/stack text. Its own closed
                    // method must match the framework's currently started original test.
                    if (witness != null) putString("E407.StartupWitness.V1", witness)
                })
            } catch (_: Throwable) { /* Diagnostic transport cannot replace the primary failure. */ }
            throw failure
        }
    }

    private fun awaitBootstrap(fixture: Task407NormalRuntimeRoot): Startup {
        var displayed: ReaderOwnerAndInputPublicationSnapshot? = null
        awaitStartupBoundary(fixture, "E407.Startup.RealProducerDrawAndAtomicPublicationRequired") {
            if (fixture.rootOrNull() == null || fixture.store().snapshot == null || fixture.runtime() == null ||
                fixture.material() == null || fixture.engine() == null || fixture.passiveRuntime() == null) return@awaitStartupBoundary false
            displayed = fixture.displayed()
            displayed != null
        }
        return task407OnMain {
            val original = fixture.owners()
            fixture.requireSameOwners(original)
            val publication = task407Required(displayed, "E407.Startup.OriginalPublication")
            // Resolve AFTER lawful completion from original readable physical custody.
            val custody = fixture.readableCustody(publication)
            val prepared = custody.work
            val initialPlan = custody.plan
            val initialResolved = task407Required(original.material.initialResolvedLayout,
                "A407.Startup.OriginalMaterialRetainedInitialLayout")
            task407Require(prepared.lease.transitionId.operation == ReaderTransitionOperation.BootstrapNativePage &&
                initialPlan.centerPageIndex == 0 && initialPlan.step == 1 && initialPlan.pageCount >= 3 &&
                initialPlan.targets.map { it.pageIndex } == (0 until initialPlan.pageCount).toList() &&
                initialPlan.resolvedLayout?.pageIndex == 0 && initialPlan.resolvedLayout == initialResolved.layout &&
                initialResolved.scope.installation === original.material &&
                prepared.lease.transitionId.expectedBinding is ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial,
                "A407.Startup.RealInitialOnlyCompleteChapterPlan")
            fixture.requireInstalledPageTurnModelIngressBinding()
            task407Require(original.engine.captureResolvedLayoutCurrency() == null,
                "E407.InitialOnly.NoLayoutCurrencyOutsideOriginalQueryDelivery")
            task407Require(fixture.owningController !== fixture.originalOpenedController && fixture.initialIngressCount == 1 &&
                original.runtime.snapshot().activeTransitionsRegistered == 1 && fixture.bootstrapPublicationCount == 1 &&
                fixture.turnIngressCount == 0 && fixture.turnPublicationCount == 0 && fixture.legacyIngressCount == 0 &&
                fixture.lastTurnReceipt == null, "A407.Startup.RealReactiveOwningIngress")
            task407Require(original.passiveRuntime.captureMetrics().pixelCopySuccesses > 0 &&
                original.curl.installedWorkFor(prepared.lease.resourceKey) === prepared,
                "E407.Startup.OriginalRealPixelCopyProducerNotCacheOnly")
            Startup(original, prepared, publication)
        }
    }

    private fun closeAndRequireReturns(fixture: Task407NormalRuntimeRoot, owners: Task407OriginalOwners,
        works: List<ReaderActivatedRasterWork>, publications: List<ReaderOwnerAndInputPublicationSnapshot>) {
        val initial = task407OnMain { fixture.initialIngressCount }
        val turns = task407OnMain { fixture.turnIngressCount }
        val publicationsBeforeClose = task407OnMain {
            Triple(fixture.bootstrapPublicationCount, fixture.turnPublicationCount, fixture.retainedPublicationCount)
        }
        task407OnMain { fixture.closeComposition() }
        task407AwaitOriginal("C407.Close.ExactOriginalPhysicalReturnsRequired") {
            fixture.requirePhysicalClose(owners, works, publications)
        }
        task407AwaitOwnership(owners.probe, closed = true)
        task407OnMain {
            fixture.requireNoObservationFailure()
            task407Require(fixture.initialIngressCount == initial && fixture.turnIngressCount == turns &&
                Triple(fixture.bootstrapPublicationCount, fixture.turnPublicationCount, fixture.retainedPublicationCount) == publicationsBeforeClose &&
                fixture.legacyIngressCount == 0 && fixture.gateway.captureSelectedSemanticEpoch() == null,
                "C407.Close.NoLateOriginalCallbackReplay")
        }
    }

    private fun withOriginalRoot(method: Task407OriginalStartupMethod, body: (Task407NormalRuntimeRoot) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        task407Require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O, "A407.Runtime.RealPixelCopyPlatformRequired")
        task407Require(context.packageName == Task407RuntimePackage, "A407.Runtime.SelfInstrumentedDisposablePackage")
        val failures = Task407FailureAccount()
        var epub: Task407PublicEpub? = null
        var scenario: ActivityScenario<Task407RuntimeActivity>? = null
        val root = AtomicReference<Task407NormalRuntimeRoot?>()
        val loggerObservation = Task407LoggerStartupObservation(method)
        var cleanupCustody: Task407OriginalCleanupCustody? = null
        var custodyCaptured = false
        var compositionDisposed = false
        var scenarioClosed = false
        var physicalReturned = false
        // Android Logger emits synchronously after Log output. Keep this per-fixture observer live
        // from before mount through independent cleanup, without making its failure test-fatal.
        try { loggerObservation.install() } catch (_: Throwable) { }
        try {
            val material = Task407PublicEpub.create(context)
            epub = material
            val activityScenario = ActivityScenario.launch<Task407RuntimeActivity>(Intent(context, Task407RuntimeActivity::class.java))
            scenario = activityScenario
            val mountFailure = AtomicReference<Task407FixedFailure?>()
            activityScenario.onActivity { activity ->
                try {
                    val fixture = Task407NormalRuntimeRoot(activity, material, loggerObservation)
                    root.set(fixture)
                    fixture.mount()
                } catch (safe: Task407FixedFailure) { mountFailure.set(safe.sanitizedCopy()) }
                catch (_: Throwable) { mountFailure.set(Task407FixedFailure("E407.Runtime.OriginalMountFailed")) }
            }
            mountFailure.get()?.let { throw it }
            body(task407Required(root.get(), "A407.Runtime.OriginalMountedFixture"))
        } catch (failure: Throwable) {
            failures.record(failure, "E407.Runtime.UnexpectedFailure")
        } finally {
            // Each step is attempted independently. No finally/use exception can replace primary.
            // Exact original references are captured before disposal in the same main action.
            failures.attempt("C407.Cleanup.MainCleanupActionFailed") {
                task407OnMain {
                    failures.attempt("C407.Cleanup.OriginalCustodyCaptureFailed") {
                        cleanupCustody = root.get()?.captureCleanupCustody()
                        custodyCaptured = true
                    }
                    failures.attempt("C407.Cleanup.CompositionDisposeFailed") {
                        root.get()?.closeComposition()
                        compositionDisposed = true // ONLY after disposal/removal returned.
                    }
                }
            }
            failures.attempt("C407.Cleanup.ScenarioCloseFailed") {
                scenario?.close()
                scenarioClosed = true // ONLY after ActivityScenario.close returned.
            }
            var originalPhysicalReturnsObserved = false
            var originalOwnershipReturnsObserved = false
            failures.attempt("C407.Cleanup.OriginalPhysicalReturnsUnconfirmed") {
                task407Require(custodyCaptured, "C407.Cleanup.OriginalCustodyUnavailable")
                cleanupCustody?.let { custody ->
                    task407AwaitOriginal("C407.Cleanup.ExactOriginalPhysicalReturnsRequired") { custody.physicalReturns() }
                }
                originalPhysicalReturnsObserved = true
            }
            // Attempt the independent renderer/application accounting even if physical waiting
            // failed. Neither result acknowledges the other or substitutes for an actual return.
            failures.attempt("C407.Cleanup.OriginalOwnershipReturnsUnconfirmed") {
                task407Require(custodyCaptured, "C407.Cleanup.OriginalCustodyUnavailable")
                cleanupCustody?.let { task407AwaitOwnership(it.probe, closed = true) }
                originalOwnershipReturnsObserved = true
            }
            failures.attempt("C407.Cleanup.LifecycleCloseUnreturned") {
                task407Require(compositionDisposed && scenarioClosed && originalPhysicalReturnsObserved &&
                    originalOwnershipReturnsObserved, "C407.Cleanup.LifecycleOrPhysicalCloseUnreturned")
                physicalReturned = true
            }
            failures.attempt("C407.Cleanup.AcceptedPublicationObservationFailed") {
                task407OnMain { root.get()?.requireNoObservationFailure() }
            }
            if (physicalReturned) {
                failures.attempt("C407.Cleanup.EpubReleaseFailed") { epub?.close() }
            } else if (epub != null) {
                // This is a failed cleanup, NOT success or a synthetic return. Keep material alive
                // for an unresolved original consumer; do not release it based on timeout.
                failures.record(Task407FixedFailure("C407.Cleanup.EpubRetainedForUnreturnedPhysicalOwners"),
                    "C407.Cleanup.EpubRetainedForUnreturnedPhysicalOwners")
            }
            try {
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("E407.ReaderPhases.V1", loggerObservation.readerPhasesJson())
                })
            } catch (_: Throwable) { /* Observation cannot replace success, failure or cleanup. */ }
            try { loggerObservation.restore() } catch (_: Throwable) { }
        }
        failures.throwIfAny()
    }
}
