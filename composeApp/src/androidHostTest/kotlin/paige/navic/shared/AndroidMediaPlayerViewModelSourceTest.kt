package paige.navic.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class AndroidMediaPlayerViewModelSourceTest {
	@Test
	fun mediaItemMetadataMappingLivesOutsideAndroidMediaPlayerViewModel() {
		val viewModel = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt")
		val mediaItemFactory = androidSharedSourceFile("AndroidMediaItemFactory.android.kt")
		val viewModelText = viewModel.readText()
		val factoryText = mediaItemFactory.readText()

		assertTrue(
			viewModel.readLines().size < 1_500,
			"AndroidMediaPlayerViewModel should not own Media3 metadata construction and stream/download URI selection."
		)
		assertContains(viewModelText, "private val mediaItemFactory = AndroidMediaItemFactory(")
		assertContains(viewModelText, "private fun DomainSong.toMediaItem(): MediaItem =")
		assertContains(factoryText, "internal class AndroidMediaItemFactory")
		assertContains(factoryText, "fun toMediaItem(song: DomainSong): MediaItem")
		assertContains(factoryText, "MediaMetadata.Builder()")
		assertContains(factoryText, "downloadManager.getDownloadedFilePath(id)")
	}
}

private fun androidSharedSourceFile(fileName: String): File =
	listOf(
		File("src/androidMain/kotlin/paige/navic/shared/$fileName"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/$fileName")
	).firstOrNull { it.isFile }
		?: error("Could not locate Android shared source file $fileName")
