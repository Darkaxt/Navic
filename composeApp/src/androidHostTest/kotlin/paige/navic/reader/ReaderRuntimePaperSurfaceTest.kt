package paige.navic.reader

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimePaperSurfaceTest {
	private fun requiredSliceAfter(
		actual: String,
		delimiter: String,
		description: String
	): String {
		assertTrue(
			actual.contains(delimiter),
			"Missing source marker for $description: $delimiter"
		)
		return actual.substringAfter(delimiter)
	}

	@Test
	fun readerPageShellPrototypeIsTrackedAndCaptureFriendly() {
		val prototype = listOf(
			File("docs/superpowers/prototypes/reader-page-shell/index.html"),
			File("../docs/superpowers/prototypes/reader-page-shell/index.html")
		).firstOrNull { it.isFile }
		val readme = listOf(
			File("docs/superpowers/prototypes/reader-page-shell/README.md"),
			File("../docs/superpowers/prototypes/reader-page-shell/README.md")
		).firstOrNull { it.isFile }

		assertTrue(
			prototype?.isFile == true,
			"Reader page-shell prototype must be tracked before production shell changes continue."
		)
		assertTrue(
			readme?.isFile == true,
			"Reader page-shell prototype must document capture and acceptance steps."
		)

		val html = prototype.readText()
		assertContains(html, "data-mode=\"spread\"")
		assertContains(html, "data-mode=\"portrait\"")
		assertContains(html, "data-mode=\"cover\"")
		assertContains(html, "data-capture")
		assertContains(html, "paper-texture-toggle")
		assertContains(html, "edge-width")
		assertContains(html, "back-cover-plane")
		assertContains(html, "foreground-cover")
		assertContains(html, "diffuse-cover-backdrop")
		assertFalse(
			html.contains("chrome-profile"),
			"The tracked prototype must not depend on local Chrome profile folders."
		)
	}

	@Test
	fun readerPageShellPrototypeUsesSharedGeometryVisualRules() {
		val prototype = listOf(
			File("docs/superpowers/prototypes/reader-page-shell/index.html"),
			File("../docs/superpowers/prototypes/reader-page-shell/index.html")
		).firstOrNull { it.isFile }
			?: error("Reader page-shell prototype must be tracked")
		val html = prototype.readText()

		assertContains(html, "--gutter-width")
		assertContains(html, "--edge-width")
		assertContains(html, "--edge-opacity")
		assertContains(html, "--shell-padding")
		assertContains(html, ".spread-shell")
		assertContains(html, "url(\"assets/shell-paper-warm.jpg\")")
		assertContains(html, "url(\"assets/page-edge-wear.png\")")
		assertContains(html, "url(\"assets/page-edge-rim.png\")")
		assertContains(html, "url(\"assets/page-stain-overlay.png\")")
		assertContains(html, "background: transparent")
		assertContains(html, "object-fit: contain")
		assertContains(html, ".back-cover-plane")
		assertContains(html, ".diffuse-cover-backdrop")
		assertContains(html, "grid-template-columns: minmax(0, 1fr) var(--gutter-width) minmax(0, 1fr)")
		assertContains(html, "grid-template-columns: var(--gutter-width) minmax(0, 1fr)")
		assertFalse(
			html.contains("radial-gradient(circle at 2% 3%"),
			"Prototype edge wear must not paint visible circular corner dots; those read as holes/windows instead of worn paper."
		)
		assertFalse(
			html.contains("radial-gradient(circle at 98% 97%"),
			"Prototype edge wear must not paint visible circular corner dots; those read as holes/windows instead of worn paper."
		)
		assertFalse(
			html.contains("radial-gradient(circle"),
			"The page-shell prototype must not use circular radial gradients. They read as windows/holes instead of a continuous ebook surface."
		)
		assertTrue(
			html.indexOf("diffuse-cover-backdrop") < html.indexOf("foreground-cover"),
			"Cover backdrop must be declared below the foreground cover in the rendered stack."
		)
		assertFalse(
			html.contains("coffee"),
			"Edge wear must be represented as a narrow tunable wear layer, not a broad border stain."
		)
	}

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
	fun agedPaperIntensityUsesTheSharedWarmPaperSemantic() {
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()

		assertContains(helperText, "readerThemeUsesWarmPaperTreatment")
		assertFalse(
			helperText.contains("ReaderThemeSepia ||") || helperText.contains("ReaderThemeAgedPaper ||"),
			"Paper composition must not duplicate warm-theme pair checks."
		)
	}

	@Test
	fun pageBoxProbeReportsTheNativeFoliateGapAndDocumentColumnGap() {
		val probe = listOf(
			File("tools/reader-harness/src/adb-webview-eval.mjs"),
			File("../tools/reader-harness/src/adb-webview-eval.mjs"),
		).firstOrNull { it.isFile }?.readText()
			?: error("Could not locate the ADB WebView probe")

		assertContains(probe, "gap: renderer.getAttribute('gap') || ''")
		assertContains(probe, "columnGap")
	}

	@Test
	fun landscapeDecorationUsesExplicitOuterEdgesAndCoverTint() {
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val appearanceText = readerAssetRoot().resolve("navic-reader-appearance.js").readText()
		val shellCoverText = readerAssetRoot().resolve("navic-reader-shell-cover.js").readText()
		val bridgeText = readerCommonFile("ReaderBridgeProtocol.kt").readText()
		val engineText = readerCommonFile("ReaderEngine.kt").readText()
		val engineHostText = readerCommonFile("ReaderEngineHostProtocol.kt").readText()
		val openRequestText = readerCommonUiFile("ReaderOpenRequest.kt").readText()
		val readerScreenText = readerCommonUiFile("ReaderScreen.kt").readText()
		val runtimeHostText = readerAndroidFile("ReaderPublicationRuntimeHost.android.kt").readText()
		val resourceText = readerAndroidPackageFile("ReaderPublicationResource.android.kt").readText()
		val webViewHostText = readerEngineWebViewHostFile().readText()

		assertContains(helperText, "export const readerSurfacePageDecorationGeometry = ({")
		assertContains(helperText, "outerInsetPercent = gapPercent / 2")
		assertContains(helperText, "pageWidthPercent = 50 - outerInsetPercent")
		assertContains(helperText, "backCoverRevealPercent = boundedSpread ? outerInsetPercent : portraitRevealPercent")
		assertContains(helperText, "backCoverStartPercent = 0")
		assertContains(helperText, "backCoverVisible")
		assertContains(helperText, "shellCoverVisible !== true")
		assertContains(helperText, "data-navic-surface-back-cover-plane")
		assertContains(appearanceText, "foliateGap: pageBox.foliateGap")
		assertContains(appearanceText, "shellCoverVisible: this.shellCoverVisible")
		assertContains(appearanceText, "coverTint: this.shellCoverDominantColor")
		assertContains(appearanceText, "this.surfacePageDecorationGeometry")
		assertContains(shellCoverText, "readerDominantCoverColorFromBlob(blob)")
		assertContains(shellCoverText, "createImageBitmap(blob")
		assertContains(shellCoverText, "this.shellCoverDominantColor = color")
		assertContains(helperText, "geometry.coverTint")
		assertContains(resourceText, "shellCoverTint: String? = null")
		assertContains(resourceText, "withShellCoverTint()")
		assertContains(runtimeHostText, "resolved.shellCoverTint")
		assertContains(runtimeHostText, "shellCoverTint=")
		assertContains(runtimeHostText, "resolved.shellCoverTint.isNullOrBlank()")
		assertContains(readerScreenText, "shellCoverTint")
		assertContains(openRequestText, "shellCoverTint: String?")
		assertContains(openRequestText, "nativeShellCoverTint = shellCoverTint")
		assertContains(engineText, "val nativeShellCoverTint: String? = null")
		assertContains(engineHostText, "nativeShellCoverTint = viewState.nativeShellCoverTint")
		assertContains(webViewHostText, "nativeShellCoverTint = nativeShellCoverTint")
		assertContains(webViewHostText, "tint=")
		assertContains(webViewHostText, "nativeShellCoverTint.isNullOrBlank()")
		assertContains(bridgeText, "val nativeShellCoverTint: String? = null")
		assertContains(bridgeText, "nativeShellCoverTint?.let { put(\"nativeShellCoverTint\", it) }")
		assertFalse(
			helperText.contains("data-navic-static-paper-shell"),
			"Balanced page decoration must reuse overlay bounds and must not recreate shell geometry."
		)
	}

	@Test
	fun landscapeContentMarginsDoNotWidenBackCoverReveal() {
		val typographyText = readerAssetRoot().resolve("navic-reader-typography.js").readText()
		val viewportText = readerAssetRoot().resolve("navic-reader-viewport.js").readText()
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val paginatorText = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
		val probeText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()

		assertContains(typographyText, "export const readerResolvedFoliateGap")
		assertContains(typographyText, "? '2%'")
		assertContains(typographyText, "export const readerResolvedFoliateContentGap")
		assertContains(typographyText, "? '6%'")
		assertContains(typographyText, "foliateContentGap: readerResolvedFoliateContentGap({")
		assertContains(viewportText, "renderer.setAttribute('content-gap', resolvedContentGap)")
		assertContains(viewportText, "renderer.removeAttribute('content-gap')")
		assertContains(paginatorText, "'flow', 'gap', 'content-gap', 'margin'")
		assertContains(paginatorText, "--_content-gap: var(--_gap);")
		assertContains(paginatorText, "style.getPropertyValue('--_content-gap')")
		assertContains(probeText, "contentGap: renderer.getAttribute('content-gap') || ''")
		assertContains(probeText, "paddingLeft: documentElementStyle?.paddingLeft || ''")
		assertContains(probeText, "paddingRight: documentElementStyle?.paddingRight || ''")
		assertContains(helperText, "backCoverRevealPercent = boundedSpread ? outerInsetPercent : portraitRevealPercent")
		assertFalse(
			helperText.contains("backCoverRevealPercent = contentGapPercent"),
			"Increasing text margins must not widen the external back-cover reveal."
		)
	}

	@Test
	fun portraitPaperCompositionDoesNotInheritLandscapeGeometry() {
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val appearanceText = readerAssetRoot().resolve("navic-reader-appearance.js").readText()
		val typographyText = readerAssetRoot().resolve("navic-reader-typography.js").readText()
		val viewportText = readerAssetRoot().resolve("navic-reader-viewport.js").readText()

		assertContains(helperText, "export const readerPaperLayoutProfile = ({")
		assertContains(helperText, "? { mode: 'spread', bindingEdge: 'center' }")
		assertContains(helperText, "flowMode === ReaderFlowPaged ? 'right' : 'none'")
		assertContains(helperText, ": { mode: 'single', bindingEdge: 'left', backCoverEdge }")
		assertContains(helperText, "export const readerPortraitBindingHintBoxShadow = (settings, profile) =>")
		assertContains(helperText, "profile?.mode !== 'single'")
		assertContains(helperText, "settings?.pageEdgesEnabled === false")
		assertContains(helperText, "'box-shadow': readerPortraitBindingHintBoxShadow(settings, layoutProfile)")
		assertContains(appearanceText, "this.surfacePaperLayoutProfile = readerPaperLayoutProfile({")
		assertContains(appearanceText, "spreadMode: this.surfaceSpreadMode")
		assertContains(appearanceText, "layoutProfile: this.surfacePaperLayoutProfile")
		assertContains(typographyText, "width >= height * 1.12")
		assertContains(typographyText, "columnCount >= 2")
		assertContains(viewportText, "renderer.removeAttribute('gap')")
		assertContains(viewportText, "renderer.removeAttribute('content-gap')")
		assertContains(helperText, "const portraitRevealPercent = portraitSingle ? 1 : 0")
		assertContains(helperText, "const backCoverEdge = boundedSpread ? 'both' : portraitSingle ? 'right' : 'none'")
		assertContains(helperText, "width: portraitSingle ? readerPercentValue(100 - portraitRevealPercent) : '100%'")
		assertContains(helperText, "geometry.backCoverEdge === 'right'")
		assertContains(helperText, "readerReadableCoverTintChannels(coverTint)")
		assertContains(helperText, "export const readerSurfaceBackCoverPalette = (settings, coverTint) =>")
		assertContains(helperText, "const coverPalette = readerSurfaceBackCoverPalette(settings, geometry.coverTint)")
		assertFalse(
			helperText
				.substringAfter("export const readerSurfaceBackCoverBackground")
				.substringBefore("export const readerSurfacePageDecorationBackground")
				.contains("readerDesaturatedColorChannels(geometry.coverTint"),
			"Orientation-specific back-cover gradients must not independently desaturate the sampled cover hue."
		)
		assertContains(helperText, "const minimumLightness = 0.22")
		assertContains(helperText, "const minimumSaturation = 0.38")
		assertContains(helperText, "readerRgba(coverPalette.highlight, 0.72)")
		assertContains(helperText, "readerRgba(coverPalette.edge, 0.96)")
		assertContains(helperText, "readerRgba(coverPalette.base, 0.90)")
		assertContains(helperText, "readerRgba(coverPalette.outer, 0.98)")
		assertContains(helperText, "if (spreadMode !== 'spread') return textureSlots || []")
		assertFalse(
			helperText.contains("renderer.style.width") || helperText.contains("renderer.setAttribute('gap', '1%')"),
			"Portrait cover decoration must not resize the Foliate renderer or emulate a page gap."
		)
	}

	@Test
	fun textureProbeReportsResolvedReaderGeometryAndDecorationState() {
		val probeText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()

		assertContains(probeText, "const readerRoot = document.body")
		assertContains(probeText, "const requestedSettings = { theme: 'sepia', ...(probeSettings || {}) }")
		assertContains(probeText, "const applySettings = window.NavicReaderBridge.dispatch({")
		assertContains(probeText, "applySettings.catch(() => {})")
		assertContains(probeText, "requestAnimationFrame(() => requestAnimationFrame(resolve))")
		assertContains(probeText, "const view = document.querySelector('foliate-view')")
		assertContains(probeText, "const renderer = view?.renderer")
		assertContains(probeText, "theme:")
		assertContains(probeText, "spreadMode:")
		assertContains(probeText, "foliateGap: renderer?.getAttribute?.('gap') || ''")
		assertContains(probeText, "foliateContentGap: renderer?.getAttribute?.('content-gap') || ''")
		assertContains(probeText, "paperTextureEnabled: effectiveSettings.paperTextureEnabled !== false")
		assertContains(probeText, "pageEdgesEnabled: effectiveSettings.pageEdgesEnabled !== false")
		assertContains(probeText, "paperStainsEnabled: effectiveSettings.paperStainsEnabled !== false")
		assertContains(probeText, "portraitBindingHintPresent:")
		assertContains(probeText, "landscapeGutterPresent:")
		assertContains(probeText, "backCoverEdge:")
		assertContains(probeText, "backCoverTint: readerRoot.dataset.navicShellCoverDominantColor || ''")
		assertContains(probeText, "backCoverRevealPixels:")
		assertContains(probeText, "backCoverRevealPercent:")
		assertContains(probeText, "surfaceTextureAsset: readerRoot.dataset.navicSurfacePaperTextureAsset || ''")
		assertContains(probeText, "surfaceBorderOverlayAsset: readerRoot.dataset.navicSurfaceBorderOverlayAsset || ''")
		assertFalse(
			probeText.contains("document.documentElement.dataset.navicSurfacePaperTextureAsset"),
			"The reader surface datasets live on document.body, not document.documentElement."
		)
	}

	@Test
	fun androidReaderPackagesHighResolutionPageEffectOverlayVariants() {
		val root = readerAssetRoot()
		val bridgeText = readerBridgeText(root)
		val edgeOverlays = (1..8).map { index ->
			root.resolve("paper-textures/page-edge-overlay-${index.toString().padStart(2, '0')}.png")
		}
		val edgeWearOverlays = (1..8).map { index ->
			root.resolve("paper-textures/page-edge-wear-overlay-${index.toString().padStart(2, '0')}.png")
		}
		val edgeRimOverlays = (1..8).map { index ->
			root.resolve("paper-textures/page-edge-rim-overlay-${index.toString().padStart(2, '0')}.png")
		}
		val stainOverlays = (1..8).map { index ->
			root.resolve("paper-textures/page-stain-overlay-${index.toString().padStart(2, '0')}.png")
		}
		val gutterOverlays = (1..4).map { index ->
			root.resolve("paper-textures/spread-gutter-overlay-${index.toString().padStart(2, '0')}.png")
		}
		val gutterHighlightOverlays = (1..4).map { index ->
			root.resolve("paper-textures/spread-gutter-highlight-overlay-${index.toString().padStart(2, '0')}.png")
		}

		val overlayVisibilityChecks = edgeOverlays.map { it to 0.5 } +
			edgeWearOverlays.map { it to 0.5 } +
			edgeRimOverlays.map { it to 0.5 } +
			stainOverlays.map { it to 0.5 } +
			gutterOverlays.map { it to 0.5 } +
			gutterHighlightOverlays.map { it to 0.25 }
		overlayVisibilityChecks.forEach { (overlay, minimumAverageAlpha) ->
			assertTrue(overlay.isFile, "${overlay.name} must be packaged")
			assertTrue(overlay.length() > 20_000, "${overlay.name} should be a real 4K-class raster overlay")
			assertTrue(overlay.hasPngAlphaChannel(), "${overlay.name} must be transparent")
			val image = ImageIO.read(overlay) ?: error("Could not read ${overlay.name}")
			assertTrue(
				image.width >= 3840 && image.height >= 2160,
				"${overlay.name} should be at least 3840x2160 for tablet landscape rendering"
			)
			assertTrue(overlay.averagePngAlpha() >= minimumAverageAlpha, "${overlay.name} must be visible at runtime")
			assertTrue(overlay.maxPngAlpha() <= 120, "${overlay.name} should remain a subtle overlay")
		}
		edgeOverlays.forEachIndexed { index, overlay ->
			assertTrue(
				overlay.outerEdgeAlphaHighFrequencyPercent() <= 12.0,
				"Reader page edge overlay ${index + 1} must not contain baked checkerboard artifacts"
			)
		}
		assertContains(bridgeText, "ReaderPageBorderOverlayAssets")
		assertContains(bridgeText, "ReaderPageEdgeWearOverlayAssets")
		assertContains(bridgeText, "ReaderPageEdgeRimOverlayAssets")
		assertContains(bridgeText, "ReaderSpreadGutterOverlayAssets")
		assertContains(bridgeText, "ReaderSpreadGutterHighlightOverlayAssets")
		assertFalse(
			bridgeText.contains("ReaderSpreadGutterOverlayAssets = ReaderPageEdgeOverlayAssets"),
			"Spread gutter overlays need their own center-crease texture family, not an alias to page edges."
		)
		edgeOverlays.forEach { overlay -> assertContains(bridgeText, "paper-textures/${overlay.name}") }
		edgeWearOverlays.forEach { overlay -> assertContains(bridgeText, "paper-textures/${overlay.name}") }
		edgeRimOverlays.forEach { overlay -> assertContains(bridgeText, "paper-textures/${overlay.name}") }
		stainOverlays.forEach { overlay -> assertContains(bridgeText, "paper-textures/${overlay.name}") }
		gutterOverlays.forEach { overlay -> assertContains(bridgeText, "paper-textures/${overlay.name}") }
		gutterHighlightOverlays.forEach { overlay -> assertContains(bridgeText, "paper-textures/${overlay.name}") }
		assertContains(bridgeText, "ReaderPageBorderOverlayVariantCount = ReaderPageBorderOverlayAssets.length * 2 * 2")
		assertContains(bridgeText, "ReaderSpreadGutterOverlayVariantCount = ReaderSpreadGutterOverlayAssets.length * 2 * 2")
		assertContains(bridgeText, "readerPageBorderOverlayVariantForPage")
		assertContains(bridgeText, "readerSpreadGutterOverlayVariantForPage")
		assertContains(bridgeText, "ReaderSurfacePageBorderOverlayLayerSelector")
		assertContains(bridgeText, "updateReaderSurfaceBorderOverlayLayer")
		assertContains(bridgeText, "updateReaderMovingPageSpreadGutterOverlayLayer")
		assertContains(bridgeText, "surfaceBorderOverlayAsset")
	}

	@Test
	fun androidReaderSplitsPageEffectOverlaysForIndependentSettings() {
		val root = readerAssetRoot()
		val bridgeText = readerBridgeText(root)
		val helperText = root.resolve("navic-reader-helpers.js").readText()
		val settingsDialogText = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt").readText()
		val protocolText = File("src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt").readText()

		for (index in 1..8) {
			val suffix = index.toString().padStart(2, '0')
			assertTrue(root.resolve("paper-textures/page-edge-overlay-$suffix.png").isFile)
			assertTrue(root.resolve("paper-textures/page-edge-wear-overlay-$suffix.png").isFile)
			assertTrue(root.resolve("paper-textures/page-edge-rim-overlay-$suffix.png").isFile)
			assertTrue(root.resolve("paper-textures/page-stain-overlay-$suffix.png").isFile)
			assertContains(bridgeText, "paper-textures/page-edge-overlay-$suffix.png")
			assertContains(bridgeText, "paper-textures/page-edge-wear-overlay-$suffix.png")
			assertContains(bridgeText, "paper-textures/page-edge-rim-overlay-$suffix.png")
			assertContains(bridgeText, "paper-textures/page-stain-overlay-$suffix.png")
		}
		for (index in 1..4) {
			val suffix = index.toString().padStart(2, '0')
			assertTrue(root.resolve("paper-textures/spread-gutter-overlay-$suffix.png").isFile)
			assertTrue(root.resolve("paper-textures/spread-gutter-highlight-overlay-$suffix.png").isFile)
			assertContains(bridgeText, "paper-textures/spread-gutter-overlay-$suffix.png")
			assertContains(bridgeText, "paper-textures/spread-gutter-highlight-overlay-$suffix.png")
		}
		assertContains(protocolText, "val paperTextureEnabled: Boolean? = null")
		assertContains(protocolText, "val pageEdgesEnabled: Boolean? = null")
		assertContains(protocolText, "val paperStainsEnabled: Boolean? = null")
		assertContains(protocolText, "val coverBackdropEnabled: Boolean? = null")
		assertContains(helperText, "settings?.paperTextureEnabled === false")
		assertContains(helperText, "settings?.pageEdgesEnabled === false")
		assertContains(helperText, "settings?.paperStainsEnabled === false")
		assertContains(helperText, "settings?.coverBackdropEnabled !== false")
		assertContains(helperText, "ReaderPageEdgeOverlayAssets")
		assertContains(helperText, "ReaderPageEdgeWearOverlayAssets")
		assertContains(helperText, "ReaderPageEdgeRimOverlayAssets")
		assertContains(helperText, "ReaderPageStainOverlayAssets")
		assertContains(helperText, "ReaderSpreadGutterOverlayAssets")
		assertContains(helperText, "ReaderSpreadGutterHighlightOverlayAssets")
		assertContains(helperText, "readerSurfaceSpreadGutterOverlayOpacity")
		assertContains(helperText, "updateReaderMovingPageSpreadGutterOverlayLayer")
		assertContains(helperText, "updateReaderSurfaceStainOverlayLayer")
		assertContains(settingsDialogText, "Paper texture")
		assertContains(settingsDialogText, "Page edges")
		assertContains(settingsDialogText, "Paper stains")
		assertContains(settingsDialogText, "Cover backdrop")
	}

	@Test
	fun androidReaderUsesDistinctTextureVariantSeedSuffixesAndSpreadModeGate() {
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val appearanceText = readerAssetRoot().resolve("navic-reader-appearance.js").readText()

		assertContains(helperText, "\${key}|paper-base")
		assertContains(helperText, "\${key}|page-edge")
		assertContains(helperText, "\${key}|page-stain")
		assertContains(helperText, "\${key}|spread-gutter")
		assertContains(helperText, "export const readerSurfaceSpreadMode = ({")
		assertContains(helperText, "flowMode === ReaderFlowScrolled")
		assertContains(helperText, "flowMode === ReaderFlowScrolledGaps")
		assertContains(helperText, "flowMode === ReaderFlowPagedVertical")
		assertContains(helperText, "width >= height * 1.12")
		assertContains(helperText, "export const readerSurfaceSpreadGutterVisible = ({")
		assertContains(helperText, "settings?.pageEdgesEnabled === false")
		assertContains(helperText, "spreadMode === 'spread'")
		assertContains(helperText, "readerSpreadPageTextureSlots = (")
		assertContains(helperText, "data-navic-surface-texture-slot-page")
		assertContains(helperText, "left")
		assertContains(helperText, "right")
		assertContains(appearanceText, "const spreadMode = readerSurfaceSpreadMode({")
		assertContains(appearanceText, "this.surfaceSpreadMode = spreadMode")
		assertContains(appearanceText, "readerSpreadPageTextureSlots(textureSlots, readerPaperTextureVariantForPage, spreadMode)")
		assertContains(appearanceText, "readerSurfaceSpreadGutterVisible({")
	}

	@Test
	fun androidReaderDoesNotFrameSpreadHalvesWithPageEffectOverlays() {
		val appearanceText = readerAssetRoot().resolve("navic-reader-appearance.js").readText()
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val surfaceTextureUpdate = appearanceText
			.substringAfter("function applySurfacePaperTextureUpdate(detail = {}, pagePosition = null)")
			.substringBefore("\nfunction shouldDeferSurfacePaperTextureUpdate")
		val borderLayerUpdater = helperText
			.substringAfter("export const updateReaderSurfaceBorderOverlayLayer =")
			.substringBefore("\n\nexport const updateReaderMovingPageBorderOverlayLayer")
		val stainLayerUpdater = helperText
			.substringAfter("export const updateReaderSurfaceStainOverlayLayer =")
			.substringBefore("\n\nexport const updateReaderMovingPageStainOverlayLayer")

		assertContains(surfaceTextureUpdate, "readerSpreadPageTextureSlots(textureSlots, readerPaperTextureVariantForPage, spreadMode)")
		assertContains(surfaceTextureUpdate, "readerSpreadPageTextureSlots(borderOverlaySlots")
		assertContains(surfaceTextureUpdate, "readerSpreadPageTextureSlots(stainOverlaySlots")
		assertFalse(
			surfaceTextureUpdate.contains("readerPageShellGeometry") ||
				surfaceTextureUpdate.contains("ensureReaderStaticPaperShell"),
			"Spread overlays may split visually, but must not reintroduce synthetic shell geometry."
		)
		assertContains(borderLayerUpdater, "const pages = slot.spreadPages?.length ? slot.spreadPages : [{ page: 'full', key: slot.key, variant: slot.variant }]")
		assertContains(stainLayerUpdater, "const pages = slot.spreadPages?.length ? slot.spreadPages : [{ page: 'full', key: slot.key, variant: slot.variant }]")
	}

	@Test
	fun androidReaderKeepsPaperTextureVisibleEnoughForSepiaTheme() {
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val settingsText = readerAssetRoot().resolve("navic-reader-settings-core.js").readText()
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
			.substringAfter("export const updateReaderSurfaceBorderOverlayLayer = (layer, borderOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '') =>")
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
			"return '0.64'",
			message = "Border overlay PNGs need to stay visible while avoiding the full-opacity framed page look."
		)
		assertFalse(
			borderOpacity.contains("return '1'"),
			"Full-opacity page edge overlays turn subtle wear into a decorative frame."
		)
		assertContains(borderUpdater, "for (const slot of borderOverlaySlots)")
		assertContains(borderUpdater, "'background-image': readerSurfacePageBorderOverlayBackgroundImage(page.variant)")
		assertContains(settingsText, "ReaderPageEdgeWearOverlayAssets")
		assertContains(settingsText, "ReaderPageEdgeRimOverlayAssets")
		assertContains(settingsText, "ReaderSpreadGutterHighlightOverlayAssets")
		assertContains(borderBackgroundImage, "readerPageEdgeWearOverlayAsset")
		assertContains(borderBackgroundImage, "readerPageEdgeRimOverlayAsset")
		assertContains(borderUpdater, "'background-blend-mode': 'multiply, screen'")
		assertFalse(
			borderBackgroundImage.contains("[textureUrl, textureUrl, textureUrl, textureUrl].join(', ')"),
			"Page edges must be decomposed into edge wear + rim light. Stamping one broad PNG four times reads as a border instead of worn paper."
		)
		assertContains(borderUpdater, "filter: readerSurfacePageBorderOverlayFilter(settings)")
		assertContains(helperText, "export const readerSurfacePageBorderOverlayFilter = settings =>")
		assertContains(helperText, "contrast(1.32) saturate(1.08) brightness(0.98)")
		assertFalse(
			helperText.contains("contrast(1.9) saturate(1.16) brightness(0.94)"),
			"Sepia edge overlays must stay softened; high-contrast borders read as framed holes/windows."
		)
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
			"readerPaperTextureBackgroundImage(page.variant)",
			message = "The moving page texture owner carries current and adjacent page slots, including independent spread pages."
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
	fun androidReaderDragAnimationModeIsExplicitAndDefaultsToNone() {
		val root = readerAssetRoot()
		val bridgeText = readerBridgeText(root)
		val pageTurnsText = root.resolve("navic-reader-page-turns.js").readText()
		val appearanceText = root.resolve("navic-reader-appearance.js").readText()
		val settingsText = File("src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt").readText()
		val chromeText = File("src/commonMain/kotlin/paige/navic/reader/ReaderChromeState.kt").readText()
		val settingsDialogText = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt").readText()

		assertContains(settingsText, "val dragAnimationMode: String? = null")
		assertContains(chromeText, "const val ReaderDragAnimationNone = \"none\"")
		assertContains(chromeText, "const val ReaderDragAnimationCanvas = \"canvas\"")
		assertContains(chromeText, "normalizedReaderDragAnimationMode")
		assertContains(chromeText, "dragAnimationMode = ReaderDragAnimationNone")
		assertContains(settingsDialogText, "title = \"Page turn\"")
		assertContains(settingsDialogText, "ReaderSupportedDragAnimationModes")
		assertContains(bridgeText, "\"dragAnimationMode\"")
		assertContains(appearanceText, "this.readerDragAnimationModeValue")
		assertContains(pageTurnsText, "readerDragAnimationModeAllowsCurl")
		assertContains(pageTurnsText, "data-navic-page-curl-sheet")
		assertContains(pageTurnsText, "data-navic-page-drag-preview-curl")
		assertContains(
			pageTurnsText,
			"if (curlEnabled)",
			message = "The retired DOM curl implementation must remain gated while the native Canvas renderer owns public Canvas mode."
		)
		assertContains(
			pageTurnsText,
			"element?.dataset?.navicPageCurlSheet === 'underneath'",
			message = "The retired preview implementation must keep its internal underlay contract until that dead code is removed separately."
		)
	}

	@Test
	fun readerHarnessProvesStandardDragModeDoesNotRetainCurlState() {
		val harnessText = sequenceOf(
			java.io.File("tools/reader-harness/src/run-reader-harness.mjs"),
			java.io.File("../tools/reader-harness/src/run-reader-harness.mjs")
		).first { it.isFile }.readText()

		assertContains(harnessText, "mode === 'epub-native-drag-standard-no-curl'")
		assertContains(
			harnessText,
			"dragAnimationMode: 'standard'",
			message = "The browser harness must exercise the release default, not only the opt-in curl path."
		)
		assertContains(
			harnessText,
			"dragAnimationMode: 'curl'",
			message = "The standard-mode probe must first create curl state so it can prove stale sheets are removed when returning to standard."
		)
		assertContains(
			harnessText,
			"previewState.curl === false",
			message = "Standard mode must explicitly assert that the preview layer reports curl=false."
		)
		assertContains(
			harnessText,
			"previewState.curlOnlySheetRoleCount === 0",
			message = "Standard mode must prove no mockup curl-only sheets remain on the drag preview layer while keeping the shared underlay."
		)
		assertContains(
			harnessText,
			"previewState.curlSnapshotCount === 0",
			message = "Standard mode must prove stale first-page curl snapshots are not retained."
		)
		assertContains(
			harnessText,
			"previewState.curlCssVarCount === 0",
			message = "Standard mode must prove curl CSS variables are cleared, not just hidden."
		)
		assertContains(
			harnessText,
			"epub-native-drag-standard-no-curl.failure.json",
			message = "The standard-vs-curl harness must write failure diagnostics; a raw wait timeout gives no root-cause evidence."
		)
		assertContains(
			harnessText,
			"reader harness epub-native-drag-standard-no-curl failure diagnostics",
			message = "The standard-vs-curl harness must print the artifact path when it fails so tablet-viewport failures are inspectable."
		)
		val standardNoCurl = harnessText
			.substringAfter("if (mode === 'epub-native-drag-standard-no-curl') {")
			.substringBefore("\nif (mode === 'epub-native-drag-preview-underlay') {")
		assertContains(
			standardNoCurl,
			"targetGlobalPageIndex",
			message = "The standard-vs-curl harness must seek an interior readable page before testing drag cleanup; frontmatter boundary pages create false tablet failures."
		)
		assertContains(
			standardNoCurl,
			"chapterPageCount > 3",
			message = "The standard-vs-curl harness must avoid one-page frontmatter sections before asserting drag-preview behavior."
		)
		assertContains(
			standardNoCurl,
			"chapterPageIndex > 0",
			message = "The standard-vs-curl harness must not prove curl cleanup from the first page of a chapter/section."
		)
		assertContains(
			standardNoCurl,
			"sectionPage < sectionPageCount - 1",
			message = "The standard-vs-curl harness must avoid section-boundary transition noise when testing standard preview cleanup."
		)
	}

	@Test
	fun readerHarnessUsesStandardModeForNativeDragSingleCommit() {
		val harnessText = sequenceOf(
			java.io.File("tools/reader-harness/src/run-reader-harness.mjs"),
			java.io.File("../tools/reader-harness/src/run-reader-harness.mjs")
		).first { it.isFile }.readText()
		val singleCommit = harnessText
			.substringAfter("if (mode === 'epub-native-drag-single-commit') {")
			.substringBefore("\nif (mode === 'epub-full-traversal') {")

		assertContains(
			singleCommit,
			"String(argValue('--drag-animation-mode') || 'standard').trim().toLowerCase()",
			message = "The native drag commit guard must keep Standard as the default when no debug drag mode is provided."
		)
		assertContains(
			singleCommit,
			"!['standard', 'curl'].includes(dragAnimationMode)",
			message = "The native drag commit guard may exercise Curl explicitly, but only through the debug harness argument."
		)
		assertContains(
			singleCommit,
			"dragAnimationMode,",
			message = "The native drag commit guard must pass the selected debug drag mode into the reader settings."
		)
		assertContains(
			singleCommit,
			"dragAnimationMode === 'standard' && !(previewVisual.curl === false)",
			message = "The standard single-commit guard must prove the preview layer is not using curl state."
		)
		assertContains(
			singleCommit,
			"dragAnimationMode === 'standard' && previewVisual.frontSnapshotPresent",
			message = "The standard single-commit guard must prove the preview layer is not using curl snapshots."
		)
		assertContains(
			singleCommit,
			"if (dragAnimationMode === 'curl')",
			message = "Curl-specific snapshot validation must be isolated to the explicit debug curl mode."
		)
		assertContains(
			singleCommit,
			"frontSnapshotMappedScrollX",
			message = "Curl debug validation must prove the front snapshot maps to the current rendered spread."
		)
		assertContains(
			singleCommit,
			"frameMappedScrollX",
			message = "Curl debug validation must prove the next-page preview frame maps to the adjacent rendered spread."
		)
		assertContains(
			singleCommit,
			"commitGlobalPageDelta !== 1",
			message = "The standard single-commit guard must keep proving release commits exactly one global page."
		)
	}

	@Test
	fun duplicateAdjacentPageTurnFallbackUsesViewNavigation() {
		val pageTurnsText = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val duplicateFallback = pageTurnsText
			.substringAfter("function handleDuplicatePageTurnRelocation(")
			.substringBefore("\nfunction nativeDragPreviewAtSectionBoundary")

		assertContains(
			duplicateFallback,
			"this.view?.goTo?.(targetIndex)",
			message = "Adjacent-section duplicate page-turn fallback must use Foliate view navigation so numeric section targets resolve through the same route as normal section navigation."
		)
		assertContains(
			duplicateFallback,
			"this.pageTurnAdjacentFallbackPromise = fallbackPromise",
			message = "Adjacent-section fallback navigation must be part of the active page-turn transaction instead of resolving after the caller has already sampled the old page."
		)
		assertFalse(
			duplicateFallback.contains("this.view?.renderer?.goTo"),
			"Adjacent-section fallback must not bypass Foliate view navigation with a raw renderer.goTo({ index }) call; that can leave stale same-section relocations at section boundaries."
		)

		val startPageTurn = pageTurnsText
			.substringAfter("function startPageTurn(direction) {")
			.substringBefore("\nfunction startNextQueuedPageTurn")
		assertContains(
			startPageTurn,
			"await adjacentFallback",
			message = "Page turns must wait for duplicate adjacent fallback navigation before settling the transaction."
		)
	}

	@Test
	fun standardDragPreviewDoesNotConstructCurlSnapshots() {
		val pageTurnsText = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val layerFactory = pageTurnsText
			.substringAfter("function ensurePageDragPreviewLayer")
			.substringBefore("\nfunction ensurePageDragPreviewLayerChild")
		val boundaryTarget = pageTurnsText
			.substringAfter("function ensurePageDragPreviewTarget")
			.substringBefore("\nfunction readerRendererPageStride")
		val interiorTarget = pageTurnsText
			.substringAfter("function ensureInteriorPageDragPreviewTarget")
			.substringBefore("\nfunction preloadPageDragPreviewTargets")

		assertContains(layerFactory, "curlEnabled = false")
		assertContains(
			layerFactory,
			"if (curlEnabled)",
			message = "The preview layer factory must not create curl sheets or snapshot iframes for Standard mode."
		)
		assertContains(
			layerFactory,
			"ensureSnapshot(turningFront, 'front')",
			message = "Curl snapshot iframes should still exist for explicit curl mode."
		)
		assertContains(
			boundaryTarget,
			"this.ensurePageDragPreviewLayer({ curlEnabled",
			message = "Boundary previews must request curl assets only when the active drag animation mode is curl."
		)
		assertContains(
			interiorTarget,
			"this.ensurePageDragPreviewLayer({ curlEnabled",
			message = "Interior previews must request curl assets only when the active drag animation mode is curl."
		)
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
			"dataset.navicStaticPaperBackingOwner = 'margin'",
			message = "The static backing is only a margin/fallback layer; Foliate owns the actual page geometry."
		)
		assertFalse(
			staticBacking.contains("ensureReaderStaticPaperShell") ||
				staticBacking.contains("readerStaticPaperShellFoldBackground") ||
				staticBacking.contains("data-navic-static-paper-shell"),
			"The rejected static paper shell must not come back through the backing layer."
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
	fun androidReaderLiveDragSharesOneDisplacementForTextPaperAndShadow() {
		val pageTurnText = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val appearanceText = readerAssetRoot().resolve("navic-reader-appearance.js").readText()

		// The lateral (non-curl) live-drag branch moves the text via renderer.scrollBy.
		val liveScrollBranch = pageTurnText
			.substringAfter("this.readerDragAnimationModeValue !== 'curl') {")
			.substringBefore("\n  this.updatePageDragPreviewLayer(")
		assertContains(
			liveScrollBranch,
			"renderer.scrollBy(",
			message = "The lateral live-drag path must keep moving the Foliate renderer so the text follows the finger."
		)
		// The paper texture + border-overlay shadow must ride the SAME accumulated
		// gesture delta, frame-locked to the scrollBy step, instead of the rescaled /
		// sign-overwritten / zero-clamped heuristic. That is what stops the texture
		// and shadows from drifting opposite or freezing relative to the text.
		assertContains(liveScrollBranch, "surfaceLiveDragOffset")
		assertContains(liveScrollBranch, "incrementalDelta.x")
		assertContains(
			liveScrollBranch,
			"this.syncMovingPageTextureSurface('live-drag')",
			message = "The texture surface must update in the same step as renderer.scrollBy so text and paper are frame-locked."
		)
		assertFalse(
			liveScrollBranch.contains("readerSurfacePaperTextureScrollOffset("),
			message = "The live-drag branch must not recompute the heuristic offset; it must ride the accumulated gesture delta."
		)
		// The heuristic is bypassed (not removed) for live drag so animated page turns keep their behavior.
		val surfaceOffsetFn = appearanceText
			.substringAfter("function surfacePaperTextureScrollOffset()")
			.substringBefore("\n\nfunction ")
		assertContains(
			surfaceOffsetFn,
			"this.surfaceLiveDragActive",
			message = "Live lateral drag must bypass the position-based heuristic so the texture is neither sign-overwritten nor zero-clamped."
		)
		assertContains(surfaceOffsetFn, "this.surfaceLiveDragOffset")
		assertContains(surfaceOffsetFn, "readerSurfacePaperTextureScrollOffset(")
		// Cancel + release must tear down the live-drag offset so the texture returns to center.
		assertContains(pageTurnText, "this.surfaceLiveDragActive = false")
		assertContains(pageTurnText, "this.surfaceLiveDragOffset = { x: 0, y: 0 }")
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
		assertContains(snapshotFrame, "applyPageDragPreviewDocumentOffset(snapshot, snapshotDoc, mappedScroll)")
		assertContains(snapshotSync, "const frontTargetScroll = pageDragCurrentRendererScroll(frontDoc")
		assertContains(snapshotSync, "targetScroll: frontTargetScroll")
		assertContains(snapshotSync, "layout: frontLayout")
		assertContains(
			snapshotSync,
			"viewSize: Number(renderer?.viewSize)",
			message = "Curl front snapshots must reproduce Foliate's paged layout width so renderer.start maps to the visible page instead of cloning chapter page 1."
		)
		val snapshotHtml = pageTurnText
			.substringAfter("function pageDragCurlSnapshotHtml")
			.substringBefore("\n\nfunction pageDragCurlSnapshotKey")
		assertContains(
			snapshotHtml,
			"'html{'",
			message = "Snapshot clones must apply Foliate's paged column geometry to the document element, matching paginator.js."
		)
		assertContains(
			snapshotHtml,
			"'body{'",
			message = "Snapshot clones may reset the body separately, but must not columnize body as a second paged container."
		)
		assertFalse(
			snapshotHtml.contains("'html,body{'"),
			"Snapshot clones must not apply paged column geometry to both html and body; that shifts preview text away from the committed Foliate page."
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
		assertContains(surfaceLayerUpdater, "const paperBase = readerSurfacePaperBaseBackground(")
		assertContains(surfaceLayerUpdater, "'background-image': paperBase.image === 'none'")
		assertContains(surfaceLayerUpdater, "readerPaperTextureBackgroundImage(page.variant)")
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
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val settingsText = readerAssetRoot().resolve("navic-reader-settings-core.js").readText()
		val surfaceLayerUpdater = bridgeText
			.substringAfter("const updateReaderSurfaceTextureLayer = (layer, textureVariant, settings")
			.substringBefore("\n\nconst isParagraphCandidate")
		val renderSurfacePaperTextureLayers = bridgeText
			.substringAfter("renderSurfacePaperTextureLayers() {")
			.substringBefore("\nfunction surfacePaperTextureIndex")
		val surfaceTextureUpdate = bridgeText
			.substringAfter("function applySurfacePaperTextureUpdate(detail = {}, pagePosition = null)")
			.substringBefore("\nfunction shouldDeferSurfacePaperTextureUpdate")
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
			settingsText,
			"ReaderSurfaceSpreadGutterOverlayLayerSelector",
			message = "Settled spread gutter needs a static root-layer selector; only defining a moving page selector recreates the panel/window failure."
		)
		assertContains(
			helperText,
			"export const ensureReaderSurfaceSpreadGutterOverlayLayer",
			message = "Reader appearance imports the static spread-gutter factory, so helpers must export it before ReaderDev can load."
		)
		assertContains(
			renderSurfacePaperTextureLayers,
			"const scrollOffset = this.surfacePaperTextureScrollOffset()",
			message = "The moving page texture render path must feed committed drag/scroll offset into both paper layers."
		)
		assertContains(renderSurfacePaperTextureLayers, "scrollOffset")
		assertFalse(
			renderSurfacePaperTextureLayers.contains("readerPageShellGeometry") ||
				renderSurfacePaperTextureLayers.contains("ensureReaderStaticPaperShell") ||
				renderSurfacePaperTextureLayers.contains("data-navic-static-paper-shell"),
			"Runtime paper rendering must stay decorative and must not recreate the synthetic shell."
		)
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
		assertContains(surfaceTextureUpdate, "let textureSlots = readerAdjacentPaperTextureSlots")
		assertContains(surfaceTextureUpdate, "let borderOverlaySlots = readerAdjacentPaperTextureSlots")
		assertContains(surfaceTextureUpdate, "this.surfaceTextureSlots = textureSlots")
		assertContains(surfaceTextureUpdate, "this.surfaceBorderOverlaySlots = borderOverlaySlots")
		assertContains(surfaceLayerUpdater, "for (const slot of textureSlots)")
		assertContains(surfaceLayerUpdater, "readerSurfaceTextureSlotTransform({")
		assertContains(surfaceLayerUpdater, "readerPaperTextureBackgroundPosition(null)")
		assertContains(surfaceLayerUpdater, "transform: readerSurfaceTextureSlotTransform({ slot: slot.slot, scrollOffset, width, height, flowMode, readerDirection })")
		assertContains(surfaceLayerUpdater, "transform: readerPaperTextureTransform(page.variant)")
		assertContains(borderLayerUpdater, "for (const slot of borderOverlaySlots)")
		assertContains(borderLayerUpdater, "readerSurfaceTextureSlotTransform({")
		assertContains(borderLayerUpdater, "readerPaperTextureBackgroundPosition(null)")
		assertContains(borderLayerUpdater, "transform: readerSurfaceTextureSlotTransform({ slot: slot.slot, scrollOffset, width, height, flowMode, readerDirection })")
		assertContains(borderLayerUpdater, "transform: readerPaperTextureTransform(page.variant)")
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
			message = "Section-boundary native readable drag previews must still route movement to the visual preview layer."
		)
		assertContains(
			previewPageDrag,
			"renderer.scrollBy(",
			message = "Same-section native readable drag previews must reuse Foliate's live strip instead of a synthetic iframe clone."
		)
		assertContains(
			previewPageDrag,
			"!readerDragAnimationModeAllowsCurl(this.readerDragAnimationModeValue)",
			message = "The retired live-strip implementation must no longer key behavior directly from the removed public curl value."
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
			.substringBefore("\n  if (!boundaryDirection && textureDirection &&")

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
			boundaryPreviewBlock.contains("renderer.scrollBy("),
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
		val ensureLayer = requiredSliceAfter(
			bridgeText,
			"function ensurePageDragPreviewLayer({ curlEnabled = false } = {}) {",
			"page drag preview layer factory"
		)
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
		val ensureLayer = requiredSliceAfter(
			bridgeText,
			"function ensurePageDragPreviewLayer({ curlEnabled = false } = {}) {",
			"page drag preview layer factory"
		)
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
