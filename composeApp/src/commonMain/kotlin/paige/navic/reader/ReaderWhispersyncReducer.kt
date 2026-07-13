package paige.navic.reader

import paige.navic.util.core.Logger

internal object ReaderWhispersyncReducer {
	fun onReadaloudPlaybackState(
		controller: ReaderController,
		playbackState: ReaderReadaloudPlaybackUiState
	): ReaderControllerStep = controller.reduceReadaloudPlaybackState(playbackState)

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
private fun ReaderController.reduceReadaloudPlaybackState(playbackState: ReaderReadaloudPlaybackUiState): ReaderControllerStep {
	if (!state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
		return ReaderControllerStep(this)
	}
	val currentWhispersync = state.whispersync
	val baseSync = if (currentWhispersync.sync.syncEnabled == playbackState.syncEnabled) {
		currentWhispersync.sync
	} else {
		currentWhispersync.sync.setSyncEnabled(playbackState.syncEnabled)
	}
	val playbackStep = if (!playbackState.isPlaying) {
		if (currentWhispersync.status.kind == ReaderWhispersyncStatusKind.Playing) {
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
	val shouldClearOverlay = command == ReaderEngineCommand.ClearMediaOverlay
	val visibleRange = currentWhispersync.visibleTextRange
	if (overlayFragment != null && overlayFragment.isOutsideWhispersyncVisibleRange(visibleRange)) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync page boundary reached audio=${overlayFragment.resourceHref.whispersyncLogValue()} " +
				"text=${overlayFragment.textHref.whispersyncLogValue()} " +
				"textRange=${overlayFragment.textStart ?: "n/a"}-${overlayFragment.textEnd ?: "n/a"} " +
				"visible=${visibleRange?.textHref.whispersyncLogValue()}:" +
				"${visibleRange?.visibleStart ?: "n/a"}-${visibleRange?.visibleEnd ?: "n/a"} " +
				"command=pause"
		)
		return ReaderControllerStep(
			controller = copy(
				state = state.copy(
					chrome = state.chrome.onReadaloudPlaybackState(playbackState.copy(isPlaying = false)),
					whispersync = currentWhispersync.copy(
						sync = syncState.copy(
							activeCueKey = null,
							activeProgressTextEnd = null
						),
						status = ReaderWhispersyncStatus(
							kind = ReaderWhispersyncStatusKind.NoActiveCue,
							message = ReaderWhispersyncStatusMessage.VisiblePageEnded,
							detail = overlayFragment.label,
							audioResource = playbackState.audioResource,
							positionMs = playbackState.positionMs
						)
					),
					activeMediaOverlay = null,
					audioMetadataLabel = null
				)
			),
			engineCommands = listOf(ReaderEngineCommand.ClearMediaOverlay),
			readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.Pause
		)
	}
	if (overlayFragment != null) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync apply overlay source=playback " +
				"audio=${overlayFragment.resourceHref.whispersyncLogValue()} " +
				"text=${overlayFragment.textHref.whispersyncLogValue()} " +
				"textRange=${overlayFragment.textStart ?: "n/a"}-${overlayFragment.textEnd ?: "n/a"} " +
				"progressTextEnd=${overlayFragment.textProgressEnd ?: "n/a"} " +
				"clip=${overlayFragment.clipBeginSeconds ?: "n/a"}-${overlayFragment.clipEndSeconds ?: "n/a"}"
		)
	} else if (shouldClearOverlay) {
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
					status = playbackStep?.status ?: currentWhispersync.status
				),
				activeMediaOverlay = when {
					overlayFragment != null -> overlayFragment
					shouldClearOverlay -> null
					else -> state.activeMediaOverlay
				},
				audioMetadataLabel = when {
					overlayFragment != null -> overlayFragment.label
					shouldClearOverlay -> null
					else -> state.audioMetadataLabel
				}
			)
		),
		engineCommands = listOfNotNull(command)
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
	val syncStep = visibleRange?.let { range ->
		baseWhispersync.sync.onVisibleTextRange(
			timeline = sidecar.timeline,
			textHref = range.textHref,
			visibleStart = range.visibleStart,
			visibleEnd = range.visibleEnd
		)
	}
	val command = syncStep?.state?.engineCommand
	val overlayFragment = command.overlayFragmentOrNull()
	val shouldClearOverlay = command == ReaderEngineCommand.ClearMediaOverlay
	return ReaderControllerStep(
		copy(
			state = state.copy(
				whispersync = baseWhispersync.copy(
					sync = syncStep?.state ?: baseWhispersync.sync,
					audioSeekTarget = syncStep?.audioSeekTarget,
					status = syncStep?.status ?: baseWhispersync.status
				),
				activeMediaOverlay = when {
					overlayFragment != null -> overlayFragment
					shouldClearOverlay -> null
					else -> state.activeMediaOverlay
				},
				audioMetadataLabel = when {
					overlayFragment != null -> overlayFragment.label
					shouldClearOverlay -> null
					else -> state.audioMetadataLabel
				}
			)
		),
		engineCommands = listOfNotNull(command),
		whispersyncAudioSeekTarget = syncStep?.audioSeekTarget
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
	val overlayFragment = command.overlayFragmentOrNull()
	val shouldClearOverlay = command == ReaderEngineCommand.ClearMediaOverlay
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
					audioSeekTarget = syncStep.audioSeekTarget,
					status = syncStep.status ?: currentWhispersync.status
				),
				activeMediaOverlay = when {
					overlayFragment != null -> overlayFragment
					shouldClearOverlay -> null
					else -> state.activeMediaOverlay
				},
				audioMetadataLabel = when {
					overlayFragment != null -> overlayFragment.label
					shouldClearOverlay -> null
					else -> state.audioMetadataLabel
				}
			)
		),
		engineCommands = listOfNotNull(command),
		progressToSave = progress,
		whispersyncAudioSeekTarget = syncStep.audioSeekTarget
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
	Logger.i(
		WhispersyncSyncLogTag,
		"Whispersync visible range source=${event.source.whispersyncLogValue()} " +
			"audioFollow=${event.isWhispersyncAudioFollowRange()} " +
			"href=${event.textHref.whispersyncLogValue()} " +
			"textRange=${event.visibleStart}-${event.visibleEnd} " +
			"active=${currentWhispersync.sync.activeCueKey.whispersyncLogValue(48)}"
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
	val overlayFragment = command.overlayFragmentOrNull()
	val shouldClearOverlay = command == ReaderEngineCommand.ClearMediaOverlay
	if (overlayFragment != null) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync apply overlay source=visible-range " +
				"audio=${overlayFragment.resourceHref.whispersyncLogValue()} " +
				"text=${overlayFragment.textHref.whispersyncLogValue()} " +
				"textRange=${overlayFragment.textStart ?: "n/a"}-${overlayFragment.textEnd ?: "n/a"} " +
				"progressTextEnd=${overlayFragment.textProgressEnd ?: "n/a"}"
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
					audioSeekTarget = syncStep.audioSeekTarget,
					status = syncStep.status ?: currentWhispersync.status
				),
				activeMediaOverlay = when {
					overlayFragment != null -> overlayFragment
					shouldClearOverlay -> null
					else -> state.activeMediaOverlay
				},
				audioMetadataLabel = when {
					overlayFragment != null -> overlayFragment.label
					shouldClearOverlay -> null
					else -> state.audioMetadataLabel
				}
			)
		),
		engineCommands = listOfNotNull(command),
		progressToSave = progress,
		whispersyncAudioSeekTarget = syncStep.audioSeekTarget
	)
}

private fun ReaderController.reduceTextPoint(event: ReaderEngineEvent.TextPoint): ReaderControllerStep {
	val currentWhispersync = state.whispersync
	Logger.i(
		WhispersyncSyncLogTag,
		"Whispersync text point source=${event.source.whispersyncLogValue()} " +
			"href=${event.textHref.whispersyncLogValue()} textOffset=${event.textOffset} " +
			"active=${currentWhispersync.sync.activeCueKey.whispersyncLogValue(48)}"
	)
	val syncStep = currentWhispersync.sync.onTextPoint(
		timeline = currentWhispersync.timeline,
		textHref = event.textHref,
		textOffset = event.textOffset
	)
	val command = syncStep.state.engineCommand
		?.takeIf { syncStep.state.engineCommandKey != currentWhispersync.sync.engineCommandKey }
	val overlayFragment = command.overlayFragmentOrNull()
	if (overlayFragment != null) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync apply overlay source=text-point " +
				"audio=${overlayFragment.resourceHref.whispersyncLogValue()} " +
				"text=${overlayFragment.textHref.whispersyncLogValue()} " +
				"textRange=${overlayFragment.textStart ?: "n/a"}-${overlayFragment.textEnd ?: "n/a"} " +
				"progressTextEnd=${overlayFragment.textProgressEnd ?: "n/a"}"
		)
	}
	return ReaderControllerStep(
		controller = copy(
			state = state.copy(
				whispersync = currentWhispersync.copy(
					sync = syncStep.state,
					audioSeekTarget = syncStep.audioSeekTarget,
					status = syncStep.status ?: currentWhispersync.status
				),
				activeMediaOverlay = overlayFragment ?: state.activeMediaOverlay,
				audioMetadataLabel = overlayFragment?.label ?: state.audioMetadataLabel
			)
		),
		engineCommands = listOfNotNull(command),
		whispersyncAudioSeekTarget = syncStep.audioSeekTarget
	)
}
