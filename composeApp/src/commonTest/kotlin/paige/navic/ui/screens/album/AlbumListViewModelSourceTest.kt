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

	@Test
	fun albumListPublishesAurralProjectionStateOutsideCardComposition() {
		val viewModel = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/album/viewmodels/AlbumListViewModel.kt"
		).readText()
		val item = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/album/components/Item.kt"
		).readText()

		assertTrue(
			"val aurralAlbumMatchesByLocalAlbumId" in viewModel,
			"Album list should expose matched Aurral album identities from the ViewModel instead of resolving inside each card."
		)
		assertTrue(
			"withContext(Dispatchers.Default)" in viewModel,
			"Album/Aurral projection joins are CPU work and must run off the UI dispatcher."
		)
		assertFalse(
			"aurralRepository.searchAlbums" in item,
			"Album cards must not run Aurral search or cache work during composition."
		)
	}

	@Test
	fun albumCardsCanNavigateWithAurralRouteHints() {
		val item = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/album/components/Item.kt"
		).readText()
		val screen = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/collection/CollectionDetailScreen.kt"
		).readText()
		val viewModel = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt"
		).readText()

		assertTrue(
			"aurralAlbumMatch: AurralAlbumSearchItem?" in item,
			"Album cards need an optional Aurral match so known catalog identity is not discarded on click."
		)
		assertTrue(
			"aurralAlbumCollectionDetailRoute(" in item,
			"Album card navigation should build a CollectionDetail route that carries the Aurral identity when available."
		)
		assertTrue(
			"route.aurralAlbumSearchItemOrNull()" in screen,
			"CollectionDetailScreen should translate route metadata into an Aurral match hint."
		)
		assertTrue(
			"applyAurralAlbumRouteHint(" in viewModel,
			"CollectionDetailViewModel should consume Aurral route hints before running local-first recovery search."
		)
	}

	@Test
	fun albumCardsDisplayAurralMetadataWhenMatched() {
		val item = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/album/components/Item.kt"
		).readText()

		assertTrue(
			"val displayedTitle =" in item &&
				"aurralAlbumMatch?.title" in item,
			"Matched album cards should display the Aurral title instead of always showing the Navidrome album name."
		)
		assertTrue(
			"val displayedSubtitle =" in item &&
				"aurralAlbumMatch?.artistName" in item,
			"Matched album cards should display the Aurral artist identity instead of always showing the Navidrome artist name."
		)
		assertTrue(
			"imageUrl = displayedImageUrl" in item &&
				"aurralAlbumMatch?.coverUrl" in item,
			"Matched album cards should render Aurral artwork through ArtGridItem's external image path."
		)
	}
}
