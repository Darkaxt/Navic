package paige.navic.reader

data class ReaderExternalLinkPromptState(
	val href: String,
	val anchorHref: String? = null
)

sealed interface ReaderLinkInteraction {
	data class Internal(
		val href: String? = null,
		val prevented: Boolean = false,
		val source: String? = null
	) : ReaderLinkInteraction

	data class External(
		val href: String? = null,
		val anchorHref: String? = null
	) : ReaderLinkInteraction
}
data class ReaderFootnotePopupState(
	val href: String? = null,
	val text: String? = null,
	val noteType: String? = null,
	val hidden: Boolean = false
) {
	val visible: Boolean
		get() = !href.isNullOrBlank() || !text.isNullOrBlank() || !noteType.isNullOrBlank()
}

sealed interface ReaderOverlayInteraction {
	data class Created(val index: Int? = null) : ReaderOverlayInteraction
	data class FootnoteOpened(
		val href: String? = null,
		val noteType: String? = null
	) : ReaderOverlayInteraction
	data object FootnoteClosed : ReaderOverlayInteraction
	data object PullUp : ReaderOverlayInteraction
}

internal object ReaderOverlayReducer {
	fun apply(controller: ReaderController, fragment: ReaderOverlayFragment): ReaderControllerStep =
		if (!controller.state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
			ReaderControllerStep(controller)
		} else {
			ReaderControllerStep(
				controller = controller.copy(
					state = controller.state.copy(
						activeMediaOverlay = fragment,
						activeMediaOverlayAnchorReceipt = null,
						audioMetadataLabel = fragment.label
					)
				),
				engineCommands = listOf(ReaderEngineCommand.ApplyMediaOverlay(fragment))
			)
		}

	fun updateProgress(controller: ReaderController, fragment: ReaderOverlayFragment): ReaderControllerStep =
		if (!controller.state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
			ReaderControllerStep(controller)
		} else {
			ReaderControllerStep(
				controller = controller.copy(
					state = controller.state.copy(
						activeMediaOverlay = fragment,
						audioMetadataLabel = fragment.label ?: controller.state.audioMetadataLabel
					)
				),
				engineCommands = listOf(ReaderEngineCommand.UpdateMediaOverlayProgress(fragment))
			)
		}

	fun clear(controller: ReaderController, fragmentId: String?): ReaderControllerStep {
		val state = controller.state
		if (!state.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
			return ReaderControllerStep(controller)
		}
		val currentFragmentId = state.activeMediaOverlay?.fragmentId
		val shouldClear = state.activeMediaOverlay != null &&
			(fragmentId == null || fragmentId == currentFragmentId)
		return if (shouldClear) {
			ReaderControllerStep(
				controller = controller.copy(
					state = state.copy(
						activeMediaOverlay = null,
						activeMediaOverlayAnchorReceipt = null,
						audioMetadataLabel = null
					)
				),
				engineCommands = listOf(ReaderEngineCommand.ClearMediaOverlay)
			)
		} else {
			ReaderControllerStep(controller)
		}
	}

	fun onActive(
		controller: ReaderController,
		event: ReaderEngineEvent.MediaOverlayActive
	): ReaderControllerStep {
		val state = controller.state
		if (state.shellCoverVisible) return ReaderControllerStep(controller)
		val currentWhispersync = state.whispersync
		val requestId = event.fragment.overlayRequestId
		val activeRequestId = currentWhispersync.sync.activeOverlayRequestId
		if (requestId != activeRequestId) return ReaderControllerStep(controller)

		val wasConfirmed = currentWhispersync.sync.hasConfirmedOverlay(requestId)
		if (wasConfirmed && event.anchorReceipt == null) {
			return ReaderControllerStep(controller)
		}
		val confirmedSync = currentWhispersync.sync.confirmOverlay(requestId)

		val pendingSeek = currentWhispersync.pendingAudioSeek
		val audioSeekTarget = pendingSeek
			?.takeIf { it.overlayRequestId == requestId && !wasConfirmed }
			?.target
		val progress = audioSeekTarget?.let {
			state.publication?.let { publication ->
				state.chrome.currentLocator?.toBinderyReadingProgress(
					bookId = publication.bookId,
					resourceHref = publication.resourceHref,
					kind = publication.kind
				)
			}
		}
		return ReaderControllerStep(
			controller = controller.copy(
				state = state.copy(
					whispersync = currentWhispersync.copy(
						sync = confirmedSync,
						pendingAudioSeek = pendingSeek
							?.takeUnless { it.overlayRequestId == requestId },
						status = if (audioSeekTarget != null) {
							readerWhispersyncReadyStatus(currentWhispersync.timeline)
						} else {
							currentWhispersync.status
						}
					),
					activeMediaOverlay = event.fragment,
					activeMediaOverlayAnchorReceipt =
						event.anchorReceipt,
					audioMetadataLabel = event.fragment.label
				)
			),
			progressToSave = progress,
			whispersyncAudioSeekTarget = audioSeekTarget
		)
	}

	fun onInactive(
		controller: ReaderController,
		event: ReaderEngineEvent.MediaOverlayInactive
	): ReaderControllerStep {
		val state = controller.state
		val currentSync = state.whispersync.sync
		val requestId = event.overlayRequestId
		if (requestId == null) {
			if (currentSync.activeOverlayRequestId != null) {
				return ReaderControllerStep(controller)
			}
			val currentFragmentId = state.activeMediaOverlay?.fragmentId
			val shouldClear = state.activeMediaOverlay != null &&
				(event.fragmentId == null || event.fragmentId == currentFragmentId)
			return if (shouldClear) {
				ReaderControllerStep(
					controller.copy(
						state = state.copy(
							activeMediaOverlay = null,
							activeMediaOverlayAnchorReceipt = null,
							audioMetadataLabel = null
						)
					)
				)
			} else {
				ReaderControllerStep(controller)
			}
		}
		if (requestId != currentSync.activeOverlayRequestId) {
			return ReaderControllerStep(controller)
		}
		val currentFragmentId = state.activeMediaOverlay?.fragmentId
		val shouldClear = event.fragmentId == null || event.fragmentId == currentFragmentId
		return ReaderControllerStep(
			controller = controller.copy(
				state = state.copy(
					whispersync = state.whispersync.copy(
						sync = currentSync.rejectOverlay(requestId),
						pendingAudioSeek = null,
						status = readerWhispersyncReadyStatus(state.whispersync.timeline)
					),
					activeMediaOverlay = state.activeMediaOverlay.takeUnless { shouldClear },
					activeMediaOverlayAnchorReceipt =
						state.activeMediaOverlayAnchorReceipt.takeUnless { shouldClear },
					audioMetadataLabel = state.audioMetadataLabel.takeUnless { shouldClear }
				)
			),
			readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.Pause
				.takeIf { state.chrome.readaloudPlayback.isPlaying }
		)
	}

	fun onExternalLink(
		controller: ReaderController,
		event: ReaderEngineEvent.ExternalLinkOpened
	): ReaderControllerStep {
		val href = event.href?.trim()?.takeIf { it.isNotEmpty() }
		val anchorHref = event.anchorHref?.trim()?.takeIf { it.isNotEmpty() }
		return ReaderControllerStep(
			controller.copy(
				state = controller.state.copy(
					lastLinkInteraction = ReaderLinkInteraction.External(event.href, event.anchorHref),
					externalLinkPrompt = href?.let { ReaderExternalLinkPromptState(it, anchorHref) }
				)
			)
		)
	}

	fun onFootnoteOpened(
		controller: ReaderController,
		event: ReaderEngineEvent.FootnoteOpened
	): ReaderControllerStep = ReaderControllerStep(
		controller.copy(
			state = controller.state.copy(
				footnotePopup = ReaderFootnotePopupState(
					href = event.href,
					text = event.text,
					noteType = event.noteType,
					hidden = event.hidden
				),
				lastOverlayInteraction = ReaderOverlayInteraction.FootnoteOpened(event.href, event.noteType)
			)
		)
	)

	fun onFootnoteClosed(controller: ReaderController): ReaderControllerStep =
		ReaderControllerStep(
			controller.copy(
				state = controller.state.copy(
					footnotePopup = null,
					lastOverlayInteraction = ReaderOverlayInteraction.FootnoteClosed
				)
			)
		)

	fun dismissExternalLink(controller: ReaderController): ReaderControllerStep =
		ReaderControllerStep(
			controller.copy(state = controller.state.copy(externalLinkPrompt = null))
		)
}
