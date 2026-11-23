package paige.navic.domain.manager

import paige.navic.reader.ReaderOrientationLandscape
import paige.navic.reader.ReaderOrientationLockedLandscape
import paige.navic.reader.ReaderOrientationLockedPortrait
import paige.navic.reader.ReaderOrientationPortrait
import paige.navic.reader.ReaderOrientationReversePortrait
import paige.navic.reader.ReaderPageCacheLayouts
import paige.navic.reader.ReaderPreparationOrientation
import paige.navic.reader.ReaderSettings
import paige.navic.reader.normalizeReaderPageBitmapQuality
import paige.navic.reader.normalizeReaderPageCacheLayouts
import paige.navic.reader.readerSettingsForBook

/**
 * "Prepare pages" (#66, #68): which layouts of a Bindery book were prepared. One
 * "bookId|signature|orientation" entry per prepared orientation; [readerPreparationSignature]
 * changes with every setting that changes pagination, so the record goes stale with them.
 */
private const val ReaderPreparedSeparator = '|'

/** The pagination-affecting reader settings; orientation and viewport are recorded separately. */
fun readerPreparationSignature(settings: ReaderSettings): String =
	listOf(
		settings.fontFamily, settings.fontSource, settings.customFontFamily, settings.customFontUrl,
		settings.fontSizePercent, settings.lineHeight, settings.paragraphSpacingPercent,
		settings.marginPercent, settings.fontWeight, settings.letterSpacing, settings.wordSpacing,
		settings.sideMargin, settings.topMargin, settings.bottomMargin, settings.indent,
		settings.headingFontSize, settings.maxColumnCount, settings.columnThreshold,
		settings.flowMode, settings.direction,
		// Pages prepared at another bitmap quality are re-rendered, so they are not prepared.
		normalizeReaderPageBitmapQuality(settings.pageBitmapQuality).persistedValue
	).joinToString(separator = ",").hashCode().toUInt().toString(16)

/**
 * The orientations a book must have prepared to read it without preparation: the one its
 * orientation setting allows, or both when the reader can rotate and "Both layouts" is on.
 * [current] stands for a rotating reader that caches only the current layout.
 */
fun readerPreparationOrientations(
	settings: ReaderSettings,
	current: ReaderPreparationOrientation
): Set<ReaderPreparationOrientation> =
	when (settings.orientation) {
		ReaderOrientationLandscape, ReaderOrientationLockedLandscape -> setOf(ReaderPreparationOrientation.Landscape)
		ReaderOrientationPortrait, ReaderOrientationLockedPortrait, ReaderOrientationReversePortrait ->
			setOf(ReaderPreparationOrientation.Portrait)
		else -> if (normalizeReaderPageCacheLayouts(settings.pageCacheLayouts) == ReaderPageCacheLayouts.Both) {
			ReaderPreparationOrientation.entries.toSet()
		} else {
			setOf(current)
		}
	}

fun PreferenceManager.readerPreparedOrientations(
	bookId: String,
	settings: ReaderSettings
): Set<ReaderPreparationOrientation> {
	val signature = readerPreparationSignature(settings)
	return readerPreparedEntries()
		.filter { entry -> entry.bookId == bookId && entry.signature == signature }
		.mapTo(mutableSetOf()) { entry -> entry.orientation }
}

/** Whether every orientation [settings] needs is prepared, as seen from [current]. */
fun PreferenceManager.isReaderBookPrepared(
	bookId: String,
	settings: ReaderSettings,
	current: ReaderPreparationOrientation
): Boolean =
	bookId.isNotBlank() &&
		readerPreparedOrientations(bookId, settings).containsAll(readerPreparationOrientations(settings, current))

/** Records [orientation] as prepared for [settings]; entries for other settings are dropped. */
fun PreferenceManager.markReaderBookPrepared(
	bookId: String,
	settings: ReaderSettings,
	orientation: ReaderPreparationOrientation
) {
	if (bookId.isBlank() || ReaderPreparedSeparator in bookId) return
	val signature = readerPreparationSignature(settings)
	val kept = readerPreparedEntries().filterNot { entry ->
		entry.bookId == bookId && (entry.signature != signature || entry.orientation == orientation)
	}
	readerPreparedBookIds = (kept + ReaderPreparedEntry(bookId, signature, orientation))
		.joinToString(separator = "\n") { entry ->
			listOf(entry.bookId, entry.signature, entry.orientation.persistedValue)
				.joinToString(separator = ReaderPreparedSeparator.toString())
		}
}

/**
 * The reader finished preparing [bookId] in [orientation] under its current settings: records it
 * and returns an orientation still missing (#68 "Both layouts"), or null when the book is prepared.
 */
fun PreferenceManager.recordReaderBookPrepared(
	bookId: String,
	orientation: ReaderPreparationOrientation
): ReaderPreparationOrientation? {
	val settings = readerSettingsForBook(bookId)
	markReaderBookPrepared(bookId, settings, orientation)
	return (readerPreparationOrientations(settings, orientation) - readerPreparedOrientations(bookId, settings))
		.firstOrNull()
}

private data class ReaderPreparedEntry(
	val bookId: String,
	val signature: String,
	val orientation: ReaderPreparationOrientation
)

/** v1.5.0 stored bare book ids; those entries carry no layout and are ignored. */
private fun PreferenceManager.readerPreparedEntries(): List<ReaderPreparedEntry> =
	readerPreparedBookIds.lineSequence()
		.mapNotNull { line ->
			val parts = line.trim().split(ReaderPreparedSeparator)
			if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank()) return@mapNotNull null
			val orientation = ReaderPreparationOrientation.fromPersisted(parts[2]) ?: return@mapNotNull null
			ReaderPreparedEntry(parts[0], parts[1], orientation)
		}
		.toList()
