package paige.navic.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class ReaderProcessStateViewModel(
	private val savedStateHandle: SavedStateHandle
) : ViewModel() {
	private var lastEncodedState: String? = savedStateHandle[ReaderProcessStateSavedStateKey]

	fun retain(snapshot: ReaderProcessStateSnapshot) {
		val encoded = encodeReaderProcessState(snapshot)
		if (encoded == null) {
			clear()
		} else if (encoded != lastEncodedState) {
			savedStateHandle[ReaderProcessStateSavedStateKey] = encoded
			lastEncodedState = encoded
		}
	}

	fun retain(state: ReaderControllerState) {
		state.toReaderProcessStateSnapshot()?.let(::retain)
	}

	fun restore(publication: ReaderPublicationIdentity): ReaderProcessStateSnapshot? =
		decodeReaderProcessState(lastEncodedState)
			?.takeIf { snapshot ->
				snapshot.publication.bookId == publication.bookId &&
					snapshot.publication.resourceHref == publication.resourceHref &&
					snapshot.publication.kind == publication.kind &&
					snapshot.publication.format == publication.format
			}

	fun clear() {
		if (lastEncodedState != null) {
			savedStateHandle.remove<String>(ReaderProcessStateSavedStateKey)
			lastEncodedState = null
		}
	}
}
