package paige.navic.domain.models

private const val MIN_LIDA_CLIP_RESUME_POSITION_MS = 3_000L
private const val LIDA_CLIP_END_RESET_WINDOW_MS = 5_000L

data class LidaClipRememberedPosition(
	val clipId: String,
	val positionMs: Long
)

fun lidaClipStartPositionMs(
	rememberPosition: Boolean,
	clipId: Int,
	lastClipId: String,
	lastPositionMs: Long,
	durationMs: Long?
): Long {
	if (!rememberPosition || lastClipId != clipId.toString()) return 0L

	return sanitizeLidaClipPositionMs(lastPositionMs, durationMs)
}

fun nextRememberedLidaClipPosition(
	rememberPosition: Boolean,
	clipId: Int,
	positionMs: Long,
	durationMs: Long?
): LidaClipRememberedPosition? {
	if (!rememberPosition) return null

	return LidaClipRememberedPosition(
		clipId = clipId.toString(),
		positionMs = sanitizeLidaClipPositionMs(positionMs, durationMs)
	)
}

private fun sanitizeLidaClipPositionMs(
	positionMs: Long,
	durationMs: Long?
): Long {
	if (positionMs < MIN_LIDA_CLIP_RESUME_POSITION_MS) return 0L
	if (
		durationMs != null &&
		durationMs > 0L &&
		positionMs >= durationMs - LIDA_CLIP_END_RESET_WINDOW_MS
	) return 0L

	return positionMs
}
