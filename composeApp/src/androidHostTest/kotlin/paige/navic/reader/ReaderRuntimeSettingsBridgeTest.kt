package paige.navic.reader

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimeSettingsBridgeTest {
	@Test
	fun androidReaderExposesKomikkuStyleTapZonePresets() {
		val bridgeText = readerBridgeText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()

		assertContains(bridgeText, "readerTapZoneMode")
		assertContains(bridgeText, "KomikkuNavigationRegionMenu")
		assertContains(bridgeText, "KomikkuNavigationRegionPrevious")
		assertContains(bridgeText, "KomikkuNavigationRegionNext")
		assertContains(bridgeText, "KomikkuNavigationRegionLeft")
		assertContains(bridgeText, "KomikkuNavigationRegionRight")
		assertContains(bridgeText, "komikkuConstantMenuRegion")
		assertContains(bridgeText, "komikkuRegionSize")
		assertContains(bridgeText, "komikkuNavigationRegions")
		assertContains(bridgeText, "komikkuTapAction")
		assertContains(bridgeText, "case ReaderTapZoneEdge")
		assertContains(bridgeText, "case ReaderTapZoneKindle")
		assertContains(bridgeText, "case ReaderTapZoneLShaped")
		assertContains(bridgeText, "case ReaderTapZoneRightLeft")
		assertContains(bridgeText, "case ReaderTapZoneDisabled")
		assertFalse(
			bridgeText.contains("CenterTapStartFraction") || bridgeText.contains("CenterTapEndFraction"),
			"Tap-zone dispatch must use the Komikku normalized-region model, not Navic's old center box thresholds."
		)
		assertContains(ebooksSettingsText, "readerTapZone")
		assertContains(ebooksSettingsText, "option_ebook_reader_tap_zone")
		assertContains(searchSettingsText, "ebooks.tap-zone")
	}

	@Test
	fun androidReaderUsesKomikkuRegionPriorityAndMenuFallback() {
		val bridgeText = readerBridgeText()
		val tapAction = bridgeText
			.substringAfter("const komikkuTapAction = (")
			.substringBefore("\n\nconst readerAssetUrl")

		assertContains(bridgeText, "smallerTapZone ? 0.25 : 0.33")
		assertFalse(
			bridgeText.contains("ReaderCenterMenuRegionSize") ||
				bridgeText.contains("readerCenterMenuRegion"),
			"Tap-zone dispatch must use Komikku region maps plus menu fallback, not Navic's old explicit center box."
		)
		assertFalse(
			bridgeText.contains("smallerTapZone ? 0.2 : 0.25"),
			"Komikku's normal/smaller tap bands are one third and one quarter of the reader surface."
		)
		assertTrue(
			tapAction.indexOf("regions.find") <
				tapAction.indexOf("komikkuConstantMenuRegion"),
			"Komikku checks explicit navigation regions before the constant menu fallback."
		)
		assertTrue(
			tapAction.indexOf("komikkuConstantMenuRegion") <
				tapAction.indexOf("return KomikkuNavigationRegionMenu"),
			"Unassigned areas must fall back to menu after explicit region checks."
		)
	}

	@Test
	fun androidReaderNormalizesReadableTapZonesThroughNativeReaderSurfaceLikeKomikku() {
		val runtimeText = readerBridgeText()
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val readerViewerText = listOf(
			File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewer.kt"),
			File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewer.kt")
		).first { it.isFile }.readText()
		val readerCoordinatorText = readerCommonFile("ReaderCoordinator.kt").readText()
		val foliateAdapterText = readerCommonFile("FoliateEpubEngineAdapter.kt").readText()

		assertContains(readerScreenText, "KomikkuReaderRoot(")
		assertContains(readerRootText, "KomikkuReaderNativeFrameHost(")
		assertContains(readerRootText, "navigator = navigator")
		assertContains(readerRootText, "readerShellCoverViewerActionFor(action)")
		assertContains(readerRootText, "viewer.viewerActionFor(action)")
		assertContains(nativeFrameHostText, "KomikkuReaderNativeViewerContainer")
		assertContains(nativeFrameHostText, "navigator.getAction(")
		assertContains(nativeFrameHostText, "onAction(action)")
		assertContains(readerViewerText, "fun readerViewerActionFor(")
		assertContains(readerViewerText, "readerTapZonePageTurnDirectionFor(")
		assertContains(readerScreenText, "fun handleEngineHostEvent(event: ReaderEngineHostEvent)")
		assertContains(readerScreenText, "onEngineHostEvent = { event -> handleEngineHostEvent(event) }")
		assertContains(readerScreenText, "applyCoordinatorStep(coordinator.onEngineHostEvent(event))")
		assertContains(readerCoordinatorText, "fun onEngineHostEvent(event: ReaderEngineHostEvent)")
		assertContains(foliateAdapterText, "override fun onHostEvent(event: ReaderEngineHostEvent)")
		assertContains(foliateAdapterText, "is ReaderEngineHostEvent.FoliateBridge -> onBridgeEvent(event.event)")
		assertContains(runtimeText, "attachReaderTapZoneGesture")
		assertContains(runtimeText, "komikkuTapAction(")
		assertContains(runtimeText, "readerTapZoneCommand(")
		assertFalse(
			webViewHostText.contains("ReaderSurfaceHost") ||
				webViewHostText.contains("dispatchReaderWideTap") ||
				webViewHostText.contains("readerTapZoneActionAt("),
			"Readable tap-zone normalization must be owned by the Komikku native frame, not the renderer WebView host."
		)
		assertFalse(
			readerScreenText.contains("ReaderBridgeEvent.CenterTap") || readerRootText.contains("ReaderBridgeEvent.CenterTap"),
			"ReaderScreen must not handle raw Foliate center taps; readable tap normalization belongs to the native surface and engine host boundary."
		)
		assertFalse(
			readerScreenText.contains("ReaderNativeTapOverlay(") || readerRootText.contains("ReaderNativeTapOverlay("),
			"Readable EPUB/PDF tap ownership must live in the Android reader surface, not a Compose sibling overlay."
		)
	}

	@Test
	fun androidReaderSurfaceObservesConfirmedTapsAfterChildDispatchLikeKomikku() {
		val readerScreenText = readerScreenFile().readText()
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val dispatchTouchEvent = nativeFrameHostText
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("\n\tprivate fun handleSwipeTouchEvent")

		assertContains(nativeFrameHostText, "private class KomikkuReaderNativeViewerContainer")
		assertContains(nativeFrameHostText, "override fun dispatchTouchEvent(event: MotionEvent): Boolean")
		assertContains(nativeFrameHostText, "val handled = super.dispatchTouchEvent(event)")
		assertContains(nativeFrameHostText, "handleSwipeTouchEvent(event)")
		assertContains(nativeFrameHostText, "gestureDetector.onTouchEvent(event)")
		assertContains(nativeFrameHostText, "val consumed = handled || nativeSwipeIntercepted || horizontalSwipeDispatched")
		assertFalse(
			nativeFrameHostText.contains("nativeShortTapIntercepted"),
			"Komikku's Pager does not use a separate short-tap intercept flag; confirmed tap ownership comes from GestureDetector after child dispatch."
		)
		assertContains(nativeFrameHostText, "return consumed")
		assertContains(nativeFrameHostText, "GestureDetector(context, listener)")
		assertContains(nativeFrameHostText, "override fun onSingleTapConfirmed(event: MotionEvent): Boolean")
		assertContains(nativeFrameHostText, "navigator.getAction(")
		assertContains(nativeFrameHostText, "ViewConfiguration.get(context).scaledTouchSlop")
		assertTrue(
			dispatchTouchEvent.indexOf("val handled = super.dispatchTouchEvent(event)") <
				dispatchTouchEvent.indexOf("gestureDetector.onTouchEvent(event)"),
			"Komikku's pager dispatches to the child/page first, then observes the same stream for confirmed reader-wide taps."
		)
		assertFalse(
			webViewHostText.contains("ReaderSurfaceHost") ||
				webViewHostText.contains("ReaderAndroidTapZoneObserver") ||
				webViewHostText.contains("dispatchReaderWideTap"),
			"Android must not keep the old split WebView-only tap-zone observer."
		)
		assertFalse(
			readerScreenText.contains("ReaderNativeTapRegion("),
			"Reader-wide tap zones must not be Compose region boxes over the WebView."
		)
	}

	@Test
	fun androidReaderDisablesJavaScriptReadableTapDispatchWhenNativeSurfaceOwnsTaps() {
		val runtimeText = readerBridgeText()
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val bridgeProtocolText = readerCommonFile("ReaderBridgeProtocol.kt").readText()
		val openPublication = runtimeText
			.substringAfter("async openPublication({")
			.substringBefore("\n  close()")
		val tapHandler = runtimeText
			.substringAfter("attachReaderTapZoneGesture(target) {")
			.substringBefore("\nfunction attachScrolledEdgeTurnGestures")

		assertContains(bridgeProtocolText, "val nativeTapZones: Boolean? = null")
		assertContains(bridgeProtocolText, "nativeTapZones?.let { put(\"nativeTapZones\", it) }")
		assertContains(webViewHostText, "settings.copy(nativeTapZones = true)")
		assertContains(runtimeText, "this.nativeTapZones = settings.nativeTapZones === true")
		assertContains(openPublication, "if (settings) this.applySettings(settings)")
		assertTrue(
			openPublication.indexOf("if (settings) this.applySettings(settings)") <
				openPublication.indexOf("await this.view.open(url)"),
			"Native tap-zone settings must be applied before Foliate loads content documents; otherwise onLoad can attach JS reader-wide tap handlers before Android owns taps."
		)
		assertContains(tapHandler, "if (this.nativeTapZones === true)")
		assertContains(tapHandler, "this.attachNativeTapZoneTouchSuppressor(target)")
		assertContains(tapHandler, "this.renderTapZoneOverlayLayer()")
		assertContains(
			tapHandler,
			"return",
			message = "Android native reader-surface ownership must disable JS reader-wide page/menu dispatch to avoid double page turns."
		)

		val touchSuppressor = runtimeText
			.substringAfter("function attachNativeTapZoneTouchSuppressor(target) {")
			.substringBefore("\nfunction handleReaderTapZoneTap")
		assertContains(touchSuppressor, "__navicNativeTapZoneTouchSuppressorAttached")
		assertContains(touchSuppressor, "this.nativeTapZones !== true")
		assertContains(touchSuppressor, "event.stopPropagation?.()")
		assertContains(touchSuppressor, "event.stopImmediatePropagation?.()")
		assertContains(touchSuppressor, "if (event.type === 'touchmove' && event.cancelable)")
		assertContains(touchSuppressor, "event.preventDefault?.()")
		assertContains(touchSuppressor, "target.addEventListener('touchstart'")
		assertContains(touchSuppressor, "target.addEventListener('touchmove'")
		assertContains(touchSuppressor, "target.addEventListener('touchend'")
		assertContains(touchSuppressor, "target.addEventListener('touchcancel'")
	}

	@Test
	fun androidReaderExposesKomikkuSmallerTapZoneControl() {
		val runtimeText = readerBridgeText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()
		val preferenceText = readerCommonFile("ReaderPreferenceSettings.kt").readText()
		val bridgeProtocolText = readerCommonFile("ReaderBridgeProtocol.kt").readText()

		assertContains(runtimeText, "settings.smallerTapZone === true")
		assertContains(runtimeText, "this.smallerTapZone = settings.smallerTapZone === true")
		assertContains(settingsDialogText, "label = \"Smaller tap zones\"")
		assertContains(settingsDialogText, "settings.copy(smallerTapZone = settings.smallerTapZone != true)")
		assertContains(ebooksSettingsText, "readerSmallerTapZone")
		assertContains(ebooksSettingsText, "option_ebook_reader_smaller_tap_zones")
		assertContains(searchSettingsText, "ebooks.smaller-tap-zones")
		assertContains(searchSettingsText, "readerSmallerTapZone")
		assertContains(preferenceText, "smallerTapZone = readerSmallerTapZone")
		assertContains(bridgeProtocolText, "val smallerTapZone: Boolean? = null")
		assertContains(bridgeProtocolText, "smallerTapZone?.let { put(\"smallerTapZone\", it) }")
	}

	@Test
	fun androidReaderExposesVisibleTapZoneOverlayControl() {
		val runtimeText = readerBridgeText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val developerSettingsText = settingsFile("DeveloperScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()
		val preferenceText = readerCommonFile("ReaderPreferenceSettings.kt").readText()
		val bridgeProtocolText = readerCommonFile("ReaderBridgeProtocol.kt").readText()
		val chromeStateText = readerCommonFile("ReaderChromeState.kt").readText()

		assertContains(runtimeText, "ensureTapZoneOverlayLayer()")
		assertContains(runtimeText, "updateTapZoneOverlayLayer(")
		assertContains(runtimeText, "settings.showTapZones !== true")
		assertContains(readerRootText, "navigationOverlayVisible = controllerState.menuVisible && controllerState.chrome.settings.showTapZones == true")
		assertFalse(
			settingsDialogText.contains("label = \"Show tap zones\"") ||
				settingsDialogText.contains("settings.copy(showTapZones = settings.showTapZones != true)"),
			"Visible tap-zone overlays are diagnostics and should not be exposed from the in-reader settings dialog."
		)
		assertFalse(
			ebooksSettingsText.contains("option_ebook_reader_show_tap_zones"),
			"Visible tap-zone overlays are diagnostics and should not be shown as an Ebook reading default."
		)
		assertContains(developerSettingsText, "readerShowTapZones")
		assertContains(developerSettingsText, "option_ebook_reader_show_tap_zones")
		assertContains(searchSettingsText, "developer.show-tap-zones")
		assertFalse(
			searchSettingsText.contains("ebooks.show-tap-zones"),
			"Settings search should route visible tap-zone overlays to Developer Options, not Ebooks."
		)
		assertContains(searchSettingsText, "readerShowTapZones")
		assertContains(preferenceText, "showTapZones = readerShowTapZones")
		assertContains(chromeStateText, "showTapZones = false")
		assertContains(bridgeProtocolText, "val showTapZones: Boolean? = null")
		assertContains(bridgeProtocolText, "showTapZones?.let { put(\"showTapZones\", it) }")
	}

	@Test
	fun androidReaderPortsKomikkuFullscreenSystemBars() {
		val systemBarsEffect = listOf(
			File("src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderSystemBarsEffect.android.kt"),
			File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderSystemBarsEffect.android.kt")
		).firstOrNull { it.isFile }
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()
		val preferenceText = readerCommonFile("ReaderPreferenceSettings.kt").readText()
		val bridgeProtocolText = readerCommonFile("ReaderBridgeProtocol.kt").readText()

		assertTrue(systemBarsEffect?.isFile == true, "Reader must own a native system-bars effect like Komikku ReaderActivity.")
		val systemBarsEffectText = systemBarsEffect.readText()
		assertContains(systemBarsEffectText, "WindowCompat.getInsetsController")
		assertContains(systemBarsEffectText, "WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE")
		assertContains(systemBarsEffectText, "WindowInsetsCompat.Type.systemBars()")
		assertContains(systemBarsEffectText, "controller.hide(WindowInsetsCompat.Type.systemBars())")
		assertContains(systemBarsEffectText, "controller.show(WindowInsetsCompat.Type.systemBars())")
		assertContains(readerScreenText, "ReaderSystemBarsEffect(")
		assertContains(readerScreenText, "systemBarsVisible = controllerState.menuVisible || settings.fullscreen == false")
		assertContains(ebooksSettingsText, "readerFullscreen")
		assertContains(ebooksSettingsText, "option_ebook_reader_fullscreen")
		assertContains(searchSettingsText, "ebooks.fullscreen")
		assertContains(searchSettingsText, "readerFullscreen")
		assertContains(preferenceText, "fullscreen = readerFullscreen")
		assertContains(bridgeProtocolText, "val fullscreen: Boolean? = null")
		assertContains(bridgeProtocolText, "fullscreen?.let { put(\"fullscreen\", it) }")
	}

	@Test
	fun androidReaderExposesExpandedThemePalettes() {
		val bridgeText = readerBridgeText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()

		assertContains(bridgeText, "ReaderThemePalettes")
		assertContains(bridgeText, "sepia: {")
		assertContains(bridgeText, "dusk: {")
		assertContains(bridgeText, "black: {")
		assertContains(bridgeText, "--reader-accent")
		assertContains(ebooksSettingsText, "option_ebook_reader_theme_sepia")
		assertContains(ebooksSettingsText, "option_ebook_reader_theme_black")
		assertContains(searchSettingsText, "ReaderSupportedThemes")
	}

	@Test
	fun androidReaderExposesParagraphSpacingAndPublisherStyleControls() {
		val bridgeText = readerBridgeText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()

		assertContains(bridgeText, "readerParagraphSpacingEm")
		assertContains(bridgeText, "--reader-paragraph-spacing")
		assertContains(bridgeText, "'--reader-paragraph-spacing': readerParagraphSpacingEm(settings)")
		assertContains(bridgeText, "margin-block-end: var(--reader-paragraph-spacing, \${readerParagraphSpacingEm(settings)})")
		assertContains(bridgeText, "margin-bottom: var(--reader-paragraph-spacing, \${readerParagraphSpacingEm(settings)})")
		assertContains(bridgeText, "settings.publisherStyles === true")
		assertContains(bridgeText, "paragraphSpacingPercent")
		assertContains(bridgeText, "paragraphSpacing=\${")
		assertContains(ebooksSettingsText, "readerParagraphSpacingPercent")
		assertContains(ebooksSettingsText, "readerPublisherStylesEnabled")
		assertContains(ebooksSettingsText, "option_ebook_reader_paragraph_spacing")
		assertContains(ebooksSettingsText, "option_ebook_reader_publisher_styles")
		assertContains(searchSettingsText, "ebooks.paragraph-spacing")
		assertContains(searchSettingsText, "ebooks.publisher-styles")
	}

	@Test
	fun androidReaderAppliesParagraphSpacingOutsidePublisherStyleOverride() {
		val bridgeText = readerBridgeText()
		val contentCss = bridgeText
			.substringAfter("const readerContentCss = settings =>")
			.substringBefore("const normalizeSearchResult")
		val typographyCss = bridgeText
			.substringAfter("const readerTypographyCss = settings =>")
			.substringBefore("const readerParagraphSpacingCss = settings =>")

		assertContains(bridgeText, "const readerParagraphSpacingCss = settings =>")
		assertContains(contentCss, "\${readerParagraphSpacingCss(settings)}")
		assertFalse(
			typographyCss.contains("margin-block-end: var(--reader-paragraph-spacing"),
			"Paragraph spacing must remain active even when publisher typography styles are enabled."
		)
	}

	@Test
	fun androidReaderFontSizeControlOverridesPublisherAbsoluteTextSizes() {
		val bridgeText = readerBridgeText()
		val typographyCss = bridgeText
			.substringAfter("const readerTypographyCss = settings =>")
			.substringBefore("const readerParagraphSpacingCss = settings =>")
		val applyDocumentTheme = bridgeText
			.substringAfter("applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {")
			.substringBefore("\nfunction currentRendererContainerPosition")
		val inlineTypographyBlockTags = bridgeText
			.substringAfter("const readerInlineTypographyBlockTags = new Set([")
			.substringBefore("])")

		assertFalse(
			typographyCss.contains("if (settings.publisherStyles === true) return ''"),
			"Publisher styles must not disable the user Font size control; absolute book paragraph sizes still need to collapse to the reader root size."
		)
		assertContains(typographyCss, "const usePublisherStyles = settings.publisherStyles === true")
		assertContains(typographyCss, "font-size: var(--reader-content-font-size")
		assertContains(typographyCss, "font-size: 1em !important")
		assertFalse(
			typographyCss.contains("font-size: 1rem !important"),
			"Reader body/prose font-size resets must inherit from the scaled reader document size; pinning prose to 1rem can leave body text unchanged while headings scale."
		)
		assertContains(
			typographyCss,
			"td,",
			message = "Font-size control must reset table-cell prose; older EPUBs commonly put body text in table-like wrappers."
		)
		assertContains(
			bridgeText,
			"const readerInlineTypographyFontSize = element => '1em'",
			message = "Inline publisher font-size normalization must keep prose blocks attached to the reader-scaled body size instead of resetting them to an unscaled rem."
		)
		assertContains(
			bridgeText,
			"normalizeReaderInlineTypography",
			message = "PDF-converted EPUB prose can use inline font-size declarations with !important; the reader must rewrite inline prose font-size ownership after injecting the reader stylesheet."
		)
		assertContains(
			inlineTypographyBlockTags,
			"'BODY',",
			message = "The inline typography normalizer adds doc.body as a candidate; BODY must be treated as prose so body-level important font-size styles cannot pin ebook text while headings scale."
		)
		assertContains(
			bridgeText,
			"style.getPropertyPriority('font-size')",
			message = "The inline typography normalizer must specifically detect important publisher font-size declarations that stylesheet selectors cannot override."
		)
		assertFalse(
			bridgeText.contains("if (!readerElementHasInlineFontSize(element)) continue"),
			"Typography normalization must not be limited to inline font-size declarations; high-specificity publisher CSS classes can pin body text while headings scale."
		)
		assertContains(
			bridgeText,
			"element.dataset.navicHadInlineFontSize",
			message = "The normalizer should still record whether a rewritten prose element originally had an inline font-size for diagnostics."
		)
		assertContains(
			applyDocumentTheme,
			"normalizeReaderInlineTypography(doc, settings)",
			message = "Every loaded EPUB document must normalize inline prose typography before pagination/reflow evidence is trusted."
		)
		assertContains(
			bridgeText,
			"normalizeReaderLineFragmentParagraphs",
			message = "Storyteller/PDF-derived EPUBs can encode one visual source line per paragraph; the reader must normalize those fragments before pagination."
		)
		assertContains(
			applyDocumentTheme,
			"normalizeReaderLineFragmentParagraphs(doc, settings)",
			message = "Loaded EPUB documents must merge source-line fragments before paragraph spacing and pagination evidence are trusted."
		)
		assertTrue(
			applyDocumentTheme.indexOf("normalizeReaderLineFragmentParagraphs(doc, settings)") <
				applyDocumentTheme.indexOf("applyReaderParagraphSpacing(doc, settings)"),
			"Line-fragment normalization must run before paragraph spacing so removed source-line blocks do not leave fake paragraph gaps."
		)
	}

	@Test
	fun androidReaderFontSizeControlScalesPreformattedTypewriterProse() {
		val bridgeText = readerBridgeText()
		val typographyCss = bridgeText
			.substringAfter("const readerTypographyCss = settings =>")
			.substringBefore("const readerParagraphSpacingCss = settings =>")

		assertContains(
			typographyCss,
			"pre,",
			message = "Reader font-size controls must reset preformatted/typewriter ebook prose, not only headings and paragraph tags."
		)
		assertContains(
			typographyCss,
			"code,",
			message = "Inline code/typewriter wrappers in EPUB prose must inherit the reader root font size."
		)
		assertContains(
			typographyCss,
			"pre span,",
			message = "Nested inline text inside preformatted ebook prose must inherit the reader root font size."
		)
	}

	@Test
	fun androidReaderLetsProseUseAdaptiveFolioWidth() {
		val bridgeText = readerBridgeText()
		val typographyCss = bridgeText
			.substringAfter("const readerTypographyCss = settings =>")
			.substringBefore("const readerParagraphSpacingCss = settings =>")

		assertContains(
			typographyCss,
			"max-width: none !important",
			message = "Publisher max-width rules on body/prose wrappers must not shrink tablet folio pages into a phone-width column."
		)
		assertContains(
			typographyCss,
			"width: auto !important",
			message = "Publisher fixed-width rules on body/prose wrappers must yield to the adaptive Foliate page box."
		)
		assertTrue(
			typographyCss.indexOf("body {") < typographyCss.indexOf("max-width: none !important"),
			"The reader must normalize body/prose width after establishing reader-root typography."
		)
	}

	@Test
	fun androidReaderPreventsLandscapeProseFromCollapsingIntoMinContentColumn() {
		val bridgeText = readerBridgeText()
		val typographyCss = bridgeText
			.substringAfter("const readerTypographyCss = settings =>")
			.substringBefore("const readerParagraphSpacingCss = settings =>")

		assertContains(
			typographyCss,
			"min-width: min(32em, 100%) !important",
			message = "Wide/tablet landscape prose must keep a readable folio column instead of collapsing to a word-wide min-content strip."
		)
		assertContains(
			typographyCss,
			"overflow-wrap: normal !important",
			message = "Publisher/conversion word wrapping must not turn Western EPUB prose into one-word vertical columns in landscape."
		)
		assertContains(
			typographyCss,
			"word-break: normal !important",
			message = "Reader typography must neutralize publisher break-all/break-word rules on normal prose."
		)
		assertContains(
			typographyCss,
			"hyphens: manual !important",
			message = "Reader typography should not auto-break words while trying to recover a wide folio page."
		)
	}

	@Test
	fun androidReaderUsesAnxMarginAttributesInsteadOfLegacyBodyMargins() {
		val bridgeText = readerBridgeText()
		val typographyCss = bridgeText
			.substringAfter("const readerTypographyCss = settings =>")
			.substringBefore("const readerParagraphSpacingCss = settings =>")
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchRowsText = settingsFile("SettingsSearchEbookRows.kt").readText()

		assertFalse(
			typographyCss.contains("margin-inline:"),
			"EPUB body typography must not apply legacy marginPercent on top of Anx renderer sideMargin/topMargin/bottomMargin attributes."
		)
		assertFalse(
			settingsDialogText.contains("label = \"Margins\""),
			"The reader settings sheet must not expose legacy Margins beside Anx Side margin; that duplicates the same visual authority."
		)
		assertFalse(
			ebooksSettingsText.contains("option_ebook_reader_margin"),
			"Settings > Ebooks must not expose legacy reader margin when Anx side/top/bottom margin controls own renderer composition."
		)
		assertFalse(
			searchRowsText.contains("option_ebook_reader_margin"),
			"Settings search must not return the retired legacy reader margin control."
		)
		assertContains(settingsDialogText, "label = \"Side margin\"")
		assertContains(settingsDialogText, "label = \"Top margin\"")
		assertContains(settingsDialogText, "label = \"Bottom margin\"")
	}

	@Test
	fun androidReaderReinjectsCompleteContentCssIntoLoadedPublicationDocuments() {
		val bridgeText = readerBridgeText()
		val applyDocumentTheme = bridgeText
			.substringAfter("applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {")
			.substringBefore("\nfunction currentRendererContainerPosition")

		assertContains(applyDocumentTheme, "themeStyle.textContent = readerContentCss(settings)")
		assertFalse(
			applyDocumentTheme.contains("themeStyle.textContent = readerDocumentThemeCss(settings)"),
			"Loaded publication documents need the full reader stylesheet, not only theme colors."
		)
		assertFalse(
			applyDocumentTheme.contains("ensurePaperTextureLayer(doc)"),
			"Paper texture must not be injected as extra publication-document layer elements."
		)
		assertFalse(
			applyDocumentTheme.contains("updatePaperTextureLayer"),
			"Paper texture must not use the old per-element/per-layer updater."
		)
		assertFalse(
			applyDocumentTheme.contains("applyDocumentPaperTexture") ||
				applyDocumentTheme.contains("updateReaderDocumentPaperTexture"),
			"Loaded documents must not receive a second paper texture owner after theme normalization."
		)
	}

	@Test
	fun androidReaderAppliesFontCssBeforeSettingsReflow() {
		val bridgeText = readerBridgeText()
		val applySettings = bridgeText
			.substringAfter("applySettings(settings) {")
			.substringBefore("\nfunction applyThemeToLoadedContent")

		assertContains(applySettings, "this.view?.renderer?.setStyles?.(readerContentCss(settings))")
		assertContains(applySettings, "this.applyThemeToLoadedContent(settings)")
		assertContains(applySettings, "this.applyReaderViewportLayout('settings')")
		assertTrue(
			applySettings.indexOf("this.view?.renderer?.setStyles?.(readerContentCss(settings))") <
				applySettings.indexOf("this.applyReaderViewportLayout('settings')"),
			"Font-size settings must update Foliate renderer CSS before the explicit settings reflow; otherwise body text can keep the old page geometry while headings repaint."
		)
		assertTrue(
			applySettings.indexOf("this.applyThemeToLoadedContent(settings)") <
				applySettings.indexOf("this.applyReaderViewportLayout('settings')"),
			"Loaded EPUB documents need the new readerContentCss before settings reflow so prose, headings, and page geometry scale together."
		)
	}

	@Test
	fun androidReaderCapsExcessiveChapterOpeningTopMargins() {
		val bridgeText = readerBridgeText()
		val applyDocumentTheme = bridgeText
			.substringAfter("applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {")
			.substringBefore("\nfunction currentRendererContainerPosition")

		assertContains(bridgeText, "readerNormalizeChapterOpeningMargins")
		assertContains(bridgeText, "data-navic-chapter-opening-margin-capped")
		assertContains(bridgeText, "margin-block-start")
		assertContains(bridgeText, "margin-top")
		assertContains(
			bridgeText,
			"0.045",
			message = "Chapter opening heading top margins should be capped to a tight Komikku-like page-relative value instead of preserving large publisher margins."
		)
		assertContains(
			bridgeText,
			"Math.min(96",
			message = "Chapter opening headings should not be allowed to drift far down large EPUB page boxes."
		)
		assertContains(
			applyDocumentTheme,
			"readerNormalizeChapterOpeningMargins(doc, settings)",
			message = "The margin cap must run for every loaded EPUB document after the reader stylesheet is injected."
		)
	}

	@Test
	fun androidReaderRefreshesContentThemeAfterRelocation() {
		val bridgeText = readerBridgeText()
		val scheduleCommittedRelocation = bridgeText
			.substringAfter("scheduleCommittedRelocation(detail, reason = 'relocate-committed') {")
			.substringBefore("\n  suppressLoadedCoverDocument")

		assertContains(
			scheduleCommittedRelocation,
			"this.applyThemeToLoadedContent(this.readerSettings)",
			message = "Foliate can reuse or swap the active content document during navigation, so EPUB document normalization must be refreshed before posting the committed relocation."
		)
		assertTrue(
			scheduleCommittedRelocation.indexOf("this.applyThemeToLoadedContent(this.readerSettings)") <
				scheduleCommittedRelocation.indexOf("this.postLocationChanged(pendingDetail, pendingReason)"),
			"Content theming must be refreshed before the committed location is posted so page geometry and visible content agree."
		)
	}

}
