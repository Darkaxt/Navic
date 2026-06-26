package paige.navic.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import paige.navic.domain.manager.SyncManager
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.entities.SyncActionType
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.data.database.mappers.toEntity
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainArtistListType
import paige.navic.domain.models.applyArtistListDirection
import paige.navic.domain.models.visibleArtistListEntries
import paige.navic.ui.core.UiState
import kotlin.time.Clock

class ArtistRepository(
	private val artistDao: ArtistDao,
	private val syncManager: SyncManager,
	private val dbRepository: DbRepository
) {
	private suspend fun getLocalData(
		listType: DomainArtistListType,
		reversed: Boolean
	): ImmutableList<DomainArtist> {
		return when (listType) {
			DomainArtistListType.AlphabeticalByName -> artistDao.getArtistsAlphabeticalByName()
			DomainArtistListType.Random -> artistDao.getArtistsRandom()
			DomainArtistListType.Starred -> artistDao.getArtistsStarred()
		}
			.map { it.toDomainModel() }
			.visibleArtistListEntries()
			.applyArtistListDirection(reversed)
			.toImmutableList()
	}

	private suspend fun refreshLocalData(
		listType: DomainArtistListType,
		reversed: Boolean
	): ImmutableList<DomainArtist> {
		dbRepository.syncArtists().getOrThrow()
		return getLocalData(listType, reversed)
	}

	fun getArtistsFlow(
		fullRefresh: Boolean,
		listType: DomainArtistListType,
		reversed: Boolean = false
	): Flow<UiState<ImmutableList<DomainArtist>>> = flow {
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
	 * Reactive artist view, backed by the shared Room queries (same SQL as [getLocalData]).
	 * Mapped per emission and shareable across collectors.
	 */
	fun artistsFlow(
		listType: DomainArtistListType,
		reversed: Boolean = false
	): Flow<ImmutableList<DomainArtist>> =
		when (listType) {
			DomainArtistListType.AlphabeticalByName -> artistDao.getArtistsAlphabeticalByNameFlow()
			DomainArtistListType.Random -> artistDao.getArtistsRandomFlow()
			DomainArtistListType.Starred -> artistDao.getArtistsStarredFlow()
		}
			.map { it.map { entity -> entity.toDomainModel() } }
			.map { artists -> artists.visibleArtistListEntries().applyArtistListDirection(reversed).toImmutableList() }

	/** Background network sync; writes to Room, which [artistsFlow] observes. */
	suspend fun syncArtists() {
		dbRepository.syncArtists().getOrThrow()
	}

	suspend fun isArtistStarred(artist: DomainArtist) = artistDao.isArtistStarred(artist.id)

	suspend fun starArtist(artist: DomainArtist) {
		val starredEntity = artist.toEntity().copy(
			starredAt = Clock.System.now()
		)
		artistDao.insertArtist(starredEntity)
		syncManager.enqueueAction(SyncActionType.STAR, artist.id)
	}

	suspend fun unstarArtist(artist: DomainArtist) {
		val unstarredEntity = artist.toEntity().copy(
			starredAt = null
		)
		artistDao.insertArtist(unstarredEntity)
		syncManager.enqueueAction(SyncActionType.UNSTAR, artist.id)
	}
}
