package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class ReaderWordSyncBoundaryRuntimeSourceTest {
	@Test
	fun readerUsesTimelineEventsAndExactBoundaryDispatchInsteadOfPositionPulseTiming() {
		val source = readerScreenSourceFile().readText()

		assertContains(source, "audiobookPlaybackManager.playbackTimelineRevision.collectAsState()")
		assertContains(source, "ReaderWordSyncBoundaryScheduler(")
		assertContains(source, "audiobookPlaybackManager.currentPlaybackTimelineSnapshot()")
		assertContains(source, "coordinator.wordSyncBoundaries(")
		assertContains(source, "coordinator.onWordSyncBoundary(dispatch)")
		assertContains(source, "publishOverlayProgress = wordSyncPublicationVerifier == null")
		assertContains(source, "DisposableEffect(wordSyncBoundaryScheduler)")
	}

	private fun readerScreenSourceFile(): File = listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate ReaderScreen.kt")
}
