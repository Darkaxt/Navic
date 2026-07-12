package paige.navic.domain.manager

import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import paige.navic.domain.models.AudioPlaybackOwner

class AudioPlaybackOwnershipCoordinatorConcurrencyTest {
	@Test
	fun simultaneousClaimsLeaveExactlyOneReleasableOwner() {
		val coordinator = AudioPlaybackOwnershipCoordinator()
		val start = CountDownLatch(1)
		val claims = Collections.synchronizedList(mutableListOf<AudioPlaybackOwnershipClaim>())
		val workers = List(32) { index ->
			thread(start = true) {
				start.await()
				claims += coordinator.claim(
					if (index % 2 == 0) AudioPlaybackOwner.Music else AudioPlaybackOwner.Audiobook
				)
			}
		}

		start.countDown()
		workers.forEach(Thread::join)

		assertNotNull(coordinator.currentOwner)
		assertEquals(1, claims.count(coordinator::release))
	}
}
