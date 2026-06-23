package paige.navic.domain.models

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class PlaybackArtworkPolicySourceTest {
	@Test
	fun playbackArtworkPolicyDoesNotKeepNoOpCompatibilityHelpers() {
		val source = File(
			"src/commonMain/kotlin/paige/navic/domain/models/PlaybackArtworkPolicy.kt"
		).readText()

		assertFalse(
			"fun effectiveArtworkSourcePriority(" in source,
			"Artwork priority should use the stored priority directly instead of a no-op wrapper."
		)
		assertFalse(
			"fun effectiveAurralArtworkPriority(" in source,
			"Aurral should not have a compatibility helper that silently rewrites source priority."
		)
		assertFalse(
			"fun visiblePlaybackCoverArtId(" in source,
			"Playback cover visibility should be resolved through resolvedPlaybackArtwork, not dead helper code."
		)
		assertFalse(
			"fun visiblePlaybackImageUrl(" in source,
			"Playback image visibility should be resolved through resolvedPlaybackArtwork, not dead helper code."
		)
	}
}
