package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import paige.navic.reader.ReaderPageBitmapQuality
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val TestRendererDeckLeaseLimit = 4

class ReaderPlayLikeCurlRasterAdapterTest {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	@AfterTest
	fun tearDown() {
		scope.cancel()
	}

	@Test
	fun duplicatePageIdentitiesLoadOnceAndMapToOneDeckEntry() = runBlocking {
		val loader = FakeRasterLoader()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = {}
		)
		val profile = profile("reference")

		val deck = adapter.prepare(profile, listOf(0, 0, 1)).await()

		checkNotNull(deck)
		assertEquals(setOf(0, 1), loader.calls.mapTo(mutableSetOf()) { key -> key.pageIndex })
		assertEquals(2, loader.calls.size)
		assertEquals("reference-page-0", deck.value(0))
		assertEquals("reference-page-1", deck.value(1))
		assertSame(deck.value(0), deck.value(0))
		deck.close()
		adapter.close()
	}

	@Test
	fun onePreparationStartsDistinctRasterLoadsConcurrently() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val loader = FakeRasterLoader(gateProfile = "parallel", gate = gate)
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = {}
		)
		val preparation = adapter.prepare(profile("parallel"), listOf(0, 1))

		withTimeout(1_000L) { loader.secondStarted.await() }
		gate.complete(Unit)
		val deck = checkNotNull(preparation.await())

		assertEquals(setOf(0, 1), loader.calls.mapTo(mutableSetOf()) { it.pageIndex })
		deck.close()
		adapter.close()
	}

	@Test
	fun cancellingPreparationCancelsItsRasterLoadsAndReturnsWorkerCapacity() = runBlocking {
		val started = CompletableDeferred<Unit>()
		val cancelled = CompletableDeferred<Unit>()
		val loader = ReaderPlayLikeCurlRasterLoader<String> { key ->
			if (key.pageIndex == 0) {
				started.complete(Unit)
				try {
					CompletableDeferred<Unit>().await()
					"unused"
				} finally {
					cancelled.complete(Unit)
				}
			} else {
				"replacement-page-${key.pageIndex}"
			}
		}
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = 1,
			release = {}
		)
		val preparation = adapter.prepare(profile("cancelled"), listOf(0))
		started.await()

		preparation.cancel()

		withTimeout(1_000L) { cancelled.await() }
		withTimeout(1_000L) {
			while (adapter.metrics().activePreparationWorkers != 0) delay(10L)
		}
		val replacement = checkNotNull(
			adapter.prepare(profile("cancelled"), listOf(1)).await()
		)
		assertEquals("replacement-page-1", replacement.value(1))
		replacement.close()
		adapter.closeAndJoin()
	}

	@Test
	fun cancelledSoleWaiterCannotPublishAValueReturnedAfterCancellation() = runBlocking {
		val started = CompletableDeferred<Unit>()
		val releaseLoad = CompletableDeferred<Unit>()
		val released = CompletableDeferred<String>()
		val profile = profile("late-cancelled")
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = ReaderPlayLikeCurlRasterLoader { key ->
				started.complete(Unit)
				withContext(NonCancellable) {
					releaseLoad.await()
					"late-cancelled-page-${key.pageIndex}"
				}
			},
			rendererDeckLeaseLimit = 1,
			release = released::complete
		)
		val preparation = adapter.prepare(profile, listOf(0))
		started.await()

		preparation.cancel()
		releaseLoad.complete(Unit)

		assertEquals("late-cancelled-page-0", withTimeout(1_000L) { released.await() })
		assertEquals(false, adapter.hasDecoded(profile, 0))
		adapter.closeAndJoin()
	}

	@Test
	fun cancellingOnePreparationDoesNotCancelASecondPreparation() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val loader = FakeRasterLoader(gateProfile = "shared-cancellation", gate = gate)
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = {}
		)
		val profile = profile("shared-cancellation")
		val cancelled = adapter.prepare(profile, listOf(0))
		loader.firstStarted.await()
		val survivor = adapter.prepare(profile, listOf(0))
		cancelled.cancel()

		gate.complete(Unit)
		val deck = checkNotNull(survivor.await())

		assertEquals("shared-cancellation-page-0", deck.value(0))
		deck.close()
		adapter.closeAndJoin()
	}

	@Test
	fun unavailableConcurrentLoadCancelsEarlierWaiterPromptly() = runBlocking {
		val firstStarted = CompletableDeferred<Unit>()
		val firstFinished = CompletableDeferred<Unit>()
		val firstGate = CompletableDeferred<Unit>()
		val loader = ReaderPlayLikeCurlRasterLoader<String> { key ->
			if (key.pageIndex == 0) {
				firstStarted.complete(Unit)
				try {
					firstGate.await()
					"unused"
				} finally {
					firstFinished.complete(Unit)
				}
			} else {
				null
			}
		}
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = {}
		)
		val preparation = adapter.prepare(profile("unavailable-parallel"), listOf(0, 1))
		firstStarted.await()

		withTimeout(1_000L) { assertNull(preparation.await()) }
		withTimeout(1_000L) { firstFinished.await() }
		withTimeout(1_000L) {
			while (adapter.metrics().activeMaterializationWorkers != 0) delay(10L)
		}
		assertEquals(0, adapter.metrics().activeMaterializationWorkers)

		adapter.closeAndJoin()
		assertEquals(0, adapter.metrics().activePreparationWorkers)
		assertEquals(0, adapter.metrics().activeMaterializationWorkers)
	}

	@Test
	fun concurrentPreparationsShareOneInFlightRaster() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val loader = FakeRasterLoader(gateProfile = "shared", gate = gate)
		val released = mutableListOf<String>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = released::add
		)
		val profile = profile("shared")

		val first = adapter.prepare(profile, listOf(0))
		loader.firstStarted.await()
		val second = adapter.prepare(profile, listOf(0))
		gate.complete(Unit)
		val firstDeck = checkNotNull(first.await())
		val secondDeck = checkNotNull(second.await())

		assertEquals(1, loader.calls.size)
		assertSame(firstDeck.value(0), secondDeck.value(0))
		firstDeck.close()
		secondDeck.close()
		assertEquals(emptyList(), released)
		adapter.close()
		assertEquals(listOf("shared-page-0"), released)
	}

	@Test
	fun profileChangeRejectsAndReleasesStaleRaster() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val loader = FakeRasterLoader(gateProfile = "old", gate = gate)
		val released = mutableListOf<String>()
		val staleReleased = CompletableDeferred<String>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = { value ->
				released += value
				if (value.startsWith("old-")) staleReleased.complete(value)
			}
		)

		val stale = adapter.prepare(profile("old"), listOf(0))
		loader.firstStarted.await()
		val current = adapter.prepare(profile("new"), listOf(1))
		gate.complete(Unit)

		assertNull(stale.await())
		val currentDeck = checkNotNull(current.await())
		assertEquals("new-page-1", currentDeck.value(1))
		assertEquals("old-page-0", staleReleased.await())
		assertEquals(listOf("old-page-0"), released)
		currentDeck.close()
		adapter.close()
		assertEquals(listOf("old-page-0", "new-page-1"), released)
	}

	@Test
	fun concurrentCurrentFencesReusePublishedEntryAndReleaseDuplicateValue() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val loader = FakeRasterLoader(gateProfile = "duplicate", gate = gate)
		val released = mutableListOf<String>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = released::add
		)
		val profile = profile("duplicate")
		val first = adapter.prepare(
			profile = profile,
			pageIndices = listOf(0),
			publicationFence = ReaderPlayLikeCurlRasterPublicationFence { true }
		)
		val second = adapter.prepare(
			profile = profile,
			pageIndices = listOf(0),
			publicationFence = ReaderPlayLikeCurlRasterPublicationFence { true }
		)
		loader.secondStarted.await()

		gate.complete(Unit)
		val firstDeck = checkNotNull(first.await())
		val secondDeck = checkNotNull(second.await())

		assertSame(firstDeck.value(0), secondDeck.value(0))
		assertEquals(listOf("duplicate-page-0"), released)
		firstDeck.close()
		secondDeck.close()
		adapter.close()
		assertEquals(listOf("duplicate-page-0", "duplicate-page-0"), released)
	}

	@Test
	fun protectedWindowCanBeRestoredWithoutStartingAnotherPreparation() = runBlocking {
		val reserveStarted = CompletableDeferred<Unit>()
		val releaseReserve = CompletableDeferred<Unit>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = ReaderPlayLikeCurlRasterLoader { key ->
				if (key.pageIndex == 2) {
					reserveStarted.complete(Unit)
					releaseReserve.await()
				}
				"protected-window-page-${key.pageIndex}"
			},
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			residentEntryLimit = 2,
			release = {}
		)
		val profile = profile("protected-window")
		checkNotNull(adapter.prepare(profile, listOf(0, 1)).await()).close()
		val reserve = adapter.prepare(profile, listOf(2))
		reserveStarted.await()

		assertTrue(adapter.updateProtectedPageIndices(profile, listOf(0)))
		releaseReserve.complete(Unit)
		val reserveDeck = checkNotNull(reserve.await())
		assertTrue(adapter.hasDecoded(profile, 0))
		assertEquals(false, adapter.hasDecoded(profile, 1))
		assertTrue(adapter.hasDecoded(profile, 2))
		assertEquals(
			false,
			adapter.updateProtectedPageIndices(profile("other-profile"), listOf(0))
		)
		adapter.close()
		assertEquals(false, adapter.updateProtectedPageIndices(profile, listOf(0)))
		reserveDeck.close()
		adapter.closeAndJoin()
	}

	@Test
	fun acquisitionInterceptorCanMissResidentEntryWithoutReloadingOrReleasingIt() = runBlocking {
		val loader = FakeRasterLoader()
		val released = mutableListOf<String>()
		var forceMiss = false
		val intercepted = mutableListOf<Int>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			acquisitionInterceptor = { key ->
				if (forceMiss) intercepted += key.pageIndex
				forceMiss
			},
			release = released::add
		)
		val profile = profile("resident-intercept")
		checkNotNull(adapter.prepare(profile, listOf(0)).await()).close()
		forceMiss = true

		val missed = adapter.prepare(profile, listOf(0)).await()

		assertNull(missed)
		assertEquals(listOf(0), intercepted)
		assertEquals(1, loader.calls.size)
		assertTrue(adapter.hasDecoded(profile, 0))
		assertTrue(released.isEmpty())
		forceMiss = false
		checkNotNull(adapter.prepare(profile, listOf(0)).await()).close()
		assertEquals(1, loader.calls.size)
		adapter.close()
		assertEquals(listOf("resident-intercept-page-0"), released)
	}

	@Test
	fun staleFinalFenceRejectsDeckBuiltFromExistingCacheEntry() = runBlocking {
		val released = mutableListOf<String>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = FakeRasterLoader(),
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = released::add
		)
		val profile = profile("cached-fence")
		checkNotNull(adapter.prepare(profile, listOf(0)).await()).close()

		val stale = adapter.prepare(
			profile = profile,
			pageIndices = listOf(0),
			publicationFence = ReaderPlayLikeCurlRasterPublicationFence { false }
		).await()

		assertNull(stale)
		assertTrue(adapter.hasDecoded(profile, 0))
		assertTrue(released.isEmpty())
		adapter.close()
		assertEquals(listOf("cached-fence-page-0"), released)
	}

	@Test
	fun staleWindowFenceCannotPublishAcrossAbaReturn() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val loader = FakeRasterLoader(gateProfile = "aba", gate = gate)
		val released = mutableListOf<String>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = released::add
		)
		val profile = profile("aba")
		var windowVersion = 1L
		val stale = adapter.prepare(
			profile = profile,
			pageIndices = listOf(4),
			publicationFence = ReaderPlayLikeCurlRasterPublicationFence {
				windowVersion == 1L
			}
		)
		loader.firstStarted.await()

		windowVersion = 2L
		windowVersion = 3L
		gate.complete(Unit)

		assertNull(stale.await())
		assertEquals(listOf("aba-page-4"), released)
		assertEquals(false, adapter.hasDecoded(profile, 4))
		val current = checkNotNull(
			adapter.prepare(
				profile = profile,
				pageIndices = listOf(4),
				publicationFence = ReaderPlayLikeCurlRasterPublicationFence {
					windowVersion == 3L
				}
			).await()
		)
		assertEquals(true, adapter.hasDecoded(profile, 4))
		current.close()
		adapter.close()
	}

	@Test
	fun progressCountsUniqueRasterRequirements() = runBlocking {
		val progress = mutableListOf<ReaderPlayLikeCurlRasterProgress>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = FakeRasterLoader(),
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = {}
		)

		val deck = adapter.prepare(profile("progress"), listOf(0, 0, 1, 2), progress::add).await()

		checkNotNull(deck)
		assertEquals(
			listOf(
				ReaderPlayLikeCurlRasterProgress(completed = 0, total = 3),
				ReaderPlayLikeCurlRasterProgress(completed = 1, total = 3),
				ReaderPlayLikeCurlRasterProgress(completed = 2, total = 3),
				ReaderPlayLikeCurlRasterProgress(completed = 3, total = 3)
			),
			progress
		)
		deck.close()
		adapter.close()
	}

	@Test
	fun overlappingWorkingSetsLoadOnlyTheNewFarEdgeRaster() = runBlocking {
		val loader = FakeRasterLoader()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = {}
		)
		val profile = profile("sliding-window")

		val first = checkNotNull(adapter.prepare(profile, listOf(0, 1, 2, 3, 4)).await())
		val second = checkNotNull(adapter.prepare(profile, listOf(1, 2, 3, 4, 5)).await())

		assertEquals((0..5).toSet(), loader.calls.mapTo(mutableSetOf()) { key -> key.pageIndex })
		assertEquals(6, loader.calls.size)
		first.close()
		second.close()
		adapter.close()
	}

	@Test
	fun unexpectedLoaderFailureCompletesPreparationExceptionallyAfterOwnershipFinalizes() =
		runBlocking {
			val expected = IllegalStateException("decode-failed")
			val adapter = ReaderPlayLikeCurlRasterAdapter(
				scope = scope,
				loader = ReaderPlayLikeCurlRasterLoader<String> { throw expected },
				rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
				release = {}
			)

			val failure = assertFailsWith<IllegalStateException> {
				adapter.prepare(profile("failure"), listOf(0)).await()
			}

			assertEquals(expected.message, failure.message)
			adapter.closeAndJoin()
			assertEquals(0, adapter.metrics().activePreparationWorkers)
			assertEquals(0, adapter.metrics().activeMaterializationWorkers)
			assertEquals(0, adapter.metrics().residentEntries)
			assertEquals(0, adapter.metrics().uniqueDecodedBitmaps)
		}

	@Test
	fun closeRejectsInFlightPreparationAndReleasesItsResult() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val loader = FakeRasterLoader(gateProfile = "closing", gate = gate)
		val released = mutableListOf<String>()
		val staleReleased = CompletableDeferred<String>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = scope,
			loader = loader,
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = { value ->
				released += value
				staleReleased.complete(value)
			}
		)
		val pending = adapter.prepare(profile("closing"), listOf(0))
		loader.firstStarted.await()

		adapter.close()
		gate.complete(Unit)

		assertNull(pending.await())
		assertEquals("closing-page-0", staleReleased.await())
		assertEquals(listOf("closing-page-0"), released)
		assertNull(adapter.prepare(profile("closing"), listOf(0)).await())
	}

	private fun profile(identity: String) = ReaderPlayLikeCurlRasterProfile(
		sourceIdentity = identity,
		orientation = ReaderPlayLikeCurlOrientation.Portrait,
		quality = ReaderPageBitmapQuality.Balanced
	)

	private class FakeRasterLoader(
		private val gateProfile: String? = null,
		private val gate: CompletableDeferred<Unit>? = null
	) : ReaderPlayLikeCurlRasterLoader<String> {
		val calls = mutableListOf<ReaderPlayLikeCurlRasterKey>()
		val firstStarted = CompletableDeferred<Unit>()
		val secondStarted = CompletableDeferred<Unit>()

		override suspend fun load(key: ReaderPlayLikeCurlRasterKey): String {
			val callCount = synchronized(calls) {
				calls += key
				calls.size
			}
			if (!firstStarted.isCompleted) firstStarted.complete(Unit)
			if (callCount >= 2 && !secondStarted.isCompleted) secondStarted.complete(Unit)
			if (key.profile.sourceIdentity == gateProfile) gate?.await()
			return "${key.profile.sourceIdentity}-page-${key.pageIndex}"
		}
	}
}
