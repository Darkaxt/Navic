package paige.navic.ui.screens.collection

import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.repositories.AurralAlbumSearchItem

fun aurralAlbumRecoveryCandidate(
	album: DomainAlbum,
	candidates: List<AurralAlbumSearchItem>
): AurralAlbumSearchItem? {
	val titleKey = album.name.normalizedAurralAlbumRecoveryKey()
	if (titleKey == null) return null
	val artistCreditKey = album.artistName.normalizedAurralAlbumRecoveryKey()
	return candidates
		.mapNotNull { candidate ->
			val candidateTitleKey = candidate.title.normalizedAurralAlbumRecoveryKey()
			if (candidateTitleKey != titleKey) return@mapNotNull null
			val candidateArtistKey = candidate.artistName.normalizedAurralAlbumRecoveryKey()
			val score = when {
				artistCreditKey != null && candidateArtistKey != null && artistCreditKey == candidateArtistKey -> 30
				artistCreditKey != null && candidateArtistKey != null && artistCreditKey.contains(candidateArtistKey) -> 20
				artistCreditKey != null && candidateArtistKey != null && candidateArtistKey.contains(artistCreditKey) -> 10
				else -> 0
			}
			candidate to score
		}
		.maxWithOrNull(
			compareBy<Pair<AurralAlbumSearchItem, Int>> { it.second }
				.thenByDescending { it.first.coverUrl?.isNotBlank() == true }
				.thenByDescending { it.first.releaseDate.orEmpty() }
		)
		?.first
}

private fun String?.normalizedAurralAlbumRecoveryKey(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""[^\p{L}\p{N}]+"""), " ")
		?.replace(Regex("""\s+"""), " ")
		?.trim()
		?.takeIf { it.isNotEmpty() }
