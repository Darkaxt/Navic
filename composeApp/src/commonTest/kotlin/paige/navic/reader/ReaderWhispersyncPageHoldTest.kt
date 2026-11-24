package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Read-along audio plays only over a READY page: the single page-readiness hold gate. */
class ReaderWhispersyncPageHoldTest {
	@Test
	fun holdPausesAudiblePlaybackAndRemembersToResume() {
		val held = ReaderCoordinator(controller = playingController()).hold()

		assertEquals(ReaderReadaloudPlaybackCommand.Pause, held.readaloudPlaybackCommand)
		val whispersync = held.coordinator.controller.state.whispersync
		assertEquals(HoldReason, whispersync.pageHoldReason)
		assertTrue(whispersync.pageHoldResumePending)
		assertEquals(SourceCommit, whispersync.pageHoldPausedDestination)
		assertFalse(held.coordinator.controller.state.chrome.readaloudPlayback.isPlaying)
		assertFalse(whispersync.canStartPlayback)
	}

	@Test
	fun externalPlayingTickWhileHeldIsPausedAgain() {
		val held = ReaderCoordinator(controller = playingController()).hold().coordinator

		val tick = held.dispatch { onReadaloudPlaybackState(playingPlaybackState(positionMs = 1_600L)) }

		assertEquals(ReaderReadaloudPlaybackCommand.Pause, tick.readaloudPlaybackCommand)
		assertFalse(tick.coordinator.controller.state.chrome.readaloudPlayback.isPlaying)
		assertEquals(HoldReason, tick.coordinator.controller.state.whispersync.pageHoldReason)
	}

	@Test
	fun releaseResumesInPlaceWithOneBarePlay() {
		val held = ReaderCoordinator(controller = playingController()).hold().coordinator
			.dispatch { onReadaloudPlaybackState(stoppedPlaybackState(positionMs = 1_500L)) }.coordinator

		val released = held.dispatch { onWhispersyncPageReadiness(null) }

		assertEquals(ReaderReadaloudPlaybackCommand.Play, released.readaloudPlaybackCommand)
		assertNull(released.whispersyncAudioSeekTarget)
		val whispersync = released.coordinator.controller.state.whispersync
		assertNull(whispersync.pageHoldReason)
		assertFalse(whispersync.pageHoldResumePending)
		assertNull(whispersync.pageHoldPausedDestination)
		// A repeated READY report changes nothing: no second Play.
		val again = released.coordinator.dispatch { onWhispersyncPageReadiness(null) }
		assertNull(again.readaloudPlaybackCommand)
	}

	@Test
	fun navigationUnderHoldCannotSeekOrPlayUntilReleaseStartsTheDestination() {
		val held = ReaderCoordinator(controller = playingController()).hold().coordinator
			.dispatch { onReadaloudPlaybackState(stoppedPlaybackState(positionMs = 1_500L)) }.coordinator
		val turn = held.dispatch { onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)) }
		val sequence = requireNotNull(turn.coordinator.controller.state.whispersync.pendingCausalIntent?.sequence)
		val relocated = turn.coordinator.onEngineEvent(destinationRelocation(sequence))
		val destination = relocated.coordinator.onEngineEvent(destinationVisibleRange(sequence))

		listOf(turn, relocated, destination).forEach { step ->
			assertNull(step.whispersyncAudioSeekTarget)
			assertTrue(step.readaloudPlaybackCommand != ReaderReadaloudPlaybackCommand.Play)
		}
		assertNull(destination.coordinator.controller.state.whispersync.pendingAudioSeek)
		assertFalse(destination.coordinator.controller.state.whispersync.playbackStartPending)
		assertTrue(destination.coordinator.controller.state.whispersync.pageHoldResumePending)

		// The page changed while held: the destination's prepared start, not the old position.
		val started = destination.coordinator.controller.onWhispersyncPageReadiness(null).settlePreparedStart()
		assertEquals(3_000L, started.whispersyncAudioSeekTarget?.positionMs)
		assertEquals(ReaderReadaloudPlaybackCommand.Play, started.readaloudPlaybackCommand)
	}

	@Test
	fun userPlayUnderHoldIsRefusedAndTheControlWaitsForThePage() {
		val prepared = ReaderCoordinator(controller = preparedController()).hold().coordinator

		val refused = prepared.dispatch { onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.Play) }

		assertEquals("page-not-ready-$HoldReason", prepared.controller.whispersyncPlayBlocker())
		assertNull(refused.readaloudPlaybackCommand)
		assertNull(refused.whispersyncAudioSeekTarget)
		assertFalse(refused.coordinator.controller.state.whispersync.playbackStartPending)
		val control = readerWhispersyncPlaybackControlState(
			status = refused.coordinator.controller.state.whispersync.status,
			playbackState = stoppedPlaybackState(),
			hasPreparedVisibleTarget = true,
			pageHeld = true
		)
		assertTrue(control.loading)
		assertFalse(control.enabled)
	}

	@Test
	fun startPendingWhenTheHoldArrivesIsCancelledAndRestartedOnRelease() {
		val start = preparedController().onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.Play)
		val apply = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(start.engineCommands.single())
		assertTrue(start.controller.state.whispersync.playbackStartPending)
		val held = ReaderCoordinator(controller = start.controller).hold().coordinator
		assertFalse(held.controller.state.whispersync.playbackStartPending)
		assertNull(held.controller.state.whispersync.pendingAudioSeek)
		assertTrue(held.controller.state.whispersync.pageHoldResumePending)

		// The confirmation of the cancelled start arrives while held: no seek, no Play.
		val late = held.onEngineEvent(ReaderEngineEvent.MediaOverlayActive(fragment = apply.fragment))
		assertNull(late.whispersyncAudioSeekTarget)
		assertTrue(late.readaloudPlaybackCommand != ReaderReadaloudPlaybackCommand.Play)

		val started = late.coordinator.controller.onWhispersyncPageReadiness(null).settlePreparedStart()
		assertEquals(1_000L, started.whispersyncAudioSeekTarget?.positionMs)
		assertEquals(ReaderReadaloudPlaybackCommand.Play, started.readaloudPlaybackCommand)
	}

	@Test
	fun userPauseOrStopDuringHoldIsNotResumedOnRelease() {
		val held = ReaderCoordinator(controller = playingController()).hold().coordinator

		val paused = held.dispatch { onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.Pause) }
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, paused.readaloudPlaybackCommand)
		assertNull(paused.coordinator.dispatch { onWhispersyncPageReadiness(null) }.readaloudPlaybackCommand)

		val stopped = held.dispatch { onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.StopAndReset) }
		assertEquals(ReaderReadaloudPlaybackCommand.StopAndReset, stopped.readaloudPlaybackCommand)
		val releasedAfterStop = stopped.coordinator.dispatch { onWhispersyncPageReadiness(null) }
		assertTrue(releasedAfterStop.readaloudPlaybackCommand != ReaderReadaloudPlaybackCommand.Play)
		assertNull(releasedAfterStop.whispersyncAudioSeekTarget)
		assertNull(releasedAfterStop.coordinator.controller.state.whispersync.pendingAudioSeek)
	}

	@Test
	fun pageEndPauseIsNotResumedByARelease() {
		val boundaryPaused = startedController().onReadaloudPlaybackState(playingPlaybackState(positionMs = 3_500L))
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, boundaryPaused.readaloudPlaybackCommand)
		val held = ReaderCoordinator(controller = boundaryPaused.controller).hold()
		assertFalse(held.coordinator.controller.state.whispersync.pageHoldResumePending)

		val released = held.coordinator.dispatch { onWhispersyncPageReadiness(null) }

		assertNull(released.readaloudPlaybackCommand)
		assertNull(released.whispersyncAudioSeekTarget)
		assertEquals(
			ReaderWhispersyncTransportPhase.BoundaryPaused,
			released.coordinator.controller.state.whispersync.transportPhase
		)
	}

	@Test
	fun onlyAPageThatIsNotReadyHoldsAudio() {
		listOf(
			ReaderPageInteractionState.Ready,
			ReaderPageInteractionState.Settling,
			ReaderPageInteractionState.RefillingWorkingSet,
			ReaderPageInteractionState.BackgroundPrefetch
		).forEach { interaction ->
			assertNull(readiness(interaction).readAlongHoldReason(), "$interaction keeps the page readable")
		}
		assertEquals(
			"blocking-initial-preparation",
			readiness(ReaderPageInteractionState.BlockingInitialPreparation).readAlongHoldReason()
		)
		assertEquals(
			"blocking-profile-regeneration",
			readiness(ReaderPageInteractionState.BlockingProfileRegeneration).readAlongHoldReason()
		)
		assertEquals("page-preparation-failed", readiness(ReaderPageInteractionState.Failed).readAlongHoldReason())
		assertEquals(
			"page-preparation-failed",
			ReaderPagePreparationState(phase = ReaderPagePreparationPhase.Failed).readAlongHoldReason()
		)
		assertEquals(
			"layout-review-pending",
			ReaderPagePreparationState(layoutReviewPending = true).readAlongHoldReason()
		)
		assertEquals(
			"page-preparation-visible",
			ReaderPagePreparationState(presentation = ReaderPagePreparationPresentation.Compact).readAlongHoldReason()
		)
		// No opening question holds the book any more: the scope is chosen on the open sheet.
		assertNull(ReaderPagePreparationState().readAlongHoldReason())
	}

	private fun ReaderCoordinator.hold(reason: String = HoldReason): ReaderCoordinatorStep =
		dispatch { onWhispersyncPageReadiness(reason) }

	/** A prepared start either seeks at once (overlay already confirmed) or after its confirmation. */
	private fun ReaderControllerStep.settlePreparedStart(): ReaderControllerStep {
		if (whispersyncAudioSeekTarget != null) return this
		assertNull(readaloudPlaybackCommand, "A prepared start must not play before its overlay confirmation.")
		assertNotNull(controller.state.whispersync.pendingAudioSeek)
		val apply = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(engineCommands.single())
		return controller.onEngineEvent(ReaderEngineEvent.MediaOverlayActive(fragment = apply.fragment))
	}

	private fun readiness(interaction: ReaderPageInteractionState): ReaderPagePreparationState =
		ReaderPagePreparationState().withReadiness(ReaderPageReadinessState(interaction = interaction))

	private fun loadedController(): ReaderController {
		val opened = ReaderController().open(
			ReaderEngineOpenRequest(
				publication = ReaderPublicationIdentity(
					bookId = "book",
					resourceHref = TextHref,
					format = ReaderPublicationFormat.Epub
				),
				url = "https://reader.invalid/book.epub",
				mediaOverlayEnabled = true
			)
		).controller
		val committed = opened.onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(href = TextHref, pageIndex = 0, pageCount = 2),
				foliateSessionId = FoliateSession,
				destinationCommitIdentity = SourceCommit
			)
		).controller
		return committed.loadWhispersyncSidecar(sidecar()).controller
	}

	private fun preparedController(): ReaderController = loadedController().onEngineEvent(
		ReaderEngineEvent.VisibleTextRange(
			textHref = TextHref,
			visibleStart = 0,
			visibleEnd = 40,
			source = "reader",
			destinationCommitIdentity = SourceCommit
		)
	).controller

	private fun startedController(): ReaderController {
		val start = preparedController().onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.Play)
		val apply = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(start.engineCommands.single())
		return start.controller.onEngineEvent(ReaderEngineEvent.MediaOverlayActive(fragment = apply.fragment)).controller
	}

	private fun playingController(): ReaderController =
		startedController().onReadaloudPlaybackState(playingPlaybackState(positionMs = 1_500L)).controller

	private fun destinationRelocation(causalSequence: Long): ReaderEngineEvent.Relocated =
		ReaderEngineEvent.Relocated(
			locator = ReaderLocator(href = TextHref, pageIndex = 1, pageCount = 2),
			foliateSessionId = FoliateSession,
			pageTurnSettleToken = "settle-token",
			pageTurnSettleSessionId = FoliateSession,
			pageTurnSettleRasterGeneration = 4L,
			pageTurnSettleTextureGeneration = 5L,
			causalSequence = causalSequence,
			destinationCommitIdentity = DestinationCommit
		)

	private fun destinationVisibleRange(causalSequence: Long): ReaderEngineEvent.VisibleTextRange =
		ReaderEngineEvent.VisibleTextRange(
			textHref = TextHref,
			visibleStart = 80,
			visibleEnd = 100,
			source = "reader",
			causalSequence = causalSequence,
			destinationCommitIdentity = DestinationCommit
		)

	private fun playingPlaybackState(positionMs: Long) = ReaderReadaloudPlaybackUiState(
		isAvailable = true,
		isPlaying = true,
		audioResource = AudioHref,
		positionMs = positionMs
	)

	private fun stoppedPlaybackState(positionMs: Long = 0L) = ReaderReadaloudPlaybackUiState(
		isAvailable = true,
		isPlaying = false,
		audioResource = AudioHref,
		positionMs = positionMs
	)

	private fun sidecar(): WhispersyncSidecar = WhispersyncSidecar(
		timeline = WhispersyncTimeline(
			segments = listOf(
				segment(id = "first", startMs = 1_000L, endMs = 2_000L, textStart = 0, textEnd = 20),
				segment(id = "second", startMs = 2_000L, endMs = 3_000L, textStart = 20, textEnd = 40),
				segment(id = "outside", startMs = 3_000L, endMs = 4_000L, textStart = 80, textEnd = 100)
			)
		)
	)

	private fun segment(id: String, startMs: Long, endMs: Long, textStart: Int, textEnd: Int) =
		WhispersyncSegment(
			id = id,
			audioResource = AudioHref,
			startMs = startMs,
			endMs = endMs,
			textHref = TextHref,
			fragmentId = id,
			textStart = textStart,
			textEnd = textEnd,
			label = id
		)

	private companion object {
		const val HoldReason = "page-preparation-visible"
		const val AudioHref = "Audio/chapter01.m4b"
		const val TextHref = "Text/chapter01.xhtml"
		const val FoliateSession = "foliate-session"
		val SourceCommit = ReaderDestinationCommitIdentity(FoliateSession, 1L)
		val DestinationCommit = ReaderDestinationCommitIdentity(FoliateSession, 2L)
	}
}
