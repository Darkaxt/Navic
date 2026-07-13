package paige.navic.domain.interactors

import paige.navic.data.database.dao.PlaylistDao
import paige.navic.data.database.dao.SongDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.data.database.mappers.toEntity
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.discoverQueueRemovalIndexes
import paige.navic.domain.models.songRadioQueue
import paige.navic.domain.repositories.SongRepository
import paige.navic.util.core.Logger

class PlaybackQueueInteractor(
	private val songDao: SongDao,
	private val playlistDao: PlaylistDao,
	private val songRepository: SongRepository,
	private val sessionManager: SessionManager
) {
	suspend fun librarySongs(): List<DomainSong> = songRepository.getAllSongs()

	suspend fun discoverRemovalIndexes(
		queue: List<DomainSong>,
		currentIndex: Int
	): List<Int> {
		if (currentIndex !in queue.indices) return emptyList()
		val candidateIds = queue
			.drop(currentIndex + 1)
			.map(DomainSong::id)
			.filterNot { id -> id.startsWith("radio_") }
			.distinct()
		if (candidateIds.isEmpty()) return emptyList()
		val knownSongIds = songDao.getStarredSongIds(candidateIds).toSet() +
			playlistDao.getPlaylistSongIds(candidateIds)
		return discoverQueueRemovalIndexes(
			queueSongIds = queue.map(DomainSong::id),
			currentIndex = currentIndex,
			knownSongIds = knownSongIds
		)
	}

	suspend fun songRadio(
		seedSong: DomainSong,
		limit: Int,
		isAvailable: (String) -> Boolean
	): List<DomainSong> {
		val serverSimilarSongs = serverSimilarSongs(seedSong.id, limit)
			.filter { song -> isAvailable(song.id) }
		val librarySongs = librarySongs()
			.filter { song -> isAvailable(song.id) }
			.shuffled()
		return songRadioQueue(
			seedSong = seedSong,
			candidateSongs = serverSimilarSongs + librarySongs,
			limit = limit,
			preferredSongIds = serverSimilarSongs.map(DomainSong::id)
		)
	}

	suspend fun serverSimilarSongs(songId: String, limit: Int): List<DomainSong> =
		runCatching {
			sessionManager.withApi { api ->
				api.getSimilarSongsID3(songId, limit.coerceAtLeast(0))
			}
				.map { song -> song.toEntity().toDomainModel() }
		}.onFailure { error ->
			Logger.w("MediaPlayer", "Navidrome similar-song lookup failed", error)
		}.getOrDefault(emptyList())
}
