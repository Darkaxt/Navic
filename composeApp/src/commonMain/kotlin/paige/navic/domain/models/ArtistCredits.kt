package paige.navic.domain.models

/** OpenSubsonic `artists` entries, kept as contributors so artist pages find the song by id. */
const val ContributorRoleArtist = "artist"

/** OpenSubsonic `albumArtists` entries, kept as contributors. */
const val ContributorRoleAlbumArtist = "albumartist"

/** One credited artist from the server's structured arrays: the id navigates, the name labels. */
data class StructuredArtistCredit(val id: String, val name: String)

/**
 * The server's own contributors plus its `artists` / `albumArtists` arrays. The arrays name every
 * credited artist with its id ("ACRAZE" and "Cherish" on "ACRAZE, Cherish"), so a credit is
 * never split by name ("Chase & Status" stays one artist).
 */
fun contributorsWithStructuredArtists(
	contributors: List<DomainContributor>,
	artists: List<StructuredArtistCredit>,
	albumArtists: List<StructuredArtistCredit>
): List<DomainContributor> {
	val result = contributors.toMutableList()
	fun addAll(role: String, credits: List<StructuredArtistCredit>) {
		credits.forEach { credit ->
			val id = credit.id.trim()
			val name = credit.name.trim()
			// The contributor column separates entries with ';' and fields with '^'.
			if (id.isEmpty() || name.isEmpty() || name.any { it == ';' || it == '^' }) return@forEach
			if (result.none { it.role.equals(role, ignoreCase = true) && it.artistId == id }) {
				result += DomainContributor(role = role, subRole = null, artistId = id, artistName = name)
			}
		}
	}
	addAll(ContributorRoleArtist, artists)
	addAll(ContributorRoleAlbumArtist, albumArtists)
	return result
}

private fun List<DomainContributor>.withRole(role: String): List<DomainContributor> =
	filter { it.role.equals(role, ignoreCase = true) && it.artistId.isNotBlank() }
		.distinctBy { it.artistId }

/** The name the server's arrays give [artistId]; null when no song credits that id. */
fun List<DomainSong>.structuredArtistName(artistId: String): String? =
	asSequence()
		.flatMap { song -> song.contributors.asSequence() }
		.firstOrNull { contributor ->
			contributor.artistId == artistId && contributor.artistName.isNotBlank() &&
				(contributor.role.equals(ContributorRoleArtist, ignoreCase = true) ||
					contributor.role.equals(ContributorRoleAlbumArtist, ignoreCase = true))
		}
		?.artistName

/** The song's credited artists from the server's array; empty when the server sent none. */
fun DomainSong.structuredArtists(): List<DomainContributor> =
	contributors.withRole(ContributorRoleArtist)

/**
 * The album's credited album artists from its songs' arrays; empty when the songs carry none
 * (the caller falls back to the display string).
 */
fun DomainAlbum.structuredAlbumArtists(): List<DomainContributor> =
	songs.firstNotNullOfOrNull { song ->
		song.contributors.withRole(ContributorRoleAlbumArtist).takeIf { it.isNotEmpty() }
	}.orEmpty()
