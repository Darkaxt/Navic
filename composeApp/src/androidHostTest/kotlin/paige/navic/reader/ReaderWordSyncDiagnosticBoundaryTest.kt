package paige.navic.reader

import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReaderWordSyncDiagnosticBoundaryTest {
	@Test
	fun activeOverlayProjectsOnlyAnchorPresenceWithoutConsumingTheEvent() {
		listOf(null, anchorReceipt()).forEach { receipt ->
			val event = ReaderEngineHostEvent.FoliateBridge(
				ReaderBridgeEvent.OverlayFragmentActive(wordFragment(), receipt)
			)
			val diagnostic = assertNotNull(overlayDiagnostic(event))
			assertEquals("Active", diagnostic.javaClass.simpleName)
			assertEquals(receipt != null, property(diagnostic, "getAnchorPresent"))
			assertPayloadFree(diagnostic)
			assertEquals(diagnostic, overlayDiagnostic(event))
			val forwarded = FoliateEpubEngineAdapter().onHostEvent(event)
			assertTrue(forwarded is ReaderEngineEvent.MediaOverlayActive)
			assertTrue(forwarded.fragment === (event.event as ReaderBridgeEvent.OverlayFragmentActive).fragment)
			assertTrue(forwarded.anchorReceipt === receipt)
		}
	}

	@Test
	fun inactiveOverlayPreservesEveryAllowlistedReasonAndFailsClosedForOtherInput() {
		val allowed = listOf(
			"animation-outside-visible-page", "animation-paint-rejected", "user-relocation-active",
			"outside-visible-page", "paint-rejected", "invalid-coordinate-mode",
			"stale-progress-request", "progress-outside-visible-page", "progress-paint-rejected",
			"document-loaded", "anchor-rejected"
		)
		val cases = allowed.map { it to it } + listOf(
			null to "absent", "" to "other", "ANCHOR-REJECTED" to "other",
			" anchor-rejected " to "other", "$payload\nreason=anchor-rejected" to "other"
		)
		cases.forEach { (reason, expected) ->
			val event = ReaderEngineHostEvent.FoliateBridge(
				ReaderBridgeEvent.OverlayFragmentInactive(
					fragmentId = payload,
					overlayRequestId = 9L,
					coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
					reason = reason
				)
			)
			val diagnostic = assertNotNull(overlayDiagnostic(event))
			assertEquals("Inactive", diagnostic.javaClass.simpleName)
			val safeReason = assertNotNull(property(diagnostic, "getReason"))
			assertTrue(safeReason.javaClass.isEnum)
			assertEquals(expected, property(safeReason, "getLogValue"))
			assertPayloadFree(diagnostic)
			val forwarded = FoliateEpubEngineAdapter().onHostEvent(event)
			assertTrue(forwarded is ReaderEngineEvent.MediaOverlayInactive)
			assertTrue(forwarded.reason == reason)
		}
	}

	@Test
	fun unrelatedAndNonWordSyncEventsHaveNoDiagnostic() {
		val events = listOf(
			ReaderEngineHostEvent.SettingsPresentationCommitted(7),
			ReaderEngineHostEvent.FoliateBridge(ReaderBridgeEvent.PublicationReady),
			ReaderEngineHostEvent.FoliateBridge(ReaderBridgeEvent.SelectionChanged(text = payload)),
			ReaderEngineHostEvent.FoliateBridge(ReaderBridgeEvent.Error(payload)),
			ReaderEngineHostEvent.FoliateBridge(
				ReaderBridgeEvent.OverlayFragmentActive(ReaderOverlayFragment(resourceHref = payload))
			),
			ReaderEngineHostEvent.FoliateBridge(
				ReaderBridgeEvent.OverlayFragmentInactive(reason = payload)
			),
			ReaderEngineHostEvent.FoliateBridge(
				ReaderBridgeEvent.OverlayFragmentInactive(
					coordinateMode = ReaderOverlayCoordinateMode.CueV1DomUtf16,
					reason = "anchor-rejected"
				)
			)
		)
		events.forEach { assertTrue(overlayDiagnostic(it) == null) }
	}

	@Test
	fun commandDiagnosticPreservesKeyTransitionSemanticsWithoutInspectingPayloadOrOrder() {
		val opened = FoliateEpubEngineAdapter().onCommand(ReaderEngineCommand.OpenPublication(openRequest()))
		val publication = opened.viewState as ReaderEngineViewState.WebViewPublication
		val single = ReaderEngineHostCommand.FoliateBridge(ReaderBridgeCommand.UpdateOverlayFragmentProgress(wordFragment()))
		val sequence = ReaderEngineHostCommand.FoliateBridgeSequence(
			listOf(ReaderBridgeCommand.ClearOverlay, ReaderBridgeCommand.UpdateOverlayFragmentProgress(wordFragment()))
		)
		val cases = listOf(
			Triple(ReaderEngineViewState.Empty, ReaderEngineViewState.Empty, false),
			Triple(publication, ReaderEngineViewState.Empty, false),
			Triple(ReaderEngineViewState.Empty, publication, true),
			Triple(publication, publication.copy(title = payload), false),
			Triple(publication, publication.copy(command = single), false),
			Triple(publication, publication.copy(commandKey = 1L), true),
			Triple(publication, publication.copy(command = single, commandKey = 1L), true),
			Triple(publication, publication.copy(command = sequence, commandKey = 2L), true),
			Triple(publication.copy(commandKey = 5L), publication, true)
		)
		cases.forEach { (previous, next, expected) ->
			val diagnostic = commandDiagnostic(previous, next)
			assertEquals(expected, property(diagnostic, "getPublished"))
			assertEquals(if (expected) "update-overlay" else "none", property(diagnostic, "getCommandLogValue"))
			assertPayloadFree(diagnostic)
		}
		assertTrue(sequence.commands[0] === ReaderBridgeCommand.ClearOverlay)
		assertTrue(sequence.commands[1] is ReaderBridgeCommand.UpdateOverlayFragmentProgress)
	}

	@Test
	fun sharedProjectionLeavesAdapterCapabilitiesAndTypedDispatchUnchangedAcrossFormats() {
		ReaderPublicationFormat.entries.forEach { format ->
			val adapter: ReaderEngine = when (format) {
				ReaderPublicationFormat.Epub -> FoliateEpubEngineAdapter()
				ReaderPublicationFormat.Pdf -> FoliatePdfEngineAdapter()
				else -> FoliatePublicationEngineAdapter(format)
			}
			val opened = adapter.onCommand(ReaderEngineCommand.OpenPublication(openRequest(format)))
			val command = ReaderEngineCommand.UpdateMediaOverlayProgress(wordFragment())
			val next = opened.engine.onCommand(command)
			val supportsOverlay = ReaderEngineCapability.MediaOverlay in adapter.capabilities
			assertEquals(supportsOverlay, property(commandDiagnostic(opened.viewState, next.viewState), "getPublished"))
			if (supportsOverlay) {
				val hostCommand = (next.viewState as ReaderEngineViewState.WebViewPublication).command
				assertTrue(hostCommand is ReaderEngineHostCommand.FoliateBridge)
				assertTrue(hostCommand.command is ReaderBridgeCommand.UpdateOverlayFragmentProgress)
			} else {
				assertTrue(next.viewState === opened.viewState)
			}
			val event = ReaderEngineHostEvent.FoliateBridge(ReaderBridgeEvent.OverlayFragmentActive(wordFragment()))
			// Diagnostics describe host observations even when engine capabilities reject the event.
			assertNotNull(overlayDiagnostic(event))
			assertEquals(supportsOverlay, opened.engine.onHostEvent(event) != null)
		}
	}

	@Test
	fun shellConsumesOnlyProjectionsBeforeTheUnchangedApplyBoundaries() {
		val source = listOf(
			File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"),
			File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
		).first { it.isFile }.readText()
		val commandBody = source.substringAfter("rememberUpdatedState<(ReaderWordSyncBoundaryDispatch) -> Unit>")
			.substringBefore("val currentWordSyncClearHandler")
		assertTrue(commandBody.contains("wordSyncCommandDiagnosticSince(coordinator.viewState)"))
		assertTrue(commandBody.indexOf("coordinator.onWordSyncBoundary(dispatch)") < commandBody.indexOf("wordSyncCommandDiagnosticSince("))
		assertTrue(commandBody.indexOf("wordSyncCommandDiagnosticSince(") < commandBody.indexOf("Logger.i("))
		assertTrue(commandBody.indexOf("Logger.i(") < commandBody.indexOf("applyCoordinatorStep(step)"))
		assertTrue(commandBody.contains("diagnostic.published") && commandBody.contains("diagnostic.commandLogValue"))
		assertTrue(commandBody.contains("mode=word-exact count=\${dispatch.coalescedCount}"))
		val eventBody = source.substringAfter("fun handleEngineHostEvent(event: ReaderEngineHostEvent)")
			.substringBefore("fun applyReaderSettings(")
		assertTrue(eventBody.contains("event.wordSyncOverlayDiagnostic()"))
		assertTrue(eventBody.contains("is ReaderWordSyncOverlayDiagnostic.Active"))
		assertTrue(eventBody.contains("is ReaderWordSyncOverlayDiagnostic.Inactive"))
		assertTrue(eventBody.contains("diagnostic.anchorPresent") && eventBody.contains("diagnostic.reason.logValue"))
		assertTrue(eventBody.indexOf("Logger.i(") < eventBody.indexOf("Logger.w("))
		assertTrue(eventBody.indexOf("Logger.w(") < eventBody.indexOf("applyCoordinatorStep(coordinator.onEngineHostEvent(event))"))
		assertTrue(!eventBody.contains(".FoliateBridge") && !eventBody.contains(".event"))
	}

	// Runtime lookup makes the pre-implementation RED an explicit missing-projection assertion,
	// not a compilation failure. The behavioral cases above exercise the real adapter functions.
	private fun projection(name: String, vararg arguments: Any): Any? {
		val owner = runCatching { Class.forName("paige.navic.reader.FoliateEpubEngineAdapterKt") }.getOrNull()
		assertNotNull(owner, "Common adapter diagnostic projection is missing")
		val method = owner.declaredMethods.singleOrNull { it.name == name && it.parameterCount == arguments.size }
		assertNotNull(method, "Typed diagnostic projection is missing")
		return method.invoke(null, *arguments)
	}

	private fun overlayDiagnostic(event: ReaderEngineHostEvent): Any? = projection("wordSyncOverlayDiagnostic", event)

	private fun commandDiagnostic(previous: ReaderEngineViewState, next: ReaderEngineViewState): Any =
		assertNotNull(projection("wordSyncCommandDiagnosticSince", next, previous))

	private fun property(value: Any, getter: String): Any? = value.javaClass.getMethod(getter).invoke(value)

	private fun assertPayloadFree(diagnostic: Any) {
		assertTrue(!diagnostic.toString().contains(payload), "Diagnostic must not retain synthetic protected fields")
		val fields = diagnostic.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
		assertTrue(fields.isNotEmpty())
		assertTrue(fields.all { it.type == Boolean::class.javaPrimitiveType || it.type.isEnum },
			"Diagnostic instance fields must contain only booleans or enums")
	}

	private fun wordFragment() = ReaderOverlayFragment(
		resourceHref = payload,
		coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
		textHref = payload,
		spokenText = payload,
		label = payload,
		rawProvenanceId = payload,
		rawSpineIndex = 0,
		rawByteStart = 0,
		rawByteEnd = 4
	)

	private fun openRequest(format: ReaderPublicationFormat = ReaderPublicationFormat.Epub) = ReaderEngineOpenRequest(
		publication = ReaderPublicationIdentity(bookId = payload, title = payload, resourceHref = payload, format = format),
		url = payload
	)

	private fun anchorReceipt() = ReaderWhispersyncAnchorReceipt(
		foliateSessionId = payload, destinationCommitToken = payload,
		visualPageOrdinal = 0, spineIndex = 0, rasterGeneration = 1L, textureGeneration = 1L,
		presentationMutationGeneration = 1L, presentationSequence = 1L, anchorGeneration = 1L,
		boundarySequence = 1L, layoutGeneration = 1L, viewGeneration = 1L, commitSequence = 1L,
		committedSpineIndex = 0, committedChapterPageIndex = 0, committedChapterPageCount = 1,
		paginationFingerprint = payload, layoutFingerprint = payload, readerSettingsRasterKey = payload,
		captureGeometry = ReaderPageTurnCaptureGeometry(
			viewportWidth = 100.0, viewportHeight = 100.0, mode = ReaderPageTurnLayoutMode.Single,
			pages = listOf(ReaderPageTurnPageRect(ReaderPageTurnPageRole.Full, 0.0, 0.0, 100.0, 100.0))
		),
		pageLocalRects = listOf(ReaderWhispersyncPageLocalRect(ReaderPageTurnPageRole.Full, 1.0, 1.0, 10.0, 10.0))
	)

	private companion object {
		const val payload = "synthetic-protected-payload"
	}
}
