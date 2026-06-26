package paige.navic.ui.screens.album

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AurralAlbumQueueSourceTest {
	@Test
	fun acquisitionQueueMappingLivesInRepositoryLayerAndIsSharedByAlbumAndCollection() {
		val models = sourceFile("domain/repositories/AurralModels.kt").readText()
		val albumVm = sourceFile("ui/screens/album/viewmodels/AlbumListViewModel.kt").readText()
		val collectionVm = sourceFile("ui/screens/collection/viewmodels/CollectionDetailViewModel.kt").readText()

		assertTrue("fun AurralAcquisitionQueueItem.toAlbumRequest()" in models)
		assertFalse("private fun AurralAcquisitionQueueItem.toAlbumRequest()" in albumVm)
		assertFalse("private fun AurralAcquisitionQueueItem.toAlbumRequest()" in collectionVm)
		assertTrue("aurralRepository.albumRequests" in albumVm)
		assertTrue("aurralRepository.albumRequests" in collectionVm)
	}

	@Test
	fun albumGridReceivesDownloadOwnershipSnapshotWithoutPerCardCollection() {
		val albumScreen = sourceFile("ui/screens/album/AlbumListScreen.kt").readText()
		val content = sourceFile("ui/screens/album/components/Content.kt").readText()
		val item = sourceFile("ui/screens/album/components/Item.kt").readText()
		val viewModel = sourceFile("ui/screens/album/viewmodels/AlbumListViewModel.kt").readText()

		assertTrue("val albumDownloadOwnershipStatuses" in viewModel)
		assertTrue("val albumDownloadOwnershipStatuses by viewModel.albumDownloadOwnershipStatuses" in albumScreen)
		assertTrue("albumDownloadOwnershipStatuses: Map<String, AurralOwnershipStatus>" in content)
		assertTrue("ownershipStatus = albumDownloadOwnershipStatuses[album.id]" in content)
		assertTrue("ownershipStatus = ownershipStatus" in item)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("src/commonMain/kotlin/paige/navic/$path"),
			File("composeApp/src/commonMain/kotlin/paige/navic/$path"),
			File("../composeApp/src/commonMain/kotlin/paige/navic/$path")
		).first(File::exists)
}
