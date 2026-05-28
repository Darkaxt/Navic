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
			val existing = loadCacheEntries()
				.firstOrNull { it.songId == song.id }
				?.let {
					usableMusicBrainzArtworkCacheEntry(
						entry = it,
						fingerprint = fingerprint,
						nowMillis = currentTimeMillis()
					)
				}

			if (existing != null) {
				emitCache()
				return@runCatching existing.takeIf { it.status == MusicBrainzArtworkCacheStatus.Found }
			}

			if (
				!shouldResolveMusicBrainzArtworkOnPlayback(
					enabled = preferenceManager.musicBrainzArtworkFallbackEnabled,
					isOnline = connectivityManager.isOnline.value,
					isRadio = false,
					songCoverArtId = song.coverArtId,
					albumCoverArtId = albumCoverArtId,
					songMusicBrainzId = song.musicBrainzId,
					albumMusicBrainzId = albumMusicBrainzId
				)
			) {
				return@runCatching null
			}

			val recording = song.musicBrainzId.normalizedMbidOrNull()?.let { fetchRecording(it) }
			val resolved = resolveArtwork(
				albumMusicBrainzId = albumMusicBrainzId,
				recordingReleases = recording?.releases.orEmpty()
			)
			val metadata = recording?.let {
				musicBrainzTrackMetadata(
					recording = it,
					preferredReleaseMbid = resolved?.releaseMbid
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
				updatedAtMillis = currentTimeMillis()
			)

			putCacheEntry(entry)
			entry.takeIf { it.status == MusicBrainzArtworkCacheStatus.Found }
		}.onFailure { error ->
			Logger.w(TAG, "MusicBrainz artwork lookup failed for ${song.id}", error)
		}

	private suspend fun resolveArtwork(
		albumMusicBrainzId: String?,
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

		for (release in recordingReleases.take(MUSICBRAINZ_RECORDING_RELEASE_LOOKUP_LIMIT)) {
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

internal fun shouldResolveMusicBrainzArtworkOnPlayback(
	enabled: Boolean,
	isOnline: Boolean,
	isRadio: Boolean,
	songCoverArtId: String?,
	albumCoverArtId: String?,
	songMusicBrainzId: String?,
	albumMusicBrainzId: String?
): Boolean =
	enabled &&
		isOnline &&
		!isRadio &&
		songCoverArtId.isNullOrBlank() &&
		albumCoverArtId.isNullOrBlank() &&
		(!songMusicBrainzId.isNullOrBlank() || !albumMusicBrainzId.isNullOrBlank())

internal fun coverArtArchiveReleaseEndpoint(mbid: String): String =
	"$COVER_ART_ARCHIVE_BASE_URL/release/${mbid.normalizedMbidOrNull() ?: mbid.trim()}"

internal fun coverArtArchiveReleaseGroupEndpoint(mbid: String): String =
	"$COVER_ART_ARCHIVE_BASE_URL/release-group/${mbid.normalizedMbidOrNull() ?: mbid.trim()}"

internal fun musicBrainzRecordingLookupEndpoint(mbid: String): String =
	"$MUSICBRAINZ_BASE_URL/ws/2/recording/${encodePathSegment(mbid.trim())}?inc=artist-credits+isrcs+releases+genres+tags&fmt=json"

internal fun musicBrainzTrackMetadata(
	recording: MusicBrainzRecordingDto,
	preferredReleaseMbid: String?
): MusicBrainzTrackMetadata {
	val release = recording.releases
		.firstOrNull { it.id == preferredReleaseMbid }
		?: recording.releases.firstOrNull()
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

private fun encodePathSegment(value: String): String {
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
