package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.publicationIdentity

internal fun readerTestPresentationReceipt(
	event: ReaderPresentationEvent,
	postState: ReaderPresentationState,
	disposition: ReaderPresentationEventDisposition =
		ReaderPresentationEventDisposition.Accepted
): ReaderPresentationEventReceipt = ReaderPresentationEventReceipt(
	event = event,
	version = ReaderPresentationReceiptVersion(
		readerSessionGeneration = 1L,
		publicationIdentity = postState.binding?.publicationIdentity,
		eventSequence = 1L
	),
	disposition = disposition,
	postState = postState,
	effects = emptyList()
)
