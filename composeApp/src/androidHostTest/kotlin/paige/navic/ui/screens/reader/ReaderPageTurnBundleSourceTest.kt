package paige.navic.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.webkit.ValueCallback
import android.webkit.WebView
import android.os.Handler
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.FrameLayout
import java.io.File
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

import kotlin.coroutines.CoroutineContext
import karacken.curl.PageSurfaceView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRelocationRequest
import paige.navic.reader.ReaderPageRelocationToken
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole
import paige.navic.reader.ReaderPageTurnPixelRect
import paige.navic.reader.ReaderTransitionFailureReason

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageTurnBundleSourceTest {
	@Test
	fun snapshotCacheFreezesDrainsAndRestoresItsStableExactOwner() = runTest {
		val source = ReaderPageTurnBundleSource()
		val cached = assertNotNull(
			source.cacheCurrentSnapshot(
				pageIndex = 2,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				current = captureResult(),
				persist = false
			)
		)
		val domain = ReaderLegacyPhysicalDomain(43L, ReaderLegacyFreezeToken(44L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val row = source.snapshotFrozenOwnership().single {
			it.physicalIdentity.source == ReaderLegacyInventorySource.RasterSnapshotCache
		}
		assertEquals(paige.navic.reader.ReaderTransitionResourceKind.Raster, row.kind)
		assertEquals(ReaderLegacyResourceState.Prepared, row.state)
		assertFalse(
			source.cacheCurrentSnapshot(
				pageIndex = 3,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				current = captureResult(),
				persist = false
			) != null
		)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
		)
		assertEquals(listOf(row.physicalIdentity), confirmed)
		assertFalse(source.hasSnapshot(2, ReaderPageTurnTransitionKind.PortraitSlide))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		assertTrue(source.hasSnapshot(2, ReaderPageTurnTransitionKind.PortraitSlide))
		val secondDomain = ReaderLegacyPhysicalDomain(43L, ReaderLegacyFreezeToken(45L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(secondDomain)
		)
		val restored = source.snapshotFrozenOwnership().single {
			it.physicalIdentity.source == ReaderLegacyInventorySource.RasterSnapshotCache
		}
		assertEquals(row.physicalIdentity.sourceLocalToken, restored.physicalIdentity.sourceLocalToken)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.drainFrozenOwnership(restored.physicalIdentity) {}
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(secondDomain)
		)
		assertNotNull(cached)
		source.closeAndJoin()
	}

	@Test
	fun restorationWithoutDrainPreservesUntouchedSnapshotOwnersAndTokens() = runTest {
		val source = ReaderPageTurnBundleSource()
		try {
			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 1,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					current = captureResult(),
					persist = false
				)
			)
			assertNotNull(
				source.cacheCurrentSnapshot(
					pageIndex = 2,
					kind = ReaderPageTurnTransitionKind.PortraitSlide,
					current = captureResult(),
					persist = false
				)
			)
			val firstDomain = ReaderLegacyPhysicalDomain(79L, ReaderLegacyFreezeToken(80L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(firstDomain)
			)
			val firstRows = source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterSnapshotCache
			}
			assertEquals(2, firstRows.size)

			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(firstDomain)
			)
			assertTrue(source.hasSnapshot(1, ReaderPageTurnTransitionKind.PortraitSlide))
			assertTrue(source.hasSnapshot(2, ReaderPageTurnTransitionKind.PortraitSlide))

			val secondDomain = ReaderLegacyPhysicalDomain(79L, ReaderLegacyFreezeToken(81L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(secondDomain)
			)
			val secondRows = source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterSnapshotCache
			}
			assertEquals(
				firstRows.map { it.physicalIdentity.sourceLocalToken }.toSet(),
				secondRows.map { it.physicalIdentity.sourceLocalToken }.toSet()
			)
			secondRows.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					source.drainFrozenOwnership(row.physicalIdentity) {}
				)
			}
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(secondDomain)
			)
		} finally {
			source.closeAndJoin()
		}
	}

	@Test
	fun partialSnapshotDrainRestoresDrainedAndUntouchedOwnersWithoutDoubleRelease() = runTest {
		val source = ReaderPageTurnBundleSource()
		val first = assertNotNull(
			source.cacheCurrentSnapshot(
				pageIndex = 1,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				current = captureResult(),
				persist = false
			)
		)
		val second = assertNotNull(
			source.cacheCurrentSnapshot(
				pageIndex = 2,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				current = captureResult(),
				persist = false
			)
		)
		try {
			val firstDomain = ReaderLegacyPhysicalDomain(81L, ReaderLegacyFreezeToken(82L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(firstDomain)
			)
			val firstRows = source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterSnapshotCache
			}
			assertEquals(2, firstRows.size)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(firstRows.first().physicalIdentity) {}
			)
			assertEquals(1, source.snapshotFrozenOwnership().count {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterSnapshotCache
			})

			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(firstDomain)
			)
			assertTrue(source.hasSnapshot(1, ReaderPageTurnTransitionKind.PortraitSlide))
			assertTrue(source.hasSnapshot(2, ReaderPageTurnTransitionKind.PortraitSlide))

			val secondDomain = ReaderLegacyPhysicalDomain(81L, ReaderLegacyFreezeToken(83L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(secondDomain)
			)
			val secondRows = source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterSnapshotCache
			}
			assertEquals(
				firstRows.map { it.physicalIdentity.sourceLocalToken }.toSet(),
				secondRows.map { it.physicalIdentity.sourceLocalToken }.toSet()
			)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(secondDomain)
			)
		} finally {
			source.closeAndJoin()
		}
		assertTrue(first.bitmap.isRecycled)
		assertTrue(second.bitmap.isRecycled)
	}

	@Test
	fun frozenPageInvalidationPreservesStableSnapshotOwnerForDrainAndRestore() = runTest {
		val source = ReaderPageTurnBundleSource()
		val cached = assertNotNull(
			source.cacheCurrentSnapshot(
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				current = captureResult(),
				persist = false
			)
		)
		try {
			val domain = ReaderLegacyPhysicalDomain(89L, ReaderLegacyFreezeToken(90L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val frozenRow = source.snapshotFrozenOwnership().single {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterSnapshotCache
			}

			source.invalidatePage(4, "frozen-page-regression")

			assertTrue(source.hasSnapshot(4, ReaderPageTurnTransitionKind.PortraitSlide))
			assertEquals(
				frozenRow.physicalIdentity,
				source.snapshotFrozenOwnership().single {
					it.physicalIdentity.source ==
						ReaderLegacyInventorySource.RasterSnapshotCache
				}.physicalIdentity
			)
			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(
					frozenRow.physicalIdentity,
					confirmations::add
				)
			)
			assertEquals(listOf(frozenRow.physicalIdentity), confirmations)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
			assertTrue(source.hasSnapshot(4, ReaderPageTurnTransitionKind.PortraitSlide))

			val secondDomain = ReaderLegacyPhysicalDomain(89L, ReaderLegacyFreezeToken(91L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(secondDomain)
			)
			val restoredRow = source.snapshotFrozenOwnership().single {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterSnapshotCache
			}
			assertEquals(
				frozenRow.physicalIdentity.sourceLocalToken,
				restoredRow.physicalIdentity.sourceLocalToken
			)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(secondDomain)
			)
		} finally {
			source.closeAndJoin()
		}
		assertTrue(cached.bitmap.isRecycled)
	}

	@Test
	fun frozenHydrationCannotRetainOrDeliverCachedSnapshot() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBundleSource()
		val cached = assertNotNull(
			source.cacheCurrentSnapshot(
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				current = captureResult(),
				persist = false
			)
		)
		val domain = ReaderLegacyPhysicalDomain(101L, ReaderLegacyFreezeToken(102L))
		var restored = false
		try {
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val frozenRows = source.snapshotFrozenOwnership()
				.filter { row ->
					row.physicalIdentity.source ==
						ReaderLegacyInventorySource.RasterSnapshotCache
				}
			val delivered = mutableListOf<ReaderPageRasterHydrationResult?>()

			source.hydrateSnapshotWithDurability(
				webView = webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = cached,
				onHydrated = delivered::add
			)

			val result = delivered.single()
			result?.snapshot?.release()
			assertNull(result)
			assertEquals(
				frozenRows,
				source.snapshotFrozenOwnership().filter { row ->
					row.physicalIdentity.source ==
						ReaderLegacyInventorySource.RasterSnapshotCache
				}
			)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
			restored = true
		} finally {
			if (!restored) source.restoreAfterTransitionActivation(domain)
			source.closeAndJoin()
		}
		assertTrue(cached.bitmap.isRecycled)
	}

	@Test
	fun frozenPreparedCaptureCannotChangeLayoutOrInvalidatePublications() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBundleSource()
		val reference = slideSnapshot(Rect(100, 0, 120, 30))
		val domain = ReaderLegacyPhysicalDomain(103L, ReaderLegacyFreezeToken(104L))
		var restored = false
		try {
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val layoutEpoch = rasterPhysicalLayoutEpoch(source)
			val publicationEpoch = publicationLedger(source).currentEpoch()
			var captureFailures = 0
			val captured = mutableListOf<ReaderPageRasterPublicationCompletion>()

			source.capturePreparedRasterPage(
				webView = webView,
				pageIndex = 4,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference,
				itemToken = "frozen-layout",
				previewGeneration = 1L,
				priority = paige.navic.reader.ReaderPageRasterPriority.Current,
				mutationGeneration = ReaderForegroundWebViewMutationGeneration(1L),
				onStagingStarted = { _, _ -> },
				onCaptureFailed = { captureFailures += 1 },
				onCaptured = captured::add
			)

			assertEquals(1, captureFailures)
			assertTrue(captured.isEmpty())
			assertEquals(layoutEpoch, rasterPhysicalLayoutEpoch(source))
			assertEquals(publicationEpoch, publicationLedger(source).currentEpoch())
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
			restored = true
		} finally {
			if (!restored) source.restoreAfterTransitionActivation(domain)
			reference.releaseCacheOwnership()
			source.closeAndJoin()
		}
		assertTrue(reference.bitmap.isRecycled)
	}

	@Test
	fun bundleFreezeSnapshotsDrainsAndRestoresItsExactSchedulerOwners() = runTest {
		val hydration = ReaderPageRasterHydrationScheduler(backgroundScope, 1)
		val publicationTokens = ReaderLegacySourceLocalTokenAllocator()
		val publication = ReaderPageRasterPublicationScheduler(
			backgroundScope,
			1,
			publicationTokens
		)
		val hydrationStarted = CompletableDeferred<Unit>()
		val publicationStarted = CompletableDeferred<Unit>()
		val hold = CompletableDeferred<Unit>()
		checkNotNull(hydration.schedule {
			hydrationStarted.complete(Unit)
			hold.await()
		})
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publication.schedule(ReaderPageRasterPublicationRequest("owned", 1L)) {
				publicationStarted.complete(Unit)
				hold.await()
			}
		)
		hydrationStarted.await()
		publicationStarted.await()
		val descriptorTokens = ReaderLegacySourceLocalTokenAllocator()
		val descriptorOwners = ReaderPagePendingCallbackOwners<ReaderPageSlideSnapshot>(
			retain = ReaderPageSlideSnapshot::retain,
			release = ReaderPageSlideSnapshot::release,
			tokenAllocator = descriptorTokens
		)
		val descriptorSnapshot = ReaderPageSlideSnapshot(
			key = ReaderPageSlideSnapshotKey(
				visualPageIndex = 0,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				bitmapQuality = ReaderPageBitmapQuality.Balanced,
				bitmapWidth = 1,
				bitmapHeight = 1,
				surfaceWidth = 1,
				surfaceHeight = 1
			),
			bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
			surfaceRectInWindow = Rect(0, 0, 1, 1),
			leafGeometry = ReaderPageTurnLeafGeometry(
				fullLeafRect = ReaderPageTurnPixelRect(0, 0, 1, 1),
				leftLeafRect = null,
				gutterRect = null,
				rightLeafRect = null
			),
			reverseFaceColor = Color.WHITE
		)
		var descriptorAbandoned = false
		checkNotNull(descriptorOwners.acquire(descriptorSnapshot) {
			descriptorAbandoned = true
		})
		val source = ReaderPageTurnBundleSource(
			publicationOwnershipTokenAllocator = publicationTokens,
			descriptorOwnershipTokenAllocator = descriptorTokens,
			hydrationSchedulerOverride = hydration,
			publicationSchedulerOverride = publication,
			pendingDescriptorOwnersOverride = descriptorOwners
		)
		source.setPublicationCapacityAvailableListener { }
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 41L,
			freezeToken = ReaderLegacyFreezeToken(42L)
		)

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership()
		assertEquals(4, rows.size)
		assertEquals(
			setOf(
				ReaderLegacyInventorySource.RasterHydration,
				ReaderLegacyInventorySource.RasterPublication,
				ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback
			),
			rows.map { it.physicalIdentity.source }.toSet()
		)
		assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)
		assertTrue(rows.all { it.physicalIdentity.domain == domain })
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		runCurrent()
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertTrue(source.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		assertTrue(checkNotNull(hydration.schedule { }).also { runCurrent() }.isCompleted)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publication.schedule(ReaderPageRasterPublicationRequest("restored", 1L)) { }
		)
		hold.complete(Unit)
		source.closeAndJoin()
		assertTrue(descriptorAbandoned)
		descriptorSnapshot.releaseCacheOwnership()
	}

	@Test
	fun compositeRestorationRetriesAfterLaterClaimedDescriptorDrainSettles() = runTest {
		val descriptorTokens = ReaderLegacySourceLocalTokenAllocator()
		val descriptorOwners = ReaderPagePendingCallbackOwners<ReaderPageSlideSnapshot>(
			retain = ReaderPageSlideSnapshot::retain,
			release = ReaderPageSlideSnapshot::release,
			tokenAllocator = descriptorTokens
		)
		val retained = slideSnapshot()
		val lease = assertNotNull(descriptorOwners.acquire(retained) {})
		val claimed = assertNotNull(descriptorOwners.claim(lease))
		val source = ReaderPageTurnBundleSource(
			descriptorOwnershipTokenAllocator = descriptorTokens,
			pendingDescriptorOwnersOverride = descriptorOwners
		)
		try {
			val domain = ReaderLegacyPhysicalDomain(83L, ReaderLegacyFreezeToken(84L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val descriptorRow = source.snapshotFrozenOwnership().single {
				it.physicalIdentity.source ==
					ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback
			}
			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(descriptorRow.physicalIdentity, confirmations::add)
			)
			assertTrue(confirmations.isEmpty())

			assertEquals(
				ReaderPortCommandResult.Rejected(
					ReaderTransitionFailureReason.InvalidLegacyResource
				),
				source.restoreAfterTransitionActivation(domain)
			)
			assertEquals(
				setOf(domain),
				assertNotNull(source.snapshotConnectedFrozenOwnership())
					.map { it.domain }
					.toSet()
			)

			descriptorOwners.complete(claimed)
			assertEquals(listOf(descriptorRow.physicalIdentity), confirmations)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
			assertEquals(null, source.snapshotConnectedFrozenOwnership())
		} finally {
			if (!retained.bitmap.isRecycled) {
				retained.releaseCacheOwnership()
			}
			source.closeAndJoin()
		}
	}

	@Test
	fun pendingDescriptorRequestAndRecipientFreezeAsExactOwnersBeforeLateAdmission() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val descriptorPort = HeldDescriptorPort()
		val source = ReaderPageTurnBundleSource(descriptorPort = descriptorPort)
		val reference = slideSnapshot()
		val results = mutableListOf<ReaderPageSlideSnapshot?>()
		source.hydrateSnapshot(
			webView = webView,
			pageIndex = 1,
			kind = ReaderPageTurnTransitionKind.PortraitSlide,
			reference = reference,
			onHydrated = results::add
		)
		assertEquals(1, descriptorPort.requestCount)
		val domain = ReaderLegacyPhysicalDomain(53L, ReaderLegacyFreezeToken(54L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership().filter {
			it.physicalIdentity.source ==
				ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback
		}
		assertEquals(2, rows.size)
		assertEquals(
			setOf(
				paige.navic.reader.ReaderTransitionResourceKind.Raster,
				paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
			),
			rows.map { it.kind }.toSet()
		)
		assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)

		source.hydrateSnapshot(
			webView = webView,
			pageIndex = 2,
			kind = ReaderPageTurnTransitionKind.PortraitSlide,
			reference = reference,
			onHydrated = results::add
		)
		assertEquals(1, descriptorPort.requestCount)
		assertEquals(listOf<ReaderPageSlideSnapshot?>(null), results)

		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		descriptorPort.respond(1)
		assertEquals(listOf<ReaderPageSlideSnapshot?>(null), results)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		reference.releaseCacheOwnership()
		source.closeAndJoin()
	}

	@Test
	fun invalidationCompletesPendingDescriptorRequestAndRecipientOwners() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val descriptorPort = HeldDescriptorPort()
		val source = ReaderPageTurnBundleSource(descriptorPort = descriptorPort)
		val reference = slideSnapshot()
		val results = mutableListOf<ReaderPageSlideSnapshot?>()
		try {
			source.hydrateSnapshot(
				webView = webView,
				pageIndex = 1,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference,
				onHydrated = results::add
			)
			assertEquals(1, descriptorPort.requestCount)

			source.invalidate("test")
			assertEquals(listOf<ReaderPageSlideSnapshot?>(null), results)
			val domain = ReaderLegacyPhysicalDomain(59L, ReaderLegacyFreezeToken(60L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			assertTrue(source.snapshotFrozenOwnership().none {
				it.physicalIdentity.source ==
					ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback
			})
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
			descriptorPort.respond(1)
			assertEquals(listOf<ReaderPageSlideSnapshot?>(null), results)
		} finally {
			reference.releaseCacheOwnership()
			source.closeAndJoin()
		}
	}

	@Test
	fun hydrationWorkerAndRecipientRemainExactOwnersUntilPhysicalReadCompletes() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val descriptorPort = HeldDescriptorPort()
		val readStarted = CompletableDeferred<Unit>()
		val allowReadCompletion = CompletableDeferred<Unit>()
		val readFinished = CompletableDeferred<Unit>()
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap>? {
				readStarted.complete(Unit)
				try {
					allowReadCompletion.await()
				} finally {
					readFinished.complete(Unit)
				}
				return null
			}

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean = false
		}
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptorPort,
			hydrationStorePort = store
		)
		val reference = slideSnapshot()
		val results = mutableListOf<ReaderPageSlideSnapshot?>()
		val originalTerminal = CompletableDeferred<Unit>()
		source.hydrateSnapshot(
			webView = webView,
			pageIndex = 1,
			kind = ReaderPageTurnTransitionKind.PortraitSlide,
			reference = reference
		) { result ->
			results += result
			originalTerminal.complete(Unit)
		}
		descriptorPort.respond(1)
		readStarted.await()
		val domain = ReaderLegacyPhysicalDomain(55L, ReaderLegacyFreezeToken(56L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership().filter {
			it.physicalIdentity.source == ReaderLegacyInventorySource.RasterHydration
		}
		assertEquals(3, rows.size)
		assertEquals(
			setOf(
				paige.navic.reader.ReaderTransitionResourceKind.Raster,
				paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
			),
			rows.map { it.kind }.toSet()
		)
		assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)

		source.hydrateSnapshot(
			webView = webView,
			pageIndex = 2,
			kind = ReaderPageTurnTransitionKind.PortraitSlide,
			reference = reference,
			onHydrated = results::add
		)
		assertEquals(1, descriptorPort.requestCount)
		assertEquals(listOf<ReaderPageSlideSnapshot?>(null), results)

		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		val rasterRows = rows.filter {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.Raster
		}
		rasterRows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertTrue(confirmed.isEmpty())
		allowReadCompletion.complete(Unit)
		readFinished.await()
		org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
		runCurrent()
		originalTerminal.await()
		assertEquals(rasterRows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		val callbackRow = rows.single {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		}
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.drainFrozenOwnership(callbackRow.physicalIdentity, confirmed::add)
		)
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		reference.releaseCacheOwnership()
		source.closeAndJoin()
	}

	@Test
	fun invalidationReleasesHydrationRecipientBeforePhysicalReadCompletion() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val descriptorPort = HeldDescriptorPort()
		val readStarted = CompletableDeferred<Unit>()
		val allowReadCompletion = CompletableDeferred<Unit>()
		val readFinished = CompletableDeferred<Unit>()
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap>? {
				readStarted.complete(Unit)
				try {
					allowReadCompletion.await()
				} finally {
					readFinished.complete(Unit)
				}
				return null
			}

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean = false
		}
		val hydrationTokens = ReaderLegacySourceLocalTokenAllocator()
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptorPort,
			hydrationStorePort = store,
			hydrationOwnershipTokenAllocator = hydrationTokens,
			hydrationSchedulerOverride = ReaderPageRasterHydrationScheduler(
				backgroundScope,
				1,
				hydrationTokens
			)
		)
		val reference = slideSnapshot()
		val results = mutableListOf<ReaderPageSlideSnapshot?>()
		try {
			source.hydrateSnapshot(
				webView = webView,
				pageIndex = 1,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference,
				onHydrated = results::add
			)
			descriptorPort.respond(1)
			readStarted.await()
			source.invalidate("test")
			assertEquals(listOf<ReaderPageSlideSnapshot?>(null), results)
			val domain = ReaderLegacyPhysicalDomain(67L, ReaderLegacyFreezeToken(68L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val rows = source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterHydration
			}
			allowReadCompletion.complete(Unit)
			readFinished.await()
			runCurrent()
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
			runCurrent()
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
			assertEquals(2, rows.size)
			assertTrue(rows.all {
				it.kind == paige.navic.reader.ReaderTransitionResourceKind.Raster
			})
		} finally {
			allowReadCompletion.complete(Unit)
			reference.releaseCacheOwnership()
			source.closeAndJoin()
		}
	}

	@Test
	fun hydrationCompletionAfterFreezeCannotAdmitASnapshotCacheOwner() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val descriptorPort = HeldDescriptorPort()
		val readStarted = CompletableDeferred<Unit>()
		val allowReadCompletion = CompletableDeferred<Unit>()
		val readFinished = CompletableDeferred<Unit>()
		val decoded = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap> {
				readStarted.complete(Unit)
				try {
					allowReadCompletion.await()
				} finally {
					readFinished.complete(Unit)
				}
				return ReaderPageRaster(
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
						reverseFaceColor = Color.WHITE
					),
					value = decoded
				)
			}

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean = false
		}
		val hydrationTokens = ReaderLegacySourceLocalTokenAllocator()
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptorPort,
			hydrationStorePort = store,
			hydrationOwnershipTokenAllocator = hydrationTokens,
			hydrationSchedulerOverride = ReaderPageRasterHydrationScheduler(
				backgroundScope,
				1,
				hydrationTokens
			)
		)
		val reference = slideSnapshot()
		val results = mutableListOf<ReaderPageSlideSnapshot?>()
		val terminal = CompletableDeferred<Unit>()
		try {
			source.hydrateSnapshot(
				webView = webView,
				pageIndex = 1,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference
			) { result ->
				results += result
				terminal.complete(Unit)
			}
			descriptorPort.respond(1)
			readStarted.await()
			val domain = ReaderLegacyPhysicalDomain(57L, ReaderLegacyFreezeToken(58L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)

			allowReadCompletion.complete(Unit)
			readFinished.await()
			runCurrent()
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
			runCurrent()
			terminal.await()
			assertEquals(listOf<ReaderPageSlideSnapshot?>(null), results)
			assertTrue(source.snapshotFrozenOwnership().none {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterSnapshotCache
			})
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
		} finally {
			allowReadCompletion.complete(Unit)
			reference.releaseCacheOwnership()
			source.closeAndJoin()
		}
	}

	@Test
	fun mixedHydrationFinalizationRetainsExactOwnersWhenCacheAdmissionFreezes() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val descriptorPort = HeldDescriptorPort()
		val readStarted = CompletableDeferred<Unit>()
		val allowReadCompletion = CompletableDeferred<Unit>()
		val readFinished = CompletableDeferred<Unit>()
		val decoded = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
		val store = object : ReaderPageRasterHydrationStorePort {
			override suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap> {
				readStarted.complete(Unit)
				try {
					allowReadCompletion.await()
				} finally {
					readFinished.complete(Unit)
				}
				return ReaderPageRaster(
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
						reverseFaceColor = Color.WHITE
					),
					value = decoded
				)
			}

			override suspend fun remove(
				key: ReaderPageRasterKey,
				expectedMetadata: ReaderPageRasterMetadata
			): Boolean = false
		}
		val hydrationTokens = ReaderLegacySourceLocalTokenAllocator()
		val source = ReaderPageTurnBundleSource(
			descriptorPort = descriptorPort,
			hydrationStorePort = store,
			hydrationOwnershipTokenAllocator = hydrationTokens,
			hydrationSchedulerOverride = ReaderPageRasterHydrationScheduler(
				backgroundScope,
				1,
				hydrationTokens
			)
		)
		val reference = slideSnapshot()
		val results = mutableListOf<Pair<String, ReaderPageSlideSnapshot?>>()
		val domain = ReaderLegacyPhysicalDomain(69L, ReaderLegacyFreezeToken(70L))
		val firstRecipientEligible = AtomicBoolean(true)
		val finalizationCallback = CountDownLatch(1)
		var freezeResult: ReaderPortCommandResult? = null
		var rowsDuringIneligibleCallback = emptyList<ReaderFrozenLegacyResource>()
		try {
			source.hydrateSnapshot(
				webView = webView,
				pageIndex = 1,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference,
				publicationFence = firstRecipientEligible::get
			) { result ->
				results += "ineligible" to result
				if (result == null) {
					freezeResult = source.freezeForTransitionActivation(domain)
					rowsDuringIneligibleCallback = source.snapshotFrozenOwnership().filter {
						it.physicalIdentity.source ==
							ReaderLegacyInventorySource.RasterHydration
					}
					finalizationCallback.countDown()
				} else {
					result.release()
				}
			}
			source.hydrateSnapshot(
				webView = webView,
				pageIndex = 1,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				reference = reference,
				publicationFence = { true }
			) { result ->
				results += "eligible" to result
				result?.release()
			}
			assertEquals(1, descriptorPort.requestCount)
			descriptorPort.respond(1)
			readStarted.await()
			firstRecipientEligible.set(false)

			allowReadCompletion.complete(Unit)
			readFinished.await()
			for (attempt in 0 until 3) {
				runCurrent()
				org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
				runCurrent()
				if (finalizationCallback.count == 0L) break
				if (attempt < 2) finalizationCallback.await(5, TimeUnit.SECONDS)
			}
			assertEquals(0L, finalizationCallback.count)

			assertEquals(ReaderPortCommandResult.Accepted, freezeResult)
			assertEquals(
				listOf<Pair<String, ReaderPageSlideSnapshot?>>(
					"ineligible" to null,
					"eligible" to null
				),
				results
			)
			assertEquals(4, rowsDuringIneligibleCallback.size)
			assertEquals(
				2,
				rowsDuringIneligibleCallback.count {
					it.kind == paige.navic.reader.ReaderTransitionResourceKind.Raster
				}
			)
			assertEquals(
				2,
				rowsDuringIneligibleCallback.count {
					it.kind ==
						paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
				}
			)
			val finalizedRows = source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterHydration
			}
			assertEquals(
				rowsDuringIneligibleCallback.map { it.physicalIdentity }.toSet(),
				finalizedRows.map { it.physicalIdentity }.toSet()
			)
			assertTrue(finalizedRows.all { it.state == ReaderLegacyResourceState.Released })
			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			finalizedRows.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					source.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
			assertEquals(
				finalizedRows.map { it.physicalIdentity }.toSet(),
				confirmations.toSet()
			)
			assertEquals(confirmations.size, confirmations.toSet().size)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
		} finally {
			allowReadCompletion.complete(Unit)
			reference.releaseCacheOwnership()
			source.closeAndJoin()
		}
	}

	@Test
	fun bundleReportsExactlyEightConnectedSourcesIncludingTruthfulEmptyRows() = runTest {
		val source = ReaderPageTurnBundleSource()
		assertEquals(null, source.snapshotConnectedFrozenOwnership())
		val domain = ReaderLegacyPhysicalDomain(45L, ReaderLegacyFreezeToken(46L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)

		val inventories = assertNotNull(source.snapshotConnectedFrozenOwnership())
		assertEquals(
			setOf(
				ReaderLegacyInventorySource.RasterSnapshotCache,
				ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback,
				ReaderLegacyInventorySource.RasterHydration,
				ReaderLegacyInventorySource.RasterPublication,
				ReaderLegacyInventorySource.RasterGenerationAndPersistence,
				ReaderLegacyInventorySource.RasterCaptureAndVisualState,
				ReaderLegacyInventorySource.RasterLiveValidation,
				ReaderLegacyInventorySource.RasterStoreAndCache
			),
			inventories.map { it.source }.toSet()
		)
		assertTrue(inventories.all { it.domain == domain && it.resources.isEmpty() })
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		source.closeAndJoin()
	}

	@Test
	fun frozenColdRasterInitializationCannotCreateLateOwners() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		val source = ReaderPageTurnBundleSource()
		val domain = ReaderLegacyPhysicalDomain(47L, ReaderLegacyFreezeToken(48L))
		try {
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			assertTrue(
				assertNotNull(source.snapshotConnectedFrozenOwnership())
					.single { it.source == ReaderLegacyInventorySource.RasterGenerationAndPersistence }
					.resources
					.isEmpty()
			)

			assertFailsWith<CancellationException> {
				runBlocking { source.initializeRasterCache(webView) }
			}

			assertTrue(
				assertNotNull(source.snapshotConnectedFrozenOwnership())
					.single { it.source == ReaderLegacyInventorySource.RasterGenerationAndPersistence }
					.resources
					.isEmpty()
			)
			assertNull(bundleRasterScheduler(source))
			assertNull(bundleRasterCache(source))
			assertNull(bundlePersistentStore(source))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
		} finally {
			source.closeAndJoin()
		}
	}

	@Test
	fun frozenInFlightRasterInitializationIsDrainableBeforeItCanPublish() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		val source = ReaderPageTurnBundleSource()
		val initializationMutex = rasterInitializationMutex(source)
		val domain = ReaderLegacyPhysicalDomain(49L, ReaderLegacyFreezeToken(50L))
		var initialization: Deferred<Unit>? = null
		var mutexLocked = false
		try {
			withContext(Dispatchers.Main.immediate) {
				initializationMutex.lock()
				mutexLocked = true
				initialization = async(start = CoroutineStart.UNDISPATCHED) {
					source.initializeRasterCache(webView)
				}
				assertFalse(checkNotNull(initialization).isCompleted)
				assertEquals(
					ReaderPortCommandResult.Accepted,
					source.freezeForTransitionActivation(domain)
				)
				val row = source.snapshotFrozenOwnership().single {
					it.physicalIdentity.source ==
						ReaderLegacyInventorySource.RasterGenerationAndPersistence
				}
				val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
				assertEquals(
					ReaderPortCommandResult.Accepted,
					source.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
				assertTrue(confirmations.isEmpty())
				assertEquals(
					ReaderPortCommandResult.Rejected(
						ReaderTransitionFailureReason.InvalidLegacyResource
					),
					source.restoreAfterTransitionActivation(domain)
				)

				initializationMutex.unlock()
				mutexLocked = false
				val failure = try {
					checkNotNull(initialization).await()
					null
				} catch (caught: Throwable) {
					caught
				}
				assertTrue(failure is CancellationException)
				assertEquals(listOf(row.physicalIdentity), confirmations)
				assertNull(bundleRasterScheduler(source))
				assertNull(bundleRasterCache(source))
				assertNull(bundlePersistentStore(source))
				assertEquals(
					ReaderPortCommandResult.Accepted,
					source.restoreAfterTransitionActivation(domain)
				)
			}
		} finally {
			if (mutexLocked) initializationMutex.unlock()
			initialization?.cancel()
			try {
				source.closeAndJoin()
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	@Test
	fun activationBoundaryNativeMainCancellationRequiresLooperDeliveryBeforeTeardownCanSettle() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = DeferredJavascriptWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		var copiedBitmap: Bitmap? = null
		lateinit var source: ReaderPageTurnBundleSource
		source = ReaderPageTurnBundleSource(onOwnershipMutated = {
			if (copiedBitmap == null) copiedBitmap = ownedPersistenceBitmap(source)
		})
		val mutex = rasterInitializationMutex(source)
		val snapshot = assertNotNull(source.cacheCurrentSnapshot(2, ReaderPageTurnTransitionKind.PortraitSlide, captureResult(), persist = false))
		val completions = mutableListOf<ReaderPageRasterPublicationCompletion>()
		val confirmations = Collections.synchronizedList(mutableListOf<ReaderLegacyPhysicalIdentity>())
		var locked = false
		try {
			source.hydrateSnapshotWithDurability(webView, 2, snapshot.key.kind, snapshot) { it?.snapshot?.release() }
			mutex.lock()
			locked = true
			source.ensurePersistentSnapshot(snapshot, paige.navic.reader.ReaderPageRasterPriority.Current, onPersisted = completions::add)
			webView.completeNextJavascript(persistenceDescriptorJson(2))
			val domain = ReaderLegacyPhysicalDomain(125L, ReaderLegacyFreezeToken(126L))
			source.freezeForTransitionActivation(domain)
			val rows = source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterGenerationAndPersistence
			}
			assertEquals(2, rows.size)
			withContext(Dispatchers.Default) {
				rows.forEach { row ->
					assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(row.physicalIdentity, confirmations::add))
				}
			}
			assertTrue(confirmations.isEmpty())
			assertFalse(checkNotNull(copiedBitmap).isRecycled)
			val closing = source.close()
			assertFalse(closing.isCompleted)
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
			assertTrue(checkNotNull(copiedBitmap).isRecycled)
			assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
			assertEquals(listOf(ReaderPageRasterPublicationCompletion(ReaderPageRasterPublicationResult.Failed)), completions)
			mutex.unlock()
			locked = false
			closing.await()
			teardownCompletionJob(source).join()
		} finally {
			if (locked) mutex.unlock()
			val closing = source.close()
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
			closing.await()
		}
	}

	@Test
	fun activationBoundaryLauncherEligibilityFreezeReplaysWithoutPoisoningClose() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = DeferredJavascriptWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBundleSource()
		val snapshot = assertNotNull(source.cacheCurrentSnapshot(2, ReaderPageTurnTransitionKind.PortraitSlide, captureResult(), persist = false))
		val domain = ReaderLegacyPhysicalDomain(143L, ReaderLegacyFreezeToken(144L))
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		val completions = mutableListOf<ReaderPageRasterPublicationCompletion>()
		val completed = CompletableDeferred<Unit>()
		var eligibilityCalls = 0
		var closeOutcome: Result<Unit>? = null
		try {
			source.hydrateSnapshotWithDurability(webView, 2, snapshot.key.kind, snapshot) { it?.snapshot?.release() }
			source.ensurePersistentSnapshot(snapshot, paige.navic.reader.ReaderPageRasterPriority.Current, isStillCurrent = {
				eligibilityCalls += 1
				if (eligibilityCalls == 3) {
					assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain))
					val rows = source.snapshotFrozenOwnership().filter {
						it.physicalIdentity.source == ReaderLegacyInventorySource.RasterGenerationAndPersistence ||
							it.physicalIdentity.source == ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback
					}
					assertEquals(2, rows.size)
					assertNull(ownedPersistenceBitmap(source))
					rows.forEach { assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(it.physicalIdentity, confirmations::add)) }
					assertTrue(confirmations.isEmpty(), "Launcher/descriptor tail confirmed on the third eligibility stack")
					assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), source.restoreAfterTransitionActivation(domain))
				}
				true
			}) {
				completions += it
				completed.complete(Unit)
			}
			webView.completeNextJavascript(persistenceDescriptorJson(2))
			assertEquals(3, eligibilityCalls)
			assertEquals(2, confirmations.size)
			assertTrue(completions.isEmpty())
			assertEquals(0, bundlePersistenceJobCount(source))
			assertNull(ownedPersistenceBitmap(source))
			assertEquals(ReaderPortCommandResult.Accepted, source.restoreAfterTransitionActivation(domain))
			completed.await()
			assertEquals(listOf(ReaderPageRasterPublicationCompletion(ReaderPageRasterPublicationResult.Durable)), completions)
			val key = assertNotNull(readerPageRasterDescriptor(persistenceDescriptorJson(2))).key(snapshot.key.bitmapQuality)
			assertTrue(assertNotNull(bundlePersistentStore(source)).contains(key))
			assertEquals(0, webView.pendingJavascriptCount)
			val closing = source.close()
			runCurrent()
			val outcome = runCatching { closing.await() }
			closeOutcome = outcome
			teardownCompletionJob(source).join()
			assertNull(outcome.exceptionOrNull(), "Normal launcher freeze/replay retained a PublicationDispatch failure through close")
			assertNull(publicationLedger(source).dispatchFailure())
			assertEquals(1, completions.size)
		} finally {
			val closing = source.close()
			runCurrent()
			try {
				if (closeOutcome == null) closing.await()
				teardownCompletionJob(source).join()
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	@Test
	fun activationBoundaryPersistenceFreezeAloneFencesLaunchAndQueuedRunningEntry() = runTest {
		assertPersistenceFreezeAlone(freezeFromOwnershipObserver = false)
		assertPersistenceFreezeAlone(freezeFromOwnershipObserver = true)
	}

	private suspend fun kotlinx.coroutines.test.TestScope.assertPersistenceFreezeAlone(
		freezeFromOwnershipObserver: Boolean
	) {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = DeferredJavascriptWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val page = if (freezeFromOwnershipObserver) 3 else 2
		val domain = ReaderLegacyPhysicalDomain(147L, ReaderLegacyFreezeToken(if (freezeFromOwnershipObserver) 149L else 148L))
		var copiedBitmap: Bitmap? = null
		var lazyJob: Job? = null
		lateinit var source: ReaderPageTurnBundleSource
		source = ReaderPageTurnBundleSource(onOwnershipMutated = {
			val bitmap = ownedPersistenceBitmap(source)
			if (copiedBitmap == null && bitmap != null) {
				copiedBitmap = bitmap
				lazyJob = (ReaderPageTurnBundleSource::class.java.getDeclaredField("rasterPersistenceJobs")
					.apply { isAccessible = true }.get(source) as Set<*>).single() as Job
				if (freezeFromOwnershipObserver) {
					assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain))
				}
			}
		})
		val snapshot = assertNotNull(source.cacheCurrentSnapshot(page, ReaderPageTurnTransitionKind.PortraitSlide, captureResult(), persist = false))
		val completions = mutableListOf<ReaderPageRasterPublicationCompletion>()
		val completed = CompletableDeferred<Unit>()
		try {
			source.hydrateSnapshotWithDurability(webView, page, snapshot.key.kind, snapshot) { it?.snapshot?.release() }
			source.ensurePersistentSnapshot(snapshot, paige.navic.reader.ReaderPageRasterPriority.Current) {
				completions += it
				completed.complete(Unit)
			}
			webView.completeNextJavascript(persistenceDescriptorJson(page))
			val bitmap = assertNotNull(copiedBitmap)
			val job = assertNotNull(lazyJob)
			if (freezeFromOwnershipObserver) {
				assertFalse(job.isActive, "Freeze-alone persistence launcher started its lazy job after the observer fence")
			} else {
				assertTrue(job.isActive)
				assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain))
			}
			val identity = source.snapshotFrozenOwnership().single {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterGenerationAndPersistence
			}.physicalIdentity
			val owner = persistencePhysicalOwner(source, identity.sourceLocalToken)
			runCurrent()
			assertEquals(ReaderLegacyResourceState.Reserved, owner.state, "Frozen queued persistence entered Running before drain")
			assertTrue(job.isCompleted)
			assertTrue(bitmap.isRecycled, "Rejected frozen persistence retained its copied pixels")
			assertNull(ownedPersistenceBitmap(source))
			assertEquals(0, bundlePersistenceJobCount(source))
			assertNull(bundleRasterScheduler(source))
			assertTrue(completions.isEmpty())
			val rows = source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterGenerationAndPersistence ||
					it.physicalIdentity.source == ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback
			}
			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			rows.forEach { row ->
				assertEquals(ReaderLegacyResourceState.Released, row.state)
				assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(row.physicalIdentity) {
					assertTrue(bitmap.isRecycled)
					confirmations += it
				})
			}
			assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
			assertEquals(rows.size, confirmations.size)
			assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), source.drainFrozenOwnership(identity) {})
			assertEquals(ReaderPortCommandResult.Accepted, source.restoreAfterTransitionActivation(domain))
			completed.await()
			assertEquals(listOf(ReaderPageRasterPublicationCompletion(ReaderPageRasterPublicationResult.Durable)), completions)
			val key = assertNotNull(readerPageRasterDescriptor(persistenceDescriptorJson(page))).key(snapshot.key.bitmapQuality)
			assertTrue(assertNotNull(bundlePersistentStore(source)).contains(key))
			assertEquals(0, webView.pendingJavascriptCount, "Frozen persistence repeated descriptor lookup")
		} finally {
			source.snapshotFrozenOwnership().forEach { source.drainFrozenOwnership(it.physicalIdentity) {} }
			val closing = source.close()
			runCurrent()
			try {
				closing.await()
				teardownCompletionJob(source).join()
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun persistencePhysicalOwner(
		source: ReaderPageTurnBundleSource,
		token: ReaderLegacySourceLocalOpaqueToken
	): ReaderExactPhysicalOwnerRegistry.Owner {
		val registry = ReaderPageTurnBundleSource::class.java.getDeclaredField("rasterGenerationAndPersistenceOwnership")
			.apply { isAccessible = true }.get(source) as ReaderExactPhysicalOwnerRegistry
		val owners = ReaderExactPhysicalOwnerRegistry::class.java.getDeclaredField("owners")
			.apply { isAccessible = true }.get(registry) as Set<ReaderExactPhysicalOwnerRegistry.Owner>
		return owners.single { it.token == token }
	}

	@Test
	fun activationBoundaryQueuedInitializationCannotStartAfterFreezeAndRestartsOnce() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val context = InitializationStorageContext(activity)
		val webView = WebView(context)
		activity.setContentView(webView)
		context.filesDirectoryReads = 0
		val io = HeldInitializationIoDispatcher()
		val source = ReaderPageTurnBundleSource(rasterInitializationDispatcher = io)
		val domain = ReaderLegacyPhysicalDomain(135L, ReaderLegacyFreezeToken(136L))
		try {
			val initialization = backgroundScope.async { source.initializeRasterCache(webView) }
			runCurrent()
			assertEquals(1, io.queuedCount)
			assertEquals(0, context.filesDirectoryReads)
			assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain))
			val row = source.snapshotFrozenOwnership().single {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterGenerationAndPersistence
			}
			io.runNext()
			runCurrent()
			assertFailsWith<CancellationException> { initialization.await() }
			assertEquals(0, context.filesDirectoryReads, "Queued initialization touched physical storage after freeze")
			assertEquals(ReaderLegacyResourceState.Reserved, row.state)
			assertNull(bundleRasterCache(source))
			assertNull(bundlePersistentStore(source))
			assertNull(bundleRasterScheduler(source))
			assertEquals(ReaderLegacyResourceState.Released, source.snapshotFrozenOwnership().single().state)
			var confirmed = false
			assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(row.physicalIdentity) { confirmed = true })
			assertTrue(confirmed)
			assertEquals(ReaderPortCommandResult.Accepted, source.restoreAfterTransitionActivation(domain))
			runCurrent()
			assertEquals(1, io.queuedCount, "Restoration lost the immutable initialization restart")
			io.runNext()
			runCurrent()
			assertEquals(1, context.filesDirectoryReads)
			assertNotNull(bundleRasterCache(source))
			assertNotNull(bundlePersistentStore(source))
			assertNotNull(bundleRasterScheduler(source))
			val nextDomain = ReaderLegacyPhysicalDomain(135L, ReaderLegacyFreezeToken(137L))
			assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(nextDomain))
			assertTrue(source.snapshotFrozenOwnership().isEmpty())
			assertEquals(ReaderPortCommandResult.Accepted, source.restoreAfterTransitionActivation(nextDomain))
			runCurrent()
			assertEquals(0, io.queuedCount)
			assertEquals(1, context.filesDirectoryReads, "Initialization replayed more than once")
		} finally {
			val closing = source.close()
			runCurrent()
			try {
				closing.await()
				teardownCompletionJob(source).join()
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	@Test
	fun activationBoundaryRunningInitializationOwnsPartialCleanupUntilMainReturn() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val context = InitializationStorageContext(activity)
		val webView = WebView(context)
		activity.setContentView(webView)
		context.filesDirectoryReads = 0
		val io = HeldInitializationIoDispatcher()
		val source = ReaderPageTurnBundleSource(rasterInitializationDispatcher = io)
		val domain = ReaderLegacyPhysicalDomain(139L, ReaderLegacyFreezeToken(140L))
		var confirmed = false
		context.onFilesDirectoryRead = {
			context.onFilesDirectoryRead = null
			assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain))
			val row = source.snapshotFrozenOwnership().single()
			assertEquals(ReaderLegacyResourceState.Running, row.state)
			assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(row.physicalIdentity) { confirmed = true })
			assertFalse(confirmed, "Running IO owner confirmed before partial construction returned")
			assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), source.restoreAfterTransitionActivation(domain))
		}
		try {
			val initialization = backgroundScope.async { source.initializeRasterCache(webView) }
			runCurrent()
			io.runNext()
			assertEquals(1, context.filesDirectoryReads)
			assertFalse(confirmed, "IO owner confirmed before Main cleanup delivery")
			assertEquals(ReaderLegacyResourceState.ReleaseRequested, source.snapshotFrozenOwnership().single().state)
			runCurrent()
			assertFailsWith<CancellationException> { initialization.await() }
			assertTrue(confirmed)
			assertTrue(source.snapshotFrozenOwnership().isEmpty())
			assertNull(bundleRasterCache(source))
			assertNull(bundlePersistentStore(source))
			assertNull(bundleRasterScheduler(source))
			assertEquals(ReaderPortCommandResult.Accepted, source.restoreAfterTransitionActivation(domain))
			runCurrent()
			assertEquals(1, io.queuedCount)
			io.runNext()
			runCurrent()
			assertEquals(2, context.filesDirectoryReads)
			assertNotNull(bundleRasterScheduler(source))
		} finally {
			context.onFilesDirectoryRead = null
			val closing = source.close()
			runCurrent()
			try {
				closing.await()
				teardownCompletionJob(source).join()
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	private class InitializationStorageContext(base: Context) : ContextWrapper(base) {
		var filesDirectoryReads = 0
		var onFilesDirectoryRead: (() -> Unit)? = null

		override fun getApplicationContext(): Context = this

		override fun getFilesDir(): File {
			filesDirectoryReads += 1
			onFilesDirectoryRead?.invoke()
			return super.getFilesDir()
		}
	}

	private class HeldInitializationIoDispatcher : CoroutineDispatcher() {
		private val queued = ArrayDeque<Runnable>()
		val queuedCount: Int get() = queued.size

		override fun dispatch(context: CoroutineContext, block: Runnable) {
			queued.addLast(block)
		}

		fun runNext() {
			assertEquals(1, queued.size, "Expected exactly one real initialization IO continuation")
			queued.removeFirst().run()
		}
	}

	@Test
	fun activationBoundaryDescriptorHandoffFreezesWithoutLosingImmutablePersistenceDemand() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = DeferredJavascriptWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBundleSource()
		val snapshot = assertNotNull(source.cacheCurrentSnapshot(2, ReaderPageTurnTransitionKind.PortraitSlide, captureResult(), persist = false))
		val domain = ReaderLegacyPhysicalDomain(127L, ReaderLegacyFreezeToken(128L))
		val completions = mutableListOf<ReaderPageRasterPublicationCompletion>()
		val completed = CompletableDeferred<Unit>()
		var eligibilityCalls = 0
		var descriptorConfirmed = false
		try {
			source.hydrateSnapshotWithDurability(webView, 2, snapshot.key.kind, snapshot) { it?.snapshot?.release() }
			source.ensurePersistentSnapshot(snapshot, paige.navic.reader.ReaderPageRasterPriority.Current, isStillCurrent = {
				eligibilityCalls += 1
				if (eligibilityCalls == 2) {
					assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain))
					val descriptorRow = source.snapshotFrozenOwnership().single {
						it.physicalIdentity.source == ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback
					}
					assertEquals(ReaderLegacyResourceState.Registered, descriptorRow.state)
					assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(descriptorRow.physicalIdentity) { descriptorConfirmed = true })
					assertFalse(descriptorConfirmed, "Descriptor handoff confirmed before callback returned")
					assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), source.restoreAfterTransitionActivation(domain))
					assertEquals(0, bundlePersistenceJobCount(source), "Partial handoff restoration started persistence")
				}
				true
			}) {
				completions += it
				completed.complete(Unit)
			}
			webView.completeNextJavascript(persistenceDescriptorJson(2))
			assertTrue(completions.isEmpty(), "Descriptor handoff delivered Failure while frozen")
			assertEquals(2, eligibilityCalls)
			assertTrue(descriptorConfirmed)
			assertEquals(0, bundlePersistenceJobCount(source))
			assertNull(ownedPersistenceBitmap(source), "Frozen handoff retained copied pixels")
			assertTrue(source.snapshotFrozenOwnership().none {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterGenerationAndPersistence
			}, "Frozen logical handoff admitted a new physical owner")
			assertEquals(ReaderPortCommandResult.Accepted, source.restoreAfterTransitionActivation(domain))
			assertTrue(bundlePersistenceJobCount(source) > 0, "Restoration accepted without restarting handoff persistence")
			completed.await()
			assertEquals(listOf(ReaderPageRasterPublicationCompletion(ReaderPageRasterPublicationResult.Durable)), completions)
			val key = assertNotNull(readerPageRasterDescriptor(persistenceDescriptorJson(2))).key(snapshot.key.bitmapQuality)
			assertTrue(assertNotNull(bundlePersistentStore(source)).contains(key))
			assertEquals(0, webView.pendingJavascriptCount, "Handoff replay repeated descriptor lookup")
			val nextDomain = ReaderLegacyPhysicalDomain(127L, ReaderLegacyFreezeToken(129L))
			assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(nextDomain))
			source.snapshotFrozenOwnership().forEach { source.drainFrozenOwnership(it.physicalIdentity) {} }
			assertEquals(ReaderPortCommandResult.Accepted, source.restoreAfterTransitionActivation(nextDomain))
			runCurrent()
			assertEquals(1, completions.size)
		} finally {
			val closing = source.close()
			runCurrent()
			try {
				closing.await()
				teardownCompletionJob(source).join()
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	@Test
	fun activationBoundaryInitializedCloseRemainsConnectedUntilPublicationAndTeardownSettle() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		val publicationTokens = ReaderLegacySourceLocalTokenAllocator()
		val publication = ReaderPageRasterPublicationScheduler(backgroundScope, 1, publicationTokens)
		val source = ReaderPageTurnBundleSource(publicationOwnershipTokenAllocator = publicationTokens, publicationSchedulerOverride = publication)
		val started = CompletableDeferred<Unit>()
		val cancelling = CompletableDeferred<Unit>()
		val allowCompletion = CompletableDeferred<Unit>()
		try {
			source.initializeRasterCache(webView)
			val raster = assertNotNull(bundleRasterScheduler(source))
			val rasterWorker = ReaderPageRasterScheduler::class.java.getDeclaredField("workerJob")
				.apply { isAccessible = true }.get(raster) as Job
			assertEquals(ReaderPortCommandResult.Accepted, publication.schedule(ReaderPageRasterPublicationRequest("initialized-close", 1L)) {
				started.complete(Unit)
				try {
					awaitCancellation()
				} finally {
					cancelling.complete(Unit)
					withContext(NonCancellable) { allowCompletion.await() }
				}
			})
			started.await()
			val closing = source.close()
			rasterWorker.join()
			cancelling.await()
			assertTrue(ReaderPageRasterScheduler::class.java.getDeclaredField("closed")
				.apply { isAccessible = true }.getBoolean(raster), "Actual initialized raster worker did not exit")
			assertFalse(closing.isCompleted)
			val domain = ReaderLegacyPhysicalDomain(131L, ReaderLegacyFreezeToken(132L))
			assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain), "Initialized close-in-progress lost connected drain access")
			val inventories = assertNotNull(source.snapshotConnectedFrozenOwnership())
			assertEquals(8, inventories.size)
			assertTrue(inventories.all { it.domain == domain })
			assertTrue(inventories.single { it.source == ReaderLegacyInventorySource.RasterGenerationAndPersistence }.resources.isEmpty())
			val tails = listOf(
				inventories.single { it.source == ReaderLegacyInventorySource.RasterPublication }.resources.single(),
				inventories.single { it.source == ReaderLegacyInventorySource.RasterStoreAndCache }.resources.single()
			)
			val wrongDomain = ReaderLegacyPhysicalDomain(131L, ReaderLegacyFreezeToken(133L))
			assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), source.freezeForTransitionActivation(wrongDomain))
			assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), source.drainFrozenOwnership(tails.first().physicalIdentity.copy(domain = wrongDomain)) {})
			assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), source.restoreAfterTransitionActivation(domain))
			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			tails.forEach { assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(it.physicalIdentity, confirmations::add)) }
			assertTrue(confirmations.isEmpty(), "Closing tails confirmed before physical settlement")
			assertTrue(bundleRasterScheduler(source) === raster, "Closing scheduler reference was discarded before frozen drain")
			allowCompletion.complete(Unit)
			closing.await()
			teardownCompletionJob(source).join()
			assertEquals(tails.map { it.physicalIdentity }.toSet(), confirmations.toSet())
			assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), source.restoreAfterTransitionActivation(domain))
			assertFailsWith<CancellationException> { source.initializeRasterCache(webView) }
		} finally {
			allowCompletion.complete(Unit)
			val closing = source.close()
			runCurrent()
			try {
				closing.await()
				teardownCompletionJob(source).join()
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	@Test
	fun activationBoundaryActualPersistenceCancelledBeforeBodyRestoresItsExactCompletion() = runTest {
		assertActualPersistenceRestoration(startInitialization = false, permanentlyClose = false)
	}

	@Test
	fun activationBoundaryActualPersistenceCancelledInInitializationRestoresItsExactCompletion() = runTest {
		assertActualPersistenceRestoration(startInitialization = true, permanentlyClose = false)
	}

	@Test
	fun activationBoundaryActualPersistencePermanentCloseSettlesBeforeConfirmation() = runTest {
		assertActualPersistenceRestoration(startInitialization = false, permanentlyClose = true)
	}

	@Test
	fun activationBoundaryActualPersistenceCancelledBeforeStartReleasesCopiedBitmap() = runTest {
		assertActualPersistenceRestoration(startInitialization = false, permanentlyClose = false, cancelBeforeStart = true)
	}

	@Test
	fun activationBoundaryActualPersistenceThrowingCompletionStillReleasesAndConfirms() = runTest {
		assertActualPersistenceRestoration(startInitialization = false, permanentlyClose = true, throwOnCompletion = true)
	}

	private suspend fun kotlinx.coroutines.test.TestScope.assertActualPersistenceRestoration(
		startInitialization: Boolean,
		permanentlyClose: Boolean,
		cancelBeforeStart: Boolean = false,
		throwOnCompletion: Boolean = false
	) {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = DeferredJavascriptWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val domain = ReaderLegacyPhysicalDomain(121L, ReaderLegacyFreezeToken(122L))
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		var beforeStartRows = emptyList<ReaderFrozenLegacyResource>()
		var copiedBitmap: Bitmap? = null
		var descriptorConfirmed = false
		lateinit var source: ReaderPageTurnBundleSource
		source = ReaderPageTurnBundleSource(onOwnershipMutated = {
			if (copiedBitmap == null) copiedBitmap = ownedPersistenceBitmap(source)
			if (cancelBeforeStart && copiedBitmap != null && beforeStartRows.isEmpty()) {
				assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain))
				beforeStartRows = source.snapshotFrozenOwnership().filter {
					it.physicalIdentity.source == ReaderLegacyInventorySource.RasterGenerationAndPersistence
				}
				assertEquals(ReaderLegacyResourceState.Reserved, beforeStartRows.single().state)
				source.drainFrozenOwnership(beforeStartRows.single().physicalIdentity) {
					assertTrue(checkNotNull(copiedBitmap).isRecycled)
					confirmations += it
				}
				val descriptorRow = source.snapshotFrozenOwnership().single {
					it.physicalIdentity.source == ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback
				}
				assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(descriptorRow.physicalIdentity) { descriptorConfirmed = true })
				assertFalse(descriptorConfirmed, "Descriptor callback tail confirmed while still on-stack")
				assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), source.restoreAfterTransitionActivation(domain))
				assertEquals(0, bundlePersistenceJobCount(source), "Partial restoration started persistence")
				assertNull(bundleRasterScheduler(source))
			}
		})
		val mutex = rasterInitializationMutex(source)
		val snapshot = assertNotNull(source.cacheCurrentSnapshot(
			pageIndex = 2,
			kind = ReaderPageTurnTransitionKind.PortraitSlide,
			current = captureResult(),
			persist = false
		))
		val completions = mutableListOf<ReaderPageRasterPublicationCompletion>()
		val completed = CompletableDeferred<Unit>()
		var locked = false
		try {
			source.hydrateSnapshotWithDurability(webView, 2, snapshot.key.kind, snapshot) { it?.snapshot?.release() }
			mutex.lock()
			locked = true
			source.ensurePersistentSnapshot(snapshot, paige.navic.reader.ReaderPageRasterPriority.Current) {
				completions += it
				completed.complete(Unit)
				if (throwOnCompletion) throw IllegalStateException("persistence-completion-failed")
			}
			assertEquals(1, webView.pendingJavascriptCount)
			webView.completeNextJavascript(persistenceDescriptorJson(2))
			if (cancelBeforeStart) assertTrue(descriptorConfirmed)
			if (startInitialization) runCurrent()
			assertNotNull(copiedBitmap)
			assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain))
			val rows = if (cancelBeforeStart) beforeStartRows else source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterGenerationAndPersistence
			}
			assertEquals(if (startInitialization) 2 else 1, rows.size)
			assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)
			if (permanentlyClose) source.fenceForClose()
			if (!cancelBeforeStart) rows.forEach { row ->
				assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(row.physicalIdentity) {
					if (permanentlyClose) assertEquals(1, completions.size, "Confirmation preceded terminal result")
					confirmations += it
				})
			}
			runCurrent()
			mutex.unlock()
			locked = false
			runCurrent()
			assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
			assertTrue(checkNotNull(copiedBitmap).isRecycled, "Drained persistence retained its copied bitmap")
			assertNull(bundleRasterScheduler(source))
			assertNull(bundleRasterCache(source))
			if (permanentlyClose) {
				assertEquals(listOf(ReaderPageRasterPublicationCompletion(ReaderPageRasterPublicationResult.Failed)), completions)
				assertEquals(ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource), source.restoreAfterTransitionActivation(domain))
			} else {
				assertTrue(completions.isEmpty(), "Cancellation delivered a legacy consequence while frozen")
				assertEquals(ReaderPortCommandResult.Accepted, source.restoreAfterTransitionActivation(domain))
				assertTrue(bundlePersistenceJobCount(source) > 0, "Restoration accepted without restarting actual persistence")
				completed.await()
				assertEquals(listOf(ReaderPageRasterPublicationCompletion(ReaderPageRasterPublicationResult.Durable)), completions)
				assertEquals(0, webView.pendingJavascriptCount, "Restore repeated descriptor lookup instead of immutable request")
				val key = assertNotNull(readerPageRasterDescriptor(persistenceDescriptorJson(2))).key(snapshot.key.bitmapQuality)
				assertTrue(assertNotNull(bundlePersistentStore(source)).contains(key))
				val nextDomain = ReaderLegacyPhysicalDomain(121L, ReaderLegacyFreezeToken(123L))
				assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(nextDomain))
				source.snapshotFrozenOwnership().forEach { source.drainFrozenOwnership(it.physicalIdentity) {} }
				assertEquals(ReaderPortCommandResult.Accepted, source.restoreAfterTransitionActivation(nextDomain))
				runCurrent()
				assertEquals(1, completions.size)
			}
		} finally {
			if (locked) mutex.unlock()
			val closing = source.close()
			runCurrent()
			try {
				if (throwOnCompletion) {
					assertEquals("persistence-completion-failed", assertFailsWith<IllegalStateException> { closing.await() }.cause?.message)
				} else closing.await()
				teardownCompletionJob(source).join()
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	private fun persistenceDescriptorJson(page: Int): String = """
		{"publicationUrl":"publication-boundary","paginationFingerprint":"pagination-boundary",
		"layoutFingerprint":"layout-boundary","decorationFingerprint":"decoration-boundary",
		"viewportWidth":20,"viewportHeight":30,"pageCount":20,"spineIndex":0,
		"href":"chapter","chapterPageIndex":$page,"chapterPageCount":20,"visualPageOrdinal":$page}
	""".trimIndent()

	@Test
	fun captureAndVisualStateFreezeDrainsItsExactOwnerAndRejectsLateAdmission() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = DeferredJavascriptWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBundleSource()
		assertTrue(source.isAvailable)
		assertTrue(webView.isAttachedToWindow)
		assertEquals(20, webView.width)
		assertEquals(30, webView.height)
		val captured = mutableListOf<ReaderPageTurnCaptureResult?>()
		source.captureCurrentSurface(webView, source.currentGeneration(), captured::add)
		assertEquals(1, webView.pendingJavascriptCount)
		val domain = ReaderLegacyPhysicalDomain(47L, ReaderLegacyFreezeToken(48L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership().filter {
			it.physicalIdentity.source ==
				ReaderLegacyInventorySource.RasterCaptureAndVisualState
		}
		assertEquals(3, rows.size)
		assertEquals(
			setOf(
				paige.navic.reader.ReaderTransitionResourceKind.Raster,
				paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
			),
			rows.map { it.kind }.toSet()
		)
		assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)

		source.captureCurrentSurface(webView, source.currentGeneration(), captured::add)
		assertEquals(1, webView.pendingJavascriptCount)
		assertEquals(listOf<ReaderPageTurnCaptureResult?>(null), captured)

		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		assertEquals(
			listOf<ReaderPageTurnCaptureResult?>(null, null),
			captured
		)
		webView.completeNextJavascript("null")
		assertEquals(
			listOf<ReaderPageTurnCaptureResult?>(null, null),
			captured
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		source.closeAndJoin()
	}

	@Test
	fun visualStateCallbackFreezesAndLateCompletionCannotAdvanceCapture() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = DeferredJavascriptWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBundleSource()
		val captured = mutableListOf<ReaderPageTurnCaptureResult?>()
		source.captureCurrentSurface(webView, source.currentGeneration(), captured::add)
		webView.completeNextJavascript(captureGeometryJson())
		assertEquals(1, webView.pendingVisualStateCount)
		val domain = ReaderLegacyPhysicalDomain(57L, ReaderLegacyFreezeToken(58L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership().filter {
			it.physicalIdentity.source ==
				ReaderLegacyInventorySource.RasterCaptureAndVisualState
		}
		assertEquals(3, rows.size)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		assertEquals(listOf<ReaderPageTurnCaptureResult?>(null), captured)
		webView.completeNextVisualState()
		assertEquals(0, webView.pendingAnimationCount)
		assertEquals(listOf<ReaderPageTurnCaptureResult?>(null), captured)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		source.closeAndJoin()
	}

	@Test
	fun animationRunnableFreezesAndLateExecutionCannotDraw() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = DeferredJavascriptWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBundleSource()
		val captured = mutableListOf<ReaderPageTurnCaptureResult?>()
		source.captureCurrentSurface(webView, source.currentGeneration(), captured::add)
		webView.completeNextJavascript(captureGeometryJson())
		webView.completeNextVisualState()
		assertEquals(1, webView.pendingAnimationCount)
		val domain = ReaderLegacyPhysicalDomain(59L, ReaderLegacyFreezeToken(60L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership().filter {
			it.physicalIdentity.source ==
				ReaderLegacyInventorySource.RasterCaptureAndVisualState
		}
		assertEquals(3, rows.size)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		assertEquals(listOf<ReaderPageTurnCaptureResult?>(null), captured)
		webView.completeNextAnimation()
		assertEquals(listOf<ReaderPageTurnCaptureResult?>(null), captured)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		source.closeAndJoin()
	}

	@Test
	fun drawStageConfirmsOnlyAfterThePhysicalWebViewDrawReturns() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = BlockingDrawWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBundleSource()
		val captured = mutableListOf<ReaderPageTurnCaptureResult?>()
		source.captureCurrentSurface(webView, source.currentGeneration(), captured::add)
		webView.completeNextJavascript(captureGeometryJson())
		webView.completeNextVisualState()
		val drawing = Thread(webView::completeNextAnimation)
		drawing.start()
		assertTrue(webView.drawStarted.await(5, TimeUnit.SECONDS))
		val domain = ReaderLegacyPhysicalDomain(61L, ReaderLegacyFreezeToken(62L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership().filter {
			it.physicalIdentity.source ==
				ReaderLegacyInventorySource.RasterCaptureAndVisualState
		}
		assertEquals(4, rows.size)
		assertEquals(2, rows.count {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.Raster
		})
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertTrue(confirmed.size < rows.size)
		webView.allowDrawCompletion.countDown()
		drawing.join(5_000L)
		assertFalse(drawing.isAlive)
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		assertEquals(listOf<ReaderPageTurnCaptureResult?>(null), captured)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		source.closeAndJoin()
	}

	@Test
	@GraphicsMode(GraphicsMode.Mode.NATIVE)
	fun terminalCaptureCallbackRetainsExactOwnersUntilCallbackReturn() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = ColoredDrawWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBundleSource()
		val callbackStarted = CountDownLatch(1)
		val allowCallbackReturn = CountDownLatch(1)
		val callbackReturned = AtomicBoolean(false)
		val capturedValue = AtomicBoolean(false)
		source.captureCurrentSurface(webView, source.currentGeneration()) { captured ->
			capturedValue.set(captured != null)
			callbackStarted.countDown()
			callbackReturned.set(allowCallbackReturn.await(5, TimeUnit.SECONDS))
			captured?.bitmap?.recycle()
		}
		webView.completeNextJavascript(captureGeometryJson())
		webView.completeNextVisualState()
		val animation = Thread(webView::completeNextAnimation)
		animation.start()
		try {
			assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))
			val domain = ReaderLegacyPhysicalDomain(81L, ReaderLegacyFreezeToken(82L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val rows = source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source ==
					ReaderLegacyInventorySource.RasterCaptureAndVisualState
			}
			assertEquals(3, rows.size)
			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			rows.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					source.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
			assertTrue(confirmations.isEmpty())

			allowCallbackReturn.countDown()
			animation.join(5_000L)
			assertFalse(animation.isAlive)
			assertTrue(callbackReturned.get())
			assertTrue(capturedValue.get())
			assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
		} finally {
			allowCallbackReturn.countDown()
			animation.join(5_000L)
			source.closeAndJoin()
		}
	}

	@Test
	@GraphicsMode(GraphicsMode.Mode.NATIVE)
	fun presentedTerminalCallbackRetainsExactOwnersUntilCallbackReturn() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = ColoredDrawWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBitmapSource()
		val target = ReaderPageTurnPresentationTarget.Preview(
			token = "preview",
			pageIndex = 1L,
			previewGeneration = 2L,
			foregroundMutationGeneration = 3L
		)
		val callbackStarted = CountDownLatch(1)
		val allowCallbackReturn = CountDownLatch(1)
		val callbackReturned = AtomicBoolean(false)
		val capturedValue = AtomicBoolean(false)
		source.capturePresentedSurface(webView, target) { captured ->
			capturedValue.set(captured != null)
			callbackStarted.countDown()
			callbackReturned.set(allowCallbackReturn.await(5, TimeUnit.SECONDS))
			captured?.bitmap?.recycle()
		}
		webView.completeNextJavascript(previewPresentationReceiptJson())
		webView.completeNextJavascript(captureGeometryJson())
		webView.completeNextVisualState()
		webView.completeNextAnimation()
		assertEquals(1, webView.pendingJavascriptCount)
		val finalFence = Thread {
			webView.completeNextJavascript(previewPresentationReceiptJson())
		}
		finalFence.start()
		try {
			assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))
			val domain = ReaderLegacyPhysicalDomain(83L, ReaderLegacyFreezeToken(84L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val rows = source.snapshotFrozenOwnership()
			assertEquals(3, rows.size)
			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			rows.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					source.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
			assertTrue(confirmations.isEmpty())

			allowCallbackReturn.countDown()
			finalFence.join(5_000L)
			assertFalse(finalFence.isAlive)
			assertTrue(callbackReturned.get())
			assertTrue(capturedValue.get())
			assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
		} finally {
			allowCallbackReturn.countDown()
			finalFence.join(5_000L)
		}
	}

	@Test
	fun presentedCaptureAndReceiptCallbackAreExactOwnersBeforeAnyCandidateExists() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = DeferredJavascriptWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBitmapSource()
		val target = ReaderPageTurnPresentationTarget.Preview(
			token = "preview",
			pageIndex = 1L,
			previewGeneration = 2L,
			foregroundMutationGeneration = 3L
		)
		val captured = mutableListOf<ReaderPageTurnCaptureResult?>()
		source.capturePresentedSurface(webView, target, onCaptured = captured::add)
		assertEquals(1, webView.pendingJavascriptCount)
		val domain = ReaderLegacyPhysicalDomain(63L, ReaderLegacyFreezeToken(64L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership()
		assertEquals(3, rows.size)
		assertEquals(1, rows.count {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.Raster
		})
		assertEquals(2, rows.count {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		})
		source.capturePresentedSurface(webView, target, onCaptured = captured::add)
		assertEquals(1, webView.pendingJavascriptCount)
		assertEquals(listOf<ReaderPageTurnCaptureResult?>(null), captured)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		assertEquals(
			listOf<ReaderPageTurnCaptureResult?>(null, null),
			captured
		)
		webView.completeNextJavascript("null")
		assertEquals(
			listOf<ReaderPageTurnCaptureResult?>(null, null),
			captured
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	@GraphicsMode(GraphicsMode.Mode.NATIVE)
	fun presentedCaptureRetainedCandidateIsAnExactOwnerUntilFinalFence() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = ColoredDrawWebView(activity)
		activity.setContentView(webView)
		layoutForCapture(webView)
		val source = ReaderPageTurnBitmapSource()
		val target = ReaderPageTurnPresentationTarget.Preview(
			token = "preview",
			pageIndex = 1L,
			previewGeneration = 2L,
			foregroundMutationGeneration = 3L
		)
		val captured = mutableListOf<ReaderPageTurnCaptureResult?>()
		source.capturePresentedSurface(webView, target, onCaptured = captured::add)
		webView.completeNextJavascript(previewPresentationReceiptJson())
		webView.completeNextJavascript(captureGeometryJson())
		webView.completeNextVisualState()
		webView.completeNextAnimation()
		assertEquals(1, webView.pendingJavascriptCount)
		val domain = ReaderLegacyPhysicalDomain(73L, ReaderLegacyFreezeToken(74L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership()
		assertEquals(
			2,
			rows.count { it.kind == paige.navic.reader.ReaderTransitionResourceKind.Raster }
		)
		assertEquals(4, rows.size)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		assertEquals(listOf<ReaderPageTurnCaptureResult?>(null), captured)
		webView.completeNextJavascript(previewPresentationReceiptJson())
		assertEquals(listOf<ReaderPageTurnCaptureResult?>(null), captured)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun liveCaptureHandlerRunnableIsAnExactOwnerAndCannotStartAfterFreeze() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val container = FrameLayout(activity)
		val webView = DeferredJavascriptWebView(activity)
		val rendererSurface = PageSurfaceView(activity)
		container.addView(webView)
		container.addView(rendererSurface)
		activity.setContentView(container)
		layoutForCapture(webView)
		layoutForCapture(rendererSurface)
		val source = ReaderPageTurnBitmapSource()
		val target = ReaderPageTurnPresentationTarget.Live(
			token = "live",
			pageIndex = 1L,
			foliateSessionId = "session",
			rasterGeneration = 2L,
			textureGeneration = 3L,
			foregroundMutationGeneration = 4L
		)
		val captured = mutableListOf<ReaderPageTurnLiveCaptureResult?>()
		var handle: ReaderPageRelocationContentValidationHandle? = null
		val registering = Thread {
			handle = source.captureLiveCompositedSurface(
				webView = webView,
				rendererSurface = rendererSurface,
				target = target,
				expectedBitmapWidth = 20,
				expectedBitmapHeight = 30,
				onCaptured = captured::add
			)
		}
		registering.start()
		registering.join(5_000L)
		assertFalse(registering.isAlive)
		assertNotNull(handle)
		assertEquals(0, webView.pendingJavascriptCount)
		val domain = ReaderLegacyPhysicalDomain(65L, ReaderLegacyFreezeToken(66L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership()
		assertEquals(3, rows.size)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		assertEquals(listOf<ReaderPageTurnLiveCaptureResult?>(null), captured)
		org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
		assertEquals(0, webView.pendingJavascriptCount)
		source.captureLiveCompositedSurface(
			webView = webView,
			rendererSurface = rendererSurface,
			target = target,
			expectedBitmapWidth = 20,
			expectedBitmapHeight = 30,
			onCaptured = captured::add
		)
		assertEquals(
			listOf<ReaderPageTurnLiveCaptureResult?>(null, null),
			captured
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun offMainLiveCaptureCancellationSettlesQueuedHandlerOwnerWithoutCleanupPost() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val container = FrameLayout(activity)
		val webView = DeferredJavascriptWebView(activity)
		val rendererSurface = PageSurfaceView(activity)
		container.addView(webView)
		container.addView(rendererSurface)
		activity.setContentView(container)
		layoutForCapture(webView)
		layoutForCapture(rendererSurface)
		val source = ReaderPageTurnBitmapSource()
		val target = ReaderPageTurnPresentationTarget.Live(
			token = "cancel-before-start",
			pageIndex = 1L,
			foliateSessionId = "session",
			rasterGeneration = 2L,
			textureGeneration = 3L,
			foregroundMutationGeneration = 4L
		)
		val captured = mutableListOf<ReaderPageTurnLiveCaptureResult?>()
		val mainLooper = org.robolectric.Shadows
			.shadowOf(android.os.Looper.getMainLooper())
		mainLooper.idle()
		assertTrue(mainLooper.isIdle)
		var handle: ReaderPageRelocationContentValidationHandle? = null
		val registering = Thread {
			handle = source.captureLiveCompositedSurface(
				webView = webView,
				rendererSurface = rendererSurface,
				target = target,
				expectedBitmapWidth = 20,
				expectedBitmapHeight = 30,
				onCaptured = captured::add
			)
		}
		registering.start()
		registering.join(5_000L)
		assertFalse(registering.isAlive)
		assertFalse(mainLooper.isIdle)

		var cancelled = false
		val cancelling = Thread {
			cancelled = checkNotNull(handle).cancel()
		}
		cancelling.start()
		cancelling.join(5_000L)
		assertFalse(cancelling.isAlive)

		assertTrue(cancelled)
		assertEquals(listOf<ReaderPageTurnLiveCaptureResult?>(null), captured)
		assertTrue(
			mainLooper.isIdle,
			"Cancellation must remove postedStart without adding an unowned cleanup Runnable"
		)
		val domain = ReaderLegacyPhysicalDomain(91L, ReaderLegacyFreezeToken(92L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		assertTrue(source.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
		assertEquals(0, webView.pendingJavascriptCount)
		assertEquals(listOf<ReaderPageTurnLiveCaptureResult?>(null), captured)
	}

	@Test
	fun liveCaptureGeometryCallbackIsAnExactOwnerAfterReceiptCompletion() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val container = FrameLayout(activity)
		val webView = DeferredJavascriptWebView(activity)
		val rendererSurface = PageSurfaceView(activity)
		container.addView(webView)
		container.addView(rendererSurface)
		activity.setContentView(container)
		layoutForCapture(webView)
		layoutForCapture(rendererSurface)
		val source = ReaderPageTurnBitmapSource()
		val target = ReaderPageTurnPresentationTarget.Live(
			token = "live",
			pageIndex = 1L,
			foliateSessionId = "session",
			rasterGeneration = 2L,
			textureGeneration = 3L,
			foregroundMutationGeneration = 4L
		)
		val captured = mutableListOf<ReaderPageTurnLiveCaptureResult?>()
		source.captureLiveCompositedSurface(
			webView = webView,
			rendererSurface = rendererSurface,
			target = target,
			expectedBitmapWidth = 20,
			expectedBitmapHeight = 30,
			onCaptured = captured::add
		)
		assertEquals(1, webView.pendingJavascriptCount)
		webView.completeNextJavascript(livePresentationReceiptJson())
		assertEquals(1, webView.pendingJavascriptCount)
		val domain = ReaderLegacyPhysicalDomain(67L, ReaderLegacyFreezeToken(68L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership()
		assertEquals(3, rows.size)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		assertEquals(listOf<ReaderPageTurnLiveCaptureResult?>(null), captured)
		webView.completeNextJavascript(captureGeometryJson())
		assertEquals(listOf<ReaderPageTurnLiveCaptureResult?>(null), captured)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun liveCapturePresentedFrameRequestIsAnExactOwnerAndCancelsBeforeConfirmation() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val container = FrameLayout(activity)
		val webView = DeferredJavascriptWebView(activity)
		val rendererSurface = DeferredPresentedFrameSurface(activity)
		container.addView(webView)
		container.addView(rendererSurface)
		activity.setContentView(container)
		layoutForCapture(webView)
		layoutForCapture(rendererSurface)
		rendererSurface.useValidSurface()
		val source = ReaderPageTurnBitmapSource()
		val target = ReaderPageTurnPresentationTarget.Live(
			token = "live",
			pageIndex = 1L,
			foliateSessionId = "session",
			rasterGeneration = 2L,
			textureGeneration = 3L,
			foregroundMutationGeneration = 4L
		)
		val captured = mutableListOf<ReaderPageTurnLiveCaptureResult?>()
		source.captureLiveCompositedSurface(
			webView = webView,
			rendererSurface = rendererSurface,
			target = target,
			expectedBitmapWidth = 20,
			expectedBitmapHeight = 30,
			onCaptured = captured::add
		)
		webView.completeNextJavascript(livePresentationReceiptJson())
		webView.completeNextJavascript(captureGeometryJson())
		assertEquals(1, rendererSurface.pendingPresentedFrameCount)
		val domain = ReaderLegacyPhysicalDomain(69L, ReaderLegacyFreezeToken(70L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership()
		assertEquals(4, rows.size)
		assertEquals(
			2,
			rows.count { it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration }
		)
		assertEquals(
			2,
			rows.count { it.kind == paige.navic.reader.ReaderTransitionResourceKind.Raster }
		)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertEquals(0, rendererSurface.pendingPresentedFrameCount)
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		assertEquals(listOf<ReaderPageTurnLiveCaptureResult?>(null), captured)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		rendererSurface.releaseSurface()
	}

	@Test
	@Config(sdk = [35], shadows = [DeferredPixelCopyShadow::class])
	fun livePixelCopyWriteCallbackAndCandidateConfirmOnlyAfterCopyCompletion() = runTest {
		DeferredPixelCopyShadow.reset()
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val container = FrameLayout(activity)
		val webView = DeferredJavascriptWebView(activity)
		val rendererSurface = DeferredPresentedFrameSurface(activity)
		container.addView(webView)
		container.addView(rendererSurface)
		activity.setContentView(container)
		layoutForCapture(webView)
		layoutForCapture(rendererSurface)
		rendererSurface.useValidSurface()
		val source = ReaderPageTurnBitmapSource()
		val target = ReaderPageTurnPresentationTarget.Live(
			token = "live",
			pageIndex = 1L,
			foliateSessionId = "session",
			rasterGeneration = 2L,
			textureGeneration = 3L,
			foregroundMutationGeneration = 4L
		)
		val captured = mutableListOf<ReaderPageTurnLiveCaptureResult?>()
		source.captureLiveCompositedSurface(
			webView = webView,
			rendererSurface = rendererSurface,
			target = target,
			expectedBitmapWidth = 20,
			expectedBitmapHeight = 30,
			onCaptured = captured::add
		)
		webView.completeNextJavascript(livePresentationReceiptJson())
		webView.completeNextJavascript(captureGeometryJson())
		rendererSurface.completeNextPresentedFrame()
		assertEquals(1, DeferredPixelCopyShadow.pendingCount)
		val domain = ReaderLegacyPhysicalDomain(71L, ReaderLegacyFreezeToken(72L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership()
		assertEquals(5, rows.size)
		assertEquals(
			3,
			rows.count { it.kind == paige.navic.reader.ReaderTransitionResourceKind.Raster }
		)
		assertEquals(
			2,
			rows.count { it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration }
		)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertTrue(confirmed.isEmpty())
		assertTrue(captured.isEmpty())

		DeferredPixelCopyShadow.completeNext(PixelCopy.ERROR_UNKNOWN)
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		assertEquals(listOf<ReaderPageTurnLiveCaptureResult?>(null), captured)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		rendererSurface.releaseSurface()
	}

	@Test
	fun liveValidationFreezeDrainsItsExactCaptureStageAndRejectsLateAdmission() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val container = FrameLayout(activity)
		val webView = DeferredJavascriptWebView(activity)
		val rendererSurface = PageSurfaceView(activity)
		container.addView(webView)
		container.addView(rendererSurface)
		activity.setContentView(container)
		layoutForCapture(webView)
		layoutForCapture(rendererSurface)
		val source = ReaderPageTurnBundleSource()
		val target = slideSnapshot()
		target.retain()
		val results = mutableListOf<ReaderPageRelocationContentValidationResult>()
		source.validateLivePresentation(
			webView = webView,
			rendererSurface = rendererSurface,
			request = relocationRequest(),
			foregroundMutationGeneration = ReaderForegroundWebViewMutationGeneration(1L),
			expectedTarget = target,
			expectedSource = null,
			isStillCurrent = { true },
			onValidated = results::add
		)
		assertEquals(1, webView.pendingJavascriptCount)
		val domain = ReaderLegacyPhysicalDomain(49L, ReaderLegacyFreezeToken(50L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership().filter {
			it.physicalIdentity.source == ReaderLegacyInventorySource.RasterLiveValidation
		}
		assertEquals(2, rows.size)
		assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)
		val lateTarget = slideSnapshot()
		lateTarget.retain()
		source.validateLivePresentation(
			webView = webView,
			rendererSurface = rendererSurface,
			request = relocationRequest().copy(token = ReaderPageRelocationToken("late")),
			foregroundMutationGeneration = ReaderForegroundWebViewMutationGeneration(1L),
			expectedTarget = lateTarget,
			expectedSource = null,
			isStillCurrent = { true },
			onValidated = results::add
		)
		assertEquals(1, webView.pendingJavascriptCount)
		assertEquals(
			listOf(ReaderPageRelocationContentValidationResult.Invalidated),
			results
		)

		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmed.toSet())
		webView.completeNextJavascript("null")
		assertEquals(
			listOf(ReaderPageRelocationContentValidationResult.Invalidated),
			results
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		target.releaseCacheOwnership()
		lateTarget.releaseCacheOwnership()
		source.closeAndJoin()
	}

	@Test
	fun liveValidationPostedResultRunnableReplacesCompletedCaptureOwners() = runTest {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val container = FrameLayout(activity)
		val webView = DeferredJavascriptWebView(activity)
		val rendererSurface = PageSurfaceView(activity)
		container.addView(webView)
		container.addView(rendererSurface)
		activity.setContentView(container)
		layoutForCapture(webView)
		layoutForCapture(rendererSurface)
		val source = ReaderPageTurnBundleSource()
		val target = slideSnapshot()
		target.retain()
		val results = mutableListOf<ReaderPageRelocationContentValidationResult>()
		source.validateLivePresentation(
			webView = webView,
			rendererSurface = rendererSurface,
			request = relocationRequest(),
			foregroundMutationGeneration = ReaderForegroundWebViewMutationGeneration(1L),
			expectedTarget = target,
			expectedSource = null,
			isStillCurrent = { true },
			onValidated = results::add
		)
		webView.completeNextJavascript("null")
		assertTrue(results.isEmpty())
		val domain = ReaderLegacyPhysicalDomain(75L, ReaderLegacyFreezeToken(76L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val rows = source.snapshotFrozenOwnership().filter {
			it.physicalIdentity.source == ReaderLegacyInventorySource.RasterLiveValidation
		}
		assertEquals(1, rows.size)
		val row = rows.single()
		assertEquals(
			paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration,
			row.kind
		)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
		)
		assertEquals(listOf(row.physicalIdentity), confirmed)
		org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
		assertTrue(results.isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.restoreAfterTransitionActivation(domain)
		)
		target.releaseCacheOwnership()
		source.closeAndJoin()
	}

	@Test
	@Config(sdk = [35], shadows = [DeferredPixelCopyShadow::class])
	fun liveValidationWorkerIsAnExactOwnerUntilItsJobActuallyCompletes() = runTest {
		DeferredPixelCopyShadow.reset()
		val validationGate = BoundedValidationDispatcherGate()
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val container = FrameLayout(activity)
		val webView = DeferredJavascriptWebView(activity)
		val rendererSurface = DeferredPresentedFrameSurface(activity)
		container.addView(webView)
		container.addView(rendererSurface)
		activity.setContentView(container)
		layoutForCapture(webView)
		layoutForCapture(rendererSurface)
		rendererSurface.useValidSurface()
		val source = ReaderPageTurnBundleSource(
			liveValidationDispatcher = validationGate.dispatcher
		)
		val target = slideSnapshot()
		target.retain()
		val results = mutableListOf<ReaderPageRelocationContentValidationResult>()
		try {
			source.validateLivePresentation(
				webView = webView,
				rendererSurface = rendererSurface,
				request = relocationRequest(),
				foregroundMutationGeneration = ReaderForegroundWebViewMutationGeneration(4L),
				expectedTarget = target,
				expectedSource = null,
				isStillCurrent = { true },
				onValidated = results::add
			)
			webView.completeNextJavascript(validationLivePresentationReceiptJson())
			webView.completeNextJavascript(captureGeometryJson())
			rendererSurface.completeNextPresentedFrame()
			DeferredPixelCopyShadow.completeNext(PixelCopy.SUCCESS)
			webView.completeNextJavascript(validationLivePresentationReceiptJson())
			val domain = ReaderLegacyPhysicalDomain(77L, ReaderLegacyFreezeToken(78L))

			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val rows = source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterLiveValidation
			}
			assertEquals(1, rows.size)
			val row = rows.single()
			assertEquals(paige.navic.reader.ReaderTransitionResourceKind.Raster, row.kind)
			val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
			val confirmation = CountDownLatch(1)
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity) { identity ->
					synchronized(confirmed) { confirmed += identity }
					confirmation.countDown()
				}
			)
			assertEquals(1L, confirmation.count)
			assertTrue(synchronized(confirmed) { confirmed.isEmpty() })
			validationGate.release()
			assertTrue(confirmation.await(5, TimeUnit.SECONDS))
			assertEquals(
				listOf(row.physicalIdentity),
				synchronized(confirmed) { confirmed.toList() }
			)
			assertTrue(results.isEmpty())
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
		} finally {
			validationGate.release()
			try {
				target.releaseCacheOwnership()
				source.closeAndJoin()
				rendererSurface.releaseSurface()
			} finally {
				validationGate.close()
			}
		}
	}

	@Test
	@Config(sdk = [35], shadows = [DeferredPixelCopyShadow::class])
	fun frozenWorkerDrainWaitsForWorkerFinishedCleanupToReturn() = runTest {
		DeferredPixelCopyShadow.reset()
		val validationGate = BoundedValidationDispatcherGate()
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val container = FrameLayout(activity)
		val webView = DeferredJavascriptWebView(activity)
		val rendererSurface = DeferredPresentedFrameSurface(activity)
		container.addView(webView)
		container.addView(rendererSurface)
		activity.setContentView(container)
		layoutForCapture(webView)
		layoutForCapture(rendererSurface)
		rendererSurface.useValidSurface()
		val source = ReaderPageTurnBundleSource(
			liveValidationDispatcher = validationGate.dispatcher
		)
		val target = slideSnapshot().also { snapshot ->
			snapshot.bitmap.eraseColor(Color.BLACK)
			snapshot.bitmap.setHasAlpha(false)
		}
		target.retain()
		val results = mutableListOf<ReaderPageRelocationContentValidationResult>()
		val cleanupLock = checkNotNull(
			ReaderPageTurnBundleSource::class.java
				.getDeclaredField("closeFenceLock")
				.apply { isAccessible = true }
				.get(source)
		)
		val cleanupLockHeld = CountDownLatch(1)
		val allowCleanupReturn = CountDownLatch(1)
		val lockHolder = Thread {
			synchronized(cleanupLock) {
				cleanupLockHeld.countDown()
				check(allowCleanupReturn.await(5, TimeUnit.SECONDS))
			}
		}
		var draining: Thread? = null
		try {
			source.validateLivePresentation(
				webView = webView,
				rendererSurface = rendererSurface,
				request = relocationRequest(),
				foregroundMutationGeneration = ReaderForegroundWebViewMutationGeneration(4L),
				expectedTarget = target,
				expectedSource = null,
				isStillCurrent = { true },
				onValidated = results::add
			)
			webView.completeNextJavascript(validationLivePresentationReceiptJson())
			webView.completeNextJavascript(captureGeometryJson())
			rendererSurface.completeNextPresentedFrame()
			DeferredPixelCopyShadow.completeNext(PixelCopy.SUCCESS, Color.BLACK)
			webView.completeNextJavascript(validationLivePresentationReceiptJson())
			val domain = ReaderLegacyPhysicalDomain(99L, ReaderLegacyFreezeToken(100L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val workerRow = source.snapshotFrozenOwnership().single { row ->
				row.physicalIdentity.source == ReaderLegacyInventorySource.RasterLiveValidation &&
					row.kind == paige.navic.reader.ReaderTransitionResourceKind.Raster
			}
			val confirmations = Collections.synchronizedList(
				mutableListOf<ReaderLegacyPhysicalIdentity>()
			)
			val confirmation = CountDownLatch(1)
			val drainResult = AtomicReference<ReaderPortCommandResult?>()
			lockHolder.start()
			assertTrue(cleanupLockHeld.await(5, TimeUnit.SECONDS))
			draining = Thread {
				drainResult.set(
					source.drainFrozenOwnership(workerRow.physicalIdentity) { identity ->
						confirmations += identity
						confirmation.countDown()
					}
				)
			}.also(Thread::start)
			checkNotNull(draining).join(5_000L)
			assertFalse(checkNotNull(draining).isAlive)
			assertEquals(ReaderPortCommandResult.Accepted, drainResult.get())
			validationGate.release()
			assertTrue(
				awaitThreadBlockedIn("unregisterLiveValidation"),
				"Worker finalization did not reach its blocked cleanup boundary"
			)
			assertTrue(synchronized(confirmations) { confirmations.isEmpty() })

			allowCleanupReturn.countDown()
			assertTrue(confirmation.await(5, TimeUnit.SECONDS))
			assertEquals(
				listOf(workerRow.physicalIdentity),
				synchronized(confirmations) { confirmations.toList() }
			)
			assertTrue(results.isEmpty())
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
		} finally {
			allowCleanupReturn.countDown()
			validationGate.release()
			draining?.join(5_000L)
			lockHolder.join(5_000L)
			try {
				target.releaseCacheOwnership()
				source.closeAndJoin()
				rendererSurface.releaseSurface()
			} finally {
				validationGate.close()
			}
		}
	}

	@Test
	@Config(sdk = [35], shadows = [DeferredPixelCopyShadow::class])
	fun liveValidationFinalFenceIsAnExactOwnerUntilReceiptCancellation() = runTest {
		DeferredPixelCopyShadow.reset()
		val validationGate = BoundedValidationDispatcherGate()
		var awaitedMutation: CountDownLatch? = null
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val container = FrameLayout(activity)
		val webView = DeferredJavascriptWebView(activity)
		val rendererSurface = DeferredPresentedFrameSurface(activity)
		container.addView(webView)
		container.addView(rendererSurface)
		activity.setContentView(container)
		layoutForCapture(webView)
		layoutForCapture(rendererSurface)
		rendererSurface.useValidSurface()
		val source = ReaderPageTurnBundleSource(
			onOwnershipMutated = { awaitedMutation?.countDown() },
			liveValidationDispatcher = validationGate.dispatcher
		)
		val targetLocation = IntArray(2).also(webView::getLocationInWindow)
		val target = slideSnapshot(
			Rect(
				targetLocation[0],
				targetLocation[1],
				targetLocation[0] + 20,
				targetLocation[1] + 30
			)
		).also { snapshot ->
			snapshot.bitmap.eraseColor(Color.BLACK)
			snapshot.bitmap.setHasAlpha(false)
		}
		target.retain()
		val results = mutableListOf<ReaderPageRelocationContentValidationResult>()
		try {
			source.validateLivePresentation(
				webView = webView,
				rendererSurface = rendererSurface,
				request = relocationRequest(),
				foregroundMutationGeneration = ReaderForegroundWebViewMutationGeneration(4L),
				expectedTarget = target,
				expectedSource = null,
				isStillCurrent = { true },
				onValidated = results::add
			)
			webView.completeNextJavascript(validationLivePresentationReceiptJson())
			webView.completeNextJavascript(captureGeometryJson())
			rendererSurface.completeNextPresentedFrame()
			DeferredPixelCopyShadow.completeNext(PixelCopy.SUCCESS, Color.BLACK)
			val resultPublicationPosted = CountDownLatch(4)
			awaitedMutation = resultPublicationPosted
			webView.completeNextJavascript(validationLivePresentationReceiptJson())
			validationGate.release()
			assertTrue(resultPublicationPosted.await(5, TimeUnit.SECONDS))
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
			assertEquals(1, webView.pendingJavascriptCount, "results=$results")
			val domain = ReaderLegacyPhysicalDomain(79L, ReaderLegacyFreezeToken(80L))

			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val rows = source.snapshotFrozenOwnership().filter {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterLiveValidation
			}
			assertEquals(1, rows.size)
			val row = rows.single()
			assertEquals(
				paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration,
				row.kind
			)
			val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
			assertEquals(listOf(row.physicalIdentity), confirmed)
			assertTrue(results.isEmpty())
			webView.completeNextJavascript(validationLivePresentationReceiptJson())
			assertTrue(results.isEmpty())
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.restoreAfterTransitionActivation(domain)
			)
		} finally {
			validationGate.release()
			try {
				target.releaseCacheOwnership()
				source.closeAndJoin()
				rendererSurface.releaseSurface()
			} finally {
				validationGate.close()
			}
		}
	}

	@Test
	fun storeAndCacheCompletionBetweenFreezeAndFirstInventorySurvivesTeardown() = runTest {
		assertFrozenStoreAndCacheOwnerSurvivesTeardown(
			inventoryBeforeClose = false,
			seed = 1
		)
	}

	@Test
	fun alreadyInventoriedStoreAndCacheOwnerSurvivesTeardownUntilExactDrain() = runTest {
		assertFrozenStoreAndCacheOwnerSurvivesTeardown(
			inventoryBeforeClose = true,
			seed = 2
		)
	}

	@Test
	fun frozenCloseFinalizesActiveAndQueuedPublicationEntriesBeforeTeardown() = runTest {
		val publicationTokens = ReaderLegacySourceLocalTokenAllocator()
		val scheduler = ReaderPageRasterPublicationScheduler(
			backgroundScope,
			1,
			publicationTokens
		)
		val source = ReaderPageTurnBundleSource(
			publicationOwnershipTokenAllocator = publicationTokens,
			publicationSchedulerOverride = scheduler
		)
		val ledger = publicationLedger(source)
		val activeValue = publicationValue(1)
		val queuedValue = publicationValue(2)
		val callbacks = Collections.synchronizedList(mutableListOf<String>())
		val activeRequest = (
			ledger.begin("frozen-close-active", activeValue) { persisted ->
				callbacks += "active-$persisted"
			} as ReaderPageRasterPublicationRegistration.Started
		).request
		val queuedRequest = (
			ledger.begin("frozen-close-queued", queuedValue) { persisted ->
				callbacks += "queued-$persisted"
			} as ReaderPageRasterPublicationRegistration.Started
		).request
		val activeStarted = CompletableDeferred<Unit>()
		val activeFinished = CompletableDeferred<Unit>()
		val queuedBodyEntered = CompletableDeferred<Unit>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.schedule(activeRequest) {
				check(ledger.acquireForPersistence(activeRequest) === activeValue)
				activeStarted.complete(Unit)
				try {
					awaitCancellation()
				} finally {
					ledger.complete(activeRequest, persisted = false)
					activeFinished.complete(Unit)
				}
			}
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.schedule(queuedRequest) {
				queuedBodyEntered.complete(Unit)
				if (ledger.acquireForPersistence(queuedRequest) != null) {
					ledger.complete(queuedRequest, persisted = false)
				}
			}
		)
		activeStarted.await()
		val domain = ReaderLegacyPhysicalDomain(105L, ReaderLegacyFreezeToken(106L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val frozenRows = source.snapshotFrozenOwnership().filter { row ->
			row.physicalIdentity.source == ReaderLegacyInventorySource.RasterPublication
		}
		assertEquals(10, frozenRows.size)
		assertEquals(10, frozenRows.map { it.physicalIdentity }.toSet().size)
		val closing = source.close()
		try {
			closing.await()
			activeFinished.await()
			assertFalse(queuedBodyEntered.isCompleted)
			assertEquals(0, ledger.entryCount())
			assertEquals(
				setOf("active-false", "queued-false"),
				synchronized(callbacks) { callbacks.toSet() }
			)
			assertEquals(2, synchronized(callbacks) { callbacks.size })
			assertTrue(activeValue.generation.value.isRecycled)
			assertTrue(queuedValue.generation.value.isRecycled)
			val terminalRows = source.snapshotFrozenOwnership().filter { row ->
				row.physicalIdentity.source == ReaderLegacyInventorySource.RasterPublication
			}
			assertEquals(
				frozenRows.map { it.physicalIdentity }.toSet(),
				terminalRows.map { it.physicalIdentity }.toSet()
			)
			assertTrue(terminalRows.all { row ->
				row.state == ReaderLegacyResourceState.Released
			})
			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			terminalRows.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					source.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
			assertEquals(
				terminalRows.map { it.physicalIdentity }.toSet(),
				confirmations.toSet()
			)
			assertTrue(source.snapshotFrozenOwnership().none { row ->
				row.physicalIdentity.source == ReaderLegacyInventorySource.RasterPublication
			})
		} finally {
			ledger.invalidate()
			runCatching { closing.await() }
		}
	}

	@Test
	fun frozenCloseDispatchesPublicationCleanupOutsideCloseFenceBeforeRasterCancellation() =
		runTest {
			val source = ReaderPageTurnBundleSource()
			val ledger = publicationLedger(source)
			val closeFenceLock = checkNotNull(
				ReaderPageTurnBundleSource::class.java
					.getDeclaredField("closeFenceLock")
					.apply { isAccessible = true }
					.get(source)
			)
			val rasterJob = ReaderPageTurnBundleSource::class.java
				.getDeclaredField("rasterJob")
				.apply { isAccessible = true }
				.get(source) as Job
			val value = publicationValue(3)
			val callbackCount = AtomicLong()
			val callbackEnteredCloseFence = AtomicBoolean()
			assertTrue(
				ledger.begin("frozen-close-lock-boundary", value) { persisted ->
					callbackCount.incrementAndGet()
					check(!persisted)
					check(!Thread.holdsLock(closeFenceLock)) {
						"Publication callback ran while the close fence was held"
					}
					synchronized(closeFenceLock) {
						callbackEnteredCloseFence.set(true)
					}
					check(rasterJob.isActive) {
						"Raster work was cancelled before publication cleanup"
					}
				} is ReaderPageRasterPublicationRegistration.Started
			)
			var closing: Deferred<Unit>? = null
			try {
				val domain = ReaderLegacyPhysicalDomain(107L, ReaderLegacyFreezeToken(108L))
				assertEquals(
					ReaderPortCommandResult.Accepted,
					source.freezeForTransitionActivation(domain)
				)
				val frozenRows = source.snapshotFrozenOwnership().filter { row ->
					row.physicalIdentity.source == ReaderLegacyInventorySource.RasterPublication
				}
				assertTrue(frozenRows.isNotEmpty())
				assertEquals(
					frozenRows.size,
					frozenRows.map { row -> row.physicalIdentity }.toSet().size
				)

				closing = source.close()
				closing.await()

				assertEquals(1L, callbackCount.get())
				assertTrue(callbackEnteredCloseFence.get())
				assertFalse(rasterJob.isActive)
				assertTrue(value.generation.value.isRecycled)
				val terminalRows = source.snapshotFrozenOwnership().filter { row ->
					row.physicalIdentity.source == ReaderLegacyInventorySource.RasterPublication
				}
				assertEquals(
					frozenRows.map { row -> row.physicalIdentity }.toSet(),
					terminalRows.map { row -> row.physicalIdentity }.toSet()
				)
				assertTrue(terminalRows.all { row ->
					row.state == ReaderLegacyResourceState.Released
				})
				val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
				terminalRows.forEach { row ->
					assertEquals(
						ReaderPortCommandResult.Accepted,
						source.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
					)
				}
				assertEquals(
					terminalRows.map { row -> row.physicalIdentity }.toSet(),
					confirmations.toSet()
				)
			} finally {
				runCatching { (closing ?: source.close()).await() }
			}
		}

	@Test
	fun reentrantFrozenCloseSharesFenceCompletionBeforeTeardownStarts() = runTest {
		val source = ReaderPageTurnBundleSource()
		val ledger = publicationLedger(source)
		val closeFenceLock = checkNotNull(
			ReaderPageTurnBundleSource::class.java
				.getDeclaredField("closeFenceLock")
				.apply { isAccessible = true }
				.get(source)
		)
		val rasterJob = ReaderPageTurnBundleSource::class.java
			.getDeclaredField("rasterJob")
			.apply { isAccessible = true }
			.get(source) as Job
		@Suppress("UNCHECKED_CAST")
		val activeLiveValidations = ReaderPageTurnBundleSource::class.java
			.getDeclaredField("activeLiveValidations")
			.apply { isAccessible = true }
			.get(source) as MutableSet<ReaderPageRelocationContentValidationHandle>
		fun teardownStarted(): Boolean {
			val teardown = ReaderPageTurnBundleSource::class.java
				.getDeclaredField("teardown")
				.apply { isAccessible = true }
				.get(source)
			val closeTask = teardown.javaClass
				.getDeclaredField("closeTask")
				.apply { isAccessible = true }
				.get(teardown)
			return closeTask.javaClass
				.getDeclaredField("completion")
				.apply { isAccessible = true }
				.get(closeTask) != null
		}
		val liveValidationCancelled = AtomicBoolean()
		lateinit var liveValidation: ReaderPageRelocationContentValidationHandle
		liveValidation = ReaderPageRelocationContentValidationHandle {
			val removed = synchronized(closeFenceLock) {
				activeLiveValidations.remove(liveValidation)
			}
			liveValidationCancelled.set(removed)
			removed
		}
		synchronized(closeFenceLock) {
			activeLiveValidations += liveValidation
		}
		val reentrantClosing = AtomicReference<Deferred<Unit>?>()
		val callbackCount = AtomicLong()
		val value = publicationValue(4)
		assertTrue(
			ledger.begin("reentrant-frozen-close", value) { persisted ->
				callbackCount.incrementAndGet()
				check(!persisted)
				check(!Thread.holdsLock(closeFenceLock))
				check(!liveValidationCancelled.get())
				check(rasterJob.isActive)
				reentrantClosing.set(source.close())
				check(!teardownStarted()) {
					"Reentrant close started teardown before the owning close fence completed"
				}
			} is ReaderPageRasterPublicationRegistration.Started
		)
		var closing: Deferred<Unit>? = null
		try {
			val domain = ReaderLegacyPhysicalDomain(109L, ReaderLegacyFreezeToken(110L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val frozenRows = source.snapshotFrozenOwnership().filter { row ->
				row.physicalIdentity.source == ReaderLegacyInventorySource.RasterPublication
			}
			assertTrue(frozenRows.isNotEmpty())
			assertEquals(
				frozenRows.size,
				frozenRows.map { row -> row.physicalIdentity }.toSet().size
			)

			closing = source.close()
			assertTrue(closing === reentrantClosing.get())
			closing.await()

			assertEquals(1L, callbackCount.get())
			assertTrue(teardownStarted())
			assertTrue(liveValidationCancelled.get())
			assertTrue(synchronized(closeFenceLock) { activeLiveValidations.isEmpty() })
			assertFalse(rasterJob.isActive)
			assertTrue(value.generation.value.isRecycled)
			val terminalRows = source.snapshotFrozenOwnership().filter { row ->
				row.physicalIdentity.source == ReaderLegacyInventorySource.RasterPublication
			}
			assertEquals(
				frozenRows.map { row -> row.physicalIdentity }.toSet(),
				terminalRows.map { row -> row.physicalIdentity }.toSet()
			)
			assertTrue(terminalRows.all { row ->
				row.state == ReaderLegacyResourceState.Released
			})
			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			terminalRows.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					source.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
			assertEquals(
				terminalRows.map { row -> row.physicalIdentity }.toSet(),
				confirmations.toSet()
			)
		} finally {
			runCatching { (closing ?: source.close()).await() }
		}
	}

	@Test
	fun throwingValidationCancellationStillCompletesEveryCloseFenceAction() = runTest {
		lateinit var source: ReaderPageTurnBundleSource
		lateinit var rasterJob: Job
		fun teardownStarted(): Boolean {
			val teardown = ReaderPageTurnBundleSource::class.java
				.getDeclaredField("teardown")
				.apply { isAccessible = true }
				.get(source)
			val closeTask = teardown.javaClass
				.getDeclaredField("closeTask")
				.apply { isAccessible = true }
				.get(teardown)
			return closeTask.javaClass
				.getDeclaredField("completion")
				.apply { isAccessible = true }
				.get(closeTask) != null
		}
		val descriptorSnapshot = slideSnapshot()
		val descriptorOwners = ReaderPagePendingCallbackOwners<ReaderPageSlideSnapshot>(
			retain = ReaderPageSlideSnapshot::retain,
			release = ReaderPageSlideSnapshot::release
		)
		val descriptorAbandoned = AtomicBoolean()
		val pendingFailure = IllegalStateException("pending close failure")
		assertNotNull(descriptorOwners.acquire(descriptorSnapshot) {
			descriptorAbandoned.set(true)
			check(!teardownStarted()) {
				"Teardown started before pending descriptor closure"
			}
			check(!rasterJob.isActive) {
				"Pending descriptor closure ran before raster cancellation"
			}
			throw pendingFailure
		})
		source = ReaderPageTurnBundleSource(
			pendingDescriptorOwnersOverride = descriptorOwners
		)
		rasterJob = ReaderPageTurnBundleSource::class.java
			.getDeclaredField("rasterJob")
			.apply { isAccessible = true }
			.get(source) as Job
		val closeFenceLock = checkNotNull(
			ReaderPageTurnBundleSource::class.java
				.getDeclaredField("closeFenceLock")
				.apply { isAccessible = true }
				.get(source)
		)
		@Suppress("UNCHECKED_CAST")
		val activeLiveValidations = ReaderPageTurnBundleSource::class.java
			.getDeclaredField("activeLiveValidations")
			.apply { isAccessible = true }
			.get(source) as MutableSet<ReaderPageRelocationContentValidationHandle>
		val ledger = publicationLedger(source)
		val publicationValue = publicationValue(5)
		val publicationInvalidated = AtomicBoolean()
		val secondCancellationAttempted = AtomicBoolean()
		assertTrue(
			ledger.begin("throwing-validation-close", publicationValue) { persisted ->
				check(!persisted)
				check(!Thread.holdsLock(closeFenceLock))
				check(secondCancellationAttempted.get()) {
					"Publication invalidation skipped the remaining validation cancellation"
				}
				check(!rasterJob.isActive) {
					"Publication invalidation ran before raster cancellation"
				}
				check(descriptorAbandoned.get()) {
					"Publication invalidation ran before pending descriptor closure"
				}
				check(!teardownStarted()) {
					"Teardown started before publication invalidation"
				}
				publicationInvalidated.set(true)
			} is ReaderPageRasterPublicationRegistration.Started
		)
		val cancellationFailure = IllegalStateException("validation cancellation failure")
		val reentrantClosing = AtomicReference<Deferred<Unit>?>()
		lateinit var throwingValidation: ReaderPageRelocationContentValidationHandle
		throwingValidation = ReaderPageRelocationContentValidationHandle {
			synchronized(closeFenceLock) {
				activeLiveValidations.remove(throwingValidation)
			}
			reentrantClosing.set(source.close())
			throw cancellationFailure
		}
		lateinit var remainingValidation: ReaderPageRelocationContentValidationHandle
		remainingValidation = ReaderPageRelocationContentValidationHandle {
			secondCancellationAttempted.set(true)
			synchronized(closeFenceLock) {
				activeLiveValidations.remove(remainingValidation)
			}
			true
		}
		synchronized(closeFenceLock) {
			activeLiveValidations += throwingValidation
			activeLiveValidations += remainingValidation
		}
		var closing: Deferred<Unit>? = null
		try {
			val closeAdmission = runCatching { source.close() }
			assertTrue(
				closeAdmission.isSuccess,
				"Close admission did not return its shared terminal after cancellation failure"
			)
			closing = closeAdmission.getOrThrow()
			assertTrue(closing === reentrantClosing.get())
			val terminalFailure = runCatching { closing.await() }.exceptionOrNull()
			val teardownFailure = assertNotNull(
				terminalFailure as? ReaderPageTeardownException
			)
			assertEquals(ReaderPageTeardownStage.RasterInvalidation, teardownFailure.stage)
			assertTrue(teardownFailure.cause === cancellationFailure)

			assertTrue(secondCancellationAttempted.get())
			assertFalse(rasterJob.isActive)
			assertTrue(descriptorAbandoned.get())
			assertEquals(0, descriptorOwners.pendingCount())
			assertTrue(publicationInvalidated.get())
			assertEquals(0, ledger.entryCount())
			assertTrue(publicationValue.generation.value.isRecycled)
			assertTrue(synchronized(closeFenceLock) { activeLiveValidations.isEmpty() })
			val aggregate = ReaderPageTurnBundleSource::class.java
				.getDeclaredField("closeInvalidationFailure")
				.apply { isAccessible = true }
				.get(source) as Throwable?
			assertTrue(aggregate === cancellationFailure)
			assertEquals(listOf(pendingFailure), aggregate.suppressed.toList())
			val fenceCompletion = ReaderPageTurnBundleSource::class.java
				.getDeclaredField("closeFenceCompletion")
				.apply { isAccessible = true }
				.get(source) as Deferred<*>
			assertTrue(fenceCompletion.isCompleted)
		} finally {
			rasterJob.cancel()
			synchronized(closeFenceLock) { activeLiveValidations.clear() }
			runCatching { descriptorOwners.close() }
			runCatching { ledger.invalidate() }
			runCatching { (closing ?: reentrantClosing.get() ?: source.close()).await() }
			descriptorSnapshot.releaseCacheOwnership()
		}
	}

	@Test
	fun closeInProgressFreezeFencesAllEightSourcesAndInventoriesActivePublication() = runTest {
		val scheduler = ReaderPageRasterPublicationScheduler(backgroundScope, 1)
		val workerStarted = CompletableDeferred<Unit>()
		val teardownReachedWorker = CompletableDeferred<Unit>()
		val allowCompletion = CompletableDeferred<Unit>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.schedule(ReaderPageRasterPublicationRequest("closing-freeze", 1L)) {
				workerStarted.complete(Unit)
				try {
					awaitCancellation()
				} finally {
					teardownReachedWorker.complete(Unit)
					withContext(NonCancellable) { allowCompletion.await() }
				}
			}
		)
		workerStarted.await()
		val source = ReaderPageTurnBundleSource(publicationSchedulerOverride = scheduler)
		val closing = source.close()
		try {
			teardownReachedWorker.await()
			val domain = ReaderLegacyPhysicalDomain(93L, ReaderLegacyFreezeToken(94L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val inventories = assertNotNull(source.snapshotConnectedFrozenOwnership())
			assertEquals(8, inventories.size)
			assertEquals(
				setOf(
					ReaderLegacyInventorySource.RasterSnapshotCache,
					ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback,
					ReaderLegacyInventorySource.RasterHydration,
					ReaderLegacyInventorySource.RasterPublication,
					ReaderLegacyInventorySource.RasterGenerationAndPersistence,
					ReaderLegacyInventorySource.RasterCaptureAndVisualState,
					ReaderLegacyInventorySource.RasterLiveValidation,
					ReaderLegacyInventorySource.RasterStoreAndCache
				),
				inventories.map { it.source }.toSet()
			)
			assertTrue(inventories.all { it.domain == domain })
			val publicationRow = inventories.single {
				it.source == ReaderLegacyInventorySource.RasterPublication
			}.resources.single()
			val teardownRow = inventories.single {
				it.source == ReaderLegacyInventorySource.RasterStoreAndCache
			}.resources.single()
			assertEquals(domain, publicationRow.physicalIdentity.domain)
			assertEquals(domain, teardownRow.physicalIdentity.domain)

			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			listOf(publicationRow, teardownRow).forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					source.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
			assertTrue(confirmations.isEmpty())
			allowCompletion.complete(Unit)
			closing.await()
			teardownCompletionJob(source).join()
			assertEquals(
				setOf(publicationRow.physicalIdentity, teardownRow.physicalIdentity),
				confirmations.toSet()
			)
		} finally {
			allowCompletion.complete(Unit)
			closing.await()
		}
	}

	@Test
	fun alreadyStartedTeardownIsAnExactOwnerUntilActualCompletion() = runTest {
		val scheduler = ReaderPageRasterPublicationScheduler(backgroundScope, 1)
		val workerStarted = CompletableDeferred<Unit>()
		val teardownReachedWorker = CompletableDeferred<Unit>()
		val allowCompletion = CompletableDeferred<Unit>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.schedule(ReaderPageRasterPublicationRequest("teardown", 1L)) {
				workerStarted.complete(Unit)
				try {
					awaitCancellation()
				} finally {
					teardownReachedWorker.complete(Unit)
					withContext(NonCancellable) { allowCompletion.await() }
				}
			}
		)
		workerStarted.await()
		val source = ReaderPageTurnBundleSource(publicationSchedulerOverride = scheduler)
		val closing = async { source.closeAndJoin() }
		try {
			teardownReachedWorker.await()
			val domain = ReaderLegacyPhysicalDomain(51L, ReaderLegacyFreezeToken(52L))
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.freezeForTransitionActivation(domain)
			)
			val row = source.snapshotFrozenOwnership().single {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterStoreAndCache
			}
			val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
			assertTrue(confirmed.isEmpty())
			allowCompletion.complete(Unit)
			closing.await()
			assertEquals(listOf(row.physicalIdentity), confirmed)
		} finally {
			allowCompletion.complete(Unit)
			closing.await()
		}
	}

	@Test
	fun frozenCloseRegistersExactTeardownOwnerUntilPhysicalCompletion() = runTest {
		val scheduler = ReaderPageRasterPublicationScheduler(backgroundScope, 1)
		val workerStarted = CompletableDeferred<Unit>()
		val teardownReachedWorker = CompletableDeferred<Unit>()
		val allowCompletion = CompletableDeferred<Unit>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			scheduler.schedule(ReaderPageRasterPublicationRequest("frozen-close", 1L)) {
				workerStarted.complete(Unit)
				try {
					awaitCancellation()
				} finally {
					teardownReachedWorker.complete(Unit)
					withContext(NonCancellable) { allowCompletion.await() }
				}
			}
		)
		workerStarted.await()
		val source = ReaderPageTurnBundleSource(publicationSchedulerOverride = scheduler)
		val domain = ReaderLegacyPhysicalDomain(85L, ReaderLegacyFreezeToken(86L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val closing = source.close()
		try {
			teardownReachedWorker.await()
			assertEquals(
				setOf(domain),
				assertNotNull(source.snapshotConnectedFrozenOwnership())
					.map { it.domain }
					.toSet()
			)
			val row = source.snapshotFrozenOwnership().single {
				it.physicalIdentity.source == ReaderLegacyInventorySource.RasterStoreAndCache
			}
			assertEquals(domain, row.physicalIdentity.domain)
			val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
			)
			assertTrue(confirmed.isEmpty())

			allowCompletion.complete(Unit)
			closing.await()
			teardownCompletionJob(source).join()
			assertEquals(listOf(row.physicalIdentity), confirmed)
			assertEquals(
				setOf(domain),
				assertNotNull(source.snapshotConnectedFrozenOwnership())
					.map { it.domain }
					.toSet()
			)
		} finally {
			allowCompletion.complete(Unit)
			closing.await()
		}
	}

	@Test
	fun closeWaitsForClaimedJavascriptDescriptorCallbackFinalization() = runTest {
		val snapshot = slideSnapshot()
		val descriptorOwners = ReaderPagePendingCallbackOwners<ReaderPageSlideSnapshot>(
			retain = ReaderPageSlideSnapshot::retain,
			release = ReaderPageSlideSnapshot::release
		)
		val lease = checkNotNull(descriptorOwners.acquire(snapshot) {})
		val source = ReaderPageTurnBundleSource(
			pendingDescriptorOwnersOverride = descriptorOwners
		)
		val callbackEntered = CountDownLatch(1)
		val allowCallbackReturn = CountDownLatch(1)
		var callbackFailure: Throwable? = null
		val javascriptDescriptorCallback = Thread {
			try {
				val claimed = checkNotNull(descriptorOwners.claim(lease))
				callbackEntered.countDown()
				check(allowCallbackReturn.await(5, TimeUnit.SECONDS))
				descriptorOwners.complete(claimed)
			} catch (failure: Throwable) {
				callbackFailure = failure
			}
		}
		javascriptDescriptorCallback.start()
		assertTrue(callbackEntered.await(5, TimeUnit.SECONDS))
		val closing = source.close()
		val closeFinished = CountDownLatch(1)
		closing.invokeOnCompletion { closeFinished.countDown() }
		try {
			assertFalse(
				closeFinished.await(250, TimeUnit.MILLISECONDS),
				"Teardown completed while a claimed descriptor callback was still on-stack"
			)
			assertEquals(1, descriptorOwners.pendingCount())
		} finally {
			allowCallbackReturn.countDown()
			javascriptDescriptorCallback.join(5_000L)
			assertFalse(javascriptDescriptorCallback.isAlive)
			callbackFailure?.let { throw it }
			closing.await()
			snapshot.releaseCacheOwnership()
		}
		assertEquals(0, descriptorOwners.pendingCount())
	}

	@Test
	fun repeatedCloseReturnsSameTerminalWithoutCreatingAnotherTeardownOwner() = runTest {
		val source = ReaderPageTurnBundleSource()
		val first = source.close()
		first.await()
		teardownCompletionJob(source).join()

		val second = source.close()
		assertTrue(first === second)
		assertTrue(second.isCompleted)
		second.await()

		val domain = ReaderLegacyPhysicalDomain(87L, ReaderLegacyFreezeToken(88L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		assertTrue(source.snapshotFrozenOwnership().none {
			it.physicalIdentity.source == ReaderLegacyInventorySource.RasterStoreAndCache
		})
	}

	private fun awaitThreadBlockedIn(
		methodName: String,
		timeoutMillis: Long = 5_000L
	): Boolean {
		val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
		do {
			val blocked = Thread.getAllStackTraces().any { (thread, stackTrace) ->
				thread.state == Thread.State.BLOCKED &&
					stackTrace.any { frame -> frame.methodName == methodName }
			}
			if (blocked) return true
			Thread.sleep(1L)
		} while (System.nanoTime() < deadline)
		return false
	}

	private fun initializeRasterCacheOnMain(
		source: ReaderPageTurnBundleSource,
		webView: WebView
	) {
		val outcome = AtomicReference<Result<Unit>?>()
		val completed = CountDownLatch(1)
		val initialization = Thread {
			try {
				outcome.set(runCatching {
					runBlocking { source.initializeRasterCache(webView) }
				})
			} finally {
				completed.countDown()
			}
		}
		initialization.start()
		val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
		while (!completed.await(1L, TimeUnit.MILLISECONDS) && System.nanoTime() < deadline) {
			mainLooper.idle()
		}
		mainLooper.idle()
		initialization.join(5_000L)
		check(!initialization.isAlive) {
			"Raster cache initialization did not finish"
		}
		checkNotNull(outcome.get()).getOrThrow()
	}

	private suspend fun assertFrozenStoreAndCacheOwnerSurvivesTeardown(
		inventoryBeforeClose: Boolean,
		seed: Int
	) {
		val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
		val webView = WebView(activity)
		activity.setContentView(webView)
		val source = ReaderPageTurnBundleSource()
		initializeRasterCacheOnMain(source, webView)
		@Suppress("UNCHECKED_CAST")
		val cache = ReaderPageTurnBundleSource::class.java
			.getDeclaredField("rasterCache")
			.apply { isAccessible = true }
			.get(source) as ReaderPageRasterCache<Bitmap>
		val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
		val key = ReaderPageRasterKey(
			publicationHash = "group-g-publication-$seed",
			paginationHash = "group-g-pagination-$seed",
			spineIndex = seed,
			hrefHash = "group-g-href-$seed",
			chapterPageIndex = seed,
			visualPageOrdinal = seed,
			viewportWidth = 20,
			viewportHeight = 30,
			layoutHash = "group-g-layout-$seed",
			decorationHash = "group-g-decoration-$seed",
			quality = ReaderPageBitmapQuality.Balanced
		)
		val metadata = ReaderPageRasterMetadata(
			surfaceLeft = 0,
			surfaceTop = 0,
			surfaceRight = 20,
			surfaceBottom = 30,
			fullLeafRect = ReaderPageRasterRect(0, 0, 20, 30),
			leftLeafRect = null,
			gutterRect = null,
			rightLeafRect = null,
			reverseFaceColor = Color.WHITE
		)
		val write = cache.write(key, metadata, bitmap)
		assertTrue(write.persisted)
		assertEquals(ReaderPageRasterValueOwnership.Store, write.ownership)
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 95L + seed,
			freezeToken = ReaderLegacyFreezeToken(96L + seed)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			source.freezeForTransitionActivation(domain)
		)
		val inventoriedIdentity = if (inventoryBeforeClose) {
			assertNotNull(source.snapshotConnectedFrozenOwnership())
				.single { it.source == ReaderLegacyInventorySource.RasterStoreAndCache }
				.resources.single()
				.physicalIdentity
		} else {
			null
		}

		source.closeAndJoin()
		teardownCompletionJob(source).join()

		val connected = assertNotNull(source.snapshotConnectedFrozenOwnership())
		assertTrue(connected.all { inventory -> inventory.domain == domain })
		val rows = connected.single {
			it.source == ReaderLegacyInventorySource.RasterStoreAndCache
		}.resources
		assertEquals(2, rows.size)
		assertTrue(rows.all { row -> row.state == ReaderLegacyResourceState.Released })
		inventoriedIdentity?.let { identity ->
			assertTrue(rows.any { row -> row.physicalIdentity == identity })
		}
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				source.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertTrue(
			assertNotNull(source.snapshotConnectedFrozenOwnership())
				.single { it.source == ReaderLegacyInventorySource.RasterStoreAndCache }
				.resources.isEmpty()
		)
		listOf("persistentStore", "rasterCache").forEach { fieldName ->
			val field = ReaderPageTurnBundleSource::class.java
				.getDeclaredField(fieldName)
				.apply { isAccessible = true }
			assertNull(field.get(source), "$fieldName must clear after exact terminal drain")
		}
		assertTrue(bitmap.isRecycled)
	}

	private class BoundedValidationDispatcherGate : AutoCloseable {
		val dispatcher = Executors.newSingleThreadExecutor { action ->
			Thread(action, "reader-validation-test").apply { isDaemon = true }
		}.asCoroutineDispatcher()
		private val scopeJob = Job()
		private val started = CountDownLatch(1)
		private val released = CountDownLatch(1)
		private val closed = AtomicBoolean()

		init {
			CoroutineScope(scopeJob + dispatcher).launch {
				started.countDown()
				released.await()
			}
			try {
				check(started.await(5, TimeUnit.SECONDS)) {
					"Dedicated validation dispatcher did not start"
				}
			} catch (failure: Throwable) {
				close()
				throw failure
			}
		}

		fun release() {
			released.countDown()
		}

		override fun close() {
			if (!closed.compareAndSet(false, true)) return
			release()
			scopeJob.cancel()
			dispatcher.close()
		}
	}

	private fun publicationValue(seed: Int): ReaderPageRasterPublicationValue<Bitmap> =
		ReaderPageRasterPublicationValue(
			key = ReaderPageRasterKey(
				publicationHash = "frozen-close-publication-$seed",
				paginationHash = "frozen-close-pagination-$seed",
				spineIndex = seed,
				hrefHash = "frozen-close-href-$seed",
				chapterPageIndex = seed,
				visualPageOrdinal = seed,
				viewportWidth = 20,
				viewportHeight = 30,
				layoutHash = "frozen-close-layout-$seed",
				decorationHash = "frozen-close-decoration-$seed",
				quality = ReaderPageBitmapQuality.Balanced
			),
			generation = ReaderPageRasterGeneration(
				metadata = ReaderPageRasterMetadata(
					surfaceLeft = 0,
					surfaceTop = 0,
					surfaceRight = 20,
					surfaceBottom = 30,
					fullLeafRect = ReaderPageRasterRect(0, 0, 20, 30),
					leftLeafRect = null,
					gutterRect = null,
					rightLeafRect = null,
					reverseFaceColor = Color.WHITE
				),
				value = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888),
				captureMillis = 1L
			)
		)

	@Suppress("UNCHECKED_CAST")
	private fun publicationLedger(
		source: ReaderPageTurnBundleSource
	): ReaderPageRasterPublicationLedger<ReaderPageRasterPublicationValue<Bitmap>> =
		ReaderPageTurnBundleSource::class.java
			.getDeclaredField("publicationLedger")
			.apply { isAccessible = true }
			.get(source) as ReaderPageRasterPublicationLedger<
				ReaderPageRasterPublicationValue<Bitmap>
			>

	private fun ownedPersistenceBitmap(source: ReaderPageTurnBundleSource): Bitmap? {
		val attempts = ReaderPageTurnBundleSource::class.java.getDeclaredField("rasterPersistenceAttempts")
			.apply { isAccessible = true }.get(source) as Set<*>
		val attempt = attempts.singleOrNull() ?: return null
		return attempt.javaClass.getDeclaredField("ownedBitmap")
			.apply { isAccessible = true }.get(attempt) as Bitmap?
	}

	private fun bundlePersistenceJobCount(source: ReaderPageTurnBundleSource): Int =
		(ReaderPageTurnBundleSource::class.java.getDeclaredField("rasterPersistenceJobs")
			.apply { isAccessible = true }.get(source) as Set<*>).size

	private fun rasterInitializationMutex(source: ReaderPageTurnBundleSource): Mutex =
		ReaderPageTurnBundleSource::class.java
			.getDeclaredField("rasterInitializationMutex")
			.apply { isAccessible = true }
			.get(source) as Mutex

	@Suppress("UNCHECKED_CAST")
	private fun bundleRasterScheduler(
		source: ReaderPageTurnBundleSource
	): ReaderPageRasterScheduler<Bitmap>? = ReaderPageTurnBundleSource::class.java
		.getDeclaredField("rasterScheduler")
		.apply { isAccessible = true }
		.get(source) as ReaderPageRasterScheduler<Bitmap>?

	@Suppress("UNCHECKED_CAST")
	private fun bundleRasterCache(
		source: ReaderPageTurnBundleSource
	): ReaderPageRasterCache<Bitmap>? = ReaderPageTurnBundleSource::class.java
		.getDeclaredField("rasterCache")
		.apply { isAccessible = true }
		.get(source) as ReaderPageRasterCache<Bitmap>?

	@Suppress("UNCHECKED_CAST")
	private fun bundlePersistentStore(
		source: ReaderPageTurnBundleSource
	): ReaderPageRasterCacheStore<Bitmap>? = ReaderPageTurnBundleSource::class.java
		.getDeclaredField("persistentStore")
		.apply { isAccessible = true }
		.get(source) as ReaderPageRasterCacheStore<Bitmap>?

	private fun rasterPhysicalLayoutEpoch(source: ReaderPageTurnBundleSource): Long =
		(
			ReaderPageTurnBundleSource::class.java
				.getDeclaredField("rasterPhysicalLayoutEpoch")
				.apply { isAccessible = true }
				.get(source) as AtomicLong
		).get()

	private fun teardownCompletionJob(source: ReaderPageTurnBundleSource): Job {
		val field = ReaderPageTurnBundleSource::class.java
			.getDeclaredField("teardownJob")
			.apply { isAccessible = true }
		return field.get(source) as Job
	}

	private fun slideSnapshot(
		surfaceRectInWindow: Rect = Rect(0, 0, 20, 30)
	) = ReaderPageSlideSnapshot(
		key = ReaderPageSlideSnapshotKey(
			visualPageIndex = 1,
			kind = ReaderPageTurnTransitionKind.PortraitSlide,
			bitmapQuality = ReaderPageBitmapQuality.Balanced,
			bitmapWidth = 20,
			bitmapHeight = 30,
			surfaceWidth = 20,
			surfaceHeight = 30
		),
		bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888),
		surfaceRectInWindow = Rect(surfaceRectInWindow),
		leafGeometry = ReaderPageTurnLeafGeometry(
			fullLeafRect = ReaderPageTurnPixelRect(0, 0, 20, 30),
			leftLeafRect = null,
			gutterRect = null,
			rightLeafRect = null
		),
		reverseFaceColor = Color.WHITE
	)

	private fun relocationRequest() = ReaderPageRelocationRequest(
		token = ReaderPageRelocationToken("validation"),
		gestureId = 1L,
		rasterGeneration = 2L,
		textureGeneration = 3L,
		sourceOrdinal = 0,
		destinationOrdinal = 1,
		logicalDirection = ReaderPageTurnDirection.Next,
		foliateSessionId = "session"
	)

	private fun layoutForCapture(view: View) {
		view.layoutParams = FrameLayout.LayoutParams(20, 30)
		view.measure(
			View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(30, View.MeasureSpec.EXACTLY)
		)
		view.left = 0
		view.top = 0
		view.right = 20
		view.bottom = 30
	}

	private class HeldDescriptorPort : ReaderPageRasterDescriptorPort {
		private var pending: Pair<Int, (ReaderPageRasterDescriptor?) -> Unit>? = null
		var requestCount = 0
			private set

		override fun request(
			webView: WebView,
			pageIndex: Int,
			onDescriptor: (ReaderPageRasterDescriptor?) -> Unit
		) {
			requestCount += 1
			pending = pageIndex to onDescriptor
		}

		fun respond(pageIndex: Int) {
			val request = checkNotNull(pending)
			pending = null
			assertEquals(pageIndex, request.first)
			request.second(
				ReaderPageRasterDescriptor(
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
			)
		}
	}

	private open class DeferredJavascriptWebView(activity: Activity) : WebView(activity) {
		private val callbacks = ArrayDeque<ValueCallback<String?>>()
		private val visualCallbacks = ArrayDeque<Pair<Long, VisualStateCallback>>()
		private val animationCallbacks = ArrayDeque<Runnable>()

		val pendingJavascriptCount: Int
			get() = callbacks.size

		val pendingVisualStateCount: Int
			get() = visualCallbacks.size

		val pendingAnimationCount: Int
			get() = animationCallbacks.size

		override fun evaluateJavascript(
			script: String,
			resultCallback: ValueCallback<String?>?
		) {
			resultCallback?.let(callbacks::addLast)
		}

		override fun postVisualStateCallback(
			requestId: Long,
			callback: VisualStateCallback
		) {
			visualCallbacks.addLast(requestId to callback)
		}

		override fun postOnAnimation(action: Runnable) {
			animationCallbacks.addLast(action)
		}

		fun completeNextJavascript(result: String?) {
			callbacks.removeFirst().onReceiveValue(result)
		}

		fun completeNextVisualState() {
			val (requestId, callback) = visualCallbacks.removeFirst()
			callback.onComplete(requestId)
		}

		fun completeNextAnimation() {
			animationCallbacks.removeFirst().run()
		}
	}

	private class DeferredPresentedFrameSurface(activity: Activity) : PageSurfaceView(activity) {
		private val callbacks = linkedMapOf<Long, Runnable>()
		private val validSurface = Surface(SurfaceTexture(0))
		private val validHolder = Proxy.newProxyInstance(
			SurfaceHolder::class.java.classLoader,
			arrayOf(SurfaceHolder::class.java)
		) { _, method, _ ->
			when (method.name) {
				"getSurface" -> validSurface
				"getSurfaceFrame" -> Rect(0, 0, 20, 30)
				"isCreating" -> false
				else -> null
			}
		} as SurfaceHolder
		private var useValidHolder = false
		private var nextRequestId = 1L

		val pendingPresentedFrameCount: Int
			get() = callbacks.size

		fun releaseSurface() {
			validSurface.release()
		}

		override fun getHolder(): SurfaceHolder =
			if (useValidHolder) validHolder else super.getHolder()

		fun useValidSurface() {
			useValidHolder = true
		}

		fun completeNextPresentedFrame() {
			val requestId = callbacks.keys.first()
			callbacks.remove(requestId)?.run()
		}

		override fun requestNextPresentedFrame(callback: Runnable): Long =
			nextRequestId++.also { requestId -> callbacks[requestId] = callback }

		override fun cancelPresentedFrameRequest(requestId: Long): Boolean =
			callbacks.remove(requestId) != null
	}

	private class ColoredDrawWebView(activity: Activity) : DeferredJavascriptWebView(activity) {
		override fun draw(canvas: Canvas) {
			canvas.drawColor(Color.WHITE)
			canvas.drawRect(
				0f,
				0f,
				width / 2f,
				height.toFloat(),
				Paint().apply { color = Color.BLACK }
			)
		}
	}

	private class BlockingDrawWebView(activity: Activity) : DeferredJavascriptWebView(activity) {
		val drawStarted = CountDownLatch(1)
		val allowDrawCompletion = CountDownLatch(1)

		override fun draw(canvas: Canvas) {
			drawStarted.countDown()
			check(allowDrawCompletion.await(5, TimeUnit.SECONDS))
			super.draw(canvas)
		}
	}

	@Implements(PixelCopy::class)
	class DeferredPixelCopyShadow {
		companion object {
			private data class Pending(
				val destination: Bitmap,
				val listener: PixelCopy.OnPixelCopyFinishedListener
			)

			private val callbacks = ArrayDeque<Pending>()

			val pendingCount: Int
				get() = callbacks.size

			@Implementation
			@JvmStatic
			fun request(
				source: Surface,
				sourceRect: Rect,
				destination: Bitmap,
				listener: PixelCopy.OnPixelCopyFinishedListener,
				handler: Handler
			) {
				check(source.isValid)
				check(!sourceRect.isEmpty)
				check(destination.isMutable)
				check(handler.looper === android.os.Looper.getMainLooper())
				callbacks.addLast(Pending(destination, listener))
			}

			fun completeNext(result: Int, color: Int = Color.TRANSPARENT) {
				val pending = callbacks.removeFirst()
				if (result == PixelCopy.SUCCESS) pending.destination.eraseColor(color)
				pending.listener.onPixelCopyFinished(result)
			}

			@Resetter
			@JvmStatic
			fun reset() {
				callbacks.clear()
			}
		}
	}

	private fun previewPresentationReceiptJson() = """{
		"scope":"preview",
		"token":"preview",
		"pageIndex":1,
		"previewGeneration":2,
		"foregroundMutationGeneration":3,
		"presentationSequence":5
	}""".trimIndent()

	private fun validationLivePresentationReceiptJson() = """{
		"scope":"live",
		"token":"validation",
		"pageIndex":1,
		"foliateSessionId":"session",
		"rasterGeneration":2,
		"textureGeneration":3,
		"foregroundMutationGeneration":4,
		"presentationSequence":5
	}""".trimIndent()

	private fun livePresentationReceiptJson() = """{
		"scope":"live",
		"token":"live",
		"pageIndex":1,
		"foliateSessionId":"session",
		"rasterGeneration":2,
		"textureGeneration":3,
		"foregroundMutationGeneration":4,
		"presentationSequence":5
	}""".trimIndent()

	private fun captureGeometryJson() = """{
		"viewportWidth":20,
		"viewportHeight":30,
		"mode":"single",
		"pages":[{"role":"full","left":0,"top":0,"width":20,"height":30}],
		"reverseFaceColorArgb":4294967295
	}""".trimIndent()

	private fun captureResult() = ReaderPageTurnCaptureResult(
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
}
