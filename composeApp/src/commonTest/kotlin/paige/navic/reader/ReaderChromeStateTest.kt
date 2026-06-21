package paige.navic.reader

import paige.navic.ui.screens.reader.KomikkuNavigationRegion
import paige.navic.ui.screens.reader.KomikkuPoint
import paige.navic.ui.screens.reader.KomikkuRightAndLeftNavigation
import paige.navic.ui.screens.reader.KomikkuTappingInvertMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderChromeStateTest {
	@Test
	fun locationEventsDriveProgressAndCurrentSectionLabels() {
		val state = ReaderChromeState().onLocationChanged(
			locator = ReaderLocator(
				href = "chapter-03.xhtml",
				cfi = "epubcfi(/6/8!/4/1:0)",
				progress = 0.342
			),
			tocTitle = "Chapter 3"
		)

		assertEquals("Chapter 3", state.currentSectionTitle)
		assertEquals("epubcfi(/6/8!/4/1:0)", state.currentLocator?.cfi)
		assertEquals(0.342f, state.progressFraction)
		assertEquals("34%", state.progressLabel)
	}

	@Test
	fun progressLabelsClampInvalidReaderFractions() {
		val overComplete = ReaderChromeState().onLocationChanged(
			locator = ReaderLocator(progress = 1.4),
			tocTitle = null
		)
		val beforeStart = ReaderChromeState().onLocationChanged(
			locator = ReaderLocator(progress = -0.4),
			tocTitle = null
		)

		assertEquals(1f, overComplete.progressFraction)
		assertEquals("100%", overComplete.progressLabel)
		assertEquals(0f, beforeStart.progressFraction)
		assertEquals("0%", beforeStart.progressLabel)
	}

	@Test
	fun fixedLayoutLocationsUsePageAwareProgressLabels() {
		val state = ReaderChromeState().onLocationChanged(
			locator = ReaderLocator(
				progress = 0.05,
				pageIndex = 6,
				pageCount = 120
			),
			tocTitle = null
		)

		assertEquals(0.05f, state.progressFraction)
		assertEquals("Page 7 of 120 • 5%", state.progressLabel)
	}

	@Test
	fun typographyControlsUpdateReaderSettings() {
		val larger = ReaderChromeState().adjustFontSize(12)
		val sepia = larger.toggleTheme()
		val scrolled = sepia.togglePagedMode()
		val serif = scrolled.toggleFontFamily()
		val taller = serif.adjustLineHeight(0.1)
		val wider = taller.adjustMargin(8)

		assertEquals(DefaultReaderFontSizePercent + 12, larger.settings.fontSizePercent)
		assertEquals(ReaderSepiaTheme, sepia.settings.theme)
		assertFalse(scrolled.settings.paged ?: true)
		assertEquals("Georgia, serif", serif.settings.fontFamily)
		assertEquals(1.9, taller.settings.lineHeight)
		assertEquals(8, wider.settings.marginPercent)
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
		assertEquals(0.33f, readerTapZoneSize(smallerTapZone = false))
		assertEquals(0.25f, readerTapZoneSize(smallerTapZone = true))
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
	fun nativeTapOverlayRegionsAreVisualOnlyKomikkuNavigationRegions() {
		val regions = readerTapZoneInteractiveRegions(
			tapZone = ReaderTapZoneDefault,
			smallerTapZone = false,
			flowMode = ReaderFlowPaged
		)

		assertEquals(2, regions.size)
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
			"Visual tap-zone overlay must leave fallback menu areas unpainted, matching Komikku's diagnostic overlay."
		)
		assertFalse(
			regions.any { region -> region.action == ReaderTapZoneAction.Menu },
			"Visible tap-zone overlay must not model menu fallback as an input authority."
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
		assertEquals(
			ReaderTapZoneAction.Menu,
			readerTapZoneActionAt(
				tapZone = ReaderTapZoneDefault,
				xFraction = 0.5f,
				yFraction = 0.5f,
				flowMode = ReaderFlowScrolled
			)
		)
	}

	@Test
	fun nativeTapZonesUseKomikkuRegionPriorityBeforeMenuFallback() {
		assertEquals(
			ReaderTapZoneAction.Previous,
			readerTapZoneActionAt(
				tapZone = ReaderTapZoneLShaped,
				xFraction = 0.5f,
				yFraction = 0.02f,
				flowMode = ReaderFlowScrolled
			)
		)
		assertEquals(
			ReaderTapZoneAction.Menu,
			readerTapZoneActionAt(
				tapZone = ReaderTapZoneKindle,
				xFraction = 0.5f,
				yFraction = 0.02f,
				flowMode = ReaderFlowPaged
			)
		)
		assertEquals(
			ReaderTapZoneAction.Next,
			readerTapZoneActionAt(
				tapZone = ReaderTapZoneEdge,
				xFraction = 0.05f,
				yFraction = 0.5f,
				flowMode = ReaderFlowPaged
			)
		)
		assertEquals(
			ReaderTapZoneAction.Previous,
			readerTapZoneActionAt(
				tapZone = ReaderTapZoneEdge,
				xFraction = 0.5f,
				yFraction = 0.9f,
				flowMode = ReaderFlowPaged
			)
		)
	}

	@Test
	fun nativeTapZoneDirectionsRespectReadingDirection() {
		assertEquals(
			ReaderPageTurnDirection.Previous,
			readerTapZonePageTurnDirectionFor(ReaderTapZoneAction.Left, ReaderDirectionLtr)
		)
		assertEquals(
			ReaderPageTurnDirection.Next,
			readerTapZonePageTurnDirectionFor(ReaderTapZoneAction.Right, ReaderDirectionLtr)
		)
		assertEquals(
			ReaderPageTurnDirection.Next,
			readerTapZonePageTurnDirectionFor(ReaderTapZoneAction.Left, ReaderDirectionRtl)
		)
		assertEquals(
			ReaderPageTurnDirection.Previous,
			readerTapZonePageTurnDirectionFor(ReaderTapZoneAction.Right, ReaderDirectionRtl)
		)
		assertEquals(null, readerTapZonePageTurnDirectionFor(ReaderTapZoneAction.Menu, ReaderDirectionLtr))
	}

	@Test
	fun portedKomikkuNavigationMirrorsTapRegionsWhenInverted() {
		val navigation = KomikkuRightAndLeftNavigation()

		assertEquals(KomikkuNavigationRegion.LEFT, navigation.getAction(KomikkuPoint(0.05f, 0.5f)))
		assertEquals(KomikkuNavigationRegion.RIGHT, navigation.getAction(KomikkuPoint(0.95f, 0.5f)))

		navigation.invertMode = KomikkuTappingInvertMode.HORIZONTAL

		assertEquals(KomikkuNavigationRegion.RIGHT, navigation.getAction(KomikkuPoint(0.05f, 0.5f)))
		assertEquals(KomikkuNavigationRegion.LEFT, navigation.getAction(KomikkuPoint(0.95f, 0.5f)))
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
		assertTrue(
			readerShouldReturnToNativeShellCover(
				shellCoverUrl = "https://appassets.androidplatform.net/reader-cache/cover.png",
				shellCoverVisible = false,
				locator = ReaderLocator(pageIndex = 1, pageCount = 411)
			),
			"Foliate can report the first readable page after cover suppression as page index 1."
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
	fun nativeShellCoverBoundaryDoesNotTreatLaterChapterFirstPageAsBookStart() {
		assertFalse(
			readerShouldReturnToNativeShellCover(
				shellCoverUrl = "https://appassets.androidplatform.net/reader-cache/cover.png",
				shellCoverVisible = false,
				locator = ReaderLocator(
					href = "OEBPS/Text/Hobbit_chap-14.html",
					progress = 0.8032493311658307,
					pageIndex = 0,
					pageCount = 1748,
					chapterProgress = 0.0,
					chapterPageIndex = 0,
					chapterPageCount = 14
				)
			),
			"Chapter-local page zero is not the native cover boundary when global progress is deep into the book."
		)
	}

	@Test
	fun nativeShellCoverBoundaryAllowsFrontmatterWhenGlobalPageIndexIsAlreadyPastOne() {
		assertTrue(
			readerShouldReturnToNativeShellCover(
				shellCoverUrl = "https://appassets.androidplatform.net/reader-cache/cover.png",
				shellCoverVisible = false,
				locator = ReaderLocator(
					href = "OEBPS/Text/sinopsis.xhtml",
					progress = 0.0007927082115140601,
					pageIndex = 10,
					pageCount = 1534,
					chapterProgress = 0.0,
					chapterPageIndex = 0,
					chapterPageCount = 2
				)
			),
			"Frontmatter can have several global pages before the first readable chapter; previous must still return to native cover."
		)
	}

	@Test
	fun nativeShellCoverBoundaryDoesNotTrustLocalPageZeroWhenHrefIsLaterChapter() {
		assertFalse(
			readerShouldReturnToNativeShellCover(
				shellCoverUrl = "https://appassets.androidplatform.net/reader-cache/cover.png",
				shellCoverVisible = false,
				locator = ReaderLocator(
					href = "OEBPS/Text/Hobbit_chap-14.html",
					pageIndex = 0,
					pageCount = 1748,
					chapterProgress = 0.0,
					chapterPageIndex = 0,
					chapterPageCount = 14
				)
			),
			"Later chapter-local page zero must not jump to native cover when the bridge omits global progress."
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

	@Test
	fun nativeReaderSwipeActionRequiresHorizontalDominanceOutsideShellCover() {
		assertEquals(null, readerNativeReaderSwipeAction(deltaX = -9f, deltaY = 1f, thresholdPx = 10f))
		assertEquals(ReaderTapZoneAction.Right, readerNativeReaderSwipeAction(deltaX = -24f, deltaY = 8f, thresholdPx = 10f))
		assertEquals(ReaderTapZoneAction.Left, readerNativeReaderSwipeAction(deltaX = 24f, deltaY = 8f, thresholdPx = 10f))
		assertEquals(
			null,
			readerNativeReaderSwipeAction(deltaX = -24f, deltaY = 30f, thresholdPx = 10f),
			"Readable EPUB/PDF swipes must not convert mostly vertical scroll or drift into page turns."
		)
		assertEquals(
			ReaderTapZoneAction.Right,
			readerShellCoverSwipeAction(deltaX = -24f, deltaY = 30f, thresholdPx = 10f),
			"Shell-cover drags can stay permissive because there is no readable scroll stream under the cover."
		)
	}

	@Test
	fun nativeReaderSwipeActionUsesVerticalDominanceForPagedVerticalPreview() {
		assertEquals(
			null,
			readerNativeReaderSwipeAction(
				deltaX = 2f,
				deltaY = -9f,
				thresholdPx = 10f,
				verticalPageDragPreview = true
			)
		)
		assertEquals(
			ReaderTapZoneAction.Right,
			readerNativeReaderSwipeAction(
				deltaX = 8f,
				deltaY = -24f,
				thresholdPx = 10f,
				verticalPageDragPreview = true
			),
			"Dragging up in paged-vertical mode should preview and commit the next page."
		)
		assertEquals(
			ReaderTapZoneAction.Left,
			readerNativeReaderSwipeAction(
				deltaX = 8f,
				deltaY = 24f,
				thresholdPx = 10f,
				verticalPageDragPreview = true
			),
			"Dragging down in paged-vertical mode should preview and commit the previous page."
		)
		assertEquals(
			null,
			readerNativeReaderSwipeAction(
				deltaX = 30f,
				deltaY = -24f,
				thresholdPx = 10f,
				verticalPageDragPreview = true
			),
			"Paged-vertical reader drags must not convert mostly horizontal drift into page turns."
		)
	}
}
