package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSettingsDefaultsTest {
	@Test
	fun readerSettingsDefaultsUseReadableParagraphSeparation() {
		assertEquals(100, defaultReaderSettings().paragraphSpacingPercent)
	}

	@Test
	fun readerSettingsDefaultsPreserveAnxReadableProseScale() {
		assertEquals(140, defaultReaderSettings().fontSizePercent)
		assertEquals(1.8, defaultReaderSettings().lineHeight)
	}

	@Test
	fun readerSettingsDefaultsExposeAnxStyleDimensions() {
		assertEquals(400.0, defaultReaderSettings().fontWeight)
		assertEquals(0.0, defaultReaderSettings().letterSpacing)
		assertEquals(0.0, defaultReaderSettings().wordSpacing)
		assertEquals(6.0, defaultReaderSettings().sideMargin)
		assertEquals(90.0, defaultReaderSettings().topMargin)
		assertEquals(50.0, defaultReaderSettings().bottomMargin)
		assertEquals(0.0, defaultReaderSettings().indent)
		assertEquals(1.0, defaultReaderSettings().headingFontSize)
		assertEquals(0, defaultReaderSettings().maxColumnCount)
		assertEquals(720.0, defaultReaderSettings().columnThreshold)
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
				fontWeight = 900.0,
				letterSpacing = 7.0,
				wordSpacing = 12.0,
				sideMargin = 20.0,
				topMargin = 200.0,
				bottomMargin = 0.0,
				indent = 8.0,
				headingFontSize = 2.0,
				maxColumnCount = 2,
				columnThreshold = 1200.0,
				dimOverlayPercent = 80,
				colorFilterEnabled = false,
				colorFilterArgb = 0,
				colorFilterMode = ReaderColorFilterModeSrcOver,
				grayscaleEnabled = false,
				invertedColors = false,
				orientation = ReaderOrientationDefault,
				theme = "light",
				direction = ReaderDirectionDefault,
				navBarType = ReaderNavBarTypeVerticalRight,
				flowMode = ReaderFlowScrolled,
				pageTurnAnimation = ReaderPageTurnNone,
				paged = false,
				tapZone = ReaderTapZoneDefault,
				tapZoneInvertMode = ReaderTapZoneInvertNone,
				smallerTapZone = false,
				showTapZones = false,
				pdfFitMode = ReaderPdfFitWidth,
				pdfCropBorders = false,
				pdfPageGapPercent = 0,
				publisherStyles = false,
				fullscreen = true,
				keepScreenOn = false,
				readaloudSyncEnabled = true,
				whispersyncHighlightLeadMs = MaxReaderWhispersyncHighlightLeadMs,
				volumeKeyPageTurns = false,
				webContentsDebuggingEnabled = true
			),
			normalizedReaderSettings(
				fontFamily = ReaderSerifFontFamily,
				fontSizePercent = 260,
				lineHeightPercent = 80,
				paragraphSpacingPercent = 500,
				marginPercent = 60,
				fontWeight = 1600.0,
				letterSpacing = 20.0,
				wordSpacing = 30.0,
				sideMargin = 60.0,
				topMargin = 250.0,
				bottomMargin = -50.0,
				indent = 20.0,
				headingFontSize = 3.0,
				maxColumnCount = 5,
				columnThreshold = 2000.0,
				dimOverlayPercent = 120,
				orientation = "sideways",
				theme = "neon",
				direction = "sideways",
				pageTurnAnimation = "fold",
				paged = false,
				tapZone = "maze",
				smallerTapZone = false,
				showTapZones = false,
				publisherStyles = false,
				fullscreen = true,
				keepScreenOn = false,
				readaloudSyncEnabled = true,
				whispersyncHighlightLeadMs = 8_000,
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
				fontWeight = 650.0,
				letterSpacing = 1.25,
				wordSpacing = 2.5,
				sideMargin = 12.0,
				topMargin = 80.0,
				bottomMargin = 60.0,
				indent = 1.5,
				headingFontSize = 1.25,
				maxColumnCount = 2,
				columnThreshold = 840.0,
				dimOverlayPercent = 30,
				colorFilterEnabled = false,
				colorFilterArgb = 0,
				colorFilterMode = ReaderColorFilterModeSrcOver,
				grayscaleEnabled = true,
				invertedColors = true,
				orientation = ReaderOrientationLockedLandscape,
				theme = ReaderSepiaTheme,
				direction = ReaderDirectionRtl,
				navBarType = ReaderNavBarTypeBottom,
				flowMode = ReaderFlowPaged,
				pageTurnAnimation = ReaderPageTurnCanvas,
				paged = true,
				tapZone = ReaderTapZoneKindle,
				tapZoneInvertMode = ReaderTapZoneInvertBoth,
				smallerTapZone = true,
				showTapZones = true,
				pdfFitMode = ReaderPdfFitWidth,
				pdfCropBorders = false,
				pdfPageGapPercent = 0,
				publisherStyles = true,
				fullscreen = false,
				keepScreenOn = true,
				readaloudSyncEnabled = false,
				whispersyncHighlightLeadMs = 1_500,
				volumeKeyPageTurns = true,
				webContentsDebuggingEnabled = false
			),
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 112,
				lineHeightPercent = 170,
				paragraphSpacingPercent = 75,
				marginPercent = 8,
				fontWeight = 650.0,
				letterSpacing = 1.25,
				wordSpacing = 2.5,
				sideMargin = 12.0,
				topMargin = 80.0,
				bottomMargin = 60.0,
				indent = 1.5,
				headingFontSize = 1.25,
				maxColumnCount = 2,
				columnThreshold = 840.0,
				dimOverlayPercent = 30,
				grayscaleEnabled = true,
				invertedColors = true,
				orientation = ReaderOrientationLockedLandscape,
				theme = ReaderSepiaTheme,
				direction = ReaderDirectionRtl,
				navBarType = ReaderNavBarTypeBottom,
				pageTurnAnimation = ReaderPageTurnCanvas,
				paged = true,
				tapZone = ReaderTapZoneKindle,
				tapZoneInvertMode = ReaderTapZoneInvertBoth,
				smallerTapZone = true,
				showTapZones = true,
				publisherStyles = true,
				fullscreen = false,
				keepScreenOn = true,
				readaloudSyncEnabled = false,
				whispersyncHighlightLeadMs = 1_500,
				volumeKeyPageTurns = true,
				webContentsDebuggingEnabled = false
			)
		)
	}

	@Test
	fun readerSettingsDefaultsKeepExpandedFontSources() {
		val typewriterFontFamily = "\"American Typewriter\", \"Courier Prime\", \"Courier New\", ui-monospace, monospace"

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
			listOf(
				ReaderSansFontFamily,
				ReaderSerifFontFamily,
				ReaderBookFontFamily,
				ReaderHumanistFontFamily,
				ReaderDyslexicFontFamily,
				typewriterFontFamily,
				ReaderMonoFontFamily,
				ReaderPublisherFontFamily
			),
			ReaderSupportedFontFamilies
		)
		assertEquals(typewriterFontFamily, normalizedReaderFontFamily(typewriterFontFamily))
		assertEquals("Dyx", readerFontFamilyShortLabel(typewriterFontFamily))
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
	fun readerSettingsDefaultsKeepExplicitDragAnimationModes() {
		assertEquals(
			listOf(ReaderPageTurnNone, ReaderPageTurnCanvas, ReaderPageTurnWebgl),
			ReaderSupportedPageTurnAnimations
		)
		assertEquals(ReaderPageTurnNone, defaultReaderSettings().pageTurnAnimation)
		assertEquals(ReaderPageTurnNone, normalizedPageTurnAnimation("fold"))
		assertEquals(ReaderPageTurnCanvas, normalizedPageTurnAnimation(ReaderPageTurnCanvas))
		assertEquals("None", pageTurnAnimationShortLabel(ReaderPageTurnNone))
		assertEquals("Canvas", pageTurnAnimationShortLabel(ReaderPageTurnCanvas))
		assertEquals("WebGL", pageTurnAnimationShortLabel(ReaderPageTurnWebgl))
		assertEquals(
			ReaderPageTurnCanvas,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true,
				pageTurnAnimation = ReaderPageTurnCanvas
			).pageTurnAnimation
		)
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
	fun readerSettingsDefaultsKeepKomikkuNavBarTypes() {
		assertEquals(
			listOf(ReaderNavBarTypeVerticalRight, ReaderNavBarTypeVerticalLeft, ReaderNavBarTypeBottom),
			ReaderSupportedNavBarTypes
		)
		assertEquals(ReaderNavBarTypeVerticalRight, defaultReaderSettings().navBarType)
		assertEquals(ReaderNavBarTypeVerticalRight, normalizedReaderNavBarType("missing"))
		assertEquals(ReaderNavBarTypeVerticalLeft, normalizedReaderNavBarType(ReaderNavBarTypeVerticalLeft))
		assertEquals(ReaderNavBarTypeBottom, normalizedReaderNavBarType(ReaderNavBarTypeBottom))
		assertEquals("Right", readerNavBarTypeShortLabel(ReaderNavBarTypeVerticalRight))
		assertEquals("Left", readerNavBarTypeShortLabel(ReaderNavBarTypeVerticalLeft))
		assertEquals("Bottom", readerNavBarTypeShortLabel(ReaderNavBarTypeBottom))
		assertEquals(
			ReaderNavBarTypeVerticalLeft,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true,
				navBarType = ReaderNavBarTypeVerticalLeft
			).navBarType
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
	fun readerSettingsDefaultsKeepKomikkuColorFilterModel() {
		assertEquals(false, defaultReaderSettings().colorFilterEnabled)
		assertEquals(0, defaultReaderSettings().colorFilterArgb)
		assertEquals(ReaderColorFilterModeSrcOver, defaultReaderSettings().colorFilterMode)
		assertEquals(false, defaultReaderSettings().grayscaleEnabled)
		assertEquals(false, defaultReaderSettings().invertedColors)
		assertEquals(
			listOf(
				ReaderColorFilterModeSrcOver,
				ReaderColorFilterModeMultiply,
				ReaderColorFilterModeScreen,
				ReaderColorFilterModeOverlay,
				ReaderColorFilterModeLighten,
				ReaderColorFilterModeDarken
			),
			ReaderSupportedColorFilterModes
		)

		val settings = normalizedReaderSettings(
			fontFamily = ReaderSansFontFamily,
			fontSizePercent = 100,
			lineHeightPercent = 155,
			marginPercent = 0,
			theme = "light",
			paged = true,
			colorFilterEnabled = true,
			colorFilterArgb = 0x66336699,
			colorFilterMode = ReaderColorFilterModeMultiply,
			grayscaleEnabled = true,
			invertedColors = true
		)

		assertEquals(true, settings.colorFilterEnabled)
		assertEquals(0x66336699, settings.colorFilterArgb)
		assertEquals(ReaderColorFilterModeMultiply, settings.colorFilterMode)
		assertEquals(true, settings.grayscaleEnabled)
		assertEquals(true, settings.invertedColors)
		assertEquals(ReaderColorFilterModeSrcOver, normalizedReaderColorFilterMode("missing"))
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
			listOf(
				ReaderTapZoneInvertNone,
				ReaderTapZoneInvertHorizontal,
				ReaderTapZoneInvertVertical,
				ReaderTapZoneInvertBoth
			),
			ReaderSupportedTapZoneInvertModes
		)
		assertEquals(ReaderTapZoneInvertNone, defaultReaderSettings().tapZoneInvertMode)
		assertEquals(ReaderTapZoneInvertNone, normalizedReaderTapZoneInvertMode("missing"))
		assertEquals("None", readerTapZoneInvertModeShortLabel(ReaderTapZoneInvertNone))
		assertEquals("Horizontal", readerTapZoneInvertModeShortLabel(ReaderTapZoneInvertHorizontal))
		assertEquals("Vertical", readerTapZoneInvertModeShortLabel(ReaderTapZoneInvertVertical))
		assertEquals("Both", readerTapZoneInvertModeShortLabel(ReaderTapZoneInvertBoth))
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
			ReaderTapZoneInvertHorizontal,
			normalizedReaderSettings(
				fontFamily = ReaderSansFontFamily,
				fontSizePercent = 100,
				lineHeightPercent = 155,
				marginPercent = 0,
				theme = "light",
				paged = true,
				tapZoneInvertMode = ReaderTapZoneInvertHorizontal
			).tapZoneInvertMode
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
