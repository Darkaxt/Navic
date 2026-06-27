package paige.navic.ui.screens.artist

import paige.navic.domain.models.AurralSimilarArtistRow
import paige.navic.domain.models.DomainArtist
import paige.navic.ui.navigation.Screen
import paige.navic.ui.navigation.SearchScope

fun aurralExternalArtistRoute(row: AurralSimilarArtistRow): Screen.AurralArtist? {
	if (row.localArtistId != null) return null
	val artistMbid = row.artist.id.trim().takeIf { it.isNotEmpty() } ?: return null
	val artistName = row.artist.name.trim().takeIf { it.isNotEmpty() } ?: return null
	return Screen.AurralArtist(
		artistMbid = artistMbid,
		artistName = artistName,
		imageUrl = row.artist.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
	)
}

fun artistListDestination(artist: DomainArtist): Screen {
	if (isAurralNameLookupArtistId(artist.id)) {
		return Screen.AurralArtist(
			artistMbid = artist.id.trim(),
			artistName = artist.name.trim().takeIf { it.isNotEmpty() } ?: artist.id,
			imageUrl = artist.artistImageUrl?.trim()?.takeIf { it.isNotEmpty() }
		)
	}
	return Screen.ArtistDetail(artist.id)
}

fun albumArtistCreditRoute(
	artistId: String?,
	artistName: String?,
	localArtists: List<DomainArtist>,
	aurralEnabled: Boolean
): Screen? {
	val name = artistName.normalizedArtistDisplayName() ?: return null
	return if (name.isCompoundArtistCredit()) {
		Screen.Search(
			nested = true,
			scope = SearchScope.Music,
			initialQuery = name
		)
	} else {
		artistCreditRoute(
			artistId = artistId,
			artistName = name,
			localArtists = localArtists,
			aurralEnabled = aurralEnabled
		)
	}
}

fun artistCreditRoute(
	artistId: String?,
	artistName: String?,
	localArtists: List<DomainArtist>,
	aurralEnabled: Boolean
): Screen? {
	val id = artistId?.trim()?.takeIf { it.isNotEmpty() }
	val name = artistName.normalizedArtistDisplayName()
	val localArtist = localArtists.firstOrNull { artist -> id != null && artist.id == id }
		?: localArtists.firstOrNull { artist ->
			name != null && artist.name.normalizedArtistDisplayName()
				.equals(name, ignoreCase = true)
		}
	if (localArtist != null) return Screen.ArtistDetail(localArtist.id)
	if (!aurralEnabled || name == null) return null
	return Screen.AurralArtist(
		artistMbid = aurralNameLookupArtistId(name),
		artistName = name
	)
}

fun isAurralNameLookupArtistId(value: String?): Boolean =
	value?.trim()?.startsWith(AURRAL_NAME_LOOKUP_PREFIX) == true

fun aurralNameLookupArtistId(artistName: String): String =
	AURRAL_NAME_LOOKUP_PREFIX + artistName
		.trim()
		.lowercase()
		.replace(Regex("""[^\p{L}\p{N}]+"""), "-")
		.trim('-')
		.takeIf { it.isNotEmpty() }
		.orEmpty()

private const val AURRAL_NAME_LOOKUP_PREFIX = "name:"

private fun String?.normalizedArtistDisplayName(): String? =
	this
		?.trim()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

private fun String.isCompoundArtistCredit(): Boolean =
	Regex("""\s(&|and|feat\.?|featuring|with|x)\s|[,;/]""", RegexOption.IGNORE_CASE)
		.containsMatchIn(this)
