package paige.navic.reader

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimeCommonChromeTest {
	@Test
	fun androidReaderPackagesBundledFontSourcesForWebViewRendering() {
		val root = readerAssetRoot()
		val bridgeText = readerBridgeText(root)
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
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
		assertContains(readerOptionsPanelText, "Font source")
		assertContains(readerOptionsPanelText, "ReaderSupportedFontSources")
		assertContains(ebooksSettingsText, "readerFontSource")
		assertContains(ebooksSettingsText, "ReaderFontSourceOption")
		assertContains(searchSettingsText, "ebooks.font-source")
		assertContains(searchSettingsText, "ReaderSupportedFontSources")
	}

	@Test
	fun commonReaderChromeExposesDimOverlayControl() {
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(readerScreenText, "ReaderDimOverlay")
		assertContains(readerScreenText, "matchParentSize()")
		assertContains(readerScreenText, "Color.Black.copy")
		assertContains(ebooksSettingsText, "readerDimOverlayPercent")
		assertContains(ebooksSettingsText, "option_ebook_reader_dim_overlay")
		assertContains(searchSettingsText, "ebooks.dim-overlay")
		assertContains(searchSettingsText, "readerDimOverlayPercent")
	}

	@Test
	fun androidReaderExposesKomikkuStyleOrientationControl() {
		val orientationEffectText = readerAndroidFile("ReaderOrientationEffect.android.kt").readText()
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(orientationEffectText, "SCREEN_ORIENTATION_FULL_SENSOR")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_SENSOR_PORTRAIT")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_SENSOR_LANDSCAPE")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_PORTRAIT")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_LANDSCAPE")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_REVERSE_PORTRAIT")
		assertContains(orientationEffectText, "activity.requestedOrientation = previousOrientation")
		assertContains(readerScreenText, "ReaderOrientationEffect(chromeState.settings.orientation)")
		assertContains(ebooksSettingsText, "readerOrientation")
		assertContains(ebooksSettingsText, "option_ebook_reader_orientation")
		assertContains(searchSettingsText, "ebooks.orientation")
		assertContains(searchSettingsText, "ReaderSupportedOrientations")
	}

	@Test
	fun commonReaderChromeExposesVolumeKeyPageTurnControl() {
		val readerScreenText = readerScreenFile().readText()
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(readerScreenText, "onPreviewKeyEvent")
		assertContains(readerScreenText, "Key.VolumeUp")
		assertContains(readerScreenText, "Key.VolumeDown")
		assertContains(readerScreenText, "volumeKeyPageTurns")
		assertContains(readerOptionsPanelText, "Volume keys")
		assertContains(ebooksSettingsText, "readerVolumeKeyPageTurns")
		assertContains(ebooksSettingsText, "option_ebook_reader_volume_keys")
		assertContains(searchSettingsText, "ebooks.volume-keys")
		assertContains(searchSettingsText, "readerVolumeKeyPageTurns")
	}

	@Test
	fun commonReaderDefaultSettingsRememberKeyTracksReaderPreferenceInputs() {
		val readerScreenText = readerScreenFile().readText()
		val defaultSettingsRemember = readerScreenText
			.substringAfter("val defaultReaderSettings = remember(")
			.substringBefore("\n\t) {\n\t\tpreferenceManager.readerDefaultSettings()")
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
			"readerOrientation",
			"readerTheme",
			"readerDirection",
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
			"readerWebContentsDebuggingEnabled"
		)

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
	fun commonReaderChromeUsesKomikkuStyleOptionsSheetInsteadOfDockedSettingsList() {
		val readerScreenText = readerScreenFile().readText()
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val bottomChromeBody = readerScreenText.substringAfter("private fun ReaderBottomChrome(")
			.substringBefore("private fun ReaderKomikkuOptionsSheet(")

		assertContains(readerScreenText, "optionsVisible")
		assertContains(readerScreenText, "onToggleOptions: () -> Unit")
		assertContains(readerScreenText, "Icons.Filled.Settings")
		assertContains(readerScreenText, "ReaderKomikkuOptionsSheet(")
		assertContains(readerScreenText, "skipPartiallyExpanded = false")
		assertContains(readerScreenText, "BoxWithConstraints")
		assertContains(readerScreenText, "heightIn(max = maxHeight * 0.75f)")
		assertContains(readerOptionsPanelText, "ReaderOptionsTabChip")
		assertFalse(
			bottomChromeBody.contains("ReaderOptionsPanel("),
			"Bottom reader chrome must stay compact; settings belong in the Komikku-style modal sheet."
		)
	}

	@Test
	fun commonReaderOptionsUseKomikkuStyleChipGroups() {
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val readingOptionsBody = readerOptionsPanelText.substringAfter("private fun ReaderReadingOptions(")
			.substringBefore("private fun ReaderGeneralOptions(")
		val generalOptionsBody = readerOptionsPanelText.substringAfter("private fun ReaderGeneralOptions(")
			.substringBefore("private fun ReaderMediaOptions(")

		assertContains(readerOptionsPanelText, "ReaderSettingsChipRow")
		assertContains(readerOptionsPanelText, "ReaderOptionChip")
		assertContains(readerOptionsPanelText, "ReaderToggleChip")
		assertContains(readerOptionsPanelText, "FilterChip(")
		assertContains(readerOptionsPanelText, "FlowRow(")
		assertContains(readingOptionsBody, "ReaderSupportedFlowModes")
		assertContains(readingOptionsBody, "ReaderSupportedDirections")
		assertContains(readingOptionsBody, "ReaderSupportedFontFamilies")
		assertContains(generalOptionsBody, "ReaderSupportedThemes")
		assertContains(generalOptionsBody, "ReaderSupportedOrientations")
		assertContains(generalOptionsBody, "ReaderSupportedTapZones")
		assertFalse(
			readingOptionsBody.contains("ReaderCycleRow(") || generalOptionsBody.contains("ReaderCycleRow("),
			"Reading and General reader options should use Komikku-style selectable chip groups instead of cyclic value rows."
		)
	}

	@Test
	fun commonReaderOptionsSeparatePdfImageSettingsByPublicationFormat() {
		val readerScreenText = readerScreenFile().readText()
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val readerChromeStateText = readerCommonFile("ReaderChromeState.kt").readText()

		assertContains(readerChromeStateText, "ReaderOptionsTab.PdfImage")
		assertContains(readerChromeStateText, "publicationFormat: ReaderPublicationFormat")
		assertContains(readerScreenText, "publicationFormat = reader.publicationFormat")
		assertContains(readerOptionsPanelText, "publicationFormat: ReaderPublicationFormat")
		assertContains(readerOptionsPanelText, "ReaderPdfImageOptions(")
		assertContains(readerOptionsPanelText, "Page fit")
		assertContains(readerOptionsPanelText, "ReaderSupportedPdfFitModes")
		assertContains(readerOptionsPanelText, "Crop borders")
		assertContains(readerOptionsPanelText, "Page gap")
		assertContains(readerOptionsPanelText, "setPdfFitMode")
		assertContains(readerOptionsPanelText, "togglePdfCropBorders")
		assertContains(readerOptionsPanelText, "adjustPdfPageGap")
	}

	@Test
	fun commonReaderOptionsSupportKomikkuStylePerBookSettingsScope() {
		val readerScreenText = readerScreenFile().readText()
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
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
		assertContains(readerScreenText, "readerSettingsForBook(reader.bookId)")
		assertContains(readerScreenText, "readerBookSettingsJson")
		assertContains(readerScreenText, "setReaderBookSettings(reader.bookId")
		assertContains(readerScreenText, "clearReaderBookSettings(reader.bookId)")
		assertContains(readerOptionsPanelText, "For this book")
		assertContains(readerOptionsPanelText, "Global")
		assertContains(readerOptionsPanelText, "Reset book")
	}

	@Test
	fun commonReadaloudChromeSurfacesAudioMetadataLabels() {
		val readerScreenText = readerScreenFile().readText()
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val runtimeHostText = readerAndroidFile("ReaderReadaloudRuntimeHost.android.kt").readText()

		assertContains(readerScreenText, "activeAudioLabel")
		assertContains(readerScreenText, "ReaderReadaloudMetadataLabel")
		assertContains(readerOptionsPanelText, "activeAudioMetadata")
		assertContains(readerOptionsPanelText, "Narrator")
		assertContains(readerOptionsPanelText, "Quality")
		assertContains(readerOptionsPanelText, "Source")
		assertContains(readerOptionsPanelText, "Release")
		assertContains(readerOptionsPanelText, "Source URL")
		assertContains(runtimeHostText, "activeAudioLabel =")
		assertContains(runtimeHostText, "activeLabelForPlaybackPosition")
		assertContains(runtimeHostText, "activeAudioMetadata =")
		assertContains(runtimeHostText, "metadataLabelsForPlaybackPosition")
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

	@Test
	fun commonReadaloudChromeExposesPlaybackSpeedControls() {
		val readerScreenText = readerScreenFile().readText()
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val runtimeHostText = readerAndroidFile("ReaderReadaloudRuntimeHost.android.kt").readText()

		assertContains(readerScreenText, "onReadaloudSpeedChange")
		assertContains(readerOptionsPanelText, "adjustSpeedCommand")
		assertContains(readerOptionsPanelText, "ReaderControlStepper(")
		assertContains(readerOptionsPanelText, "label = \"Speed\"")
		assertContains(runtimeHostText, "ReaderReadaloudPlaybackCommand.SetSpeed")
		assertContains(runtimeHostText, "controller.setPlaybackSpeed")
	}

	@Test
	fun commonReadaloudChromeExposesSyncHighlightToggle() {
		val readerScreenText = readerScreenFile().readText()
		val readerOptionsPanelText = readerOptionsPanelFile().readText()
		val runtimeHostText = readerAndroidFile("ReaderReadaloudRuntimeHost.android.kt").readText()
		val preferenceText = readerCommonFile("ReaderPreferenceSettings.kt").readText()

		assertContains(readerScreenText, "onReadaloudSyncChange")
		assertContains(readerOptionsPanelText, "toggleSyncCommand")
		assertContains(readerOptionsPanelText, "Sync highlight")
		assertContains(readerScreenText, "readaloudSyncEnabled")
		assertContains(preferenceText, "readerReadaloudSyncEnabled")
		assertContains(runtimeHostText, "ReaderReadaloudPlaybackCommand.SetSyncEnabled")
		assertContains(runtimeHostText, "setSyncEnabled")
		assertContains(runtimeHostText, "readaloudSyncEnabled")
	}

}
