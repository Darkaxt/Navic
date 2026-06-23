package paige.navic.ui.screens.album

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AlbumListViewModelSourceTest {
	@Test
	fun refreshAlbumsUsesSingleActiveAlbumFlowCollector() {
		val source = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/album/viewmodels/AlbumListViewModel.kt"
		).readText()

		assertTrue(
			"private var refreshAlbumsJob: Job? = null" in source,
			"Album refresh must keep the active flow collector so repeated boot/tab refreshes do not accumulate loaders."
		)
		assertTrue(
			"refreshAlbumsJob?.cancel()" in source,
			"Album refresh must cancel the previous album-flow collector before starting another one."
		)
		assertTrue(
			"refreshAlbumsJob = viewModelScope.launch" in source,
			"Album refresh must assign the launched collector job to the single-flight slot."
		)
	}
}
