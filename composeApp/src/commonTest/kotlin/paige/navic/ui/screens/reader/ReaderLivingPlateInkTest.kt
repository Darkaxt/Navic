package paige.navic.ui.screens.reader

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderLivingPlateInkTest {
	private val w = 120
	private val h = 180

	@Test
	fun inkStartsInTheCentreAndReachesTheCornersLast() {
		val field = livingPlateInkField(w, h, LivingPlateInkTuning())
		assertTrue(field.all { it in 0f..1f })
		val centre = field[(h / 2) * w + w / 2]
		val corner = field[(h / 10) * w + w / 10]
		assertTrue(centre < 0.2f, "centre colours early, was $centre")
		assertTrue(corner > 0.8f, "corners colour late, was $corner")
	}

	@Test
	fun visibleColouredShareFollowsTheTimelineToTheEnd() {
		val field = livingPlateInkField(w, h, LivingPlateInkTuning())
		for (progress in listOf(0.25f, 0.5f, 0.75f, 0.95f)) {
			var visible = 0
			var coloured = 0
			for (y in 0 until h) for (x in 0 until w) {
				val vx = (x + 0.5f) / w
				val vy = (y + 0.5f) / h
				if (vx <= 0.07f || vx >= 0.93f || vy <= 0.07f || vy >= 0.93f) continue
				visible++
				if (field[y * w + x] <= progress) coloured++
			}
			val share = coloured.toFloat() / visible
			assertTrue(abs(share - progress) < 0.03f, "at $progress the visible share was $share")
		}
	}

	@Test
	fun paceCurvesStartAndEndAtTheTimelineEnds() {
		for (pace in 0..3) {
			assertEquals(0f, livingPlateInkPace(0f, pace))
			assertEquals(1f, livingPlateInkPace(1f, pace))
		}
		assertTrue(livingPlateInkPace(0.5f, 2) > 0.7f)
		assertEquals(0.5f, livingPlateInkPace(0.5f, 0))
	}

	@Test
	fun theSameTuningDrawsTheSameInk() {
		val tuning = LivingPlateInkTuning(seed = 11)
		assertTrue(livingPlateInkField(40, 60, tuning).contentEquals(livingPlateInkField(40, 60, tuning)))
	}
}
