package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPageVisualLocationOriginTest {
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
