package paige.navic.ui.screens.aurral

import androidx.compose.runtime.Immutable
import paige.navic.domain.models.AurralMissingAlbumRow
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.aurralOwnershipStatusForProgress
import paige.navic.domain.models.aurralOwnershipStatusForStatus
import paige.navic.domain.models.settings.ArtworkSourcePriority
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.ui.screens.artist.ArtistHeaderImageCacheEntry

@Immutable
enum class AurralDiscoveryCollectionKind {
	RecentlyAddedArtists,
	RecentReleases,
	RecommendedArtists,
	BasedOnArtists,
	GlobalTopArtists,
	GenreArtists,
	TopTags
}

@Immutable
sealed interface AurralDiscoveryCollectionRow {
	val kind: AurralDiscoveryCollectionKind

	@Immutable
	data class Artists(
		override val kind: AurralDiscoveryCollectionKind,
		val artists: List<AurralDiscoverArtist>,
		val tag: String? = null
	) : AurralDiscoveryCollectionRow

	@Immutable
	data class Albums(
		override val kind: AurralDiscoveryCollectionKind,
		val albums: List<AurralAlbumSearchItem>
	) : AurralDiscoveryCollectionRow

	@Immutable
	data class Tags(
		override val kind: AurralDiscoveryCollectionKind = AurralDiscoveryCollectionKind.TopTags,
		val tags: List<String>
	) : AurralDiscoveryCollectionRow
}
fun aurralHubDiscoverArtists(
	discovery: AurralDiscoverySummary,
	limit: Int = 8,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralDiscoverArtist> =
	aurralDiscoverListArtists(discovery)
		.withCachedArtistPhotos(
			entries = artistPhotoCacheEntries,
			artistArtworkPriority = artistArtworkPriority,
			externalArtworkEnabled = externalArtworkEnabled
		)
		.take(limit.coerceAtLeast(0))

fun aurralHubDiscoverHasMore(
	discovery: AurralDiscoverySummary,
	visibleLimit: Int = 8
): Boolean =
	aurralDiscoverListArtists(discovery).size > visibleLimit.coerceAtLeast(0)

fun aurralHubCanRenderDiscoveryWithoutStatus(
	discovery: AurralDiscoverySummary?
): Boolean =
	discovery?.let { aurralDiscoveryCollectionRows(it).isNotEmpty() } == true

fun aurralDiscoveryCollectionRows(
	discovery: AurralDiscoverySummary,
	limit: Int = 8,
	genreRowLimit: Int = Int.MAX_VALUE,
	tagLimit: Int = Int.MAX_VALUE,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralDiscoveryCollectionRow> {
	val safeLimit = limit.coerceAtLeast(0)
	val libraryArtists = discovery.libraryArtists.withCachedArtistPhotos(
		entries = artistPhotoCacheEntries,
		artistArtworkPriority = artistArtworkPriority,
		externalArtworkEnabled = externalArtworkEnabled
	)
	fun List<AurralDiscoverArtist>.displayArtists(): List<AurralDiscoverArtist> =
		withLibraryArtistMonitoring(libraryArtists)
			.withCachedArtistPhotos(
				entries = artistPhotoCacheEntries,
				artistArtworkPriority = artistArtworkPriority,
				externalArtworkEnabled = externalArtworkEnabled
			)
	return buildList {
		val recentlyAdded = aurralHubSearchArtists(
			discovery.recentlyAdded.displayArtists(),
			safeLimit
		)
		if (recentlyAdded.isNotEmpty()) {
			add(
				AurralDiscoveryCollectionRow.Artists(
					kind = AurralDiscoveryCollectionKind.RecentlyAddedArtists,
					artists = recentlyAdded
				)
			)
		}

		val recentReleases = aurralHubSearchAlbums(discovery.recentReleases, safeLimit)
		if (recentReleases.isNotEmpty()) {
			add(
				AurralDiscoveryCollectionRow.Albums(
					kind = AurralDiscoveryCollectionKind.RecentReleases,
					albums = recentReleases
				)
			)
		}

		val recommendations = aurralHubSearchArtists(
			discovery.recommendations.displayArtists(),
			safeLimit
		)
		if (recommendations.isNotEmpty()) {
			add(
				AurralDiscoveryCollectionRow.Artists(
					kind = AurralDiscoveryCollectionKind.RecommendedArtists,
					artists = recommendations
				)
			)
		}

		val basedOn = aurralHubSearchArtists(
			discovery.basedOn.displayArtists(),
			safeLimit
		)
		if (basedOn.isNotEmpty()) {
			add(
				AurralDiscoveryCollectionRow.Artists(
					kind = AurralDiscoveryCollectionKind.BasedOnArtists,
					artists = basedOn
				)
			)
		}

		val globalTop = aurralHubSearchArtists(
			discovery.globalTop.displayArtists(),
			safeLimit
		)
		if (globalTop.isNotEmpty()) {
			add(
				AurralDiscoveryCollectionRow.Artists(
					kind = AurralDiscoveryCollectionKind.GlobalTopArtists,
					artists = globalTop
				)
			)
		}

		addAll(
			aurralDiscoveryGenreRows(
				discovery = discovery,
				limit = safeLimit,
				genreRowLimit = genreRowLimit,
				artistPhotoCacheEntries = artistPhotoCacheEntries,
				artistArtworkPriority = artistArtworkPriority,
				externalArtworkEnabled = externalArtworkEnabled
			)
		)

		val topTags = aurralDiscoverTopTags(discovery, limit = tagLimit)
		if (topTags.isNotEmpty()) {
			add(AurralDiscoveryCollectionRow.Tags(tags = topTags))
		}
	}
}

fun aurralDiscoverTagArtists(
	discovery: AurralDiscoverySummary,
	tag: String,
	limit: Int = Int.MAX_VALUE,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralDiscoverArtist> {
	val normalizedTag = tag.normalizedAurralName() ?: return emptyList()
	return aurralDiscoverTagCandidateArtists(
		discovery = discovery,
		artistPhotoCacheEntries = artistPhotoCacheEntries,
		artistArtworkPriority = artistArtworkPriority,
		externalArtworkEnabled = externalArtworkEnabled
	)
		.filter { artist -> artist.matchesAurralTag(normalizedTag) }
		.take(limit.coerceAtLeast(0))
}

fun aurralDiscoverListArtists(
	discovery: AurralDiscoverySummary,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralDiscoverArtist> =
	mergeAurralDiscoverArtists(
		discovery.recommendations +
			discovery.recentReleases.mapNotNull { it.toDiscoverArtistRecommendation() } +
			discovery.globalTop
	).withLibraryArtistMonitoring(
		discovery.libraryArtists.withCachedArtistPhotos(
			entries = artistPhotoCacheEntries,
			artistArtworkPriority = artistArtworkPriority,
			externalArtworkEnabled = externalArtworkEnabled
		)
	).withCachedArtistPhotos(
		entries = artistPhotoCacheEntries,
		artistArtworkPriority = artistArtworkPriority,
		externalArtworkEnabled = externalArtworkEnabled
	)

fun aurralSimilarArtistImageCandidates(
	discovery: AurralDiscoverySummary,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralSimilarArtist> =
	mergeAurralDiscoverArtists(
		discovery.libraryArtists +
			discovery.recentlyAdded +
			discovery.recommendations +
			discovery.globalTop +
			discovery.basedOn +
			discovery.fallbackGenres.flatMap { it.artists }
	).withCachedArtistPhotos(
		entries = artistPhotoCacheEntries,
		artistArtworkPriority = artistArtworkPriority,
		externalArtworkEnabled = externalArtworkEnabled
	).mapNotNull { artist ->
		val imageUrl = artist.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
			?: return@mapNotNull null
		val id = artist.id.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		val name = artist.name.trim().takeIf { it.isNotEmpty() } ?: id
		AurralSimilarArtist(
			id = id,
			name = name,
			imageUrl = imageUrl
		)
	}

fun aurralDiscoverCollectionKind(routeValue: String?): AurralDiscoveryCollectionKind? =
	routeValue?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
		runCatching { AurralDiscoveryCollectionKind.valueOf(value) }.getOrNull()
	}

fun aurralDiscoverCollectionArtists(
	discovery: AurralDiscoverySummary,
	kind: AurralDiscoveryCollectionKind,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralDiscoverArtist> =
	when (kind) {
		AurralDiscoveryCollectionKind.RecentlyAddedArtists ->
			aurralHubSearchArtists(discovery.recentlyAdded, Int.MAX_VALUE)

		AurralDiscoveryCollectionKind.RecommendedArtists ->
			aurralHubSearchArtists(discovery.recommendations, Int.MAX_VALUE)

		AurralDiscoveryCollectionKind.BasedOnArtists ->
			aurralHubSearchArtists(discovery.basedOn, Int.MAX_VALUE)

		AurralDiscoveryCollectionKind.GlobalTopArtists ->
			aurralHubSearchArtists(discovery.globalTop, Int.MAX_VALUE)

		AurralDiscoveryCollectionKind.RecentReleases ->
			mergeAurralDiscoverArtists(
				discovery.recentReleases.mapNotNull { it.toDiscoverArtistRecommendation() }
			)

		AurralDiscoveryCollectionKind.GenreArtists,
		AurralDiscoveryCollectionKind.TopTags -> emptyList()
	}.withLibraryArtistMonitoring(
		discovery.libraryArtists.withCachedArtistPhotos(
			entries = artistPhotoCacheEntries,
			artistArtworkPriority = artistArtworkPriority,
			externalArtworkEnabled = externalArtworkEnabled
		)
	).withCachedArtistPhotos(
		entries = artistPhotoCacheEntries,
		artistArtworkPriority = artistArtworkPriority,
		externalArtworkEnabled = externalArtworkEnabled
	)

fun aurralHubSearchArtists(
	artists: List<AurralDiscoverArtist>,
	limit: Int = 8
): List<AurralDiscoverArtist> =
	artists
		.filter { artist -> artist.id.isNotBlank() && artist.name.isNotBlank() }
		.map { artist -> artist.copy(imageUrl = artist.imageUrl.externalAurralArtworkUrlOrNull()) }
		.distinctBy { it.id.trim().lowercase() }
		.take(limit.coerceAtLeast(0))

fun aurralHubSearchAlbums(
	albums: List<AurralAlbumSearchItem>,
	limit: Int = 8
): List<AurralAlbumSearchItem> =
	albums
		.filter { album ->
			album.id.isNotBlank() &&
				album.title.isNotBlank() &&
				album.artistName.isNotBlank() &&
				album.artistMbid.isNotBlank()
		}
		.distinctBy { it.id.trim().lowercase() }
		.sortedWith(
			compareBy<AurralAlbumSearchItem> { it.releaseDate.aurralSearchYearOrNull() == null }
				.thenByDescending { it.releaseDate.aurralSearchYearOrNull() ?: Int.MIN_VALUE }
				.thenBy { it.title.trim().lowercase() }
		)
		.take(limit.coerceAtLeast(0))

fun aurralDiscoverArtistsWithCachedPhotos(
	artists: List<AurralDiscoverArtist>,
	entries: List<ArtistHeaderImageCacheEntry>,
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralDiscoverArtist> =
	artists.withCachedArtistPhotos(
		entries = entries,
		artistArtworkPriority = artistArtworkPriority,
		externalArtworkEnabled = externalArtworkEnabled
	)

fun aurralSearchAlbumOwnershipStatus(album: AurralAlbumSearchItem): AurralOwnershipStatus =
	when {
		album.inLibrary || !album.libraryAlbumId.isNullOrBlank() -> AurralOwnershipStatus.Owned
		else -> aurralOwnershipStatusForStatus(album.status)
	}

fun aurralMissingAlbumOwnershipStatus(row: AurralMissingAlbumRow): AurralOwnershipStatus =
	aurralOwnershipStatusForProgress(row.acquisitionProgress)

private fun aurralDiscoveryGenreRows(
	discovery: AurralDiscoverySummary,
	limit: Int,
	genreRowLimit: Int,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralDiscoveryCollectionRow.Artists> {
	val safeLimit = limit.coerceAtLeast(0)
	val safeGenreRowLimit = genreRowLimit.coerceAtLeast(0)
	if (safeLimit == 0 || safeGenreRowLimit == 0) return emptyList()
	val libraryArtists = discovery.libraryArtists.withCachedArtistPhotos(
		entries = artistPhotoCacheEntries,
		artistArtworkPriority = artistArtworkPriority,
		externalArtworkEnabled = externalArtworkEnabled
	)
	fun List<AurralDiscoverArtist>.displayArtists(): List<AurralDiscoverArtist> =
		withLibraryArtistMonitoring(libraryArtists)
			.withCachedArtistPhotos(
				entries = artistPhotoCacheEntries,
				artistArtworkPriority = artistArtworkPriority,
				externalArtworkEnabled = externalArtworkEnabled
			)
	val fallbackRows = discovery.fallbackGenres
		.asSequence()
		.mapNotNull { section ->
			val genre = section.genre.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val artists = aurralHubSearchArtists(
				section.artists.displayArtists(),
				safeLimit
			)
			if (artists.isEmpty()) {
				null
			} else {
				AurralDiscoveryCollectionRow.Artists(
					kind = AurralDiscoveryCollectionKind.GenreArtists,
					artists = artists,
					tag = genre
				)
			}
		}
		.take(safeGenreRowLimit)
		.toList()
	if (fallbackRows.isNotEmpty()) return fallbackRows

	val candidatePool = aurralDiscoverTagCandidateArtists(
		discovery = discovery,
		artistPhotoCacheEntries = artistPhotoCacheEntries,
		artistArtworkPriority = artistArtworkPriority,
		externalArtworkEnabled = externalArtworkEnabled
	)
	return discovery.topGenres
		.cleanedAurralDisplayStrings()
		.asSequence()
		.mapNotNull { genre ->
			val normalizedGenre = genre.normalizedAurralName() ?: return@mapNotNull null
			val artists = candidatePool
				.asSequence()
				.filter { artist ->
					artist.id.normalizedAurralKey() != null &&
						artist.matchesAurralTag(normalizedGenre)
				}
				.sortedWith(compareBy<AurralDiscoverArtist> { it.name.trim().lowercase() })
				.take(safeLimit)
				.toList()
			if (artists.isEmpty()) {
				null
			} else {
				AurralDiscoveryCollectionRow.Artists(
					kind = AurralDiscoveryCollectionKind.GenreArtists,
					artists = artists,
					tag = genre
				)
			}
		}
		.take(safeGenreRowLimit)
		.toList()
}

private fun aurralDiscoverTagCandidateArtists(
	discovery: AurralDiscoverySummary,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralDiscoverArtist> =
	mergeAurralDiscoverArtists(
		discovery.recommendations +
			discovery.recentReleases.mapNotNull { it.toDiscoverArtistRecommendation() } +
			discovery.globalTop +
			discovery.basedOn
	).withLibraryArtistMonitoring(
		discovery.libraryArtists.withCachedArtistPhotos(
			entries = artistPhotoCacheEntries,
			artistArtworkPriority = artistArtworkPriority,
			externalArtworkEnabled = externalArtworkEnabled
		)
	).withCachedArtistPhotos(
		entries = artistPhotoCacheEntries,
		artistArtworkPriority = artistArtworkPriority,
		externalArtworkEnabled = externalArtworkEnabled
	)

fun aurralDiscoverTopTags(
	discovery: AurralDiscoverySummary,
	limit: Int = Int.MAX_VALUE
): List<String> =
	discovery.topTags
		.cleanedAurralDisplayStrings()
		.take(limit.coerceAtLeast(0))

private fun AurralDiscoverArtist.matchesAurralTag(normalizedTag: String): Boolean =
	(matchedTags.ifEmpty { tags })
		.mapNotNull { it.normalizedAurralName() }
		.any { tag -> tag == normalizedTag || tag.contains(normalizedTag) || normalizedTag.contains(tag) }

private fun List<String>.cleanedAurralDisplayStrings(): List<String> =
	mapNotNull { it.trim().takeIf(String::isNotEmpty) }
		.distinctBy { it.lowercase() }
fun aurralDiscoverArtistDetail(artist: AurralDiscoverArtist): String {
	val tags = artist.matchedTags.ifEmpty { artist.tags }.take(3)
	return listOfNotNull(
		artist.reason,
		tags.takeIf { it.isNotEmpty() }?.joinToString(", ")
	).joinToString(" • ").takeIf { it.isNotEmpty() }
		?: artist.discoveryTier
		?: artist.sourceType
		?: ""
}
