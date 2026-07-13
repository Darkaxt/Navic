package paige.navic.reader

import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class ReaderWhispersyncDiagnosticsSourceTest {
	private val root = sequence {
		var candidate = Path("").toAbsolutePath()
		while (true) {
			yield(candidate)
			candidate = candidate.parent ?: break
		}
	}.first { candidate ->
		candidate.resolve("androidApp/build.gradle.kts").exists()
	}

	@Test
	fun whispersyncReaderAndPlaybackDecisionsStayLoggedForLiveDiagnosis() {
		val controller = root
			.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt")
			.readText()
		val syncCoordinator = root
			.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinator.kt")
			.readText()
		val reducer = root
			.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncReducer.kt")
			.readText()
		val readerScreen = root
			.resolve("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
			.readText()

		assertContainsAll(
			controller + reducer + syncCoordinator + readerScreen,
			"WhispersyncSyncLogTag",
			"Whispersync visible range",
			"Whispersync text point",
			"Whispersync playback position",
			"Whispersync apply overlay",
			"Whispersync audio seek dispatch",
			"Whispersync audio seek ignored",
			"source=",
			"audio=",
			"positionMs=",
			"textRange="
		)
	}

	private fun assertContainsAll(text: String, vararg needles: String) {
		val missing = needles.filterNot(text::contains)
		assertTrue(
			missing.isEmpty(),
			"Whispersync sync diagnostics are missing expected log markers: ${missing.joinToString()}"
		)
	}
}
