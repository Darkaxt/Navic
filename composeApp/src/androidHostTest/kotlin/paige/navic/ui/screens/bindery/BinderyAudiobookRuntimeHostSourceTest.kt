package paige.navic.ui.screens.bindery

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class BinderyAudiobookRuntimeHostSourceTest {
	@Test
	fun binderyAudiobookHostAutostartsSharedManagerAndPublishesPositions() {
		val source = runtimeHostSourceFile().readText()

		assertContains(source, "playbackManager.load(")
		assertContains(source, "playWhenReady = true")
		assertContains(source, "currentOnPlaybackPosition.value(miniPlayerState.toReadaloudPlaybackPosition())")
	}

	@Test
	fun sharedAudiobookManagerUsesReadaloudController() {
		val source = audiobookManagerSourceFile().readText()

		assertContains(source, "controller.load(playbackPlan, playWhenReady = playWhenReady)")
		assertContains(source, "controller.play()")
		assertContains(source, "controller.pause()")
	}

	private fun runtimeHostSourceFile(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/ui/screens/bindery/BinderyAudiobookRuntimeHost.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/bindery/BinderyAudiobookRuntimeHost.android.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate BinderyAudiobookRuntimeHost.android.kt")

	private fun audiobookManagerSourceFile(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/shared/AndroidAudiobookPlaybackManager.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidAudiobookPlaybackManager.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate AndroidAudiobookPlaybackManager.kt")
}
