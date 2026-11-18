package paige.navic.ui.screens.genre

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class GenreListSortSourceTest {
	@Test
	fun genreScreenShowsFilteredSortedStateWithSortAndSearchControls() {
		val screen = sourceFile("ui/screens/genre/GenreListScreen.kt").readText()

		assertContains(screen, "viewModel.visibleGenresState.collectAsState()")
		assertContains(screen, "GenreListScreenSortButton(")
		assertContains(screen, "onSetSorting = { viewModel.setSorting(it) }")
		assertContains(screen, "onSetReversed = { viewModel.setReversed(it) }")
		assertContains(screen, "GenreListScreenSearchField(")
		assertContains(screen, "viewModel.setSearchQuery(query)")
	}

	@Test
	fun genreSortChoiceIsPersistedInPreferences() {
		val viewModel = sourceFile("ui/screens/genre/viewmodels/GenreListViewModel.kt").readText()
		val preferences = sourceFile("domain/manager/PreferenceManager.kt").readText()

		assertContains(preferences, "var genreListSorting by preference(DomainGenreListType.AlbumCount)")
		assertContains(preferences, "var genreListReversed by preference(false)")
		assertContains(viewModel, "MutableStateFlow(preferenceManager.genreListSorting)")
		assertContains(viewModel, "MutableStateFlow(preferenceManager.genreListReversed)")
		assertContains(viewModel, "preferenceManager.genreListSorting = sorting")
		assertContains(viewModel, "preferenceManager.genreListReversed = reversed")
		assertContains(viewModel, "visibleGenreSummaries(")
	}

	@Test
	fun libraryGenreRowKeepsUnfilteredRepositoryOrder() {
		val library = sourceFile("ui/screens/library/LibraryScreen.kt").readText()

		assertTrue(
			"visibleGenresState" !in library,
			"The library genre row must not inherit the genre screen's search filter."
		)
	}

	private fun sourceFile(relativePath: String): File = listOf(
		File("src/commonMain/kotlin/paige/navic/$relativePath"),
		File("composeApp/src/commonMain/kotlin/paige/navic/$relativePath")
	).firstOrNull { it.isFile }
		?: error("Unable to locate $relativePath")
}
