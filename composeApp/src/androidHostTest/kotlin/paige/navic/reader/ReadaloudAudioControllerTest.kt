package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import paige.navic.shared.PlaybackService

class ReadaloudAudioControllerTest {
	@Test
	fun readaloudPlaybackUsesDedicatedMediaSessionService() {
		assertEquals(
			"paige.navic.reader.ReadaloudPlaybackService",
			ReadaloudPlaybackService.serviceClassName
		)
		assertNotEquals(
			PlaybackService::class.java.name,
			ReadaloudPlaybackService.serviceClassName
		)
		assertEquals(
			ReadaloudPlaybackService.serviceClassName,
			ReadaloudAudioController.serviceClassName
		)
	}
}
