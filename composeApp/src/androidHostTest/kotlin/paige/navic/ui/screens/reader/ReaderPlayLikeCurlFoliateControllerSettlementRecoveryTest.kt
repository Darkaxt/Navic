package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPlayLikeCurlFoliateControllerSettlementRecoveryTest {
	@Test
	fun rejectedTerminalInvalidatesPromotedRendererBeforeRepreparingSource() {
		val events = mutableListOf<String>()
		var currentOrdinal = 9

		recoverRejectedReaderSettlement(
			sourceOrdinal = 8,
			promotedGeneration = 42L,
			rendererEnabled = true,
			restoreSourceOrdinal = { ordinal ->
				currentOrdinal = ordinal
				events += "restore:$ordinal"
			},
			invalidateRenderer = { reason -> events += "invalidate:$reason" },
			requestPrewarm = { events += "prewarm" }
		)

		assertEquals(8, currentOrdinal)
		assertEquals(
			listOf(
				"restore:8",
				"invalidate:settlement-terminal-rejected:42",
				"prewarm"
			),
			events
		)
	}

	@Test
	fun rejectedTerminalDoesNotReprepareDisabledRenderer() {
		val events = mutableListOf<String>()

		recoverRejectedReaderSettlement(
			sourceOrdinal = 8,
			promotedGeneration = 42L,
			rendererEnabled = false,
			restoreSourceOrdinal = { ordinal -> events += "restore:$ordinal" },
			invalidateRenderer = { reason -> events += "invalidate:$reason" },
			requestPrewarm = { events += "prewarm" }
		)

		assertEquals(
			listOf(
				"restore:8",
				"invalidate:settlement-terminal-rejected:42"
			),
			events
		)
	}
}
