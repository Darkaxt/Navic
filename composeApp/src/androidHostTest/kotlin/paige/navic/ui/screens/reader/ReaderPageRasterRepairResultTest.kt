package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPageRasterRepairResultTest {
	@Test
	fun repairedResultCarriesAnImmutableDurableWindow() {
		val mutableWindow = linkedSetOf(3, 4, 5, 6, 7)

		val result = readerPageRasterRepairedResult(
			repairedPageIndices = mutableWindow,
			centerOrdinal = 5,
			rasterEpoch = 9L
		)
		mutableWindow.clear()

		assertEquals(
			ReaderPageRasterRepairResult.Repaired(
				repairedPageIndices = setOf(3, 4, 5, 6, 7),
				centerOrdinal = 5,
				rasterEpoch = 9L
			),
			result
		)
	}

	@Test
	fun deferredRepairRetainsItsTypedRetryReason() {
		ReaderPageRasterDeferralReason.entries.forEach { reason ->
			assertEquals(
				ReaderPageRasterRepairResult.Deferred(reason),
				ReaderPageRasterRepairResult.Deferred(reason)
			)
		}
	}
}
