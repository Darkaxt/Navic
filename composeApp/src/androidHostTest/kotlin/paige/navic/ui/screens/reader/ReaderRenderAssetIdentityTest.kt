package paige.navic.ui.screens.reader

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReaderRenderAssetIdentityTest {
	private fun digest(assets: Map<String, String>): String =
		readerRenderAssetDigest(assets.keys) { path ->
			ByteArrayInputStream(assets.getValue(path).encodeToByteArray())
		}

	private val baseline = mapOf(
		"reader/index.html" to "<html></html>",
		"reader/navic-reader-typography.js" to "export const a = 1",
		"reader/fonts/navic-opendyslexic-regular.otf" to "font-bytes"
	)

	@Test
	fun identicalAssetsOnAnotherInstallKeepTheSameToken() {
		assertEquals(digest(baseline), digest(baseline.toList().reversed().toMap()))
		assertTrue(digest(baseline).startsWith("assets-v1-"))
	}

	@Test
	fun anyAssetChangeRetiresTheToken() {
		val original = digest(baseline)
		val edited = baseline + ("reader/navic-reader-typography.js" to "export const a = 2")
		val added = baseline + ("reader/navic-reader-extra.css" to "")
		val removed = baseline - "reader/index.html"
		val renamed = (baseline - "reader/index.html") + ("reader/index2.html" to "<html></html>")
		listOf(edited, added, removed, renamed).forEach { changed ->
			assertNotEquals(original, digest(changed))
		}
	}

	@Test
	fun contentCannotShiftAcrossFileBoundaries() {
		assertNotEquals(
			digest(mapOf("reader/a" to "xy", "reader/b" to "z")),
			digest(mapOf("reader/a" to "x", "reader/b" to "yz"))
		)
	}
}
