package paige.navic.ui.screens

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AurralAlbumRequestPropagationSourceTest {
	@Test
	fun searchAlbumResultsUseScreenLevelAurralRequests() {
		val source = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/search/SearchScreen.kt").readText()

		assertContains(source, "albumListViewModel.aurralAlbumRequests.collectAsState")
		assertFalse(
			source.contains("aurralAlbumRequests = emptyList()"),
			"Search album result tiles should show Aurral acquisition/request progress like album and Library rows."
		)
	}

	@Test
	fun collectionMoreByArtistRowsUseAurralRequests() {
		val screenSource =
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/CollectionDetailScreen.kt").readText()
		val rowSource =
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/components/MoreByArtistRow.kt").readText()

		assertContains(screenSource, "viewModel.aurralAlbumRequests.collectAsState")
		assertContains(screenSource, "aurralAlbumRequests =")
		assertContains(rowSource, "aurralAlbumRequests: List<AurralAlbumRequest>")
		assertContains(rowSource, "acquisitionProgress = aurralAlbumAcquisitionProgress")
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull { it.isFile }
			?: error("Could not locate $path")
}
