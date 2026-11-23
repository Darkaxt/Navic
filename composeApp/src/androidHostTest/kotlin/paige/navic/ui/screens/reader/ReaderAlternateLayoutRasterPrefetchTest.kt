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
import paige.navic.reader.ReaderBookPreparationPhase
import paige.navic.reader.ReaderBookPreparationProgress
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLayoutMode
import paige.navic.reader.ReaderPageTurnPageRect
import paige.navic.reader.ReaderPageTurnPageRole
import paige.navic.reader.ReaderPreparationOrientation

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

	/** #70: "Prepare pages" prepares every page of the other orientation, not the chapter set. */
	@Test
	fun theBookPassTargetsEveryAlternatePageAndCountsWholeChapters() {
		val encoded = """{"context":{"centerPageIndex":120,"pageCount":141,"layoutMode":"single",
			"chapterRanges":[[0,100],[100,41]]},
			"targets":[
			{"pageIndex":120,"priority":"current","authority":"OffscreenPassive"},
			{"pageIndex":101,"priority":"current-chapter","authority":"OffscreenPassive"}]}"""
		val plan = assertNotNull(readerPageRasterPreparationPlan(encoded, requireCurrentLive = false))
		assertEquals((0 until 141).toList(), readerAlternateLayoutBookOrdinals(plan))
		assertEquals(listOf(120, 101), readerAlternateLayoutTargetOrdinals(plan), "the idle prefetch keeps its working set")

		val portrait = ReaderPreparationOrientation.Portrait
		assertEquals(
			ReaderBookPreparationProgress(ReaderBookPreparationPhase.Preparing, 99, 141, 0, 2, portrait),
			readerAlternateLayoutBookProgress(plan, 99, ReaderBookPreparationPhase.Preparing, portrait)
		)
		assertEquals(
			ReaderBookPreparationProgress(ReaderBookPreparationPhase.Preparing, 100, 141, 1, 2, portrait),
			readerAlternateLayoutBookProgress(plan, 100, ReaderBookPreparationPhase.Preparing, portrait)
		)
		assertEquals(
			ReaderBookPreparationProgress(ReaderBookPreparationPhase.Complete, 141, 141, 2, 2, portrait),
			readerAlternateLayoutBookProgress(plan, 141, ReaderBookPreparationPhase.Complete, portrait)
		)
	}

	@Test
	fun theAlternateProfileStatusIsReadFromTheBridge() {
		assertEquals(
			ReaderAlternateProfileStatus("building", ""),
			readerAlternateProfileStatus("\"{\\\"state\\\":\\\"building\\\",\\\"reason\\\":\\\"\\\"}\"")
		)
		assertEquals(
			ReaderAlternateProfileStatus("failed", "superseded"),
			readerAlternateProfileStatus("""{"state":"failed","reason":"superseded"}""")
		)
		assertNull(readerAlternateProfileStatus("null"))
		assertNull(readerAlternateProfileStatus("\"null\""))
	}

	/**
	 * #70: the whole-book pass builds the other profile in place, prefetches all its pages through
	 * the alternate manifest port and records that orientation with the alternate pagination.
	 */
	@Test
	fun theBookPassRecordsTheOtherOrientationWithItsOwnPagination() {
		val source = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderAlternateLayoutRasterPrefetch.android.kt"
		).readText()
		val book = source.substringAfter("fun prepareBook(").substringBefore("fun onCurrentLayoutSettled()")
		assertContains(book, "ensureAlternatePaginationProfile?.()")
		assertContains(book, "alternatePaginationProfileStatus?.()")
		assertContains(book, "readerAlternateLayoutBookOrdinals(plan)")
		assertFalse("readerAlternateLayoutTargetOrdinals" in book)
		assertContains(book, "bundleSource.markAlternateBookPrepared(target.orientation, descriptor)")
		assertTrue(
			book.indexOf("bundleSource.markAlternateBookPrepared(") <
				book.indexOf("ReaderBookPreparationPhase.Complete"),
			"Complete is reported only once the orientation is recorded"
		)
		val bundle = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnBundleSource.android.kt"
		).readText()
			.substringAfter("fun markAlternateBookPrepared(")
			.substringBefore("fun retireRasterChaptersBehindCurrent(")
		assertContains(bundle, "val key = descriptor.key(bitmapQuality)")
		assertContains(bundle, "scheduler.markBookPrepared(key.publicationHash, orientation.persistedValue, key.paginationHash)")

		val host = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		assertContains(host, "if (!alternateLayoutPreparation.bookPreparationActive) current(progress)")
		assertContains(host, "alternateLayoutPreparation.prepareBook(requested)")
		assertContains(host, "if (!alternateLayoutPreparation.retryBook()) pageRasterPreparationController.retryBookPreparation()")
		val bridge = File("src/androidMain/assets/reader/navic-reader.js").readText()
		assertContains(bridge, "ensureAlternatePaginationProfile: () => {")
		assertContains(bridge, "alternatePaginationProfileStatus: () => runtime.alternatePaginationProfileStatus()")
	}

	/** #70: no rotation request or "turn the device" prompt is left in the preparation path. */
	@Test
	fun preparingTheOtherOrientationNeverRotatesTheReader() {
		val screen = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt").readText()
		assertContains(screen, "ReaderOrientationEffect(orientation = settings.orientation)")
		assertFalse("preparationOrientationOverride" in screen)
		assertContains(screen, "bookPreparationAlternateAvailable = settings.fullscreen != false")
		val strings = File("src/commonMain/composeResources/values/strings.xml").readText()
		assertFalse("info_reader_book_preparation_turn_" in strings)
		val root = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt").readText()
		val effect = root.substringAfter("LaunchedEffect(bookPreparationActive, bookPreparationProgress.phase")
			.substringBefore("val bookPreparationAwaiting")
		assertTrue(
			effect.indexOf("onBookPrepared(bookPreparationProgress.orientation)") < effect.indexOf("delay(800)"),
			"each orientation is recorded before the other orientation's progress replaces it"
		)
		val app = File("src/commonMain/kotlin/paige/navic/App.kt").readText()
		assertContains(app, "start = if (readerOnTop) 0.dp else contentPadding")
		assertContains(app, "end = if (readerOnTop) 0.dp else contentPadding")
	}

	/** #70: Done is offered only when the other orientation cannot be, or was not, prepared. */
	@Test
	fun doneIsOfferedOnlyWhenTheOtherOrientationIsUnavailableOrIncomplete() {
		val landscape = ReaderPreparationOrientation.Landscape
		val portrait = ReaderPreparationOrientation.Portrait
		fun progress(phase: ReaderBookPreparationPhase, orientation: ReaderPreparationOrientation) =
			ReaderBookPreparationProgress(phase = phase, orientation = orientation)
		val landscapeDone = progress(ReaderBookPreparationPhase.Complete, landscape)
		assertNull(readerBookPreparationAwaiting(null, null, landscapeDone), "nothing missing")
		assertEquals(portrait, readerBookPreparationAwaiting(portrait, null, landscapeDone), "unavailable in place")
		assertNull(readerBookPreparationAwaiting(portrait, portrait, landscapeDone), "about to start")
		assertNull(
			readerBookPreparationAwaiting(portrait, portrait, progress(ReaderBookPreparationPhase.Preparing, portrait))
		)
		assertEquals(
			portrait,
			readerBookPreparationAwaiting(portrait, portrait, progress(ReaderBookPreparationPhase.Incomplete, portrait))
		)
	}
}
