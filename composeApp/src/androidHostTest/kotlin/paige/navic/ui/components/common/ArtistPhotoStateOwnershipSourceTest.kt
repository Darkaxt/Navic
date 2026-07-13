package paige.navic.ui.components.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ArtistPhotoStateOwnershipSourceTest {
	@Test
	fun artistPhotoCacheUsesExplicitHotStoreInsteadOfCompositionLocalState() {
		val app = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/App.kt").readText()
		val artwork = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/PlaybackArtworkState.kt"
		).readText()
		val store = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/data/database/ArtistPhotoSnapshotStore.kt"
		).readText()

		assertFalse(app.contains("LocalArtistPhotoEntries"))
		assertFalse(artwork.contains("compositionLocalOf"))
		assertContains(artwork, "koinInject<ArtistPhotoSnapshotStore>()")
		assertContains(store, "stateIn(scope, SharingStarted.Lazily, emptyList())")
	}

	private fun sourceFile(path: String): File =
		listOf(File(path), File("../$path")).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}
