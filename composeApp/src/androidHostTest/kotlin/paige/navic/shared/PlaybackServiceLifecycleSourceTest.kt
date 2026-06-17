package paige.navic.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PlaybackServiceLifecycleSourceTest {
	@Test
	fun taskRemovalDoesNotDestroyMediaSessionServiceDirectly() {
		val source = playbackServiceSourceFile().readText()
		val onTaskRemovedBody = source.functionBody("onTaskRemoved")

		assertFalse(
			onTaskRemovedBody.contains("onDestroy()"),
			"PlaybackService.onTaskRemoved must not call onDestroy() directly because Android/Media3 owns service destruction."
		)
		assertContains(onTaskRemovedBody, "exoPlayer?.isPlaying != true")
		assertContains(onTaskRemovedBody, "stopSelf()")
	}

	@Test
	fun onDestroyDoesNotRequestServiceStopAgain() {
		val source = playbackServiceSourceFile().readText()
		val onDestroyBody = source.functionBody("onDestroy")

		assertFalse(
			onDestroyBody.contains("stopSelf()"),
			"PlaybackService.onDestroy is already running during service teardown and must not request another stop."
		)
	}

	private fun String.functionBody(functionName: String): String {
		val signature = "override fun $functionName("
		val start = indexOf(signature)
		require(start >= 0) { "Unable to locate $functionName" }
		val bodyStart = indexOf('{', start)
		require(bodyStart >= 0) { "Unable to locate $functionName body" }
		var depth = 0
		for (index in bodyStart until length) {
			when (this[index]) {
				'{' -> depth++
				'}' -> {
					depth--
					if (depth == 0) return substring(bodyStart + 1, index)
				}
			}
		}
		error("Unable to parse $functionName body")
	}

	private fun playbackServiceSourceFile(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate MediaPlayer.android.kt")
}
