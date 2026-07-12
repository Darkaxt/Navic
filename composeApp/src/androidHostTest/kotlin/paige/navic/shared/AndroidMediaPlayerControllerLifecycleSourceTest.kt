package paige.navic.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AndroidMediaPlayerControllerLifecycleSourceTest {
	@Test
	fun mediaPlayerOwnsControllerFutureAndHandlesDisconnects() {
		val source = sourceFile().readText()

		assertContains(source, "FutureConnectionOwner<MediaController>")
		assertContains(source, "MediaController.Listener")
		assertContains(source, "connectionOwner.disconnect(disconnectedController)")
		assertContains(source, "connectionOwner.close()")
		assertFalse(source.contains("controllerFuture?.get()"))
	}

	@Test
	fun persistedUpcomingOrderIsAppliedBeforeShuffleIsEnabled() {
		val viewModelSource = sourceFile().readText()
		val serviceSource = playbackServiceSourceFile().readText()

		assertContains(viewModelSource, "restoredShuffleOrder(")
		assertContains(viewModelSource, "PlaybackService.restoreShuffleOrder(")
		assertContains(serviceSource, "ShuffleOrder.DefaultShuffleOrder")
		assertContains(serviceSource, "ACTION_RESTORE_SHUFFLE_ORDER")
	}

	private fun sourceFile(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate AndroidMediaPlayerViewModel.android.kt")

	private fun playbackServiceSourceFile(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate MediaPlayer.android.kt")
}
