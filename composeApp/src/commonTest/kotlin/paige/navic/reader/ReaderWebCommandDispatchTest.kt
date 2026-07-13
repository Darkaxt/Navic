package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderWebCommandDispatchTest {
	private val firstOpen = ReaderBridgeCommand.OpenPublication(
		url = "https://bindery.local/books/1.epub",
		startLocator = ReaderLocator(cfi = "epubcfi(/6/2!/4/1:0)")
	)

	@Test
	fun readerCommandsDispatchOnlyAfterEntrypointRuntimeIsReady() {
		val entrypoint = "file:///android_asset/reader/index.html"

		assertFalse(shouldDispatchReaderCommandsToWebRuntime(runtimeReady = false, currentUrl = entrypoint, entrypointUrl = entrypoint))
		assertFalse(shouldDispatchReaderCommandsToWebRuntime(runtimeReady = true, currentUrl = "about:blank", entrypointUrl = entrypoint))
		assertTrue(shouldDispatchReaderCommandsToWebRuntime(runtimeReady = true, currentUrl = entrypoint, entrypointUrl = entrypoint))
	}

	@Test
	fun firstReadyDispatchesStableOpenAndCurrentCommandIdsInOrder() {
		val locator = ReaderBridgeCommand.GoToCfi("epubcfi(/6/4!/4/1:0)")

		val first = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = locator,
			commandKey = 7L
		)

		assertEquals(listOf("reader-open-1", "reader-command-1-7"), first.commands.map { it.id })
		assertEquals(listOf(firstOpen, locator), first.commands.map { it.command })
		assertEquals(first.commands, first.state.pendingCommands.map { it.dispatch })
		assertEquals(listOf(0, 0), first.state.pendingCommands.map { it.lastDispatchedGeneration })
	}

	@Test
	fun duplicateReadyInSameGenerationDoesNotExecutePendingCommandsTwice() {
		val command = ReaderBridgeCommand.NextPage
		val first = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = command,
			commandKey = 1L
		)

		val duplicate = first.state.commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = command,
			commandKey = 1L
		)

		assertEquals(emptyList(), duplicate.commands)
		assertEquals(first.state, duplicate.state)
		assertEquals(2, duplicate.state.pendingCommands.size)
	}

	@Test
	fun acknowledgementRemovesOnlyMatchingPendingCommandAndIsIdempotent() {
		val first = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = ReaderBridgeCommand.ClearOverlay,
			commandKey = 3L
		)

		val openAcknowledged = first.state.acknowledge("reader-open-1")
		assertEquals(listOf("reader-command-1-3"), openAcknowledged.pendingCommands.map { it.dispatch.id })
		assertEquals(openAcknowledged, openAcknowledged.acknowledge("reader-open-1"))
		assertEquals(openAcknowledged, openAcknowledged.acknowledge("unknown-command"))

		val allAcknowledged = openAcknowledged.acknowledge("reader-command-1-3")
		assertEquals(emptyList(), allAcknowledged.pendingCommands)
		assertEquals(3L, allAcknowledged.lastCommandKey)
	}

	@Test
	fun newerCommandAppendsAndDispatchesWithoutReopeningCurrentGeneration() {
		val first = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = ReaderBridgeCommand.NextPage,
			commandKey = 1L
		)
		val acknowledged = first.commands.fold(first.state) { state, dispatch ->
			state.acknowledge(dispatch.id)
		}

		val next = acknowledged.commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = ReaderBridgeCommand.PreviousPage,
			commandKey = 2L
		)

		assertEquals(listOf("reader-command-1-2"), next.commands.map { it.id })
		assertEquals(listOf(ReaderBridgeCommand.PreviousPage), next.commands.map { it.command })
	}

	@Test
	fun rendererGenerationReplaysOpenThenLatestCommandWithSameStableIds() {
		val latestLocator = ReaderBridgeCommand.GoToProgress(0.72)
		val first = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = latestLocator,
			commandKey = 8L
		)
		val acknowledged = first.commands.fold(first.state) { state, dispatch ->
			state.acknowledge(dispatch.id)
		}

		val replay = acknowledged.commandsForReadyReaderRuntime(
			runtimeGeneration = 1,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = latestLocator,
			commandKey = 8L
		)

		assertEquals(first.commands, replay.commands)
		assertEquals(listOf(firstOpen, latestLocator), replay.commands.map { it.command })
		val duplicateReady = replay.state.commandsForReadyReaderRuntime(
			runtimeGeneration = 1,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = latestLocator,
			commandKey = 8L
		)
		assertEquals(emptyList(), duplicateReady.commands)
	}

	@Test
	fun rendererGenerationDropsSupersededPendingCommandsAndReplaysLatestIntent() {
		val first = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = ReaderBridgeCommand.NextPage,
			commandKey = 1L
		)
		val newer = first.state.commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = ReaderBridgeCommand.GoToProgress(0.64),
			commandKey = 2L
		)

		val replay = newer.state.commandsForReadyReaderRuntime(
			runtimeGeneration = 1,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = ReaderBridgeCommand.GoToProgress(0.64),
			commandKey = 2L
		)

		assertEquals(listOf("reader-open-1", "reader-command-1-2"), replay.commands.map { it.id })
		assertEquals(listOf(firstOpen, ReaderBridgeCommand.GoToProgress(0.64)), replay.commands.map { it.command })
		assertEquals(2, replay.state.pendingCommands.size)
	}

	@Test
	fun publicationChangeReplacesLedgerAndOrdersNewOpenBeforeMatchingCommandKey() {
		val active = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = ReaderBridgeCommand.ClearOverlay,
			commandKey = 4L
		).state
		val secondOpen = ReaderBridgeCommand.OpenPublication(
			url = "https://bindery.local/books/2.epub",
			startLocator = ReaderLocator(progress = 0.3)
		)

		val reopened = active.commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-2",
			openCommand = secondOpen,
			command = ReaderBridgeCommand.ClearOverlay,
			commandKey = 4L
		)

		assertEquals(listOf("reader-open-2", "reader-command-2-4"), reopened.commands.map { it.id })
		assertEquals(listOf(secondOpen, ReaderBridgeCommand.ClearOverlay), reopened.commands.map { it.command })
		assertEquals("book-2", reopened.state.publicationKey)
		assertEquals(2L, reopened.state.publicationSequence)
	}
}
