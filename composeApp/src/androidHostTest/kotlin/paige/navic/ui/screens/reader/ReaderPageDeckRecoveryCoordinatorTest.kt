package paige.navic.ui.screens.reader

import karacken.curl.DeckRejectionReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderPageDeckRecoveryCoordinatorTest {
	@Test
	fun pendingCancellationFollowsADeckPromotedToActive() {
		assertTrue(
			readerRecoveredDeckCancellationRoleMatches(
				ReaderDeckSubmissionRole.Pending,
				ReaderDeckSubmissionRole.Active
			)
		)
		assertFalse(
			readerRecoveredDeckCancellationRoleMatches(
				ReaderDeckSubmissionRole.Active,
				ReaderDeckSubmissionRole.Pending
			)
		)
	}

	@Test
	fun activeRepairBlocksUntilTheMatchingGenerationIsPrepared() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)

		assertTrue(coordinator.accept(repairedWindow()))
		val requestId = host.singleBuildRequestId()
		assertFalse(coordinator.canAcceptPointer)
		assertTrue(host.submissions.isEmpty())

		host.completeBuild(requestId, generationId = 42L)
		assertEquals(
			listOf(42L to ReaderDeckSubmissionRole.Active),
			host.submissions
		)
		assertFalse(coordinator.canAcceptPointer)
		host.prepared += 41L
		assertFalse(coordinator.onDeckPrepared(41L))
		host.prepared += 42L
		host.usableActiveDeck = true
		assertTrue(coordinator.onDeckPrepared(42L))
		assertTrue(coordinator.canAcceptPointer)
	}

	@Test
	fun pendingRepairPreservesThePreparedActiveGeneration() {
		val host = FakeDeckRecoveryHost(
			usableActiveDeck = true,
			currentRole = ReaderDeckSubmissionRole.Pending
		)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)

		coordinator.accept(repairedWindow())
		assertTrue(coordinator.canAcceptPointer)
		host.completeBuild(host.singleBuildRequestId(), generationId = 43L)

		assertEquals(
			listOf(43L to ReaderDeckSubmissionRole.Pending),
			host.submissions
		)
		assertTrue(coordinator.canAcceptPointer)
		host.prepared += 43L
		assertTrue(coordinator.onDeckPrepared(43L))
		host.usableActiveDeck = false
		assertFalse(
			coordinator.canAcceptPointer,
			"A prepared pending generation cannot admit a pointer without a prepared active owner."
		)
	}

	@Test
	fun submissionRoleIsSelectedAfterBuildCompletion() {
		val host = FakeDeckRecoveryHost(
			usableActiveDeck = true,
			currentRole = ReaderDeckSubmissionRole.Pending
		)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())
		val requestId = host.singleBuildRequestId()

		host.usableActiveDeck = false
		host.currentRole = ReaderDeckSubmissionRole.Active
		host.completeBuild(requestId, generationId = 44L)

		assertEquals(
			listOf(44L to ReaderDeckSubmissionRole.Active),
			host.submissions
		)
	}

	@Test
	fun inverseSubmissionRoleChangeIsAlsoSelectedAfterBuildCompletion() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())
		val requestId = host.singleBuildRequestId()

		host.usableActiveDeck = true
		host.currentRole = ReaderDeckSubmissionRole.Pending
		host.completeBuild(requestId, generationId = 45L)

		assertEquals(
			listOf(45L to ReaderDeckSubmissionRole.Pending),
			host.submissions
		)
	}

	@Test
	fun staleRepairAndSupersededBuildCallbacksAreNoOps() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)

		assertFalse(coordinator.accept(repairedWindow(center = 6)))
		assertEquals(ReaderPageDeckRecoveryState.Idle, coordinator.state)
		coordinator.accept(repairedWindow())
		val oldRequest = host.singleBuildRequestId()
		coordinator.accept(repairedWindow())
		val newRequest = host.singleBuildRequestId()

		host.deliverCancelledBuild(oldRequest, generationId = 46L)
		assertEquals(listOf(46L), host.releasedGenerations)
		assertTrue(host.submissions.isEmpty())
		host.completeBuild(newRequest, generationId = 47L)
		assertEquals(
			listOf(47L to ReaderDeckSubmissionRole.Active),
			host.submissions
		)
	}

	@Test
	fun synchronousCancelledBuildCallbackIsAlreadyTombstoned() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())
		host.synchronousCancelledGeneration = 48L

		coordinator.accept(repairedWindow())

		assertEquals(listOf(48L), host.releasedGenerations)
		assertTrue(host.submissions.isEmpty())
		assertEquals(1, host.buildRequestIds().size)
	}

	@Test
	fun sameRequestBuildThatBecomesStaleIsReleased() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())
		val requestId = host.singleBuildRequestId()
		host.windowCurrent = false

		host.completeBuild(requestId, generationId = 49L)

		assertEquals(listOf(49L), host.releasedGenerations)
		assertTrue(host.submissions.isEmpty())
		assertEquals(ReaderPageDeckRecoveryState.Idle, coordinator.state)
	}

	@Test
	fun staleSameRequestBuildIsANoOpRatherThanAFailure() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())

		host.completeStaleBuild(host.singleBuildRequestId())

		assertEquals(ReaderPageDeckRecoveryState.Idle, coordinator.state)
		assertTrue(host.submissions.isEmpty())
		assertTrue(host.releasedGenerations.isEmpty())
	}

	@Test
	fun asynchronousBuildFailurePublishesTypedRecoveryFailure() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())

		host.failBuild(host.singleBuildRequestId(), "decoded-window-unavailable")

		assertEquals(
			ReaderPageDeckRecoveryState.Failed("decoded-window-unavailable"),
			coordinator.state
		)
		assertFalse(coordinator.canAcceptPointer)
	}

	@Test
	fun synchronousRendererRejectionCannotBeOverwritten() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		host.synchronousRejection = { generationId, reason ->
			host.releasedGenerations += generationId
			assertTrue(coordinator.onDeckRejected(generationId, reason))
		}
		coordinator.accept(repairedWindow())

		host.completeBuild(host.singleBuildRequestId(), generationId = 50L)

		assertIs<ReaderPageDeckRecoveryState.Failed>(coordinator.state)
		assertEquals(listOf(50L), host.releasedGenerations)
		host.prepared += 50L
		assertFalse(coordinator.onDeckPrepared(50L))
		assertIs<ReaderPageDeckRecoveryState.Failed>(coordinator.state)
	}

	@Test
	fun pendingPreparationFailurePreservesPreparedActivePointerAdmission() {
		val host = FakeDeckRecoveryHost(
			usableActiveDeck = true,
			currentRole = ReaderDeckSubmissionRole.Pending
		)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())
		host.completeBuild(host.singleBuildRequestId(), generationId = 50L)

		assertTrue(coordinator.onDeckPreparationFailed(50L, "GPU_BUDGET"))

		assertEquals(
			ReaderPageDeckRecoveryState.Failed(
				"recovered-deck-preparation-failed:GPU_BUDGET"
			),
			coordinator.state
		)
		assertTrue(coordinator.canAcceptPointer)
	}

	@Test
	fun preparedPendingRecoveryCanReportARehydrationFailure() {
		val host = FakeDeckRecoveryHost(
			usableActiveDeck = true,
			currentRole = ReaderDeckSubmissionRole.Pending
		)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())
		host.completeBuild(host.singleBuildRequestId(), generationId = 51L)
		host.prepared += 51L
		assertTrue(coordinator.onDeckPrepared(51L))

		assertTrue(coordinator.onDeckPreparationFailed(51L, "CONTEXT"))

		assertEquals(
			ReaderPageDeckRecoveryState.Failed(
				"recovered-deck-preparation-failed:CONTEXT"
			),
			coordinator.state
		)
		assertTrue(coordinator.canAcceptPointer)
	}

	@Test
	fun activePreparationFailureBlocksPointerAdmission() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())
		host.completeBuild(host.singleBuildRequestId(), generationId = 51L)

		assertTrue(coordinator.onDeckPreparationFailed(51L, "BITMAP"))

		assertIs<ReaderPageDeckRecoveryState.Failed>(coordinator.state)
		assertFalse(coordinator.canAcceptPointer)
	}

	@Test
	fun throwingSubmissionReleasesTheUnacceptedGeneration() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		host.throwOnSubmission = true
		coordinator.accept(repairedWindow())

		host.completeBuild(host.singleBuildRequestId(), generationId = 51L)

		assertEquals(listOf(51L), host.releasedGenerations)
		assertIs<ReaderPageDeckRecoveryState.Failed>(coordinator.state)
		assertEquals(0, host.retainedSubmittedGenerations)
	}

	@Test
	fun supersedingSubmittedGenerationCancelsItsExactRole() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())
		host.completeBuild(host.singleBuildRequestId(), generationId = 51L)

		coordinator.accept(repairedWindow())

		assertEquals(
			listOf(51L to ReaderDeckSubmissionRole.Active),
			host.cancelledSubmissions
		)
		assertEquals(0, host.retainedSubmittedGenerations)
	}

	@Test
	fun teardownWhileWaitingForPreparationCancelsSubmittedGeneration() {
		val host = FakeDeckRecoveryHost(
			usableActiveDeck = true,
			currentRole = ReaderDeckSubmissionRole.Pending
		)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())
		host.completeBuild(host.singleBuildRequestId(), generationId = 52L)

		coordinator.cancelAll()

		assertEquals(
			listOf(52L to ReaderDeckSubmissionRole.Pending),
			host.cancelledSubmissions
		)
		assertEquals(0, host.retainedSubmittedGenerations)
		assertEquals(ReaderPageDeckRecoveryState.Idle, coordinator.state)
	}

	@Test
	fun releaseBeforePreparationCannotStrandRecovery() {
		val host = FakeDeckRecoveryHost(usableActiveDeck = false)
		val coordinator = ReaderPageDeckRecoveryCoordinator(host)
		coordinator.accept(repairedWindow())
		host.completeBuild(host.singleBuildRequestId(), generationId = 53L)

		assertTrue(coordinator.onDeckReleased(53L))

		assertIs<ReaderPageDeckRecoveryState.Failed>(coordinator.state)
		assertFalse(coordinator.canAcceptPointer)
	}

	private fun repairedWindow(
		center: Int = 5,
		rasterEpoch: Long = 9L
	): ReaderPageRasterRepairResult.Repaired = readerPageRasterRepairedResult(
		repairedPageIndices = setOf(3, 4, 5, 6, 7),
		centerOrdinal = center,
		rasterEpoch = rasterEpoch
	)

	private class FakeDeckRecoveryHost(
		var usableActiveDeck: Boolean,
		var currentRole: ReaderDeckSubmissionRole = ReaderDeckSubmissionRole.Active
	) : ReaderPageDeckRecoveryHost {
		private val builds = linkedMapOf<
			Long,
			(ReaderPageRecoveredDeckBuildResult) -> Unit
		>()
		private val cancelledBuilds = linkedMapOf<
			Long,
			(ReaderPageRecoveredDeckBuildResult) -> Unit
		>()
		val cancelledRequests = mutableListOf<Long>()
		val releasedGenerations = mutableListOf<Long>()
		val submissions = mutableListOf<Pair<Long, ReaderDeckSubmissionRole>>()
		val cancelledSubmissions = mutableListOf<Pair<Long, ReaderDeckSubmissionRole>>()
		val prepared = mutableSetOf<Long>()
		var windowCurrent = true
		var synchronousCancelledGeneration: Long? = null
		var synchronousRejection: ((Long, DeckRejectionReason) -> Unit)? = null
		var throwOnSubmission = false
		private val submittedGenerations = mutableSetOf<Long>()
		val retainedSubmittedGenerations: Int
			get() = submittedGenerations.size

		override fun isCurrentRepairWindow(
			repairedPageIndices: Set<Int>,
			centerOrdinal: Int,
			rasterEpoch: Long
		): Boolean = windowCurrent &&
			repairedPageIndices == setOf(3, 4, 5, 6, 7) &&
			centerOrdinal == 5 &&
			rasterEpoch == 9L

		override fun hasUsablePreparedActiveDeck(): Boolean = usableActiveDeck

		override fun requestRecoveredDeckBuild(
			requestId: Long,
			repairedPageIndices: Set<Int>,
			centerOrdinal: Int,
			rasterEpoch: Long,
			onBuilt: (ReaderPageRecoveredDeckBuildResult) -> Unit
		) {
			check(isCurrentRepairWindow(repairedPageIndices, centerOrdinal, rasterEpoch))
			builds[requestId] = onBuilt
		}

		override fun cancelRecoveredDeckBuild(requestId: Long) {
			cancelledRequests += requestId
			val callback = builds.remove(requestId) ?: return
			val synchronousGeneration = synchronousCancelledGeneration
			if (synchronousGeneration == null) {
				cancelledBuilds[requestId] = callback
			} else {
				synchronousCancelledGeneration = null
				callback(ReaderPageRecoveredDeckBuildResult.Built(synchronousGeneration))
			}
		}

		override fun currentRecoveredDeckRole(): ReaderDeckSubmissionRole = currentRole

		override fun submitRecoveredDeck(
			generationId: Long,
			role: ReaderDeckSubmissionRole
		) {
			submissions += generationId to role
			submittedGenerations += generationId
			if (throwOnSubmission) error("submission-failed")
			synchronousRejection?.invoke(
				generationId,
				DeckRejectionReason.INVALID_CONTENT
			)
			if (synchronousRejection != null) submittedGenerations -= generationId
		}

		override fun releaseRecoveredDeck(generationId: Long) {
			releasedGenerations += generationId
			submittedGenerations -= generationId
		}

		override fun cancelSubmittedRecoveredDeck(
			generationId: Long,
			role: ReaderDeckSubmissionRole
		) {
			cancelledSubmissions += generationId to role
			submittedGenerations -= generationId
		}

		override fun isPrepared(generationId: Long): Boolean = generationId in prepared

		fun buildRequestIds(): Set<Long> = builds.keys.toSet()

		fun singleBuildRequestId(): Long = builds.keys.single()

		fun completeBuild(requestId: Long, generationId: Long) {
			checkNotNull(builds.remove(requestId)).invoke(
				ReaderPageRecoveredDeckBuildResult.Built(generationId)
			)
		}

		fun completeStaleBuild(requestId: Long) {
			checkNotNull(builds.remove(requestId)).invoke(
				ReaderPageRecoveredDeckBuildResult.Stale
			)
		}

		fun failBuild(requestId: Long, reason: String) {
			checkNotNull(builds.remove(requestId)).invoke(
				ReaderPageRecoveredDeckBuildResult.Failed(reason)
			)
		}

		fun deliverCancelledBuild(requestId: Long, generationId: Long) {
			checkNotNull(cancelledBuilds.remove(requestId)).invoke(
				ReaderPageRecoveredDeckBuildResult.Built(generationId)
			)
		}
	}
}
