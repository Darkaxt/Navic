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
		assertEquals(ReaderPageTurnPhase.Capturing, machine.phase)
		assertTrue(machine.update(deltaAxis = -250f, axisSize = 1000, timestampMs = 10).isEmpty())

		val effects = machine.captureSucceeded(generation)
		assertIs<ReaderPageTurnEffect.AttachOverlay>(effects.first())
		assertIs<ReaderPageTurnEffect.Render>(effects.last())
		assertEquals(ReaderPageTurnPhase.Deforming, machine.phase)
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
	fun distanceCommitNavigatesOnlyAfterAnimationAndExactlyOnce() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(ReaderPageTurnPhysicalDirection.TowardLeft, spread = true)
		machine.captureSucceeded(generation)
		machine.update(-500f, 1000, 100)
		assertIs<ReaderPageTurnEffect.AnimateCommit>(machine.release(-500f, 1000, 180).single())
		assertEquals(ReaderPageTurnPhase.Committing, machine.phase)

		val finished = machine.animationFinished()
		assertEquals(1, finished.count { it is ReaderPageTurnEffect.Commit })
		assertEquals(1, finished.count { it is ReaderPageTurnEffect.DetachOverlay })
		assertTrue(machine.animationFinished().isEmpty())
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
		assertEquals(ReaderPageTurnPhase.Committing, machine.phase)
	}

	@Test
	fun captureFailureReturnsToIdleWithoutNavigation() {
		val machine = ReaderPageTurnStateMachine()
		val generation = machine.begin(ReaderPageTurnPhysicalDirection.TowardLeft, spread = false)
		assertTrue(machine.captureFailed(generation).isEmpty())
		assertEquals(ReaderPageTurnPhase.Idle, machine.phase)
	}
}
