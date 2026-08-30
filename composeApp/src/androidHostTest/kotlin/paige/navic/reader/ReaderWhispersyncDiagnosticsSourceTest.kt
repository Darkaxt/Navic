package paige.navic.reader

import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
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
	fun whispersyncDiagnosticsExposeOnlyBoundedStateReasonsAndCounts() {
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
		val diagnostics = controller + reducer + syncCoordinator + readerScreen
		val whispersyncLogStatements = Regex(
			"Logger\\.(?:i|w|e)\\([\\s\\S]*?\\)",
			RegexOption.MULTILINE
		).findAll(diagnostics)
			.map { it.value }
			.filter { it.contains("Whispersync") }
			.joinToString("\n")

		assertContainsAll(
			diagnostics,
			"WhispersyncSyncLogTag",
			"WordSync boundary state=timeline",
			"WordSync boundary state=dispatch",
			"state=",
			"reason=",
			"command=",
			"mode=",
			"matched=",
			"active=",
			"count="
		)
		listOf(
			"audio=",
			"href=",
			"text=",
			"cue=",
			"textRange=",
			"textOffset=",
			"positionMs=",
			"progressTextEnd=",
			"clip=",
			"artifact=",
			"audiobook=",
			"bookFile=",
			"path=",
			"sidecarPath",
			"audioResource",
			"error"
		).forEach { protectedDiagnostic ->
			assertFalse(
				whispersyncLogStatements.contains(protectedDiagnostic),
				"Whispersync diagnostics must not expose protected content: $protectedDiagnostic"
			)
		}
		assertFalse(
			Regex("command=\\${'$'}command(?=[\\s\"])")
				.containsMatchIn(whispersyncLogStatements),
			"Whispersync diagnostics must not interpolate parameterized commands"
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
