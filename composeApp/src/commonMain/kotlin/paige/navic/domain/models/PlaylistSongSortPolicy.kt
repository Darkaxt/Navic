package paige.navic.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_sort_playlist_song_album
import navic.composeapp.generated.resources.option_sort_playlist_song_artist
import navic.composeapp.generated.resources.option_sort_playlist_song_manual
import navic.composeapp.generated.resources.option_sort_playlist_song_title
import navic.composeapp.generated.resources.option_sort_playlist_duration
import org.jetbrains.compose.resources.StringResource

@Immutable
enum class DomainPlaylistSongSortType(val displayName: StringResource) {
	ManualOrder(Res.string.option_sort_playlist_song_manual),
	Title(Res.string.option_sort_playlist_song_title),
	Artist(Res.string.option_sort_playlist_song_artist),
	Album(Res.string.option_sort_playlist_song_album),
	Duration(Res.string.option_sort_playlist_duration)
}

fun playlistSongSortOptions(): ImmutableList<DomainPlaylistSongSortType> =
	persistentListOf(
		DomainPlaylistSongSortType.ManualOrder,
		DomainPlaylistSongSortType.Title,
		DomainPlaylistSongSortType.Artist,
		DomainPlaylistSongSortType.Album,
		DomainPlaylistSongSortType.Duration
	)

fun List<DomainSong>.sortedForPlaylistDetail(
	sortType: DomainPlaylistSongSortType,
	reversed: Boolean
): List<DomainSong> {
	val sorted = when (sortType) {
		DomainPlaylistSongSortType.ManualOrder -> this
		DomainPlaylistSongSortType.Title -> sortedWith(compareBy(
			{ it.title.sortKey() },
			{ it.artistName.sortKey() },
			{ it.albumTitle.sortKey() },
			{ it.id }
		))
		DomainPlaylistSongSortType.Artist -> sortedWith(compareBy(
			{ it.artistName.sortKey() },
			{ it.albumTitle.sortKey() },
			{ it.discNumber ?: Int.MAX_VALUE },
			{ it.trackNumber ?: Int.MAX_VALUE },
			{ it.title.sortKey() },
			{ it.id }
		))
		DomainPlaylistSongSortType.Album -> sortedWith(compareBy(
			{ it.albumTitle.sortKey() },
			{ it.discNumber ?: Int.MAX_VALUE },
			{ it.trackNumber ?: Int.MAX_VALUE },
			{ it.title.sortKey() },
			{ it.id }
		))
		DomainPlaylistSongSortType.Duration -> sortedWith(compareBy(
			{ it.duration },
			{ it.title.sortKey() },
			{ it.id }
		))
	}
	return if (reversed) sorted.reversed() else sorted
}

private fun String?.sortKey(): String = orEmpty().lowercase()
