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
		val texture1 = root.resolve("paper-textures/paper-texture-1.png")
		val texture2 = root.resolve("paper-textures/paper-texture-2.png")
		val texture3 = root.resolve("paper-textures/paper-texture-3.png")

		assertTrue(texture1.isFile, "Reader paper texture 1 must be packaged")
		assertTrue(texture2.isFile, "Reader paper texture 2 must be packaged")
		assertTrue(texture3.isFile, "Reader paper texture 3 must be packaged")
		assertTrue(texture1.length() > 1_000, "Reader paper texture 1 should be a real image")
		assertTrue(texture2.length() > 1_000, "Reader paper texture 2 should be a real image")
		assertTrue(texture3.length() > 1_000, "Reader paper texture 3 should be a real image")
		assertTrue(texture1.hasPngAlphaChannel(), "Reader paper texture 1 must be transparent")
		assertTrue(texture2.hasPngAlphaChannel(), "Reader paper texture 2 must be transparent")
		assertTrue(texture3.hasPngAlphaChannel(), "Reader paper texture 3 must be transparent")
		assertTrue(texture1.averagePngAlpha() >= 2.0, "Reader paper texture 1 must be visible at runtime")
		assertTrue(texture2.averagePngAlpha() >= 2.0, "Reader paper texture 2 must be visible at runtime")
		assertTrue(texture3.averagePngAlpha() >= 2.0, "Reader paper texture 3 must be visible at runtime")
		assertContains(bridgeText, "ReaderPaperTextureAssets")
		assertContains(bridgeText, "paper-textures/paper-texture-1.png")
		assertContains(bridgeText, "paper-textures/paper-texture-2.png")
		assertContains(bridgeText, "paper-textures/paper-texture-3.png")
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
		val borderUpdater = helperText
			.substringAfter("export const updateReaderSurfaceBorderOverlayLayer = (layer, borderOverlayVariant, settings, scrollOffset = null) =>")
			.substringBefore("\n\nexport const readerPageNumberLayerStyle")

		assertContains(textureOpacity, "case ReaderThemeSepia:")
		assertContains(textureOpacity, "return '0.42'")
		assertFalse(
			textureOpacity.contains("return '0.14'"),
			"Sepia paper pores were too subtle at 0.14 and looked absent on-device."
		)
		assertFalse(
			textureOpacity.contains("case ReaderThemeSepia:\n      return '0.24'"),
			"Sepia paper pores were still too subtle at 0.24 on-device."
		)
		assertContains(helperText, "readerSurfacePageBorderOverlayBackgroundImage")
		assertContains(borderUpdater, "'background-image': readerSurfacePageBorderOverlayBackgroundImage(borderOverlayVariant)")
		assertContains(
			helperText,
			"[textureUrl, textureUrl].join(', ')",
			message = "The edge degradation source PNGs have intentionally subtle alpha; compositing twice keeps one surface layer while making the borders visible."
		)
		assertContains(borderUpdater, "filter: readerSurfacePageBorderOverlayFilter(settings)")
		assertContains(helperText, "export const readerSurfacePageBorderOverlayFilter = settings =>")
		assertContains(helperText, "contrast(1.35) saturate(1.08)")
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
			.substringBefore("\n  applyReaderDirection")

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
	fun androidReaderMirrorsPaperTextureOnTopLevelSurface() {
		val bridgeText = readerBridgeText()
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val surfaceTextureUpdater = bridgeText
			.substringAfter("updateSurfacePaperTexture(detail = {}, pagePosition = null) {")
			.substringBefore("\n  applyReaderDirection")
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
		assertContains(bridgeText, "updateReaderSurfaceTextureLayer")
		assertContains(bridgeText, "readerRoot.append(layer)")
		assertContains(bridgeText, "data-navic-surface-paper-texture-layer")
		assertContains(bridgeText, "readerSurfacePaperTextureOpacity")
		assertContains(bridgeText, "this.updateSurfacePaperTexture")
		assertFalse(
			surfaceTextureUpdater.contains("if (this.view?.isFixedLayout !== true)"),
			"The paper texture must cover the reader window for EPUB and fixed-layout content."
		)
		assertContains(surfaceTextureUpdater, "readerRoot.dataset.navicSurfacePaperTextureAsset")
		assertFalse(
			surfaceLayerUpdater.contains("readerPaperTextureBackgroundImage(textureVariant, settings)"),
			"The top-level surface texture must stay subtle and must not reuse the stacked document texture overlay."
		)
		assertContains(applySettings, "this.renderSurfacePaperTextureLayers()")
		assertContains(onLoad, "this.updateReaderPageNumberLayer()")
		assertContains(
			bridgeText,
			"this.updateSurfacePaperTexture(detail, pagePosition)",
			message = "Committed location posts should refresh the surface texture with the same page position used for numbering."
		)
		assertContains(bridgeText, "position: 'fixed'")
		assertContains(bridgeText, "'pointer-events': 'none'")
	}

	@Test
	fun androidReaderKeepsPaperTextureAtReaderWindowSurfaceOnly() {
		val bridgeText = readerBridgeText()
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val documentThemeCss = bridgeText
			.substringAfter("const readerDocumentThemeCss = settings =>")
			.substringBefore("const readerContentCss = settings =>")
		val applyDocumentTheme = bridgeText
			.substringAfter("applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {")
			.substringBefore("\n  applyReaderDirection")
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
			"Texture must not be injected into individual EPUB documents."
		)
		assertFalse(
			documentThemeCss.contains("background-image: var(--reader-paper-texture-image)"),
			"Document backgrounds should stay solid theme colors; texture belongs to the reader window."
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
		assertFalse(applyDocumentTheme.contains("'background-image': readerPaperTextureBackgroundImage"))
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
		assertContains(surfaceLayerUpdater, "'background-image': textureUrl")
		assertContains(surfaceLayerUpdater, "opacity: readerSurfacePaperTextureOpacity(settings)")
		assertContains(surfaceLayerUpdater, "'pointer-events': 'none'")
	}

	@Test
	fun androidReaderSurfaceSwipeGestureStaysSeparateFromReadableTapZones() {
		val bridgeText = readerBridgeText()
		val surfaceGesture = bridgeText
			.substringAfter("attachSurfaceTapGesture(element) {")
			.substringBefore("\n  readerTapZoneActionForPoint")

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
	fun androidReaderSyncsSurfaceTextureWithPaginatorScrollDrags() {
		val bridgeText = readerBridgeText()
		val surfaceLayerUpdater = bridgeText
			.substringAfter("const updateReaderSurfaceTextureLayer = (layer, textureVariant, settings")
			.substringBefore("\n\nconst isParagraphCandidate")
		val runtimeFields = bridgeText
			.substringAfter("class NavicReaderRuntime {")
			.substringBefore("\n  constructor()")

		assertContains(bridgeText, "attachSurfacePaperTextureScrollSync")
		assertContains(bridgeText, "syncSurfacePaperTextureScrollOffset")
		assertContains(bridgeText, "surfacePaperTextureScrollOffset")
		assertContains(bridgeText, "renderer.containerPosition")
		assertContains(bridgeText, "renderer.addEventListener('scroll'")
		assertContains(runtimeFields, "surfacePaperTextureBaseOffset")
		assertContains(runtimeFields, "surfaceTextureScrollOffset")
		assertContains(bridgeText, "surfacePaperTextureDiagnosticState(reason = 'scroll')")
		assertContains(bridgeText, "position: this.currentRendererContainerPosition()")
		assertContains(bridgeText, "baseOffset: this.surfacePaperTextureBaseOffset")
		assertContains(bridgeText, "delta: position - this.surfacePaperTextureBaseOffset")
		assertContains(bridgeText, "pageTurnDirection: this.surfacePaperTextureTurnDirection || this.pageTurnDirection || ''")
		assertContains(bridgeText, "textureKey: readerRoot.dataset.navicSurfacePaperTextureKey || ''")
		assertContains(bridgeText, "readerTrace('texture:scroll', diagnostic)")
		assertContains(
			bridgeText,
			"const expectedDirectionSign = pageTurnDirection === 'next' ? 1 : -1",
			message = "Texture movement needs the logical turn sign for normal in-flight movement."
		)
		assertContains(bridgeText, "hasKnownDirection &&")
		assertContains(
			bridgeText,
			"? expectedDirectionSign * Math.min(maxOffset, Math.abs(delta))",
			message = "Known next/previous turns must dominate renderer coordinate wraps so area transitions do not invert texture motion."
		)
		assertFalse(
			bridgeText.contains("const knownDirectionBoundaryWrap ="),
			"Known-direction boundary-wrap detection codified the maps-to-Author's-Note inversion as expected behavior."
		)
		assertContains(bridgeText, "? { x: 0, y: -signedOffset }")
		assertContains(bridgeText, ": { x: -signedOffset, y: 0 }")
		assertContains(surfaceLayerUpdater, "readerPaperTextureBackgroundPosition(scrollOffset)")
	}

	@Test
	fun androidReaderKeepsTextureTurnDirectionUntilCommittedTextureUpdate() {
		val bridgeText = readerBridgeText()
		val runtimeFields = bridgeText
			.substringAfter("class NavicReaderRuntime {")
			.substringBefore("\n  constructor()")
		val startPageTurn = bridgeText
			.substringAfter("startPageTurn(direction) {")
			.substringBefore("\n  startNextQueuedPageTurn")
		val surfaceOffset = bridgeText
			.substringAfter("surfacePaperTextureScrollOffset() {")
			.substringBefore("\n  surfacePaperTextureDiagnosticState")
		val surfaceTextureUpdate = bridgeText
			.substringAfter("updateSurfacePaperTexture(detail = {}, pagePosition = null) {")
			.substringBefore("\n  applyReaderDirection")

		assertContains(
			runtimeFields,
			"surfacePaperTextureTurnDirection = null",
			message = "Texture motion needs a direction state that can outlive pageTurnPromise settlement at area boundaries."
		)
		assertContains(
			startPageTurn,
			"this.surfacePaperTextureTurnDirection = direction",
			message = "Every explicit next/previous action must seed texture movement direction before Foliate emits delayed scroll/relocate events."
		)
		assertContains(
			surfaceOffset,
			"pageTurnDirection: this.surfacePaperTextureTurnDirection || this.pageTurnDirection",
			message = "Texture offset must prefer the sticky surface direction, not only the transient pageTurnDirection cleared in finally."
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
		val textureKey = bridgeText
			.substringAfter("const readerPaperTextureVariantKey = (publicationUrl, section, index, detail = {}) =>")
			.substringBefore("\n\nconst readerPaperTextureVariantForPage")
		val surfaceTextureUpdater = bridgeText
			.substringAfter("updateSurfacePaperTexture(detail = {}, pagePosition = null) {")
			.substringBefore("\n  applyReaderDirection")

		assertContains(bridgeText, "readerPaperTexturePageLocator")
		assertContains(bridgeText, "detail?.cfi")
		assertContains(bridgeText, "detail?.fraction ?? detail?.progress ?? detail?.totalProgress")
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
	fun readerHarnessTextureFrontmatterTransitionTargetsVisibleAuthorNoteBoundary() {
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
		assertContains(frontmatterMode, "AUTHOR")
		assertContains(frontmatterMode, "author-note-boundary")
		assertFalse(
			frontmatterMode.contains("while (Number(currentLocation?.pageIndex) < 4)"),
			"Texture frontmatter coverage must seek the real visible Author's Note boundary, not hard-code a shallow page index."
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
			"performReaderTouchDrag",
			message = "The texture boundary harness must exercise real drag-driven Foliate page turns, not only bridge nextPage commands."
		)
		assertContains(
			frontmatterMode,
			"drag-author-note-boundary",
			message = "The maps/frontmatter -> Author's Note texture inversion must have a named drag probe for phone-equivalent behavior."
		)
		assertContains(
			frontmatterMode,
			"drag-post-author-note-boundary",
			message = "The texture harness must prove texture direction remains correct after the frontmatter boundary, not just while entering it."
		)
		assertContains(
			frontmatterMode,
			"drag-reverse-author-note-boundary",
			message = "The texture harness must reproduce the phone-side reverse transition from chapter content back into Author's Note."
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
			"payload.pageTurnDirection === 'next' && Number.isFinite(xOffset) && xOffset > 1",
			message = "Trace assertions must reject positive next offsets even when renderer coordinates wrap."
		)
		assertContains(
			assertions,
			"movedSamples.find(({ textureDelta }) => textureDelta > 1)",
			message = "Real page-turn samples must reject forward texture inversion without exempting renderer wraps."
		)
		assertContains(
			frontmatterMode,
			"assertTextureTracePayloadsTrackTurnDirection(result.trace)",
			message = "The Author's Note boundary harness must fail if texture:scroll payloads invert after the boundary."
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
