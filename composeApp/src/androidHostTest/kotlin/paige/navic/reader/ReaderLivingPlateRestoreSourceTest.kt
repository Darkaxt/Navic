package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

// Living plates (Darkaxt/Navic#77): leaving a page halts its plate; coming back continues it, never grey.
class ReaderLivingPlateRestoreSourceTest {
	private val overlay = readerScreenFile("ReaderLivingPlateOverlay.android.kt").readText()
	private val host = readerNativeFrameHostFile().readText()
	private val controller = readerScreenFile("ReaderPlayLikeCurlFoliateController.android.kt").readText()

	@Test
	fun clearingRemembersEachPlateAndShowingContinuesIt() {
		val clear = overlay.substringAfter("fun clear() {").substringBefore("\n\t}\n")
		assertContains(clear, "plate.memory()?.let { memories.put(memoryKey(plate.target), it)?.frame?.recycle() }")
		val show = overlay.substringAfter("fun show(targets: List<ReaderLivingPlateTarget>) {").substringBefore("\n\t}\n")
		assertContains(show, "memories.remove(memoryKey(target))")
		assertContains(show, "LivingPlateView(context, target, tuning, memory)")
		// The colouring continues where it stood; the loop seeks back to its frame.
		assertContains(overlay, "if (fromMillis > 0L) currentPlayTime = fromMillis.coerceAtMost(millis)")
		assertContains(overlay, "seekTo(resumeAt.toLong(), MediaPlayer.SEEK_CLOSEST)")
		assertContains(overlay, "restoredLoop -> LivingPlateRestoreFadeMillis")
		assertFalse(overlay.contains("Every arrival colours"))
	}

	@Test
	fun turningBackPutsRememberedPlatesUpUnderTheCurl() {
		val location = host.substringAfter("if (pageTurnVisualPageIndex != normalized || sessionChanged) {")
			.substringBefore("\n\t\t}\n")
		assertContains(location, "scheduleLivingPlateProbe()")
		assertContains(location, "showRememberedLivingPlates(normalized)")
		// Turning plates off forgets every remembered state; a layout change only the page positions.
		val forget = host.substringAfter("private fun forgetLivingPlates() {").substringBefore("\n\t}\n")
		assertContains(forget, "livingPlateOverlay.forget()")
		val epoch = host.substringAfter("private fun onRasterProfileEpochChanged(epoch: Long?) {")
			.substringBefore("rasterProfileEpoch = epoch")
		assertContains(epoch, "playLikeCurlController.forgetLivingPlateRecordings()")
	}

	@Test
	fun rememberedPlatesStandAboveTheRasterShieldAndBelowTheCurl() {
		val layers = host.substringAfter("descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS")
			.substringBefore("fun hasValidatedRasterPresentation()")
		val shield = layers.indexOf("playLikeCurlController.inlineRasterShieldView,")
		val plates = layers.indexOf("livingPlateOverlay,")
		val curl = layers.indexOf("playLikeCurlController.surfaceView,")
		kotlin.test.assertTrue(shield in 0 until plates && plates < curl, "shield=$shield plates=$plates curl=$curl")
	}

	@Test
	fun curlRecordingsAreKeyedByLogicalPage() {
		val record = controller.substringAfter("private fun recordLivingPlatesForTurn() {").substringBefore("\n\t}\n")
		assertContains(record, "stills[readerPlayLikeCurlLogicalPageId(pages.profile, ordinal)] = it")
		assertContains(record, "surfaceView.putPagePlates(stills)")
		assertContains(controller, "readerPlayLikeCurlLogicalPageId(profile, ordinal),")
	}

	@Test
	fun livePageCheckMasksLivingPlates() {
		// A returning page's curl recording is coloured where its raster draws the plate grey.
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		assertContains(runtime, "...readerLivingPlateCaptureMaskRects(),")
		assertContains(runtime, "JSON.parse(window.NavicReaderBridge.livingPlateRects())")
	}

	@Test
	fun plateFadesOnlyOnceTheLoopFrameIsOnTheTexture() {
		// The fade must not run ahead of the video: a resumed loop's seeked frame lands after start().
		val start = overlay.substringAfter("private fun startVideoIfReady() {").substringBefore("\n\t}\n")
		assertContains(start, "awaitingLoopFrame = !loopFrameShown")
		assertFalse(start.contains("crossfadeIfReady()"))
		val updated = overlay.substringAfter("override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {")
			.substringBefore("\n\t}\n")
		assertContains(updated, "loopFrameShown = true")
		assertContains(updated, "crossfadeIfReady()")
		assertContains(overlay, "if (released || !loopFrameShown || !videoPrepared || !revealDone) return")
		assertFalse(overlay.contains("MEDIA_INFO_VIDEO_RENDERING_START"))
	}

	private fun readerScreenFile(name: String): File =
		listOf(
			File("src/androidMain/kotlin/paige/navic/ui/screens/reader/$name"),
			File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/$name")
		).firstOrNull { it.isFile } ?: error("Could not locate $name")
}
