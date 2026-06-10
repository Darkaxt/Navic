package paige.navic.ui.components.layouts

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class AudiobookMiniPlayerSourceTest {
	@Test
	fun audiobookMiniPlayerUsesSessionArtwork() {
		val source = audiobookMiniPlayerSourceFile().readText()

		assertContains(source, "AsyncImage(")
		assertContains(source, "state.coverUrl")
		assertContains(source, "state.coverCacheKey")
		assertContains(source, "val imageRequestHeaders = state.imageRequestHeaders")
		assertContains(source, "imageRequestHeaders.toNetworkHeaders()")
	}

	@Test
	fun audiobookMiniPlayerCentersDetachedContainerLikeMusicMiniPlayer() {
		val source = audiobookMiniPlayerSourceFile().readText()

		assertContains(source, "modifier = modifier.fillMaxWidth()")
		assertContains(source, "contentAlignment = Alignment.Center")
		assertContains(source, ".widthIn(max = if (detached) 600.dp else Dp.Unspecified)")
	}

	private fun audiobookMiniPlayerSourceFile(): File = listOf(
		File("src/commonMain/kotlin/paige/navic/ui/components/layouts/AudiobookMiniPlayer.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/components/layouts/AudiobookMiniPlayer.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate AudiobookMiniPlayer.kt")
}
