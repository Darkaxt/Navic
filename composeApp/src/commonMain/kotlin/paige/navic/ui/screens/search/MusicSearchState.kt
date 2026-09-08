package paige.navic.ui.screens.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSong
import paige.navic.ui.core.UiState

enum class MusicSearchSource {
	Library, Navidrome, AurralArtists, AurralAlbums;

	fun isRelevant(category: SearchCategory): Boolean = when (this) {
		Library, Navidrome -> true
		AurralArtists -> category == SearchCategory.ALL || category == SearchCategory.ARTISTS
		AurralAlbums -> category == SearchCategory.ALL || category == SearchCategory.ALBUMS
	}
}

data class MusicSearchState(
	val query: String = "",
	val pending: Set<MusicSearchSource> = emptySet(),
	val completed: Map<MusicSearchSource, UiState<List<Any>>> = emptyMap()
) {
	val results: List<Any>
		get() {
			val remote = completed[MusicSearchSource.Navidrome]?.data.orEmpty()
			val remoteById = remote.associateBy(::librarySearchIdentity)
			val library = completed[MusicSearchSource.Library]?.data.orEmpty()
			return (library.map { remoteById[librarySearchIdentity(it)] ?: it } + remote)
				.distinctBy(::librarySearchIdentity) +
				completed[MusicSearchSource.AurralArtists]?.data.orEmpty() +
				completed[MusicSearchSource.AurralAlbums]?.data.orEmpty()
		}

	fun isLoading(category: SearchCategory): Boolean = pending.any { it.isRelevant(category) }

	fun failure(category: SearchCategory): UiState.Error<List<Any>>? =
		completed.entries.firstNotNullOfOrNull { (source, result) ->
			(result as? UiState.Error)?.takeIf { source.isRelevant(category) }
		}

	fun contentState(category: SearchCategory): UiState<List<Any>> {
		val available = results
		if (!searchResultBuckets(available, category).isEmpty) return UiState.Success(available)
		if (isLoading(category)) return UiState.Loading()
		return failure(category) ?: UiState.Success(available)
	}
}

private fun librarySearchIdentity(item: Any): Any = when (item) {
	is DomainArtist -> DomainArtist::class to item.id
	is DomainAlbum -> DomainAlbum::class to item.id
	is DomainSong -> DomainSong::class to item.id
	is DomainPlaylist -> DomainPlaylist::class to item.id
	else -> item
}

internal typealias MusicSearchRequests = Map<MusicSearchSource, suspend () -> List<Any>>

internal fun eligibleMusicSearchRequest(
	isEligible: () -> Boolean,
	search: suspend () -> List<Any>
): suspend () -> List<Any> = { if (isEligible()) search() else emptyList() }

/** Query replacement owns cancellation before the existing input debounce, not after it. */
internal fun musicSearchStates(
	queries: Flow<String>,
	requests: (String) -> MusicSearchRequests
): Flow<MusicSearchState> = channelFlow {
	val publication = Mutex()
	var generation = 0L
	var work: Job? = null
	var state = MusicSearchState()
	queries.collect { rawQuery ->
		val query = rawQuery.trim()
		val sources = if (query.isEmpty()) emptyMap() else requests(query)
		publication.withLock {
			val owner = ++generation
			work?.cancel()
			state = MusicSearchState(query, sources.keys)
			send(state)
			// Cancelled cleanup remains a child of this flow, but cannot hold up a new query.
			work = if (sources.isEmpty()) null else launch {
				delay(300) // Existing input debounce, never a request deadline.
				for ((source, search) in sources) launch {
					val result: UiState<List<Any>> = try {
						UiState.Success(search())
					} catch (error: Exception) {
						if (error is CancellationException) throw error
						UiState.Error(error)
					}
					currentCoroutineContext().ensureActive()
					publication.withLock {
						if (generation == owner) {
							state = state.copy(pending = state.pending - source, completed = state.completed + (source to result))
							send(state)
						}
					}
				}
			}
		}
	}
}.buffer(Channel.CONFLATED)
