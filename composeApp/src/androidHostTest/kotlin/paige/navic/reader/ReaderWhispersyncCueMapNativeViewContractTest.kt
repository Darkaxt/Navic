package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertTrue

class ReaderWhispersyncCueMapNativeViewContractTest {
	@Test
	fun holdProgressArcHasAContrastingTrackOnLightReaderPages() {
		val source = readerAndroidFile("ReaderWhispersyncCueMapNativeView.android.kt").readText()
		val holdBlock = source
			.substringAfter("if (tracker.sourceOrdinal == anchor.sourceOrdinal) {")
			.substringBefore("\n\t\t}")

		val trackColorIndex = holdBlock.indexOf("strokePaint.color = HoldProgressTrack")
		val trackCircleIndex = holdBlock.indexOf(
			"canvas.drawCircle(centerX, centerY, holdRingRadius, strokePaint)"
		)
		val progressColorIndex = holdBlock.indexOf("strokePaint.color = Color.WHITE")
		val progressArcIndex = holdBlock.indexOf("canvas.drawArc(")

		assertTrue(trackColorIndex >= 0)
		assertTrue(trackCircleIndex > trackColorIndex)
		assertTrue(progressColorIndex > trackCircleIndex)
		assertTrue(progressArcIndex > progressColorIndex)
		assertTrue(source.contains("val HoldProgressTrack = Color.argb("))
	}
}
