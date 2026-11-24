package paige.navic.reader

import paige.navic.util.core.Logger

/**
 * Why read-along audio must not play over the visible page, or null when the page is READY.
 * READY includes page preparation: the opening/regeneration cover, a failed preparation, a pending
 * layout review, any visible preparation card, and the unanswered opening "Prepare pages for"
 * question hold audio. The per-turn states (Settling, RefillingWorkingSet, BackgroundPrefetch)
 * keep the page readable and do not hold.
 */
fun ReaderPagePreparationState.readAlongHoldReason(openingQuestionPending: Boolean = false): String? = when {
	phase == ReaderPagePreparationPhase.Failed ||
		readiness.interaction == ReaderPageInteractionState.Failed -> "page-preparation-failed"
	layoutReviewPending -> "layout-review-pending"
	readiness.interaction == ReaderPageInteractionState.BlockingInitialPreparation ->
		"blocking-initial-preparation"
	readiness.interaction == ReaderPageInteractionState.BlockingProfileRegeneration ->
		"blocking-profile-regeneration"
	presentation != ReaderPagePreparationPresentation.Hidden -> "page-preparation-visible"
	openingQuestionPending -> "opening-question-pending"
	else -> null
}

/**
 * Read-along audio plays only over a READY page. The host reports page readiness here; the
 * hold itself is applied by [withWhispersyncPageHold], which every controller step passes through
 * (ReaderCoordinator.applyWordSyncDecision), so no start, resume or external play escapes it.
 */
internal fun ReaderController.onWhispersyncPageReadiness(holdReason: String?): ReaderControllerStep {
	val current = state.whispersync
	if (current.pageHoldReason == holdReason) return ReaderControllerStep(this)
	if (holdReason != null) {
		Logger.i(WhispersyncSyncLogTag, "Whispersync page hold state=held reason=$holdReason")
		return ReaderControllerStep(
			copy(state = state.copy(whispersync = current.copy(pageHoldReason = holdReason)))
		)
	}
	val released = copy(
		state = state.copy(
			whispersync = current.copy(
				pageHoldReason = null,
				pageHoldResumePending = false,
				pageHoldPausedDestination = null
			)
		)
	)
	val resume = current.pageHoldResumePending &&
		current.playbackIntent == ReaderWhispersyncPlaybackIntent.Enabled &&
		!current.userPaused &&
		!current.stopResetPending &&
		current.transportPhase != ReaderWhispersyncTransportPhase.BoundaryPaused
	val inPlace = resume &&
		current.pageHoldPausedDestination != null &&
		current.pageHoldPausedDestination == state.destinationCommitIdentity &&
		released.hasCurrentCanonicalAdmission()
	Logger.i(
		WhispersyncSyncLogTag,
		"Whispersync page hold state=released resume=" + when {
			inPlace -> "in-place"
			resume -> "prepared blocker=${released.whispersyncPlayBlocker() ?: "none"}"
			else -> "none"
		}
	)
	return when {
		inPlace -> ReaderControllerStep(
			controller = released,
			readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.Play
		)
		resume -> released.beginPreparedWhispersyncPlayback()
		else -> ReaderControllerStep(released)
	}
}

/** The single audio gate: while the page is not READY nothing may play or start. */
internal fun ReaderControllerStep.withWhispersyncPageHold(): ReaderControllerStep {
	val state = controller.state
	val whispersync = state.whispersync
	val holdReason = whispersync.pageHoldReason ?: return this
	val command = readaloudPlaybackCommand
	// An explicit Pause or Stop (the user's, or a page end's) stays as it is.
	if (command != null && command != ReaderReadaloudPlaybackCommand.Play) return this
	val audioPlaying = state.chrome.readaloudPlayback.isPlaying
	val startPending = whispersync.playbackStartPending || whispersync.pendingAudioSeek != null
	val playRequested = command == ReaderReadaloudPlaybackCommand.Play
	if (!audioPlaying && !startPending && !playRequested) return this
	// Audible audio, or a bare resume without a seek, continues in place on release.
	val inPlace = audioPlaying || (playRequested && whispersyncAudioSeekTarget == null)
	Logger.i(
		WhispersyncSyncLogTag,
		"Whispersync page hold state=intercepted reason=$holdReason " +
			"command=${if (audioPlaying) "pause" else "none"} inPlace=$inPlace"
	)
	return copy(
		controller = controller.copy(
			state = state.copy(
				chrome = state.chrome.onReadaloudPlaybackState(
					state.chrome.readaloudPlayback.copy(isPlaying = false)
				),
				whispersync = whispersync.copy(
					playbackStartPending = false,
					pendingAudioSeek = null,
					transportPhase = ReaderWhispersyncTransportPhase.Preparing,
					pageHoldResumePending = !whispersync.userPaused,
					pageHoldPausedDestination = if (inPlace) {
						state.destinationCommitIdentity
					} else {
						whispersync.pageHoldPausedDestination
					}
				)
			)
		),
		whispersyncAudioSeekTarget = whispersyncAudioSeekTarget.takeUnless { playRequested },
		readaloudPlaybackCommand = ReaderReadaloudPlaybackCommand.Pause.takeIf { audioPlaying }
	)
}
