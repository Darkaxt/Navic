package paige.navic.domain.models

import androidx.compose.runtime.Immutable

@Immutable
data class DomainGenreSummary(
	val name: String,
	val albumCount: Int,
	val songCount: Int,
	val coverArtIds: List<String>
)

data class GenreAlbumSummaryInput(
	val albumId: String,
	val genre: String?,
	val genres: List<String>,
	val coverArtId: String,
	val songCount: Int
)
