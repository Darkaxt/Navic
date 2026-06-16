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
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
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
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(readerRootText, "KomikkuReaderContentOverlay")
		assertContains(readerRootText, "Modifier.matchParentSize()")
		assertContains(contentOverlayText, "drawRect(Color.Black.copy")
		assertContains(settingsDialogText, "Dim overlay")
		assertContains(settingsDialogText, "adjustDimOverlay")
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
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
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
		assertContains(settingsDialogText, "updateReaderColorFilterChannel")
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
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
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
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

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
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

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
		assertContains(settingsDialogText, "BasicAlertDialog(")
		assertContains(readerRootText, "Modifier.matchParentSize()")
		assertContains(appBarsText, "Box(modifier = modifier.fillMaxSize())")
		assertContains(appBarsText, ".align(Alignment.TopCenter)")
		assertContains(appBarsText, ".align(Alignment.CenterEnd)")
		assertContains(appBarsText, ".align(Alignment.CenterStart)")
		assertContains(appBarsText, ".align(Alignment.BottomCenter)")
		assertContains(appBarsText, "Ported from Komikku ReaderAppBars")
		assertContains(settingsDialogText, "Ported from Komikku ReaderSettingsDialog")
		assertFalse(
			appBarsBody.contains("Column(modifier = modifier.fillMaxHeight())") || appBarsBody.contains(".weight(1f)"),
			"Komikku reader app bars must be independent full-window overlays, not a weighted vertical layout that can center the progress rail."
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
				readerScreenText.contains("private fun KomikkuVerticalChapterProgressRail(") ||
				readerScreenText.contains("private fun KomikkuChapterProgressSlider("),
			"Komikku chapter/progress navigation must live in its own reader component file, not inside ReaderScreen."
		)
		assertContains(navigatorText, "internal const val KomikkuReaderVerticalRailHeightFraction = 0.82f")
		assertContains(navigatorText, "internal fun KomikkuChapterNavigator(")
		assertContains(navigatorText, "private fun KomikkuChapterNavigatorVertical(")
		assertContains(navigatorText, "private fun KomikkuVerticalChapterProgressRail(")
		assertContains(navigatorText, "private fun KomikkuChapterProgressSlider(")
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
				readerScreenText.contains("private fun KomikkuSettingsChipRow(") ||
				readerScreenText.contains("private fun KomikkuSettingsSwitchRow(") ||
				readerScreenText.contains("private fun KomikkuSettingsStepperRow("),
			"Komikku reader settings must live in its own reader component file, not inside ReaderScreen."
		)
		assertContains(settingsDialogText, "internal val TabbedDialogPaddingsVertical = 8.dp")
		assertContains(settingsDialogText, "private enum class KomikkuSettingsTab(val label: String, val compactLabel: String = label)")
		assertContains(settingsDialogText, "internal fun KomikkuReaderSettingsDialog(")
		assertContains(settingsDialogText, "private fun KomikkuTabbedDialog(")
		assertContains(settingsDialogText, "private fun KomikkuSettingsTabRow(")
		assertContains(settingsDialogText, "private fun KomikkuSettingsChipRow(")
		assertContains(settingsDialogText, "private fun KomikkuSettingsSwitchRow(")
		assertContains(settingsDialogText, "private fun KomikkuSettingsStepperRow(")
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
		val appBarsBody = appBarsText.substringAfter("internal fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")
		val sideRailBody = navigatorText.substringAfter("private fun KomikkuChapterNavigatorVertical(")
			.substringBefore("\n}\n")
		val bottomChromeBody = appBarsText.substringAfter("private fun KomikkuReaderBottomBar(")

		assertContains(navigatorText, "KomikkuChapterNavigatorVertical(")
		assertContains(appBarsBody, "ReaderNavBarTypeVerticalRight")
		assertContains(appBarsBody, "Box(modifier = modifier.fillMaxSize())")
		assertContains(appBarsBody, ".align(Alignment.CenterEnd)")
		assertContains(appBarsBody, ".align(Alignment.CenterStart)")
		assertContains(appBarsBody, "KomikkuReaderVerticalRailHeightFraction")
		assertFalse(
			appBarsBody.contains("Column(modifier = modifier.fillMaxHeight())") ||
				appBarsBody.contains(".weight(1f)"),
			"Komikku app bars must mount top chrome, side rail, and bottom chrome as independent overlays; stacking them in a weighted Column can center the rail over the page."
		)
		assertContains(
			navigatorText,
			"internal const val KomikkuReaderVerticalRailHeightFraction = 0.82f",
			message = "The vertical rail should be long enough to feel useful while still avoiding top/bottom chrome collisions."
		)
		assertContains(navigatorText, "private fun KomikkuChapterProgressSlider(")
		assertContains(navigatorText, "MutableInteractionSource")
		assertContains(navigatorText, "collectIsDraggedAsState")
		assertContains(navigatorText, "HapticFeedbackType.TextHandleMove")
		assertContains(navigatorText, "roundToInt()")
		assertContains(appBarsBody, "Modifier.fillMaxHeight(KomikkuReaderVerticalRailHeightFraction)")
		assertContains(sideRailBody, "KomikkuVerticalChapterProgressRail(")
		assertContains(sideRailBody, "valueRange = 1..totalPages")
		assertContains(sideRailBody, "onPageIndexChange(page - 1)")
		assertContains(sideRailBody, "Text(text = currentPageText)")
		assertContains(sideRailBody, "Text(text = totalPages.toString())")
		assertContains(sideRailBody, "Icons.Filled.SkipPrevious")
		assertContains(sideRailBody, "Icons.Filled.SkipNext")
		assertFalse(
			sideRailBody.contains("ReaderProgressSeekControl("),
			"The Komikku side rail must not reuse the old bottom progress control."
		)
		assertFalse(
			bottomChromeBody.contains("ReaderProgressSeekControl(") ||
				bottomChromeBody.contains("LinearProgressIndicator("),
			"Komikku-equivalent progress belongs in the side rail overlay, not inside the bottom chrome surface."
		)
	}

	@Test
	fun commonReaderVerticalProgressRailUsesKomikkuDottedRailVisuals() {
		val navigatorText = readerCommonUiFile("ReaderChapterNavigator.kt").readText()
		val sideRailBody = navigatorText.substringAfter("private fun KomikkuChapterNavigatorVertical(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuVerticalChapterProgressRail(")
		val verticalRailBody = navigatorText.substringAfter("private fun KomikkuVerticalChapterProgressRail(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuChapterProgressSlider(")

		assertContains(sideRailBody, "KomikkuVerticalChapterProgressRail(")
		assertFalse(
			sideRailBody.contains("KomikkuChapterProgressSlider("),
			"The vertical Komikku rail should not expose the rotated Material slider as its visible design."
		)
		assertContains(verticalRailBody, "Canvas(")
		assertContains(verticalRailBody, "drawRoundRect(")
		assertContains(verticalRailBody, "drawCircle(")
		assertContains(verticalRailBody, "alpha = 0.01f")
		assertContains(sideRailBody, "valueRange = 1..totalPages")
	}

	@Test
	fun commonReaderChapterNavigatorHonorsRtlDirectionLikeKomikku() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val navigatorText = readerCommonUiFile("ReaderChapterNavigator.kt").readText()
		val appBarsBody = appBarsText.substringAfter("internal fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")
		val navigatorBody = navigatorText.substringAfter("internal fun KomikkuChapterNavigator(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuVerticalChapterProgressRail(")
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
	}

	@Test
	fun commonReaderProgressRailPlacementIsControllerSettingNotHardcoded() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
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
		assertContains(appBarsText, "normalizedReaderNavBarType")
		assertContains(appBarsBody, "val navBarType = normalizedReaderNavBarType(controllerState.chrome.settings.navBarType)")
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
	fun commonReaderBottomActionsAreCenteredAndDoNotDuplicateBookmarkAction() {
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val bottomChromeBody = appBarsText.substringAfter("private fun KomikkuReaderBottomBar(")
		val bottomActionRow = bottomChromeBody
			.substringAfter("Row(")
			.substringBefore("}")

		assertContains(bottomActionRow, ".fillMaxWidth()")
		assertContains(bottomActionRow, "horizontalArrangement = Arrangement.SpaceEvenly")
		assertContains(bottomActionRow, "verticalAlignment = Alignment.CenterVertically")
		assertFalse(
			bottomActionRow.contains("horizontalScroll("),
			"Komikku's bottom actions are centered/distributed, not a left-aligned horizontally scrolling toolbar."
		)
		assertFalse(
			bottomActionRow.contains("Icons.Filled.Star") || bottomActionRow.contains("Icons.Outlined.Star"),
			"The bookmark/star affordance belongs in the top chrome; duplicating it in the bottom action row makes the menu feel inconsistent."
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
	fun commonReaderSettingsDialogUsesCompactNonWrappingKomikkuTabs() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")
		val settingsDialogHeaderBody = settingsDialogBody.substringBefore("HorizontalPager(")

		assertContains(settingsDialogBody, "KomikkuSettingsTabRow(")
		assertFalse(
			settingsDialogHeaderBody.contains("tabs.forEachIndexed"),
			"The Komikku settings dialog must not hand-roll title-sized text tabs; use the compact tab row helper."
		)
		assertContains(settingsDialogText, "private fun KomikkuSettingsTabRow(")

		val tabRowBody = settingsDialogText.substringAfter("private fun KomikkuSettingsTabRow(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")

		assertContains(tabRowBody, "TabRow(")
		assertContains(tabRowBody, "Tab(")
		assertContains(tabRowBody, "text = tab.compactLabel")
		assertContains(tabRowBody, "MaterialTheme.typography.labelMedium")
		assertContains(tabRowBody, "maxLines = 1")
		assertContains(tabRowBody, "overflow = TextOverflow.Ellipsis")
		assertFalse(
			tabRowBody.contains("MaterialTheme.typography.titleMedium"),
			"Settings tabs must stay compact and single-line instead of inheriting panel title typography."
		)
	}

	@Test
	fun commonReaderSettingsDialogUsesDenseKomikkuDialogSpacingAndCompactTabLabels() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val tabbedDialogBody = settingsDialogText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsTabRow(")
		val tabRowBody = settingsDialogText.substringAfter("private fun KomikkuSettingsTabRow(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsDialogPage(")
		val dialogPageBody = settingsDialogText.substringAfter("private fun KomikkuSettingsDialogPage(")
			.substringBefore("\n}\n\n@Composable\ninternal fun KomikkuSettingsDialogLine(")
		val chipRowBody = settingsDialogText.substringAfter("private fun KomikkuSettingsChipRow(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsSwitchRow(")
		val switchRowBody = settingsDialogText.substringAfter("private fun KomikkuSettingsSwitchRow(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsStepperRow(")
		val stepperRowBody = settingsDialogText.substringAfter("private fun KomikkuSettingsStepperRow(")

		assertContains(settingsDialogText, "internal val TabbedDialogPaddingsVertical = 8.dp")
		assertContains(settingsDialogText, "private enum class KomikkuSettingsTab(val label: String, val compactLabel: String = label)")
		assertContains(settingsDialogText, "Reading(\"Reading mode\", \"Reading\")")
		assertContains(settingsDialogText, "CustomFilter(\"Custom filter\", \"Filter\")")
		assertContains(settingsDialogBody, "val dialogWidthFraction = if (maxWidth < 720.dp) 0.96f else 0.72f")
		assertContains(tabbedDialogBody, ".padding(horizontal = 20.dp, vertical = 16.dp)")
		assertContains(tabRowBody, "text = tab.compactLabel")
		assertContains(tabRowBody, "MaterialTheme.typography.labelMedium")
		assertFalse(
			tabRowBody.contains("text = tab.label"),
			"Reader settings tabs should use compact labels in the visible tab strip so Reading/Custom labels do not truncate."
		)
		assertFalse(
			tabRowBody.contains("MaterialTheme.typography.labelLarge"),
			"Reader settings tabs should use denser Komikku-like tab text instead of larger labels that crowd the dialog."
		)
		assertContains(dialogPageBody, "MaterialTheme.typography.titleSmall")
		assertContains(chipRowBody, "MaterialTheme.typography.labelLarge")
		assertContains(chipRowBody, "MaterialTheme.typography.labelMedium")
		assertContains(switchRowBody, "MaterialTheme.typography.bodyMedium")
		assertContains(stepperRowBody, "MaterialTheme.typography.bodyMedium")
		assertContains(stepperRowBody, "MaterialTheme.typography.labelMedium")
		assertFalse(
			chipRowBody.contains("MaterialTheme.typography.bodyLarge"),
			"Settings chip groups should use compact labels instead of bodyLarge headings in the constrained overlay."
		)
		assertFalse(
			switchRowBody.contains("MaterialTheme.typography.bodyLarge") ||
				stepperRowBody.contains("MaterialTheme.typography.bodyLarge"),
			"Switch and stepper rows should use compact row text so the settings sheet does not feel like a docked settings page."
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
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsTabRow(")

		assertContains(settingsDialogBody, "KomikkuTabbedDialog(")
		assertContains(settingsDialogBody, "modifier = Modifier.heightIn(max = maxHeight * 0.75f)")
		assertFalse(
			settingsDialogBody.contains("BasicAlertDialog(") ||
				settingsDialogBody.contains("Surface(") ||
				settingsDialogBody.contains("HorizontalPager(") ||
				settingsDialogBody.contains("KomikkuSettingsTabRow("),
			"ReaderSettingsDialog should mirror Komikku by delegating shell, tabs, and pager ownership to a reusable TabbedDialog primitive."
		)
		assertContains(tabbedDialogBody, "BasicAlertDialog(")
		assertContains(tabbedDialogBody, "Surface(")
		assertContains(tabbedDialogBody, "KomikkuSettingsTabRow(")
		assertContains(tabbedDialogBody, "HorizontalPager(")
		assertContains(tabbedDialogBody, "content(page)")
		assertContains(tabbedDialogBody, "footer()")
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
	fun commonReaderSettingsDialogUsesKomikkuBoundedScrollableDialogContract() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val tabbedDialogBody = settingsDialogText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsTabRow(")

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
		assertFalse(
			tabbedDialogBody.contains("modifier = Modifier.animateContentSize()"),
			"Pager content should be bounded and scrollable like Komikku's TabbedDialog body, not unbounded animated content."
		)
	}

	@Test
	fun commonReaderSettingsDialogUsesResponsiveWidthInsteadOfPlatformDialogCap() {
		val settingsDialogText = readerCommonUiFile("ReaderSettingsDialog.kt").readText()
		val settingsDialogBody = settingsDialogText.substringAfter("internal fun KomikkuReaderSettingsDialog(")
			.substringBefore("\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun KomikkuTabbedDialog(")
		val tabbedDialogBody = settingsDialogText.substringAfter("private fun KomikkuTabbedDialog(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuSettingsTabRow(")

		assertContains(settingsDialogBody, "val dialogWidthFraction = if (maxWidth < 720.dp) 0.96f else 0.72f")
		assertContains(settingsDialogBody, "widthFraction = dialogWidthFraction")
		assertContains(tabbedDialogBody, "DialogProperties(usePlatformDefaultWidth = false)")
		assertContains(tabbedDialogBody, "widthFraction: Float")
		assertContains(tabbedDialogBody, "modifier.fillMaxWidth(widthFraction)")
		assertFalse(
			tabbedDialogBody.contains("modifier.fillMaxWidth(0.78f)"),
			"Settings dialog width must not stay trapped in the old fixed/platform-capped width that truncates Komikku tab labels."
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

		assertContains(settingsDialogText, "KomikkuSettingsChipRow")
		assertContains(settingsDialogText, "KomikkuSettingsSwitchRow")
		assertContains(settingsDialogText, "KomikkuSettingsStepperRow")
		assertContains(settingsDialogText, "FilterChip(")
		assertContains(settingsDialogText, "FlowRow(")
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
		assertContains(settingsDialogText, "pdfCropBorders = cropBorders")
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
