package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReaderRawTextProvenanceBridgeTest {
	@Test
	fun installCommandBecomesDurableFoliateProvenanceState() {
		val descriptor = descriptor()
		val command = ReaderEngineCommand.InstallRawTextProvenance(descriptor)
		assertEquals(ReaderEngineCapability.MediaOverlay, command.requiredCapability)

		val opened = FoliateEpubEngineAdapter()
			.onCommand(ReaderEngineCommand.OpenPublication(openRequest()))
			.engine
		val installed = opened.onCommand(command)
		val viewState = assertIs<ReaderEngineViewState.WebViewPublication>(installed.viewState)
		assertEquals(listOf(descriptor), viewState.rawTextProvenanceDescriptors)
		assertNull(viewState.command)
	}

	@Test
	fun adapterMapsSafeRawStatusAndOptionalInverseFields() {
		val status = ReaderBridgeEvent.RawTextProvenanceStatusChanged(
			provenanceId = "chapter-raw-1",
			status = RawTextProvenanceStatus.Ready
		)
		val visible = ReaderBridgeEvent.VisibleTextRange(
			textHref = "OPS/Text/chapter.xhtml",
			visibleStart = 12,
			visibleEnd = 31,
			rawProvenanceId = "chapter-raw-1",
			rawSpineIndex = 3,
			rawByteStart = 44,
			rawByteEnd = 69
		)
		val point = ReaderBridgeEvent.TextPoint(
			textHref = "OPS/Text/chapter.xhtml",
			textOffset = 18,
			rawProvenanceId = "chapter-raw-1",
			rawByteOffset = 52
		)
		val adapter = FoliateEpubEngineAdapter()

		assertEquals(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = "chapter-raw-1",
				status = RawTextProvenanceStatus.Ready
			),
			adapter.onHostEvent(ReaderEngineHostEvent.FoliateBridge(status))
		)
		assertEquals(
			ReaderEngineEvent.VisibleTextRange(
				textHref = visible.textHref,
				visibleStart = visible.visibleStart,
				visibleEnd = visible.visibleEnd,
				rawProvenanceId = visible.rawProvenanceId,
				rawSpineIndex = visible.rawSpineIndex,
				rawByteStart = visible.rawByteStart,
				rawByteEnd = visible.rawByteEnd
			),
			adapter.onHostEvent(ReaderEngineHostEvent.FoliateBridge(visible))
		)
		assertEquals(
			ReaderEngineEvent.TextPoint(
				textHref = point.textHref,
				textOffset = point.textOffset,
				rawProvenanceId = point.rawProvenanceId,
				rawByteOffset = point.rawByteOffset
			),
			adapter.onHostEvent(ReaderEngineHostEvent.FoliateBridge(point))
		)
	}

	@Test
	fun controllerTracksRawReadinessByProvenanceIdAndClearsItOnOpen() {
		val descriptor = descriptor()
		val opened = ReaderController().open(openRequest()).controller
		val unsolicited = opened.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = "never-installed",
				status = RawTextProvenanceStatus.Ready
			)
		).controller
		assertEquals(emptyMap(), unsolicited.state.rawTextProvenanceById)

		val pending = opened.installRawTextProvenance(descriptor)
		assertEquals(
			RawTextProvenanceState(RawTextProvenanceStatus.Pending),
			pending.controller.state.rawTextProvenanceById[descriptor.id]
		)
		assertEquals(
			listOf(ReaderEngineCommand.InstallRawTextProvenance(descriptor)),
			pending.engineCommands
		)

		val ready = pending.controller.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Ready
			)
		).controller
		assertEquals(
			RawTextProvenanceState(RawTextProvenanceStatus.Ready),
			ready.state.rawTextProvenanceById[descriptor.id]
		)

		val rejected = ready.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			)
		).controller
		assertEquals(
			RawTextProvenanceState(
				status = RawTextProvenanceStatus.Rejected,
				reason = RawTextProvenanceReason.DocumentChanged
			),
			rejected.state.rawTextProvenanceById[descriptor.id]
		)

		val reopened = rejected.open(openRequest()).controller
		assertEquals(emptyMap(), reopened.state.rawTextProvenanceById)
	}

	@Test
	fun unsupportedFormatRejectsRawInstallAndStatusWithoutStateMutation() {
		val descriptor = descriptor()
		val pdfRequest = openRequest().copy(
			publication = openRequest().publication.copy(format = ReaderPublicationFormat.Pdf)
		)
		val controller = ReaderController().open(pdfRequest).controller

		val install = controller.installRawTextProvenance(descriptor)
		assertEquals(controller.state, install.controller.state)
		assertEquals(emptyList(), install.engineCommands)
		val status = controller.onEngineEvent(
			ReaderEngineEvent.RawTextProvenanceStatusChanged(
				provenanceId = descriptor.id,
				status = RawTextProvenanceStatus.Ready
			)
		)
		assertEquals(controller.state, status.controller.state)
		assertNull(status.controller.state.rawTextProvenanceById[descriptor.id])
	}

	private fun descriptor(): ReaderRawTextProvenanceDescriptor =
		ReaderRawTextProvenanceDescriptor(
			id = "chapter-raw-1",
			href = "OPS/Text/chapter.xhtml",
			spineIndex = 3,
			sourceHash = "sha256:${"a".repeat(64)}",
			extractedTextHash = "sha256:${"b".repeat(64)}",
			byteLength = 144,
			tokenCount = 22
		)

	private fun openRequest(): ReaderEngineOpenRequest =
		ReaderEngineOpenRequest(
			publication = ReaderPublicationIdentity(
				bookId = "book-1",
				resourceHref = "book.epub",
				format = ReaderPublicationFormat.Epub
			),
			url = "https://appassets.androidplatform.net/reader-cache/book-1/book.epub"
		)
}
