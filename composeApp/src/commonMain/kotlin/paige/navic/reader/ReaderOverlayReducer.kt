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
		event: ReaderEngineEvent.MediaOverlayActive,
		resolveCueMapSourceOrdinal: (
			WhispersyncTimeline?,
			ReaderOverlayFragment
		) -> Int? = { timeline, fragment -> timeline?.sourceOrdinalFor(fragment) },
		projectCueMapPresentation: (
			ReaderWhispersyncCueMapState,
			ReaderControllerState
		) -> ReaderWhispersyncCueMapPresentation? = { cueMap, state -> cueMap.presentation(state) }
	): ReaderControllerStep {
		val state = controller.state
		if (state.shellCoverVisible) return ReaderControllerStep(controller)
		val currentWhispersync = state.whispersync
		if (
			currentWhispersync.pendingCausalIntent?.provenance ==
			ReaderWhispersyncEventProvenance.UserNavigation
		) {
			return ReaderControllerStep(controller)
		}
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
		val startPlayback = currentWhispersync.playbackStartPending && audioSeekTarget != null
		val progress = audioSeekTarget?.let {
			state.publication?.let { publication ->
				state.chrome.currentLocator?.toBinderyReadingProgress(
					bookId = publication.bookId,
					resourceHref = publication.resourceHref,
					kind = publication.kind
				)
			}
		}
		val currentCueMap = currentWhispersync.cueMap
		val shouldResolveCueMapSourceOrdinal = currentCueMap.enabled &&
			(!wasConfirmed || currentCueMap.renderedHighlightSourceOrdinal == null)
		val nextCueMap = if (shouldResolveCueMapSourceOrdinal) {
			currentCueMap.renderedHighlight(
				sourceOrdinal = resolveCueMapSourceOrdinal(
					currentWhispersync.timeline,
					event.fragment
				),
				revisionDigest = currentWhispersync.sidecar?.revisionDigest.orEmpty()
			)
		} else {
			currentCueMap
		}
		val cueMapChanged = nextCueMap != currentCueMap
		val nextController = controller.copy(
			state = state.copy(
				whispersync = currentWhispersync.copy(
					sync = confirmedSync,
					pendingAudioSeek = pendingSeek
						?.takeUnless { it.overlayRequestId == requestId },
					playbackStartPending = currentWhispersync.playbackStartPending &&
						audioSeekTarget == null,
					transportPhase = if (audioSeekTarget != null) {
						ReaderWhispersyncTransportPhase.Seeking
					} else {
						currentWhispersync.transportPhase
					},
					status = if (audioSeekTarget != null) {
						readerWhispersyncReadyStatus(currentWhispersync.timeline)
					} else {
						currentWhispersync.status
					},
					cueMap = nextCueMap
				),
				activeMediaOverlay = event.fragment,
				activeMediaOverlayAnchorReceipt = event.anchorReceipt,
				audioMetadataLabel = event.fragment.label
			)
		)
		return ReaderControllerStep(
			controller = nextController,
			engineCommands = if (cueMapChanged) {
				listOfNotNull(
					projectCueMapPresentation(
						nextController.state.whispersync.cueMap,
						nextController.state
					)?.let(ReaderEngineCommand::ReplaceWhispersyncCueMap)
				)
			} else {
				emptyList()
			},
			progressToSave = progress,
			whispersyncAudioSeekTarget = audioSeekTarget,
			readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.Play.takeIf { startPlayback }
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

	fun onBack(controller: ReaderController, includeMenu: Boolean): ReaderControllerBackStep {
		val state = controller.state
		val closeOverlay = when {
			state.dialog == ReaderControllerDialog.Search -> controller.closeSearchDialog()
			state.dialog != null -> controller.closeDialog()
			state.selectionNoteDraft != null -> controller.dismissSelectionNote()
			state.selectionActions.visible -> controller.dismissSelectionActions()
			state.annotationPopup?.visible == true -> controller.dismissAnnotationPopup()
			state.footnotePopup?.visible == true -> controller.dismissFootnotePopup()
			state.externalLinkPrompt != null -> controller.dismissExternalLinkPrompt()
			includeMenu && state.menuVisible -> controller.hideMenus()
			else -> null
		}
		return closeOverlay?.asBackStep() ?: returnToShellCoverBeforeLeaving(controller)
	}

	fun showNativeShellCover(controller: ReaderController): ReaderControllerStep {
		val state = controller.state
		val prepared = controller.copy(
			state = state.copy(
				shellCoverVisible = true,
				pendingShellCoverDismissal = null,
				nativeShellCoverReturnLocatorKey = readerNativeShellCoverReturnLocatorKey(
					state.chrome.currentLocator
				),
				menuVisible = false,
				dialog = null,
				whispersync = state.whispersync.forShellCoverPresentation(),
				activeMediaOverlay = null,
				activeMediaOverlayAnchorReceipt = null,
				audioMetadataLabel = null
			)
		).requestShellCoverPresentation()
		return ReaderControllerStep(
			controller = prepared.controller,
			engineCommands = state.clearOverlayForShellCoverCommands(),
			readaloudPlaybackCommand = state.shellCoverReadaloudResetCommand(),
			presentationEffects = prepared.presentationEffects,
			presentationReceipt = prepared.presentationReceipt
		)
	}

	private fun returnToShellCoverBeforeLeaving(
		controller: ReaderController
	): ReaderControllerBackStep {
		val state = controller.state
		if (
			!state.shellCoverVisible &&
			state.canReturnToShellCover &&
			!state.nativeShellCoverUrl.isNullOrBlank()
		) {
			val prepared = controller.copy(
				state = state.copy(
					shellCoverVisible = true,
					menuVisible = false,
					dialog = null,
					whispersync = state.whispersync.forShellCoverPresentation(),
					activeMediaOverlay = null,
					activeMediaOverlayAnchorReceipt = null,
					audioMetadataLabel = null
				)
			).requestShellCoverPresentation()
			return ReaderControllerBackStep(
				controller = prepared.controller,
				engineCommands = state.clearOverlayForShellCoverCommands(),
				handled = true,
				readaloudPlaybackCommand = state.shellCoverReadaloudResetCommand(),
				presentationEffects = prepared.presentationEffects,
				presentationReceipt = prepared.presentationReceipt
			)
		}
		return ReaderControllerBackStep(controller = controller, handled = false)
	}

	private fun ReaderControllerStep.asBackStep(): ReaderControllerBackStep =
		ReaderControllerBackStep(
			controller = controller,
			engineCommands = engineCommands,
			handled = true,
			readaloudPlaybackCommand = readaloudPlaybackCommand,
			presentationEffects = presentationEffects,
			presentationReceipt = presentationReceipt
		)
}

private fun ReaderController.requestShellCoverPresentation(): ReaderControllerStep =
	onPresentationEvent(
		ReaderPresentationEvent.ShellCoverRequested(
			coverGeneration = state.presentation.nextTokenValue
		)
	)

private fun ReaderControllerState.shellCoverReadaloudResetCommand(): ReaderReadaloudPlaybackCommand? =
	ReaderReadaloudPlaybackCommand.StopAndReset.takeIf { chrome.readaloudPlayback.isAvailable }

private fun ReaderControllerState.clearOverlayForShellCoverCommands(): List<ReaderEngineCommand> =
	listOfNotNull(
		ReaderEngineCommand.ClearMediaOverlay.takeIf {
			supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay) &&
				(whispersync.sync.activeOverlayRequestId != null || activeMediaOverlay != null)
		}
	)

private fun ReaderWhispersyncSessionState.forShellCoverPresentation(): ReaderWhispersyncSessionState {
	val canonicalTerminal = sidecar?.coordinateBasis != null &&
		canonicalGenerationState != ReaderWhispersyncCanonicalGenerationState.Open
	return copy(
		sync = sync.rejectOverlay(null),
		visibleTextRange = null,
		pendingAudioSeek = null,
		playbackIntent = ReaderWhispersyncPlaybackIntent.UserStopped,
		transportPhase = if (canonicalTerminal) {
			ReaderWhispersyncTransportPhase.Failed
		} else {
			ReaderWhispersyncTransportPhase.Unavailable
		},
		preparedVisibleTarget = null,
		playbackStartPending = false,
		stopResetPending = false,
		pendingCausalIntent = null,
		status = if (canonicalTerminal) status else readerWhispersyncReadyStatus(timeline)
	)
}
