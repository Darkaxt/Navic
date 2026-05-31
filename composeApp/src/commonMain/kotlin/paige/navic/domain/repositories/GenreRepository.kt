package paige.navic.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.models.DomainGenre
import paige.navic.domain.models.genreGroupsFromAlbums
import paige.navic.ui.core.UiState

class GenreRepository(
	private val albumDao: AlbumDao,
	private val dbRepository: DbRepository
) {
	private suspend fun getLocalData(): ImmutableList<DomainGenre> {
		return genreGroupsFromAlbums(
			albumDao
				.getAllAlbumsList()
				.map { it.toDomainModel() }
		)
			.toImmutableList()
	}

	private suspend fun refreshLocalData(): ImmutableList<DomainGenre> {
		dbRepository.syncLibrarySongs().getOrThrow()
		dbRepository.syncGenres().getOrThrow()
		return getLocalData()
	}

	suspend fun getGenreByName(
		genreName: String,
		fullRefresh: Boolean
	): DomainGenre? {
		val genres = if (fullRefresh) refreshLocalData() else getLocalData()
		return genres.firstOrNull { it.name.equals(genreName, ignoreCase = true) }
	}

	fun getGenresFlow(
		fullRefresh: Boolean
	): Flow<UiState<ImmutableList<DomainGenre>>> = flow {
		val localData = getLocalData()
		if (fullRefresh) {
			emit(UiState.Loading(data = localData))
			try {
				emit(UiState.Success(data = refreshLocalData()))
			} catch (error: Exception) {
				emit(UiState.Error(error = error, data = localData))
			}
		} else {
			emit(UiState.Success(data = localData))
		}
	}.flowOn(Dispatchers.IO)
}
