package paige.navic.reader

data class WhispersyncPlaybackSyncInput(
	val audioResource: String,
	val positionMs: Long,
	val audioTrackIndex: Int? = null,
	val playbackSpeed: Float = 1f,
	val highlightLeadMs: Int = 0
)

sealed interface WhispersyncReaderSyncInput {
	data class VisibleRange(
		val textHref: String,
		val visibleStart: Int,
		val visibleEnd: Int
	) : WhispersyncReaderSyncInput

	data class TextPoint(
		val textHref: String,
		val textOffset: Int
	) : WhispersyncReaderSyncInput
}

data class WhispersyncPlaybackSyncResolution(
	val segment: WhispersyncSegment,
	val cue: ReaderOverlayCue
)

class WhispersyncOverlaySyncAdapter(
	private val timeline: WhispersyncTimeline
) : ReaderOverlayTimelineAdapter<
	WhispersyncPlaybackSyncInput,
	WhispersyncReaderSyncInput,
	WhispersyncAudioSeekTarget
	> {
	override fun playbackCue(input: WhispersyncPlaybackSyncInput): ReaderOverlayCue? =
		playbackResolution(input)?.cue

	fun playbackResolution(
		input: WhispersyncPlaybackSyncInput
	): WhispersyncPlaybackSyncResolution? {
		val segment = timeline.activeSegment(
			audioResource = input.audioResource,
			positionMs = input.positionMs,
			audioTrackIndex = input.audioTrackIndex
		) ?: return null
		val progressTextEnd = segment.textEnd?.takeIf { end ->
			segment.textStart?.let { start -> end > start } == true
		}
		return WhispersyncPlaybackSyncResolution(
			segment = segment,
			cue = timeline.cueFor(
				segment = segment,
				progressTextEnd = progressTextEnd,
				progressFraction = null,
				playbackSpeed = input.playbackSpeed
			)
		)
	}

	override fun readerTarget(
		input: WhispersyncReaderSyncInput
	): ReaderOverlayReaderTarget<WhispersyncAudioSeekTarget>? =
		when (input) {
			is WhispersyncReaderSyncInput.VisibleRange ->
				timeline.seekTargetForVisibleTextRange(
					textHref = input.textHref,
					visibleStart = input.visibleStart,
					visibleEnd = input.visibleEnd
				)?.toReaderTarget(
					repeatSeek = false,
					updateRepeatedCue = false
				)
			is WhispersyncReaderSyncInput.TextPoint ->
				timeline.seekTargetForTextPoint(
					textHref = input.textHref,
					textOffset = input.textOffset
				)?.toReaderTarget(
					repeatSeek = true,
					updateRepeatedCue = true
				)
		}

	private fun WhispersyncAudioSeekTarget.toReaderTarget(
		repeatSeek: Boolean,
		updateRepeatedCue: Boolean
	): ReaderOverlayReaderTarget<WhispersyncAudioSeekTarget> =
		ReaderOverlayReaderTarget(
			cue = timeline.cueFor(
				segment = segment,
				progressTextEnd = segment.textEnd?.takeIf { end ->
					segment.textStart?.let { start -> end > start } == true
				},
				progressFraction = null,
				playbackSpeed = 1f
			),
			seekTarget = this,
			repeatSeek = repeatSeek,
			updateRepeatedCue = updateRepeatedCue
		)
}

private fun WhispersyncTimeline.cueFor(
	segment: WhispersyncSegment,
	progressTextEnd: Int?,
	progressFraction: Double?,
	playbackSpeed: Float
): ReaderOverlayCue =
	ReaderOverlayCue(
		key = segment.overlaySyncKey(),
		fragment = segment.toReaderOverlayFragment(
			textProgressEnd = progressTextEnd,
			textProgressFraction = progressFraction,
			playbackSpeed = playbackSpeed,
			nextSegment = nextSegmentAfter(segment)
		),
		progressTextEnd = progressTextEnd
	)

private fun WhispersyncSegment.overlaySyncKey(): String =
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
