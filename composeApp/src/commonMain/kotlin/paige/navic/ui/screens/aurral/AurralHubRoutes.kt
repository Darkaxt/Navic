package paige.navic.ui.screens.aurral

import paige.navic.domain.models.DomainArtist
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.ui.navigation.Screen

fun aurralDiscoverCollectionRoute(row: AurralDiscoveryCollectionRow): Screen? =
	when (row) {
		is AurralDiscoveryCollectionRow.Artists ->
			if (row.kind == AurralDiscoveryCollectionKind.GenreArtists) {
				row.tag?.trim()?.takeIf { it.isNotEmpty() }?.let(Screen::AurralDiscoverTag)
			} else {
				Screen.AurralDiscoverCollection(row.kind.name)
			}

		is AurralDiscoveryCollectionRow.Albums -> null
		is AurralDiscoveryCollectionRow.Tags -> null
	}

fun aurralArtistRoute(artist: AurralDiscoverArtist): Screen.AurralArtist? {
	val artistMbid = artist.id.trim().takeIf { it.isNotEmpty() } ?: return null
	val artistName = artist.name.trim().takeIf { it.isNotEmpty() } ?: return null
	return Screen.AurralArtist(
		artistMbid = artistMbid,
		artistName = artistName,
		imageUrl = artist.imageUrl.externalAurralArtworkUrlOrNull()
	)
}

fun aurralArtistHeroCoverArtId(
	localArtist: DomainArtist?,
	externalArtworkEnabled: Boolean
): String? =
	if (externalArtworkEnabled) {
		null
	} else {
		localArtist?.coverArtId?.trim()?.takeIf { it.isNotEmpty() }
	}

fun aurralArtistRecommendationRoute(
	artist: AurralDiscoverArtist,
	localArtists: List<DomainArtist>
): Screen? {
	val artistKey = artist.id.normalizedAurralKey()
	val nameKey = artist.name.normalizedAurralName()
	val localArtist = localArtists.firstOrNull { local ->
		val localMbidKey = local.musicBrainzId.normalizedAurralKey()
		val localNameKey = local.name.normalizedAurralName()
		(artistKey != null && localMbidKey != null && artistKey == localMbidKey) ||
			(nameKey != null && localNameKey != null && nameKey == localNameKey)
	}
	return localArtist?.let { Screen.ArtistDetail(it.id) } ?: aurralArtistRoute(artist)
}

fun aurralAlbumSearchRoute(album: AurralAlbumSearchItem): Screen.AurralMissingAlbum? {
	val releaseGroupId = album.id.trim().takeIf { it.isNotEmpty() } ?: return null
	val title = album.title.trim().takeIf { it.isNotEmpty() } ?: return null
	val artistMbid = album.artistMbid.trim().takeIf { it.isNotEmpty() } ?: return null
	val artistName = album.artistName.trim().takeIf { it.isNotEmpty() } ?: return null
	return Screen.AurralMissingAlbum(
		artistId = artistMbid,
		artistName = artistName,
		artistMbid = artistMbid,
		releaseGroupId = releaseGroupId,
		title = title,
		year = album.releaseDate.aurralSearchYearOrNull()?.toString(),
		primaryType = album.primaryType?.trim()?.takeIf { it.isNotEmpty() }
			?: album.secondaryTypes.firstOrNull(),
		coverUrl = album.coverUrl?.trim()?.takeIf { it.isNotEmpty() },
		requestStatus = album.status?.trim()?.takeIf { it.isNotEmpty() }
	)
}

fun aurralAlbumSearchDestination(album: AurralAlbumSearchItem): Screen? {
	val libraryAlbumId = album.libraryAlbumId?.trim()?.takeIf { it.isNotEmpty() }
	if (album.inLibrary && libraryAlbumId != null) {
		return Screen.CollectionDetail(libraryAlbumId, "search")
	}
	return aurralAlbumSearchRoute(album)
}
