package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPlayLikeCurlReferencePathSourceTest {
	@Test
	fun referencePortCannotDependOnTheFailedCustomRendererOrLeafProjection() {
		val referenceFiles = readerSourceRoot()
			.walkTopDown()
			.filter { file ->
				file.isFile &&
					file.extension == "kt" &&
					file.name.startsWith("ReaderPlayLikeCurl")
			}
			.toList()
		assertTrue(referenceFiles.isNotEmpty(), "The faithful PlayLikeCurl source path must exist")

		val forbidden = listOf(
			"ReaderPageCurlGlRenderer",
			"ReaderPageCurlLeafProjection",
			"ReaderPageCurlGeometry.forward",
			"ReaderPageCurlGeometry.backward"
		)
		for (file in referenceFiles) {
			val source = file.readText()
			for (symbol in forbidden) {
				assertFalse(
					source.contains(symbol),
					"${file.name} must not reuse failed-prototype symbol $symbol"
				)
			}
		}
	}

	private fun readerSourceRoot(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/ui/screens/reader"),
		File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader")
	).firstOrNull(File::isDirectory)
		?: error("Could not locate Android reader sources")
}
