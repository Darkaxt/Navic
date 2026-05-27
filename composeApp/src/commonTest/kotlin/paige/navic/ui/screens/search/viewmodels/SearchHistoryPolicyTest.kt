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
}
