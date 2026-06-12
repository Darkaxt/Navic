package paige.navic.reader

import com.russhwolf.settings.MapSettings
import paige.navic.domain.manager.PreferenceManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderPreferenceSettingsTest {
	@Test
	fun readerDefaultSettingsRoundTripFontSourcePreference() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerFontSource = ReaderFontSourceCustom
		preferences.readerCustomFontFamily = "Storyteller Serif"
		preferences.readerCustomFontUrl = "https://appassets.androidplatform.net/reader-cache/fonts/storyteller-serif.ttf"

		val defaults = preferences.readerDefaultSettings()
		assertEquals(ReaderFontSourceCustom, defaults.fontSource)
		assertEquals("Storyteller Serif", defaults.customFontFamily)
		assertEquals(
			"https://appassets.androidplatform.net/reader-cache/fonts/storyteller-serif.ttf",
			defaults.customFontUrl
		)

		preferences.setReaderDefaultSettings(
			ReaderSettings(
				fontSource = ReaderFontSourceSystem,
				customFontFamily = "Ignored",
				customFontUrl = "https://appassets.androidplatform.net/reader-cache/fonts/ignored.ttf"
			)
		)

		assertEquals(ReaderFontSourceSystem, preferences.readerFontSource)
		assertEquals("", preferences.readerCustomFontFamily)
		assertEquals("", preferences.readerCustomFontUrl)
	}

	@Test
	fun readerDefaultSettingsRoundTripTapZonePreference() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerTapZone = ReaderTapZoneEdge
		preferences.readerSmallerTapZone = true
		preferences.readerShowTapZones = true

		assertEquals(ReaderTapZoneEdge, preferences.readerDefaultSettings().tapZone)
		assertEquals(true, preferences.readerDefaultSettings().smallerTapZone)
		assertEquals(true, preferences.readerDefaultSettings().showTapZones)

		preferences.setReaderDefaultSettings(
			ReaderSettings(
				tapZone = ReaderTapZoneDisabled,
				smallerTapZone = false,
				showTapZones = false
			)
		)

		assertEquals(ReaderTapZoneDisabled, preferences.readerTapZone)
		assertEquals(false, preferences.readerSmallerTapZone)
		assertEquals(false, preferences.readerShowTapZones)
	}

	@Test
	fun readerDefaultSettingsRoundTripParagraphSpacingAndPublisherStyles() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerParagraphSpacingPercent = 75
		preferences.readerPublisherStylesEnabled = true

		val defaults = preferences.readerDefaultSettings()
		assertEquals(75, defaults.paragraphSpacingPercent)
		assertEquals(true, defaults.publisherStyles)

		preferences.setReaderDefaultSettings(
			ReaderSettings(
				paragraphSpacingPercent = 150,
				publisherStyles = false
			)
		)

		assertEquals(150, preferences.readerParagraphSpacingPercent)
		assertEquals(false, preferences.readerPublisherStylesEnabled)
	}

	@Test
	fun readerDefaultSettingsMigratesLegacyParagraphSpacingDefault() {
		val preferences = PreferenceManager(MapSettings())

		val defaults = preferences.readerDefaultSettings()

		assertEquals(100, defaults.paragraphSpacingPercent)
		assertEquals(100, preferences.readerParagraphSpacingPercent)
		assertEquals(true, preferences.readerParagraphSpacingDefaultMigrated)
	}

	@Test
	fun readerDefaultSettingsMigratesInstalledZeroParagraphSpacingToReadableDefault() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerParagraphSpacingDefaultMigrated = true
		preferences.readerParagraphSpacingReadableDefaultMigrated = false
		preferences.readerParagraphSpacingPercent = 0

		assertEquals(100, preferences.readerDefaultSettings().paragraphSpacingPercent)
		assertEquals(100, preferences.readerParagraphSpacingPercent)
		assertEquals(true, preferences.readerParagraphSpacingReadableDefaultMigrated)
	}

	@Test
	fun readerDefaultSettingsKeepsExplicitZeroParagraphSpacingAfterReadableMigration() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerParagraphSpacingDefaultMigrated = true
		preferences.readerParagraphSpacingReadableDefaultMigrated = true
		preferences.readerParagraphSpacingPercent = 0

		assertEquals(0, preferences.readerDefaultSettings().paragraphSpacingPercent)

		preferences.setReaderDefaultSettings(ReaderSettings(paragraphSpacingPercent = 0))

		assertEquals(0, preferences.readerParagraphSpacingPercent)
		assertEquals(true, preferences.readerParagraphSpacingDefaultMigrated)
		assertEquals(true, preferences.readerParagraphSpacingReadableDefaultMigrated)
	}

	@Test
	fun readerDefaultSettingsRoundTripKeepScreenOn() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerKeepScreenOn = true

		assertEquals(true, preferences.readerDefaultSettings().keepScreenOn)

		preferences.setReaderDefaultSettings(
			ReaderSettings(keepScreenOn = false)
		)

		assertEquals(false, preferences.readerKeepScreenOn)
	}

	@Test
	fun readerDefaultSettingsRoundTripFullscreen() {
		val preferences = PreferenceManager(MapSettings())

		assertEquals(true, preferences.readerDefaultSettings().fullscreen)

		preferences.setReaderDefaultSettings(
			ReaderSettings(fullscreen = false)
		)

		assertEquals(false, preferences.readerFullscreen)
		assertEquals(false, preferences.readerDefaultSettings().fullscreen)
	}

	@Test
	fun readerDefaultSettingsRoundTripReadaloudSyncEnabled() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerReadaloudSyncEnabled = false

		assertEquals(false, preferences.readerDefaultSettings().readaloudSyncEnabled)

		preferences.setReaderDefaultSettings(
			ReaderSettings(readaloudSyncEnabled = true)
		)

		assertEquals(true, preferences.readerReadaloudSyncEnabled)
	}

	@Test
	fun readerDefaultSettingsRoundTripDimOverlay() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerDimOverlayPercent = 30

		assertEquals(30, preferences.readerDefaultSettings().dimOverlayPercent)

		preferences.setReaderDefaultSettings(
			ReaderSettings(dimOverlayPercent = 60)
		)

		assertEquals(60, preferences.readerDimOverlayPercent)
	}

	@Test
	fun readerDefaultSettingsRoundTripOrientation() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerOrientation = ReaderOrientationPortrait

		assertEquals(ReaderOrientationPortrait, preferences.readerDefaultSettings().orientation)

		preferences.setReaderDefaultSettings(
			ReaderSettings(orientation = ReaderOrientationLockedLandscape)
		)

		assertEquals(ReaderOrientationLockedLandscape, preferences.readerOrientation)
	}

	@Test
	fun readerDefaultSettingsRoundTripExplicitFlowModePreference() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerFlowMode = ReaderFlowScrolledGaps

		assertEquals(ReaderFlowScrolledGaps, preferences.readerDefaultSettings().flowMode)
		assertEquals(false, preferences.readerDefaultSettings().paged)

		preferences.setReaderDefaultSettings(
			ReaderSettings(flowMode = ReaderFlowPagedVertical)
		)

		assertEquals(ReaderFlowPagedVertical, preferences.readerFlowMode)
		assertEquals(true, preferences.readerPaged)
	}

	@Test
	fun readerDefaultSettingsRoundTripReadingDirectionPreference() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerDirection = ReaderDirectionRtl

		assertEquals(ReaderDirectionRtl, preferences.readerDefaultSettings().direction)

		preferences.setReaderDefaultSettings(
			ReaderSettings(direction = ReaderDirectionLtr)
		)

		assertEquals(ReaderDirectionLtr, preferences.readerDirection)
	}

	@Test
	fun readerDefaultSettingsRoundTripVolumeKeyPageTurns() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerVolumeKeyPageTurns = true

		assertEquals(true, preferences.readerDefaultSettings().volumeKeyPageTurns)

		preferences.setReaderDefaultSettings(
			ReaderSettings(volumeKeyPageTurns = false)
		)

		assertEquals(false, preferences.readerVolumeKeyPageTurns)
	}

	@Test
	fun readerDefaultSettingsRoundTripPdfImagePreferences() {
		val preferences = PreferenceManager(MapSettings())

		preferences.readerPdfFitMode = ReaderPdfFitHeight
		preferences.readerPdfCropBorders = true
		preferences.readerPdfPageGapPercent = 18

		val defaults = preferences.readerDefaultSettings()
		assertEquals(ReaderPdfFitHeight, defaults.pdfFitMode)
		assertEquals(true, defaults.pdfCropBorders)
		assertEquals(18, defaults.pdfPageGapPercent)

		preferences.setReaderDefaultSettings(
			ReaderSettings(
				pdfFitMode = ReaderPdfFitOriginal,
				pdfCropBorders = false,
				pdfPageGapPercent = 24
			)
		)

		assertEquals(ReaderPdfFitOriginal, preferences.readerPdfFitMode)
		assertEquals(false, preferences.readerPdfCropBorders)
		assertEquals(24, preferences.readerPdfPageGapPercent)
		assertEquals(ReaderPdfFitOriginal, preferences.readerDefaultSettings().pdfFitMode)
		assertEquals(false, preferences.readerDefaultSettings().pdfCropBorders)
		assertEquals(24, preferences.readerDefaultSettings().pdfPageGapPercent)
	}

	@Test
	fun readerBookSettingsOverrideMergesOverGlobalDefaultsWithoutMutatingThem() {
		val preferences = PreferenceManager(MapSettings())
		preferences.readerTheme = ReaderDarkTheme
		preferences.readerFontSizePercent = 100
		preferences.readerDirection = ReaderDirectionDefault

		preferences.setReaderBookSettings(
			bookId = "book-1",
			settings = ReaderSettings(
				theme = ReaderSepiaTheme,
				fontSizePercent = 128,
				direction = ReaderDirectionRtl
			)
		)

		val bookSettings = preferences.readerSettingsForBook("book-1")
		assertEquals(ReaderSepiaTheme, bookSettings.theme)
		assertEquals(128, bookSettings.fontSizePercent)
		assertEquals(ReaderDirectionRtl, bookSettings.direction)
		assertEquals(ReaderDarkTheme, preferences.readerDefaultSettings().theme)
		assertEquals(100, preferences.readerDefaultSettings().fontSizePercent)
	}

	@Test
	fun readerBookSettingsOverrideIsScopedToTheRequestedBook() {
		val preferences = PreferenceManager(MapSettings())
		preferences.readerTheme = ReaderDarkTheme

		preferences.setReaderBookSettings(
			bookId = "book-1",
			settings = ReaderSettings(theme = ReaderSepiaTheme)
		)

		assertEquals(ReaderSepiaTheme, preferences.readerSettingsForBook("book-1").theme)
		assertEquals(ReaderDarkTheme, preferences.readerSettingsForBook("book-2").theme)
		assertNull(preferences.readerBookSettings("book-2"))
	}

	@Test
	fun readerBookSettingsOverrideCanBeCleared() {
		val preferences = PreferenceManager(MapSettings())
		preferences.readerTheme = ReaderDarkTheme
		preferences.setReaderBookSettings(
			bookId = "book-1",
			settings = ReaderSettings(theme = ReaderSepiaTheme)
		)

		preferences.clearReaderBookSettings("book-1")

		assertEquals(ReaderDarkTheme, preferences.readerSettingsForBook("book-1").theme)
		assertNull(preferences.readerBookSettings("book-1"))
	}
}
