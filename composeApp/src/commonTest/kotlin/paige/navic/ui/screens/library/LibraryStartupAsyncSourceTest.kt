package paige.navic.ui.screens.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LibraryStartupAsyncSourceTest {
	@Test
	fun mostPlayedArtworkAggregationRunsOffTheUiDispatcher() {
		val source = commonMain(
			"paige/navic/ui/screens/library/MostPlayedShortcutsViewModel.kt"
		)

		assertTrue(
			".flowOn(Dispatchers.Default)" in source,
			"Most-played shortcut artwork aggregation combines broad artist, album, and song artwork tables; " +
				"that combine body must not run on the UI dispatcher during Library startup."
		)
		assertTrue(
			"viewModelScope.launch(Dispatchers.IO)" in source,
			"Most-played Aurral artist photo hydration must run cache/network work on IO."
		)
	}

	@Test
	fun artistListAurralCacheMergeRunsOffTheUiDispatcher() {
		val source = commonMain(
			"paige/navic/ui/screens/artist/viewmodels/ArtistListViewModel.kt"
		)

		assertTrue(
			".combine(artistPhotoCacheDao.observeArtistPhotoCache())" in source,
			"Artist list must keep merging cached Aurral photos into catalog rows."
		)
		assertTrue(
			".flowOn(Dispatchers.Default)" in source,
			"Artist list cache merging should not run on the UI dispatcher during Library startup."
		)
		assertTrue(
			"viewModelScope.launch(Dispatchers.IO)" in source,
			"Artist list Aurral photo hydration must run cache/network work on IO."
		)
	}

	@Test
	fun albumAurralStatusRefreshRunsOffTheUiDispatcher() {
		val source = commonMain(
			"paige/navic/ui/screens/album/viewmodels/AlbumListViewModel.kt"
		)

		assertTrue(
			"private fun refreshAurralAcquisitionRequests()" in source,
			"Album list must keep refreshing Aurral acquisition status for request chips."
		)
		assertTrue(
			"viewModelScope.launch(Dispatchers.IO)" in source,
			"Album Aurral service-status refresh is cache/network work and must not run on the UI dispatcher."
		)
	}

	private fun commonMain(path: String): String =
		File("src/commonMain/kotlin/$path").readText()
}
