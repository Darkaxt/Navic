package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole
import paige.navic.reader.ReaderPageTurnPixelRect

@RunWith(RobolectricTestRunner::class)
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
