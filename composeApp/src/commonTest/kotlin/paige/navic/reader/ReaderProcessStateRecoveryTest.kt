package paige.navic.reader

import androidx.lifecycle.SavedStateHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderProcessStateRecoveryTest {
	@Test
	fun savedStateHandleRoundTripRetainsOnlyThePublicationMatchedSnapshot() {
		val publication = publication()
		val snapshot = ReaderProcessStateSnapshot(
			publication = publication,
			dialog = ReaderControllerDialog.Search,
			searchQuery = "restored query",
			searchSubmitted = true,
			selection = ReaderSelection(
				text = "Selected text",
				cfi = "epubcfi(/6/4!/4/2:0)",
				href = "chapter-1.xhtml",
				posLeft = 99.0,
				posTop = 101.0
			),
			selectionNoteDraft = ReaderSelectionNoteDraft(
				bookId = publication.bookId,
				bookTitle = publication.title,
				text = "Selected text",
				cfi = "epubcfi(/6/4!/4/2:0)",
				href = "chapter-1.xhtml",
				sectionTitle = "Chapter 1",
				note = "Half-written note"
			)
		)
		val handle = SavedStateHandle()

		ReaderProcessStateViewModel(handle).retain(snapshot)
		val recreatedHandle = SavedStateHandle(
			mapOf(
				ReaderProcessStateSavedStateKey to
					assertNotNull(handle.get<String>(ReaderProcessStateSavedStateKey))
			)
		)
		val restored = ReaderProcessStateViewModel(recreatedHandle).restore(publication)

		assertNotNull(restored)
		assertEquals(ReaderControllerDialog.Search, restored.dialog)
		assertEquals("restored query", restored.searchQuery)
		assertTrue(restored.searchSubmitted)
		assertEquals("Selected text", restored.selection?.text)
		assertEquals("epubcfi(/6/4!/4/2:0)", restored.selection?.cfi)
		assertNull(restored.selection?.posLeft)
		assertNull(restored.selection?.posTop)
		assertEquals("Half-written note", restored.selectionNoteDraft?.note)
		assertNull(
			ReaderProcessStateViewModel(recreatedHandle).restore(
				publication.copy(resourceHref = "different.epub")
			)
		)
	}

	@Test
	fun malformedSavedStateIsIgnoredWithoutCrashing() {
		val handle = SavedStateHandle(
			mapOf(ReaderProcessStateSavedStateKey to "{not-json")
		)

		assertNull(ReaderProcessStateViewModel(handle).restore(publication()))
	}

	@Test
	fun submittedSearchIsReissuedAfterFreshOpenWithoutRetainingDerivedResults() {
		val publication = publication()
		val opened = ReaderController().open(openRequest(publication)).controller
		val snapshot = ReaderProcessStateSnapshot(
			publication = publication,
			dialog = ReaderControllerDialog.Search,
			searchQuery = "needle",
			searchSubmitted = true
		)

		val restored = opened.restoreProcessState(snapshot)

		assertEquals(ReaderControllerDialog.Search, restored.controller.state.dialog)
		assertEquals("needle", restored.controller.state.search.query)
		assertTrue(restored.controller.state.search.active)
		assertTrue(restored.controller.state.search.results.isEmpty())
		assertEquals(listOf(ReaderEngineCommand.Search("needle")), restored.engineCommands)
		assertTrue(restored.controller.state.toc.isEmpty())
		assertNull(restored.controller.state.loadedDocument)
		assertNull(restored.controller.state.annotationPopup)
		assertFalse(restored.controller.state.whispersync.available)
	}

	@Test
	fun unsubmittedSearchInputRestoresWithoutExecutingEngineSearch() {
		val publication = publication()
		val opened = ReaderController().open(openRequest(publication)).controller
		val snapshot = ReaderProcessStateSnapshot(
			publication = publication,
			dialog = ReaderControllerDialog.Search,
			searchQuery = "typed but not submitted",
			searchSubmitted = false
		)

		val restored = opened.restoreProcessState(snapshot)

		assertEquals("typed but not submitted", restored.controller.state.search.query)
		assertFalse(restored.controller.state.search.active)
		assertTrue(restored.engineCommands.isEmpty())
	}

	@Test
	fun noteDraftAndSemanticSelectionRestoreWithoutRendererGeometry() {
		val publication = publication()
		val controller = ReaderController().open(openRequest(publication)).controller
		val snapshot = ReaderProcessStateSnapshot(
			publication = publication,
			dialog = ReaderControllerDialog.Contents,
			selection = ReaderSelection(
				text = "Selected text",
				cfi = "epubcfi(/6/4!/4/2:0)",
				href = "chapter-1.xhtml",
				contextText = "renderer context",
				posRight = 200.0
			),
			selectionNoteDraft = ReaderSelectionNoteDraft(
				bookId = publication.bookId,
				bookTitle = publication.title,
				text = "Selected text",
				cfi = "epubcfi(/6/4!/4/2:0)",
				href = "chapter-1.xhtml",
				note = "Draft survives"
			)
		)

		val state = controller.restoreProcessState(snapshot).controller.state

		assertEquals(ReaderControllerDialog.Contents, state.dialog)
		assertEquals("Selected text", state.selection?.text)
		assertNull(state.selection?.contextText)
		assertNull(state.selection?.posRight)
		assertEquals("Draft survives", state.selectionNoteDraft?.note)
	}

	@Test
	fun unsupportedPdfAndCbzStateIsDroppedByCapabilityPolicy() {
		listOf(ReaderPublicationFormat.Pdf, ReaderPublicationFormat.Cbz).forEach { format ->
			val publication = publication(format)
			val opened = ReaderController().open(openRequest(publication)).controller
			val snapshot = ReaderProcessStateSnapshot(
				publication = publication,
				dialog = ReaderControllerDialog.Search,
				searchQuery = "unsupported",
				searchSubmitted = true
			)

			val searchRestore = opened.restoreProcessState(snapshot)

			assertNull(searchRestore.controller.state.dialog)
			assertEquals(ReaderSearchState(), searchRestore.controller.state.search)
			assertTrue(searchRestore.engineCommands.isEmpty())

			val whispersyncRestore = opened.restoreProcessState(
				snapshot.copy(dialog = ReaderControllerDialog.WhispersyncPlayer)
			)
			assertNull(whispersyncRestore.controller.state.dialog)
		}
	}

	@Test
	fun controllerSnapshotExcludesDerivedStateAndPreservesCurrentDraftText() {
		val publication = publication()
		val state = ReaderControllerState(
			publication = publication,
			activeEngine = publication.format,
			dialog = ReaderControllerDialog.Search,
			search = ReaderSearchState(
				query = "active",
				results = listOf(
					ReaderSearchResult(id = "result", href = "chapter-2.xhtml")
				),
				active = true,
				complete = true
			),
			toc = listOf(ReaderTocItem(id = "chapter-2", title = "Chapter 2", href = "chapter-2.xhtml")),
			selectionNoteDraft = ReaderSelectionNoteDraft(
				bookId = publication.bookId,
				bookTitle = publication.title,
				text = "Selected text",
				cfi = "epubcfi(/6/4!/4/2:0)",
				note = "Current input"
			),
			loadedDocument = ReaderLoadedDocument(href = "chapter-2.xhtml")
		)

		val snapshot = state.toReaderProcessStateSnapshot()

		assertNotNull(snapshot)
		assertEquals("active", snapshot.searchQuery)
		assertTrue(snapshot.searchSubmitted)
		assertEquals("Current input", snapshot.selectionNoteDraft?.note)
		val encoded = assertNotNull(encodeReaderProcessState(snapshot))
		assertFalse(encoded.contains("result"))
		assertFalse(encoded.contains("Chapter 2"))
		assertFalse(encoded.contains("loadedDocument"))
		assertIs<ReaderProcessStateSnapshot>(decodeReaderProcessState(encoded))
	}

	@Test
	fun inputChangesUpdateSavedIntentWithoutDispatchingEngineCommands() {
		val publication = publication()
		val selectedController = ReaderController().open(openRequest(publication)).controller
			.onEngineEvent(
				ReaderEngineEvent.SelectionChanged(
					text = "Selected text",
					cfi = "epubcfi(/6/4!/4/2:0)",
					href = "chapter-1.xhtml"
				)
			).controller
		val noteController = selectedController.startSelectionNote().controller

		val noteStep = noteController.updateSelectionNoteDraft("Typing now")
		val searchStep = noteStep.controller.updateSearchInput("draft query")

		assertEquals("Typing now", noteStep.controller.state.selectionNoteDraft?.note)
		assertTrue(noteStep.engineCommands.isEmpty())
		assertEquals("draft query", searchStep.controller.state.search.query)
		assertFalse(searchStep.controller.state.search.active)
		assertTrue(searchStep.engineCommands.isEmpty())
		val snapshot = assertNotNull(searchStep.controller.state.toReaderProcessStateSnapshot())
		assertEquals("Typing now", snapshot.selectionNoteDraft?.note)
		assertEquals("draft query", snapshot.searchQuery)
		assertFalse(snapshot.searchSubmitted)
	}

	private fun publication(
		format: ReaderPublicationFormat = ReaderPublicationFormat.Epub
	): ReaderPublicationIdentity = ReaderPublicationIdentity(
		bookId = "book-1",
		title = "Book One",
		resourceHref = "publication.${format.name.lowercase()}",
		kind = ReaderPublicationKind.Ebook,
		format = format
	)

	private fun openRequest(publication: ReaderPublicationIdentity): ReaderEngineOpenRequest =
		ReaderEngineOpenRequest(
			publication = publication,
			url = "https://appassets.androidplatform.net/reader-cache/${publication.bookId}/${publication.resourceHref}",
			startLocator = ReaderLocator(
				href = "chapter-1.xhtml",
				cfi = "epubcfi(/6/4!/4/2:0)",
				progress = 0.25
			)
		)
}
