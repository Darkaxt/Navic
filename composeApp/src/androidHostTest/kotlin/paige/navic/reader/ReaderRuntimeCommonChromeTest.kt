package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimeCommonChromeTest {
	@Test
	fun androidReaderPackagesBundledFontSourcesForWebViewRendering() {
		val root = readerAssetRoot()
		val bridgeText = readerBridgeText(root)
		val readerScreenText = readerScreenFile().readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()
		val literata = root.resolve("fonts/navic-literata-regular.ttf")
		val atkinson = root.resolve("fonts/navic-atkinson-hyperlegible-regular.otf")
		val openDyslexic = root.resolve("fonts/navic-opendyslexic-regular.otf")

		assertTrue(literata.isFile, "Literata must be bundled for book font rendering")
		assertTrue(atkinson.isFile, "Atkinson Hyperlegible must be bundled for humanist font rendering")
		assertTrue(openDyslexic.isFile, "OpenDyslexic must be bundled for dyslexic font rendering")
		assertTrue(literata.length() > 16_000, "Literata asset should be a real font file")
		assertTrue(atkinson.length() > 16_000, "Atkinson asset should be a real font file")
		assertTrue(openDyslexic.length() > 16_000, "OpenDyslexic asset should be a real font file")
		assertContains(bridgeText, "readerFontFaceCss")
		assertContains(bridgeText, "@font-face")
		assertContains(bridgeText, "Navic Literata")
		assertContains(bridgeText, "Navic Atkinson Hyperlegible")
		assertContains(bridgeText, "Navic OpenDyslexic")
		assertContains(bridgeText, "American Typewriter")
		assertContains(bridgeText, "Courier Prime")
		assertContains(bridgeText, "fonts/navic-literata-regular.ttf")
		assertContains(bridgeText, "fonts/navic-atkinson-hyperlegible-regular.otf")
		assertContains(bridgeText, "fonts/navic-opendyslexic-regular.otf")
		assertContains(bridgeText, "ReaderFontSourceNavic")
		assertContains(bridgeText, "ReaderFontSourceSystem")
		assertContains(bridgeText, "ReaderFontSourcePublisher")
		assertContains(bridgeText, "ReaderFontSourceCustom")
		assertContains(bridgeText, "readerCustomFontUrl")
		assertContains(bridgeText, "readerFontFaceCss(settings)")
		assertContains(bridgeText, "readerEffectiveFontFamily(settings)")
		assertContains(bridgeText, "settings?.fontSource")
		assertContains(settingsDialogText, "Font source")
		assertContains(settingsDialogText, "ReaderSupportedFontSources")
		assertContains(ebooksSettingsText, "readerFontSource")
		assertContains(ebooksSettingsText, "ReaderFontSourceOption")
		assertContains(searchSettingsText, "ebooks.font-source")
		assertContains(searchSettingsText, "ReaderSupportedFontSources")
	}

	@Test
	fun commonReaderChromeExposesDimOverlayControl() {
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val contentOverlayText = readerCommonUiFile("ReaderContentOverlay.kt").readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()

		assertContains(readerRootText, "KomikkuReaderContentOverlay")
		assertContains(readerRootText, "Modifier.matchParentSize()")
		assertContains(contentOverlayText, "drawRect(Color.Black.copy")
		assertContains(settingsDialogText, "Dim overlay")
		assertContains(settingsDialogText, "SliderItem(")
		assertContains(settingsDialogText, "dimOverlayPercent = dimOverlayPercent")
		assertContains(ebooksSettingsText, "readerDimOverlayPercent")
		assertContains(ebooksSettingsText, "option_ebook_reader_dim_overlay")
		assertContains(searchSettingsText, "ebooks.dim-overlay")
		assertContains(searchSettingsText, "readerDimOverlayPercent")
	}

	@Test
	fun commonReaderCustomFilterPortsKomikkuColorFilterControls() {
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()
		val preferencesText = listOf(
			File("src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt"),
			File("composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate PreferenceManager.kt")
		val bridgeText = readerCommonFile("ReaderBridgeProtocol.kt").readText()

		assertContains(readerRootText, "readerColorFilterColor(controllerState.chrome.settings)")
		assertContains(readerRootText, "readerColorFilterBlendMode(controllerState.chrome.settings.colorFilterMode)")
		assertContains(settingsDialogText, "Color filter")
		assertContains(settingsDialogText, "Grayscale")
		assertContains(settingsDialogText, "Inverted colors")
		assertContains(settingsDialogText, "ReaderSupportedColorFilterModes")
		assertContains(settingsDialogText, "Red")
		assertContains(settingsDialogText, "Green")
		assertContains(settingsDialogText, "Blue")
		assertContains(settingsDialogText, "Alpha")
		assertContains(settingsDialogText, "setReaderColorFilterChannel")
		assertContains(preferencesText, "readerColorFilterEnabled")
		assertContains(preferencesText, "readerColorFilterArgb")
		assertContains(preferencesText, "readerColorFilterMode")
		assertContains(preferencesText, "readerGrayscaleEnabled")
		assertContains(preferencesText, "readerInvertedColors")
		assertContains(bridgeText, "colorFilterEnabled")
		assertContains(bridgeText, "colorFilterArgb")
		assertContains(bridgeText, "colorFilterMode")
		assertContains(bridgeText, "grayscaleEnabled")
		assertContains(bridgeText, "invertedColors")
		assertContains(ebooksSettingsText, "option_ebook_reader_grayscale")
		assertContains(ebooksSettingsText, "readerGrayscaleEnabled")
		assertContains(ebooksSettingsText, "option_ebook_reader_inverted_colors")
		assertContains(ebooksSettingsText, "readerInvertedColors")
		assertContains(searchSettingsText, "ebooks.grayscale")
		assertContains(searchSettingsText, "ebooks.inverted-colors")
	}

	@Test
	fun commonReaderReadingTabPortsKomikkuTappingInversionControl() {
		val readerNavigationText = readerCommonUiFile("ReaderNavigation.kt").readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()
		val preferencesText = listOf(
			File("src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt"),
			File("composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate PreferenceManager.kt")
		val bridgeText = readerCommonFile("ReaderBridgeProtocol.kt").readText()

		assertContains(settingsDialogText, "Tapping inversion")
		assertContains(settingsDialogText, "KomikkuTapZoneInvertOptions")
		assertContains(settingsDialogText, "settings.copy(tapZoneInvertMode = tapZoneInvertMode)")
		assertContains(readerNavigationText, "komikkuTappingInvertMode(settings.tapZoneInvertMode)")
		assertContains(preferencesText, "readerTapZoneInvertMode")
		assertContains(bridgeText, "tapZoneInvertMode")
		assertContains(ebooksSettingsText, "option_ebook_reader_tap_zone_invert")
		assertContains(ebooksSettingsText, "readerTapZoneInvertMode")
		assertContains(searchSettingsText, "ebooks.tap-zone-invert")
		assertContains(searchSettingsText, "ReaderSupportedTapZoneInvertModes")
	}

	@Test
	fun androidReaderExposesKomikkuStyleOrientationControl() {
		val orientationEffectText = readerAndroidFile("ReaderOrientationEffect.android.kt").readText()
		val readerScreenText = readerScreenFile().readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()

		assertContains(orientationEffectText, "SCREEN_ORIENTATION_FULL_SENSOR")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_SENSOR_PORTRAIT")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_SENSOR_LANDSCAPE")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_PORTRAIT")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_LANDSCAPE")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_REVERSE_PORTRAIT")
		assertContains(orientationEffectText, "activity.requestedOrientation = previousOrientation")
		assertContains(readerScreenText, "ReaderOrientationEffect(orientation = settings.orientation)")
		assertContains(settingsDialogText, "Rotation")
		assertContains(settingsDialogText, "ReaderSupportedOrientations")
		assertContains(ebooksSettingsText, "readerOrientation")
		assertContains(ebooksSettingsText, "option_ebook_reader_orientation")
		assertContains(searchSettingsText, "ebooks.orientation")
		assertContains(searchSettingsText, "ReaderSupportedOrientations")
	}

	@Test
	fun commonReaderChromeExposesVolumeKeyPageTurnControl() {
		val readerScreenText = readerScreenFile().readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()

		assertContains(readerScreenText, "onPreviewKeyEvent")
		assertContains(readerScreenText, "Key.VolumeUp")
		assertContains(readerScreenText, "Key.VolumeDown")
		assertContains(readerScreenText, "volumeKeyPageTurns")
		assertContains(settingsDialogText, "Volume keys")
		assertContains(ebooksSettingsText, "readerVolumeKeyPageTurns")
		assertContains(ebooksSettingsText, "option_ebook_reader_volume_keys")
		assertContains(searchSettingsText, "ebooks.volume-keys")
		assertContains(searchSettingsText, "readerVolumeKeyPageTurns")
	}

	@Test
	fun commonReaderChromeRoutesAnxPushStateToKomikkuHistoryCapsule() {
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val historyCapsuleText = readerCommonUiFile("ReaderHistoryCapsule.kt").readText()
		val readerScreenText = readerScreenFile().readText()
		val controllerText = readerCommonFile("ReaderController.kt").readText()
		val bridgeText = readerRuntimeImplementationText(readerAssetRoot())

		assertContains(readerRootText, "KomikkuReaderHistoryCapsule")
		assertContains(readerRootText, "navigation = controllerState.engineNavigation")
		assertContains(historyCapsuleText, "if (!navigation.visible) return")
		assertContains(readerRootText, "onHistoryBack")
		assertContains(readerRootText, "onHistoryForward")
		assertContains(readerRootText, "onDismissHistory")
		assertContains(readerScreenText, "coordinator.navigateHistoryBack()")
		assertContains(readerScreenText, "coordinator.navigateHistoryForward()")
		assertContains(readerScreenText, "coordinator.dismissHistoryNavigation()")
		assertContains(controllerText, "direction = ReaderHistoryDirection.Back")
		assertContains(controllerText, "direction = ReaderHistoryDirection.Forward")
		assertContains(controllerText, "ReaderEngineCommand.NavigateHistory(direction)")
		assertContains(bridgeText, "case 'historyBack':")
		assertContains(bridgeText, "this.view?.history?.back?.()")
		assertContains(bridgeText, "case 'historyForward':")
		assertContains(bridgeText, "this.view?.history?.forward?.()")
	}

	@Test
	fun commonReaderDefaultSettingsRememberKeyTracksReaderPreferenceInputs() {
		val readerScreenText = readerScreenFile().readText()
		val settingsSessionText = readerCommonUiFile("ReaderSettingsSession.kt").readText()
		val defaultSettingsRemember = readerScreenText
			.substringAfter("val defaultReaderSettings = remember(")
			.substringBefore("\n\t) {")
		val expectedPreferenceInputs = listOf(
			"readerFontFamily",
			"readerFontSource",
			"readerCustomFontFamily",
			"readerCustomFontUrl",
			"readerFontSizePercent",
			"readerLineHeightPercent",
			"readerParagraphSpacingPercent",
			"readerMarginPercent",
			"readerDimOverlayPercent",
			"readerColorFilterEnabled",
			"readerColorFilterArgb",
			"readerColorFilterMode",
			"readerGrayscaleEnabled",
			"readerInvertedColors",
			"readerOrientation",
			"readerTheme",
			"readerDirection",
			"readerNavBarType",
			"readerFlowMode",
			"readerPaged",
			"readerTapZone",
			"readerSmallerTapZone",
			"readerShowTapZones",
			"readerPublisherStylesEnabled",
			"readerFullscreen",
			"readerKeepScreenOn",
			"readerReadaloudSyncEnabled",
			"readerVolumeKeyPageTurns",
			"readerWebContentsDebuggingEnabled",
			"readerBookSettingsJson"
		)

		assertContains(readerScreenText, "koinInject<PreferenceManager>()")
		assertContains(readerScreenText, "preferenceManager.readerSettingsForScope(")
		assertContains(settingsSessionText, "readerSettingsForBook(bookId)")
		expectedPreferenceInputs.forEach { preferenceName ->
			assertContains(
				defaultSettingsRemember,
				"preferenceManager.$preferenceName",
				message = "ReaderScreen default settings must refresh when $preferenceName changes."
			)
		}
	}

	@Test
	fun commonSettingsEbooksScreenCanImportCustomFontIntoPreferences() {
		val ebooksScreenText = settingsFile("EbooksScreen.kt").readText()

		assertContains(ebooksScreenText, "rememberReaderFontImporter(")
		assertContains(ebooksScreenText, "fontImporter.launch()")
		assertContains(ebooksScreenText, "readerFontSource = ReaderFontSourceCustom")
		assertContains(ebooksScreenText, "readerCustomFontFamily = imported.family")
		assertContains(ebooksScreenText, "readerCustomFontUrl = imported.url")
		assertContains(ebooksScreenText, "option_ebook_reader_import_font")
	}

	@Test
	fun commonSettingsEbooksScreenCanClearImportedFontAndShowsFontCacheStorage() {
		val ebooksScreenText = settingsFile("EbooksScreen.kt").readText()
		val fontImporterText = settingsFile("ReaderFontImporter.kt").readText()
		val androidImporterText = listOf(
			File("src/androidMain/kotlin/paige/navic/ui/screens/settings/ReaderFontImporter.android.kt"),
			File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/settings/ReaderFontImporter.android.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Android ReaderFontImporter.android.kt")

		assertContains(fontImporterText, "val cachedFontBytes: Long")
		assertContains(fontImporterText, "fun clearImportedFonts()")
		assertContains(androidImporterText, "ReaderImportedFontCache(readerPublicationCacheRoot(context))")
		assertContains(androidImporterText, "fontCache.cachedFontsByteSize()")
		assertContains(androidImporterText, "fontCache.clearImportedFonts()")
		assertContains(ebooksScreenText, "storageSizeText(fontImporter.cachedFontBytes)")
		assertContains(ebooksScreenText, "option_ebook_reader_imported_font_storage")
		assertContains(ebooksScreenText, "option_ebook_reader_clear_imported_font")
		assertContains(ebooksScreenText, "fontImporter.clearImportedFonts()")
		assertContains(ebooksScreenText, "preferenceManager.readerFontSource = ReaderFontSourceNavic")
		assertContains(ebooksScreenText, "preferenceManager.readerCustomFontFamily = \"\"")
		assertContains(ebooksScreenText, "preferenceManager.readerCustomFontUrl = \"\"")
	}

	@Test
	fun commonReaderShellUsesKomikkuEquivalentOverlayStack() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val appBarsBody = appBarsText.substringAfter("internal fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()

		assertContains(readerScreenText, "KomikkuReaderRoot(")
		assertContains(readerRootText, "KomikkuReaderNativeFrameHost(")
		assertContains(readerRootText, "KomikkuComposeOverlay(")
		assertContains(readerRootText, "KomikkuReaderAppBars(")
		assertContains(appBarsText, "KomikkuReaderTopBar(")
		assertContains(readerRootText, "KomikkuReaderSettingsDialog(")
		assertContains(settingsDialogText, "KomikkuAdaptiveSheet(")
		assertContains(readerRootText, "Modifier.matchParentSize()")
		assertContains(appBarsBody, "Column(modifier = modifier.fillMaxHeight())")
		assertContains(appBarsBody, ".weight(1f)")
		assertContains(appBarsBody, ".align(Alignment.End)")
		assertContains(appBarsBody, ".align(Alignment.Start)")
		assertContains(appBarsText, "Ported from Komikku ReaderAppBars")
		assertContains(settingsDialogText, "Ported from Komikku ReaderSettingsDialog")
		assertFalse(
			appBarsBody.contains("Box(modifier = modifier.fillMaxSize())") ||
				appBarsBody.contains("Alignment.CenterEnd") ||
				appBarsBody.contains("Alignment.CenterStart"),
			"Komikku reader app bars place the vertical navigator in the weighted middle slot of the app-bar Column, not a centered full-screen Box."
		)
		assertFalse(
			readerScreenText.contains("Scaffold(") || readerScreenText.contains("bottomBar ="),
			"Reader shell must follow Komikku's overlay stack; chrome cannot be hosted as a Scaffold bottomBar that resizes content."
		)
		assertFalse(
			readerScreenText.contains("ModalBottomSheet(") || readerScreenText.contains("rememberModalBottomSheetState"),
			"Reader settings must be an overlay dialog above the full-window reader, not a modal bottom sheet tied to app chrome behavior."
		)
		assertFalse(
			readerScreenText.contains(".padding(innerPadding)"),
			"Reader content must remain full-window; chrome/settings padding must never be applied to the content host."
		)
		assertFalse(
			readerScreenText.contains("ReaderOptionsPanel("),
			"The active Komikku reader must not resurrect the old docked options panel."
		)
	}

	@Test
	fun commonReaderChapterNavigatorLivesInDedicatedKomikkuComponentFile() {
		val readerScreenText = readerScreenFile().readText()
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val navigatorText = readerCommonUiFile("ReaderChapterNavigator.kt").readText()

		assertContains(appBarsText, "KomikkuChapterNavigator(")
		assertFalse(
			readerScreenText.contains("private fun KomikkuChapterNavigator(") ||
				readerScreenText.contains("private fun KomikkuChapterNavigatorVertical(") ||
				readerScreenText.contains("private fun KomikkuChapterProgressSlider("),
			"Komikku chapter/progress navigation must live in its own reader component file, not inside ReaderScreen."
		)
		assertContains(navigatorText, "internal fun KomikkuChapterNavigator(")
		assertContains(navigatorText, "private fun KomikkuChapterNavigatorVertical(")
		assertContains(navigatorText, "private fun KomikkuChapterProgressSlider(")
		assertContains(navigatorText, "private fun ColumnScope.KomikkuVerticalChapterProgressRail(")
		assertFalse(
			navigatorText.contains("komikkuChapterRailPageForOffset("),
			"Komikku's vertical rail uses the shared rotated Slider; Navic must not keep a custom y-offset mapper."
		)
		assertFalse(
			navigatorText.contains("KomikkuReaderVerticalRailHeightFraction"),
			"The dedicated navigator file must not preserve a Navic-only vertical rail height constant; Komikku derives rail height from the app-bar layout slots."
		)
	}

	@Test
	fun commonReaderSettingsDialogLivesInDedicatedKomikkuComponentFile() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()

		assertContains(readerRootText, "KomikkuReaderSettingsDialog(")
		assertFalse(
				readerScreenText.contains("private fun KomikkuReaderSettingsDialog(") ||
				readerScreenText.contains("private fun KomikkuTabbedDialog(") ||
				readerScreenText.contains("private fun KomikkuSettingsTabRow(") ||
				readerScreenText.contains("private fun SettingsChipRow(") ||
				readerScreenText.contains("private fun CheckboxItem(") ||
				readerScreenText.contains("private fun SliderItem("),
			"Komikku reader settings must live in its own reader component file, not inside ReaderScreen."
		)
		assertContains(settingsDialogText, "internal val TabbedDialogPaddingsVertical = 8.dp")
		assertContains(settingsDialogText, "private enum class KomikkuSettingsTab(val label: String, val compactLabel: String = label)")
		assertContains(settingsDialogText, "val compactTabLabels = maxWidth < 520.dp")
		assertContains(settingsDialogText, "useCompactLabels: Boolean")
		assertContains(settingsDialogText, "text = if (useCompactLabels) tab.compactLabel else tab.label")
		assertContains(settingsDialogText, "internal fun KomikkuReaderSettingsDialog(")
		assertContains(settingsDialogText, "private fun KomikkuTabbedDialog(")
		assertContains(settingsDialogText, "private fun KomikkuSettingsTabRow(")
		assertContains(settingsDialogText, "private object SettingsItemsPaddings")
		assertContains(settingsDialogText, "private fun HeadingItem(")
		assertContains(settingsDialogText, "private fun SettingsChipRow(")
		assertContains(settingsDialogText, "private fun CheckboxItem(")
		assertContains(settingsDialogText, "private fun SliderItem(")
		assertContains(settingsDialogText, "private fun BaseSettingsItem(")
		assertContains(settingsDialogText, "private fun Pill(")
		assertContains(settingsDialogText, "KomikkuIntegerSlider(")
	}

	@Test
	fun commonReaderAppBarsLiveInDedicatedKomikkuComponentFile() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()

		assertContains(readerRootText, "KomikkuReaderAppBars(")
		assertFalse(
			readerScreenText.contains("private fun KomikkuReaderAppBars(") ||
				readerScreenText.contains("private fun KomikkuReaderTopBar(") ||
				readerScreenText.contains("private fun KomikkuReaderBottomBar("),
			"Komikku reader app bars must live in their own reader component file, not inside ReaderScreen."
		)
		assertContains(appBarsText, "internal fun KomikkuReaderAppBars(")
		assertContains(appBarsText, "private fun KomikkuReaderTopBar(")
		assertContains(appBarsText, "private fun KomikkuReaderBottomBar(")
		assertContains(appBarsText, "Ported from Komikku ReaderAppBars")
		assertContains(appBarsText, "Ported from Komikku ReaderBottomBar")
	}

	@Test
	fun commonReaderChromeUsesKomikkuEquivalentSideProgressRail() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val navigatorText = readerCommonUiFile("ReaderChapterNavigator.kt").readText()
		val sliderPrimitiveText = readerCommonUiFile("KomikkuIntegerSlider.kt").readText()
		val appBarsBody = appBarsText.substringAfter("internal fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")
		val sideRailBody = navigatorText.substringAfter("private fun KomikkuChapterNavigatorVertical(")
			.substringBefore("\n@Composable\nprivate fun ColumnScope.KomikkuVerticalChapterProgressRail(")
		val verticalRailBody = navigatorText.substringAfter("private fun ColumnScope.KomikkuVerticalChapterProgressRail(")
			.substringBefore("\n}\n\nprivate fun readerShouldShowChapterProgressSlider(")
		val bottomChromeBody = appBarsText.substringAfter("private fun KomikkuReaderBottomBar(")

		assertContains(navigatorText, "KomikkuChapterNavigatorVertical(")
		assertContains(appBarsBody, "val showChapterNavigator = visible && !controllerState.shellCoverVisible")
		assertContains(appBarsBody, "visible = showChapterNavigator")
		assertContains(appBarsBody, "if (navBarType == ReaderNavBarTypeBottom && !controllerState.shellCoverVisible)")
		assertContains(appBarsBody, "ReaderNavBarTypeVerticalRight")
		assertContains(appBarsBody, "Column(modifier = modifier.fillMaxHeight())")
		assertContains(appBarsBody, ".weight(1f)")
		assertContains(appBarsBody, ".align(Alignment.End)")
		assertContains(appBarsBody, ".align(Alignment.Start)")
		assertFalse(
			appBarsBody.contains("Box(modifier = modifier.fillMaxSize())") ||
				appBarsBody.contains("Alignment.CenterEnd") ||
				appBarsBody.contains("Alignment.CenterStart") ||
				appBarsBody.contains("KomikkuReaderVerticalRailHeightFraction"),
			"Komikku app bars place the vertical navigator in the weighted middle slot of the app-bar Column; Navic must not keep a hard-coded centered height fraction."
		)
		assertFalse(
			navigatorText.contains("KomikkuReaderVerticalRailHeightFraction"),
			"Vertical rail height should come from Komikku's top/middle/bottom app-bar layout, not a Navic-only fraction constant."
		)
		assertContains(navigatorText, "internal fun readerShouldShowChapterProgressSlider(totalPages: Int): Boolean = totalPages > 1")
		assertContains(navigatorText, "if (readerShouldShowChapterProgressSlider(totalPages))")
		assertContains(navigatorText, "private fun KomikkuChapterProgressSlider(")
		assertContains(navigatorText, "import navic.composeapp.generated.resources.Res")
		assertContains(navigatorText, "import org.jetbrains.compose.resources.stringResource")
		assertContains(navigatorText, "import androidx.compose.foundation.isSystemInDarkTheme")
		assertContains(navigatorText, "import androidx.compose.ui.semantics.contentDescription")
		assertContains(navigatorText, "import androidx.compose.ui.semantics.semantics")
		assertContains(navigatorText, "MutableInteractionSource")
		assertContains(navigatorText, "collectIsDraggedAsState")
		assertContains(navigatorText, "HapticFeedbackType.TextHandleMove")
		assertContains(navigatorText, "semantics(mergeDescendants = true)")
		assertContains(navigatorText, "contentDescription = \"Chapter page slider\"")
		assertContains(sliderPrimitiveText, "onValueChange = { onValueChange(it.roundToInt()) }")
		assertContains(sideRailBody, ".copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)")
		assertContains(sideRailBody, "val textColor = MaterialTheme.colorScheme.onSurface")
		assertContains(sideRailBody, "KomikkuVerticalChapterProgressRail(")
		assertContains(sideRailBody, "onPageIndexChange(page - 1)")
		assertContains(sideRailBody, "text = currentPageText")
		assertContains(sideRailBody, "text = totalPages.toString()")
		assertContains(sideRailBody, "color = textColor")
		assertContains(sideRailBody, "Icons.Outlined.SkipPrevious")
		assertContains(sideRailBody, "Icons.Outlined.SkipNext")
		assertContains(sideRailBody, "stringResource(Res.string.action_previous_chapter)")
		assertContains(sideRailBody, "stringResource(Res.string.action_next_chapter)")
		assertFalse(
			sideRailBody.contains("Icons.Filled.SkipPrevious") ||
				sideRailBody.contains("Icons.Filled.SkipNext"),
			"Komikku's chapter navigator uses outlined skip icons; keeping Navic filled icons is a non-faithful visual fork."
		)
		assertFalse(
			sideRailBody.contains("contentDescription = \"Previous\"") ||
				sideRailBody.contains("contentDescription = \"Next\""),
			"Komikku's chapter navigator uses resource-backed previous/next chapter labels; hardcoded labels are a non-faithful progress rail fork."
		)
		assertFalse(
			sideRailBody.contains("ReaderProgressSeekControl("),
			"The Komikku side rail must not reuse the old bottom progress control."
		)
		assertFalse(
			bottomChromeBody.contains("ReaderProgressSeekControl(") ||
				bottomChromeBody.contains("LinearProgressIndicator("),
			"Komikku-equivalent progress belongs in the side rail overlay, not inside the bottom chrome surface."
		)
		assertContains(verticalRailBody, "KomikkuChapterProgressSlider(")
		assertContains(verticalRailBody, "graphicsLayer {")
		assertContains(verticalRailBody, "rotationZ = 90f")
		assertContains(verticalRailBody, "transformOrigin = TransformOrigin(0f, 0f)")
		assertContains(verticalRailBody, ".layout { measurable, constraints ->")
		assertContains(verticalRailBody, "Constraints(")
		assertContains(verticalRailBody, "placeable.place(0, -placeable.height)")
		assertContains(verticalRailBody, ".weight(1f)")
		assertContains(verticalRailBody, "onValueChange = { page ->")
		assertContains(verticalRailBody, "onPageChange(page)")
		assertFalse(
			verticalRailBody.contains("Canvas(") ||
				verticalRailBody.contains("pointerInput(") ||
				verticalRailBody.contains("detectTapGestures") ||
				verticalRailBody.contains("detectDragGestures") ||
				navigatorText.contains("readerPageForVerticalChapterProgressOffset(") ||
				navigatorText.contains("komikkuChapterRailPageForOffset("),
			"Komikku's side rail must remain the shared Slider rotated vertically; Navic must not add a custom transparent rail touch layer."
		)
	}

	@Test
	fun commonReaderVerticalProgressRailUsesKomikkuLayoutWithDeterministicEndpointMapping() {
		val navigatorText = readerCommonUiFile("ReaderChapterNavigator.kt").readText()
		val sideRailBody = navigatorText.substringAfter("private fun KomikkuChapterNavigatorVertical(")
			.substringBefore("\n@Composable\nprivate fun ColumnScope.KomikkuVerticalChapterProgressRail(")
		val verticalRailBody = navigatorText.substringAfter("private fun ColumnScope.KomikkuVerticalChapterProgressRail(")
			.substringBefore("\n}\n\nprivate fun readerShouldShowChapterProgressSlider(")
		val komikkuNavigatorText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku ChapterNavigator.kt reference")
		val komikkuVerticalBody = komikkuNavigatorText.substringAfter("fun ChapterNavigatorVert(")
			.substringBefore("\n}\n\n@Preview")

		assertContains(komikkuVerticalBody, "Slider(")
		assertContains(komikkuVerticalBody, "copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)")
		assertContains(komikkuVerticalBody, "val textColor = MaterialTheme.colorScheme.onSurface")
		assertContains(komikkuVerticalBody, "Icons.Outlined.SkipPrevious")
		assertContains(komikkuVerticalBody, "Icons.Outlined.SkipNext")
		assertContains(komikkuVerticalBody, "stringResource(MR.strings.action_previous_chapter)")
		assertContains(komikkuVerticalBody, "stringResource(MR.strings.action_next_chapter)")
		assertContains(komikkuVerticalBody, "graphicsLayer {")
		assertContains(komikkuVerticalBody, "rotationZ = 90f")
		assertContains(komikkuVerticalBody, "transformOrigin = TransformOrigin(0f, 0f)")
		assertContains(komikkuVerticalBody, ".layout { measurable, constraints ->")
		assertContains(komikkuVerticalBody, ".weight(1f)")
		assertContains(sideRailBody, "copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)")
		assertContains(sideRailBody, "val textColor = MaterialTheme.colorScheme.onSurface")
		assertContains(sideRailBody, "Icons.Outlined.SkipPrevious")
		assertContains(sideRailBody, "Icons.Outlined.SkipNext")
		assertContains(sideRailBody, "stringResource(Res.string.action_previous_chapter)")
		assertContains(sideRailBody, "stringResource(Res.string.action_next_chapter)")
		assertContains(sideRailBody, "KomikkuVerticalChapterProgressRail(")
		assertContains(sideRailBody, "contentDescription = \"Chapter page slider\"")
		assertContains(sideRailBody, ".weight(1f)")
		assertContains(verticalRailBody, "KomikkuChapterProgressSlider(")
		assertContains(verticalRailBody, "graphicsLayer {")
		assertContains(verticalRailBody, "rotationZ = 90f")
		assertContains(verticalRailBody, "transformOrigin = TransformOrigin(0f, 0f)")
		assertContains(verticalRailBody, ".layout { measurable, constraints ->")
		assertContains(verticalRailBody, "Constraints(")
		assertContains(verticalRailBody, "placeable.place(0, -placeable.height)")
		assertContains(verticalRailBody, ".weight(1f)")
		assertFalse(
			verticalRailBody.contains("Canvas(") ||
				verticalRailBody.contains("drawRoundRect(") ||
				verticalRailBody.contains("drawCircle(") ||
				verticalRailBody.contains("pointerInput(") ||
				verticalRailBody.contains("detectTapGestures") ||
				verticalRailBody.contains("detectDragGestures") ||
				navigatorText.contains("readerPageForVerticalChapterProgressOffset(") ||
				navigatorText.contains("komikkuChapterRailPageForOffset("),
			"Navic must reuse Komikku's rotated Slider rail instead of replacing or covering it with custom rail input handling."
		)
	}

	@Test
	fun commonReaderBottomMenuDoesNotRenderOverShellCover() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()

		assertContains(appBarsText, "val showBottomBar = visible && !controllerState.shellCoverVisible")
		assertContains(appBarsText, "visible = showBottomBar")
		assertFalse(
			appBarsText.contains("visible = visible,\n\t\t\tenter = slideInVertically(initialOffsetY = { it }"),
			"The shell cover is owned by the native/Komikku reader surface; the bottom menu must not render over it."
		)
	}

	@Test
	fun commonReaderBottomToolbarDoesNotExposeDuplicateSettingsDialogs() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val bottomBarBody = appBarsText.substringAfter("private fun KomikkuReaderBottomBar(")
			.substringBefore("\n}\n\nprivate enum class KomikkuReaderBottomButton")
		val defaultsBody = appBarsText.substringAfter("val NAVIC_SUPPORTED_DEFAULTS = setOf(")
			.substringBefore("\n\t\t).map")

		assertContains(bottomBarBody, "IconButton(onClick = onSettings)")
		assertContains(bottomBarBody, "IconButton(onClick = onContents)")
		assertContains(bottomBarBody, "IconButton(onClick = onSearch)")
		assertFalse(
			defaultsBody.contains("ReadingMode"),
			"The bottom toolbar must not expose a second button that opens the same settings sheet."
		)
		assertFalse(
			bottomBarBody.contains("onReadingMode"),
			"The bottom toolbar should have one settings entry; reading-mode tabs live inside that settings sheet until a distinct route exists."
		)
		assertFalse(
			bottomBarBody.contains("Icons.Outlined.Book"),
			"The book icon currently duplicates the settings sheet and should not be rendered as a bottom action."
		)
	}

	@Test
	fun commonReaderProgressAndSettingsUseSharedKomikkuIntegerSliderPrimitive() {
		val navigatorText = readerCommonUiFile("ReaderChapterNavigator.kt").readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val sliderPrimitiveText = readerCommonUiFile("KomikkuIntegerSlider.kt").readText()
		val komikkuSliderText = listOf(
			File("tmp/references/komikku/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Slider.kt"),
			File("../tmp/references/komikku/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Slider.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku material Slider.kt reference")

		assertContains(komikkuSliderText, "fun Slider(")
		assertContains(komikkuSliderText, "value: Int")
		assertContains(komikkuSliderText, "valueRange: IntProgression = 0..1")
		assertContains(komikkuSliderText, "SliderDefaults.Thumb")
		assertContains(komikkuSliderText, "SliderDefaults.Track")
		assertContains(komikkuSliderText, "onValueChange = { onValueChange(it.roundToInt()) }")
		assertContains(sliderPrimitiveText, "internal fun KomikkuIntegerSlider(")
		assertContains(sliderPrimitiveText, "value: Int")
		assertContains(sliderPrimitiveText, "valueRange: IntProgression = 0..1")
		assertContains(sliderPrimitiveText, "SliderDefaults.Thumb")
		assertContains(sliderPrimitiveText, "SliderDefaults.Track")
		assertContains(sliderPrimitiveText, "onValueChange = { onValueChange(it.roundToInt()) }")
		assertContains(navigatorText, "KomikkuIntegerSlider(")
		assertContains(settingsDialogText, "KomikkuIntegerSlider(")
		assertFalse(
			navigatorText.contains("import androidx.compose.material3.Slider") ||
				settingsDialogText.contains("private fun KomikkuIntegerSlider("),
			"Working direct Material3 sliders and private settings-only wrappers are non-faithful; the reader should share Komikku's integer slider primitive."
		)
	}

	@Test
	fun commonReaderChapterNavigatorUsesKomikkuTabletPaddingContract() {
		val navigatorText = readerCommonUiFile("ReaderChapterNavigator.kt").readText()
		val platformHostsText = readerCommonUiFile("ReaderPlatformHosts.kt").readText()
		val androidTabletText = listOf(
			File("src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderTabletUi.android.kt"),
			File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderTabletUi.android.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: ""
		val horizontalBody = navigatorText.substringAfter("internal fun KomikkuChapterNavigator(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuChapterProgressSlider(")
		val verticalBody = navigatorText.substringAfter("private fun KomikkuChapterNavigatorVertical(")
			.substringBefore("\n}\n")
		val komikkuNavigatorText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku ChapterNavigator.kt reference")

		assertContains(komikkuNavigatorText, "val isTabletUi = isTabletUi()")
		assertContains(komikkuNavigatorText, "val horizontalPadding = if (isTabletUi) 24.dp else 8.dp")
		assertContains(komikkuNavigatorText, "val verticalPadding = if (isTabletUi) 24.dp else 8.dp")
		assertContains(platformHostsText, "expect fun komikkuReaderIsTabletUi(): Boolean")
		assertContains(androidTabletText, "LocalConfiguration.current.smallestScreenWidthDp")
		assertContains(androidTabletText, "KomikkuTabletUiRequiredScreenWidthDp = 720")
		assertContains(horizontalBody, "val isTabletUi = komikkuReaderIsTabletUi()")
		assertContains(horizontalBody, "val horizontalPadding = if (isTabletUi) 24.dp else 8.dp")
		assertContains(horizontalBody, ".padding(horizontal = horizontalPadding)")
		assertContains(verticalBody, "val isTabletUi = komikkuReaderIsTabletUi()")
		assertContains(verticalBody, "val verticalPadding = if (isTabletUi) 24.dp else 8.dp")
		assertContains(verticalBody, ".padding(vertical = verticalPadding, horizontal = 8.dp)")
		assertFalse(
			horizontalBody.contains(".fillMaxWidth()\n\t\t\t\t.padding(horizontal = 8.dp)") ||
				verticalBody.contains(".fillMaxHeight()\n\t\t\t.padding(vertical = 8.dp, horizontal = 8.dp)"),
			"Komikku changes chapter navigator padding on tablet UI; hard-coded phone padding is a non-faithful visual fork."
		)
	}

	@Test
	fun commonReaderChapterNavigatorHonorsRtlDirectionLikeKomikku() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val navigatorText = readerCommonUiFile("ReaderChapterNavigator.kt").readText()
		val appBarsBody = appBarsText.substringAfter("internal fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")
		val navigatorBody = navigatorText.substringAfter("internal fun KomikkuChapterNavigator(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuChapterProgressSlider(")
		val komikkuNavigatorText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/components/ChapterNavigator.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku ChapterNavigator.kt reference")

		assertContains(komikkuNavigatorText, "LocalLayoutDirection provides LayoutDirection.Ltr")
		assertContains(komikkuNavigatorText, "LocalLayoutDirection provides layoutDirection")
		assertContains(komikkuNavigatorText, "enabled = if (isRtl) enabledNext else enabledPrevious")
		assertContains(komikkuNavigatorText, "onClick = if (isRtl) onNextChapter else onPreviousChapter")
		assertContains(komikkuNavigatorText, "stringResource(")
		assertContains(komikkuNavigatorText, "if (isRtl) MR.strings.action_next_chapter else MR.strings.action_previous_chapter")
		assertContains(komikkuNavigatorText, "if (isRtl) MR.strings.action_previous_chapter else MR.strings.action_next_chapter")
		assertContains(appBarsBody, "val isRtl = normalizedReaderDirection(controllerState.chrome.settings.direction) == ReaderDirectionRtl")
		assertContains(appBarsBody, "isRtl = isRtl")
		assertContains(navigatorBody, "isRtl: Boolean")
		assertContains(navigatorBody, "val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr")
		assertContains(navigatorBody, "CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)")
		assertContains(navigatorBody, "CompositionLocalProvider(LocalLayoutDirection provides layoutDirection)")
		assertContains(navigatorBody, "enabled = if (isRtl) enabledNext else enabledPrevious")
		assertContains(navigatorBody, "onClick = if (isRtl) onNextChapter else onPreviousChapter")
		assertContains(navigatorBody, "enabled = if (isRtl) enabledPrevious else enabledNext")
		assertContains(navigatorBody, "onClick = if (isRtl) onPreviousChapter else onNextChapter")
		assertContains(navigatorBody, "stringResource(")
		assertContains(navigatorBody, "if (isRtl) Res.string.action_next_chapter else Res.string.action_previous_chapter")
		assertContains(navigatorBody, "if (isRtl) Res.string.action_previous_chapter else Res.string.action_next_chapter")
		assertFalse(
			navigatorBody.contains("contentDescription = if (isRtl) \"Next\" else \"Previous\"") ||
				navigatorBody.contains("contentDescription = if (isRtl) \"Previous\" else \"Next\""),
			"RTL chapter navigation labels must follow Komikku's resource-backed mapping, not hardcoded strings."
		)
	}

	@Test
	fun commonReaderProgressRailPlacementIsControllerSettingNotHardcoded() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()
		val preferencesText = listOf(
			File("src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt"),
			File("composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate PreferenceManager.kt")
		val appBarsBody = appBarsText.substringAfter("internal fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")

		assertContains(settingsDialogText, "ReaderSupportedNavBarTypes")
		assertContains(settingsDialogText, "readerNavBarTypeShortLabel")
		assertContains(appBarsText, "readerEffectiveNavBarTypeFor")
		assertContains(appBarsBody, "val navBarType = readerEffectiveNavBarTypeFor(controllerState.chrome.settings)")
		assertFalse(
			appBarsBody.contains("val navBarType = KomikkuNavBarType.VerticalRight"),
			"Komikku nav bar placement must be a reader setting, not a hardcoded right rail."
		)
		assertContains(settingsDialogBody, "title = \"Progress rail\"")
		assertContains(settingsDialogBody, "settings.copy(navBarType = navBarType)")
		assertContains(preferencesText, "readerNavBarType")
		assertContains(ebooksSettingsText, "option_ebook_reader_nav_bar_type")
		assertContains(ebooksSettingsText, "ReaderNavBarTypeOption")
		assertContains(searchSettingsText, "ebooks.nav-bar-type")
		assertContains(searchSettingsText, "readerNavBarType")
	}

	@Test
	fun commonReaderChromeSeparatesTopPanelFromBottomActions() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val appBarsBody = appBarsText.substringAfter("internal fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")
		val topChromeBody = appBarsText.substringAfter("private fun KomikkuReaderTopBar(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderBottomBar(")
		val bottomChromeBody = appBarsText.substringAfter("private fun KomikkuReaderBottomBar(")

		assertContains(appBarsBody, "KomikkuReaderTopBar(")
		assertContains(topChromeBody, "bookmarked: Boolean")
		assertContains(topChromeBody, "canBookmark: Boolean")
		assertContains(topChromeBody, "onToggleBookmarked")
		assertContains(topChromeBody, "Icons.Outlined.Bookmark")
		assertContains(topChromeBody, "chapterTitle")
		assertFalse(
			bottomChromeBody.contains("chapterTitle") ||
				bottomChromeBody.contains("state.progressLabel"),
			"Bottom chrome must be an action strip; title/progress belongs in the top panel and side rail."
		)
	}

	@Test
	fun commonReaderChapterNavigatorArrowsUseTocChapterNavigationCallbacks() {
		val readerScreenText = readerCommonUiFile("ReaderScreen.kt").readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val appBarsBody = appBarsText.substringAfter("internal fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")

		assertContains(appBarsText, "onPreviousChapter: () -> Unit")
		assertContains(appBarsText, "onNextChapter: () -> Unit")
		assertContains(readerRootText, "onPreviousChapter: () -> Unit")
		assertContains(readerRootText, "onNextChapter: () -> Unit")
		assertContains(readerScreenText, "coordinator.navigateToPreviousChapter()")
		assertContains(readerScreenText, "coordinator.navigateToNextChapter()")
		assertContains(appBarsBody, "enabledNext = controllerState.canNavigateToNextChapter")
		assertContains(appBarsBody, "enabledPrevious = controllerState.canNavigateToPreviousChapter")
		assertFalse(
			appBarsBody.contains("onNextChapter = onNextPage") ||
				appBarsBody.contains("onPreviousChapter = onPreviousPage"),
			"Komikku chapter navigator arrows must navigate TOC chapters; page turns belong to the native tap/drag overlay."
		)
	}

	@Test
	fun commonReaderAppBarsOwnSharedKomikkuBarBackgroundAtHostLevel() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val appBarsBody = appBarsText.substringAfter("internal fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")
		val topChromeBody = appBarsText.substringAfter("private fun KomikkuReaderTopBar(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderBottomBar(")
		val bottomChromeBody = appBarsText.substringAfter("private fun KomikkuReaderBottomBar(")
		val komikkuAppBarsText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku ReaderAppBars.kt reference")

		assertContains(komikkuAppBarsText, "val backgroundColor = MaterialTheme.colorScheme")
		assertContains(komikkuAppBarsText, ".surfaceColorAtElevation(3.dp)")
		assertContains(komikkuAppBarsText, ".copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)")
		assertContains(appBarsText, "import androidx.compose.foundation.background")
		assertContains(appBarsText, "import androidx.compose.foundation.isSystemInDarkTheme")
		assertContains(appBarsText, "import androidx.compose.foundation.layout.WindowInsets")
		assertContains(appBarsText, "import androidx.compose.foundation.layout.navigationBars")
		assertContains(appBarsText, "import androidx.compose.foundation.layout.windowInsetsPadding")
		assertContains(appBarsBody, "val backgroundColor = MaterialTheme.colorScheme")
		assertContains(appBarsBody, ".surfaceColorAtElevation(3.dp)")
		assertContains(appBarsBody, ".copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)")
		assertContains(appBarsBody, ".background(backgroundColor)")
		assertContains(appBarsBody, ".windowInsetsPadding(WindowInsets.navigationBars)")
		assertFalse(
			topChromeBody.contains("Surface(") ||
				bottomChromeBody.contains("Surface(") ||
				topChromeBody.contains(".copy(alpha = 0.92f)") ||
				bottomChromeBody.contains(".copy(alpha = 0.92f)") ||
				topChromeBody.contains(".surfaceColorAtElevation(3.dp)") ||
				bottomChromeBody.contains(".surfaceColorAtElevation(3.dp)"),
			"Komikku ReaderAppBars owns the shared bar background; top/bottom bars should not create Navic-local Surface backgrounds or fixed alpha values."
		)
	}

	@Test
	fun commonReaderTopBarUsesPortedKomikkuAppBarPrimitive() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val topChromeBody = appBarsText.substringAfter("private fun KomikkuReaderTopBar(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderBottomBar(")
		val komikkuTopBarText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderTopBar.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderTopBar.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku ReaderTopBar.kt reference")
		val komikkuAppBarText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/components/AppBar.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/components/AppBar.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku AppBar.kt reference")

		assertContains(komikkuTopBarText, "AppBar(")
		assertContains(komikkuTopBarText, "backgroundColor = Color.Transparent")
		assertContains(komikkuTopBarText, "AppBarActions(")
		assertContains(komikkuAppBarText, "fun AppBar(")
		assertContains(komikkuAppBarText, "fun AppBarTitle(")
		assertContains(komikkuAppBarText, "fun AppBarActions(")
		assertContains(komikkuAppBarText, "sealed interface AppBar")
		assertContains(appBarsText, "import androidx.compose.material3.TopAppBar")
		assertContains(appBarsText, "private fun KomikkuReaderAppBar(")
		assertContains(appBarsText, "private fun KomikkuReaderAppBarTitle(")
		assertContains(appBarsText, "private fun KomikkuReaderAppBarActions(")
		assertContains(appBarsText, "private sealed interface KomikkuReaderAppBarAction")
		assertContains(topChromeBody, "KomikkuReaderAppBar(")
		assertContains(topChromeBody, "backgroundColor = Color.Transparent")
		assertContains(topChromeBody, "title = title")
		assertContains(topChromeBody, "subtitle = chapterTitle")
		assertContains(topChromeBody, "navigateUp = onNavigateBack")
		assertContains(topChromeBody, "KomikkuReaderAppBarActions(")
		assertFalse(
			topChromeBody.contains("Row(") ||
				topChromeBody.contains("headlineSmall") ||
				topChromeBody.contains("bodyLarge") ||
				topChromeBody.contains("Arrangement.spacedBy(14.dp)"),
			"ReaderTopBar should be a port of Komikku's AppBar/AppBarActions path, not a custom Navic Row with local typography and spacing."
		)
	}

	@Test
	fun commonReaderBottomActionsAreCenteredAndDoNotDuplicateBookmarkAction() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val bottomChromeBody = appBarsText.substringAfter("private fun KomikkuReaderBottomBar(")

		assertContains(bottomChromeBody, "Row(")
		assertContains(bottomChromeBody, ".fillMaxWidth()")
		assertContains(bottomChromeBody, "horizontalArrangement = Arrangement.SpaceEvenly")
		assertContains(bottomChromeBody, "verticalAlignment = Alignment.CenterVertically")
		assertFalse(
			bottomChromeBody.contains("horizontalScroll("),
			"Komikku's bottom actions are centered/distributed, not a left-aligned horizontally scrolling toolbar."
		)
		assertFalse(
			bottomChromeBody.contains("Icons.Filled.Star") || bottomChromeBody.contains("Icons.Outlined.Star"),
			"The bookmark/star affordance belongs in the top chrome; duplicating it in the bottom action row makes the menu feel inconsistent."
		)
	}

	@Test
	fun commonReaderBottomBarUsesKomikkuBottomButtonActionModel() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val appBarsBody = appBarsText.substringAfter("internal fun KomikkuReaderAppBars(")
		val bottomChromeBody = appBarsText.substringAfter("private fun KomikkuReaderBottomBar(")
		val bottomButtonModel = appBarsText.substringAfter("private enum class KomikkuReaderBottomButton(")
		val komikkuBottomBarText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt")
		).firstOrNull(File::isFile)?.readText()
			?: error("Could not locate Komikku ReaderBottomBar.kt reference")
		val komikkuBottomButtonText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderBottomButton.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderBottomButton.kt")
		).firstOrNull(File::isFile)?.readText()
			?: error("Could not locate Komikku ReaderBottomButton.kt reference")

		assertContains(komikkuBottomBarText, "ReaderBottomButton.ViewChapters")
		assertContains(komikkuBottomBarText, "Icons.Outlined.FormatListNumbered")
		assertContains(komikkuBottomBarText, "Icons.Outlined.Settings")
		assertContains(komikkuBottomBarText, "stringResource(MR.strings.chapters)")
		assertContains(komikkuBottomBarText, "stringResource(MR.strings.viewer)")
		assertContains(komikkuBottomBarText, "stringResource(MR.strings.action_settings)")
		assertContains(komikkuBottomButtonText, "enum class ReaderBottomButton(val value: String")
		assertContains(komikkuBottomButtonText, "fun isIn(buttons: Collection<String>) = value in buttons")
		assertContains(komikkuBottomButtonText, "val BUTTONS_DEFAULTS = setOf(")
		assertContains(appBarsText, "import navic.composeapp.generated.resources.Res")
		assertContains(appBarsText, "import org.jetbrains.compose.resources.stringResource")
		assertContains(appBarsText, "private enum class KomikkuReaderBottomButton(val value: String)")
		assertContains(bottomButtonModel, "ViewChapters(\"vc\")")
		assertContains(bottomButtonModel, "WebView(\"wb\")")
		assertContains(bottomButtonModel, "Browser(\"br\")")
		assertContains(bottomButtonModel, "Share(\"sh\")")
		assertContains(bottomButtonModel, "ReadingMode(\"rm\")")
		assertContains(bottomButtonModel, "Rotation(\"rot\")")
		assertContains(bottomButtonModel, "CropBordersPager(\"cbp\")")
		assertContains(bottomButtonModel, "CropBordersContinuesVertical(\"cbc\")")
		assertContains(bottomButtonModel, "CropBordersWebtoon(\"cbw\")")
		assertContains(bottomButtonModel, "PageLayout(\"pl\")")
		assertContains(bottomButtonModel, "fun isIn(buttons: Collection<String>) = value in buttons")
		assertContains(bottomButtonModel, "val BUTTONS_DEFAULTS = setOf(")
		assertContains(appBarsBody, "val enabledButtons = KomikkuReaderBottomButton.NAVIC_SUPPORTED_DEFAULTS")
		assertContains(bottomChromeBody, "enabledButtons: Set<String>")
		assertContains(bottomChromeBody, "KomikkuReaderBottomButton.ViewChapters.isIn(enabledButtons)")
		assertContains(bottomChromeBody, "IconButton(onClick = onSettings)")
		assertContains(bottomChromeBody, "Icons.Outlined.FormatListNumbered")
		assertContains(bottomChromeBody, "Icons.Outlined.Settings")
		assertContains(bottomChromeBody, "stringResource(Res.string.title_chapters)")
		assertContains(bottomChromeBody, "stringResource(Res.string.title_settings)")
		assertFalse(
			bottomChromeBody.contains("KomikkuReaderBottomButton.ReadingMode.isIn(enabledButtons)") ||
				bottomChromeBody.contains("stringResource(Res.string.action_reader_reading_mode)"),
			"Navic keeps Komikku's ReaderBottomButton model, but must not render ReadingMode as a duplicate settings-sheet entry."
		)
		assertFalse(
			bottomChromeBody.contains("showWhispersyncPlayer") ||
				bottomChromeBody.contains("onWhispersyncPlayer") ||
				bottomChromeBody.contains("Icons.Outlined.Audiobooks") ||
				bottomChromeBody.contains("Whispersync player"),
			"Whispersync playback belongs to the page-scoped headset/control surface; the Komikku bottom bar must not grow a Navic-specific audiobook shortcut."
		)
		assertFalse(
			appBarsBody.contains("val bottomActions = listOf(") ||
				appBarsText.contains("private sealed interface KomikkuReaderBottomAction") ||
				bottomChromeBody.contains("contentDescription = \"Contents\"") ||
				bottomChromeBody.contains("contentDescription = \"Reading mode\"") ||
				bottomChromeBody.contains("contentDescription = \"Settings\"") ||
				bottomChromeBody.contains("Icons.Outlined.List") ||
				bottomChromeBody.contains("Icons.Filled.Settings"),
			"ReaderBottomBar should use Komikku's enabled ReaderBottomButton model, resource-backed labels, and outlined reference icons, not the old Navic list/settings shortcuts."
		)
	}

	@Test
	fun commonReaderContentsDialogLivesInDedicatedKomikkuComponentFile() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val contentsDialogText = readerCommonUiFile("ReaderContentsDialog.kt").readText()

		assertContains(readerRootText, "KomikkuReaderContentsDialog(")
		assertFalse(
			readerScreenText.contains("private fun KomikkuReaderContentsDialog("),
			"Komikku contents/chapter list dialog must live in its own reader component file, not inside ReaderScreen."
		)
		assertContains(contentsDialogText, "internal fun KomikkuReaderContentsDialog(")
		assertContains(contentsDialogText, "BasicAlertDialog(")
		assertContains(contentsDialogText, "LazyColumn(")
	}

	@Test
	fun commonReaderContentOverlayLivesInDedicatedKomikkuComponentFile() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val contentOverlayText = readerCommonUiFile("ReaderContentOverlay.kt").readText()

		assertContains(readerRootText, "KomikkuReaderContentOverlay(")
		assertFalse(
			readerScreenText.contains("private fun KomikkuReaderContentOverlay(") ||
				readerScreenText.contains("private fun readerColorFilterColor(") ||
				readerScreenText.contains("private fun readerColorFilterBlendMode("),
			"Komikku content/filter overlay must live in its own reader component file, not inside ReaderScreen."
		)
		assertContains(contentOverlayText, "internal fun KomikkuReaderContentOverlay(")
		assertContains(contentOverlayText, "Ported from Komikku ReaderContentOverlay")
		assertContains(contentOverlayText, "drawRect(Color.Black.copy")
		assertContains(contentOverlayText, "blendMode = colorBlendMode ?: BlendMode.SrcOver")
		assertContains(contentOverlayText, "internal fun readerColorFilterColor(")
		assertContains(contentOverlayText, "internal fun readerColorFilterBlendMode(")
	}

	@Test
	fun commonReaderPaginationProfileBadgeLivesInDedicatedKomikkuOverlayFile() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val profileBadgeText = readerCommonUiFile("ReaderPaginationProfileBadge.kt").readText()

		assertContains(readerRootText, "KomikkuPaginationProfileStatusBadge(")
		assertFalse(
			readerScreenText.contains("private fun KomikkuPaginationProfileStatusBadge(") ||
				readerScreenText.contains("readerPaginationBadgeFadeAnimationSpec"),
			"Komikku pagination profiling status belongs in its own overlay component, not inside ReaderScreen."
		)
		assertContains(profileBadgeText, "internal fun KomikkuPaginationProfileStatusBadge(")
		assertContains(profileBadgeText, "private val readerPaginationBadgeFadeAnimationSpec = tween<Float>(150)")
		assertContains(profileBadgeText, "AnimatedVisibility(")
		assertContains(profileBadgeText, "profile.status == \"measuring\" || profile.status == \"failed\"")
		assertContains(profileBadgeText, "LinearProgressIndicator(")
	}

	@Test
	fun commonReaderSuppressesPaginationProfileBadgeOverNativeCover() {
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val composeOverlayBody = readerRootText.substringAfter("private fun KomikkuComposeOverlay(")

		assertContains(composeOverlayBody, "if (!controllerState.shellCoverVisible)")
		assertTrue(
			composeOverlayBody.indexOf("if (!controllerState.shellCoverVisible)") <
				composeOverlayBody.indexOf("KomikkuPaginationProfileStatusBadge("),
			"Pagination/profile status is reader diagnostics chrome, not cover content; it must be suppressed while the native shell cover is visible."
		)
	}

	@Test
	fun commonReaderSearchIsKomikkuOverlayAndControllerRouted() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val searchDialogText = readerCommonUiFile("ReaderSearchDialog.kt").readText()

		assertContains(appBarsText, "Icons.Outlined.Search")
		assertContains(appBarsText, "onSearch")
		assertContains(readerRootText, "KomikkuReaderSearchDialog(")
		assertContains(readerRootText, "controllerState.search")
		assertContains(readerRootText, "onSearchQuery = onSearchQuery")
		assertContains(readerRootText, "onNavigateToSearchResult = onNavigateToSearchResult")
		assertContains(readerRootText, "onDismissSearch = onDismissSearch")
		assertContains(readerRootText, "ReaderControllerDialog.Search -> KomikkuReaderSearchDialog(")
		assertFalse(
			readerScreenText.contains("private fun KomikkuReaderSearchDialog(") ||
				readerScreenText.contains("ReaderSearchState("),
			"Reader search UI must be a Komikku overlay component routed through controller state, not local ReaderScreen state."
		)
		assertContains(searchDialogText, "internal fun KomikkuReaderSearchDialog(")
		assertContains(searchDialogText, "ReaderSearchState")
		assertContains(searchDialogText, "ReaderSearchResult")
		assertContains(searchDialogText, "TextField(")
		assertContains(searchDialogText, "FocusRequester")
		assertContains(searchDialogText, "focusRequester(")
		assertContains(searchDialogText, "requestFocus()")
		assertContains(searchDialogText, "KeyboardActions(")
		assertContains(searchDialogText, "ImeAction.Search")
		assertContains(searchDialogText, "LazyColumn(")
		assertContains(searchDialogText, "items(")
		assertContains(searchDialogText, "onSearchQuery(queryText)")
		assertContains(searchDialogText, "onNavigateToSearchResult(result)")
		assertContains(readerScreenText, "coordinator.openSearchDialog()")
		assertContains(readerScreenText, "coordinator.search(query)")
		assertContains(readerScreenText, "coordinator.navigateToSearchResult(result)")
		assertContains(readerScreenText, "coordinator.closeSearchDialog()")
	}

	@Test
	fun commonReaderSelectionActionsAreKomikkuOverlayAndControllerRouted() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val selectionActionsText = readerCommonUiFile("ReaderSelectionActions.kt").readText()
		val selectionNoteDialogText = readerCommonUiFile("ReaderSelectionNoteDialog.kt").readText()
		val engineHostText = readerEngineWebViewHostFile().readText()

		assertContains(readerRootText, "KomikkuReaderSelectionActions(")
		assertContains(readerRootText, "controllerState.selectionActions")
		assertContains(readerRootText, "KomikkuReaderSelectionNoteDialog(")
		assertContains(readerRootText, "controllerState.selectionNoteDraft")
		assertContains(readerRootText, "onHighlightSelection = onHighlightSelection")
		assertContains(readerRootText, "onCopySelection = onCopySelection")
		assertContains(readerRootText, "onStartSelectionNote = onStartSelectionNote")
		assertContains(readerRootText, "onSaveSelectionNote = onSaveSelectionNote")
		assertContains(readerRootText, "onDismissSelectionNote = onDismissSelectionNote")
		assertFalse(
			readerScreenText.contains("private fun KomikkuReaderSelectionActions(") ||
				readerScreenText.contains("private fun KomikkuReaderSelectionNoteDialog(") ||
				readerScreenText.contains("ReaderSelectionActionState("),
			"Selection action UI must be a Komikku overlay component, not local ReaderScreen state."
		)
		assertContains(selectionActionsText, "internal fun KomikkuReaderSelectionActions(")
		assertContains(selectionActionsText, "ReaderSelectionActionState")
		assertContains(selectionActionsText, "Icons.Outlined.Copy")
		assertContains(selectionActionsText, "Icons.Outlined.Note")
		assertContains(selectionActionsText, "onHighlightSelection")
		assertContains(selectionActionsText, "selectionActions.selectedText?.let(onCopySelection)")
		assertContains(selectionActionsText, "onStartSelectionNote")
		assertContains(selectionNoteDialogText, "internal fun KomikkuReaderSelectionNoteDialog(")
		assertContains(selectionNoteDialogText, "ReaderSelectionNoteDraft")
		assertContains(selectionNoteDialogText, "OutlinedTextField(")
		assertContains(selectionNoteDialogText, "onSaveSelectionNote(noteText)")
		assertContains(selectionNoteDialogText, "onDismissSelectionNote")
		assertContains(readerScreenText, "LocalClipboardManager.current")
		assertContains(readerScreenText, "AnnotatedString(text)")
		assertContains(readerScreenText, "Logger.i(ReaderScreenTag, \"Reader selection copied length=")
		assertContains(readerScreenText, "coordinator.dismissSelectionActions()")
		assertContains(readerScreenText, "Logger.i(ReaderScreenTag, \"Reader selection note save length=")
		assertContains(readerScreenText, "coordinator.addSelectionHighlight()")
		assertContains(readerScreenText, "coordinator.startSelectionNote()")
		assertContains(readerScreenText, "coordinator.saveSelectionNote(note)")
		assertContains(readerScreenText, "coordinator.dismissSelectionNote()")
		assertContains(engineHostText, "isLongClickable = false")
		assertContains(engineHostText, "setOnLongClickListener")
		assertContains(engineHostText, "native frame owns selection actions")
	}

	@Test
	fun androidReaderNoteAnnotationBatchesAreVisibleInBridgeCommandLogs() {
		val engineHostText = readerEngineWebViewHostFile().readText()
		val applyHighlightsLabel = engineHostText
			.substringAfter("is ReaderBridgeCommand.ApplyHighlights ->")
			.substringBefore("is ReaderBridgeCommand.ApplyOverlayFragment ->")

		assertContains(applyHighlightsLabel, "highlights.count")
		assertContains(applyHighlightsLabel, "it.note?.trim()?.isNotEmpty() == true")
		assertContains(applyHighlightsLabel, "notes=")
		assertContains(
			applyHighlightsLabel,
			"applyHighlights(count=\${highlights.size}, notes=\$noteCount)",
			message = "ADB/logcat command labels must distinguish Note Save annotation batches from plain highlight batches."
		)
	}

	@Test
	fun commonReaderAnnotationClickIsKomikkuOverlayAndControllerRouted() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val annotationDialogText = readerCommonUiFile("ReaderAnnotationDialog.kt").readText()

		assertContains(readerRootText, "KomikkuReaderAnnotationDialog(")
		assertContains(readerRootText, "controllerState.annotationPopup")
		assertContains(readerRootText, "onDismissAnnotationPopup = onDismissAnnotationPopup")
		assertFalse(
			readerScreenText.contains("private fun KomikkuReaderAnnotationDialog(") ||
				readerScreenText.contains("ReaderAnnotationPopupState("),
			"Annotation-click UI must be a Komikku overlay component routed through controller state, not local ReaderScreen state."
		)
		assertContains(annotationDialogText, "internal fun KomikkuReaderAnnotationDialog(")
		assertContains(annotationDialogText, "ReaderAnnotationPopupState")
		assertContains(annotationDialogText, "BasicAlertDialog(")
		assertContains(annotationDialogText, "onDismissAnnotationPopup")
		assertContains(readerScreenText, "coordinator.dismissAnnotationPopup()")
	}

	@Test
	fun commonReaderMarksAreSeededAndPersistedOutsideTheWebView() {
		val readerScreenText = readerScreenFile().readText()
		val readerMarksPreferenceText = readerCommonFile("ReaderMarksPreference.kt").readText()

		assertContains(readerMarksPreferenceText, "fun PreferenceManager.readerAnnotationState()")
		assertContains(readerMarksPreferenceText, "decodeReaderAnnotations(readerAnnotationsJson)")
		assertContains(readerMarksPreferenceText, "fun PreferenceManager.readerBookmarkState()")
		assertContains(readerMarksPreferenceText, "decodeReaderBookmarks(readerBookmarksJson)")
		assertContains(readerMarksPreferenceText, "fun PreferenceManager.persistReaderMarksIfChanged(")
		assertContains(readerMarksPreferenceText, "previous.annotations != next.annotations")
		assertContains(readerMarksPreferenceText, "previous.bookmarks != next.bookmarks")
		assertContains(readerScreenText, "annotations = preferenceManager.readerAnnotationState()")
		assertContains(readerScreenText, "bookmarks = preferenceManager.readerBookmarkState()")
		assertContains(readerScreenText, "preferenceManager.persistReaderMarksIfChanged(")
		assertFalse(
			readerScreenText.contains("ReaderAnnotationState()") &&
				!readerScreenText.contains("readerAnnotationState()"),
			"ReaderScreen must not initialize annotations as transient empty state."
		)
	}

	@Test
	fun commonReaderFootnotesAreKomikkuOverlayAndControllerRouted() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val footnoteDialogText = readerCommonUiFile("ReaderFootnoteDialog.kt").readText()

		assertContains(readerRootText, "KomikkuReaderFootnoteDialog(")
		assertContains(readerRootText, "controllerState.footnotePopup")
		assertContains(readerRootText, "onDismissFootnotePopup = onDismissFootnotePopup")
		assertFalse(
			readerScreenText.contains("private fun KomikkuReaderFootnoteDialog(") ||
				readerScreenText.contains("ReaderFootnotePopupState("),
			"Footnote UI must be a Komikku overlay component routed through controller state, not local ReaderScreen state."
		)
		assertContains(footnoteDialogText, "internal fun KomikkuReaderFootnoteDialog(")
		assertContains(footnoteDialogText, "ReaderFootnotePopupState")
		assertContains(footnoteDialogText, "BasicAlertDialog(")
		assertContains(footnoteDialogText, "onDismissFootnotePopup")
		assertContains(readerScreenText, "coordinator.dismissFootnotePopup()")
	}

	@Test
	fun commonReaderExternalLinksAreKomikkuOverlayAndNativeUriRouted() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val externalLinkDialogText = readerCommonUiFile("ReaderExternalLinkDialog.kt").readText()

		assertContains(readerRootText, "KomikkuReaderExternalLinkDialog(")
		assertContains(readerRootText, "controllerState.externalLinkPrompt")
		assertContains(readerRootText, "onOpenExternalLink = onOpenExternalLink")
		assertContains(readerRootText, "onDismissExternalLinkPrompt = onDismissExternalLinkPrompt")
		assertFalse(
			readerScreenText.contains("private fun KomikkuReaderExternalLinkDialog(") ||
				readerScreenText.contains("ReaderExternalLinkPromptState("),
			"External-link UI must be a Komikku overlay component routed through controller state, not local ReaderScreen state."
		)
		assertContains(externalLinkDialogText, "internal fun KomikkuReaderExternalLinkDialog(")
		assertContains(externalLinkDialogText, "ReaderExternalLinkPromptState")
		assertContains(externalLinkDialogText, "BasicAlertDialog(")
		assertContains(externalLinkDialogText, "onOpenExternalLink(link.href)")
		assertContains(externalLinkDialogText, "onDismissExternalLinkPrompt")
		assertContains(readerScreenText, "LocalUriHandler.current")
		assertContains(readerScreenText, "uriHandler.openUri(url)")
		assertContains(readerScreenText, "coordinator.dismissExternalLinkPrompt()")
	}

	@Test
	fun commonReaderNavigatorSettingsMappingLivesWithKomikkuNavigationModel() {
		val readerScreenText = readerScreenFile().readText()
		val readerNavigationText = readerCommonUiFile("ReaderNavigation.kt").readText()

		assertContains(readerScreenText, "komikkuNavigatorForReaderSettings(settings)")
		assertFalse(
			readerScreenText.contains("private fun komikkuNavigatorForReaderSettings(") ||
				readerScreenText.contains("private fun komikkuTappingInvertMode("),
			"Navic reader settings to Komikku navigator mapping must live with the navigation model, not inside ReaderScreen."
		)
		assertContains(readerNavigationText, "internal fun komikkuNavigatorForReaderSettings(")
		assertContains(readerNavigationText, "internal fun komikkuTappingInvertMode(")
		assertContains(readerNavigationText, "readerDefaultTapZoneMode(settings.flowMode)")
		assertContains(readerNavigationText, "KomikkuLNavigation(smallerTapZone)")
		assertContains(readerNavigationText, "KomikkuKindlishNavigation(smallerTapZone)")
		assertContains(readerNavigationText, "KomikkuEdgeNavigation(smallerTapZone)")
		assertContains(readerNavigationText, "KomikkuRightAndLeftNavigation(smallerTapZone)")
		assertContains(readerNavigationText, "KomikkuDisabledNavigation(smallerTapZone)")
		assertContains(readerNavigationText, "navigation.invertMode = komikkuTappingInvertMode(settings.tapZoneInvertMode)")
	}

	@Test
	fun commonReaderRootAndComposeOverlayLiveInDedicatedKomikkuShellFile() {
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()

		assertContains(readerScreenText, "KomikkuReaderRoot(")
		assertFalse(
			readerScreenText.contains("private fun KomikkuReaderRoot(") ||
				readerScreenText.contains("private fun KomikkuComposeOverlay(") ||
				readerScreenText.contains("private fun shellCoverTitleFor("),
			"Komikku shell root, retained viewer slot, shell-cover title mapping, and overlay stack must not live inside ReaderScreen."
		)
		assertContains(readerRootText, "internal fun KomikkuReaderRoot(")
		assertContains(readerRootText, "private fun shellCoverTitleFor(")
		assertContains(readerRootText, "private fun KomikkuComposeOverlay(")
		assertContains(readerRootText, "ReaderViewerLifecycleSlot()")
		assertContains(readerRootText, "viewerSlot.update(viewState)")
		assertContains(readerRootText, "KomikkuReaderNativeFrameHost(")
		assertContains(readerRootText, "ReaderViewerHost(")
		assertContains(readerRootText, "readerShellCoverViewerActionFor(action)")
		assertContains(readerRootText, "viewer.viewerActionFor(action)")
		assertContains(readerRootText, "KomikkuReaderContentOverlay(")
		assertContains(readerRootText, "KomikkuReaderAppBars(")
		assertContains(readerRootText, "KomikkuReaderContentsDialog(")
		assertContains(readerRootText, "KomikkuReaderSettingsDialog(")
	}

	@Test
	fun commonReaderEngineOpenRequestFactoryLivesOutsideReaderScreen() {
		val readerScreenText = readerScreenFile().readText()
		val openRequestText = readerCommonUiFile("ReaderOpenRequest.kt").readText()

		assertContains(readerScreenText, "reader.toReaderEngineOpenRequest(")
		assertFalse(
			readerScreenText.contains("internal fun Screen.Reader.toReaderEngineOpenRequest("),
			"ReaderScreen should call the engine open-request mapper, not own route/progress/start-locator construction."
		)
		assertContains(openRequestText, "internal fun Screen.Reader.toReaderEngineOpenRequest(")
		assertContains(openRequestText, "savedProgress: BinderyReadingProgress? = null")
		assertContains(openRequestText, "ReaderEngineOpenRequest(")
		assertContains(openRequestText, "ReaderPublicationIdentity(")
		assertContains(openRequestText, "savedProgress?.toReaderStartLocatorForReader(")
		assertContains(openRequestText, "bestReaderStartLocator(")
		assertContains(openRequestText, "nativeShellCoverUrl = shellCoverUrl")
		assertContains(openRequestText, "canReturnToShellCover = hasShellCover")
	}

	@Test
	fun commonReaderSettingsStoreRulesLiveOutsideReaderScreen() {
		val readerScreenText = readerScreenFile().readText()
		val settingsSessionText = readerCommonUiFile("ReaderSettingsSession.kt").readText()

		assertContains(readerScreenText, "readerHasBookSettings(preferenceManager, reader.bookId)")
		assertContains(readerScreenText, "readerInitialSettingsScope(hasReaderBookSettings)")
		assertContains(readerScreenText, "preferenceManager.readerSettingsForScope(")
		assertContains(readerScreenText, "preferenceManager.persistReaderSettingsForScope(")
		assertContains(readerScreenText, "preferenceManager.readerSettingsForSelectedScope(")
		assertContains(readerScreenText, "preferenceManager.resetReaderBookSettingsToGlobal(")
		assertFalse(
			readerScreenText.contains("preferenceManager.readerBookSettings(reader.bookId)") ||
				readerScreenText.contains("preferenceManager.readerSettingsForBook(reader.bookId)") ||
				readerScreenText.contains("preferenceManager.setReaderBookSettings(reader.bookId") ||
				readerScreenText.contains("preferenceManager.clearReaderBookSettings(reader.bookId)"),
			"ReaderScreen should call the reader settings session boundary, not own global/per-book persistence rules."
		)
		assertContains(settingsSessionText, "internal fun readerHasBookSettings(")
		assertContains(settingsSessionText, "internal fun readerInitialSettingsScope(")
		assertContains(settingsSessionText, "internal fun PreferenceManager.readerSettingsForScope(")
		assertContains(settingsSessionText, "internal fun PreferenceManager.persistReaderSettingsForScope(")
		assertContains(settingsSessionText, "internal fun PreferenceManager.readerSettingsForSelectedScope(")
		assertContains(settingsSessionText, "internal fun PreferenceManager.resetReaderBookSettingsToGlobal(")
		assertContains(settingsSessionText, "settings.normalizedReaderSettings()")
		assertContains(settingsSessionText, "setReaderBookSettings(bookId, normalized)")
		assertContains(settingsSessionText, "clearReaderBookSettings(bookId)")
	}

	@Test
	fun commonReaderContentsDialogUsesKomikkuLazyChapterListContract() {
		val contentsDialogText = readerCommonUiFile("ReaderContentsDialog.kt").readText()
		val contentsDialogBody = contentsDialogText.substringAfter("internal fun KomikkuReaderContentsDialog(")
		val komikkuChapterListText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/ChapterListDialog.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/ChapterListDialog.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku ChapterListDialog.kt reference")

		assertContains(komikkuChapterListText, "LazyColumn(")
		assertContains(komikkuChapterListText, "rememberLazyListState(")
		assertContains(komikkuChapterListText, "Modifier.heightIn(min = 200.dp, max = 500.dp)")
		assertContains(contentsDialogBody, "rememberLazyListState(")
		assertContains(contentsDialogBody, "LazyColumn(")
		assertContains(contentsDialogBody, "Modifier.heightIn(min = 200.dp, max = 500.dp)")
		assertContains(contentsDialogBody, "items(")
		assertContains(contentsDialogBody, "key = { item ->")
		assertFalse(
			contentsDialogBody.contains(".verticalScroll(rememberScrollState())"),
			"Komikku chapter/contents navigation must use a bounded lazy list instead of eagerly composing every entry in a scrolling Column."
		)
	}

	@Test
	fun commonReaderSettingsDialogUsesFullReferenceKomikkuTabs() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")
		val settingsDialogHeaderBody = settingsDialogBody.substringBefore("HorizontalPager(")
		val komikkuTabbedDialogText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku TabbedDialog.kt reference")

		assertContains(settingsDialogBody, "KomikkuSettingsTabRow(")
		assertFalse(
			settingsDialogHeaderBody.contains("tabs.forEachIndexed"),
			"The Komikku settings dialog must not hand-roll title-sized text tabs; use the reference tab row helper."
		)
		assertContains(settingsDialogText, "private fun KomikkuSettingsTabRow(")

		val tabRowBody = settingsDialogText.substringAfter("private fun KomikkuSettingsTabRow(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")

		assertContains(komikkuTabbedDialogText, "text = { TabText(text = tab) }")
		assertContains(tabRowBody, "PrimaryTabRow(")
		assertContains(tabRowBody, "Tab(")
		assertContains(tabRowBody, "text = if (useCompactLabels) tab.compactLabel else tab.label")
		assertContains(tabRowBody, "MaterialTheme.typography.labelMedium")
		assertContains(tabRowBody, "maxLines = 1")
		assertFalse(
			tabRowBody.contains("MaterialTheme.typography.titleMedium"),
			"Settings tabs must stay compact and single-line instead of inheriting panel title typography."
		)
	}

	@Test
	fun commonReaderSettingsDialogUsesDenseKomikkuDialogSpacingAndReferenceTabLabels() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val tabbedDialogBody = settingsDialogText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n@Composable\nprivate fun KomikkuSettingsTabRow(")
		val tabRowBody = settingsDialogText.substringAfter("private fun KomikkuSettingsTabRow(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")
		val dialogPageBody = settingsDialogText.substringAfter("private fun KomikkuSettingsDialogPage(")
			.substringBefore("\n}\n\n@Composable\ninternal fun KomikkuSettingsDialogLine(")
		val chipRowBody = settingsDialogText.substringAfter("private fun SettingsChipRow(")
			.substringBefore("\n}\n\n@Composable\nprivate fun CheckboxItem(")
		val checkboxItemBody = settingsDialogText.substringAfter("private fun CheckboxItem(")
			.substringBefore("\n}\n\n@Composable\nprivate fun SliderItem(")
		val sliderItemBody = settingsDialogText.substringAfter("private fun SliderItem(")
			.substringBefore("\n}\n\n@Composable\nprivate fun BaseSliderItem(")
		val baseSliderItemBody = settingsDialogText.substringAfter("private fun BaseSliderItem(")
			.substringBefore("\n}\n\n@Composable\nprivate fun Pill(")

		assertContains(settingsDialogText, "internal val TabbedDialogPaddingsVertical = 8.dp")
		assertContains(settingsDialogText, "private object SettingsItemsPaddings")
		assertContains(settingsDialogText, "private enum class KomikkuSettingsTab(val label: String, val compactLabel: String = label)")
		assertContains(settingsDialogText, "Reading(\"Reading mode\",")
		assertContains(settingsDialogText, "CustomFilter(\"Custom filter\",")
		assertContains(settingsDialogText, "KomikkuAdaptiveSheet(")
		assertFalse(
			settingsDialogText.contains("dialogWidthFraction"),
			"Komikku settings width is owned by AdaptiveSheet, not by Navic dialogWidthFraction plumbing."
		)
		assertFalse(
			settingsDialogText.contains(".padding(horizontal = 20.dp, vertical = 16.dp)"),
			"Komikku's TabbedDialog does not wrap the whole tab row and pager in Navic outer padding; settings items own their own padding."
		)
		assertContains(settingsDialogText, "text = if (useCompactLabels) tab.compactLabel else tab.label")
		assertContains(settingsDialogText, "MaterialTheme.typography.labelMedium")
		assertFalse(
			tabRowBody.contains("MaterialTheme.typography.labelLarge"),
			"Reader settings tabs should use denser Komikku-like tab text instead of larger labels that crowd the dialog."
		)
		assertContains(settingsDialogText, "HeadingItem(title)")
		assertContains(settingsDialogText, "HeadingItem(label)")
		assertContains(settingsDialogText, "horizontalArrangement = Arrangement.spacedBy(6.dp)")
		assertFalse(
			chipRowBody.contains("verticalArrangement = Arrangement.spacedBy"),
			"Komikku SettingsChipRow does not add an extra explicit vertical gap between wrapped chips; the added Navic gap makes dense rows taller and easier to clip."
		)
		assertContains(settingsDialogText, "bottom = SettingsItemsPaddings.Vertical")
		assertContains(settingsDialogText, "MaterialTheme.typography.bodyMedium")
		assertContains(settingsDialogText, "Pill(")
		assertContains(settingsDialogText, "KomikkuIntegerSlider(")
		assertFalse(
			settingsDialogText.contains("MaterialTheme.typography.bodyLarge"),
			"Settings rows should use compact body text, not bodyLarge."
		)
		assertFalse(
			settingsDialogText.contains("IconButton(") && settingsDialogText.contains("Pill("),
			"Komikku reader settings use slider rows with a value pill, not Navic plus/minus steppers."
		)
	}

	@Test
	fun commonReaderSettingsControlsUsePortedKomikkuSettingsItemsContract() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val komikkuSettingsItemsText = listOf(
			File("tmp/references/komikku/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt"),
			File("../tmp/references/komikku/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku SettingsItems.kt reference")
		val komikkuSliderText = listOf(
			File("tmp/references/komikku/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Slider.kt"),
			File("../tmp/references/komikku/presentation-core/src/main/java/tachiyomi/presentation/core/components/material/Slider.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku material Slider.kt reference")

		assertContains(komikkuSettingsItemsText, "object SettingsItemsPaddings")
		assertContains(komikkuSettingsItemsText, "fun HeadingItem(text: String)")
		assertContains(komikkuSettingsItemsText, "fun CheckboxItem(label: String, checked: Boolean, onClick: () -> Unit)")
		assertContains(komikkuSettingsItemsText, "fun SliderItem(")
		assertContains(komikkuSettingsItemsText, "fun BaseSliderItem(")
		assertContains(komikkuSettingsItemsText, "fun SettingsChipRow(")
		assertContains(komikkuSettingsItemsText, "private fun BaseSettingsItem(")
		assertContains(komikkuSliderText, "fun Slider(")
		assertContains(komikkuSliderText, "onValueChange = { onValueChange(it.roundToInt()) }")
		assertContains(settingsDialogText, "private object SettingsItemsPaddings")
		assertContains(settingsDialogText, "private fun HeadingItem(text: String)")
		assertContains(settingsDialogText, "private fun CheckboxItem(label: String, checked: Boolean, onClick: () -> Unit)")
		assertContains(settingsDialogText, "private fun SliderItem(")
		assertContains(settingsDialogText, "private fun BaseSliderItem(")
		assertContains(settingsDialogText, "private fun SettingsChipRow(")
		assertContains(settingsDialogText, "private fun BaseSettingsItem(")
		assertContains(settingsDialogText, "private fun Pill(")
		assertContains(settingsDialogText, "KomikkuIntegerSlider(")
		assertFalse(
			settingsDialogText.contains("private fun KomikkuSettingsChipRow(") ||
				settingsDialogText.contains("private fun KomikkuSettingsCheckboxItem(") ||
				settingsDialogText.contains("private fun KomikkuSettingsSliderItem(") ||
				settingsDialogText.contains("private fun KomikkuSettingsValuePill(") ||
				settingsDialogText.contains("private fun KomikkuIntegerSlider(") ||
				settingsDialogText.contains("SettingsItemsPaddingsHorizontal") ||
				settingsDialogText.contains("SettingsItemsPaddingsVertical"),
			"Working Navic-local KomikkuSettings* wrappers are still non-faithful; port the SettingsItems-shaped primitives from Komikku instead."
		)
	}

	@Test
	fun commonReaderSettingsDialogUsesKomikkuTabbedPagerContent() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")

		assertContains(settingsDialogBody, "rememberPagerState(")
		assertContains(settingsDialogBody, "HorizontalPager(")
		assertContains(settingsDialogBody, "pagerState.animateScrollToPage(index)")
		assertContains(settingsDialogBody, "selectedTab = pagerState.currentPage")
		assertContains(settingsDialogBody, "when (tabs[page])")
		assertFalse(
			settingsDialogBody.contains("var selectedTab by remember"),
			"Komikku settings tabs should be backed by pager state, not local selectedTab state."
		)
		assertFalse(
			settingsDialogBody.contains("when (tabs[selectedTab])"),
			"Komikku settings content should be paged horizontally instead of swapped by a raw when block."
		)
	}

	@Test
	fun commonReaderSettingsDialogUsesReusableKomikkuTabbedDialogPrimitive() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val tabbedDialogBody = settingsDialogText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n@Composable\nprivate fun KomikkuAdaptiveSheet(")

		assertContains(settingsDialogBody, "KomikkuTabbedDialog(")
		assertContains(settingsDialogBody, "modifier = Modifier.heightIn(max = maxHeight * 0.75f)")
		assertFalse(
			settingsDialogBody.contains("BasicAlertDialog(") ||
				settingsDialogBody.contains("Surface(") ||
				settingsDialogBody.contains("HorizontalPager(") ||
				settingsDialogBody.contains("KomikkuSettingsTabRow("),
			"ReaderSettingsDialog should mirror Komikku by delegating shell, tabs, and pager ownership to a reusable TabbedDialog primitive."
		)
		assertContains(tabbedDialogBody, "KomikkuAdaptiveSheet(")
		assertContains(tabbedDialogBody, "KomikkuSettingsTabRow(")
		assertContains(tabbedDialogBody, "HorizontalPager(")
		assertContains(tabbedDialogBody, "content(page)")
	}

	@Test
	fun commonReaderSettingsDialogPortsKomikkuAdaptiveSheetInsteadOfBasicAlertDialogShell() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val platformHostsText = readerCommonUiFile("ReaderPlatformHosts.kt").readText()
		val androidAdaptiveSheetText = listOf(
			File("src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuAdaptiveSheet.android.kt"),
			File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuAdaptiveSheet.android.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: ""
		val iosAdaptiveSheetText = listOf(
			File("src/iosMain/kotlin/paige/navic/ui/screens/reader/KomikkuAdaptiveSheet.ios.kt"),
			File("composeApp/src/iosMain/kotlin/paige/navic/ui/screens/reader/KomikkuAdaptiveSheet.ios.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: ""
		val komikkuAppAdaptiveSheetText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/components/AdaptiveSheet.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/components/AdaptiveSheet.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku app AdaptiveSheet.kt reference")
		val komikkuCoreAdaptiveSheetText = listOf(
			File("tmp/references/komikku/presentation-core/src/main/java/tachiyomi/presentation/core/components/AdaptiveSheet.kt"),
			File("../tmp/references/komikku/presentation-core/src/main/java/tachiyomi/presentation/core/components/AdaptiveSheet.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku core AdaptiveSheet.kt reference")
		val tabbedDialogBody = settingsDialogText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n@Composable\nprivate fun KomikkuSettingsTabRow(")

		assertContains(komikkuAppAdaptiveSheetText, "fun AdaptiveSheet(")
		assertContains(komikkuAppAdaptiveSheetText, "Dialog(")
		assertContains(komikkuAppAdaptiveSheetText, "DialogProperties(")
		assertContains(komikkuCoreAdaptiveSheetText, "if (isTabletUi)")
		assertContains(komikkuCoreAdaptiveSheetText, "contentAlignment = Alignment.Center")
		assertContains(komikkuCoreAdaptiveSheetText, "contentAlignment = Alignment.BottomCenter")
		assertContains(komikkuCoreAdaptiveSheetText, "surfaceContainerHigh")
		assertContains(komikkuCoreAdaptiveSheetText, "BackHandler(")
		assertContains(komikkuCoreAdaptiveSheetText, "AnchoredDraggableState(")
		assertContains(komikkuCoreAdaptiveSheetText, "DraggableAnchors")
		assertContains(komikkuCoreAdaptiveSheetText, ".anchoredDraggable(")
		assertContains(komikkuCoreAdaptiveSheetText, ".nestedScroll(")
		assertContains(komikkuCoreAdaptiveSheetText, "snapshotFlow { anchoredDraggableState.settledValue }")

		assertContains(tabbedDialogBody, "KomikkuAdaptiveSheet(")
		assertFalse(
			tabbedDialogBody.contains("BasicAlertDialog(") ||
				tabbedDialogBody.contains("DialogProperties(usePlatformDefaultWidth = false)") ||
				tabbedDialogBody.contains("Surface("),
			"Komikku TabbedDialog routes through AdaptiveSheet; keeping a direct BasicAlertDialog/Surface shell is a non-faithful settings UI fork."
		)
		assertFalse(
			settingsDialogText.contains("private fun KomikkuAdaptiveSheet(") ||
				settingsDialogText.contains("import androidx.compose.ui.window.Dialog") ||
				settingsDialogText.contains("import androidx.compose.ui.window.DialogProperties") ||
				settingsDialogText.contains("\n\t\tDialog(") ||
				settingsDialogText.contains("\n\tDialog("),
			"Komikku AdaptiveSheet uses Android-only BackHandler/anchoredDraggable behavior; Navic must not keep a static commonMain approximation."
		)
		assertContains(platformHostsText, "expect fun KomikkuAdaptiveSheet(")
		assertContains(androidAdaptiveSheetText, "actual fun KomikkuAdaptiveSheet(")
		assertContains(androidAdaptiveSheetText, "DialogProperties(")
		assertContains(androidAdaptiveSheetText, "usePlatformDefaultWidth = false")
		assertContains(androidAdaptiveSheetText, "decorFitsSystemWindows = true")
		assertContains(androidAdaptiveSheetText, "val isTabletUi = maxWidth >= 720.dp")
		assertContains(androidAdaptiveSheetText, "animateFloatAsState(")
		assertContains(androidAdaptiveSheetText, ".alpha(alpha)")
		assertContains(androidAdaptiveSheetText, "BackHandler(")
		assertContains(androidAdaptiveSheetText, "AnchoredDraggableState(")
		assertContains(androidAdaptiveSheetText, "DraggableAnchors")
		assertContains(androidAdaptiveSheetText, ".anchoredDraggable(")
		assertContains(androidAdaptiveSheetText, ".nestedScroll(")
		assertContains(androidAdaptiveSheetText, ".offset {")
		assertContains(androidAdaptiveSheetText, ".navigationBarsPadding()")
		assertContains(androidAdaptiveSheetText, ".statusBarsPadding()")
		assertContains(androidAdaptiveSheetText, "snapshotFlow { anchoredDraggableState.settledValue }")
		assertContains(androidAdaptiveSheetText, "collectLatest")
		assertContains(androidAdaptiveSheetText, "requiredWidthIn(max = 460.dp)")
		assertContains(androidAdaptiveSheetText, "widthIn(max = 460.dp)")
		assertContains(androidAdaptiveSheetText, "MaterialTheme.colorScheme.surfaceContainerHigh")
		assertContains(iosAdaptiveSheetText, "actual fun KomikkuAdaptiveSheet(")
	}

	@Test
	fun commonReaderSettingsDialogDismissRestoresChromeAndDoesNotAddNavicCloseFooter() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val komikkuSettingsDialogText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku ReaderSettingsDialog.kt reference")
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val tabbedDialogSignature = settingsDialogText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("content: @Composable (Int) -> Unit")
		val tabbedDialogBody = settingsDialogText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n@Composable\nprivate fun KomikkuSettingsTabRow(")

		assertContains(komikkuSettingsDialogText, "onDismissRequest = {")
		assertContains(komikkuSettingsDialogText, "onDismissRequest()")
		assertContains(komikkuSettingsDialogText, "onShowMenus()")
		assertContains(settingsDialogBody, "onDismissRequest = {")
		assertContains(settingsDialogBody, "onDismissRequest()")
		assertContains(settingsDialogBody, "onShowMenus()")
		assertFalse(
			settingsDialogBody.contains("Text(\"Close\")") ||
				settingsDialogText.contains("Text(\"Close\")"),
			"Komikku's reader settings dialog dismisses through the dialog shell; a Navic-only Close footer is a non-faithful UI fork."
		)
		assertFalse(
			tabbedDialogSignature.contains("footer:") ||
				tabbedDialogBody.contains("footer()"),
			"Komikku's TabbedDialog owns only shell, tabs, and paged content; it must not grow a Navic-only footer slot."
		)
	}

	@Test
	fun commonReaderSettingsDialogHidesChromeOnCustomFilterLikeKomikku() {
		val readerScreenText = readerScreenFile().readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")

		assertContains(settingsDialogBody, "onShowMenus: () -> Unit")
		assertContains(settingsDialogBody, "onHideMenus: () -> Unit")
		assertContains(settingsDialogBody, "LaunchedEffect(pagerState.currentPage)")
		assertContains(settingsDialogBody, "tabs[pagerState.currentPage] == KomikkuSettingsTab.CustomFilter")
		assertContains(settingsDialogBody, "onHideMenus()")
		assertContains(settingsDialogBody, "onShowMenus()")
		assertContains(readerScreenText, "onShowMenus = {")
		assertContains(readerScreenText, "coordinator.showMenus()")
		assertContains(readerScreenText, "onHideMenus = {")
		assertContains(readerScreenText, "coordinator.hideMenus()")
	}

	@Test
	fun commonReaderSettingsDialogMatchesKomikkuCustomFilterDimAmount() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val platformHostsText = readerCommonUiFile("ReaderPlatformHosts.kt").readText()
		val androidAdaptiveSheetText = readerAndroidFile("KomikkuAdaptiveSheet.android.kt").readText()
		val iosAdaptiveSheetText = listOf(
			File("src/iosMain/kotlin/paige/navic/ui/screens/reader/KomikkuAdaptiveSheet.ios.kt"),
			File("composeApp/src/iosMain/kotlin/paige/navic/ui/screens/reader/KomikkuAdaptiveSheet.ios.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: ""
		val komikkuSettingsDialogText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReaderSettingsDialog.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku ReaderSettingsDialog.kt reference")
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val tabbedDialogBody = settingsDialogText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n@Composable\nprivate fun KomikkuSettingsTabRow(")

		assertContains(komikkuSettingsDialogText, "window?.setDimAmount(0f)")
		assertContains(komikkuSettingsDialogText, "window?.setDimAmount(0.5f)")
		assertContains(settingsDialogBody, "val settingsDimAmount = if (tabs[pagerState.currentPage] == KomikkuSettingsTab.CustomFilter) 0f else 0.5f")
		assertContains(settingsDialogBody, "dimAmount = settingsDimAmount")
		assertContains(tabbedDialogBody, "dimAmount = dimAmount")
		assertContains(platformHostsText, "dimAmount: Float = 0.5f")
		assertContains(androidAdaptiveSheetText, "DialogWindowProvider")
		assertContains(androidAdaptiveSheetText, "window?.setDimAmount(dimAmount)")
		assertContains(iosAdaptiveSheetText, "dimAmount: Float")
	}

	@Test
	fun commonReaderPublisherStylesBelongsToGeneralInsteadOfCustomFilter() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val komikkuColorFilterText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ColorFilterPage.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ColorFilterPage.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku ColorFilterPage.kt reference")
		val generalPageBody = settingsDialogText.substringAfter("KomikkuSettingsTab.General -> KomikkuSettingsDialogPage(")
			.substringBefore("KomikkuSettingsTab.PdfImage -> KomikkuSettingsDialogPage(")
		val customFilterPageBody = settingsDialogText.substringAfter("KomikkuSettingsTab.CustomFilter -> KomikkuSettingsDialogPage(")
			.substringBefore("\n\t\t\t\t}\n\t\t\t}\n\t\t}")

		assertFalse(
			komikkuColorFilterText.contains("Publisher styles") ||
				komikkuColorFilterText.contains("publisherStyles"),
			"Komikku's ColorFilterPage is visual-filter-only; EPUB publisher style controls do not belong in Custom filter."
		)
		assertContains(generalPageBody, "label = \"Publisher styles\"")
		assertContains(generalPageBody, "publisherStyles = settings.publisherStyles != true")
		assertFalse(
			customFilterPageBody.contains("Publisher styles") ||
				customFilterPageBody.contains("publisherStyles"),
			"Custom filter must stay aligned with Komikku's visual filter page; publisher CSS controls belong in General with typography/theme settings."
		)
	}

	@Test
	fun commonReaderSettingsGeneralTabGroupsAnxControlsIntoKomikkuSections() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val komikkuSettingsItemsText = listOf(
			File("tmp/references/komikku/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt"),
			File("../tmp/references/komikku/presentation-core/src/main/java/tachiyomi/presentation/core/components/SettingsItems.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku SettingsItems.kt reference")
		val generalPageBody = settingsDialogText.substringAfter("KomikkuSettingsTab.General -> KomikkuSettingsDialogPage(")
			.substringBefore("KomikkuSettingsTab.PdfImage -> KomikkuSettingsDialogPage(")

		fun indexOfRequired(label: String): Int {
			val index = generalPageBody.indexOf(label)
			assertTrue(index >= 0, "General tab must contain $label")
			return index
		}

		assertContains(komikkuSettingsItemsText, "fun HeadingItem(text: String)")
		assertContains(settingsDialogText, "private fun SettingsSection(")
		val typographyIndex = indexOfRequired("title = \"Typography\"")
		val spacingIndex = indexOfRequired("title = \"Spacing\"")
		val pageLayoutIndex = indexOfRequired("title = \"Page layout\"")
		val themeDeviceIndex = indexOfRequired("title = \"Theme and device\"")

		assertTrue(
			typographyIndex < spacingIndex &&
				spacingIndex < pageLayoutIndex &&
				pageLayoutIndex < themeDeviceIndex,
			"General controls must be grouped into stable Komikku-style sections instead of one flat Anx control stream."
		)
		assertTrue(indexOfRequired("title = \"Font\"") > typographyIndex)
		assertTrue(indexOfRequired("title = \"Font source\"") > typographyIndex)
		assertTrue(indexOfRequired("label = \"Font size\"") > typographyIndex)
		assertTrue(indexOfRequired("label = \"Line height\"") > spacingIndex)
		assertTrue(indexOfRequired("label = \"Paragraph spacing\"") > spacingIndex)
		assertTrue(indexOfRequired("label = \"Side margin\"") > pageLayoutIndex)
		assertTrue(indexOfRequired("label = \"Column threshold\"") > pageLayoutIndex)
		assertTrue(indexOfRequired("title = \"Theme\"") > themeDeviceIndex)
		assertTrue(indexOfRequired("title = \"Rotation\"") > themeDeviceIndex)
	}

	@Test
	fun commonReaderSettingsExpandedAnxControlsUseSeparateSectionAndSliderDensity() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsSectionBody = settingsDialogText.substringAfter("private fun SettingsSection(")
			.substringBefore("\n}\n\n@Composable\ninternal fun KomikkuSettingsDialogLine(")
		val sectionHeadingBody = settingsDialogText.substringAfter("private fun SettingsSectionHeading(")
			.substringBefore("\n}\n\n@OptIn(ExperimentalLayoutApi::class)")
		val sliderItemBody = settingsDialogText.substringAfter("private fun SliderItem(")
			.substringBefore("\n}\n\n@Composable\nprivate fun BaseSliderItem(")
		val baseSliderItemBody = settingsDialogText.substringAfter("private fun BaseSliderItem(")
			.substringBefore("\n}\n\n@Composable\nprivate fun Pill(")

		assertContains(settingsDialogText, "val SectionVertical = 6.dp")
		assertContains(settingsDialogText, "val SliderVertical = 6.dp")
		assertContains(settingsDialogText, "private fun SettingsSectionHeading(text: String)")
		assertContains(settingsSectionBody, "SettingsSectionHeading(title)")
		assertFalse(
			settingsSectionBody.contains("HeadingItem(title)"),
			"Expanded Anx groups need a section heading density distinct from the Komikku page heading; reusing HeadingItem makes the modal read like a long settings page."
		)
		assertContains(sectionHeadingBody, "MaterialTheme.typography.labelLarge")
		assertContains(sectionHeadingBody, "MaterialTheme.colorScheme.onSurfaceVariant")
		assertContains(sliderItemBody, "vertical = SettingsItemsPaddings.SliderVertical")
		assertContains(baseSliderItemBody, "verticalArrangement = Arrangement.spacedBy(0.dp)")
	}

	@Test
	fun commonReaderSettingsDialogUsesKomikkuBoundedScrollableDialogContract() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val tabbedDialogBody = settingsDialogText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n@Composable\nprivate fun KomikkuSettingsTabRow(")
		val komikkuTabbedDialogText = listOf(
			File("tmp/references/komikku/app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt"),
			File("../tmp/references/komikku/app/src/main/java/eu/kanade/presentation/components/TabbedDialog.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate Komikku TabbedDialog.kt reference")

		assertContains(settingsDialogBody, "BoxWithConstraints")
		assertContains(settingsDialogBody, ".heightIn(max = maxHeight * 0.75f)")
		assertContains(settingsDialogBody, "TabbedDialogPaddingsVertical")
		assertContains(settingsDialogBody, "val settingsScrollState = rememberScrollState()")
		assertContains(settingsDialogBody, ".verticalScroll(settingsScrollState)")
		assertContains(settingsDialogBody, ".padding(vertical = TabbedDialogPaddingsVertical)")
		assertFalse(
			settingsDialogBody.contains("modifier = Modifier.fillMaxWidth(0.78f)"),
			"Komikku settings should use the shared bounded dialog modifier instead of only fixed-width surface sizing."
		)
		assertContains(tabbedDialogBody, "HorizontalPager(")
		assertContains(komikkuTabbedDialogText, "modifier = Modifier.animateContentSize()")
		assertContains(tabbedDialogBody, "modifier = Modifier.animateContentSize()")
	}

	@Test
	fun commonReaderSettingsDialogUsesResponsiveWidthInsteadOfPlatformDialogCap() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val tabbedDialogBody = settingsDialogText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n@Composable\nprivate fun KomikkuSettingsTabRow(")
		val androidAdaptiveSheetText = listOf(
			File("src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuAdaptiveSheet.android.kt"),
			File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuAdaptiveSheet.android.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: ""

		assertContains(tabbedDialogBody, "KomikkuAdaptiveSheet(")
		assertContains(androidAdaptiveSheetText, "DialogProperties(")
		assertContains(androidAdaptiveSheetText, "usePlatformDefaultWidth = false")
		assertContains(androidAdaptiveSheetText, "decorFitsSystemWindows = true")
		assertContains(androidAdaptiveSheetText, "val isTabletUi = maxWidth >= 720.dp")
		assertContains(androidAdaptiveSheetText, "widthIn(max = 460.dp)")
		assertContains(androidAdaptiveSheetText, "requiredWidthIn(max = 460.dp)")
		assertFalse(
			settingsDialogBody.contains("dialogWidthFraction") ||
				settingsDialogBody.contains("widthFraction =") ||
				tabbedDialogBody.contains("widthFraction: Float") ||
				tabbedDialogBody.contains("fillMaxWidth(widthFraction)") ||
				tabbedDialogBody.contains("modifier.fillMaxWidth(0.78f)"),
			"Settings dialog width must be owned by Komikku AdaptiveSheet, not by Navic's old fixed/platform-capped width plumbing."
		)
	}

	@Test
	fun commonReaderSettingsDialogUsesScrollEdgeFadeInsteadOfAbruptCutoff() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val fadeBody = settingsDialogText.substringAfter("private fun Modifier.komikkuVerticalScrollEdgeFade(")
			.substringBefore("\n\n@Composable\nprivate fun KomikkuSettingsTabRow(")

		assertContains(settingsDialogBody, "val settingsScrollState = rememberScrollState()")
		assertContains(settingsDialogBody, ".verticalScroll(settingsScrollState)")
		assertContains(settingsDialogBody, ".komikkuVerticalScrollEdgeFade(settingsScrollState)")
		assertContains(settingsDialogText, "private fun Modifier.komikkuVerticalScrollEdgeFade(")
		assertContains(fadeBody, "drawWithContent")
		assertContains(fadeBody, "settingsScrollState.maxValue")
		assertContains(fadeBody, "Brush.verticalGradient")
		assertContains(fadeBody, "BlendMode.DstIn")
		assertFalse(
			settingsDialogBody.contains(".verticalScroll(rememberScrollState())"),
			"Settings pages need a shared scroll state so the dialog can render a top/bottom fade instead of hard-cutting rows."
		)
	}

	@Test
	fun commonReaderOptionsUseKomikkuStyleChipGroups() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")

		assertContains(settingsDialogText, "SettingsChipRow")
		assertContains(settingsDialogText, "CheckboxItem")
		assertContains(settingsDialogText, "SliderItem")
		assertContains(settingsDialogText, "FilterChip(")
		assertContains(settingsDialogText, "FlowRow(")
		assertContains(settingsDialogText, "Checkbox(")
		assertContains(settingsDialogText, "Slider(")
		assertContains(settingsDialogText, "KomikkuReadingModeOptions")
		assertContains(settingsDialogBody, "ReaderSupportedDirections")
		assertContains(settingsDialogBody, "ReaderSupportedFontFamilies")
		assertContains(settingsDialogBody, "ReaderSupportedFontSources")
		assertContains(settingsDialogBody, "ReaderSupportedThemes")
		assertContains(settingsDialogBody, "ReaderSupportedOrientations")
		assertContains(settingsDialogBody, "KomikkuTapZoneOptions")
		assertFalse(
			settingsDialogBody.contains("ReaderCycleRow("),
			"Reader settings should use Komikku-style selectable chip groups instead of cyclic value rows."
		)
		assertFalse(
			settingsDialogText.contains("private fun KomikkuSettingsSwitchRow(") ||
				settingsDialogText.contains("private fun KomikkuSettingsStepperRow(") ||
				settingsDialogText.contains("private fun KomikkuSettingsCheckboxItem(") ||
				settingsDialogText.contains("private fun KomikkuSettingsSliderItem("),
			"Working Navic-only switch/stepper rows are still non-faithful; settings controls must be rebuilt around Komikku CheckboxItem and SliderItem primitives."
		)
	}

	@Test
	fun commonReaderOptionsSeparatePdfImageSettingsByPublicationFormat() {
		val readerScreenText = readerScreenFile().readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val readerChromeStateText = readerCommonFile("ReaderChromeState.kt").readText()

		assertContains(readerChromeStateText, "ReaderOptionsTab.PdfImage")
		assertContains(readerChromeStateText, "publicationFormat: ReaderPublicationFormat")
		assertContains(readerScreenText, "publicationFormat = reader.publicationFormat")
		assertContains(settingsDialogText, "publicationFormat: ReaderPublicationFormat")
		assertContains(settingsDialogText, "PDF/Image")
		assertContains(settingsDialogText, "Page fit")
		assertContains(settingsDialogText, "ReaderSupportedPdfFitModes")
		assertContains(settingsDialogText, "Crop borders")
		assertContains(settingsDialogText, "Page gap")
		assertContains(settingsDialogText, "pdfFitMode = fitMode")
		assertContains(settingsDialogText, "pdfCropBorders = settings.pdfCropBorders != true")
		assertContains(settingsDialogText, "pdfPageGapPercent")
	}

	@Test
	fun commonReaderOptionsSupportKomikkuStylePerBookSettingsScope() {
		val readerScreenText = readerScreenFile().readText()
		val settingsSessionText = readerCommonUiFile("ReaderSettingsSession.kt").readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val preferenceText = readerCommonFile("ReaderPreferenceSettings.kt").readText()
		val preferenceManagerText = listOf(
			File("src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt"),
			File("composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt")
		).firstOrNull { it.isFile }
			?.readText()
			?: error("Could not locate PreferenceManager.kt")

		assertContains(preferenceManagerText, "readerBookSettingsJson")
		assertContains(preferenceText, "readerSettingsForBook")
		assertContains(preferenceText, "setReaderBookSettings")
		assertContains(preferenceText, "clearReaderBookSettings")
		assertContains(readerScreenText, "readerBookSettingsJson")
		assertContains(settingsSessionText, "readerSettingsForBook(bookId)")
		assertContains(settingsSessionText, "setReaderBookSettings(bookId")
		assertContains(settingsSessionText, "clearReaderBookSettings(bookId)")
		assertContains(settingsDialogText, "For this book")
		assertContains(settingsDialogText, "ReaderSupportedSettingsScopes")
		assertContains(settingsDialogText, "readerSettingsScopeLabel(scope)")
		assertContains(settingsDialogText, "Reset book")
	}

	@Test
	fun commonReadaloudRuntimeCapabilitiesRemainAvailableBehindControllerBoundary() {
		val readerScreenText = readerScreenFile().readText()
		val chromeStateText = readerCommonFile("ReaderChromeState.kt").readText()
		val runtimeHostText = readerAndroidFile("ReaderReadaloudRuntimeHost.android.kt").readText()

		assertContains(chromeStateText, "activeAudioLabel")
		assertContains(chromeStateText, "activeAudioMetadata")
		assertContains(chromeStateText, "adjustSpeedCommand")
		assertContains(chromeStateText, "toggleSyncCommand")
		assertContains(chromeStateText, "ReaderReadaloudPlaybackCommand.SetSpeed")
		assertContains(chromeStateText, "ReaderReadaloudPlaybackCommand.SetSyncEnabled")
		assertContains(runtimeHostText, "activeAudioLabel =")
		assertContains(runtimeHostText, "activeLabelForPlaybackPosition")
		assertContains(runtimeHostText, "activeAudioMetadata =")
		assertContains(runtimeHostText, "metadataLabelsForPlaybackPosition")
		assertContains(runtimeHostText, "controller.setPlaybackSpeed")
		assertContains(runtimeHostText, "setSyncEnabled")
		assertContains(readerScreenText, "ReaderReadaloudRuntimeHost(")
		assertContains(readerScreenText, "coordinator.onReadaloudEngineCommand(command)")
		assertContains(readerScreenText, "coordinator.onReadaloudPlaybackState(playbackState)")
		assertFalse(
			runtimeHostText.contains("toLegacyReaderBridgeCommand"),
			"The active Komikku reader must not reattach readaloud by converting sync commands back to legacy bridge ownership."
		)
	}

	@Test
	fun commonWhispersyncPlaybackControlBlendsIntoPageInsteadOfRenderingChromePill() {
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val whispersyncControlText = readerCommonUiFile("ReaderWhispersyncStatusBadge.kt").readText()
		val playbackControlBody = whispersyncControlText
			.substringAfter("internal fun KomikkuWhispersyncPlaybackControl(")
			.substringBefore("\n}\n\n@Composable\ninternal fun KomikkuWhispersyncStatusBadge(")

		assertContains(readerRootText, "KomikkuWhispersyncPlaybackControl(")
		assertContains(readerRootText, ".align(Alignment.TopStart)")
		assertContains(playbackControlBody, "MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)")
		assertContains(playbackControlBody, "Icons.Outlined.Headset")
		assertContains(playbackControlBody, "drawLine(")
		assertFalse(
			playbackControlBody.contains("Surface(") ||
				playbackControlBody.contains("RoundedCornerShape(") ||
				playbackControlBody.contains("MaterialTheme.colorScheme.surface.copy") ||
				playbackControlBody.contains("CircularProgressIndicator(") ||
				playbackControlBody.contains("IconButton("),
			"The page-scoped Whispersync control should be a faint headset glyph on the paper surface, not a persistent chrome pill/circle."
		)
	}

	@Test
	fun androidReadaloudMediaItemsPreserveSourceReleaseMetadataInMedia3Extras() {
		val mediaItemsText = readerAndroidPackageFile("ReadaloudMediaItems.android.kt").readText()

		assertContains(mediaItemsText, "descriptor.toReadaloudMediaExtras()")
		assertContains(mediaItemsText, "putString(\"chapterLabel\", extras.chapterLabel)")
		assertContains(mediaItemsText, "putString(\"sectionLabel\", extras.sectionLabel)")
		assertContains(mediaItemsText, "putString(\"sourceProvider\", extras.sourceProvider)")
		assertContains(mediaItemsText, "putString(\"sourceRelease\", extras.sourceRelease)")
		assertContains(mediaItemsText, "putString(\"sourceUrl\", extras.sourceUrl)")
	}
}
