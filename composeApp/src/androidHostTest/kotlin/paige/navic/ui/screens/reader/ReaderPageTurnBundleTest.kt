package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderPageTurnBundleTest {
	@Test
	fun parsesSimplifiedLandscapeSpreadSlidePlan() {
		val plan = ReaderPageTurnTransitionPlan.parseOrThrow(
			encoded = """
				{
				  "kind": "landscape-spread-slide",
				  "logicalDirection": "next",
				  "sourcePageIndex": 16,
				  "targetPageIndex": 18,
				  "sourcePageSide": "left",
				  "targetPageSide": "left"
				}
			""".trimIndent(),
			token = "turn-16-18",
			generation = 7L
		)

		assertEquals(ReaderPageTurnTransitionKind.LandscapeSpreadSlide, plan.kind)
		assertEquals(ReaderPageTurnLogicalDirection.Next, plan.logicalDirection)
		assertEquals(18, plan.targetPageIndex)
		assertEquals(16, plan.sourcePageIndex)
		assertEquals(7L, plan.generation)
	}

	@Test
	fun parsesSimplifiedPortraitSlideWithoutCurlRoles() {
		val plan = ReaderPageTurnTransitionPlan.parse(
			encoded = """
				{
				  "kind": "portrait-slide",
				  "logicalDirection": "next",
				  "sourcePageIndex": 6,
				  "targetPageIndex": 7,
				  "sourcePageSide": "left",
				  "targetPageSide": "right"
				}
			""".trimIndent(),
			token = "portrait-6-7",
			generation = 8L
		)

		assertEquals(ReaderPageTurnTransitionKind.PortraitSlide, plan?.kind)
		assertEquals(6, plan?.sourcePageIndex)
		assertEquals(7, plan?.targetPageIndex)
	}

	@Test
	fun rejectsUnknownTransitionKind() {
		val plan = ReaderPageTurnTransitionPlan.parse(
			encoded = """{"kind":"curl","logicalDirection":"next","sourcePageIndex":6,"targetPageIndex":7,"sourcePageSide":"left","targetPageSide":"right"}""",
			token = "invalid",
			generation = 9L
		)

		assertNull(plan)
	}
}
