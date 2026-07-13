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
					state = state.copy(activeMediaOverlay = null, audioMetadataLabel = null)
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
		if (state.activeMediaOverlay == event.fragment) {
			return ReaderControllerStep(
				controller.copy(
					state = state.copy(
						activeMediaOverlay = event.fragment,
						audioMetadataLabel = event.fragment.label
					)
				)
			)
		}
		val audioSeekTarget = state.whispersync.audioSeekTargetForActiveOverlay(event.fragment)
		return ReaderControllerStep(
			controller = controller.copy(
				state = state.copy(
					whispersync = audioSeekTarget?.let { target ->
						state.whispersync.copy(
							audioSeekTarget = target,
							status = ReaderWhispersyncStatus(
								kind = ReaderWhispersyncStatusKind.SeekingAudio,
								message = ReaderWhispersyncStatusMessage.SeekingAudio,
								detail = target.segment.label,
								audioResource = target.audioResource,
								positionMs = target.positionMs
							)
						)
					} ?: state.whispersync,
					activeMediaOverlay = event.fragment,
					audioMetadataLabel = event.fragment.label
				)
			),
			whispersyncAudioSeekTarget = audioSeekTarget
		)
	}

	fun onInactive(
		controller: ReaderController,
		event: ReaderEngineEvent.MediaOverlayInactive
	): ReaderControllerStep {
		val currentFragmentId = controller.state.activeMediaOverlay?.fragmentId
		val shouldClear = event.fragmentId == null || event.fragmentId == currentFragmentId
		return ReaderControllerStep(
			if (shouldClear) {
				controller.copy(
					state = controller.state.copy(activeMediaOverlay = null, audioMetadataLabel = null)
				)
			} else {
				controller
			}
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
