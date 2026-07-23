package paige.navic.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Looper
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPixelRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageTurnBundleHydrationTest {
	@Test
	fun productionResolverUsesRetainedThenPersistentWithoutCapture() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val descriptors = FakeDescriptorPort(events)
		val store = FakeHydrationStore(events, durablePages = setOf(4))
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptors,
			hydrationStorePort = store
		)
		val reference = referenceSnapshot()
		try {
			val persistentResult = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { persistentResult.complete(it) }

			assertNotNull(persistentResult.await()).release()
			assertEquals(listOf("descriptor:4", "persistent:4"), events)

			events.clear()
			val retainedResult = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { retainedResult.complete(it) }

			assertNotNull(retainedResult.await()).release()
			assertTrue(events.isEmpty())

			val missingResult = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView,
				pageIndex = 5,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { missingResult.complete(it) }

			assertNull(missingResult.await())
			assertEquals(listOf("descriptor:5", "persistent:5"), events)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun warmPersistentHitEmitsAcquisitionWithoutStartingWebViewCapture() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val messages = mutableListOf<String>()
		val persistentHit = CompletableDeferred<Unit>()
		val events = mutableListOf<String>()
		val diagnostics = ReaderPageRuntimeDiagnostics(
			readerSession = 17L,
			nowMs = { 25L },
			emit = { message ->
				messages += message
				if (message.contains("result=Hit")) persistentHit.complete(Unit)
			}
		)
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(events),
			hydrationStorePort = FakeHydrationStore(events, durablePages = setOf(4))
		)
		val controller = ReaderPageRasterBatchController(source, diagnostics)
		val reference = referenceSnapshot()
		try {
			val outcomes = mutableListOf<ReaderPageRasterBatchOutcome>()
			reference.retain()
			assertTrue(
				controller.start(
					webView = webView,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					reference = reference,
					targets = listOf(
						ReaderPageRasterBatchTarget(
							pageIndex = 4,
							priority = ReaderPageRasterPriority.Current
						)
					),
					trigger = ReaderPageRasterAcquisitionTrigger.WarmReopen,
					onComplete = outcomes::add
				)
			)
			persistentHit.await()

			assertEquals(listOf("descriptor:4", "persistent:4"), events)
			val acquisitions = messages.filter {
				it.startsWith("reader-raster-acquisition ")
			}
			assertEquals(2, acquisitions.size)
			assertTrue(acquisitions[0].contains("source=PersistentHydration"))
			assertTrue(acquisitions[0].contains("trigger=WarmReopen result=Started"))
			assertTrue(acquisitions[1].contains("source=PersistentHydration"))
			assertTrue(acquisitions[1].contains("trigger=WarmReopen result=Hit"))
			assertEquals(
				acquisitions[0].substringAfter("attempt=").substringBefore(' '),
				acquisitions[1].substringAfter("attempt=").substringBefore(' ')
			)
			assertFalse(messages.any { it.contains("source=WebViewCapture") })
			controller.cancel()
			assertEquals(
				listOf<ReaderPageRasterBatchOutcome>(ReaderPageRasterBatchOutcome.Cancelled),
				outcomes
			)
		} finally {
			controller.cancel()
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun productionFoliateLoaderUsesRetainedThenPersistentThenOneRepair() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(events),
			hydrationStorePort = FakeHydrationStore(events, durablePages = setOf(4))
		)
		val reference = referenceSnapshot()
		val repairs = mutableListOf<Int>()
		val profile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = "test",
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			quality = ReaderPageBitmapQuality.Balanced,
			pageCount = 20,
			rasterGeneration = source.currentGeneration()
		)
		val loader = ReaderPlayLikeCurlFoliateRasterLoader(
			bundleSource = source,
			profile = profile,
			webViewProvider = { webView },
			referenceSnapshotProvider = {
				reference.retain()
				reference
			},
			onMissingRaster = repairs::add
		)
		try {
			val persistent = loader.load(ReaderPlayLikeCurlRasterKey(profile, 4))
			assertNotNull(persistent).bitmap.recycle()
			assertEquals(listOf("descriptor:4", "persistent:4"), events)
			assertTrue(repairs.isEmpty())

			events.clear()
			val retained = loader.load(ReaderPlayLikeCurlRasterKey(profile, 4))
			assertNotNull(retained).bitmap.recycle()
			assertTrue(events.isEmpty())
			assertTrue(repairs.isEmpty())

			val missing = loader.load(ReaderPlayLikeCurlRasterKey(profile, 5))
			assertNull(missing)
			assertEquals(listOf("descriptor:5", "persistent:5"), events)
			assertEquals(listOf(5), repairs)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun cancellingBeforeOffMainCopyReleasesRetainedSnapshot() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		var durableBitmap: Bitmap? = null
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap> =
				persistentRaster(key).also { raster -> durableBitmap = raster.value }

			override suspend fun remove(key: ReaderPageRasterKey): Boolean = false
		}
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(mutableListOf()),
			hydrationStorePort = store
		)
		val reference = referenceSnapshot()
		val profile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = "cancel-before-copy",
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			quality = ReaderPageBitmapQuality.Balanced,
			pageCount = 20,
			rasterGeneration = source.currentGeneration()
		)
		try {
			assertNotNull(
				source.resolveSnapshot(
					webView = webView,
					pageIndex = 3,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					reference = reference,
					publicationFence = { true }
				)
			).release()
			val loader = ReaderPlayLikeCurlFoliateRasterLoader(
				bundleSource = source,
				profile = profile,
				webViewProvider = { webView },
				referenceSnapshotProvider = {
					reference.retain()
					reference
				},
				copyDispatcher = StandardTestDispatcher(testScheduler)
			)
			val pending = async(start = CoroutineStart.UNDISPATCHED) {
				loader.load(ReaderPlayLikeCurlRasterKey(profile, 3))
			}
			assertFalse(pending.isCompleted)

			pending.cancel()
			runCurrent()
			pending.join()
			source.invalidate("cancel-before-copy")

			assertTrue(checkNotNull(durableBitmap).isRecycled)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun cancellingBatchCancelsItsPendingHydrationRecipient() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val descriptors = FakeDescriptorPort(events, automatic = false)
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptors,
			hydrationStorePort = FakeHydrationStore(events, setOf(14))
		)
		val reference = referenceSnapshot()
		val outcomes = mutableListOf<ReaderPageRasterBatchOutcome>()
		val controller = ReaderPageRasterBatchController(source)
		try {
			reference.retain()
			assertTrue(
				controller.start(
					webView = webView,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					reference = reference,
					targets = listOf(
						ReaderPageRasterBatchTarget(
							pageIndex = 14,
							priority = ReaderPageRasterPriority.Current
						)
					),
					onComplete = outcomes::add
				)
			)
			assertEquals(
				ReaderPageRasterHydrationOwnerCounts(1, 1, 0, 0),
				source.hydrationOwnerCounts()
			)

			controller.cancel()
			descriptors.respond(pageIndex = 14)

			assertEquals(
				listOf<ReaderPageRasterBatchOutcome>(ReaderPageRasterBatchOutcome.Cancelled),
				outcomes
			)
			assertEquals(
				ReaderPageRasterHydrationOwnerCounts(0, 0, 0, 0),
				source.hydrationOwnerCounts()
			)
			assertEquals(listOf("descriptor:14"), events)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun cancellingBeforeDescriptorAcknowledgementTombstonesOnlyThatRecipient() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val descriptors = FakeDescriptorPort(events, automatic = false)
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptors,
			hydrationStorePort = FakeHydrationStore(events, setOf(6))
		)
		val reference = referenceSnapshot()
		try {
			var callbackCount = 0
			val request = source.hydrateSnapshot(
				webView,
				pageIndex = 6,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { callbackCount += 1 }

			request.cancel()
			descriptors.respond(pageIndex = 6)
			Shadows.shadowOf(Looper.getMainLooper()).idle()

			assertEquals(0, callbackCount)
			assertEquals(listOf("descriptor:6"), events)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun cancellingOneCoalescedRecipientLeavesTheOtherRecipientOwned() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val descriptors = FakeDescriptorPort(events, automatic = false)
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptors,
			hydrationStorePort = FakeHydrationStore(events, setOf(7))
		)
		val reference = referenceSnapshot()
		try {
			var cancelledCallbackCount = 0
			val survivingResult = CompletableDeferred<ReaderPageSlideSnapshot?>()
			val cancelled = source.hydrateSnapshot(
				webView,
				pageIndex = 7,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { cancelledCallbackCount += 1 }
			source.hydrateSnapshot(
				webView,
				pageIndex = 7,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { survivingResult.complete(it) }

			cancelled.cancel()
			descriptors.respond(pageIndex = 7)

			assertEquals(0, cancelledCallbackCount)
			assertNotNull(survivingResult.await()).release()
			assertEquals(1, descriptors.requestCount)
			assertEquals(listOf("descriptor:7", "persistent:7"), events)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun coalescedRecipientsArePreRetainedBeforeReentrantInvalidation() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val descriptors = FakeDescriptorPort(events, automatic = false)
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptors,
			hydrationStorePort = FakeHydrationStore(events, setOf(11))
		)
		val reference = referenceSnapshot()
		try {
			var secondReceivedLiveBitmap = false
			val recipientsCompleted = CompletableDeferred<Unit>()
			source.hydrateSnapshot(
				webView,
				pageIndex = 11,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { snapshot ->
				checkNotNull(snapshot).release()
				source.invalidate("reentrant-recipient")
			}
			source.hydrateSnapshot(
				webView,
				pageIndex = 11,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { snapshot ->
				val received = checkNotNull(snapshot)
				secondReceivedLiveBitmap = !received.bitmap.isRecycled
				received.release()
				recipientsCompleted.complete(Unit)
			}

			descriptors.respond(pageIndex = 11)
			recipientsCompleted.await()

			assertTrue(secondReceivedLiveBitmap)
			source.closeAndJoin()
			assertEquals(
				ReaderPageRasterHydrationOwnerCounts(0, 0, 0, 0),
				source.hydrationOwnerCounts()
			)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun corruptPersistentGeometryIsRemovedWithoutDecodedPublication() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val removed = mutableListOf<Int>()
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap> {
				val raster = persistentRaster(key)
				return raster.copy(
					metadata = raster.metadata.copy(surfaceRight = 19)
				)
			}

			override suspend fun remove(key: ReaderPageRasterKey): Boolean {
				removed += key.visualPageOrdinal
				return true
			}
		}
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(mutableListOf()),
			hydrationStorePort = store
		)
		val reference = referenceSnapshot()
		try {
			val result = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView,
				pageIndex = 12,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { result.complete(it) }

			assertNull(result.await())
			assertEquals(listOf(12), removed)
			assertFalse(
				source.hasSnapshot(12, ReaderPageTurnTransitionKind.PortraitSlide)
			)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun closeDuringDecodeDrainsEveryHydrationOwner() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val readStarted = CompletableDeferred<Unit>()
		val readRelease = CompletableDeferred<Unit>()
		var readFinally = false
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap>? {
				try {
					readStarted.complete(Unit)
					readRelease.await()
					return persistentRaster(key)
				} finally {
					readFinally = true
				}
			}

			override suspend fun remove(key: ReaderPageRasterKey): Boolean = false
		}
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(mutableListOf()),
			hydrationStorePort = store
		)
		val reference = referenceSnapshot()
		try {
			var callbackCount = 0
			var result: ReaderPageSlideSnapshot? = reference
			source.hydrateSnapshot(
				webView,
				pageIndex = 13,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) {
				callbackCount += 1
				result = it
			}
			readStarted.await()

			source.close()
			readRelease.complete(Unit)
			source.closeAndJoin()
			runCurrent()

			assertTrue(readFinally)
			assertEquals(1, callbackCount)
			assertNull(result)
			assertEquals(
				ReaderPageRasterHydrationOwnerCounts(0, 0, 0, 0),
				source.hydrationOwnerCounts()
			)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun stalePublicationFencePreventsDecodedInsertionAfterRead() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val readStarted = CompletableDeferred<Unit>()
		val readRelease = CompletableDeferred<Unit>()
		var current = true
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap>? {
				events += "persistent:${key.visualPageOrdinal}"
				readStarted.complete(Unit)
				readRelease.await()
				return persistentRaster(key)
			}

			override suspend fun remove(key: ReaderPageRasterKey): Boolean = false
		}
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(events),
			hydrationStorePort = store
		)
		val reference = referenceSnapshot()
		try {
			var callbackCount = 0
			val result = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView,
				pageIndex = 9,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference,
				publicationFence = { current }
			) {
				callbackCount += 1
				result.complete(it)
			}
			readStarted.await()

			current = false
			readRelease.complete(Unit)
			assertNull(result.await())

			assertEquals(1, callbackCount)
			assertFalse(
				source.hasSnapshot(9, ReaderPageTurnTransitionKind.PortraitSlide)
			)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun closeBeforeDescriptorAcknowledgementCompletesRecipientOnlyOnce() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val descriptors = FakeDescriptorPort(events, automatic = false)
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptors,
			hydrationStorePort = FakeHydrationStore(events, setOf(10))
		)
		val reference = referenceSnapshot()
		try {
			var callbackCount = 0
			var result: ReaderPageSlideSnapshot? = reference
			source.hydrateSnapshot(
				webView,
				pageIndex = 10,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) {
				callbackCount += 1
				result = it
			}

			source.close()
			source.closeAndJoin()
			descriptors.respond(pageIndex = 10)
			Shadows.shadowOf(Looper.getMainLooper()).idle()

			assertEquals(1, callbackCount)
			assertNull(result)
			assertEquals(listOf("descriptor:10"), events)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun cancellingFinalRecipientCancelsItsExactReadWorker() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val readStarted = CompletableDeferred<Unit>()
		val readRelease = CompletableDeferred<Unit>()
		val readFinished = CompletableDeferred<Unit>()
		var readFinally = false
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap>? {
				try {
					events += "persistent:${key.visualPageOrdinal}"
					readStarted.complete(Unit)
					readRelease.await()
					return persistentRaster(key)
				} finally {
					readFinally = true
					readFinished.complete(Unit)
				}
			}

			override suspend fun remove(key: ReaderPageRasterKey): Boolean = false
		}
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(events),
			hydrationStorePort = store
		)
		val reference = referenceSnapshot()
		try {
			var callbackCount = 0
			val request = source.hydrateSnapshot(
				webView,
				pageIndex = 8,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { callbackCount += 1 }
			readStarted.await()

			request.cancel()
			readRelease.complete(Unit)
			readFinished.await()

			assertTrue(readFinally)
			assertEquals(0, callbackCount)
			assertEquals(listOf("descriptor:8", "persistent:8"), events)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	private class FakeDescriptorPort(
		private val events: MutableList<String>,
		private val automatic: Boolean = true
	) : ReaderPageRasterDescriptorPort {
		private var pending: Pair<Int, (ReaderPageRasterDescriptor?) -> Unit>? = null
		var requestCount = 0
			private set

		override fun request(
			webView: WebView,
			pageIndex: Int,
			onDescriptor: (ReaderPageRasterDescriptor?) -> Unit
		) {
			requestCount += 1
			events += "descriptor:$pageIndex"
			if (automatic) onDescriptor(descriptor(pageIndex))
			else pending = pageIndex to onDescriptor
		}

		fun respond(pageIndex: Int) {
			val request = checkNotNull(pending)
			pending = null
			assertEquals(pageIndex, request.first)
			request.second(descriptor(pageIndex))
		}
	}

	private class FakeHydrationStore(
		private val events: MutableList<String>,
		private val durablePages: Set<Int>
	) : ReaderPageRasterHydrationStorePort {
		override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap>? {
			events += "persistent:${key.visualPageOrdinal}"
			return if (key.visualPageOrdinal in durablePages) persistentRaster(key) else null
		}

		override suspend fun remove(key: ReaderPageRasterKey): Boolean = false
	}
}

private fun descriptor(pageIndex: Int) = ReaderPageRasterDescriptor(
	publicationUrl = "publication",
	paginationFingerprint = "pagination",
	layoutFingerprint = "layout",
	decorationFingerprint = "decoration",
	viewportWidth = 20,
	viewportHeight = 30,
	pageCount = 20,
	spineIndex = 0,
	href = "chapter",
	chapterPageIndex = pageIndex,
	chapterPageCount = 20,
	visualPageOrdinal = pageIndex
)

private fun persistentRaster(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap> =
	ReaderPageRaster(
		key = key,
		metadata = ReaderPageRasterMetadata(
			surfaceLeft = 0,
			surfaceTop = 0,
			surfaceRight = 20,
			surfaceBottom = 30,
			fullLeafRect = ReaderPageRasterRect(0, 0, 20, 30),
			leftLeafRect = null,
			gutterRect = null,
			rightLeafRect = null,
			reverseFaceColor = 0xffead9ae.toInt()
		),
		value = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
	)

private fun referenceSnapshot(): ReaderPageSlideSnapshot = ReaderPageSlideSnapshot(
	key = ReaderPageSlideSnapshotKey(
		visualPageIndex = 0,
		kind = ReaderPageTurnTransitionKind.PortraitSlide,
		bitmapQuality = ReaderPageBitmapQuality.Balanced,
		bitmapWidth = 20,
		bitmapHeight = 30,
		surfaceWidth = 20,
		surfaceHeight = 30
	),
	bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888),
	surfaceRectInWindow = Rect(0, 0, 20, 30),
	leafGeometry = ReaderPageTurnLeafGeometry(
		fullLeafRect = ReaderPageTurnPixelRect(0, 0, 20, 30),
		leftLeafRect = null,
		gutterRect = null,
		rightLeafRect = null
	),
	reverseFaceColor = 0xffead9ae.toInt()
)
