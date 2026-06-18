package paige.navic.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val DefaultReaderHighlightColor = "#f4d35e"

private val ReaderAnnotationJson = Json {
	ignoreUnknownKeys = true
	encodeDefaults = false
}

@Serializable
data class ReaderAnnotation(
	val id: String,
	val bookId: String,
	val bookTitle: String,
	val cfi: String,
	val text: String,
	val href: String? = null,
	val color: String = DefaultReaderHighlightColor,
	val note: String? = null,
	val sectionTitle: String? = null
) {
	val displayTitle: String
		get() = sectionTitle ?: text
}

data class ReaderAnnotationState(
	val annotations: List<ReaderAnnotation> = emptyList()
) {
	fun annotationsForBook(bookId: String): List<ReaderAnnotation> =
		annotations.filter { it.bookId == bookId }

	fun addSelectionHighlight(
		bookId: String,
		bookTitle: String,
		selection: ReaderBridgeEvent.SelectionChanged?,
		sectionTitle: String?,
		color: String = DefaultReaderHighlightColor
	): ReaderAnnotationState =
		addSelectionHighlight(
			bookId = bookId,
			bookTitle = bookTitle,
			selectionText = selection?.text,
			selectionCfi = selection?.cfi,
			selectionHref = selection?.href,
			sectionTitle = sectionTitle,
			color = color
		)

	fun addSelectionHighlight(
		bookId: String,
		bookTitle: String,
		selectionText: String?,
		selectionCfi: String?,
		selectionHref: String?,
		sectionTitle: String?,
		color: String = DefaultReaderHighlightColor
	): ReaderAnnotationState {
		val cfi = selectionCfi?.trim()?.takeIf { it.isNotEmpty() } ?: return this
		val text = selectionText?.trim()?.takeIf { it.isNotEmpty() } ?: return this
		val annotation = ReaderAnnotation(
			id = "$bookId|$cfi",
			bookId = bookId,
			bookTitle = bookTitle,
			cfi = cfi,
			text = text,
			href = selectionHref?.trim()?.takeIf { it.isNotEmpty() },
			color = color,
			sectionTitle = sectionTitle?.trim()?.takeIf { it.isNotEmpty() }
		)
		return copy(annotations = annotations.filterNot { it.id == annotation.id } + annotation)
	}

	fun addSelectionNote(
		draft: ReaderSelectionNoteDraft,
		note: String,
		color: String = DefaultReaderHighlightColor
	): ReaderAnnotationState {
		val normalizedNote = note.trim().takeIf { it.isNotEmpty() } ?: return this
		val annotation = ReaderAnnotation(
			id = "${draft.bookId}|${draft.cfi}",
			bookId = draft.bookId,
			bookTitle = draft.bookTitle,
			cfi = draft.cfi,
			text = draft.text,
			href = draft.href,
			color = color,
			note = normalizedNote,
			sectionTitle = draft.sectionTitle
		)
		return copy(annotations = annotations.filterNot { it.id == annotation.id } + annotation)
	}
}

fun encodeReaderAnnotations(annotations: List<ReaderAnnotation>): String =
	ReaderAnnotationJson.encodeToString(annotations)

fun decodeReaderAnnotations(json: String): List<ReaderAnnotation> =
	runCatching {
		ReaderAnnotationJson.decodeFromString<List<ReaderAnnotation>>(json)
			.filter { it.id.isNotBlank() && it.bookId.isNotBlank() && it.cfi.isNotBlank() }
	}.getOrDefault(emptyList())
