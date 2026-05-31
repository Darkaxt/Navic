package paige.navic.ui.screens.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.ui.components.dialogs.DeletionEndpoint
import paige.navic.ui.navigation.Screen

class CollectionDetailDeleteNavigationPolicyTest {
	@Test
	fun playlistDeleteRemovesCurrentRouteWithoutRefreshingDeletedCollection() {
		val effect = collectionDeleteNavigationEffect(
			endpoint = DeletionEndpoint.PLAYLIST,
			collectionId = "playlist-id",
			tab = "library"
		)

		assertEquals(Screen.CollectionDetail("playlist-id", "library"), effect.routeToRemove)
		assertFalse(effect.refreshCurrentCollection)
	}

	@Test
	fun shareDeleteKeepsCurrentCollectionRefreshAvailable() {
		val effect = collectionDeleteNavigationEffect(
			endpoint = DeletionEndpoint.SHARE,
			collectionId = "playlist-id",
			tab = "library"
		)

		assertNull(effect.routeToRemove)
		assertTrue(effect.refreshCurrentCollection)
	}
}
