package paige.navic.reader

import com.russhwolf.settings.MapSettings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.isReaderBookPrepared
import paige.navic.domain.manager.markReaderBookPrepared
import paige.navic.domain.manager.readerPreparationOrientations
import paige.navic.domain.manager.readerPreparedOrientations

/** #68: "Prepare pages" records each orientation per pagination-affecting settings. */
class ReaderPreparedBooksTest {
	private val landscape = ReaderPreparationOrientation.Landscape
	private val portrait = ReaderPreparationOrientation.Portrait

	@Test
	fun bothLayoutsNeedEveryOrientationUnlessTheReaderIsLocked() {
		val both = ReaderSettings(pageCacheLayouts = ReaderPageCacheLayouts.Both.persistedValue)
		assertEquals(setOf(landscape, portrait), readerPreparationOrientations(both, landscape))
		assertEquals(setOf(portrait), readerPreparationOrientations(ReaderSettings(), portrait))
		assertEquals(
			setOf(landscape),
			readerPreparationOrientations(both.copy(orientation = ReaderOrientationLockedLandscape), portrait)
		)
		assertEquals(
			setOf(portrait),
			readerPreparationOrientations(both.copy(orientation = ReaderOrientationPortrait), landscape)
		)
	}

	@Test
	fun theCheckMarkNeedsEveryOrientationAndGoesStaleWithPaginationSettings() {
		val preferences = PreferenceManager(MapSettings())
		val settings = ReaderSettings(
			fontSizePercent = 100,
			pageCacheLayouts = ReaderPageCacheLayouts.Both.persistedValue
		)
		preferences.readerPreparedBookIds = "book-1" // v1.5.0 bare entry carries no layout
		assertFalse(preferences.isReaderBookPrepared("book-1", settings, landscape))

		preferences.markReaderBookPrepared("book-1", settings, landscape)
		assertFalse(preferences.isReaderBookPrepared("book-1", settings, landscape))
		assertTrue(preferences.isReaderBookPrepared("book-1", settings.copy(pageCacheLayouts = "current"), landscape))

		preferences.markReaderBookPrepared("book-1", settings, portrait)
		assertTrue(preferences.isReaderBookPrepared("book-1", settings, portrait))
		assertFalse(preferences.isReaderBookPrepared("book-2", settings, portrait))

		// A non-pagination setting keeps the record; a font size change makes it stale.
		assertTrue(preferences.isReaderBookPrepared("book-1", settings.copy(fullscreen = false), landscape))
		val bigger = settings.copy(fontSizePercent = 120)
		assertFalse(preferences.isReaderBookPrepared("book-1", bigger, landscape))

		// Preparing under new settings drops the stale entries.
		preferences.markReaderBookPrepared("book-1", bigger, landscape)
		assertEquals(setOf(landscape), preferences.readerPreparedOrientations("book-1", bigger))
		assertEquals(emptySet(), preferences.readerPreparedOrientations("book-1", settings))
	}
}
