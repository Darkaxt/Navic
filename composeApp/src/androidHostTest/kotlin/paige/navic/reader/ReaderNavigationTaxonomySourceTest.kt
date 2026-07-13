package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReaderNavigationTaxonomySourceTest {
	@Test
	fun publicReaderBoundaryUsesPagedAndScrolledTaxonomy() {
		val boundary = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderNavigationMode.kt"
		).readText()
		val viewer = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewer.kt"
		).readText()
		val documentation = sourceFile("docs/architecture/reader-navigation-taxonomy.md").readText()

		assertContains(boundary, "enum class ReaderNavigationMode")
		assertContains(boundary, "Paged")
		assertContains(boundary, "Scrolled")
		assertContains(viewer, "private enum class ReaderViewerImplementation")
		assertFalse(viewer.contains("WebtoonPublicationReaderViewer"))
		assertContains(documentation, "Komikku-derived tap regions remain an input adapter")
	}

	private fun sourceFile(path: String): File =
		listOf(File(path), File("../$path")).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}
