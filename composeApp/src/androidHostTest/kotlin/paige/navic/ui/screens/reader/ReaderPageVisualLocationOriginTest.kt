package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.reader.ReaderPageTurnSettlementAck

class ReaderPageVisualLocationOriginTest {
	@Test
	fun onlyCurrentExactPageTurnCarriesItsAcknowledgementIntoBindingReport() {
		val acknowledgement = ReaderPageTurnSettlementAck(
			token = "page-turn-1",
			pageIndex = 4,
			foliateSessionId = "session-a",
			rasterGeneration = 5L,
			textureGeneration = 6L
		)

		assertEquals(
			acknowledgement,
			readerPageVisualLocationRelocationAcknowledgement(
				ReaderPageVisualLocationOrigin.ExactPageTurn,
				acknowledgement
			)
		)
		listOf(
			ReaderPageVisualLocationOrigin.External,
			ReaderPageVisualLocationOrigin.StaleAcknowledgement,
			ReaderPageVisualLocationOrigin.PendingExactPageTurn
		).forEach { origin ->
			assertNull(
				readerPageVisualLocationRelocationAcknowledgement(
					origin,
					acknowledgement
				),
				origin.name
			)
		}
		assertNull(
			readerPageVisualLocationRelocationAcknowledgement(
				ReaderPageVisualLocationOrigin.ExactPageTurn,
				null
			)
		)
	}

	@Test
	fun exactReceiptCannotSettleAReplacedCurlAuthorityToken() {
		assertEquals(
			ReaderPageVisualLocationOrigin.StaleAcknowledgement,
			readerPageVisualLocationOrigin(
				foliateSessionRelocationPending = false,
				exactAcknowledgementMatches = true,
				exactAuthorityTokenMatches = false,
				acknowledgementPresent = true,
				relocationInFlight = true
			)
		)
		assertEquals(
			ReaderPageVisualLocationOrigin.ExactPageTurn,
			readerPageVisualLocationOrigin(
				foliateSessionRelocationPending = false,
				exactAcknowledgementMatches = true,
				exactAuthorityTokenMatches = true,
				acknowledgementPresent = true,
				relocationInFlight = true
			)
		)
	}

	@Test
	fun unmatchedAcknowledgementIsAlwaysStaleAndCannotReviveATerminalRequest() {
		assertEquals(
			ReaderPageVisualLocationOrigin.StaleAcknowledgement,
			readerPageVisualLocationOrigin(
				foliateSessionRelocationPending = false,
				exactAcknowledgementMatches = false,
				acknowledgementPresent = true,
				relocationInFlight = false
			)
		)
		assertEquals(
			ReaderPageVisualLocationOrigin.StaleAcknowledgement,
			readerPageVisualLocationOrigin(
				foliateSessionRelocationPending = false,
				exactAcknowledgementMatches = false,
				acknowledgementPresent = true,
				relocationInFlight = true
			)
		)
	}

	@Test
	fun exactAndSessionAuthoritiesOutrankStaleOrPendingClassification() {
		assertEquals(
			ReaderPageVisualLocationOrigin.ExactPageTurn,
			readerPageVisualLocationOrigin(
				foliateSessionRelocationPending = false,
				exactAcknowledgementMatches = true,
				acknowledgementPresent = true,
				relocationInFlight = true
			)
		)
		assertEquals(
			ReaderPageVisualLocationOrigin.External,
			readerPageVisualLocationOrigin(
				foliateSessionRelocationPending = true,
				exactAcknowledgementMatches = true,
				acknowledgementPresent = true,
				relocationInFlight = true
			)
		)
	}

	@Test
	fun untaggedLocationWaitsForTheEntireExactHandoff() {
		assertEquals(
			ReaderPageVisualLocationOrigin.PendingExactPageTurn,
			readerPageVisualLocationOrigin(
				foliateSessionRelocationPending = false,
				exactAcknowledgementMatches = false,
				acknowledgementPresent = false,
				relocationInFlight = true
			)
		)
		assertEquals(
			ReaderPageVisualLocationOrigin.External,
			readerPageVisualLocationOrigin(
				foliateSessionRelocationPending = false,
				exactAcknowledgementMatches = false,
				acknowledgementPresent = false,
				relocationInFlight = false
			)
		)
	}
}
