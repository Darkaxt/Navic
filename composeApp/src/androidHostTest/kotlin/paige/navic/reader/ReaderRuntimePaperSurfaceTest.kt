package paige.navic.reader

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimePaperSurfaceTest {
	@Test
	fun androidReaderPackagesDeterministicPaperTextureVariants() {
		val root = readerAssetRoot()
		val bridgeText = readerBridgeText(root)
		val textures = (1..9).map { index ->
			root.resolve("paper-textures/paper-texture-${index.toString().padStart(2, '0')}.jpg")
		}

		assertEquals(9, textures.size, "Reader paper texture pack should expose nine cropped source regions")
		textures.forEachIndexed { index, texture ->
			val label = index + 1
			assertTrue(texture.isFile, "Reader paper texture $label must be packaged")
			assertTrue(texture.length() > 1_500_000, "Reader paper texture $label should be a real 4K-class image")
			val stats = texture.sampledLuminanceStats()
			assertTrue(stats.width >= 2700, "Reader paper texture $label should preserve realistic pore scale on tablet screens")
			assertTrue(stats.height >= 3800, "Reader paper texture $label should preserve realistic pore scale on tablet screens")
			assertTrue(stats.average in 205.0..240.0, "Reader paper texture $label should stay neutral/light for theme tinting")
			assertTrue(stats.standardDeviation >= 2.5, "Reader paper texture $label must contain visible paper fibers/pores")
		}
		assertContains(bridgeText, "ReaderPaperTextureAssets")
		textures.forEach { texture ->
			assertContains(bridgeText, "paper-textures/${texture.name}")
		}
		assertContains(bridgeText, "ReaderPaperTextureVariantCount = ReaderPaperTextureAssets.length * 2 * 2")
		assertContains(bridgeText, "readerPaperTextureVariantKey")
		assertContains(bridgeText, "readerPaperTextureVariantForPage")
		assertContains(bridgeText, "textureIndex")
		assertContains(bridgeText, "rotate180")
		assertContains(bridgeText, "mirrored")
		assertContains(bridgeText, "scaleX(-1)")
		assertContains(bridgeText, "rotate(180deg)")
		assertContains(bridgeText, "'pointer-events': 'none'")
		assertContains(bridgeText, "ReaderSurfacePaperTextureLayerSelector")
		assertContains(bridgeText, "readerSurfacePaperTextureOpacity")
		assertContains(bridgeText, "surfaceTextureOpacity=\${")
		assertContains(bridgeText, "surfaceTextureImage=\${")
		assertFalse(
			bridgeText.contains("ReaderPaperTextureLayerSelector = '[data-navic-paper-texture-layer=\"true\"]'"),
			"Document-scoped texture layers cause opacity stacking across rendered elements."
		)
	}

	@Test
	fun androidReaderPackagesDeterministicPaperBorderOverlayVariants() {
		val root = readerAssetRoot()
		val bridgeText = readerBridgeText(root)
		val overlay1 = root.resolve("paper-textures/page-border-overlay-1.png")
		val overlay2 = root.resolve("paper-textures/page-border-overlay-2.png")
		val overlay3 = root.resolve("paper-textures/page-border-overlay-3.png")
		val overlay4 = root.resolve("paper-textures/page-border-overlay-4.png")

		assertTrue(overlay1.isFile, "Reader page border overlay 1 must be packaged")
		assertTrue(overlay2.isFile, "Reader page border overlay 2 must be packaged")
		assertTrue(overlay3.isFile, "Reader page border overlay 3 must be packaged")
		assertTrue(overlay4.isFile, "Reader page border overlay 4 must be packaged")
		assertTrue(overlay1.length() > 1_000, "Reader page border overlay 1 should be a real image")
		assertTrue(overlay2.length() > 1_000, "Reader page border overlay 2 should be a real image")
		assertTrue(overlay3.length() > 1_000, "Reader page border overlay 3 should be a real image")
		assertTrue(overlay4.length() > 1_000, "Reader page border overlay 4 should be a real image")
		assertTrue(overlay1.hasPngAlphaChannel(), "Reader page border overlay 1 must be transparent")
		assertTrue(overlay2.hasPngAlphaChannel(), "Reader page border overlay 2 must be transparent")
		assertTrue(overlay3.hasPngAlphaChannel(), "Reader page border overlay 3 must be transparent")
		assertTrue(overlay4.hasPngAlphaChannel(), "Reader page border overlay 4 must be transparent")
		assertTrue(overlay1.averagePngAlpha() >= 2.0, "Reader page border overlay 1 must be visible at runtime")
		assertTrue(overlay2.averagePngAlpha() >= 2.0, "Reader page border overlay 2 must be visible at runtime")
		assertTrue(overlay3.averagePngAlpha() >= 2.0, "Reader page border overlay 3 must be visible at runtime")
		assertTrue(overlay4.averagePngAlpha() >= 2.0, "Reader page border overlay 4 must be visible at runtime")
		listOf(overlay1, overlay2, overlay3, overlay4).forEachIndexed { index, overlay ->
			assertTrue(
				overlay.outerEdgeAlphaHighFrequencyPercent() <= 0.5,
				"Reader page border overlay ${index + 1} must not contain baked checkerboard artifacts"
			)
			assertTrue(
				overlay.maxPngAlpha() <= 70,
				"Reader page border overlay ${index + 1} should be subtle page-edge degradation"
			)
		}
		assertContains(bridgeText, "ReaderPageBorderOverlayAssets")
		assertContains(bridgeText, "paper-textures/page-border-overlay-1.png")
		assertContains(bridgeText, "paper-textures/page-border-overlay-2.png")
		assertContains(bridgeText, "paper-textures/page-border-overlay-3.png")
		assertContains(bridgeText, "paper-textures/page-border-overlay-4.png")
		assertContains(bridgeText, "ReaderPageBorderOverlayVariantCount = ReaderPageBorderOverlayAssets.length * 2 * 2")
		assertContains(bridgeText, "readerPageBorderOverlayVariantForPage")
		assertContains(bridgeText, "ReaderSurfacePageBorderOverlayLayerSelector")
		assertContains(bridgeText, "ensureReaderSurfaceBorderOverlayLayer")
		assertContains(bridgeText, "updateReaderSurfaceBorderOverlayLayer")
		assertContains(bridgeText, "surfaceBorderOverlayAsset")
	}

	@Test
	fun androidReaderKeepsPaperTextureVisibleEnoughForSepiaTheme() {
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val textureOpacity = helperText
			.substringAfter("export const readerSurfacePaperTextureOpacity = settings =>")
			.substringBefore("\n\nexport const readerSurfacePageBorderOverlayOpacity")
		val borderOpacity = helperText
			.substringAfter("export const readerSurfacePageBorderOverlayOpacity = settings =>")
			.substringBefore("\n\nexport const readerSurfacePageBorderOverlayFilter")
		val borderBackgroundImage = helperText
			.substringAfter("export const readerSurfacePageBorderOverlayBackgroundImage = borderOverlayVariant =>")
			.substringBefore("\n\nexport const readerPageNumberPageCount")
		val borderUpdater = helperText
			.substringAfter("export const updateReaderSurfaceBorderOverlayLayer = (layer, borderOverlayVariant, settings, scrollOffset = null) =>")
			.substringBefore("\n\nexport const readerPageNumberLayerStyle")

		assertContains(textureOpacity, "case ReaderThemeSepia:")
		assertContains(textureOpacity, "return '0.46'")
		assertContains(
			textureOpacity,
			"return '0.28'",
			message = "The root surface texture is the single full-window paper owner, but it must remain visible enough to show page movement."
		)
		assertFalse(
			textureOpacity.contains("return '0.66'"),
			"The old high-opacity root overlay caused visible transition artifacts because it was animated independently from Foliate pages."
		)
		assertFalse(helperText.contains("readerDocumentPaperTextureBackground"))
		assertFalse(helperText.contains("updateReaderDocumentPaperTexture"))
		assertContains(helperText, "readerSurfacePageBorderOverlayBackgroundImage")
		assertContains(
			borderOpacity,
			"return '1'",
			message = "Border overlay PNGs have low source alpha, so the single border layer needs enough opacity to be visible over paper texture."
		)
		assertContains(borderUpdater, "for (const slot of borderOverlaySlots)")
		assertContains(borderUpdater, "'background-image': readerSurfacePageBorderOverlayBackgroundImage(slot.variant)")
		assertContains(
			borderBackgroundImage,
			"[textureUrl, textureUrl, textureUrl, textureUrl].join(', ')",
			message = "The edge degradation source PNGs have intentionally subtle alpha; compositing four times keeps one surface layer while making the borders visible."
		)
		assertContains(borderUpdater, "filter: readerSurfacePageBorderOverlayFilter(settings)")
		assertContains(helperText, "export const readerSurfacePageBorderOverlayFilter = settings =>")
		assertContains(helperText, "contrast(1.9) saturate(1.16) brightness(0.94)")
	}

	@Test
	fun androidReaderAppliesParagraphSpacingAsElementStyles() {
		val bridgeText = readerBridgeText()
		val paragraphSpacing = bridgeText
			.substringAfter("const applyReaderParagraphSpacing = (doc, settings) =>")
			.substringBefore("\n\nconst ensureReaderSurfaceTextureLayer")
		val paragraphSpacingCss = bridgeText
			.substringAfter("const readerParagraphSpacingCss = settings =>")
			.substringBefore("\n\nconst isThemeBackgroundMediaElement")
		val applyDocumentTheme = bridgeText
			.substringAfter("applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {")
			.substringBefore("\nfunction currentRendererContainerPosition")

		assertContains(paragraphSpacing, "const spacing = readerParagraphSpacingEm(settings)")
		assertContains(paragraphSpacing, "doc?.querySelectorAll?.('p,[data-navic-paragraph-block=\"true\"]')")
		assertContains(paragraphSpacing, "const blocks = Array.from")
		assertContains(paragraphSpacing, "'display': 'block'")
		assertContains(paragraphSpacing, "'margin-block-end': spacing")
		assertContains(paragraphSpacing, "'margin-block-start': '0'")
		assertContains(paragraphSpacing, "'padding-block-end': '0'")
		assertContains(paragraphSpacing, "'margin-bottom': spacing")
		assertContains(paragraphSpacingCss, "margin-block-end: var(--reader-paragraph-spacing")
		assertContains(paragraphSpacingCss, "margin-bottom: var(--reader-paragraph-spacing")
		assertFalse(
			paragraphSpacingCss.contains("html body p::after"),
			"Paragraph spacing must use real element margins because paginated EPUB layout can ignore pseudo-element spacing."
		)
		assertContains(applyDocumentTheme, "applyReaderParagraphSpacing(doc, settings)")
	}

	@Test
	fun androidReaderUsesStaticBackingAndMovingPagePaperTextureOwners() {
		val bridgeText = readerBridgeText()
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val appearanceText = readerAssetRoot().resolve("navic-reader-appearance.js").readText()
		val indexText = readerAssetRoot().resolve("index.html").readText()
		val surfaceTextureUpdater = appearanceText
			.substringAfter("function applySurfacePaperTextureUpdate(detail = {}, pagePosition = null)")
			.substringBefore("\nfunction shouldDeferSurfacePaperTextureUpdate")
		val surfaceLayerUpdater = helperText
			.substringAfter("export const updateReaderSurfaceTextureLayer = (layer, textureVariant, settings, scrollOffset = null) =>")
			.substringBefore("\n\nexport const updateReaderSurfaceBorderOverlayLayer")
		val applySettings = bridgeText
			.substringAfter("applySettings(settings) {")
			.substringBefore("\n  applyThemeToLoadedContent")
		val onLoad = bridgeText
			.substringAfter("onLoad(detail = {}) {")
			.substringBefore("\n  logContentLayout")
		val onRelocate = bridgeText
			.substringAfter("onRelocate(detail) {")
			.substringBefore("\n  attachContentDocumentBehaviors")

		assertContains(bridgeText, "ReaderSurfacePaperTextureLayerSelector")
		assertContains(bridgeText, "ensureReaderSurfaceTextureLayer")
		assertContains(bridgeText, "updateReaderStaticPaperBackingLayer")
		assertContains(bridgeText, "updateReaderMovingPageTextureLayer")
		assertContains(bridgeText, "readerRoot.append(layer)")
		assertContains(bridgeText, "data-navic-surface-paper-texture-layer")
		assertContains(bridgeText, "readerSurfacePaperTextureOpacity")
		assertContains(bridgeText, "this.updateSurfacePaperTexture")
		assertFalse(
			bridgeText.contains("applySurfacePaperTextureToDocuments") ||
				bridgeText.contains("this.applyDocumentPaperTexture(doc)") ||
				bridgeText.contains("updateReaderDocumentPaperTexture"),
			"Paper texture must stay out of EPUB documents. The moving page texture is the root-document paper bitmap owner."
		)
		assertFalse(
			surfaceTextureUpdater.contains("if (this.view?.isFixedLayout !== true)"),
			"The paper texture must cover the reader window for EPUB and fixed-layout content."
		)
		assertContains(surfaceTextureUpdater, "readerRoot.dataset.navicSurfacePaperTextureAsset")
		assertContains(
			surfaceLayerUpdater,
			"readerPaperTextureBackgroundImage(slot.variant)",
			message = "The moving page texture owner carries current and adjacent page slots."
		)
		assertContains(indexText, "body > foliate-view")
		assertContains(indexText, "z-index: 1;")
		assertContains(indexText, "background: transparent;")
		assertContains(
			bridgeText,
			"'z-index': '2147483630'",
			message = "The moving page texture surface must sit above Foliate content but below chrome, so it travels with page content without intercepting touches."
		)
		assertContains(
			bridgeText,
			"'z-index': '0'",
			message = "The static backing belongs behind Foliate so it covers margins/fallback without duplicating the moving paper texture."
		)
		assertContains(surfaceLayerUpdater, "readerSurfaceTextureSlotTransform")
		assertContains(surfaceLayerUpdater, "readerPaperTextureBackgroundPosition(null)")
		assertContains(applySettings, "this.renderSurfacePaperTextureLayers()")
		assertContains(onLoad, "this.updateReaderPageNumberLayer()")
		assertContains(
			bridgeText,
			"this.updateSurfacePaperTexture(detail, pagePosition, reason)",
			message = "Committed location posts should refresh the surface texture with the same page position and reason used for numbering."
		)
		assertContains(bridgeText, "position: 'fixed'")
		assertContains(bridgeText, "'pointer-events': 'none'")
	}

	@Test
	fun androidReaderSplitsStaticMarginPaperFromMovingPageTextureOwner() {
		val bridgeText = readerBridgeText()
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val appearanceText = readerAssetRoot().resolve("navic-reader-appearance.js").readText()
		val pageTurnText = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val settingsText = readerAssetRoot().resolve("navic-reader-settings-core.js").readText()

		assertContains(settingsText, "ReaderMovingPagePaperTextureLayerSelector")
		assertContains(settingsText, "ReaderMovingPageBorderOverlayLayerSelector")
		assertContains(helperText, "ensureReaderMovingPageTextureLayer")
		assertContains(helperText, "ensureReaderMovingPageBorderOverlayLayer")
		assertContains(helperText, "updateReaderMovingPageTextureLayer")
		assertContains(helperText, "updateReaderMovingPageBorderOverlayLayer")
		assertContains(helperText, "dataset.navicMovingPagePaperTextureLayer")
		assertContains(helperText, "dataset.navicMovingPageBorderOverlayLayer")
		assertContains(helperText, "'z-index': '2147483630'")
		assertContains(helperText, "'z-index': '0'")
		assertContains(
			helperText,
			"updateReaderStaticPaperBackingLayer",
			message = "The full-window backing must remain for margins/fallback while moving page texture is owned separately."
		)
		val staticBacking = helperText
			.substringAfter("export const updateReaderStaticPaperBackingLayer")
			.substringBefore("\n\nexport const updateReaderSurfaceTextureLayer")
		assertContains(
			staticBacking,
			"'background-image': 'none'",
			message = "The static backing must be color-only; paper bitmaps belong to the moving page texture slots."
		)
		assertFalse(
			staticBacking.contains("readerPaperTextureBackgroundImage("),
			"The static backing must not apply the same paper bitmap a second time."
		)
		assertContains(
			appearanceText,
			"this.movingPageTextureLayer",
			message = "Appearance updates must render a moving page-paper owner, not only mutate the static root paper backing."
		)
		assertContains(appearanceText, "updateReaderMovingPageTextureLayer(")
		assertContains(appearanceText, "updateReaderStaticPaperBackingLayer(")
		assertContains(pageTurnText, "syncPageDragPreviewTextureLayers")
		assertContains(pageTurnText, "pageDragPreviewTextureScrollOffset")
		assertContains(pageTurnText, "this.syncPageDragPreviewTextureLayers(layer, previewTextureScrollOffset)")
		assertFalse(
			pageTurnText.contains("renderer.scrollBy(-incrementalDelta.x, -incrementalDelta.y)"),
			"Drag previews must not move the committed Foliate renderer; texture movement belongs to the visual preview layer."
		)
	}

	@Test
	fun androidReaderPageDragPreviewCarriesTheSamePaperAndBorderTextureSurface() {
		val pageTurnText = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val previewUpdater = pageTurnText
			.substringAfter("function updatePageDragPreviewLayer({ direction, deltaX, deltaY, viewWidth, viewHeight, renderer }) {")
			.substringBefore("\nfunction previewPageDrag(command)")

		assertContains(pageTurnText, "ensurePageDragPreviewTextureLayers")
		assertContains(pageTurnText, "syncPageDragPreviewTextureLayers")
		assertContains(previewUpdater, "const previewTextureScrollOffset = pageDragPreviewTextureScrollOffset")
		assertContains(previewUpdater, "this.syncPageDragPreviewTextureLayers(layer, previewTextureScrollOffset)")
		assertContains(
			pageTurnText,
			"updateReaderMovingPageTextureLayer(",
			message = "The page-drag preview sits above the static backing; it must reuse the moving page texture updater instead of exposing a flat theme background."
		)
		assertContains(pageTurnText, "updateReaderMovingPageBorderOverlayLayer(")
		assertContains(pageTurnText, "data-navic-page-drag-preview-paper-layer")
		assertContains(pageTurnText, "data-navic-page-drag-preview-border-layer")
		assertTrue(
			previewUpdater.indexOf("this.syncPageDragPreviewTextureLayers(layer, previewTextureScrollOffset)") <
				previewUpdater.indexOf("if (!ready)"),
			"Texture surfaces must be attached even during the not-ready preview fallback; otherwise boundary drags reveal an untextured or black void until the adjacent page iframe loads."
		)
		assertContains(
			helperText,
			"updateReaderSurfaceTextureLayer",
			message = "Preview texture layers must stay on the same single-surface helper path, not resurrect document-scoped texture injection."
		)
	}

	@Test
	fun androidReaderInteriorDragPreviewUsesFoliatePageStride() {
		val pageTurnText = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val strideHelper = pageTurnText
			.substringAfter("function readerRendererPageStride")
			.substringBefore("\nfunction pageDragInteriorPreviewScroll")
		val interiorScroll = pageTurnText
			.substringAfter("function pageDragInteriorPreviewScroll")
			.substringBefore("\nfunction syncPageDragInteriorPreviewFrame")
		val syncInteriorFrame = pageTurnText
			.substringAfter("function syncPageDragInteriorPreviewFrame")
			.substringBefore("\nfunction ensureInteriorPageDragPreviewTarget")

		assertContains(strideHelper, "Number(renderer?.size)")
		assertContains(strideHelper, "Number(renderer?.viewSize)")
		assertContains(strideHelper, "Number(renderer?.pages)")
		assertContains(interiorScroll, "renderer")
		assertContains(interiorScroll, "readerRendererPageStride(renderer")
		assertFalse(
			interiorScroll.contains("Number(vertical ? height : width)"),
			"Interior drag previews must not step by native viewport dimensions; Foliate commits by its renderer page size, so viewport stepping previews the wrong page on tablets/landscape."
		)
		assertContains(syncInteriorFrame, "pageDragInteriorPreviewScroll(doc, { direction, width, height, vertical, renderer })")
		assertContains(syncInteriorFrame, "dataset.navicPageDragPreviewFrameAxisStep")
		assertContains(syncInteriorFrame, "dataset.navicPageDragPreviewFrameRendererPage")
		assertContains(syncInteriorFrame, "dataset.navicPageDragPreviewFrameRendererPages")
	}

	@Test
	fun androidReaderCurlSnapshotUsesCurrentFoliateRendererPageNotChapterStart() {
		val pageTurnText = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val currentScrollHelper = pageTurnText
			.substringAfter("function pageDragCurrentRendererScroll")
			.substringBefore("\nfunction pageDragMappedPreviewScroll")
		val snapshotFrame = pageTurnText
			.substringAfter("function syncPageDragCurlSnapshotFrame")
			.substringBefore("\nfunction syncPageDragCurlSnapshots")
		val snapshotSync = pageTurnText
			.substringAfter("function syncPageDragCurlSnapshots")
			.substringBefore("\nfunction buildPageDragPreviewTargetKey")

		assertContains(currentScrollHelper, "Number(renderer?.start)")
		assertContains(currentScrollHelper, "readerRendererPageStride(renderer")
		assertContains(snapshotFrame, "pageDragMappedPreviewScroll(snapshot, snapshotDoc, targetScroll")
		assertContains(snapshotFrame, "pageDragCurlSnapshotHtml(doc, layout)")
		assertContains(snapshotSync, "const frontTargetScroll = pageDragCurrentRendererScroll(frontDoc")
		assertContains(snapshotSync, "targetScroll: frontTargetScroll")
		assertContains(snapshotSync, "layout: frontLayout")
		assertContains(
			snapshotSync,
			"viewSize: Number(renderer?.viewSize)",
			message = "Curl front snapshots must reproduce Foliate's paged layout width so renderer.start maps to the visible page instead of cloning chapter page 1."
		)
	}

	@Test
	fun androidReaderKeepsPaperTextureSingleSurfaceWithoutDocumentStacking() {
		val bridgeText = readerBridgeText()
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val documentThemeCss = bridgeText
			.substringAfter("const readerDocumentThemeCss = settings =>")
			.substringBefore("const readerContentCss = settings =>")
		val applyDocumentTheme = bridgeText
			.substringAfter("applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {")
			.substringBefore("\nfunction currentRendererContainerPosition")
		val surfaceTextureUpdater = bridgeText
			.substringAfter("updateSurfacePaperTexture(detail = {}, pagePosition = null) {")
			.substringBefore("\n  applyReaderDirection")
		val surfaceLayerUpdater = helperText
			.substringAfter("export const updateReaderSurfaceTextureLayer = (layer, textureVariant, settings, scrollOffset = null) =>")
			.substringBefore("\n\nexport const updateReaderSurfaceBorderOverlayLayer")
		assertFalse(documentThemeCss.contains("html::before"), "Document pseudo-elements must not carry paper texture.")
		assertFalse(documentThemeCss.contains("body::before"), "Document pseudo-elements must not carry paper texture.")
		assertFalse(
			documentThemeCss.contains("[data-navic-paper-texture-layer=\"true\"]"),
			"Texture must not be injected as extra per-document layer elements."
		)
		assertFalse(
			helperText.contains("updateReaderDocumentPaperTexture") ||
				bridgeText.contains("applyDocumentPaperTexture") ||
				bridgeText.contains("applySurfacePaperTextureToDocuments"),
			"Loaded EPUB documents must not receive their own paper texture. One full-surface texture layer prevents double opacity and reinjection churn."
		)
		assertFalse(
			bridgeText.contains("readerPaperTextureLayerCount(settings)"),
			"Stacking the same texture many times creates the sepia opacity mess."
		)
		assertFalse(
			bridgeText.contains("Array.from({ length: readerPaperTextureLayerCount(settings) }"),
			"Texture must be one window layer, not a repeated background stack."
		)
		assertFalse(applyDocumentTheme.contains("ensurePaperTextureLayer(doc)"))
		assertFalse(applyDocumentTheme.contains("updatePaperTextureLayer"))
		assertFalse(applyDocumentTheme.contains("PaperTextureLayer"))
		assertFalse(applyDocumentTheme.contains("querySelectorAll('p"))
		assertFalse(applyDocumentTheme.contains("querySelectorAll(\"p"))
		assertContains(surfaceTextureUpdater, "ensureReaderSurfaceTextureLayer()")
		assertContains(surfaceLayerUpdater, "position: 'fixed'")
		assertContains(surfaceLayerUpdater, "const { width, height } = readerViewportSize()")
		assertContains(surfaceLayerUpdater, "width: widthPx")
		assertContains(surfaceLayerUpdater, "height: heightPx")
		assertContains(surfaceLayerUpdater, "'min-height': heightPx")
		assertFalse(
			surfaceLayerUpdater.contains("height: '100vh'"),
			"Android WebView resolved 100vh to a zero-height fixed texture layer in the reader."
		)
		assertContains(surfaceLayerUpdater, "'background-image': readerPaperTextureBackgroundImage(slot.variant)")
		assertContains(surfaceLayerUpdater, "opacity: readerSurfacePaperTextureOpacity(settings)")
		assertContains(surfaceLayerUpdater, "'pointer-events': 'none'")
	}

	@Test
	fun androidReaderSurfaceSwipeGestureStaysSeparateFromReadableTapZones() {
		val bridgeText = readerBridgeText()
		val surfaceGesture = bridgeText
			.substringAfter("attachSurfaceTapGesture(element) {")
			.substringBefore("\nfunction readerTapZoneActionForPoint")

		assertFalse(
			surfaceGesture.contains("handleReaderTapZone"),
			"Fixed-layout swipe handling must stay separate from readable WebView tap-zone classification."
		)
		assertFalse(
			surfaceGesture.contains("surface-touch"),
			"Surface touch taps must not dispatch reader-wide page/menu actions from JavaScript."
		)
		assertContains(bridgeText, "attachReaderTapZoneGesture")
		assertContains(bridgeText, "handleReaderTapZoneTap")
		assertFalse(
			surfaceGesture.contains("if (this.view?.isFixedLayout !== true) return\n      const touch"),
			"Surface gesture setup must still allow fixed-layout swipe handling without gating the whole listener away."
		)
		assertFalse(
			surfaceGesture.contains("if (this.view?.isFixedLayout === true) {\n        await this.handleReaderTapZone(event, document, 'surface')"),
			"Fixed-layout surface clicks must not use WebView-owned reader-wide tap zones."
		)
	}

	@Test
	fun androidReaderKeepsSingleRootTextureAboveContentWithoutDocumentMovement() {
		val bridgeText = readerBridgeText()
		val surfaceLayerUpdater = bridgeText
			.substringAfter("const updateReaderSurfaceTextureLayer = (layer, textureVariant, settings")
			.substringBefore("\n\nconst isParagraphCandidate")
		val renderSurfacePaperTextureLayers = bridgeText
			.substringAfter("renderSurfacePaperTextureLayers() {")
			.substringBefore("\nfunction surfacePaperTextureIndex")
		val pageTurnSnapshot = bridgeText
			.substringAfter("function pageDragCurlSnapshotHtml(doc) {")
			.substringBefore("\n\nfunction pageDragCurlSnapshotKey")
		val runtimeFields = bridgeText
			.substringAfter("class NavicReaderRuntime {")
			.substringBefore("\n  constructor()")

		assertContains(bridgeText, "attachSurfacePaperTextureScrollSync")
		assertContains(bridgeText, "syncSurfacePaperTextureScrollOffset")
		assertContains(bridgeText, "surfacePaperTextureScrollOffset")
		assertFalse(bridgeText.contains("applySurfacePaperTextureToDocuments"))
		assertFalse(bridgeText.contains("applyDocumentPaperTexture"))
		assertFalse(bridgeText.contains("updateReaderDocumentPaperTexture"))
		assertFalse(bridgeText.contains("readerDocumentPaperTextureBackground"))
		assertContains(bridgeText, "renderer.containerPosition")
		assertContains(bridgeText, "renderer.addEventListener('scroll'")
		assertContains(runtimeFields, "surfacePaperTextureBaseOffset")
		assertContains(runtimeFields, "surfaceTextureScrollOffset")
		assertContains(bridgeText, "surfacePaperTextureDiagnosticState(reason = 'scroll')")
		assertContains(bridgeText, "position: this.currentRendererContainerPosition()")
		assertContains(bridgeText, "baseOffset: this.surfacePaperTextureBaseOffset")
		assertContains(bridgeText, "delta: position - this.surfacePaperTextureBaseOffset")
		assertContains(bridgeText, "pageTurnDirection: this.surfacePaperTextureTurnDirection || this.pageTurnDirection || ''")
		assertContains(bridgeText, "viewportWidth: width")
		assertContains(bridgeText, "viewportHeight: height")
		assertContains(bridgeText, "flowMode: this.readerFlowModeValue")
		assertContains(bridgeText, "textureKey: readerRoot.dataset.navicSurfacePaperTextureKey || ''")
		assertContains(bridgeText, "readerTrace('texture:scroll', diagnostic)")
		assertContains(
			bridgeText,
			"const effectiveDirection = explicitDirection || (directionlessBoundaryLikeDelta ? fallbackDirection : null)",
			message = "Texture movement needs the recent logical page-turn direction when renderer coordinates wrap at section boundaries."
		)
		assertContains(surfaceLayerUpdater, "readerSurfaceTextureSlotTransform")
		assertContains(
			surfaceLayerUpdater,
			"readerPaperTextureBackgroundPosition(null)",
			message = "Each page slot keeps its own texture centered while the slot itself moves with the renderer."
		)
		assertContains(renderSurfacePaperTextureLayers, "updateReaderStaticPaperBackingLayer(")
		assertContains(renderSurfacePaperTextureLayers, "updateReaderMovingPageTextureLayer(")
		assertContains(
			renderSurfacePaperTextureLayers,
			"const scrollOffset = this.surfacePaperTextureScrollOffset()",
			message = "The moving page texture render path must feed committed drag/scroll offset into both paper layers."
		)
		assertContains(renderSurfacePaperTextureLayers, "scrollOffset")
		assertContains(pageTurnSnapshot, "background-color:var(--reader-background, transparent)!important;")
		assertFalse(
			pageTurnSnapshot.contains("readerDocumentPaperTextureBackground") ||
				pageTurnSnapshot.contains("navicDocumentPaperTexture"),
			"Page curl snapshots must not clone a second texture owner into preview documents."
		)
		assertContains(
			surfaceLayerUpdater,
			"readerSurfaceTextureSlotTransform({ slot: slot.slot, scrollOffset, width, height, flowMode, readerDirection })",
			message = "The single root surface must move page texture slots with the rendered page instead of swapping one parent background."
		)
		assertContains(surfaceLayerUpdater, "readerPaperTextureTransform(slot.variant)")
		assertContains(
			surfaceLayerUpdater,
			"'z-index': '2147483630'",
			message = "The paper texture must cover Foliate content without sitting above reader chrome."
		)
	}

	@Test
	fun androidReaderMovesPageTextureSlotsWithPageDragInsteadOfSwappingOneBackground() {
		val bridgeText = readerBridgeText()
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val surfaceTextureUpdate = bridgeText
			.substringAfter("updateSurfacePaperTexture(detail = {}, pagePosition = null) {")
			.substringBefore("\n  applyReaderDirection")
		val surfaceLayerUpdater = helperText
			.substringAfter("export const updateReaderSurfaceTextureLayer = (layer, textureSlots, settings, scrollOffset = null")
			.substringBefore("\n\nexport const updateReaderSurfaceBorderOverlayLayer")
		val borderLayerUpdater = helperText
			.substringAfter("export const updateReaderSurfaceBorderOverlayLayer = (layer, borderOverlaySlots, settings, scrollOffset = null")
			.substringBefore("\n\nexport const readerPageNumberLayerStyle")

		assertContains(
			helperText,
			"data-navic-surface-paper-texture-slot",
			message = "The root texture owner must contain page slots so the incoming page texture is visible during drag."
		)
		assertContains(helperText, "readerAdjacentPaperTextureSlots")
		assertContains(helperText, "readerSurfaceTextureSlotTransform")
		assertContains(helperText, "ensureReaderSurfaceTextureSlotArtwork")
		assertContains(helperText, "ReaderDirectionRtl")
		assertContains(surfaceTextureUpdate, "const textureSlots = readerAdjacentPaperTextureSlots")
		assertContains(surfaceTextureUpdate, "const borderOverlaySlots = readerAdjacentPaperTextureSlots")
		assertContains(surfaceTextureUpdate, "this.surfaceTextureSlots = textureSlots")
		assertContains(surfaceTextureUpdate, "this.surfaceBorderOverlaySlots = borderOverlaySlots")
		assertContains(surfaceLayerUpdater, "for (const slot of textureSlots)")
		assertContains(surfaceLayerUpdater, "readerSurfaceTextureSlotTransform({")
		assertContains(surfaceLayerUpdater, "readerPaperTextureBackgroundPosition(null)")
		assertContains(surfaceLayerUpdater, "transform: readerSurfaceTextureSlotTransform({ slot: slot.slot, scrollOffset, width, height, flowMode, readerDirection })")
		assertContains(surfaceLayerUpdater, "transform: readerPaperTextureTransform(slot.variant)")
		assertContains(borderLayerUpdater, "for (const slot of borderOverlaySlots)")
		assertContains(borderLayerUpdater, "readerSurfaceTextureSlotTransform({")
		assertContains(borderLayerUpdater, "readerPaperTextureBackgroundPosition(null)")
		assertContains(borderLayerUpdater, "transform: readerSurfaceTextureSlotTransform({ slot: slot.slot, scrollOffset, width, height, flowMode, readerDirection })")
		assertContains(borderLayerUpdater, "transform: readerPaperTextureTransform(slot.variant)")
		assertFalse(
			surfaceLayerUpdater.contains("'background-image': readerPaperTextureBackgroundImage(textureVariant)"),
			"One mutable root background swaps after relocation; slots let current and adjacent page textures move with the text."
		)
		assertFalse(
			borderLayerUpdater.contains("'background-image': readerSurfacePageBorderOverlayBackgroundImage(borderOverlayVariant)"),
			"Border degradation must follow the same page slots as the paper texture, not repaint as one static background."
		)
	}

	@Test
	fun androidReaderKeepsTextureTurnDirectionUntilCommittedTextureUpdate() {
		val bridgeText = readerBridgeText()
		val appearanceText = readerAssetRoot().resolve("navic-reader-appearance.js").readText()
		val runtimeFields = bridgeText
			.substringAfter("class NavicReaderRuntime {")
			.substringBefore("\n  constructor()")
		val startPageTurn = bridgeText
			.substringAfter("startPageTurn(direction) {")
			.substringBefore("\n  startNextQueuedPageTurn")
		val surfaceOffset = bridgeText
			.substringAfter("surfacePaperTextureScrollOffset() {")
			.substringBefore("\n  surfacePaperTextureDiagnosticState")
		val surfaceTextureUpdate = appearanceText
			.substringAfter("function applySurfacePaperTextureUpdate(detail = {}, pagePosition = null)")
			.substringBefore("\nfunction shouldDeferSurfacePaperTextureUpdate")

		assertContains(
			runtimeFields,
			"surfacePaperTextureTurnDirection = null",
			message = "Texture motion needs a direction state that can outlive pageTurnPromise settlement at area boundaries."
		)
		assertContains(
			runtimeFields,
			"surfacePaperTextureMotionFrame = null",
			message = "Animated page turns need a requestAnimationFrame owner because Foliate can move containerPosition between sparse scroll events."
		)
		assertContains(
			runtimeFields,
			"surfacePaperTextureMotionSyncActive = false",
			message = "Texture frame sync needs an explicit active flag so it can stop on committed texture updates without product timeouts."
		)
		assertContains(
			startPageTurn,
			"this.surfacePaperTextureTurnDirection = direction",
			message = "Every explicit next/previous action must seed texture movement direction before Foliate emits delayed scroll/relocate events."
		)
		assertContains(
			startPageTurn,
			"this.startSurfacePaperTextureMotionSync('page-turn-animation')",
			message = "Texture slots must track renderer movement on animation frames while the page turn is in progress."
		)
		assertContains(
			surfaceOffset,
			"pageTurnDirection: this.surfacePaperTextureTurnDirection || this.pageTurnDirection",
			message = "Texture offset must prefer the sticky surface direction, not only the transient pageTurnDirection cleared in finally."
		)
		assertContains(
			bridgeText,
			"startSurfacePaperTextureMotionSync(reason = 'page-turn-animation')",
			message = "The runtime must expose a named frame-sync owner for animated texture motion."
		)
		assertContains(
			bridgeText,
			"requestAnimationFrame(tick)",
			message = "Frame sync should follow renderer motion, not wait for relocation or scroll events that can arrive late."
		)
		assertContains(
			bridgeText,
			"cancelAnimationFrame(this.surfacePaperTextureMotionFrame)",
			message = "Frame sync must be explicitly stopped when the committed texture update lands."
		)
		assertContains(
			surfaceTextureUpdate,
			"this.stopSurfacePaperTextureMotionSync('texture-update')",
			message = "The page-turn frame sync must stop only when the committed paper texture is updated."
		)
		assertTrue(
			surfaceTextureUpdate.indexOf("this.stopSurfacePaperTextureMotionSync('texture-update')") <
				surfaceTextureUpdate.indexOf("this.surfacePaperTextureTurnDirection = null"),
			"Final texture sync must run while the sticky direction is still available."
		)
		assertContains(
			surfaceTextureUpdate,
			"this.surfacePaperTextureTurnDirection = null",
			message = "The sticky texture direction should be cleared only after the committed page texture has been updated."
		)
		assertFalse(
			startPageTurn.substringAfter("completionPromise = turnPromise.finally(() => {").contains("this.surfacePaperTextureTurnDirection = null"),
			"Clearing texture direction in the page-turn finally races Foliate's delayed relocation at area transitions."
		)
	}

	@Test
	fun androidReaderKeepsMovingTextureOffsetForPagedRendererScrollEvents() {
		val bridgeText = readerBridgeText()
		val onLoad = bridgeText
			.substringAfter("onLoad(detail = {}) {")
			.substringBefore("\n  logContentLayout")
		val onRelocate = bridgeText
			.substringAfter("function onRelocate(detail) {")
			.substringBefore("\n\nfunction cancelPendingCommittedRelocation")
		val surfaceOffset = bridgeText
			.substringAfter("surfacePaperTextureScrollOffset() {")
			.substringBefore("\n  surfacePaperTextureDiagnosticState")

		assertContains(
			onLoad,
			"this.attachSurfacePaperTextureScrollSync()",
			message = "Foliate can install or replace renderer after open; load must bind the moving texture scroll listener to the active renderer."
		)
		assertContains(
			onRelocate,
			"this.attachSurfacePaperTextureScrollSync()",
			message = "Relocation can expose a fresh renderer/content set; texture movement must keep the renderer scroll listener attached."
		)
		assertFalse(
			surfaceOffset.contains("renderer.scrolled || !Number.isFinite(position)"),
			"Paged Foliate renderer scroll events must still move the moving texture slots; suppressing all renderer.scrolled paths makes the grain swap after the text."
		)
		assertContains(
			surfaceOffset,
			"this.readerFlowModeValue === ReaderFlowScrolled || this.readerFlowModeValue === ReaderFlowScrolledGaps",
			message = "Only true continuous-scroll modes should pin the paper texture; paged and vertical-paged readers need the container delta."
		)
	}

	@Test
	fun androidReaderSeedsTextureTurnDirectionFromReaderDocumentDrags() {
		val bridgeText = readerBridgeText()
		val textureDragDirection = bridgeText
			.substringAfter("attachSurfacePaperTextureDragDirection(doc) {")
			.substringBefore("\n  surfacePaperTextureScrollOffset")
		val contentDocumentBehaviors = bridgeText
			.substringAfter("attachContentDocumentBehaviors(doc, index) {")
			.substringBefore("\n  contentEntries")

		assertContains(
			bridgeText,
			"readerPaperTextureDragDirection",
			message = "Directionless Foliate drag gestures need explicit texture direction instead of raw renderer coordinate sign."
		)
		assertContains(
			textureDragDirection,
			"doc.addEventListener('touchmove'",
			message = "Reader content documents must watch drag motion because Foliate can turn pages without Navic nextPage/previousPage commands."
		)
		assertContains(
			textureDragDirection,
			"this.surfacePaperTextureTurnDirection = direction",
			message = "A detected finger drag must seed the same sticky texture direction used by explicit page-turn commands."
		)
		assertContains(
			contentDocumentBehaviors,
			"this.attachSurfacePaperTextureDragDirection(doc)",
			message = "Every loaded EPUB content document must install the texture drag-direction tracker."
		)
	}

	@Test
	fun androidReaderSeedsTextureTurnDirectionFromNativeReadableDragPreview() {
		val bridgeText = readerBridgeText()
		val previewPageDrag = bridgeText
			.substringAfter("previewPageDrag(command) {")
			.substringBefore("\nasync function scrollViewport")

		assertContains(
			previewPageDrag,
			"readerPaperTextureDragDirection({",
			message = "Native-owned readable drags bypass document touchmove tracking, so the bridge preview path must seed texture direction itself."
		)
		assertContains(
			previewPageDrag,
			"this.surfacePaperTextureTurnDirection = textureDirection",
			message = "Native readable drag preview must use the same sticky next/previous texture direction as explicit page turns."
		)
		assertContains(
			previewPageDrag,
			"readerTrace('texture:drag-direction'",
			message = "Texture direction seeded by native drag preview must be visible in harness and ADB trace diagnostics."
		)
		assertContains(
			previewPageDrag,
			"const deltaY = Number(command?.deltaY)",
			message = "Native readable drag preview must preserve vertical deltas for paged-vertical books."
		)
		assertContains(
			previewPageDrag,
			"readerPageDragPreviewMotion({",
			message = "Preview movement must use the shared reader motion contract instead of hard-coded horizontal math."
		)
		assertContains(
			previewPageDrag,
			"this.updatePageDragPreviewLayer({",
			message = "Native readable drag previews must route movement to the visual preview layer."
		)
		assertFalse(
			previewPageDrag.contains("renderer.scrollBy(-incrementalDelta.x, -incrementalDelta.y)"),
			"Native readable drag previews must not mutate Foliate renderer position before the release page turn."
		)
	}

	@Test
	fun androidReaderKeepsCurrentPageMovingWhileBoundaryPreviewLoads() {
		val bridgeText = readerBridgeText()
		val previewPageDrag = bridgeText
			.substringAfter("previewPageDrag(command) {")
			.substringBefore("\nasync function scrollViewport")
		val boundaryPreviewBlock = previewPageDrag
			.substringAfter("if (boundaryDirection) {")
			.substringBefore("\n  this.updatePageDragPreviewLayer({")

		assertContains(
			previewPageDrag,
			"let waitingForBoundaryPreview = false",
			message = "Section-boundary drags need an explicit pending-preview state instead of returning before movement."
		)
		assertContains(
			boundaryPreviewBlock,
			"waitingForBoundaryPreview = true",
			message = "Pending adjacent-page previews must be visible in source and runtime traces."
		)
		assertFalse(
			boundaryPreviewBlock.contains("return"),
			"Boundary drags must keep moving the current page while the adjacent iframe preview is still loading."
		)
		assertContains(
			previewPageDrag,
			"source: waitingForBoundaryPreview ? 'boundary-preview-loading' : 'native-preview'",
			message = "Diagnostics must distinguish a real adjacent-page preview from the fallback surface shown while it loads."
		)
		assertTrue(
			previewPageDrag.indexOf("waitingForBoundaryPreview = true") <
				previewPageDrag.indexOf("this.updatePageDragPreviewLayer({"),
			"The boundary-preview loading branch must still reach the visual preview update instead of stopping before it."
		)
		assertFalse(
			previewPageDrag.contains("renderer.scrollBy(-incrementalDelta.x, -incrementalDelta.y)"),
			"Boundary drag preview must not mutate the committed renderer while the adjacent iframe preview is loading."
		)
	}

	@Test
	fun androidReaderShowsPaperFallbackWhileBoundaryPreviewLoads() {
		val bridgeText = readerBridgeText()
		val previewLayer = bridgeText
			.substringAfter("function updatePageDragPreviewLayer(")
			.substringBefore("\nfunction previewPageDrag")
		val loadingBranch = previewLayer
			.substringAfter("if (!ready) {")
			.substringBefore("\n    readerTrace('page-drag-preview:underlay-waiting'")
		val layerStyle = loadingBranch
			.substringAfter("setStylesImportant(layer, {")
			.substringBefore("\n    })")

		assertContains(
			loadingBranch,
			"layer.dataset.navicPageDragPreviewFallback = 'paper'",
			message = "A section-boundary drag must expose a paper fallback instead of an offscreen hidden layer while the adjacent iframe loads."
		)
		assertContains(
			layerStyle,
			"width: `${'$'}{fallbackWidth}px`",
			message = "The fallback underlay must cover the dragged exposure width instead of a 1px hidden strip."
		)
		assertContains(
			layerStyle,
			"height: `${'$'}{fallbackHeight}px`",
			message = "The fallback underlay must cover the dragged exposure height instead of hiding the page preview."
		)
		assertContains(
			layerStyle,
			"opacity: '1'",
			message = "Boundary preview fallback must remain visible; opacity 0 exposes the native black background."
		)
		assertFalse(
			layerStyle.contains("left: '-1px'"),
			"Boundary preview fallback must not be moved offscreen while the current page keeps moving."
		)
		assertFalse(
			layerStyle.contains("width: '1px'"),
			"Boundary preview fallback must not collapse to a hidden 1px strip."
		)
		assertFalse(
			layerStyle.contains("opacity: '0'"),
			"Boundary preview fallback must not be transparent."
		)
	}

	@Test
	fun androidReaderPortsCurlMetricsToDragPreviewLayerOnly() {
		val bridgeText = readerBridgeText()
		val curlMetrics = bridgeText
			.substringAfter("function readerPageDragCurlMetrics(")
			.substringBefore("\nfunction applyPageDragCurlMetrics")
		val applyCurlMetrics = bridgeText
			.substringAfter("function applyPageDragCurlMetrics(")
			.substringBefore("\nfunction buildPageDragPreviewTargetKey")
		val previewLayer = bridgeText
			.substringAfter("function updatePageDragPreviewLayer(")
			.substringBefore("\nfunction previewPageDrag")
		val previewPageDrag = bridgeText
			.substringAfter("previewPageDrag(command) {")
			.substringBefore("\nasync function scrollViewport")
		val releaseBranch = previewPageDrag
			.substringAfter("if (phase === 'release') {")
			.substringBefore("\n  if (this.readerIsFixedLayoutPublication())")

		assertContains(
			curlMetrics,
			"progress < 0.5",
			message = "The drag curl should port the mockup's non-linear easing instead of using linear slide opacity."
		)
		assertContains(
			curlMetrics,
			"Math.sin(Math.PI * progress)",
			message = "Curl width and shadow should follow the mockup's sinusoidal peak during the drag."
		)
		assertContains(
			previewLayer,
			"this.applyPageDragCurlMetrics(layer, {",
			message = "The clipped adjacent-page underlay must receive curl metrics during drag preview updates."
		)
		assertContains(
			applyCurlMetrics,
			"dataset.navicPageDragPreviewCurl = 'true'",
			message = "Harness and ADB probes need a concrete dataset flag proving curl is active only on the drag preview layer."
		)
		assertContains(
			applyCurlMetrics,
			"--navic-page-curl-progress",
			message = "The layer must expose curl progress as CSS state so browser probes can verify the effect."
		)
		assertContains(
			applyCurlMetrics,
			"--navic-page-curl-angle",
			message = "The layer must expose the mockup-derived curl angle for drag-only visual transforms."
		)
		assertContains(
			releaseBranch,
			"this.removePageDragPreviewLayer()",
			message = "Curl visuals must be removed on release instead of remaining after a tap/menu action."
		)
		assertFalse(
			releaseBranch.contains("applyPageDragCurlMetrics"),
			"Release handling must not create curl visuals; only drag-preview updates are allowed to apply them."
		)
	}

	@Test
	fun androidReaderPortsMockupCurlSheetRolesToDragPreviewOnly() {
		val bridgeText = readerBridgeText()
		val ensureLayer = bridgeText
			.substringAfter("function ensurePageDragPreviewLayer() {")
			.substringBefore("\nfunction removePageDragPreviewLayer")
		val applySheet = bridgeText
			.substringAfter("function applyPageDragCurlSheet(")
			.substringBefore("\nfunction buildPageDragPreviewTargetKey")
		val previewLayer = bridgeText
			.substringAfter("function updatePageDragPreviewLayer(")
			.substringBefore("\nfunction previewPageDrag")
		val previewPageDrag = bridgeText
			.substringAfter("previewPageDrag(command) {")
			.substringBefore("\nasync function scrollViewport")
		val releaseBranch = previewPageDrag
			.substringAfter("if (phase === 'release') {")
			.substringBefore("\n  const deltaX = Number(command?.deltaX)")

		assertContains(
			ensureLayer,
			"data-navic-page-curl-sheet",
			message = "The drag preview must create explicit mockup-derived sheet roles instead of styling the underlay as a single flat panel."
		)
		assertContains(
			ensureLayer,
			"turning-front",
			message = "The mockup front face must have its own layer so the current page can behave like the turning sheet."
		)
		assertContains(
			ensureLayer,
			"turning-back",
			message = "The mockup back face must have its own layer so spread mode can later render the reverse side instead of an inverted copy."
		)
		assertContains(
			ensureLayer,
			"underneath",
			message = "The target page must stay as the underneath layer, matching the Komikku/mockup mental model."
		)
		assertContains(
			applySheet,
			"dataset.navicPageCurlSheetMode",
			message = "Harness and ADB probes need an observable single/spread sheet mode, not only angle variables."
		)
		assertContains(
			applySheet,
			"dataset.navicPageCurlSheetRoles",
			message = "Harness and ADB probes need to prove the front/back/underneath roles are mounted."
		)
		assertContains(
			applySheet,
			"--navic-page-curl-front-face-opacity",
			message = "The drag sheet must port the mockup's front-face fade contract."
		)
		assertContains(
			applySheet,
			"--navic-page-curl-back-face-opacity",
			message = "The drag sheet must port the mockup's back-face reveal contract even when single-page mode suppresses it."
		)
		assertContains(
			previewLayer,
			"this.applyPageDragCurlSheet(layer, {",
			message = "Curl sheet roles must be applied only from the drag-preview layer update path."
		)
		assertContains(
			releaseBranch,
			"this.removePageDragPreviewLayer()",
			message = "Release must remove all curl sheet roles before the actual page turn."
		)
		assertFalse(
			releaseBranch.contains("applyPageDragCurlSheet"),
			"Release handling must not create curl sheet roles; only active drags may show them."
		)
	}

	@Test
	fun androidReaderPortsCurlSnapshotsToDragPreviewOnly() {
		val bridgeText = readerBridgeText()
		val ensureLayer = bridgeText
			.substringAfter("function ensurePageDragPreviewLayer() {")
			.substringBefore("\nfunction removePageDragPreviewLayer")
		val snapshotHelper = bridgeText
			.substringAfter("function syncPageDragCurlSnapshots(")
			.substringBefore("\nfunction buildPageDragPreviewTargetKey")
		val previewLayer = bridgeText
			.substringAfter("function updatePageDragPreviewLayer(")
			.substringBefore("\nfunction previewPageDrag")
		val previewPageDrag = bridgeText
			.substringAfter("previewPageDrag(command) {")
			.substringBefore("\nasync function scrollViewport")
		val releaseBranch = previewPageDrag
			.substringAfter("if (phase === 'release') {")
			.substringBefore("\n  const deltaX = Number(command?.deltaX)")
		val cancelBranch = previewPageDrag
			.substringAfter("if (phase === 'cancel') {")
			.substringBefore("\n  if (phase === 'release') {")

		assertContains(
			ensureLayer,
			"data-navic-page-curl-snapshot",
			message = "The mockup curl sheets must reserve observable snapshot surfaces for current/reverse page content."
		)
		assertContains(
			snapshotHelper,
			"data-navic-page-curl-snapshot",
			message = "Snapshot capture needs concrete markers so harness and ADB probes can prove rendered page content is present."
		)
		assertContains(
			snapshotHelper,
			"front",
			message = "The active turning sheet must clone the current page front face instead of remaining a gradient-only layer."
		)
		assertContains(
			snapshotHelper,
			"back",
			message = "Spread mode must have a reverse-face capture path for the adjacent page."
		)
		assertContains(
			previewLayer,
			"this.syncPageDragCurlSnapshots(layer, {",
			message = "Snapshot capture must run only from the active drag-preview update path."
		)
		assertFalse(
			releaseBranch.contains("syncPageDragCurlSnapshots"),
			"Release handling must not capture snapshots; it must remove the preview layer before the actual page turn."
		)
		assertFalse(
			cancelBranch.contains("syncPageDragCurlSnapshots"),
			"Cancel handling must not capture snapshots; it must only restore and clear preview state."
		)
	}

	@Test
	fun androidReaderRestoresAndClearsNativeDragPreviewOnReleaseBeforePageTurn() {
		val bridgeText = readerBridgeText()
		val previewPageDrag = bridgeText
			.substringAfter("previewPageDrag(command) {")
			.substringBefore("\nasync function scrollViewport")
		assertContains(
			previewPageDrag,
			"if (phase === 'release') {",
			message = "Release must have an explicit cleanup branch separate from update and cancel."
		)
		val releaseBranch = previewPageDrag
			.substringAfter("if (phase === 'release') {")
			.substringBefore("\n  const deltaX = Number(command?.deltaX)")

		assertFalse(
			releaseBranch.contains("renderer.scrollBy(previousDelta.x, previousDelta.y)"),
			message = "Release must not restore Foliate synthetic drag scroll because native previews are visual-only."
		)
		assertContains(
			releaseBranch,
			"const releaseTextureDirection = readerPaperTextureDragDirection({",
			message = "Release must derive a texture fallback from the final native delta instead of relying only on earlier move events."
		)
		assertContains(
			releaseBranch,
			"this.surfacePaperTextureFallbackDirection = releaseTextureDirection",
			message = "Release must preserve the direction as a fallback for the real page turn after clearing preview-only direction state."
		)
		assertContains(
			releaseBranch,
			"this.removePageDragPreviewLayer()",
			message = "Release must remove the visual underlay immediately; relocation is not guaranteed to fire after a queued/blocked page turn."
		)
		assertContains(
			releaseBranch,
			"this.nativePageDragPreview = null",
			message = "Release must clear the tracked preview delta before page-turn commands can queue or coalesce."
		)
	}

	@Test
	fun androidReaderPaperTextureDoesNotAbortFixedLayoutOpenWhenPageIndexIsUnavailable() {
		val bridgeText = readerBridgeText()
		val fixedLayoutIndex = bridgeText
			.substringAfter("fixedLayoutCurrentPageIndex() {")
			.substringBefore("\n  fixedLayoutAdjacentPageTarget")
		val surfaceTextureIndex = bridgeText
			.substringAfter("surfacePaperTextureIndex(detail = {}) {")
			.substringBefore("\n  updateSurfacePaperTexture")

		assertContains(
			fixedLayoutIndex,
			"try {",
			message = "Foliate fixed-layout renderer.index can throw before a current spread exists."
		)
		assertContains(fixedLayoutIndex, "catch (error)")
		assertContains(fixedLayoutIndex, "fixed-layout-index:unavailable")
		assertContains(fixedLayoutIndex, "return null")
		assertContains(surfaceTextureIndex, "const detailIndex = Number(detail?.index)")
		assertContains(surfaceTextureIndex, "this.fixedLayoutCurrentPageIndex()")
		assertContains(surfaceTextureIndex, "return Number.isFinite(entryIndex) ? Math.floor(entryIndex) : 0")
	}

	@Test
	fun androidReaderPaperTextureVariesByRenderedPageLocatorInsideSameEpubSection() {
		val bridgeText = readerBridgeText()
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val textureKey = bridgeText
			.substringAfter("const readerPaperTextureVariantKey = (publicationUrl, section, index, detail = {}) =>")
			.substringBefore("\n\nconst readerPaperTextureVariantForPage")
		val texturePageLocator = helperText
			.substringAfter("export const readerPaperTexturePageLocator = detail =>")
			.substringBefore("\n\nexport const readerPaperTextureVariantKey")
		val surfaceTextureUpdater = bridgeText
			.substringAfter("updateSurfacePaperTexture(detail = {}, pagePosition = null) {")
			.substringBefore("\n  applyReaderDirection")

		assertContains(bridgeText, "readerPaperTexturePageLocator")
		assertContains(bridgeText, "detail?.cfi")
		assertContains(bridgeText, "detail?.fraction ?? detail?.progress ?? detail?.totalProgress")
		assertContains(texturePageLocator, "detail?.pageIndex")
		assertFalse(
			texturePageLocator.contains("detail?.pageCount") ||
				texturePageLocator.contains("\${Math.floor(pageCount)}"),
			"Paper texture identity must follow the visible page, not the provisional/final page-count denominator."
		)
		assertContains(textureKey, "readerPaperTexturePageLocator(detail)")
		assertContains(
			surfaceTextureUpdater,
			"readerPaperTextureVariantKey(this.publicationUrl, section, index, textureDetail)",
			message = "Paginated EPUB pages inside the same spine item need distinct deterministic texture variants."
		)
		assertFalse(
			surfaceTextureUpdater.contains("readerPaperTextureVariantKey(this.publicationUrl, section, index)"),
			"Section-only texture keys reuse the same paper variant for every page in a chapter."
		)
	}

	@Test
	fun readerHarnessTextureFrontmatterTransitionDiscoversFixtureBoundary() {
		val harnessFile = listOf(
			java.io.File("tools/reader-harness/src/run-reader-harness.mjs"),
			java.io.File("../tools/reader-harness/src/run-reader-harness.mjs")
		).firstOrNull { it.isFile }
			?: error("Could not locate reader harness")
		val frontmatterModeText = harnessFile.readText()
		val frontmatterMode = frontmatterModeText
			.substringAfter("if (mode === 'epub-texture-frontmatter-transition') {")
			.substringBefore("\nif (mode === 'pdf-smoke')")

		assertContains(frontmatterMode, "visibleText")
		assertContains(frontmatterMode, "findTextureTransitionBoundary")
		assertContains(frontmatterMode, "transition-boundary-entry")
		assertFalse(
			frontmatterMode.contains("while (Number(currentLocation?.pageIndex) < 4)"),
			"Texture transition coverage must seek a real visible fixture boundary, not hard-code a shallow page index."
		)
		assertFalse(
			frontmatterMode.contains("Author's Note") || frontmatterMode.contains("AUTHOR'S NOTE"),
			"Texture transition coverage must not be hard-coded to the Hobbit Author's Note heading."
		)
	}

	@Test
	fun readerHarnessTextureFrontmatterTransitionIncludesRealDragProbe() {
		val harnessFile = listOf(
			java.io.File("tools/reader-harness/src/run-reader-harness.mjs"),
			java.io.File("../tools/reader-harness/src/run-reader-harness.mjs")
		).firstOrNull { it.isFile }
			?: error("Could not locate reader harness")
		val frontmatterMode = harnessFile.readText()
			.substringAfter("if (mode === 'epub-texture-frontmatter-transition') {")
			.substringBefore("\nif (mode === 'pdf-smoke')")

		assertContains(
			frontmatterMode,
			"type: 'previewPageDrag'",
			message = "The texture boundary harness must exercise native-overlay drag preview, not only bridge nextPage commands."
		)
		assertContains(
			frontmatterMode,
			"drag-transition-entry-boundary",
			message = "The fixture-discovered texture transition must have a named drag probe for phone-equivalent behavior."
		)
		assertContains(
			frontmatterMode,
			"drag-post-transition-boundary",
			message = "The texture harness must prove texture direction remains correct after the discovered boundary, not just while entering it."
		)
		assertContains(
			frontmatterMode,
			"drag-reverse-transition-boundary",
			message = "The texture harness must reproduce the phone-side reverse transition across the discovered boundary."
		)
		assertContains(
			frontmatterMode,
			"traceStart",
			message = "Drag-direction evidence must be scoped per probe so a pre-boundary drag event cannot mask a post-boundary regression."
		)
		assertContains(
			frontmatterMode,
			"texture:drag-direction",
			message = "The drag probe must verify that runtime touch tracking actually seeded texture direction."
		)
	}

	@Test
	fun readerHarnessTextureFrontmatterTransitionValidatesTracePayloadDirection() {
		val harnessFile = listOf(
			java.io.File("tools/reader-harness/src/run-reader-harness.mjs"),
			java.io.File("../tools/reader-harness/src/run-reader-harness.mjs")
		).firstOrNull { it.isFile }
			?: error("Could not locate reader harness")
		val assertionsFile = listOf(
			java.io.File("tools/reader-harness/src/reader-trace-assertions.mjs"),
			java.io.File("../tools/reader-harness/src/reader-trace-assertions.mjs")
		).firstOrNull { it.isFile }
			?: error("Could not locate reader trace assertions")
		val frontmatterMode = harnessFile.readText()
			.substringAfter("if (mode === 'epub-texture-frontmatter-transition') {")
			.substringBefore("\nif (mode === 'pdf-smoke')")
		val assertions = assertionsFile.readText()

		assertContains(
			assertions,
			"assertTextureTracePayloadsTrackTurnDirection",
			message = "Texture trace assertions must inspect structured texture:scroll payloads, not only sampled CSS strings."
		)
		assertContains(
			assertions,
			"event?.payload",
			message = "Texture trace assertions must read the payload emitted by readerTrace('texture:scroll', diagnostic)."
		)
		assertContains(
			assertions,
			"pageTurnDirection === 'next'",
			message = "Forward texture traces must be checked by explicit runtime turn direction."
		)
		assertContains(
			assertions,
			"const textureOffset = flowMode === 'paged-vertical' ? yOffset : xOffset",
			message = "Trace assertions must check the vertical offset axis when the reader is in vertical paged mode."
		)
		assertContains(
			assertions,
			"payload.pageTurnDirection === 'next' && Number.isFinite(textureOffset) && textureOffset > 1 && !directedBoundaryWrap",
			message = "Trace assertions must reject positive next offsets unless the renderer crossed a known boundary wrap."
		)
		assertContains(
			assertions,
			"movedSamples.find(({ textureDelta, rendererBoundaryWrap }) =>",
			message = "Real page-turn samples must reject forward texture inversion while excluding valid renderer wraps."
		)
		assertContains(
			assertions,
			"parseBackgroundPositionOffsets(sample?.textureBackgroundPosition)",
			message = "Sample trace assertions must parse both background-position axes so vertical paging is not invisible to the harness."
		)
		assertContains(
			assertions,
			"String(sample?.flowMode || '') === 'paged-vertical' ? offsets?.y : offsets?.x",
			message = "Sample trace assertions must compare vertical texture motion against the y axis."
		)
		assertContains(
			frontmatterMode,
			"assertTextureTracePayloadsTrackTurnDirection(result.trace)",
			message = "The texture boundary harness must fail if texture:scroll payloads invert after the discovered boundary."
		)
	}

	@Test
	fun readerHarnessAllowsDirectedTextureWrapsAtRendererBoundaries() {
		val assertionFile = listOf(
			java.io.File("tools/reader-harness/src/reader-trace-assertions.mjs"),
			java.io.File("../tools/reader-harness/src/reader-trace-assertions.mjs")
		).firstOrNull { it.isFile }
			?: error("Could not locate reader trace assertions")
		val assertionsText = assertionFile.readText()
		val textureAssertion = assertionsText
			.substringAfter("const expectedDirectionSign = probe.direction === 'forward' ? 1 : probe.direction === 'backward' ? -1 : 0")
			.substringBefore("\n    const stationary =")

		assertContains(
			textureAssertion,
			"!rendererBoundaryWrap",
			message = "Forward/backward texture probes must not flag valid full-page renderer wraps as inverted texture motion."
		)
		assertContains(
			textureAssertion,
			"textureDelta > 1 && !rendererBoundaryWrap",
			message = "Forward probe failures must explicitly exclude the boundary-wrap case detected from renderer deltas."
		)
		assertContains(
			textureAssertion,
			"textureDelta < -1 && !rendererBoundaryWrap",
			message = "Backward probe failures must explicitly exclude the boundary-wrap case detected from renderer deltas."
		)
	}

	@Test
	fun readerHarnessTextureScrollCapturesBeforeTransformBeforeRendererMutation() {
		val harnessFile = listOf(
			java.io.File("tools/reader-harness/src/run-reader-harness.mjs"),
			java.io.File("../tools/reader-harness/src/run-reader-harness.mjs")
		).firstOrNull { it.isFile }
			?: error("Could not locate reader harness")
		val harnessText = harnessFile.readText()
		val scrollMode = harnessText
			.substringAfter("if (mode === 'epub-texture-scroll') {")
			.substringBefore("\nif (mode === 'epub-texture-page-turns') {")

		assertContains(
			scrollMode,
			"const beforeTextureSlotTransform = beforeSlot?.style?.transform || ''",
			message = "The scroll harness must copy the before transform string before mutating renderer.containerPosition; DOM style objects are live."
		)
		assertTrue(
			scrollMode.indexOf("const beforeTextureSlotTransform = beforeSlot?.style?.transform || ''") <
				scrollMode.indexOf("renderer.containerPosition = beforePosition + delta"),
			"The before texture transform must be captured before the renderer scroll mutation."
		)
	}

	@Test
	fun readerHarnessFullTraversalUsesLightweightPerPageSnapshots() {
		val harnessFile = listOf(
			java.io.File("tools/reader-harness/src/run-reader-harness.mjs"),
			java.io.File("../tools/reader-harness/src/run-reader-harness.mjs")
		).firstOrNull { it.isFile }
			?: error("Could not locate reader harness")
		val harnessText = harnessFile.readText()
		val fullTraversalMode = harnessText
			.substringAfter("if (mode === 'epub-full-traversal') {")
			.substringBefore("\nif (mode === 'epub-texture-scroll')")
		val traversalLoop = fullTraversalMode
			.substringAfter("for (let turn = 0; turn < maxTurns; turn += 1) {")
			.substringBefore("\n    const trace =")

		assertContains(fullTraversalMode, "collectCoverScanSnapshot")
		assertContains(fullTraversalMode, "collectLocationSnapshot")
		assertContains(traversalLoop, "snapshot = await collectLocationSnapshot()")
		assertContains(traversalLoop, "renderer?.removeAttribute?.('animated')")
		assertFalse(
			traversalLoop.contains("snapshot = await collectSnapshot()"),
			"Full traversal must not run the expensive DOM/image geometry scan on every page."
		)
	}

	@Test
	fun readerHarnessFullTraversalRejectsRawLocationPageTotals() {
		val assertionFile = listOf(
			java.io.File("tools/reader-harness/src/reader-trace-assertions.mjs"),
			java.io.File("../tools/reader-harness/src/reader-trace-assertions.mjs")
		).firstOrNull { it.isFile }
			?: error("Could not locate reader trace assertions")
		val assertionsText = assertionFile.readText()
		val fullTraversalAssertion = assertionsText
			.substringAfter("export const assertFullEpubTraversal = result => {")
			.substringBefore("\nexport const assertShellCoverDoesNotNavigateWebViewToCover")

		assertContains(
			fullTraversalAssertion,
			"page.location?.pageCountSource !== 'pagination-profile'",
			message = "Full traversal must fail if any visible EPUB page falls back to raw Foliate location totals such as 1748."
		)
		assertContains(
			fullTraversalAssertion,
			"Expected full traversal page labels to use pagination-profile",
			message = "The failure message must identify profile-source regressions instead of reporting only sequential labels."
		)
	}

	@Test
	fun readerHarnessPhase1RunnerReportsPerStepTimeoutsAndElapsedTime() {
		val harnessFile = listOf(
			java.io.File("tools/reader-harness/src/run-reader-harness.mjs"),
			java.io.File("../tools/reader-harness/src/run-reader-harness.mjs")
		).firstOrNull { it.isFile }
			?: error("Could not locate reader harness")
		val harnessText = harnessFile.readText()
		val phase1Mode = harnessText
			.substringAfter("if (mode === 'phase1-stabilization') {")
			.substringBefore("\nif (mode === 'texture-offset-logic')")

		assertContains(phase1Mode, "timeout:")
		assertContains(phase1Mode, "ETIMEDOUT")
		assertContains(phase1Mode, "elapsed")
		assertContains(phase1Mode, "timeoutMs")
	}

	@Test
	fun readerHarnessCanRunTextureProbesAtAndroidViewportParity() {
		val harnessFile = listOf(
			java.io.File("tools/reader-harness/src/run-reader-harness.mjs"),
			java.io.File("../tools/reader-harness/src/run-reader-harness.mjs")
		).firstOrNull { it.isFile }
			?: error("Could not locate reader harness")
		val harnessText = harnessFile.readText()

		assertContains(
			harnessText,
			"const readerHarnessViewport =",
			message = "Texture/page-number probes must be runnable at the Android viewport that produced the phone-side pagination bugs."
		)
		assertContains(harnessText, "--viewport-width")
		assertContains(harnessText, "--viewport-height")
		assertContains(harnessText, "--device-scale-factor")
		assertContains(
			harnessText,
			"await browser.newPage(readerHarnessViewport)",
			message = "Harness modes must use the resolved viewport override instead of the fixed narrow phone viewport."
		)
	}

	@Test
	fun readerHarnessSupportsPositionalModeArgument() {
		val harnessFile = listOf(
			java.io.File("tools/reader-harness/src/run-reader-harness.mjs"),
			java.io.File("../tools/reader-harness/src/run-reader-harness.mjs")
		).firstOrNull { it.isFile }
			?: error("Could not locate reader harness")
		val harnessText = harnessFile.readText()

		assertContains(
			harnessText,
			"const positionalMode =",
			message = "The harness must not silently run smoke mode when a mode is passed positionally."
		)
		assertContains(
			harnessText,
			"modeFromFlag || positionalMode || 'smoke'",
			message = "Both --mode epub-frontmatter and positional epub-frontmatter must select the requested harness mode."
		)
	}

}
