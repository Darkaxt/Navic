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
		assertContains(settingsCore, "rawMode === ReaderDragAnimationCanvas || rawMode === LegacyReaderDragAnimationCurl")
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

		assertContains(readerRoot, "dragAnimationMode == ReaderDragAnimationCanvas")
		assertFalse(readerRoot.contains("ReaderDragAnimationCurl"))
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
		assertContains(harness, "{ input: 'curl', expected: 'canvas' }")
	}
}
