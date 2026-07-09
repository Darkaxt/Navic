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
		assertContains(helperText, "pageBoxWidth")
		assertContains(helperText, "pageBoxMaxColumnCount")
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

		assertContains(
			viewportLayout,
			"width: fixedLayout ? width : shellGeometry.renderer.pageBoxWidth",
			message = "main viewport layout must size reflowable Foliate columns from the readable page box, not the full decorative shell."
		)
		assertContains(
			profileLayout,
			"width: shellGeometry.renderer.pageBoxWidth",
			message = "profile viewport layout must size Foliate columns from the readable page box, not the full decorative shell."
		)

		for ((name, body) in listOf("main" to viewportLayout, "profile" to profileLayout)) {
			assertContains(
				body,
				"readerPageShellRectStyle(shellGeometry.shellRect)",
				message = "$name viewport layout must use the same shell rectangle as paper and gutter overlays."
			)
			assertContains(
				body,
				"maxColumnCount: shellGeometry.renderer.pageBoxMaxColumnCount",
				message = "$name viewport layout must preserve spread mode after switching page-box math to per-page content width."
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
		val typographyText = readerAssetRoot().resolve("navic-reader-typography.js").readText()
		val typographyCss = typographyText.substringAfter("export const readerTypographyCss = settings =>")
			.substringBefore("\n\nexport const readerParagraphSpacingCss")

		assertContains(bridgeText, "applyReaderPageShellContentGeometry")
		assertContains(bridgeText, "--navic-reader-shell-content-left")
		assertContains(bridgeText, "--navic-reader-shell-content-width")
		assertContains(bridgeText, "--navic-reader-shell-gutter-width")
		assertContains(bridgeText, "data-navic-reader-shell-content")
		assertContains(typographyCss, "html[data-navic-reader-shell-content=\"true\"] body")
		assertContains(typographyCss, "max-width: var(--navic-reader-shell-content-width")
		assertContains(typographyCss, "margin-inline: auto")
		assertContains(appearanceText, "this.readerPageShellGeometry")
		assertContains(appearanceText, "this.applyReaderPageShellContentGeometry(doc, settings, index)")
		assertContains(appearanceText, "this.applyThemeToLoadedContent(settings)")
	}

	@Test
	fun androidReaderShellContentDocumentsDoNotCoverPaperSurface() {
		val typographyText = readerAssetRoot().resolve("navic-reader-typography.js").readText()
		val documentThemeCss = typographyText.substringAfter("export const readerDocumentThemeCss = settings =>")
			.substringBefore("\n\nexport const readerContentCss")

		assertContains(documentThemeCss, "html[data-navic-reader-shell-content=\"true\"],")
		assertContains(documentThemeCss, "html[data-navic-reader-shell-content=\"true\"] body")
		assertContains(documentThemeCss, "background: transparent !important;")
		assertContains(documentThemeCss, "background-color: transparent !important;")
		assertContains(documentThemeCss, "background-image: none !important;")
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
	fun androidNativeShellCoverUsesCoverGeometryContract() {
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val shellCoverClass = nativeFrameHostText
			.substringAfter("private class KomikkuReaderNativeShellCoverView")
			.substringBefore("\nprivate fun Bitmap.readerDominantCoverColor")

		assertContains(
			nativeFrameHostText,
			"private data class NativeReaderShellCoverGeometry",
			message = "The native shell cover must resolve foreground, back-cover, and backdrop rects as one geometry contract."
		)
		assertContains(
			nativeFrameHostText,
			"resolveNativeReaderShellCoverGeometry(",
			message = "Native cover mode must use a named geometry resolver instead of inferring each layer separately."
		)
		assertContains(shellCoverClass, "val shellGeometry = resolveNativeReaderShellCoverGeometry(")
		assertContains(shellCoverClass, "drawDiffuseCoverBackdrop(canvas, currentBitmap, shellGeometry)")
		assertContains(shellCoverClass, "drawNativeBackCoverPlane(canvas, shellGeometry)")
		assertContains(shellCoverClass, "drawContainedNativeShellCover(canvas, currentBitmap, shellGeometry)")
		assertFalse(
			shellCoverClass.contains("val foregroundBounds = nativeShellCoverForegroundRect(currentBitmap)"),
			"Foreground, back-cover, and backdrop geometry must not be split across independent ad hoc calculations."
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

	@Test
	fun androidReaderMasksPageEdgeOverlayToEdgeBands() {
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val body = helperText.substringAfter("export const updateReaderSurfaceBorderOverlayLayer =")
			.substringBefore("\n\nexport const")

		assertContains(
			helperText,
			"readerPageEdgeOverlayMask",
			message = "Page edge overlays need a shared mask helper so edge assets cannot render as full-page panels."
		)
		assertContains(
			helperText,
			"geometry.edgeInsets",
			message = "The edge mask must come from shell geometry edge insets, not a hard-coded full-page overlay."
		)
		assertContains(
			body,
			"readerPageEdgeOverlayMask(page.page, geometry)",
			message = "Every settled/moving page edge overlay must be masked to the active page edge bands."
		)
		assertContains(body, "'-webkit-mask'")
		assertContains(body, "mask:")
	}
}
