package paige.navic.ui.screens.collection

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import paige.navic.ui.components.dialogs.DeletionEndpoint
import paige.navic.ui.navigation.Screen

internal data class CollectionDeleteNavigationEffect(
	val routeToRemove: Screen? = null,
	val refreshCurrentCollection: Boolean = true
)

internal fun collectionDeleteNavigationEffect(
	endpoint: DeletionEndpoint,
	collectionId: String,
	tab: String
): CollectionDeleteNavigationEffect =
	when (endpoint) {
		DeletionEndpoint.PLAYLIST -> CollectionDeleteNavigationEffect(
			routeToRemove = Screen.CollectionDetail(collectionId, tab),
			refreshCurrentCollection = false
		)
		DeletionEndpoint.SHARE -> CollectionDeleteNavigationEffect()
	}

internal fun collectionDetailAlbumTrackLeadingWidth(): Dp = 25.dp
