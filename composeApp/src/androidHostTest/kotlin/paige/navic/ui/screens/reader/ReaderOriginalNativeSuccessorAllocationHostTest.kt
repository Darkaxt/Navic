package paige.navic.ui.screens.reader

import android.os.Build
import android.webkit.WebView
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Bounded ORIGINAL retained-consumer prerequisite, not Native/Root successor acceptance.
 * The existing controlled MaterialFixture creates its original deck through commands and
 * exercises original ownership/listener/publication consumers. Its capture and platform
 * shadow are UNIT boundaries, not passive Chromium/Foliate/GL execution. Never install this
 * fixture's domain, deck, Ready or publication into the normal Native root to bypass its
 * producer prerequisite. Actual successor allocation/query/reentry remains a separate gate. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
    shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class,
        Task398CanonicalStoragePathShadow::class])
class ReaderOriginalNativeSuccessorAllocationHostTest {
    @Test
    fun originalReadablePredecessorKeepsItsExactRasterKeyAfterInvocationRetirement() {
        ReaderInstalledRasterPreparationOwnershipHostTest().withMaterialFixture(legacySelection = false) { f ->
            f.bootstrap()
            val predecessor = assertNotNull(f.barrier.snapshot, "B407.KeyedReadable.OriginalPublishedPredecessor")
            val deck = assertNotNull(f.deck, "B407.KeyedReadable.OriginalDeck")
            val work = f.work
            assertFalse(work.isCurrent(), "B407.KeyedReadable.PreparationInvocationAlreadyRetired")
            assertTrue(f.coordinator.releaseStateOf(work.lease.resourceKey) == ReaderTransitionResourceState.Released,
                "B407.KeyedReadable.OriginalRasterPhysicalReturnRecorded")
            assertTrue(f.curl.installedWorkFor(work.lease.resourceKey) === work,
                "B407.KeyedReadable.OriginalDeckRetainsExactWorkDespiteRasterReturn")
            assertTrue(f.curl.installedWorkFor(work.lease.resourceKey.copy(opaqueId = work.lease.rasterGeneration + 1L)) == null,
                "B407.KeyedReadable.ForeignKeyCannotBorrowReadablePredecessor")
            assertTrue(f.curl.installedDeckFor(predecessor.resource.key) === deck,
                "B407.KeyedReadable.PublicationKeyResolvesOriginalDeck")
            assertNotNull(f.curl.installedNativeCandidate(deck, null, 120, 80),
                "B407.KeyedReadable.ReturnedInvocationStillHasReadableOriginalConsumer")
            assertTrue(predecessor === f.barrier.snapshot && f.barrier.atomicCommitCount == 1,
                "B407.KeyedReadable.LookupCannotInventSuccessorPublication")
        }
    }

    @Test
    fun originalWaitingTurnCancellationRetainsReadablePredecessorWithoutSuccessorAllocation() {
        ReaderInstalledRasterPreparationOwnershipHostTest().withMaterialFixture(
            legacySelection = false, installedForeground = true) { f ->
            f.bootstrap()
            val predecessor = assertNotNull(f.barrier.snapshot, "B407.WaitingCustody.OriginalPredecessor")
            var waitingDeliveries = 0
            var retirements = 0
            val (source, call) = f.startOriginalInstalledTurn { admitted ->
                { origin, context, _ ->
                    lateinit var token: ReaderSemanticTransportRetirement
                    token = ReaderSemanticTransportRetirement {
                        admitted.retireOriginalSemanticTransportExact(token)
                        retirements++
                    }
                    val binding = context.bindTransportRetirement(origin, token)
                    val sourceBound = admitted.bindOriginalSemanticTransport(token) { waitingDeliveries++ }
                    assertTrue(binding == ReaderSemanticTransportBindingResult.Bound && sourceBound,
                        "B407.WaitingCustody.OneOriginalTransportTokenBound")
                    ReaderSemanticExecutableResult.Accepted
                }
            }
            assertTrue(source.work === f.curl.installedWorkFor(source.work.lease.resourceKey) && !source.work.isCurrent(),
                "B407.WaitingCustody.OriginalSourceUsesKeyedReadableNotLiveInvocation")
            f.dispatchOriginalDown(source)
            f.coordinator.enqueue(ReaderTransitionFact.Intent(call.origin.transitionId, ReaderCancelIntent))
            assertTrue(retirements == 1 && waitingDeliveries == 0,
                "B407.WaitingCustody.CancelFencesOriginalWaitingTransport")
            val retained = assertNotNull(f.barrier.snapshot, "B407.WaitingCustody.OriginalRetainedPublication")
            assertTrue(predecessor.owner == retained.owner && predecessor.resource.key == retained.resource.key &&
                predecessor.binding == retained.binding,
                "B407.WaitingCustody.CancelRetainsAtomicReadablePredecessor")
            f.surface.listener.onGestureCancelled(source.intent.gestureId.value, source.deck.textureGeneration)
            assertTrue(retirements == 1 && waitingDeliveries == 0,
                "B407.WaitingCustody.LateOriginalReturnCannotRestartSemanticConsumer")
            assertTrue(f.curl.installedWorkFor(source.work.lease.resourceKey) === source.work &&
                f.curl.installedDeckFor(predecessor.resource.key) === source.deck,
                "B407.WaitingCustody.OriginalReadableKeySurvivesWaitingCancellation")
            // This fixture records issue() commands, not the dedicated publication port.
            // The original barrier assertions above prove the retained physical owner.
            assertTrue(f.commands.filterIsInstance<ReaderTransitionCommand.AllocateMaterialBinding>()
                .singleOrNull()?.transitionId == source.work.lease.transitionId,
                "B407.WaitingCustody.NoInitialAllocationReplayOrSuccessorReservation")
        }
    }

    /** SOURCE/API availability RED only. An Engine capability is not a Native caller.
     * Stop here on absence: no query/plan reentry, counter, terminal-frame, publication
     * or subsequent-input assertion is qualified by this test. In particular, mounting
     * controlled MaterialFixture output into Root would bypass the missing producer. */
    @Test
    fun originalNativeSuccessorAllocationRequiresItsOriginalAllocationLayoutQueryRoute() {
        val capability = ReaderEngineCurrentDestinationQuerySource::class.java.methods.singleOrNull {
            it.name == "queryAllocationLayout" && it.parameterTypes.contentEquals(arrayOf(
                ReaderTransitionCommand.AllocateMaterialBinding::class.java,
                kotlin.jvm.functions.Function0::class.java, kotlin.jvm.functions.Function1::class.java)) &&
                it.returnType == ReaderSemanticTransportRetirement::class.java
        }
        assertNotNull(capability, "E407.NativeAllocation.SourceApi.OriginalEngineAllocationQueryRequired")
        val native = originalNativeSource().substringAfter("private class KomikkuReaderNativeViewerContainer(", "")
        assertTrue(native.isNotEmpty(), "E407.NativeAllocation.SourceApi.OriginalNativeOwnerRequired")
        assertTrue(Regex("\\.queryAllocationLayout\\s*\\(").containsMatchIn(native),
            "E407.NativeAllocation.SourceApi.NativeSuccessorAllocationQueryRouteMissing")
    }

    /** Initial-only SOURCE guard, not proof that a successor has acquired allocation
     * authority. Neither generic current-location capture nor the retired initial query
     * can replace the successor's original allocation-owned query delivery. */
    @Test
    fun originalBootstrapAllocationCaptureExplicitlyRejectsNonInitialOperationsAndBindings() {
        val capture = originalNativeFunction("captureInitialResolvedLayoutForAllocation")
        assertTrue(Regex("\\boperation\\s*!=\\s*(?:paige\\.navic\\.reader\\.)?ReaderTransitionOperation\\.BootstrapNativePage")
            .containsMatchIn(capture), "E407.NativeAllocation.Source.InitialOperationOnly")
        assertTrue(Regex("\\bexpectedBinding\\s*!is\\s*(?:paige\\.navic\\.reader\\.)?ReaderExpectedPresentationBinding\\.FoliateAuthoritativeInitial")
            .containsMatchIn(capture), "E407.NativeAllocation.Source.InitialExpectedBindingOnly")
        assertFalse(capture.contains("captureCurrentDestinationSemanticContext("),
            "E407.NativeAllocation.Source.GenericLocationCannotSubstituteForInitialAllocationAuthority")
    }

    /** Existing original initial producer SOURCE-order control. This qualifies neither
     * the unavailable successor route nor live Foliate plans. All complete chapter and
     * blocking checks must precede each of the three existing material-owner counters. */
    @Test
    fun originalInitialPlanChecksCompleteChapterAndBlockingWindowBeforeOriginalMaterialCounters() {
        val allocation = originalNativeFunction("createOriginalInitialMaterialAllocationPort")
        val counters = listOf("reserveActivatedPreparationGeneration(", "reserveActivatedRasterGeneration(",
            "reserveActivatedMaterialBinding(").map { call ->
            allocation.indexOf(call).also { assertTrue(it >= 0,
                "E407.NativeAllocation.Source.OriginalMaterialCounterOwnerRequired") }
        }
        val firstCounter = counters.minOrNull()!!
        val checks = listOf("plan.resolvedLayout != layout", "plan.currentChapterIndex != layout.sectionIndex",
            "plan.currentChapterPageStartIndex < 0", "plan.currentChapterPageCount != layout.pageCount",
            "plan.centerPageIndex.toLong() - plan.currentChapterPageStartIndex.toLong() != layout.pageIndex.toLong()",
            "plan.currentChapterPageStartIndex.toLong() + plan.currentChapterPageCount.toLong() > plan.pageCount.toLong()",
            "plan.blockingTargetsOrNull() == null")
        assertTrue(checks.all { check -> allocation.indexOf(check).let { it >= 0 && it < firstCounter } },
            "E407.NativeAllocation.Source.FullInitialPlanValidatedBeforeAnyMaterialCounter")
        assertTrue(counters.zipWithNext().all { (before, after) -> before < after },
            "E407.NativeAllocation.Source.OriginalPreparationRasterTextureReservationOrder")
    }

    /** UNIT callback-cell controls only. No Engine ledger/Root/Chromium is installed,
     * and no successful layout, semantic receipt or plan is fabricated. These exercise
     * the actual original continuation's synchronous admission/cancellation fence; they
     * cannot qualify Native retention-before-query or retention-before-plan outward calls. */
    @Test
    fun originalContinuationSynchronousAdmissionRetirementFencesLateDeliveryWithoutRetiringFreshToken() {
        val view = WebView(org.robolectric.RuntimeEnvironment.getApplication())
        try {
            var oldDeliveries = 0
            var freshDeliveries = 0
            lateinit var old: ReaderEngineCurrentDestinationContinuation
            old = ReaderEngineCurrentDestinationContinuation(view, "public-old-query", 1, "public-publication",
                "public-session", { old.retire(); true }, { oldDeliveries++ })
            val fresh = ReaderEngineCurrentDestinationContinuation(view, "public-fresh-query", 1,
                "public-publication", "public-session", { true }, { freshDeliveries++ })
            assertFalse(old.admits(), "E407.NativeAllocation.Unit.SynchronousAdmissionRetirementRejectsOldToken")
            old.retire()
            old.complete(ReaderCurrentDestinationQueryOutcome.Failed(ReaderCurrentDestinationQueryFailure.SubmissionRejected))
            assertTrue(old.retired && !old.active && oldDeliveries == 0 && fresh.admits() && fresh.active,
                "E407.NativeAllocation.Unit.OldRetirementAndLateDeliveryCannotBorrowFreshCallback")
            fresh.complete(ReaderCurrentDestinationQueryOutcome.Failed(ReaderCurrentDestinationQueryFailure.QueryCommandFailed))
            fresh.complete(ReaderCurrentDestinationQueryOutcome.Failed(ReaderCurrentDestinationQueryFailure.QueryCommandFailed))
            assertTrue(freshDeliveries == 1 && !fresh.active && oldDeliveries == 0,
                "E407.NativeAllocation.Unit.FreshCallbackConsumedExactlyOnce")
        } finally { view.destroy() }
    }

    @Test
    fun originalContinuationConsumesCallbackBeforeSynchronousRetirementAndDuplicateDelivery() {
        val view = WebView(org.robolectric.RuntimeEnvironment.getApplication())
        try {
            var deliveries = 0
            var consumedBeforeReentry = false
            lateinit var token: ReaderEngineCurrentDestinationContinuation
            val failure = ReaderCurrentDestinationQueryOutcome.Failed(ReaderCurrentDestinationQueryFailure.QueryCommandFailed)
            token = ReaderEngineCurrentDestinationContinuation(view, "public-query", 1, "public-publication",
                "public-session", { true }, {
                    deliveries++
                    consumedBeforeReentry = !token.active
                    token.retire()
                    token.complete(failure)
                })
            assertTrue(token.admits(), "E407.NativeAllocation.Unit.OriginalCallbackCellAdmitted")
            token.complete(failure)
            token.complete(failure)
            token.retire()
            assertTrue(consumedBeforeReentry && deliveries == 1 && token.retired && !token.active,
                "E407.NativeAllocation.Unit.ConsumedBeforeReentrantCancellationAndLateDuplicate")
        } finally { view.destroy() }
    }

    /** ORIGINAL coordinator/FIFO and Curl/BundleSource counters with CONTROLLED UNIT
     * semantic destination and preparation scalar. This is not Native/Root, live Foliate,
     * passive capture or GL qualification. Stop at the genuine raster command: no successor
     * Ready, deck, target, frame, ACK, physical return or publication is supplied. */
    @Test
    fun originalCurrentProfileSuccessorMustConsumeOriginalAllocationAndReachRasterCommand() {
        ReaderInstalledRasterPreparationOwnershipHostTest().withMaterialFixture(legacySelection = false) { f ->
            f.bootstrap()
            val predecessor = assertNotNull(f.barrier.snapshot, "A407.Profile.OriginalReadablePredecessor")
            val bootstrapLease = f.work.lease
            assertTrue(bootstrapLease.binding == predecessor.binding &&
                predecessor.binding.profileGeneration > 0L && !f.work.isCurrent(),
                "A407.Profile.OriginalBootstrapLeaseAndRetainedPublication")
            val before = f.commands.size
            f.beginControlledUnitProfileSuccessor()
            val issued = assertNotNull(f.commands.drop(before)
                .filterIsInstance<ReaderTransitionCommand.AllocateMaterialBinding>().singleOrNull(),
                "A407.Profile.OneOriginalSuccessorAllocationCommand")
            val original = assertNotNull(f.unitOriginalSuccessorAllocation, "A407.Profile.OriginalCounterOutput")
            assertTrue(issued.transitionId.operation == ReaderTransitionOperation.ExternalSemanticRelocation &&
                issued.transitionId.expectedBinding == ReaderExpectedPresentationBinding.Exact(issued.binding) &&
                issued.binding.profileGeneration == predecessor.binding.profileGeneration,
                "A407.Profile.AuthenticOriginalCommandRetainsResolvedSourceProfile")
            assertTrue(original.transitionId == issued.transitionId && original.requestedBinding === issued.binding &&
                original.allocatedBinding.profileGeneration > issued.binding.profileGeneration &&
                original.rasterGeneration > bootstrapLease.rasterGeneration &&
                original.textureGeneration > assertNotNull(bootstrapLease.binding.textureGeneration,
                    "A407.Profile.OriginalBootstrapTextureGeneration") &&
                original.preparationGeneration == Math.incrementExact(bootstrapLease.preparationGeneration),
                "A407.Profile.OriginalProfileRasterTextureCountersWithControlledUnitPreparation")
            assertTrue(f.barrier.snapshot?.owner == predecessor.owner &&
                f.barrier.snapshot?.resource == predecessor.resource &&
                f.barrier.snapshot?.binding == predecessor.binding,
                "A407.Profile.ProfileQualificationIsNotSuccessorPublication")
            assertTrue(f.commands.drop(before).none { it is ReaderTransitionCommand.ReserveDeck ||
                it is ReaderTransitionCommand.PrepareFrameTarget || it is ReaderTransitionCommand.RequestFramePresentation },
                "A407.Profile.NoSuccessorMaterialReadinessOrPresentation")
            assertTrue(f.coordinator.snapshot().maxAdvanceDepth == 1,
                "A407.Profile.OneOriginalFifoDrain")
            val next = assertNotNull(f.commands.drop(before)
                .filterIsInstance<ReaderTransitionCommand.RequestRasterPreparation>().singleOrNull(),
                "A407.Profile.OriginalAllocatedProfileCannotStrandResolvedSuccessor")
            assertTrue(next.transitionId == issued.transitionId && next.allocation === original &&
                next.binding == original.allocatedBinding,
                "A407.Profile.ExactOriginalAllocationFlowsToOriginalRasterCommand")
        }
    }

    private fun originalNativeSource(): String {
        val file = File("src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt")
        assertTrue(file.isFile, "E407.NativeAllocation.SourceApi.OriginalNativeSourceRequired")
        // Source only; remove comments so a proposed route in paperwork cannot pass RED.
        return file.readText().replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("(?m)//.*$"), "")
    }

    private fun originalNativeFunction(name: String): String {
        val source = originalNativeSource()
        val start = source.indexOf("\n\tfun $name(")
        assertTrue(start >= 0, "E407.NativeAllocation.SourceApi.OriginalSourceMethodRequired")
        val next = Regex("(?m)^\\t(?:private |internal )?fun ").find(source, start + 2)?.range?.first
            ?: source.length
        return source.substring(start, next)
    }
}
