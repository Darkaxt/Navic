package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSettingsDefaultsTest {
	@Test
	fun readerSettingsDefaultsUseReadableParagraphSeparation() {
		assertEquals(100, defaultReaderSettings().paragraphSpacingPercent)
	}

	@Test
	fun readerSettingsDefaultsNormalizePersistedValues() {
		assertEquals(
			ReaderSettings(
				fontFamily = ReaderSerifFontFamily,
				fontSource = ReaderFontSourceNavic,
				fontSizePercent = 180,
				lineHeight = 1.2,
				paragraphSpacingPercent = 200,
				marginPercent = 24,
				dimOverlayPercent = 80,
				orientation = ReaderOrientationDefault,
				theme = "light",
				direction = ReaderDirectionDefault,
				flowMode = ReaderFlowScrolled,
				paged = false,
				tapZone = ReaderTapZoneDefault,
				smallerTapZone = false,
				showTapZones = false,
				pdfFitMode = ReaderPdfFitWidth,
				pdfCropBorders = false,
				pdfPageGapPercent = 0,
				publisherStyles = false,
				fullscreen = true,
				keepScreenOn = false,
				readaloudSyncEnabled = true,
				volumeKeyPageTurns = false,
				webContentsDebuggingEnabled = true
			),
			normalizedReaderSettings(
				fontFamily = ReaderSerifFontFamily,
				fontSizePercent = 260,
				lineHeightPercent = 80,
				paragraphSpacingPercent = 500,
				marginPercent = 60,
				dimOverlayPercent = 120,
				orientation = "sideways",
				theme = "neon",
				direction = "sideways",
				paged = false,
				tapZone = "maze",
				smallerTapZone = false,
				showTapZones = false,
				publisherStyles = false,
				fullscreen = true,
				keepScreenOn = false,
				readaloudSyncEnabled = true,
				volumeKeyPageTurns = false,
				webContentsDebuggingEnabled = true
			)
		)
	}

	@Test
	fun readerSettingsDefaultsKeepValidConfiguredValues() {
		assertEquals(
			ReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSource = ReaderFontSourceNavic,
				fontSizePercent = 112,
				lineHeight = 1.7,
				paragraphSpacingPercent = 75,
				marginPercent = 8,
				dimOverlayPercent = 30,
				orientation = ReaderOrientationLockedLandscape,
				theme = ReaderSepiaTheme,
				direction = ReaderDirectionRtl,
				flowMode = ReaderFlowPaged,
				paged = true,
				tapZone = ReaderTapZoneKindle,
				smallerTapZone = true,
				showTapZones = true,
				pdfFitMode = ReaderPdfFitWidth,
				pdfCropBorders = false,
				pdfPageGapPercent = 0,
				publisherStyles = true,
				fullscreen = false,
				keepScreenOn = true,
				readaloudSyncEnabled = false,
				volumeKeyPageTurns = true,
				webContentsDebuggingEnabled = false
			),
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 112,
				lineHeightPercent = 170,
				paragraphSpacingPercent = 75,
				marginPercent = 8,
				dimOverlayPercent = 30,
				orientation = ReaderOrientationLockedLandscape,
				theme = ReaderSepiaTheme,
				direction = ReaderDirectionRtl,
				paged = true,
				tapZone = ReaderTapZoneKindle,
				smallerTapZone = true,
				showTapZones = true,
				publisherStyles = true,
				fullscreen = false,
				keepScreenOn = true,
				readaloudSyncEnabled = false,
				volumeKeyPageTurns = true,
				webContentsDebuggingEnabled = false
			)
		)
	}

	@Test
	fun readerSettingsDefaultsKeepExpandedFontSources() {
		assertEquals(ReaderFontSourceNavic, defaultReaderSettings().fontSource)
		assertEquals(
			listOf(ReaderFontSourceNavic, ReaderFontSourceSystem, ReaderFontSourcePublisher, ReaderFontSourceCustom),
			ReaderSupportedFontSources
		)
		assertEquals(ReaderFontSourceNavic, normalizedReaderFontSource("missing"))
		assertEquals(ReaderFontSourceSystem, normalizedReaderFontSource(ReaderFontSourceSystem))
		assertEquals(ReaderFontSourcePublisher, normalizedReaderFontSource(ReaderFontSourcePublisher))
		assertEquals(ReaderFontSourceCustom, normalizedReaderFontSource(ReaderFontSourceCustom))
		assertEquals("Imported", readerFontSourceShortLabel(ReaderFontSourceCustom))
		assertEquals("\"Navic Literata\", Literata, Bookerly, Georgia, serif", ReaderBookFontFamily)
		assertEquals(
			"\"Navic Atkinson Hyperlegible\", \"Atkinson Hyperlegible\", Lexend, system-ui, sans-serif",
			ReaderHumanistFontFamily
		)
		assertEquals(
			"\"Navic OpenDyslexic\", OpenDyslexic, \"Navic Atkinson Hyperlegible\", system-ui, sans-serif",
			ReaderDyslexicFontFamily
		)
		assertEquals(
			ReaderBookFontFamily,
			normalizedReaderSettings(
				fontFamily = ReaderBookFontFamily,
				fontSource = ReaderFontSourceSystem,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true
			).fontFamily
		)
		assertEquals(
			ReaderFontSourceSystem,
			normalizedReaderSettings(
				fontFamily = ReaderBookFontFamily,
				fontSource = ReaderFontSourceSystem,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true
			).fontSource
		)
		assertEquals(
			ReaderPublisherFontFamily,
			normalizedReaderSettings(
				fontFamily = ReaderPublisherFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true
			).fontFamily
		)
		assertEquals(
			ReaderSansFontFamily,
			normalizedReaderSettings(
				fontFamily = "Comic Sans MS",
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true
			).fontFamily
		)
		val custom = normalizedReaderSettings(
			fontFamily = ReaderSansFontFamily,
			fontSource = ReaderFontSourceCustom,
			customFontFamily = "Storyteller Serif",
			customFontUrl = "https://appassets.androidplatform.net/reader-cache/fonts/storyteller-serif.ttf",
			fontSizePercent = 100,
			lineHeightPercent = 155,
			marginPercent = 0,
			theme = "light",
			paged = true
		)
		assertEquals(ReaderFontSourceCustom, custom.fontSource)
		assertEquals("Storyteller Serif", custom.customFontFamily)
		assertEquals(
			"https://appassets.androidplatform.net/reader-cache/fonts/storyteller-serif.ttf",
			custom.customFontUrl
		)
	}

	@Test
	fun readerSettingsDefaultsKeepExpandedThemePalettes() {
		assertEquals(
			ReaderDuskTheme,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = ReaderDuskTheme,
				paged = true
			).theme
		)
		assertEquals(
			ReaderBlackTheme,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = ReaderBlackTheme,
				paged = true
			).theme
		)
	}

	@Test
	fun readerSettingsDefaultsKeepExplicitReadingFlowModes() {
		assertEquals(
			listOf(ReaderFlowPaged, ReaderFlowPagedVertical, ReaderFlowScrolled, ReaderFlowScrolledGaps),
			ReaderSupportedFlowModes
		)
		assertEquals(
			ReaderFlowPagedVertical,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true,
				flowMode = ReaderFlowPagedVertical
			).flowMode
		)
		assertEquals(
			ReaderFlowScrolledGaps,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true,
				flowMode = ReaderFlowScrolledGaps
			).flowMode
		)
		assertEquals(
			ReaderFlowPaged,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true,
				flowMode = "unknown-flow"
			).flowMode
		)
		assertEquals(ReaderFlowPaged, normalizedReaderFlowMode(flowMode = null, paged = true))
		assertEquals(ReaderFlowScrolled, normalizedReaderFlowMode(flowMode = null, paged = false))
	}

	@Test
	fun readerSettingsDefaultsKeepExplicitReadingDirections() {
		assertEquals(
			listOf(ReaderDirectionDefault, ReaderDirectionLtr, ReaderDirectionRtl),
			ReaderSupportedDirections
		)
		assertEquals(
			ReaderDirectionLtr,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true,
				direction = ReaderDirectionLtr
			).direction
		)
		assertEquals(
			ReaderDirectionRtl,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true,
				direction = ReaderDirectionRtl
			).direction
		)
		assertEquals(
			ReaderDirectionDefault,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true,
				direction = "zigzag"
			).direction
		)
	}

	@Test
	fun readerSettingsDefaultsClampParagraphSpacing() {
		assertEquals(
			0,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				paragraphSpacingPercent = -40,
				marginPercent = 0,
				theme = "light",
				paged = true
			).paragraphSpacingPercent
		)
		assertEquals(
			200,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				paragraphSpacingPercent = 360,
				marginPercent = 0,
				theme = "light",
				paged = true
			).paragraphSpacingPercent
		)
	}

	@Test
	fun readerSettingsDefaultsClampDimOverlay() {
		assertEquals(
			0,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				dimOverlayPercent = -20,
				theme = "light",
				paged = true
			).dimOverlayPercent
		)
		assertEquals(
			80,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				dimOverlayPercent = 120,
				theme = "light",
				paged = true
			).dimOverlayPercent
		)
	}

	@Test
	fun readerSettingsDefaultsKeepSupportedOrientationModes() {
		assertEquals(
			ReaderOrientationFree,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				orientation = ReaderOrientationFree,
				theme = "light",
				paged = true
			).orientation
		)
		assertEquals(
			ReaderOrientationReversePortrait,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				orientation = ReaderOrientationReversePortrait,
				theme = "light",
				paged = true
			).orientation
		)
		assertEquals(
			ReaderOrientationDefault,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				orientation = "diagonal",
				theme = "light",
				paged = true
			).orientation
		)
	}

	@Test
	fun readerSettingsDefaultsKeepValidTapZonePresets() {
		assertEquals(
			ReaderTapZoneEdge,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true,
				tapZone = ReaderTapZoneEdge
			).tapZone
		)
		assertEquals(
			ReaderTapZoneDisabled,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true,
				tapZone = ReaderTapZoneDisabled
			).tapZone
		)
	}
}
