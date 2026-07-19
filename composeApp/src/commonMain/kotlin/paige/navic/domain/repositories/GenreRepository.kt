package paige.navic.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.models.DomainGenre
import paige.navic.domain.models.DomainGenreSummary
import paige.navic.domain.models.GenreAlbumSummaryInput
import paige.navic.domain.models.genreGroupByName
import paige.navic.domain.models.genreSummariesFromAlbums

class GenreRepository(
	private val albumDao: AlbumDao
) {
	fun observeGenreSummaries(): Flow<ImmutableList<DomainGenreSummary>> =
		albumDao.observeAlbumGenreMetadata().map { albums ->
			genreSummariesFromAlbums(
				albums.map { album ->
					GenreAlbumSummaryInput(
						albumId = album.albumId,
						genre = album.genre,
						genres = album.genres,
						coverArtId = album.coverArtId,
						songCount = album.songCount
					)
				}
			).toImmutableList()
		}.flowOn(Dispatchers.IO)

	fun observeGenreByName(genreName: String): Flow<DomainGenre?> =
		albumDao.getAlbumsByGenre(genreName).map { albums ->
			genreGroupByName(
				albums = albums.map { it.toDomainModel() },
				genreName = genreName
			)
		}.flowOn(Dispatchers.IO)
}
