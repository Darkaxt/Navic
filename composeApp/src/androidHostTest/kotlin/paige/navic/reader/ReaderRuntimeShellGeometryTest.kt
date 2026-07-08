package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimeShellGeometryTest {
	@Test
	fun androidReaderDefinesSharedPageShellGeometryModel() {
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()

		assertContains(helperText, "export const readerPageShellGeometry = (")
		assertContains(helperText, "ReaderPageShellDefaultOuterEdgePx")
		assertContains(helperText, "ReaderPageShellDefaultGutterPx")
		assertContains(helperText, "viewportRect")
		assertContains(helperText, "shellRect")
		assertContains(helperText, "pageRects")
		assertContains(helperText, "contentRects")
		assertContains(helperText, "gutterRect")
		assertContains(helperText, "cover: {")
		assertContains(helperText, "backdropRect")
		assertContains(helperText, "foregroundRect")
		assertContains(helperText, "backCoverRect")
		assertContains(helperText, "inner:")
		assertContains(helperText, "readerPageShellGeometryForViewport")
	}

	@Test
	fun androidReaderConstrainsFoliateRendererToSharedShellGeometry() {
		val viewportText = readerAssetRoot().resolve("navic-reader-viewport.js").readText()
		val viewportLayout = viewportText.substringAfter("function applyReaderViewportLayout")
			.substringBefore("\n\nfunction applyReaderViewportLayoutToProfilerView")
		val profileLayout = viewportText.substringAfter("function applyReaderViewportLayoutToProfilerView")
			.substringBefore("\n\nfunction applyPdfImageSettings")

		for ((name, body) in listOf("main" to viewportLayout, "profile" to profileLayout)) {
			assertContains(
				body,
				"readerPageShellRectStyle(shellGeometry.shellRect)",
				message = "$name viewport layout must use the same shell rectangle as paper and gutter overlays."
			)
			assertContains(
				body,
				"renderer.dataset.navicReaderShellRect",
				message = "$name viewport layout must expose the renderer shell rectangle for readerdev diagnostics."
			)
			assertFalse(
				body.contains("renderer.setAttribute('max-inline-size', pageBox.maxInlineSize)") &&
					body.contains("const pageBox = readerAdaptiveFoliatePageBox({ width, height }"),
				"$name viewport layout must not compute Foliate page-box sizing from the raw viewport after shell geometry is available."
			)
		}
	}

	@Test
	fun androidReaderSurfaceLayersConsumeSharedPageShellGeometry() {
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()

		for (functionName in listOf(
			"updateReaderSurfaceTextureLayer",
			"updateReaderSurfaceBorderOverlayLayer",
			"updateReaderSurfaceStainOverlayLayer",
			"updateReaderSurfaceSpreadGutterOverlayLayer",
			"updateReaderStaticPaperBackingLayer"
		)) {
			val body = helperText.substringAfter("export const $functionName =")
				.substringBefore("\n\nexport const")
			assertContains(
				body,
				"readerPageShellGeometryForViewport",
				message = "$functionName must render against the shared shell geometry, not raw viewport splits."
			)
			assertContains(
				body,
				"readerPageShellRectStyle",
				message = "$functionName must position artwork from geometry rects."
			)
		}

		assertFalse(
			helperText.contains("page.page === 'full' ? widthPx : '50%'"),
			"Surface overlays must not split spreads with hard-coded 50% page widths."
		)
		assertFalse(
			helperText.contains("page.page === 'right' ? '50%' : '0px'"),
			"Surface overlays must not place right pages with hard-coded 50% offsets."
		)
	}

	@Test
	fun androidReaderAppliesShellGeometryToContentDocuments() {
		val bridgeText = readerBridgeText()
		val appearanceText = readerAssetRoot().resolve("navic-reader-appearance.js").readText()

		assertContains(bridgeText, "applyReaderPageShellContentGeometry")
		assertContains(bridgeText, "--navic-reader-shell-content-left")
		assertContains(bridgeText, "--navic-reader-shell-content-width")
		assertContains(bridgeText, "--navic-reader-shell-gutter-width")
		assertContains(bridgeText, "data-navic-reader-shell-content")
		assertContains(appearanceText, "this.readerPageShellGeometry")
		assertContains(appearanceText, "this.applyReaderPageShellContentGeometry(doc, settings, index)")
		assertContains(appearanceText, "this.applyThemeToLoadedContent(settings)")
	}

	@Test
	fun androidReaderExposesShellGeometryDiagnostics() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "readerShellGeometryDiagnosticState")
		assertContains(bridgeText, "reader-shell-geometry")
		assertContains(bridgeText, "navicReaderShellGeometryMode")
		assertContains(bridgeText, "navicReaderShellGutterWidth")
		assertContains(bridgeText, "viewportRect")
		assertContains(bridgeText, "shellRect")
		assertContains(bridgeText, "contentRects")
		assertContains(bridgeText, "edgeInsets")
		assertContains(bridgeText, "cover")
	}

	@Test
	fun androidReaderShellCoverUsesBackdropBackCoverAndContainedForeground() {
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val body = helperText.substringAfter("export const updateReaderShellCoverLayer =")
			.substringBefore("\n\nexport const")

		assertContains(helperText, "ensureReaderShellCoverBackCover")
		assertContains(helperText, "data-navic-shell-cover-back-cover")
		assertContains(body, "readerPageShellGeometryForViewport")
		assertContains(body, "shellGeometry.cover?.backCoverRect")
		assertContains(body, "shellGeometry.cover?.foregroundRect")
		assertContains(body, "shellGeometry.cover?.backdropRect")
		assertContains(body, "object-fit")
		assertContains(body, "contain")
		assertFalse(
			body.contains("background: '#000000'"),
			"The cover shell should use the blurred/diffused cover backdrop, not a flat black stage."
		)
	}

	@Test
	fun androidReaderDoesNotRenderGutterLayerWithoutSpreadGutterRect() {
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val body = helperText.substringAfter("export const updateReaderSurfaceSpreadGutterOverlayLayer =")
			.substringBefore("\n\nexport const")

		assertContains(
			body,
			"if (!geometry.gutterRect) return",
			message = "Spread gutter artwork must not fall back to a 1x1 origin rectangle in single-page mode."
		)
	}
}
