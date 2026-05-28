package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingTechnicalInfoStyle
import kotlin.math.roundToInt

data class NowPlayingTechnicalInfoInput(
	val playbackMimeType: String? = null,
	val fileExtension: String? = null,
	val playbackSampleRateHz: Int? = null,
	val sourceSampleRateHz: Int? = null,
	val playbackBitrateBps: Int? = null,
	val sourceBitrateKbps: Int? = null,
	val requestedTranscodeBitrateKbps: Int? = null,
	val bitDepth: Int? = null,
	val channelCount: Int? = null,
	val fileSizeBytes: Long = 0L,
	val replayGain: DomainReplayGain? = null
)

data class NowPlayingTechnicalInfo(
	val primary: String,
	val secondary: String?
)

fun nowPlayingTechnicalInfo(
	style: NowPlayingTechnicalInfoStyle,
	input: NowPlayingTechnicalInfoInput
): NowPlayingTechnicalInfo {
	val primary = listOf(
		formatAudioFormat(input.playbackMimeType, input.fileExtension),
		formatSampleRate(input.playbackSampleRateHz ?: input.sourceSampleRateHz),
		formatBitrate(input)
	).joinToString(" • ")

	val secondary = when (style) {
		NowPlayingTechnicalInfoStyle.Compact -> null
		NowPlayingTechnicalInfoStyle.Detailed -> listOfNotNull(
			input.bitDepth?.takeIf { it > 0 }?.let { "$it-bit" },
			input.channelCount?.takeIf { it > 0 }?.let { "$it ch" },
			formatFileSize(input.fileSizeBytes),
			formatReplayGain(input.replayGain)
		).takeIf { it.isNotEmpty() }?.joinToString(" • ")
	}

	return NowPlayingTechnicalInfo(primary = primary, secondary = secondary)
}

private fun formatAudioFormat(playbackMimeType: String?, fileExtension: String?): String =
	(playbackMimeType
		?.substringBefore(";")
		?.substringAfterLast("/")
		?.removePrefix("x-")
		?.replace("mpeg", "mp3")
		?: fileExtension)
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?.uppercase()
		?: "--"

private fun formatSampleRate(sampleRateHz: Int?): String {
	val sampleRate = sampleRateHz?.takeIf { it > 0 } ?: return "-- kHz"
	val khz = sampleRate / 1000.0
	return "${formatDecimal(khz, decimals = 1)} kHz"
}

private fun formatBitrate(input: NowPlayingTechnicalInfoInput): String {
	val playbackBitrateKbps = input.playbackBitrateBps
		?.takeIf { it > 0 }
		?.let { (it / 1000).coerceAtLeast(1) }
	val requestedOpusBitrateKbps = if (
		input.playbackMimeType?.contains("opus", ignoreCase = true) == true
	) {
		input.requestedTranscodeBitrateKbps?.takeIf { it > 0 }
	} else {
		null
	}
	val bitrateKbps = playbackBitrateKbps
		?: requestedOpusBitrateKbps
		?: input.sourceBitrateKbps?.takeIf { it > 0 }

	return bitrateKbps?.let { "$it kbps" } ?: "-- kbps"
}

private fun formatFileSize(bytes: Long): String? {
	if (bytes <= 0L) return null
	var value = bytes.toDouble()
	var unitIndex = 0
	val units = listOf("B", "KB", "MB", "GB", "TB")

	while (value >= 1024.0 && unitIndex < units.lastIndex) {
		value /= 1024.0
		unitIndex++
	}

	return "${formatDecimal(value, decimals = if (unitIndex == 0) 0 else 1)} ${units[unitIndex]}"
}

private fun formatReplayGain(replayGain: DomainReplayGain?): String? {
	val gain = replayGain?.trackGain
		?: replayGain?.albumGain
		?: replayGain?.baseGain
		?: replayGain?.fallbackGain
		?: return null

	return "RG ${formatDecimal(gain.toDouble(), decimals = 2)} dB"
}

private fun formatDecimal(value: Double, decimals: Int): String {
	if (decimals == 0) return value.roundToInt().toString()
	val scale = when (decimals) {
		1 -> 10.0
		else -> 100.0
	}
	val rounded = (value * scale).roundToInt() / scale
	return rounded.toString().trimEnd('0').trimEnd('.')
}
