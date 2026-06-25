package paige.navic.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import paige.navic.domain.manager.SyncManager
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.entities.SyncActionType
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.data.database.mappers.toEntity
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainAlbumListType
import paige.navic.domain.models.shouldAutoDownloadStarredAlbum
import paige.navic.ui.core.UiState
import paige.navic.util.core.toSqlQuery
import kotlin.time.Clock

class AlbumRepository(
	private val albumDao: AlbumDao,
	private val downloadDao: DownloadDao,
	private val syncManager: SyncManager,
	private val dbRepository: DbRepository,
	private val preferenceManager: PreferenceManager,
	private val connectivityManager: ConnectivityManager,
	private val downloadManager: DownloadManager
) {
	private suspend fun getLocalData(
		listType: DomainAlbumListType,
		reversed: Boolean
	): ImmutableList<DomainAlbum> {
		val downloadedSongIds = if (listType == DomainAlbumListType.Downloaded) {
			downloadDao.getAllDownloadsList()
				.filter { it.status == DownloadStatus.DOWNLOADED }
				.map { it.songId }
				.toSet()
		} else null

		return albumDao
			.getAlbumsByQuery(listType.toSqlQuery())
			.map { it.toDomainModel() }
			.let { if (reversed) it.asReversed() else it }
			.filter { album -> downloadedSongIds == null || downloadedSongIds.containsAll(album.songs.map { it.id }) }
			.toImmutableList()
	}

	private suspend fun refreshLocalData(
		listType: DomainAlbumListType,
		reversed: Boolean
	): ImmutableList<DomainAlbum> {
		dbRepository.syncLibrarySongs().getOrThrow()
		return getLocalData(listType, reversed)
	}

	fun getAlbumsFlow(
		fullRefresh: Boolean,
		listType: DomainAlbumListType,
		reversed: Boolean
	): Flow<UiState<ImmutableList<DomainAlbum>>> = flow {
		val localData = getLocalData(listType, reversed)
		if (fullRefresh) {
			emit(UiState.Loading(data = localData))
			try {
				emit(UiState.Success(data = refreshLocalData(listType, reversed)))
			} catch (error: Exception) {
				emit(UiState.Error(error = error, data = localData))
			}
		} else {
			emit(UiState.Success(data = localData))
		}
	}.flowOn(Dispatchers.IO)

	/**
	 * Reactive view of albums for a list type, backed by the shared Room query
	 * ([getAlbumsByQueryFlow]). The SQL sort/filter is unchanged from [getLocalData];
	 * the Downloaded filter combines with the reactive downloads flow. The mapping runs
	 * per emission and the underlying Room query is shared across collectors.
	 */
	fun albumsFlow(
		listType: DomainAlbumListType,
		reversed: Boolean
	): Flow<ImmutableList<DomainAlbum>> {
		val queried = albumDao.getAlbumsByQueryFlow(listType.toSqlQuery())
			.map { rows -> rows.map { it.toDomainModel() } }
		val filtered = if (listType == DomainAlbumListType.Downloaded) {
			queried.combine(downloadManager.allDownloads) { albums, downloads ->
				val downloadedSongIds = downloads
					.filter { it.status == DownloadStatus.DOWNLOADED }
					.map { it.songId }
					.toSet()
				albums.filter { album -> downloadedSongIds.containsAll(album.songs.map { it.id }) }
			}
		} else {
			queried
		}
		return filtered.map { albums ->
			(if (reversed) albums.asReversed() else albums).toImmutableList()
		}
	}

	/** Background network sync; writes to Room, which [albumsFlow] observes. */
	suspend fun syncAlbums() {
		dbRepository.syncLibrarySongs().getOrThrow()
	}

	suspend fun isAlbumStarred(album: DomainAlbum) = albumDao.isAlbumStarred(album.id)
	suspend fun getAlbumRating(album: DomainAlbum) = albumDao.getAlbumRating(album.id) ?: 0

	suspend fun starAlbum(album: DomainAlbum) {
		val starredEntity = album.toEntity().copy(
			starredAt = Clock.System.now()
		)
		albumDao.insertAlbum(starredEntity)
		syncManager.enqueueAction(SyncActionType.STAR, album.id)
		if (shouldAutoDownloadStarredAlbum(
				autoDownloadStarredAlbums = preferenceManager.autoDownloadStarredAlbums,
				isStarring = true,
				isOnline = connectivityManager.isOnline.value,
				hasSongsToDownload = album.songs.any { !downloadManager.isDownloaded(it.id) }
			)
		) {
			downloadManager.downloadCollection(album)
		}
	}

	suspend fun unstarAlbum(album: DomainAlbum) {
		val unstarredEntity = album.toEntity().copy(
			starredAt = null
		)
		albumDao.insertAlbum(unstarredEntity)
		syncManager.enqueueAction(SyncActionType.UNSTAR, album.id)
	}

	suspend fun rateAlbum(album: DomainAlbum, rating: Int) {
		val ratedEntity = album.toEntity().copy(
			userRating = rating
		)
		albumDao.insertAlbum(ratedEntity)
		when (rating) {
			0 -> syncManager.enqueueAction(SyncActionType.STAR_0, album.id)
			1 -> syncManager.enqueueAction(SyncActionType.STAR_1, album.id)
			2 -> syncManager.enqueueAction(SyncActionType.STAR_2, album.id)
			3 -> syncManager.enqueueAction(SyncActionType.STAR_3, album.id)
			4 -> syncManager.enqueueAction(SyncActionType.STAR_4, album.id)
			5 -> syncManager.enqueueAction(SyncActionType.STAR_5, album.id)
		}
	}
}
