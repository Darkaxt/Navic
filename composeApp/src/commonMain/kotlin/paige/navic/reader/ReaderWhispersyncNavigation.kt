package paige.navic.reader

import kotlin.math.roundToLong

internal fun ReaderEngineEvent.VisibleTextRange.isWhispersyncAudioFollowRange(): Boolean =
	source.equals("media-overlay-follow", ignoreCase = true)

internal fun ReaderOverlayFragment.isOutsideWhispersyncVisibleRange(
	visibleRange: ReaderWhispersyncVisibleTextRange?
): Boolean {
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
	if (textProgressEnd != null && textProgressEnd >= visibleRange.visibleEnd) return true
	return end <= visibleRange.visibleStart || start >= visibleRange.visibleEnd
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
