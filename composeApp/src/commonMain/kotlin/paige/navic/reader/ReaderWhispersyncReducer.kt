package paige.navic.reader

import paige.navic.util.core.Logger

internal object ReaderWhispersyncReducer {
	fun onReadaloudPlaybackState(
		controller: ReaderController,
		playbackState: ReaderReadaloudPlaybackUiState,
		publishOverlayProgress: Boolean = true
	): ReaderControllerStep = controller.reduceReadaloudPlaybackState(
		playbackState = playbackState,
		publishOverlayProgress = publishOverlayProgress
	)

	fun onPlaybackCommand(
		controller: ReaderController,
		command: ReaderReadaloudPlaybackCommand
	): ReaderControllerStep = controller.reduceWhispersyncPlaybackCommand(command)

	fun loadSidecar(
		controller: ReaderController,
		sidecar: WhispersyncSidecar
	): ReaderControllerStep = controller.reduceLoadWhispersyncSidecar(sidecar)

	fun reportLoadFailure(
		controller: ReaderController,
		message: ReaderWhispersyncStatusMessage,
		detail: String?
	): ReaderControllerStep = controller.reduceWhispersyncLoadFailure(message, detail)

	fun repairMismatch(controller: ReaderController): ReaderControllerStep =
		controller.reduceRepairWhispersyncMismatch()

	fun onVisibleTextRange(
		controller: ReaderController,
		event: ReaderEngineEvent.VisibleTextRange
	): ReaderControllerStep = controller.reduceVisibleTextRange(event)

	fun onTextPoint(
		controller: ReaderController,
		event: ReaderEngineEvent.TextPoint
	): ReaderControllerStep = controller.reduceTextPoint(event)

	fun toggleCueMap(controller: ReaderController): ReaderControllerStep =
		controller.reduceToggleWhispersyncCueMap()

	fun onCueMapRendered(
		controller: ReaderController,
		event: ReaderEngineEvent.WhispersyncCueMapRendered
	): ReaderControllerStep = controller.reduceWhispersyncCueMapRendered(event)

	fun onCueMapSeekRequested(
		controller: ReaderController,
		event: ReaderEngineEvent.WhispersyncCueMapSeekRequested
	): ReaderControllerStep = controller.reduceWhispersyncCueMapSeekRequested(event)

	fun onCueMapHoldOutcome(
		controller: ReaderController,
		event: ReaderEngineEvent.WhispersyncCueMapHoldOutcome
	): ReaderControllerStep = controller.reduceWhispersyncCueMapHoldOutcome(event)

	fun reserveUserNavigation(
		controller: ReaderController,
		requiresPageTurnSettlement: Boolean = true
	): ReaderController = controller.withWhispersyncCausalIntent(
		provenance = ReaderWhispersyncEventProvenance.UserNavigation,
		requiresPageTurnSettlement = requiresPageTurnSettlement
	)

	fun reserveExplicitCueSelection(controller: ReaderController): ReaderController =
		if (
			controller.state.whispersync.playbackIntent == ReaderWhispersyncPlaybackIntent.Enabled &&
			controller.state.whispersync.available
		) {
			controller.withWhispersyncCausalIntent(
				ReaderWhispersyncEventProvenance.ExplicitCueSelection
			)
		} else {
			controller
		}

	fun onRelocated(
		controller: ReaderController,
		event: ReaderEngineEvent.Relocated
	): ReaderControllerStep = controller.reduceWhispersyncRelocated(event)

	fun onDestinationChanged(
		step: ReaderControllerStep,
		destinationReplaced: Boolean
	): ReaderControllerStep = step.replaceWhispersyncCueMapDestination(destinationReplaced)

	fun onPaginationProfileStatusChanged(
		controller: ReaderController,
		event: ReaderEngineEvent.PaginationProfileStatusChanged
	): ReaderControllerStep = controller.copy(
		state = controller.state.copy(paginationProfile = event.profile)
	).replaceWhispersyncCueMapPresentationContext()

	fun onSettingsPresentationCommitted(
		controller: ReaderController,
		event: ReaderEngineEvent.SettingsPresentationCommitted
	): ReaderControllerStep = controller.copy(
		state = controller.state.copy(readerSettingsPresentationSnapshotKey = event.snapshotKey)
	).replaceWhispersyncCueMapPresentationContext()

	fun cancelCueMapHold(
		controller: ReaderController,
		reason: ReaderWhispersyncCueMapHoldOutcome
	): ReaderControllerStep = if (!controller.state.whispersync.cueMap.enabled) {
		ReaderControllerStep(controller)
	} else {
		ReaderControllerStep(
			controller = controller,
			engineCommands = listOf(ReaderEngineCommand.CancelWhispersyncCueMapHold(reason))
		)
	}
}

private fun ReaderControllerStep.replaceWhispersyncCueMapDestination(
	destinationReplaced: Boolean
): ReaderControllerStep {
	val cueMap = controller.state.whispersync.cueMap
	if (!destinationReplaced || !cueMap.enabled) return this
	val revisionDigest = controller.state.whispersync.sidecar?.revisionDigest.orEmpty()
	val nextCueMap = if (revisionDigest.matches(Regex("[0-9a-f]{12}"))) {
		cueMap.replaced(revisionDigest)
	} else {
		cueMap
	}
	return copy(
		controller = controller.copy(
			state = controller.state.copy(
				whispersync = controller.state.whispersync.copy(cueMap = nextCueMap)
			)
		),
		engineCommands = listOf(
			ReaderEngineCommand.CancelWhispersyncCueMapHold(
				ReaderWhispersyncCueMapHoldOutcome.CancelledGenerationReplacement
			)
		) + engineCommands
	)
}

private fun ReaderController.replaceWhispersyncCueMapPresentationContext(): ReaderControllerStep {
	val cueMap = state.whispersync.cueMap
	val revisionDigest = state.whispersync.sidecar?.revisionDigest.orEmpty()
	val nextCueMap = if (cueMap.enabled && revisionDigest.matches(Regex("[0-9a-f]{12}"))) {
		cueMap.replaced(revisionDigest)
	} else {
		cueMap
	}
	val next = copy(
		state = state.copy(
			whispersync = state.whispersync.copy(cueMap = nextCueMap)
		)
	)
	return ReaderControllerStep(
		controller = next,
		engineCommands = listOfNotNull(next.whispersyncCueMapPresentationCommand())
	)
}

private fun ReaderController.reduceReadaloudPlaybackState(
	playbackState: ReaderReadaloudPlaybackUiState,
	publishOverlayProgress: Boolean
): ReaderControllerStep {
	if (!state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
		return ReaderControllerStep(this)
	}
	if (state.shellCoverVisible) {
		val stoppedPlaybackState = playbackState.copy(
			isPlaying = false,
			positionMs = 0L
		)
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					chrome = state.chrome.onReadaloudPlaybackState(stoppedPlaybackState),
					whispersync = state.whispersync.copy(
						playbackIntent = ReaderWhispersyncPlaybackIntent.UserStopped,
						transportPhase = ReaderWhispersyncTransportPhase.Unavailable,
						playbackStartPending = false,
						stopResetPending = false,
						pendingAudioSeek = null,
						lastEventProvenance = ReaderWhispersyncEventProvenance.AudioProgress
					)
				)
			),
			readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.StopAndReset
				.takeIf { playbackState.isPlaying }
		)
	}

	val currentWhispersync = state.whispersync
	if (currentWhispersync.stopResetPending) {
		if (playbackState.isPlaying) {
			return ReaderControllerStep(
				controller = copy(
					state = state.copy(
						chrome = state.chrome.onReadaloudPlaybackState(
							playbackState.copy(isPlaying = false)
						),
						whispersync = currentWhispersync.copy(
							lastEventProvenance = ReaderWhispersyncEventProvenance.AudioProgress
						)
					)
				),
				readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.StopAndReset
			)
		}
		val resetTarget = currentWhispersync.preparedVisibleTarget?.audioSeekTarget
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					chrome = state.chrome.onReadaloudPlaybackState(playbackState),
					whispersync = currentWhispersync.copy(
						stopResetPending = false,
						transportPhase = if (resetTarget == null) {
							ReaderWhispersyncTransportPhase.Unavailable
						} else {
							ReaderWhispersyncTransportPhase.Ready
						},
						status = readerWhispersyncReadyStatus(currentWhispersync.timeline),
						lastEventProvenance = ReaderWhispersyncEventProvenance.AudioProgress
					)
				)
			),
			whispersyncAudioSeekTarget = resetTarget
		)
	}

	val baseSync = if (currentWhispersync.sync.syncEnabled == playbackState.syncEnabled) {
		currentWhispersync.sync
	} else {
		currentWhispersync.sync.setSyncEnabled(playbackState.syncEnabled)
	}
	val navigationPending = currentWhispersync.pendingCausalIntent?.provenance ==
		ReaderWhispersyncEventProvenance.UserNavigation
	val playbackBlocked =
		currentWhispersync.transportPhase == ReaderWhispersyncTransportPhase.BoundaryPaused ||
			navigationPending
	if (playbackState.isPlaying && playbackBlocked) {
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					chrome = state.chrome.onReadaloudPlaybackState(
						playbackState.copy(isPlaying = false)
					),
					whispersync = currentWhispersync.copy(
						sync = baseSync,
						transportPhase = if (navigationPending) {
							ReaderWhispersyncTransportPhase.Preparing
						} else {
							ReaderWhispersyncTransportPhase.BoundaryPaused
						},
						lastEventProvenance = ReaderWhispersyncEventProvenance.AudioProgress
					)
				)
			),
			readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.Pause
		)
	}
	val playbackStep = if (!playbackState.isPlaying) {
		when {
			currentWhispersync.transportPhase == ReaderWhispersyncTransportPhase.BoundaryPaused ||
				navigationPending -> ReaderWhispersyncPlaybackPositionStep(state = baseSync)
			state.chrome.readaloudPlayback.isPlaying -> baseSync.onAudiobookPlaybackPausedStep(
				audioResource = playbackState.audioResource,
				positionMs = playbackState.positionMs,
				clearPlaybackOverlay = true
			)
			else -> ReaderWhispersyncPlaybackPositionStep(state = baseSync)
		}
	} else {
		playbackState.audioResource
			?.takeIf { it.isNotBlank() }
			?.let { audioResource ->
				baseSync.onAudiobookPlaybackPositionStep(
					timeline = currentWhispersync.timeline,
					audioResource = audioResource,
					audioTrackIndex = playbackState.trackIndex,
					positionMs = playbackState.positionMs,
					playbackSpeed = playbackState.playbackSpeed,
					highlightLeadMs = normalizedReaderWhispersyncHighlightLeadMs(
						state.chrome.settings.whispersyncHighlightLeadMs
					)
				)
			}
	}
	val syncState = playbackStep?.state ?: currentWhispersync.sync
	val command = syncState.engineCommand
		?.takeIf { syncState.engineCommandKey != currentWhispersync.sync.engineCommandKey }
	val overlayFragment = command.overlayFragmentOrNull()
	val visibleRange = currentWhispersync.visibleTextRange
	if (
		publishOverlayProgress &&
		overlayFragment != null &&
		overlayFragment.isOutsideWhispersyncVisibleRange(visibleRange)
	) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync playback state=page-ended matched=false active=false " +
				"reason=outside-visible-range command=pause"
		)
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					chrome = state.chrome.onReadaloudPlaybackState(playbackState.copy(isPlaying = false)),
					whispersync = currentWhispersync.copy(
						sync = syncState.rejectOverlay(null),
						pendingAudioSeek = null,
						playbackIntent = ReaderWhispersyncPlaybackIntent.Enabled,
						transportPhase = ReaderWhispersyncTransportPhase.BoundaryPaused,
						playbackStartPending = false,
						lastEventProvenance = ReaderWhispersyncEventProvenance.AudioProgress,
						status = ReaderWhispersyncStatus(
							kind = ReaderWhispersyncStatusKind.NoActiveCue,
							message = ReaderWhispersyncStatusMessage.VisiblePageEnded,
							detail = overlayFragment.label,
							audioResource = playbackState.audioResource,
							positionMs = playbackState.positionMs
						)
					),
					activeMediaOverlay = null,
					activeMediaOverlayAnchorReceipt = null,
					audioMetadataLabel = null
				)
			),
			engineCommands = listOf(ReaderEngineCommand.ClearMediaOverlay),
			readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.Pause
		)
	}

	val publishedCommand = command.takeIf {
		publishOverlayProgress ||
			(
				it !is ReaderEngineCommand.ApplyMediaOverlay &&
					it !is ReaderEngineCommand.UpdateMediaOverlayProgress
			)
	}
	val publishedOverlayFragment = publishedCommand.overlayFragmentOrNull()
	val publishedClearOverlay = publishedCommand == ReaderEngineCommand.ClearMediaOverlay
	val publishedAnchorReceipt = when (publishedCommand) {
		is ReaderEngineCommand.ApplyMediaOverlay,
		ReaderEngineCommand.ClearMediaOverlay -> null
		else -> state.activeMediaOverlayAnchorReceipt
	}
	if (publishedOverlayFragment != null) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync playback state=overlay-update matched=true active=true " +
				"command=${publishedCommand.whispersyncCommandLogValue()}"
		)
	} else if (publishedClearOverlay) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync apply overlay source=playback command=clear"
		)
	}
	val nextTransportPhase = when {
		playbackState.isPlaying -> ReaderWhispersyncTransportPhase.Playing
		currentWhispersync.transportPhase == ReaderWhispersyncTransportPhase.BoundaryPaused ->
			ReaderWhispersyncTransportPhase.BoundaryPaused
		currentWhispersync.playbackStartPending || navigationPending ->
			ReaderWhispersyncTransportPhase.Preparing
		currentWhispersync.preparedVisibleTarget != null -> ReaderWhispersyncTransportPhase.Ready
		else -> currentWhispersync.transportPhase
	}
	val observedSourceOrdinal = playbackState.audioResource?.let { audioResource ->
		currentWhispersync.timeline?.activeSegment(
			audioResource = audioResource,
			positionMs = playbackState.positionMs,
			audioTrackIndex = playbackState.trackIndex
		)?.sourceOrdinal
	}
	val revisionDigest = currentWhispersync.sidecar?.revisionDigest.orEmpty()
	val acknowledgedCueMap = currentWhispersync.cueMap.transportAcknowledged(
		sourceOrdinal = observedSourceOrdinal,
		revisionDigest = revisionDigest,
		audioResource = playbackState.audioResource,
		audioTrackIndex = playbackState.trackIndex,
		positionMs = playbackState.positionMs
	)
	val cueMapTransportAcknowledged =
		currentWhispersync.cueMap.transportAcknowledgementPending &&
			!acknowledgedCueMap.transportAcknowledgementPending
	val acknowledgedPreparedTarget = if (cueMapTransportAcknowledged) {
		val timeline = currentWhispersync.timeline
		val segment = currentWhispersync.cueMap.requestedTransport
			?.sourceOrdinal
			?.let { sourceOrdinal -> timeline?.segmentForSourceOrdinal(sourceOrdinal) }
		val target = if (timeline == null || segment == null) {
			null
		} else {
			WhispersyncOverlaySyncAdapter(timeline).readerTargetForSegment(segment)
		}
		val destinationCommitIdentity = state.destinationCommitIdentity
		if (target == null || destinationCommitIdentity == null) {
			null
		} else {
			ReaderWhispersyncPreparedVisibleTarget(
				destinationCommitIdentity = destinationCommitIdentity,
				firstVisibleCue = target.cue,
				audioSeekTarget = target.seekTarget,
				preparationGeneration = currentWhispersync.preparationGeneration + 1L
			)
		}
	} else {
		null
	}
	val nextCueMap = acknowledgedCueMap.audioActive(
		sourceOrdinal = observedSourceOrdinal.takeIf { playbackState.isPlaying },
		revisionDigest = revisionDigest
	)
	return ReaderControllerStep(
		copy(
			state = state.copy(
				chrome = state.chrome.onReadaloudPlaybackState(playbackState),
				whispersync = currentWhispersync.copy(
					sync = syncState,
					pendingAudioSeek = currentWhispersync.pendingAudioSeek
						?.takeIf { it.overlayRequestId == syncState.activeOverlayRequestId },
					preparedVisibleTarget =
						acknowledgedPreparedTarget ?: currentWhispersync.preparedVisibleTarget,
					preparationGeneration = acknowledgedPreparedTarget
						?.preparationGeneration ?: currentWhispersync.preparationGeneration,
					playbackIntent = if (playbackState.isPlaying) {
						ReaderWhispersyncPlaybackIntent.Enabled
					} else {
						currentWhispersync.playbackIntent
					},
					transportPhase = nextTransportPhase,
					lastEventProvenance = ReaderWhispersyncEventProvenance.AudioProgress,
					status = when {
						playbackStep?.status != null -> playbackStep.status
						cueMapTransportAcknowledged ->
							readerWhispersyncReadyStatus(currentWhispersync.timeline)
						else -> currentWhispersync.status
					},
					cueMap = nextCueMap
				),
				activeMediaOverlay = publishedCommand.confirmedOverlayOrPrevious(
					state.activeMediaOverlay
				),
				activeMediaOverlayAnchorReceipt = publishedAnchorReceipt,
				audioMetadataLabel = publishedCommand.confirmedOverlayLabelOrPrevious(
					state.audioMetadataLabel
				)
			)
		),
		engineCommands = listOfNotNull(publishedCommand)
	).withWhispersyncCueMapPresentation(previousController = this)
}

private fun ReaderController.reduceWhispersyncPlaybackCommand(
	command: ReaderReadaloudPlaybackCommand
): ReaderControllerStep {
	if (!state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
		return ReaderControllerStep(this)
	}
	return when (command) {
		ReaderReadaloudPlaybackCommand.Play -> resumeOrBeginWhispersyncPlayback()
		ReaderReadaloudPlaybackCommand.Pause -> pauseWhispersyncPlayback()
		ReaderReadaloudPlaybackCommand.StopAndReset -> stopAndResetWhispersyncPlayback()
		else -> ReaderControllerStep(
			controller = this,
			readaloudPlaybackCommand = command
		)
	}
}

private fun ReaderController.resumeOrBeginWhispersyncPlayback(): ReaderControllerStep {
	val current = state.whispersync
	if (current.transportPhase == ReaderWhispersyncTransportPhase.BoundaryPaused) {
		return ReaderControllerStep(this)
	}
	if (
		current.userPaused &&
		current.userPausedDestinationCommitIdentity == state.destinationCommitIdentity
	) {
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					whispersync = current.copy(
						userPaused = false,
						userPausedDestinationCommitIdentity = null
					)
				)
			),
			readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.Play
		)
	}
	return beginPreparedWhispersyncPlayback()
}

private fun ReaderController.pauseWhispersyncPlayback(): ReaderControllerStep {
	val current = state.whispersync
	return ReaderControllerStep(
		controller = copy(
			state = state.copy(
				whispersync = current.copy(
					userPaused = true,
					userPausedDestinationCommitIdentity = state.destinationCommitIdentity,
					playbackStartPending = false,
					pendingAudioSeek = null
				)
			)
		),
		readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.Pause
	)
}

private fun ReaderController.beginPreparedWhispersyncPlayback(): ReaderControllerStep {
	val currentWhispersync = state.whispersync
	val prepared = currentWhispersync.preparedVisibleTarget ?: return ReaderControllerStep(this)
	if (
		state.shellCoverVisible ||
		currentWhispersync.transportPhase == ReaderWhispersyncTransportPhase.BoundaryPaused ||
		prepared.destinationCommitIdentity != state.destinationCommitIdentity
	) {
		return ReaderControllerStep(this)
	}
	val enabledSync = currentWhispersync.sync.setSyncEnabled(true)
	val readerStep = enabledSync.followReaderTarget(prepared.readerTarget())
	val requestId = readerStep.state.activeOverlayRequestId
	val alreadyConfirmed = readerStep.state.hasConfirmedOverlay(requestId)
	val command = readerStep.state.engineCommand?.takeIf {
		readerStep.state.engineCommandKey != currentWhispersync.sync.engineCommandKey ||
			(!alreadyConfirmed && it is ReaderEngineCommand.ApplyMediaOverlay)
	}
	if (alreadyConfirmed) {
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						sync = readerStep.state,
						pendingAudioSeek = null,
						playbackIntent = ReaderWhispersyncPlaybackIntent.Enabled,
						transportPhase = ReaderWhispersyncTransportPhase.Seeking,
						playbackStartPending = false,
						stopResetPending = false,
						userPaused = false,
						userPausedDestinationCommitIdentity = null,
						status = prepared.seekingStatus()
					)
				)
			),
			whispersyncAudioSeekTarget = prepared.audioSeekTarget,
			readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.Play
		)
	}
	if (command == null || requestId == null) return ReaderControllerStep(this)
	return ReaderControllerStep(
		controller = copy(
			state = state.copy(
				whispersync = currentWhispersync.copy(
					sync = readerStep.state,
					pendingAudioSeek = ReaderWhispersyncPendingAudioSeek(
						overlayRequestId = requestId,
						target = prepared.audioSeekTarget
					),
					playbackIntent = ReaderWhispersyncPlaybackIntent.Enabled,
					transportPhase = ReaderWhispersyncTransportPhase.Preparing,
					playbackStartPending = true,
					stopResetPending = false,
					userPaused = false,
					userPausedDestinationCommitIdentity = null,
					status = prepared.seekingStatus()
				),
				activeMediaOverlay = null,
				activeMediaOverlayAnchorReceipt = null,
				audioMetadataLabel = null
			)
		),
		engineCommands = listOf(command)
	)
}

private fun ReaderController.stopAndResetWhispersyncPlayback(): ReaderControllerStep {
	val currentWhispersync = state.whispersync
	return ReaderControllerStep(
		controller = copy(
			state = state.copy(
				whispersync = currentWhispersync.copy(
					sync = currentWhispersync.sync.rejectOverlay(null),
					pendingAudioSeek = null,
					playbackIntent = ReaderWhispersyncPlaybackIntent.UserStopped,
					transportPhase = ReaderWhispersyncTransportPhase.Preparing,
					playbackStartPending = false,
					stopResetPending = true,
					userPaused = false,
					userPausedDestinationCommitIdentity = null,
					pendingCausalIntent = null,
					status = readerWhispersyncReadyStatus(currentWhispersync.timeline)
				),
				activeMediaOverlay = null,
				activeMediaOverlayAnchorReceipt = null,
				audioMetadataLabel = null
			)
		),
		engineCommands = listOf(ReaderEngineCommand.ClearMediaOverlay),
		readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.StopAndReset
	)
}

private fun ReaderController.reduceLoadWhispersyncSidecar(
	sidecar: WhispersyncSidecar
): ReaderControllerStep {
	if (!state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
		return ReaderControllerStep(this)
	}
	val currentWhispersync = state.whispersync
	val visibleRange = currentWhispersync.visibleTextRange
	val nextGeneration = currentWhispersync.preparationGeneration + 1L
	val prepared = state.destinationCommitIdentity?.let { destinationCommitIdentity ->
		visibleRange
			?.takeIf { it.destinationCommitIdentity == destinationCommitIdentity }
			?.preparedTarget(
				timeline = sidecar.timeline,
				destinationCommitIdentity = destinationCommitIdentity,
				preparationGeneration = nextGeneration
			)
	}
	val nextCueMap = if (
		currentWhispersync.cueMap.presentationGeneration > 0L &&
		currentWhispersync.sidecar?.revisionDigest != sidecar.revisionDigest &&
		sidecar.revisionDigest.matches(Regex("[0-9a-f]{12}"))
	) {
		currentWhispersync.cueMap.replaced(sidecar.revisionDigest)
	} else {
		currentWhispersync.cueMap
	}
	val nextWhispersync = currentWhispersync.copy(
		sidecar = sidecar,
		visibleTextRange = visibleRange,
		pendingAudioSeek = null,
		status = readerWhispersyncReadyStatus(sidecar.timeline),
		transportPhase = if (prepared == null) {
			ReaderWhispersyncTransportPhase.Preparing
		} else {
			ReaderWhispersyncTransportPhase.Ready
		},
		preparedVisibleTarget = prepared,
		preparationGeneration = if (prepared == null) {
			currentWhispersync.preparationGeneration
		} else {
			nextGeneration
		},
		lastEventProvenance = ReaderWhispersyncEventProvenance.PresentationMaintenance,
		cueMap = nextCueMap
	)
	return ReaderControllerStep(
		copy(state = state.copy(whispersync = nextWhispersync))
	).withWhispersyncCueMapPresentation(previousController = this)
}

private fun ReaderController.reduceWhispersyncLoadFailure(
	message: ReaderWhispersyncStatusMessage,
	detail: String? = null
): ReaderControllerStep =
	if (!state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
		ReaderControllerStep(this)
	} else {
		ReaderControllerStep(
			copy(
				state = state.copy(
					whispersync = state.whispersync.copy(
						transportPhase = ReaderWhispersyncTransportPhase.Failed,
						status = ReaderWhispersyncStatus(
							kind = ReaderWhispersyncStatusKind.LoadFailed,
							message = message,
							detail = detail?.trim()?.takeIf { it.isNotEmpty() }
						)
					)
				)
			)
		)
	}

private fun ReaderController.reduceRepairWhispersyncMismatch(): ReaderControllerStep {
	if (!state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
		return ReaderControllerStep(this)
	}
	if (state.shellCoverVisible) return ReaderControllerStep(this)
	val currentWhispersync = state.whispersync
	if (!currentWhispersync.status.repairable) return ReaderControllerStep(this)
	val destinationCommitIdentity = state.destinationCommitIdentity ?: return ReaderControllerStep(this)
	val visibleRange = currentWhispersync.visibleTextRange
		?.takeIf { it.destinationCommitIdentity == destinationCommitIdentity }
		?: return ReaderControllerStep(this)
	val target = readerWhispersyncVisibleTarget(
		timeline = currentWhispersync.timeline,
		textHref = visibleRange.textHref,
		visibleStart = visibleRange.visibleStart,
		visibleEnd = visibleRange.visibleEnd
	) ?: return ReaderControllerStep(this)
	val syncStep = currentWhispersync.sync
		.rejectOverlay(null)
		.followReaderTarget(target)
	val command = syncStep.state.engineCommand
		?.takeIf { syncStep.state.engineCommandKey != currentWhispersync.sync.engineCommandKey }
	val nextGeneration = currentWhispersync.preparationGeneration + 1L
	val prepared = ReaderWhispersyncPreparedVisibleTarget(
		destinationCommitIdentity = destinationCommitIdentity,
		firstVisibleCue = target.cue,
		audioSeekTarget = target.seekTarget,
		preparationGeneration = nextGeneration
	)
	return ReaderControllerStep(
		controller = copy(
			state = state.copy(
				whispersync = currentWhispersync.copy(
					sync = syncStep.state,
					pendingAudioSeek = null,
					preparedVisibleTarget = prepared,
					preparationGeneration = nextGeneration,
					transportPhase = ReaderWhispersyncTransportPhase.Ready,
					lastEventProvenance = ReaderWhispersyncEventProvenance.PresentationMaintenance,
					status = readerWhispersyncReadyStatus(currentWhispersync.timeline)
				),
				activeMediaOverlay = command.confirmedOverlayOrPrevious(state.activeMediaOverlay),
				activeMediaOverlayAnchorReceipt = null,
				audioMetadataLabel = command.confirmedOverlayLabelOrPrevious(state.audioMetadataLabel)
			)
		),
		engineCommands = listOfNotNull(command)
	)
}

private fun ReaderController.reduceVisibleTextRange(
	event: ReaderEngineEvent.VisibleTextRange
): ReaderControllerStep {
	val visibleRange = ReaderWhispersyncVisibleTextRange(
		textHref = event.textHref,
		visibleStart = event.visibleStart,
		visibleEnd = event.visibleEnd,
		rangeCfi = event.rangeCfi,
		source = event.source,
		rawProvenanceId = event.rawProvenanceId,
		rawSpineIndex = event.rawSpineIndex,
		rawByteStart = event.rawByteStart,
		rawByteEnd = event.rawByteEnd,
		causalSequence = event.causalSequence,
		destinationCommitIdentity = event.destinationCommitIdentity
	)
	val currentWhispersync = state.whispersync
	if (state.shellCoverVisible) {
		val shouldClearOverlay =
			currentWhispersync.sync.activeOverlayRequestId != null || state.activeMediaOverlay != null
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						sync = currentWhispersync.sync.rejectOverlay(null),
						visibleTextRange = visibleRange,
						pendingAudioSeek = null,
						preparedVisibleTarget = null,
						transportPhase = ReaderWhispersyncTransportPhase.Unavailable,
						lastEventProvenance = ReaderWhispersyncEventProvenance.PresentationMaintenance,
						status = readerWhispersyncReadyStatus(currentWhispersync.timeline)
					),
					activeMediaOverlay = null,
					activeMediaOverlayAnchorReceipt = null,
					audioMetadataLabel = null
				)
			),
			engineCommands = listOfNotNull(
				ReaderEngineCommand.ClearMediaOverlay.takeIf { shouldClearOverlay }
			)
		)
	}
	val destinationCommitIdentity = state.destinationCommitIdentity
	val pendingIntent = currentWhispersync.pendingCausalIntent
	val pendingNavigation = pendingIntent?.takeIf {
		it.provenance == ReaderWhispersyncEventProvenance.UserNavigation
	}
	Logger.i(
		WhispersyncSyncLogTag,
		"Whispersync visible range state=received " +
			"matched=true active=${currentWhispersync.sync.activeCueKey != null} " +
			"count=${currentWhispersync.whispersyncSegmentCountLogValue()} " +
			"source=${event.source.whispersyncSourceLogValue()} " +
			"audioFollow=${event.isWhispersyncAudioFollowRange()} " +
			"destinationAvailable=${destinationCommitIdentity != null} " +
			"destinationMatches=${event.destinationCommitIdentity == destinationCommitIdentity} " +
			"navigationPending=${pendingNavigation != null} " +
			"navigationCommitted=${pendingNavigation?.destinationCommitted == true} " +
			"causalPresent=${event.causalSequence != null} " +
			"causalMatches=${pendingNavigation != null && event.causalSequence == pendingNavigation.sequence}"
	)
	if (event.isWhispersyncAudioFollowRange()) {
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						visibleTextRange = visibleRange,
						lastEventProvenance = ReaderWhispersyncEventProvenance.AudioProgress
					)
				)
			)
		)
	}

	if (
		destinationCommitIdentity == null ||
		event.destinationCommitIdentity != destinationCommitIdentity
	) {
		return ReaderControllerStep(
			copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						lastEventProvenance = ReaderWhispersyncEventProvenance.PresentationMaintenance
					)
				)
			)
		)
	}
	if (pendingNavigation != null && !pendingNavigation.destinationCommitted) {
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						lastEventProvenance = ReaderWhispersyncEventProvenance.PresentationMaintenance
					)
				)
			)
		)
	}
	val navigationMatched = pendingNavigation?.takeIf {
		it.destinationCommitted &&
			event.causalSequence != null &&
			event.causalSequence == it.sequence &&
			it.destinationCommitIdentity == destinationCommitIdentity
	}
	if (pendingNavigation != null && navigationMatched == null) {
		return ReaderControllerStep(
			copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						lastEventProvenance = ReaderWhispersyncEventProvenance.PresentationMaintenance
					)
				)
			)
		)
	}
	val nextGeneration = currentWhispersync.preparationGeneration + 1L
	val destinationReplaced = currentWhispersync.visibleTextRange?.destinationCommitIdentity !=
		event.destinationCommitIdentity
	val revisionDigest = currentWhispersync.sidecar?.revisionDigest.orEmpty()
	val nextCueMap = if (
		destinationReplaced && currentWhispersync.cueMap.enabled &&
		revisionDigest.matches(Regex("[0-9a-f]{12}"))
	) {
		currentWhispersync.cueMap.replaced(revisionDigest)
	} else {
		currentWhispersync.cueMap
	}
	val prepared = visibleRange.preparedTarget(
		timeline = currentWhispersync.timeline,
		destinationCommitIdentity = destinationCommitIdentity,
		preparationGeneration = nextGeneration
	)
	val shouldResume = navigationMatched != null &&
		currentWhispersync.playbackIntent == ReaderWhispersyncPlaybackIntent.Enabled &&
		!currentWhispersync.userPaused
	val nextTransport = when {
		prepared == null -> ReaderWhispersyncTransportPhase.Unavailable
		shouldResume -> ReaderWhispersyncTransportPhase.Preparing
		currentWhispersync.transportPhase == ReaderWhispersyncTransportPhase.Playing ->
			ReaderWhispersyncTransportPhase.Playing
		currentWhispersync.transportPhase == ReaderWhispersyncTransportPhase.BoundaryPaused ->
			ReaderWhispersyncTransportPhase.BoundaryPaused
		else -> ReaderWhispersyncTransportPhase.Ready
	}
	val preparedController = copy(
		state = state.copy(
			whispersync = currentWhispersync.copy(
				visibleTextRange = visibleRange,
				preparedVisibleTarget = prepared,
				preparationGeneration = if (prepared == null) {
					currentWhispersync.preparationGeneration
				} else {
					nextGeneration
				},
				pendingCausalIntent = pendingIntent.takeUnless { navigationMatched != null },
				playbackStartPending = false,
				transportPhase = nextTransport,
				lastEventProvenance = if (navigationMatched != null) {
					ReaderWhispersyncEventProvenance.UserNavigation
				} else {
					ReaderWhispersyncEventProvenance.PresentationMaintenance
				},
				status = readerWhispersyncReadyStatus(currentWhispersync.timeline),
				cueMap = nextCueMap
			)
		)
	)
	val step = if (shouldResume && prepared != null) {
		preparedController.beginPreparedWhispersyncPlayback()
	} else {
		ReaderControllerStep(preparedController)
	}
	return step.withWhispersyncCueMapPresentation(previousController = this)
}

private fun ReaderController.reduceToggleWhispersyncCueMap(): ReaderControllerStep {
	if (state.shellCoverVisible || !state.whispersync.available) return ReaderControllerStep(this)
	val revisionDigest = state.whispersync.sidecar?.revisionDigest
		?.takeIf { it.matches(Regex("[0-9a-f]{12}")) }
		?: return ReaderControllerStep(this)
	val nextCueMap = state.whispersync.cueMap.toggled(revisionDigest)
	val next = copy(
		state = state.copy(
			whispersync = state.whispersync.copy(cueMap = nextCueMap)
		)
	)
	return ReaderControllerStep(
		controller = next,
		engineCommands = listOfNotNull(next.whispersyncCueMapPresentationCommand())
	)
}

private fun ReaderController.reduceWhispersyncCueMapRendered(
	event: ReaderEngineEvent.WhispersyncCueMapRendered
): ReaderControllerStep {
	if (!matchesCurrentCueMapPresentation(
			sourceRevisionDigest = event.revisionDigest,
			sourcePresentationGeneration = event.presentationGeneration,
			sourceDestination = event.destinationCommitIdentity
		)) return ReaderControllerStep(this)
	val visibleOrdinals = state.whispersync.cueMap.presentation(state)
		?.cues?.map(ReaderWhispersyncCueMapCue::sourceOrdinal)
		?.toSet()
		?: return ReaderControllerStep(this)
	if (event.sourceOrdinalsInDomReadingOrder.any { it !in visibleOrdinals }) {
		return ReaderControllerStep(this)
	}
	val geometryReceipt = event.markerReceipts.takeIf { markers -> markers.isNotEmpty() }
		?.let { markers ->
			runCatching {
				ReaderWhispersyncCueMapGeometryReceipt(
					revisionDigest = event.revisionDigest,
					presentationGeneration = event.presentationGeneration,
					destinationCommitIdentity = event.destinationCommitIdentity,
					markers = markers
				)
			}.getOrNull() ?: return ReaderControllerStep(this)
		}
	return ReaderControllerStep(
		copy(
			state = state.copy(
				whispersync = state.whispersync.copy(
					cueMap = state.whispersync.cueMap.rendered(
						sourceOrdinals = event.sourceOrdinalsInDomReadingOrder,
						revisionDigest = event.revisionDigest,
						geometryReceipt = geometryReceipt
					)
				)
			)
		)
	)
}

private fun ReaderController.reduceWhispersyncCueMapSeekRequested(
	event: ReaderEngineEvent.WhispersyncCueMapSeekRequested
): ReaderControllerStep {
	if (!matchesCurrentCueMapPresentation(
			sourceRevisionDigest = event.revisionDigest,
			sourcePresentationGeneration = event.presentationGeneration,
			sourceDestination = event.destinationCommitIdentity
		)) return ReaderControllerStep(this)
	val currentWhispersync = state.whispersync
	if (currentWhispersync.cueMap.transportAcknowledgementPending) return ReaderControllerStep(this)
	val presentation = currentWhispersync.cueMap.presentation(state) ?: return ReaderControllerStep(this)
	if (presentation.cues.none { it.sourceOrdinal == event.sourceOrdinal }) return ReaderControllerStep(this)
	val timeline = currentWhispersync.timeline ?: return ReaderControllerStep(this)
	val segment = timeline.segmentForSourceOrdinal(event.sourceOrdinal) ?: return ReaderControllerStep(this)
	val target = WhispersyncOverlaySyncAdapter(timeline).readerTargetForSegment(segment)
	val syncStep = currentWhispersync.sync
		.setSyncEnabled(true)
		.followReaderTarget(target)
	val seekTarget = syncStep.seekTarget ?: return ReaderControllerStep(this)
	val overlayCommand = syncStep.state.engineCommand
		?.takeIf { syncStep.state.engineCommandKey != currentWhispersync.sync.engineCommandKey }
	val nextCueMap = currentWhispersync.cueMap.requested(
		sourceOrdinal = event.sourceOrdinal,
		revisionDigest = event.revisionDigest,
		audioResource = seekTarget.audioResource,
		audioTrackIndex = seekTarget.audioTrackIndex,
		positionMs = seekTarget.positionMs
	)
	val next = copy(
		state = state.copy(
			whispersync = currentWhispersync.copy(
				sync = syncStep.state,
				pendingAudioSeek = null,
				transportPhase = ReaderWhispersyncTransportPhase.Seeking,
				lastEventProvenance = ReaderWhispersyncEventProvenance.ExplicitCueSelection,
				status = ReaderWhispersyncStatus(
					kind = ReaderWhispersyncStatusKind.SeekingAudio,
					message = ReaderWhispersyncStatusMessage.SeekingAudio,
					audioResource = segment.audioResource,
					positionMs = segment.startMs
				),
				cueMap = nextCueMap
			),
			activeMediaOverlay = overlayCommand.confirmedOverlayOrPrevious(state.activeMediaOverlay),
			activeMediaOverlayAnchorReceipt = when (overlayCommand) {
				is ReaderEngineCommand.ApplyMediaOverlay,
				ReaderEngineCommand.ClearMediaOverlay -> null
				else -> state.activeMediaOverlayAnchorReceipt
			},
			audioMetadataLabel = overlayCommand.confirmedOverlayLabelOrPrevious(
				state.audioMetadataLabel
			)
		)
	)
	return ReaderControllerStep(
		controller = next,
		engineCommands = listOfNotNull(overlayCommand) +
			listOfNotNull(next.whispersyncCueMapPresentationCommand()),
		whispersyncAudioSeekTarget = seekTarget
	)
}

private fun ReaderController.reduceWhispersyncCueMapHoldOutcome(
	event: ReaderEngineEvent.WhispersyncCueMapHoldOutcome
): ReaderControllerStep {
	val cueMap = state.whispersync.cueMap
	val revisionDigest = state.whispersync.sidecar?.revisionDigest
	if (
		!cueMap.enabled || event.revisionDigest != revisionDigest ||
		event.presentationGeneration != cueMap.presentationGeneration
	) {
		return ReaderControllerStep(this)
	}
	return ReaderControllerStep(
		copy(
			state = state.copy(
				whispersync = state.whispersync.copy(
					cueMap = cueMap.holdOutcome(
						sourceOrdinal = event.sourceOrdinal,
						revisionDigest = event.revisionDigest,
						outcome = event.outcome
					)
				)
			)
		)
	)
}

private fun ReaderController.matchesCurrentCueMapPresentation(
	sourceRevisionDigest: String,
	sourcePresentationGeneration: Long,
	sourceDestination: ReaderDestinationCommitIdentity
): Boolean {
	val current = state.whispersync
	return current.cueMap.enabled &&
		current.sidecar?.revisionDigest == sourceRevisionDigest &&
		current.cueMap.presentationGeneration == sourcePresentationGeneration &&
		state.destinationCommitIdentity == sourceDestination
}

private fun ReaderController.whispersyncCueMapPresentationCommand(): ReaderEngineCommand? =
	state.whispersync.cueMap.presentation(state)
		?.let(ReaderEngineCommand::ReplaceWhispersyncCueMap)

private fun ReaderControllerStep.withWhispersyncCueMapPresentation(
	previousController: ReaderController
): ReaderControllerStep {
	val presentation = controller.state.whispersync.cueMap.presentation(controller.state) ?: return this
	val previousPresentation = previousController.state.whispersync.cueMap
		.presentation(previousController.state)
	if (presentation == previousPresentation) {
		return copy(
			engineCommands = engineCommands.filterNot {
				it is ReaderEngineCommand.ReplaceWhispersyncCueMap
			}
		)
	}
	val command = ReaderEngineCommand.ReplaceWhispersyncCueMap(presentation)
	return copy(
		engineCommands = engineCommands
			.filterNot { it is ReaderEngineCommand.ReplaceWhispersyncCueMap } + command
	)
}

private fun ReaderController.reduceTextPoint(
	event: ReaderEngineEvent.TextPoint
): ReaderControllerStep {
	if (state.shellCoverVisible) return ReaderControllerStep(this)
	val currentWhispersync = state.whispersync
	val causalIntent = currentWhispersync.pendingCausalIntent
	val explicitSelection = causalIntent?.provenance ==
		ReaderWhispersyncEventProvenance.ExplicitCueSelection &&
		event.causalSequence != null &&
		event.causalSequence == causalIntent.sequence &&
		event.destinationCommitIdentity != null &&
		event.destinationCommitIdentity == state.destinationCommitIdentity
	Logger.i(
		WhispersyncSyncLogTag,
		"Whispersync text point state=received matched=$explicitSelection " +
			"active=${currentWhispersync.sync.activeCueKey != null} " +
			"source=${event.source.whispersyncSourceLogValue()}"
	)
	if (!explicitSelection) {
		return ReaderControllerStep(
			copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						lastEventProvenance = ReaderWhispersyncEventProvenance.PresentationMaintenance
					)
				)
			)
		)
	}
	val consumedWhispersync = currentWhispersync.copy(
		pendingCausalIntent = null,
		lastEventProvenance = ReaderWhispersyncEventProvenance.ExplicitCueSelection
	)
	if (currentWhispersync.playbackIntent != ReaderWhispersyncPlaybackIntent.Enabled) {
		return ReaderControllerStep(copy(state = state.copy(whispersync = consumedWhispersync)))
	}
	val syncStep = currentWhispersync.sync.onTextPoint(
		timeline = currentWhispersync.timeline,
		textHref = event.textHref,
		textOffset = event.textOffset
	)
	val target = syncStep.audioSeekTarget
		?: return ReaderControllerStep(copy(state = state.copy(whispersync = consumedWhispersync)))
	val command = syncStep.state.engineCommand
		?.takeIf { syncStep.state.engineCommandKey != currentWhispersync.sync.engineCommandKey }
	return ReaderControllerStep(
		controller = copy(
			state = state.copy(
				whispersync = consumedWhispersync.copy(
					sync = syncStep.state,
					pendingAudioSeek = null,
					transportPhase = ReaderWhispersyncTransportPhase.Seeking,
					status = syncStep.status ?: consumedWhispersync.status
				),
				activeMediaOverlay = command.confirmedOverlayOrPrevious(state.activeMediaOverlay),
				activeMediaOverlayAnchorReceipt = when (command) {
					is ReaderEngineCommand.ApplyMediaOverlay,
					ReaderEngineCommand.ClearMediaOverlay -> null
					else -> state.activeMediaOverlayAnchorReceipt
				},
				audioMetadataLabel = command.confirmedOverlayLabelOrPrevious(state.audioMetadataLabel)
			)
		),
		engineCommands = listOfNotNull(command),
		whispersyncAudioSeekTarget = target
	)
}

private fun ReaderController.withWhispersyncCausalIntent(
	provenance: ReaderWhispersyncEventProvenance,
	requiresPageTurnSettlement: Boolean = false
): ReaderController {
	val currentWhispersync = state.whispersync
	val nextSequence = currentWhispersync.causalIntentSequence + 1L
	val navigation = provenance == ReaderWhispersyncEventProvenance.UserNavigation
	return copy(
		state = state.copy(
			whispersync = currentWhispersync.copy(
				sync = if (navigation) {
					currentWhispersync.sync.rejectOverlay(null)
				} else {
					currentWhispersync.sync
				},
				pendingAudioSeek = if (navigation) null else currentWhispersync.pendingAudioSeek,
				visibleTextRange = currentWhispersync.visibleTextRange.takeUnless { navigation },
				pendingCausalIntent = ReaderWhispersyncCausalIntent(
					sequence = nextSequence,
					provenance = provenance,
					requiresPageTurnSettlement = requiresPageTurnSettlement
				),
				causalIntentSequence = nextSequence,
				preparedVisibleTarget = currentWhispersync.preparedVisibleTarget.takeUnless { navigation },
				transportPhase = if (navigation && currentWhispersync.available) {
					ReaderWhispersyncTransportPhase.Preparing
				} else {
					currentWhispersync.transportPhase
				},
				playbackStartPending = if (navigation) false else currentWhispersync.playbackStartPending,
				lastEventProvenance = provenance
			),
			activeMediaOverlay = state.activeMediaOverlay.takeUnless { navigation },
			activeMediaOverlayAnchorReceipt = state.activeMediaOverlayAnchorReceipt.takeUnless { navigation },
			audioMetadataLabel = state.audioMetadataLabel.takeUnless { navigation }
		)
	)
}

private fun ReaderController.reduceWhispersyncRelocated(
	event: ReaderEngineEvent.Relocated
): ReaderControllerStep {
	val currentWhispersync = state.whispersync
	val pending = currentWhispersync.pendingCausalIntent?.takeIf {
		it.provenance == ReaderWhispersyncEventProvenance.UserNavigation
	}
	val destinationCommitIdentity = event.destinationCommitIdentity
	val sequenceMatched = pending != null &&
		event.causalSequence != null &&
		event.causalSequence == pending.sequence
	val destinationMatched = destinationCommitIdentity != null &&
		destinationCommitIdentity == state.destinationCommitIdentity
	val settlementMatched = pending?.requiresPageTurnSettlement != true ||
		state.pageTurnSettlementAck?.let { ack ->
			event.pageTurnSettleToken != null &&
				ack.token == event.pageTurnSettleToken &&
				ack.foliateSessionId == event.foliateSessionId
		} == true
	if (!sequenceMatched || !destinationMatched || !settlementMatched) {
		return ReaderControllerStep(
			copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						lastEventProvenance = ReaderWhispersyncEventProvenance.PresentationMaintenance
					)
				)
			)
		)
	}
	val shouldClearOverlay =
		currentWhispersync.sync.activeOverlayRequestId != null || state.activeMediaOverlay != null
	return ReaderControllerStep(
		controller = copy(
			state = state.copy(
				whispersync = currentWhispersync.copy(
					sync = currentWhispersync.sync.rejectOverlay(null),
					pendingAudioSeek = null,
					preparedVisibleTarget = null,
					pendingCausalIntent = pending.copy(
						destinationCommitted = true,
						destinationCommitIdentity = destinationCommitIdentity
					),
					transportPhase = ReaderWhispersyncTransportPhase.Preparing,
					lastEventProvenance = ReaderWhispersyncEventProvenance.UserNavigation
				),
				activeMediaOverlay = null,
				activeMediaOverlayAnchorReceipt = null,
				audioMetadataLabel = null
			)
		),
		engineCommands = listOfNotNull(
			ReaderEngineCommand.ClearMediaOverlay.takeIf { shouldClearOverlay }
		),
		readaloudReaderInteraction = event.locator.href
			?.takeIf { it.isNotBlank() }
			?.let { href ->
				ReaderReadaloudReaderInteraction.UserNavigation(
					textHref = href,
					causalSequence = pending.sequence
				)
			}
	)
}

private fun ReaderWhispersyncVisibleTextRange.preparedTarget(
	timeline: WhispersyncTimeline?,
	destinationCommitIdentity: ReaderDestinationCommitIdentity,
	preparationGeneration: Long
): ReaderWhispersyncPreparedVisibleTarget? {
	val target = readerWhispersyncVisibleTarget(
		timeline = timeline,
		textHref = textHref,
		visibleStart = visibleStart,
		visibleEnd = visibleEnd
	) ?: return null
	return ReaderWhispersyncPreparedVisibleTarget(
		destinationCommitIdentity = destinationCommitIdentity,
		firstVisibleCue = target.cue,
		audioSeekTarget = target.seekTarget,
		preparationGeneration = preparationGeneration
	)
}

private fun ReaderWhispersyncPreparedVisibleTarget.seekingStatus(): ReaderWhispersyncStatus =
	ReaderWhispersyncStatus(
		kind = ReaderWhispersyncStatusKind.SeekingAudio,
		message = ReaderWhispersyncStatusMessage.SeekingAudio,
		detail = audioSeekTarget.segment.label,
		audioResource = audioSeekTarget.audioResource,
		positionMs = audioSeekTarget.positionMs
	)

private fun ReaderEngineCommand?.confirmedOverlayOrPrevious(
	previous: ReaderOverlayFragment?
): ReaderOverlayFragment? =
	when (this) {
		is ReaderEngineCommand.ApplyMediaOverlay -> null
		is ReaderEngineCommand.UpdateMediaOverlayProgress -> fragment
		ReaderEngineCommand.ClearMediaOverlay -> null
		else -> previous
	}

private fun ReaderEngineCommand?.confirmedOverlayLabelOrPrevious(
	previous: String?
): String? =
	when (this) {
		is ReaderEngineCommand.ApplyMediaOverlay -> null
		is ReaderEngineCommand.UpdateMediaOverlayProgress -> fragment.label ?: previous
		ReaderEngineCommand.ClearMediaOverlay -> null
		else -> previous
	}
