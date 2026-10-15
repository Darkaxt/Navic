package paige.navic.ui.screens.reader

import android.os.Build
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.MotionEvent
import android.webkit.WebView
import android.widget.FrameLayout
import karacken.curl.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.robolectric.Robolectric
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.reflector.Direct
import org.robolectric.util.reflector.ForType
import org.robolectric.util.reflector.Reflector.reflector
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Original preparation custody only. Test installation is not production composition proof. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
class ReaderInstalledRasterPreparationOwnershipHostTest {
	@Test
	fun initialBasisWorkIsBoundToOriginalAllocationAndCannotOutliveItsSourceOrRetirement() {
		val f = Fixture()
		val semantic = f.lease.binding.copy(profileGeneration = 0L, preparationGeneration = null,
			rasterGeneration = null, textureGeneration = null)
		val allocation = assertNotNull(f.lease.allocation).copy(requestedBinding = semantic)
		val lease = f.lease.copy(allocation = allocation)
		val basis = ReaderInitialCurrentDestinationCaptureBasis("public-synthetic-initial-query",
			assertNotNull(semantic.destinationCommitIdentity), ReaderFoliateResolvedLayout(11L, 13L, 17L, 0, 0, 8, 800.0, 600.0))
		var sourceCurrent = true
		val work = ReaderActivatedRasterWork(f.domain, f.store, f.snapshot, lease, basis) { f.current && sourceCurrent }
		assertSame(basis, work.initialCaptureBasis)
		assertTrue(work.isCurrent())
		sourceCurrent = false
		assertFalse(work.isCurrent(), "Semantic metadata cannot outlive the original physical/source predicate")
		sourceCurrent = true
		assertTrue(work.isCurrent())
		work.retire()
		assertFalse(work.isCurrent())
		assertNull(work.admit(f.installedRegistry(), f.physicalDescriptor()))
		assertTrue(runCatching {
			ReaderActivatedRasterWork(f.domain, f.store, f.snapshot, lease,
				basis.copy(destinationCommitIdentity = basis.destinationCommitIdentity.copy(commitSequence = 2L))) { true }
		}.isFailure)
		assertTrue(runCatching { ReaderActivatedRasterWork(f.domain, f.store, f.snapshot, f.lease, basis) { true } }.isFailure,
			"Initial metadata requires the original requested allocation binding, not a legacy binding-only lease")
	}

	@Test
	fun installedWorkRequiresFinalizedOriginalDomainAndExactInstallation() {
		val f = Fixture(finalize = false)
		assertEquals(ReaderRasterPreparationStartOutcome.Fenced, f.start())
		assertEquals(0, f.starts)
		assertEquals(ReaderPortCommandResult.Accepted, f.owner.finalizeActivatedHandoff(f.domain))
		val foreign = Fixture()
		assertEquals(ReaderRasterPreparationStartOutcome.Fenced, f.start(snapshot = foreign.snapshot))
		assertEquals(0, f.starts)
		assertEquals(ReaderRasterPreparationStartOutcome.Accepted, f.start())
		assertEquals(1, f.starts)
	}

	@Test
	fun installedCompletionIsOnceOnlyAndCannotReopenLegacyAdmission() {
		val f = Fixture()
		assertEquals(ReaderRasterPreparationStartOutcome.Accepted, f.start())
		val complete = assertNotNull(f.complete)
		complete { f.deliveries++ }
		complete { f.deliveries++ }
		assertEquals(1, f.deliveries)
		assertTrue(assertNotNull(f.owner.snapshotFrozenOwnership()).resources.isEmpty())
		assertFalse(f.owner.start(f.descriptor(), { true }, { false }) { f.starts++; true })
		assertEquals(1, f.starts)
		assertTrue(f.owner.restoreAfterTransitionActivation(f.domain) is ReaderPortCommandResult.Rejected)
		assertEquals(ReaderPortCommandResult.Accepted, f.release())
		assertEquals(1, f.confirmations)
		assertEquals(0, f.cancellations, "Terminal work needs accounting, not a second physical cancellation")
	}

	@Test
	fun cancellationWaitsForOriginalReleaseAndSuppressesLateCompletion() {
		val f = Fixture()
		assertEquals(ReaderRasterPreparationStartOutcome.Accepted, f.start())
		assertEquals(ReaderPortCommandResult.Accepted, f.release())
		assertEquals(1, f.cancellations)
		assertEquals(0, f.confirmations)
		assertNotNull(f.complete).invoke { f.deliveries++ }
		assertEquals(0, f.deliveries)
		assertTrue(f.release() is ReaderPortCommandResult.Rejected)
		val released = assertNotNull(f.released)
		released(); released()
		assertEquals(1, f.confirmations)
		assertNotNull(f.complete).invoke { f.deliveries++ }
		assertEquals(0, f.deliveries)
	}

	@Test
	fun rejectedCancellationCannotConvertLogicalCompletionIntoPhysicalRelease() {
		val f = Fixture()
		assertEquals(ReaderRasterPreparationStartOutcome.Accepted, f.start())
		f.rejectCancellation = true
		f.completeDuringCancellation = true
		assertTrue(f.release() is ReaderPortCommandResult.Rejected)
		assertEquals(0, f.deliveries)
		assertEquals(0, f.confirmations)
		f.rejectCancellation = false
		f.completeDuringCancellation = false
		assertEquals(ReaderPortCommandResult.Accepted, f.release())
		assertEquals(2, f.cancellations, "A logical terminal callback during cancellation is not its physical return")
		assertEquals(0, f.confirmations, "A retry must retain custody until the captured release callback returns")
		assertNotNull(f.released).invoke()
		assertEquals(1, f.confirmations)
	}

	@Test
	fun logicalCompletionAfterRejectedCancellationStillRequiresPhysicalRetry() {
		val f = Fixture()
		assertEquals(ReaderRasterPreparationStartOutcome.Accepted, f.start())
		f.rejectCancellation = true
		assertTrue(f.release() is ReaderPortCommandResult.Rejected)
		assertNotNull(f.complete).invoke { f.deliveries++ }
		assertEquals(0, f.deliveries)
		f.rejectCancellation = false
		assertEquals(ReaderPortCommandResult.Accepted, f.release())
		assertEquals(2, f.cancellations)
		assertEquals(0, f.confirmations)
		assertNotNull(f.released).invoke()
		assertEquals(1, f.confirmations)
	}

	@Test
	fun retiredCommandCannotStartOrPublishButRetainsPhysicalReleaseCustody() {
		val f = Fixture()
		f.current = false
		assertEquals(ReaderRasterPreparationStartOutcome.Fenced, f.start())
		assertEquals(0, f.starts)
		f.current = true
		assertEquals(ReaderRasterPreparationStartOutcome.Accepted, f.start())
		f.current = false
		assertNotNull(f.complete).invoke { f.deliveries++ }
		assertEquals(0, f.deliveries)
		assertEquals(ReaderPortCommandResult.Accepted, f.release())
		assertEquals(1, f.confirmations)
	}

	@Test
	fun synchronousCallbackThenRejectedPhysicalStartCannotPublish() {
		val f = Fixture()
		assertEquals(ReaderRasterPreparationStartOutcome.Rejected,
			f.start(accept = false, synchronous = true))
		assertEquals(0, f.deliveries)
		assertTrue(f.release() is ReaderPortCommandResult.Rejected)
		assertEquals(0, f.cancellations)
	}

	@Test
	fun duplicateLeaseCannotLaunchAnotherPhysicalPreparation() {
		val f = Fixture()
		assertEquals(ReaderRasterPreparationStartOutcome.Accepted, f.start())
		assertEquals(ReaderRasterPreparationStartOutcome.Fenced, f.start())
		assertEquals(1, f.starts)
	}

	@Test
	fun partialInstalledReturnRetriesOnlyRejectedChildAfterRetirement() {
		// Both callback orders use the original registry records: a returned child, an
		// accepted-but-pending child and a genuinely rejected cancellation.
		for (pendingReturnsBeforeRetry in listOf(false, true)) {
			val f = Fixture()
			val registry = f.installedRegistry()
			var returnedCalls = 0
			var pendingCalls = 0
			var rejectedCalls = 0
			var reject = true
			var originalConfirmations = 0
			var retryConfirmations = 0
			lateinit var returned: ReaderExactPhysicalOwnerRegistry.Owner
			returned = assertNotNull(f.work.admit(registry, f.physicalDescriptor()) {
				returnedCalls++
				registry.complete(returned)
				true
			})
			val pending = assertNotNull(f.work.admit(registry, f.physicalDescriptor()) {
				pendingCalls++
				true
			})
			val rejected = assertNotNull(f.work.admit(registry, f.physicalDescriptor()) {
				rejectedCalls++
				!reject
			})

			f.work.retire()
			assertFalse(f.work.isCurrent())
			assertEquals(ReaderPortCommandResult.Accepted,
				registry.releaseInstalledPhysicalOwners(f.work) { originalConfirmations++ },
				"Admitted child cancellation/return is an accepted partial physical release")
			assertEquals(listOf(1, 1, 1), listOf(returnedCalls, pendingCalls, rejectedCalls))
			assertEquals(0, originalConfirmations)
			if (pendingReturnsBeforeRetry) assertTrue(registry.complete(pending))
			reject = false
			assertEquals(ReaderPortCommandResult.Accepted,
				registry.releaseInstalledPhysicalOwners(f.work) { retryConfirmations++ },
				"A partial physical return must retain accepted children and retry only the rejected child")
			assertEquals(listOf(1, 1, 2), listOf(returnedCalls, pendingCalls, rejectedCalls))
			assertEquals(0, retryConfirmations, "Cancellation acceptance is not physical return")
			assertTrue(registry.complete(rejected))
			assertEquals(if (pendingReturnsBeforeRetry) 1 else 0, originalConfirmations)
			if (!pendingReturnsBeforeRetry) assertTrue(registry.complete(pending))
			assertEquals(1, originalConfirmations, "Retry cannot replace an accepted aggregate's original callback")
			assertEquals(0, retryConfirmations)
			assertFalse(registry.complete(returned))
			assertFalse(registry.complete(pending))
			assertFalse(registry.complete(rejected))
			assertEquals(1, originalConfirmations)
		}
	}

	@Test
	fun allRejectedInstalledReleaseHasNoEffectAndCanRetryWithFreshCallback() {
		val f = Fixture()
		val registry = f.installedRegistry()
		var reject = true
		var cancellations = 0
		var rejectedConfirmations = 0
		var acceptedConfirmations = 0
		val child = assertNotNull(f.work.admit(registry, f.physicalDescriptor()) {
			cancellations++
			!reject
		})
		f.work.retire()
		assertTrue(registry.releaseInstalledPhysicalOwners(f.work) { rejectedConfirmations++ }
			is ReaderPortCommandResult.Rejected)
		assertEquals(0, rejectedConfirmations)
		reject = false
		assertEquals(ReaderPortCommandResult.Accepted,
			registry.releaseInstalledPhysicalOwners(f.work) { acceptedConfirmations++ })
		assertEquals(2, cancellations)
		assertEquals(0, acceptedConfirmations)
		assertTrue(registry.complete(child))
		assertEquals(1, acceptedConfirmations)
		assertEquals(0, rejectedConfirmations)
	}

	@Test
	fun installedReleaseReentryCannotReplaceOriginalPhysicalReturnCallback() {
		val f = Fixture()
		val registry = f.installedRegistry()
		var cancellations = 0
		var originalConfirmations = 0
		var reentrantConfirmations = 0
		var reentrantResult: ReaderPortCommandResult? = null
		val owner = assertNotNull(f.work.admit(registry, f.physicalDescriptor()) {
			cancellations++
			reentrantResult = registry.releaseInstalledPhysicalOwners(f.work) { reentrantConfirmations++ }
			true
		})
		f.work.retire()
		assertEquals(ReaderPortCommandResult.Accepted,
			registry.releaseInstalledPhysicalOwners(f.work) { originalConfirmations++ })
		assertTrue(reentrantResult is ReaderPortCommandResult.Rejected)
		assertEquals(1, cancellations)
		assertEquals(0, originalConfirmations)
		assertEquals(0, reentrantConfirmations)
		assertTrue(registry.complete(owner))
		assertFalse(registry.complete(owner))
		assertEquals(1, originalConfirmations)
		assertEquals(0, reentrantConfirmations)
	}

	@Test
	fun retiredInstalledWorkWaitsForPhysicalReturnDespiteLateLogicalCompletion() {
		val f = Fixture()
		assertEquals(ReaderRasterPreparationStartOutcome.Accepted, f.start(isCurrent = f.work::isCurrent))
		f.work.retire()
		assertFalse(f.work.isCurrent())
		assertEquals(ReaderPortCommandResult.Accepted, f.release())
		assertEquals(1, f.cancellations)
		assertNotNull(f.complete).invoke { f.deliveries++ }
		assertEquals(0, f.deliveries)
		assertEquals(0, f.confirmations)
		val physicallyReturned = assertNotNull(f.released)
		physicallyReturned(); physicallyReturned()
		assertEquals(1, f.confirmations)
	}

	@Test
	fun retainedWorkLookupUsesOriginalExactLeaseUntilAggregateReturn() {
		val f = Fixture()
		assertNull(f.owner.installedWorkFor(f.lease.resourceKey))
		assertEquals(ReaderRasterPreparationStartOutcome.Accepted, f.start())
		assertSame(f.work, f.owner.installedWorkFor(f.lease.resourceKey))
		assertNull(f.owner.installedWorkFor(f.lease.resourceKey.copy(opaqueId = 18L)))
		f.work.retire()
		assertSame(f.work, f.owner.installedWorkFor(f.lease.resourceKey), "Retirement fences commands, not return custody")
		assertEquals(ReaderPortCommandResult.Accepted, f.release())
		assertNotNull(f.released).invoke()
		assertSame(f.work, f.owner.installedWorkFor(f.lease.resourceKey), "Other physical children may still be returning")
		f.owner.forgetReturnedInstalled(f.work)
		assertNull(f.owner.installedWorkFor(f.lease.resourceKey))
	}

	@Test
	fun aggregateReturnKeepsAcceptedCallbackAndRetriesOnlyRejectedChildren() {
		val progress = ReaderInstalledRasterReturnProgress(3)
		val calls = IntArray(3)
		val returns = arrayOfNulls<() -> Unit>(3)
		var reject = true
		var original = 0
		var replacement = 0
		val actions: List<(() -> Unit) -> ReaderPortCommandResult> = List(3) { index -> { returned ->
			calls[index]++
			returns[index] = returned
			when {
				index == 0 -> { returned(); ReaderPortCommandResult.Accepted }
				index == 2 && reject -> ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
				else -> ReaderPortCommandResult.Accepted
			}
		} }
		assertEquals(ReaderPortCommandResult.Accepted, progress.request(actions) { original++ })
		assertEquals(0, original)
		reject = false
		assertEquals(ReaderPortCommandResult.Accepted, progress.request(actions) { replacement++ })
		assertEquals(listOf(1, 1, 2), calls.toList())
		assertNotNull(returns[2]).invoke()
		assertEquals(0, original)
		assertNotNull(returns[1]).invoke()
		assertNotNull(returns[1]).invoke()
		assertEquals(1, original)
		assertEquals(0, replacement)
	}

	@Test
	fun allRejectedAggregateCanRetryAndReentrantIssuanceCannotReplaceCallback() {
		val progress = ReaderInstalledRasterReturnProgress(1)
		var reject = true
		var original = 0
		var replacement = 0
		var reentrant: ReaderPortCommandResult? = null
		var returned: (() -> Unit)? = null
		lateinit var actions: List<(() -> Unit) -> ReaderPortCommandResult>
		actions = listOf({ callback ->
			returned = callback
			reentrant = progress.request(actions) { replacement++ }
			if (reject) ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
			else ReaderPortCommandResult.Accepted
		})
		assertTrue(progress.request(actions) { replacement++ } is ReaderPortCommandResult.Rejected)
		assertTrue(reentrant is ReaderPortCommandResult.Rejected)
		reject = false
		assertEquals(ReaderPortCommandResult.Accepted, progress.request(actions) { original++ })
		assertTrue(reentrant is ReaderPortCommandResult.Rejected)
		assertNotNull(returned).invoke()
		assertEquals(1, original)
		assertEquals(0, replacement)
	}

	@Test
	fun throwingInstalledPreparationCancellationRetainsOriginalPhysicalReturnCallback() {
		val f = Fixture()
		assertEquals(ReaderRasterPreparationStartOutcome.Accepted, f.start())
		f.throwCancellation = true
		assertFailsWith<IllegalStateException> { f.release() }
		val capturedReturn = assertNotNull(f.released)
		assertEquals(1, f.cancellations)
		assertEquals(0, f.confirmations)
		assertNotNull(f.complete).invoke { f.deliveries++ }
		assertEquals(0, f.deliveries)
		f.throwCancellation = false
		assertTrue(f.release() is ReaderPortCommandResult.Rejected,
			"An ambiguous cancellation retains the original in-flight attempt, not no-effect rollback")
		assertEquals(1, f.cancellations)
		capturedReturn(); capturedReturn()
		assertEquals(1, f.confirmations)
		assertEquals(0, f.deliveries)
	}

	@Test
	fun aggregateThrowRetainsOriginalCustodyAndReachesReleaseAdapterAsAmbiguous() {
		val f = Fixture()
		val ledger = ReaderTransitionReleaseLedger()
		val registration = assertNotNull(ledger.register(f.lease.resourceKey,
			f.lease.transitionId.readerSessionGeneration, f.lease.transitionId.coordinatorEpoch))
		val command = assertNotNull(ledger.requestRelease(
			ReaderResourceReleaseIssuer.Transition(f.lease.transitionId), registration))
		val progress = ReaderInstalledRasterReturnProgress(1)
		val failure = IllegalStateException("aggregate-child-ambiguous")
		var calls = 0
		var originalReturn: (() -> Unit)? = null
		var retryConfirmations = 0
		val facts = mutableListOf<ReaderTransitionFact>()
		val actions: List<(() -> Unit) -> ReaderPortCommandResult> = listOf({ returned ->
			calls++
			originalReturn = returned
			throw failure // Physical cancellation may have started; its captured return arrives later.
		})

		ReaderPhysicalReleaseCommandAdapter().dispatchGeneric(command, invoke = { confirmation ->
			progress.request(actions) { confirmation(ReaderTransitionFact.ResourceReleased(command.identity)) }
		}, onFact = facts::add)
		val threw = assertIs<ReaderTransitionFact.ReleaseCommandThrew>(facts.single())
		assertEquals(ReaderReleaseCommandAmbiguityReason.ThrowAfterPhysicalEffectMayHaveOccurred, threw.reason)
		assertTrue(ledger.recordAmbiguousFailure(threw))
		assertTrue(facts.none { it is ReaderTransitionFact.ReleaseCommandRejected })
		val capturedReturn = assertNotNull(originalReturn)
		assertEquals(ReaderPortCommandResult.Accepted, progress.request(actions) { retryConfirmations++ })
		assertEquals(1, calls, "Ambiguity is not a proven-no-effect rejection eligible for reissue")
		assertEquals(0, retryConfirmations)
		capturedReturn()
		val released = assertIs<ReaderTransitionFact.ResourceReleased>(facts[1])
		assertTrue(ledger.confirmReleased(released))
		assertEquals(ReaderReleasePortContractViolationReason.LateCallbackAfterAmbiguousFailure,
			assertIs<ReaderTransitionFact.ReleasePortContractViolated>(facts[2]).reason)
		capturedReturn()
		assertEquals(3, facts.size)
		assertEquals(0, retryConfirmations, "A thrown invocation retains its original physical-return callback")
	}

	@Test
	fun aggregateThrowAfterReturnedSiblingKeepsAccountingAndRetriesOnlyRejectedSibling() {
		val progress = ReaderInstalledRasterReturnProgress(3)
		val failure = IllegalStateException("aggregate-partial-ambiguous")
		val calls = IntArray(3)
		val returns = arrayOfNulls<() -> Unit>(3)
		var reject = true
		var original = 0
		var replacement = 0
		val actions: List<(() -> Unit) -> ReaderPortCommandResult> = List(3) { index -> { returned ->
			calls[index]++
			returns[index] = returned
			when (index) {
				0 -> { returned(); ReaderPortCommandResult.Accepted }
				1 -> throw failure
				else -> if (reject) ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
					else ReaderPortCommandResult.Accepted
			}
		} }
		assertSame(failure, assertFailsWith<IllegalStateException> { progress.request(actions) { original++ } })
		assertEquals(listOf(1, 1, 1), calls.toList(), "A sibling throw cannot abandon bounded child cancellation accounting")
		assertEquals(0, original)
		val ambiguousReturn = assertNotNull(returns[1])
		reject = false
		assertEquals(ReaderPortCommandResult.Accepted, progress.request(actions) { replacement++ })
		assertEquals(listOf(1, 1, 2), calls.toList())
		assertSame(ambiguousReturn, returns[1])
		assertNotNull(returns[2]).invoke()
		assertEquals(0, original, "Sibling return cannot stand in for the thrown child's physical return")
		ambiguousReturn(); ambiguousReturn()
		assertEquals(1, original)
		assertEquals(0, replacement)
	}

	@Test
	fun nestedAggregateAmbiguityRetriesOnlyInnerRejectedChild() {
		val inner = ReaderInstalledRasterReturnProgress(3)
		val outer = ReaderInstalledRasterReturnProgress(1)
		val failure = IllegalStateException("nested-child-ambiguous")
		val calls = IntArray(3)
		val returns = arrayOfNulls<() -> Unit>(3)
		var reject = true
		var original = 0
		var replacement = 0
		val innerActions: List<(() -> Unit) -> ReaderPortCommandResult> = List(3) { index -> { returned ->
			calls[index]++
			returns[index] = returned
			when (index) {
				0 -> { returned(); ReaderPortCommandResult.Accepted }
				1 -> throw failure
				else -> if (reject) ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
					else ReaderPortCommandResult.Accepted
			}
		} }
		val outerActions: List<(() -> Unit) -> ReaderPortCommandResult> = listOf({ returned ->
			inner.request(innerActions, onReleased = returned)
		})
		assertSame(failure, assertFailsWith<IllegalStateException> {
			outer.request(outerActions, retryableAggregates = setOf(0)) { original++ }
		})
		assertEquals(listOf(1, 1, 1), calls.toList())
		val ambiguousReturn = assertNotNull(returns[1])
		reject = false
		assertEquals(ReaderPortCommandResult.Accepted,
			outer.request(outerActions, retryableAggregates = setOf(0)) { replacement++ })
		assertEquals(listOf(1, 1, 2), calls.toList())
		assertSame(ambiguousReturn, returns[1])
		assertNotNull(returns[2]).invoke()
		assertEquals(0, original)
		ambiguousReturn(); ambiguousReturn()
		assertEquals(1, original)
		assertEquals(0, replacement)
	}

	@Test
	fun preStartReadinessRegistrationOwnsExactWorkBeforeCallerCancellation() {
		val f = Fixture()
		val readiness = ReadinessFixture()
		var started = 0
		val settlement = f.owner.startInstalledWithOutcome(
			f.domain, f.store, f.snapshot, f.lease, f.work::isCurrent,
			releasePhysicalPreparation = { returned -> readiness.adapter.cancelInstalledReadiness(f.work, returned) },
			startPhysicalPreparation = { completed ->
				readiness.adapter.awaitInstalledReadiness(f.work) { completed { started++ } }
			}, work = f.work
		)
		settlement.publishLogicalOutcome { assertEquals(ReaderRasterPreparationStartOutcome.Accepted, it) }
		assertSame(f.work, f.owner.installedWorkFor(f.lease.resourceKey),
			"The readiness consumer must be physically registered before caller acceptance")
		assertEquals(0, started)
		f.work.retire()
		assertEquals(ReaderPortCommandResult.Accepted, f.release())
		assertEquals(1, f.confirmations, "Actual synchronous readiness-consumer removal can return custody")
		readiness.runtime.ready = true
		readiness.adapter.onOriginalRuntimeReady()
		readiness.adapter.onOriginalRuntimeReady()
		assertEquals(0, started, "A removed original consumer cannot start raster work later")
		assertFalse(readiness.adapter.cancelInstalledReadiness(f.work) { f.confirmations++ })
		assertEquals(1, f.confirmations)
		assertEquals(0, readiness.runtime.physicalOperations,
			"Removing an unstarted readiness consumer must not manufacture a passive capture cancellation")
	}

	@Test
	fun preStartReadinessReturnWaitsForExecutingOriginalCallbackAndRejectsReplacement() {
		val f = Fixture()
		val foreign = Fixture()
		val readiness = ReadinessFixture()
		var readyCalls = 0
		var returns = 0
		var replacementReturns = 0
		assertTrue(readiness.adapter.awaitInstalledReadiness(f.work) {
			readyCalls++
			f.work.retire()
			assertTrue(readiness.adapter.cancelInstalledReadiness(f.work) { returns++ })
			assertEquals(0, returns, "An on-stack callback has not physically returned")
			assertFalse(readiness.adapter.cancelInstalledReadiness(f.work) { replacementReturns++ })
		})
		assertFalse(readiness.adapter.awaitInstalledReadiness(f.work) { readyCalls += 100 })
		assertFalse(readiness.adapter.cancelInstalledReadiness(foreign.work) { returns += 100 })
		readiness.adapter.onOriginalRuntimeReady()
		assertEquals(0, readyCalls, "Only an actually ready original session may deliver")
		readiness.runtime.ready = true
		readiness.adapter.onOriginalRuntimeReady()
		assertEquals(1, readyCalls)
		assertEquals(1, returns, "Confirmation follows the original callback's physical return")
		assertEquals(0, replacementReturns)
		readiness.adapter.onOriginalRuntimeReady()
		assertEquals(1, readyCalls)
		assertEquals(1, returns)
		assertEquals(0, readiness.runtime.physicalOperations)
	}

	@Test
	fun retiredPreStartReadinessCannotPublishAndStillReturnsThroughExactRemoval() {
		val f = Fixture()
		val readiness = ReadinessFixture()
		var started = 0
		var returns = 0
		assertTrue(readiness.adapter.awaitInstalledReadiness(f.work) { started++ })
		f.work.retire()
		readiness.runtime.ready = true
		readiness.adapter.onOriginalRuntimeReady()
		assertEquals(0, started)
		assertTrue(readiness.adapter.cancelInstalledReadiness(f.work) { returns++ },
			"Stale command currentness cannot erase accepted original return custody")
		assertEquals(1, returns)
		assertFalse(readiness.adapter.cancelInstalledReadiness(f.work) { returns++ })
		assertFalse(readiness.adapter.awaitInstalledReadiness(f.work) { started++ })
		assertEquals(1, returns)
		assertEquals(0, started)
	}

	/** Qualification-only additions. The public fixture is not a GL/Foliate display ACK. */
	@Test
	fun installedCancellationJoinsExecutingReadinessWithoutInventingAnUnstartedCapture() {
		val f = Fixture()
		val readiness = ReadinessFixture()
		var readyCalls = 0
		var originalReturns = 0
		var replacementReturns = 0
		assertTrue(readiness.adapter.awaitInstalledReadiness(f.work) {
			readyCalls++
			f.work.retire()
			assertTrue(readiness.adapter.cancelInstalled(f.work) { originalReturns++ })
			assertEquals(0, originalReturns, "Capture inactivity is not the executing readiness callback's return")
			assertFalse(readiness.adapter.cancelInstalled(f.work) { replacementReturns++ })
		})
		readiness.runtime.ready = true
		readiness.adapter.onOriginalRuntimeReady()
		assertEquals(1, readyCalls)
		assertEquals(1, originalReturns)
		assertEquals(0, replacementReturns)
		assertEquals(0, readiness.runtime.physicalOperations)
		assertFalse(readiness.adapter.cancelInstalled(f.work) { originalReturns++ })
	}

	@Test
	fun closeInsideOriginalReadinessCannotConfirmUntilThatCallbackReturns() {
		val f = Fixture()
		val readiness = ReadinessFixture()
		var originalReturns = 0
		var lateStarts = 0
		assertTrue(readiness.adapter.awaitInstalledReadiness(f.work) {
			f.work.retire()
			assertTrue(readiness.adapter.cancelInstalledReadiness(f.work) { originalReturns++ })
			readiness.adapter.close()
			assertEquals(0, originalReturns)
			assertFalse(readiness.adapter.awaitInstalledReadiness(f.work) { lateStarts++ })
		})
		readiness.runtime.ready = true
		readiness.adapter.onOriginalRuntimeReady()
		assertEquals(1, originalReturns)
		assertEquals(1, readiness.runtime.destructions)
		readiness.adapter.onOriginalRuntimeReady()
		assertEquals(0, lateStarts)
		assertFalse(readiness.adapter.cancelInstalledReadiness(f.work) { originalReturns++ })
	}

	@Test
	fun publicCanonicalWordSyncBoundariesReuseOriginalInstalledRasterMaterial() {
		val f = Fixture(rasterGeneration = 1L)
		val source = ReaderPageTurnBundleSource()
		assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(f.domain))
		assertEquals(ReaderPortCommandResult.Accepted, source.finalizeActivatedHandoff(f.domain))
		val command = ReaderTransitionCommand.AllocateMaterialBinding(f.lease.transitionId,
			f.lease.binding.copy(preparationGeneration = null, rasterGeneration = null, textureGeneration = null))
		assertEquals(f.lease.rasterGeneration, source.reserveActivatedRasterGeneration(command, f.domain))
		val geometry = ReaderPageTurnCaptureGeometry(
			viewportWidth = 120.0, viewportHeight = 80.0, mode = ReaderPageTurnLayoutMode.Single,
			pages = listOf(ReaderPageTurnPageRect(ReaderPageTurnPageRole.Full, 0.0, 0.0, 120.0, 80.0)))
		val raster = android.graphics.Bitmap.createBitmap(120, 80, android.graphics.Bitmap.Config.ARGB_8888)
			.apply { setHasAlpha(false) }
		val snapshot = assertNotNull(source.cacheCurrentSnapshot(1, ReaderPageTurnTransitionKind.PortraitSlide,
			ReaderPageTurnCaptureResult(raster, android.graphics.Rect(0, 0, 120, 80), geometry, 0L),
			generation = f.lease.rasterGeneration, persist = false, work = f.work))
		snapshot.retain()
		val image = assertNotNull(readerPlayLikeCurlCopyRetainedFoliateLeaf(snapshot, ReaderPlayLikeCurlFoliateLeaf.Full))
		val profile = ReaderPlayLikeCurlRasterProfile("public-installed-wordsync-material",
			ReaderPlayLikeCurlOrientation.Portrait, ReaderPageBitmapQuality.Balanced, 8,
			rasterGeneration = f.lease.rasterGeneration)
		val material = image.material
		var targetCount = 0
		var maskCount = 0
		var previous: ReaderWhispersyncNativePresentationProof? = null
		try {
			repeat(3) { boundary ->
				val receipt = ReaderWhispersyncAnchorReceipt(
					foliateSessionId = f.lease.binding.foliateSessionId,
					destinationCommitToken = "public-installed-destination", visualPageOrdinal = 1, spineIndex = 0,
					rasterGeneration = f.lease.rasterGeneration, textureGeneration = assertNotNull(f.lease.binding.textureGeneration),
					presentationMutationGeneration = 1L, presentationSequence = 1L,
					anchorGeneration = 1L, boundarySequence = boundary.toLong() + 1L,
					layoutGeneration = 1L, viewGeneration = 1L,
					commitSequence = assertNotNull(f.lease.binding.destinationCommitIdentity).commitSequence,
					committedSpineIndex = 0, committedChapterPageIndex = 1, committedChapterPageCount = 8,
					paginationFingerprint = "public-pagination", layoutFingerprint = "public-layout",
					readerSettingsRasterKey = "public-settings", captureGeometry = geometry,
					pageLocalRects = listOf(ReaderWhispersyncPageLocalRect(ReaderPageTurnPageRole.Full,
						10.0 + boundary, 10.0, 12.0, 8.0)))
				val proof = receipt.nativePresentationProof()
				previous?.let { assertTrue(proof.isAdmissibleReplacementFor(it)) }
				val targets = assertNotNull(readerWhispersyncNativeOverlayTargets(receipt, proof,
					f.lease.binding.foliateSessionId, profile, 1, assertNotNull(f.lease.binding.textureGeneration)))
				targetCount += targets.size
				for (target in targets) {
					val mask = assertNotNull(readerWhispersyncHighlightMask(receipt, target,
						image.bitmap.width, image.bitmap.height, 0x66336699))
					try { assertFalse(mask.isRecycled); maskCount++ } finally { mask.recycle() }
				}
				assertSame(material, image.material)
				assertFalse(image.bitmap.isRecycled)
				assertEquals(listOf(1), source.cachedSnapshotPageIndices(ReaderPageTurnTransitionKind.PortraitSlide))
				previous = proof
			}
			assertEquals(3, targetCount)
			assertEquals(3, maskCount)
			assertFalse(raster.isRecycled)
			// Canonical captured receipt + original material applicability only: no displayed
			// authority, curl input lease, surface replacement or WordSync progress is invented.
		} finally {
			image.bitmap.recycle()
			f.work.retire()
			var returned = 0
			assertEquals(ReaderPortCommandResult.Accepted, source.releaseInstalled(f.work) { returned++ })
			assertEquals(1, returned)
			assertTrue(raster.isRecycled)
		}
	}

	@Test
	fun acceptedOriginalCommitCancellationWaitsForItsActualReturnAndRejectsReplacement() {
		val f = Fixture()
		val readiness = ReadinessFixture()
		var originalReturns = 0
		var replacementReturns = 0
		assertTrue(readiness.adapter.awaitInstalledReadiness(f.work) {
			assertTrue(readiness.session.commit(publicReadinessManifest(f.work)) { })
		})
		readiness.runtime.ready = true
		readiness.adapter.onOriginalRuntimeReady()
		assertEquals(1, readiness.runtime.commits)
		f.work.retire()
		assertTrue(readiness.adapter.cancelInstalled(f.work) { originalReturns++ })
		assertEquals(1, readiness.runtime.commitCancellations)
		assertEquals(0, originalReturns)
		assertFalse(readiness.adapter.cancelInstalled(f.work) { replacementReturns++ })
		val returned = assertNotNull(readiness.runtime.commitReturned)
		returned(); returned()
		assertEquals(1, originalReturns)
		assertEquals(0, replacementReturns)
		assertEquals(1, readiness.runtime.commitCancellations)
	}

	@Test
	fun alreadyReadyCallbackThrowCannotEraseAcceptedOriginalReadinessCustody() {
		val f = Fixture()
		val readiness = ReadinessFixture()
		readiness.runtime.ready = true
		val settlement = f.owner.startInstalledWithOutcome(
			f.domain, f.store, f.snapshot, f.lease, f.work::isCurrent,
			releasePhysicalPreparation = { returned -> readiness.adapter.cancelInstalled(f.work, returned) },
			startPhysicalPreparation = {
				readiness.adapter.awaitInstalledReadiness(f.work) {
					assertTrue(readiness.session.commit(publicReadinessManifest(f.work)) { })
					throw IllegalStateException("public-readiness-callback-ambiguous")
				}
			}, work = f.work)
		settlement.publishLogicalOutcome { assertEquals(ReaderRasterPreparationStartOutcome.Accepted, it,
			"The original readiness registration accepted before a synchronous callback's unknown effect") }
		assertSame(f.work, f.owner.installedWorkFor(f.lease.resourceKey))
		f.work.retire()
		assertEquals(ReaderPortCommandResult.Accepted, f.release())
		assertEquals(0, f.confirmations)
		assertNotNull(readiness.runtime.commitReturned).invoke()
		assertEquals(1, f.confirmations)
	}

	@Test
	fun closeDuringAcceptedOriginalCommitCancellationMustRetainItsCapturedReturn() {
		val f = Fixture()
		val readiness = ReadinessFixture()
		var originalReturns = 0
		assertTrue(readiness.adapter.awaitInstalledReadiness(f.work) {
			assertTrue(readiness.session.commit(publicReadinessManifest(f.work)) { })
		})
		readiness.runtime.ready = true
		readiness.adapter.onOriginalRuntimeReady()
		f.work.retire()
		assertTrue(readiness.adapter.cancelInstalled(f.work) { originalReturns++ })
		val returned = assertNotNull(readiness.runtime.commitReturned)
		assertEquals(0, originalReturns)
		readiness.adapter.close()
		assertEquals(1, readiness.runtime.destructions)
		returned(); returned()
		assertEquals(1, originalReturns,
			"Original callback return or successful destruction must not lose accepted cancellation custody")
		assertFalse(readiness.adapter.cancelInstalled(f.work) { originalReturns++ })
	}

	@Test
	fun synchronousDestroyReturnSettlesOnlyTheAcceptedOriginalCommitCancellation() {
		val f = Fixture()
		val readiness = ReadinessFixture()
		var originalReturns = 0
		assertTrue(readiness.adapter.awaitInstalledReadiness(f.work) {
			assertTrue(readiness.session.commit(publicReadinessManifest(f.work)) { })
		})
		readiness.runtime.ready = true
		readiness.adapter.onOriginalRuntimeReady()
		f.work.retire()
		assertTrue(readiness.adapter.cancelInstalled(f.work) { originalReturns++ })
		val returned = assertNotNull(readiness.runtime.commitReturned)
		readiness.runtime.onDestroy = {
			assertEquals(0, originalReturns, "Destroy entry is not original physical return")
			returned(); returned()
			assertEquals(1, originalReturns)
		}
		readiness.adapter.close()
		returned()
		assertEquals(1, originalReturns)
		assertEquals(1, readiness.runtime.destructions)
		assertEquals(0, readiness.session.metrics().activeCaptures)
	}

	@Test
	fun throwingDestroyCannotReplaceOrEraseAcceptedOriginalCommitReturn() {
		val f = Fixture()
		val readiness = ReadinessFixture()
		var originalReturns = 0
		var replacementReturns = 0
		assertTrue(readiness.adapter.awaitInstalledReadiness(f.work) {
			assertTrue(readiness.session.commit(publicReadinessManifest(f.work)) { })
		})
		readiness.runtime.ready = true
		readiness.adapter.onOriginalRuntimeReady()
		f.work.retire()
		assertTrue(readiness.adapter.cancelInstalled(f.work) { originalReturns++ })
		val returned = assertNotNull(readiness.runtime.commitReturned)
		readiness.runtime.onDestroy = { throw IllegalStateException("public-destroy-ambiguous") }
		assertFailsWith<IllegalStateException> { readiness.adapter.close() }
		assertEquals(0, originalReturns, "A throwing destroy cannot acknowledge physical return")
		assertEquals(1, readiness.session.metrics().activeCaptures)
		assertFalse(readiness.adapter.cancelInstalled(f.work) { replacementReturns++ })
		returned(); returned()
		assertEquals(1, originalReturns)
		assertEquals(0, replacementReturns)
		assertEquals(0, readiness.session.metrics().activeCaptures)
	}

	/** Prospective original-record regressions. MAIN qualifies RED before lifetime/overlay behavior.
	 * The platform shadow delivers the ORIGINAL ownership/listener/frame consumers. Preparation
	 * is controlled at the original preparation owner's public capture/cache boundary: no passive
	 * durability, production capability, real Foliate or GL acceptance is claimed. */
	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun originalCommandCreatedCommittedDeckRemainsReadableAfterRasterReturn() = withMaterialFixture { f ->
		f.start()
		val deck = assertNotNull(f.deck, "B407.Lifetime.Deck")
		val candidate = assertNotNull(f.candidate(), "B407.Lifetime.BeforeReturn")
		val image = f.surface.submitted.single().getSettlementPage(PageChange.NONE).content
		f.presentAndCommit()
		assertEquals(ReaderTransitionResourceState.Released,
			f.coordinator.releaseStateOf(f.work.lease.resourceKey), "B407.Lifetime.RasterReturned")
		assertFalse(f.work.isCurrent(), "B407.Lifetime.CommandRetired")
		assertSame(f.work, f.curl.installedWorkFor(f.work.lease.resourceKey), "B407.Lifetime.DeckCustody")
		assertEquals(deck, f.curl.installedDeckFor(deck.resourceKey), "B407.Lifetime.ExactDeck")
		assertEquals(candidate, assertNotNull(f.candidate(), "B407.Lifetime.ReadOnlyCandidate"))
		assertFalse(image.isRecycled, "B407.Lifetime.RendererImageRetained")
		assertEquals(1, f.surface.submitted.size, "B407.Lifetime.NoReplacement")
	}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun displayedAssociationCannotBeSelfCertifiedByAnAnchorReceipt() = withMaterialFixture { f ->
		f.start()
		val deck = assertNotNull(f.deck, "B407.Association.Deck")
		val captured = f.anchor(1L)
		val unpublished = ReaderNativePagePresentationProof(deck.binding, null, 99L, 120, 80,
			deck.rasterGeneration, deck.textureGeneration)
		assertNull(f.curl.installedDecorationAssociationFor(deck), "B407.Association.BeforeDisplay")
		assertIs<ReaderPortCommandResult.Rejected>(f.curl.setInstalledWhispersyncOverlay(
			deck, unpublished, captured, 0x66336699), "B407.Association.NoSelfCertificate")
		assertTrue(f.surface.overlays.isEmpty(), "B407.Association.NoPrematureOverlay")
		f.presentAndCommit()
		val association = assertNotNull(f.curl.installedDecorationAssociationFor(deck), "B407.Association.Published")
		assertEquals(f.displayedProof, association.displayedProof, "B407.Association.IndependentProof")
		assertEquals(f.plan.resolvedLayout, association.plan.resolvedLayout, "B407.Association.Layout")
		assertEquals(f.plan.captureGeometry, association.plan.captureGeometry, "B407.Association.Geometry")
		assertEquals(f.plan.preparedChapterRange(), association.plan.preparedChapterRange(), "B407.Association.Range")
		assertEquals(ReaderPortCommandResult.Accepted, f.curl.setInstalledWhispersyncOverlay(
			deck, association.displayedProof, captured, 0x66336699), "B407.Association.PositiveOverlay")
		assertEquals(1, f.surface.overlays.size, "B407.Association.OriginalPublication")
	}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun applicableInstalledWordSyncBoundariesContinuePublishingOnTheOriginalRetainedDeck() = withMaterialFixture { f ->
		f.bootstrap()
		val deck = assertNotNull(f.deck)
		val base = f.surface.submitted.single()
		val image = base.getSettlementPage(PageChange.NONE).content
		repeat(3) { boundary ->
			assertEquals(ReaderPortCommandResult.Accepted, f.curl.setInstalledWhispersyncOverlay(
				deck, assertNotNull(f.displayedProof), f.anchor(boundary.toLong() + 1L), 0x66336699),
				"B407.WordSync.ApplicableBoundary")
			assertEquals(boundary + 1, f.surface.overlays.size, "B407.WordSync.PublicationCount")
			assertSame(base, f.surface.submitted.single(), "B407.WordSync.OriginalDeck")
			assertSame(image, base.getSettlementPage(PageChange.NONE).content, "B407.WordSync.OriginalMaterial")
			assertFalse(image.isRecycled, "B407.WordSync.MaterialAlive")
		}
		assertEquals(1, f.barrier.atomicCommitCount, "B407.WordSync.NoOwnerChurn")
		assertEquals(1, f.commands.filterIsInstance<ReaderTransitionCommand.ReserveDeck>().size,
			"B407.WordSync.NoPreparationReplacement")
	}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun installedWordSyncRejectsObsoleteSessionLayoutRangeAndLateBoundaryWithoutReplacingMaterial() = withMaterialFixture { f ->
		f.bootstrap()
		val deck = assertNotNull(f.deck)
		val proof = assertNotNull(f.displayedProof)
		val applicable = f.anchor(4L)
		assertEquals(ReaderPortCommandResult.Accepted,
			f.curl.setInstalledWhispersyncOverlay(deck, proof, applicable, 0x66336699), "B407.WordSync.Control")
		val invalid = listOf(applicable.copy(foliateSessionId = "obsolete-public-session"),
			applicable.copy(layoutGeneration = applicable.layoutGeneration + 1L),
			applicable.copy(viewGeneration = applicable.viewGeneration + 1L),
			applicable.copy(committedChapterPageIndex = 2),
			applicable.copy(committedChapterPageCount = 9),
			applicable.copy(boundarySequence = 3L),
			applicable.copy(textureGeneration = deck.textureGeneration + 1L),
			applicable.copy(captureGeometry = applicable.captureGeometry.copy(viewportWidth = 121.0)))
		for (receipt in invalid) assertIs<ReaderPortCommandResult.Rejected>(
			f.curl.setInstalledWhispersyncOverlay(deck, proof, receipt, 0x66336699), "B407.WordSync.Obsolete")
		assertIs<ReaderPortCommandResult.Rejected>(f.curl.setInstalledWhispersyncOverlay(
			deck, proof.copy(presentedFrame = proof.presentedFrame + 1L), applicable, 0x66336699),
			"B407.WordSync.UnassociatedDisplay")
		assertEquals(1, f.surface.overlays.size, "B407.WordSync.NoInvalidPublication")
		f.coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
		assertIs<ReaderPortCommandResult.Rejected>(f.curl.setInstalledWhispersyncOverlay(
			deck, proof, f.anchor(5L), 0x66336699), "B407.WordSync.RetiredDeck")
	}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun originalInstalledCurlClaimKeepsHighlightedMaterialAndEmitsTypedSettlementFacts() = withMaterialFixture { f ->
		f.bootstrap()
		val deck = assertNotNull(f.deck)
		assertEquals(ReaderPortCommandResult.Accepted, f.curl.setInstalledWhispersyncOverlay(
			deck, assertNotNull(f.displayedProof), f.anchor(1L), 0x66336699), "B407.Curl.InitialHighlight")
		val originalOverlay = f.surface.overlays.single()
		val sourceImage = f.surface.submitted.single().getSettlementPage(PageChange.NONE).content
		val gesture = assertNotNull(f.input.dispatchPointer(ReaderPageHostPointerEvent.Down(110f, 40f, 100L)).gestureId)
		assertEquals(ReaderPagePointerRoute.ClaimCurl(gesture),
			f.input.dispatchPointer(ReaderPageHostPointerEvent.Move(10f, 40f, 4f)).route, "B407.Curl.PointerClaim")
		val intent = ReaderPageTurnIntent(ReaderPageTurnDirection.Next, ReaderTransitionGestureId(gesture),
			ReaderSemanticRequestHandle(1L))
		val facts = mutableListOf<ReaderInstalledCurlSettlementObservation>()
		assertEquals(ReaderPortCommandResult.Accepted,
			f.curl.beginInstalledCurlGesture(deck, intent, facts::add), "B407.Curl.OriginalSourceAdmission")
		val down = MotionEvent.obtain(100L, 101L, MotionEvent.ACTION_DOWN, 110f, 40f, 0)
		try { assertEquals(ReaderPageCurlDispatchResult.Accepted, f.curl.onPageTouchEvent(down, gesture),
			"B407.Curl.OriginalRendererClaim") } finally { down.recycle() }
		assertSame(originalOverlay, f.surface.overlays.single(), "B407.Curl.HighlightDuringClaim")
		assertFalse(sourceImage.isRecycled, "B407.Curl.MaterialDuringClaim")
		f.surface.listener.onSettlementStarted(gesture, deck.textureGeneration, "public-source", "public-target", PageChange.NEXT)
		f.surface.listener.onSettlementCompleted(gesture, deck.textureGeneration, "public-target", 2, PageChange.NEXT)
		assertEquals(listOf(ReaderInstalledCurlSettlementStage.Started, ReaderInstalledCurlSettlementStage.Completed),
			facts.map { it.stage }, "B407.Curl.TypedOriginalCallbacks")
		for (fact in facts) {
			assertSame(f.work, fact.source.work, "B407.Curl.OriginalWork")
			assertEquals(deck, fact.source.deck, "B407.Curl.OriginalDeck")
			assertEquals(intent, fact.source.intent, "B407.Curl.TypedIntent")
		}
		assertEquals(1, f.barrier.atomicCommitCount, "B407.Curl.CallbackIsNotPublication")
		f.surface.listener.onSettlementStarted(gesture, deck.textureGeneration, "public-source", "public-target", PageChange.NEXT)
		f.surface.listener.onSettlementCompleted(gesture, deck.textureGeneration, "public-target", 2, PageChange.NEXT)
		f.surface.listener.onGestureCancelled(gesture, deck.textureGeneration)
		assertEquals(2, facts.size, "B407.Curl.DuplicateAndLateCallbacksFenced")
		assertSame(originalOverlay, f.surface.overlays.single(), "B407.Curl.NoSourceDecorationAfterPhysicalTurn")
	}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun installedHighlightCoalescesWhileCurlAndWaitsForOriginalCancellationCallback() = withMaterialFixture { f ->
		f.bootstrap()
		val deck = assertNotNull(f.deck)
		val proof = assertNotNull(f.displayedProof)
		assertEquals(ReaderPortCommandResult.Accepted,
			f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(1L), 0x66336699))
		val overlay = f.surface.overlays.single()
		val observations = mutableListOf<ReaderInstalledCurlSettlementObservation>()
		val intent = ReaderPageTurnIntent(ReaderPageTurnDirection.Next, ReaderTransitionGestureId(1L),
			ReaderSemanticRequestHandle(1L))
		assertEquals(ReaderPortCommandResult.Accepted, f.curl.beginInstalledCurlGesture(deck, intent, observations::add))
		val down = MotionEvent.obtain(100L, 101L, MotionEvent.ACTION_DOWN, 110f, 40f, 0)
		try { assertEquals(ReaderPageCurlDispatchResult.Accepted, f.curl.onPageTouchEvent(down, 1L)) }
		finally { down.recycle() }
		for (boundary in listOf(2L, 3L)) assertEquals(ReaderPortCommandResult.Accepted,
			f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(boundary), 0x66336699))
		assertIs<ReaderPortCommandResult.Rejected>(f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(2L), 0x66336699))
		assertSame(overlay, f.surface.overlays.single(), "B407.Curl.CoalescedNotReplaced")
		f.curl.cancelGesture(1L)
		assertEquals(listOf(1L), f.surface.cancellations, "B407.Curl.OriginalCancelRequest")
		assertTrue(observations.isEmpty(), "B407.Curl.CancelRequestIsNotCallback")
		assertSame(overlay, f.surface.overlays.single())
		f.surface.listener.onGestureCancelled(1L, deck.textureGeneration)
		f.surface.listener.onGestureCancelled(1L, deck.textureGeneration)
		assertEquals(listOf(ReaderInstalledCurlSettlementStage.Cancelled), observations.map { it.stage })
		assertEquals(2, f.surface.overlays.size, "B407.Curl.LatestBoundaryAfterActualReturn")
		assertEquals(1, f.surface.submitted.size)
		assertEquals(1, f.barrier.atomicCommitCount)
	}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun installedHighlightClearRetainsBoundaryHighWaterAndRejectedCallbacksCannotSelfAdmit() = withMaterialFixture { f ->
		f.bootstrap()
		val deck = assertNotNull(f.deck)
		val proof = assertNotNull(f.displayedProof)
		assertEquals(ReaderPortCommandResult.Accepted,
			f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(4L), 0x66336699))
		assertEquals(ReaderPortCommandResult.Accepted, f.curl.setInstalledWhispersyncOverlay(deck, proof, null, 0x66336699))
		assertIs<ReaderPortCommandResult.Rejected>(f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(3L), 0x66336699))
		assertIs<ReaderPortCommandResult.Rejected>(f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(4L), 0x66336699))
		assertEquals(2, f.surface.overlays.size)
		assertTrue(f.surface.overlays.last().isEmpty())
		val observations = mutableListOf<ReaderInstalledCurlSettlementObservation>()
		assertEquals(ReaderPortCommandResult.Accepted, f.curl.beginInstalledCurlGesture(deck,
			ReaderPageTurnIntent(ReaderPageTurnDirection.Next, ReaderTransitionGestureId(1L),
				ReaderSemanticRequestHandle(1L)), observations::add))
		f.surface.listener.onSettlementStarted(1L, deck.textureGeneration, "public-source", "public-target", PageChange.NEXT)
		f.surface.listener.onSettlementCompleted(1L, deck.textureGeneration, "public-target", 2, PageChange.NEXT)
		assertTrue(observations.isEmpty(), "B407.Curl.CallbackCannotAdmitOriginalRenderer")
		assertEquals(1, f.barrier.atomicCommitCount)
	}

	/** Original-consumer UNIT regressions: decoration clears without claiming display authority. */
	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun installedNullHighlightClearsDuringOriginalAdmittedCurlWithoutReplacingAuthority() =
		withMaterialFixture(legacySelection = false) { f ->
			f.bootstrap()
			val deck = assertNotNull(f.deck, "C407.Clear.OriginalDeck")
			val proof = assertNotNull(f.displayedProof, "C407.Clear.OriginalDisplayedProof")
			val association = assertNotNull(f.curl.installedDecorationAssociationFor(deck), "C407.Clear.OriginalAssociation")
			assertEquals(ReaderPortCommandResult.Accepted,
				f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(4L), 0x66336699), "C407.Clear.InitialHighlight")
			val highlighted = f.surface.overlays.single()
			val base = f.surface.submitted.single()
			val material = base.getSettlementPage(PageChange.NONE).content
			val observations = mutableListOf<ReaderInstalledCurlSettlementObservation>()
			val source = f.admitOriginalSource(observations::add)
			f.dispatchOriginalDown(source)
			val publication = f.barrier.snapshot
			val commands = f.commands.toList()
			val registrations = f.surface.frames.size
			assertEquals(ReaderPortCommandResult.Accepted,
				f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(5L), 0x66336699), "C407.Clear.CoalescedBoundary")
			assertSame(highlighted, f.surface.overlays.single(), "C407.Clear.NonEmptyStillWaits")
			assertIs<ReaderPortCommandResult.Rejected>(f.curl.setInstalledWhispersyncOverlay(
				deck, proof.copy(presentedFrame = proof.presentedFrame + 1L), null, 0x66336699), "C407.Clear.ForeignDisplayRejected")
			assertSame(highlighted, f.surface.overlays.single(), "C407.Clear.NoForeignClear")
			assertEquals(ReaderPortCommandResult.Accepted,
				f.curl.setInstalledWhispersyncOverlay(deck, proof, null, 0x66336699), "C407.Clear.AcceptedDuringCurl")
			assertTrue(f.surface.overlays.last().isEmpty(), "C407.Clear.DuringOriginalCurl")
			assertEquals(2, f.surface.overlays.size, "C407.Clear.ExactRendererClearOnce")
			assertTrue(observations.isEmpty(), "C407.Clear.NotGestureIdleOrSettlement")
			assertSame(publication, f.barrier.snapshot, "C407.Clear.NoNewOwnerOrInput")
			assertEquals(association, f.curl.installedDecorationAssociationFor(deck), "C407.Clear.SameAssociation")
			assertEquals(commands, f.commands, "C407.Clear.NoMaterialRasterDeckOrSemanticCommand")
			assertEquals(registrations, f.surface.frames.size, "C407.Clear.NoFrameRegistration")
			assertSame(base, f.surface.submitted.single(), "C407.Clear.SameRendererDeck")
			assertSame(material, base.getSettlementPage(PageChange.NONE).content, "C407.Clear.SameMaterial")
			assertFalse(material.isRecycled, "C407.Clear.MaterialAlive")
			for (boundary in listOf(4L, 5L)) assertIs<ReaderPortCommandResult.Rejected>(
				f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(boundary), 0x66336699), "C407.Clear.HighWaterRetained")
			assertEquals(ReaderPortCommandResult.Accepted,
				f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(6L), 0x66336699), "C407.Clear.NextBoundaryAdmitted")
			assertEquals(2, f.surface.overlays.size, "C407.Clear.ReplacementStillWaits")
			f.curl.cancelGesture(source.intent.gestureId.value)
			assertTrue(observations.isEmpty(), "C407.Clear.CancelRequestNotReturn")
			assertEquals(2, f.surface.overlays.size, "C407.Clear.NoReplacementBeforeOriginalReturn")
			f.surface.listener.onGestureCancelled(source.intent.gestureId.value, deck.textureGeneration)
			assertEquals(listOf(ReaderInstalledCurlSettlementStage.Cancelled), observations.map { it.stage }, "C407.Clear.OriginalReturn")
			assertEquals(3, f.surface.overlays.size, "C407.Clear.LatestReplacementAfterReturn")
			assertFalse(f.surface.overlays.last().isEmpty(), "C407.Clear.NonEmptyReplacement")
		}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun installedNullHighlightClearsWhileOriginalSettlementIsPendingWithoutCompletingIt() =
		withMaterialFixture(legacySelection = false) { f ->
			f.bootstrap()
			val deck = assertNotNull(f.deck, "C407.Clear.PendingOriginalDeck")
			val proof = assertNotNull(f.displayedProof, "C407.Clear.PendingDisplayedProof")
			assertEquals(ReaderPortCommandResult.Accepted,
				f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(1L), 0x66336699), "C407.Clear.PendingInitialHighlight")
			val observations = mutableListOf<ReaderInstalledCurlSettlementObservation>()
			val source = f.admitOriginalSource(observations::add)
			f.dispatchOriginalDown(source)
			f.surface.listener.onSettlementStarted(source.intent.gestureId.value, deck.textureGeneration,
				"public-source", "public-target", PageChange.NEXT)
			assertEquals(listOf(ReaderInstalledCurlSettlementStage.Started), observations.map { it.stage }, "C407.Clear.OriginalSettlementStarted")
			val publication = f.barrier.snapshot
			val commands = f.commands.toList()
			val registrations = f.surface.frames.size
			val base = f.surface.submitted.single()
			assertEquals(ReaderPortCommandResult.Accepted,
				f.curl.setInstalledWhispersyncOverlay(deck, proof, null, 0x66336699), "C407.Clear.AcceptedDuringSettlement")
			assertTrue(f.surface.overlays.last().isEmpty(), "C407.Clear.DuringOriginalSettlement")
			assertEquals(2, f.surface.overlays.size, "C407.Clear.PendingExactRendererClearOnce")
			assertEquals(listOf(ReaderInstalledCurlSettlementStage.Started), observations.map { it.stage }, "C407.Clear.NoSyntheticTerminal")
			assertSame(publication, f.barrier.snapshot, "C407.Clear.PendingSameAuthority")
			assertEquals(commands, f.commands, "C407.Clear.PendingNoNewCommands")
			assertEquals(registrations, f.surface.frames.size, "C407.Clear.PendingNoFrameRegistration")
			assertSame(base, f.surface.submitted.single(), "C407.Clear.PendingSameDeck")
			assertEquals(0, f.coordinator.snapshot().consumedSettlementCount, "C407.Clear.NoSemanticSettlement")
		}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun throwingOriginalRendererInputRetainsCancellationCustodyWithoutInventingAdmission() = withMaterialFixture { f ->
		f.bootstrap()
		val deck = assertNotNull(f.deck)
		val observations = mutableListOf<ReaderInstalledCurlSettlementObservation>()
		assertEquals(ReaderPortCommandResult.Accepted, f.curl.beginInstalledCurlGesture(deck,
			ReaderPageTurnIntent(ReaderPageTurnDirection.Next, ReaderTransitionGestureId(1L),
				ReaderSemanticRequestHandle(1L)), observations::add))
		f.surface.throwAfterInput = true
		val down = MotionEvent.obtain(100L, 101L, MotionEvent.ACTION_DOWN, 110f, 40f, 0)
		try { assertFailsWith<IllegalStateException> { f.curl.onPageTouchEvent(down, 1L) } }
		finally { down.recycle() }
		assertTrue(observations.isEmpty(), "B407.Curl.ThrowIsNotRendererAdmissionOrTerminal")
		f.curl.cancelGesture(1L)
		assertEquals(listOf(1L), f.surface.cancellations, "B407.Curl.AmbiguousInputKeepsOriginalCancellation")
		assertTrue(observations.isEmpty())
		f.surface.listener.onGestureCancelled(1L, deck.textureGeneration)
		assertEquals(listOf(ReaderInstalledCurlSettlementStage.Cancelled), observations.map { it.stage })
		assertEquals(1, f.barrier.atomicCommitCount)
	}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun installedGestureSourceMustAdmitTheOriginalTypedTurnBeforeRendererCallbacks() = withMaterialFixture { f ->
		f.bootstrap()
		val deck = assertNotNull(f.deck)
		val intent = ReaderPageTurnIntent(ReaderPageTurnDirection.Next, ReaderTransitionGestureId(1L),
			ReaderSemanticRequestHandle(1L))
		var observations = 0
		assertEquals(ReaderPortCommandResult.Accepted, f.curl.beginInstalledCurlGesture(deck, intent) { observations++ },
			"B407.Curl.TypedSourceAdmission")
		assertEquals(0, observations, "B407.Curl.AdmissionIsNotSettlement")
		assertEquals(1, f.barrier.atomicCommitCount, "B407.Curl.AdmissionIsNotPublication")
	}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun installedTerminalFrameContractCannotInventAnOriginalSettlementOrUseNativeFrameFallback() = withMaterialFixture { f ->
		f.bootstrap()
		val deck = assertNotNull(f.deck)
		val spec = ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame(
			deck.transitionId, deck.transitionId.readerSessionGeneration, deck.binding.publicationGeneration,
			deck.binding, assertNotNull(deck.allocation), ReaderTransitionGestureId(1L),
			ReaderPageTurnSettlementAck("public-unobserved-settlement", 1, deck.binding.foliateSessionId,
				deck.rasterGeneration, deck.textureGeneration),
			ReaderPlayLikeCurlDeckTargetIdentity(f.curl.surfaceView.rendererGeneration, deck.textureGeneration,
				ReaderTransitionDeckRole.Initial), ReaderTransitionFrameGeometry(deck.binding.viewportGeneration,
				deck.binding.profileGeneration, 0, 0, 120, 80), 2L)
		val registrations = f.surface.frames.size
		assertNull(f.curl.installedCurlTerminalCandidate(spec), "B407.Terminal.NoOriginalSettlement")
		assertEquals(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID,
			f.curl.requestInstalledCurlTerminalPresentedFrame(ReaderCurlSettlementTerminalPresentationCandidate(spec)) {
				error("An ordinary Native callback cannot certify Curl terminal")
			}, "B407.Terminal.NoFrameFallback")
		assertEquals(registrations, f.surface.frames.size, "B407.Terminal.NoSyntheticConsumer")
	}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun obsoletePreparationCallbackCannotPublishOrReplaceTheCommittedOriginalDeck() = withMaterialFixture { f ->
		f.bootstrap()
		val before = f.coordinator.snapshot()
		val deck = assertNotNull(f.deck)
		f.completePreparation?.invoke { error("A retired preparation must fence logical delivery") }
		f.surface.listener.onDeckPrepared(deck.textureGeneration)
		assertEquals(before, f.coordinator.snapshot(), "B407.Lifetime.LateCallbackFenced")
		assertEquals(1, f.surface.submitted.size, "B407.Lifetime.NoLateReplacement")
		assertEquals(deck, f.curl.installedDeckFor(deck.resourceKey), "B407.Lifetime.RetainedExactDeck")
	}

	/** Capability RED is distinct from the deeper runtime assertions below. */
	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun originalAtomicPublicationNeedsAPublicInstalledDeckConsumerWithoutLegacySelection() =
		withMaterialFixture(legacySelection = false) { f -> f.requireOriginalAtomicPublicationConsumer() }

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun soleAtomicPublicationRetainsDisplayedAssociationAfterRasterReturnWithoutLegacySelection() =
		withMaterialFixture(legacySelection = false) { f ->
			f.start()
			val deck = assertNotNull(f.deck, "B408.Association.OriginalDeck")
			val preparedCandidate = assertNotNull(f.candidate(), "B408.Association.PreparedCandidate")
			assertNull(f.curl.installedDecorationAssociationFor(deck), "B408.Association.NotPreparedAsDisplayed")
			val captured = f.anchor(1L)
			val unobserved = ReaderNativePagePresentationProof(deck.binding, null, 99L, 120, 80,
				deck.rasterGeneration, deck.textureGeneration)
			assertIs<ReaderPortCommandResult.Rejected>(f.curl.setInstalledWhispersyncOverlay(
				deck, unobserved, captured, 0x66336699), "B408.Association.AnchorCannotCertifyDisplay")
			f.presentAndCommit()
			assertFalse(f.work.isCurrent(), "B408.Association.RasterCommandRetired")
			assertEquals(ReaderTransitionResourceState.Released,
				f.coordinator.releaseStateOf(f.work.lease.resourceKey), "B408.Association.OriginalRasterReturned")
			val association = assertNotNull(f.curl.installedDecorationAssociationFor(deck),
				"B408.Association.AtomicPublicationIndependentlyRetained")
			assertEquals(f.displayedProof, association.displayedProof, "B408.Association.ExactAtomicProof")
			assertEquals(f.plan, association.plan, "B408.Association.OriginalPlan")
			assertEquals(preparedCandidate, assertNotNull(f.candidate(),
				"B408.Association.ReadOnlyMetadataAfterReturn"))
			assertIs<ReaderPortCommandResult.Rejected>(f.curl.setInstalledWhispersyncOverlay(
				deck, unobserved, captured, 0x66336699), "B408.Association.ForeignDisplayRejected")
			assertEquals(ReaderPortCommandResult.Accepted, f.curl.setInstalledWhispersyncOverlay(
				deck, association.displayedProof, captured, 0x66336699), "B408.Association.ApplicableOverlay")
			assertEquals(1, f.surface.submitted.size, "B408.Association.NoReplacementDeck")
		}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun installedDisplayProjectionRequiresTheOriginalAlreadyPublishedSnapshot() =
		withMaterialFixture(legacySelection = false) { f ->
			f.bootstrap()
			f.rejectCopiedPublication()
			assertNotNull(f.curl.installedDecorationAssociationFor(assertNotNull(f.deck)),
				"B408.AtomicPublication.RejectionPreservesOriginalAssociation")
		}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun applicableInstalledBoundariesAdvanceOnSoleAtomicPublicationWithoutLegacySelection() =
		withMaterialFixture(legacySelection = false) { f ->
			f.bootstrap()
			val deck = assertNotNull(f.deck, "B408.WordSync.OriginalDeck")
			val proof = assertNotNull(f.displayedProof, "B408.WordSync.OriginalAtomicProof")
			repeat(3) { boundary ->
				assertEquals(ReaderPortCommandResult.Accepted, f.curl.setInstalledWhispersyncOverlay(
					deck, proof, f.anchor(boundary.toLong() + 1L), 0x66336699), "B408.WordSync.ApplicableBoundary")
				assertEquals(boundary + 1, f.surface.overlays.size, "B408.WordSync.OriginalConsumerProgress")
				assertNotNull(f.candidate(), "B408.WordSync.ReadOnlyDeckStillAvailable")
			}
			assertEquals(1, f.barrier.atomicCommitCount, "B408.WordSync.NoOwnerChurn")
			assertEquals(1, f.surface.submitted.size, "B408.WordSync.NoRasterReplacement")
			f.coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
			assertIs<ReaderPortCommandResult.Rejected>(f.curl.setInstalledWhispersyncOverlay(
				deck, proof, f.anchor(4L), 0x66336699), "B408.WordSync.ClosedDeckFenced")
			assertEquals(3, f.surface.overlays.size, "B408.WordSync.NoLatePublication")
		}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun originalInstalledSourceBindsOneTransportBeforeDelayedRendererCancellation() =
		withMaterialFixture(legacySelection = false) { f ->
			f.bootstrap()
			val source = f.admitOriginalSource()
			val deliveries = mutableListOf<ReaderInstalledCurlSettlementObservation>()
			var retirements = 0
			val token = ReaderSemanticTransportRetirement { retirements++ }
			assertTrue(source.bindOriginalSemanticTransport(token, deliveries::add),
				"B408.Waiting.OriginalBoundBeforeRenderer")
			assertFalse(source.bindOriginalSemanticTransport(token, deliveries::add),
				"B408.Waiting.NoCallbackReplacement")
			assertFalse(source.copy().bindOriginalSemanticTransport(token, deliveries::add),
				"B408.Waiting.CopiedSourceRejected")
			source.retireOriginalSemanticTransportExact(ReaderSemanticTransportRetirement {})
			f.dispatchOriginalDown(source)
			f.surface.listener.onGestureCancelled(source.intent.gestureId.value, source.deck.textureGeneration)
			f.surface.listener.onGestureCancelled(source.intent.gestureId.value, source.deck.textureGeneration)
			assertEquals(listOf(ReaderInstalledCurlSettlementStage.Cancelled), deliveries.map { it.stage },
				"B408.Waiting.DelayedOriginalObservationOnce")
			assertSame(source, deliveries.single().source, "B408.Waiting.OriginalSourceIdentity")
			assertEquals(0, retirements, "B408.Waiting.ObservationIsNotTransportRetirement")
		}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun explicitOriginalCurlCancellationRetiresBoundTransportOnceBeforePhysicalReturn() =
		withMaterialFixture(legacySelection = false) { f ->
			f.bootstrap()
			val physical = mutableListOf<ReaderInstalledCurlSettlementObservation>()
			val source = f.admitOriginalSource(physical::add)
			var retirements = 0
			var waitingDeliveries = 0
			val token = ReaderSemanticTransportRetirement { retirements++ }
			assertTrue(source.bindOriginalSemanticTransport(token) { waitingDeliveries++ })
			f.dispatchOriginalDown(source)
			f.curl.cancelGesture(source.intent.gestureId.value)
			f.curl.cancelGesture(source.intent.gestureId.value)
			assertEquals(1, retirements, "B408.Waiting.CancelRetiresExactTokenOnce")
			assertTrue(physical.isEmpty(), "B408.Waiting.CancelRequestIsNotPhysicalReturn")
			f.surface.listener.onGestureCancelled(source.intent.gestureId.value, source.deck.textureGeneration)
			assertEquals(0, waitingDeliveries, "B408.Waiting.CancelledTransportCannotNavigate")
			assertEquals(listOf(ReaderInstalledCurlSettlementStage.Cancelled), physical.map { it.stage },
				"B408.Waiting.CancelledOriginalPhysicalReturn")
		}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun originalLegacySettlementCallbackCannotMintTypedCurlCompletion() =
		withMaterialFixture(legacySelection = false) { f ->
			f.bootstrap()
			val observations = mutableListOf<ReaderInstalledCurlSettlementObservation>()
			val source = f.admitOriginalSource(observations::add)
			f.dispatchOriginalDown(source)
			f.surface.listener.onSettlementStarted(source.intent.gestureId.value, source.deck.textureGeneration,
				"public-source", "public-target", PageChange.NEXT)
			f.surface.listener.onSettlementCompleted(source.intent.gestureId.value, source.deck.textureGeneration,
				"public-source", 1, PageChange.NONE)
			val completion = observations.single { it.stage == ReaderInstalledCurlSettlementStage.Completed }
			assertNull(completion.rendererCompletion, "B408.Terminal.UntypedCallbackIsNotCompletion")
			assertSame(source, completion.source, "B408.Terminal.OriginalSourceRetained")
		}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun exactOriginalSourceRetirementSuppressesWaitingButNotPhysicalCancellationObservation() =
		withMaterialFixture(legacySelection = false) { f ->
			f.bootstrap()
			val physical = mutableListOf<ReaderInstalledCurlSettlementObservation>()
			val source = f.admitOriginalSource(physical::add)
			var waitingDeliveries = 0
			val token = ReaderSemanticTransportRetirement {}
			assertTrue(source.bindOriginalSemanticTransport(token) { waitingDeliveries++ },
				"B408.Waiting.OriginalBound")
			source.retireOriginalSemanticTransportExact(token)
			source.retireOriginalSemanticTransportExact(token)
			assertFalse(source.bindOriginalSemanticTransport(ReaderSemanticTransportRetirement {}) { waitingDeliveries++ },
				"B408.Waiting.RetiredCellCannotReopen")
			f.dispatchOriginalDown(source)
			f.surface.listener.onGestureCancelled(source.intent.gestureId.value, source.deck.textureGeneration)
			assertEquals(0, waitingDeliveries, "B408.Waiting.ExactRetirementFencesDelivery")
			assertEquals(listOf(ReaderInstalledCurlSettlementStage.Cancelled), physical.map { it.stage },
				"B408.Waiting.PhysicalObservationPreserved")
		}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun soleAtomicDisplayedDeckKeepsHighlightUntilOriginalClaimCancellationCallback() =
		withMaterialFixture(legacySelection = false) { f ->
			f.bootstrap()
			val deck = assertNotNull(f.deck, "B408.Curl.OriginalDeck")
			val proof = assertNotNull(f.displayedProof, "B408.Curl.OriginalAtomicProof")
			assertEquals(ReaderPortCommandResult.Accepted,
				f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(1L), 0x66336699))
			val originalOverlay = f.surface.overlays.single()
			val gesture = assertNotNull(f.input.dispatchPointer(
				ReaderPageHostPointerEvent.Down(110f, 40f, 100L)).gestureId, "B408.Curl.OriginalPointer")
			assertEquals(ReaderPagePointerRoute.ClaimCurl(gesture), f.input.dispatchPointer(
				ReaderPageHostPointerEvent.Move(10f, 40f, 4f)).route, "B408.Curl.OriginalPointerClaim")
			// Explicit component admission does NOT qualify automatic Native/ROOT ingress.
			val observations = mutableListOf<ReaderInstalledCurlSettlementObservation>()
			assertEquals(ReaderPortCommandResult.Accepted, f.curl.beginInstalledCurlGesture(deck,
				ReaderPageTurnIntent(ReaderPageTurnDirection.Next, ReaderTransitionGestureId(gesture),
					ReaderSemanticRequestHandle(1L)), observations::add), "B408.Curl.OriginalSourceAdmission")
			val down = MotionEvent.obtain(100L, 101L, MotionEvent.ACTION_DOWN, 110f, 40f, 0)
			try { assertEquals(ReaderPageCurlDispatchResult.Accepted, f.curl.onPageTouchEvent(down, gesture)) }
			finally { down.recycle() }
			for (boundary in listOf(2L, 3L)) assertEquals(ReaderPortCommandResult.Accepted,
				f.curl.setInstalledWhispersyncOverlay(deck, proof, f.anchor(boundary), 0x66336699))
			assertSame(originalOverlay, f.surface.overlays.single(), "B408.Curl.HighlightThroughClaim")
			f.curl.cancelGesture(gesture)
			assertEquals(listOf(gesture), f.surface.cancellations, "B408.Curl.ExactCancellationRequested")
			assertTrue(observations.isEmpty(), "B408.Curl.RequestIsNotPhysicalReturn")
			assertSame(originalOverlay, f.surface.overlays.single(), "B408.Curl.HighlightUntilReturn")
			f.surface.listener.onGestureCancelled(gesture, deck.textureGeneration)
			f.surface.listener.onGestureCancelled(gesture, deck.textureGeneration)
			assertEquals(listOf(ReaderInstalledCurlSettlementStage.Cancelled), observations.map { it.stage },
				"B408.Curl.OnceOnlyOriginalCancellationCallback")
			assertEquals(2, f.surface.overlays.size, "B408.Curl.LatestBoundaryAfterReturn")
			assertEquals(1, f.barrier.atomicCommitCount, "B408.Curl.NoSyntheticSuccessor")
		}

	/** Source/executor controls execute without the missing Engine API; not semantic/GL proof. */
	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun originalInstalledSourceAndExecutorShareExactWaitingRetirementWithoutLegacyForegroundThaw() =
		withMaterialFixture(legacySelection = false, installedForeground = true) { f ->
			f.bootstrap()
			val originalOwner = assertNotNull(f.barrier.snapshot).owner
			var returns = 0
			var settlementDeliveries = 0
			var originalToken: ReaderSemanticTransportRetirement? = null
			val (source, call) = f.startOriginalInstalledTurn { admitted ->
				{ origin, context, _ ->
					lateinit var token: ReaderSemanticTransportRetirement
					token = ReaderSemanticTransportRetirement {
						admitted.retireOriginalSemanticTransportExact(token)
						returns++
					}
					originalToken = token
					assertEquals(ReaderSemanticTransportBindingResult.Bound,
						context.bindTransportRetirement(origin, token), "E407.Source.OriginalExecutorBinding")
					assertTrue(admitted.bindOriginalSemanticTransport(token) { settlementDeliveries++ },
						"E407.Source.SameOriginalSourceBinding")
					ReaderSemanticExecutableResult.Accepted
				}
			}
			assertTrue(f.foreground.isFrozenForTransitionActivation(), "E407.Source.LegacyForegroundFrozen")
			assertFalse(f.foreground.originalLegacyAdmissionRestored, "E407.Source.NoLegacyThaw")
			assertEquals(0, settlementDeliveries, "E407.Source.BindingIsNotSettlement")
			assertFalse(source.bindOriginalSemanticTransport(assertNotNull(originalToken)) {},
				"E407.Source.OriginalWaitingCellOnceOnly")
			assertEquals(ReaderSemanticTransportBindingResult.Rejected,
				call.context.bindTransportRetirement(call.origin, ReaderSemanticTransportRetirement {}),
				"E407.Source.OriginalExecutorCellOnceOnly")
			f.dispatchOriginalDown(source)
			f.coordinator.enqueue(ReaderTransitionFact.Intent(call.origin.transitionId, ReaderCancelIntent))
			f.coordinator.enqueue(ReaderTransitionFact.Intent(call.origin.transitionId, ReaderCancelIntent))
			assertEquals(1, returns, "E407.Source.OriginalCoordinatorRetiresCapturedTokenOnce")
			f.surface.listener.onGestureCancelled(source.intent.gestureId.value, source.deck.textureGeneration)
			assertEquals(0, settlementDeliveries, "E407.Source.LatePhysicalCallbackCannotNavigate")
			assertEquals(originalOwner, assertNotNull(f.barrier.snapshot).owner, "E407.Source.NoSyntheticSuccessor")
		}

	@Test
	@Config(shadows = [ReaderInstalledUnitPageSurfaceShadow::class])
	fun originalInstalledSourceDeliversOneTerminalObservationToBoundExecutorScopeWithoutMintingAReceipt() =
		withMaterialFixture(legacySelection = false, installedForeground = true) { f ->
			f.bootstrap()
			val originalOwner = assertNotNull(f.barrier.snapshot).owner
			val observations = mutableListOf<ReaderInstalledCurlSettlementObservation>()
			val (source, _) = f.startOriginalInstalledTurn { admitted ->
				{ origin, context, _ ->
					lateinit var token: ReaderSemanticTransportRetirement
					token = ReaderSemanticTransportRetirement { admitted.retireOriginalSemanticTransportExact(token) }
					assertEquals(ReaderSemanticTransportBindingResult.Bound,
						context.bindTransportRetirement(origin, token), "E407.Source.OriginalScopeBound")
					assertTrue(admitted.bindOriginalSemanticTransport(token, observations::add),
						"E407.Source.OriginalListenerBound")
					ReaderSemanticExecutableResult.Accepted
				}
			}
			f.dispatchOriginalDown(source)
			f.surface.listener.onSettlementStarted(source.intent.gestureId.value, source.deck.textureGeneration,
				"public-source", "public-target", PageChange.NEXT)
			assertTrue(observations.isEmpty(), "E407.Source.StartIsNotTerminal")
			repeat(2) { f.surface.listener.onSettlementCompleted(source.intent.gestureId.value,
				source.deck.textureGeneration, "public-target", 2, PageChange.NEXT) }
			val observation = observations.single()
			assertSame(source, observation.source, "E407.Source.OriginalMintedSourceNotCopy")
			assertSame(f.work, observation.source.work, "E407.Source.OriginalReturnedRasterCustody")
			assertTrue(f.deck == observation.source.deck, "E407.Source.OriginalRetainedDeck")
			assertEquals(2, observation.settledOrdinal, "E407.Source.OriginalObservedOrdinal")
			assertEquals(ReaderInstalledCurlSettlementStage.Completed, observation.stage, "E407.Source.TerminalStage")
			assertNull(observation.rendererCompletion, "E407.Source.LegacyCallbackDoesNotMintTypedDrawAuthority")
			assertEquals(0, f.coordinator.snapshot().consumedSettlementCount,
				"E407.Source.ObservationDoesNotMintFoliateSettlement")
			assertEquals(originalOwner, assertNotNull(f.barrier.snapshot).owner, "E407.Source.NoReceiptOrSuccessorMinted")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	internal fun withMaterialFixture(legacySelection: Boolean = true,
		foliateSessionId: String = "installed-material-public-fixture", installedForeground: Boolean = false,
		liveWebView: WebView? = null, test: (MaterialFixture) -> Unit) {
		val dispatcher = UnconfinedTestDispatcher()
		Dispatchers.setMain(dispatcher)
		var fixture: MaterialFixture? = null
		try {
			fixture = b407ConstructionStage("B407.Construction.MaterialFixture") { MaterialFixture(dispatcher, legacySelection, foliateSessionId, installedForeground, liveWebView) }
			test(fixture)
		} finally {
			try {
				b407ConstructionStage("B407.Teardown.OriginalCustody") { fixture?.close() }
			} finally { Dispatchers.resetMain() }
		}
	}

	/** The one coordinator issues every allocation/preparation/deck/frame/publication command.
	 * Only captured input and the platform renderer are controlled; no internal owner map is seeded. */
	internal class MaterialFixture(dispatcher: kotlinx.coroutines.CoroutineDispatcher,
		private val legacySelection: Boolean = true,
		private val foliateSessionId: String = "installed-material-public-fixture",
		private val installedForeground: Boolean = false,
		private val liveWebView: WebView? = null) {
		private var semanticDispatches = 0
		private var semanticOrigin: ReaderPresentationEventOrigin.SemanticCommand? = null
		private var semanticReceiptConsumer: ((ReaderPresentationEventReceipt) -> Unit)? = null
		private val installed = b407ConstructionStage("B407.Construction.Activation") {
			Fixture(bootstrapRequest = { origin, _, onReceipt ->
				semanticDispatches++
				semanticOrigin = origin
				semanticReceiptConsumer = onReceipt
				ReaderSemanticExecutableResult.Accepted
			})
		}
		private val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
		private val host = FrameLayout(activity)
		private val webView = liveWebView ?: WebView(activity)
		val bundle = ReaderPageTurnBundleSource()
		val geometry = ReaderPageTurnCaptureGeometry(120.0, 80.0, ReaderPageTurnLayoutMode.Single,
			listOf(ReaderPageTurnPageRect(ReaderPageTurnPageRole.Full, 0.0, 0.0, 120.0, 80.0)))
		val plan = ReaderPageRasterPreparationPlan(1, 3, "single", ReaderPlayLikeCurlReaderDirection.Ltr,
			1, 0, 0, 3, -1, 0, -1, 0,
			listOf(1, 2, 0).map { ReaderPageRasterBatchTarget(it, ReaderPageRasterPriority.CurrentChapter) },
			geometry, ReaderFoliateResolvedLayout(11L, 13L, 0L, 0, 1, 3, 120.0, 80.0))
		val foreground = ReaderForegroundWebViewOwnership()
		val curl: ReaderPlayLikeCurlFoliateController = ReaderPlayLikeCurlFoliateController(host, { webView },
			foregroundWebViewOwnership = foreground, bundleSource = bundle,
			readInstalledOwnerAndInput = if (legacySelection) null else ({ barrier.snapshot }),
			onRequestPrewarm = {}, onRequestRasterRepair = { _, _ -> error("Public material is already captured") },
			onGestureTerminal = { _, _, _ -> error("Installed callbacks cannot use legacy terminal effects") },
			rasterDispatcher = dispatcher)
		val surface: ReaderInstalledUnitPageSurfaceShadow = Shadow.extract(curl.surfaceView)
		val commands = mutableListOf<ReaderTransitionCommand>()
		// Default-inactive: only the bounded profile regression enables this UNIT input route.
		private var unitProfileSuccessor = false
		var unitOriginalSuccessorAllocation: ReaderMaterialGenerationAllocation? = null
			private set
		lateinit var work: ReaderActivatedRasterWork
		val deck: ReaderDeckLease? get() = exactDeck
		private var exactDeck: ReaderDeckLease? = null
		var completePreparation: (((() -> Unit)) -> Unit)? = null
		private var rasterReturns = 0
		private var physical: ReaderOwnerAndInputPublicationSnapshot? = null
		val input = ReaderPageInputSettlementHostController(
			initialPresentationInputPolicy = ReaderPresentationInputPolicy.ChromeOnly,
			initialLocalSafetyPolicy = readerPageOperationPolicy(ReaderPageReadinessState(
				textureDeck = ReaderTextureDeckState.Ready, interaction = ReaderPageInteractionState.Ready)),
			pointerRouter = ReaderPagePointerRouter(ReaderPageGestureLifecycle()),
			cancellationPort = object : ReaderPageHostCancellationPort {
				override fun cancelForPointerInterruption(gestureId: Long) = Unit
				override fun clearCompletedPointerOwnership(gestureId: Long) = Unit
				override fun cancelActiveRendererGesture(reason: ReaderPageLifecycleCancellationReason) = Unit
				override fun cancelReadableViewerDragPreview(reason: ReaderPageLifecycleCancellationReason) = Unit
				override fun clearNativeTapState(reason: ReaderPageLifecycleCancellationReason) = Unit
				override fun clearSwipeTouchState(reason: ReaderPageLifecycleCancellationReason) = Unit
				override fun cancelRendererWork(reason: ReaderPageLifecycleCancellationReason) = Unit
			})
		val barrier: ReaderOwnerAndInputPublicationBarrier = ReaderOwnerAndInputPublicationBarrier({ _, lease -> input.narrowOrVeto(lease) }) { publication ->
			if (!legacySelection) {
				// Staging is not publication. The ORIGINAL reader still sees the predecessor.
				assertIs<ReaderPortCommandResult.Rejected>(
					invokeOriginalEngineFixtureBoundary(OriginalEngineFixtureInvocationBoundary.StagedInstalledPublication) {
						requireOriginalAtomicPublicationConsumer().invoke(curl, publication)
					},
					"B408.AtomicPublication.StagedProjectionRejected")
			}
			physical = publication // Controlled unit host sink, read by the ORIGINAL installed input consumer.
			if (!legacySelection) assertIs<ReaderPortCommandResult.Rejected>(
				invokeOriginalEngineFixtureBoundary(OriginalEngineFixtureInvocationBoundary.PhysicalStagedInstalledPublication) {
					requireOriginalAtomicPublicationConsumer().invoke(curl, publication)
				},
				"B408.AtomicPublication.PhysicalStagingIsNotPublishedSnapshot")
			true
		}
		private val publisher = ReaderNativePagePresentationPublisher(curl.presentedFrameSource,
			{ error("Installed preparation cannot consult a legacy candidate") }, onEvent = { error("No legacy publication") })
		private val task4 = ReaderTask4TransitionPorts(MaterialClock,
			object : ReaderRasterPreparationCommandPort {
				override fun prepare(lease: ReaderRasterPreparationLease, callbacks: ReaderRasterLeaseFactEmitter) {
					work = ReaderActivatedRasterWork(installed.domain, installed.store, installed.snapshot, lease) {
						coordinator.materialCommandIsCurrent(lease)
					}
					val started = installed.owner.startInstalledWithOutcome(installed.domain, installed.store,
						installed.snapshot, lease, work::isCurrent, releasePhysicalPreparation = { returned -> returned(); true },
						startPhysicalPreparation = { completed ->
							completePreparation = completed
							for (page in plan.targets) {
								val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
									eraseColor(0xFFF8F4E8.toInt()); setHasAlpha(false)
								}
								val location = IntArray(2).also(host::getLocationInWindow)
								assertNotNull(bundle.cacheCurrentSnapshot(page.pageIndex, ReaderPageTurnTransitionKind.PortraitSlide,
									ReaderPageTurnCaptureResult(bitmap, Rect(location[0], location[1], location[0] + 120,
										location[1] + 80), geometry, 0L),
									lease.rasterGeneration, persist = false, work = work), "B407.Prepare.OriginalCache")
							}
							completed { work.recordPreparedTargets(plan.targets.size); callbacks.onProven(lease) }
							true
						}, work = work)
					started.publishLogicalOutcome { assertEquals(ReaderRasterPreparationStartOutcome.Accepted, it,
						"B407.Prepare.OriginalOwner") }
				}
				override fun release(lease: ReaderRasterPreparationLease, onReleased: () -> Unit) {
					assertEquals(work.lease, lease)
					work.retire()
					assertEquals(ReaderPortCommandResult.Accepted, work.preparationReturn.request(listOf(
						{ returned -> installed.owner.releaseInstalled(lease, returned) },
						{ returned -> bundle.releaseInstalled(work, returned) })) {
						installed.owner.forgetReturnedInstalled(work); rasterReturns++; onReleased()
					})
				}
				override fun cancel(transitionId: ReaderTransitionId) = Unit
			}, object : ReaderRendererDeckCommandPort {
				override fun reserve(lease: ReaderDeckLease, callbacks: ReaderDeckLeaseFactEmitter) {
					exactDeck = lease
					assertEquals(ReaderPortCommandResult.Accepted, curl.reserveInstalledDeck(work, lease, coordinator::enqueue),
						"B407.Deck.OriginalReservation")
				}
				override fun release(lease: ReaderDeckLease, onReleased: () -> Unit) {
					assertEquals(ReaderPortCommandResult.Accepted, curl.releaseInstalledDeck(lease, onReleased))
				}
				override fun cancelPreparation(transitionId: ReaderTransitionId) = Unit
			})
		val coordinator: ReaderResumableTransitionCoordinator = ReaderResumableTransitionCoordinator(object : ReaderResumableTransitionPorts {
			override val clock = MaterialClock
			override val semanticCommand: ReaderSemanticCommandPort get() = installed.executor
			override val ownerAndInputPublication = object : ReaderOwnerAndInputPublicationPort {
				override fun publish(command: ReaderTransitionCommand.CommitOwnerAndInputLease) =
					barrier.publish(command).also { projectPublishedAssociation(it) }
				override fun publish(command: ReaderTransitionCommand.PublishRetainedOwnerAndInputLease) =
					barrier.publish(command).also { projectPublishedAssociation(it) }
			}
			override fun issue(command: ReaderTransitionCommand, onFact: (ReaderTransitionFact) -> Unit) {
				commands += command
				when (command) {
					is ReaderTransitionCommand.AllocateMaterialBinding -> {
						if (unitProfileSuccessor && command.transitionId.operation == ReaderTransitionOperation.ExternalSemanticRelocation) {
							assertTrue(unitOriginalSuccessorAllocation == null && coordinator.materialCommandIsCurrent(command),
								"A407.Profile.OneCurrentOriginalAllocationInvocation")
							// PhysicalOwnership has no preparation reservation API. Derive only a
							// CONTROLLED UNIT scalar from the exact original bootstrap lease; this
							// does not execute the Native preparation controller's counter.
							val unitPreparation = Math.incrementExact(work.lease.preparationGeneration)
							val raster = assertNotNull(bundle.reserveActivatedRasterGeneration(command, installed.domain),
								"A407.Profile.OriginalSuccessorRasterReservation")
							val allocation = b407ConstructionStage("A407.Profile.OriginalSuccessorAllocationConstruction") {
								assertNotNull(curl.reserveActivatedMaterialBinding(command, installed.domain,
									webView, plan, unitPreparation, raster), "A407.Profile.OriginalSuccessorAllocation")
							}
							unitOriginalSuccessorAllocation = allocation
							// SAME original Curl allocation; non-initial profiles cannot be qualified
							// by a scalar PaginationProfileReady callback.
							onFact(ReaderTransitionFact.MaterialBindingAllocated(command.transitionId, allocation))
							return
						}
						assertEquals(ReaderTransitionOperation.BootstrapNativePage, command.transitionId.operation,
							"B407.Material.BootstrapOperation")
						assertIs<ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial>(command.transitionId.expectedBinding,
							"B407.Material.InitialExpectation")
						assertTrue(coordinator.materialCommandIsCurrent(command), "B407.Material.CommandCurrent")
						val raster = assertNotNull(bundle.reserveActivatedRasterGeneration(command, installed.domain),
							"B407.Material.OriginalRasterReservation")
						val allocation = b407ConstructionStage("B407.Material.AllocationConstruction") {
							assertNotNull(curl.reserveActivatedMaterialBinding(command, installed.domain,
								webView, plan, 1L, raster), "B407.Material.OriginalAllocation")
						}
						// Match the original Native allocation callback order. Only the actual Curl
						// profile may qualify this authentic provisional bootstrap binding.
						b407ConstructionStage("B407.Material.ProfileDelivery") {
							onFact(ReaderTransitionFact.PaginationProfileReady(command.transitionId,
								allocation.allocatedBinding.profileGeneration))
						}
						b407ConstructionStage("B407.Material.AllocationDelivery") {
							onFact(ReaderTransitionFact.MaterialBindingAllocated(command.transitionId, allocation))
						}
					}
					is ReaderTransitionCommand.RequestRasterPreparation -> {
						if (!unitProfileSuccessor || command.transitionId.operation != ReaderTransitionOperation.ExternalSemanticRelocation) {
							task4.issue(command, onFact)
						}
						// Profile regression stops at this ORIGINAL command, already recorded
						// above. Never fabricate successor work, Ready, deck or frame proof.
					}
					is ReaderTransitionCommand.PrepareFrameTarget -> assertEquals(ReaderPortCommandResult.Accepted,
						publisher.prepareTarget(command, onFact), "B407.Frame.OriginalPreparation")
					is ReaderTransitionCommand.RequestFramePresentation -> assertEquals(ReaderPortCommandResult.Accepted,
						publisher.present(command, onFact), "B407.Frame.OriginalRequest")
					is ReaderTransitionCommand.CancelOwnedWork -> {
						assertEquals(ReaderPortCommandResult.Accepted, publisher.cancelOwnedWork(command.transitionId))
						task4.issue(command, onFact)
					}
					else -> task4.issue(command, onFact)
				}
			}
		}, ReaderTransitionMode.Active, installed.snapshot.journal)
		val displayedProof: ReaderNativePagePresentationProof?
			get() = (barrier.snapshot?.owner as? ReaderPresentationFrameOwner.NativePage)?.proof

		init {
			activity.setContentView(host)
			if (liveWebView == null) host.addView(webView, FrameLayout.LayoutParams(120, 80))
			host.measure(android.view.View.MeasureSpec.makeMeasureSpec(120, android.view.View.MeasureSpec.EXACTLY),
				android.view.View.MeasureSpec.makeMeasureSpec(80, android.view.View.MeasureSpec.EXACTLY))
			host.layout(0, 0, 120, 80)
			if (liveWebView == null) webView.layout(0, 0, 120, 80)
			assertTrue(webView.isAttachedToWindow, "B407.Material.AttachedFixture")
			if (installedForeground) {
				assertEquals(ReaderPortCommandResult.Accepted, foreground.freezeForTransitionActivation(installed.domain),
					"E407.Foreground.OriginalSourceFrozen")
				assertEquals(ReaderPortCommandResult.Accepted, foreground.finalizeActivatedHandoff(installed.domain),
					"E407.Foreground.OriginalSourceFinalized")
			}
			assertEquals(ReaderPortCommandResult.Accepted, bundle.freezeForTransitionActivation(installed.domain))
			assertEquals(ReaderPortCommandResult.Accepted, bundle.finalizeActivatedHandoff(installed.domain))
			assertEquals(ReaderPortCommandResult.Accepted, curl.freezeDeckOwnershipForTransitionActivation(installed.domain))
			assertEquals(ReaderPortCommandResult.Accepted, curl.finalizeDeckOwnershipAfterTransitionActivation(
				installed.domain.freezeToken, null, emptySet()))
			assertEquals(ReaderPortCommandResult.Accepted, publisher.freezeForTransitionActivation(installed.domain))
			assertTrue(publisher.frozenFrameRetirementComplete(installed.domain))
			publisher.completeFrozenFrameRetirement(installed.domain)
			publisher.activateCommandOnly { specification -> candidate()?.takeIf { it.binding == specification.binding } }
			assertEquals(ReaderPortCommandResult.Accepted, input.freezeForTransitionActivation(installed.domain))
			assertEquals(ReaderPortCommandResult.Accepted, input.activateInstalledInput({ physical },
				{ assertNotNull(installed.snapshot.initialDecision).origin }))
			assertEquals(ReaderPortCommandResult.Accepted, input.finalizeActivatedHandoff(installed.domain))
		}

		/** Proposed PUBLIC original atomic sink consumer. Lookup is deliberately exact:
		 * no legacy selection fallback, synthetic completion, reflection into success state,
		 * or alternate method may make the absent behavioral prerequisite pass. */
		fun requireOriginalAtomicPublicationConsumer(): java.lang.reflect.Method = assertNotNull(
			curl.javaClass.methods.singleOrNull { method ->
				method.name == "onInstalledOwnerAndInputPublished" &&
					method.parameterTypes.contentEquals(arrayOf(ReaderOwnerAndInputPublicationSnapshot::class.java)) &&
					method.returnType == ReaderPortCommandResult::class.java
			}, "B408.Prerequisite.PublicOriginalAtomicPublicationConsumerMissing")

		private fun projectPublishedAssociation(result: ReaderOwnerAndInputPublicationResult) {
			if (!legacySelection && result is ReaderOwnerAndInputPublicationResult.Applied) {
				assertSame(physical, barrier.snapshot, "B408.AtomicPublication.OriginalSnapshotVisible")
				assertEquals(ReaderPortCommandResult.Accepted,
					invokeOriginalEngineFixtureBoundary(OriginalEngineFixtureInvocationBoundary.AppliedInstalledPublication) {
						requireOriginalAtomicPublicationConsumer().invoke(curl, assertNotNull(barrier.snapshot))
					},
					"B408.AtomicPublication.OriginalConsumerAccepted")
			}
		}

		fun rejectCopiedPublication() {
			val original = assertNotNull(barrier.snapshot)
			assertIs<ReaderPortCommandResult.Rejected>(
				invokeOriginalEngineFixtureBoundary(OriginalEngineFixtureInvocationBoundary.CopiedInstalledPublication) {
					requireOriginalAtomicPublicationConsumer().invoke(curl, original.copy())
				},
				"B408.AtomicPublication.CopiedSnapshotRejected")
			assertEquals(ReaderPortCommandResult.Accepted,
				invokeOriginalEngineFixtureBoundary(OriginalEngineFixtureInvocationBoundary.RecheckedInstalledPublication) {
					requireOriginalAtomicPublicationConsumer().invoke(curl, original)
				},
				"B408.AtomicPublication.RecheckDoesNotReissueAuthority")
		}

		fun candidate() = exactDeck?.let { curl.installedNativeCandidate(it, null, 120, 80) }

		fun start() {
			if (!legacySelection) requireOriginalAtomicPublicationConsumer()
			val reservation = assertNotNull(installed.snapshot.initialDecision?.neutralBootstrapReservation,
				"B407.Semantic.OriginalReservation")
			b407ConstructionStage("B407.Semantic.IntentDelivery") {
				coordinator.enqueue(ReaderTransitionFact.Intent(null, ReaderBootstrapNativePageIntent(reservation.handle)))
			}
			assertEquals(1, semanticDispatches, "B407.Semantic.OriginalDispatchCount")
			assertEquals(ReaderTransitionOperation.BootstrapNativePage, coordinator.snapshot().activeOperation,
				"B407.Semantic.BootstrapActive")
			val origin = assertNotNull(semanticOrigin, "B407.Semantic.OriginalOrigin")
			val semantic = b407ConstructionStage("B407.Semantic.BindingConstruction") {
				ReaderPresentationBinding(foliateSessionId, 1L, 2L, 0L,
					destinationCommitIdentity = ReaderDestinationCommitIdentity(foliateSessionId, 1L))
			}
			val receipt = b407ConstructionStage("B407.Semantic.ReceiptConstruction") {
				readerPresentationEventTransition(ReaderPresentationState(),
					ReaderPresentationReceiptVersion(origin.transitionId.readerSessionGeneration, null, 0L), false,
					ReaderPresentationEvent.FoliateRelocated(semantic, null), origin).receipt
			}
			assertEquals(ReaderPresentationEventDisposition.Accepted, receipt.disposition, "B407.Semantic.PureReceipt")
			assertSame(origin, receipt.origin, "B407.Semantic.CommandBoundReceipt")
			b407ConstructionStage("B407.Semantic.ReceiptDelivery") {
				assertNotNull(semanticReceiptConsumer, "B407.Semantic.OriginalCallback").invoke(receipt)
			}
			assertEquals(1, commands.filterIsInstance<ReaderTransitionCommand.AllocateMaterialBinding>().size,
				"B407.Material.AllocationCommandCount")
			assertEquals(1, commands.filterIsInstance<ReaderTransitionCommand.RequestRasterPreparation>().size,
				"B407.Prepare.CommandCount")
			assertEquals(1, commands.filterIsInstance<ReaderTransitionCommand.ReserveDeck>().size,
				"B407.Deck.CommandCount")
			val lease = assertNotNull(exactDeck, "B407.Deck.Reserved")
			assertEquals(1, surface.submitted.size, "B407.Deck.PlatformSubmitted")
			assertNotNull(candidate(), "B407.Deck.ActualOwnedPrepared")
			assertTrue(curl.surfaceView.rendererGeneration > 0L, "B407.Frame.UnitRendererContext")
			val specification = b407ConstructionStage("B407.Frame.TargetConstruction") {
				ReaderTransitionFrameTargetSpecification.NativePage(lease.transitionId,
					lease.transitionId.readerSessionGeneration, lease.binding.publicationGeneration, lease.binding,
					assertNotNull(lease.allocation), ReaderNativePageHostTokenState.AuthoritativeAbsent,
					ReaderPlayLikeCurlDeckTargetIdentity(curl.surfaceView.rendererGeneration, lease.textureGeneration,
						ReaderTransitionDeckRole.Initial), ReaderTransitionFrameGeometry(lease.binding.viewportGeneration,
						lease.binding.profileGeneration, 0, 0, 120, 80), 1L)
			}
			assertEquals(ReaderPortCommandResult.Accepted, b407ConstructionStage("B407.Frame.TargetDelivery") {
				coordinator.enqueueFrameTargetDeclaration(specification, lease.resourceKey)
			}, "B407.Frame.Declaration")
			assertEquals(1, surface.frames.size, "B407.Frame.ActualConsumerRegistered")
			assertNull(displayedProof, "B407.Frame.NotPreparedAsPresented")
		}

		fun presentAndCommit() {
			b407ConstructionStage("B407.Frame.OriginalConsumerDelivery") { surface.frames.values.single().run() }
			assertNotNull(displayedProof, "B407.Frame.PublishedFromConsumer")
			assertEquals(1, barrier.atomicCommitCount, "B407.Common.AtomicSuccess")
			assertNull(coordinator.snapshot().activeOperation, "B407.Common.TerminalSuccess")
			assertEquals(1, rasterReturns, "B407.Common.OriginalRasterReleased")
			val owner = assertIs<ReaderPresentationFrameOwner.NativePage>(assertNotNull(barrier.snapshot).owner)
			if (legacySelection) {
				curl.synchronizePresentationDecision(readerPresentationDecision(ReaderPresentationState(
					authority = ReaderPresentationAuthority.SettledNativePage(owner), binding = owner.proof.binding)))
			}
		}

		fun admitOriginalSource(onPhysical: (ReaderInstalledCurlSettlementObservation) -> Unit = {},
			requestHandle: ReaderSemanticRequestHandle = ReaderSemanticRequestHandle(1L)): ReaderInstalledCurlGestureSource {
			val lease = assertNotNull(exactDeck)
			val gesture = assertNotNull(input.dispatchPointer(ReaderPageHostPointerEvent.Down(110f, 40f, 100L)).gestureId,
				"B408.Waiting.OriginalPointer")
			assertEquals(ReaderPagePointerRoute.ClaimCurl(gesture),
				input.dispatchPointer(ReaderPageHostPointerEvent.Move(10f, 40f, 4f)).route,
				"B408.Waiting.OriginalClaim")
			val intent = ReaderPageTurnIntent(ReaderPageTurnDirection.Next, ReaderTransitionGestureId(gesture), requestHandle)
			assertEquals(ReaderPortCommandResult.Accepted, curl.beginInstalledCurlGesture(lease, intent, onPhysical),
				"B408.Waiting.OriginalAdmission")
			return assertNotNull(curl.originalInstalledCurlGestureSource(lease, intent), "B408.Waiting.OriginalMintedSource")
		}

		/** Reuse this fixture's ORIGINAL installed registry/executor/coordinator, not a second
		 * transport coordinator. Source, intent handle and executable origin all arise from admission.
		 * The caller must supply the real Engine request; this helper supplies no semantic receipt. */
		fun startOriginalInstalledTurn(
			onTransportBound: (ReaderSemanticTransportRetirement) -> Unit = {},
			onMutationStarted: () -> Unit = {},
			createRequest: (ReaderInstalledCurlGestureSource) -> ReaderSemanticExecutableRequest
		): Pair<ReaderInstalledCurlGestureSource, CarrierCall> {
			var request: ReaderSemanticExecutableRequest? = null
			var call: CarrierCall? = null
			val handle = installed.registry.register { origin, context, onReceipt ->
				call = CarrierCall(origin, context, onReceipt)
				// Observe the actual executor boundary; all evidence/retirement still delegates
				// to the original context. This does not create tokens or mutation generations.
				val observed = object : ReaderSemanticMutationStartEvidence by context {
					override fun bindTransportRetirement(origin: ReaderPresentationEventOrigin.SemanticCommand,
						retirement: ReaderSemanticTransportRetirement): ReaderSemanticTransportBindingResult =
						context.bindTransportRetirement(origin, retirement).also {
							if (it == ReaderSemanticTransportBindingResult.Bound) onTransportBound(retirement)
						}
					override fun mutationStarted() { context.mutationStarted(); onMutationStarted() }
				}
				assertNotNull(request, "E407.Turn.OriginalRequestReady")(origin, observed, onReceipt)
			}
			val source = admitOriginalSource(requestHandle = handle)
			request = createRequest(source)
			coordinator.enqueue(ReaderTransitionFact.Intent(null, source.intent))
			assertEquals(ReaderTransitionOperation.CurlClaimAndSettlement,
				coordinator.snapshot().activeOperation, "E407.Turn.OriginalCoordinatorWaiting")
			val invocation = assertNotNull(call, "E407.Turn.OriginalExecutorInvocation")
			assertEquals(source.deck.transitionId.readerSessionGeneration,
				invocation.origin.transitionId.readerSessionGeneration, "E407.Turn.ExactOriginalSession")
			assertTrue(invocation.context.isExecutionCurrent(invocation.origin, handle), "E407.Turn.OriginalExecutionCurrent")
			return source to invocation
		}

		fun dispatchOriginalDown(source: ReaderInstalledCurlGestureSource) {
			val down = MotionEvent.obtain(100L, 101L, MotionEvent.ACTION_DOWN, 110f, 40f, 0)
			try {
				assertEquals(ReaderPageCurlDispatchResult.Accepted, curl.onPageTouchEvent(down, source.intent.gestureId.value),
					"B408.Waiting.OriginalRendererAdmission")
			} finally { down.recycle() }
		}

		fun bootstrap() { start(); presentAndCommit() }

		/** CONTROLLED UNIT unsolicited destination, not a live Foliate receipt or plan.
		 * Original coordinator issues the relocation and retained-publication gate. */
		fun beginControlledUnitProfileSuccessor() {
			assertTrue(!unitProfileSuccessor && unitOriginalSuccessorAllocation == null &&
				::work.isInitialized && coordinator.snapshot().activeOperation == null,
				"A407.Profile.CompletedOriginalBootstrapRequired")
			val previous = assertNotNull(barrier.snapshot, "A407.Profile.RetainedOriginalPublicationRequired")
			val commit = assertNotNull(previous.binding.destinationCommitIdentity,
				"A407.Profile.OriginalDestinationCommitRequired")
			val destination = previous.binding.copy(preparationGeneration = null,
				rasterGeneration = null, textureGeneration = null,
				destinationCommitIdentity = commit.copy(commitSequence = Math.incrementExact(commit.commitSequence)))
			unitProfileSuccessor = true
			coordinator.enqueue(ReaderTransitionFact.FoliateDestinationCommitted(null, destination))
		}

		fun close() {
			installed.executor.clear()
			installed.registry.clear()
			publisher.dispose()
			// Fixture teardown relinquishes the selected frame before returning its original
			// renderer custody. This is cleanup, not a successful common/publication assertion.
			if (legacySelection) curl.synchronizePresentationDecision(readerPresentationDecision(ReaderPresentationState()))
			exactDeck?.let { retained ->
				if (curl.installedDeckFor(retained.resourceKey) != null) curl.releaseInstalledDeck(retained) { }
			}
			if (::work.isInitialized && installed.owner.installedWorkFor(work.lease.resourceKey) === work) {
				work.retire()
				installed.owner.releaseInstalled(work.lease) { }
				bundle.releaseInstalled(work) { }
			}
			activity.finish()
		}

		fun anchor(boundary: Long): ReaderWhispersyncAnchorReceipt {
			val lease = assertNotNull(exactDeck)
			return ReaderWhispersyncAnchorReceipt(lease.binding.foliateSessionId, "public-material-destination", 1, 0,
				lease.rasterGeneration, lease.textureGeneration, 1L, 1L, 1L, boundary, 11L, 13L, 1L, 0, 1, 3,
				"public-pagination", "public-layout", "public-settings", geometry,
				listOf(ReaderWhispersyncPageLocalRect(ReaderPageTurnPageRole.Full, 10.0 + boundary, 10.0, 12.0, 8.0)))
		}
	}

	private object MaterialClock : ReaderTransitionClock {
		override fun nowMillis() = 1_000L
		override fun schedule(atMillis: Long, action: () -> Unit) = ReaderTransitionClockRegistration { }
	}

	private fun publicReadinessManifest(work: ReaderActivatedRasterWork): ReaderPassiveRasterCaptureManifest {
		val issuer = ReaderPassiveRasterManifestIssuer()
		val original = issuer.replaceCanonicalCommit(ReaderPassiveRasterCanonicalCommit(
			captureEpoch = 1L, liveFoliateSessionId = work.lease.binding.foliateSessionId,
			publicationSessionGeneration = work.lease.binding.publicationGeneration,
			destinationCommitToken = "public-readiness-destination", rasterProfileKey = "public-profile",
			paginationFingerprint = "public-pagination", layoutFingerprint = "public-layout",
			decorationFingerprint = "public-decoration",
			viewportAndCaptureGeometry = ReaderPassiveRasterGeometry(120, 80, 0, 0, 120, 80),
			rasterGeneration = work.lease.rasterGeneration))
		return assertNotNull(issuer.issue(original, "public-readiness-target", 1))
	}

	private class ReadinessFixture {
		val runtime = ReadinessRuntime()
		val session = ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
			bitmap.takeUnless { it.isRecycled }?.recycle()
		}
		val adapter = ReaderPassiveRasterPreparationAdapter(
			session = session,
			liveManifestPort = ReaderPassiveRasterLiveManifestPort { _, _, _, _, _ ->
				error("A pre-start readiness consumer cannot query a live manifest")
			},
			bundleSource = ReaderPageTurnBundleSource(), initialCaptureEpoch = 1L
		)
	}

	private class ReadinessRuntime : ReaderPassiveRasterRuntimePort<android.graphics.Bitmap> {
		var ready = false
		var physicalOperations = 0
		var destructions = 0
		var commits = 0
		var commitCancellations = 0
		var commitReturned: (() -> Unit)? = null
		var onDestroy: (() -> Unit)? = null
		override val passiveSessionId = "installed-readiness-public-fixture"
		override val isReady: Boolean get() = ready
		override fun commit(manifest: ReaderPassiveRasterCaptureManifest, captureTarget: String,
			passiveCommitSequence: Long, onCommitted: (ReaderPassiveRasterCaptureReceipt?) -> Unit) {
			physicalOperations++
			commits++ // Original session retains its actual pending commit callback.
		}
		override fun capture(geometry: ReaderPassiveRasterGeometry, onCaptured: (android.graphics.Bitmap?) -> Unit) {
			physicalOperations++
			error("Readiness registration cannot dispatch a capture")
		}
		override fun cancelActiveCommit(onDrained: () -> Unit) {
			physicalOperations++
			commitCancellations++
			commitReturned = onDrained
		}
		override fun pause() { physicalOperations++ }
		override fun resume() { physicalOperations++ }
		override fun destroy() { physicalOperations++; destructions++; onDestroy?.invoke() }
	}

	private class Fixture(
		finalize: Boolean = true,
		rasterGeneration: Long = 17L,
		bootstrapRequest: ReaderSemanticExecutableRequest = { _, _, _ -> ReaderSemanticExecutableResult.Rejected }
	) {
		val owner = ReaderRasterPreparationPhysicalOwnership()
		val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(8L))
		val store = ReaderActivatedSessionSnapshotStore()
		val snapshot: ReaderActivatedSessionSnapshot
		var current = true
		var starts = 0
		var cancellations = 0
		var deliveries = 0
		var confirmations = 0
		var rejectCancellation = false
		var throwCancellation = false
		var completeDuringCancellation = false
		var complete: (((() -> Unit)) -> Unit)? = null
		var released: (() -> Unit)? = null
		private val id = ReaderTransitionId(3L, 5L, 1L, ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L))
		private val semantic = ReaderPresentationBinding("installed-raster-fixture", 1L, 2L, 0L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity("installed-raster-fixture", 1L))
		private val binding = semantic.copy(profileGeneration = 7L, preparationGeneration = 13L,
			rasterGeneration = rasterGeneration, textureGeneration = 19L)
		private val allocation = ReaderMaterialGenerationAllocation(id, binding, 13L, rasterGeneration, 19L)
		val lease = ReaderRasterPreparationLease(id, binding, 13L, rasterGeneration,
			ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Raster, rasterGeneration), allocation)

		val work: ReaderActivatedRasterWork
		val executor: ReaderSemanticCommandExecutor
		val registry = ReaderSemanticExecutableRequestRegistry(3L, activationStore = store)

		init {
			assertEquals(ReaderPortCommandResult.Accepted, owner.freezeForTransitionActivation(domain))
			if (finalize) assertEquals(ReaderPortCommandResult.Accepted, owner.finalizeActivatedHandoff(domain))
			executor = ReaderSemanticCommandExecutor(registry)
			assertEquals(ReaderPortCommandResult.Accepted, executor.freezeForTransitionActivation(domain))
			val gateway = ReaderTransitionGateway()
			val candidate = assertNotNull(gateway.reserveActivatedCandidate(3L, 5L, store, {}, {},
				registry::register, registry::discardCandidateReservations, executor::clear))
			val reservation = assertNotNull(registry.reserveNeutralBootstrap(domain, candidate, bootstrapRequest))
			val decision = ReaderInitialActivationDecision(ReaderInitialCommittedPresentationOrigin.Neutral(3L, 5L,
				ReaderInitialPresentationInputLease.ChromeOnly, ReaderInitialPresentationInputLease.ChromeOnly),
				null, null, reservation)
			assertEquals(ReaderActivationInstallResult.Installed,
				ReaderActivatedSessionInstallationBarrier(store).installTestActivatedSession(
					completeActivatedSessionPorts().copy(gateway = ReaderSnapshotBackedActivatedGatewayPort(candidate),
						semantic = executor), decision))
			snapshot = assertNotNull(store.snapshot)
			work = ReaderActivatedRasterWork(domain, store, snapshot, lease) { current }
		}

		fun installedRegistry() = ReaderExactPhysicalOwnerRegistry(
			ReaderLegacyInventorySource.RasterGenerationAndPersistence
		).also {
			assertEquals(ReaderPortCommandResult.Accepted, it.freezeForTransitionActivation(domain))
			assertEquals(ReaderPortCommandResult.Accepted, it.finalizeActivatedHandoff(domain))
		}

		fun physicalDescriptor() = ReaderExactPhysicalOwnerDescriptor(
			kind = ReaderTransitionResourceKind.Raster,
			state = ReaderLegacyResourceState.Running
		)

		fun descriptor() = ReaderRasterPreparationPhysicalRestartDescriptor(binding,
			ReaderRasterPreparationPhysicalOperation.Prewarm, 13L, 17L)

		fun start(snapshot: ReaderActivatedSessionSnapshot = this.snapshot, accept: Boolean = true,
			synchronous: Boolean = false, isCurrent: () -> Boolean = { current }): ReaderRasterPreparationStartOutcome {
			val release: ((() -> Unit) -> Boolean) = { onReleased ->
				cancellations++; released = onReleased
				if (completeDuringCancellation) assertNotNull(complete).invoke { deliveries++ }
				if (throwCancellation) throw IllegalStateException("installed-preparation-cancellation-ambiguous")
				!rejectCancellation
			}
			val start: (((() -> Unit) -> Unit) -> Boolean) = { callback ->
				starts++; complete = callback
				if (synchronous) callback { deliveries++ }
				accept
			}
			val result = owner.startInstalledWithOutcome(domain, store, snapshot, lease, isCurrent, release, start, work)
			result.publishLogicalOutcome {}
			return result.outcome
		}

		fun release(): ReaderPortCommandResult = owner.releaseInstalled(lease) { confirmations++ }
	}
}

/** Fixture-only constructor/stage classification. Never retain the caught payload or its cause. */
private inline fun <T> b407ConstructionStage(label: String, operation: () -> T): T = try {
	operation()
} catch (_: IllegalArgumentException) {
	throw AssertionError(label)
}

/** Controlled UNIT renderer boundary, not an ownership inventory or production capability.
 * Callbacks are the original listener/transfer/frame consumers captured by the real controller.
 * The actual object's ONE Robolectric shadow must also be a compatible receiver for the
 * configured GLSurfaceView shadow. ShadowWrangler resolves inherited methods by their
 * defining real class; sibling shadows cannot receive that superclass's method handles. */
@Implements(PageSurfaceView::class)
internal class ReaderInstalledUnitPageSurfaceShadow : Task8ImmediateGlSurfaceViewShadow() {
	@RealObject private lateinit var surface: PageSurfaceView
	val submitted = mutableListOf<PageDeck<Bitmap>>()
	val frames = linkedMapOf<Long, Runnable>()
	val overlays = mutableListOf<List<PageOverlayImage<Bitmap>>>()
	val cancellations = mutableListOf<Long>()
	var throwAfterInput = false
	private var acceptedGesture: Long? = null
	private var nextFrame = 0L
	val listener: PageSurfaceListener get() = surface.pageSurfaceListener

	/** Prospective boundary witnesses forward the ORIGINAL constructor/callback arguments.
	 * Robolectric 4.16.1 ShadowGLSurfaceView uses the same @Direct constructor forwarding;
	 * no root, listener, executor, ownership record or renderer readiness is replaced here. */
	@Implementation
	override fun __constructor__(context: Context) {
		OriginalEngineFixtureConstructionProbe.at(OriginalEngineFixtureConstructionProbe.Boundary.PageSurfaceConstructor) {
			reflector(OriginalSurfaceConstruction::class.java, surface).__constructor__(context)
		}
	}

	@Implementation
	fun registerMainTerminalExecutor(executor: PageSurfaceView.MainTerminalExecutor) {
		OriginalEngineFixtureConstructionProbe.at(OriginalEngineFixtureConstructionProbe.Boundary.MainTerminalRegistration) {
			reflector(OriginalSurfaceConstruction::class.java, surface).registerMainTerminalExecutor(executor)
		}
	}

	@Implementation
	fun setPageSurfaceListener(listener: PageSurfaceListener?) {
		OriginalEngineFixtureConstructionProbe.at(OriginalEngineFixtureConstructionProbe.Boundary.OriginalSurfaceListenerRegistration) {
			reflector(OriginalSurfaceConstruction::class.java, surface).setPageSurfaceListener(listener)
		}
	}

	@Implementation
	fun setOwnershipCallbackCapacityListener(listener: Runnable) {
		OriginalEngineFixtureConstructionProbe.at(OriginalEngineFixtureConstructionProbe.Boundary.OwnershipCapacityRegistration) {
			reflector(OriginalSurfaceConstruction::class.java, surface).setOwnershipCallbackCapacityListener(listener)
		}
	}

	@ForType(PageSurfaceView::class)
	interface OriginalSurfaceConstruction {
		@Direct fun __constructor__(context: Context)
		@Direct fun registerMainTerminalExecutor(executor: PageSurfaceView.MainTerminalExecutor)
		@Direct fun setPageSurfaceListener(listener: PageSurfaceListener?)
		@Direct fun setOwnershipCallbackCapacityListener(listener: Runnable)
		@Direct fun cancelGesture(gestureId: Long)
	}

	/** This shadow replaces the GL boundary, so PageRenderer.onSurfaceCreated never runs.
	 * Supply only the controlled unit context identity required by typed frame targets;
	 * this is NOT preparation, ownership transfer or presented-frame acknowledgement. */
	@Implementation
	fun getRendererGeneration(): Long = 1L

	@Implementation
	fun submitDeckWithResult(deck: PageDeck<Bitmap>, transferred: Runnable): PageSurfaceDeckSubmissionResult {
		submitted += deck
		// Exercise callback-before-ownership buffering in the ORIGINAL physical lease.
		listener.onDeckPrepared(deck.generationId)
		transferred.run()
		return PageSurfaceDeckSubmissionResult.accepted()
	}

	@Implementation
	fun requestNativePagePresentedFrame(generation: Long, presented: Runnable): Long {
		check(submitted.any { it.generationId == generation })
		return (++nextFrame).also { frames[it] = presented }
	}

	@Implementation
	fun cancelPresentedFrameRequest(requestId: Long): Boolean = frames.remove(requestId) != null

	@Implementation
	fun replacePageOverlays(generation: Long, replacement: List<PageOverlayImage<Bitmap>>): PageOverlayUpdateResult {
		check(submitted.any { it.generationId == generation })
		overlays += replacement.toList()
		return PageOverlayUpdateResult.ACCEPTED
	}

	@Implementation
	fun releaseDeck(generation: Long): PageSurfaceDeckReleaseResult {
		listener.onDeckReleased(generation, DeckReleaseReason.EXPLICIT)
		return PageSurfaceDeckReleaseResult.accepted()
	}

	/** Explicit UNIT interaction boundary. The controlled renderer admits the real controller's
	 * command, but does not produce settlement, semantic authority, publication or frame ACKs. */
	@Implementation
	fun onPageTouchEvent(event: MotionEvent, gestureId: Long): Boolean {
		check(submitted.size == 1 && gestureId > 0L)
		if (event.actionMasked == MotionEvent.ACTION_DOWN) acceptedGesture = gestureId
		if (throwAfterInput) throw IllegalStateException("public-unit-input-ambiguous")
		return acceptedGesture == gestureId
	}

	@Implementation
	fun cancelGesture(gestureId: Long) {
		if (acceptedGesture == null && gestureId == PageSurfaceView.NO_GESTURE_ID) {
			reflector(OriginalSurfaceConstruction::class.java, surface).cancelGesture(gestureId)
			return
		}
		check(acceptedGesture == gestureId)
		cancellations += gestureId // The test must separately deliver the original listener callback.
	}

	@Implementation
	fun setSelectedFrameGeneration(generation: Long) { }
}
