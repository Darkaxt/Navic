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

	private val _artworkBySongId = MutableStateFlow(loadCacheEntries().foundBySongId())
	val artworkBySongId = _artworkBySongId.asStateFlow()
	private val _metadataBySongId = MutableStateFlow(loadCacheEntries().musicBrainzMetadataBySongId())
	val metadataBySongId = _metadataBySongId.asStateFlow()

	fun clearCache() {
		synchronized(cacheLock) {
			preferenceManager.clearMusicBrainzArtworkCache()
			emitCache()
		}
	}

	suspend fun prefetchArtworkForPlayingSong(song: DomainSong): Result<MusicBrainzArtworkCacheEntry?> =
		runCatching {
			if (song.id.startsWith("radio_")) return@runCatching null

			val album = song.albumId?.let { albumDao.getAlbumById(it)?.album }
			val albumCoverArtId = album?.coverArtId
			val albumMusicBrainzId = album?.musicBrainzId
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
				songMusicBrainzId = song.musicBrainzId,
				albumMusicBrainzId = albumMusicBrainzId,
				songTitle = song.title,
				artistName = song.artistName
			)
			val existing = loadCacheEntries()
				.firstOrNull { it.songId == song.id }
				?.let {
					usableMusicBrainzPlaybackCacheEntry(
						entry = it,
						fingerprint = fingerprint,
						nowMillis = currentTimeMillis(),
						needsMetadata = shouldResolveMetadata
					)
				}

			if (existing != null) {
				emitCache()
				return@runCatching existing.takeIf { it.status == MusicBrainzArtworkCacheStatus.Found }
			}

			if (!shouldResolveMetadata && !shouldResolveArtwork) {
				return@runCatching null
			}

			val recording = if (shouldResolveMetadata || shouldResolveArtwork) {
				song.musicBrainzId.normalizedMbidOrNull()
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
				musicBrainzTrackMetadata(
					recording = it,
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
		albumMusicBrainzId.normalizedMbidOrNull()?.let { mbid ->
			fetchCoverArtArchiveArtwork(mbid, MusicBrainzArtworkSourceType.Release)
				?.copy(releaseMbid = mbid)
				?.let { return it }
			fetchCoverArtArchiveArtwork(mbid, MusicBrainzArtworkSourceType.ReleaseGroup)
				?.copy(releaseGroupMbid = mbid)
				?.let { return it }
		}

		for (release in preferredMusicBrainzRecordingReleases(recordingReleases, albumTitle)
			.take(MUSICBRAINZ_RECORDING_RELEASE_LOOKUP_LIMIT)) {
			val releaseMbid = release.id.normalizedMbidOrNull() ?: continue
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
			val entries = loadCacheEntries()
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
		val entries = loadCacheEntries()
		_artworkBySongId.value = entries.foundBySongId()
		_metadataBySongId.value = entries.musicBrainzMetadataBySongId()
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
			!songMusicBrainzId.isNullOrBlank() ||
				canSearchMusicBrainzRecording(songTitle, artistName)
		)

internal fun shouldResolveMusicBrainzArtworkOnPlayback(
	enabled: Boolean,
	isOnline: Boolean,
	isRadio: Boolean,
	songCoverArtId: String?,
	albumCoverArtId: String?,
	songMusicBrainzId: String?,
	albumMusicBrainzId: String?,
	songTitle: String? = null,
	artistName: String? = null
): Boolean =
	enabled &&
		isOnline &&
		!isRadio &&
		songCoverArtId.isNullOrBlank() &&
		albumCoverArtId.isNullOrBlank() &&
		(
			!songMusicBrainzId.isNullOrBlank() ||
				!albumMusicBrainzId.isNullOrBlank() ||
				canSearchMusicBrainzRecording(songTitle, artistName)
		)

internal fun coverArtArchiveReleaseEndpoint(mbid: String): String =
	"$COVER_ART_ARCHIVE_BASE_URL/release/${mbid.normalizedMbidOrNull() ?: mbid.trim()}"

internal fun coverArtArchiveReleaseGroupEndpoint(mbid: String): String =
	"$COVER_ART_ARCHIVE_BASE_URL/release-group/${mbid.normalizedMbidOrNull() ?: mbid.trim()}"

internal fun musicBrainzRecordingLookupEndpoint(mbid: String): String =
	"$MUSICBRAINZ_BASE_URL/ws/2/recording/${encodePathSegment(mbid.trim())}?inc=artist-credits+isrcs+releases+release-groups+genres+tags&fmt=json"

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
			result.id.normalizedMbidOrNull() != null &&
				(result.score.toIntOrNull() ?: 0) >= MUSICBRAINZ_RECORDING_SEARCH_MIN_SCORE
		}
		?.id
		?.normalizedMbidOrNull()

private fun canSearchMusicBrainzRecording(title: String?, artistName: String?): Boolean =
	!title.isNullOrBlank() && !artistName.isNullOrBlank()

internal fun musicBrainzTrackMetadata(
	recording: MusicBrainzRecordingDto,
	preferredReleaseMbid: String?,
	preferredAlbumTitle: String? = null
): MusicBrainzTrackMetadata {
	val release = recording.releases
		.firstOrNull { it.id == preferredReleaseMbid }
		?: preferredMusicBrainzRecordingReleases(recording.releases, preferredAlbumTitle).firstOrNull()
	val releaseGroup = release?.releaseGroup
	val recordingMbid = recording.id.normalizedMbidOrNull()
	val releaseMbid = release?.id.normalizedMbidOrNull()
	val releaseGroupMbid = releaseGroup?.id.normalizedMbidOrNull()

	return MusicBrainzTrackMetadata(
		recordingMbid = recordingMbid,
		recordingTitle = recording.title.nonBlankOrNull(),
		artistCredit = musicBrainzArtistCredit(recording.artistCredits),
		firstReleaseDate = recording.firstReleaseDate.nonBlankOrNull(),
		releaseMbid = releaseMbid,
		releaseTitle = release?.title.nonBlankOrNull(),
		releaseGroupMbid = releaseGroupMbid,
		releaseGroupTitle = releaseGroup?.title.nonBlankOrNull(),
		releaseDate = release?.date.nonBlankOrNull(),
		country = release?.country.nonBlankOrNull(),
		status = release?.status.nonBlankOrNull(),
		genres = recording.genres.normalizedMusicBrainzTags(),
		tags = recording.tags.normalizedMusicBrainzTags(),
		isrcs = recording.isrcs.mapNotNull { it.nonBlankOrNull() }.distinct(),
		recordingUrl = recordingMbid?.let { "$MUSICBRAINZ_BASE_URL/recording/$it" },
		releaseUrl = releaseMbid?.let { "$MUSICBRAINZ_BASE_URL/release/$it" },
		releaseGroupUrl = releaseGroupMbid?.let { "$MUSICBRAINZ_BASE_URL/release-group/$it" }
	)
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
		MusicBrainzMetadataField.ArtistCredit.field(metadata.artistCredit),
		MusicBrainzMetadataField.FirstReleaseDate.field(metadata.firstReleaseDate),
		MusicBrainzMetadataField.ReleaseTitle.field(metadata.releaseTitle),
		MusicBrainzMetadataField.ReleaseGroupTitle.field(metadata.releaseGroupTitle),
		MusicBrainzMetadataField.ReleaseDate.field(metadata.releaseDate),
		MusicBrainzMetadataField.Country.field(metadata.country),
		MusicBrainzMetadataField.Status.field(metadata.status),
		MusicBrainzMetadataField.Genres.field(metadata.genres.joinToStringValue()),
		MusicBrainzMetadataField.Tags.field(metadata.tags.joinToStringValue()),
		MusicBrainzMetadataField.Isrcs.field(metadata.isrcs.joinToStringValue()),
		MusicBrainzMetadataField.RecordingUrl.field(metadata.recordingUrl),
		MusicBrainzMetadataField.ReleaseUrl.field(metadata.releaseUrl),
		MusicBrainzMetadataField.ReleaseGroupUrl.field(metadata.releaseGroupUrl)
	)
}

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

private fun List<MusicBrainzTagDto>.normalizedMusicBrainzTags(): List<String> =
	filter { it.name.isNotBlank() }
		.sortedWith(
			compareByDescending<MusicBrainzTagDto> { it.count ?: 0 }
				.thenBy { it.name.lowercase() }
		)
		.map { it.name.trim() }
		.distinct()
		.take(10)

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
	if (needsMetadata && usableEntry.metadata == null && !usableEntry.metadataLookupAttempted) return null
	return usableEntry
}

internal fun cappedMusicBrainzArtworkCacheEntries(
	entries: List<MusicBrainzArtworkCacheEntry>,
	maxEntries: Int
): List<MusicBrainzArtworkCacheEntry> =
	entries
		.sortedByDescending { it.updatedAtMillis }
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

private fun List<MusicBrainzArtworkCacheEntry>.foundBySongId(): Map<String, MusicBrainzArtworkCacheEntry> =
	associateBy { it.songId }
		.filterValues { it.status == MusicBrainzArtworkCacheStatus.Found && !it.imageUrl.isNullOrBlank() }

internal fun List<MusicBrainzArtworkCacheEntry>.musicBrainzMetadataBySongId(): Map<String, MusicBrainzTrackMetadata> =
	associateBy { it.songId }
		.mapValues { it.value.metadata }
		.filterValues { it != null }
		.mapValues { it.value!! }

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
	val updatedAtMillis: Long
)

@Serializable
data class MusicBrainzTrackMetadata(
	val recordingMbid: String? = null,
	val recordingTitle: String? = null,
	val artistCredit: String? = null,
	val firstReleaseDate: String? = null,
	val releaseMbid: String? = null,
	val releaseTitle: String? = null,
	val releaseGroupMbid: String? = null,
	val releaseGroupTitle: String? = null,
	val releaseDate: String? = null,
	val country: String? = null,
	val status: String? = null,
	val genres: List<String> = emptyList(),
	val tags: List<String> = emptyList(),
	val isrcs: List<String> = emptyList(),
	val recordingUrl: String? = null,
	val releaseUrl: String? = null,
	val releaseGroupUrl: String? = null
)

data class MusicBrainzMetadataDisplayField(
	val field: MusicBrainzMetadataField,
	val value: String
)

enum class MusicBrainzMetadataField {
	RecordingTitle,
	ArtistCredit,
	FirstReleaseDate,
	ReleaseTitle,
	ReleaseGroupTitle,
	ReleaseDate,
	Country,
	Status,
	Genres,
	Tags,
	Isrcs,
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
	@SerialName("first-release-date") val firstReleaseDate: String? = null,
	@SerialName("artist-credit") val artistCredits: List<MusicBrainzArtistCreditDto> = emptyList(),
	val isrcs: List<String> = emptyList(),
	val genres: List<MusicBrainzTagDto> = emptyList(),
	val tags: List<MusicBrainzTagDto> = emptyList(),
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
internal data class MusicBrainzReleaseDto(
	val id: String = "",
	val title: String? = null,
	val date: String? = null,
	val country: String? = null,
	val status: String? = null,
	@SerialName("release-group") val releaseGroup: MusicBrainzReleaseGroupDto? = null
)

@Serializable
internal data class MusicBrainzReleaseGroupDto(
	val id: String = "",
	val title: String? = null
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
