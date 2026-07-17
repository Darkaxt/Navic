package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class ReaderPlayLikeCurlProductionBoundarySourceTest {
	@Test
	fun importedLibraryIsTheOnlyPageDeformationImplementation() {
		val sourceRoot = readerSourceRoot()
		val forbiddenFiles = setOf(
			"ReaderPageCurlGeometry.android.kt",
			"ReaderPageCurlGlRenderer.android.kt",
			"ReaderPageCurlGlView.android.kt",
			"ReaderPageTurnSlideView.android.kt",
			"ReaderPageTurnWaveGeometry.android.kt",
			"ReaderPlayLikeCurlReferenceModel.android.kt",
			"ReaderPlayLikeCurlReferenceRenderer.android.kt"
		)

		for (fileName in forbiddenFiles) {
			assertFalse(
				File(sourceRoot, fileName).exists(),
				"$fileName duplicates deformation behavior owned by the imported PlayLikeCurl library"
			)
		}
	}

	private fun readerSourceRoot(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/ui/screens/reader"),
		File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader")
	).firstOrNull(File::isDirectory)
		?: error("Could not locate Android reader sources")
}
