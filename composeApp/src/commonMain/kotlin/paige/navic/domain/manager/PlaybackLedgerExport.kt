package paige.navic.domain.manager

/**
 * Read access to the persistent playback ledger: one record per played song with how long it
 * played, why playback moved on, and any stalls, silent gaps or errors. Android only; other
 * platforms register no implementation.
 */
interface PlaybackLedgerExport {
	suspend fun entryCount(): Int
	suspend fun exportText(): String
	suspend fun clear()
}
