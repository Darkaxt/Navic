package paige.navic.domain.models

import androidx.compose.runtime.Immutable
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_sort_album_count
import navic.composeapp.generated.resources.option_sort_alphabetical_by_name
import navic.composeapp.generated.resources.option_sort_song_count
import org.jetbrains.compose.resources.StringResource

@Immutable
enum class DomainGenreListType(val displayName: StringResource) {
	AlbumCount(Res.string.option_sort_album_count),
	SongCount(Res.string.option_sort_song_count),
	AlphabeticalByName(Res.string.option_sort_alphabetical_by_name)
}
