package paige.navic.domain.repositories

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistExternalLink
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralFlowSongIdPrefix
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

internal fun aurralServiceStatus(
	health: AurralHealthDto,
	authMe: AurralAuthMeDto?,
	weeklyFlow: AurralWeeklyFlowStatusDto?,
	requests: List<AurralRequestDto>
): AurralServiceStatus {
	val user = authMe?.user ?: health.user
	val stats = weeklyFlow?.stats ?: AurralFlowStatsDto()
	val flows = weeklyFlow?.flows.orEmpty().mapNotNull { flow ->
		val id = flow.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		flow.toSummary(weeklyFlow?.flowStats?.get(id))
	}
	return AurralServiceStatus(
		healthStatus = health.status,
		appVersion = health.appVersion,
		authRequired = health.authRequired,
		username = user?.username,
		role = user?.role,
		accessFlow = user?.permissions?.accessFlow == true,
		addArtist = user?.permissions?.addArtist == true,
		addAlbum = user?.permissions?.addAlbum == true,
		changeMonitoring = user?.permissions?.changeMonitoring == true,
		lidarrConfigured = health.lidarrConfigured || health.rootFolderConfigured,
		discoveryRecommendationsCount = health.discovery?.recommendationsCount ?: 0,
		discoveryUpdating = health.discovery?.isUpdating == true,
		flowsCount = weeklyFlow?.flows.orEmpty().size,
		enabledFlowsCount = weeklyFlow?.flows.orEmpty().count { it.enabled },
		sharedPlaylistsCount = weeklyFlow?.sharedPlaylists.orEmpty().size,
		requestsCount = requests.size,
		flowTracksTotal = stats.total,
		flowTracksPending = stats.pending,
		flowTracksDownloading = stats.downloading,
		flowTracksDone = stats.done,
		flowTracksFailed = stats.failed,
		flowPhase = weeklyFlow?.hint?.phase,
		flowMessage = weeklyFlow?.hint?.message,
		flows = flows,
		flowCapabilities = weeklyFlow?.capabilities?.toCapabilities() ?: AurralFlowCapabilities(),
		acquisitionQueue = requests.mapNotNull(::aurralAcquisitionQueueItem)
	)
}

internal fun aurralDefaultFlowCreatePayload(
	name: String,
	size: Int,
	scheduleDay: Int
): AurralFlowCreatePayload {
	val trimmedName = name.trim().takeIf { it.isNotEmpty() }
		?: error("Flow name is required.")
	if (size <= 0) error("Flow size must be positive.")
	if (scheduleDay !in 0..6) error("Flow schedule day must be between 0 and 6.")
	return AurralFlowCreatePayload(
		name = trimmedName,
		size = size,
		mix = AurralFlowMix(discover = 34, mix = 33, trending = 33, focus = 0),
		scheduleDays = listOf(scheduleDay),
		scheduleTime = "00:00"
	)
}

internal fun currentAurralScheduleDay(): Int {
	val isoDay = Clock.System.now()
		.toLocalDateTime(TimeZone.currentSystemDefault())
		.dayOfWeek
		.ordinal + 1
	return if (isoDay == 7) 0 else isoDay
}

private fun AurralFlowDto.toSummary(stats: AurralFlowStatsDto? = null): AurralFlowSummary? {
	val id = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Flow"
	val safeSize = size?.takeIf { it > 0 } ?: 30
	return AurralFlowSummary(
		id = id,
		name = name,
		enabled = enabled,
		size = safeSize,
		nextRunAt = nextRunAt,
		mix = mix.toMix(),
		tags = tags.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		relatedArtists = relatedArtists.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		scheduleDays = scheduleDays.filter { it in 0..6 },
		scheduleTime = scheduleTime?.trim()?.takeIf { it.isNotEmpty() } ?: "00:00",
		stats = stats.toStats()
	)
}

private fun AurralFlowMixDto.toMix(): AurralFlowMix =
	AurralFlowMix(
		discover = discover,
		mix = mix,
		trending = trending,
		focus = focus
	)

private fun AurralFlowStatsDto?.toStats(): AurralFlowStats =
	AurralFlowStats(
		total = this?.total ?: 0,
		pending = this?.pending ?: 0,
		downloading = this?.downloading ?: 0,
		done = this?.done ?: 0,
		failed = this?.failed ?: 0
	)

private fun AurralFlowCapabilitiesDto.toCapabilities(): AurralFlowCapabilities =
	AurralFlowCapabilities(
		lastfmRequired = lastfmRequired,
		availableSources = availableSources.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		unavailableSources = unavailableSources.mapNotNull { (key, value) ->
			val normalizedKey = key.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
			val normalizedValue = value.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
			normalizedKey to normalizedValue
		}.toMap()
	)

internal fun AurralFlowActionDto.toResult(): AurralFlowActionResult =
	AurralFlowActionResult(
		success = success,
		flowId = flow?.id?.trim()?.takeIf { it.isNotEmpty() }
			?: flowId?.trim()?.takeIf { it.isNotEmpty() },
		enabled = enabled ?: flow?.enabled,
		tracksQueued = tracksQueued,
		reserveTracks = reserveTracks,
		jobIds = jobIds.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		message = message?.trim()?.takeIf { it.isNotEmpty() },
		flow = flow?.toSummary()
	)

internal fun AurralFlowJobDto.toDomainSong(
	baseUrl: String,
	sessionToken: String?,
	streamToken: String?,
	allowUnauthenticatedStream: Boolean
): DomainSong? {
	val jobId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val title = trackName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val streamUrl = when {
		!sessionToken.isNullOrBlank() -> aurralFlowStreamUrl(baseUrl, jobId, sessionToken)
		!streamToken.isNullOrBlank() -> aurralFlowStreamTokenUrl(baseUrl, jobId, streamToken)
		allowUnauthenticatedStream -> aurralFlowRawStreamUrl(baseUrl, jobId)
		else -> null
	} ?: return null
	val fileExtension = finalPath
		?.substringBefore('?')
		?.substringAfterLast('/')
		?.substringAfterLast('.', "")
		?.lowercase()
		?.takeIf { it.isNotEmpty() }
		?: "mp3"

	return DomainSong(
		id = "$AurralFlowSongIdPrefix$jobId",
		title = title,
		artistName = artistName?.trim()?.takeIf { it.isNotEmpty() } ?: "Aurral",
		artistId = artistMbid?.trim()?.takeIf { it.isNotEmpty() } ?: "",
		albumTitle = albumName?.trim()?.takeIf { it.isNotEmpty() },
		albumId = albumMbid?.trim()?.takeIf { it.isNotEmpty() },
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = releaseYear?.trim()?.take(4)?.toIntOrNull(),
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = durationMs?.takeIf { it > 0 }?.milliseconds ?: 0.milliseconds,
		bpm = null,
		contributors = emptyList(),
		playCount = 0,
		userRating = null,
		averageRating = null,
		bitRate = null,
		bitDepth = null,
		sampleRate = null,
		audioChannelCount = null,
		replayGain = null,
		fileSize = 0,
		fileExtension = fileExtension,
		mimeType = fileExtension.toAurralAudioMimeType(),
		filePath = streamUrl,
		starredAt = null,
		coverArtId = null,
		musicBrainzId = trackMbid?.trim()?.takeIf { it.isNotEmpty() },
		explicitStatus = DomainExplicitStatus.Unknown
	)
}

private fun String.toAurralAudioMimeType(): String =
	when (lowercase()) {
		"flac" -> "audio/flac"
		"m4a", "mp4" -> "audio/mp4"
		"ogg", "oga" -> "audio/ogg"
		"wav" -> "audio/wav"
		"aac" -> "audio/aac"
		else -> "audio/mpeg"
	}

internal fun aurralAcquisitionQueueItem(request: AurralRequestDto): AurralAcquisitionQueueItem? {
	val id = request.id?.trim()?.takeIf { it.isNotEmpty() }
		?: request.albumId?.trim()?.takeIf { it.isNotEmpty() }?.let { "album-$it" }
		?: request.resolvedAlbumMbid()?.let { "album-$it" }
		?: return null
	val albumName = request.resolvedAlbumName() ?: return null
	val artistName = request.artistName?.trim()?.takeIf { it.isNotEmpty() } ?: "Artist"
	val status = request.status?.trim()?.takeIf { it.isNotEmpty() } ?: "requested"
	return AurralAcquisitionQueueItem(
		id = id,
		type = request.type?.trim()?.takeIf { it.isNotEmpty() } ?: "album",
		albumId = request.albumId?.trim()?.takeIf { it.isNotEmpty() },
		albumMbid = request.resolvedAlbumMbid(),
		albumName = albumName,
		artistId = request.artistId?.trim()?.takeIf { it.isNotEmpty() },
		artistMbid = request.artistMbid?.trim()?.takeIf { it.isNotEmpty() },
		artistName = artistName,
		status = status,
		requestedAt = request.requestedAt?.trim()?.takeIf { it.isNotEmpty() },
		inQueue = request.inQueue
	)
}

fun aurralAcquisitionDeleteTarget(item: AurralAcquisitionQueueItem): AurralAcquisitionDeleteTarget? =
	item.albumId?.trim()?.takeIf { it.isNotEmpty() }?.let(AurralAcquisitionDeleteTarget::Album)
		?: item.artistMbid?.trim()?.takeIf { it.isNotEmpty() }?.let(AurralAcquisitionDeleteTarget::Artist)

private fun AurralRequestDto.resolvedAlbumMbid(): String? =
	albumMbid?.trim()?.takeIf { it.isNotEmpty() }
		?: mbid?.trim()?.takeIf { it.isNotEmpty() }

private fun AurralRequestDto.resolvedAlbumName(): String? =
	albumName?.trim()?.takeIf { it.isNotEmpty() }
		?: name?.trim()?.takeIf { it.isNotEmpty() }

internal fun aurralArtistEnrichment(
	baseUrl: String,
	details: AurralArtistDetailsDto,
	preview: AurralArtistPreviewDto,
	similar: AurralSimilarArtistsDto,
	requests: List<AurralRequestDto>
): AurralArtistEnrichment {
	val artistMbid = details.id.orEmpty()
	val artistName = details.name.orEmpty()
	return AurralArtistEnrichment(
		artistMbid = artistMbid,
		artistName = artistName,
		imageUrl = aurralArtistDetailsImageUrl(baseUrl, details),
		bio = details.bio?.trim()?.takeIf { it.isNotEmpty() },
		genres = details.genres
			.mapNotNull { genre -> genre.trim().takeIf { it.isNotEmpty() } }
			.distinctBy { it.lowercase() },
		externalLinks = aurralArtistExternalLinks(details),
		releaseGroups = details.releaseGroups.mapNotNull { releaseGroup ->
			val id = releaseGroup.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val title = releaseGroup.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			AurralReleaseGroup(
				id = id,
				title = title,
				firstReleaseDate = releaseGroup.firstReleaseDate,
				primaryType = releaseGroup.primaryType,
				secondaryTypes = releaseGroup.secondaryTypes,
				coverUrl = releaseGroup.coverUrl
			)
		},
		previewTracks = aurralPreviewTracks(preview),
		similarArtists = aurralSimilarArtists(baseUrl, similar),
		requests = aurralAlbumRequests(requests),
		monitored = details.lidarrData?.monitored
	)
}

internal fun aurralArtistDetailsImageUrl(
	baseUrl: String,
	details: AurralArtistDetailsDto
): String? =
	listOf(
		details.imageUrl,
		details.coverUrl,
		details.image,
		details.images.firstOrNull { image -> image.isNotBlank() }
	).firstNotNullOfOrNull { image ->
		aurralAbsoluteImageUrl(baseUrl, image?.trim()?.takeIf { it.isNotEmpty() })
	}

internal fun aurralPreviewTracks(preview: AurralArtistPreviewDto): List<AurralPreviewTrack> =
	preview.tracks.mapNotNull { track ->
		val id = track.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		val title = track.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		AurralPreviewTrack(
			id = id,
			title = title,
			album = track.album,
			previewUrl = track.previewUrl,
			durationMs = track.durationMs,
			owned = track.owned,
			requested = track.requested,
			inLibrary = track.inLibrary,
			status = track.status
		)
	}

internal fun aurralSimilarArtists(
	baseUrl: String,
	similar: AurralSimilarArtistsDto
): List<AurralSimilarArtist> =
	similar.artists.mapNotNull { artist ->
		val id = artist.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		val name = artist.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		AurralSimilarArtist(
			id = id,
			name = name,
			imageUrl = aurralAbsoluteImageUrl(baseUrl, artist.imageUrl ?: artist.image),
			matchPercent = artist.match
		)
	}

internal fun aurralAlbumRequests(requests: List<AurralRequestDto>): List<AurralAlbumRequest> =
	requests.map { request ->
		AurralAlbumRequest(
			albumMbid = request.resolvedAlbumMbid(),
			albumName = request.resolvedAlbumName(),
			artistMbid = request.artistMbid,
			artistName = request.artistName,
			status = request.status
		)
	}

private fun aurralArtistExternalLinks(
	details: AurralArtistDetailsDto
): List<AurralArtistExternalLink> {
	val links = details.links.mapNotNull { link ->
		val type = link.type?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		val url = link.target?.trim()?.takeIf { it.isAbsoluteHttpUrl() } ?: return@mapNotNull null
		AurralArtistExternalLink(type = type, url = url)
	}
	val relations = details.relations.mapNotNull { relation ->
		val type = relation.type?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		val url = relation.url?.resource?.trim()?.takeIf { it.isAbsoluteHttpUrl() } ?: return@mapNotNull null
		AurralArtistExternalLink(type = type, url = url)
	}
	return (links + relations).distinctBy { link ->
		link.type.lowercase() to link.url.lowercase()
	}
}

private fun String.isAbsoluteHttpUrl(): Boolean =
	startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
