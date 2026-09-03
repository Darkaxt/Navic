package paige.navic.reader

data class ReaderPresentationPublicationIdentity(
	val foliateSessionId: String,
	val publicationGeneration: Long
) {
	init {
		require(foliateSessionId.isNotBlank())
		require(publicationGeneration >= 0L)
	}
}

val ReaderPresentationBinding.publicationIdentity: ReaderPresentationPublicationIdentity
	get() = ReaderPresentationPublicationIdentity(
		foliateSessionId = foliateSessionId,
		publicationGeneration = publicationGeneration
	)

data class ReaderPresentationReceiptVersion(
	val readerSessionGeneration: Long,
	val publicationIdentity: ReaderPresentationPublicationIdentity?,
	val eventSequence: Long
) {
	init {
		require(readerSessionGeneration >= 0L)
		require(eventSequence >= 0L)
	}
}

data class ReaderPresentationEventReceipt(
	val event: ReaderPresentationEvent,
	val version: ReaderPresentationReceiptVersion,
	val disposition: ReaderPresentationEventDisposition,
	val postState: ReaderPresentationState,
	val effects: List<ReaderPresentationEffect>
)
