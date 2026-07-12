package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderPageTurnBundleTest {
	@Test
	fun parsesEveryExplicitLandscapeBitmapRole() {
		val plan = ReaderPageTurnTransitionPlan.parseOrThrow(
			encoded = """
				{
				  "kind": "landscape-leaf",
				  "logicalDirection": "next",
				  "sourcePageIndex": 16,
				  "turningFrontPageIndex": 17,
				  "turningReversePageIndex": 18,
				  "underneathPageIndex": 19,
				  "targetPageIndex": 18,
				  "sourcePageSide": "right",
				  "targetPageSide": "left",
				  "turningFrontPageSide": "right",
				  "turningReversePageSide": "left",
				  "underneathPageSide": "right"
				}
			""".trimIndent(),
			token = "turn-16-18",
			generation = 7L
		)

		assertEquals(ReaderPageTurnTransitionKind.LandscapeLeaf, plan.kind)
		assertEquals(ReaderPageTurnLogicalDirection.Next, plan.logicalDirection)
		assertEquals(ReaderPageTurnPhysicalSide.Right, plan.turningFrontPageSide)
		assertEquals(ReaderPageTurnPhysicalSide.Left, plan.turningReversePageSide)
		assertEquals(ReaderPageTurnPhysicalSide.Right, plan.underneathPageSide)
		assertEquals(18, plan.targetPageIndex)
		assertEquals(7L, plan.generation)
	}

	@Test
	fun rejectsPlanBeforeAllocationWhenARequiredRoleIsMissing() {
		val plan = ReaderPageTurnTransitionPlan.parse(
			encoded = """
				{
				  "kind": "landscape-leaf",
				  "logicalDirection": "next",
				  "sourcePageIndex": 16,
				  "turningFrontPageIndex": 17,
				  "turningReversePageIndex": 18,
				  "underneathPageIndex": 19,
				  "targetPageIndex": 18,
				  "sourcePageSide": "right",
				  "targetPageSide": "left",
				  "turningFrontPageSide": "right",
				  "underneathPageSide": "right"
				}
			""".trimIndent(),
			token = "invalid",
			generation = 8L
		)

		assertNull(plan)
	}
}
