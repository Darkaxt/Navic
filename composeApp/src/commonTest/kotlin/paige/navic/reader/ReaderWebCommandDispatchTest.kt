package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderWebCommandDispatchTest {
	private val firstOpen = ReaderBridgeCommand.OpenPublication(
		url = "https://bindery.local/books/1.epub",
		foliateSessionId = ReaderUnboundFoliateSessionId,
		startLocator = ReaderLocator(cfi = "epubcfi(/6/2!/4/1:0)")
	)

	private val descriptor = ReaderRawTextProvenanceDescriptor(
		id = "wordsync-v1-spine-2",
		href = "chapter-2.xhtml",
		spineIndex = 2,
		sourceHash = "sha256:${"a".repeat(64)}",
		extractedTextHash = "sha256:${"b".repeat(64)}",
		byteLength = 12,
		tokenCount = 3
	)

	@Test
	fun runtimeSessionIsMonotonicAcrossRuntimeAndPublicationChanges() {
		val initial = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = null,
			commandKey = 0L
		)
		val initialOpen = initial.commands.single().command as ReaderBridgeCommand.OpenPublication
		assertEquals("foliate-1-0", initialOpen.foliateSessionId)
		assertEquals("foliate-1-0", initial.state.foliateSessionId)

		val recreated = initial.state.commandsForReadyReaderRuntime(
			runtimeGeneration = 1,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = null,
			commandKey = 0L
		)
		val recreatedOpen = recreated.commands.single().command as ReaderBridgeCommand.OpenPublication
		assertEquals("foliate-1-1", recreatedOpen.foliateSessionId)
		assertEquals("foliate-1-1", recreated.state.foliateSessionId)

		val reopened = recreated.state.commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-2",
			openCommand = firstOpen,
			command = null,
			commandKey = 0L
		)
		val reopenedCommand = reopened.commands.single().command as ReaderBridgeCommand.OpenPublication
		assertEquals("foliate-2-0", reopenedCommand.foliateSessionId)
		assertEquals("foliate-2-0", reopened.state.foliateSessionId)
	}

	@Test
	fun readerCommandsDispatchOnlyAfterEntrypointRuntimeIsReady() {
		val entrypoint = "file:///android_asset/reader/index.html"

		assertFalse(shouldDispatchReaderCommandsToWebRuntime(runtimeReady = false, currentUrl = entrypoint, entrypointUrl = entrypoint))
		assertFalse(shouldDispatchReaderCommandsToWebRuntime(runtimeReady = true, currentUrl = "about:blank", entrypointUrl = entrypoint))
		assertTrue(shouldDispatchReaderCommandsToWebRuntime(runtimeReady = true, currentUrl = entrypoint, entrypointUrl = entrypoint))
	}

	@Test
	fun firstReadyDispatchesOpenAndQueuesCurrentCommandBehindItsAcknowledgement() {
		val locator = ReaderBridgeCommand.GoToCfi("epubcfi(/6/4!/4/1:0)")

		val first = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = locator,
			commandKey = 7L
		)

		assertEquals(listOf("reader-open-1"), first.commands.map { it.id })
		assertEquals(
			listOf(firstOpen.copy(foliateSessionId = "foliate-1-0")),
			first.commands.map { it.command }
		)
		assertEquals(
			listOf("reader-open-1", "reader-command-1-7"),
			first.state.pendingCommands.map { it.dispatch.id }
		)
		assertEquals(listOf(0, null), first.state.pendingCommands.map { it.lastDispatchedGeneration })
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

		val commandStep = openAcknowledged.commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = ReaderBridgeCommand.ClearOverlay,
			commandKey = 3L
		)
		assertEquals(listOf("reader-command-1-3"), commandStep.commands.map { it.id })
		val allAcknowledged = commandStep.state.acknowledge("reader-command-1-3")
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
		val commandStep = first.state
			.acknowledge("reader-open-1")
			.commandsForReadyReaderRuntime(
				runtimeGeneration = 0,
				publicationKey = "book-1",
				openCommand = firstOpen,
				command = ReaderBridgeCommand.NextPage,
				commandKey = 1L
			)
		val acknowledged = commandStep.state.acknowledge("reader-command-1-1")

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
	fun observedLocatorIsRetainedWithoutChangingPendingCommandState() {
		val active = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = null,
			commandKey = 0L
		).state
		val currentLocator = ReaderLocator(
			cfi = "epubcfi(/6/12!/4/1:0)",
			href = "chapter-5.xhtml",
			progress = 0.72
		)

		val observed = active.observeLocator(currentLocator)

		assertEquals(currentLocator, observed.lastKnownLocator)
		assertEquals(active.pendingCommands, observed.pendingCommands)
	}

	@Test
	fun rendererGenerationReopensAtLatestLocatorWithoutReplayingAcknowledgedCommand() {
		val latestLocator = ReaderBridgeCommand.GoToProgress(0.72)
		val first = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = latestLocator,
			commandKey = 8L
		)
		val firstCommand = first.state
			.acknowledge("reader-open-1")
			.commandsForReadyReaderRuntime(
				runtimeGeneration = 0,
				publicationKey = "book-1",
				openCommand = firstOpen,
				command = latestLocator,
				commandKey = 8L
			)
		val observedLocator = ReaderLocator(
			cfi = "epubcfi(/6/12!/4/1:0)",
			href = "chapter-5.xhtml",
			progress = 0.72
		)
		val acknowledged = firstCommand.state
			.acknowledge("reader-command-1-8")
			.observeLocator(observedLocator)

		val replay = acknowledged.commandsForReadyReaderRuntime(
			runtimeGeneration = 1,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = latestLocator,
			commandKey = 8L
		)

		assertEquals(listOf("reader-open-1"), replay.commands.map { it.id })
		assertEquals(
			observedLocator,
			(replay.commands.single().command as ReaderBridgeCommand.OpenPublication).startLocator
		)
		val openAcknowledged = replay.state.acknowledge("reader-open-1")
		val duplicateReady = openAcknowledged.commandsForReadyReaderRuntime(
			runtimeGeneration = 1,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = latestLocator,
			commandKey = 8L
		)
		assertEquals(emptyList(), duplicateReady.commands)
	}

	@Test
	fun durableProvenanceQueuesBeforeACommandThatReplacedItsViewState() {
		val first = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = ReaderBridgeCommand.ClearOverlay,
			commandKey = 9L,
			rawTextProvenanceDescriptors = listOf(descriptor)
		)

		assertEquals(
			listOf("reader-open-1", "reader-provenance-1-1", "reader-command-1-9"),
			first.state.pendingCommands.map { it.dispatch.id }
		)
		val provenance = first.state
			.acknowledge("reader-open-1")
			.commandsForReadyReaderRuntime(
				runtimeGeneration = 0,
				publicationKey = "book-1",
				openCommand = firstOpen,
				command = ReaderBridgeCommand.ClearOverlay,
				commandKey = 9L,
				rawTextProvenanceDescriptors = listOf(descriptor)
			)
		assertEquals(
			listOf(ReaderBridgeCommand.InstallRawTextProvenance(descriptor)),
			provenance.commands.map { it.command }
		)
	}

	@Test
	fun acknowledgedProvenanceReplaysAfterRuntimeRecreation() {
		val initial = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = null,
			commandKey = 0L,
			rawTextProvenanceDescriptors = listOf(descriptor)
		)
		val provenance = initial.state
			.acknowledge("reader-open-1")
			.commandsForReadyReaderRuntime(
				runtimeGeneration = 0,
				publicationKey = "book-1",
				openCommand = firstOpen,
				command = null,
				commandKey = 0L,
				rawTextProvenanceDescriptors = listOf(descriptor)
			)
		val acknowledged = provenance.state.acknowledge(provenance.commands.single().id)

		val recreated = acknowledged.commandsForReadyReaderRuntime(
			runtimeGeneration = 1,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = null,
			commandKey = 0L,
			rawTextProvenanceDescriptors = listOf(descriptor)
		)

		assertEquals(
			listOf("reader-open-1", "reader-provenance-1-2"),
			recreated.state.pendingCommands.map { it.dispatch.id }
		)
	}

	@Test
	fun replacementProvenanceWithSameIdQueuesANewVerifiedAttempt() {
		val initial = ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = null,
			commandKey = 0L,
			rawTextProvenanceDescriptors = listOf(descriptor)
		)
		val provenance = initial.state
			.acknowledge("reader-open-1")
			.commandsForReadyReaderRuntime(
				runtimeGeneration = 0,
				publicationKey = "book-1",
				openCommand = firstOpen,
				command = null,
				commandKey = 0L,
				rawTextProvenanceDescriptors = listOf(descriptor)
			)
		val acknowledged = provenance.state.acknowledge(provenance.commands.single().id)
		val replacement = descriptor.copy(extractedTextHash = "sha256:${"c".repeat(64)}")

		val replaced = acknowledged.commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = null,
			commandKey = 0L,
			rawTextProvenanceDescriptors = listOf(replacement)
		)

		assertEquals(listOf("reader-provenance-1-2"), replaced.commands.map { it.id })
		assertEquals(
			listOf(ReaderBridgeCommand.InstallRawTextProvenance(replacement)),
			replaced.commands.map { it.command }
		)
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

		val observedLocator = ReaderLocator(cfi = "epubcfi(/6/10!/4/1:0)", progress = 0.6)
		val replay = newer.state.observeLocator(observedLocator).commandsForReadyReaderRuntime(
			runtimeGeneration = 1,
			publicationKey = "book-1",
			openCommand = firstOpen,
			command = ReaderBridgeCommand.GoToProgress(0.64),
			commandKey = 2L
		)

		assertEquals(listOf("reader-open-1"), replay.commands.map { it.id })
		assertEquals(
			observedLocator,
			(replay.commands.single().command as ReaderBridgeCommand.OpenPublication).startLocator
		)
		assertEquals(2, replay.state.pendingCommands.size)
		val replayLatest = replay.state
			.acknowledge("reader-open-1")
			.commandsForReadyReaderRuntime(
				runtimeGeneration = 1,
				publicationKey = "book-1",
				openCommand = firstOpen,
				command = ReaderBridgeCommand.GoToProgress(0.64),
				commandKey = 2L
			)
		assertEquals(listOf("reader-command-1-2"), replayLatest.commands.map { it.id })
		assertEquals(listOf(ReaderBridgeCommand.GoToProgress(0.64)), replayLatest.commands.map { it.command })
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
			foliateSessionId = ReaderUnboundFoliateSessionId,
			startLocator = ReaderLocator(progress = 0.3)
		)

		val reopened = active.commandsForReadyReaderRuntime(
			runtimeGeneration = 0,
			publicationKey = "book-2",
			openCommand = secondOpen,
			command = ReaderBridgeCommand.ClearOverlay,
			commandKey = 4L
		)

		assertEquals(listOf("reader-open-2"), reopened.commands.map { it.id })
		assertEquals(
			listOf(secondOpen.copy(foliateSessionId = "foliate-2-0")),
			reopened.commands.map { it.command }
		)
		assertEquals(
			listOf("reader-open-2", "reader-command-2-4"),
			reopened.state.pendingCommands.map { it.dispatch.id }
		)
		assertEquals("book-2", reopened.state.publicationKey)
		assertEquals(2L, reopened.state.publicationSequence)
	}
}
