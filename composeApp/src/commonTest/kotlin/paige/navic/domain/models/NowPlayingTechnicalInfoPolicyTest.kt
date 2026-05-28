package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingTechnicalInfoStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NowPlayingTechnicalInfoPolicyTest {
	@Test
	fun compactStyleFormatsCurrentPlaybackDetails() {
		val info = nowPlayingTechnicalInfo(
			style = NowPlayingTechnicalInfoStyle.Compact,
			input = NowPlayingTechnicalInfoInput(
				playbackMimeType = "audio/mpeg",
				fileExtension = "flac",
				playbackSampleRateHz = 44_100,
				sourceSampleRateHz = 96_000,
				playbackBitrateBps = 320_000,
				sourceBitrateKbps = 1_411
			)
		)

		assertEquals("MP3 • 44.1 kHz • 320 kbps", info.primary)
		assertNull(info.secondary)
	}

	@Test
	fun compactStyleDoesNotRenderNullForMissingBitrate() {
		val info = nowPlayingTechnicalInfo(
			style = NowPlayingTechnicalInfoStyle.Compact,
			input = NowPlayingTechnicalInfoInput(
				fileExtension = "flac",
				sourceSampleRateHz = 48_000
			)
		)

		assertEquals("FLAC • 48 kHz • -- kbps", info.primary)
	}

	@Test
	fun opusTranscodeUsesRequestedBitrateWhenActualBitrateIsMissing() {
		val info = nowPlayingTechnicalInfo(
			style = NowPlayingTechnicalInfoStyle.Compact,
			input = NowPlayingTechnicalInfoInput(
				playbackMimeType = "audio/opus",
				requestedTranscodeBitrateKbps = 128,
				sourceBitrateKbps = 1_411
			)
		)

		assertEquals("OPUS • -- kHz • 128 kbps", info.primary)
	}

	@Test
	fun detailedStyleAddsSourceStatsForNerdsLine() {
		val info = nowPlayingTechnicalInfo(
			style = NowPlayingTechnicalInfoStyle.Detailed,
			input = NowPlayingTechnicalInfoInput(
				fileExtension = "flac",
				sourceSampleRateHz = 96_000,
				sourceBitrateKbps = 1_411,
				bitDepth = 24,
				channelCount = 2,
				fileSizeBytes = 1_048_576,
				replayGain = DomainReplayGain(
					albumGain = null,
					albumPeak = null,
					trackGain = -5.25f,
					trackPeak = null,
					baseGain = null,
					fallbackGain = null
				)
			)
		)

		assertEquals("FLAC • 96 kHz • 1411 kbps", info.primary)
		assertEquals("24-bit • 2 ch • 1 MB • RG -5.25 dB", info.secondary)
	}
}
