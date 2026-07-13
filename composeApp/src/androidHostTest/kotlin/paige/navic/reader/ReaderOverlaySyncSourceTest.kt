package paige.navic.reader

import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderOverlaySyncSourceTest {
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
	fun productionUsesOneOverlayStateAndCommandReducer() {
		val readerRoot = root.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader")
		val allReaderSources = readerRoot
			.listDirectoryEntries("*.kt")
			.sortedBy { it.fileName.toString() }
			.joinToString("\n") { source -> source.readText() }

		assertEquals(
			1,
			Regex("data class ReaderOverlaySyncState\\(").findAll(allReaderSources).count()
		)
		assertFalse(allReaderSources.contains("data class ReaderWhispersyncSyncState("))
		assertFalse(allReaderSources.contains("data class ReaderMediaOverlaySyncState("))
		assertFalse(allReaderSources.contains("data class ReaderReadaloudSyncState("))
		assertEquals(
			1,
			Regex("engineCommandKey = engineCommandKey \\+ 1L").findAll(allReaderSources).count()
		)
	}

	@Test
	fun bothProductionPathsUseTypedAdaptersAndOldReducersAreGone() {
		val readerRoot = root.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader")
		val whispersyncCoordinator = readerRoot.resolve("ReaderWhispersyncSyncCoordinator.kt").readText()
		val whispersyncAdapter = readerRoot.resolve("ReaderWhispersyncOverlaySyncAdapter.kt").readText()
		val mediaAdapter = readerRoot.resolve("ReaderMediaOverlaySyncAdapter.kt").readText()
		val readaloudHost = root
			.resolve("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderReadaloudRuntimeHost.android.kt")
			.readText()

		assertTrue(whispersyncCoordinator.contains("WhispersyncOverlaySyncAdapter(timeline)"))
		assertTrue(whispersyncCoordinator.contains("typealias ReaderWhispersyncSyncState = ReaderOverlaySyncState"))
		assertTrue(whispersyncAdapter.contains("class WhispersyncOverlaySyncAdapter("))
		assertTrue(mediaAdapter.contains("class MediaOverlaySyncAdapter("))
		assertTrue(mediaAdapter.contains("typealias ReaderReadaloudSyncState = ReaderOverlaySyncState"))
		assertTrue(mediaAdapter.contains("MediaOverlaySyncAdapter(plan, timeline)"))
		assertTrue(readaloudHost.contains("ReaderReadaloudSyncState("))
		assertFalse(readaloudHost.contains("ReaderMediaOverlaySyncState"))
		assertFalse(readerRoot.resolve("ReaderMediaOverlaySync.kt").exists())
		assertFalse(readerRoot.resolve("ReaderReadaloudSyncCoordinator.kt").exists())
	}
}
