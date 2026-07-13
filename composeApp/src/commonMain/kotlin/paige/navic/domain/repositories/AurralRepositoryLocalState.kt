package paige.navic.domain.repositories

import paige.navic.data.remote.aurral.withLibraryArtistMonitoring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistEnrichment

internal class AurralRepositoryLocalState(
	private val nowMillis: () -> Long
) {
	private val optimisticAlbumRequestsByMbid = mutableMapOf<String, AurralAlbumRequest>()
	private val optimisticArtistMonitoringByMbid = mutableMapOf<String, Boolean>()
	private val releaseGroupCoverUrlsByMbid = mutableMapOf<String, String>()
	private var libraryArtistsCache: AurralLibraryArtistsCacheEntry? = null
	private val _albumRequests = MutableStateFlow<List<AurralAlbumRequest>>(emptyList())
	val albumRequests = _albumRequests.asStateFlow()
	private val _libraryArtistMonitorStates = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
	val libraryArtistMonitorStates = _libraryArtistMonitorStates.asStateFlow()
	private val _artistStateRevision = MutableStateFlow(0)
	val artistStateRevision = _artistStateRevision.asStateFlow()

	fun clearIntegrationState() {
		_albumRequests.value = emptyList()
		_libraryArtistMonitorStates.value = emptyMap()
	}

	fun clearAlbumRequests() {
		_albumRequests.value = emptyList()
	}

	fun setAlbumRequests(requests: List<AurralAlbumRequest>) {
		_albumRequests.value = requests
	}

	fun rememberOptimisticAlbumRequest(payload: AurralAlbumRequestPayload) {
		val albumKey = payload.albumMbid.normalizedAurralCacheKey() ?: return
		val request = AurralAlbumRequest(
			albumMbid = payload.albumMbid,
			albumName = payload.albumName,
			artistMbid = payload.artistMbid,
			artistName = payload.artistName,
			status = "requested"
		)
		if (optimisticAlbumRequestsByMbid[albumKey] == request) return
		optimisticAlbumRequestsByMbid[albumKey] = request
		upsertAlbumRequest(request)
		bumpArtistStateRevision()
	}

	fun upsertAlbumRequest(request: AurralAlbumRequest) {
		val requestKey = request.albumMbid.normalizedAurralCacheKey()
			?: request.albumName.normalizedAurralCacheKey()
			?: return
		val current = _albumRequests.value
		val filtered = current.filterNot { existing ->
			existing.albumMbid.normalizedAurralCacheKey() == requestKey ||
				(existing.albumMbid.normalizedAurralCacheKey() == null &&
					existing.albumName.normalizedAurralCacheKey() == requestKey)
		}
		_albumRequests.value = filtered + request
	}

	fun removeAlbumRequest(
		albumMbid: String?,
		albumName: String
	) {
		val albumMbidKey = albumMbid.normalizedAurralCacheKey()
		val albumNameKey = albumName.normalizedAurralCacheKey()
		_albumRequests.value = _albumRequests.value.filterNot { request ->
			(albumMbidKey != null && request.albumMbid.normalizedAurralCacheKey() == albumMbidKey) ||
				(albumNameKey != null && request.albumName.normalizedAurralCacheKey() == albumNameKey)
		}
	}

	fun rememberOptimisticArtistMonitoring(
		artistMbid: String,
		artistName: String,
		monitored: Boolean
	) {
		val artistKey = artistMbid.normalizedAurralCacheKey() ?: return
		if (optimisticArtistMonitoringByMbid[artistKey] == monitored) return
		optimisticArtistMonitoringByMbid[artistKey] = monitored
		libraryArtistsCache = libraryArtistsCache?.withMonitoring(
			artistMbid = artistMbid,
			artistName = artistName,
			monitored = monitored
		)
		publishLibraryArtistMonitorStates(libraryArtistsCache?.artists.orEmpty())
		upsertLibraryArtistMonitorState(artistMbid, artistName, monitored)
		bumpArtistStateRevision()
	}

	fun rememberReleaseGroupCoverUrl(
		releaseGroupMbid: String,
		coverUrl: String
	) {
		val releaseGroupKey = releaseGroupMbid.normalizedAurralCacheKey() ?: return
		if (releaseGroupCoverUrlsByMbid[releaseGroupKey] == coverUrl) return
		releaseGroupCoverUrlsByMbid[releaseGroupKey] = coverUrl
		bumpArtistStateRevision()
	}

	fun bumpArtistStateRevision() {
		_artistStateRevision.value = _artistStateRevision.value + 1
	}

	fun freshLibraryArtists(
		cacheKey: String,
		currentTime: Long = nowMillis()
	): List<AurralDiscoverArtist>? =
		libraryArtistsCache
			?.takeIf { it.key == cacheKey && it.isFresh(currentTime) }
			?.let {
				val artists = it.artists.withOptimisticMonitoring()
				publishLibraryArtistMonitorStates(artists)
				artists
			}

	fun cachedLibraryArtistsFallback(cacheKey: String): List<AurralDiscoverArtist> =
		libraryArtistsCache
			?.takeIf { it.key == cacheKey }
			?.artists
			?.withOptimisticMonitoring()
			.orEmpty()
			.also(::publishLibraryArtistMonitorStates)

	fun rememberLibraryArtists(
		cacheKey: String,
		artists: List<AurralDiscoverArtist>,
		loadedAtMillis: Long = nowMillis()
	) {
		libraryArtistsCache = AurralLibraryArtistsCacheEntry(
			key = cacheKey,
			artists = artists.withOptimisticMonitoring(),
			loadedAtMillis = loadedAtMillis
		)
		publishLibraryArtistMonitorStates(libraryArtistsCache?.artists.orEmpty())
	}

	fun cachedLibraryArtistMonitoring(
		cacheKey: String,
		artistMbid: String,
		artistName: String,
		currentTime: Long = nowMillis()
	): Boolean? =
		artistMbid.normalizedAurralCacheKey()?.let(optimisticArtistMonitoringByMbid::get)
			?: libraryArtistsCache
			?.takeIf { it.key == cacheKey && it.isFresh(currentTime) }
			?.artists
			?.findAurralLibraryArtist(artistMbid, artistName)
			?.monitored

	fun rememberLibraryArtistMonitoring(
		cacheKey: String,
		artistMbid: String,
		artistName: String,
		monitored: Boolean,
		loadedAtMillis: Long = nowMillis()
	) {
		val current = libraryArtistsCache?.takeIf { it.key == cacheKey }
		libraryArtistsCache = (current ?: AurralLibraryArtistsCacheEntry(
			key = cacheKey,
			artists = emptyList(),
			loadedAtMillis = loadedAtMillis
		)).withMonitoring(
			artistMbid = artistMbid,
			artistName = artistName,
			monitored = monitored,
			loadedAtMillis = loadedAtMillis
		)
		publishLibraryArtistMonitorStates(libraryArtistsCache?.artists.orEmpty())
		upsertLibraryArtistMonitorState(artistMbid, artistName, monitored)
	}

	fun withLocalArtistState(
		enrichment: AurralArtistEnrichment,
		libraryArtistMonitoring: Boolean?
	): AurralArtistEnrichment = with(enrichment) {
		val existingRequestKeys = requests
			.mapNotNull { request -> request.albumMbid.normalizedAurralCacheKey() }
			.toSet()
		val artistKey = artistMbid.normalizedAurralCacheKey()
		val mergedRequests = requests + optimisticAlbumRequestsByMbid.values.filter { request ->
			val requestAlbumKey = request.albumMbid.normalizedAurralCacheKey()
			val requestArtistKey = request.artistMbid.normalizedAurralCacheKey()
			requestAlbumKey != null &&
				requestAlbumKey !in existingRequestKeys &&
				(artistKey == null || requestArtistKey == null || requestArtistKey == artistKey)
		}
		val releaseGroupsWithCovers = releaseGroups.map { releaseGroup ->
			if (!releaseGroup.coverUrl.isNullOrBlank()) {
				releaseGroup
			} else {
				releaseGroup.id.normalizedAurralCacheKey()
					?.let(releaseGroupCoverUrlsByMbid::get)
					?.let { coverUrl -> releaseGroup.copy(coverUrl = coverUrl) }
					?: releaseGroup
			}
		}

		copy(
			releaseGroups = releaseGroupsWithCovers,
			requests = mergedRequests,
			monitored = artistKey?.let(optimisticArtistMonitoringByMbid::get)
				?: monitored
				?: libraryArtistMonitoring
		)
	}

	fun withLibraryArtists(
		discovery: AurralDiscoverySummary,
		libraryArtists: List<AurralDiscoverArtist>
	): AurralDiscoverySummary {
		val monitoredLibraryArtists = libraryArtists.withOptimisticMonitoring()
		return discovery.copy(
			recentlyAdded = discovery.recentlyAdded.withLibraryArtistMonitoring(monitoredLibraryArtists),
			recommendations = discovery.recommendations.withLibraryArtistMonitoring(monitoredLibraryArtists),
			globalTop = discovery.globalTop.withLibraryArtistMonitoring(monitoredLibraryArtists),
			basedOn = discovery.basedOn.withLibraryArtistMonitoring(monitoredLibraryArtists),
			libraryArtists = monitoredLibraryArtists,
			fallbackGenres = discovery.fallbackGenres.map { section ->
				section.copy(artists = section.artists.withLibraryArtistMonitoring(monitoredLibraryArtists))
			}
		)
	}

	private fun publishLibraryArtistMonitorStates(artists: List<AurralDiscoverArtist>) {
		_libraryArtistMonitorStates.value = artists
			.withOptimisticMonitoring()
			.flatMap { artist ->
				listOfNotNull(
					artist.id.normalizedAurralCacheKey()?.let { key -> key to artist.monitored },
					artist.name.normalizedAurralCacheKey()?.let { key -> key to artist.monitored }
				)
			}
			.toMap()
	}

	private fun upsertLibraryArtistMonitorState(
		artistMbid: String,
		artistName: String,
		monitored: Boolean
	) {
		_libraryArtistMonitorStates.value = _libraryArtistMonitorStates.value + listOfNotNull(
			artistMbid.normalizedAurralCacheKey()?.let { key -> key to monitored },
			artistName.normalizedAurralCacheKey()?.let { key -> key to monitored }
		)
	}

	private fun List<AurralDiscoverArtist>.withOptimisticMonitoring(): List<AurralDiscoverArtist> =
		map { artist ->
			artist.id.normalizedAurralCacheKey()
				?.let(optimisticArtistMonitoringByMbid::get)
				?.let { monitored -> artist.copy(monitored = monitored) }
				?: artist
		}
}
