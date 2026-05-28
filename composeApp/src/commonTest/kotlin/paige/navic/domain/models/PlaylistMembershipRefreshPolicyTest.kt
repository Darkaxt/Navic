package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistMembershipRefreshPolicyTest {
	@Test
	fun refreshesOnlyNonBlankSelectedPlaylistIdsInOrder() {
		assertEquals(
			listOf("playlist-1", "playlist-2"),
			playlistIdsToRefreshAfterMembershipUpdate(
				listOf("playlist-1", "", " playlist-2 ", "playlist-1")
			)
		)
	}
}
