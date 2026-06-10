package paige.navic.ui.screens.bindery

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class BinderyAudiobookPlayerScreenSourceTest {
	@Test
	fun audiobookProgressUsesNowPlayingStyledSlider() {
		val source = playerScreenSourceFile().readText()

		assertContains(source, "PlaybackProgressSlider(")
		assertContains(source, "isPlaying = state.isPlaying")
		assertContains(source, "onValueChange = { progress ->")
	}

	private fun playerScreenSourceFile(): File = listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyAudiobookPlayerScreen.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyAudiobookPlayerScreen.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate BinderyAudiobookPlayerScreen.kt")
}
