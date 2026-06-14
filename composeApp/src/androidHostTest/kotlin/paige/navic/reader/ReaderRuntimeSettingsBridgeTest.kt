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
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

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
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()
		val webViewHostText = readerWebViewHostFile().readText()

		assertContains(webViewHostText, "ReaderSurfaceHost")
		assertContains(webViewHostText, "readerTapZoneActionAt(")
		assertContains(webViewHostText, "readerTapZonePageTurnDirectionFor(")
		assertContains(webViewHostText, "dispatchReaderWideTap")
		assertContains(webViewHostText, "ReaderBridgeEvent.CenterTap")
		assertContains(readerScreenText, "event is ReaderBridgeEvent.CenterTap")
		assertContains(runtimeText, "attachReaderTapZoneGesture")
		assertContains(runtimeText, "komikkuTapAction(")
		assertContains(runtimeText, "readerTapZoneCommand(")
		assertFalse(
			readerScreenText.contains("ReaderNativeTapOverlay("),
			"Readable EPUB/PDF tap ownership must live in the Android reader surface, not a Compose sibling overlay."
		)
	}

	@Test
	fun androidReaderSurfaceObservesConfirmedTapsAfterChildDispatchLikeKomikku() {
		val readerScreenText = readerScreenFile().readText()
		val webViewHostText = readerWebViewHostFile().readText()
		val dispatchTouchEvent = webViewHostText
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("\n\tprivate val readerGestureDetector")

		assertContains(webViewHostText, "private class ReaderSurfaceHost")
		assertContains(webViewHostText, "override fun dispatchTouchEvent(event: MotionEvent): Boolean")
		assertContains(webViewHostText, "val childHandled = super.dispatchTouchEvent(event)")
		assertContains(webViewHostText, "readerGestureDetector.onTouchEvent(event)")
		assertContains(webViewHostText, "childHandled")
		assertContains(webViewHostText, "return if (readerWideTapsEnabled && shellCoverWasVisible)")
		assertContains(webViewHostText, "GestureDetector.SimpleOnGestureListener()")
		assertContains(webViewHostText, "override fun onSingleTapConfirmed(event: MotionEvent): Boolean")
		assertContains(webViewHostText, "dispatchReaderWideTap(event)")
		assertContains(webViewHostText, "ViewConfiguration.get(context).scaledTouchSlop")
		assertTrue(
			dispatchTouchEvent.indexOf("val childHandled = super.dispatchTouchEvent(event)") <
				dispatchTouchEvent.indexOf("readerGestureDetector.onTouchEvent(event)"),
			"Komikku's pager dispatches to the child/page first, then observes the same stream for confirmed reader-wide taps."
		)
		assertFalse(
			webViewHostText.contains("ReaderAndroidTapZoneObserver"),
			"Android must not keep the old split WebView-only tap-zone observer."
		)
		assertFalse(
			readerScreenText.contains("ReaderNativeTapRegion("),
			"Reader-wide tap zones must not be Compose region boxes over the WebView."
		)
	}

	@Test
	fun androidReaderDisablesJavaScriptReadableTapDispatchWhenNativeSurfaceOwnsTaps() {
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val webViewHostText = readerWebViewHostFile().readText()
		val bridgeProtocolText = readerCommonFile("ReaderBridgeProtocol.kt").readText()
		val openPublication = runtimeText
			.substringAfter("async openPublication({ url, mediaOverlayEnabled = false, externalShellCover = false, startLocator = null, settings = null }) {")
			.substringBefore("\n  close()")
		val tapHandler = runtimeText
			.substringAfter("attachReaderTapZoneGesture(target) {")
			.substringBefore("\n  attachScrolledEdgeTurnGestures")

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
		assertContains(tapHandler, "this.renderTapZoneOverlayLayer()")
		assertContains(
			tapHandler,
			"return",
			message = "Android native reader-surface ownership must disable JS reader-wide page/menu dispatch to avoid double page turns."
		)
	}

	@Test
	fun androidReaderExposesKomikkuSmallerTapZoneControl() {
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
		val preferenceText = readerCommonFile("ReaderPreferenceSettings.kt").readText()
		val bridgeProtocolText = readerCommonFile("ReaderBridgeProtocol.kt").readText()

		assertContains(runtimeText, "settings.smallerTapZone === true")
		assertContains(runtimeText, "this.smallerTapZone = settings.smallerTapZone === true")
		assertContains(readerOptionsPanelText, "Smaller tap zones")
		assertContains(readerOptionsPanelText, "toggleSmallerTapZone()")
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
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
		val preferenceText = readerCommonFile("ReaderPreferenceSettings.kt").readText()
		val bridgeProtocolText = readerCommonFile("ReaderBridgeProtocol.kt").readText()
		val chromeStateText = readerCommonFile("ReaderChromeState.kt").readText()

		assertContains(runtimeText, "ensureTapZoneOverlayLayer()")
		assertContains(runtimeText, "updateTapZoneOverlayLayer(")
		assertContains(runtimeText, "settings.showTapZones !== true")
		assertContains(readerOptionsPanelText, "Show tap zones")
		assertContains(readerOptionsPanelText, "toggleShowTapZones()")
		assertContains(ebooksSettingsText, "readerShowTapZones")
		assertContains(ebooksSettingsText, "option_ebook_reader_show_tap_zones")
		assertContains(searchSettingsText, "ebooks.show-tap-zones")
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
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
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
		assertContains(readerScreenText, "chromeVisible || optionsVisible")
		assertContains(readerOptionsPanelText, "Fullscreen")
		assertContains(readerOptionsPanelText, "toggleFullscreen()")
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
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

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
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

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
	fun androidReaderReinjectsCompleteContentCssIntoLoadedPublicationDocuments() {
		val bridgeText = readerBridgeText()
		val applyDocumentTheme = bridgeText
			.substringAfter("applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {")
			.substringBefore("\n  applyReaderDirection")

		assertContains(applyDocumentTheme, "themeStyle.textContent = readerContentCss(settings)")
		assertFalse(
			applyDocumentTheme.contains("themeStyle.textContent = readerDocumentThemeCss(settings)"),
			"Loaded publication documents need the full reader stylesheet, not only theme colors."
		)
		assertFalse(
			applyDocumentTheme.contains("ensurePaperTextureLayer(doc)"),
			"Paper texture must be a single reader-window layer, not injected into each publication document."
		)
		assertFalse(
			applyDocumentTheme.contains("updatePaperTextureLayer"),
			"Paper texture must not be applied to rendered EPUB elements."
		)
	}

}
