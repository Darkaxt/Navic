package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderEngineCapabilityTest {
	@Test
	fun publicationFormatsExposeExplicitSearchAndMediaOverlayCapabilities() {
		val allCapabilities = setOf(
			ReaderEngineCapability.Search,
			ReaderEngineCapability.MediaOverlay
		)

		listOf(
			ReaderPublicationFormat.Epub,
			ReaderPublicationFormat.Azw3,
			ReaderPublicationFormat.Mobi,
			ReaderPublicationFormat.Fb2
		).forEach { format ->
			assertEquals(allCapabilities, format.readerEngineCapabilities, format.name)
		}
		listOf(
			ReaderPublicationFormat.Pdf,
			ReaderPublicationFormat.Cbz
		).forEach { format ->
			assertEquals(emptySet(), format.readerEngineCapabilities, format.name)
		}
	}

	@Test
	fun onlySearchAndMediaOverlayCommandsRequireOptionalCapabilities() {
		val fragment = overlayFragment()

		assertEquals(ReaderEngineCapability.Search, ReaderEngineCommand.Search("dragon").requiredCapability)
		assertEquals(ReaderEngineCapability.Search, ReaderEngineCommand.ClearSearch.requiredCapability)
		assertEquals(ReaderEngineCapability.MediaOverlay, ReaderEngineCommand.ApplyMediaOverlay(fragment).requiredCapability)
		assertEquals(
			ReaderEngineCapability.MediaOverlay,
			ReaderEngineCommand.UpdateMediaOverlayProgress(fragment).requiredCapability
		)
		assertEquals(ReaderEngineCapability.MediaOverlay, ReaderEngineCommand.ClearMediaOverlay.requiredCapability)
		assertNull(ReaderEngineCommand.TurnPage(ReaderPageTurnDirection.Next).requiredCapability)
		assertNull(ReaderEngineCommand.ApplySettings(defaultReaderSettings()).requiredCapability)
	}

	@Test
	fun pdfAndCbzAdaptersRejectUnsupportedCommandsWithoutChangingBridgeState() {
		listOf(ReaderPublicationFormat.Pdf, ReaderPublicationFormat.Cbz).forEach { format ->
			val opened = adapterFor(format).onCommand(ReaderEngineCommand.OpenPublication(openRequest(format))).engine
			val paged = opened.onCommand(ReaderEngineCommand.TurnPage(ReaderPageTurnDirection.Next))
			val expectedViewState = assertIs<ReaderEngineViewState.WebViewPublication>(paged.viewState)
			val fragment = overlayFragment()
			val commands = listOf(
				ReaderEngineCommand.Search("dragon"),
				ReaderEngineCommand.ClearSearch,
				ReaderEngineCommand.ApplyMediaOverlay(fragment),
				ReaderEngineCommand.UpdateMediaOverlayProgress(fragment.copy(textProgressEnd = 22)),
				ReaderEngineCommand.ClearMediaOverlay
			)

			var engine = paged.engine
			commands.forEach { command ->
				val rejected = engine.onCommand(command)
				assertEquals(expectedViewState, rejected.viewState, "$format rejected $command")
				assertEquals(1L, assertIs<ReaderEngineViewState.WebViewPublication>(rejected.viewState).commandKey)
				engine = rejected.engine
			}
		}
	}

	@Test
	fun supportedPublicationAdaptersStillDispatchSearchAndMediaOverlayCommands() {
		listOf(
			ReaderPublicationFormat.Epub,
			ReaderPublicationFormat.Azw3,
			ReaderPublicationFormat.Mobi,
			ReaderPublicationFormat.Fb2
		).forEach { format ->
			val opened = adapterFor(format).onCommand(ReaderEngineCommand.OpenPublication(openRequest(format))).engine
			val search = opened.onCommand(ReaderEngineCommand.Search("dragon"))
			val overlay = search.engine.onCommand(ReaderEngineCommand.ApplyMediaOverlay(overlayFragment()))

			assertEquals(
				ReaderBridgeCommand.Search("dragon"),
				assertIs<ReaderEngineViewState.WebViewPublication>(search.viewState).bridgeCommand()
			)
			assertIs<ReaderBridgeCommand.ApplyOverlayFragment>(
				assertIs<ReaderEngineViewState.WebViewPublication>(overlay.viewState).bridgeCommand()
			)
		}
	}

	@Test
	fun pdfAndCbzAdaptersIgnoreUnsupportedHostEvents() {
		listOf(ReaderPublicationFormat.Pdf, ReaderPublicationFormat.Cbz).forEach { format ->
			val adapter = adapterFor(format)

			assertNull(
				adapter.onHostEvent(
					ReaderEngineHostEvent.FoliateBridge(
						ReaderBridgeEvent.SearchResults(query = "dragon", results = emptyList())
					)
				)
			)
			assertNull(
				adapter.onHostEvent(
					ReaderEngineHostEvent.FoliateBridge(
						ReaderBridgeEvent.OverlayFragmentActive(overlayFragment())
					)
				)
			)
			assertIs<ReaderEngineEvent.Relocated>(
				adapter.onHostEvent(
					ReaderEngineHostEvent.FoliateBridge(
						ReaderBridgeEvent.LocationChanged(
								locator = ReaderLocator(progress = 0.4),
								foliateSessionId = "session-a"
							)
					)
				)
			)
		}
	}

	@Test
	fun controllerNoopsUnsupportedActionsBeforeMutatingState() {
		listOf(ReaderPublicationFormat.Pdf, ReaderPublicationFormat.Cbz).forEach { format ->
			val controller = ReaderController().open(openRequest(format)).controller

			val searchDialog = controller.openSearchDialog()
			val searched = controller.search("dragon")
			val overlaid = controller.applyMediaOverlay(overlayFragment())
			val sidecar = controller.loadWhispersyncSidecar(whispersyncSidecar())
			val player = controller.openWhispersyncPlayerDialog()

			listOf(searchDialog, searched, overlaid, sidecar, player).forEach { step ->
				assertEquals(controller.state, step.controller.state, "$format state")
				assertEquals(emptyList(), step.engineCommands, "$format commands")
				assertNull(step.whispersyncAudioSeekTarget, "$format seek")
				assertNull(step.readaloudPlaybackCommand, "$format playback")
			}
		}
	}

	@Test
	fun openingUnsupportedPublicationClearsTransientCapabilityState() {
		val epub = ReaderController().open(openRequest(ReaderPublicationFormat.Epub)).controller
		val searched = epub.search("dragon").controller
		val overlaid = searched.applyMediaOverlay(overlayFragment()).controller
		val synced = overlaid.loadWhispersyncSidecar(whispersyncSidecar()).controller

		val pdf = synced.open(openRequest(ReaderPublicationFormat.Pdf)).controller

		assertEquals(ReaderSearchState(), pdf.state.search)
		assertNull(pdf.state.activeMediaOverlay)
		assertNull(pdf.state.audioMetadataLabel)
		assertFalse(pdf.state.whispersync.available)
	}

	@Test
	fun coordinatorDoesNotDispatchUnsupportedCommandsOrEvents() {
		listOf(ReaderPublicationFormat.Pdf, ReaderPublicationFormat.Cbz).forEach { format ->
			val opened = ReaderCoordinator().open(openRequest(format)).coordinator
			val initialViewState = assertIs<ReaderEngineViewState.WebViewPublication>(opened.viewState)
			val searched = opened.search("dragon").coordinator
			val overlaid = searched.applyMediaOverlay(overlayFragment()).coordinator
			val hostEvent = overlaid.onEngineHostEvent(
				ReaderEngineHostEvent.FoliateBridge(
					ReaderBridgeEvent.SearchResults(query = "dragon", results = emptyList())
				)
			).coordinator

			assertEquals(initialViewState, searched.viewState)
			assertEquals(initialViewState, overlaid.viewState)
			assertEquals(initialViewState, hostEvent.viewState)
			assertEquals(ReaderSearchState(), hostEvent.controller.state.search)
			assertNull(hostEvent.controller.state.activeMediaOverlay)
			assertEquals(0L, initialViewState.commandKey)
			assertNull(initialViewState.command)
		}
	}

	@Test
	fun controllerStateReportsCapabilityFromItsActiveFormat() {
		val epub = ReaderController().open(openRequest(ReaderPublicationFormat.Epub)).controller.state
		val pdf = ReaderController().open(openRequest(ReaderPublicationFormat.Pdf)).controller.state

		assertTrue(epub.supportsReaderEngineCapability(ReaderEngineCapability.Search))
		assertTrue(epub.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay))
		assertFalse(pdf.supportsReaderEngineCapability(ReaderEngineCapability.Search))
		assertFalse(pdf.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay))
	}

	private fun adapterFor(format: ReaderPublicationFormat): ReaderEngine =
		when (format) {
			ReaderPublicationFormat.Epub -> FoliateEpubEngineAdapter()
			ReaderPublicationFormat.Pdf -> FoliatePdfEngineAdapter()
			else -> FoliatePublicationEngineAdapter(format)
		}

	private fun openRequest(format: ReaderPublicationFormat): ReaderEngineOpenRequest =
		ReaderEngineOpenRequest(
			publication = ReaderPublicationIdentity(
				bookId = "book-$format",
				title = "Capability fixture",
				resourceHref = "publication.${format.name.lowercase()}",
				format = format
			),
			url = "https://appassets.androidplatform.net/reader-cache/book-$format/publication",
			settings = defaultReaderSettings()
		)

	private fun overlayFragment(): ReaderOverlayFragment =
		ReaderOverlayFragment(
			resourceHref = "audio/chapter-01.mp3",
			fragmentId = "clip-1",
			textHref = "chapter-01.xhtml",
			textStart = 10,
			textEnd = 42,
			textProgressEnd = 18,
			label = "Chapter 1"
		)

	private fun whispersyncSidecar(): WhispersyncSidecar =
		WhispersyncSidecar(
			artifactId = "artifact-3",
			ebookBookFileId = "3913",
			audiobookBookFileId = "694",
			timeline = WhispersyncTimeline(segments = emptyList())
		)

	private fun ReaderEngineViewState.WebViewPublication.bridgeCommand(): ReaderBridgeCommand? =
		(command as? ReaderEngineHostCommand.FoliateBridge)?.command
}
