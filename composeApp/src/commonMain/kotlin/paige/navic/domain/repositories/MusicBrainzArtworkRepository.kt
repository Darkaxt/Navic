package paige.navic.domain.repositories

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import paige.navic.data.database.dao.AlbumDao
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.isSyntheticUnknownArtistName
import paige.navic.util.core.Logger
import paige.navic.util.core.synchronized
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

private const val TAG = "MusicBrainzArtworkRepository"
private const val COVER_ART_ARCHIVE_BASE_URL = "https://coverartarchive.org"
private const val MUSICBRAINZ_BASE_URL = "https://musicbrainz.org"
private const val MUSICBRAINZ_USER_AGENT = "Navic/1.0 (https://github.com/Darkaxt/Navic)"
private const val MUSICBRAINZ_ARTWORK_CACHE_MAX_ENTRIES = 500
internal const val MUSICBRAINZ_METADATA_CACHE_SCHEMA_VERSION = 4
private const val MUSICBRAINZ_RECORDING_SEARCH_LIMIT = 5
private const val MUSICBRAINZ_RECORDING_SEARCH_MIN_SCORE = 90
private const val MUSICBRAINZ_RECORDING_RELEASE_LOOKUP_LIMIT = 3
private const val MUSICBRAINZ_REQUEST_INTERVAL_MILLIS = 1_000L
private val MUSICBRAINZ_ARTWORK_FOUND_MAX_AGE_MILLIS = 180.days.inWholeMilliseconds
private val MUSICBRAINZ_ARTWORK_MISSING_MAX_AGE_MILLIS = 14.days.inWholeMilliseconds

class MusicBrainzArtworkRepository(
	private val preferenceManager: PreferenceManager,
	private val albumDao: AlbumDao,
	private val connectivityManager: ConnectivityManager
) {
	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
	}
	private val cacheLock = Any()
	private val serverCoverFailureLock = Any()
	private val failedServerCoverSongIds = mutableSetOf<String>()
	private val _serverCoverLoadFailedSongIds = MutableStateFlow<Set<String>>(emptySet())
	val serverCoverLoadFailedSongIds = _serverCoverLoadFailedSongIds.asStateFlow()
	private val requestThrottle = Mutex()
	private var lastRequestMillis = 0L
	private val client = HttpClient {
		install(HttpTimeout) {
			requestTimeoutMillis = 20000
			connectTimeoutMillis = 20000
			socketTimeoutMillis = 20000
		}
		install(ContentNegotiation) {
			json(json)
		}
	}

	private val initialCacheEntries = loadCacheEntries()
	private val _artworkBySongId = MutableStateFlow(
		initialCacheEntries.visibleMusicBrainzArtworkBySongId(
			enabled = preferenceManager.musicBrainzArtworkFallbackEnabled,
			nowMillis = currentTimeMillis()
		)
	)
	val artworkBySongId = _artworkBySongId.asStateFlow()
	private val _metadataBySongId = MutableStateFlow(
		initialCacheEntries.visibleMusicBrainzMetadataBySongId(
			enabled = preferenceManager.musicBrainzArtworkFallbackEnabled,
			nowMillis = currentTimeMillis()
		)
	)
	val metadataBySongId = _metadataBySongId.asStateFlow()
	private val _cacheStats = MutableStateFlow(
		musicBrainzCacheStats(
			entries = initialCacheEntries,
			nowMillis = currentTimeMillis()
		)
	)
	val cacheStats = _cacheStats.asStateFlow()

	fun clearCache() {
		synchronized(cacheLock) {
			preferenceManager.clearMusicBrainzArtworkCache()
			emitCache()
		}
	}

	fun refreshCacheVisibility() {
		synchronized(cacheLock) {
			emitCache()
		}
	}

	fun reportServerCoverLoadFailed(songId: String) {
		if (songId.isBlank()) return
		synchronized(serverCoverFailureLock) {
			failedServerCoverSongIds.add(songId)
			_serverCoverLoadFailedSongIds.value = failedServerCoverSongIds.toSet()
		}
	}

	suspend fun prefetchArtworkForPlayingSong(song: DomainSong): Result<MusicBrainzArtworkCacheEntry?> =
		runCatching {
			if (song.id.startsWith("radio_")) return@runCatching null

			val album = song.albumId?.let { albumDao.getAlbumById(it)?.album }
			val albumCoverArtId = album?.coverArtId
			val albumMusicBrainzId = album?.musicBrainzId
			val serverCoverLoadFailed = synchronized(serverCoverFailureLock) {
				failedServerCoverSongIds.contains(song.id)
			}
			val fingerprint = musicBrainzArtworkFingerprint(song, albumMusicBrainzId)
			val shouldResolveMetadata = shouldResolveMusicBrainzMetadataOnPlayback(
				enabled = preferenceManager.musicBrainzArtworkFallbackEnabled,
				isOnline = connectivityManager.isOnline.value,
				isRadio = false,
				songMusicBrainzId = song.musicBrainzId,
				songTitle = song.title,
				artistName = song.artistName
			)
			val shouldResolveArtwork = shouldResolveMusicBrainzArtworkOnPlayback(
				enabled = preferenceManager.musicBrainzArtworkFallbackEnabled,
				isOnline = connectivityManager.isOnline.value,
				isRadio = false,
				songCoverArtId = song.coverArtId,
				albumCoverArtId = albumCoverArtId,
				serverCoverLoadFailed = serverCoverLoadFailed,
				songMusicBrainzId = song.musicBrainzId,
				albumMusicBrainzId = albumMusicBrainzId,
				songTitle = song.title,
				artistName = song.artistName
			)
			val existing = loadCacheEntries()
				.usableMusicBrainzPlaybackCacheEntry(
					songId = song.id,
					fingerprint = fingerprint,
					nowMillis = currentTimeMillis(),
					needsMetadata = shouldResolveMetadata
				)

			if (existing != null) {
				emitCache()
				return@runCatching existing.takeIf { it.status == MusicBrainzArtworkCacheStatus.Found }
			}

			if (!shouldResolveMetadata && !shouldResolveArtwork) {
				return@runCatching null
			}

			val recording = if (shouldResolveMetadata || shouldResolveArtwork) {
				musicBrainzLookupMbidOrNull(song.musicBrainzId)
					?.let { fetchRecording(it) }
					?: searchRecording(song)
			} else {
				null
			}
			val resolved = if (shouldResolveArtwork) {
				resolveArtwork(
					albumMusicBrainzId = albumMusicBrainzId,
					albumTitle = song.albumTitle,
					recordingReleases = recording?.releases.orEmpty()
				)
			} else {
				null
			}
			val metadata = recording?.let {
				val metadataRecording = if (shouldResolveMetadata) {
					recording.withSelectedReleaseRelationMetadata(
						preferredReleaseMbid = resolved?.releaseMbid,
						preferredAlbumTitle = song.albumTitle
					)
				} else {
					recording
				}
				musicBrainzTrackMetadata(
					recording = metadataRecording,
					preferredReleaseMbid = resolved?.releaseMbid,
					preferredAlbumTitle = song.albumTitle
				)
			}
			val entry = resolved?.let {
				MusicBrainzArtworkCacheEntry(
					songId = song.id,
					fingerprint = fingerprint,
					status = MusicBrainzArtworkCacheStatus.Found,
					imageUrl = it.imageUrl,
					sourceMbid = it.sourceMbid,
					sourceType = it.sourceType,
					metadata = metadata,
					metadataLookupAttempted = shouldResolveMetadata,
					metadataSchemaVersion = if (shouldResolveMetadata) {
						MUSICBRAINZ_METADATA_CACHE_SCHEMA_VERSION
					} else {
						0
					},
					updatedAtMillis = currentTimeMillis()
				)
			} ?: MusicBrainzArtworkCacheEntry(
				songId = song.id,
				fingerprint = fingerprint,
				status = MusicBrainzArtworkCacheStatus.NotFound,
				imageUrl = null,
				sourceMbid = null,
				sourceType = null,
				metadata = metadata,
				metadataLookupAttempted = shouldResolveMetadata,
				metadataSchemaVersion = if (shouldResolveMetadata) {
					MUSICBRAINZ_METADATA_CACHE_SCHEMA_VERSION
				} else {
					0
				},
				updatedAtMillis = currentTimeMillis()
			)

			putCacheEntry(entry)
			entry.takeIf { it.status == MusicBrainzArtworkCacheStatus.Found }
		}.onFailure { error ->
			Logger.w(TAG, "MusicBrainz artwork lookup failed for ${song.id}", error)
		}

	private suspend fun resolveArtwork(
		albumMusicBrainzId: String?,
		albumTitle: String?,
		recordingReleases: List<MusicBrainzReleaseDto>
	): ResolvedMusicBrainzArtwork? {
		musicBrainzLookupMbidOrNull(albumMusicBrainzId)?.let { mbid ->
			fetchCoverArtArchiveArtwork(mbid, MusicBrainzArtworkSourceType.Release)
				?.copy(releaseMbid = mbid)
				?.let { return it }
			fetchCoverArtArchiveArtwork(mbid, MusicBrainzArtworkSourceType.ReleaseGroup)
				?.copy(releaseGroupMbid = mbid)
				?.let { return it }
		}

		for (release in preferredMusicBrainzRecordingReleases(recordingReleases, albumTitle)
			.take(MUSICBRAINZ_RECORDING_RELEASE_LOOKUP_LIMIT)) {
			val releaseMbid = musicBrainzLookupMbidOrNull(release.id) ?: continue
			fetchCoverArtArchiveArtwork(releaseMbid, MusicBrainzArtworkSourceType.Release)
				?.copy(
					releaseMbid = releaseMbid,
					releaseGroupMbid = release.releaseGroup?.id
				)
				?.let { return it }
			release.releaseGroup?.id?.let { releaseGroupId ->
				fetchCoverArtArchiveArtwork(releaseGroupId, MusicBrainzArtworkSourceType.ReleaseGroup)
					?.copy(
						releaseMbid = releaseMbid,
						releaseGroupMbid = releaseGroupId
					)
					?.let { return it }
			}
		}
		return null
	}

	private suspend fun fetchCoverArtArchiveArtwork(
		mbid: String,
		sourceType: MusicBrainzArtworkSourceType
	): ResolvedMusicBrainzArtwork? {
		val endpoint = when (sourceType) {
			MusicBrainzArtworkSourceType.Release -> coverArtArchiveReleaseEndpoint(mbid)
			MusicBrainzArtworkSourceType.ReleaseGroup -> coverArtArchiveReleaseGroupEndpoint(mbid)
		}
		val response = throttledGet(endpoint)

		return when {
			response.status == HttpStatusCode.NotFound -> null
			response.status.isSuccess() -> musicBrainzFrontArtworkImageUrl(response.body<CoverArtArchiveResponseDto>())
				?.let { imageUrl ->
					ResolvedMusicBrainzArtwork(
						imageUrl = imageUrl,
						sourceMbid = mbid,
						sourceType = sourceType
					)
				}

			else -> error("Cover Art Archive returned HTTP ${response.status.value}")
		}
	}

	private suspend fun fetchRecording(recordingMbid: String): MusicBrainzRecordingDto? {
		val response = throttledGet(musicBrainzRecordingLookupEndpoint(recordingMbid))
		return when {
			response.status == HttpStatusCode.NotFound -> null
			response.status.isSuccess() -> response.body<MusicBrainzRecordingDto>()
			else -> error("MusicBrainz recording lookup returned HTTP ${response.status.value}")
		}
	}

	private suspend fun fetchRelease(releaseMbid: String): MusicBrainzReleaseDto? {
		val response = throttledGet(musicBrainzReleaseLookupEndpoint(releaseMbid))
		return when {
			response.status == HttpStatusCode.NotFound -> null
			response.status.isSuccess() -> response.body<MusicBrainzReleaseDto>()
			else -> error("MusicBrainz release lookup returned HTTP ${response.status.value}")
		}
	}

	private suspend fun fetchReleaseGroup(releaseGroupMbid: String): MusicBrainzReleaseGroupDto? {
		val response = throttledGet(musicBrainzReleaseGroupLookupEndpoint(releaseGroupMbid))
		return when {
			response.status == HttpStatusCode.NotFound -> null
			response.status.isSuccess() -> response.body<MusicBrainzReleaseGroupDto>()
			else -> error("MusicBrainz release-group lookup returned HTTP ${response.status.value}")
		}
	}

	private suspend fun MusicBrainzRecordingDto.withSelectedReleaseRelationMetadata(
		preferredReleaseMbid: String?,
		preferredAlbumTitle: String?
	): MusicBrainzRecordingDto {
		val selectedRelease = preferredMusicBrainzTrackRelease(
			recording = this,
			preferredReleaseMbid = preferredReleaseMbid,
			preferredAlbumTitle = preferredAlbumTitle
		) ?: return this
		val selectedReleaseMbid = musicBrainzLookupMbidOrNull(selectedRelease.id) ?: return this
		val release = runCatching {
			fetchRelease(selectedReleaseMbid)
		}.getOrElse { error ->
			Logger.w(TAG, "MusicBrainz release relation lookup failed for $selectedReleaseMbid", error)
			null
		}
		val selectedReleaseGroupMbid = (
			release?.releaseGroup?.id ?: selectedRelease.releaseGroup?.id
			).let(::musicBrainzLookupMbidOrNull)
		val releaseGroup = selectedReleaseGroupMbid?.let { releaseGroupMbid ->
			runCatching {
				fetchReleaseGroup(releaseGroupMbid)
			}.getOrElse { error ->
				Logger.w(TAG, "MusicBrainz release-group relation lookup failed for $releaseGroupMbid", error)
				null
			}
		}
		if (release == null && releaseGroup == null) return this

		return copy(
			releases = releases.map { candidate ->
				if (musicBrainzLookupMbidOrNull(candidate.id) != selectedReleaseMbid) {
					candidate
				} else {
					val baseReleaseGroup = candidate.releaseGroup ?: release?.releaseGroup
					candidate.copy(
						relations = candidate.relations + release?.relations.orEmpty(),
						releaseGroup = baseReleaseGroup?.copy(
							relations = baseReleaseGroup.relations + releaseGroup?.relations.orEmpty()
						)
					)
				}
			}
		)
	}

	private suspend fun searchRecording(song: DomainSong): MusicBrainzRecordingDto? {
		if (!canSearchMusicBrainzRecording(song.title, song.artistName)) return null
		val searchEndpoints = musicBrainzRecordingSearchEndpoints(
			title = song.title,
			artistName = song.artistName,
			albumTitle = song.albumTitle
		)
		for (endpoint in searchEndpoints) {
			val response = throttledGet(endpoint)
			val recordingMbid = when {
				response.status == HttpStatusCode.NotFound -> null
				response.status.isSuccess() -> bestMusicBrainzRecordingSearchMatch(
					response.body<MusicBrainzRecordingSearchResponseDto>()
				)

				else -> error("MusicBrainz recording search returned HTTP ${response.status.value}")
			}
			if (recordingMbid != null) {
				return fetchRecording(recordingMbid)
			}
		}
		return null
	}

	private suspend fun throttledGet(url: String) =
		requestThrottle.withLock {
			val now = currentTimeMillis()
			val waitMillis = MUSICBRAINZ_REQUEST_INTERVAL_MILLIS - (now - lastRequestMillis)
			if (waitMillis > 0) delay(waitMillis)
			lastRequestMillis = currentTimeMillis()
			client.get(url) {
				accept(ContentType.Application.Json)
				header(HttpHeaders.UserAgent, MUSICBRAINZ_USER_AGENT)
			}
		}

	private fun putCacheEntry(entry: MusicBrainzArtworkCacheEntry) {
		synchronized(cacheLock) {
			val nowMillis = currentTimeMillis()
			val entries = loadCacheEntries()
				.usableMusicBrainzCacheEntries(nowMillis)
				.filterNot { it.songId == entry.songId }
				.plus(entry)
			preferenceManager.musicBrainzArtworkCacheJson = json.encodeToString(
				MusicBrainzArtworkCacheStore(
					entries = cappedMusicBrainzArtworkCacheEntries(
						entries = entries,
						maxEntries = MUSICBRAINZ_ARTWORK_CACHE_MAX_ENTRIES
					)
				)
			)
			emitCache()
		}
	}

	private fun emitCache() {
		val nowMillis = currentTimeMillis()
		val entries = loadCacheEntries()
		val enabled = preferenceManager.musicBrainzArtworkFallbackEnabled
		_artworkBySongId.value = entries.visibleMusicBrainzArtworkBySongId(
			enabled = enabled,
			nowMillis = nowMillis
		)
		_metadataBySongId.value = entries.visibleMusicBrainzMetadataBySongId(
			enabled = enabled,
			nowMillis = nowMillis
		)
		_cacheStats.value = musicBrainzCacheStats(
			entries = entries,
			nowMillis = nowMillis
		)
	}

	private fun loadCacheEntries(): List<MusicBrainzArtworkCacheEntry> =
		try {
			val raw = preferenceManager.musicBrainzArtworkCacheJson
			if (raw.isBlank()) emptyList() else json.decodeFromString<MusicBrainzArtworkCacheStore>(raw).entries
		} catch (error: Exception) {
			Logger.w(TAG, "Dropping invalid MusicBrainz artwork cache", error)
			emptyList()
		}

	private fun currentTimeMillis() = Clock.System.now().toEpochMilliseconds()
}

internal fun shouldResolveMusicBrainzMetadataOnPlayback(
	enabled: Boolean,
	isOnline: Boolean,
	isRadio: Boolean,
	songMusicBrainzId: String?,
	songTitle: String? = null,
	artistName: String? = null
): Boolean =
	enabled &&
		isOnline &&
		!isRadio &&
		(
			musicBrainzLookupMbidOrNull(songMusicBrainzId) != null ||
				canSearchMusicBrainzRecording(songTitle, artistName)
		)

internal fun shouldResolveMusicBrainzArtworkOnPlayback(
	enabled: Boolean,
	isOnline: Boolean,
	isRadio: Boolean,
	songCoverArtId: String?,
	albumCoverArtId: String?,
	serverCoverLoadFailed: Boolean = false,
	songMusicBrainzId: String?,
	albumMusicBrainzId: String?,
	songTitle: String? = null,
	artistName: String? = null
): Boolean =
	enabled &&
		isOnline &&
		!isRadio &&
		(
			serverCoverLoadFailed ||
				(songCoverArtId.isNullOrBlank() && albumCoverArtId.isNullOrBlank())
		) &&
		(
			musicBrainzLookupMbidOrNull(songMusicBrainzId) != null ||
				musicBrainzLookupMbidOrNull(albumMusicBrainzId) != null ||
				canSearchMusicBrainzRecording(songTitle, artistName)
		)

internal fun coverArtArchiveReleaseEndpoint(mbid: String): String =
	"$COVER_ART_ARCHIVE_BASE_URL/release/${mbid.normalizedMbidOrNull() ?: mbid.trim()}"

internal fun coverArtArchiveReleaseGroupEndpoint(mbid: String): String =
	"$COVER_ART_ARCHIVE_BASE_URL/release-group/${mbid.normalizedMbidOrNull() ?: mbid.trim()}"

internal fun musicBrainzRecordingLookupEndpoint(mbid: String): String =
	"$MUSICBRAINZ_BASE_URL/ws/2/recording/${encodePathSegment(mbid.trim())}?inc=artist-credits+isrcs+releases+release-groups+genres+tags+url-rels+work-rels+work-level-rels&fmt=json"

internal fun musicBrainzReleaseLookupEndpoint(mbid: String): String =
	"$MUSICBRAINZ_BASE_URL/ws/2/release/${encodePathSegment(mbid.trim())}?inc=release-groups+url-rels&fmt=json"

internal fun musicBrainzReleaseGroupLookupEndpoint(mbid: String): String =
	"$MUSICBRAINZ_BASE_URL/ws/2/release-group/${encodePathSegment(mbid.trim())}?inc=url-rels&fmt=json"

internal fun musicBrainzRecordingSearchEndpoint(
	title: String,
	artistName: String,
	albumTitle: String? = null
): String {
	val query = listOfNotNull(
		"recording:${lucenePhrase(title)}",
		"artistname:${lucenePhrase(artistName)}",
		albumTitle.nonBlankOrNull()?.let { "release:${lucenePhrase(it)}" }
	).joinToString(" AND ")
	return "$MUSICBRAINZ_BASE_URL/ws/2/recording?query=${encodeUrlComponent(query)}&limit=$MUSICBRAINZ_RECORDING_SEARCH_LIMIT&fmt=json"
}

internal fun musicBrainzRecordingSearchEndpoints(
	title: String,
	artistName: String,
	albumTitle: String?
): List<String> =
	listOfNotNull(
		albumTitle.nonBlankOrNull()?.let {
			musicBrainzRecordingSearchEndpoint(
				title = title,
				artistName = artistName,
				albumTitle = it
			)
		},
		musicBrainzRecordingSearchEndpoint(
			title = title,
			artistName = artistName
		)
	).distinct()

internal fun bestMusicBrainzRecordingSearchMatch(
	response: MusicBrainzRecordingSearchResponseDto
): String? =
	response.recordings
		.firstOrNull { result ->
			musicBrainzLookupMbidOrNull(result.id) != null &&
				(result.score.toIntOrNull() ?: 0) >= MUSICBRAINZ_RECORDING_SEARCH_MIN_SCORE
		}
		?.id
		?.let(::musicBrainzLookupMbidOrNull)

private fun canSearchMusicBrainzRecording(title: String?, artistName: String?): Boolean =
	!title.isNullOrBlank() &&
		!artistName.isNullOrBlank() &&
		!isSyntheticUnknownArtistName(artistName)

internal fun musicBrainzTrackMetadata(
	recording: MusicBrainzRecordingDto,
	preferredReleaseMbid: String?,
	preferredAlbumTitle: String? = null
): MusicBrainzTrackMetadata {
	val release = preferredMusicBrainzTrackRelease(
		recording = recording,
		preferredReleaseMbid = preferredReleaseMbid,
		preferredAlbumTitle = preferredAlbumTitle
	)
	val releaseGroup = release?.releaseGroup
	val recordingMbid = recording.id.normalizedMbidOrNull()
	val releaseMbid = release?.id.normalizedMbidOrNull()
	val releaseGroupMbid = releaseGroup?.id.normalizedMbidOrNull()

	return MusicBrainzTrackMetadata(
		recordingMbid = recordingMbid,
		recordingTitle = recording.title.nonBlankOrNull(),
		recordingDisambiguation = recording.disambiguation.nonBlankOrNull(),
		artistCredit = musicBrainzArtistCredit(recording.artistCredits),
		firstReleaseDate = recording.firstReleaseDate.nonBlankOrNull(),
		releaseMbid = releaseMbid,
		releaseTitle = release?.title.nonBlankOrNull(),
		releaseDisambiguation = release?.disambiguation.nonBlankOrNull(),
		releaseGroupMbid = releaseGroupMbid,
		releaseGroupTitle = releaseGroup?.title.nonBlankOrNull(),
		releaseGroupDisambiguation = releaseGroup?.disambiguation.nonBlankOrNull(),
		releaseGroupType = musicBrainzReleaseGroupType(releaseGroup),
		releaseDate = release?.date.nonBlankOrNull(),
		country = release?.country.nonBlankOrNull(),
		status = release?.status.nonBlankOrNull(),
		genres = recording.genres.normalizedMusicBrainzTags(),
		tags = recording.tags.normalizedMusicBrainzTags(),
		isrcs = recording.isrcs.mapNotNull { it.nonBlankOrNull() }.distinct(),
		externalLinks = musicBrainzExternalLinks(
			recording = recording,
			release = release,
			releaseGroup = releaseGroup
		),
		recordingUrl = recordingMbid?.let { "$MUSICBRAINZ_BASE_URL/recording/$it" },
		releaseUrl = releaseMbid?.let { "$MUSICBRAINZ_BASE_URL/release/$it" },
		releaseGroupUrl = releaseGroupMbid?.let { "$MUSICBRAINZ_BASE_URL/release-group/$it" }
	)
}

internal fun preferredMusicBrainzTrackRelease(
	recording: MusicBrainzRecordingDto,
	preferredReleaseMbid: String?,
	preferredAlbumTitle: String?
): MusicBrainzReleaseDto? =
	recording.releases
		.firstOrNull { musicBrainzMbidMatches(it.id, preferredReleaseMbid) }
		?: preferredMusicBrainzRecordingReleases(recording.releases, preferredAlbumTitle).firstOrNull()

private fun musicBrainzMbidMatches(candidate: String?, preferred: String?): Boolean {
	if (candidate == preferred) return true
	val normalizedCandidate = candidate?.normalizedMbidOrNull() ?: return false
	val normalizedPreferred = preferred?.normalizedMbidOrNull() ?: return false
	return normalizedCandidate == normalizedPreferred
}

internal fun preferredMusicBrainzRecordingReleases(
	releases: List<MusicBrainzReleaseDto>,
	preferredAlbumTitle: String?
): List<MusicBrainzReleaseDto> {
	if (normalizedMusicBrainzTitle(preferredAlbumTitle) == null) return releases
	val (matches, misses) = releases.partition {
		musicBrainzReleaseMatchesAlbumTitle(
			release = it,
			albumTitle = preferredAlbumTitle
		)
	}
	return matches + misses
}

internal fun musicBrainzReleaseMatchesAlbumTitle(
	release: MusicBrainzReleaseDto,
	albumTitle: String?
): Boolean {
	val normalizedAlbumTitle = normalizedMusicBrainzTitle(albumTitle) ?: return false
	return normalizedMusicBrainzTitle(release.title) == normalizedAlbumTitle ||
		normalizedMusicBrainzTitle(release.releaseGroup?.title) == normalizedAlbumTitle
}

internal fun musicBrainzMetadataDisplayFields(
	metadata: MusicBrainzTrackMetadata?
): List<MusicBrainzMetadataDisplayField> {
	if (metadata == null) return emptyList()
	return listOfNotNull(
		MusicBrainzMetadataField.RecordingTitle.field(metadata.recordingTitle),
		MusicBrainzMetadataField.RecordingDisambiguation.field(metadata.recordingDisambiguation),
		MusicBrainzMetadataField.ArtistCredit.field(metadata.artistCredit),
		MusicBrainzMetadataField.FirstReleaseDate.field(metadata.firstReleaseDate),
		MusicBrainzMetadataField.ReleaseTitle.field(metadata.releaseTitle),
		MusicBrainzMetadataField.ReleaseDisambiguation.field(metadata.releaseDisambiguation),
		MusicBrainzMetadataField.ReleaseGroupTitle.field(metadata.releaseGroupTitle),
		MusicBrainzMetadataField.ReleaseGroupDisambiguation.field(metadata.releaseGroupDisambiguation),
		MusicBrainzMetadataField.ReleaseGroupType.field(metadata.releaseGroupType),
		MusicBrainzMetadataField.ReleaseDate.field(metadata.releaseDate),
		MusicBrainzMetadataField.Country.field(metadata.country),
		MusicBrainzMetadataField.Status.field(metadata.status),
		MusicBrainzMetadataField.Genres.field(metadata.genres.joinToStringValue()),
		MusicBrainzMetadataField.Tags.field(metadata.tags.joinToStringValue()),
		MusicBrainzMetadataField.Isrcs.field(metadata.isrcs.joinToStringValue()),
		*metadata.externalLinks.map { link ->
			MusicBrainzMetadataDisplayField(
				field = MusicBrainzMetadataField.ExternalLink,
				value = link.label,
				url = link.url
			)
		}.toTypedArray(),
		MusicBrainzMetadataField.RecordingUrl.field(metadata.recordingUrl),
		MusicBrainzMetadataField.ReleaseUrl.field(metadata.releaseUrl),
		MusicBrainzMetadataField.ReleaseGroupUrl.field(metadata.releaseGroupUrl)
	)
}

internal fun musicBrainzMetadataUrlOrNull(
	field: MusicBrainzMetadataField?,
	value: Any?
): String? {
	if (field !in MusicBrainzUrlFields) return null
	val url = (value as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	return url.takeIf { it.startsWith("https://musicbrainz.org/", ignoreCase = true) }
}

private val MusicBrainzUrlFields = setOf(
	MusicBrainzMetadataField.RecordingUrl,
	MusicBrainzMetadataField.ReleaseUrl,
	MusicBrainzMetadataField.ReleaseGroupUrl
)

private fun MusicBrainzMetadataField.field(value: String?): MusicBrainzMetadataDisplayField? =
	value.nonBlankOrNull()?.let { MusicBrainzMetadataDisplayField(this, it) }

private fun List<String>.joinToStringValue(): String? =
	takeIf { it.isNotEmpty() }?.joinToString(", ")

private fun musicBrainzArtistCredit(artistCredits: List<MusicBrainzArtistCreditDto>): String? {
	if (artistCredits.isEmpty()) return null
	return artistCredits.joinToString("") { credit ->
		"${credit.name}${credit.joinphrase}"
	}.nonBlankOrNull()
}

private fun musicBrainzReleaseGroupType(releaseGroup: MusicBrainzReleaseGroupDto?): String? =
	listOfNotNull(
		releaseGroup?.primaryType.nonBlankOrNull(),
		*releaseGroup?.secondaryTypes.orEmpty().mapNotNull { it.nonBlankOrNull() }.toTypedArray()
	).distinct().joinToStringValue()

private fun List<MusicBrainzTagDto>.normalizedMusicBrainzTags(): List<String> =
	filter { it.name.isNotBlank() }
		.sortedWith(
			compareByDescending<MusicBrainzTagDto> { it.count ?: 0 }
				.thenBy { it.name.lowercase() }
		)
		.map { it.name.trim() }
		.distinct()
		.take(10)

private fun musicBrainzExternalLinks(
	recording: MusicBrainzRecordingDto,
	release: MusicBrainzReleaseDto?,
	releaseGroup: MusicBrainzReleaseGroupDto?
): List<MusicBrainzExternalLink> {
	val relations =
		recording.relations +
			recording.relations.flatMap { it.work?.relations.orEmpty() } +
			release?.relations.orEmpty() +
			releaseGroup?.relations.orEmpty()
	return relations
		.mapNotNull { relation ->
			if (relation.ended == true) {
				null
			} else {
				musicBrainzExternalLinkOrNull(relation.url?.resource)
			}
		}
		.distinctBy { it.url.lowercase() }
		.take(8)
}

private fun musicBrainzExternalLinkOrNull(resource: String?): MusicBrainzExternalLink? {
	val normalized = normalizedMusicBrainzExternalLinkUrlOrNull(resource) ?: return null
	val label = when {
		normalized.host == "discogs.com" || normalized.host == "www.discogs.com" -> "Discogs"
		normalized.host == "songfacts.com" || normalized.host == "www.songfacts.com" -> "Songfacts"
		normalized.host == "wikidata.org" || normalized.host == "www.wikidata.org" -> "Wikidata"
		normalized.host == "wikipedia.org" || normalized.host.endsWith(".wikipedia.org") -> "Wikipedia"
		else -> return null
	}
	return MusicBrainzExternalLink(label = label, url = normalized.url)
}

private fun normalizedMusicBrainzExternalLinkUrlOrNull(resource: String?): NormalizedExternalLink? {
	val trimmed = resource?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val schemeLength = when {
		trimmed.startsWith("https://", ignoreCase = true) -> "https://".length
		trimmed.startsWith("http://", ignoreCase = true) -> "http://".length
		else -> return null
	}
	val withoutScheme = trimmed.drop(schemeLength)
	val hostAndRestSplit = withoutScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
	val hostAndPort = if (hostAndRestSplit >= 0) {
		withoutScheme.take(hostAndRestSplit)
	} else {
		withoutScheme
	}
	val rest = if (hostAndRestSplit >= 0) withoutScheme.drop(hostAndRestSplit) else ""
	val host = hostAndPort.substringBefore(':').trim().lowercase().takeIf { it.isNotEmpty() } ?: return null
	return NormalizedExternalLink(
		host = host,
		url = "https://$host$rest"
	)
}

private data class NormalizedExternalLink(
	val host: String,
	val url: String
)

internal fun musicBrainzFrontArtworkImageUrl(response: CoverArtArchiveResponseDto): String? {
	val image = response.images.firstOrNull { it.front }
		?: response.images.firstOrNull()
	return image?.thumbnails?.let { thumbnails ->
		thumbnails["1200"] ?: thumbnails["large"] ?: thumbnails["500"] ?: thumbnails["250"] ?: thumbnails["small"]
	}?.normalizedCoverArtArchiveImageUrl() ?: image?.image?.normalizedCoverArtArchiveImageUrl()
}

internal fun String.normalizedCoverArtArchiveImageUrl(): String =
	if (startsWith("http://coverartarchive.org/", ignoreCase = true)) {
		"https://${drop("http://".length)}"
	} else {
		this
	}

internal fun usableMusicBrainzArtworkCacheEntry(
	entry: MusicBrainzArtworkCacheEntry,
	fingerprint: String,
	nowMillis: Long
): MusicBrainzArtworkCacheEntry? {
	if (entry.fingerprint != fingerprint) return null
	val maxAgeMillis = when (entry.status) {
		MusicBrainzArtworkCacheStatus.Found -> MUSICBRAINZ_ARTWORK_FOUND_MAX_AGE_MILLIS
		MusicBrainzArtworkCacheStatus.NotFound -> MUSICBRAINZ_ARTWORK_MISSING_MAX_AGE_MILLIS
	}
	val ageMillis = max(0L, nowMillis - entry.updatedAtMillis)
	return entry.takeIf { ageMillis <= maxAgeMillis }
}

internal fun usableMusicBrainzPlaybackCacheEntry(
	entry: MusicBrainzArtworkCacheEntry,
	fingerprint: String,
	nowMillis: Long,
	needsMetadata: Boolean
): MusicBrainzArtworkCacheEntry? {
	val usableEntry = usableMusicBrainzArtworkCacheEntry(
		entry = entry,
		fingerprint = fingerprint,
		nowMillis = nowMillis
	) ?: return null
	if (needsMetadata && usableEntry.metadataSchemaVersion < MUSICBRAINZ_METADATA_CACHE_SCHEMA_VERSION) {
		return null
	}
	if (needsMetadata && usableEntry.metadata == null && !usableEntry.metadataLookupAttempted) return null
	return usableEntry
}

internal fun List<MusicBrainzArtworkCacheEntry>.usableMusicBrainzPlaybackCacheEntry(
	songId: String,
	fingerprint: String,
	nowMillis: Long,
	needsMetadata: Boolean
): MusicBrainzArtworkCacheEntry? =
	newestMusicBrainzCacheEntriesBySongId()
		.firstOrNull { it.songId == songId }
		?.let {
			usableMusicBrainzPlaybackCacheEntry(
				entry = it,
				fingerprint = fingerprint,
				nowMillis = nowMillis,
				needsMetadata = needsMetadata
			)
		}

internal fun cappedMusicBrainzArtworkCacheEntries(
	entries: List<MusicBrainzArtworkCacheEntry>,
	maxEntries: Int
): List<MusicBrainzArtworkCacheEntry> =
	entries
		.newestMusicBrainzCacheEntriesBySongId()
		.take(maxEntries.coerceAtLeast(0))

internal fun musicBrainzArtworkFingerprint(
	song: DomainSong,
	albumMusicBrainzId: String?
): String = listOf(
	song.id,
	song.musicBrainzId.orEmpty(),
	albumMusicBrainzId.orEmpty(),
	song.title,
	song.artistName,
	song.albumTitle.orEmpty()
).joinToString("|") { it.trim().lowercase() }

private val MusicBrainzMbidRegex = Regex(
	"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
	RegexOption.IGNORE_CASE
)

internal fun musicBrainzLookupMbidOrNull(value: String?): String? =
	value
		?.trim()
		?.lowercase()
		?.takeIf { MusicBrainzMbidRegex.matches(it) }

private fun String?.normalizedMbidOrNull(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.nonBlankOrNull(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

private fun normalizedMusicBrainzTitle(value: String?): String? =
	value
		?.trim()
		?.replace(Regex("\\s+"), " ")
		?.lowercase()
		?.takeIf { it.isNotEmpty() }

internal fun List<MusicBrainzArtworkCacheEntry>.usableMusicBrainzCacheEntries(
	nowMillis: Long
): List<MusicBrainzArtworkCacheEntry> =
	filter { entry ->
		usableMusicBrainzArtworkCacheEntry(
			entry = entry,
			fingerprint = entry.fingerprint,
			nowMillis = nowMillis
		) != null
	}

internal fun List<MusicBrainzArtworkCacheEntry>.musicBrainzArtworkBySongId(
	nowMillis: Long
): Map<String, MusicBrainzArtworkCacheEntry> =
	usableMusicBrainzCacheEntries(nowMillis).foundBySongId()

private fun List<MusicBrainzArtworkCacheEntry>.foundBySongId(): Map<String, MusicBrainzArtworkCacheEntry> =
	newestMusicBrainzCacheEntriesBySongId()
		.filter { it.status == MusicBrainzArtworkCacheStatus.Found && !it.imageUrl.isNullOrBlank() }
		.associateBy { it.songId }

internal fun List<MusicBrainzArtworkCacheEntry>.musicBrainzMetadataBySongId(
	nowMillis: Long? = null
): Map<String, MusicBrainzTrackMetadata> =
	(nowMillis?.let { usableMusicBrainzCacheEntries(it) } ?: this)
		.newestMusicBrainzCacheEntriesBySongId()
		.mapNotNull { entry -> entry.metadata?.let { metadata -> entry.songId to metadata } }
		.toMap()

internal fun List<MusicBrainzArtworkCacheEntry>.visibleMusicBrainzArtworkBySongId(
	enabled: Boolean,
	nowMillis: Long
): Map<String, MusicBrainzArtworkCacheEntry> =
	if (enabled) musicBrainzArtworkBySongId(nowMillis) else emptyMap()

internal fun List<MusicBrainzArtworkCacheEntry>.visibleMusicBrainzMetadataBySongId(
	enabled: Boolean,
	nowMillis: Long
): Map<String, MusicBrainzTrackMetadata> =
	if (enabled) musicBrainzMetadataBySongId(nowMillis) else emptyMap()

data class MusicBrainzCacheStats(
	val totalSongs: Int = 0,
	val artworkSongs: Int = 0,
	val metadataSongs: Int = 0,
	val missingSongs: Int = 0
)

internal fun musicBrainzCacheStats(
	entries: List<MusicBrainzArtworkCacheEntry>,
	nowMillis: Long
): MusicBrainzCacheStats {
	val usableEntries = entries
		.usableMusicBrainzCacheEntries(nowMillis)
		.newestMusicBrainzCacheEntriesBySongId()
	return MusicBrainzCacheStats(
		totalSongs = usableEntries.size,
		artworkSongs = usableEntries.count {
			it.status == MusicBrainzArtworkCacheStatus.Found && !it.imageUrl.isNullOrBlank()
		},
		metadataSongs = usableEntries.count { it.metadata != null },
		missingSongs = usableEntries.count { it.status == MusicBrainzArtworkCacheStatus.NotFound }
	)
}

private fun List<MusicBrainzArtworkCacheEntry>.newestMusicBrainzCacheEntriesBySongId(): List<MusicBrainzArtworkCacheEntry> =
	sortedByDescending { it.updatedAtMillis }
		.distinctBy { it.songId }

private data class ResolvedMusicBrainzArtwork(
	val imageUrl: String,
	val sourceMbid: String,
	val sourceType: MusicBrainzArtworkSourceType,
	val releaseMbid: String? = null,
	val releaseGroupMbid: String? = null
)

@Serializable
internal data class MusicBrainzArtworkCacheStore(
	val entries: List<MusicBrainzArtworkCacheEntry> = emptyList()
)

@Serializable
data class MusicBrainzArtworkCacheEntry(
	val songId: String,
	val fingerprint: String,
	val status: MusicBrainzArtworkCacheStatus,
	val imageUrl: String?,
	val sourceMbid: String?,
	val sourceType: MusicBrainzArtworkSourceType?,
	val metadata: MusicBrainzTrackMetadata? = null,
	val metadataLookupAttempted: Boolean = false,
	val metadataSchemaVersion: Int = 0,
	val updatedAtMillis: Long
)

@Serializable
data class MusicBrainzTrackMetadata(
	val recordingMbid: String? = null,
	val recordingTitle: String? = null,
	val recordingDisambiguation: String? = null,
	val artistCredit: String? = null,
	val firstReleaseDate: String? = null,
	val releaseMbid: String? = null,
	val releaseTitle: String? = null,
	val releaseDisambiguation: String? = null,
	val releaseGroupMbid: String? = null,
	val releaseGroupTitle: String? = null,
	val releaseGroupDisambiguation: String? = null,
	val releaseGroupType: String? = null,
	val releaseDate: String? = null,
	val country: String? = null,
	val status: String? = null,
	val genres: List<String> = emptyList(),
	val tags: List<String> = emptyList(),
	val isrcs: List<String> = emptyList(),
	val externalLinks: List<MusicBrainzExternalLink> = emptyList(),
	val recordingUrl: String? = null,
	val releaseUrl: String? = null,
	val releaseGroupUrl: String? = null
)

data class MusicBrainzMetadataDisplayField(
	val field: MusicBrainzMetadataField,
	val value: String,
	val url: String? = null
)

@Serializable
data class MusicBrainzExternalLink(
	val label: String,
	val url: String
)

enum class MusicBrainzMetadataField {
	RecordingTitle,
	RecordingDisambiguation,
	ArtistCredit,
	FirstReleaseDate,
	ReleaseTitle,
	ReleaseDisambiguation,
	ReleaseGroupTitle,
	ReleaseGroupDisambiguation,
	ReleaseGroupType,
	ReleaseDate,
	Country,
	Status,
	Genres,
	Tags,
	Isrcs,
	ExternalLink,
	RecordingUrl,
	ReleaseUrl,
	ReleaseGroupUrl
}

@Serializable
enum class MusicBrainzArtworkCacheStatus {
	Found,
	NotFound
}

@Serializable
enum class MusicBrainzArtworkSourceType {
	Release,
	ReleaseGroup
}

@Serializable
internal data class CoverArtArchiveResponseDto(
	val images: List<CoverArtArchiveImageDto> = emptyList()
)

@Serializable
internal data class CoverArtArchiveImageDto(
	val front: Boolean = false,
	val image: String? = null,
	val thumbnails: Map<String, String> = emptyMap()
)

@Serializable
internal data class MusicBrainzRecordingDto(
	val id: String = "",
	val title: String? = null,
	val disambiguation: String? = null,
	@SerialName("first-release-date") val firstReleaseDate: String? = null,
	@SerialName("artist-credit") val artistCredits: List<MusicBrainzArtistCreditDto> = emptyList(),
	val isrcs: List<String> = emptyList(),
	val genres: List<MusicBrainzTagDto> = emptyList(),
	val tags: List<MusicBrainzTagDto> = emptyList(),
	val relations: List<MusicBrainzRelationDto> = emptyList(),
	val releases: List<MusicBrainzReleaseDto> = emptyList()
)

@Serializable
internal data class MusicBrainzRecordingSearchResponseDto(
	val recordings: List<MusicBrainzRecordingSearchResultDto> = emptyList()
)

@Serializable
internal data class MusicBrainzRecordingSearchResultDto(
	val id: String = "",
	val score: String = ""
)

@Serializable
internal data class MusicBrainzArtistCreditDto(
	val name: String = "",
	val joinphrase: String = ""
)

@Serializable
internal data class MusicBrainzTagDto(
	val name: String = "",
	val count: Int? = null
)

@Serializable
internal data class MusicBrainzRelationDto(
	val type: String? = null,
	val ended: Boolean? = null,
	val url: MusicBrainzRelationUrlDto? = null,
	val work: MusicBrainzWorkDto? = null
)

@Serializable
internal data class MusicBrainzRelationUrlDto(
	val resource: String? = null
)

@Serializable
internal data class MusicBrainzWorkDto(
	val relations: List<MusicBrainzRelationDto> = emptyList()
)

@Serializable
internal data class MusicBrainzReleaseDto(
	val id: String = "",
	val title: String? = null,
	val disambiguation: String? = null,
	val date: String? = null,
	val country: String? = null,
	val status: String? = null,
	val relations: List<MusicBrainzRelationDto> = emptyList(),
	@SerialName("release-group") val releaseGroup: MusicBrainzReleaseGroupDto? = null
)

@Serializable
internal data class MusicBrainzReleaseGroupDto(
	val id: String = "",
	val title: String? = null,
	val disambiguation: String? = null,
	@SerialName("primary-type") val primaryType: String? = null,
	@SerialName("secondary-types") val secondaryTypes: List<String> = emptyList(),
	val relations: List<MusicBrainzRelationDto> = emptyList()
)

private fun lucenePhrase(value: String): String =
	value
		.trim()
		.replace(Regex("\\s+"), " ")
		.let { normalized ->
			"\"${buildString {
				normalized.forEach { char ->
					if (char in luceneSpecialChars) append('\\')
					append(char)
				}
			}}\""
		}

private val luceneSpecialChars = setOf(
	'\\',
	'+',
	'-',
	'!',
	'(',
	')',
	'{',
	'}',
	'[',
	']',
	'^',
	'"',
	'~',
	'*',
	'?',
	':',
	'/'
)

private fun encodePathSegment(value: String): String =
	encodeUrlComponent(value)

private fun encodeUrlComponent(value: String): String {
	val hex = "0123456789ABCDEF"
	return buildString {
		value.encodeToByteArray().forEach { byte ->
			val code = byte.toInt() and 0xff
			val char = code.toChar()
			if (
				char in 'A'..'Z' ||
				char in 'a'..'z' ||
				char in '0'..'9' ||
				char == '-' ||
				char == '.' ||
				char == '_' ||
				char == '~'
			) {
				append(char)
			} else {
				append('%')
				append(hex[code shr 4])
				append(hex[code and 0x0f])
			}
		}
	}
}
