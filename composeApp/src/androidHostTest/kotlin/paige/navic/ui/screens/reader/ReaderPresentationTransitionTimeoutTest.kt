package paige.navic.ui.screens.reader

import android.os.Looper
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import paige.navic.reader.*

@RunWith(RobolectricTestRunner::class)
class ReaderPresentationTransitionTimeoutTest {
	@Test
	fun presentationTimeoutCommandBindingUsesOneExistingPhysicalTimer() {
		val scheduler = CommandScheduler()
		val fixture = Fixture(timeoutScheduler = scheduler)
		try {
			val port = commandTimerPort(fixture)
			val expired = mutableListOf<ReaderTransitionFact.DeadlineExpired>()
			val id = commandTimerId(fixture.binding)
			val registration = kotlin.test.assertNotNull(port.bindBeforeWork(id, expired::add))
			assertEquals(1, scheduler.scheduled.size)
			assertEquals(1, scheduler.active.size)
			assertTrue(registration.transitionId == id, "Binding preserves full authentic transition identity")
			fixture.dispatch(ReaderPresentationEvent.NativePageRequested)
			assertEquals(1, scheduler.scheduled.size, "Legacy pending work cannot schedule a competing timer")
			fixture.advance(10)
			assertTrue(expired == listOf(ReaderTransitionFact.DeadlineExpired(id)), "Only exact deadline fact is emitted")
			fixture.advance(30)
			assertEquals(1, expired.size)
			assertEquals(0, fixture.timeoutCount(), "Installed command timer never delivers legacy TimedOut")
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun runtimeTimeoutTransferSnapshotIsExactAndDoesNotSchedule() {
		val scheduler = CommandScheduler()
		val fixture = Fixture(timeoutScheduler = scheduler)
		try {
			val port = commandTimerPort(fixture)
			val registration = kotlin.test.assertNotNull(port.bindBeforeWork(commandTimerId(fixture.binding)) {})
			val initial = kotlin.test.assertNotNull(port.snapshotForTask7Transfer(registration))
			fixture.advance(4)
			val current = kotlin.test.assertNotNull(port.snapshotForTask7Transfer(registration))
			assertTrue(current.registration == registration, "Snapshot returns exact physical registration")
			assertEquals(initial.nextExpiresAtMillis, current.nextExpiresAtMillis)
			assertEquals(1, scheduler.scheduled.size, "Snapshot neither transfers nor schedules")
			assertEquals(1, scheduler.active.size)
			assertEquals(ReaderPortCommandResult.Accepted, port.cancel(registration))
			kotlin.test.assertNull(port.snapshotForTask7Transfer(registration))
			assertTrue(scheduler.active.isEmpty())
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun runtimeAttemptTimerAtomicallyFencesIntoTwoSecondCleanup() {
		val scheduler = CommandScheduler()
		val fixture = Fixture(timeoutScheduler = scheduler)
		try {
			val port = commandTimerPort(fixture)
			val ordinary = mutableListOf<ReaderTransitionFact.DeadlineExpired>()
			val registration = kotlin.test.assertNotNull(port.bindBeforeWork(commandTimerId(fixture.binding), ordinary::add))
			val retired = scheduler.scheduled.single().first
			val key = ReaderReleaseOnlyCleanupKey(17L, 19L, ReaderReleaseOnlyCleanupId(23L), ReaderReleaseOnlyCleanupGeneration(29L))
			val elapsed = mutableListOf<ReaderReleaseOnlyCleanupKey>()
			val cleanup = kotlin.test.assertNotNull(port.bindReleaseOnlyCloseBudget(key, elapsed::add))
			assertEquals(1, scheduler.scheduled.size, "Cleanup PREPARE must not arm or replace the attempt")
			assertTrue(port.snapshotForTask7Transfer(registration)?.registration == registration)
			assertEquals(1, scheduler.active.size)
			val commit = port.javaClass.methods.singleOrNull {
				it.name == "commitReleaseOnlyCloseBudget" && it.parameterCount == 1
			}
			kotlin.test.assertNotNull(commit, "Actual timer owner needs a separate typed cleanup commit boundary")
			assertEquals(ReaderPortCommandResult.Accepted, commit.invoke(port, cleanup))
			assertEquals(2, scheduler.scheduled.size)
			assertEquals(2_000L, scheduler.scheduled.last().second)
			assertEquals(1, scheduler.active.size, "Committed cleanup is the sole physical owner")
			kotlin.test.assertNull(port.snapshotForTask7Transfer(registration))
			retired.run()
			assertTrue(ordinary.isEmpty())
			fixture.advance(1)
			assertTrue(elapsed.isEmpty())
			fixture.advance(1)
			assertTrue(elapsed == listOf(key), "Only exact cleanup expiry is emitted")
			scheduler.scheduled.last().first.run()
			assertEquals(1, elapsed.size)
			assertEquals(0, fixture.timeoutCount())
			assertEquals(ReaderPortCommandResult.Accepted, port.cancelReleaseOnlyCloseBudget(cleanup))
			kotlin.test.assertNull(port.bindBeforeWork(commandTimerId(fixture.binding), ordinary::add),
				"Terminal cleanup cancellation cannot reopen ordinary timer admission")
			scheduler.scheduled.last().first.run()
			assertEquals(1, elapsed.size)
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun commandTimerCancellationRequiresFullIdentityAndCannotReviveLegacyScheduling() {
		val scheduler = CommandScheduler()
		val fixture = Fixture(timeoutScheduler = scheduler)
		try {
			val port = commandTimerPort(fixture)
			val expired = mutableListOf<ReaderTransitionFact.DeadlineExpired>()
			val registration = kotlin.test.assertNotNull(port.bindBeforeWork(commandTimerId(fixture.binding), expired::add))
			val retired = scheduler.scheduled.single().first
			val wrongBinding = registration.copy(transitionId = registration.transitionId.copy(
				expectedBinding = ReaderExpectedPresentationBinding.Exact(fixture.binding.copy(profileGeneration = 99L))))
			assertIs<ReaderPortCommandResult.Rejected>(port.cancel(wrongBinding))
			kotlin.test.assertNull(port.snapshotForTask7Transfer(wrongBinding))
			assertEquals(1, scheduler.active.size)
			assertEquals(ReaderPortCommandResult.Accepted, port.cancel(registration))
			assertTrue(scheduler.active.isEmpty())
			retired.run()
			fixture.dispatch(ReaderPresentationEvent.NativePageRequested)
			fixture.advance(30)
			assertTrue(expired.isEmpty())
			assertEquals(1, scheduler.scheduled.size)
			assertEquals(0, fixture.timeoutCount())
			assertIs<ReaderPortCommandResult.Rejected>(port.cancel(registration))
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun preparedCleanupCancellationLeavesTheOrdinaryPhysicalTimerUntouched() {
		val scheduler = CommandScheduler()
		val fixture = Fixture(timeoutScheduler = scheduler)
		try {
			val port = commandTimerPort(fixture)
			val ordinary = mutableListOf<ReaderTransitionFact.DeadlineExpired>()
			val registration = kotlin.test.assertNotNull(port.bindBeforeWork(commandTimerId(fixture.binding), ordinary::add))
			val key = ReaderReleaseOnlyCleanupKey(17L, 19L, ReaderReleaseOnlyCleanupId(23L), ReaderReleaseOnlyCleanupGeneration(29L))
			val elapsed = mutableListOf<ReaderReleaseOnlyCleanupKey>()
			val cleanup = kotlin.test.assertNotNull(port.bindReleaseOnlyCloseBudget(key, elapsed::add))
			assertIs<ReaderPortCommandResult.Rejected>(port.cancelReleaseOnlyCloseBudget(cleanup.copy(
				cleanupKey = key.copy(generation = ReaderReleaseOnlyCleanupGeneration(30L)))))
			assertEquals(ReaderPortCommandResult.Accepted, port.cancelReleaseOnlyCloseBudget(cleanup))
			assertEquals(1, scheduler.scheduled.size)
			assertEquals(1, scheduler.active.size)
			assertEquals(registration, port.snapshotForTask7Transfer(registration)?.registration)
			fixture.advance(10)
			assertEquals(listOf(ReaderTransitionFact.DeadlineExpired(registration.transitionId)), ordinary)
			assertTrue(elapsed.isEmpty())
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun coordinatorPublicationCloseCommitsActualSolePhysicalCloseTimer() {
		val scheduler = CommandScheduler()
		val fixture = Fixture(timeoutScheduler = scheduler)
		try {
			val timer = commandTimerPort(fixture)
			val retained = retainedCloseFixture(fixture.binding)
			val commands = mutableListOf<ReaderTransitionCommand>()
			val physicalOwnersAtEgress = mutableListOf<Int>()
			val delegate = ReaderActivatedTransitionPorts(productionActivatedSessionPorts(
				completeActivatedSessionPorts().copy(factOnlyTimer = timer)
			))
			val ports = object : ReaderResumableTransitionPorts by delegate {
				override fun issueImportedLegacyRelease(
					dispatch: ReaderImportedLegacyReleaseDispatch,
					onFact: (ReaderTransitionFact) -> Unit
				) {
					commands += dispatch.command
					physicalOwnersAtEgress += scheduler.active.size
					delegate.issueImportedLegacyRelease(dispatch, onFact)
				}
				override fun issue(command: ReaderTransitionCommand, onFact: (ReaderTransitionFact) -> Unit) {
					commands += command
					physicalOwnersAtEgress += scheduler.active.size
					delegate.issue(command, onFact)
				}
			}
			val coordinator = ReaderResumableTransitionCoordinator(
				ports = ports, mode = ReaderTransitionMode.Active,
				journal = ReaderTransitionJournal(committed = ReaderCommittedPresentation.Initial(retained.decision.origin)),
				releaseLedger = retained.ledger,
				activationState = { ReaderSessionActivationState.Activated }
			)

			coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))

			assertTrue(coordinator.snapshot().releaseOnlySink, "Actual close caller must permanently fence ordinary egress")
			assertTrue(scheduler.active.size == 1, "Actual coordinator close must commit one physical close runnable, not report false Armed")
			assertTrue(scheduler.scheduled.map { it.second } == listOf(2_000L), "Actual close caller must arm the sole exact two-second budget")
			assertTrue(physicalOwnersAtEgress.isNotEmpty() && physicalOwnersAtEgress.all { it == 1 },
				"Physical COMMIT must precede actual cleanup egress")
			assertTrue(commands.any { it is ReaderTransitionCommand.ReleaseResource }, "Retained resource must require actual cleanup")
			assertTrue(coordinator.snapshot().releaseOnlyDeadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Armed,
				"Armed must describe the actual committed physical clock")
			fixture.advance(1)
			assertTrue(coordinator.snapshot().releaseOnlyDeadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Armed,
				"Cleanup cannot expire before its full physical budget")
			fixture.advance(1)
			assertTrue(coordinator.snapshot().releaseOnlyDeadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed,
				"The actual physical expiry must reach the coordinator caller")
			assertTrue(coordinator.releaseStateOf(retained.resource.registration.key) == ReaderTransitionResourceState.TimedOutUnreleased,
				"Physical expiry must retain truthful unreleased resource custody")
			scheduler.scheduled.single().first.run()
			assertTrue(coordinator.snapshot().releaseOnlyDeadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed,
				"Duplicate physical expiry cannot resurrect a close budget")
			assertTrue(fixture.timeoutCount() == 0, "Command close expiry must never deliver a legacy timeout")
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun snapshotFinalizationRejectionCommitsActualSolePhysicalCloseTimer() {
		val scheduler = CommandScheduler()
		val fixture = Fixture(timeoutScheduler = scheduler)
		try {
			val timer = commandTimerPort(fixture)
			val retained = retainedCloseFixture(fixture.binding)
			val queue = ArrayDeque<() -> Unit>()
			val store = ReaderActivatedSessionSnapshotStore { action -> queue.addLast(action); true }
			val basePorts = completeActivatedSessionPorts()
			val physicalOwnersAtRelease = mutableListOf<Int>()
			val resources = object : ReaderTransitionResourcePort by requireNotNull(basePorts.resources) {
				override fun releaseLegacy(
					command: ReaderTransitionCommand.ReleaseResource,
					imported: ReaderImportedLegacyResourceRegistration,
					onConfirmed: (ReaderLegacyPhysicalIdentity, ReaderTransitionFact.ResourceReleased) -> Unit
				): ReaderPortCommandResult {
					physicalOwnersAtRelease += scheduler.active.size
					return ReaderPortCommandResult.Accepted // Deliberately retain physical release custody.
				}
			}
			val barrier = ReaderActivatedSessionInstallationBarrier(store)
			assertTrue(barrier.installTestActivatedSession(
				basePorts.copy(resources = resources, factOnlyTimer = timer), retained.decision, retained.ledger
			) == ReaderActivationInstallResult.Installed, "Host scaffold must install the actual snapshot store")

			assertTrue(barrier.failClosedAfterFinalizationRejection(retained.decision), "The public finalization-rejection caller must close the installed snapshot")

			assertTrue(store.state == ReaderSessionActivationState.ReleaseOnly && !store.commandEgressOpen,
				"Finalization rejection must permanently fence ordinary egress")
			assertTrue(scheduler.active.size == 1, "Actual snapshot cleanup must commit one physical close runnable, not report false Armed")
			assertTrue(scheduler.scheduled.map { it.second } == listOf(2_000L), "Snapshot cleanup must arm the sole exact two-second budget")
			assertTrue(physicalOwnersAtRelease == listOf(1), "Actual physical COMMIT must precede imported cleanup release")
			assertTrue(store.journal.releaseOnlyCleanup?.deadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Armed,
				"Snapshot Armed must describe the committed physical clock")
			fixture.advance(1)
			while (queue.isNotEmpty()) queue.removeFirst().invoke()
			assertTrue(store.journal.releaseOnlyCleanup?.deadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Armed,
				"Snapshot cleanup cannot expire before the physical budget")
			fixture.advance(1)
			while (queue.isNotEmpty()) queue.removeFirst().invoke()
			assertTrue(store.journal.releaseOnlyCleanup?.deadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed,
				"The actual physical expiry must reach the snapshot-store caller")
			assertTrue(retained.ledger.stateOf(retained.resource.registration.key) == ReaderTransitionResourceState.TimedOutUnreleased,
				"Snapshot expiry must retain truthful unreleased resource custody")
			scheduler.scheduled.single().first.run()
			while (queue.isNotEmpty()) queue.removeFirst().invoke()
			assertTrue(store.journal.releaseOnlyCleanup?.deadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed,
				"Duplicate physical expiry cannot resurrect snapshot cleanup")
			assertTrue(fixture.timeoutCount() == 0, "Snapshot close expiry must never deliver a legacy timeout")
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun groupedPublicCallersPrepareAndCommitFailuresRetainCustodyWithoutCleanupEgress() {
		for (caller in CloseCaller.entries) for (failure in CloseFailure.entries) {
			val scheduler = CommandScheduler()
			val fixture = Fixture(timeoutScheduler = scheduler)
			try {
				val actual = commandTimerPort(fixture)
				lateinit var owner: PublicCloseOwner
				var prepareCalls = 0
				var commitCalls = 0
				var prepareFenceObserved = false
				var preparePhaseObserved = false
				var commitFenceObserved = false
				var commitPhaseObserved = false
				val port = object : ReaderTask6FactOnlyTimerPort by actual {
					override fun bindReleaseOnlyCloseBudget(
						cleanupKey: ReaderReleaseOnlyCleanupKey,
						onExpired: (ReaderReleaseOnlyCleanupKey) -> Unit
					): ReaderTask6ReleaseOnlyTimerRegistration? {
						prepareCalls += 1
						prepareFenceObserved = owner.fenced()
						preparePhaseObserved = owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.Prepared
						if (failure == CloseFailure.PrepareNull) return null
						if (failure == CloseFailure.PrepareThrow) error("bounded timer failure")
						return actual.bindReleaseOnlyCloseBudget(cleanupKey, onExpired)
					}
					override fun commitReleaseOnlyCloseBudget(
						registration: ReaderTask6ReleaseOnlyTimerRegistration
					): ReaderPortCommandResult {
						commitCalls += 1
						commitFenceObserved = owner.fenced()
						commitPhaseObserved = owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.Committing
						if (failure == CloseFailure.CommitThrow) error("bounded timer failure")
						return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
					}
				}
				owner = publicCloseOwner(caller, fixture.binding, port)
				owner.close()
				owner.drain()
				val expected = if (failure == CloseFailure.PrepareThrow || failure == CloseFailure.CommitThrow) {
					ReaderReleaseOnlyCleanupDeadlineStatus.BindingThrew
				} else ReaderReleaseOnlyCleanupDeadlineStatus.BindingRejected
				assertTrue(prepareFenceObserved && preparePhaseObserved, "PREPARE must see a truthful phase and permanent egress fence")
				if (commitCalls > 0) assertTrue(commitFenceObserved && commitPhaseObserved,
					"COMMIT must see a truthful phase before acceptance")
				assertTrue(prepareCalls == 1, "Public caller must attempt one PREPARE")
				assertTrue(commitCalls == if (failure == CloseFailure.CommitReject || failure == CloseFailure.CommitThrow) 1 else 0,
					"Only successful PREPARE may reach COMMIT")
				assertTrue(owner.status() == expected, "Binding failure disposition must remain truthful")
				assertTrue(owner.fenced() && owner.egressCount == 0, "Failed binding must not issue cleanup or reopen ordinary work")
				assertTrue(owner.resourceState() == ReaderTransitionResourceState.TimedOutUnreleased,
					"Rejected or ambiguous close must retain unreleased custody")
				assertTrue(scheduler.scheduled.isEmpty(), "Rejected close must not create a second scheduler")
			} finally { fixture.bridge.dispose() }
		}
	}

	@Test
	fun groupedPublicCallersPhysicalCommitRejectOrThrowNeverClaimArmed() {
		for (caller in CloseCaller.entries) for (throws in listOf(false, true)) {
			val scheduler = CommandScheduler()
			val fixture = Fixture(timeoutScheduler = scheduler)
			try {
				val owner = publicCloseOwner(caller, fixture.binding, commandTimerPort(fixture))
				var physicalFenceObserved = false
				var physicalPhaseObserved = false
				scheduler.onPost = { _, _ ->
					physicalFenceObserved = owner.fenced()
					physicalPhaseObserved = owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.Committing
					if (throws) error("bounded scheduler failure")
					false
				}
				owner.close(); owner.drain()
				assertTrue(physicalFenceObserved && physicalPhaseObserved, "Physical schedule must see Committing behind the permanent fence")
				assertTrue(owner.status() == if (throws) ReaderReleaseOnlyCleanupDeadlineStatus.BindingThrew
					else ReaderReleaseOnlyCleanupDeadlineStatus.BindingRejected, "Physical COMMIT failure must be accounted")
				assertTrue(owner.egressCount == 0 && owner.fenced(), "Failed physical COMMIT must suppress cleanup egress")
				assertTrue(owner.resourceState() == ReaderTransitionResourceState.TimedOutUnreleased,
					"Physical schedule failure must retain resource custody")
			} finally { fixture.bridge.dispose() }
		}
	}

	@Test
	fun groupedPublicCallersSynchronousPhysicalExpirySuppressesCleanupAndDuplicates() {
		for (caller in CloseCaller.entries) {
			val scheduler = CommandScheduler()
			val fixture = Fixture(timeoutScheduler = scheduler)
			try {
				val owner = publicCloseOwner(caller, fixture.binding, commandTimerPort(fixture))
				scheduler.onPost = { action, _ -> action.run(); true }
				owner.close(); owner.drain()
				assertTrue(owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed,
					"$caller: Synchronous physical expiry must win over the old COMMIT return")
				assertTrue(owner.fenced() && owner.egressCount == 0, "$caller: Observed expiry must suppress cleanup egress")
				assertTrue(owner.resourceState() == ReaderTransitionResourceState.TimedOutUnreleased,
					"$caller: Synchronous expiry must retain exact custody")
				owner.coordinator?.snapshot()?.let {
					assertEquals(0, it.scheduledCallbackCount, "$caller: Exact expiry callback custody must retire")
					assertEquals(ReaderTransitionOutcomeKind.Failed, it.lastOutcome, "$caller: Expiry must remain failed")
					assertEquals(null, it.activeOperation, "$caller: Expiry cannot retain ordinary work")
				}
				owner.store?.journal?.let {
					val failed = assertIs<ReaderTransitionOutcome.Failed>(it.lastOutcome, "$caller: Expiry must remain failed")
					assertEquals(ReaderTransitionFailureReason.CloseDrainTimeout, failed.reason)
					assertEquals(ReaderTransitionRetryability.NonRetryable, failed.retryability)
					assertEquals(null, it.active, "$caller: Expiry cannot retain ordinary work")
				}
				val beforeCoordinator = owner.coordinator?.snapshot()
				val beforeStore = owner.store?.snapshot
				repeat(2) { scheduler.scheduled.single().first.run(); owner.drain() }
				assertTrue(owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed && owner.egressCount == 0,
					"$caller: Duplicate expiry must be inert")
				assertTrue(owner.resourceState() == ReaderTransitionResourceState.TimedOutUnreleased && owner.fenced(),
					"$caller: Duplicate expiry must preserve exact unreleased custody and the permanent fence")
				beforeCoordinator?.let { assertEquals(it, owner.coordinator!!.snapshot(), "$caller: Duplicate cannot change callback custody or outcome") }
				beforeStore?.let { kotlin.test.assertSame(it, owner.store!!.snapshot, "$caller: Duplicate cannot republish terminal accounting") }
				assertEquals(1, scheduler.scheduled.size, "$caller: Synchronous expiry cannot rearm or transfer the clock")
				assertEquals(0, fixture.timeoutCount(), "$caller: No legacy timeout may follow command expiry")
			} finally { fixture.bridge.dispose() }
		}
	}

	@Test
	fun groupedPublicCallersReentrantPreparedCancelCannotCommitRetiredPhysicalRegistration() {
		for (caller in CloseCaller.entries) {
			val scheduler = CommandScheduler()
			val fixture = Fixture(timeoutScheduler = scheduler)
			try {
				val actual = commandTimerPort(fixture)
				var cancelledExact = false
				val port = object : ReaderTask6FactOnlyTimerPort by actual {
					override fun bindReleaseOnlyCloseBudget(cleanupKey: ReaderReleaseOnlyCleanupKey,
						onExpired: (ReaderReleaseOnlyCleanupKey) -> Unit): ReaderTask6ReleaseOnlyTimerRegistration? {
						return actual.bindReleaseOnlyCloseBudget(cleanupKey, onExpired)?.also {
							cancelledExact = actual.cancelReleaseOnlyCloseBudget(it) == ReaderPortCommandResult.Accepted
						}
					}
				}
				val owner = publicCloseOwner(caller, fixture.binding, port)
				owner.close(); owner.drain()
				assertTrue(cancelledExact && owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.BindingRejected,
					"Retired PREPARE registration cannot be accepted by physical COMMIT")
				assertTrue(owner.egressCount == 0 && scheduler.scheduled.isEmpty() && owner.fenced(),
					"Reentrant prepared cancellation cannot schedule or reopen work")
			} finally { fixture.bridge.dispose() }
		}
	}

	@Test
	fun groupedPublicCallersReentrantPhysicalCancelCannotBePublishedAsArmed() {
		for (caller in CloseCaller.entries) {
			val scheduler = CommandScheduler()
			val fixture = Fixture(timeoutScheduler = scheduler)
			try {
				val actual = commandTimerPort(fixture)
				lateinit var prepared: ReaderTask6ReleaseOnlyTimerRegistration
				val port = object : ReaderTask6FactOnlyTimerPort by actual {
					override fun bindReleaseOnlyCloseBudget(key: ReaderReleaseOnlyCleanupKey,
						onExpired: (ReaderReleaseOnlyCleanupKey) -> Unit): ReaderTask6ReleaseOnlyTimerRegistration? =
						actual.bindReleaseOnlyCloseBudget(key, onExpired)?.also { prepared = it }
				}
				val owner = publicCloseOwner(caller, fixture.binding, port)
				var cancelledExact = false
				scheduler.onPost = { _, _ ->
					cancelledExact = actual.cancelReleaseOnlyCloseBudget(prepared) == ReaderPortCommandResult.Accepted
					true
				}
				owner.close(); owner.drain()
				assertTrue(cancelledExact, "Reentrant cancellation must retire exact physical close custody")
				assertTrue(scheduler.active.isEmpty(), "Retired physical close must not leave a runnable")
				assertTrue(owner.status() != ReaderReleaseOnlyCleanupDeadlineStatus.Armed,
					"COMMIT returning after physical cancellation must not claim Armed")
				assertTrue(owner.egressCount == 0 && owner.fenced(), "Lost physical custody must suppress cleanup egress")
			} finally { fixture.bridge.dispose() }
		}
	}

	@Test
	fun groupedCoordinatorReentrantReplacementAndCloseRetainTerminalFence() {
		for (closeAgain in listOf(false, true)) {
			val scheduler = CommandScheduler()
			val fixture = Fixture(timeoutScheduler = scheduler)
			try {
				val owner = publicCloseOwner(CloseCaller.Coordinator, fixture.binding, commandTimerPort(fixture))
				scheduler.onPost = { _, _ ->
					owner.coordinator!!.enqueue(if (closeAgain) ReaderTransitionFact.PublicationClosed(null)
						else ReaderTransitionFact.PublicationReplaced(null))
					true
				}
				owner.close(); owner.drain()
				assertTrue(owner.fenced(), "Reentrant close or replacement cannot reopen ordinary authority")
				assertTrue(scheduler.scheduled.size == 1, "Reentrant terminal ingress cannot bind another close timer")
			} finally { fixture.bridge.dispose() }
		}
	}

	@Test
	fun groupedPublicCallersTerminalAccountingCancelsExactCommittedPhysicalClock() {
		for (caller in CloseCaller.entries) {
			val scheduler = CommandScheduler()
			val fixture = Fixture(timeoutScheduler = scheduler)
			try {
				val owner = publicCloseOwner(caller, fixture.binding, commandTimerPort(fixture), confirmRelease = true)
				owner.close(); owner.drain()
				assertTrue(owner.egressCount == 1, "Exact imported release must be dispatched once")
				assertTrue(owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting,
					"Terminal release must retire the committed timer")
				assertTrue(scheduler.active.isEmpty(), "Terminal accounting must cancel the actual physical runnable")
				assertTrue(owner.resourceState() == ReaderTransitionResourceState.Released, "Exact release must be accounted")
				scheduler.scheduled.single().first.run(); owner.drain()
				assertTrue(owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting,
					"Retired physical expiry cannot undo terminal accounting")
			} finally { fixture.bridge.dispose() }
		}
	}

	@Test
	fun groupedPublicCallersPrepareKeepsOrdinaryPhysicalClockUntilCommit() {
		for (caller in CloseCaller.entries) {
			val scheduler = CommandScheduler()
			val fixture = Fixture(timeoutScheduler = scheduler)
			try {
				val actual = commandTimerPort(fixture)
				val ordinary = kotlin.test.assertNotNull(actual.bindBeforeWork(commandTimerId(fixture.binding)) {})
				val retired = scheduler.scheduled.single().first
				var prepareChecked = false
				val port = object : ReaderTask6FactOnlyTimerPort by actual {
					override fun bindReleaseOnlyCloseBudget(cleanupKey: ReaderReleaseOnlyCleanupKey,
						onExpired: (ReaderReleaseOnlyCleanupKey) -> Unit): ReaderTask6ReleaseOnlyTimerRegistration? {
						val prepared = actual.bindReleaseOnlyCloseBudget(cleanupKey, onExpired)
						assertTrue(actual.snapshotForTask7Transfer(ordinary)?.registration == ordinary,
							"PREPARE must preserve original ordinary clock ownership")
						assertTrue(scheduler.scheduled.size == 1 && scheduler.active.size == 1,
							"PREPARE must not schedule a competing physical clock")
						prepareChecked = true
						return prepared
					}
				}
				val owner = publicCloseOwner(caller, fixture.binding, port)
				owner.close(); owner.drain()
				assertTrue(prepareChecked && scheduler.scheduled.size == 2 && scheduler.active.size == 1,
					"Actual COMMIT must replace ordinary with the sole close clock")
				retired.run(); owner.drain()
				assertTrue(owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.Armed,
					"Retired ordinary runnable cannot expire the committed close clock")
			} finally { fixture.bridge.dispose() }
		}
	}

	@Test
	fun groupedPublicCallersTerminalCancellationRejectOrThrowRetainsExactExpiryAuthority() {
		// Same selector as ownership190; prospectively correct its assistant-created
		// Elapsed expectation to the governing design sections 9/12. Exact physical
		// expiry retires callback custody, not fail-closed terminal consequence authority.
		for (caller in CloseCaller.entries) for (throws in listOf(false, true)) {
			val scheduler = CommandScheduler()
			val fixture = Fixture(timeoutScheduler = scheduler)
			try {
				val actual = commandTimerPort(fixture)
				var bindCalls = 0
				var commitCalls = 0
				var cancelCalls = 0
				var expiryDeliveries = 0
				val port = object : ReaderTask6FactOnlyTimerPort by actual {
					override fun bindReleaseOnlyCloseBudget(cleanupKey: ReaderReleaseOnlyCleanupKey,
						onExpired: (ReaderReleaseOnlyCleanupKey) -> Unit): ReaderTask6ReleaseOnlyTimerRegistration? {
						bindCalls += 1
						return actual.bindReleaseOnlyCloseBudget(cleanupKey) { expiredKey ->
							expiryDeliveries += 1
							onExpired(expiredKey)
						}
					}
					override fun commitReleaseOnlyCloseBudget(registration: ReaderTask6ReleaseOnlyTimerRegistration): ReaderPortCommandResult {
						commitCalls += 1
						return actual.commitReleaseOnlyCloseBudget(registration)
					}
					override fun cancelReleaseOnlyCloseBudget(registration: ReaderTask6ReleaseOnlyTimerRegistration): ReaderPortCommandResult {
						cancelCalls += 1
						if (throws) error("bounded cancellation failure")
						return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
					}
				}
				val owner = publicCloseOwner(caller, fixture.binding, port, confirmRelease = true)
				owner.close(); owner.drain()
				val expectedStatus = if (throws) ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew
					else ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected
				assertEquals(expectedStatus, owner.status())
				assertTrue(owner.resourceState() == ReaderTransitionResourceState.Released && owner.fenced() && owner.egressCount == 1,
					"Terminal cancellation failure must retain Released accounting and the permanent fence")
				assertTrue(scheduler.active.size == 1 && scheduler.scheduled.size == 1,
					"Cancellation failure must preserve the original sole physical timer")
				val beforeCoordinator = owner.coordinator?.snapshot()
				val beforeStore = owner.store?.snapshot
				val beforeStoreWrites = owner.store?.atomicWriteCount
				beforeCoordinator?.let {
					assertEquals(ReaderTransitionOutcomeKind.Failed, it.lastOutcome)
					assertEquals(null, it.activeOperation)
					assertEquals(1, it.scheduledCallbackCount)
				}
				beforeStore?.let {
					assertEquals(ReaderTransitionFailureReason.CloseDrainTimeout,
						assertIs<ReaderTransitionOutcome.Failed>(it.journal.lastOutcome).reason)
					assertEquals(null, it.journal.active)
				}
				scheduler.scheduled.single().first.run(); owner.drain()
				assertEquals(1, expiryDeliveries, "The original physical callback must be consumed once")
				assertEquals(expectedStatus, owner.status(), "Late physical expiry cannot rewrite terminal cancellation failure")
				beforeCoordinator?.let {
					assertEquals(it.copy(scheduledCallbackCount = 0), owner.coordinator!!.snapshot(),
						"Only captured callback custody may change, not terminal outcome, accounting or ordinary authority")
				}
				beforeStore?.let {
					kotlin.test.assertSame(it, owner.store!!.snapshot, "Late physical expiry cannot republish the terminal snapshot")
					assertEquals(beforeStoreWrites, owner.store!!.atomicWriteCount)
				}
				assertTrue(owner.resourceState() == ReaderTransitionResourceState.Released && owner.fenced(),
					"Timer cancellation failure cannot resurrect a released resource or ordinary work")
				scheduler.scheduled.single().first.run()
				owner.confirmLateRelease!!.invoke()
				owner.coordinator?.enqueue(ReaderTransitionFact.PublicationClosed(null))
				owner.coordinator?.enqueue(ReaderTransitionFact.Retry(null)); owner.drain()
				assertTrue(owner.status() == expectedStatus && owner.egressCount == 1 && owner.fenced() &&
					owner.resourceState() == ReaderTransitionResourceState.Released,
					"Duplicate expiry, release and terminal ingress cannot rewrite or reissue terminal accounting")
				beforeCoordinator?.let {
					val after = owner.coordinator!!.snapshot()
					assertEquals(it.lastOutcome, after.lastOutcome)
					assertEquals(null, after.activeOperation)
					assertEquals(0, after.scheduledCallbackCount)
				}
				beforeStore?.let { kotlin.test.assertSame(it, owner.store!!.snapshot) }
				assertEquals(1, expiryDeliveries)
				assertEquals(1, bindCalls)
				assertEquals(1, commitCalls)
				assertEquals(1, cancelCalls, "Terminal cancellation cannot be attempted again")
				assertTrue(scheduler.scheduled.size == 1, "Cancellation failure and expiry must never transfer or rearm the clock")
				assertEquals(0, fixture.timeoutCount(), "No legacy timeout or wake may follow terminal cleanup")
			} finally { fixture.bridge.dispose() }
		}
	}

	@Test
	fun groupedPublicCallersLatePhysicalReleaseAfterExpiryRetainsReleasedAccounting() {
		for (caller in CloseCaller.entries) {
			val scheduler = CommandScheduler()
			val fixture = Fixture(timeoutScheduler = scheduler)
			try {
				val owner = publicCloseOwner(caller, fixture.binding, commandTimerPort(fixture))
				owner.close(); owner.drain()
				scheduler.scheduled.single().first.run(); owner.drain()
				assertTrue(owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed &&
					owner.resourceState() == ReaderTransitionResourceState.TimedOutUnreleased,
					"Actual expiry retains exact unresolved release custody")
				kotlin.test.assertNotNull(owner.confirmLateRelease).invoke(); owner.drain()
				assertTrue(owner.resourceState() == ReaderTransitionResourceState.Released && owner.fenced() &&
					owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed,
					"Late exact physical confirmation must account Released without reopening timer or ordinary work")
				owner.confirmLateRelease!!.invoke(); scheduler.scheduled.single().first.run(); owner.drain()
				assertTrue(owner.resourceState() == ReaderTransitionResourceState.Released && owner.egressCount == 1 &&
					scheduler.scheduled.size == 1, "Duplicate late release and expiry cannot fabricate new custody or clock")
			} finally { fixture.bridge.dispose() }
		}
	}

	@Test
	fun realRegistryExecutorCoordinatorRetryReadmitsPremutationFailureWithoutRenewedHandle() {
		val fixture = Fixture(timeoutScheduler = CommandScheduler())
		try {
			val actual = commandTimerPort(fixture)
			val retained = retainedCloseFixture(fixture.binding)
			val registry = ReaderSemanticExecutableRequestRegistry(17L)
			val executor = ReaderSemanticCommandExecutor(registry)
			var executableCalls = 0
			val origins = mutableListOf<ReaderPresentationEventOrigin.SemanticCommand>()
			val admittedHandles = mutableListOf<ReaderSemanticRequestHandle>()
			val delegate = ReaderActivatedTransitionPorts(productionActivatedSessionPorts(
				completeActivatedSessionPorts().copy(factOnlyTimer = actual,
					semantic = ReaderSemanticCommandPort(executor::synchronize))
			))
			val coordinator = ReaderResumableTransitionCoordinator(delegate, ReaderTransitionMode.Active,
				ReaderTransitionJournal(committed = ReaderCommittedPresentation.Initial(retained.decision.origin)),
				retained.ledger, activationState = { ReaderSessionActivationState.Activated })
			val gateway = ReaderTransitionGateway()
			gateway.attachActivated(enqueue = coordinator::enqueue, registerSemanticRequest = registry::register)
			gateway.dispatchSemanticActivatedOrLegacy(
				intent = { admittedHandles += it; ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, it) },
				request = { origin, _, _ -> origins += origin; executableCalls += 1; ReaderSemanticExecutableResult.Rejected },
				activatedResult = { Unit }, legacyDispatch = { error("legacy dispatch forbidden") })
			assertTrue(executableCalls == 1 && executor.activeSlotCount == 0,
				"First genuine pre-mutation failure must retire its real slot")
			assertTrue(coordinator.snapshot().lastOutcome == ReaderTransitionOutcomeKind.Failed,
				"Actual first failure must reach retryable common reduction")
			coordinator.enqueue(ReaderTransitionFact.Retry(null))
			assertTrue(executableCalls == 2, "Retry must lawfully readmit the original executable through its real owner lifetime")
			assertTrue(executor.activeSlotCount == 0 && registry.activeHandleCount == 1,
				"A still-Retriable intent owns one bounded executable custody, not a leaked slot")
			repeat(3) { coordinator.enqueue(ReaderTransitionFact.Retry(null)) }
			assertTrue(executableCalls == 5 && admittedHandles.size == 1,
				"Repeated lawful pre-mutation failures must use the original handle without a retry cap")
			assertTrue(origins.map { it.slotId }.distinct().size == origins.size &&
				origins.map { it.transitionId.sequence } == listOf(1L, 2L, 3L, 4L, 5L),
				"Each genuine retry must receive a new executor-owned slot for its actual coordinator transition")
			assertTrue(executor.activeSlotCount == 0 && registry.activeHandleCount == 1,
				"Repeated retries retain exactly one authentic request in the same registry")
			coordinator.enqueue(ReaderTransitionFact.Intent(null, ReaderCancelIntent))
			assertTrue(registry.activeHandleCount == 0 && executor.activeSlotCount == 0,
				"Cancel must dispose exact retry custody")
			coordinator.enqueue(ReaderTransitionFact.Retry(null))
			assertTrue(executableCalls == 5 && registry.activeHandleCount == 0,
				"Retry after cancellation must not resurrect disposed executable work")
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun realRetryCustodyEndsOnPublicationCloseReplacementAndSupersession() {
		for (terminal in listOf(ReaderTransitionFact.PublicationClosed(null), ReaderTransitionFact.PublicationReplaced(null))) {
			val fixture = Fixture(timeoutScheduler = CommandScheduler())
			try {
				val chain = semanticOwnerFixture(fixture)
				var starts = 0
				chain.dispatch { _, _, _ -> starts += 1; ReaderSemanticExecutableResult.Rejected }
				assertTrue(chain.registry.activeHandleCount == 1, "Retriable work must have truthful exact request custody")
				chain.coordinator.enqueue(terminal)
				chain.coordinator.enqueue(ReaderTransitionFact.Retry(null))
				assertTrue(starts == 1 && chain.registry.activeHandleCount == 0 && chain.executor.activeSlotCount == 0,
					"Close or replacement must dispose retained request and prevent resurrection")
				assertTrue(chain.coordinator.snapshot().releaseOnlySink, "Publication termination keeps ordinary egress closed")
			} finally { fixture.bridge.dispose() }
		}
		val fixture = Fixture(timeoutScheduler = CommandScheduler())
		try {
			val chain = semanticOwnerFixture(fixture)
			var firstStarts = 0
			var secondStarts = 0
			chain.dispatch { _, _, _ -> firstStarts += 1; ReaderSemanticExecutableResult.Rejected }
			chain.dispatch { _, _, _ -> secondStarts += 1; ReaderSemanticExecutableResult.Rejected }
			chain.coordinator.enqueue(ReaderTransitionFact.Retry(null))
			assertTrue(firstStarts == 1 && secondStarts == 2 && chain.registry.activeHandleCount == 1,
				"Supersession must dispose old custody while retrying only the new exact semantic intent")
			chain.coordinator.enqueue(ReaderTransitionFact.Intent(null, ReaderCancelIntent))
			assertTrue(chain.registry.activeHandleCount == 0, "Cancel must end the sole successor request custody")
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun realMutationStartedRejectOrThrowNeverReplaysRequestOnRetry() {
		for (throws in listOf(false, true)) {
			val fixture = Fixture(timeoutScheduler = CommandScheduler())
			try {
				val chain = semanticOwnerFixture(fixture)
				var starts = 0
				chain.dispatch { _, context, _ ->
					starts += 1; context.mutationStarted()
					if (throws) error("bounded executable failure")
					ReaderSemanticExecutableResult.Rejected
				}
				chain.coordinator.enqueue(ReaderTransitionFact.Retry(null))
				assertTrue(starts == 1 && chain.registry.activeHandleCount == 0 && chain.executor.activeSlotCount == 0,
					"Started mutation is not retry custody and must never be replayed")
				assertTrue(chain.coordinator.snapshot().releaseOnlySink, "Ambiguous mutation must retain permanent release-only authority")
			} finally { fixture.bridge.dispose() }
		}
	}

	@Test
	fun realSemanticReceiptDisposesRequestEvenBeforeMaterialTerminalOutcome() {
		val fixture = Fixture(timeoutScheduler = CommandScheduler())
		try {
			val chain = semanticOwnerFixture(fixture)
			var starts = 0
			chain.dispatch { origin, context, callback ->
				starts += 1; context.mutationStarted()
				val destination = fixture.binding.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 2L))
				val event = ReaderPresentationEvent.FoliateRelocated(destination, null)
				callback(readerPresentationEventTransition(fixture.state,
					ReaderPresentationReceiptVersion(origin.transitionId.readerSessionGeneration, fixture.binding.publicationIdentity, 0L),
					false, event, origin).receipt)
				ReaderSemanticExecutableResult.Accepted
			}
			assertTrue(starts == 1 && chain.registry.activeHandleCount == 0 && chain.executor.activeSlotCount == 0,
				"Successful semantic terminal receipt must dispose exact executable custody")
			chain.coordinator.enqueue(ReaderTransitionFact.Retry(null))
			assertTrue(starts == 1, "Material continuation cannot replay successful semantic work")
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun realNeverInvokedRequestCustodyEndsOnCancelAfterRetainedPublicationRejection() {
		val fixture = Fixture(timeoutScheduler = CommandScheduler())
		try {
			val chain = semanticOwnerFixture(fixture, rejectRetainedPublication = true)
			var starts = 0
			chain.dispatch { _, _, _ -> starts += 1; ReaderSemanticExecutableResult.Accepted }
			assertTrue(starts == 0 && chain.registry.activeHandleCount == 1,
				"Rejected authentic retained publication leaves one never-invoked retry request with its real owner")
			chain.coordinator.enqueue(ReaderTransitionFact.Intent(null, ReaderCancelIntent))
			chain.coordinator.enqueue(ReaderTransitionFact.Retry(null))
			assertTrue(starts == 0 && chain.registry.activeHandleCount == 0,
				"Cancel before executable registration must dispose exact pending request, not resurrect it")
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun realGatewayAttachmentCloseDisposesRetriableRequestAndRejectsFurtherRegistration() {
		for (rejectRetainedPublication in listOf(false, true)) {
			val fixture = Fixture(timeoutScheduler = CommandScheduler())
			try {
				val chain = semanticOwnerFixture(fixture, rejectRetainedPublication)
				chain.dispatch { _, _, _ -> ReaderSemanticExecutableResult.Rejected }
				chain.attachment.close()
				assertTrue(chain.registry.activeHandleCount == 0 && chain.coordinator.snapshot().releaseOnlySink,
					"Existing gateway attachment close must permanently end exact request custody")
				assertTrue(runCatching { chain.dispatch { _, _, _ -> ReaderSemanticExecutableResult.Accepted } }.isFailure,
					"Closed gateway cannot register work or reopen legacy routing")
			} finally { fixture.bridge.dispose() }
		}
	}

	private class SemanticOwnerFixture(
		val registry: ReaderSemanticExecutableRequestRegistry,
		val executor: ReaderSemanticCommandExecutor,
		val coordinator: ReaderResumableTransitionCoordinator,
		val gateway: ReaderTransitionGateway,
		val attachment: ReaderTransitionGatewayRegistration
	) {
		fun dispatch(request: ReaderSemanticExecutableRequest) = gateway.dispatchSemanticActivatedOrLegacy(
			intent = { ReaderExternalRelocationIntent(ReaderExternalRelocationSource.Toc, it) },
			request = request, activatedResult = { Unit }, legacyDispatch = { error("legacy dispatch forbidden") })
	}

	private fun semanticOwnerFixture(fixture: Fixture, rejectRetainedPublication: Boolean = false): SemanticOwnerFixture {
		val retained = retainedCloseFixture(fixture.binding)
		val registry = ReaderSemanticExecutableRequestRegistry(17L)
		val executor = ReaderSemanticCommandExecutor(registry)
		val base = completeActivatedSessionPorts()
		val publication = if (rejectRetainedPublication) object : ReaderOwnerAndInputPublicationPort by requireNotNull(base.ownerAndInput) {
			override fun publish(command: ReaderTransitionCommand.PublishRetainedOwnerAndInputLease): ReaderOwnerAndInputPublicationResult =
				ReaderOwnerAndInputPublicationResult.Rejected(command.transitionId,
					ReaderOwnerAndInputPublicationSubject.Retained(command.retainedResource), command.publicationIdentity,
					ReaderTransitionFailureReason.AtomicPublicationRejected)
		} else base.ownerAndInput
		val ports = ReaderActivatedTransitionPorts(productionActivatedSessionPorts(base.copy(
			factOnlyTimer = commandTimerPort(fixture), semantic = executor, ownerAndInput = publication)))
		val coordinator = ReaderResumableTransitionCoordinator(ports, ReaderTransitionMode.Active,
			ReaderTransitionJournal(committed = ReaderCommittedPresentation.Initial(retained.decision.origin)),
			retained.ledger, activationState = { ReaderSessionActivationState.Activated })
		val gateway = ReaderTransitionGateway()
		val attachment = gateway.attachActivated(enqueue = coordinator::enqueue, registerSemanticRequest = registry::register,
			retireSemanticRequests = registry::clear)
		return SemanticOwnerFixture(registry, executor, coordinator, gateway, attachment)
	}

	private enum class CloseCaller { Coordinator, SnapshotStore }
	private enum class CloseFailure { PrepareNull, PrepareThrow, CommitReject, CommitThrow }

	// Real public callers and real bridge timer. Retained origin/port package are host scaffolding,
	// not production authentication, Native activation, or successful root composition evidence.
	private class PublicCloseOwner(
		val close: () -> Unit,
		val drain: () -> Unit,
		val status: () -> ReaderReleaseOnlyCleanupDeadlineStatus?,
		val fenced: () -> Boolean,
		val resourceState: () -> ReaderTransitionResourceState?,
		val coordinator: ReaderResumableTransitionCoordinator? = null,
		val store: ReaderActivatedSessionSnapshotStore? = null
	) {
		var egressCount = 0
		var confirmLateRelease: (() -> Unit)? = null
	}

	private fun publicCloseOwner(caller: CloseCaller, binding: ReaderPresentationBinding,
		timer: ReaderTask6FactOnlyTimerPort, confirmRelease: Boolean = false): PublicCloseOwner {
		val retained = retainedCloseFixture(binding)
		val base = completeActivatedSessionPorts()
		lateinit var owner: PublicCloseOwner
		val resources = object : ReaderTransitionResourcePort by requireNotNull(base.resources) {
			override fun releaseLegacy(command: ReaderTransitionCommand.ReleaseResource,
				imported: ReaderImportedLegacyResourceRegistration,
				onConfirmed: (ReaderLegacyPhysicalIdentity, ReaderTransitionFact.ResourceReleased) -> Unit): ReaderPortCommandResult {
				owner.egressCount += 1
				assertTrue(owner.fenced(), "Cleanup egress must see permanent ordinary fence")
				assertTrue(owner.status() == ReaderReleaseOnlyCleanupDeadlineStatus.Armed,
					"Cleanup egress requires accepted actual COMMIT")
				owner.confirmLateRelease = { onConfirmed(imported.physicalIdentity, ReaderTransitionFact.ResourceReleased(command.identity)) }
				if (confirmRelease) owner.confirmLateRelease!!.invoke()
				return ReaderPortCommandResult.Accepted
			}
		}
		val ports = base.copy(resources = resources, factOnlyTimer = timer)
		if (caller == CloseCaller.Coordinator) {
			val coordinator = ReaderResumableTransitionCoordinator(
				ReaderActivatedTransitionPorts(productionActivatedSessionPorts(ports)), ReaderTransitionMode.Active,
				ReaderTransitionJournal(committed = ReaderCommittedPresentation.Initial(retained.decision.origin)),
				retained.ledger, activationState = { ReaderSessionActivationState.Activated })
			owner = PublicCloseOwner(
				close = { coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null)) },
				drain = { shadowOf(Looper.getMainLooper()).idle() },
				status = { coordinator.snapshot().releaseOnlyDeadlineStatus },
				fenced = { coordinator.snapshot().releaseOnlySink },
				resourceState = { retained.ledger.stateOf(retained.resource.registration.key) },
				coordinator = coordinator)
		} else {
			val queue = ArrayDeque<() -> Unit>()
			val store = ReaderActivatedSessionSnapshotStore { action -> queue.addLast(action); true }
			val barrier = ReaderActivatedSessionInstallationBarrier(store)
			assertTrue(barrier.installTestActivatedSession(ports, retained.decision, retained.ledger) == ReaderActivationInstallResult.Installed,
				"Public snapshot caller must own the installed decision")
			owner = PublicCloseOwner(
				close = { assertTrue(barrier.failClosedAfterFinalizationRejection(retained.decision), "Exact installed decision must close") },
				drain = { while (queue.isNotEmpty()) queue.removeFirst().invoke() },
				status = { store.journal.releaseOnlyCleanup?.deadlineStatus },
				fenced = { store.state == ReaderSessionActivationState.ReleaseOnly && !store.commandEgressOpen },
				resourceState = { retained.ledger.stateOf(retained.resource.registration.key) },
				store = store)
		}
		return owner
	}

	private data class RetainedCloseFixture(
		val decision: ReaderInitialActivationDecision,
		val resource: ReaderImportedLegacyResourceRegistration,
		val ledger: ReaderTransitionReleaseLedger
	)

	// Host resource scaffolding, not Native activation/composition or semantic authority proof.
	private fun retainedCloseFixture(binding: ReaderPresentationBinding): RetainedCloseFixture {
		val seedId = ReaderAdoptedPredecessorSeedId.fromValidatedImport(1L)
		val identity = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(41L)),
			ReaderLegacyInventorySource.FrameOrHandoff, ReaderLegacySourceLocalOpaqueToken(43L)
		)
		val ledger = ReaderTransitionReleaseLedger()
		val imported = kotlin.test.assertNotNull(ledger.importLegacy(
			identity, ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId),
			ReaderTransitionResourceKind.FrameHandoff, 19L
		))
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(ReaderPresentationToken(47L), binding, 53L, 59L, 100, 200)
		)
		val seed = ReaderAdoptedPredecessorSeed(seedId, identity, ReaderTransitionResourceKind.FrameHandoff,
			binding, owner, 17L, 19L)
		val origin = ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(
			seedId, 17L, 19L, owner, binding, imported.registration,
			requestedLease = ReaderInitialPresentationInputLease.ChromeOnly,
			physicalLease = ReaderInitialPresentationInputLease.ChromeOnly
		)
		return RetainedCloseFixture(ReaderInitialActivationDecision(origin, seed, imported, null), imported, ledger)
	}

	private class CommandScheduler : ReaderPageRelocationDispatchTimeoutScheduler {
		private val handler = android.os.Handler(Looper.getMainLooper())
		val scheduled = mutableListOf<Pair<Runnable, Long>>()
		val active = linkedSetOf<Runnable>()
		var onPost: ((Runnable, Long) -> Boolean)? = null
		override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
			scheduled += action to delayMillis
			active += action
			onPost?.let { return it(action, delayMillis) }
			return handler.postDelayed(action, delayMillis)
		}
		override fun removeCallbacks(action: Runnable) {
			active -= action
			handler.removeCallbacks(action)
		}
	}

	private fun commandTimerPort(fixture: Fixture): ReaderTask6FactOnlyTimerPort {
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(31L))
		assertEquals(ReaderPortCommandResult.Accepted, fixture.bridge.freezeForTransitionActivation(domain))
		assertEquals(ReaderPortCommandResult.Accepted, fixture.bridge.restoreAfterTransitionActivation(domain))
		val selectedOwner = fixture.bridge.javaClass.getDeclaredField("transitionTimeout").apply {
			isAccessible = true
		}.get(fixture.bridge)
		return assertIs<ReaderTask6FactOnlyTimerPort>(selectedOwner,
			"Adapt the existing physical timeout; the bridge itself need not implement the timer port")
	}

	private fun commandTimerId(binding: ReaderPresentationBinding) = ReaderTransitionId(
		17L, 19L, 1L, ReaderTransitionOperation.BootstrapNativePage,
		ReaderExpectedPresentationBinding.Exact(binding)
	)

	@Test
	fun bridgeTimesOutMissingCoverDrawAndRetryGetsOneFreshDeadline() {
		val fixture = Fixture()
		try {
			fixture.dispatch(ReaderPresentationEvent.ShellCoverRequested(2L))
			val token = assertIs<ReaderRequiredTransition.CommitShellCover>(fixture.decision.requiredTransition).token
			fixture.advance(6)
			repeat(3) { fixture.bridge.update(fixture.decision) }
			fixture.advance(4)
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(fixture.decision.diagnosticPresentation).reason)
			assertEquals(ReaderRequiredTransition.None, fixture.decision.requiredTransition)
			assertEquals(1, fixture.timeoutCount())
			fixture.dispatch(ReaderPresentationEvent.Retry)
			val retry = assertIs<ReaderRequiredTransition.CommitShellCover>(fixture.decision.requiredTransition)
			assertTrue(retry.token.value > token.value)
			fixture.dispatch(ReaderPresentationEvent.Retry)
			fixture.advance(9)
			assertEquals(1, fixture.timeoutCount())
			fixture.advance(1)
			assertEquals(2, fixture.timeoutCount())
			fixture.dispatch(ReaderPresentationEvent.Cancel)
			fixture.advance(30)
			assertEquals(2, fixture.timeoutCount())
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun visibilitySuspendsHostWorkAndResumesSamePendingAttempt() {
		val fixture = Fixture()
		try {
			fixture.dispatch(ReaderPresentationEvent.ShellCoverRequested(2L))
			val pending = fixture.state
			fixture.advance(4)
			fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost))
			assertEquals(ReaderRequiredTransition.None, fixture.decision.requiredTransition)
			fixture.advance(30)
			assertEquals(0, fixture.timeoutCount())
			fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityRestored))
			assertEquals(pending, fixture.state)
			fixture.advance(6)
			assertEquals(1, fixture.timeoutCount())
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun foliateWaitAndNativeWaitShareTheOriginalWholeTransactionDeadline() {
		val fixture = Fixture()
		try {
			val binding = fixture.binding
			fixture.dispatch(ReaderPresentationEvent.NativePagePresented(
				ReaderNativePagePresentationProof(binding, null, 1L, 100, 200, 4L, 5L)))
			val token = ReaderPresentationToken(7L)
			fixture.dispatch(ReaderPresentationEvent.CurlClaimed(
				ReaderCurlPresentationFrame(token, binding, 2L, 100, 200, 4L, 5L)))
			val next = binding.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 2L),
				textureGeneration = 8L)
			val ack = ReaderPageTurnSettlementAck("fixture-7", 2, "fixture", 4L, 8L)
			fixture.dispatch(ReaderPresentationEvent.CurlTerminal(token, binding, ack))
			fixture.advance(6)
			fixture.dispatch(ReaderPresentationEvent.FoliateRelocated(next, ack))
			fixture.advance(4)
			assertEquals(1, fixture.timeoutCount())
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(fixture.decision.diagnosticPresentation).reason)
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun terminalPublicationAndDisposalCancelScheduledDelivery() {
		for (closePublication in listOf(true, false)) {
			val fixture = Fixture()
			fixture.dispatch(ReaderPresentationEvent.NativePageRequested)
			if (closePublication) {
				fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.PublicationClosed))
			} else fixture.bridge.dispose()
			fixture.advance(30)
			assertEquals(0, fixture.timeoutCount())
			fixture.bridge.dispose()
		}
	}

	@Test
	fun existingHandbackTimerCannotExpireWhileHiddenAndRestoreKeepsWholeDeadline() {
		val fixture = Fixture()
		val token = ReaderPresentationToken(3L)
		fixture.state = fixture.state.copy(
			authority = ReaderPresentationAuthority.LiveEngineHandoffPending(
				retainedFrame = ReaderPresentationFrameOwner.LiveEngine(
					ReaderLiveEnginePresentationProof(ReaderPresentationToken(2L), fixture.binding, 1L)),
				token = token,
				binding = fixture.binding,
				direction = ReaderLiveEngineHandoffDirection.LiveEngineToNative
			)
		)
		val handler = android.os.Handler(Looper.getMainLooper())
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = object : ReaderNativePagePresentedFrameSource {
				override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long =
					error("No native candidate is available")
				override fun cancelPresentedFrameRequest(requestId: Long) = false
			},
			currentCandidate = { null },
			currentHandoffTransition = {
				fixture.decision.authoritativeLiveEngineToNativeTransitionOrNull()
			},
			handoffTimeoutScheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
				override fun postDelayed(action: Runnable, delayMillis: Long) = handler.postDelayed(action, delayMillis)
				override fun removeCallbacks(action: Runnable) = handler.removeCallbacks(action)
			}
		) { event ->
			fixture.events += event
			val result = readerPresentationReduce(fixture.state, event)
			fixture.state = result.state
			fixture.bridge.update(fixture.decision)
			readerTestPresentationReceipt(event, result.state, result.disposition)
		}
		try {
			fixture.bridge.update(fixture.decision)
			publisher.update()
			fixture.advance(4)
			fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost))
			publisher.update()
			fixture.advance(30)
			assertTrue(fixture.events.isEmpty(), "Neither deadline may fail hidden authority")
			fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityRestored))
			publisher.update()
			fixture.advance(5)
			assertTrue(fixture.events.isEmpty())
			fixture.advance(1)
			assertEquals(1, fixture.timeoutCount())
			publisher.update()
			fixture.advance(30)
			assertEquals(1, fixture.events.size, "The old handback clock must not deliver another terminal")
		} finally {
			publisher.dispose()
			fixture.bridge.dispose()
		}
	}

	@Test
	fun deadlineRegistrationFreezesDrainsAndRestoresItsRemainingDelay() {
		val scheduled = mutableListOf<Pair<Runnable, Long>>()
		val removed = mutableListOf<Runnable>()
		var now = 100L
		val delivered = mutableListOf<ReaderPresentationEvent.TimedOut>()
		val timer = ReaderPresentationTransitionTimeout(
			scheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
				override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
					scheduled += action to delayMillis
					return true
				}
				override fun removeCallbacks(action: Runnable) { removed += action }
			},
			nowMillis = { now },
			onTimeout = { delivered += it; true }
		)
		val fixture = Fixture()
		val pending = readerPresentationReduce(
			fixture.state,
			ReaderPresentationEvent.NativePageRequested
		)
		timer.update(pending.decision)
		now = 4_100L
		val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(59L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			timer.freezeForTransitionActivation(domain)
		)
		val row = timer.snapshotFrozenOwnership().single()
		assertEquals(ReaderLegacyInventorySource.DeadlineRegistration, row.physicalIdentity.source)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			timer.drainFrozenOwnership(row.physicalIdentity) {}
		)
		assertEquals(1, removed.size)
		assertTrue(timer.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			timer.restoreAfterTransitionActivation(domain)
		)
		assertEquals(listOf(10_000L, 6_000L), scheduled.map { it.second })
		scheduled.first().first.run()
		assertTrue(delivered.isEmpty())
		scheduled.last().first.run()
		assertEquals(
			listOf(ReaderPresentationEvent.TimedOut(pending.decision.pendingTransitionToken)),
			delivered
		)
		timer.cancel()
		fixture.bridge.dispose()
	}

	@Test
	fun cancelledSchedulerCallbacksCannotExpireRestoredOrSuccessorAttempts() {
		val fixture = Fixture()
		val scheduled = mutableListOf<Pair<Runnable, Long>>()
		val removed = mutableListOf<Runnable>()
		var now = 0L
		val delivered = mutableListOf<ReaderPresentationEvent.TimedOut>()
		val timer = ReaderPresentationTransitionTimeout(
			scheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
				override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
					scheduled += action to delayMillis
					return true
				}
				override fun removeCallbacks(action: Runnable) { removed += action }
			}, nowMillis = { now }, onTimeout = { delivered += it; true })
		try {
			val pending = readerPresentationReduce(fixture.state, ReaderPresentationEvent.NativePageRequested)
			timer.update(pending.decision)
			val first = scheduled.single().first
			now = 4_000L
			val hidden = readerPresentationReduce(pending.state,
				ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost))
			timer.update(hidden.decision)
			first.run()
			assertTrue(delivered.isEmpty())
			now = 34_000L
			timer.update(pending.decision)
			assertEquals(listOf(10_000L, 6_000L), scheduled.map { it.second })
			val resumed = scheduled.last().first
			val failed = readerPresentationReduce(pending.state,
				ReaderPresentationEvent.TimedOut(pending.decision.pendingTransitionToken))
			val retry = readerPresentationReduce(failed.state, ReaderPresentationEvent.Retry)
			timer.update(retry.decision)
			resumed.run()
			assertTrue(delivered.isEmpty())
			assertEquals(10_000L, scheduled.last().second)
			val current = scheduled.last().first
			current.run()
			current.run()
			assertEquals(listOf(ReaderPresentationEvent.TimedOut(retry.decision.pendingTransitionToken)), delivered)
			assertEquals(listOf(first, resumed), removed)
		} finally { timer.cancel(); fixture.bridge.dispose() }
	}

	@Test
	fun rejectedSchedulingRetriesMissingReceiptWithoutReentrantOrDuplicateDelivery() {
		val fixture = Fixture()
		var decision = readerPresentationReduce(fixture.state, ReaderPresentationEvent.NativePageRequested).decision
		var attempts = 0
		var schedules = 0
		lateinit var timer: ReaderPresentationTransitionTimeout
		timer = ReaderPresentationTransitionTimeout(
			scheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
				override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
					schedules += 1
					return false
				}
				override fun removeCallbacks(action: Runnable) = Unit
			}, nowMillis = { 0L }, onTimeout = {
				attempts += 1
				timer.update(decision)
				attempts > 1
			})
		try {
			timer.update(decision)
			assertEquals(1, attempts)
			timer.update(decision)
			assertEquals(2, attempts)
			timer.update(decision)
			assertEquals(2, attempts)
			assertEquals(1, schedules)
			decision = decision.copy(lifecycle = ReaderPresentationLifecycleState.Destroyed)
			timer.update(decision)
		} finally { timer.cancel(); fixture.bridge.dispose() }
	}

	@Test
	fun bridgeInjectionRetriesRejectedSchedulingOnlyAfterMissingReceiptReturns() {
		var acceptReceipt = false
		var schedules = 0
		val fixture = Fixture(timeoutScheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
			override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
				schedules += 1
				return false
			}
			override fun removeCallbacks(action: Runnable) = Unit
		}, nowMillis = { 0L }, acceptTimeout = { acceptReceipt })
		try {
			fixture.dispatch(ReaderPresentationEvent.NativePageRequested)
			assertEquals(1, fixture.timeoutCount())
			assertEquals(ReaderDiagnosticPresentation.Hidden, fixture.decision.diagnosticPresentation)
			acceptReceipt = true
			fixture.bridge.update(fixture.decision)
			assertEquals(2, fixture.timeoutCount())
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(fixture.decision.diagnosticPresentation).reason)
			fixture.bridge.update(fixture.decision)
			assertEquals(2, fixture.timeoutCount())
			assertEquals(1, schedules)
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun bridgeInjectedCancellationAndDisposalFenceRetiredRunnables() {
		val scheduled = mutableListOf<Runnable>()
		val removed = mutableListOf<Runnable>()
		val fixture = Fixture(timeoutScheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
			override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
				scheduled += action
				assertEquals(10_000L, delayMillis)
				return true
			}
			override fun removeCallbacks(action: Runnable) { removed += action }
		}, nowMillis = { 0L })
		fixture.dispatch(ReaderPresentationEvent.NativePageRequested)
		fixture.dispatch(ReaderPresentationEvent.Cancel)
		scheduled.single().run()
		fixture.dispatch(ReaderPresentationEvent.NativePageRequested)
		fixture.bridge.dispose()
		scheduled.forEach(Runnable::run)
		assertEquals(scheduled, removed)
		assertEquals(0, fixture.timeoutCount())
	}

	private class Fixture(
		timeoutScheduler: ReaderPageRelocationDispatchTimeoutScheduler = HandlerTimeoutScheduler(),
		nowMillis: () -> Long = android.os.SystemClock::uptimeMillis,
		acceptTimeout: () -> Boolean = { true }
	) {
		val binding = ReaderPresentationBinding("fixture", 1L, 2L, 3L,
			ReaderDestinationCommitIdentity("fixture", 1L), 4L, 5L, 6L)
		var state = readerPresentationReduce(ReaderPresentationState(),
			ReaderPresentationEvent.PublicationOpened(binding)).state
		val decision get() = readerPresentationDecision(state)
		val events = mutableListOf<ReaderPresentationEvent>()
		private val host = object : ReaderPresentationCommitHost {
			override val isAttachedToWindow = true
			override val currentPresentationBinding get() = state.binding
			override var currentShellCoverGeneration: Long? = null
			override val shellCoverSelected get() = state.authority is ReaderPresentationAuthority.ShellCover
			override val measuredViewportWidth = 100
			override val measuredViewportHeight = 200
			override fun prepareOpaqueShellCover(coverGeneration: Long) { currentShellCoverGeneration = coverGeneration }
			override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
			override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
			override fun registerShellCoverDrawListener(onDraw: () -> Unit) = ReaderPresentationDrawRegistration {}
			override fun postShellCoverAnimationFrame(onFrame: () -> Unit) = Unit
		}
		val bridge = ReaderPresentationHostBridge(host,
			transitionTimeoutScheduler = timeoutScheduler, transitionNowMillis = nowMillis) { event ->
			events += event
			if (event is ReaderPresentationEvent.TimedOut && !acceptTimeout()) return@ReaderPresentationHostBridge null
			val reduction = readerPresentationReduce(state, event)
			state = reduction.state
			readerTestPresentationReceipt(event, state, reduction.disposition)
		}
		fun dispatch(event: ReaderPresentationEvent) {
			state = readerPresentationReduce(state, event).state
			bridge.update(decision)
		}
		fun advance(seconds: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds)) }
		fun timeoutCount() = events.count { it is ReaderPresentationEvent.TimedOut }
	}
}
