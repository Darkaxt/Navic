package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderBinderyLocalizationBoundarySourceTest {
	@Test
	fun whispersyncDomainStateCarriesTypedMessagesInsteadOfVisibleEnglish() {
		val coordinator = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinator.kt"
		).readText()
		val playbackPolicy = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncPlaybackPolicy.kt"
		).readText()

		assertTrue(coordinator.contains("val message: ReaderWhispersyncStatusMessage?"))
		assertFalse(coordinator.contains("val label: String?"))
		assertTrue(playbackPolicy.contains("val contentDescription: ReaderWhispersyncPlaybackControlDescription"))
		assertFalse(playbackPolicy.contains("val contentDescription: String"))
	}

	@Test
	fun scopedReaderAndBinderySurfacesUseComposeResources() {
		val paths = listOf(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt",
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncSyncCoordinator.kt",
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderWhispersyncPlaybackPolicy.kt",
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt",
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncStatusBadge.kt",
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncPlayerDialog.kt",
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyHubScreen.kt",
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookScreen.kt",
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyAudiobookDetailScreen.kt"
		)
		val visibleLiterals = listOf(
			"Whispersync paused",
			"No synced text here",
			"Whispersync playing",
			"Syncing audiobook",
			"End of visible page",
			"Whispersync unavailable",
			"Whispersync audio unavailable",
			"Whispersync audiobook loading",
			"Reset Whispersync audiobook",
			"Play Whispersync audiobook",
			"Open with Whispersync?",
			"Ebook only",
			"Whispersync matches",
			"Open with audiobook",
			"Narrated by $",
			"Seek back 10 seconds",
			"Seek forward 10 seconds",
			"Pause audiobook",
			"Audio not loaded",
			"Following the ebook page",
			"Playback detached from page turns"
		)

		paths.forEach { path ->
			val source = sourceFile(path).readText()
			visibleLiterals.forEach { literal ->
				assertFalse(source.contains(literal), "$path must resolve '$literal' through Compose resources")
			}
		}
	}

	private fun sourceFile(path: String): File =
		listOf(File("../$path"), File(path)).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}
