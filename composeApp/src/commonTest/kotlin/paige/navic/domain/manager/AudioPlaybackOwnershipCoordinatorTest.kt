package paige.navic.domain.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.models.AudioPlaybackOwner

class AudioPlaybackOwnershipCoordinatorTest {
	@Test
	fun newestClaimOwnsPlaybackAndStaleReleaseCannotClearIt() {
		val coordinator = AudioPlaybackOwnershipCoordinator()
		val musicClaim = coordinator.claim(AudioPlaybackOwner.Music)
		val audiobookClaim = coordinator.claim(AudioPlaybackOwner.Audiobook)

		assertEquals(AudioPlaybackOwner.Audiobook, coordinator.currentOwner)
		assertFalse(coordinator.release(musicClaim))
		assertEquals(AudioPlaybackOwner.Audiobook, coordinator.currentOwner)
		assertTrue(coordinator.release(audiobookClaim))
		assertNull(coordinator.currentOwner)
	}

	@Test
	fun olderClaimFromSameOwnerCannotReleaseNewerClaim() {
		val coordinator = AudioPlaybackOwnershipCoordinator()
		val oldClaim = coordinator.claim(AudioPlaybackOwner.Music)
		val currentClaim = coordinator.claim(AudioPlaybackOwner.Music)

		assertFalse(coordinator.release(oldClaim))
		assertEquals(AudioPlaybackOwner.Music, coordinator.currentOwner)
		assertTrue(coordinator.release(currentClaim))
		assertNull(coordinator.currentOwner)
	}

	@Test
	fun claimIsReplayableToSubscribersThatObserveAfterTheClaim() {
		val coordinator = AudioPlaybackOwnershipCoordinator()
		val claim = coordinator.claim(AudioPlaybackOwner.Audiobook)

		assertEquals(claim, coordinator.activeClaim.value)
		assertEquals(AudioPlaybackOwner.Audiobook, coordinator.currentOwner)
	}
}
