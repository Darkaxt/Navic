package paige.navic.ui.screens.bindery

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.canonicalReaderResourceHref

/** The open sheet's remembered languages and media type for one book; null means "no choice yet". */
@Serializable
data class BinderyBookLanguageChoice(
	val ebook: String? = null,
	val audio: String? = null,
	/** Read and listen (true) or read only (false). */
	val audiobook: Boolean? = null
)

/** "Continue from 42% (Other edition)": the fraction of another edition of the same book. */
data class BinderyProgressImportSuggestion(
	val fraction: Double,
	val sourceTitle: String,
	/** The selected edition has no progress of its own, so importing is the default. */
	val selectedByDefault: Boolean,
	/** Where the selected edition itself was left, when it has been opened before. */
	val ownFraction: Double? = null
)

private val BinderyLanguageChoiceJson = Json { ignoreUnknownKeys = true }

/** Ebook rows the reader can open, one per edition, in every language. */
fun binderyReadableEbookEditions(rows: List<BinderyBookVersionRow>): List<BinderyBookVersionRow> =
	rows.filter { row ->
		row.kind == BinderyBookVersionKind.Ebook &&
			row.readerSupported &&
			canonicalReaderResourceHref(row.id) != null
	}

fun binderyEditionLanguages(editions: List<BinderyBookVersionRow>): List<String> =
	editions.mapNotNull(BinderyBookVersionRow::language).distinct().sorted()

fun binderyMatchLanguages(matches: List<BinderyWhispersyncMatch>): List<String> =
	matches.mapNotNull(BinderyWhispersyncMatch::oppositeLanguage).distinct().sorted()

/** Matches of one audiobook language; an unknown or absent language keeps every match. */
fun binderyMatchesForLanguage(
	matches: List<BinderyWhispersyncMatch>,
	language: String?
): List<BinderyWhispersyncMatch> =
	if (language == null || language !in binderyMatchLanguages(matches)) {
		matches
	} else {
		matches.filter { match -> match.oppositeLanguage == language }
	}

/** The edition to open in [language]: the current one when it already matches, else the most recently read. */
fun binderyEditionForLanguage(
	editions: List<BinderyBookVersionRow>,
	language: String,
	current: BinderyBookVersionRow,
	progresses: List<BinderyReadingProgress>
): BinderyBookVersionRow {
	if (current.language == language) return current
	val candidates = editions.filter { row -> row.language == language }
	if (candidates.isEmpty()) return current
	return candidates.maxByOrNull { row ->
		progresses.progressFor(row)?.updatedAtValue() ?: Long.MIN_VALUE
	}?.takeIf { row -> progresses.progressFor(row) != null }
		?: candidates.firstOrNull { row -> row.format == ReaderPublicationFormat.Epub }
		?: candidates.first()
}

/**
 * Suggests carrying the reading position over from another edition (or language) of the same book when
 * that edition was read more recently. Positions map by whole-book fraction: editions differ in pages.
 */
fun binderyProgressImportSuggestion(
	selected: BinderyBookVersionRow,
	editions: List<BinderyBookVersionRow>,
	progresses: List<BinderyReadingProgress>
): BinderyProgressImportSuggestion? {
	val own = progresses.progressFor(selected)
	val other = editions
		.filter { row -> canonicalReaderResourceHref(row.id) != canonicalReaderResourceHref(selected.id) }
		.mapNotNull { row -> progresses.progressFor(row)?.let { progress -> row to progress } }
		.filter { (_, progress) -> progress.validFraction() != null }
		.maxByOrNull { (_, progress) -> progress.updatedAtValue() }
		?: return null
	val (sourceRow, sourceProgress) = other
	val fraction = sourceProgress.validFraction() ?: return null
	if (own != null) {
		if (sourceProgress.updatedAtValue() <= own.updatedAtValue()) return null
		val ownFraction = own.validFraction() ?: 0.0
		if (kotlin.math.abs(ownFraction - fraction) < 0.01) return null
	}
	return BinderyProgressImportSuggestion(
		fraction = fraction,
		sourceTitle = listOfNotNull(
			sourceRow.title.trim().takeIf { it.isNotEmpty() },
			sourceRow.language?.let(::binderyLanguageDisplayName)
		).joinToString(" · "),
		selectedByDefault = own == null,
		ownFraction = own?.validFraction()
	)
}

/** Whole-book fraction where [row] was left, if it has been read. */
fun binderyEditionProgressFraction(
	row: BinderyBookVersionRow,
	progresses: List<BinderyReadingProgress>
): Double? = progresses.progressFor(row)?.validFraction()

fun binderyBookLanguageChoice(json: String, bookId: String): BinderyBookLanguageChoice =
	decodeBinderyBookLanguageChoices(json)[bookId.trim()] ?: BinderyBookLanguageChoice()

fun binderyBookLanguageChoicesJsonWithUpdate(
	json: String,
	bookId: String,
	choice: BinderyBookLanguageChoice
): String {
	val key = bookId.trim().takeIf { it.isNotEmpty() } ?: return json
	val updated = decodeBinderyBookLanguageChoices(json).toMutableMap()
	if (choice == BinderyBookLanguageChoice()) updated.remove(key) else updated[key] = choice
	return BinderyLanguageChoiceJson.encodeToString(updated)
}

fun binderyLanguageDisplayName(language: String): String =
	when (language) {
		"eng" -> "English"
		"spa" -> "Español"
		"fra" -> "Français"
		"deu" -> "Deutsch"
		"ita" -> "Italiano"
		"por" -> "Português"
		"jpn" -> "日本語"
		"kor" -> "한국어"
		"zho" -> "中文"
		else -> language.uppercase()
	}

private fun decodeBinderyBookLanguageChoices(json: String): Map<String, BinderyBookLanguageChoice> =
	json.trim().takeIf { it.isNotEmpty() }?.let { raw ->
		runCatching {
			BinderyLanguageChoiceJson.decodeFromString<Map<String, BinderyBookLanguageChoice>>(raw)
		}.getOrNull()
	}.orEmpty()

private fun List<BinderyReadingProgress>.progressFor(row: BinderyBookVersionRow): BinderyReadingProgress? {
	val href = canonicalReaderResourceHref(row.id) ?: return null
	return filter { progress ->
		progress.kind == BinderyReadingProgressKind.Ebook && progress.readerResourceHref() == href
	}.maxByOrNull { progress -> progress.updatedAtValue() }
}

private fun BinderyReadingProgress.updatedAtValue(): Long = updatedAt?.trim()?.toLongOrNull() ?: 0L

private fun BinderyReadingProgress.validFraction(): Double? =
	progressFraction?.takeIf { it.isFinite() && it > 0.0 }?.coerceAtMost(1.0)
