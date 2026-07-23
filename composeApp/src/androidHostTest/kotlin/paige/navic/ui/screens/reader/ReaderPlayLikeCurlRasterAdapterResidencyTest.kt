package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageMaximumProtectedRasterEntriesPerLease
import paige.navic.reader.ReaderPageMaximumRasterImagesPerDeck
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TestRendererDeckLeaseLimit = 4

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPlayLikeCurlRasterAdapterResidencyTest {
	private val profile = ReaderPlayLikeCurlRasterProfile(
		sourceIdentity = "residency-fixture",
		orientation = ReaderPlayLikeCurlOrientation.Portrait,
		quality = ReaderPageBitmapQuality.Balanced
	)

	private fun TestScope.intRasterAdapter(
		release: (Int) -> Unit = {},
		residentEntryLimit: Int =
			TestRendererDeckLeaseLimit *
				ReaderPageMaximumProtectedRasterEntriesPerLease
	): ReaderPlayLikeCurlRasterAdapter<Int> = rasterAdapter(
		loader = ReaderPlayLikeCurlRasterLoader { key -> key.pageIndex },
		release = release,
		residentEntryLimit = residentEntryLimit
	)

	private fun <T : Any> TestScope.rasterAdapter(
		loader: ReaderPlayLikeCurlRasterLoader<T>,
		release: (T) -> Unit,
		residentEntryLimit: Int =
			TestRendererDeckLeaseLimit *
				ReaderPageMaximumProtectedRasterEntriesPerLease
	): ReaderPlayLikeCurlRasterAdapter<T> = ReaderPlayLikeCurlRasterAdapter(
		scope = this,
		loader = loader,
		rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
		residentEntryLimit = residentEntryLimit,
		release = release
	)

	@Test
	fun maximumRasterImagesPerDeckMatchesLandscapeDeckContract() {
		assertEquals(6, ReaderPageMaximumRasterImagesPerDeck)
		assertEquals(10, ReaderPageMaximumProtectedRasterEntriesPerLease)
	}

	@Test
	fun fourLandscapeProtectedWindowsFitFourRendererLeases() = runTest {
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key -> key.pageIndex },
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = {}
		)
		val decks = (0 until 4).map { lease ->
			val window = (lease * 10 until lease * 10 + 10).toList()
			assertNotNull(adapter.prepare(profile, window).await())
		}

		assertEquals(40, adapter.metrics().residentEntryLimit)
		assertEquals(40, adapter.metrics().residentEntries)
		decks.forEach { it.close() }
		adapter.closeAndJoin()
		assertEquals(0, adapter.metrics().residentEntries)
	}

	@Test
	fun sharedBudgetBoundsCurrentAndRetiringAdaptersAtForty() = runTest {
		var capacitySignals = 0
		val budget = ReaderPlayLikeCurlRasterResidencyBudget(
			residentEntryLimit = 40,
			onCapacityAvailable = {
				capacitySignals += 1
				true
			}
		)
		fun adapter() = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key -> key.pageIndex },
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			residencyBudget = budget,
			release = {}
		)
		val retiring = adapter()
		val current = adapter()
		val retiringDecks = (0 until 4).map { lease ->
			assertNotNull(
				retiring.prepare(
					profile,
					(lease * 10 until lease * 10 + 10).toList()
				).await()
			)
		}
		assertEquals(40, budget.metrics().residentEntries)

		assertNull(current.prepare(profile, (40 until 50).toList()).await())
		assertEquals(40, budget.metrics().residentEntries)
		assertEquals(0, capacitySignals)

		retiringDecks.forEach { it.close() }
		retiring.closeAndJoin()
		assertEquals(1, capacitySignals)
		val currentDeck = assertNotNull(
			current.prepare(profile, (40 until 50).toList()).await()
		)
		assertEquals(10, budget.metrics().residentEntries)
		assertEquals(40, budget.metrics().peakResidentEntries)

		currentDeck.close()
		current.closeAndJoin()
		assertEquals(0, budget.metrics().residentEntries)
	}

	@Test
	fun saturatedAggregateBudgetAtomicallyReplacesLocalUnpinnedEntry() = runTest {
		var capacitySignals = 0
		val budget = ReaderPlayLikeCurlRasterResidencyBudget(
			residentEntryLimit = 2,
			onCapacityAvailable = {
				capacitySignals += 1
				true
			}
		)
		val requesterReleases = mutableListOf<Int>()
		val blocker = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key -> key.pageIndex },
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			residencyBudget = budget,
			release = { _: Int -> }
		)
		val requester = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key -> key.pageIndex },
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			residencyBudget = budget,
			release = requesterReleases::add
		)
		blocker.prepare(profile, listOf(10)).await()!!.close()
		requester.prepare(profile, listOf(1)).await()!!.close()
		assertEquals(2, budget.metrics().residentEntries)

		val replacement = requester.prepare(profile, listOf(2)).await()

		assertNotNull(replacement)
		assertEquals(listOf(1), requesterReleases)
		assertEquals(2, budget.metrics().residentEntries)
		assertEquals(2, budget.metrics().peakResidentEntries)
		assertEquals(0, capacitySignals)
		replacement.close()
		requester.closeAndJoin()
		blocker.closeAndJoin()
		assertEquals(0, budget.metrics().residentEntries)
		assertEquals(listOf(1, 2), requesterReleases)
	}

	@Test
	fun oneHundredTurnsStayBoundedAndReleaseEveryValueExactlyOnce() = runTest {
		val released = mutableListOf<Int>()
		val adapter = intRasterAdapter(release = released::add)

		repeat(100) { center ->
			val window = (center - 2..center + 2).filter { it >= 0 }
			adapter.prepare(profile, window).await()!!.use {
				val metrics = adapter.metrics()
				assertTrue(metrics.residentEntries <= metrics.residentEntryLimit)
				assertTrue(metrics.uniqueDecodedBitmaps <= metrics.uniqueDecodedBitmapLimit)
				assertTrue(metrics.peakResidentEntries <= metrics.residentEntryLimit)
				assertTrue(metrics.peakUniqueDecodedBitmaps <= metrics.uniqueDecodedBitmapLimit)
			}
		}
		adapter.closeAndJoin()

		assertEquals(released.size, released.distinct().size)
		assertEquals(0, adapter.metrics().residentEntries)
		assertEquals(0, adapter.metrics().activePreparationWorkers)
		assertEquals(0, adapter.metrics().activeMaterializationWorkers)
	}

	@Test
	fun closeWaitsForActiveMaterializationAndReleasesItsLateValueOnce() = runTest {
		val materializationStarted = CompletableDeferred<Unit>()
		val releaseMaterialization = CompletableDeferred<Unit>()
		val released = mutableListOf<Int>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key ->
				materializationStarted.complete(Unit)
				releaseMaterialization.await()
				key.pageIndex
			},
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = released::add
		)
		val preparation = adapter.prepare(profile, listOf(4))
		materializationStarted.await()

		val close = async { adapter.closeAndJoin() }
		runCurrent()
		assertFalse(close.isCompleted)
		assertEquals(1, adapter.metrics().activeMaterializationWorkers)

		releaseMaterialization.complete(Unit)
		assertNull(preparation.await())
		close.await()

		assertEquals(listOf(4), released)
		assertEquals(0, adapter.metrics().residentEntries)
		assertEquals(0, adapter.metrics().activePreparationWorkers)
		assertEquals(0, adapter.metrics().activeMaterializationWorkers)
		assertEquals(0, adapter.metrics().pendingValueReleases)
	}

	@Test
	fun cancellingParentUnblocksSuspendedMaterializerBeforeAdapterJoin() = runTest {
		val parent = SupervisorJob()
		val adapterScope = CoroutineScope(coroutineContext + parent)
		val materializationStarted = CompletableDeferred<Unit>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = adapterScope,
			loader = ReaderPlayLikeCurlRasterLoader {
				materializationStarted.complete(Unit)
				awaitCancellation()
			},
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			release = { _: Int -> error("cancelled loader produced a value") }
		)
		val preparation = adapter.prepare(profile, listOf(4))
		materializationStarted.await()

		adapter.close()
		parent.cancelAndJoin()
		adapter.closeAndJoin()

		assertNull(preparation.await())
		assertEquals(0, adapter.metrics().residentEntries)
		assertEquals(0, adapter.metrics().activePreparationWorkers)
		assertEquals(0, adapter.metrics().activeMaterializationWorkers)
		assertEquals(0, adapter.metrics().pendingValueReleases)
	}

	@Test
	fun capacityRejectionRetriesFromProductionCompletionEvent() = runTest {
		val started = Channel<Int>(Channel.UNLIMITED)
		val retries = Channel<Deferred<ReaderPlayLikeCurlRasterDeck<Int>?>>(Channel.UNLIMITED)
		val gates = (1..3).associateWith { CompletableDeferred<Unit>() }
		lateinit var adapter: ReaderPlayLikeCurlRasterAdapter<Int>
		adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key ->
				started.send(key.pageIndex)
				gates.getValue(key.pageIndex).await()
				key.pageIndex
			},
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			residentEntryLimit = 2,
			onCapacityAvailable = {
				retries.trySend(adapter.prepare(profile, listOf(3))).isSuccess
			},
			release = {}
		)

		val first = adapter.prepare(profile, listOf(1))
		val second = adapter.prepare(profile, listOf(2))
		assertEquals(setOf(1, 2), setOf(started.receive(), started.receive()))
		assertEquals(2, adapter.metrics().activeMaterializationWorkers)

		val rejected = adapter.prepare(profile, listOf(3))
		runCurrent()
		assertNull(rejected.await())
		assertTrue(started.tryReceive().isFailure)
		assertEquals(2, adapter.metrics().activeMaterializationWorkers)

		gates.getValue(1).complete(Unit)
		first.await()!!.close()
		val retried = retries.receive()
		assertEquals(3, started.receive())
		assertEquals(2, adapter.metrics().activeMaterializationWorkers)

		gates.getValue(2).complete(Unit)
		gates.getValue(3).complete(Unit)
		second.await()!!.close()
		retried.await()!!.close()
		adapter.closeAndJoin()

		assertTrue(retries.tryReceive().isFailure)
		assertEquals(0, adapter.metrics().activePreparationWorkers)
		assertEquals(0, adapter.metrics().activeMaterializationWorkers)
		assertEquals(0, adapter.metrics().pendingValueReleases)
		assertEquals(0, adapter.metrics().uniqueDecodedBitmaps)
	}

	@Test
	fun twoKeysSharingOneDecodedIdentityReleaseOnlyAfterBothEntriesDrain() = runTest {
		val shared = Any()
		val releases = mutableListOf<Any>()
		val adapter = rasterAdapter(
			loader = ReaderPlayLikeCurlRasterLoader { shared },
			release = releases::add,
			residentEntryLimit = 2
		)

		adapter.prepare(profile, listOf(1, 2)).await()!!.use { }
		adapter.closeAndJoin()

		assertEquals(listOf(shared), releases)
		assertEquals(0, adapter.metrics().residentEntries)
		assertEquals(0, adapter.metrics().uniqueDecodedBitmaps)
	}

	@Test
	fun saturatedAliasReplacementKeepsOwnerActiveUntilNewEntryDrains() = runTest {
		val shared = Any()
		val releases = mutableListOf<Any>()
		val adapter = rasterAdapter(
			loader = ReaderPlayLikeCurlRasterLoader { shared },
			release = releases::add,
			residentEntryLimit = 1
		)
		adapter.prepare(profile, listOf(1)).await()!!.close()

		val replacement = adapter.prepare(profile, listOf(2)).await()

		assertNotNull(replacement)
		assertTrue(releases.isEmpty())
		assertEquals(1, adapter.metrics().residentEntries)
		assertEquals(1, adapter.metrics().uniqueDecodedBitmaps)
		replacement.close()
		adapter.closeAndJoin()
		assertEquals(listOf(shared), releases)
	}

	@Test
	fun staleLateAliasCannotRecycleIdentityStillOwnedByAnotherKey() = runTest {
		val shared = Any()
		val releaseLate = CompletableDeferred<Unit>()
		val releases = mutableListOf<Any>()
		val adapter = rasterAdapter(
			loader = ReaderPlayLikeCurlRasterLoader { key ->
				if (key.pageIndex == 2) releaseLate.await()
				shared
			},
			release = releases::add,
			residentEntryLimit = 2
		)

		val first = adapter.prepare(profile, listOf(1)).await()!!
		val stale = adapter.prepare(profile, listOf(2))
		runCurrent()
		adapter.close()
		releaseLate.complete(Unit)
		assertNull(stale.await())
		assertTrue(releases.isEmpty())

		first.close()
		adapter.closeAndJoin()
		assertEquals(listOf(shared), releases)
	}

	@Test
	fun identityCannotBeReadoptedWhileItsReleaseCallbackIsPending() = runTest {
		val shared = Any()
		val distinct = Any()
		val releaseEntered = CountDownLatch(1)
		val allowRelease = CountDownLatch(1)
		val releases = mutableListOf<Any>()
		val adapter = rasterAdapter(
			loader = ReaderPlayLikeCurlRasterLoader { key ->
				if (key.pageIndex == 2) distinct else shared
			},
			release = { value ->
				if (value === shared) {
					releaseEntered.countDown()
					check(allowRelease.await(5, TimeUnit.SECONDS))
				}
				synchronized(releases) { releases += value }
			},
			residentEntryLimit = 1
		)
		adapter.prepare(profile, listOf(1)).await()!!.close()

		val eviction = async(Dispatchers.Default) {
			adapter.prepare(profile, listOf(2)).await()
		}
		withContext(Dispatchers.IO) {
			assertTrue(releaseEntered.await(5, TimeUnit.SECONDS))
		}
		val readoption = adapter.prepare(profile, listOf(3))
		runCurrent()

		assertNull(readoption.await())
		assertEquals(2, adapter.metrics().uniqueDecodedBitmaps)
		assertEquals(1, adapter.metrics().activeMaterializationWorkers)
		assertEquals(1, adapter.metrics().pendingValueReleases)
		allowRelease.countDown()
		eviction.await()?.close()
		adapter.closeAndJoin()
		assertEquals(1, synchronized(releases) { releases.count { it === shared } })
		assertEquals(0, adapter.metrics().uniqueDecodedBitmaps)
		assertEquals(0, adapter.metrics().activeMaterializationWorkers)
		assertEquals(0, adapter.metrics().pendingValueReleases)
	}

	@Test
	fun throwingReleaserAttemptsEveryIdentityAndCloseNeverHangs() = runTest {
		val attempts = mutableListOf<Int>()
		val adapter = rasterAdapter(
			loader = ReaderPlayLikeCurlRasterLoader { key -> key.pageIndex },
			release = { value ->
				attempts += value
				error("release-$value")
			},
			residentEntryLimit = 2
		)
		val deck = adapter.prepare(profile, listOf(1, 2)).await()!!

		adapter.close()
		deck.close()
		val first = async { runCatching { adapter.closeAndJoin() } }
		val second = async { runCatching { adapter.closeAndJoin() } }
		runCurrent()

		assertTrue(first.isCompleted)
		assertTrue(second.isCompleted)
		val firstFailure = first.await().exceptionOrNull()
		val secondFailure = second.await().exceptionOrNull()
		assertNotNull(firstFailure)
		assertNotNull(secondFailure)
		assertEquals(firstFailure::class, secondFailure::class)
		assertEquals(firstFailure.suppressed.size, secondFailure.suppressed.size)
		assertEquals(setOf(1, 2), attempts.toSet())
		assertEquals(2, attempts.size)
		assertEquals(0, adapter.metrics().residentEntries)
		assertEquals(0, adapter.metrics().uniqueDecodedBitmaps)
	}

	@Test
	fun activeDeckPinPreventsEvictionUntilDeckClose() = runTest {
		val released = mutableListOf<Int>()
		val signals = AtomicInteger()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key -> key.pageIndex },
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			residentEntryLimit = 1,
			onCapacityAvailable = {
				signals.incrementAndGet()
				true
			},
			release = released::add
		)
		val pinned = adapter.prepare(profile, listOf(1)).await()!!

		assertNull(adapter.prepare(profile, listOf(2)).await())
		assertEquals(1, adapter.metrics().pinnedEntries)
		assertEquals(listOf(2), released)
		assertEquals(0, signals.get())

		val close = async { adapter.closeAndJoin() }
		runCurrent()
		assertFalse(close.isCompleted)
		pinned.close()
		close.await()
		assertEquals(listOf(2, 1), released)
	}

	@Test
	fun preparationWorkerReturnRetriesRejectedPreparation() = runTest {
		val gate = CompletableDeferred<Unit>()
		val signals = AtomicInteger()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key ->
				if (key.pageIndex == 1) gate.await()
				key.pageIndex
			},
			rendererDeckLeaseLimit = 1,
			onCapacityAvailable = {
				signals.incrementAndGet()
				true
			},
			release = {}
		)
		val first = adapter.prepare(profile, listOf(1))
		runCurrent()

		assertNull(adapter.prepare(profile, listOf(2)).await())
		assertEquals(0, signals.get())
		gate.complete(Unit)
		first.await()!!.close()
		runCurrent()
		assertEquals(1, signals.get())
		adapter.closeAndJoin()
	}

	@Test
	fun unusedMaterializationReservationRetriesRejectedIdentity() = runTest {
		val gate = CompletableDeferred<Unit>()
		val started = CompletableDeferred<Unit>()
		val signals = AtomicInteger()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key ->
				if (key.pageIndex == 1) {
					started.complete(Unit)
					gate.await()
					null
				} else {
					key.pageIndex
				}
			},
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			residentEntryLimit = 1,
			onCapacityAvailable = {
				signals.incrementAndGet()
				true
			},
			release = {}
		)
		val first = adapter.prepare(profile, listOf(1))
		started.await()

		assertNull(adapter.prepare(profile, listOf(2)).await())
		assertEquals(0, signals.get())
		gate.complete(Unit)
		assertNull(first.await())
		runCurrent()
		assertEquals(1, signals.get())
		adapter.closeAndJoin()
	}

	@Test
	fun releaseCallbackReturnRetriesBlockedIdentity() = runTest {
		val releaseEntered = CountDownLatch(1)
		val allowRelease = CountDownLatch(1)
		val signals = AtomicInteger()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key -> key.pageIndex },
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			residentEntryLimit = 1,
			onCapacityAvailable = {
				signals.incrementAndGet()
				true
			},
			release = { value ->
				if (value == 1) {
					releaseEntered.countDown()
					check(allowRelease.await(5, TimeUnit.SECONDS))
				}
			}
		)
		adapter.prepare(profile, listOf(1)).await()!!.close()
		val replacement = async(Dispatchers.Default) {
			adapter.prepare(profile, listOf(2)).await()
		}
		withContext(Dispatchers.IO) {
			assertTrue(releaseEntered.await(5, TimeUnit.SECONDS))
		}

		assertNull(adapter.prepare(profile, listOf(3)).await())
		assertEquals(0, signals.get())
		allowRelease.countDown()
		replacement.await()?.close()
		runCurrent()
		assertEquals(1, signals.get())
		adapter.closeAndJoin()
	}

	@Test
	fun deckUnpinRetriesBlockedResidentSlot() = runTest {
		val signals = AtomicInteger()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key -> key.pageIndex },
			rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
			residentEntryLimit = 1,
			onCapacityAvailable = {
				signals.incrementAndGet()
				true
			},
			release = {}
		)
		val pinned = adapter.prepare(profile, listOf(1)).await()!!

		assertNull(adapter.prepare(profile, listOf(2)).await())
		assertEquals(0, signals.get())
		pinned.close()
		assertEquals(1, signals.get())
		adapter.closeAndJoin()
	}

	@Test
	fun retiringAdapterDrainRetriesLatestProfileOnly() = runTest {
		val retries = Channel<Deferred<ReaderPlayLikeCurlRasterDeck<Int>?>>(Channel.UNLIMITED)
		val latestProfile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = "latest",
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			quality = ReaderPageBitmapQuality.Balanced
		)
		lateinit var current: ReaderPlayLikeCurlRasterAdapter<Int>
		val budget = ReaderPlayLikeCurlRasterResidencyBudget(
			residentEntryLimit = 1,
			onCapacityAvailable = {
				retries.trySend(current.prepare(latestProfile, listOf(3))).isSuccess
			}
		)
		fun adapter(loader: ReaderPlayLikeCurlRasterLoader<Int>) =
			ReaderPlayLikeCurlRasterAdapter(
				scope = this,
				loader = loader,
				rendererDeckLeaseLimit = TestRendererDeckLeaseLimit,
				residentEntryLimit = 1,
				residencyBudget = budget,
				release = {}
			)
		val retiring = adapter(ReaderPlayLikeCurlRasterLoader { key -> key.pageIndex })
		val calls = mutableListOf<Int>()
		current = adapter(ReaderPlayLikeCurlRasterLoader { key ->
			calls += key.pageIndex
			key.pageIndex
		})
		val oldDeck = retiring.prepare(profile, listOf(1)).await()!!

		assertNull(current.prepare(profile, listOf(2)).await())
		assertNull(current.prepare(latestProfile, listOf(3)).await())
		oldDeck.close()
		retiring.closeAndJoin()
		val retried = retries.receive()
		retried.await()!!.close()
		assertEquals(listOf(2, 3, 3), calls)
		assertTrue(retries.tryReceive().isFailure)
		current.closeAndJoin()
	}

	@Test
	fun coalescedCapacityEdgesPostOneRefreshWithoutLosingWork() = runTest {
		val acceptedSignals = AtomicInteger()
		val callbackAttempts = AtomicInteger()
		val gate = CompletableDeferred<Unit>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key ->
				if (key.pageIndex == 1) gate.await()
				key.pageIndex
			},
			rendererDeckLeaseLimit = 1,
			onCapacityAvailable = {
				val attempt = callbackAttempts.incrementAndGet()
				if (attempt == 1) {
					false
				} else {
					acceptedSignals.incrementAndGet()
					true
				}
			},
			release = {}
		)
		val first = adapter.prepare(profile, listOf(1))
		runCurrent()
		assertNull(adapter.prepare(profile, listOf(2)).await())
		gate.complete(Unit)
		first.await()!!.close()
		runCurrent()
		assertEquals(2, callbackAttempts.get())
		assertEquals(1, acceptedSignals.get())
		adapter.closeAndJoin()
	}

	@Test
	fun rejectedCapacityDispatchRearmsExactReasons() = runTest {
		val attempts = AtomicInteger()
		val firstGate = CompletableDeferred<Unit>()
		val adapter = ReaderPlayLikeCurlRasterAdapter(
			scope = this,
			loader = ReaderPlayLikeCurlRasterLoader { key ->
				if (key.pageIndex == 1) firstGate.await()
				key.pageIndex
			},
			rendererDeckLeaseLimit = 1,
			onCapacityAvailable = { attempts.incrementAndGet() > 1 },
			release = {}
		)
		val first = adapter.prepare(profile, listOf(1))
		runCurrent()
		assertNull(adapter.prepare(profile, listOf(2)).await())
		firstGate.complete(Unit)
		first.await()!!.close()
		runCurrent()
		assertEquals(2, attempts.get())
		adapter.closeAndJoin()
	}
}
