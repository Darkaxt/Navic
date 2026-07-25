package paige.navic.ui.screens.reader

internal fun readerPageVisualLocationOrigin(
	foliateSessionRelocationPending: Boolean,
	exactAcknowledgementMatches: Boolean,
	acknowledgementPresent: Boolean,
	relocationInFlight: Boolean
): ReaderPageVisualLocationOrigin = when {
	foliateSessionRelocationPending -> ReaderPageVisualLocationOrigin.External
	exactAcknowledgementMatches -> ReaderPageVisualLocationOrigin.ExactPageTurn
	acknowledgementPresent -> ReaderPageVisualLocationOrigin.StaleAcknowledgement
	relocationInFlight -> ReaderPageVisualLocationOrigin.PendingExactPageTurn
	else -> ReaderPageVisualLocationOrigin.External
}
