package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPixelRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderPlayLikeCurlFoliateRasterCopyTest {
	@Test
	fun exactLeafCopyFlattensTransparencyAndOutlivesItsSnapshot() {
		val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply {
			eraseColor(Color.TRANSPARENT)
			setPixel(1, 0, Color.RED)
			setPixel(0, 1, Color.argb(128, 255, 0, 0))
			setHasAlpha(true)
		}
		val snapshot = ReaderPageSlideSnapshot(
			key = ReaderPageSlideSnapshotKey(
				visualPageIndex = 2,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				bitmapQuality = ReaderPageBitmapQuality.Balanced,
				bitmapWidth = 4,
				bitmapHeight = 2,
				surfaceWidth = 4,
				surfaceHeight = 2
			),
			bitmap = source,
			surfaceRectInWindow = Rect(0, 0, 4, 2),
			leafGeometry = ReaderPageTurnLeafGeometry(
				fullLeafRect = null,
				leftLeafRect = ReaderPageTurnPixelRect(0, 0, 2, 2),
				gutterRect = ReaderPageTurnPixelRect(2, 0, 2, 2),
				rightLeafRect = ReaderPageTurnPixelRect(2, 0, 4, 2)
			),
			reverseFaceColor = Color.WHITE
		)
		snapshot.retain()

		val copied = checkNotNull(
			readerPlayLikeCurlCopyRetainedFoliateLeaf(
				snapshot,
				ReaderPlayLikeCurlFoliateLeaf.Left
			)
		)
		snapshot.releaseCacheOwnership()

		assertTrue(source.isRecycled)
		assertFalse(copied.bitmap.isRecycled)
		assertEquals(2, copied.bitmap.width)
		assertEquals(2, copied.bitmap.height)
		assertEquals(Bitmap.Config.ARGB_8888, copied.bitmap.config)
		assertFalse(copied.bitmap.hasAlpha())
		assertEquals(Color.WHITE, copied.bitmap.getPixel(0, 0))
		assertEquals(Color.RED, copied.bitmap.getPixel(1, 0))
		assertEquals(Color.rgb(255, 127, 127), copied.bitmap.getPixel(0, 1))
		copied.bitmap.recycle()
	}

	@Test
	fun partialOpaqueCropOutlivesItsSnapshotWithExactPixels() {
		val mutable = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply {
			setPixel(0, 0, Color.RED)
			setPixel(1, 0, Color.GREEN)
			setPixel(2, 0, Color.BLUE)
			setPixel(3, 0, Color.WHITE)
			setPixel(0, 1, Color.YELLOW)
			setPixel(1, 1, Color.MAGENTA)
			setPixel(2, 1, Color.CYAN)
			setPixel(3, 1, Color.BLACK)
			setHasAlpha(false)
		}
		val source = checkNotNull(mutable.copy(Bitmap.Config.ARGB_8888, false))
		mutable.recycle()
		assertFalse(source.isMutable)
		assertFalse(source.hasAlpha())
		val snapshot = ReaderPageSlideSnapshot(
			key = ReaderPageSlideSnapshotKey(
				visualPageIndex = 2,
				kind = ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
				bitmapQuality = ReaderPageBitmapQuality.Balanced,
				bitmapWidth = 4,
				bitmapHeight = 2,
				surfaceWidth = 4,
				surfaceHeight = 2
			),
			bitmap = source,
			surfaceRectInWindow = Rect(0, 0, 4, 2),
			leafGeometry = ReaderPageTurnLeafGeometry(
				fullLeafRect = null,
				leftLeafRect = ReaderPageTurnPixelRect(0, 0, 2, 2),
				gutterRect = ReaderPageTurnPixelRect(2, 0, 2, 2),
				rightLeafRect = ReaderPageTurnPixelRect(2, 0, 4, 2)
			),
			reverseFaceColor = Color.WHITE
		)
		snapshot.retain()

		val copied = checkNotNull(
			readerPlayLikeCurlCopyRetainedFoliateLeaf(
				snapshot,
				ReaderPlayLikeCurlFoliateLeaf.Left
			)
		)
		snapshot.releaseCacheOwnership()

		assertTrue(source.isRecycled)
		assertFalse(copied.bitmap.isRecycled)
		assertFalse(copied.bitmap.hasAlpha())
		assertEquals(2, copied.bitmap.width)
		assertEquals(2, copied.bitmap.height)
		assertEquals(Color.RED, copied.bitmap.getPixel(0, 0))
		assertEquals(Color.GREEN, copied.bitmap.getPixel(1, 0))
		assertEquals(Color.YELLOW, copied.bitmap.getPixel(0, 1))
		assertEquals(Color.MAGENTA, copied.bitmap.getPixel(1, 1))
		copied.bitmap.recycle()
	}

	@Test
	fun opaqueRasterEncodingRoundTripPreservesOpacityMetadata() {
		val source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
			eraseColor(Color.WHITE)
			setHasAlpha(false)
			setPremultiplied(true)
		}
		val file = File.createTempFile("reader-raster-", ".png")
		var decoded: Bitmap? = null
		try {
			assertTrue(ReaderAndroidPageRasterCodec.encode(source, file))
			decoded = checkNotNull(ReaderAndroidPageRasterCodec.decode(file))
			assertFalse(decoded.hasAlpha())
			assertEquals(Color.WHITE, decoded.getPixel(0, 0))
		} finally {
			source.recycle()
			decoded?.recycle()
			file.delete()
		}
	}

	@Test
	fun fullOpaqueCopyRemainsIndependentWhenTheCropWouldReturnItsSource() {
		val mutable = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
			eraseColor(Color.GREEN)
			setHasAlpha(false)
		}
		val source = checkNotNull(mutable.copy(Bitmap.Config.ARGB_8888, false))
		mutable.recycle()
		assertFalse(source.isMutable)
		val snapshot = ReaderPageSlideSnapshot(
			key = ReaderPageSlideSnapshotKey(
				visualPageIndex = 1,
				kind = ReaderPageTurnTransitionKind.PortraitSlide,
				bitmapQuality = ReaderPageBitmapQuality.Balanced,
				bitmapWidth = 2,
				bitmapHeight = 2,
				surfaceWidth = 2,
				surfaceHeight = 2
			),
			bitmap = source,
			surfaceRectInWindow = Rect(0, 0, 2, 2),
			leafGeometry = ReaderPageTurnLeafGeometry(
				fullLeafRect = ReaderPageTurnPixelRect(0, 0, 2, 2),
				leftLeafRect = null,
				gutterRect = null,
				rightLeafRect = null
			),
			reverseFaceColor = Color.WHITE
		)
		snapshot.retain()

		val copied = checkNotNull(
			readerPlayLikeCurlCopyRetainedFoliateLeaf(
				snapshot,
				ReaderPlayLikeCurlFoliateLeaf.Full
			)
		)
		snapshot.releaseCacheOwnership()

		assertTrue(source.isRecycled)
		assertFalse(copied.bitmap.isRecycled)
		assertEquals(Bitmap.Config.ARGB_8888, copied.bitmap.config)
		assertFalse(copied.bitmap.hasAlpha())
		assertEquals(Color.GREEN, copied.bitmap.getPixel(0, 0))
		copied.bitmap.recycle()
	}
}
