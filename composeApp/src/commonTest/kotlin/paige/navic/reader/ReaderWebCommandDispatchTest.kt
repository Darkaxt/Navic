package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderWebCommandDispatchTest {
	@Test
	fun runtimeReadyDispatchesOpenCommandAndEachExternalCommandOnlyOnce() {
		val open = ReaderBridgeCommand.OpenPublication(
			url = "https://bindery.local/books/1.epub",
			mediaOverlayEnabled = true
		)
		val overlay = ReaderBridgeCommand.ApplyOverlayFragment(
			ReaderOverlayFragment(
				resourceHref = "EPUB/Audio/chapter1.mp3",
				fragmentId = "frag-1",
				textHref = "EPUB/Text/chapter1.xhtml",
				clipBeginSeconds = 1.25,
				clipEndSeconds = 3.5
			)
		)
		val initial = ReaderWebCommandDispatchState()

		val first = initial.commandsForReadyReaderRuntime(
			publicationKey = "book-1",
			openCommand = open,
			command = overlay,
			commandKey = 1L
		)
		assertEquals(listOf(open, overlay), first.commands)

		val duplicate = first.state.commandsForReadyReaderRuntime(
			publicationKey = "book-1",
			openCommand = open,
			command = overlay,
			commandKey = 1L
		)
		assertEquals(emptyList(), duplicate.commands)

		val clear = duplicate.state.commandsForReadyReaderRuntime(
			publicationKey = "book-1",
			openCommand = open,
			command = ReaderBridgeCommand.ClearOverlay,
			commandKey = 2L
		)
		assertEquals(listOf(ReaderBridgeCommand.ClearOverlay), clear.commands)
	}

	@Test
	fun publicationChangeReopensReaderAndResetsCommandKeyForTheNewRuntime() {
		val firstOpen = ReaderBridgeCommand.OpenPublication(url = "https://bindery.local/books/1.epub")
		val secondOpen = ReaderBridgeCommand.OpenPublication(url = "https://bindery.local/books/2.epub")
		val active = ReaderWebCommandDispatchState()
			.commandsForReadyReaderRuntime(
				publicationKey = "book-1",
				openCommand = firstOpen,
				command = ReaderBridgeCommand.ClearOverlay,
				commandKey = 4L
			)
			.state

		val reopened = active.commandsForReadyReaderRuntime(
			publicationKey = "book-2",
			openCommand = secondOpen,
			command = ReaderBridgeCommand.ClearOverlay,
			commandKey = 4L
		)

		assertEquals(listOf(secondOpen, ReaderBridgeCommand.ClearOverlay), reopened.commands)
	}
}
