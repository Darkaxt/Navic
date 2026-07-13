package paige.navic.data.remote.aurral

import paige.navic.domain.repositories.*

import kotlinx.serialization.decodeFromString

internal fun aurralDiscoverySummary(
	baseUrl: String,
	response: AurralDiscoveryResponseDto,
	recentlyAdded: List<AurralDiscoverArtist> = emptyList(),
	libraryArtists: List<AurralDiscoverArtist> = emptyList(),
	recentReleases: List<AurralAlbumSearchItem> = emptyList()
): AurralDiscoverySummary {
	val safeLibraryArtists = libraryArtists
	return AurralDiscoverySummary(
		recentlyAdded = recentlyAdded.withLibraryArtistMonitoring(safeLibraryArtists),
		recommendations = response.recommendations.mapNotNull { it.toDiscoverArtist(baseUrl) }
			.withLibraryArtistMonitoring(safeLibraryArtists),
		globalTop = response.globalTop.mapNotNull { it.toDiscoverArtist(baseUrl) }
			.withLibraryArtistMonitoring(safeLibraryArtists),
		basedOn = response.basedOn.mapNotNull { it.toDiscoverArtist(baseUrl) }
			.withLibraryArtistMonitoring(safeLibraryArtists),
		libraryArtists = safeLibraryArtists,
		recentReleases = recentReleases,
		fallbackGenres = response.fallbackGenres.mapNotNull { it.toFallbackGenreSection(baseUrl) }
			.map { section ->
				section.copy(artists = section.artists.withLibraryArtistMonitoring(safeLibraryArtists))
			},
		topTags = response.topTags.cleanedAurralStrings(),
		topGenres = response.topGenres.cleanedAurralStrings(),
		isUpdating = response.isUpdating,
		stale = response.stale,
		provider = response.provider?.trim()?.takeIf { it.isNotEmpty() },
		discoveryMode = response.discoveryMode?.trim()?.takeIf { it.isNotEmpty() }
	)
}

internal fun aurralArtistSearchResult(
	baseUrl: String,
	query: String,
	response: AurralArtistSearchResponseDto
): AurralArtistSearchResult =
	AurralArtistSearchResult(
		query = query,
		count = response.count,
		offset = response.offset,
		artists = response.artists.mapNotNull { it.toDiscoverArtist(baseUrl, trustUuidId = true) }
	)

internal fun aurralAlbumSearchResult(
	baseUrl: String,
	query: String,
	response: AurralAlbumSearchResponseDto
): AurralAlbumSearchResult =
	AurralAlbumSearchResult(
		query = response.query?.trim()?.takeIf { it.isNotEmpty() } ?: query,
		count = response.count,
		offset = response.offset,
		hasMore = response.hasMore,
		albums = response.items.mapNotNull { it.toAlbumSearchItem(baseUrl) }
	)

internal fun aurralRecentReleases(
	baseUrl: String,
	response: List<AurralAlbumSearchItemDto>
): List<AurralAlbumSearchItem> =
	response.mapNotNull { it.toAlbumSearchItem(baseUrl) }

internal fun decodeAurralAlbumTracks(responseText: String): List<AurralAlbumTrackDto> =
	runCatching {
		AURRAL_JSON.decodeFromString<List<AurralAlbumTrackDto>>(responseText)
	}.getOrElse {
		AURRAL_JSON.decodeFromString<AurralAlbumTracksResponseDto>(responseText).tracks
	}

internal fun aurralAlbumTrackItems(
	response: List<AurralAlbumTrackDto>
): List<AurralAlbumTrackItem> =
	response.mapNotNull { it.toAlbumTrackItem() }

internal fun aurralRecentlyAddedArtists(
	baseUrl: String,
	response: List<AurralDiscoverArtistDto>
): List<AurralDiscoverArtist> =
	response.mapNotNull { it.toRecentlyAddedArtist(baseUrl) }

internal fun aurralLibraryArtists(
	baseUrl: String,
	response: List<AurralDiscoverArtistDto>
): List<AurralDiscoverArtist> =
	response.mapNotNull { it.toLibraryArtist(baseUrl) }

private fun AurralDiscoverArtistDto.toDiscoverArtist(
	baseUrl: String,
	trustUuidId: Boolean = false
): AurralDiscoverArtist? {
	val recommendedAlbum = toRecommendedAlbum(baseUrl)
	if (recommendedAlbum != null) {
		val recommendedArtistId = listOf(artistMbid, foreignArtistId)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
			?: return null
		val recommendedArtistName = artistName?.trim()?.takeIf { it.isNotEmpty() }
			?: return null
		return AurralDiscoverArtist(
			id = recommendedArtistId,
			name = recommendedArtistName,
			imageUrl = null,
			tags = (tags + genres).cleanedAurralStrings(),
			matchedTags = matchedTags.cleanedAurralStrings(),
			reason = "Recommended: ${recommendedAlbum.title}",
			sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
			discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
			monitored = monitored,
			recommendedAlbums = listOf(recommendedAlbum),
			detailsIdVerified = true
		)
	}

	val artistId = verifiedAurralArtistIdCandidate(trustUuidId)
		?: return null
	val artistName = listOf(name, artistName)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralDiscoverArtist(
		id = artistId.id,
		name = artistName,
		imageUrl = aurralAbsoluteImageUrl(baseUrl, imageUrl ?: image),
		tags = (tags + genres).cleanedAurralStrings(),
		matchedTags = matchedTags.cleanedAurralStrings(),
		reason = aurralDiscoveryReason(this),
		sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
		discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
		monitored = monitored,
		detailsIdVerified = artistId.verified
	)
}

private fun AurralDiscoverArtistDto.toRecentlyAddedArtist(baseUrl: String): AurralDiscoverArtist? {
	val artistId = verifiedAurralArtistIdCandidate()
		?: return null
	val artistName = listOf(artistName, name, id)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralDiscoverArtist(
		id = artistId.id,
		name = artistName,
		imageUrl = aurralAbsoluteImageUrl(baseUrl, imageUrl ?: image),
		tags = (tags + genres).cleanedAurralStrings(),
		matchedTags = matchedTags.cleanedAurralStrings(),
		sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
		discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
		monitored = monitored,
		detailsIdVerified = artistId.verified
	)
}

private fun AurralDiscoverArtistDto.toLibraryArtist(baseUrl: String): AurralDiscoverArtist? {
	val artistId = verifiedAurralArtistIdCandidate()
		?: return null
	val artistName = listOf(artistName, name, id)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralDiscoverArtist(
		id = artistId.id,
		name = artistName,
		imageUrl = aurralAbsoluteImageUrl(baseUrl, imageUrl ?: image),
		tags = (tags + genres).cleanedAurralStrings(),
		matchedTags = matchedTags.cleanedAurralStrings(),
		sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
		discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
		monitored = monitored ?: monitorOption?.trim()?.equals("none", ignoreCase = true)?.not(),
		detailsIdVerified = artistId.verified
	)
}

private data class AurralArtistIdCandidate(
	val id: String,
	val verified: Boolean
)

private fun AurralDiscoverArtistDto.verifiedAurralArtistIdCandidate(trustUuidId: Boolean = false): AurralArtistIdCandidate? =
	listOf(
		foreignArtistId to true,
		mbid to true,
		artistMbid to true,
		id to (trustUuidId && id.isMusicBrainzUuid())
	).firstNotNullOfOrNull { (candidate, verified) ->
		candidate?.trim()?.takeIf(String::isNotEmpty)?.let { AurralArtistIdCandidate(it, verified) }
	}

private fun String?.isMusicBrainzUuid(): Boolean =
	this?.trim()?.matches(MUSICBRAINZ_UUID_REGEX) == true

private val MUSICBRAINZ_UUID_REGEX = Regex(
	"""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"""
)

private fun AurralFallbackGenreSectionDto.toFallbackGenreSection(baseUrl: String): AurralFallbackGenreSection? {
	val safeGenre = listOf(genre, name, title)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeArtists = artists
		.mapNotNull { it.toDiscoverArtist(baseUrl) }
		.distinctBy { it.id.trim().lowercase() }
	if (safeArtists.isEmpty()) return null
	return AurralFallbackGenreSection(
		genre = safeGenre,
		artists = safeArtists
	)
}

private fun AurralDiscoverArtistDto.toRecommendedAlbum(baseUrl: String): AurralAlbumSearchItem? {
	val kind = type?.trim()?.lowercase()
	val albumTitle = listOf(albumName, title, name.takeIf { kind == "album" })
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val albumId = listOf(foreignAlbumId, mbid, id)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeArtistName = artistName?.trim()?.takeIf { it.isNotEmpty() }
		?: return null
	val safeArtistMbid = listOf(artistMbid, foreignArtistId)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralAlbumSearchItem(
		id = albumId,
		title = albumTitle,
		artistName = safeArtistName,
		artistMbid = safeArtistMbid,
		releaseDate = releaseDate?.trim()?.takeIf { it.isNotEmpty() },
		primaryType = primaryType?.trim()?.takeIf { it.isNotEmpty() },
		secondaryTypes = secondaryTypes.cleanedAurralStrings(),
		coverUrl = aurralAbsoluteImageUrl(baseUrl, coverUrl ?: imageUrl ?: image),
		status = status?.trim()?.takeIf { it.isNotEmpty() }
	)
}

private fun AurralAlbumSearchItemDto.toAlbumSearchItem(baseUrl: String): AurralAlbumSearchItem? {
	val albumId = listOf(id, mbid, foreignAlbumId)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val albumTitle = listOf(title, albumName)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeArtistName = artistName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val safeArtistMbid = listOf(artistMbid, foreignArtistId)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralAlbumSearchItem(
		id = albumId,
		title = albumTitle,
		artistName = safeArtistName,
		artistMbid = safeArtistMbid,
		releaseDate = releaseDate?.trim()?.takeIf { it.isNotEmpty() },
		primaryType = primaryType?.trim()?.takeIf { it.isNotEmpty() },
		secondaryTypes = secondaryTypes.cleanedAurralStrings(),
		coverUrl = aurralAbsoluteImageUrl(baseUrl, coverUrl ?: imageUrl ?: image),
		inLibrary = inLibrary,
		libraryAlbumId = libraryAlbumId?.trim()?.takeIf { it.isNotEmpty() },
		libraryArtistId = libraryArtistId?.trim()?.takeIf { it.isNotEmpty() },
		status = status?.trim()?.takeIf { it.isNotEmpty() }
	)
}

private fun AurralAlbumTrackDto.toAlbumTrackItem(): AurralAlbumTrackItem? {
	val safeTitle = listOf(title, trackName, recordingTitle)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeId = listOf(id, mbid, recordingMbid)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: "track-${discNumber ?: mediumNumber ?: 1}-${trackNumber ?: position ?: absoluteTrackNumber ?: safeTitle}"
	return AurralAlbumTrackItem(
		id = safeId,
		title = safeTitle,
		artistName = listOf(artistName, artistCredit, artist)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) },
		recordingMbid = listOf(recordingMbid, mbid)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) },
		discNumber = discNumber ?: mediumNumber,
		trackNumber = trackNumber ?: position ?: absoluteTrackNumber,
		durationMs = durationMs ?: durationMsSnake ?: length,
		previewUrl = listOf(previewUrl, previewUrlSnake)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) },
		status = status?.trim()?.takeIf { it.isNotEmpty() },
		requested = requested
	)
}

private fun aurralDiscoveryReason(artist: AurralDiscoverArtistDto): String? {
	val sourceArtist = artist.sourceArtist?.trim()?.takeIf { it.isNotEmpty() }
	if (sourceArtist != null) return "Similar to $sourceArtist"
	val sourceArtists = artist.sourceArtists.cleanedAurralStrings()
	return when {
		sourceArtists.size == 1 -> "Because you listen to ${sourceArtists.single()}"
		sourceArtists.size > 1 -> "Because you listen to ${sourceArtists.take(2).joinToString(", ")}"
		artist.discoveryTier?.trim()?.equals("deeper", ignoreCase = true) == true ->
			"A deeper discovery pick"
		else -> null
	}
}

internal fun List<AurralDiscoverArtist>.withLibraryArtistMonitoring(
	libraryArtists: List<AurralDiscoverArtist>
): List<AurralDiscoverArtist> {
	if (libraryArtists.isEmpty()) return this
	val libraryById = libraryArtists
		.mapNotNull { artist -> artist.id.normalizedAurralArtistKey()?.let { it to artist } }
		.toMap()
	val libraryByName = libraryArtists
		.mapNotNull { artist -> artist.name.normalizedAurralArtistName()?.let { it to artist } }
		.toMap()
	return map { artist ->
		val libraryArtist = artist.id.normalizedAurralArtistKey()?.let(libraryById::get)
			?: artist.name.normalizedAurralArtistName()?.let(libraryByName::get)
		val preferredImageUrl = libraryArtist?.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
			?: artist.imageUrl
		val monitored = artist.monitored ?: libraryArtist?.monitored
		if (preferredImageUrl != artist.imageUrl || monitored != artist.monitored) {
			artist.copy(
				imageUrl = preferredImageUrl,
				monitored = monitored
			)
		} else {
			artist
		}
	}
}

private fun List<String>.cleanedAurralStrings(): List<String> =
	mapNotNull { it.trim().takeIf(String::isNotEmpty) }
		.distinctBy { it.lowercase() }

private fun String?.normalizedAurralArtistKey(): String? =
	this
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }

private fun String?.normalizedAurralArtistName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }
