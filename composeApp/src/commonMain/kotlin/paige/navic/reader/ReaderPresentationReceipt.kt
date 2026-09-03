package paige.navic.reader

data class ReaderPresentationReceiptVersion(
	val readerSessionGeneration: Long,
	val eventSequence: Long
) {
	init {
		require(readerSessionGeneration >= 0L)
		require(eventSequence > 0L)
	}
}

data class ReaderPresentationEventReceipt(
	val event: ReaderPresentationEvent,
	val version: ReaderPresentationReceiptVersion,
	val disposition: ReaderPresentationEventDisposition,
	val postState: ReaderPresentationState,
	val effects: List<ReaderPresentationEffect>
)
