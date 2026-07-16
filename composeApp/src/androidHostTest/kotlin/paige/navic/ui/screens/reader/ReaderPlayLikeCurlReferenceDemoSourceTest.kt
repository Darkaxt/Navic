package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPlayLikeCurlReferenceDemoSourceTest {
	private val viewSource by lazy {
		repoFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlReferenceView.android.kt"
		).readText()
	}
	private val bitmapSource by lazy {
		repoFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlBitmapSource.android.kt"
		).readText()
	}
	private val deckFactorySource by lazy {
		repoFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlLibraryDeckFactory.android.kt"
		).readText()
	}

	@Test
	fun readerDevOwnsAStandaloneReferenceActivityAndOriginalTextureSet() {
		val activity = repoFile(
			"androidApp/src/readerDev/kotlin/paige/navic/androidApp/PlayLikeCurlReferenceActivity.kt"
		).readText()
		val manifest = repoFile("androidApp/src/readerDev/AndroidManifest.xml").readText()

		assertContains(activity, "ReaderPlayLikeCurlReferenceView")
		assertFalse(activity.contains("KomikkuReader"))
		assertFalse(activity.contains("Foliate"))
		assertContains(manifest, ".PlayLikeCurlReferenceActivity")
		assertContains(manifest, "android:exported=\"true\"")

		for (orientation in listOf("portrait", "landscape")) {
			val textureRoot = repoFile("androidApp/src/readerDev/assets/playlikecurl-reference/$orientation")
			val pages = textureRoot.listFiles()
				.orEmpty()
				.filter { it.isFile && it.extension == "png" }
				.sortedBy(File::getName)
			assertEquals((1..8).map { "page$it.png" }, pages.map(File::getName))
			assertTrue(pages.all { it.length() > 100_000L }, "Reference textures must not be placeholders")
		}
		assertTrue(repoFile("androidApp/src/readerDev/assets/playlikecurl-reference/LICENSE.txt").isFile)
	}

	@Test
	fun readerDevUsesTheImportedProductionSurfaceAndDeckContract() {
		assertContains(viewSource, "import karacken.curl.PageSurfaceView")
		assertContains(viewSource, ") : PageSurfaceView(context)")
		assertContains(viewSource, "readerPlayLikeCurlLibraryDeck")
		assertContains(deckFactorySource, "PortraitPageDeck")
		assertContains(deckFactorySource, "LandscapePageDeck")
		assertContains(viewSource, "PageSurfaceListener")
		assertContains(viewSource, "submitDeck")
		assertFalse(viewSource.contains("ReaderPlayLikeCurlReferenceModel"))
		assertFalse(viewSource.contains("ReaderPlayLikeCurlReferenceRenderer"))
		assertFalse(viewSource.contains("setRenderer("))
	}

	@Test
	fun importedSurfaceConsumesPreparedRasterDeckWithoutFrameDecodeOrUpload() {
		assertContains(viewSource, "ReaderPlayLikeCurlRasterAdapter")
		assertContains(viewSource, "interactionReady")
		assertContains(viewSource, "onDeckReleased")
		assertFalse(viewSource.contains("GLUtils.texImage2D"))
		assertFalse(viewSource.contains("BitmapFactory"))
		assertFalse(viewSource.contains("assets.open"))
	}

	@Test
	fun rasterPreparationIsBackgroundBoundedAndVisibleBeforeInteraction() {
		val activitySource = repoFile(
			"androidApp/src/readerDev/kotlin/paige/navic/androidApp/PlayLikeCurlReferenceActivity.kt"
		).readText()

		assertContains(bitmapSource, "withContext(Dispatchers.IO)")
		assertContains(bitmapSource, "ReaderPageBitmapQuality.Balanced")
		assertContains(viewSource, "SupervisorJob() + Dispatchers.Default")
		assertContains(viewSource, "override fun onDeckPrepared")
		assertFalse(viewSource.contains("override fun onTouchEvent"))
		assertContains(viewSource, "onPreparationProgress")
		assertContains(viewSource, "onPreparationCoverReady")
		assertContains(activitySource, "reader.resumeReference()")
		assertContains(activitySource, "reader.pauseReference()")
		assertContains(activitySource, "Preparing pages")
		assertContains(activitySource, "progressBarStyleHorizontal")
		assertFalse(bitmapSource.contains("withTimeout"))
		assertFalse(viewSource.contains("withTimeout"))
	}

	private fun repoFile(path: String): File = sequenceOf(
		File(path),
		File("..", path)
	).firstOrNull { it.exists() }
		?: error("Could not locate $path")
}
