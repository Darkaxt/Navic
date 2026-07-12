package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderPageTurnStateMachineTest {
	@Test
	fun captureMustFinishBeforeOverlayAttaches() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(ReaderPageTurnPhysicalDirection.TowardLeft, spread = false)
		assertEquals(ReaderPageTurnPhase.Preparing, machine.phase)
		assertTrue(machine.update(deltaAxis = -250f, axisSize = 1000, timestampMs = 10).isEmpty())

		val effects = machine.captureSucceeded(generation)
		assertIs<ReaderPageTurnEffect.AttachOverlay>(effects.first())
		assertIs<ReaderPageTurnEffect.Render>(effects.last())
		assertEquals(ReaderPageTurnPhase.Deforming, machine.phase)
	}

	@Test
	fun latestPointerReplacesEarlierPointerWhilePreparing() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(ReaderPageTurnPhysicalDirection.TowardLeft, spread = false)
		machine.update(-420f, 1000, 10)
		machine.update(-180f, 1000, 20)

		val render = machine.captureSucceeded(generation).filterIsInstance<ReaderPageTurnEffect.Render>().single()

		assertEquals(0.18f, render.progress)
	}

	@Test
	fun preparationCanAssignExactTargetBeforeRelease() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(ReaderPageTurnPhysicalDirection.TowardLeft, spread = true)

		assertTrue(machine.setTargetPageIndex(generation, pageIndex = 18))
		machine.captureSucceeded(generation)
		machine.update(-500f, 1000, 100)
		machine.release(-500f, 1000, 180)
		machine.animationFinished()

		assertTrue(machine.destinationSettled(pageIndex = 17).isEmpty())
		assertEquals(ReaderPageTurnPhase.Settling, machine.phase)
		assertIs<ReaderPageTurnEffect.DetachOverlay>(machine.destinationSettled(pageIndex = 18).single())
	}

	@Test
	fun staleCaptureCallbackAfterCancelIsIgnored() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(ReaderPageTurnPhysicalDirection.TowardLeft, spread = false)
		machine.cancel()
		assertTrue(machine.captureSucceeded(generation).isEmpty())
		assertEquals(ReaderPageTurnPhase.Idle, machine.phase)
	}

	@Test
	fun belowThresholdReleaseRelaxesWithoutNavigation() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(ReaderPageTurnPhysicalDirection.TowardLeft, spread = false)
		machine.captureSucceeded(generation)
		machine.update(-120f, 1000, 100)
		val release = machine.release(-120f, 1000, 160)
		assertIs<ReaderPageTurnEffect.AnimateRelax>(release.single())
		assertEquals(ReaderPageTurnPhase.Relaxing, machine.phase)
		assertFalse(machine.animationFinished().any { it is ReaderPageTurnEffect.Commit })
	}

	@Test
	fun animationCompletionShowsFinalBaseBeforeStartingExactNavigation() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			spread = true,
			targetPageIndex = 18
		)
		machine.captureSucceeded(generation)
		machine.update(-500f, 1000, 100)
		val release = machine.release(-500f, 1000, 180)
		assertIs<ReaderPageTurnEffect.AnimateCommit>(release.single())
		assertEquals(ReaderPageTurnPhase.Committing, machine.phase)

		val animationFinished = machine.animationFinished()
		assertIs<ReaderPageTurnEffect.ShowFinalBase>(animationFinished.first())
		assertIs<ReaderPageTurnEffect.Commit>(animationFinished.last())
		assertEquals(ReaderPageTurnPhase.Settling, machine.phase)
		val finished = machine.destinationSettled(pageIndex = 18)
		assertEquals(1, animationFinished.count { it is ReaderPageTurnEffect.Commit })
		assertEquals(1, finished.count { it is ReaderPageTurnEffect.DetachOverlay })
		assertTrue(machine.animationFinished().isEmpty())
	}

	@Test
	fun destinationSettlementBeforeNavigationCommitIsIgnored() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			spread = false,
			targetPageIndex = 9
		)
		machine.captureSucceeded(generation)
		machine.update(-500f, 1000, 100)
		machine.release(-500f, 1000, 180)

		assertTrue(machine.destinationSettled(pageIndex = 9).isEmpty())
		assertEquals(ReaderPageTurnPhase.Committing, machine.phase)
		val finished = machine.animationFinished()
		assertIs<ReaderPageTurnEffect.ShowFinalBase>(finished.first())
		assertIs<ReaderPageTurnEffect.Commit>(finished.last())
		assertEquals(ReaderPageTurnPhase.Settling, machine.phase)
		assertIs<ReaderPageTurnEffect.DetachOverlay>(machine.destinationSettled(pageIndex = 9).single())
		assertEquals(ReaderPageTurnPhase.Idle, machine.phase)
	}

	@Test
	fun wrongDestinationPageRemainsSettling() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(
			direction = ReaderPageTurnPhysicalDirection.TowardLeft,
			spread = true,
			targetPageIndex = 18
		)
		machine.captureSucceeded(generation)
		machine.update(-500f, 1000, 100)
		machine.release(-500f, 1000, 180)
		machine.animationFinished()

		assertTrue(machine.destinationSettled(pageIndex = 17).isEmpty())
		assertEquals(ReaderPageTurnPhase.Settling, machine.phase)
	}

	@Test
	fun fastFlickReleasedDuringCaptureWaitsForCaptureThenCommits() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(ReaderPageTurnPhysicalDirection.TowardRight, spread = false)
		machine.update(20f, 1000, 100)
		machine.update(140f, 1000, 110)
		assertTrue(machine.release(140f, 1000, 120).isEmpty())
		val effects = machine.captureSucceeded(generation)
		assertTrue(effects.any { it is ReaderPageTurnEffect.AttachOverlay })
		assertTrue(effects.any { it is ReaderPageTurnEffect.AnimateCommit })
		assertFalse(effects.any { it is ReaderPageTurnEffect.Commit })
		assertEquals(ReaderPageTurnPhase.Committing, machine.phase)
		assertTrue(machine.animationFinished().any { it is ReaderPageTurnEffect.Commit })
	}

	@Test
	fun captureFailureReturnsToIdleWithoutNavigation() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(ReaderPageTurnPhysicalDirection.TowardLeft, spread = false)
		assertTrue(machine.captureFailed(generation))
		assertEquals(ReaderPageTurnPhase.Idle, machine.phase)
	}

	@Test
	fun staleCaptureFailureCannotDisableTheCurrentSession() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(ReaderPageTurnPhysicalDirection.TowardLeft, spread = false)
		machine.cancel()

		assertFalse(machine.captureFailed(generation))
		assertEquals(ReaderPageTurnPhase.Idle, machine.phase)
	}
}
