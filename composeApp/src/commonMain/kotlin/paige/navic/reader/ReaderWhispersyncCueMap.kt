package paige.navic.reader

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToLong

const val ReaderWhispersyncCueMapTransitionLimit = 32
private const val ReaderWhispersyncCueMapTransportAcknowledgementToleranceMs = 500L

enum class ReaderWhispersyncCueMapMarkerState {
	Mapped,
	Prepared,
	Requested,
	AudioActive,
	RenderedHighlight
}

enum class ReaderWhispersyncCueMapHoldOutcome(val bridgeValue: String) {
	Completed("completed"),
	CancelledEarlyRelease("cancelled-early-release"),
	CancelledMovement("cancelled-movement"),
	CancelledPointer("cancelled-pointer"),
	CancelledChromeInterception("cancelled-chrome-interception"),
	CancelledCurlStart("cancelled-curl-start"),
	CancelledGenerationReplacement("cancelled-generation-replacement");

	companion object {
		fun fromBridgeValue(value: String?): ReaderWhispersyncCueMapHoldOutcome? =
			entries.firstOrNull { outcome -> outcome.bridgeValue == value }
	}
}

enum class ReaderWhispersyncCueMapTransitionOutcome {
	Projected,
	Prepared,
	SeekRequested,
	TransportAcknowledged,
	Rendered,
	HoldCancelled,
	GenerationReplaced
}

data class ReaderWhispersyncCueMapTransition(
	val sourceOrdinal: Int?,
	val revisionDigest: String,
	val state: ReaderWhispersyncCueMapMarkerState?,
	val presentationGeneration: Long,
	val outcome: ReaderWhispersyncCueMapTransitionOutcome,
	val holdOutcome: ReaderWhispersyncCueMapHoldOutcome? = null
) {
	init {
		require(sourceOrdinal == null || sourceOrdinal >= 0)
		require(revisionDigest.matches(Regex("[0-9a-f]{12}")))
		require(presentationGeneration >= 0L)
		require((outcome == ReaderWhispersyncCueMapTransitionOutcome.HoldCancelled) == (holdOutcome != null))
	}
}

data class ReaderWhispersyncCueMapDiagnosticSurface(
	val revisionDigest: String,
	val sourceOrdinals: List<Int>,
	val tokens: List<String>
) {
	init {
		require(revisionDigest.matches(Regex("[0-9a-f]{12}")))
		require(sourceOrdinals.size <= ReaderWhispersyncCueMapTransitionLimit)
		require(sourceOrdinals.all { it >= 0 })
		require(tokens.size <= ReaderWhispersyncCueMapTransitionLimit)
		require(tokens.all { it.matches(Regex("[a-z0-9:-]+")) })
	}

	val label: String = "cue-map $revisionDigest · " +
		(tokens.joinToString(separator = "→").ifEmpty { "–" })
}

data class ReaderWhispersyncCueMapCue(
	val sourceOrdinal: Int,
	val textHref: String,
	val textStart: Int,
	val textEnd: Int,
	val ebookText: String? = null,
	val nextTextHref: String? = null,
	val nextTextStart: Int? = null,
	val nextTextEnd: Int? = null,
	val nextEbookText: String? = null
) {
	init {
		require(sourceOrdinal >= 0)
		require(textHref.isNotBlank())
		require(textStart >= 0)
		require(textEnd > textStart)
	}
}

data class ReaderWhispersyncCueMapPresentation(
	val enabled: Boolean,
	val revisionDigest: String,
	val presentationGeneration: Long,
	val destinationCommitIdentity: ReaderDestinationCommitIdentity,
	val cues: List<ReaderWhispersyncCueMapCue>,
	val preparedSourceOrdinal: Int? = null,
	val requestedSourceOrdinal: Int? = null,
	val audioActiveSourceOrdinal: Int? = null,
	val renderedHighlightSourceOrdinal: Int? = null,
	val transportAcknowledgementPending: Boolean = false
) {
	init {
		require(revisionDigest.matches(Regex("[0-9a-f]{12}")))
		require(presentationGeneration > 0L)
		require(cues.map(ReaderWhispersyncCueMapCue::sourceOrdinal).distinct().size == cues.size)
	}
}

data class ReaderWhispersyncCueMapTransportRequest(
	val sourceOrdinal: Int,
	val revisionDigest: String,
	val presentationGeneration: Long,
	val normalizedAudioResource: String,
	val audioTrackIndex: Int?,
	val positionMs: Long
) {
	init {
		require(sourceOrdinal >= 0)
		require(revisionDigest.matches(Regex("[0-9a-f]{12}")))
		require(presentationGeneration > 0L)
		require(normalizedAudioResource.isNotBlank())
		require(positionMs >= 0L)
	}

	internal fun matches(
		sourceOrdinal: Int?,
		revisionDigest: String,
		presentationGeneration: Long,
		audioResource: String?,
		audioTrackIndex: Int?,
		positionMs: Long
	): Boolean {
		val transportIdentityMatches = this.audioTrackIndex?.let { requestedTrackIndex ->
			requestedTrackIndex == audioTrackIndex
		} ?: (
			normalizedAudioResource ==
				normalizedMediaOverlayResource(audioResource.orEmpty())
		)
		return this.sourceOrdinal == sourceOrdinal &&
			this.revisionDigest == revisionDigest &&
			this.presentationGeneration == presentationGeneration &&
			transportIdentityMatches &&
			positionMs >= 0L &&
			abs(positionMs - this.positionMs) <=
				ReaderWhispersyncCueMapTransportAcknowledgementToleranceMs
	}
}

data class ReaderWhispersyncCueMapMarkerReceipt(
	val sourceOrdinal: Int,
	val prepared: Boolean,
	val requested: Boolean,
	val audioActive: Boolean,
	val renderedHighlight: Boolean,
	val anchorReceipt: ReaderWhispersyncAnchorReceipt
) {
	init {
		require(sourceOrdinal >= 0)
		require(anchorReceipt.boundarySequence == sourceOrdinal.toLong())
	}
}

private data class ReaderWhispersyncCueMapAnchorAuthorityIdentity(
	val foliateSessionId: String,
	val destinationCommitToken: String,
	val visualPageOrdinal: Int,
	val spineIndex: Int,
	val rasterGeneration: Long,
	val textureGeneration: Long,
	val presentationMutationGeneration: Long,
	val presentationSequence: Long,
	val layoutGeneration: Long,
	val viewGeneration: Long,
	val commitSequence: Long,
	val committedSpineIndex: Int,
	val committedChapterPageIndex: Int,
	val committedChapterPageCount: Int,
	val paginationFingerprint: String,
	val layoutFingerprint: String,
	val readerSettingsRasterKey: String,
	val captureGeometry: ReaderPageTurnCaptureGeometry
)

private fun ReaderWhispersyncAnchorReceipt.cueMapAuthorityIdentity() =
	ReaderWhispersyncCueMapAnchorAuthorityIdentity(
		foliateSessionId = foliateSessionId,
		destinationCommitToken = destinationCommitToken,
		visualPageOrdinal = visualPageOrdinal,
		spineIndex = spineIndex,
		rasterGeneration = rasterGeneration,
		textureGeneration = textureGeneration,
		presentationMutationGeneration = presentationMutationGeneration,
		presentationSequence = presentationSequence,
		layoutGeneration = layoutGeneration,
		viewGeneration = viewGeneration,
		commitSequence = commitSequence,
		committedSpineIndex = committedSpineIndex,
		committedChapterPageIndex = committedChapterPageIndex,
		committedChapterPageCount = committedChapterPageCount,
		paginationFingerprint = paginationFingerprint,
		layoutFingerprint = layoutFingerprint,
		readerSettingsRasterKey = readerSettingsRasterKey,
		captureGeometry = captureGeometry
	)

data class ReaderWhispersyncCueMapGeometryReceipt(
	val revisionDigest: String,
	val presentationGeneration: Long,
	val destinationCommitIdentity: ReaderDestinationCommitIdentity,
	val markers: List<ReaderWhispersyncCueMapMarkerReceipt>
) {
	init {
		require(revisionDigest.matches(Regex("[0-9a-f]{12}")))
		require(presentationGeneration > 0L)
		require(markers.isNotEmpty())
		require(markers.size <= ReaderWhispersyncCueMapTransitionLimit)
		require(markers.map(ReaderWhispersyncCueMapMarkerReceipt::sourceOrdinal).distinct().size == markers.size)
		require(markers.all { marker ->
			marker.anchorReceipt.foliateSessionId == destinationCommitIdentity.foliateSessionId
		})
		require(
			markers.map { marker -> marker.anchorReceipt.cueMapAuthorityIdentity() }
				.distinct().size == 1
		)
	}
}

data class ReaderWhispersyncCueMapViewportAnchor(
	val sourceOrdinal: Int,
	val x: Float,
	val y: Float,
	val prepared: Boolean,
	val requested: Boolean,
	val audioActive: Boolean,
	val renderedHighlight: Boolean
)

fun ReaderWhispersyncCueMapGeometryReceipt.viewportAnchors(
	viewWidth: Float,
	viewHeight: Float
): List<ReaderWhispersyncCueMapViewportAnchor> {
	if (!viewWidth.isFinite() || viewWidth <= 0f || !viewHeight.isFinite() || viewHeight <= 0f) {
		return emptyList()
	}
	return markers.mapNotNull { marker ->
		val geometry = marker.anchorReceipt.captureGeometry
		val rect = marker.anchorReceipt.pageLocalRects.firstOrNull() ?: return@mapNotNull null
		val page = geometry.pages.firstOrNull { page -> page.role == rect.role }
			?: return@mapNotNull null
		val x = ((page.left + rect.left) / geometry.viewportWidth * viewWidth).toFloat()
		val y = ((page.top + rect.top) / geometry.viewportHeight * viewHeight).toFloat()
		if (!x.isFinite() || !y.isFinite()) return@mapNotNull null
		ReaderWhispersyncCueMapViewportAnchor(
			sourceOrdinal = marker.sourceOrdinal,
			x = x,
			y = y,
			prepared = marker.prepared,
			requested = marker.requested,
			audioActive = marker.audioActive,
			renderedHighlight = marker.renderedHighlight
		)
	}
}

class ReaderWhispersyncCueMapHoldTracker(
	private val holdDurationMs: Long = 1_000L,
	private val touchSlopPx: Float
) {
	init {
		require(holdDurationMs > 0L)
		require(touchSlopPx.isFinite() && touchSlopPx >= 0f)
	}

	var sourceOrdinal: Int? = null
		private set
	private var startX = 0f
	private var startY = 0f
	private var startedAtMillis = 0L
	private var completed = false

	val active: Boolean
		get() = sourceOrdinal != null

	fun begin(sourceOrdinal: Int, x: Float, y: Float, nowMillis: Long): Boolean {
		if (active || sourceOrdinal < 0 || !x.isFinite() || !y.isFinite()) return false
		this.sourceOrdinal = sourceOrdinal
		startX = x
		startY = y
		startedAtMillis = nowMillis
		completed = false
		return true
	}

	fun progress(nowMillis: Long): Float = if (!active) {
		0f
	} else {
		((nowMillis - startedAtMillis).coerceAtLeast(0L).toFloat() / holdDurationMs)
			.coerceIn(0f, 1f)
	}

	fun move(x: Float, y: Float): ReaderWhispersyncCueMapHoldOutcome? {
		if (!active || completed || !x.isFinite() || !y.isFinite()) return null
		if (hypot((x - startX).toDouble(), (y - startY).toDouble()) <= touchSlopPx) return null
		return finish(ReaderWhispersyncCueMapHoldOutcome.CancelledMovement)
	}

	fun advance(nowMillis: Long): ReaderWhispersyncCueMapHoldOutcome? {
		if (!active || completed || nowMillis - startedAtMillis < holdDurationMs) return null
		completed = true
		return ReaderWhispersyncCueMapHoldOutcome.Completed
	}

	fun release(nowMillis: Long): ReaderWhispersyncCueMapHoldOutcome? {
		if (!active) return null
		val completedOutcome = advance(nowMillis)
		if (completedOutcome != null) {
			reset()
			return completedOutcome
		}
		if (completed) {
			reset()
			return null
		}
		return finish(ReaderWhispersyncCueMapHoldOutcome.CancelledEarlyRelease)
	}

	fun cancel(outcome: ReaderWhispersyncCueMapHoldOutcome): ReaderWhispersyncCueMapHoldOutcome? {
		if (!active || completed || outcome == ReaderWhispersyncCueMapHoldOutcome.Completed) return null
		return finish(outcome)
	}

	fun abandon() {
		reset()
	}

	private fun finish(outcome: ReaderWhispersyncCueMapHoldOutcome): ReaderWhispersyncCueMapHoldOutcome {
		reset()
		return outcome
	}

	private fun reset() {
		sourceOrdinal = null
		completed = false
	}
}

data class ReaderWhispersyncCueMapState(
	val enabled: Boolean = false,
	val presentationGeneration: Long = 0L,
	val requestedSourceOrdinal: Int? = null,
	val transportAcknowledgementPending: Boolean = false,
	val requestedTransport: ReaderWhispersyncCueMapTransportRequest? = null,
	val audioActiveSourceOrdinal: Int? = null,
	val renderedHighlightSourceOrdinal: Int? = null,
	val sourceOrdinalsInDomReadingOrder: List<Int> = emptyList(),
	val geometryReceipt: ReaderWhispersyncCueMapGeometryReceipt? = null,
	val transitionTrail: List<ReaderWhispersyncCueMapTransition> = emptyList()
) {
	fun toggled(revisionDigest: String): ReaderWhispersyncCueMapState =
		replaced(
			revisionDigest = revisionDigest,
			enabled = !enabled
		)

	fun replaced(
		revisionDigest: String,
		enabled: Boolean = this.enabled
	): ReaderWhispersyncCueMapState {
		val retainedTransitions = transitionTrail.filter { transition ->
			transition.revisionDigest == revisionDigest
		}
		val revisionChanged = retainedTransitions.size != transitionTrail.size
		if (!enabled && !this.enabled && !revisionChanged) return this
		val generation = presentationGeneration + 1L
		val replacement = ReaderWhispersyncCueMapTransition(
			sourceOrdinal = null,
			revisionDigest = revisionDigest,
			state = null,
			presentationGeneration = generation,
			outcome = ReaderWhispersyncCueMapTransitionOutcome.GenerationReplaced
		)
		return copy(
			enabled = enabled,
			presentationGeneration = generation,
			requestedSourceOrdinal = null,
			transportAcknowledgementPending = false,
			requestedTransport = null,
			audioActiveSourceOrdinal = null,
			renderedHighlightSourceOrdinal = null,
			sourceOrdinalsInDomReadingOrder = emptyList(),
			geometryReceipt = null,
			transitionTrail = appendTransition(
				transition = replacement,
				trail = retainedTransitions
			)
		)
	}

	fun rendered(
		sourceOrdinals: List<Int>,
		revisionDigest: String,
		geometryReceipt: ReaderWhispersyncCueMapGeometryReceipt? = null
	): ReaderWhispersyncCueMapState {
		if (!enabled) return this
		val bounded = sourceOrdinals.filter { it >= 0 }.take(ReaderWhispersyncCueMapTransitionLimit)
		if (bounded == sourceOrdinalsInDomReadingOrder && geometryReceipt == this.geometryReceipt) return this
		var next = copy(
			sourceOrdinalsInDomReadingOrder = bounded,
			geometryReceipt = geometryReceipt
		)
		bounded.forEach { sourceOrdinal ->
			next = next.record(
				sourceOrdinal = sourceOrdinal,
				revisionDigest = revisionDigest,
				state = ReaderWhispersyncCueMapMarkerState.Mapped,
				outcome = ReaderWhispersyncCueMapTransitionOutcome.Projected
			)
		}
		return next
	}

	fun requested(
		sourceOrdinal: Int,
		revisionDigest: String,
		audioResource: String,
		audioTrackIndex: Int?,
		positionMs: Long
	): ReaderWhispersyncCueMapState {
		if (!enabled || transportAcknowledgementPending) return this
		val transportRequest = ReaderWhispersyncCueMapTransportRequest(
			sourceOrdinal = sourceOrdinal,
			revisionDigest = revisionDigest,
			presentationGeneration = presentationGeneration,
			normalizedAudioResource = normalizedMediaOverlayResource(audioResource),
			audioTrackIndex = audioTrackIndex,
			positionMs = positionMs
		)
		return copy(
			requestedSourceOrdinal = sourceOrdinal,
			transportAcknowledgementPending = true,
			requestedTransport = transportRequest
		).record(
			sourceOrdinal = sourceOrdinal,
			revisionDigest = revisionDigest,
			state = ReaderWhispersyncCueMapMarkerState.Requested,
			outcome = ReaderWhispersyncCueMapTransitionOutcome.SeekRequested
		)
	}

	fun transportAcknowledged(
		sourceOrdinal: Int?,
		revisionDigest: String,
		audioResource: String?,
		audioTrackIndex: Int?,
		positionMs: Long
	): ReaderWhispersyncCueMapState {
		val request = requestedTransport
		if (
			!enabled || !transportAcknowledgementPending || request == null ||
			!request.matches(
				sourceOrdinal = sourceOrdinal,
				revisionDigest = revisionDigest,
				presentationGeneration = presentationGeneration,
				audioResource = audioResource,
				audioTrackIndex = audioTrackIndex,
				positionMs = positionMs
			)
		) return this
		return copy(
			transportAcknowledgementPending = false,
			requestedTransport = null
		).record(
			sourceOrdinal = request.sourceOrdinal,
			revisionDigest = revisionDigest,
			state = ReaderWhispersyncCueMapMarkerState.Requested,
			outcome = ReaderWhispersyncCueMapTransitionOutcome.TransportAcknowledged
		)
	}

	fun audioActive(sourceOrdinal: Int?, revisionDigest: String): ReaderWhispersyncCueMapState {
		if (!enabled) return this
		if (sourceOrdinal == null || sourceOrdinal < 0) {
			return if (audioActiveSourceOrdinal == null) this else copy(audioActiveSourceOrdinal = null)
		}
		if (audioActiveSourceOrdinal == sourceOrdinal) return this
		return copy(audioActiveSourceOrdinal = sourceOrdinal).record(
			sourceOrdinal = sourceOrdinal,
			revisionDigest = revisionDigest,
			state = ReaderWhispersyncCueMapMarkerState.AudioActive,
			outcome = ReaderWhispersyncCueMapTransitionOutcome.Projected
		)
	}

	fun renderedHighlight(sourceOrdinal: Int?, revisionDigest: String): ReaderWhispersyncCueMapState {
		if (!enabled) return this
		if (sourceOrdinal == null || sourceOrdinal < 0) {
			return if (renderedHighlightSourceOrdinal == null) this else copy(renderedHighlightSourceOrdinal = null)
		}
		if (renderedHighlightSourceOrdinal == sourceOrdinal) return this
		return copy(renderedHighlightSourceOrdinal = sourceOrdinal).record(
			sourceOrdinal = sourceOrdinal,
			revisionDigest = revisionDigest,
			state = ReaderWhispersyncCueMapMarkerState.RenderedHighlight,
			outcome = ReaderWhispersyncCueMapTransitionOutcome.Rendered
		)
	}

	fun holdOutcome(
		sourceOrdinal: Int,
		revisionDigest: String,
		outcome: ReaderWhispersyncCueMapHoldOutcome
	): ReaderWhispersyncCueMapState =
		if (!enabled || outcome == ReaderWhispersyncCueMapHoldOutcome.Completed) {
			this
		} else {
			record(
				sourceOrdinal = sourceOrdinal,
				revisionDigest = revisionDigest,
				state = null,
				outcome = ReaderWhispersyncCueMapTransitionOutcome.HoldCancelled,
				holdOutcome = outcome
			)
		}

	private fun record(
		sourceOrdinal: Int,
		revisionDigest: String,
		state: ReaderWhispersyncCueMapMarkerState?,
		outcome: ReaderWhispersyncCueMapTransitionOutcome,
		holdOutcome: ReaderWhispersyncCueMapHoldOutcome? = null
	): ReaderWhispersyncCueMapState {
		val transition = ReaderWhispersyncCueMapTransition(
			sourceOrdinal = sourceOrdinal,
			revisionDigest = revisionDigest,
			state = state,
			presentationGeneration = presentationGeneration,
			outcome = outcome,
			holdOutcome = holdOutcome
		)
		return copy(transitionTrail = appendTransition(transition))
	}

	private fun appendTransition(
		transition: ReaderWhispersyncCueMapTransition,
		trail: List<ReaderWhispersyncCueMapTransition> = transitionTrail
	): List<ReaderWhispersyncCueMapTransition> =
		if (
			transition.outcome != ReaderWhispersyncCueMapTransitionOutcome.HoldCancelled &&
			trail.lastOrNull() == transition
		) {
			trail
		} else {
			(trail + transition).takeLast(ReaderWhispersyncCueMapTransitionLimit)
		}
}

fun ReaderWhispersyncCueMapState.productionDiagnosticSurface(
	revisionDigest: String
): ReaderWhispersyncCueMapDiagnosticSurface {
	val boundedTransitions = transitionTrail
		.filter { transition -> transition.revisionDigest == revisionDigest }
		.takeLast(ReaderWhispersyncCueMapTransitionLimit)
	return ReaderWhispersyncCueMapDiagnosticSurface(
		revisionDigest = revisionDigest,
		sourceOrdinals = boundedTransitions
			.mapNotNull(ReaderWhispersyncCueMapTransition::sourceOrdinal),
		tokens = boundedTransitions.map(ReaderWhispersyncCueMapTransition::diagnosticToken)
	)
}

private fun ReaderWhispersyncCueMapTransition.diagnosticToken(): String {
	val stateToken = when (state) {
		ReaderWhispersyncCueMapMarkerState.Mapped -> "m"
		ReaderWhispersyncCueMapMarkerState.Prepared -> "p"
		ReaderWhispersyncCueMapMarkerState.Requested -> "r"
		ReaderWhispersyncCueMapMarkerState.AudioActive -> "a"
		ReaderWhispersyncCueMapMarkerState.RenderedHighlight -> "h"
		null -> "-"
	}
	val outcomeToken = when (outcome) {
		ReaderWhispersyncCueMapTransitionOutcome.Projected -> "project"
		ReaderWhispersyncCueMapTransitionOutcome.Prepared -> "prepare"
		ReaderWhispersyncCueMapTransitionOutcome.SeekRequested -> "seek"
		ReaderWhispersyncCueMapTransitionOutcome.TransportAcknowledged -> "ack"
		ReaderWhispersyncCueMapTransitionOutcome.Rendered -> "render"
		ReaderWhispersyncCueMapTransitionOutcome.HoldCancelled ->
			requireNotNull(holdOutcome).bridgeValue
		ReaderWhispersyncCueMapTransitionOutcome.GenerationReplaced -> "replace"
	}
	return "g$presentationGeneration:${sourceOrdinal ?: "-"}:$stateToken:$outcomeToken"
}

internal fun ReaderWhispersyncCueMapState.presentation(
	controllerState: ReaderControllerState
): ReaderWhispersyncCueMapPresentation? {
	if (presentationGeneration <= 0L) return null
	val whispersync = controllerState.whispersync
	val sidecar = whispersync.sidecar ?: return null
	val revisionDigest = sidecar.revisionDigest.takeIf { it.matches(Regex("[0-9a-f]{12}")) }
		?: return null
	val destination = controllerState.destinationCommitIdentity ?: return null
	if (!enabled) {
		return ReaderWhispersyncCueMapPresentation(
			enabled = false,
			revisionDigest = revisionDigest,
			presentationGeneration = presentationGeneration,
			destinationCommitIdentity = destination,
			cues = emptyList()
		)
	}
	val visibleRange = whispersync.visibleTextRange ?: return null
	if (visibleRange.destinationCommitIdentity != destination) return null
	val visibleSegments = sidecar.timeline.cueMapProjectionForVisibleTextRange(
		textHref = visibleRange.textHref,
		visibleStart = visibleRange.visibleStart,
		visibleEnd = visibleRange.visibleEnd
	)
	val cues = visibleSegments.mapNotNull { segment ->
		val sourceOrdinal = segment.sourceOrdinal.takeIf { it >= 0 } ?: return@mapNotNull null
		val textStart = segment.textStart ?: return@mapNotNull null
		val textEnd = segment.textEnd ?: return@mapNotNull null
		val next = sidecar.timeline.nextSegmentAfter(segment)
		ReaderWhispersyncCueMapCue(
			sourceOrdinal = sourceOrdinal,
			textHref = segment.textHref,
			textStart = textStart,
			textEnd = textEnd,
			ebookText = segment.ebookText,
			nextTextHref = next?.textHref,
			nextTextStart = next?.textStart,
			nextTextEnd = next?.textEnd,
			nextEbookText = next?.ebookText
		)
	}
	return ReaderWhispersyncCueMapPresentation(
		enabled = true,
		revisionDigest = revisionDigest,
		presentationGeneration = presentationGeneration,
		destinationCommitIdentity = destination,
		cues = cues,
		preparedSourceOrdinal = whispersync.preparedVisibleTarget
			?.audioSeekTarget?.segment?.sourceOrdinal?.takeIf { it >= 0 },
		requestedSourceOrdinal = requestedSourceOrdinal,
		audioActiveSourceOrdinal = audioActiveSourceOrdinal,
		renderedHighlightSourceOrdinal = renderedHighlightSourceOrdinal,
		transportAcknowledgementPending = transportAcknowledgementPending
	)
}

internal fun WhispersyncTimeline.segmentForSourceOrdinal(sourceOrdinal: Int): WhispersyncSegment? =
	segments.firstOrNull { segment -> segment.sourceOrdinal == sourceOrdinal }

internal fun WhispersyncTimeline.sourceOrdinalFor(fragment: ReaderOverlayFragment?): Int? {
	if (fragment == null) return null
	val clipBeginSeconds = fragment.clipBeginSeconds ?: return null
	val clipEndSeconds = fragment.clipEndSeconds ?: return null
	return segments.firstOrNull { segment ->
		segment.startMs == (clipBeginSeconds * 1000.0).roundToLong() &&
			segment.endMs == (clipEndSeconds * 1000.0).roundToLong() &&
			normalizedMediaOverlayResource(segment.textHref) ==
				normalizedMediaOverlayResource(fragment.textHref.orEmpty()) &&
			segment.textStart == fragment.textStart &&
			segment.textEnd == fragment.textEnd
	}?.sourceOrdinal?.takeIf { it >= 0 }
}
