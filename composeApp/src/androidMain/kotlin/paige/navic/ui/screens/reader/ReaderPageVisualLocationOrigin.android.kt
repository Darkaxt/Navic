package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageTurnSettlementAck

internal fun readerPageVisualLocationRelocationAcknowledgement(
	origin: ReaderPageVisualLocationOrigin,
	acknowledgement: ReaderPageTurnSettlementAck?
): ReaderPageTurnSettlementAck? = acknowledgement.takeIf {
	origin == ReaderPageVisualLocationOrigin.ExactPageTurn
}

internal fun readerPageVisualLocationOrigin(
	foliateSessionRelocationPending: Boolean,
	exactAcknowledgementMatches: Boolean,
	exactAuthorityTokenMatches: Boolean = true,
	acknowledgementPresent: Boolean,
	relocationInFlight: Boolean
): ReaderPageVisualLocationOrigin = when {
	foliateSessionRelocationPending -> ReaderPageVisualLocationOrigin.External
	exactAcknowledgementMatches && exactAuthorityTokenMatches ->
		ReaderPageVisualLocationOrigin.ExactPageTurn
	acknowledgementPresent -> ReaderPageVisualLocationOrigin.StaleAcknowledgement
	relocationInFlight -> ReaderPageVisualLocationOrigin.PendingExactPageTurn
	else -> ReaderPageVisualLocationOrigin.External
}
