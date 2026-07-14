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
	private val rendererSource by lazy {
		repoFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlReferenceRenderer.android.kt"
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
	fun gles2ReferencePathUsesThreePersistentPagesAndOriginalDrawOrder() {
		assertContains(viewSource, "setEGLContextClientVersion(2)")
		assertContains(viewSource, "ReaderPlayLikeCurlReferenceModel")
		assertContains(rendererSource, "GLES20.GL_DEPTH_TEST")
		assertContains(rendererSource, "Matrix.perspectiveM")
		assertContains(rendererSource, "45f")
		assertContains(rendererSource, "leftPage")
		assertContains(rendererSource, "frontPage")
		assertContains(rendererSource, "rightPage")

		val draw = rendererSource.substringAfter("override fun onDrawFrame")
		assertTrue(draw.indexOf("drawPage(leftPage") < draw.indexOf("drawPage(frontPage"))
		assertTrue(draw.indexOf("drawPage(frontPage") < draw.indexOf("drawPage(rightPage"))
		assertFalse(rendererSource.contains("ReaderPageCurlLeafProjection"))
		assertFalse(rendererSource.contains("ReaderPageCurlGlRenderer"))
	}

	private fun repoFile(path: String): File = sequenceOf(
		File(path),
		File("..", path)
	).firstOrNull { it.exists() }
		?: error("Could not locate $path")
}
