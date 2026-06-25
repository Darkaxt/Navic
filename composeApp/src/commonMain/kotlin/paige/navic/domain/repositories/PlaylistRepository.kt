package paige.navic.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.dao.PlaylistDao
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainPlaylistListType
import paige.navic.ui.core.UiState

class PlaylistRepository(
	private val playlistDao: PlaylistDao,
	private val dbRepository: DbRepository,
	private val downloadDao: DownloadDao
) {
	private suspend fun getLocalData(
		listType: DomainPlaylistListType,
		reversed: Boolean
	): ImmutableList<DomainPlaylist> {
		val sorted = when (listType) {
			DomainPlaylistListType.Name -> playlistDao.getAllPlaylistsByName()
			DomainPlaylistListType.DateAdded -> playlistDao.getAllPlaylistsByDateAdded()
			DomainPlaylistListType.Duration -> playlistDao.getAllPlaylistsByDuration()
			DomainPlaylistListType.Random -> playlistDao.getAllPlaylistsRandom()
			DomainPlaylistListType.Downloaded -> {
				playlistDao.getAllPlaylistsByDateAdded().filter { (_, songs) ->
					downloadDao.getAllDownloadsList()
						.filter { it.status == DownloadStatus.DOWNLOADED }
						.map { it.songId }
						.containsAll(songs.map { it.song.songId })
				}
			}
		}.map { it.toDomainModel() }.toImmutableList()
		return if (reversed) {
			sorted.reversed().toImmutableList()
		} else {
			sorted
		}
	}

	private suspend fun refreshLocalData(
		listType: DomainPlaylistListType,
		reversed: Boolean
	): ImmutableList<DomainPlaylist> {
		dbRepository.syncPlaylists().getOrThrow().forEach { playlist ->
			dbRepository.syncPlaylistSongs(playlist.playlistId).getOrThrow()
		}
		return getLocalData(listType, reversed)
	}

	suspend fun getPlaylistForPlayback(playlist: DomainPlaylist): DomainPlaylist {
		if (!shouldRefreshPlaylistSongsBeforePlayback(playlist)) {
			return playlist
		}

		dbRepository.syncPlaylistSongs(playlist.id).getOrThrow()
		return playlistDao.getPlaylistById(playlist.id)?.toDomainModel() ?: playlist
	}

	fun getPlaylistSongIdsFlow(songIds: List<String>): Flow<Set<String>> {
		val distinctSongIds = songIds.distinct()
		return if (distinctSongIds.isEmpty()) {
			flowOf(emptySet())
		} else {
			playlistDao.getPlaylistSongIdsFlow(distinctSongIds)
				.map { it.toSet() }
				.flowOn(Dispatchers.IO)
		}
	}

	fun getPlaylistsFlow(
		fullRefresh: Boolean,
		listType: DomainPlaylistListType,
		reversed: Boolean
	): Flow<UiState<ImmutableList<DomainPlaylist>>> = flow {
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
	 * Reactive playlist view, backed by the shared Room queries (same SQL as
	 * [getLocalData]). The Downloaded filter combines with the reactive downloads flow.
	 * Mapped per emission and shareable across collectors.
	 */
	fun playlistsFlow(
		listType: DomainPlaylistListType,
		reversed: Boolean
	): Flow<ImmutableList<DomainPlaylist>> {
		val base = when (listType) {
			DomainPlaylistListType.Name -> playlistDao.getAllPlaylistsByNameFlow()
			DomainPlaylistListType.DateAdded -> playlistDao.getAllPlaylistsByDateAddedFlow()
			DomainPlaylistListType.Duration -> playlistDao.getAllPlaylistsByDurationFlow()
			DomainPlaylistListType.Random -> playlistDao.getAllPlaylistsRandomFlow()
			DomainPlaylistListType.Downloaded -> playlistDao.getAllPlaylistsByDateAddedFlow()
				.combine(downloadDao.getAllDownloads()) { playlists, downloads ->
					val downloadedSongIds = downloads
						.filter { it.status == DownloadStatus.DOWNLOADED }
						.map { it.songId }
					playlists.filter { playlist ->
						downloadedSongIds.containsAll(playlist.songs.map { it.song.songId })
					}
				}
		}
		return base
			.map { it.map { playlist -> playlist.toDomainModel() } }
			.map { playlists -> (if (reversed) playlists.reversed() else playlists).toImmutableList() }
	}

	/** Background network sync; writes to Room, which [playlistsFlow] observes. */
	suspend fun syncPlaylists() {
		dbRepository.syncPlaylists().getOrThrow().forEach { playlist ->
			dbRepository.syncPlaylistSongs(playlist.playlistId).getOrThrow()
		}
	}
}
