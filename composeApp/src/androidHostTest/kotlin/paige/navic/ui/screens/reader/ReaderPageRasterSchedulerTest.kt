package paige.navic.ui.screens.reader

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.readerAndroidFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderPageRasterSchedulerTest {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	@AfterTest
	fun tearDown() {
		scope.cancel()
	}

	@Test
	fun duplicateRequestsShareOneGeneration() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val store = FakeRasterStore()
		val generator = FakeRasterGenerator(gate = gate)
		val profile = rasterProfile("active")
		val scheduler = ReaderPageRasterScheduler(
			scope = scope,
			store = store,
			generator = generator,
			release = { }
		)
		scheduler.activateProfile(profile)
		val key = rasterKey(profile, page = 3)

		val first = scheduler.request(key, ReaderPageRasterPriority.Current)
		val second = scheduler.request(key, ReaderPageRasterPriority.NextTransition)

		assertSame(first, second)
		gate.complete(Unit)
		assertEquals(ReaderPageRasterScheduleStatus.Published, first.await().status)
		assertEquals(listOf(key), generator.calls)
	}

	@Test
	fun queuedRequestsRunInPriorityOrder() = runBlocking {
		val activeGate = CompletableDeferred<Unit>()
		val store = FakeRasterStore()
		val generator = FakeRasterGenerator(firstGate = activeGate)
		val profile = rasterProfile("priority")
		val scheduler = ReaderPageRasterScheduler(
			scope = scope,
			store = store,
			generator = generator,
			release = { }
		)
		scheduler.activateProfile(profile)
		val current = rasterKey(profile, page = 0)
		val chapter = rasterKey(profile, page = 7)
		val next = rasterKey(profile, page = 1)

		val currentResult = scheduler.request(current, ReaderPageRasterPriority.Current)
		generator.firstStarted.await()
		val chapterResult = scheduler.request(chapter, ReaderPageRasterPriority.CurrentChapter)
		val nextResult = scheduler.request(next, ReaderPageRasterPriority.NextTransition)
		activeGate.complete(Unit)

		currentResult.await()
		nextResult.await()
		chapterResult.await()
		assertEquals(listOf(current, next, chapter), generator.calls)
	}

	@Test
	fun obsoleteProfileCannotPublish() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val released = mutableListOf<String>()
		val store = FakeRasterStore()
		val generator = FakeRasterGenerator(gate = gate)
		val oldProfile = rasterProfile("old")
		val scheduler = ReaderPageRasterScheduler(scope, store, generator, released::add)
		scheduler.activateProfile(oldProfile)
		val oldKey = rasterKey(oldProfile, page = 2)
		val pending = scheduler.request(oldKey, ReaderPageRasterPriority.Current)
		generator.firstStarted.await()

		scheduler.activateProfile(rasterProfile("new"))
		gate.complete(Unit)

		assertEquals(ReaderPageRasterScheduleStatus.Stale, pending.await().status)
		assertNull(store.read(oldKey))
		assertEquals(listOf("page-2"), released)
	}

	@Test
	fun callerOwnedWriteResultIsReleasedAfterPublication() = runBlocking {
		val released = mutableListOf<String>()
		val store = FakeRasterStore(
			writeOwnership = ReaderPageRasterValueOwnership.Caller
		)
		val profile = rasterProfile("caller-owned")
		val scheduler = ReaderPageRasterScheduler(
			scope,
			store,
			FakeRasterGenerator(),
			released::add
		)
		scheduler.activateProfile(profile)
		val key = rasterKey(profile, page = 3)

		val result = scheduler.request(
			key,
			ReaderPageRasterPriority.Current
		).await()

		assertEquals(ReaderPageRasterScheduleStatus.Published, result.status)
		assertEquals(listOf("page-3"), released)
	}

	@Test
	fun cancellationAfterStoreAdoptionPreservesStoreOwnershipResult() =
		runBlocking {
			val workerDispatcher =
				Executors.newSingleThreadExecutor().asCoroutineDispatcher()
			val localScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
			try {
				val released = mutableListOf<String>()
				val store = FakeRasterStore()
				val profile = rasterProfile("cancelled-return")
				val scheduler = ReaderPageRasterScheduler(
					scope = localScope,
					store = store,
					generator = FakeRasterGenerator(),
					release = released::add,
					ioDispatcher = workerDispatcher
				)
				scheduler.activateProfile(profile)
				val key = rasterKey(profile, page = 6)
				store.afterWrite = { localScope.cancel() }

				val result = scheduler.request(
					key,
					ReaderPageRasterPriority.Current
				).await()

				assertEquals(
					ReaderPageRasterScheduleStatus.Published,
					result.status
				)
				assertEquals("page-6", store.read(key)?.value)
				assertTrue(released.isEmpty())
			} finally {
				localScope.cancel()
				workerDispatcher.close()
			}
		}

	@Test
	fun rollbackFailureIsRetainedWithoutStoppingTheDrain() = runBlocking {
		val store = FakeRasterStore()
		val oldProfile = rasterProfile("rollback-failure")
		val currentProfile = rasterProfile("after-rollback-failure")
		val scheduler = ReaderPageRasterScheduler(
			scope,
			store,
			FakeRasterGenerator(),
			release = { }
		)
		scheduler.activateProfile(oldProfile)
		store.rollbackFailure = IllegalStateException("manifest-failed")
		store.afterWrite = { scheduler.activateProfile(currentProfile) }
		val staleKey = rasterKey(oldProfile, page = 7)

		val stale = scheduler.request(
			staleKey,
			ReaderPageRasterPriority.Current
		).await()

		assertEquals(ReaderPageRasterScheduleStatus.Failed, stale.status)
		assertEquals("manifest-failed", scheduler.dispatchFailure()?.message)
		store.rollbackFailure = null
		store.afterWrite = {}
		val currentKey = rasterKey(currentProfile, page = 8)
		val current = scheduler.request(
			currentKey,
			ReaderPageRasterPriority.Current
		).await()
		assertEquals(ReaderPageRasterScheduleStatus.Published, current.status)
	}

	@Test
	fun staleWriteRollsBackOnlyItsExactReceipt() = runBlocking {
		val store = FakeRasterStore()
		val oldProfile = rasterProfile("stale-receipt")
		val scheduler = ReaderPageRasterScheduler(
			scope,
			store,
			FakeRasterGenerator(),
			release = { }
		)
		val key = rasterKey(oldProfile, page = 4)
		scheduler.activateProfile(oldProfile)
		store.afterWrite = {
			scheduler.activateProfile(rasterProfile("replacement"))
		}

		val result = scheduler.request(
			key,
			ReaderPageRasterPriority.Current
		).await()

		assertEquals(ReaderPageRasterScheduleStatus.Stale, result.status)
		assertNull(store.read(key))
	}

	@Test
	fun newerSameKeyWriteSurvivesStaleReceiptRollback() = runBlocking {
		val store = FakeRasterStore(retainProfiles = false)
		val oldProfile = rasterProfile("aba")
		val scheduler = ReaderPageRasterScheduler(
			scope,
			store,
			FakeRasterGenerator(),
			release = { }
		)
		val key = rasterKey(oldProfile, page = 5)
		scheduler.activateProfile(oldProfile)
		store.afterWrite = { writtenKey ->
			store.overwrite(writtenKey, "newer")
			scheduler.activateProfile(rasterProfile("replacement"))
		}

		val result = scheduler.request(
			key,
			ReaderPageRasterPriority.Current
		).await()

		assertEquals(ReaderPageRasterScheduleStatus.Stale, result.status)
		assertEquals("newer", store.read(key)?.value)
	}

	@Test
	fun cachedRasterProbeDoesNotMaterializeOrReleaseTheCacheOwnedValue() = runBlocking {
		val released = mutableListOf<String>()
		val store = FakeRasterStore()
		val generator = FakeRasterGenerator()
		val profile = rasterProfile("cached-release")
		val key = rasterKey(profile, page = 4)
		store.write(key, testRasterMetadata(), "cached-page")
		val scheduler = ReaderPageRasterScheduler(scope, store, generator, released::add)
		scheduler.activateProfile(profile)

		val result = scheduler.request(key, ReaderPageRasterPriority.Current).await()

		assertEquals(ReaderPageRasterScheduleStatus.Cached, result.status)
		assertTrue(store.readCalls.isEmpty())
		assertTrue(released.isEmpty())
		assertTrue(generator.calls.isEmpty())
	}

	@Test
	fun closeRejectsQueuedAndInFlightWorkWithoutPublishing() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val released = mutableListOf<String>()
		val store = FakeRasterStore()
		val generator = FakeRasterGenerator(gate = gate)
		val profile = rasterProfile("closing")
		val scheduler = ReaderPageRasterScheduler(scope, store, generator, released::add)
		scheduler.activateProfile(profile)
		val inFlightKey = rasterKey(profile, page = 0)
		val queuedKey = rasterKey(profile, page = 1)
		val rejectedKey = rasterKey(profile, page = 2)
		val inFlight = scheduler.request(inFlightKey, ReaderPageRasterPriority.Current)
		generator.firstStarted.await()
		val queued = scheduler.request(queuedKey, ReaderPageRasterPriority.NextTransition)

		scheduler.close()

		assertEquals(ReaderPageRasterScheduleStatus.Stale, queued.await().status)
		assertEquals(
			ReaderPageRasterScheduleStatus.Stale,
			scheduler.request(rejectedKey, ReaderPageRasterPriority.Current).await().status
		)
		gate.complete(Unit)
		assertEquals(ReaderPageRasterScheduleStatus.Stale, inFlight.await().status)
		assertNull(store.read(inFlightKey))
		assertNull(store.read(queuedKey))
		assertEquals(listOf("page-0"), released)
	}

	@Test
	fun closeAndJoinWaitsForActiveWorkerBeforeLaterOwnersClose() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val store = FakeRasterStore()
		val generator = FakeRasterGenerator(gate = gate)
		val profile = rasterProfile("ordered-close")
		val scheduler = ReaderPageRasterScheduler(
			scope = scope,
			store = store,
			generator = generator,
			release = {}
		)
		scheduler.activateProfile(profile)
		val active = scheduler.request(
			rasterKey(profile, page = 1),
			ReaderPageRasterPriority.Current
		)
		generator.firstStarted.await()
		val queued = scheduler.request(
			rasterKey(profile, page = 2),
			ReaderPageRasterPriority.NextTransition
		)
		val closeEvents = mutableListOf<String>()

		val close = async {
			scheduler.closeAndJoin()
			closeEvents += "generation-worker"
			closeEvents += "persistent-store"
			closeEvents += "decoded-cache"
		}
		yield()

		assertFalse(close.isCompleted)
		assertTrue(closeEvents.isEmpty())
		assertEquals(ReaderPageRasterScheduleStatus.Stale, queued.await().status)
		gate.complete(Unit)
		assertEquals(ReaderPageRasterScheduleStatus.Stale, active.await().status)
		close.await()
		assertEquals(
			listOf("generation-worker", "persistent-store", "decoded-cache"),
			closeEvents
		)
	}

	@Test
	fun rollbackAndReleaseFailuresAreAggregatedWithoutStoppingDrain() = runBlocking {
		val writeEntered = CompletableDeferred<Unit>()
		val allowWrite = CountDownLatch(1)
		val rollbackFailure = IllegalStateException("rollback-failed")
		val releaseFailure = IllegalArgumentException("release-failed")
		val releases = mutableListOf<String>()
		val store = FakeRasterStore(
			writeOwnership = ReaderPageRasterValueOwnership.Caller
		)
		val oldProfile = rasterProfile("aggregate-old")
		val currentProfile = rasterProfile("aggregate-current")
		val oldKey = rasterKey(oldProfile, page = 7)
		val currentKey = rasterKey(currentProfile, page = 8)
		store.beforeWrite = { key ->
			if (key == oldKey) {
				writeEntered.complete(Unit)
				check(allowWrite.await(5, TimeUnit.SECONDS))
			}
		}
		store.rollbackFailure = rollbackFailure
		val scheduler = ReaderPageRasterScheduler(
			scope = scope,
			store = store,
			generator = FakeRasterGenerator(),
			release = { value ->
				releases += value
				if (value == "page-7") throw releaseFailure
			}
		)
		scheduler.activateProfile(oldProfile)
		val stale = scheduler.request(oldKey, ReaderPageRasterPriority.Current)
		withTimeout(5_000) { writeEntered.await() }
		scheduler.activateProfile(currentProfile)
		val current = scheduler.request(
			currentKey,
			ReaderPageRasterPriority.Current
		)
		allowWrite.countDown()

		assertEquals(ReaderPageRasterScheduleStatus.Failed, stale.await().status)
		assertEquals(ReaderPageRasterScheduleStatus.Published, current.await().status)
		assertEquals(listOf("page-7", "page-8"), releases)
		val firstClose = assertFailsWith<IllegalStateException> {
			scheduler.closeAndJoin()
		}
		val secondClose = assertFailsWith<IllegalStateException> {
			scheduler.closeAndJoin()
		}
		assertEquals(rollbackFailure.message, firstClose.message)
		assertSame(firstClose, secondClose)
		assertEquals(listOf(releaseFailure), firstClose.suppressed.toList())
		assertEquals(0, pendingRequestCount(scheduler))
	}

	@Test
	fun parentCancellationCompletesCurrentAndQueuedWaitersAsStale() = runBlocking {
		val parent = SupervisorJob()
		val localScope = CoroutineScope(parent + Dispatchers.Default)
		val gate = CompletableDeferred<Unit>()
		val generator = FakeRasterGenerator(gate = gate)
		val profile = rasterProfile("parent-cancel")
		val scheduler = ReaderPageRasterScheduler(
			scope = localScope,
			store = FakeRasterStore(),
			generator = generator,
			release = {}
		)
		scheduler.activateProfile(profile)
		val current = scheduler.request(
			rasterKey(profile, page = 1),
			ReaderPageRasterPriority.Current
		)
		generator.firstStarted.await()
		val queued = scheduler.request(
			rasterKey(profile, page = 2),
			ReaderPageRasterPriority.NextTransition
		)

		parent.cancel()

		withTimeout(5_000) {
			assertEquals(ReaderPageRasterScheduleStatus.Stale, current.await().status)
			assertEquals(ReaderPageRasterScheduleStatus.Stale, queued.await().status)
			val afterWorkerExit = scheduler.request(
				rasterKey(profile, page = 3),
				ReaderPageRasterPriority.Current
			)
			assertEquals(
				ReaderPageRasterScheduleStatus.Stale,
				afterWorkerExit.await().status
			)
			scheduler.closeAndJoin()
		}
		assertEquals(0, pendingRequestCount(scheduler))
	}

	@Test
	fun earlyStoreCloseFailureDoesNotPoisonLaterOrderedClose() {
		val encodeEntered = CountDownLatch(1)
		val allowEncode = CountDownLatch(1)
		val cache = ReaderPageRasterCache(
			root = createTempDirectory("navic-active-raster-store").toFile(),
			codec = object : ReaderPageRasterCodec<String> {
				override fun encode(value: String, target: File): Boolean {
					encodeEntered.countDown()
					check(allowEncode.await(5, TimeUnit.SECONDS))
					target.writeText(value)
					return true
				}

				override fun decode(source: File): String? = source.readText()

				override fun release(value: String) = Unit
			},
			maxDecodedEntries = 1
		)
		val store = ReaderPageRasterCacheStore(cache)
		val key = rasterKey(rasterProfile("active-store"), page = 1)
		var write: ReaderPageRasterWriteResult? = null
		val writer = thread(start = true) {
			write = store.write(key, testRasterMetadata(), "value")
		}
		assertTrue(encodeEntered.await(5, TimeUnit.SECONDS))

		assertFailsWith<IllegalStateException> { store.close() }
		assertFalse(store.contains(key))
		allowEncode.countDown()
		writer.join(5_000)
		assertFalse(writer.isAlive)
		assertTrue(checkNotNull(write).persisted)

		store.close()
		store.close()
		val beforeClosedCall = cache.metrics()
		assertFalse(store.write(key, testRasterMetadata(), "later").persisted)
		assertEquals(beforeClosedCall, cache.metrics())
		cache.close()
	}

	@Test
	fun closedCacheStoreRejectsEveryOperationWithoutMutatingCache() {
		val cache = ReaderPageRasterCache(
			root = createTempDirectory("navic-closed-raster-store").toFile(),
			codec = StringRasterCodec(),
			maxDecodedEntries = 1
		)
		val store = ReaderPageRasterCacheStore(cache)
		val key = rasterKey(rasterProfile("closed-store"), page = 1)
		store.close()
		store.close()

		val write = store.write(key, testRasterMetadata(), "value")

		assertFalse(write.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Caller, write.ownership)
		assertFalse(store.contains(key))
		assertNull(store.readCopy(key) { it })
		assertFalse(store.remove(key))
		assertFalse(
			store.rollbackPublication(
				ReaderPageRasterWriteReceipt(key, "unused.png", 1L)
			)
		)
		val metrics = cache.metrics()
		assertEquals(0, metrics.diskEntries)
		assertEquals(0L, metrics.diskBytes)
		assertEquals(0, metrics.decodedEntries)
		assertEquals(0, metrics.uniqueDecodedBitmaps)
		assertEquals(0, metrics.activeEncodePins)
	}

	@Test
	fun frozenSchedulerInventoriesQueuedAndActiveWorkAndRestoresRestartDescriptors() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val generator = FakeRasterGenerator(firstGate = gate)
		val profile = rasterProfile("activation")
		val scheduler = ReaderPageRasterScheduler(
			scope = scope,
			store = FakeRasterStore(),
			generator = generator,
			release = { }
		)
		scheduler.activateProfile(profile)
		val activeKey = rasterKey(profile, page = 1)
		val queuedKey = rasterKey(profile, page = 2)
		val active = scheduler.request(activeKey, ReaderPageRasterPriority.Current)
		generator.firstStarted.await()
		val queued = scheduler.request(queuedKey, ReaderPageRasterPriority.NextTransition)
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 61L,
			freezeToken = ReaderLegacyFreezeToken(62L)
		)

		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.freezeForTransitionActivation(domain)
		)
		val first = scheduler.snapshotFrozenOwnership()
		val second = scheduler.snapshotFrozenOwnership()
		assertEquals(first, second)
		assertEquals(2, first.size)
		assertEquals(1, first.count { it.state == ReaderLegacyResourceState.Running })
		assertEquals(1, first.count { it.state == ReaderLegacyResourceState.Reserved })
		assertTrue(first.all { row ->
			row.physicalIdentity.domain == domain &&
				row.physicalIdentity.source ==
				ReaderLegacyInventorySource.RasterGenerationAndPersistence &&
				row.kind == ReaderTransitionResourceKind.Raster
		})
		val fenced = scheduler.request(
			rasterKey(profile, page = 3),
			ReaderPageRasterPriority.Current
		)
		assertEquals(ReaderPageRasterScheduleStatus.Stale, fenced.await().status)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		first.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				scheduler.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		assertEquals(ReaderPageRasterScheduleStatus.Stale, queued.await().status)
		assertEquals(1, confirmations.size)
		gate.complete(Unit)
		assertEquals(ReaderPageRasterScheduleStatus.Stale, active.await().status)
		withTimeout(2_000L) {
			while (confirmations.size != 2) yield()
		}
		assertEquals(first.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertTrue(scheduler.snapshotFrozenOwnership().isEmpty())

		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.restoreAfterTransitionActivation(domain)
		)
		withTimeout(2_000L) {
			while (generator.calls.count { it == activeKey } < 2 ||
				generator.calls.count { it == queuedKey } < 1
			) yield()
		}
		scheduler.closeAndJoin()
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun naturallyCompletedFrozenWorkRemainsDrainableWithoutDuplicatePhysicalRestart() = runTest {
		val generationGate = CompletableDeferred<Unit>()
		val started = CompletableDeferred<Unit>()
		val calls = mutableListOf<ReaderPageRasterKey>()
		val profile = rasterProfile("natural-freeze")
		val scheduler = ReaderPageRasterScheduler(
			scope = backgroundScope,
			store = FakeRasterStore(),
			generator = ReaderPageRasterGenerator { key ->
				calls += key
				if (calls.size == 1) {
					started.complete(Unit)
					generationGate.await()
				}
				ReaderPageRasterGeneration(
					metadata = testRasterMetadata(),
					value = "page-${key.visualPageOrdinal}",
					captureMillis = 1L
				)
			},
			release = { },
			ioDispatcher = StandardTestDispatcher(testScheduler)
		)
		scheduler.activateProfile(profile)
		val key = rasterKey(profile, page = 6)
		val initial = scheduler.request(key, ReaderPageRasterPriority.Current)
		started.await()
		val domain = ReaderLegacyPhysicalDomain(63L, ReaderLegacyFreezeToken(64L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.freezeForTransitionActivation(domain)
		)

		generationGate.complete(Unit)
		runCurrent()
		assertEquals(ReaderPageRasterScheduleStatus.Stale, initial.await().status)
		val frozen = scheduler.snapshotFrozenOwnership()
		val row = frozen.single()
		assertEquals(ReaderLegacyResourceState.Released, row.state)
		assertEquals(
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			),
			scheduler.drainFrozenOwnership(
				row.physicalIdentity.copy(
					domain = ReaderLegacyPhysicalDomain(65L, ReaderLegacyFreezeToken(66L))
				)
			) { }
		)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
		)
		assertEquals(listOf(row.physicalIdentity), confirmations)
		assertTrue(scheduler.snapshotFrozenOwnership().isEmpty())

		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.restoreAfterTransitionActivation(domain)
		)
		val restored = scheduler.request(key, ReaderPageRasterPriority.Current).await()
		assertTrue(restored.status == ReaderPageRasterScheduleStatus.Published || restored.status == ReaderPageRasterScheduleStatus.Cached)
		scheduler.closeAndJoin()
		assertEquals(listOf(key, key), calls)
	}

	@Test
	fun frozenCloseRetainsSchedulerOwnersForDrainWithoutRestoringWork() = runBlocking {
		val generationGate = CompletableDeferred<Unit>()
		val started = CompletableDeferred<Unit>()
		val profile = rasterProfile("frozen-close")
		val scheduler = ReaderPageRasterScheduler(
			scope = scope,
			store = FakeRasterStore(),
			generator = ReaderPageRasterGenerator { key ->
				started.complete(Unit)
				generationGate.await()
				ReaderPageRasterGeneration(
					metadata = testRasterMetadata(),
					value = "page-${key.visualPageOrdinal}",
					captureMillis = 1L
				)
			},
			release = { }
		)
		scheduler.activateProfile(profile)
		val active = scheduler.request(
			rasterKey(profile, page = 7),
			ReaderPageRasterPriority.Current
		)
		started.await()
		val queued = scheduler.request(
			rasterKey(profile, page = 8),
			ReaderPageRasterPriority.NextTransition
		)
		val domain = ReaderLegacyPhysicalDomain(67L, ReaderLegacyFreezeToken(68L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.freezeForTransitionActivation(domain)
		)
		val frozen = scheduler.snapshotFrozenOwnership()
		assertEquals(2, frozen.size)

		scheduler.close()
		assertEquals(ReaderPageRasterScheduleStatus.Stale, queued.await().status)
		generationGate.complete(Unit)
		assertEquals(ReaderPageRasterScheduleStatus.Stale, active.await().status)
		scheduler.closeAndJoin()

		val terminal = scheduler.snapshotFrozenOwnership()
		assertEquals(
			frozen.map { it.physicalIdentity }.toSet(),
			terminal.map { it.physicalIdentity }.toSet()
		)
		assertTrue(terminal.all { it.state == ReaderLegacyResourceState.Released })
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		terminal.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				scheduler.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		assertEquals(terminal.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertEquals(
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			),
			scheduler.restoreAfterTransitionActivation(domain)
		)
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun activationBoundaryQueuedGenerationWaitsForRestoreAndCannotPublishWhileFrozen() = runTest {
		val gate = CompletableDeferred<Unit>()
		val generator = FakeRasterGenerator(firstGate = gate)
		val store = FakeRasterStore()
		val profile = rasterProfile("boundary-queue")
		val scheduler = ReaderPageRasterScheduler(backgroundScope, store, generator, {}, StandardTestDispatcher(testScheduler))
		scheduler.activateProfile(profile)
		val firstKey = rasterKey(profile, 1)
		val secondKey = rasterKey(profile, 2)
		val first = scheduler.request(firstKey, ReaderPageRasterPriority.Current)
		runCurrent()
		val second = scheduler.request(secondKey, ReaderPageRasterPriority.NextTransition)
		val domain = ReaderLegacyPhysicalDomain(101L, ReaderLegacyFreezeToken(102L))
		scheduler.freezeForTransitionActivation(domain)
		gate.complete(Unit)
		runCurrent()
		assertEquals(listOf(firstKey), generator.calls, "Queued generation started after freeze")
		assertEquals(ReaderPageRasterScheduleStatus.Stale, first.await().status)
		assertFalse(second.isCompleted)
		assertNull(store.read(firstKey))
		val rows = scheduler.snapshotFrozenOwnership()
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { scheduler.drainFrozenOwnership(it.physicalIdentity, confirmations::add) }
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertEquals(ReaderPageRasterScheduleStatus.Stale, second.await().status)
		assertEquals(ReaderPortCommandResult.Accepted, scheduler.restoreAfterTransitionActivation(domain))
		runCurrent()
		assertEquals(listOf(firstKey, firstKey, secondKey), generator.calls)
		assertNotNull(store.read(firstKey))
		assertNotNull(store.read(secondKey))
		scheduler.closeAndJoin()
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun activationBoundaryNaturalResultDrainWaitsForReentrantThrowingCallbackTail() = runTest {
		val gate = CompletableDeferred<Unit>()
		val generator = FakeRasterGenerator(firstGate = gate)
		val profile = rasterProfile("boundary-tail")
		val scheduler = ReaderPageRasterScheduler(backgroundScope, FakeRasterStore(), generator, {}, StandardTestDispatcher(testScheduler))
		scheduler.activateProfile(profile)
		val result = scheduler.request(rasterKey(profile, 1), ReaderPageRasterPriority.Current)
		runCurrent()
		val domain = ReaderLegacyPhysicalDomain(103L, ReaderLegacyFreezeToken(104L))
		scheduler.freezeForTransitionActivation(domain)
		val identity = scheduler.snapshotFrozenOwnership().single().physicalIdentity
		val events = mutableListOf<String>()
		result.invokeOnCompletion {
			events += "callback"
			assertEquals(ReaderPortCommandResult.Accepted, scheduler.drainFrozenOwnership(identity) { events += "confirmed" })
			assertEquals(listOf("callback"), events, "Confirmed while terminal callback was on-stack")
			events += "tail"
			throw IllegalStateException("terminal-callback-failed")
		}
		gate.complete(Unit)
		runCurrent()
		assertEquals(listOf("callback", "tail", "confirmed"), events)
		assertTrue(scheduler.snapshotFrozenOwnership().isEmpty())
		scheduler.close()
		runCurrent()
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun activationBoundaryFrozenCloseResultDrainWaitsForCallbackTail() = runTest {
		val gate = CompletableDeferred<Unit>()
		val generator = FakeRasterGenerator(firstGate = gate)
		val profile = rasterProfile("boundary-close-tail")
		val scheduler = ReaderPageRasterScheduler(backgroundScope, FakeRasterStore(), generator, {}, StandardTestDispatcher(testScheduler))
		scheduler.activateProfile(profile)
		val active = scheduler.request(rasterKey(profile, 1), ReaderPageRasterPriority.Current)
		runCurrent()
		val queued = scheduler.request(rasterKey(profile, 2), ReaderPageRasterPriority.NextTransition)
		val domain = ReaderLegacyPhysicalDomain(105L, ReaderLegacyFreezeToken(106L))
		scheduler.freezeForTransitionActivation(domain)
		val identity = scheduler.snapshotFrozenOwnership().single { it.state == ReaderLegacyResourceState.Reserved }.physicalIdentity
		val events = mutableListOf<String>()
		queued.invokeOnCompletion {
			events += "callback"
			scheduler.drainFrozenOwnership(identity) { events += "confirmed" }
			assertEquals(listOf("callback"), events)
			events += "tail"
		}
		try {
			scheduler.close()
			assertEquals(listOf("callback", "tail", "confirmed"), events)
		} finally {
			gate.complete(Unit)
			runCurrent()
			active.await()
			scheduler.closeAndJoin()
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun activationBoundaryWorkerExitResultDrainWaitsForCallbackTail() = runTest {
		val gate = CompletableDeferred<Unit>()
		val generator = FakeRasterGenerator(firstGate = gate)
		val profile = rasterProfile("boundary-worker-exit")
		val scheduler = ReaderPageRasterScheduler(backgroundScope, FakeRasterStore(), generator, {}, StandardTestDispatcher(testScheduler))
		scheduler.activateProfile(profile)
		val active = scheduler.request(rasterKey(profile, 1), ReaderPageRasterPriority.Current)
		runCurrent()
		val queued = scheduler.request(rasterKey(profile, 2), ReaderPageRasterPriority.NextTransition)
		val domain = ReaderLegacyPhysicalDomain(113L, ReaderLegacyFreezeToken(114L))
		scheduler.freezeForTransitionActivation(domain)
		val rows = scheduler.snapshotFrozenOwnership()
		val identity = rows.single { it.state == ReaderLegacyResourceState.Reserved }.physicalIdentity
		val events = mutableListOf<String>()
		queued.invokeOnCompletion {
			events += "callback"
			scheduler.drainFrozenOwnership(identity) { events += "confirmed" }
			assertEquals(listOf("callback"), events)
			events += "tail"
		}
		backgroundScope.cancel()
		runCurrent()
		assertEquals(listOf("callback", "tail", "confirmed"), events)
		assertEquals(ReaderPageRasterScheduleStatus.Stale, active.await().status)
		val activeIdentity = rows.single { it.state == ReaderLegacyResourceState.Running }.physicalIdentity
		assertEquals(ReaderPortCommandResult.Accepted, scheduler.drainFrozenOwnership(activeIdentity) {})
		assertTrue(scheduler.snapshotFrozenOwnership().isEmpty())
		scheduler.closeAndJoin()
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun activationBoundaryClosedSchedulerInventoriesItsOnStackResultUntilActualReturn() = runTest {
		val gate = CompletableDeferred<Unit>()
		val generator = FakeRasterGenerator(firstGate = gate)
		val profile = rasterProfile("boundary-closing-inventory")
		val scheduler = ReaderPageRasterScheduler(backgroundScope, FakeRasterStore(), generator, {}, StandardTestDispatcher(testScheduler))
		scheduler.activateProfile(profile)
		val active = scheduler.request(rasterKey(profile, 1), ReaderPageRasterPriority.Current)
		runCurrent()
		val queued = scheduler.request(rasterKey(profile, 2), ReaderPageRasterPriority.NextTransition)
		val domain = ReaderLegacyPhysicalDomain(115L, ReaderLegacyFreezeToken(116L))
		val events = mutableListOf<String>()
		var freezeResult: ReaderPortCommandResult? = null
		queued.invokeOnCompletion {
			events += "callback"
			freezeResult = scheduler.freezeForTransitionActivation(domain)
			if (freezeResult == ReaderPortCommandResult.Accepted) {
				val rows = scheduler.snapshotFrozenOwnership()
				assertEquals(2, rows.size)
				val tail = rows.single { it.physicalIdentity.sourceLocalToken != rows.first().physicalIdentity.sourceLocalToken }
				assertEquals(ReaderLegacyResourceState.Running, tail.state)
				assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), scheduler.drainFrozenOwnership(tail.physicalIdentity.copy(source = ReaderLegacyInventorySource.RasterPublication)) {})
				assertEquals(ReaderPortCommandResult.Accepted, scheduler.drainFrozenOwnership(tail.physicalIdentity) { events += "confirmed" })
				assertEquals(listOf("callback"), events, "Closing result owner confirmed on-stack")
				assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), scheduler.restoreAfterTransitionActivation(domain))
			}
			events += "tail"
		}
		try {
			scheduler.close()
			assertEquals(ReaderPortCommandResult.Accepted, freezeResult, "Closed scheduler rejected inventory of its unsettled result tail")
			assertEquals(listOf("callback", "tail", "confirmed"), events)
			assertEquals(ReaderPortCommandResult.Accepted, scheduler.freezeForTransitionActivation(domain))
			assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), scheduler.freezeForTransitionActivation(ReaderLegacyPhysicalDomain(115L, ReaderLegacyFreezeToken(117L))))
			gate.complete(Unit)
			runCurrent()
			assertEquals(ReaderPageRasterScheduleStatus.Stale, active.await().status)
			scheduler.snapshotFrozenOwnership().forEach { assertEquals(ReaderPortCommandResult.Accepted, scheduler.drainFrozenOwnership(it.physicalIdentity) {}) }
			assertTrue(scheduler.snapshotFrozenOwnership().isEmpty())
			assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), scheduler.restoreAfterTransitionActivation(domain))
		} finally {
			gate.complete(Unit)
			runCurrent()
			scheduler.closeAndJoin()
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun activationBoundaryDispatchedRetentionIsInventoriedAndActuallyRestored() = runTest {
		val ioDispatcher = HeldRasterIoDispatcher()
		val cache = ReaderPageRasterCache(
			root = createTempDirectory("navic-retention-boundary").toFile(),
			codec = StringRasterCodec(),
			maxDecodedEntries = 1
		)
		val store = ReaderPageRasterCacheStore(cache)
		val oldProfile = rasterProfile("boundary-retention-old")
		val profile = rasterProfile("boundary-retention-new")
		val oldKey = rasterKey(oldProfile, 1)
		store.write(oldKey, testRasterMetadata(), "obsolete")
		val scheduler = ReaderPageRasterScheduler(backgroundScope, store, FakeRasterGenerator(), {}, ioDispatcher)
		scheduler.activateProfile(profile)
		runCurrent()
		val domain = ReaderLegacyPhysicalDomain(107L, ReaderLegacyFreezeToken(108L))
		scheduler.freezeForTransitionActivation(domain)
		store.freezeForTransitionActivation(domain)
		cache.freezeForTransitionActivation(domain)
		assertTrue(checkNotNull(store.connectedFrozenOwnership()).resources.isEmpty())
		val rows = scheduler.snapshotFrozenOwnership()
		assertEquals(1, rows.size, "Dispatched retention lost its physical identity")
		assertEquals(ReaderLegacyInventorySource.RasterGenerationAndPersistence, rows.single().physicalIdentity.source)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		scheduler.drainFrozenOwnership(rows.single().physicalIdentity, confirmations::add)
		ioDispatcher.runNext()
		runCurrent()
		assertEquals(1, cache.metrics().diskEntries, "Retention IO started after freeze")
		assertEquals(listOf(rows.single().physicalIdentity), confirmations)
		assertEquals(ReaderPortCommandResult.Accepted, store.restoreAfterTransitionActivation(domain))
		assertEquals(ReaderPortCommandResult.Accepted, cache.restoreAfterTransitionActivation(domain))
		assertEquals(ReaderPortCommandResult.Accepted, scheduler.restoreAfterTransitionActivation(domain))
		runCurrent()
		ioDispatcher.runNext()
		runCurrent()
		assertEquals(0, cache.metrics().diskEntries, "Same-profile restore lost the retention contract")
		scheduler.closeAndJoin()
		store.close()
		cache.close()
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun activationBoundaryQueuedRetentionCannotDisappearOrStartWhileFrozen() = runTest {
		val store = FakeRasterStore()
		val oldProfile = rasterProfile("boundary-queued-retention-old")
		val profile = rasterProfile("boundary-queued-retention-new")
		val oldKey = rasterKey(oldProfile, 1)
		store.write(oldKey, testRasterMetadata(), "obsolete")
		val scheduler = ReaderPageRasterScheduler(backgroundScope, store, FakeRasterGenerator(), {}, StandardTestDispatcher(testScheduler))
		scheduler.activateProfile(profile)
		val domain = ReaderLegacyPhysicalDomain(109L, ReaderLegacyFreezeToken(110L))
		scheduler.freezeForTransitionActivation(domain)
		val rows = scheduler.snapshotFrozenOwnership()
		assertEquals(1, rows.size, "Queued retention has no inventory row")
		runCurrent()
		assertNotNull(store.read(oldKey))
		scheduler.drainFrozenOwnership(rows.single().physicalIdentity) {}
		assertEquals(ReaderPortCommandResult.Accepted, scheduler.restoreAfterTransitionActivation(domain))
		runCurrent()
		assertNull(store.read(oldKey))
		scheduler.closeAndJoin()
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun activationBoundaryDispatchedProtectionCopiesItsRequestAndRestoresOnce() = runTest {
		val ioDispatcher = HeldRasterIoDispatcher()
		val store = FakeRasterStore()
		val scheduler = ReaderPageRasterScheduler(backgroundScope, store, FakeRasterGenerator(), {}, ioDispatcher)
		val profile = rasterProfile("boundary-protection")
		val pins = linkedSetOf(1, 2)
		val protecting = backgroundScope.async { scheduler.protectEncodedWindow(profile, 1, pins) }
		runCurrent()
		val domain = ReaderLegacyPhysicalDomain(111L, ReaderLegacyFreezeToken(112L))
		scheduler.freezeForTransitionActivation(domain)
		val rows = scheduler.snapshotFrozenOwnership()
		assertEquals(1, rows.size, "Dispatched protection is missing")
		pins.clear()
		scheduler.drainFrozenOwnership(rows.single().physicalIdentity) {}
		ioDispatcher.runNext()
		runCurrent()
		protecting.await()
		assertTrue(store.protections.isEmpty())
		assertEquals(ReaderPortCommandResult.Accepted, scheduler.restoreAfterTransitionActivation(domain))
		runCurrent()
		ioDispatcher.runNext()
		runCurrent()
		assertEquals(listOf(setOf(1, 2)), store.protections)
		scheduler.closeAndJoin()
	}

	@Test
	fun schedulerSourceHasOneOwnerAndNoCancellationTimeout() {
		val source = readerAndroidFile("ReaderPageRasterScheduler.android.kt").readText()

		assertTrue("Channel<Unit>(capacity = 1)" in source)
		assertTrue("private val workerJob = scope.launch" in source)
		assertTrue("for (signal in wakeups)" in source)
		assertTrue("suspend fun closeAndJoin()" in source)
		assertTrue("withContext(NonCancellable)" in source)
		assertFalse("withTimeout" in source)
		assertFalse("timeoutMillis" in source)
		assertFalse("delay(" in source)
	}

	private class HeldRasterIoDispatcher : CoroutineDispatcher() {
		private val continuations = ArrayDeque<Runnable>()

		override fun dispatch(context: CoroutineContext, block: Runnable) {
			continuations.addLast(block)
		}

		fun runNext() {
			assertEquals(1, continuations.size)
			continuations.removeFirst().run()
		}
	}

	private class StringRasterCodec : ReaderPageRasterCodec<String> {
		override fun encode(value: String, target: File): Boolean {
			target.writeText(value)
			return true
		}

		override fun decode(source: File): String? = source.readText()

		override fun release(value: String) = Unit
	}

	private class FakeRasterGenerator(
		private val gate: CompletableDeferred<Unit>? = null,
		private val firstGate: CompletableDeferred<Unit>? = null
	) : ReaderPageRasterGenerator<String> {
		val calls = mutableListOf<ReaderPageRasterKey>()
		val firstStarted = CompletableDeferred<Unit>()

		override suspend fun generate(key: ReaderPageRasterKey): ReaderPageRasterGeneration<String>? {
			calls += key
			if (!firstStarted.isCompleted) firstStarted.complete(Unit)
			when {
				firstGate != null && calls.size == 1 -> firstGate.await()
				gate != null -> gate.await()
			}
			return ReaderPageRasterGeneration(
				metadata = testRasterMetadata(),
				value = "page-${key.visualPageOrdinal}",
				captureMillis = 25
			)
		}
	}

	private class FakeRasterStore(
		private val writeOwnership: ReaderPageRasterValueOwnership =
			ReaderPageRasterValueOwnership.Store,
		private val retainProfiles: Boolean = true
	) : ReaderPageRasterStore<String> {
		private val values =
			mutableMapOf<ReaderPageRasterKey, ReaderPageRaster<String>>()
		private val revisions = mutableMapOf<ReaderPageRasterKey, Long>()
		private var nextRevision = 1L
		val readCalls = mutableListOf<ReaderPageRasterKey>()
		val protections = mutableListOf<Set<Int>>()
		override fun protectEncodedWindow(
			profile: ReaderPageRasterProfile,
			centerPageOrdinal: Int,
			pinnedPageOrdinals: Set<Int>
		) {
			protections += pinnedPageOrdinals.toSet()
		}
		var beforeWrite: (ReaderPageRasterKey) -> Unit = {}
		var afterWrite: (ReaderPageRasterKey) -> Unit = {}
		var rollbackFailure: Throwable? = null

		override fun contains(key: ReaderPageRasterKey): Boolean = key in values

		fun read(key: ReaderPageRasterKey): ReaderPageRaster<String>? {
			readCalls += key
			return values[key]
		}

		fun overwrite(key: ReaderPageRasterKey, value: String) {
			values[key] = ReaderPageRaster(key, testRasterMetadata(), value)
			revisions[key] = nextRevision++
		}

		override fun <R : Any> readCopy(
			key: ReaderPageRasterKey,
			copy: (String) -> R?
		): ReaderPageRaster<R>? {
			readCalls += key
			val raster = values[key] ?: return null
			return copy(raster.value)?.let { copied ->
				ReaderPageRaster(key, raster.metadata, copied)
			}
		}

		override fun write(
			key: ReaderPageRasterKey,
			metadata: ReaderPageRasterMetadata,
			value: String
		): ReaderPageRasterWriteResult {
			beforeWrite(key)
			values[key] = ReaderPageRaster(key, metadata, value)
			val revision = nextRevision++
			revisions[key] = revision
			afterWrite(key)
			return ReaderPageRasterWriteResult(
				persisted = true,
				ownership = writeOwnership,
				receipt = ReaderPageRasterWriteReceipt(
					key = key,
					rasterFileName = "${key.digest}.png",
					inProcessRevision = revision
				)
			)
		}

		override fun remove(key: ReaderPageRasterKey): Boolean {
			revisions.remove(key)
			return values.remove(key) != null
		}

		override fun rollbackPublication(
			receipt: ReaderPageRasterWriteReceipt
		): Boolean {
			rollbackFailure?.let { throw it }
			if (revisions[receipt.key] != receipt.inProcessRevision) {
				return false
			}
			revisions.remove(receipt.key)
			return values.remove(receipt.key) != null
		}

		override fun retainProfile(profile: ReaderPageRasterProfile): Int {
			if (!retainProfiles) return 0
			val removed = values.keys.filter { key ->
				key.publicationHash == profile.publicationHash &&
					key.profile != profile
			}
			removed.forEach { key ->
				values.remove(key)
				revisions.remove(key)
			}
			return removed.size
		}

		override fun protectChapter(
			chapter: ReaderPageRasterChapterKey?
		) = Unit

		override fun encodedBytes(key: ReaderPageRasterKey): Long = 1024
	}

	private fun pendingRequestCount(
		scheduler: ReaderPageRasterScheduler<*>
	): Int {
		val field = scheduler.javaClass.getDeclaredField("pending")
		field.isAccessible = true
		return (field.get(scheduler) as Map<*, *>).size
	}

	private fun rasterProfile(id: String) = ReaderPageRasterProfile(
		publicationHash = "publication",
		paginationHash = "pagination-$id",
		layoutHash = "layout-$id",
		decorationHash = "decoration-$id",
		quality = ReaderPageBitmapQuality.Balanced,
		schemaVersion = ReaderPageRasterSchemaVersion
	)

	private fun rasterKey(profile: ReaderPageRasterProfile, page: Int) = ReaderPageRasterKey(
		publicationHash = profile.publicationHash,
		paginationHash = profile.paginationHash,
		spineIndex = 0,
		hrefHash = "href",
		chapterPageIndex = page,
		visualPageOrdinal = page,
		viewportWidth = 1000,
		viewportHeight = 700,
		layoutHash = profile.layoutHash,
		decorationHash = profile.decorationHash,
		quality = profile.quality,
		schemaVersion = profile.schemaVersion
	)

}

private fun testRasterMetadata() = ReaderPageRasterMetadata(
	surfaceLeft = 0,
	surfaceTop = 0,
	surfaceRight = 1000,
	surfaceBottom = 700,
	fullLeafRect = ReaderPageRasterRect(0, 0, 1000, 700),
	leftLeafRect = null,
	gutterRect = null,
	rightLeafRect = null,
	reverseFaceColor = 0xffead9ae.toInt()
)
