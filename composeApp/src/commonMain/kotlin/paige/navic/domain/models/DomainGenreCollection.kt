package paige.navic.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Immutable
@Serializable
data class DomainGenreCollection(
	override val id: String,
	override val name: String,
	override val coverArtId: String?,
	override val duration: Duration?,
	override val songCount: Int,
	override val songs: List<DomainSong>
) : DomainSongCollection

fun DomainGenre.toSongCollection(): DomainGenreCollection {
	val songs = genrePlayableSongs(this)
	return DomainGenreCollection(
		id = name,
		name = name,
		coverArtId = genreAlbums(this).firstOrNull()?.coverArtId,
		duration = genreTotalDuration(this),
		songCount = songs.size,
		songs = songs
	)
}
