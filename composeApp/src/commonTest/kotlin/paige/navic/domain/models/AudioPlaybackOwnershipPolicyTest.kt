package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioPlaybackOwnershipPolicyTest {
	@Test
	fun pausesOnlyWhenAnotherOwnerClaimsPlaybackWhileCurrentOwnerIsPlaying() {
		assertTrue(
			shouldPauseForAudioPlaybackClaim(
				currentOwner = AudioPlaybackOwner.Music,
				claimedOwner = AudioPlaybackOwner.Audiobook,
				isPlaying = true
			)
		)
		assertTrue(
			shouldPauseForAudioPlaybackClaim(
				currentOwner = AudioPlaybackOwner.Audiobook,
				claimedOwner = AudioPlaybackOwner.Music,
				isPlaying = true
			)
		)
		assertFalse(
			shouldPauseForAudioPlaybackClaim(
				currentOwner = AudioPlaybackOwner.Music,
				claimedOwner = AudioPlaybackOwner.Music,
				isPlaying = true
			)
		)
		assertFalse(
			shouldPauseForAudioPlaybackClaim(
				currentOwner = AudioPlaybackOwner.Music,
				claimedOwner = AudioPlaybackOwner.Audiobook,
				isPlaying = false
			)
		)
	}
}
