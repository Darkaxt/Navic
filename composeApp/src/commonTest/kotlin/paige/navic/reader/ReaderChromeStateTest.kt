package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderChromeStateTest {
	@Test
	fun locationEventsDriveProgressAndCurrentSectionLabels() {
		val state = ReaderChromeState().onReaderEvent(
			ReaderBridgeEvent.LocationChanged(
				locator = ReaderLocator(
					href = "chapter-03.xhtml",
					cfi = "epubcfi(/6/8!/4/1:0)",
					progress = 0.342
				),
				tocTitle = "Chapter 3"
			)
		)

		assertEquals("Chapter 3", state.currentSectionTitle)
		assertEquals("epubcfi(/6/8!/4/1:0)", state.currentLocator?.cfi)
		assertEquals(0.342f, state.progressFraction)
		assertEquals("34%", state.progressLabel)
	}

	@Test
	fun progressLabelsClampInvalidReaderFractions() {
		val overComplete = ReaderChromeState().onReaderEvent(
			ReaderBridgeEvent.LocationChanged(ReaderLocator(progress = 1.4))
		)
		val beforeStart = ReaderChromeState().onReaderEvent(
			ReaderBridgeEvent.LocationChanged(ReaderLocator(progress = -0.4))
		)

		assertEquals(1f, overComplete.progressFraction)
		assertEquals("100%", overComplete.progressLabel)
		assertEquals(0f, beforeStart.progressFraction)
		assertEquals("0%", beforeStart.progressLabel)
	}

	@Test
	fun fixedLayoutLocationsUsePageAwareProgressLabels() {
		val state = ReaderChromeState().onReaderEvent(
			ReaderBridgeEvent.LocationChanged(
				ReaderLocator(
					progress = 0.05,
					pageIndex = 6,
					pageCount = 120
				)
			)
		)

		assertEquals(0.05f, state.progressFraction)
		assertEquals("Page 7 of 120 • 5%", state.progressLabel)
	}

	@Test
	fun typographyControlsCreateReaderSettingsCommands() {
		val larger = ReaderChromeState().adjustFontSize(12)
		val sepia = larger.toggleTheme()
		val scrolled = sepia.togglePagedMode()
		val serif = scrolled.toggleFontFamily()
		val taller = serif.adjustLineHeight(0.1)
		val wider = taller.adjustMargin(8)

		assertEquals(112, larger.settings.fontSizePercent)
		assertEquals(ReaderSepiaTheme, sepia.settings.theme)
		assertFalse(scrolled.settings.paged ?: true)
		assertEquals("Georgia, serif", serif.settings.fontFamily)
		assertEquals(1.65, taller.settings.lineHeight)
		assertEquals(8, wider.settings.marginPercent)
		assertIs<ReaderBridgeCommand.ApplySettings>(wider.toSettingsCommand())
		assertEquals(wider.settings, wider.toSettingsCommand().settings)
	}

	@Test
	fun themeControlsCycleThroughReaderPalettes() {
		var state = ReaderChromeState()
		val visited = mutableListOf<String>()

		repeat(ReaderSupportedThemes.size) {
			state = state.toggleTheme()
			visited += state.settings.theme ?: error("theme must be normalized")
		}

		assertEquals(
			ReaderSupportedThemes.drop(1) + ReaderSupportedThemes.take(1),
			visited
		)
		assertEquals("Sepia", readerThemeShortLabel(ReaderSepiaTheme))
		assertEquals("Black", readerThemeShortLabel(ReaderBlackTheme))
	}

	@Test
	fun fontFamilyControlsCycleThroughReaderFontSources() {
		var state = ReaderChromeState()
		val visited = mutableListOf<String>()

		repeat(ReaderSupportedFontFamilies.size) {
			state = state.toggleFontFamily()
			visited += state.settings.fontFamily ?: error("font family must be normalized")
		}

		assertEquals(
			ReaderSupportedFontFamilies.drop(1) + ReaderSupportedFontFamilies.take(1),
			visited
		)
		assertEquals("Book", readerFontFamilyShortLabel(ReaderBookFontFamily))
		assertEquals("Pub", readerFontFamilyShortLabel(ReaderPublisherFontFamily))
	}

	@Test
	fun readingDirectionControlsCycleThroughKomikkuStyleDirectionPresets() {
		var state = ReaderChromeState()
		val visited = mutableListOf<String>()

		repeat(ReaderSupportedDirections.size) {
			state = state.toggleDirection()
			visited += state.settings.direction ?: error("direction must be normalized")
		}

		assertEquals(
			ReaderSupportedDirections.drop(1) + ReaderSupportedDirections.take(1),
			visited
		)
		assertEquals("Default", readerDirectionShortLabel(ReaderDirectionDefault))
		assertEquals("LTR", readerDirectionShortLabel(ReaderDirectionLtr))
		assertEquals("RTL", readerDirectionShortLabel(ReaderDirectionRtl))
	}

	@Test
	fun nativeTapZonesMatchKomikkuRightLeftPagedDefault() {
		assertEquals(
			ReaderTapZoneAction.Left,
			readerTapZoneActionAt(
				tapZone = ReaderTapZoneDefault,
				xFraction = 0.05f,
				yFraction = 0.5f,
				flowMode = ReaderFlowPaged
			)
		)
		assertEquals(
			ReaderTapZoneAction.Right,
			readerTapZoneActionAt(
				tapZone = ReaderTapZoneDefault,
				xFraction = 0.95f,
				yFraction = 0.5f,
				flowMode = ReaderFlowPaged
			)
		)
		assertEquals(
			ReaderTapZoneAction.Menu,
			readerTapZoneActionAt(
				tapZone = ReaderTapZoneDefault,
				xFraction = 0.5f,
				yFraction = 0.5f,
				flowMode = ReaderFlowPaged
			)
		)
	}

	@Test
	fun nativeTapOverlayRegionsLeaveContentInteractionGaps() {
		val regions = readerTapZoneInteractiveRegions(
			tapZone = ReaderTapZoneDefault,
			smallerTapZone = false,
			flowMode = ReaderFlowPaged
		)

		assertTrue(regions.any { region ->
			region.action == ReaderTapZoneAction.Menu &&
				region.contains(0.5f, 0.02f)
		})
		assertTrue(regions.any { region ->
			region.action == ReaderTapZoneAction.Menu &&
				region.contains(0.5f, 0.5f)
		})
		assertTrue(regions.any { region ->
			region.action == ReaderTapZoneAction.Left &&
				region.contains(0.05f, 0.5f)
		})
		assertTrue(regions.any { region ->
			region.action == ReaderTapZoneAction.Right &&
				region.contains(0.95f, 0.5f)
		})
		assertFalse(
			regions.any { region -> region.contains(0.5f, 0.12f) },
			"Native overlay must leave non-zone content areas available for WebView links, images, and text selection."
		)
		assertFalse(
			regions.any { region ->
				region.left == 0f && region.top == 0f && region.right == 1f && region.bottom == 1f
			},
			"Native overlay must not model a full-screen pointer target."
		)
	}

	@Test
	fun nativeTapZonesUseLShapedDefaultForScrolledModes() {
		assertEquals(
			ReaderTapZoneAction.Previous,
			readerTapZoneActionAt(
				tapZone = ReaderTapZoneDefault,
				xFraction = 0.5f,
				yFraction = 0.1f,
				flowMode = ReaderFlowScrolled
			)
		)
		assertEquals(
			ReaderTapZoneAction.Next,
			readerTapZoneActionAt(
				tapZone = ReaderTapZoneDefault,
				xFraction = 0.5f,
				yFraction = 0.9f,
				flowMode = ReaderFlowScrolled
			)
		)
	}

	@Test
	fun nativeTapZoneCommandsRespectReadingDirection() {
		assertEquals(
			ReaderBridgeCommand.PreviousPage,
			readerTapZonePageTurnCommand(ReaderTapZoneAction.Left, ReaderDirectionLtr)
		)
		assertEquals(
			ReaderBridgeCommand.NextPage,
			readerTapZonePageTurnCommand(ReaderTapZoneAction.Right, ReaderDirectionLtr)
		)
		assertEquals(
			ReaderBridgeCommand.NextPage,
			readerTapZonePageTurnCommand(ReaderTapZoneAction.Left, ReaderDirectionRtl)
		)
		assertEquals(
			ReaderBridgeCommand.PreviousPage,
			readerTapZonePageTurnCommand(ReaderTapZoneAction.Right, ReaderDirectionRtl)
		)
		assertEquals(null, readerTapZonePageTurnCommand(ReaderTapZoneAction.Menu, ReaderDirectionLtr))
	}

	@Test
	fun nativeTapOverlayDragCommandsRespectReadingDirection() {
		assertEquals(
			ReaderBridgeCommand.NextPage,
			readerTapZoneDragPageTurnCommand(
				deltaX = -96f,
				deltaY = 12f,
				direction = ReaderDirectionLtr,
				thresholdPx = 48f
			)
		)
		assertEquals(
			ReaderBridgeCommand.PreviousPage,
			readerTapZoneDragPageTurnCommand(
				deltaX = 96f,
				deltaY = 12f,
				direction = ReaderDirectionLtr,
				thresholdPx = 48f
			)
		)
		assertEquals(
			ReaderBridgeCommand.PreviousPage,
			readerTapZoneDragPageTurnCommand(
				deltaX = -96f,
				deltaY = 12f,
				direction = ReaderDirectionRtl,
				thresholdPx = 48f
			)
		)
		assertEquals(
			null,
			readerTapZoneDragPageTurnCommand(
				deltaX = -24f,
				deltaY = 4f,
				direction = ReaderDirectionLtr,
				thresholdPx = 48f
			)
		)
		assertEquals(
			null,
			readerTapZoneDragPageTurnCommand(
				deltaX = -96f,
				deltaY = 128f,
				direction = ReaderDirectionLtr,
				thresholdPx = 48f
			)
		)
	}

	@Test
	fun nativeShellCoverBoundaryInterceptsPreviousOnlyFromFirstReadablePage() {
		assertTrue(
			readerShouldReturnToNativeShellCover(
				shellCoverUrl = "https://appassets.androidplatform.net/reader-cache/cover.png",
				shellCoverVisible = false,
				locator = ReaderLocator(pageIndex = 0, pageCount = 411)
			)
		)
		assertFalse(
			readerShouldReturnToNativeShellCover(
				shellCoverUrl = "https://appassets.androidplatform.net/reader-cache/cover.png",
				shellCoverVisible = false,
				locator = ReaderLocator(pageIndex = 4, pageCount = 411)
			)
		)
		assertFalse(
			readerShouldReturnToNativeShellCover(
				shellCoverUrl = null,
				shellCoverVisible = false,
				locator = ReaderLocator(pageIndex = 0, pageCount = 411)
			)
		)
		assertFalse(
			readerShouldReturnToNativeShellCover(
				shellCoverUrl = "https://appassets.androidplatform.net/reader-cache/cover.png",
				shellCoverVisible = true,
				locator = ReaderLocator(pageIndex = 0, pageCount = 411)
			)
		)
	}

	@Test
	fun fullscreenControlDefaultsToKomikkuStyleImmersiveReading() {
		val initial = ReaderChromeState()
		val updated = initial.toggleFullscreen()

		assertEquals(true, initial.settings.fullscreen)
		assertEquals(false, updated.settings.fullscreen)
		assertEquals(true, updated.toggleFullscreen().settings.fullscreen)
	}

	@Test
	fun readaloudChromeOnlyShowsForMediaOverlayReadaloudAndTogglesPlaybackIntent() {
		assertTrue(readerReadaloudControlsVisible(ReaderPublicationKind.Readaloud, mediaOverlayEnabled = true))
		assertFalse(readerReadaloudControlsVisible(ReaderPublicationKind.Ebook, mediaOverlayEnabled = true))
		assertFalse(readerReadaloudControlsVisible(ReaderPublicationKind.Readaloud, mediaOverlayEnabled = false))

		assertEquals(
			ReaderReadaloudPlaybackCommand.Play,
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = false,
				activeAudioLabel = "Chapter 1 / Paragraph 1"
			).toggleCommand()
		)
		assertEquals(
			"Chapter 1 / Paragraph 1",
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				activeAudioLabel = "Chapter 1 / Paragraph 1",
				activeAudioMetadata = ReadaloudPlaybackMetadataLabels(
					chapterLabel = "Chapter 1",
					sectionLabel = "Opening",
					narratorLabel = "Michael Kramer",
					qualityLabel = "High",
					sourceProviderLabel = "Audible"
				)
			).activeAudioLabel
		)
		assertEquals(
			"Michael Kramer",
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				activeAudioMetadata = ReadaloudPlaybackMetadataLabels(narratorLabel = "Michael Kramer")
			).activeAudioMetadata?.narratorLabel
		)
		assertEquals(
			ReaderReadaloudPlaybackCommand.Pause,
			ReaderReadaloudPlaybackUiState(isAvailable = true, isPlaying = true).toggleCommand()
		)
		assertEquals(null, ReaderReadaloudPlaybackUiState(isAvailable = false).toggleCommand())
		assertEquals(
			ReaderReadaloudPlaybackCommand.SetSyncEnabled(false),
			ReaderReadaloudPlaybackUiState(isAvailable = true, syncEnabled = true).toggleSyncCommand()
		)
		assertEquals(
			ReaderReadaloudPlaybackCommand.SetSyncEnabled(true),
			ReaderReadaloudPlaybackUiState(isAvailable = true, syncEnabled = false).toggleSyncCommand()
		)
		assertEquals(null, ReaderReadaloudPlaybackUiState(isAvailable = false).toggleSyncCommand())
	}

	@Test
	fun readaloudPlaybackSpeedControlsClampAndDispatchSpeedIntent() {
		assertEquals(
			ReaderReadaloudPlaybackCommand.SetSpeed(1.25f),
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				playbackSpeed = 1f
			).adjustSpeedCommand(0.25f)
		)
		assertEquals(
			ReaderReadaloudPlaybackCommand.SetSpeed(3f),
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				playbackSpeed = 2.9f
			).adjustSpeedCommand(0.25f)
		)
		assertEquals(
			ReaderReadaloudPlaybackCommand.SetSpeed(0.5f),
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				playbackSpeed = 0.6f
			).adjustSpeedCommand(-0.25f)
		)
		assertEquals(
			null,
			ReaderReadaloudPlaybackUiState(
				isAvailable = false,
				playbackSpeed = 1f
			).adjustSpeedCommand(0.25f)
		)
	}

	@Test
	fun readerOptionsPanelStateExposesKomikkuStyleControlGroups() {
		assertEquals(
			listOf(ReaderOptionsTab.Reading, ReaderOptionsTab.General),
			readerOptionsTabs(showReadaloudControls = false)
		)
		assertEquals(
			listOf(ReaderOptionsTab.Reading, ReaderOptionsTab.General, ReaderOptionsTab.Media),
			readerOptionsTabs(showReadaloudControls = true)
		)
		assertEquals(
			ReaderOptionsTab.Reading,
			normalizedReaderOptionsTab(ReaderOptionsTab.Media, showReadaloudControls = false)
		)

		val updated = ReaderChromeState()
			.adjustParagraphSpacing(50)
			.adjustDimOverlay(20)
			.togglePublisherStyles()
			.toggleKeepScreenOn()
			.toggleVolumeKeyPageTurns()
			.toggleTapZone()
			.toggleSmallerTapZone()
			.toggleOrientation()

		assertEquals(150, updated.settings.paragraphSpacingPercent)
		assertEquals(20, updated.settings.dimOverlayPercent)
		assertEquals(true, updated.settings.publisherStyles)
		assertEquals(true, updated.settings.keepScreenOn)
		assertEquals(true, updated.settings.volumeKeyPageTurns)
		assertEquals(ReaderTapZoneEdge, updated.settings.tapZone)
		assertEquals(true, updated.settings.smallerTapZone)
		assertEquals(ReaderOrientationFree, updated.settings.orientation)
		assertEquals("Edge", readerTapZoneShortLabel(updated.settings.tapZone))
		assertEquals("Free", readerOrientationShortLabel(updated.settings.orientation))
	}

	@Test
	fun pdfReaderOptionsUseDedicatedPdfImageTabAndSettings() {
		assertEquals(
			listOf(ReaderOptionsTab.Reading, ReaderOptionsTab.General, ReaderOptionsTab.PdfImage),
			readerOptionsTabs(
				showReadaloudControls = false,
				publicationFormat = ReaderPublicationFormat.Pdf
			)
		)
		assertEquals(
			ReaderOptionsTab.Reading,
			normalizedReaderOptionsTab(
				tab = ReaderOptionsTab.Media,
				showReadaloudControls = true,
				publicationFormat = ReaderPublicationFormat.Pdf
			)
		)
		assertEquals(
			ReaderOptionsTab.PdfImage,
			normalizedReaderOptionsTab(
				tab = ReaderOptionsTab.PdfImage,
				showReadaloudControls = false,
				publicationFormat = ReaderPublicationFormat.Pdf
			)
		)
		assertEquals("PDF/Image", readerOptionsTabLabel(ReaderOptionsTab.PdfImage))
		assertEquals(ReaderPdfFitWidth, defaultReaderSettings().pdfFitMode)

		val updated = ReaderChromeState()
			.setPdfFitMode(ReaderPdfFitPage)
			.adjustPdfPageGap(12)
			.togglePdfCropBorders()

		assertEquals(ReaderPdfFitPage, updated.settings.pdfFitMode)
		assertEquals(12, updated.settings.pdfPageGapPercent)
		assertEquals(true, updated.settings.pdfCropBorders)
		assertEquals("Page", readerPdfFitShortLabel(updated.settings.pdfFitMode))
	}
}
