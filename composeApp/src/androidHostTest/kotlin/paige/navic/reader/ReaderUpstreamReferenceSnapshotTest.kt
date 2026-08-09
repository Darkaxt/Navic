package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ReaderUpstreamReferenceSnapshotTest {
	@Test
	fun snapshotsDeclarePinnedSourceAndLicense() {
		val anxProvenance = readerUpstreamReferenceText("anx-reader", "PROVENANCE.md")
		val komikkuProvenance = readerUpstreamReferenceText("komikku", "PROVENANCE.md")

		assertContains(anxProvenance, "https://github.com/Anxcye/anx-reader")
		assertContains(anxProvenance, "107f4fa74db0e7247c846c49d6211df3edf9887c")
		assertContains(anxProvenance, "MIT")
		assertContains(komikkuProvenance, "https://github.com/komikku-app/komikku")
		assertContains(komikkuProvenance, "3b06366fd979e7983cd42e3e092608411b70cff3")
		assertContains(komikkuProvenance, "Apache-2.0")
		assertTrue(readerUpstreamReferenceText("anx-reader", "LICENSE").isNotBlank())
		assertTrue(readerUpstreamReferenceText("komikku", "LICENSE").isNotBlank())
	}

	@Test
	fun snapshotsExposeEveryReaderParitySourceWithoutExternalCheckouts() {
		val anxSources = listOf(
			"lib/page/book_player/epub_player.dart",
			"assets/foliate-js/src/view.js",
			"lib/models/book_style.dart",
			"assets/foliate-js/src/footnotes.js",
			"assets/foliate-js/src/book.js",
			"assets/foliate-js/src/pdf.js",
			"assets/foliate-js/src/paginator.js",
			"lib/providers/fonts.dart",
			"lib/service/font.dart",
			"lib/models/font_model.dart"
		)
		val komikkuSources = listOf(
			"app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt",
			"app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/Viewer.kt",
			"app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt",
			"app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReadingMode.kt",
			"app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderBottomButton.kt",
			"app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt",
			"app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderTopBar.kt",
			"app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt",
			"app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt",
			"app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt",
			"app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt",
			"app/src/main/java/eu/kanade/presentation/reader/settings/ColorFilterPage.kt",
			"app/src/main/java/eu/kanade/presentation/reader/ChapterListDialog.kt",
			"app/src/main/java/eu/kanade/presentation/components/AppBar.kt",
			"app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt",
			"app/src/main/java/eu/kanade/presentation/components/AdaptiveSheet.kt",
			"presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Slider.kt",
			"presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt",
			"presentation-core/src/main/java/tachiyomi/presentation/core/components/AdaptiveSheet.kt"
		)

		for (relativePath in anxSources) {
			assertTrue(
				readerUpstreamReferenceText("anx-reader", relativePath).isNotBlank(),
				"Missing packaged Anx reader reference: $relativePath"
			)
		}
		for (relativePath in komikkuSources) {
			assertTrue(
				readerUpstreamReferenceText("komikku", relativePath).isNotBlank(),
				"Missing packaged Komikku reader reference: $relativePath"
			)
		}
		assertContains(
			readerUpstreamReferenceText("anx-reader", "assets/foliate-js/src/paginator.js"),
			"const getVisibleRange"
		)
		assertContains(
			readerUpstreamReferenceText(
				"komikku",
				"app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt"
			),
			"ReaderPreferences.TapZones.mapIndexed"
		)
	}
}
