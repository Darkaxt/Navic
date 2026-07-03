package paige.navic.reader

import paige.navic.util.core.Logger
import kotlin.math.roundToInt

const val WhispersyncSyncLogTag = "WhispersyncSync"

data class ReaderWhispersyncSessionState(
	val sidecar: WhispersyncSidecar? = null,
	val sync: ReaderWhispersyncSyncState = ReaderWhispersyncSyncState(),
	val visibleTextRange: ReaderWhispersyncVisibleTextRange? = null,
	val audioSeekTarget: WhispersyncAudioSeekTarget? = null,
	val status: ReaderWhispersyncStatus = ReaderWhispersyncStatus()
) {
	val timeline: WhispersyncTimeline?
		get() = sidecar?.timeline

	val available: Boolean
		get() = timeline?.segments?.isNotEmpty() == true
}

data class ReaderWhispersyncSyncState(
	val syncEnabled: Boolean = true,
	val activeSegmentKey: String? = null,
	val activeSegmentProgressTextEnd: Int? = null,
	val engineCommand: ReaderEngineCommand? = null,
	val engineCommandKey: Long = 0L
)

data class ReaderWhispersyncVisibleTextRange(
	val textHref: String,
	val visibleStart: Int,
	val visibleEnd: Int,
	val rangeCfi: String? = null,
	val source: String? = null
)

enum class ReaderWhispersyncStatusKind {
	Unavailable,
	Ready,
	SyncDisabled,
	SeekingAudio,
	Playing,
	NoActiveCue,
	Mismatch,
	LoadFailed
}

data class ReaderWhispersyncStatus(
	val kind: ReaderWhispersyncStatusKind = ReaderWhispersyncStatusKind.Unavailable,
	val label: String? = null,
	val detail: String? = null,
	val audioResource: String? = null,
	val positionMs: Long? = null
) {
	val visible: Boolean
		get() = kind != ReaderWhispersyncStatusKind.Unavailable

	val requiresAttention: Boolean
		get() = kind == ReaderWhispersyncStatusKind.Mismatch ||
			kind == ReaderWhispersyncStatusKind.LoadFailed

	val repairable: Boolean
		get() = kind == ReaderWhispersyncStatusKind.Mismatch
}

data class ReaderWhispersyncVisibleRangeStep(
	val state: ReaderWhispersyncSyncState,
	val audioSeekTarget: WhispersyncAudioSeekTarget? = null,
	val status: ReaderWhispersyncStatus? = null
)

data class ReaderWhispersyncPlaybackPositionStep(
	val state: ReaderWhispersyncSyncState,
	val status: ReaderWhispersyncStatus? = null
)

fun ReaderWhispersyncSyncState.setSyncEnabled(enabled: Boolean): ReaderWhispersyncSyncState {
	val nextState = copy(
		syncEnabled = enabled,
		activeSegmentKey = if (enabled) activeSegmentKey else null,
		activeSegmentProgressTextEnd = if (enabled) activeSegmentProgressTextEnd else null
	)
	val clearCommand = if (!enabled && activeSegmentKey != null) {
		ReaderEngineCommand.ClearMediaOverlay
	} else {
		null
	}
	return nextState.withEngineCommand(clearCommand)
}

fun ReaderWhispersyncSyncState.onAudiobookPlaybackPosition(
	timeline: WhispersyncTimeline?,
	audioResource: String,
	positionMs: Long,
	audioTrackIndex: Int? = null
): ReaderWhispersyncSyncState =
	onAudiobookPlaybackPositionStep(
		timeline = timeline,
		audioResource = audioResource,
		positionMs = positionMs,
		audioTrackIndex = audioTrackIndex
	).state

fun ReaderWhispersyncSyncState.onAudiobookPlaybackPositionStep(
	timeline: WhispersyncTimeline?,
	audioResource: String,
	positionMs: Long,
	audioTrackIndex: Int? = null
): ReaderWhispersyncPlaybackPositionStep {
	if (!syncEnabled) {
		return ReaderWhispersyncPlaybackPositionStep(
			state = this,
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SyncDisabled,
				label = "Whispersync paused"
			)
		)
	}
	if (timeline == null) {
		return ReaderWhispersyncPlaybackPositionStep(state = this)
	}
	val segment = timeline.activeSegment(
		audioResource = audioResource,
		positionMs = positionMs,
		audioTrackIndex = audioTrackIndex
	)
		?: run {
			if (activeSegmentKey != null) {
				Logger.w(
					WhispersyncSyncLogTag,
					"Whispersync playback position lost cue audio=${audioResource.whispersyncLogValue()} " +
						"track=${audioTrackIndex ?: "n/a"} positionMs=$positionMs"
				)
			}
			return ReaderWhispersyncPlaybackPositionStep(
				state = clearOverlayIfNeeded(),
				status = ReaderWhispersyncStatus(
					kind = ReaderWhispersyncStatusKind.NoActiveCue,
					label = "No synced text here",
					detail = audioResource,
					audioResource = audioResource,
					positionMs = positionMs
				)
			)
	}
	val key = segment.readerOverlaySyncKey()
	val progressTextEnd = segment.textProgressEndForPosition(positionMs)
	val nextState = if (key == activeSegmentKey) {
		if (progressTextEnd != null && progressTextEnd != activeSegmentProgressTextEnd) {
			Logger.i(
				WhispersyncSyncLogTag,
				"Whispersync playback position audio=${audioResource.whispersyncLogValue()} " +
					"track=${audioTrackIndex ?: "n/a"} positionMs=$positionMs " +
					"cue=${segment.id.orEmpty().ifBlank { "n/a" }} " +
					"text=${segment.textHref.whispersyncLogValue()} " +
					"textRange=${segment.textStart ?: "n/a"}-${segment.textEnd ?: "n/a"} " +
					"progressTextEnd=$progressTextEnd " +
					"command=updateOverlayProgress"
			)
			copy(activeSegmentProgressTextEnd = progressTextEnd)
				.withEngineCommand(
					ReaderEngineCommand.UpdateMediaOverlayProgress(
						segment.toReaderOverlayFragment(textProgressEnd = progressTextEnd)
					)
				)
		} else {
			this
		}
	} else {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync playback position audio=${audioResource.whispersyncLogValue()} " +
				"track=${audioTrackIndex ?: "n/a"} positionMs=$positionMs " +
				"cue=${segment.id.orEmpty().ifBlank { "n/a" }} " +
				"text=${segment.textHref.whispersyncLogValue()} " +
				"textRange=${segment.textStart ?: "n/a"}-${segment.textEnd ?: "n/a"} " +
				"progressTextEnd=${progressTextEnd ?: "n/a"} " +
				"command=applyOverlay"
		)
		copy(
			activeSegmentKey = key,
			activeSegmentProgressTextEnd = progressTextEnd
		).withEngineCommand(
			ReaderEngineCommand.ApplyMediaOverlay(
				segment.toReaderOverlayFragment(textProgressEnd = progressTextEnd)
			)
		)
	}
	return ReaderWhispersyncPlaybackPositionStep(
		state = nextState,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.Playing,
			label = "Whispersync playing",
			detail = segment.label,
			audioResource = segment.audioResource,
			positionMs = positionMs
		)
	)
}

fun ReaderWhispersyncSyncState.onAudiobookPlaybackPausedStep(
	audioResource: String? = null,
	positionMs: Long? = null,
	clearPlaybackOverlay: Boolean = false
): ReaderWhispersyncPlaybackPositionStep {
	val nextState = if (clearPlaybackOverlay) {
		clearOverlayIfNeeded()
	} else {
		this
	}
	return ReaderWhispersyncPlaybackPositionStep(
		state = nextState,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.SyncDisabled,
			label = "Whispersync paused",
			audioResource = audioResource?.trim()?.takeIf { it.isNotEmpty() },
			positionMs = positionMs
		)
	)
}

fun ReaderWhispersyncSyncState.onVisibleTextRange(
	timeline: WhispersyncTimeline?,
	textHref: String,
	visibleStart: Int,
	visibleEnd: Int
): ReaderWhispersyncVisibleRangeStep {
	if (timeline == null) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
	}
	val target = timeline.seekTargetForVisibleTextRange(
		textHref = textHref,
		visibleStart = visibleStart,
		visibleEnd = visibleEnd
	) ?: run {
		Logger.w(
			WhispersyncSyncLogTag,
			"Whispersync visible range not matched href=${textHref.whispersyncLogValue()} " +
				"textRange=$visibleStart-$visibleEnd"
		)
		return ReaderWhispersyncVisibleRangeStep(
			state = clearOverlayIfNeeded(),
			status = readerWhispersyncReadyStatus(timeline)
		)
	}
	if (!syncEnabled) {
		return ReaderWhispersyncVisibleRangeStep(
			state = this,
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SyncDisabled,
				label = "Whispersync paused"
			)
		)
	}
	val key = target.segment.readerOverlaySyncKey()
	if (key == activeSegmentKey) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
	}
	Logger.i(
		WhispersyncSyncLogTag,
		"Whispersync visible range selected audio=${target.audioResource.whispersyncLogValue()} " +
			"positionMs=${target.positionMs} href=${textHref.whispersyncLogValue()} " +
			"textRange=$visibleStart-$visibleEnd " +
			"cue=${target.segment.id.orEmpty().ifBlank { "n/a" }} " +
			"cueTextRange=${target.segment.textStart ?: "n/a"}-${target.segment.textEnd ?: "n/a"}"
	)
	val progressTextEnd = target.segment.textStart
	val nextState = copy(
		activeSegmentKey = key,
		activeSegmentProgressTextEnd = progressTextEnd
	).withEngineCommand(
		ReaderEngineCommand.ApplyMediaOverlay(
			target.segment.toReaderOverlayFragment(textProgressEnd = progressTextEnd)
		)
	)
	return ReaderWhispersyncVisibleRangeStep(
		state = nextState,
		audioSeekTarget = target,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.SeekingAudio,
			label = "Syncing audiobook",
			detail = target.segment.label,
			audioResource = target.audioResource,
			positionMs = target.positionMs
		)
	)
}

fun ReaderWhispersyncSyncState.onTextPoint(
	timeline: WhispersyncTimeline?,
	textHref: String,
	textOffset: Int
): ReaderWhispersyncVisibleRangeStep {
	if (timeline == null) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
	}
	val target = timeline.seekTargetForTextPoint(
		textHref = textHref,
		textOffset = textOffset
	) ?: run {
		Logger.w(
			WhispersyncSyncLogTag,
			"Whispersync text point not matched href=${textHref.whispersyncLogValue()} " +
				"textOffset=$textOffset"
		)
		return ReaderWhispersyncVisibleRangeStep(
			state = this,
			status = readerWhispersyncReadyStatus(timeline)
		)
	}
	if (!syncEnabled) {
		return ReaderWhispersyncVisibleRangeStep(
			state = this,
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SyncDisabled,
				label = "Whispersync paused"
			)
		)
	}
	val key = target.segment.readerOverlaySyncKey()
	val progressTextEnd = target.segment.textStart
	val command = if (key == activeSegmentKey) {
		if (progressTextEnd != null && progressTextEnd != activeSegmentProgressTextEnd) {
			ReaderEngineCommand.UpdateMediaOverlayProgress(
				target.segment.toReaderOverlayFragment(textProgressEnd = progressTextEnd)
			)
		} else {
			null
		}
	} else {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync text point selected audio=${target.audioResource.whispersyncLogValue()} " +
				"positionMs=${target.positionMs} href=${textHref.whispersyncLogValue()} " +
				"textOffset=$textOffset cue=${target.segment.id.orEmpty().ifBlank { "n/a" }} " +
				"textRange=${target.segment.textStart ?: "n/a"}-${target.segment.textEnd ?: "n/a"}"
		)
		ReaderEngineCommand.ApplyMediaOverlay(
			target.segment.toReaderOverlayFragment(textProgressEnd = progressTextEnd)
		)
	}
	return ReaderWhispersyncVisibleRangeStep(
		state = copy(
			activeSegmentKey = key,
			activeSegmentProgressTextEnd = progressTextEnd
		).withEngineCommand(command),
		audioSeekTarget = target,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.SeekingAudio,
			label = "Syncing audiobook",
			detail = target.segment.label,
			audioResource = target.audioResource,
			positionMs = target.positionMs
		)
	)
}

fun readerWhispersyncReadyStatus(timeline: WhispersyncTimeline?): ReaderWhispersyncStatus {
	val count = timeline?.segments?.size ?: 0
	return if (count > 0) {
		ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.Ready,
			label = "Whispersync ready",
			detail = "$count synced ${if (count == 1) "segment" else "segments"}"
		)
	} else {
		ReaderWhispersyncStatus()
	}
}

private fun ReaderWhispersyncSyncState.clearOverlayIfNeeded(): ReaderWhispersyncSyncState =
	if (activeSegmentKey == null) {
		this
	} else {
		copy(
			activeSegmentKey = null,
			activeSegmentProgressTextEnd = null
		)
			.withEngineCommand(ReaderEngineCommand.ClearMediaOverlay)
	}

private fun ReaderWhispersyncSyncState.withEngineCommand(
	command: ReaderEngineCommand?
): ReaderWhispersyncSyncState =
	if (command == null) {
		this
	} else {
		copy(
			engineCommand = command,
			engineCommandKey = engineCommandKey + 1L
		)
	}

private fun WhispersyncSegment.readerOverlaySyncKey(): String =
	listOf(
		audioResourceId.orEmpty(),
		audioTrackIndex?.toString().orEmpty(),
		normalizedMediaOverlayResource(audioResource),
		normalizedMediaOverlayResource(textHref),
		fragmentId.orEmpty(),
		rangeCfi.orEmpty(),
		startMs.toString(),
		endMs.toString()
	).joinToString("|")

private fun WhispersyncSegment.textProgressEndForPosition(positionMs: Long): Int? {
	val start = textStart ?: return null
	val end = textEnd ?: return null
	if (end <= start) return null
	val durationMs = (endMs - startMs).coerceAtLeast(1L)
	val elapsedMs = (positionMs - startMs).coerceIn(0L, durationMs)
	val progress = elapsedMs.toDouble() / durationMs.toDouble()
	val characterCount = end - start
	return (start + (characterCount * progress).roundToInt())
		.coerceIn(start, end)
}

internal fun String?.whispersyncLogValue(maxLength: Int = 96): String =
	this
		?.replace('\\', '/')
		?.replace(Regex("\\s+"), " ")
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?.let { value ->
			if (value.length <= maxLength) value else value.take(maxLength - 1) + "..."
		}
		?: "n/a"
