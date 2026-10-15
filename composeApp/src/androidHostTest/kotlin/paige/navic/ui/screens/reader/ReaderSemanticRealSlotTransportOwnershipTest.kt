package paige.navic.ui.screens.reader

import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import paige.navic.reader.*

/**
 * Real gateway/registry/executor/coordinator ownership assertions; original ten observed in
 * ownership190, additional ordering cases require fresh execution. Initial neutral policy
 * and local receipt fixtures are host scaffolding, not Native root authentication or
 * transport/matcher acceptance. Origins below are executor-created only.
 */
@RunWith(RobolectricTestRunner::class)
class ReaderSemanticRealSlotTransportOwnershipTest {
	@Test
	fun cancellationBeforeTransportBindingDisposesLateExactRetirementOnceOutsideLocks() {
		val fixture = Fixture()
		val call = fixture.dispatch()
		fixture.cancel()
		val cleanup = CleanupProbe(fixture)
		val result = call.context.bindTransportRetirement(call.origin, cleanup)
		assertTrue(result == ReaderSemanticTransportBindingResult.RetiredAndDisposed,
			"A canceled real slot must dispose its late exact transport binding, not resurrect work")
		assertTrue(cleanup.calls == 1 && cleanup.outsideLocks, "Late cancellation cleanup must be exact once and outside ownership locks")
		assertFalse(call.context.reportAsyncFailure(call.origin, ReaderSemanticAsyncFailure.Rejected),
			"Canceled slot failure has no outcome authority")
		fixture.cancel()
		assertTrue(cleanup.calls == 1 && fixture.executor.activeSlotCount == 0 && fixture.registry.activeHandleCount == 0,
			"Duplicate cancel must not duplicate transport cleanup or restore request custody")
	}

	@Test
	fun cancellationAfterTransportBindingDisposesOnlyBoundExactRetirementOnceOutsideLocks() {
		val fixture = Fixture()
		val call = fixture.dispatch()
		val cleanup = CleanupProbe(fixture)
		assertTrue(call.context.bindTransportRetirement(call.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound,
			"The authentic active slot must own its bound transport retirement")
		fixture.cancel(); fixture.cancel()
		assertTrue(cleanup.calls == 1 && cleanup.outsideLocks, "Cancellation must retire exact transport once outside all owner locks")
		assertFalse(call.context.reportAsyncFailure(call.origin, ReaderSemanticAsyncFailure.Threw),
			"Retired transport cannot publish an async outcome")
	}

	@Test
	fun closedAndReplacedRealSlotsDisposeTransportAndRejectDelayedFailure() {
		for (terminal in listOf(ReaderTransitionFact.PublicationClosed(null), ReaderTransitionFact.PublicationReplaced(null))) {
			val fixture = Fixture()
			val call = fixture.dispatch()
			val cleanup = CleanupProbe(fixture)
			assertTrue(call.context.bindTransportRetirement(call.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound,
				"Actual slot must bind before publication termination")
			fixture.gateway.enqueue(terminal)
			assertTrue(cleanup.calls == 1 && cleanup.outsideLocks && fixture.coordinator.snapshot().releaseOnlySink,
				"Publication termination retires exact transport behind permanent ordinary fence")
			assertFalse(call.context.reportAsyncFailure(call.origin, ReaderSemanticAsyncFailure.Rejected),
				"Closed or replaced slot failure cannot revive semantic work")
		}
	}

	@Test
	fun supersededRealSlotRejectsStaleAndOtherActualSlotFailuresWithoutRetiringSuccessor() {
		val fixture = Fixture()
		val first = fixture.dispatch()
		val cleanup = CleanupProbe(fixture)
		assertTrue(first.context.bindTransportRetirement(first.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound,
			"First real slot must bind its transport")
		val second = fixture.dispatch()
		assertTrue(first.origin != second.origin, "Distinct actual commands must use distinct authentic slot origins")
		assertTrue(cleanup.calls == 1 && cleanup.outsideLocks, "Supersession must retire only first transport once")
		assertFalse(first.context.reportAsyncFailure(first.origin, ReaderSemanticAsyncFailure.Threw), "Superseded failure is stale")
		assertFalse(second.context.reportAsyncFailure(first.origin, ReaderSemanticAsyncFailure.Rejected), "Unknown exact slot is not authority")
		assertFalse(first.context.reportAsyncFailure(second.origin, ReaderSemanticAsyncFailure.Rejected), "A stale context cannot borrow the actual successor origin")
		assertTrue(fixture.executor.activeSlotCount == 1 && !fixture.coordinator.snapshot().releaseOnlySink,
			"Stale or mismatched failure cannot affect the genuine successor")
		fixture.cancel()
	}

	@Test
	fun frozenRealSlotRetiresBoundTransportAndRejectsBindingAndAsyncOutcome() {
		val fixture = Fixture()
		val call = fixture.dispatch()
		val cleanup = CleanupProbe(fixture)
		assertTrue(call.context.bindTransportRetirement(call.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound,
			"Actual slot must own transport before freeze")
		val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(71L))
		assertTrue(fixture.executor.freezeForTransitionActivation(domain) == ReaderPortCommandResult.Accepted,
			"Existing executor must own freeze admission")
		assertTrue(cleanup.calls == 1 && cleanup.outsideLocks, "Freeze must retire transport outside locks without moving its clock")
		assertFalse(call.context.reportAsyncFailure(call.origin, ReaderSemanticAsyncFailure.Rejected), "Frozen source cannot admit async effects")
		val lateCleanup = CleanupProbe(fixture)
		assertTrue(call.context.bindTransportRetirement(call.origin, lateCleanup) == ReaderSemanticTransportBindingResult.RetiredAndDisposed,
			"Frozen late binding must fail closed and dispose its exact transport")
		assertTrue(lateCleanup.calls == 1 && lateCleanup.outsideLocks, "Frozen late transport disposal must remain exact once")
		fixture.executor.snapshotFrozenOwnership().forEach { row ->
			fixture.executor.drainFrozenOwnership(row.physicalIdentity) {}
		}
		fixture.executor.clear()
	}

	@Test
	fun delayedPremutationAsyncFailureReachesRealCoordinatorRetryThroughExactRequestCustody() {
		for (failure in ReaderSemanticAsyncFailure.entries) {
			val fixture = Fixture()
			val first = fixture.dispatch()
			val cleanup = CleanupProbe(fixture)
			assertTrue(first.context.bindTransportRetirement(first.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound,
				"Real sender retirement must bind before delayed failure")
			assertTrue(onWorker { first.context.reportAsyncFailure(first.origin, failure) },
				"Authentic delayed pre-mutation failure must be admitted by the actual slot context")
			fixture.drain()
			assertTrue(fixture.coordinator.snapshot().lastOutcome == ReaderTransitionOutcomeKind.Failed &&
				!fixture.coordinator.snapshot().releaseOnlySink && fixture.registry.activeHandleCount == 1 && fixture.executor.activeSlotCount == 0,
				"Pre-mutation async failure must reach Retriable while retaining one genuine request")
			assertTrue(cleanup.calls == 1 && cleanup.outsideLocks, "Delayed failure must retire sender once outside locks")
			assertFalse(first.context.reportAsyncFailure(first.origin, failure), "Duplicate async failure must be inert")
			fixture.gateway.enqueue(ReaderTransitionFact.Retry(null))
			assertTrue(fixture.calls.size == 2 && fixture.registeredHandles.size == 1 &&
				fixture.calls[1].origin != first.origin, "Common Retry must readmit same real request with a fresh authentic slot")
			assertFalse(first.context.reportAsyncFailure(first.origin, failure), "Old async failure cannot retire retried slot")
			fixture.cancel()
		}
	}

	@Test
	fun delayedMutationStartedAsyncFailureClosesRealCoordinatorWithoutReplay() {
		for (failure in ReaderSemanticAsyncFailure.entries) {
			val fixture = Fixture()
			val call = fixture.dispatch()
			val cleanup = CleanupProbe(fixture)
			assertTrue(call.context.bindTransportRetirement(call.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound,
				"Real sender retirement must bind before mutation")
			call.context.mutationStarted()
			assertTrue(onWorker { call.context.reportAsyncFailure(call.origin, failure) },
				"Exact delayed post-mutation failure must reach the actual coordinator mailbox")
			fixture.drain()
			fixture.gateway.enqueue(ReaderTransitionFact.Retry(null))
			assertTrue(fixture.coordinator.snapshot().releaseOnlySink && fixture.calls.size == 1 &&
				fixture.registry.activeHandleCount == 0 && fixture.executor.activeSlotCount == 0,
				"Started async mutation cannot be retried or replayed")
			assertTrue(cleanup.calls == 1 && cleanup.outsideLocks, "Post-mutation transport retires exactly once outside locks")
			assertFalse(call.context.reportAsyncFailure(call.origin, failure), "Duplicate post-mutation failure is inert")
		}
	}

	@Test
	fun failureAfterExactReceiptIsRejectedButGenericDuplicateExecutableReceiptStillViolates() {
		val fixture = Fixture()
		val call = fixture.dispatch()
		fixture.deliverDestination(call)
		assertTrue(fixture.executor.activeSlotCount == 0 && fixture.registry.activeHandleCount == 0,
			"Exact executable receipt must retire real slot and request")
		assertFalse(call.context.reportAsyncFailure(call.origin, ReaderSemanticAsyncFailure.Threw),
			"Async failure after receipt cannot become new outcome authority")
		assertTrue(!fixture.coordinator.snapshot().releaseOnlySink, "Rejected async failure must be inert")
		fixture.deliverDestination(call)
		assertTrue(fixture.coordinator.snapshot().releaseOnlySink,
			"Generic duplicate executable receipt must remain visible as a genuine contract violation")
	}

	@Test
	fun failureAfterCancellationIsRejectedButGenericLateExecutableReceiptStillViolates() {
		val fixture = Fixture()
		val call = fixture.dispatch()
		fixture.cancel()
		assertFalse(call.context.reportAsyncFailure(call.origin, ReaderSemanticAsyncFailure.Rejected),
			"Canceled async outcome cannot acquire authority")
		assertTrue(!fixture.coordinator.snapshot().releaseOnlySink, "Canceled async outcome must be inert")
		fixture.deliverDestination(call)
		assertTrue(fixture.coordinator.snapshot().releaseOnlySink,
			"Illegal generic late executable receipt cannot be blank-suppressed by transport retirement")
	}

	@Test
	fun activeContextRejectsOtherRealSlotOriginAndDuplicateTransportBinding() {
		val fixture = Fixture()
		val retired = fixture.dispatch()
		val current = fixture.dispatch()
		val wrongCleanup = CleanupProbe(fixture)
		assertTrue(current.context.bindTransportRetirement(retired.origin, wrongCleanup) == ReaderSemanticTransportBindingResult.Rejected,
			"Binding cannot borrow another executor-created origin")
		assertTrue(wrongCleanup.calls == 0, "Unknown binding remains caller-owned rather than being falsely retired")
		val cleanup = CleanupProbe(fixture)
		assertTrue(current.context.bindTransportRetirement(current.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound,
			"Exact active slot admits one authentic transport binding")
		val duplicate = CleanupProbe(fixture)
		assertTrue(current.context.bindTransportRetirement(current.origin, duplicate) == ReaderSemanticTransportBindingResult.Rejected,
			"Duplicate binding cannot replace the slot's exact transport owner")
		fixture.cancel()
		assertTrue(cleanup.calls == 1 && cleanup.outsideLocks && duplicate.calls == 0,
			"Cancellation retires only bound owner; rejected duplicate remains caller-owned")
	}

	@Test
	fun asyncFailureBeforeSynchronousReturnIsNotLostOrAppliedTwice() {
		for (failure in ReaderSemanticAsyncFailure.entries) {
			for (mutation in listOf(false, true)) {
				for (completion in 0..2) {
					val fixture = Fixture()
					val cleanup = CleanupProbe(fixture)
					var bound = false
					var admitted = false
					val call = fixture.dispatch { current ->
						bound = current.context.bindTransportRetirement(current.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound
						if (mutation) current.context.mutationStarted()
						admitted = current.context.reportAsyncFailure(current.origin, failure)
						when (completion) {
							0 -> ReaderSemanticExecutableResult.Accepted
							1 -> ReaderSemanticExecutableResult.Rejected
							else -> error("fixture synchronous failure")
						}
					}
					fixture.drain()
					assertTrue(bound && admitted, "Registration must bind the actual invocation before executable starts")
					assertTrue(cleanup.calls == 1 && cleanup.outsideLocks && fixture.executor.activeSlotCount == 0,
						"Pre-return async completion consumes transport exactly once outside locks")
					assertTrue(fixture.coordinator.snapshot().lastOutcome == ReaderTransitionOutcomeKind.Failed &&
						fixture.coordinator.snapshot().releaseOnlySink == mutation && fixture.registry.activeHandleCount == if (mutation) 0 else 1,
						"Async completion must survive the later authentic return without duplicating or changing its outcome")
					assertFalse(call.context.reportAsyncFailure(call.origin, failure), "Pre-return duplicate async failure is inert")
					assertTrue(fixture.processed.count { it == ReaderTransitionFactKind.CommandRejected } == (if (mutation) 0 else 1) &&
						fixture.processed.count { it == ReaderTransitionFactKind.SemanticPortContractViolated } == (if (mutation) 1 else 0),
						"The same invocation publishes exactly one truthful async outcome despite its later return")
					fixture.gateway.enqueue(ReaderTransitionFact.Retry(null))
					assertTrue(fixture.calls.size == if (mutation) 1 else 2, "Only genuine pre-mutation failure retains same-handle Retry")
					assertTrue(fixture.registeredHandles.size == 1 && fixture.coordinator.snapshot().maxAdvanceDepth == 1,
						"Async completion uses the existing invocation FIFO, not a new request or recursive reduction")
					fixture.cancel()
				}
			}
		}
	}

	@Test
	fun queuedCancellationBeforeAdmittedAsyncOutcomeCannotReplayMutationOrRestoreRetry() {
		for (mutation in listOf(false, true)) {
			for (failure in ReaderSemanticAsyncFailure.entries) {
				val fixture = Fixture()
				val cleanup = CleanupProbe(fixture)
				var bound = false
				var admitted = false
				val call = fixture.dispatch { current ->
					bound = current.context.bindTransportRetirement(current.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound
					if (mutation) current.context.mutationStarted()
					fixture.gateway.enqueue(ReaderTransitionFact.Intent(null, ReaderCancelIntent))
					admitted = current.context.reportAsyncFailure(current.origin, failure)
					ReaderSemanticExecutableResult.Accepted
				}
				fixture.drain()
				assertTrue(bound && admitted && cleanup.calls == 1 && cleanup.outsideLocks,
					"An actual active slot may report while its earlier cancellation is still queued")
				fixture.gateway.enqueue(ReaderTransitionFact.Retry(null))
				assertTrue(fixture.coordinator.snapshot().releaseOnlySink == mutation && fixture.calls.size == 1 &&
					fixture.registry.activeHandleCount == 0 && fixture.executor.activeSlotCount == 0,
					"FIFO cancellation forbids renewed Retry, while already-admitted mutation failure still permanently fences ordinary work")
				assertFalse(call.context.reportAsyncFailure(call.origin, failure), "Retired context cannot readmit another async failure")
			}
		}
	}

	@Test
	fun reentrantCancellationRetirementCannotDisposeTheSuccessorOrReadmitOldOutcome() {
		val fixture = Fixture()
		val first = fixture.dispatch()
		var calls = 0
		var outsideLocks = false
		var duplicateRejected = false
		var successor: Call? = null
		val cleanup = ReaderSemanticTransportRetirement {
			calls += 1
			outsideLocks = onWorker { fixture.executor.retirementSnapshot(); fixture.registry.activeHandleCount; true }
			duplicateRejected = !first.context.reportAsyncFailure(first.origin, ReaderSemanticAsyncFailure.Threw)
			successor = fixture.dispatch()
		}
		assertTrue(first.context.bindTransportRetirement(first.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound)
		fixture.cancel()
		assertTrue(calls == 1 && outsideLocks && duplicateRejected && successor != null,
			"Ownership must be consumed before invoking reentrant external retirement")
		assertTrue(fixture.executor.activeSlotCount == 1 && fixture.registry.activeHandleCount == 1 &&
			!fixture.coordinator.snapshot().releaseOnlySink, "Old retirement cannot dispose new exact request custody")
		assertFalse(first.context.reportAsyncFailure(first.origin, ReaderSemanticAsyncFailure.Rejected))
		fixture.cancel()
		assertTrue(calls == 1 && fixture.executor.activeSlotCount == 0 && fixture.registry.activeHandleCount == 0)
	}

	@Test
	fun reentrantAsyncRetirementRetriesSameRequestWithoutDisposingFreshSlot() {
		val fixture = Fixture()
		val first = fixture.dispatch()
		var calls = 0
		var duplicateRejected = false
		var outsideLocks = false
		val cleanup = ReaderSemanticTransportRetirement {
			calls += 1
			duplicateRejected = !first.context.reportAsyncFailure(first.origin, ReaderSemanticAsyncFailure.Threw)
			fixture.gateway.enqueue(ReaderTransitionFact.Retry(null))
			outsideLocks = onWorker { fixture.executor.retirementSnapshot(); fixture.registry.activeHandleCount; true }
		}
		assertTrue(first.context.bindTransportRetirement(first.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound)
		assertTrue(first.context.reportAsyncFailure(first.origin, ReaderSemanticAsyncFailure.Rejected))
		fixture.drain()
		assertTrue(calls == 1 && duplicateRejected && outsideLocks && fixture.calls.size == 2 && fixture.registeredHandles.size == 1,
			"Exact async outcome and request custody must precede reentrant sender cleanup and same-handle Retry")
		assertTrue(fixture.calls[1].origin != first.origin && fixture.executor.activeSlotCount == 1 && fixture.registry.activeHandleCount == 1 &&
			!fixture.coordinator.snapshot().releaseOnlySink, "Old async retirement must not dispose the genuine new Retry slot")
		fixture.cancel()
		assertTrue(calls == 1 && fixture.executor.activeSlotCount == 0 && fixture.registry.activeHandleCount == 0)
	}

	@Test
	fun receiptAndClearRetireExactBoundTransportOnceAndLateBindingIsConsumedBeforeCallback() {
		for (receipt in listOf(false, true)) {
			val fixture = Fixture()
			val call = fixture.dispatch()
			val cleanup = CleanupProbe(fixture)
			assertTrue(call.context.bindTransportRetirement(call.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound)
			if (receipt) fixture.deliverDestination(call) else fixture.executor.clear()
			assertTrue(cleanup.calls == 1 && cleanup.outsideLocks && fixture.registry.activeHandleCount == 0 && fixture.executor.activeSlotCount == 0)
			var lateCalls = 0
			var reentrantRejected = false
			lateinit var late: ReaderSemanticTransportRetirement
			late = ReaderSemanticTransportRetirement {
				lateCalls += 1
				reentrantRejected = call.context.bindTransportRetirement(call.origin, late) == ReaderSemanticTransportBindingResult.Rejected &&
					!call.context.reportAsyncFailure(call.origin, ReaderSemanticAsyncFailure.Rejected)
			}
			assertTrue(call.context.bindTransportRetirement(call.origin, late) == ReaderSemanticTransportBindingResult.RetiredAndDisposed)
			assertTrue(lateCalls == 1 && reentrantRejected, "Late exact cleanup must consume ownership before external reentrancy")
			fixture.executor.clear()
			assertTrue(cleanup.calls == 1 && lateCalls == 1)
		}
	}

	@Test
	fun synchronousReceiptRetirementReentrantNewerReceiptRetainsLatestAuthority() {
		assertReceiptRetirementReentrantNewerReceiptRetainsLatestAuthority(synchronous = true)
	}

	@Test
	fun asynchronousReceiptRetirementReentrantNewerReceiptRetainsLatestAuthority() {
		assertReceiptRetirementReentrantNewerReceiptRetainsLatestAuthority(synchronous = false)
	}

	private fun assertReceiptRetirementReentrantNewerReceiptRetainsLatestAuthority(synchronous: Boolean) {
		val fixture = Fixture(captureAcceptedFacts = true)
		var receipts: Pair<ReaderPresentationEventReceipt, ReaderPresentationEventReceipt>? = null
		var bound = false
		var cleanupCalls = 0
		var outsideLocks = false
		var newerCallbackReturned = false
		fun bindAndDeliver(current: Call) {
			val pair = fixture.destinationReceipts(current)
			receipts = pair
			val cleanup = ReaderSemanticTransportRetirement {
				cleanupCalls += 1
				outsideLocks = onWorker { fixture.executor.retirementSnapshot(); fixture.registry.activeHandleCount; true }
				current.onReceipt(pair.second)
				newerCallbackReturned = true
			}
			bound = current.context.bindTransportRetirement(current.origin, cleanup) == ReaderSemanticTransportBindingResult.Bound
			current.onReceipt(pair.first)
		}
		val call = if (synchronous) fixture.dispatch { current ->
			bindAndDeliver(current)
			ReaderSemanticExecutableResult.Accepted
		} else fixture.dispatch().also(::bindAndDeliver)
		fixture.drain()

		// All assertions run after the real callback drain, outside caught executable/disposal callbacks.
		assertTrue(bound && newerCallbackReturned, "Exact binding and nested authentic callback must both complete")
		assertTrue(cleanupCalls == 1 && outsideLocks, "Receipt retirement must dispose exactly once outside both ownership locks")
		val (older, newer) = assertNotNull(receipts)
		val olderDestination = assertNotNull(older.semanticReceipt).binding
		val newerDestination = assertNotNull(newer.semanticReceipt).binding
		assertTrue(olderDestination.destinationCommitIdentity != newerDestination.destinationCommitIdentity,
			"The nested receipt must carry a distinct newer destination commit")
		assertEquals(call.origin, older.origin)
		assertEquals(call.origin, newer.origin)
		assertEquals(older.version, newer.preVersion)
		assertTrue(newer.version.eventSequence > older.version.eventSequence,
			"Host policy receipt versions must advance coherently from A to B")
		val violation = fixture.acceptedFacts.filterIsInstance<ReaderTransitionFact.SemanticPortContractViolated>().single()
		assertEquals(ReaderSemanticPortContractViolationReason.DuplicateCallback, violation.reason)
		assertEquals(ReaderSaturatingCallbackCount.Two, violation.callbackCount)
		assertTrue(fixture.processed.count { it == ReaderTransitionFactKind.SemanticPortContractViolated } == 1,
			"Generic duplicate receipt must remain visible as one bounded fatal contract violation")
		fixture.cancel(); fixture.cancel(); fixture.executor.clear(); fixture.executor.clear(); fixture.drain()
		assertFalse(call.context.reportAsyncFailure(call.origin, ReaderSemanticAsyncFailure.Rejected))
		assertTrue(cleanupCalls == 1 && fixture.executor.activeSlotCount == 0 && fixture.registry.activeHandleCount == 0,
			"Duplicate cancel/clear cannot redispose original transport or restore exact custody")
		assertTrue(fixture.coordinator.snapshot().releaseOnlySink &&
			fixture.coordinator.snapshot().lastOutcome == ReaderTransitionOutcomeKind.Failed &&
			fixture.coordinator.snapshot().activeOperation == null && fixture.coordinator.snapshot().maxAdvanceDepth == 1,
			"Duplicate generic receipt must permanently fence ordinary work without recursive reduction")
		val retained = assertNotNull(fixture.journal().releaseOnlyCleanup?.authoritativeSemanticDestination)
		assertEquals(newerDestination, retained.binding,
			"Receipt-triggered reentrant retirement must retain latest observed destination B, not older A")

		call.onReceipt(newer); fixture.drain(); fixture.cancel(); fixture.executor.clear()
		assertTrue(cleanupCalls == 1 && fixture.executor.activeSlotCount == 0 && fixture.registry.activeHandleCount == 0 &&
			fixture.coordinator.snapshot().releaseOnlySink, "Further generic receipt/cancel/clear cannot redispose the original owner")
		assertTrue(fixture.acceptedFacts.count { it is ReaderTransitionFact.SemanticPortContractViolated } == 1,
			"Further generic callbacks refresh authority without publishing an unbounded violation inventory")
		assertEquals(newerDestination, fixture.journal().releaseOnlyCleanup?.authoritativeSemanticDestination?.binding)
	}

	private class Call(
		val origin: ReaderPresentationEventOrigin.SemanticCommand,
		val context: ReaderSemanticMutationStartEvidence,
		val onReceipt: (ReaderPresentationEventReceipt) -> Unit
	)

	private class Fixture(private val captureAcceptedFacts: Boolean = false) {
		val registry = ReaderSemanticExecutableRequestRegistry(3L)
		val executor = ReaderSemanticCommandExecutor(registry)
		val calls = mutableListOf<Call>()
		val registeredHandles = mutableListOf<ReaderSemanticRequestHandle>()
		val processed = mutableListOf<ReaderTransitionFactKind>()
		val acceptedFacts = mutableListOf<ReaderTransitionFact>()
		private val binding = ReaderPresentationBinding("fixture", 2L, 3L, 5L,
			ReaderDestinationCommitIdentity("fixture", 1L), null, null, null)
		private val state = readerPresentationReduce(ReaderPresentationState(), ReaderPresentationEvent.PublicationOpened(binding)).state
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = object : ReaderResumableTransitionPorts {
				override val clock = object : ReaderTransitionClock {
					override fun nowMillis() = 0L
					override fun schedule(atMillis: Long, action: () -> Unit) = ReaderTransitionClockRegistration {}
				}
				override val semanticCommand = executor
				override fun acceptsFact(fact: ReaderTransitionFact): Boolean {
					if (captureAcceptedFacts) acceptedFacts += fact
					return true
				}
				override fun issue(command: ReaderTransitionCommand, onFact: (ReaderTransitionFact) -> Unit) = Unit
			},
			mode = ReaderTransitionMode.Active,
			journal = ReaderTransitionJournal(committed = ReaderCommittedPresentation.Initial(
				ReaderInitialCommittedPresentationOrigin.Neutral(3L, 5L,
					ReaderInitialPresentationInputLease.ChromeOnly, ReaderInitialPresentationInputLease.ChromeOnly))),
			onObservation = { observation ->
				if (observation.kind == ReaderTransitionCoordinatorObservationKind.FactProcessed) {
					observation.factKind?.let(processed::add)
				}
			}
		)
		val gateway = ReaderTransitionGateway().also {
			it.attachActivated(enqueue = coordinator::enqueue, registerSemanticRequest = registry::register)
		}
		fun dispatch(execute: (Call) -> ReaderSemanticExecutableResult = { ReaderSemanticExecutableResult.Accepted }): Call {
			gateway.dispatchSemanticActivatedOrLegacy(
				intent = { registeredHandles += it; ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, it) },
				request = { origin, context, callback ->
					val call = Call(origin, context, callback)
					calls += call
					execute(call)
				}, activatedResult = { Unit }, legacyDispatch = { error("legacy dispatch forbidden") })
			return calls.last()
		}
		/** Coherent common-policy receipts only; this does not authenticate a Native root or transport. */
		fun destinationReceipts(call: Call): Pair<ReaderPresentationEventReceipt, ReaderPresentationEventReceipt> {
			val olderDestination = binding.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 2L))
			val older = readerPresentationEventTransition(state,
				ReaderPresentationReceiptVersion(call.origin.transitionId.readerSessionGeneration, binding.publicationIdentity, 0L),
				false, ReaderPresentationEvent.FoliateRelocated(olderDestination, null), call.origin).receipt
			val newerDestination = binding.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 3L))
			val newer = readerPresentationEventTransition(older.postState, older.version,
				false, ReaderPresentationEvent.FoliateRelocated(newerDestination, null), call.origin).receipt
			return older to newer
		}
		fun journal() = ReaderResumableTransitionCoordinator::class.java.getDeclaredField("journal")
			.apply { isAccessible = true }.get(coordinator) as ReaderTransitionJournal
		fun cancel() { gateway.enqueue(ReaderTransitionFact.Intent(null, ReaderCancelIntent)); drain() }
		fun drain() = shadowOf(Looper.getMainLooper()).idle()
		fun deliverDestination(call: Call) {
			val destination = binding.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 2L))
			val event = ReaderPresentationEvent.FoliateRelocated(destination, null)
			call.onReceipt(readerPresentationEventTransition(state,
				ReaderPresentationReceiptVersion(call.origin.transitionId.readerSessionGeneration, binding.publicationIdentity, 0L),
				false, event, call.origin).receipt)
			drain()
		}
	}

	/** Record evidence inside external cleanup; assert outside any swallowed callback. */
	private class CleanupProbe(private val fixture: Fixture) : ReaderSemanticTransportRetirement {
		var calls = 0
		var outsideLocks = false
		override fun retire() {
			calls += 1
			outsideLocks = onWorker {
				fixture.executor.retirementSnapshot()
				fixture.registry.activeHandleCount
				true
			}
		}
	}

	private companion object {
		fun <T> onWorker(action: () -> T): T {
			val worker = Executors.newSingleThreadExecutor()
			return try { worker.submit<T> { action() }.get(2L, TimeUnit.SECONDS) }
			finally { worker.shutdownNow(); worker.awaitTermination(2L, TimeUnit.SECONDS) }
		}
	}
}
