package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReaderDragAnimationMigrationSourceTest {
	private fun readerAsset(name: String): String =
		File("src/androidMain/assets/reader/$name").readText()

	@Test
	fun javascriptNormalizesLegacyValuesWithoutPublishingThem() {
		val settingsCore = readerAsset("navic-reader-settings-core.js")

		assertContains(settingsCore, "ReaderDragAnimationNone = 'none'")
		assertContains(settingsCore, "ReaderDragAnimationCanvas = 'canvas'")
		assertContains(settingsCore, "LegacyReaderDragAnimationStandard = 'standard'")
		assertContains(settingsCore, "LegacyReaderDragAnimationCurl = 'curl'")
		assertContains(settingsCore, "if (rawMode === ReaderDragAnimationCanvas) return ReaderDragAnimationCanvas")
		assertContains(settingsCore, "if (rawMode === LegacyReaderDragAnimationCurl) return ReaderDragAnimationNone")
		assertContains(settingsCore, "return ReaderDragAnimationNone")
	}

	@Test
	fun noneModeCannotEnterTheLegacyLiveScrollPreview() {
		val pageTurns = readerAsset("navic-reader-page-turns.js")

		assertContains(pageTurns, "this.readerDragAnimationModeValue === ReaderDragAnimationNone")
		assertContains(pageTurns, "this.removePageDragPreviewLayer()")
		assertContains(pageTurns, "return")
		assertFalse(
			pageTurns.contains("this.readerDragAnimationModeValue !== 'curl'"),
			"The removed public curl value must not remain the switch controlling live Foliate scrolling."
		)
	}

	@Test
	fun nativeCanvasIsEnabledOnlyByTheCanvasMode() {
		val readerRoot = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt").readText()

		assertContains(
			readerRoot,
			"normalizedReaderDragAnimationMode(controllerState.chrome.settings.dragAnimationMode) =="
		)
		assertContains(readerRoot, "ReaderDragAnimationCanvas")
		assertFalse(readerRoot.contains("ReaderDragAnimationCurl"))
	}

	@Test
	fun removingLegacyCanvasRequiresTheOpenGlReplacement() {
		val legacyCanvas = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageTurnSlideView.android.kt"
		)
		val openGlRenderer = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlRenderer.android.kt"
		)
		val openGlView = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPageCurlGlView.android.kt"
		)

		if (!legacyCanvas.isFile) {
			check(openGlRenderer.isFile) {
				"The persisted canvas mode must target ReaderPageCurlGlRenderer after Canvas removal."
			}
			check(openGlView.isFile) {
				"The animated mode requires the persistent ReaderPageCurlGlView after Canvas removal."
			}
		}
	}

	@Test
	fun defaultReaderHarnessRunsTheNewModeMigrationContract() {
		val harness = sequenceOf(
			File("tools/reader-harness/src/run-reader-harness.mjs"),
			File("../tools/reader-harness/src/run-reader-harness.mjs")
		).first { candidate -> candidate.isFile }.readText()
		val stabilization = harness
			.substringAfter("if (mode === 'phase1-stabilization') {")
			.substringBefore("\nif (mode === 'trace-smoke') {")

		assertContains(stabilization, "drag-animation-mode-migration")
		assertFalse(
			stabilization.contains("epub-native-drag-standard-no-curl"),
			"The default harness must not execute the retired DOM standard/curl preview probe."
		)
		assertContains(harness, "if (mode === 'drag-animation-mode-migration') {")
		assertContains(harness, "{ input: 'standard', expected: 'none' }")
		assertContains(harness, "{ input: 'curl', expected: 'none' }")
	}
}
