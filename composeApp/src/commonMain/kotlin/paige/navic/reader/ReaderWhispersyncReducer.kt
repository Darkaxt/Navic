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
	return ReaderControllerStep(
		copy(
			state = state.copy(
				chrome = state.chrome.onReadaloudPlaybackState(playbackState),
				whispersync = currentWhispersync.copy(
					sync = syncState,
					pendingAudioSeek = currentWhispersync.pendingAudioSeek
						?.takeIf { it.overlayRequestId == syncState.activeOverlayRequestId },
					playbackIntent = if (playbackState.isPlaying) {
						ReaderWhispersyncPlaybackIntent.Enabled
					} else {
						currentWhispersync.playbackIntent
					},
					transportPhase = nextTransportPhase,
					lastEventProvenance = ReaderWhispersyncEventProvenance.AudioProgress,
					status = playbackStep?.status ?: currentWhispersync.status
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
	)
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
	val command = readerStep.state.engineCommand
		?.takeIf { readerStep.state.engineCommandKey != currentWhispersync.sync.engineCommandKey }
	val requestId = readerStep.state.activeOverlayRequestId
	val alreadyConfirmed = readerStep.state.hasConfirmedOverlay(requestId)
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
		lastEventProvenance = ReaderWhispersyncEventProvenance.PresentationMaintenance
	)
	return ReaderControllerStep(
		copy(state = state.copy(whispersync = nextWhispersync))
	)
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
	Logger.i(
		WhispersyncSyncLogTag,
		"Whispersync visible range state=received " +
			"matched=true active=${currentWhispersync.sync.activeCueKey != null} " +
			"count=${currentWhispersync.whispersyncSegmentCountLogValue()} " +
			"source=${event.source.whispersyncSourceLogValue()} " +
			"audioFollow=${event.isWhispersyncAudioFollowRange()}"
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

	val destinationCommitIdentity = state.destinationCommitIdentity
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
	val pendingIntent = currentWhispersync.pendingCausalIntent
	val pendingNavigation = pendingIntent?.takeIf {
		it.provenance == ReaderWhispersyncEventProvenance.UserNavigation
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
				status = readerWhispersyncReadyStatus(currentWhispersync.timeline)
			)
		)
	)
	return if (shouldResume && prepared != null) {
		preparedController.beginPreparedWhispersyncPlayback()
	} else {
		ReaderControllerStep(preparedController)
	}
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
