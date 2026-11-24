package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertTrue

// A one-spread section (Author's Foreword) never re-expands after its overlayer attaches; the
// overlayer must still get expand()'s geometry or every highlight paints a page to the left.
class ReaderPaginatorOverlayerLayoutSourceTest {
	private val paginator = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()

	@Test
	fun overlayerAttachedAfterExpandReceivesTheCurrentGeometry() {
		val setter = paginator.substringAfter("set overlayer(overlayer) {").substringBefore("\n    }")
		assertTrue(
			setter.contains("this.#applyOverlayerLayout()"),
			"Attaching an overlayer must apply the last expand() geometry."
		)
		val expand = paginator.substringAfter("    expand() {").substringBefore("\n    #applyOverlayerLayout()")
		assertTrue(
			expand.contains("this.#overlayerLayout = {") && expand.contains("this.#applyOverlayerLayout()"),
			"expand() must record and apply the overlayer geometry."
		)
		val apply = paginator.substringAfter("    #applyOverlayerLayout() {").substringBefore("\n    }")
		assertTrue(
			apply.contains("style.left = layout.left") &&
				apply.contains("style[layout.side] = `\${layout.expandedSize}px`"),
			"The overlayer geometry must offset by the leading padding page and size to the content."
		)
	}

	// Chapter 1's illustration plate (#74) sits at the section start, inside the first highlight range.
	@Test
	fun highlightRectsSkipChapterIllustrationPlates() {
		val overlayer = readerAssetRoot().resolve("vendor/foliate-js/overlayer.js").readText()
		val rectsFor = overlayer.substringAfter("    #rectsFor(range) {").substringBefore("\n    }")
		assertTrue(
			rectsFor.contains("querySelectorAll?.('[data-navic-illustration]')") &&
				rectsFor.contains(".filter(rect => !insidePlate(rect))"),
			"Highlight rects inside an illustration plate must be dropped."
		)
		assertTrue(
			overlayer.split("const rects = this.#rectsFor(range)").size == 3,
			"add() and redraw() must both use the plate-aware rects."
		)
	}
}
