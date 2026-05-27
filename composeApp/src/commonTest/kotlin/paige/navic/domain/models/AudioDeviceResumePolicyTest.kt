package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioDeviceResumePolicyTest {
	@Test
	fun resumesOnlyWhenEnabledPausedQueuedAndPlayableDeviceAdded() {
		assertTrue(
			shouldResumePlaybackWhenAudioDeviceAdded(
				resumePlaybackOnAudioDeviceConnect = true,
				isPlaying = false,
				hasMediaItems = true,
				hasPlayableDevice = true
			)
		)
		assertFalse(
			shouldResumePlaybackWhenAudioDeviceAdded(
				resumePlaybackOnAudioDeviceConnect = false,
				isPlaying = false,
				hasMediaItems = true,
				hasPlayableDevice = true
			)
		)
		assertFalse(
			shouldResumePlaybackWhenAudioDeviceAdded(
				resumePlaybackOnAudioDeviceConnect = true,
				isPlaying = true,
				hasMediaItems = true,
				hasPlayableDevice = true
			)
		)
		assertFalse(
			shouldResumePlaybackWhenAudioDeviceAdded(
				resumePlaybackOnAudioDeviceConnect = true,
				isPlaying = false,
				hasMediaItems = false,
				hasPlayableDevice = true
			)
		)
		assertFalse(
			shouldResumePlaybackWhenAudioDeviceAdded(
				resumePlaybackOnAudioDeviceConnect = true,
				isPlaying = false,
				hasMediaItems = true,
				hasPlayableDevice = false
			)
		)
	}
}
