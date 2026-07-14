package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import paige.navic.reader.ReaderPageBitmapQuality
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ReaderPlayLikeCurlRasterAdapterTest {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	@AfterTest
	fun tearDown() {
		scope.cancel()
	}

	@Test
	fun duplicatePageIdentitiesLoadOnceAndMapToOneDeckEntry() = runBlocking {
		val loader = FakeRasterLoader()
		val adapter = ReaderPlayLikeCurlRasterAdapter(scope, loader, release = {})
		val profile = profile("reference")

		val deck = adapter.prepare(profile, listOf(0, 0, 1)).await()

		checkNotNull(deck)
		assertEquals(listOf(0, 1), loader.calls.map { key -> key.pageIndex })
		assertEquals("reference-page-0", deck.value(0))
		assertEquals("reference-page-1", deck.value(1))
		assertSame(deck.value(0), deck.value(0))
		deck.close()
		adapter.close()
	}

	@Test
	fun concurrentPreparationsShareOneInFlightRaster() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val loader = FakeRasterLoader(gateProfile = "shared", gate = gate)
		val released = mutableListOf<String>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(scope, loader, released::add)
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
		val adapter = ReaderPlayLikeCurlRasterAdapter(scope, loader) { value ->
			released += value
			if (value.startsWith("old-")) staleReleased.complete(value)
		}

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
	fun progressCountsUniqueRasterRequirements() = runBlocking {
		val progress = mutableListOf<ReaderPlayLikeCurlRasterProgress>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(scope, FakeRasterLoader(), release = {})

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
	fun closeRejectsInFlightPreparationAndReleasesItsResult() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val loader = FakeRasterLoader(gateProfile = "closing", gate = gate)
		val released = mutableListOf<String>()
		val staleReleased = CompletableDeferred<String>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(scope, loader) { value ->
			released += value
			staleReleased.complete(value)
		}
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

		override suspend fun load(key: ReaderPlayLikeCurlRasterKey): String {
			synchronized(calls) { calls += key }
			if (!firstStarted.isCompleted) firstStarted.complete(Unit)
			if (key.profile.sourceIdentity == gateProfile) gate?.await()
			return "${key.profile.sourceIdentity}-page-${key.pageIndex}"
		}
	}
}
