package paige.navic.ui.screens.reader

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import paige.navic.reader.*

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
    fun originalRootRequiresExecutableOriginalPassiveProducerBeforeBootstrapDisplay() = withOriginalRoot { fixture ->
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
    fun originalRootPublishesBootstrapThenTwoImmediateSettledTurns() = withOriginalRoot { fixture ->
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
    fun cancelOriginalPendingBootstrapThenCloseReturnsExactPhysicalOwners() = withOriginalRoot { fixture ->
        // Prospective bounded profile: an ACTUAL registered Raster owner, not a pending request,
        // is captured and canceled in this SAME main-looper callback. A missed fast interval FAILS;
        // no producer/release is delayed and no sampled declaration is treated as an ACK.
        var original: Task407OriginalOwners? = null
        var work: ReaderActivatedRasterWork? = null
        task407AwaitOriginal("C407.BootstrapCancel.OriginalPendingPreparationRequired") {
            if (fixture.rootOrNull() == null || fixture.store().snapshot == null || fixture.material() == null ||
                fixture.engine() == null || fixture.passiveRuntime() == null) return@task407AwaitOriginal false
            val pending = fixture.registeredActiveWork() ?: return@task407AwaitOriginal false
            if (fixture.journal().active?.id != pending.lease.transitionId || !pending.isCurrent() ||
                pending.preparationFacts?.phase == ReaderPagePreparationPhase.Ready ||
                pending.lease.transitionId.operation != ReaderTransitionOperation.BootstrapNativePage) return@task407AwaitOriginal false
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

    private fun awaitBootstrap(fixture: Task407NormalRuntimeRoot): Startup {
        var displayed: ReaderOwnerAndInputPublicationSnapshot? = null
        task407AwaitOriginal("E407.Startup.RealProducerDrawAndAtomicPublicationRequired") {
            if (fixture.rootOrNull() == null || fixture.store().snapshot == null || fixture.runtime() == null ||
                fixture.material() == null || fixture.engine() == null || fixture.passiveRuntime() == null) return@task407AwaitOriginal false
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

    private fun withOriginalRoot(body: (Task407NormalRuntimeRoot) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        task407Require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O, "A407.Runtime.RealPixelCopyPlatformRequired")
        task407Require(context.packageName == Task407RuntimePackage, "A407.Runtime.SelfInstrumentedDisposablePackage")
        val failures = Task407FailureAccount()
        var epub: Task407PublicEpub? = null
        var scenario: ActivityScenario<Task407RuntimeActivity>? = null
        val root = AtomicReference<Task407NormalRuntimeRoot?>()
        var cleanupCustody: Task407OriginalCleanupCustody? = null
        var custodyCaptured = false
        var compositionDisposed = false
        var scenarioClosed = false
        var physicalReturned = false
        try {
            val material = Task407PublicEpub.create(context)
            epub = material
            val activityScenario = ActivityScenario.launch<Task407RuntimeActivity>(Intent(context, Task407RuntimeActivity::class.java))
            scenario = activityScenario
            val mountFailure = AtomicReference<Task407FixedFailure?>()
            activityScenario.onActivity { activity ->
                try {
                    val fixture = Task407NormalRuntimeRoot(activity, material)
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
        }
        failures.throwIfAny()
    }
}
