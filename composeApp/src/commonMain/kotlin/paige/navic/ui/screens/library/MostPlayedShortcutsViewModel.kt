package paige.navic.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.data.database.dao.SongDao
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.PlaybackOriginRepository
import paige.navic.ui.core.UiState
import paige.navic.util.core.Logger
import kotlin.time.Clock

class MostPlayedShortcutsViewModel(
	private val repository: PlaybackOriginRepository,
	private val artistDao: ArtistDao,
	private val artistPhotoCacheDao: ArtistPhotoCacheDao,
	private val albumDao: AlbumDao,
	private val songDao: SongDao,
	private val aurralRepository: AurralRepository,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	private val _shortcutsState =
		MutableStateFlow<UiState<ImmutableList<DomainMostPlayedShortcut>>>(UiState.Loading())
	val shortcutsState = _shortcutsState.asStateFlow()
	private val aurralArtistArtwork =
		MutableStateFlow<List<MostPlayedShortcutArtistArtwork>>(emptyList())
	private val attemptedAurralArtistPhotoKeys = mutableSetOf<String>()
	private var lastDiagnosticInputSignature: String? = null
	private var lastDiagnosticStateSignature: String? = null

	init {
		viewModelScope.launch {
			observeResolvedShortcuts()
				.flowOn(Dispatchers.Default)
				.catch { error ->
					_shortcutsState.value = UiState.Error(
						error.asException(),
						_shortcutsState.value.data
					)
				}
				.collect { shortcuts ->
					_shortcutsState.value = UiState.Success(shortcuts)
					logMostPlayedArtistState(shortcuts)
					hydrateAurralArtistPhotos(shortcuts)
				}
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	private fun observeResolvedShortcuts(): Flow<ImmutableList<DomainMostPlayedShortcut>> =
		repository.observeMostPlayed(MOST_PLAYED_LIMIT).flatMapLatest shortcutFlow@ { shortcuts ->
			val lookup = mostPlayedArtistLookupIdentities(shortcuts)
			if (lookup.ids.isEmpty() && lookup.normalizedNames.isEmpty()) {
				return@shortcutFlow flowOf(
					resolveShortcutArtwork(
						shortcuts = mostPlayedShortcutsWithResolvedLocalArtists(shortcuts, emptyList()),
						localArtists = emptyList(),
						cachedArtistPhotos = emptyList(),
						albums = emptyList(),
						songs = emptyList(),
						aurralArtists = emptyList()
					)
				)
			}

			artistDao.observeArtistsByIdentity(
				artistIds = lookup.ids,
				normalizedArtistNames = lookup.normalizedNames
			).flatMapLatest artistFlow@ { artists ->
				val localArtistArtwork = artists.map { artist ->
					MostPlayedShortcutArtistArtwork(
						id = artist.artistId,
						name = artist.name,
						coverArtId = artist.coverArtId,
						artistImageUrl = artist.artistImageUrl,
						trustedExternalPhoto = false
					)
				}
				val resolvedShortcuts = mostPlayedShortcutsWithResolvedLocalArtists(
					shortcuts = shortcuts,
					artists = localArtistArtwork
				)
				val resolvedLookup = mostPlayedArtistLookupIdentities(resolvedShortcuts)
				if (resolvedLookup.ids.isEmpty() && resolvedLookup.normalizedNames.isEmpty()) {
					return@artistFlow flowOf(
						resolveShortcutArtwork(
							shortcuts = resolvedShortcuts,
							localArtists = localArtistArtwork,
							cachedArtistPhotos = emptyList(),
							albums = emptyList(),
							songs = emptyList(),
							aurralArtists = emptyList()
						)
					)
				}

				combine(
					artistPhotoCacheDao.observeArtistPhotoCacheByIdentity(
						artistIds = resolvedLookup.ids,
						normalizedArtistNames = resolvedLookup.normalizedNames
					),
					albumDao.observeAlbumArtistArtworkByIdentity(
						artistIds = resolvedLookup.ids,
						normalizedArtistNames = resolvedLookup.normalizedNames
					),
					songDao.observeArtistSongArtworkByIdentity(
						artistIds = resolvedLookup.ids,
						normalizedArtistNames = resolvedLookup.normalizedNames
					),
					aurralArtistArtwork
				) { cachedArtistPhotos, albums, songs, aurralArtists ->
					resolveShortcutArtwork(
						shortcuts = resolvedShortcuts,
						localArtists = localArtistArtwork,
						cachedArtistPhotos = cachedArtistPhotos.map {
							it.toMostPlayedArtistPhotoCacheEntry()
						},
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
						},
						aurralArtists = aurralArtists.filter { artist ->
							resolvedShortcuts.any { shortcut -> artist.matchesShortcut(shortcut) }
						}
					)
				}
			}
		}

	private fun resolveShortcutArtwork(
		shortcuts: List<DomainMostPlayedShortcut>,
		localArtists: List<MostPlayedShortcutArtistArtwork>,
		cachedArtistPhotos: List<MostPlayedArtistPhotoCacheEntry>,
		albums: List<MostPlayedShortcutAlbumArtwork>,
		songs: List<MostPlayedShortcutSongArtwork>,
		aurralArtists: List<MostPlayedShortcutArtistArtwork>
	): ImmutableList<DomainMostPlayedShortcut> {
		val cachedArtistArtwork = shortcuts
			.mapNotNull { shortcut ->
				mostPlayedArtistPhotoCacheArtworkForShortcut(
					shortcut = shortcut,
					entries = cachedArtistPhotos
				)
			}
			.distinctBy { artist -> artist.id.trim().lowercase() }
		logMostPlayedArtistInputs(
			shortcuts = shortcuts,
			localArtists = localArtists,
			cachedArtists = cachedArtistArtwork,
			aurralArtists = aurralArtists
		)
		return mostPlayedShortcutsWithResolvedArtwork(
			shortcuts = shortcuts,
			artists = cachedArtistArtwork + aurralArtists + localArtists,
			albums = albums,
			songs = songs,
			artistArtworkPriority = preferenceManager.artistArtworkPriority,
			aurralArtworkEnabled = preferenceManager.aurralEnabled
		).toImmutableList()
	}

	fun clearError() {
		_shortcutsState.value = UiState.Success(_shortcutsState.value.data ?: persistentListOf())
	}

	private fun Throwable.asException(): Exception =
		this as? Exception ?: Exception(this)

	private fun hydrateAurralArtistPhotos(shortcuts: List<DomainMostPlayedShortcut>) {
		if (!shouldHydrateAurralArtistPhotos(
				aurralEnabled = preferenceManager.aurralEnabled,
				artistArtworkPriority = preferenceManager.artistArtworkPriority
			)
		) {
			Logger.i(
				MOST_PLAYED_ARTWORK_TAG,
				"hydrate disabled aurralEnabled=${preferenceManager.aurralEnabled} " +
					"artistArtworkPriority=${preferenceManager.artistArtworkPriority}"
			)
			return
		}
		val targets = mutableListOf<DomainMostPlayedShortcut>()
		shortcuts
			.filter { shortcut -> shortcut.type == PlaybackOriginType.Artist }
			.forEach { shortcut ->
				val key = shortcut.artistPhotoLookupKey()
				when {
					aurralArtistArtwork.value.any { artist -> artist.matchesShortcut(shortcut) } -> {
						Logger.i(
							MOST_PLAYED_ARTWORK_TAG,
							"hydrate skip reason=cached-aurral id=${mostPlayedDiagnosticText(shortcut.id)} " +
								"title=${mostPlayedDiagnosticText(shortcut.title)}"
						)
					}

					!attemptedAurralArtistPhotoKeys.add(key) -> {
						Logger.i(
							MOST_PLAYED_ARTWORK_TAG,
							"hydrate skip reason=already-attempted id=${mostPlayedDiagnosticText(shortcut.id)} " +
								"title=${mostPlayedDiagnosticText(shortcut.title)} " +
								"cover=${mostPlayedDiagnosticUrlSummary(shortcut.coverArtId)}"
						)
					}

					else -> {
						targets += shortcut
						Logger.i(
							MOST_PLAYED_ARTWORK_TAG,
							"hydrate queued id=${mostPlayedDiagnosticText(shortcut.id)} " +
								"title=${mostPlayedDiagnosticText(shortcut.title)} " +
								"existingCover=${mostPlayedDiagnosticUrlSummary(shortcut.coverArtId)}"
						)
					}
				}
			}
		if (targets.isEmpty()) return

		viewModelScope.launch(Dispatchers.IO) {
			val resolved = targets.mapNotNull { shortcut ->
				val result = aurralRepository.searchArtists(
					query = shortcut.title,
					limit = AURRAL_ARTIST_PHOTO_SEARCH_LIMIT
				)
				result.onFailure { error ->
					Logger.w(
						MOST_PLAYED_ARTWORK_TAG,
						"hydrate search failed id=${mostPlayedDiagnosticText(shortcut.id)} " +
							"title=${mostPlayedDiagnosticText(shortcut.title)}",
						error
					)
				}
				val candidates = result.getOrNull()
					?.artists
					.orEmpty()
					.map { artist ->
						MostPlayedShortcutArtistArtwork(
							id = artist.id,
							name = artist.name,
							coverArtId = null,
							artistImageUrl = artist.imageUrl,
							trustedExternalPhoto = true
						)
					}
				Logger.i(
					MOST_PLAYED_ARTWORK_TAG,
					"hydrate search result id=${mostPlayedDiagnosticText(shortcut.id)} " +
						"title=${mostPlayedDiagnosticText(shortcut.title)} candidates=${candidates.size}"
				)
				candidates.take(5).forEachIndexed { index, candidate ->
					Logger.i(
						MOST_PLAYED_ARTWORK_TAG,
						"hydrate candidate[$index] shortcut=${mostPlayedDiagnosticText(shortcut.title)} " +
							"id=${mostPlayedDiagnosticText(candidate.id)} " +
							"name=${mostPlayedDiagnosticText(candidate.name)} " +
							"image=${mostPlayedDiagnosticUrlSummary(candidate.artistImageUrl)}"
					)
				}
				mostPlayedArtistArtworkForShortcut(shortcut, candidates).also { selected ->
					if (selected == null) {
						Logger.w(
							MOST_PLAYED_ARTWORK_TAG,
							"hydrate no-selected-artist-photo id=${mostPlayedDiagnosticText(shortcut.id)} " +
								"title=${mostPlayedDiagnosticText(shortcut.title)}"
						)
					} else {
						Logger.i(
							MOST_PLAYED_ARTWORK_TAG,
							"hydrate selected id=${mostPlayedDiagnosticText(shortcut.id)} " +
								"title=${mostPlayedDiagnosticText(shortcut.title)} " +
								"selectedId=${mostPlayedDiagnosticText(selected.id)} " +
								"selectedName=${mostPlayedDiagnosticText(selected.name)} " +
								"image=${mostPlayedDiagnosticUrlSummary(selected.artistImageUrl)}"
						)
					}
				}?.let { selected -> shortcut to selected }
			}
			if (resolved.isEmpty()) return@launch
			val nowMillis = Clock.System.now().toEpochMilliseconds()
			val cacheEntries = resolved.mapNotNull { (shortcut, selected) ->
				mostPlayedArtistPhotoCacheEntity(
					shortcut = shortcut,
					artist = selected,
					nowMillis = nowMillis
				)
			}
			if (cacheEntries.isNotEmpty()) {
				artistPhotoCacheDao.upsertArtistPhotoCacheEntries(cacheEntries)
				Logger.i(
					MOST_PLAYED_ARTWORK_TAG,
					"hydrate persisted artistPhotoCacheEntries=${cacheEntries.size}"
				)
			}
			aurralArtistArtwork.value = (aurralArtistArtwork.value + resolved.map { it.second })
				.distinctBy { artist ->
					listOf(artist.id, artist.name).joinToString("|") { it.trim().lowercase() }
				}
		}.invokeOnCompletion { error ->
			if (error != null) {
				Logger.w("MostPlayedShortcutsViewModel", "Failed to hydrate Aurral artist photos", error)
			}
		}
	}

	private fun logMostPlayedArtistState(shortcuts: List<DomainMostPlayedShortcut>) {
		val artists = shortcuts.filter { shortcut -> shortcut.type == PlaybackOriginType.Artist }
		val signature = buildString {
			artists.forEach { shortcut ->
				append(shortcut.id)
				append('|')
				append(shortcut.title)
				append('|')
				append(shortcut.coverArtId)
				append(';')
			}
			append("cache=")
			aurralArtistArtwork.value.forEach { artist ->
				append(artist.id)
				append('|')
				append(artist.name)
				append('|')
				append(artist.artistImageUrl)
				append(';')
			}
		}
		if (signature == lastDiagnosticStateSignature) return
		lastDiagnosticStateSignature = signature

		Logger.i(
			MOST_PLAYED_ARTWORK_TAG,
			"state artistShortcuts=${artists.size} aurralPhotoCache=${aurralArtistArtwork.value.size}"
		)
		artists.forEach { shortcut ->
			Logger.i(
				MOST_PLAYED_ARTWORK_TAG,
				"state item id=${mostPlayedDiagnosticText(shortcut.id)} " +
					"title=${mostPlayedDiagnosticText(shortcut.title)} " +
					"cover=${mostPlayedDiagnosticUrlSummary(shortcut.coverArtId)} " +
					"coverAbsolute=${shortcut.coverArtId.isAbsoluteHttpUrl()} " +
					"cachedAurral=${aurralArtistArtwork.value.any { artist -> artist.matchesShortcut(shortcut) }} " +
					"attempted=${attemptedAurralArtistPhotoKeys.contains(shortcut.artistPhotoLookupKey())}"
			)
		}
	}

	private fun logMostPlayedArtistInputs(
		shortcuts: List<DomainMostPlayedShortcut>,
		localArtists: List<MostPlayedShortcutArtistArtwork>,
		cachedArtists: List<MostPlayedShortcutArtistArtwork>,
		aurralArtists: List<MostPlayedShortcutArtistArtwork>
	) {
		val artistShortcuts = shortcuts.filter { shortcut -> shortcut.type == PlaybackOriginType.Artist }
		val signature = buildString {
			artistShortcuts.forEach { shortcut ->
				append(shortcut.id)
				append('|')
				append(shortcut.title)
				append('|')
				append(shortcut.coverArtId)
				append(';')
			}
			append("local=")
			localArtists.forEach { artist ->
				append(artist.id)
				append('|')
				append(artist.name)
				append('|')
				append(artist.coverArtId)
				append('|')
				append(artist.artistImageUrl)
				append(';')
			}
			append("cached=")
			cachedArtists.forEach { artist ->
				append(artist.id)
				append('|')
				append(artist.name)
				append('|')
				append(artist.artistImageUrl)
				append(';')
			}
			append("aurral=")
			aurralArtists.forEach { artist ->
				append(artist.id)
				append('|')
				append(artist.name)
				append('|')
				append(artist.artistImageUrl)
				append(';')
			}
		}
		if (signature == lastDiagnosticInputSignature) return
		lastDiagnosticInputSignature = signature

		Logger.i(
			MOST_PLAYED_ARTWORK_TAG,
			"inputs artistShortcuts=${artistShortcuts.size} " +
				"localArtists=${localArtists.size} cachedArtists=${cachedArtists.size} " +
				"aurralArtists=${aurralArtists.size}"
		)
		artistShortcuts.forEach { shortcut ->
			val localMatches = localArtists.filter { artist -> artist.looseInputMatches(shortcut) }
			val cachedMatches = cachedArtists.filter { artist -> artist.looseInputMatches(shortcut) }
			val aurralMatches = aurralArtists.filter { artist -> artist.looseInputMatches(shortcut) }
			Logger.i(
				MOST_PLAYED_ARTWORK_TAG,
				"inputs item id=${mostPlayedDiagnosticText(shortcut.id)} " +
					"title=${mostPlayedDiagnosticText(shortcut.title)} " +
					"snapshot=${mostPlayedDiagnosticUrlSummary(shortcut.coverArtId)} " +
					"localMatches=${localMatches.size} cachedMatches=${cachedMatches.size} " +
					"aurralMatches=${aurralMatches.size}"
			)
			localMatches.take(3).forEachIndexed { index, artist ->
				Logger.i(
					MOST_PLAYED_ARTWORK_TAG,
					"inputs local[$index] shortcut=${mostPlayedDiagnosticText(shortcut.title)} " +
						"id=${mostPlayedDiagnosticText(artist.id)} " +
						"name=${mostPlayedDiagnosticText(artist.name)} " +
						"coverArtId=${mostPlayedDiagnosticUrlSummary(artist.coverArtId)} " +
						"artistImage=${mostPlayedDiagnosticUrlSummary(artist.artistImageUrl)}"
				)
			}
			cachedMatches.take(3).forEachIndexed { index, artist ->
				Logger.i(
					MOST_PLAYED_ARTWORK_TAG,
					"inputs cached[$index] shortcut=${mostPlayedDiagnosticText(shortcut.title)} " +
						"id=${mostPlayedDiagnosticText(artist.id)} " +
						"name=${mostPlayedDiagnosticText(artist.name)} " +
						"artistImage=${mostPlayedDiagnosticUrlSummary(artist.artistImageUrl)}"
				)
			}
			aurralMatches.take(3).forEachIndexed { index, artist ->
				Logger.i(
					MOST_PLAYED_ARTWORK_TAG,
					"inputs aurral[$index] shortcut=${mostPlayedDiagnosticText(shortcut.title)} " +
						"id=${mostPlayedDiagnosticText(artist.id)} " +
						"name=${mostPlayedDiagnosticText(artist.name)} " +
						"artistImage=${mostPlayedDiagnosticUrlSummary(artist.artistImageUrl)}"
				)
			}
		}
	}

	private fun DomainMostPlayedShortcut.artistPhotoLookupKey(): String =
		"${id.trim().lowercase()}|${title.trim().lowercase()}"

	private fun MostPlayedShortcutArtistArtwork.matchesShortcut(shortcut: DomainMostPlayedShortcut): Boolean =
		mostPlayedArtistArtworkForShortcut(shortcut, listOf(this)) != null

	private fun MostPlayedShortcutArtistArtwork.looseInputMatches(shortcut: DomainMostPlayedShortcut): Boolean {
		val shortcutId = shortcut.id.trim().lowercase()
		val shortcutTitle = shortcut.title.trim().lowercase().replace(Regex("""\s+"""), " ")
		val artistId = id.trim().lowercase()
		val artistName = name.trim().lowercase().replace(Regex("""\s+"""), " ")
		return artistId == shortcutId || artistName == shortcutTitle
	}

	private fun String?.isAbsoluteHttpUrl(): Boolean =
		this?.trim()?.let { value ->
			value.startsWith("http://", ignoreCase = true) ||
				value.startsWith("https://", ignoreCase = true)
		} == true

	private companion object {
		const val MOST_PLAYED_LIMIT = 20
		const val AURRAL_ARTIST_PHOTO_SEARCH_LIMIT = 5
	}
}
