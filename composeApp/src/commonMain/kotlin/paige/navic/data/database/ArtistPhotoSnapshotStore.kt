package paige.navic.data.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.data.database.entities.ArtistPhotoCacheEntity

class ArtistPhotoSnapshotStore(
	artistPhotoCacheDao: ArtistPhotoCacheDao,
	scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
	val entries: StateFlow<List<ArtistPhotoCacheEntity>> = artistPhotoCacheDao
		.observeArtistPhotoCache()
		.stateIn(scope, SharingStarted.Lazily, emptyList())
}
