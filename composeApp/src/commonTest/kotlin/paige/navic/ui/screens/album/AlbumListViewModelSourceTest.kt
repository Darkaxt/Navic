package paige.navic.ui.screens.album

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlbumListViewModelSourceTest {
	@Test
	fun albumsStateIsReactiveNotManuallyCollected() {
		val source = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/album/viewmodels/AlbumListViewModel.kt"
		).readText()

		// The album list is now a single stateIn-derived StateFlow backed by the shared
		// repository cache — no manual cold collector that could accumulate on refresh.
		assertTrue(
			"repository.albumsFlow" in source,
			"Album state must be derived from the shared reactive repository flow."
		)
		assertTrue(
			".stateIn(viewModelScope" in source,
			"Album state must be a single stateIn-derived StateFlow."
		)
		assertFalse(
			Regex("""getAlbumsFlow\([^)]*\)\s*\.collect""").containsMatchIn(source),
			"Album state must not manually collect a cold getAlbumsFlow (the reactive cache replaces it)."
		)
	}
}
