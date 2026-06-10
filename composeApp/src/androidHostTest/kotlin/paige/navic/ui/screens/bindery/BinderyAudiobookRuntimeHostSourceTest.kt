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
	fun binderyAudiobookScreenPassesResolvedCoverToRuntimeHost() {
		val source = playerScreenSourceFile().readText()

		assertContains(source, "coverUrl = coverUrl")
		assertContains(source, "coverCacheKey = coverCacheKey")
		assertContains(source, "imageRequestHeaders = requestHeaders")
	}

	@Test
	fun sharedAudiobookManagerUsesReadaloudController() {
		val source = audiobookManagerSourceFile().readText()

		assertContains(source, "controller.load(playbackPlan, playWhenReady = playWhenReady)")
		assertContains(source, "controller.play()")
		assertContains(source, "controller.pause()")
	}

	@Test
	fun sharedAudiobookManagerPublishesCoverMetadata() {
		val source = audiobookManagerSourceFile().readText()

		assertContains(source, "activeCoverUrl")
		assertContains(source, "coverUrl = activeCoverUrl")
		assertContains(source, "coverCacheKey = activeCoverCacheKey")
		assertContains(source, "imageRequestHeaders = activeImageRequestHeaders")
	}

	private fun playerScreenSourceFile(): File = listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyAudiobookPlayerScreen.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyAudiobookPlayerScreen.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate BinderyAudiobookPlayerScreen.kt")

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
