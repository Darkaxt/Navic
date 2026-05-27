package paige.navic.ui.screens.search.viewmodels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchHistoryPolicyTest {
	@Test
	fun savesOnlyNonBlankQueriesWhenHistoryIsNotPaused() {
		assertTrue(shouldSaveSearchHistory(query = "artist", pauseSearchHistory = false))
		assertFalse(shouldSaveSearchHistory(query = "", pauseSearchHistory = false))
		assertFalse(shouldSaveSearchHistory(query = "   ", pauseSearchHistory = false))
		assertFalse(shouldSaveSearchHistory(query = "artist", pauseSearchHistory = true))
	}

	@Test
	fun hidesSearchHistoryWhilePaused() {
		val history = listOf("artist", "album")

		assertEquals(history, visibleSearchHistory(history, pauseSearchHistory = false))
		assertEquals(emptyList(), visibleSearchHistory(history, pauseSearchHistory = true))
	}

	@Test
	fun submittedQueriesMoveToTheTopAndAreLimited() {
		val initial = (1..10).map { "query-$it" }

		assertEquals(
			listOf("query-3", "query-1", "query-2", "query-4", "query-5", "query-6", "query-7", "query-8", "query-9", "query-10"),
			updatedSearchHistoryAfterSubmit(query = "query-3", history = initial, pauseSearchHistory = false)
		)
		assertEquals(
			listOf("new") + initial.take(9),
			updatedSearchHistoryAfterSubmit(query = " new ", history = initial, pauseSearchHistory = false)
		)
	}

	@Test
	fun submittedQueriesDoNotChangeHistoryWhilePaused() {
		val history = listOf("artist", "album")

		assertEquals(
			history,
			updatedSearchHistoryAfterSubmit(query = "track", history = history, pauseSearchHistory = true)
		)
	}

	@Test
	fun encodedHistoryRoundTripsWithoutBlanksOrOverflow() {
		val history = listOf("artist", "", "album", "artist") + (1..20).map { "query-$it" }
		val decoded = decodeSearchHistory(encodeSearchHistory(history))

		assertEquals(
			listOf("artist", "album", "query-1", "query-2", "query-3", "query-4", "query-5", "query-6", "query-7", "query-8"),
			decoded
		)
	}

	@Test
	fun removesQueryFromHistory() {
		assertEquals(
			listOf("artist", "track"),
			updatedSearchHistoryAfterRemoval(
				query = "album",
				history = listOf("artist", "album", "track")
			)
		)
	}
}
