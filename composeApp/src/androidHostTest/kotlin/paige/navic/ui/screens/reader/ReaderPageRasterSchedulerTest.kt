package paige.navic.ui.screens.reader

import java.io.File
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.readerAndroidFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

		assertEquals(ReaderPageRasterScheduleStatus.Stale, stale.status)
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
		assertEquals(ReaderPageRasterCacheMetrics(0, 0L, 0), cache.metrics())
	}

	@Test
	fun schedulerSourceHasOneOwnerAndNoCancellationTimeout() {
		val source = readerAndroidFile("ReaderPageRasterScheduler.android.kt").readText()

		assertTrue("Channel<Unit>(capacity = 1)" in source)
		assertTrue("for (signal in wakeups)" in source)
		assertFalse("withTimeout" in source)
		assertFalse("timeoutMillis" in source)
		assertFalse("delay(" in source)
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
