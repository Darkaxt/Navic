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
	fun curlRecordingsAreKeyedByLogicalPage() {
		val record = controller.substringAfter("private fun recordLivingPlatesForTurn() {").substringBefore("\n\t}\n")
		assertContains(record, "stills[readerPlayLikeCurlLogicalPageId(pages.profile, ordinal)] = it")
		assertContains(record, "surfaceView.putPagePlates(stills)")
		assertContains(controller, "readerPlayLikeCurlLogicalPageId(profile, ordinal),")
	}

	private fun readerScreenFile(name: String): File =
		listOf(
			File("src/androidMain/kotlin/paige/navic/ui/screens/reader/$name"),
			File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/$name")
		).firstOrNull { it.isFile } ?: error("Could not locate $name")
}
