package paige.navic.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole
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
	fun rasterGeometryMustMatchTheRequestedTransitionKind() {
		val full = ReaderPageTurnPixelRect(0, 0, 20, 30)
		val left = ReaderPageTurnPixelRect(0, 0, 10, 30)
		val right = ReaderPageTurnPixelRect(10, 0, 20, 30)
		val portrait = ReaderPageTurnLeafGeometry(full, null, null, null)
		val spread = ReaderPageTurnLeafGeometry(null, left, null, right)

		assertTrue(
			readerPageRasterGeometryMatches(
				ReaderPageTurnTransitionKind.PortraitSlide,
				portrait
			)
		)
		assertFalse(
			readerPageRasterGeometryMatches(
				ReaderPageTurnTransitionKind.PortraitSlide,
				spread
			)
		)
		assertTrue(
			readerPageRasterGeometryMatches(
				ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				spread
			)
		)
		assertFalse(
			readerPageRasterGeometryMatches(
				ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				portrait
			)
		)
	}

	@Test
	fun physicalLayoutMustMatchTheCurrentLandscapeReference() {
		val reference = landscapeReferenceSnapshot()
		val matching = landscapeReferenceSnapshot()
		val containedButSmaller = landscapeReferenceSnapshot(
			surfaceRectInWindow = Rect(0, 0, 100, 60),
			left = ReaderPageTurnPixelRect(10, 5, 40, 55),
			right = ReaderPageTurnPixelRect(60, 5, 90, 55)
		)
		val shiftedSurface = landscapeReferenceSnapshot(
			surfaceRectInWindow = Rect(10, 5, 110, 65)
		)
		val snapshots = listOf(reference, matching, containedButSmaller, shiftedSurface)
		try {
			assertTrue(readerPageRasterPhysicalLayoutMatches(matching, reference))
			assertFalse(readerPageRasterPhysicalLayoutMatches(containedButSmaller, reference))
			assertFalse(readerPageRasterPhysicalLayoutMatches(shiftedSurface, reference))
		} finally {
			snapshots.forEach { snapshot -> snapshot.releaseCacheOwnership() }
		}
	}

	@Test
	fun currentCaptureMustMatchItsDeclaredTransitionKind() {
		val source = ReaderPageTurnBundleSource()
		val captured = captureResult()
		try {
			assertNull(
				source.cacheCurrentSnapshot(
					pageIndex = 0,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					current = captured
				)
			)
			assertTrue(captured.bitmap.isRecycled)
		} finally {
			source.close()
		}
	}

	@Test
	fun currentLayoutSnapshotRequiresExactGenerationAndQuality() {
		val source = ReaderPageTurnBundleSource()
		try {
			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 5,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					current = captureResult()
				)
			)
			val generation = source.currentGeneration()
			assertNotNull(
				source.retainedCurrentLayoutSnapshot(
					pageIndex = 5,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					expectedGeneration = generation,
					expectedQuality = ReaderPageBitmapQuality.Balanced
				)
			).release()
			assertNull(
				source.retainedCurrentLayoutSnapshot(
					pageIndex = 5,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					expectedGeneration = generation + 1L,
					expectedQuality = ReaderPageBitmapQuality.Balanced
				)
			)
			assertNull(
				source.retainedCurrentLayoutSnapshot(
					pageIndex = 5,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					expectedGeneration = generation,
					expectedQuality = ReaderPageBitmapQuality.High
				)
			)
		} finally {
			source.close()
		}
	}

	@Test
	fun retainedCurrentLayoutSnapshotNeverReusesAnInactiveTransitionLayout() {
		val source = ReaderPageTurnBundleSource()
		try {
			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 4,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					current = landscapeCaptureResult()
				)
			)
			assertNotNull(
				source.retainedCurrentLayoutSnapshot(
					pageIndex = 4,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					expectedGeneration = source.currentGeneration(),
					expectedQuality = ReaderPageBitmapQuality.Balanced
				)
			).release()

			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 5,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					current = captureResult()
				)
			)
			assertNotNull(
				source.retainedSnapshot(
					4,
					ReaderPageTurnTransitionKind.LandscapeSpreadSlide
				)
			).release()
			assertNull(
				source.retainedCurrentLayoutSnapshot(
					pageIndex = 4,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					expectedGeneration = source.currentGeneration(),
					expectedQuality = ReaderPageBitmapQuality.Balanced
				)
			)
			assertNotNull(
				source.retainedCurrentLayoutSnapshot(
					pageIndex = 5,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					expectedGeneration = source.currentGeneration(),
					expectedQuality = ReaderPageBitmapQuality.Balanced
				)
			).release()
		} finally {
			source.close()
		}
	}

	@Test
	fun staleContainedLandscapeRasterIsRejectedBeforePersistentPublication() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		var removeCount = 0
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap> =
				ReaderPageRaster(
					key = key,
					metadata = ReaderPageRasterMetadata(
						surfaceLeft = 0,
						surfaceTop = 0,
						surfaceRight = 100,
						surfaceBottom = 60,
						fullLeafRect = null,
						leftLeafRect = ReaderPageRasterRect(10, 5, 40, 55),
						gutterRect = ReaderPageRasterRect(40, 5, 60, 55),
						rightLeafRect = ReaderPageRasterRect(60, 5, 90, 55),
						reverseFaceColor = 0xffead9ae.toInt()
					),
					value = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
				)

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean {
				removeCount += 1
				return true
			}
		}
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(events),
			hydrationStorePort = store
		)
		val reference = landscapeReferenceSnapshot()
		try {
			val result = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView = webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				reference = reference
			) { result.complete(it) }

			assertNull(result.await())
			assertEquals(0, removeCount)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun staleHydrationWorkerCannotRemoveRasterAfterLayoutInvalidation() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val readStarted = CompletableDeferred<Unit>()
		val finishRead = CompletableDeferred<Unit>()
		var removeCount = 0
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap> {
				readStarted.complete(Unit)
				finishRead.await()
				return persistentLandscapeRaster(
					key = key,
					left = ReaderPageRasterRect(10, 5, 40, 55),
					right = ReaderPageRasterRect(60, 5, 90, 55)
				)
			}

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean {
				removeCount += 1
				return true
			}
		}
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(mutableListOf()),
			hydrationStorePort = store
		)
		val reference = landscapeReferenceSnapshot()
		try {
			val result = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView = webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				reference = reference
			) { result.complete(it) }
			readStarted.await()

			source.invalidate("physical-layout-changed")
			finishRead.complete(Unit)
			runCurrent()

			assertNull(result.await())
			assertEquals(0, removeCount)
		} finally {
			finishRead.complete(Unit)
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun hydrationCannotPublishAfterAnotherPageDetectsANewPhysicalLayout() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val readStarted = CompletableDeferred<Unit>()
		val finishRead = CompletableDeferred<Unit>()
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap> {
				readStarted.complete(Unit)
				finishRead.await()
				return persistentLandscapeRaster(
					key = key,
					left = ReaderPageRasterRect(0, 0, 50, 60),
					right = ReaderPageRasterRect(50, 0, 100, 60)
				)
			}

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean = false
		}
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(mutableListOf()),
			hydrationStorePort = store
		)
		val reference = landscapeReferenceSnapshot()
		try {
			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 5,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					current = landscapeCaptureResult()
				)
			)
			val result = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView = webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				reference = reference
			) { result.complete(it) }
			readStarted.await()

			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 5,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					current = landscapeCaptureResult(Rect(10, 5, 110, 65))
				)
			)
			finishRead.complete(Unit)
			runCurrent()

			assertNull(result.await())
			assertFalse(
				source.hasSnapshot(
					4,
					ReaderPageTurnTransitionKind.LandscapeSpreadSlide
				)
			)
		} finally {
			finishRead.complete(Unit)
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun staleReferenceCannotEvictTheCurrentPhysicalLayout() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(events),
			hydrationStorePort = FakeHydrationStore(events, durablePages = emptySet())
		)
		var staleReference: ReaderPageSlideSnapshot? = null
		try {
			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 4,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					current = landscapeCaptureResult()
				)
			)
			val stale = assertNotNull(
				source.retainedSnapshot(
					4,
					ReaderPageTurnTransitionKind.LandscapeSpreadSlide
				)
			)
			staleReference = stale
			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 5,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					current = landscapeCaptureResult(Rect(10, 5, 110, 65))
				)
			)

			val result = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView = webView,
				pageIndex = 6,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				reference = stale
			) { result.complete(it) }

			assertNull(result.await())
			assertTrue(
				source.hasSnapshot(
					5,
					ReaderPageTurnTransitionKind.LandscapeSpreadSlide
				)
			)
			assertTrue(events.isEmpty())
		} finally {
			staleReference?.release()
			source.close()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun staleInMemoryLandscapeSnapshotIsRejectedAgainstTheCurrentReference() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(events),
			hydrationStorePort = FakeHydrationStore(events, durablePages = emptySet())
		)
		val reference = landscapeReferenceSnapshot()
		try {
			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 4,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					current = landscapeCaptureResult(Rect(20, 10, 80, 50))
				)
			)
			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 0,
					kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					current = landscapeCaptureResult()
				)
			)
			val result = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView = webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				reference = reference
			) { result.complete(it) }

			assertNull(result.await())
			assertEquals(listOf("descriptor:4", "persistent:4"), events)
			assertFalse(
				source.hasSnapshot(
					4,
					ReaderPageTurnTransitionKind.LandscapeSpreadSlide
				)
			)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun preparedSnapshotMetadataComesFromTheExposedDestinationCapture() {
		val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
		val captured = ReaderPageTurnCaptureResult(
			bitmap = bitmap,
			sourceRectInWindow = Rect(4, 5, 24, 35),
			geometry = ReaderPageTurnCaptureGeometry(
				viewportWidth = 20.0,
				viewportHeight = 30.0,
				mode = ReaderPageTurnLayoutMode.Spread,
				pages = listOf(
					ReaderPageTurnPageRect(
						ReaderPageTurnPageRole.Left,
						0.0,
						0.0,
						10.0,
						30.0
					),
					ReaderPageTurnPageRect(
						ReaderPageTurnPageRole.Right,
						10.0,
						0.0,
						10.0,
						30.0
					)
				),
				reverseFaceColorArgb = 0xff123456L
			),
			elapsedMs = 7L
		)
		try {
			val geometry = assertNotNull(
				readerPagePreparedSnapshotGeometry(
					ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
					captured
				)
			)

			assertEquals(Rect(4, 5, 24, 35), geometry.surfaceRectInWindow)
			assertNull(geometry.leafGeometry.fullLeafRect)
			assertEquals(
				ReaderPageTurnPixelRect(0, 0, 10, 30),
				geometry.leafGeometry.leftLeafRect
			)
			assertEquals(
				ReaderPageTurnPixelRect(10, 0, 20, 30),
				geometry.leafGeometry.rightLeafRect
			)
			assertEquals(0xff123456.toInt(), geometry.reverseFaceColor)
			assertNull(
				readerPagePreparedSnapshotGeometry(
					ReaderPageTurnTransitionKind.PortraitSlide,
					captured
				)
			)
		} finally {
			bitmap.recycle()
		}
	}

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
	fun preemptionAfterPublicationAdmissionCannotCommitAStaleRaster() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val pageIndex = 18
		val webView = RasterDescriptorWebView(activity.get(), pageIndex)
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val ownershipChanges = Channel<Unit>(Channel.UNLIMITED)
		val qaFaultRegistry = ReaderPageQaFaultRegistry(
			onOwnershipMutated = { ownershipChanges.trySend(Unit) }
		)
		val source = ReaderPageTurnBundleSource(
			qaFaultRegistry = qaFaultRegistry,
			onOwnershipMutated = { ownershipChanges.trySend(Unit) }
		)
		val publicationResult = CompletableDeferred<ReaderPageRasterPublicationResult>()
		val foregroundOwnership = ReaderForegroundWebViewOwnership()
		val passiveLease = assertNotNull(
			foregroundOwnership.tryAcquirePassive(
				sessionId = 7_001L,
				cancelAndRestore = { complete ->
					complete(ReaderPageRasterCancellationRestoration.Restored)
				}
			)
		)
		var liveClaim: ReaderForegroundWebViewLiveClaim? = null
		try {
			val snapshot = assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = pageIndex,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					current = captureResult()
				)
			)
			source.initializeRasterCache(webView)
			val activated = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView = webView,
				pageIndex = pageIndex,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = snapshot
			) { activated.complete(it) }
			assertNotNull(activated.await()).release()
			val baselineDiskEntries = source.rasterCacheMetrics().diskEntries
			assertTrue(
				qaFaultRegistry.enqueue(
					requestId = "stale-publication-after-admission",
					fault = ReaderPageQaFault.PauseNextPublication
				)
			)

			source.ensurePersistentSnapshot(
				snapshot = snapshot,
				priority = ReaderPageRasterPriority.Current,
				isStillCurrent = { foregroundOwnership.isCurrent(passiveLease) },
				onPersisted = publicationResult::complete
			)
			withContext(Dispatchers.Default.limitedParallelism(1)) {
				withTimeout(10_000L) {
					while (
						qaFaultRegistry.pendingCallbackCount() == 0 &&
						!publicationResult.isCompleted
					) {
						ownershipChanges.receive()
					}
				}
			}
			assertFalse(
				publicationResult.isCompleted,
				"Publication failed before reaching the post-admission QA gate"
			)

			liveClaim = foregroundOwnership.acquireLive(gestureId = 7_002L)
			assertFalse(foregroundOwnership.isCurrent(passiveLease))
			assertTrue(qaFaultRegistry.releasePublication("release-stale-publication"))
			withContext(Dispatchers.Default.limitedParallelism(1)) {
				withTimeout(10_000L) {
					while (source.ownershipMetrics().stagedPublications != 0) {
						ownershipChanges.receive()
					}
				}
			}
			source.closeAndJoin()

			assertEquals(
				baselineDiskEntries,
				source.rasterCacheMetrics().diskEntries,
				"A passive publication invalidated after admission must be rolled back"
			)
		} finally {
			liveClaim?.let(foregroundOwnership::releaseLive)
			foregroundOwnership.releasePassive(passiveLease)
			qaFaultRegistry.clear("clear-stale-publication-test")
			source.closeAndJoin()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun capturedSnapshotHydrationStillRequiresPublication() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val source = ReaderPageTurnBundleSource()
		val reference = referenceSnapshot()
		try {
			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 4,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					current = captureResult()
				)
			)
			val result = CompletableDeferred<ReaderPageRasterHydrationResult?>()

			source.hydrateSnapshotWithDurability(
				webView = webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { result.complete(it) }

			val hydrated = assertNotNull(result.await())
			assertEquals(
				ReaderPageRasterHydrationDurability.RequiresPublication,
				hydrated.durability
			)
			hydrated.snapshot.release()
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun unprotectedCachedPersistentSnapshotRequiresPublicationAgain() = runTest {
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
		try {
			val first = CompletableDeferred<ReaderPageRasterHydrationResult?>()
			source.hydrateSnapshotWithDurability(
				webView = webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { first.complete(it) }
			val persistent = assertNotNull(first.await())
			assertEquals(
				ReaderPageRasterHydrationDurability.PersistentStoreVerified,
				persistent.durability
			)
			persistent.snapshot.release()

			source.protectEncodedWindow(centerPageIndex = 10, step = 1, pageCount = 20)
			val retained = CompletableDeferred<ReaderPageRasterHydrationResult?>()
			source.hydrateSnapshotWithDurability(
				webView = webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { retained.complete(it) }

			val unprotected = assertNotNull(retained.await())
			assertEquals(
				ReaderPageRasterHydrationDurability.RequiresPublication,
				unprotected.durability
			)
			unprotected.snapshot.release()
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun cachedDescriptorBypassesWebViewDuringRapidTurnHydration() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val descriptors = FakeDescriptorPort(events)
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptors,
			hydrationStorePort = FakeHydrationStore(events, durablePages = setOf(4))
		)
		val reference = referenceSnapshot(Rect(0, 0, 40, 60))
		try {
			val first = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { first.complete(it) }
			assertNotNull(first.await()).release()
			source.invalidatePage(4, "exercise-descriptor-cache")
			events.clear()

			val second = CompletableDeferred<ReaderPageSlideSnapshot?>()
			source.hydrateSnapshot(
				webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { second.complete(it) }

			assertNotNull(second.await()).release()
			assertEquals(listOf("persistent:4"), events)
			assertEquals(1, descriptors.requestCount)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun hydratedRecipientLeaseSurvivesImmediateCacheTrim() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val events = mutableListOf<String>()
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(events),
			hydrationStorePort = FakeHydrationStore(events, durablePages = setOf(6))
		)
		val reference = referenceSnapshot()
		try {
			repeat(5) { pageIndex ->
				assertNotNull(
					source.cacheCurrentSnapshot(
						pageIndex = pageIndex,
						kind = ReaderPageTurnTransitionKind.PortraitSlide,
						current = captureResult()
					)
				)
			}
			source.protectDecodedPageIndices((0 until 5).toSet())
			val result = CompletableDeferred<ReaderPageSlideSnapshot?>()

			source.hydrateSnapshot(
				webView = webView,
				pageIndex = 6,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { result.complete(it) }

			val hydrated = assertNotNull(result.await())
			assertFalse(hydrated.bitmap.isRecycled)
			assertFalse(
				source.hasSnapshot(
					6,
					ReaderPageTurnTransitionKind.PortraitSlide
				)
			)
			hydrated.release()
			assertTrue(hydrated.bitmap.isRecycled)
		} finally {
			source.close()
			reference.releaseCacheOwnership()
			activity.destroy()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun warmPersistentHitCompletesWithoutRepublishingTheHydratedRaster() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = WebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val messages = mutableListOf<String>()
		val events = mutableListOf<String>()
		val diagnostics = ReaderPageRuntimeDiagnostics(
			readerSession = 17L,
			nowMs = { 25L },
			emit = messages::add
		)
		val source = ReaderPageTurnBundleSource(
			descriptorPort = FakeDescriptorPort(events),
			hydrationStorePort = FakeHydrationStore(events, durablePages = setOf(4))
		)
		val controller = ReaderPageRasterBatchController(source, diagnostics)
		val reference = referenceSnapshot()
		val durableTargets = mutableListOf<Int>()
		try {
			val firstOutcome = CompletableDeferred<ReaderPageRasterBatchOutcome>()
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
					onStagingStarted = { _, onPresented -> onPresented(true) },
					onTargetDurable = { target -> durableTargets += target.pageIndex },
					onComplete = firstOutcome::complete
				)
			)

			assertEquals(ReaderPageRasterBatchOutcome.Ready, firstOutcome.await())
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
			assertEquals(listOf(4), durableTargets)

			events.clear()
			messages.clear()
			val retainedOutcome = CompletableDeferred<ReaderPageRasterBatchOutcome>()
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
					trigger = ReaderPageRasterAcquisitionTrigger.WorkingSetRefill,
					onStagingStarted = { _, onPresented -> onPresented(true) },
					onTargetDurable = { target -> durableTargets += target.pageIndex },
					onComplete = retainedOutcome::complete
				)
			)

			assertEquals(ReaderPageRasterBatchOutcome.Ready, retainedOutcome.await())
			assertTrue(events.isEmpty())
			assertFalse(messages.any { it.contains("source=WebViewCapture") })
			assertEquals(listOf(4, 4), durableTargets)
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

			events.clear()
			val cacheOnlyMissing = loader.load(
				ReaderPlayLikeCurlRasterKey(
					profile = profile,
					pageIndex = 6,
					missingRasterPolicy = ReaderPlayLikeCurlMissingRasterPolicy.CacheOnly
				)
			)
			assertNull(cacheOnlyMissing)
			assertEquals(listOf("descriptor:6", "persistent:6"), events)
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

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean = false
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
	fun repeatedBatchCancellationJoinsTheOriginalRestorationTerminal() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val webView = DelayedRestorationWebView(activity.get())
		activity.get().setContentView(webView)
		Shadows.shadowOf(Looper.getMainLooper()).idle()
		val descriptors = FakeDescriptorPort(mutableListOf(), automatic = false)
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptors,
			hydrationStorePort = FakeHydrationStore(mutableListOf(), setOf(14))
		)
		val controller = ReaderPageRasterBatchController(source)
		val reference = referenceSnapshot()
		val outcomes = mutableListOf<ReaderPageRasterBatchOutcome>()
		val firstRestoration = mutableListOf<ReaderPageRasterCancellationRestoration>()
		val secondRestoration = mutableListOf<ReaderPageRasterCancellationRestoration>()
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
					onStagingStarted = { _, onPresented -> onPresented(true) },
					onComplete = outcomes::add
				)
			)

			controller.cancel(firstRestoration::add)
			controller.cancel(secondRestoration::add)

			assertEquals(
				listOf<ReaderPageRasterBatchOutcome>(ReaderPageRasterBatchOutcome.Cancelled),
				outcomes
			)
			assertTrue(firstRestoration.isEmpty())
			assertTrue(secondRestoration.isEmpty())

			webView.completeRestoration()
			Shadows.shadowOf(Looper.getMainLooper()).idle()

			assertEquals(
				listOf(ReaderPageRasterCancellationRestoration.Restored),
				firstRestoration
			)
			assertEquals(
				listOf(ReaderPageRasterCancellationRestoration.Restored),
				secondRestoration
			)
		} finally {
			webView.completeRestorationIfPending()
			controller.cancel()
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
					onStagingStarted = { _, onPresented -> onPresented(true) },
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

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean {
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

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean = false
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

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean = false
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

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean = false
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

	private class DelayedRestorationWebView(context: Context) : WebView(context) {
		private var evaluationCallback: ValueCallback<String>? = null
		private var visualStateCallback: Pair<Long, VisualStateCallback>? = null

		override fun evaluateJavascript(
			script: String,
			resultCallback: ValueCallback<String>?
		) {
			check(evaluationCallback == null)
			evaluationCallback = resultCallback
		}

		override fun postVisualStateCallback(
			requestId: Long,
			callback: VisualStateCallback
		) {
			check(visualStateCallback == null)
			visualStateCallback = requestId to callback
		}

		fun completeRestoration() {
			val evaluation = checkNotNull(evaluationCallback)
			evaluationCallback = null
			evaluation.onReceiveValue("null")
			val (requestId, visual) = checkNotNull(visualStateCallback)
			visualStateCallback = null
			visual.onComplete(requestId)
		}

		fun completeRestorationIfPending() {
			if (evaluationCallback != null) completeRestoration()
		}
	}

	private class RasterDescriptorWebView(
		context: Context,
		private val pageIndex: Int
	) : WebView(context) {
		override fun evaluateJavascript(
			script: String,
			resultCallback: ValueCallback<String>?
		) {
			val result = if (script.contains("pageTurnRasterDescriptor")) {
				"""{
					"publicationUrl":"publication-stale-fence",
					"paginationFingerprint":"pagination-stale-fence",
					"layoutFingerprint":"layout-stale-fence",
					"decorationFingerprint":"decoration-stale-fence",
					"viewportWidth":20,
					"viewportHeight":30,
					"pageCount":20,
					"spineIndex":0,
					"href":"chapter-stale-fence",
					"chapterPageIndex":$pageIndex,
					"chapterPageCount":20,
					"visualPageOrdinal":$pageIndex
				}""".trimIndent()
			} else {
				"null"
			}
			resultCallback?.onReceiveValue(result)
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

		override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean = false
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

private fun persistentLandscapeRaster(
	key: ReaderPageRasterKey,
	left: ReaderPageRasterRect,
	right: ReaderPageRasterRect
): ReaderPageRaster<Bitmap> = ReaderPageRaster(
	key = key,
	metadata = ReaderPageRasterMetadata(
		surfaceLeft = 0,
		surfaceTop = 0,
		surfaceRight = 100,
		surfaceBottom = 60,
		fullLeafRect = null,
		leftLeafRect = left,
		gutterRect = ReaderPageRasterRect(left.right, 0, right.left, 60),
		rightLeafRect = right,
		reverseFaceColor = 0xffead9ae.toInt()
	),
	value = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
)

private fun landscapeCaptureResult(
	surfaceRectInWindow: Rect = Rect(0, 0, 100, 60)
): ReaderPageTurnCaptureResult = ReaderPageTurnCaptureResult(
	bitmap = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888),
	sourceRectInWindow = Rect(surfaceRectInWindow),
	geometry = ReaderPageTurnCaptureGeometry(
		viewportWidth = 100.0,
		viewportHeight = 60.0,
		mode = ReaderPageTurnLayoutMode.Spread,
		pages = listOf(
			ReaderPageTurnPageRect(
				role = ReaderPageTurnPageRole.Left,
				left = 0.0,
				top = 0.0,
				width = 50.0,
				height = 60.0
			),
			ReaderPageTurnPageRect(
				role = ReaderPageTurnPageRole.Right,
				left = 50.0,
				top = 0.0,
				width = 50.0,
				height = 60.0
			)
		)
	),
	elapsedMs = 1L
)

private fun captureResult(): ReaderPageTurnCaptureResult = ReaderPageTurnCaptureResult(
	bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888),
	sourceRectInWindow = Rect(0, 0, 20, 30),
	geometry = ReaderPageTurnCaptureGeometry(
		viewportWidth = 20.0,
		viewportHeight = 30.0,
		mode = ReaderPageTurnLayoutMode.Single,
		pages = listOf(
			ReaderPageTurnPageRect(
				role = ReaderPageTurnPageRole.Full,
				left = 0.0,
				top = 0.0,
				width = 20.0,
				height = 30.0
			)
		)
	),
	elapsedMs = 1L
)

private fun landscapeReferenceSnapshot(
	surfaceRectInWindow: Rect = Rect(0, 0, 100, 60),
	left: ReaderPageTurnPixelRect = ReaderPageTurnPixelRect(0, 0, 50, 60),
	right: ReaderPageTurnPixelRect = ReaderPageTurnPixelRect(50, 0, 100, 60)
): ReaderPageSlideSnapshot = ReaderPageSlideSnapshot(
	key = ReaderPageSlideSnapshotKey(
		visualPageIndex = 0,
		kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
		bitmapQuality = ReaderPageBitmapQuality.Balanced,
		bitmapWidth = 100,
		bitmapHeight = 60,
		surfaceWidth = surfaceRectInWindow.width(),
		surfaceHeight = surfaceRectInWindow.height()
	),
	bitmap = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888),
	surfaceRectInWindow = Rect(surfaceRectInWindow),
	leafGeometry = ReaderPageTurnLeafGeometry(
		fullLeafRect = null,
		leftLeafRect = left,
		gutterRect = ReaderPageTurnPixelRect(left.right, 0, right.left, 60),
		rightLeafRect = right
	),
	reverseFaceColor = 0xffead9ae.toInt()
)

private fun referenceSnapshot(
	surfaceRectInWindow: Rect = Rect(0, 0, 20, 30)
): ReaderPageSlideSnapshot = ReaderPageSlideSnapshot(
	key = ReaderPageSlideSnapshotKey(
		visualPageIndex = 0,
		kind = ReaderPageTurnTransitionKind.PortraitSlide,
		bitmapQuality = ReaderPageBitmapQuality.Balanced,
		bitmapWidth = 20,
		bitmapHeight = 30,
		surfaceWidth = surfaceRectInWindow.width(),
		surfaceHeight = surfaceRectInWindow.height()
	),
	bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888),
	surfaceRectInWindow = Rect(surfaceRectInWindow),
	leafGeometry = ReaderPageTurnLeafGeometry(
		fullLeafRect = ReaderPageTurnPixelRect(0, 0, 20, 30),
		leftLeafRect = null,
		gutterRect = null,
		rightLeafRect = null
	),
	reverseFaceColor = 0xffead9ae.toInt()
)
