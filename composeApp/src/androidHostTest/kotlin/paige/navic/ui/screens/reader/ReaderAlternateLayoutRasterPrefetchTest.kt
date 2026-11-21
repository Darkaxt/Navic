package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole

/** #57 "Both layouts": the other orientation is prefetched to disk without touching the live deck. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderAlternateLayoutRasterPrefetchTest {
	private val spread = ReaderAlternateLayoutGeometry(
		physicalWidth = 1280,
		physicalHeight = 800,
		captureGeometry = ReaderPageTurnCaptureGeometry(
			viewportWidth = 640.0,
			viewportHeight = 400.0,
			mode = ReaderPageTurnLayoutMode.Spread,
			pages = listOf(
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Left, 0.0, 0.0, 320.0, 400.0),
				ReaderPageTurnPageRect(ReaderPageTurnPageRole.Right, 320.0, 0.0, 320.0, 400.0)
			),
			reverseFaceColorArgb = 0xFF102030
		)
	)
	private val fullFrame = ReaderPassiveRasterGeometry(1280, 800, 0, 0, 1280, 800)

	@Test
	fun alternateRasterCarriesTheOtherLayoutsSurfaceAndLeaves() {
		val (bitmap, metadata) = assertNotNull(
			readerAlternateLayoutRaster(
				Bitmap.createBitmap(1280, 800, Bitmap.Config.ARGB_8888),
				fullFrame,
				spread,
				ReaderPageBitmapQuality.Balanced
			)
		)
		assertEquals(640 to 400, bitmap.width to bitmap.height)
		assertEquals(listOf(0, 0, 640, 400), with(metadata) { listOf(surfaceLeft, surfaceTop, surfaceRight, surfaceBottom) })
		assertNotNull(metadata.leftLeafRect)
		assertNotNull(metadata.rightLeafRect)
		assertNull(metadata.fullLeafRect)
		assertEquals(0xFF102030.toInt(), metadata.reverseFaceColor)
		assertEquals(ReaderPageTurnTransitionKind.LandscapeSpreadSlide, spread.kind)
	}

	@Test
	fun aMismatchedCaptureIsRejectedAndReleased() {
		val wrongSize = Bitmap.createBitmap(800, 1280, Bitmap.Config.ARGB_8888)
		assertNull(readerAlternateLayoutRaster(wrongSize, fullFrame, spread, ReaderPageBitmapQuality.Balanced))
		assertTrue(wrongSize.isRecycled)
	}

	@Test
	fun theAlternatePlanIsAllPassiveAndKeepsOnlyTheChapterWorkingSet() {
		val encoded = """{"context":{"centerPageIndex":120,"pageCount":141,"layoutMode":"single"},
			"targets":[
			{"pageIndex":120,"priority":"current","authority":"OffscreenPassive"},
			{"pageIndex":121,"priority":"next-transition","authority":"OffscreenPassive"},
			{"pageIndex":101,"priority":"current-chapter","authority":"OffscreenPassive"},
			{"pageIndex":99,"priority":"previous-chapter","authority":"OffscreenPassive"},
			{"pageIndex":141,"priority":"next-chapter","authority":"OffscreenPassive"}]}"""
		assertNull(readerPageRasterPreparationPlan(encoded), "the live parser still demands a current-live centre")
		val plan = assertNotNull(readerPageRasterPreparationPlan(encoded, requireCurrentLive = false))
		assertEquals(listOf(120, 121, 101), readerAlternateLayoutTargetOrdinals(plan))
		val withLive = encoded.replaceFirst("\"authority\":\"OffscreenPassive\"", "\"authority\":\"CurrentLive\"")
		assertNull(readerPageRasterPreparationPlan(withLive, requireCurrentLive = false))
	}

	@Test
	fun persistingAnAlternateRasterNeverTouchesTheLiveDeck() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt"
		).readText()
		val persist = source
			.substringAfter("fun persistAlternateLayoutRaster(")
			.substringBefore("fun admitPassiveRasterCapture(")
		for (forbidden in listOf("activateProfile", "putSnapshot", "protectEncodedWindow", "physicalLayoutAuthority")) {
			assertFalse(forbidden in persist, forbidden)
		}
		assertContains(persist, "readerAdmitPassiveRaster(")
		assertContains(persist, "store.writePublication(")
		assertContains(persist, "recordPersistentRasterAlias(webView, realizedKey, issuedDigest)")
	}

	@Test
	fun theAlternateManifestPortAsksForTheOtherOrientation() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderAlternateLayoutRasterPrefetch.android.kt"
		).readText()
		assertContains(source, "bridgeMethod = \"pageTurnAlternatePassiveRasterManifestInputs\"")
		assertContains(source, "measuredSize = { layout.physicalWidth to layout.physicalHeight }")
		assertContains(source, "pageTurnAlternateLayoutPlan?.()")
		val bridge = File("src/androidMain/assets/reader/navic-reader.js").readText()
		assertContains(bridge, "pageTurnAlternatePassiveRasterManifestInputs")
		assertContains(bridge, "pageTurnAlternateLayoutPlan")
	}
}
