package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPageRasterDurabilityGateTest {
	@Test
	fun manifestFailureCannotCompleteBatchAndRetrySkipsDurablePages() {
		val gate = ReaderPageRasterDurabilityGate(setOf(4, 5))

		assertEquals(
			ReaderPageRasterDurabilityDecision.Continue(
				completed = 1,
				required = 2
			),
			gate.record(pageIndex = 4, persisted = true)
		)
		assertEquals(
			ReaderPageRasterDurabilityDecision.Failed(pageIndex = 5),
			gate.record(pageIndex = 5, persisted = false)
		)
		assertEquals(setOf(5), gate.retryPageIndices())
		assertEquals(
			ReaderPageRasterDurabilityDecision.Ready,
			gate.record(pageIndex = 5, persisted = true)
		)
	}
}
