package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReaderWhispersyncCueMapGeometryTest {
	@Test
	fun foliatePageLocalCueStartsProjectIntoTheVisibleNativeSpread() {
		val geometry = ReaderPageTurnCaptureGeometry(
			viewportWidth = 1_200.0,
			viewportHeight = 800.0,
			mode = ReaderPageTurnLayoutMode.Spread,
			pages = listOf(
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Left, 0.0, 0.0, 600.0, 800.0),
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Right, 600.0, 0.0, 600.0, 800.0)
			)
		)
		val receipt = ReaderWhispersyncCueMapGeometryReceipt(
			revisionDigest = "5f04c2a19e7d",
			presentationGeneration = 8L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity("session-a", 41L),
			markers = listOf(
				marker(sourceOrdinal = 70, geometry, ReaderPageTurnPageRole.Left, left = 100.0, top = 200.0),
				marker(sourceOrdinal = 82, geometry, ReaderPageTurnPageRole.Right, left = 50.0, top = 100.0)
			)
		)

		val anchors = receipt.viewportAnchors(viewWidth = 2_400f, viewHeight = 1_600f)

		assertEquals(listOf(70, 82), anchors.map { anchor -> anchor.sourceOrdinal })
		assertEquals(200f, anchors[0].x)
		assertEquals(400f, anchors[0].y)
		assertEquals(1_300f, anchors[1].x)
		assertEquals(200f, anchors[1].y)
	}

	@Test
	fun mixedFoliateAnchorAuthoritiesAreRejected() {
		val geometry = ReaderPageTurnCaptureGeometry(
			viewportWidth = 1_200.0,
			viewportHeight = 800.0,
			mode = ReaderPageTurnLayoutMode.Single,
			pages = listOf(
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Full, 0.0, 0.0, 1_200.0, 800.0)
			)
		)
		val first = marker(70, geometry, ReaderPageTurnPageRole.Full, left = 100.0, top = 200.0)
		val second = marker(82, geometry, ReaderPageTurnPageRole.Full, left = 300.0, top = 400.0)
			.let { marker ->
				marker.copy(
					anchorReceipt = marker.anchorReceipt.copy(commitSequence = 74L)
				)
			}

		assertFailsWith<IllegalArgumentException> {
			ReaderWhispersyncCueMapGeometryReceipt(
				revisionDigest = "5f04c2a19e7d",
				presentationGeneration = 8L,
				destinationCommitIdentity = ReaderDestinationCommitIdentity("session-a", 41L),
				markers = listOf(first, second)
			)
		}
	}

	private fun marker(
		sourceOrdinal: Int,
		geometry: ReaderPageTurnCaptureGeometry,
		role: ReaderPageTurnPageRole,
		left: Double,
		top: Double
	) = ReaderWhispersyncCueMapMarkerReceipt(
		sourceOrdinal = sourceOrdinal,
		prepared = sourceOrdinal == 70,
		requested = false,
		audioActive = sourceOrdinal == 82,
		renderedHighlight = false,
		anchorReceipt = ReaderWhispersyncAnchorReceipt(
			foliateSessionId = "session-a",
			destinationCommitToken = "opaque-token",
			visualPageOrdinal = 9,
			spineIndex = 7,
			rasterGeneration = 12L,
			textureGeneration = 13L,
			presentationMutationGeneration = 14L,
			presentationSequence = 15L,
			anchorGeneration = sourceOrdinal.toLong() + 1L,
			boundarySequence = sourceOrdinal.toLong(),
			layoutGeneration = 17L,
			viewGeneration = 18L,
			commitSequence = 73L,
			committedSpineIndex = 7,
			committedChapterPageIndex = 9,
			committedChapterPageCount = 127,
			paginationFingerprint = "pagination-safe",
			layoutFingerprint = "layout-safe",
			readerSettingsRasterKey = "settings-safe",
			captureGeometry = geometry,
			pageLocalRects = listOf(
				ReaderWhispersyncPageLocalRect(role, left, top, width = 180.0, height = 24.0)
			)
		)
	)
}
