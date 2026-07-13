package paige.navic.reader

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val ReaderProcessStateSavedStateKey = "reader_process_state_v1"

private const val ReaderProcessStateVersion = 1
private const val ReaderProcessStateMaxEncodedChars = 131_072
private const val ReaderProcessIdentityMaxChars = 4_096
private const val ReaderProcessTitleMaxChars = 2_048
private const val ReaderProcessQueryMaxChars = 1_024
private const val ReaderProcessSelectionTextMaxChars = 16_384
private const val ReaderProcessLocatorMaxChars = 16_384
private const val ReaderProcessNoteMaxChars = 65_536

private val ReaderProcessStateJson = Json {
	encodeDefaults = true
	ignoreUnknownKeys = true
}

data class ReaderProcessStateSnapshot(
	val publication: ReaderPublicationIdentity,
	val dialog: ReaderControllerDialog? = null,
	val searchQuery: String = "",
	val searchSubmitted: Boolean = false,
	val selection: ReaderSelection? = null,
	val selectionNoteDraft: ReaderSelectionNoteDraft? = null
)

fun ReaderControllerState.toReaderProcessStateSnapshot(): ReaderProcessStateSnapshot? {
	val currentPublication = publication ?: return null
	return ReaderProcessStateSnapshot(
		publication = currentPublication,
		dialog = dialog,
		searchQuery = search.query,
		searchSubmitted = search.active,
		selection = selection?.semanticReaderSelection(),
		selectionNoteDraft = selectionNoteDraft
	)
}

fun ReaderController.restoreProcessState(snapshot: ReaderProcessStateSnapshot): ReaderControllerStep {
	val publication = state.publication
	if (publication == null || !publication.matchesReaderProcessPublication(snapshot.publication)) {
		return ReaderControllerStep(this)
	}

	val supportsSearch = state.supportsReaderEngineCapability(ReaderEngineCapability.Search)
	val supportsMediaOverlay = state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)
	val restoredDialog = when (snapshot.dialog) {
		ReaderControllerDialog.Search -> ReaderControllerDialog.Search.takeIf { supportsSearch }
		ReaderControllerDialog.WhispersyncPlayer -> ReaderControllerDialog.WhispersyncPlayer.takeIf {
			supportsMediaOverlay
		}
		ReaderControllerDialog.Contents -> ReaderControllerDialog.Contents
		ReaderControllerDialog.Settings -> ReaderControllerDialog.Settings
		null -> null
	}
	val restoreSearch = restoredDialog == ReaderControllerDialog.Search
	val searchQuery = snapshot.searchQuery.takeIf { restoreSearch }.orEmpty()
	val restoredSelection = snapshot.selection?.semanticReaderSelection()
	val restoredDraft = snapshot.selectionNoteDraft?.takeIf { draft ->
		draft.bookId == publication.bookId
	}
	val restored = copy(
		state = state.copy(
			dialog = restoredDialog,
			menuVisible = restoredDialog != null,
			search = ReaderSearchState(query = searchQuery),
			selection = restoredSelection,
			selectionNoteDraft = restoredDraft
		)
	)

	return if (restoreSearch && snapshot.searchSubmitted && searchQuery.isNotBlank()) {
		restored.search(searchQuery)
	} else {
		ReaderControllerStep(restored)
	}
}

internal fun encodeReaderProcessState(snapshot: ReaderProcessStateSnapshot): String? {
	val normalized = snapshot.normalizedForSavedState()
	return try {
		ReaderProcessStateJson.encodeToString(normalized.toPersistedReaderProcessState())
			.takeIf { it.length <= ReaderProcessStateMaxEncodedChars }
	} catch (_: SerializationException) {
		null
	} catch (_: IllegalArgumentException) {
		null
	}
}

internal fun decodeReaderProcessState(encoded: String?): ReaderProcessStateSnapshot? {
	if (encoded.isNullOrBlank() || encoded.length > ReaderProcessStateMaxEncodedChars) return null
	return try {
		ReaderProcessStateJson.decodeFromString<PersistedReaderProcessState>(encoded)
			.toReaderProcessStateSnapshot()
	} catch (_: SerializationException) {
		null
	} catch (_: IllegalArgumentException) {
		null
	}
}

private fun ReaderProcessStateSnapshot.normalizedForSavedState(): ReaderProcessStateSnapshot = copy(
	publication = publication.copy(
		bookId = publication.bookId.take(ReaderProcessIdentityMaxChars),
		title = publication.title.take(ReaderProcessTitleMaxChars),
		resourceHref = publication.resourceHref.take(ReaderProcessIdentityMaxChars)
	),
	searchQuery = searchQuery.take(ReaderProcessQueryMaxChars),
	selection = selection?.semanticReaderSelection(),
	selectionNoteDraft = selectionNoteDraft?.copy(
		bookId = selectionNoteDraft.bookId.take(ReaderProcessIdentityMaxChars),
		bookTitle = selectionNoteDraft.bookTitle.take(ReaderProcessTitleMaxChars),
		text = selectionNoteDraft.text.take(ReaderProcessSelectionTextMaxChars),
		cfi = selectionNoteDraft.cfi.take(ReaderProcessLocatorMaxChars),
		href = selectionNoteDraft.href?.take(ReaderProcessIdentityMaxChars),
		sectionTitle = selectionNoteDraft.sectionTitle?.take(ReaderProcessTitleMaxChars),
		note = selectionNoteDraft.note.take(ReaderProcessNoteMaxChars)
	)
)

private fun ReaderSelection.semanticReaderSelection(): ReaderSelection? {
	val semanticText = text?.take(ReaderProcessSelectionTextMaxChars)
	val semanticCfi = cfi?.take(ReaderProcessLocatorMaxChars)
	val semanticHref = href?.take(ReaderProcessIdentityMaxChars)
	if (semanticText.isNullOrBlank() && semanticCfi.isNullOrBlank() && semanticHref.isNullOrBlank()) return null
	return ReaderSelection(
		text = semanticText,
		cfi = semanticCfi,
		href = semanticHref
	)
}

private fun ReaderPublicationIdentity.matchesReaderProcessPublication(other: ReaderPublicationIdentity): Boolean =
	bookId == other.bookId &&
		resourceHref == other.resourceHref &&
		kind == other.kind &&
		format == other.format

@Serializable
private data class PersistedReaderProcessState(
	val version: Int = ReaderProcessStateVersion,
	val bookId: String,
	val bookTitle: String,
	val resourceHref: String,
	val publicationKind: String,
	val publicationFormat: String,
	val dialog: String? = null,
	val searchQuery: String = "",
	val searchSubmitted: Boolean = false,
	val selection: PersistedReaderSelection? = null,
	val selectionNoteDraft: PersistedReaderSelectionNoteDraft? = null
)

@Serializable
private data class PersistedReaderSelection(
	val text: String? = null,
	val cfi: String? = null,
	val href: String? = null
)

@Serializable
private data class PersistedReaderSelectionNoteDraft(
	val bookId: String,
	val bookTitle: String,
	val text: String,
	val cfi: String,
	val href: String? = null,
	val sectionTitle: String? = null,
	val note: String = ""
)

private fun ReaderProcessStateSnapshot.toPersistedReaderProcessState(): PersistedReaderProcessState =
	PersistedReaderProcessState(
		bookId = publication.bookId,
		bookTitle = publication.title,
		resourceHref = publication.resourceHref,
		publicationKind = publication.kind.name,
		publicationFormat = publication.format.name,
		dialog = dialog?.name,
		searchQuery = searchQuery,
		searchSubmitted = searchSubmitted,
		selection = selection?.let { current ->
			PersistedReaderSelection(
				text = current.text,
				cfi = current.cfi,
				href = current.href
			)
		},
		selectionNoteDraft = selectionNoteDraft?.let { draft ->
			PersistedReaderSelectionNoteDraft(
				bookId = draft.bookId,
				bookTitle = draft.bookTitle,
				text = draft.text,
				cfi = draft.cfi,
				href = draft.href,
				sectionTitle = draft.sectionTitle,
				note = draft.note
			)
		}
	)

private fun PersistedReaderProcessState.toReaderProcessStateSnapshot(): ReaderProcessStateSnapshot? {
	if (version != ReaderProcessStateVersion) return null
	val kind = enumValues<ReaderPublicationKind>().firstOrNull { it.name == publicationKind } ?: return null
	val format = enumValues<ReaderPublicationFormat>().firstOrNull { it.name == publicationFormat } ?: return null
	val restoredDialog = dialog?.let { name ->
		enumValues<ReaderControllerDialog>().firstOrNull { it.name == name }
	}
	if (dialog != null && restoredDialog == null) return null
	return ReaderProcessStateSnapshot(
		publication = ReaderPublicationIdentity(
			bookId = bookId,
			title = bookTitle,
			resourceHref = resourceHref,
			kind = kind,
			format = format
		),
		dialog = restoredDialog,
		searchQuery = searchQuery,
		searchSubmitted = searchSubmitted,
		selection = selection?.let { current ->
			ReaderSelection(
				text = current.text,
				cfi = current.cfi,
				href = current.href
			)
		},
		selectionNoteDraft = selectionNoteDraft?.let { draft ->
			ReaderSelectionNoteDraft(
				bookId = draft.bookId,
				bookTitle = draft.bookTitle,
				text = draft.text,
				cfi = draft.cfi,
				href = draft.href,
				sectionTitle = draft.sectionTitle,
				note = draft.note
			)
		}
	).normalizedForSavedState()
}
