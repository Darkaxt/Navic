package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderRuntimeAssetsTest {
	@Test
	fun androidReaderAssetsPackageFoliateRuntimeAndNavicBridge() {
		val root = readerAssetRoot()
		val runtimeManifest = root.resolve("runtime.json")
		val index = root.resolve("index.html")
		val bridge = root.resolve("navic-reader.js")
		val foliatePackage = root.resolve("vendor/foliate-js/package.json")
		val foliateView = root.resolve("vendor/foliate-js/view.js")

		assertTrue(runtimeManifest.isFile, "reader runtime manifest must be packaged")
		assertTrue(index.isFile, "reader index.html must be packaged")
		assertTrue(bridge.isFile, "Navic reader bridge must be packaged")
		assertTrue(foliatePackage.isFile, "foliate-js package metadata must be packaged")
		assertTrue(foliateView.isFile, "foliate-js view runtime must be packaged")

		val manifestText = runtimeManifest.readText()
		assertContains(manifestText, "\"engine\": \"foliate-js\"")
		assertContains(manifestText, "\"version\": \"1.0.1\"")
		assertContains(manifestText, "\"entrypoint\": \"index.html\"")

		assertContains(index.readText(), "navic-reader.js")
		assertContains(bridge.readText(), "window.NavicReaderBridge")
		assertContains(bridge.readText(), "selectionChanged")
		assertContains(bridge.readText(), "applyOverlayFragment")
		assertContains(bridge.readText(), "applyHighlights")
		assertContains(bridge.readText(), "publicationReady")
		assertContains(bridge.readText(), "overlayFragmentActive")
		assertContains(bridge.readText(), "normalizeSearchResult")
		assertContains(bridge.readText(), "sectionTitle")
		assertContains(bridge.readText(), "postToc")
		assertContains(bridge.readText(), "flattenTocItems")
		assertContains(bridge.readText(), "type: 'toc'")
		assertContains(bridge.readText(), "margin-inline")
		assertContains(foliateView.readText(), "customElements.define('foliate-view'")
		assertContains(foliatePackage.readText(), "\"name\": \"foliate-js\"")
		assertContains(foliatePackage.readText(), "\"version\": \"1.0.1\"")
	}

	@Test
	fun androidRuntimeConstantsPointAtPackagedReaderEntrypoint() {
		assertEquals("reader/index.html", ReaderWebRuntime.AssetEntrypointPath)
		assertEquals("file:///android_asset/reader/index.html", ReaderWebRuntime.entrypointUrl)
		assertEquals("NavicAndroidBridge", ReaderWebRuntime.AndroidBridgeName)
		assertTrue(ReaderWebRuntime.LocalPublicationFileAccessEnabled)
	}

	private fun readerAssetRoot(): File =
		listOf(
			File("src/androidMain/assets/reader"),
			File("composeApp/src/androidMain/assets/reader")
		).firstOrNull { it.isDirectory }
			?: error("Could not locate Android reader assets")
}
