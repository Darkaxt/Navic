package paige.navic.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidMediaPlayerDecompositionSourceTest {
	@Test
	fun viewModelCoordinatesExtractedPlaybackResponsibilities() {
		val viewModel = source("AndroidMediaPlayerViewModel.android.kt").readText()
		assertTrue(viewModel.lines().size < 1_200, "Android media player ViewModel must remain below 1,200 lines")
		assertContains(viewModel, "AndroidMediaControllerConnection")
		assertContains(viewModel, "AndroidPlaybackStateSynchronizer")
		assertContains(viewModel, "AndroidDownloadedMediaRecovery")
		assertContains(viewModel, "AndroidAudioEffectsController")
		assertContains(viewModel, "AndroidBulkPlaybackCoordinator")
		assertContains(viewModel, "PlaybackQueueInteractor")
		assertFalse("private val playlistDao:" in viewModel)
		assertFalse("private val songDao:" in viewModel)
		assertFalse("private val songRepository:" in viewModel)
	}

	private fun source(name: String): File {
		val path = "composeApp/src/androidMain/kotlin/paige/navic/shared/$name"
		return listOf(File(path), File("../$path")).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
	}
}
