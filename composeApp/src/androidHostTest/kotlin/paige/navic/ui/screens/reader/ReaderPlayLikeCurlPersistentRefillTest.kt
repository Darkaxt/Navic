package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageTurnDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPlayLikeCurlPersistentRefillTest {
	@Test
	fun nextTurnPublishesDestinationWindowBeforeHydratingMissingFarEdge() = runTest {
		val harness = PersistentRefillHarness(
			initiallyDecoded = setOf(2, 3, 4, 5, 6),
			durablePages = (0..12).toSet()
		)

		harness.commitTurn(ReaderPageTurnDirection.Next, destinationOrdinal = 5)

		assertEquals(
			listOf("window:[3, 4, 5, 6, 7]", "persistent:7"),
			harness.events
		)
		assertEquals(setOf(3, 4, 5, 6, 7), harness.protectedPageIndices)
		assertTrue(harness.repairPageIndices.isEmpty())
	}

	@Test
	fun previousTurnPublishesDestinationWindowBeforeHydratingMissingFarEdge() = runTest {
		val harness = PersistentRefillHarness(
			initiallyDecoded = setOf(3, 4, 5, 6, 7),
			durablePages = (0..12).toSet()
		)

		harness.commitTurn(ReaderPageTurnDirection.Previous, destinationOrdinal = 4)

		assertEquals(
			listOf("window:[2, 3, 4, 5, 6]", "persistent:2"),
			harness.events
		)
		assertEquals(setOf(2, 3, 4, 5, 6), harness.protectedPageIndices)
		assertTrue(harness.repairPageIndices.isEmpty())
	}

	@Test
	fun decodedFarEdgeSkipsPersistentReadAndRepair() = runTest {
		val harness = PersistentRefillHarness(
			initiallyDecoded = setOf(3, 4, 5, 6, 7),
			durablePages = emptySet()
		)

		harness.commitTurn(ReaderPageTurnDirection.Next, destinationOrdinal = 5)

		assertEquals(listOf("window:[3, 4, 5, 6, 7]"), harness.events)
		assertTrue(harness.repairPageIndices.isEmpty())
	}

	@Test
	fun persistentMissRequestsExactlyOneFarEdgeRepair() = runTest {
		val harness = PersistentRefillHarness(
			initiallyDecoded = setOf(3, 4, 5, 6),
			durablePages = emptySet()
		)

		harness.commitTurn(ReaderPageTurnDirection.Next, destinationOrdinal = 5)

		assertEquals(listOf(7), harness.repairPageIndices)
		assertEquals(1, harness.repairPageIndices.count { it == 7 })
	}

	@Test
	fun completedRepairFromPriorSameProfileTurnCannotRefillAfterAbaReturn() {
		val profile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = "repair-aba",
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			quality = ReaderPageBitmapQuality.Balanced
		)
		val fence = ReaderPlayLikeCurlRasterRepairFence(
			profile = profile,
			requestGeneration = 4L,
			destinationOrdinal = 5,
			committedTurnVersion = 7L,
			protectedWindowVersion = 11L,
			protectedWindow = listOf(3, 4, 5, 6, 7)
		)

		assertTrue(
			fence.matches(
				profile = profile,
				requestGeneration = 4L,
				destinationOrdinal = 5,
				committedTurnVersion = 7L,
				protectedWindowVersion = 11L,
				protectedWindow = listOf(3, 4, 5, 6, 7)
			)
		)
		assertFalse(
			fence.matches(
				profile = profile,
				requestGeneration = 4L,
				destinationOrdinal = 5,
				committedTurnVersion = 9L,
				protectedWindowVersion = 13L,
				protectedWindow = listOf(3, 4, 5, 6, 7)
			)
		)
	}

	@Test
	fun repairRegistryPreservesCurrentRecipientAcrossSamePageAba() {
		val profile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = "repair-registry-aba",
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			quality = ReaderPageBitmapQuality.Balanced
		)
		fun recipient(turnVersion: Long, windowVersion: Long) =
			ReaderPlayLikeCurlRasterRepairRecipient(
				fence = ReaderPlayLikeCurlRasterRepairFence(
					profile = profile,
					requestGeneration = 4L,
					destinationOrdinal = 5,
					committedTurnVersion = turnVersion,
					protectedWindowVersion = windowVersion,
					protectedWindow = listOf(3, 4, 5, 6, 7)
				)
			)
		val registry = ReaderPlayLikeCurlRasterRepairRegistry()

		val operationToken = checkNotNull(
			registry.register(profile, sourcePageIndex = 7, recipient(7L, 11L))
		)
		assertEquals(
			null,
			registry.register(profile, sourcePageIndex = 7, recipient(9L, 13L))
		)
		val recipients = checkNotNull(
			registry.complete(profile, sourcePageIndex = 7, operationToken)
		)
		val current = recipients.lastOrNull { candidate ->
			candidate.fence.matches(
				profile = profile,
				requestGeneration = 4L,
				destinationOrdinal = 5,
				committedTurnVersion = 9L,
				protectedWindowVersion = 13L,
				protectedWindow = listOf(3, 4, 5, 6, 7)
			)
		}

		assertEquals(2, recipients.size)
		assertEquals(9L, current?.fence?.committedTurnVersion)
		assertTrue(registry.isEmpty())

		val retryToken = checkNotNull(
			registry.register(
				profile,
				sourcePageIndex = 7,
				recipient(9L, 13L).copy(attempt = 1)
			)
		)
		assertEquals(
			null,
			registry.complete(profile, sourcePageIndex = 7, operationToken)
		)
		assertEquals(
			1,
			registry.complete(profile, sourcePageIndex = 7, retryToken)?.size
		)
	}

	@Test
	fun abaReturnToSameDestinationCannotPublishStaleHydrationOrRepair() = runTest {
		var committedTurnVersion = 1L
		var protectedWindowVersion = 0L
		var protectedWindow = emptyList<Int>()
		val hydrationStarted = CompletableDeferred<Unit>()
		val releaseHydration = CompletableDeferred<Unit>()
		val decoded = mutableSetOf<Int>()
		val repairs = mutableListOf<Int>()
		val coordinator = ReaderPagePersistentRefillCoordinator(
			protectedWindowForCenter = { center -> (center - 2..center + 2).toList() },
			publishProtectedWindow = { window ->
				protectedWindowVersion += 1L
				protectedWindow = window
				protectedWindowVersion
			},
			isDecoded = decoded::contains,
			hydratePersistent = { pageIndex, fence, isStillCurrent ->
				hydrationStarted.complete(Unit)
				releaseHydration.await()
				if (isStillCurrent(fence)) decoded += pageIndex
				false
			},
			requestRepair = repairs::add
		)
		val expectedTurnVersion = committedTurnVersion
		val refill = async {
			coordinator.onTurnCommitted(
				direction = ReaderPageTurnDirection.Next,
				destinationOrdinal = 5,
				committedTurnVersion = expectedTurnVersion,
				isTurnStillCurrent = { committedTurnVersion == expectedTurnVersion },
				isStillCurrent = { fence ->
					committedTurnVersion == fence.committedTurnVersion &&
						protectedWindowVersion == fence.protectedWindowVersion &&
						protectedWindow == fence.protectedWindow
				}
			)
		}
		hydrationStarted.await()

		committedTurnVersion = 2L
		protectedWindowVersion = 2L
		protectedWindow = (4..8).toList()
		committedTurnVersion = 3L
		protectedWindowVersion = 3L
		protectedWindow = (3..7).toList()
		releaseHydration.complete(Unit)
		refill.await()

		assertTrue(decoded.isEmpty())
		assertTrue(repairs.isEmpty())
		assertEquals(3L, committedTurnVersion)
		assertEquals(3L, protectedWindowVersion)
	}

	private class PersistentRefillHarness(
		initiallyDecoded: Set<Int>,
		private val durablePages: Set<Int>
	) {
		private val decoded = initiallyDecoded.toMutableSet()
		val events = mutableListOf<String>()
		val repairPageIndices = mutableListOf<Int>()
		var protectedPageIndices = emptySet<Int>()
			private set
		private var committedTurnVersion = 0L
		private var protectedWindowVersion = 0L
		private val coordinator = ReaderPagePersistentRefillCoordinator(
			protectedWindowForCenter = { center -> (center - 2..center + 2).toList() },
			publishProtectedWindow = { window ->
				protectedWindowVersion += 1L
				protectedPageIndices = window.toSet()
				events += "window:$window"
				protectedWindowVersion
			},
			isDecoded = decoded::contains,
			hydratePersistent = { pageIndex, fence, isStillCurrent ->
				events += "persistent:$pageIndex"
				val hit = pageIndex in durablePages
				if (hit && isStillCurrent(fence)) decoded += pageIndex
				hit
			},
			requestRepair = repairPageIndices::add
		)

		suspend fun commitTurn(
			direction: ReaderPageTurnDirection,
			destinationOrdinal: Int
		) {
			committedTurnVersion += 1L
			val expectedVersion = committedTurnVersion
			coordinator.onTurnCommitted(
				direction = direction,
				destinationOrdinal = destinationOrdinal,
				committedTurnVersion = expectedVersion,
				isTurnStillCurrent = { committedTurnVersion == expectedVersion },
				isStillCurrent = { fence ->
					committedTurnVersion == fence.committedTurnVersion &&
						protectedWindowVersion == fence.protectedWindowVersion &&
						protectedPageIndices == fence.protectedWindow.toSet()
				}
			)
		}
	}
}
