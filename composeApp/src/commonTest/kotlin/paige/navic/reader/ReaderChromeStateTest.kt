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
	fun typographyControlsCreateReaderSettingsCommands() {
		val larger = ReaderChromeState().adjustFontSize(12)
		val darker = larger.toggleTheme()
		val scrolled = darker.togglePagedMode()
		val serif = scrolled.toggleFontFamily()
		val taller = serif.adjustLineHeight(0.1)
		val wider = taller.adjustMargin(8)

		assertEquals(112, larger.settings.fontSizePercent)
		assertEquals("dark", darker.settings.theme)
		assertFalse(scrolled.settings.paged ?: true)
		assertEquals("Georgia, serif", serif.settings.fontFamily)
		assertEquals(1.65, taller.settings.lineHeight)
		assertEquals(8, wider.settings.marginPercent)
		assertIs<ReaderBridgeCommand.ApplySettings>(wider.toSettingsCommand())
		assertEquals(wider.settings, wider.toSettingsCommand().settings)
	}

	@Test
	fun readaloudChromeOnlyShowsForMediaOverlayReadaloudAndTogglesPlaybackIntent() {
		assertTrue(readerReadaloudControlsVisible(ReaderPublicationKind.Readaloud, mediaOverlayEnabled = true))
		assertFalse(readerReadaloudControlsVisible(ReaderPublicationKind.Ebook, mediaOverlayEnabled = true))
		assertFalse(readerReadaloudControlsVisible(ReaderPublicationKind.Readaloud, mediaOverlayEnabled = false))

		assertEquals(
			ReaderReadaloudPlaybackCommand.Play,
			ReaderReadaloudPlaybackUiState(isAvailable = true, isPlaying = false).toggleCommand()
		)
		assertEquals(
			ReaderReadaloudPlaybackCommand.Pause,
			ReaderReadaloudPlaybackUiState(isAvailable = true, isPlaying = true).toggleCommand()
		)
		assertEquals(null, ReaderReadaloudPlaybackUiState(isAvailable = false).toggleCommand())
	}
}
