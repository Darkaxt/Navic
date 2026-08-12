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
					chrome = state.chrome.onReadaloudPlaybackState(stoppedPlaybackState)
				)
			),
			readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.StopAndReset
				.takeIf { playbackState.isPlaying }
		)
	}
	val currentWhispersync = state.whispersync
	val baseSync = if (currentWhispersync.sync.syncEnabled == playbackState.syncEnabled) {
		currentWhispersync.sync
	} else {
		currentWhispersync.sync.setSyncEnabled(playbackState.syncEnabled)
	}
	val playbackStep = if (!playbackState.isPlaying) {
		if (state.chrome.readaloudPlayback.isPlaying) {
			baseSync.onAudiobookPlaybackPausedStep(
				audioResource = playbackState.audioResource,
				positionMs = playbackState.positionMs,
				clearPlaybackOverlay = true
			)
		} else {
			ReaderWhispersyncPlaybackPositionStep(state = baseSync)
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
	val publishedAnchorReceipt = if (publishedClearOverlay) {
		null
	} else {
		state.activeMediaOverlayAnchorReceipt
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
	return ReaderControllerStep(
		copy(
			state = state.copy(
				chrome = state.chrome.onReadaloudPlaybackState(playbackState),
				whispersync = currentWhispersync.copy(
					sync = syncState,
					pendingAudioSeek = currentWhispersync.pendingAudioSeek
						?.takeIf { it.overlayRequestId == syncState.activeOverlayRequestId },
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

private fun ReaderController.reduceLoadWhispersyncSidecar(sidecar: WhispersyncSidecar): ReaderControllerStep {
	if (!state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
		return ReaderControllerStep(this)
	}
	val currentWhispersync = state.whispersync
	val visibleRange = currentWhispersync.visibleTextRange
	val baseWhispersync = ReaderWhispersyncSessionState(
		sidecar = sidecar,
		visibleTextRange = visibleRange,
		status = readerWhispersyncReadyStatus(sidecar.timeline)
	)
	val syncStep = visibleRange
		?.takeUnless { state.shellCoverVisible }
		?.let { range ->
			baseWhispersync.sync.onVisibleTextRange(
				timeline = sidecar.timeline,
				textHref = range.textHref,
				visibleStart = range.visibleStart,
				visibleEnd = range.visibleEnd
			)
		}
	val command = syncStep?.state?.engineCommand
	val seekDelivery = syncStep?.seekDelivery()
	return ReaderControllerStep(
		copy(
			state = state.copy(
				whispersync = baseWhispersync.copy(
					sync = syncStep?.state ?: baseWhispersync.sync,
					pendingAudioSeek = seekDelivery?.pending,
					status = syncStep?.status ?: baseWhispersync.status
				),
				activeMediaOverlay = command.confirmedOverlayOrPrevious(
					state.activeMediaOverlay
				),
				audioMetadataLabel = command.confirmedOverlayLabelOrPrevious(
					state.audioMetadataLabel
				)
			)
		),
		engineCommands = listOfNotNull(command),
		whispersyncAudioSeekTarget = seekDelivery?.immediate
	)
}

private fun ReaderController.reduceWhispersyncLoadFailure(
	message: ReaderWhispersyncStatusMessage,
	detail: String? = null
): ReaderControllerStep =
	if (!state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
		ReaderControllerStep(this)
	} else ReaderControllerStep(
		copy(
			state = state.copy(
				whispersync = state.whispersync.copy(
					status = ReaderWhispersyncStatus(
						kind = ReaderWhispersyncStatusKind.LoadFailed,
						message = message,
						detail = detail?.trim()?.takeIf { it.isNotEmpty() }
					)
				)
			)
		)
	)

private fun ReaderController.reduceRepairWhispersyncMismatch(): ReaderControllerStep {
	if (!state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
		return ReaderControllerStep(this)
	}
	if (state.shellCoverVisible) return ReaderControllerStep(this)
	val currentWhispersync = state.whispersync
	if (!currentWhispersync.status.repairable) {
		return ReaderControllerStep(this)
	}
	val visibleRange = currentWhispersync.visibleTextRange
		?: return ReaderControllerStep(this)
	val syncStep = currentWhispersync.sync
		.copy(activeCueKey = null)
		.onVisibleTextRange(
			timeline = currentWhispersync.timeline,
			textHref = visibleRange.textHref,
			visibleStart = visibleRange.visibleStart,
			visibleEnd = visibleRange.visibleEnd
		)
	val command = syncStep.state.engineCommand
		?.takeIf { syncStep.state.engineCommandKey != currentWhispersync.sync.engineCommandKey }
	val seekDelivery = syncStep.seekDelivery()
	val progress = syncStep.audioSeekTarget?.let {
		state.publication?.let { publication ->
			state.chrome.currentLocator?.toBinderyReadingProgress(
				bookId = publication.bookId,
				resourceHref = publication.resourceHref,
				kind = publication.kind
			)
		}
	}
	return ReaderControllerStep(
		controller = copy(
			state = state.copy(
				whispersync = currentWhispersync.copy(
					sync = syncStep.state,
					pendingAudioSeek = seekDelivery.pendingOrRetained(
						current = currentWhispersync.pendingAudioSeek,
						activeOverlayRequestId = syncStep.state.activeOverlayRequestId
					),
					status = syncStep.status ?: currentWhispersync.status
				),
				activeMediaOverlay = command.confirmedOverlayOrPrevious(
					state.activeMediaOverlay
				),
				audioMetadataLabel = command.confirmedOverlayLabelOrPrevious(
					state.audioMetadataLabel
				)
			)
		),
		engineCommands = listOfNotNull(command),
		progressToSave = progress,
		whispersyncAudioSeekTarget = seekDelivery.immediate
	)
}

private fun ReaderController.reduceVisibleTextRange(event: ReaderEngineEvent.VisibleTextRange): ReaderControllerStep {
	val visibleRange = ReaderWhispersyncVisibleTextRange(
		textHref = event.textHref,
		visibleStart = event.visibleStart,
		visibleEnd = event.visibleEnd,
		rangeCfi = event.rangeCfi,
		source = event.source
	)
	val currentWhispersync = state.whispersync
	if (state.shellCoverVisible) {
		val shouldClearOverlay =
			currentWhispersync.sync.activeOverlayRequestId != null ||
				state.activeMediaOverlay != null
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						sync = currentWhispersync.sync.rejectOverlay(null),
						visibleTextRange = visibleRange,
						pendingAudioSeek = null,
						status = readerWhispersyncReadyStatus(
							currentWhispersync.timeline
						)
					),
					activeMediaOverlay = null,
					activeMediaOverlayAnchorReceipt = null,
					audioMetadataLabel = null
				)
			),
			engineCommands = listOfNotNull(
				ReaderEngineCommand.ClearMediaOverlay.takeIf {
					shouldClearOverlay
				}
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
						visibleTextRange = visibleRange
					)
				)
			)
		)
	}
	val syncStep = currentWhispersync.sync.onVisibleTextRange(
		timeline = currentWhispersync.timeline,
		textHref = event.textHref,
		visibleStart = event.visibleStart,
		visibleEnd = event.visibleEnd
	)
	val command = syncStep.state.engineCommand
		?.takeIf { syncStep.state.engineCommandKey != currentWhispersync.sync.engineCommandKey }
	val seekDelivery = syncStep.seekDelivery()
	val overlayFragment = command.overlayFragmentOrNull()
	val shouldClearOverlay = command == ReaderEngineCommand.ClearMediaOverlay
	if (overlayFragment != null) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync visible range state=overlay-update matched=true active=true " +
				"command=${command.whispersyncCommandLogValue()}"
		)
	} else if (shouldClearOverlay) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync apply overlay source=visible-range command=clear"
		)
	}
	val progress = syncStep.audioSeekTarget?.let {
		state.publication?.let { publication ->
			state.chrome.currentLocator?.toBinderyReadingProgress(
				bookId = publication.bookId,
				resourceHref = publication.resourceHref,
				kind = publication.kind
			)
		}
	}
	return ReaderControllerStep(
		controller = copy(
			state = state.copy(
				whispersync = currentWhispersync.copy(
					sync = syncStep.state,
					visibleTextRange = visibleRange,
					pendingAudioSeek = seekDelivery.pendingOrRetained(
						current = currentWhispersync.pendingAudioSeek,
						activeOverlayRequestId = syncStep.state.activeOverlayRequestId
					),
					status = syncStep.status ?: currentWhispersync.status
				),
				activeMediaOverlay = command.confirmedOverlayOrPrevious(
					state.activeMediaOverlay
				),
				audioMetadataLabel = command.confirmedOverlayLabelOrPrevious(
					state.audioMetadataLabel
				)
			)
		),
		engineCommands = listOfNotNull(command),
		progressToSave = progress,
		whispersyncAudioSeekTarget = seekDelivery.immediate
	)
}

private fun ReaderController.reduceTextPoint(event: ReaderEngineEvent.TextPoint): ReaderControllerStep {
	if (state.shellCoverVisible) return ReaderControllerStep(this)
	val currentWhispersync = state.whispersync
	Logger.i(
		WhispersyncSyncLogTag,
		"Whispersync text point state=received matched=true " +
			"active=${currentWhispersync.sync.activeCueKey != null} " +
			"source=${event.source.whispersyncSourceLogValue()}"
	)
	val syncStep = currentWhispersync.sync.onTextPoint(
		timeline = currentWhispersync.timeline,
		textHref = event.textHref,
		textOffset = event.textOffset
	)
	val command = syncStep.state.engineCommand
		?.takeIf { syncStep.state.engineCommandKey != currentWhispersync.sync.engineCommandKey }
	val seekDelivery = syncStep.seekDelivery()
	val overlayFragment = command.overlayFragmentOrNull()
	if (overlayFragment != null) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync text point state=overlay-update matched=true active=true " +
				"command=${command.whispersyncCommandLogValue()}"
		)
	}
	return ReaderControllerStep(
		controller = copy(
			state = state.copy(
				whispersync = currentWhispersync.copy(
					sync = syncStep.state,
					pendingAudioSeek = seekDelivery.pendingOrRetained(
						current = currentWhispersync.pendingAudioSeek,
						activeOverlayRequestId = syncStep.state.activeOverlayRequestId
					),
					status = syncStep.status ?: currentWhispersync.status
				),
				activeMediaOverlay = command.confirmedOverlayOrPrevious(
					state.activeMediaOverlay
				),
				audioMetadataLabel = command.confirmedOverlayLabelOrPrevious(
					state.audioMetadataLabel
				)
			)
		),
		engineCommands = listOfNotNull(command),
		whispersyncAudioSeekTarget = seekDelivery.immediate
	)
}

private data class ReaderWhispersyncSeekDelivery(
	val pending: ReaderWhispersyncPendingAudioSeek? = null,
	val immediate: WhispersyncAudioSeekTarget? = null
)

private fun ReaderWhispersyncVisibleRangeStep.seekDelivery(): ReaderWhispersyncSeekDelivery {
	val target = audioSeekTarget ?: return ReaderWhispersyncSeekDelivery()
	val requestId = state.activeOverlayRequestId ?: return ReaderWhispersyncSeekDelivery()
	return if (state.hasConfirmedOverlay(requestId)) {
		ReaderWhispersyncSeekDelivery(immediate = target)
	} else {
		ReaderWhispersyncSeekDelivery(
			pending = ReaderWhispersyncPendingAudioSeek(
				overlayRequestId = requestId,
				target = target
			)
		)
	}
}

private fun ReaderWhispersyncSeekDelivery.pendingOrRetained(
	current: ReaderWhispersyncPendingAudioSeek?,
	activeOverlayRequestId: Long?
): ReaderWhispersyncPendingAudioSeek? =
	pending ?: current?.takeIf {
		immediate == null && it.overlayRequestId == activeOverlayRequestId
	}

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
