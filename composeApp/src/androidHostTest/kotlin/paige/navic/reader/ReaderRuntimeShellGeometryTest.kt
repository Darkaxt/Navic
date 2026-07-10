package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReaderRuntimeShellGeometryTest {
	private fun readerAssetText(fileName: String): String =
		readerAssetRoot().resolve(fileName).readText()

	@Test
	fun normalTextPagesDoNotUseSyntheticShellGeometry() {
		val helpers = readerAssetText("navic-reader-helpers.js")
		val viewport = readerAssetText("navic-reader-viewport.js")
		val root = readerAssetText("navic-reader.js")

		listOf(helpers, viewport, root).forEach { source ->
			assertFalse(
				source.contains("readerPageShellGeometry"),
				"Normal text pages must not route through readerPageShellGeometry; Foliate owns page geometry.",
			)
			assertFalse(
				source.contains("readerPageShellGeometryForViewport"),
				"Normal text pages must not derive renderer bounds from the synthetic book shell.",
			)
		}

		assertFalse(
			helpers.contains("ensureReaderStaticPaperShell"),
			"The static paper shell created the extra book-cover/page/body margin stack and must stay removed.",
		)
		assertFalse(
			helpers.contains("data-navic-static-paper-shell"),
			"Text pages must not render a fake static paper shell around the Foliate page.",
		)
	}

	@Test
	fun viewportKeepsFoliateAsTheLayoutSourceOfTruth() {
		val viewport = readerAssetText("navic-reader-viewport.js")

		assertContains(viewport, "readerAdaptiveFoliatePageBox")
		assertContains(viewport, "readerTopMarginValue")
		assertContains(viewport, "readerBottomMarginValue")
		assertContains(viewport, "readerSideMarginValue")
		assertContains(viewport, "readerAdaptiveFoliatePageBox({ width, height }, this.readerSettings)")
		assertContains(viewport, "inset: '0px'")

		assertFalse(
			viewport.contains("readerPageShellRectStyle"),
			"The viewport must not resize Foliate into a simulated shell rectangle.",
		)
		assertFalse(
			viewport.contains("navicReaderShellRect"),
			"Diagnostics must not expose shell rects for normal text pages.",
		)
		assertFalse(
			viewport.contains("applyReaderPageShellContentGeometry"),
			"Document content must not be rewritten to fit a synthetic shell.",
		)
	}

	@Test
	fun documentThemeDoesNotInjectShellContentMargins() {
		val appearance = readerAssetText("navic-reader-appearance.js")
		val typography = readerAssetText("navic-reader-typography.js")
		val root = readerAssetText("navic-reader.js")

		listOf(appearance, typography, root).forEach { source ->
			assertFalse(
				source.contains("data-navic-reader-shell-content"),
				"Reader documents must not get shell-content data attributes.",
			)
			assertFalse(
				source.contains("--navic-reader-shell-content"),
				"Reader documents must not receive shell-content CSS variables.",
			)
			assertFalse(
				source.contains("applyReaderPageShellContentGeometry"),
				"Reader documents must not have shell geometry injected.",
			)
		}
	}

	@Test
	fun paperEdgesAndStainsAreDecorativeOverlaysOnly() {
		val helpers = readerAssetText("navic-reader-helpers.js")

		assertContains(helpers, "updateReaderSurfaceTextureLayer")
		assertContains(helpers, "updateReaderSurfaceBorderOverlayLayer")
		assertContains(helpers, "updateReaderSurfaceStainOverlayLayer")

		listOf(
			"updateReaderSurfaceTextureLayer",
			"updateReaderSurfaceBorderOverlayLayer",
			"updateReaderSurfaceStainOverlayLayer",
		).forEach { functionName ->
			val functionSource = helpers.substringAfter("export const $functionName")
				.substringBefore("\nexport function ")
				.substringBefore("\nexport const ")
			assertFalse(
				functionSource.contains("readerPageShellGeometry"),
				"$functionName must decorate resolved reader surfaces without changing page geometry.",
			)
			assertFalse(
				functionSource.contains("readerPageShellRect"),
				"$functionName must not size itself from a synthetic shell rectangle.",
			)
			assertFalse(
				functionSource.contains("data-navic-static-paper-shell"),
				"$functionName must not render through the removed static shell.",
			)
		}
	}

	@Test
	fun coverBackdropRemainsCoverOnlyAndDoesNotCropTheForegroundCover() {
		val helpers = readerAssetText("navic-reader-helpers.js")
		val coverLayer = helpers.substringAfter("export const updateReaderShellCoverLayer")
			.substringBefore("\nexport function ")
			.substringBefore("\nexport const ")

		assertContains(coverLayer, "readerCoverBackdropEnabled(settings)")
		assertContains(coverLayer, "backdrop")
		assertContains(coverLayer, "'background-size': 'cover, cover'")
		assertContains(coverLayer, "image")
		assertContains(coverLayer, "'object-fit': 'contain'")
		assertContains(coverLayer, "width: '100%'")
		assertContains(coverLayer, "height: '100%'")

		assertFalse(
			coverLayer.contains("backCover"),
			"Cover pages may use a blurred backdrop, but must not inject the failed fake back-cover spread shell.",
		)
		assertFalse(
			coverLayer.contains("readerPageShellGeometryForViewport"),
			"Cover backdrop must not crop the foreground cover through shell geometry.",
		)
	}
}
