package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.reader.ReaderPageTurnPixelRect

class ReaderPageRasterHydrationTest {
	@Test
	fun validSpreadMetadataRestoresLeafGeometry() {
		val metadata = metadata(
			left = ReaderPageRasterRect(0, 0, 480, 700),
			gutter = ReaderPageRasterRect(480, 0, 520, 700),
			right = ReaderPageRasterRect(520, 0, 1_000, 700)
		)

		val geometry = readerPageRasterLeafGeometry(metadata, bitmapWidth = 1_000, bitmapHeight = 700)

		assertEquals(ReaderPageTurnPixelRect(0, 0, 480, 700), geometry?.leftLeafRect)
		assertEquals(ReaderPageTurnPixelRect(480, 0, 520, 700), geometry?.gutterRect)
		assertEquals(ReaderPageTurnPixelRect(520, 0, 1_000, 700), geometry?.rightLeafRect)
		assertNull(geometry?.fullLeafRect)
	}

	@Test
	fun validSinglePageMetadataRestoresFullLeaf() {
		val metadata = metadata(
			full = ReaderPageRasterRect(0, 0, 600, 900),
			surfaceWidth = 600,
			surfaceHeight = 900
		)

		val geometry = readerPageRasterLeafGeometry(metadata, bitmapWidth = 600, bitmapHeight = 900)

		assertEquals(ReaderPageTurnPixelRect(0, 0, 600, 900), geometry?.fullLeafRect)
		assertNull(geometry?.leftLeafRect)
		assertNull(geometry?.rightLeafRect)
	}

	@Test
	fun malformedOrMixedGeometryIsRejected() {
		assertNull(
			readerPageRasterLeafGeometry(
				metadata(full = ReaderPageRasterRect(0, 0, 1_001, 700)),
				bitmapWidth = 1_000,
				bitmapHeight = 700
			)
		)
		assertNull(
			readerPageRasterLeafGeometry(
				metadata(
					full = ReaderPageRasterRect(0, 0, 1_000, 700),
					left = ReaderPageRasterRect(0, 0, 480, 700),
					right = ReaderPageRasterRect(520, 0, 1_000, 700)
				),
				bitmapWidth = 1_000,
				bitmapHeight = 700
			)
		)
		assertNull(
			readerPageRasterLeafGeometry(
				metadata(left = ReaderPageRasterRect(0, 0, 480, 700)),
				bitmapWidth = 1_000,
				bitmapHeight = 700
			)
		)
	}

	private fun metadata(
		full: ReaderPageRasterRect? = null,
		left: ReaderPageRasterRect? = null,
		gutter: ReaderPageRasterRect? = null,
		right: ReaderPageRasterRect? = null,
		surfaceWidth: Int = 1_000,
		surfaceHeight: Int = 700
	) = ReaderPageRasterMetadata(
		surfaceLeft = 0,
		surfaceTop = 0,
		surfaceRight = surfaceWidth,
		surfaceBottom = surfaceHeight,
		fullLeafRect = full,
		leftLeafRect = left,
		gutterRect = gutter,
		rightLeafRect = right,
		reverseFaceColor = 0xffead9ae.toInt()
	)
}
