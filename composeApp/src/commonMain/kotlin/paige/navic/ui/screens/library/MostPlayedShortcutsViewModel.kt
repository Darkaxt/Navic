package paige.navic.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.SongDao
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.PlaybackOriginRepository
import paige.navic.ui.core.UiState
import paige.navic.util.core.Logger

class MostPlayedShortcutsViewModel(
	private val repository: PlaybackOriginRepository,
	private val artistDao: ArtistDao,
	private val albumDao: AlbumDao,
	private val songDao: SongDao,
	private val aurralRepository: AurralRepository
) : ViewModel() {
	private val _shortcutsState =
		MutableStateFlow<UiState<ImmutableList<DomainMostPlayedShortcut>>>(UiState.Loading())
	val shortcutsState = _shortcutsState.asStateFlow()
	private val aurralArtistArtwork =
		MutableStateFlow<List<MostPlayedShortcutArtistArtwork>>(emptyList())
	private val attemptedAurralArtistPhotoKeys = mutableSetOf<String>()

	init {
		viewModelScope.launch {
			combine(
				repository.observeMostPlayed(MOST_PLAYED_LIMIT),
				artistDao.getAllArtists(),
				albumDao.observeAlbumArtistArtwork(),
				songDao.observeArtistSongArtwork(),
				aurralArtistArtwork
			) { shortcuts, artists, albums, songs, aurralArtists ->
				mostPlayedShortcutsWithResolvedArtwork(
					shortcuts = shortcuts,
					artists = artists.map { artist ->
						MostPlayedShortcutArtistArtwork(
							id = artist.artistId,
							name = artist.name,
							coverArtId = artist.coverArtId,
							artistImageUrl = artist.artistImageUrl
						)
					} + aurralArtists,
					albums = albums.map { album ->
						MostPlayedShortcutAlbumArtwork(
							artistId = album.artistId,
							artistName = album.artistName,
							coverArtId = album.coverArtId,
							year = album.year,
							name = album.name
						)
					},
					songs = songs.map { song ->
						MostPlayedShortcutSongArtwork(
							artistId = song.artistId,
							artistName = song.artistName,
							coverArtId = song.coverArtId,
							year = song.year,
							albumTitle = song.albumTitle,
							title = song.title,
							playCount = song.playCount
						)
					}
				).toImmutableList()
			}
				.catch { error ->
					_shortcutsState.value = UiState.Error(
						error.asException(),
						_shortcutsState.value.data
					)
				}
				.collect { shortcuts ->
					_shortcutsState.value = UiState.Success(shortcuts)
					hydrateAurralArtistPhotos(shortcuts)
				}
		}
	}

	fun clearError() {
		_shortcutsState.value = UiState.Success(_shortcutsState.value.data ?: persistentListOf())
	}

	private fun Throwable.asException(): Exception =
		this as? Exception ?: Exception(this)

	private fun hydrateAurralArtistPhotos(shortcuts: List<DomainMostPlayedShortcut>) {
		val targets = shortcuts
			.asSequence()
			.filter { shortcut -> shortcut.type == PlaybackOriginType.Artist }
			.filterNot { shortcut -> shortcut.coverArtId.isAbsoluteHttpUrl() }
			.filterNot { shortcut ->
				aurralArtistArtwork.value.any { artist -> artist.matchesShortcut(shortcut) }
			}
			.filter { shortcut -> attemptedAurralArtistPhotoKeys.add(shortcut.artistPhotoLookupKey()) }
			.take(AURRAL_ARTIST_PHOTO_LOOKUP_LIMIT)
			.toList()
		if (targets.isEmpty()) return

		viewModelScope.launch {
			val resolved = targets.mapNotNull { shortcut ->
				aurralRepository.searchArtists(
					query = shortcut.title,
					limit = AURRAL_ARTIST_PHOTO_SEARCH_LIMIT
				).getOrNull()
					?.artists
					.orEmpty()
					.map { artist ->
						MostPlayedShortcutArtistArtwork(
							id = artist.id,
							name = artist.name,
							coverArtId = null,
							artistImageUrl = artist.imageUrl
						)
					}
					.let { candidates ->
						mostPlayedArtistArtworkForShortcut(shortcut, candidates)
					}
			}
			if (resolved.isEmpty()) return@launch
			aurralArtistArtwork.value = (aurralArtistArtwork.value + resolved)
				.distinctBy { artist ->
					listOf(artist.id, artist.name).joinToString("|") { it.trim().lowercase() }
				}
		}.invokeOnCompletion { error ->
			if (error != null) {
				Logger.w("MostPlayedShortcutsViewModel", "Failed to hydrate Aurral artist photos", error)
			}
		}
	}

	private fun DomainMostPlayedShortcut.artistPhotoLookupKey(): String =
		"${id.trim().lowercase()}|${title.trim().lowercase()}"

	private fun MostPlayedShortcutArtistArtwork.matchesShortcut(shortcut: DomainMostPlayedShortcut): Boolean =
		mostPlayedArtistArtworkForShortcut(shortcut, listOf(this)) != null

	private fun String?.isAbsoluteHttpUrl(): Boolean =
		this?.trim()?.let { value ->
			value.startsWith("http://", ignoreCase = true) ||
				value.startsWith("https://", ignoreCase = true)
		} == true

	private companion object {
		const val MOST_PLAYED_LIMIT = 20
		const val AURRAL_ARTIST_PHOTO_LOOKUP_LIMIT = 8
		const val AURRAL_ARTIST_PHOTO_SEARCH_LIMIT = 5
	}
}
