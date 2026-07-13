package paige.navic.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AndroidMediaPlayerControllerLifecycleSourceTest {
	@Test
	fun mediaPlayerOwnsControllerFutureAndHandlesDisconnects() {
		val source = sourceFile("AndroidMediaControllerConnection.android.kt").readText()

		assertContains(source, "FutureConnectionOwner(")
		assertContains(source, "MediaController.Listener")
		assertContains(source, "owner.disconnect(controller)")
		assertContains(source, "owner.close()")
		assertFalse(source.contains("controllerFuture?.get()"))
	}

	@Test
	fun persistedUpcomingOrderIsAppliedBeforeShuffleIsEnabled() {
		val viewModelSource = sourceFile("AndroidPlaybackStateSynchronizer.android.kt").readText()
		val serviceSource = playbackServiceSourceFile().readText()

		assertContains(viewModelSource, "restoredShuffleOrder(")
		assertContains(viewModelSource, "PlaybackService.restoreShuffleOrder(")
		assertContains(serviceSource, "ShuffleOrder.DefaultShuffleOrder")
		assertContains(serviceSource, "ACTION_RESTORE_SHUFFLE_ORDER")
	}

	private fun sourceFile(name: String): File = listOf(
		File("src/androidMain/kotlin/paige/navic/shared/$name"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/$name")
	).firstOrNull { it.isFile }
		?: error("Unable to locate $name")

	private fun playbackServiceSourceFile(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate MediaPlayer.android.kt")
}
