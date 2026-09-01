package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderWhispersyncLifecycleReducerTest {
	@Test
	fun canonicalVisibleRangeFenceRequiresMatchingOverlappingRawAuthority() {
		val fragment = ReaderOverlayFragment(
			resourceHref = AudioHref,
			coordinateMode = ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8,
			textHref = TextHref,
			rawProvenanceId = "wordsync-v1-spine-2",
			rawSpineIndex = 2,
			rawByteStart = 100,
			rawByteEnd = 120
		)
		fun visibleRange(
			rawProvenanceId: String? = "wordsync-v1-spine-2",
			rawSpineIndex: Int? = 2,
			rawByteStart: Int? = 110,
			rawByteEnd: Int? = 130
		) = ReaderWhispersyncVisibleTextRange(
			textHref = TextHref,
			visibleStart = 100,
			visibleEnd = 110,
			rawProvenanceId = rawProvenanceId,
			rawSpineIndex = rawSpineIndex,
			rawByteStart = rawByteStart,
			rawByteEnd = rawByteEnd
		)

		assertFalse(fragment.isOutsideWhispersyncVisibleRange(visibleRange()))
		assertTrue(fragment.isOutsideWhispersyncVisibleRange(visibleRange(rawByteStart = 120, rawByteEnd = 140)))
		assertTrue(
			fragment.isOutsideWhispersyncVisibleRange(
				visibleRange(rawProvenanceId = null, rawSpineIndex = null, rawByteStart = null, rawByteEnd = null)
			)
		)
		assertTrue(fragment.isOutsideWhispersyncVisibleRange(visibleRange(rawProvenanceId = "wordsync-v1-spine-9")))
		assertTrue(fragment.isOutsideWhispersyncVisibleRange(visibleRange(rawSpineIndex = 9)))
		assertTrue(fragment.isOutsideWhispersyncVisibleRange(visibleRange(rawByteStart = 130, rawByteEnd = 110)))
	}

	@Test
	fun maintenanceVisibleRangePreparesFirstCueWithoutSeekingOrPainting() {
		val step = loadedController().onEngineEvent(sourceVisibleRange())

		val whispersync = step.controller.state.whispersync
		assertEquals(1_000L, whispersync.preparedVisibleTarget?.audioSeekTarget?.positionMs)
		assertEquals(SourceCommit, whispersync.preparedVisibleTarget?.destinationCommitIdentity)
		assertEquals(ReaderWhispersyncTransportPhase.Ready, whispersync.transportPhase)
		assertEquals(ReaderWhispersyncEventProvenance.PresentationMaintenance, whispersync.lastEventProvenance)
		assertNull(step.whispersyncAudioSeekTarget)
		assertTrue(step.engineCommands.isEmpty())
		assertNull(step.controller.state.activeMediaOverlay)
	}

	@Test
	fun mismatchedMaintenanceRangeCannotReplaceCurrentPreparedTarget() {
		val ready = preparedController()
		val stale = ready.onEngineEvent(
			sourceVisibleRange(destinationCommitIdentity = DestinationCommit)
		)

		assertEquals(
			SourceCommit,
			stale.controller.state.whispersync.preparedVisibleTarget?.destinationCommitIdentity
		)
		assertEquals(1_000L, stale.controller.state.whispersync.preparedVisibleTarget?.audioSeekTarget?.positionMs)
		assertNull(stale.whispersyncAudioSeekTarget)
		assertTrue(stale.engineCommands.isEmpty())
	}

	@Test
	fun startWaitsForPresentationThenSeeksAndPlaysPreparedCue() {
		val start = preparedController().onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)

		assertEquals(ReaderWhispersyncPlaybackIntent.Enabled, start.controller.state.whispersync.playbackIntent)
		assertEquals(ReaderWhispersyncTransportPhase.Preparing, start.controller.state.whispersync.transportPhase)
		assertTrue(start.controller.state.whispersync.playbackStartPending)
		assertNull(start.readaloudPlaybackCommand)
		assertNull(start.whispersyncAudioSeekTarget)
		val apply = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(start.engineCommands.single())

		val confirmed = start.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(fragment = apply.fragment)
		)

		assertEquals(1_000L, confirmed.whispersyncAudioSeekTarget?.positionMs)
		assertEquals(ReaderReadaloudPlaybackCommand.Play, confirmed.readaloudPlaybackCommand)
		assertFalse(confirmed.controller.state.whispersync.playbackStartPending)
		assertEquals(ReaderWhispersyncTransportPhase.Seeking, confirmed.controller.state.whispersync.transportPhase)
	}

	@Test
	fun startRejectsPreparedTargetFromAnotherDestination() {
		val ready = preparedController()
		val mismatched = ready.copy(
			state = ready.state.copy(destinationCommitIdentity = DestinationCommit)
		)

		val start = mismatched.onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.Play)

		assertEquals(mismatched, start.controller)
		assertNull(start.whispersyncAudioSeekTarget)
		assertNull(start.readaloudPlaybackCommand)
		assertTrue(start.engineCommands.isEmpty())
	}

	@Test
	fun manualStopRetainsPreparedTargetAndResetsAudioToItsFirstCue() {
		val playing = playingController()

		val stop = playing.onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.StopAndReset)

		assertEquals(ReaderReadaloudPlaybackCommand.StopAndReset, stop.readaloudPlaybackCommand)
		assertEquals(ReaderWhispersyncPlaybackIntent.UserStopped, stop.controller.state.whispersync.playbackIntent)
		assertEquals(ReaderWhispersyncTransportPhase.Preparing, stop.controller.state.whispersync.transportPhase)
		assertTrue(stop.controller.state.whispersync.stopResetPending)
		assertNotNull(stop.controller.state.whispersync.preparedVisibleTarget)
		assertTrue(stop.engineCommands.contains(ReaderEngineCommand.ClearMediaOverlay))

		val reset = stop.controller.onReadaloudPlaybackState(stoppedPlaybackState())

		assertEquals(1_000L, reset.whispersyncAudioSeekTarget?.positionMs)
		assertEquals(ReaderWhispersyncTransportPhase.Ready, reset.controller.state.whispersync.transportPhase)
		assertFalse(reset.controller.state.whispersync.stopResetPending)
		assertTrue(reset.controller.state.whispersync.canStartPlayback)
		assertNull(reset.controller.state.activeMediaOverlay)
	}

	@Test
	fun explicitPauseThenPlayResumesWithoutPreparedTargetSeek() {
		val paused = playingController().onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Pause
		)

		assertEquals(ReaderReadaloudPlaybackCommand.Pause, paused.readaloudPlaybackCommand)
		assertTrue(paused.controller.state.whispersync.userPaused)
		assertEquals(ReaderWhispersyncPlaybackIntent.Enabled, paused.controller.state.whispersync.playbackIntent)
		assertNull(paused.whispersyncAudioSeekTarget)
		assertTrue(paused.engineCommands.isEmpty())

		val stopped = paused.controller.onReadaloudPlaybackState(stoppedPlaybackState(positionMs = 1_500L)).controller
		val resumed = stopped.onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.Play)

		assertEquals(ReaderReadaloudPlaybackCommand.Play, resumed.readaloudPlaybackCommand)
		assertFalse(resumed.controller.state.whispersync.userPaused)
		assertNull(resumed.whispersyncAudioSeekTarget)
		assertTrue(resumed.engineCommands.isEmpty())
	}

	@Test
	fun pageBoundaryPausesWithoutChangingEnabledIntentOrTurningPage() {
		val ended = startedController().onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = AudioHref,
				positionMs = 3_500L
			)
		)

		assertEquals(ReaderReadaloudPlaybackCommand.Pause, ended.readaloudPlaybackCommand)
		assertEquals(ReaderWhispersyncPlaybackIntent.Enabled, ended.controller.state.whispersync.playbackIntent)
		assertEquals(ReaderWhispersyncTransportPhase.BoundaryPaused, ended.controller.state.whispersync.transportPhase)
		assertFalse(ended.controller.state.whispersync.canStartPlayback)
		assertTrue(ended.engineCommands.none { it is ReaderEngineCommand.TurnPage })
	}

	@Test
	fun boundaryPausedCannotRestartFinishedSpread() {
		val boundaryPaused = startedController().onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = AudioHref,
				positionMs = 3_500L
			)
		).controller

		val play = boundaryPaused.onWhispersyncPlaybackCommand(ReaderReadaloudPlaybackCommand.Play)
		val control = readerWhispersyncPlaybackControlState(
			status = boundaryPaused.state.whispersync.status,
			playbackState = stoppedPlaybackState(),
			hasPreparedVisibleTarget = true,
			transportPhase = ReaderWhispersyncTransportPhase.BoundaryPaused
		)

		assertEquals(boundaryPaused, play.controller)
		assertNull(play.whispersyncAudioSeekTarget)
		assertNull(play.readaloudPlaybackCommand)
		assertTrue(play.engineCommands.isEmpty())
		assertFalse(control.enabled)
		assertNull(control.command)
	}

	@Test
	fun boundaryPausedRejectsLatePlayingCallback() {
		val boundaryPaused = startedController().onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = AudioHref,
				positionMs = 3_500L
			)
		).controller

		val late = boundaryPaused.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = AudioHref,
				positionMs = 3_600L
			),
			publishOverlayProgress = false
		)

		assertEquals(ReaderWhispersyncTransportPhase.BoundaryPaused, late.controller.state.whispersync.transportPhase)
		assertEquals(ReaderWhispersyncPlaybackIntent.Enabled, late.controller.state.whispersync.playbackIntent)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, late.readaloudPlaybackCommand)
		assertNull(late.whispersyncAudioSeekTarget)
		assertNull(late.controller.state.activeMediaOverlay)
	}

	@Test
	fun pendingNavigationRejectsLatePlayingCallbackUntilDestinationCommits() {
		val turn = startedController().onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)

		val late = turn.controller.onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = AudioHref,
				positionMs = 1_600L
			),
			publishOverlayProgress = false
		)

		assertEquals(ReaderWhispersyncTransportPhase.Preparing, late.controller.state.whispersync.transportPhase)
		assertEquals(turn.controller.state.whispersync.pendingCausalIntent, late.controller.state.whispersync.pendingCausalIntent)
		assertEquals(ReaderReadaloudPlaybackCommand.Pause, late.readaloudPlaybackCommand)
		assertNull(late.whispersyncAudioSeekTarget)
		assertNull(late.controller.state.activeMediaOverlay)
	}

	@Test
	fun staleExplicitCueSequenceCannotSeekOrConsumeCurrentIntent() {
		val enabled = startedController()
		val reserved = enabled.onViewerAction(
			ReaderViewerAction.ContentLongPressAt(
				x = 10.0,
				y = 20.0,
				viewWidth = 100.0,
				viewHeight = 100.0
			)
		)
		val sequence = requireNotNull(reserved.controller.state.whispersync.pendingCausalIntent?.sequence)
		assertEquals(
			sequence,
			assertIs<ReaderEngineCommand.ContentLongPressAt>(reserved.engineCommands.single()).causalSequence
		)

		val stale = reserved.controller.onEngineEvent(
			textPoint(causalSequence = sequence + 1L)
		)

		assertNull(stale.whispersyncAudioSeekTarget)
		assertEquals(sequence, stale.controller.state.whispersync.pendingCausalIntent?.sequence)

		val selected = stale.controller.onEngineEvent(textPoint(causalSequence = sequence))
		assertEquals(2_000L, selected.whispersyncAudioSeekTarget?.positionMs)
		assertNull(selected.controller.state.whispersync.pendingCausalIntent)

		val repeated = selected.controller.onEngineEvent(textPoint(causalSequence = sequence))
		assertNull(repeated.whispersyncAudioSeekTarget)
		assertTrue(repeated.engineCommands.isEmpty())
	}

	@Test
	fun stoppedPageTurnPreparesDestinationWithoutPlaying() {
		val turn = preparedController().onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		val sequence = requireNotNull(turn.controller.state.whispersync.pendingCausalIntent?.sequence)
		assertEquals(
			sequence,
			turn.engineCommands.filterIsInstance<ReaderEngineCommand.TurnPage>().single().causalSequence
		)

		val relocated = turn.controller.onEngineEvent(destinationRelocation(sequence)).controller
		val destination = relocated.onEngineEvent(destinationVisibleRange(sequence))

		assertEquals(3_000L, destination.controller.state.whispersync.preparedVisibleTarget?.audioSeekTarget?.positionMs)
		assertEquals(DestinationCommit, destination.controller.state.whispersync.preparedVisibleTarget?.destinationCommitIdentity)
		assertEquals(ReaderWhispersyncPlaybackIntent.UserStopped, destination.controller.state.whispersync.playbackIntent)
		assertEquals(ReaderWhispersyncTransportPhase.Ready, destination.controller.state.whispersync.transportPhase)
		assertNull(destination.whispersyncAudioSeekTarget)
		assertNull(destination.readaloudPlaybackCommand)
		assertTrue(destination.engineCommands.isEmpty())
	}

	@Test
	fun stopRemainsDurableAcrossPageTurn() {
		val stop = startedController().onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.StopAndReset
		)
		val turn = stop.controller.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		val sequence = requireNotNull(turn.controller.state.whispersync.pendingCausalIntent?.sequence)
		val relocated = turn.controller.onEngineEvent(destinationRelocation(sequence)).controller
		val destination = relocated.onEngineEvent(destinationVisibleRange(sequence))

		assertEquals(ReaderWhispersyncPlaybackIntent.UserStopped, destination.controller.state.whispersync.playbackIntent)
		assertEquals(3_000L, destination.controller.state.whispersync.preparedVisibleTarget?.audioSeekTarget?.positionMs)
		assertNull(destination.whispersyncAudioSeekTarget)
		assertNull(destination.readaloudPlaybackCommand)
		assertTrue(destination.engineCommands.isEmpty())
	}

	@Test
	fun boundaryPausedPageTurnResumesOnlyAfterDestinationPresentation() {
		val boundaryPaused = startedController().onReadaloudPlaybackState(
			ReaderReadaloudPlaybackUiState(
				isAvailable = true,
				isPlaying = true,
				audioResource = AudioHref,
				positionMs = 3_500L
			)
		).controller
		val turn = boundaryPaused.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		val sequence = requireNotNull(turn.controller.state.whispersync.pendingCausalIntent?.sequence)
		val relocated = turn.controller.onEngineEvent(destinationRelocation(sequence)).controller
		val destination = relocated.onEngineEvent(destinationVisibleRange(sequence))

		assertNull(destination.whispersyncAudioSeekTarget)
		assertNull(destination.readaloudPlaybackCommand)
		val apply = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(destination.engineCommands.single())

		val confirmed = destination.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(fragment = apply.fragment)
		)

		assertEquals(3_000L, confirmed.whispersyncAudioSeekTarget?.positionMs)
		assertEquals(ReaderReadaloudPlaybackCommand.Play, confirmed.readaloudPlaybackCommand)
	}

	@Test
	fun explicitlyPausedPageTurnPreparesDestinationWithoutAutoPlay() {
		val paused = playingController().onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Pause
		).controller.onReadaloudPlaybackState(stoppedPlaybackState(positionMs = 1_500L)).controller
		val turn = paused.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next))
		val sequence = requireNotNull(turn.controller.state.whispersync.pendingCausalIntent?.sequence)
		val relocated = turn.controller.onEngineEvent(destinationRelocation(sequence)).controller
		val destination = relocated.onEngineEvent(destinationVisibleRange(sequence))

		assertTrue(destination.controller.state.whispersync.userPaused)
		assertEquals(ReaderWhispersyncPlaybackIntent.Enabled, destination.controller.state.whispersync.playbackIntent)
		assertEquals(3_000L, destination.controller.state.whispersync.preparedVisibleTarget?.audioSeekTarget?.positionMs)
		assertNull(destination.whispersyncAudioSeekTarget)
		assertNull(destination.readaloudPlaybackCommand)
		assertTrue(destination.engineCommands.isEmpty())
	}

	@Test
	fun stalePageTurnSequenceCannotCommitPrepareOrSeek() {
		val turn = preparedController().onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		val sequence = requireNotNull(turn.controller.state.whispersync.pendingCausalIntent?.sequence)
		val staleSequence = sequence + 1L
		val staleRelocation = turn.controller.onEngineEvent(
			destinationRelocation(staleSequence)
		)
		assertNull(staleRelocation.readaloudReaderInteraction)
		val uncommitted = staleRelocation.controller
		val staleRange = uncommitted.onEngineEvent(destinationVisibleRange(staleSequence))

		assertNull(staleRange.controller.state.whispersync.preparedVisibleTarget)
		assertFalse(staleRange.controller.state.whispersync.pendingCausalIntent?.destinationCommitted ?: true)
		assertNull(staleRange.whispersyncAudioSeekTarget)
		assertTrue(staleRange.engineCommands.isEmpty())
	}

	@Test
	fun pageTurnWithoutMatchingSettlementCannotPrepareOrSeek() {
		val turn = preparedController().onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		val sequence = requireNotNull(turn.controller.state.whispersync.pendingCausalIntent?.sequence)
		val uncommitted = turn.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(href = TextHref, pageIndex = 1, pageCount = 2),
				foliateSessionId = FoliateSession,
				causalSequence = sequence,
				destinationCommitIdentity = DestinationCommit
			)
		).controller
		val maintenance = uncommitted.onEngineEvent(destinationVisibleRange(sequence))

		assertNull(maintenance.controller.state.whispersync.preparedVisibleTarget)
		assertFalse(maintenance.controller.state.whispersync.pendingCausalIntent?.destinationCommitted ?: true)
		assertNull(maintenance.whispersyncAudioSeekTarget)
		assertTrue(maintenance.engineCommands.isEmpty())
	}

	@Test
	fun currentUntaggedRelocationCannotUseHistoricalSettlementAcknowledgement() {
		val acknowledged = preparedController().onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(href = TextHref, pageIndex = 0, pageCount = 2),
				foliateSessionId = FoliateSession,
				pageTurnSettleToken = "opaque-receipt",
				pageTurnSettleSessionId = FoliateSession,
				pageTurnSettleRasterGeneration = 7L,
				pageTurnSettleTextureGeneration = 8L,
				destinationCommitIdentity = SourceCommit
			)
		).controller
		assertNotNull(acknowledged.state.pageTurnSettlementAck)
		val turn = acknowledged.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		val sequence = requireNotNull(turn.controller.state.whispersync.pendingCausalIntent?.sequence)

		val untagged = turn.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(href = TextHref, pageIndex = 1, pageCount = 2),
				foliateSessionId = FoliateSession,
				causalSequence = sequence,
				destinationCommitIdentity = DestinationCommit
			)
		)

		assertNull(untagged.controller.state.pageTurnSettlementAck)
		assertFalse(untagged.controller.state.whispersync.pendingCausalIntent?.destinationCommitted ?: true)
		assertNull(untagged.readaloudReaderInteraction)
	}

	@Test
	fun matchingCurrentSettlementReceiptCommitsDestinationExactlyOnce() {
		val turn = preparedController().onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		val sequence = requireNotNull(turn.controller.state.whispersync.pendingCausalIntent?.sequence)
		val receipt = destinationRelocation(sequence)

		val committed = turn.controller.onEngineEvent(receipt)
		assertEquals(
			ReaderReadaloudReaderInteraction.UserNavigation(
				textHref = TextHref,
				causalSequence = sequence
			),
			committed.readaloudReaderInteraction
		)
		assertTrue(committed.controller.state.whispersync.pendingCausalIntent?.destinationCommitted == true)

		val duplicate = committed.controller.onEngineEvent(receipt)
		assertNull(duplicate.readaloudReaderInteraction)
		assertTrue(duplicate.controller.state.whispersync.pendingCausalIntent?.destinationCommitted == true)

		val consumed = duplicate.controller.onEngineEvent(destinationVisibleRange(sequence)).controller
		assertNull(consumed.state.whispersync.pendingCausalIntent)
		val unrelatedTurn = consumed.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		val replayed = unrelatedTurn.controller.onEngineEvent(receipt)
		assertFalse(replayed.controller.state.whispersync.pendingCausalIntent?.destinationCommitted ?: true)
		assertNull(replayed.readaloudReaderInteraction)
	}

	@Test
	fun controllerOrderedNavigationCommitsBeforePreparingDestination() {
		val navigation = preparedController().navigateTo(
			ReaderLocator(href = TextHref, chapterProgress = 0.75)
		)
		val sequence = requireNotNull(navigation.controller.state.whispersync.pendingCausalIntent?.sequence)
		val prematureRange = navigation.controller.onEngineEvent(destinationVisibleRange(sequence))
		assertNull(prematureRange.controller.state.whispersync.preparedVisibleTarget)

		val relocatedStep = prematureRange.controller.onEngineEvent(
			ReaderEngineEvent.Relocated(
				locator = ReaderLocator(href = TextHref, chapterProgress = 0.75),
				foliateSessionId = FoliateSession,
				causalSequence = sequence,
				destinationCommitIdentity = DestinationCommit
			)
		)
		assertEquals(
			ReaderReadaloudReaderInteraction.UserNavigation(
				textHref = TextHref,
				causalSequence = sequence
			),
			relocatedStep.readaloudReaderInteraction
		)
		val destination = relocatedStep.controller.onEngineEvent(destinationVisibleRange(sequence))

		assertEquals(3_000L, destination.controller.state.whispersync.preparedVisibleTarget?.audioSeekTarget?.positionMs)
		assertEquals(ReaderWhispersyncEventProvenance.UserNavigation, destination.controller.state.whispersync.lastEventProvenance)
		assertNull(destination.controller.state.whispersync.pendingCausalIntent)
		assertNull(destination.whispersyncAudioSeekTarget)
		assertTrue(destination.engineCommands.isEmpty())
	}

	@Test
	fun navigationInvalidatesPendingSourceActivationAndRejectsLateConfirmation() {
		val sourceStart = preparedController().onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val sourceApply = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(sourceStart.engineCommands.single())

		val turn = sourceStart.controller.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		assertNull(turn.controller.state.whispersync.pendingAudioSeek)
		assertNull(turn.controller.state.whispersync.sync.activeOverlayRequestId)
		assertFalse(turn.controller.state.whispersync.playbackStartPending)
		assertNull(turn.controller.state.activeMediaOverlay)

		val late = turn.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(sourceApply.fragment)
		)
		assertNull(late.whispersyncAudioSeekTarget)
		assertNull(late.readaloudPlaybackCommand)
		assertNull(late.controller.state.activeMediaOverlay)
	}

	@Test
	fun playbackControlUsesPreparedTargetInsteadOfActiveHighlight() {
		val control = readerWhispersyncPlaybackControlState(
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.Ready,
				message = ReaderWhispersyncStatusMessage.Ready
			),
			playbackState = stoppedPlaybackState(),
			hasPreparedVisibleTarget = true,
			transportPhase = ReaderWhispersyncTransportPhase.Ready
		)

		assertTrue(control.enabled)
		assertEquals(ReaderReadaloudPlaybackCommand.Play, control.command)
	}

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
		sourceVisibleRange()
	).controller

	private fun startedController(): ReaderController {
		val start = preparedController().onWhispersyncPlaybackCommand(
			ReaderReadaloudPlaybackCommand.Play
		)
		val apply = assertIs<ReaderEngineCommand.ApplyMediaOverlay>(start.engineCommands.single())
		return start.controller.onEngineEvent(
			ReaderEngineEvent.MediaOverlayActive(fragment = apply.fragment)
		).controller
	}

	private fun playingController(): ReaderController = startedController().onReadaloudPlaybackState(
		ReaderReadaloudPlaybackUiState(
			isAvailable = true,
			isPlaying = true,
			audioResource = AudioHref,
			positionMs = 1_500L
		)
	).controller

	private fun sourceVisibleRange(
		destinationCommitIdentity: ReaderDestinationCommitIdentity = SourceCommit
	): ReaderEngineEvent.VisibleTextRange = ReaderEngineEvent.VisibleTextRange(
		textHref = TextHref,
		visibleStart = 0,
		visibleEnd = 40,
		source = "reader",
		destinationCommitIdentity = destinationCommitIdentity
	)

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

	private fun textPoint(causalSequence: Long): ReaderEngineEvent.TextPoint =
		ReaderEngineEvent.TextPoint(
			textHref = TextHref,
			textOffset = 25,
			source = "reader",
			causalSequence = causalSequence,
			destinationCommitIdentity = SourceCommit
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

	private fun segment(
		id: String,
		startMs: Long,
		endMs: Long,
		textStart: Int,
		textEnd: Int
	): WhispersyncSegment = WhispersyncSegment(
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
		const val AudioHref = "Audio/chapter01.m4b"
		const val TextHref = "Text/chapter01.xhtml"
		const val FoliateSession = "foliate-session"
		val SourceCommit = ReaderDestinationCommitIdentity(FoliateSession, 1L)
		val DestinationCommit = ReaderDestinationCommitIdentity(FoliateSession, 2L)
	}
}
