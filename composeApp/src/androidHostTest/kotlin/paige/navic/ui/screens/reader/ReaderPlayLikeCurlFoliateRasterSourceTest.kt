package paige.navic.ui.screens.reader

import java.io.File
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPixelRect
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPlayLikeCurlFoliateRasterSourceTest {
	@Test
	fun portraitUsesTheMatchingFullPageSnapshot() {
		assertEquals(
			ReaderPlayLikeCurlFoliatePageRequest(
				logicalOrdinal = 4,
				sourcePageIndex = 4,
				leaf = ReaderPlayLikeCurlFoliateLeaf.Full
			),
			readerPlayLikeCurlFoliatePageRequest(
				orientation = ReaderPlayLikeCurlOrientation.Portrait,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
				logicalOrdinal = 4,
				pageCount = 8
			)
		)
	}

	@Test
	fun landscapeLtrUsesBothLeavesFromTheSameSpreadSnapshot() {
		assertEquals(
			ReaderPlayLikeCurlFoliatePageRequest(
				logicalOrdinal = 2,
				sourcePageIndex = 2,
				leaf = ReaderPlayLikeCurlFoliateLeaf.Left
			),
			readerPlayLikeCurlFoliatePageRequest(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
				logicalOrdinal = 2,
				pageCount = 8,
				spreadAnchorParity = 0
			)
		)
		assertEquals(
			ReaderPlayLikeCurlFoliatePageRequest(
				logicalOrdinal = 3,
				sourcePageIndex = 2,
				leaf = ReaderPlayLikeCurlFoliateLeaf.Right
			),
			readerPlayLikeCurlFoliatePageRequest(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
				logicalOrdinal = 3,
				pageCount = 8,
				spreadAnchorParity = 0
			)
		)
	}

	@Test
	fun landscapeUsesTheFoliateSpreadAnchorParityInsteadOfAssumingEvenSnapshots() {
		assertEquals(
			ReaderPlayLikeCurlFoliatePageRequest(
				logicalOrdinal = 13,
				sourcePageIndex = 13,
				leaf = ReaderPlayLikeCurlFoliateLeaf.Left
			),
			readerPlayLikeCurlFoliatePageRequest(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
				logicalOrdinal = 13,
				pageCount = 30,
				spreadAnchorParity = 1
			)
		)
		assertEquals(
			ReaderPlayLikeCurlFoliatePageRequest(
				logicalOrdinal = 14,
				sourcePageIndex = 13,
				leaf = ReaderPlayLikeCurlFoliateLeaf.Right
			),
			readerPlayLikeCurlFoliatePageRequest(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
				logicalOrdinal = 14,
				pageCount = 30,
				spreadAnchorParity = 1
			)
		)
		assertEquals(
			ReaderPlayLikeCurlFoliatePageRequest(
				logicalOrdinal = 12,
				sourcePageIndex = 11,
				leaf = ReaderPlayLikeCurlFoliateLeaf.Right
			),
			readerPlayLikeCurlFoliatePageRequest(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
				logicalOrdinal = 12,
				pageCount = 30,
				spreadAnchorParity = 1
			)
		)
	}

	@Test
	fun landscapeRtlReversesPhysicalLeavesWithoutChangingTheSpreadSnapshot() {
		assertEquals(
			ReaderPlayLikeCurlFoliatePageRequest(
				logicalOrdinal = 2,
				sourcePageIndex = 2,
				leaf = ReaderPlayLikeCurlFoliateLeaf.Right
			),
			readerPlayLikeCurlFoliatePageRequest(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Rtl,
				logicalOrdinal = 2,
				pageCount = 8,
				spreadAnchorParity = 0
			)
		)
		assertEquals(
			ReaderPlayLikeCurlFoliatePageRequest(
				logicalOrdinal = 3,
				sourcePageIndex = 2,
				leaf = ReaderPlayLikeCurlFoliateLeaf.Left
			),
			readerPlayLikeCurlFoliatePageRequest(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Rtl,
				logicalOrdinal = 3,
				pageCount = 8,
				spreadAnchorParity = 0
			)
		)
	}

	@Test
	fun requestsClampAtTheBookBoundaryBeforeChoosingTheLeaf() {
		assertEquals(
			ReaderPlayLikeCurlFoliatePageRequest(
				logicalOrdinal = 6,
				sourcePageIndex = 6,
				leaf = ReaderPlayLikeCurlFoliateLeaf.Left
			),
			readerPlayLikeCurlFoliatePageRequest(
				orientation = ReaderPlayLikeCurlOrientation.Landscape,
				readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
				logicalOrdinal = 9,
				pageCount = 7,
				spreadAnchorParity = 0
			)
		)
	}

	@Test
	fun landscapeRasterRequestDelegatesSpreadPlacementToSharedAuthority() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlFoliateRasterSource.android.kt"
		).readText()
		val request = source
			.substringAfter("internal fun readerPlayLikeCurlFoliatePageRequest(")
			.substringBefore("internal fun readerPlayLikeCurlFoliateLeafRect(")

		assertContains(request, "readerPlayLikeCurlSpreadSlot(")
		assertFalse(request.contains("val relativeParity"))
		assertFalse(request.contains("val anchorLeaf"))
		assertFalse(request.contains("val adjacentLeaf"))
	}

	@Test
	fun requestedLeafSelectsOnlyTheMatchingFoliatePixelRect() {
		val full = ReaderPageTurnPixelRect(0, 0, 900, 1_200)
		val left = ReaderPageTurnPixelRect(0, 0, 440, 1_200)
		val gutter = ReaderPageTurnPixelRect(440, 0, 460, 1_200)
		val right = ReaderPageTurnPixelRect(460, 0, 900, 1_200)
		val portrait = ReaderPageTurnLeafGeometry(full, null, null, null)
		val landscape = ReaderPageTurnLeafGeometry(null, left, gutter, right)

		assertEquals(full, readerPlayLikeCurlFoliateLeafRect(portrait, ReaderPlayLikeCurlFoliateLeaf.Full))
		assertEquals(left, readerPlayLikeCurlFoliateLeafRect(landscape, ReaderPlayLikeCurlFoliateLeaf.Left))
		assertEquals(right, readerPlayLikeCurlFoliateLeafRect(landscape, ReaderPlayLikeCurlFoliateLeaf.Right))
		assertNull(readerPlayLikeCurlFoliateLeafRect(portrait, ReaderPlayLikeCurlFoliateLeaf.Right))
		assertNull(readerPlayLikeCurlFoliateLeafRect(landscape, ReaderPlayLikeCurlFoliateLeaf.Full))
	}

	@Test
	fun halfResolutionRasterMapsBackToTheFullPhysicalFoliateLayout() {
		val geometry = ReaderPageTurnLeafGeometry(
			fullLeafRect = null,
			leftLeafRect = ReaderPageTurnPixelRect(0, 0, 725, 924),
			gutterRect = ReaderPageTurnPixelRect(725, 0, 725, 924),
			rightLeafRect = ReaderPageTurnPixelRect(725, 0, 1_450, 924)
		)

		val layout = checkNotNull(
			readerPlayLikeCurlRasterLayout(
				geometry = geometry,
				bitmapWidth = 1_450,
				bitmapHeight = 924,
				surfaceLeftInWindow = 30,
				surfaceTopInWindow = 100,
				surfaceRightInWindow = 2_930,
				surfaceBottomInWindow = 1_948
			)
		)

		assertEquals(
			ReaderPlayLikeCurlPhysicalRect(30, 100, 2_930, 1_948),
			layout.surfaceRectInWindow
		)
		assertEquals(
			ReaderPlayLikeCurlPhysicalRect(0, 0, 1_450, 1_848),
			layout.leftLeafRect
		)
		assertEquals(
			ReaderPlayLikeCurlPhysicalRect(1_450, 0, 1_450, 1_848),
			layout.gutterRect
		)
		assertEquals(
			ReaderPlayLikeCurlPhysicalRect(1_450, 0, 2_900, 1_848),
			layout.rightLeafRect
		)
		assertEquals(1_450, checkNotNull(layout.displayRect(ReaderPlayLikeCurlFoliateLeaf.Left)).widthPx)
		assertEquals(1_848, checkNotNull(layout.displayRect(ReaderPlayLikeCurlFoliateLeaf.Left)).heightPx)
	}

	@Test
	fun physicalLeafPlacementIsRelativeToTheRendererSurface() {
		val layout = checkNotNull(
			readerPlayLikeCurlRasterLayout(
				geometry = ReaderPageTurnLeafGeometry(
					fullLeafRect = null,
					leftLeafRect = ReaderPageTurnPixelRect(0, 0, 725, 924),
					gutterRect = ReaderPageTurnPixelRect(725, 0, 725, 924),
					rightLeafRect = ReaderPageTurnPixelRect(725, 0, 1_450, 924)
				),
				bitmapWidth = 1_450,
				bitmapHeight = 924,
				surfaceLeftInWindow = 30,
				surfaceTopInWindow = 100,
				surfaceRightInWindow = 2_930,
				surfaceBottomInWindow = 1_948
			)
		)

		val left = checkNotNull(
			layout.displayRect(
				leaf = ReaderPlayLikeCurlFoliateLeaf.Left,
				rendererLeftInWindow = 10,
				rendererTopInWindow = 40,
				rendererWidth = 3_000,
				rendererHeight = 2_000
			)
		)
		val right = checkNotNull(
			layout.displayRect(
				leaf = ReaderPlayLikeCurlFoliateLeaf.Right,
				rendererLeftInWindow = 10,
				rendererTopInWindow = 40,
				rendererWidth = 3_000,
				rendererHeight = 2_000
			)
		)

		assertEquals(20, left.leftPx)
		assertEquals(60, left.topPx)
		assertEquals(1_470, left.rightPx)
		assertEquals(1_908, left.bottomPx)
		assertEquals(1_470, right.leftPx)
		assertEquals(2_920, right.rightPx)
		assertNull(
			layout.displayRect(
				leaf = ReaderPlayLikeCurlFoliateLeaf.Right,
				rendererLeftInWindow = 10,
				rendererTopInWindow = 40,
				rendererWidth = 2_900,
				rendererHeight = 2_000
			)
		)
	}

	@Test
	fun portraitFillersBorrowTheFullLeafPlacement() {
		assertEquals(
			ReaderPlayLikeCurlFoliateLeaf.Full,
			readerPlayLikeCurlFillerFoliateLeaf(
				ReaderPlayLikeCurlOrientation.Portrait,
				ReaderPlayLikeCurlPhysicalLeaf.Left
			)
		)
		assertEquals(
			ReaderPlayLikeCurlFoliateLeaf.Full,
			readerPlayLikeCurlFillerFoliateLeaf(
				ReaderPlayLikeCurlOrientation.Portrait,
				ReaderPlayLikeCurlPhysicalLeaf.Right
			)
		)
		assertEquals(
			ReaderPlayLikeCurlFoliateLeaf.Left,
			readerPlayLikeCurlFillerFoliateLeaf(
				ReaderPlayLikeCurlOrientation.Landscape,
				ReaderPlayLikeCurlPhysicalLeaf.Left
			)
		)
	}

	@Test
	fun displayLayoutIsInvariantAcrossBalancedAndFullRasterQuality() {
		val balanced = readerPlayLikeCurlRasterLayout(
			geometry = ReaderPageTurnLeafGeometry(
				fullLeafRect = null,
				leftLeafRect = ReaderPageTurnPixelRect(0, 0, 725, 924),
				gutterRect = ReaderPageTurnPixelRect(725, 0, 725, 924),
				rightLeafRect = ReaderPageTurnPixelRect(725, 0, 1_450, 924)
			),
			bitmapWidth = 1_450,
			bitmapHeight = 924,
			surfaceLeftInWindow = 0,
			surfaceTopInWindow = 0,
			surfaceRightInWindow = 2_900,
			surfaceBottomInWindow = 1_848
		)
		val full = readerPlayLikeCurlRasterLayout(
			geometry = ReaderPageTurnLeafGeometry(
				fullLeafRect = null,
				leftLeafRect = ReaderPageTurnPixelRect(0, 0, 1_450, 1_848),
				gutterRect = ReaderPageTurnPixelRect(1_450, 0, 1_450, 1_848),
				rightLeafRect = ReaderPageTurnPixelRect(1_450, 0, 2_900, 1_848)
			),
			bitmapWidth = 2_900,
			bitmapHeight = 1_848,
			surfaceLeftInWindow = 0,
			surfaceTopInWindow = 0,
			surfaceRightInWindow = 2_900,
			surfaceBottomInWindow = 1_848
		)

		assertEquals(full, balanced)
	}

	@Test
	fun foliateRasterCopyOwnsAnIndependentOpaqueBitmap() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlFoliateRasterSource.android.kt"
		).readText()

		assertTrue(source.contains("Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)"))
		assertTrue(source.contains("Canvas(target).drawBitmap("))
		assertTrue(source.contains("target.eraseColor(paperColorArgb)"))
		assertTrue(source.contains("val layout = readerPlayLikeCurlRasterLayout("))
		assertTrue(source.contains("layout = layout"))
		assertTrue(source.contains("leaf = leaf"))
		assertTrue(source.contains("snapshot.release()"))
	}

	@Test
	fun foliateLoaderResolvesRetainedThenPersistentAndCopiesOffMain() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlFoliateRasterSource.android.kt"
		).readText()

		assertTrue(source.contains("bundleSource.resolveSnapshot("))
		assertTrue(source.contains("referenceSnapshotProvider(request)"))
		assertTrue(source.contains("key.publicationFence::isCurrent"))
		assertTrue(source.contains("private val copyDispatcher: CoroutineDispatcher = Dispatchers.Default"))
		assertTrue(source.contains("withContext(copyDispatcher)"))
		assertTrue(source.contains("readerPlayLikeCurlCopyRetainedFoliateLeaf(snapshot, request.leaf)"))
		assertTrue(!source.contains("captureCurrentSurface("))
		assertTrue(!source.contains("BitmapFactory"))
	}

	@Test
	fun qaRasterMissTargetsOnlyTheCurrentLogicalOrdinal() {
		val current = readerPlayLikeCurlFoliatePageRequest(
			orientation = ReaderPlayLikeCurlOrientation.Landscape,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
			logicalOrdinal = 2,
			pageCount = 8
		)
		val adjacentSameSnapshot = readerPlayLikeCurlFoliatePageRequest(
			orientation = ReaderPlayLikeCurlOrientation.Landscape,
			readerDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
			logicalOrdinal = 3,
			pageCount = 8
		)

		assertTrue(readerPlayLikeCurlQaMissIsEligible(current, targetLogicalOrdinal = 2))
		assertFalse(
			readerPlayLikeCurlQaMissIsEligible(
				adjacentSameSnapshot,
				targetLogicalOrdinal = 2
			)
		)
		assertTrue(
			readerPlayLikeCurlQaMissIsEligible(
				adjacentSameSnapshot,
				targetLogicalOrdinal = null
			)
		)
	}

	@Test
	fun qaRasterMissAppliesAndStartsRepairInsideOneCurrentMainThreadFence() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlFoliateRasterSource.android.kt"
		).readText()
		val load = source
			.substringAfter("override suspend fun load(")
			.substringBefore("internal suspend fun consumeQaMiss(")
		val qaMiss = source
			.substringAfter("internal suspend fun consumeQaMiss(")
			.substringBefore("suspend fun hydratePersistent(")

		assertContains(load, "if (consumeQaMiss(key)) return null")
		assertContains(qaMiss, "withContext(Dispatchers.Main.immediate)")
		assertContains(qaMiss, "key.publicationFence.isCurrent()")
		assertContains(qaMiss, "readerPlayLikeCurlQaMissIsEligible(")
		assertContains(qaMiss, "qaMissTargetOrdinalProvider()")
		assertContains(
			qaMiss,
			"registry.hasQueued(ReaderPageQaFault.MissNextRasterLoad)"
		)
		assertContains(qaMiss, "registry.consumeAndApply(")
		assertContains(qaMiss, "onQaMissingRaster(")
		assertTrue(
			qaMiss.indexOf("key.publicationFence.isCurrent()") <
				qaMiss.indexOf("registry.consumeAndApply(")
		)
		assertTrue(
			qaMiss.indexOf("readerPlayLikeCurlQaMissIsEligible(") <
				qaMiss.indexOf("registry.consumeAndApply(")
		)
		assertTrue(
			qaMiss.indexOf("registry.consumeAndApply(") <
				qaMiss.indexOf("onQaMissingRaster(")
		)
	}

	@Test
	fun missingFoliateRasterReportsItsSourcePageForTargetedRepair() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlFoliateRasterSource.android.kt"
		).readText()

		val load = source
			.substringAfter("override suspend fun load(")
			.substringBefore("suspend fun hydratePersistent(")
		assertTrue(source.contains("private val onMissingRaster: (Int) -> Unit"))
		assertTrue(load.contains("onMissingRaster(request.sourcePageIndex)"))
		assertTrue(!load.contains("onMissingRaster(request.logicalOrdinal)"))
		assertTrue(
			load.indexOf("val snapshot = resolve(") <
				load.indexOf("onMissingRaster(request.sourcePageIndex)")
		)
	}

	private fun sourceFile(relativePath: String): File {
		var directory = File(System.getProperty("user.dir")).absoluteFile
		repeat(8) {
			File(directory, relativePath).takeIf(File::isFile)?.let { return it }
			directory = directory.parentFile ?: return@repeat
		}
		error("Could not locate $relativePath from ${System.getProperty("user.dir")}")
	}
}
