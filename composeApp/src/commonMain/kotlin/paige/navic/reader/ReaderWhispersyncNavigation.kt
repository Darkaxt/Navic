package paige.navic.reader

import kotlin.math.roundToLong

internal fun ReaderEngineEvent.VisibleTextRange.isWhispersyncAudioFollowRange(): Boolean =
	source.equals("media-overlay-follow", ignoreCase = true)

internal fun ReaderOverlayFragment.isOutsideWhispersyncVisibleRange(
	visibleRange: ReaderWhispersyncVisibleTextRange?
): Boolean {
	return when (coordinateMode) {
		ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8 -> {
			visibleRange ?: return true
			val fragmentHref = textHref?.trim()?.takeIf { it.isNotEmpty() } ?: return true
			val visibleHref = visibleRange.textHref.trim().takeIf { it.isNotEmpty() } ?: return true
			if (readerTocHrefKey(fragmentHref) != readerTocHrefKey(visibleHref)) return true
			val provenanceId = rawProvenanceId?.takeIf { it.isNotBlank() && it == it.trim() }
				?: return true
			val spineIndex = rawSpineIndex?.takeIf { it >= 0 } ?: return true
			val start = rawByteStart?.takeIf { it >= 0 } ?: return true
			val end = rawByteEnd?.takeIf { it > start } ?: return true
			val visibleProvenanceId = visibleRange.rawProvenanceId
				?.takeIf { it.isNotBlank() && it == it.trim() }
				?: return true
			val visibleSpineIndex = visibleRange.rawSpineIndex?.takeIf { it >= 0 } ?: return true
			val visibleStart = visibleRange.rawByteStart?.takeIf { it >= 0 } ?: return true
			val visibleEnd = visibleRange.rawByteEnd?.takeIf { it > visibleStart } ?: return true
			provenanceId != visibleProvenanceId ||
				spineIndex != visibleSpineIndex ||
				end <= visibleStart || start >= visibleEnd
		}
		ReaderOverlayCoordinateMode.CueV1DomUtf16 -> {
			visibleRange ?: return false
			val fragmentHref = textHref?.trim()?.takeIf { it.isNotEmpty() }
			val visibleHref = visibleRange.textHref.trim().takeIf { it.isNotEmpty() }
			if (
				fragmentHref != null &&
				visibleHref != null &&
				readerTocHrefKey(fragmentHref) != readerTocHrefKey(visibleHref)
			) {
				return true
			}
			val start = textStart ?: return false
			val end = textEnd ?: return false
			if (end <= start) return false
			end <= visibleRange.visibleStart || start >= visibleRange.visibleEnd
		}
	}
}

internal fun ReaderEngineCommand?.overlayFragmentOrNull(): ReaderOverlayFragment? =
	when (this) {
		is ReaderEngineCommand.ApplyMediaOverlay -> fragment
		is ReaderEngineCommand.UpdateMediaOverlayProgress -> fragment
		else -> null
	}

internal fun ReaderLocator.isWhispersyncAudioFollowRelocation(): Boolean =
	reason.equals("media-overlay-follow", ignoreCase = true)

internal fun ReaderWhispersyncSessionState.audioSeekTargetForActiveOverlay(
	fragment: ReaderOverlayFragment
): WhispersyncAudioSeekTarget? {
	if (!available || !sync.syncEnabled) return null
	val clipBeginSeconds = fragment.clipBeginSeconds
		?.takeIf(Double::isFinite)
		?: return null
	val startMs = (clipBeginSeconds * 1000.0).roundToLong().coerceAtLeast(0L)
	val endMs = fragment.clipEndSeconds
		?.takeIf(Double::isFinite)
		?.let { (it * 1000.0).roundToLong().coerceAtLeast(startMs) }
		?: startMs
	val segment = WhispersyncSegment(
		id = fragment.fragmentId,
		audioResource = fragment.resourceHref,
		startMs = startMs,
		endMs = endMs,
		textHref = fragment.textHref?.trim().orEmpty(),
		fragmentId = fragment.fragmentId,
		textStart = fragment.textStart,
		textEnd = fragment.textEnd,
		label = fragment.label
	)
	return WhispersyncAudioSeekTarget(
		audioResource = segment.audioResource,
		positionMs = segment.startMs,
		segment = segment
	)
}

internal fun ReaderControllerStep.withWhispersyncUserNavigation(
	pauseCommand: ReaderReadaloudPlaybackCommand?
): ReaderControllerStep {
	if (engineCommands.isEmpty()) return this
	val reservedController = ReaderWhispersyncReducer.reserveUserNavigation(
		controller = controller,
		requiresPageTurnSettlement = false
	)
	val causalSequence = reservedController.pendingWhispersyncCausalSequence()
	return copy(
		controller = reservedController,
		engineCommands = engineCommands.map { it.withCausalSequence(causalSequence) },
		readaloudPlaybackCommand = readaloudPlaybackCommand ?: pauseCommand
	)
}

internal fun ReaderController.pendingWhispersyncCausalSequence(): Long? =
	state.whispersync.pendingCausalIntent?.sequence

private fun ReaderEngineCommand.withCausalSequence(causalSequence: Long?): ReaderEngineCommand =
	when (this) {
		is ReaderEngineCommand.NavigateTo -> copy(causalSequence = causalSequence)
		is ReaderEngineCommand.TurnPage -> copy(causalSequence = causalSequence)
		is ReaderEngineCommand.ScrollViewport -> copy(causalSequence = causalSequence)
		else -> this
	}

internal fun ReaderWhispersyncSessionState.navigationPauseCommand(): ReaderReadaloudPlaybackCommand? =
	ReaderReadaloudPlaybackCommand.Pause.takeIf {
		playbackIntent == ReaderWhispersyncPlaybackIntent.Enabled &&
			!userPaused &&
			transportPhase != ReaderWhispersyncTransportPhase.BoundaryPaused
	}
