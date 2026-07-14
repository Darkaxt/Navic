package paige.navic.ui.screens.reader

import java.io.File
import kotlinx.coroutines.CompletableDeferred
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
	fun schedulerSourceHasOneOwnerAndNoCancellationTimeout() {
		val source = readerAndroidFile("ReaderPageRasterScheduler.android.kt").readText()

		assertTrue("Channel<Unit>(capacity = 1)" in source)
		assertTrue("for (signal in wakeups)" in source)
		assertFalse("withTimeout" in source)
		assertFalse("timeoutMillis" in source)
		assertFalse("delay(" in source)
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

	private class FakeRasterStore : ReaderPageRasterStore<String> {
		private val values = mutableMapOf<ReaderPageRasterKey, ReaderPageRaster<String>>()
		val readCalls = mutableListOf<ReaderPageRasterKey>()

		override fun contains(key: ReaderPageRasterKey): Boolean = key in values

		fun read(key: ReaderPageRasterKey): ReaderPageRaster<String>? {
			readCalls += key
			return values[key]
		}

		override fun write(
			key: ReaderPageRasterKey,
			metadata: ReaderPageRasterMetadata,
			value: String
		): Boolean {
			values[key] = ReaderPageRaster(key, metadata, value)
			return true
		}

		override fun remove(key: ReaderPageRasterKey): Boolean = values.remove(key) != null

		override fun retainProfile(profile: ReaderPageRasterProfile): Int {
			val removed = values.keys.filter { key ->
				key.publicationHash == profile.publicationHash && key.profile != profile
			}
			removed.forEach(values::remove)
			return removed.size
		}

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
