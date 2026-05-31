package paige.navic.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class PlaybackOriginType {
	Artist,
	Genre,
	Album,
	Playlist,
	Station
}

@Immutable
@Serializable
data class PlaybackOrigin(
	val type: PlaybackOriginType,
	val id: String,
	val title: String,
	val subtitle: String? = null,
	val coverArtId: String? = null
) {
	init {
		require(id.isNotBlank()) { "Playback origin id must not be blank" }
	}

	val key: String = "${type.name}:$id"
}

fun DomainPlaylist.toPlaybackOriginType(): PlaybackOriginType =
	if (isStationPlaylist()) PlaybackOriginType.Station else PlaybackOriginType.Playlist

fun DomainPlaylist.toPlaybackOrigin(): PlaybackOrigin =
	PlaybackOrigin(
		type = toPlaybackOriginType(),
		id = id,
		title = stationDisplayName(),
		subtitle = owner,
		coverArtId = coverArtId
	)

fun DomainAlbum.toPlaybackOrigin(): PlaybackOrigin =
	PlaybackOrigin(
		type = PlaybackOriginType.Album,
		id = id,
		title = name,
		subtitle = artistName,
		coverArtId = coverArtId
	)

fun DomainArtist.toPlaybackOrigin(): PlaybackOrigin =
	PlaybackOrigin(
		type = PlaybackOriginType.Artist,
		id = id,
		title = name,
		coverArtId = artistImageUrl ?: coverArtId
	)

fun DomainGenre.toPlaybackOrigin(): PlaybackOrigin =
	PlaybackOrigin(
		type = PlaybackOriginType.Genre,
		id = name,
		title = name,
		subtitle = "$albumCount albums"
	)

fun DomainSongCollection.toPlaybackOrigin(): PlaybackOrigin? =
	when (this) {
		is DomainAlbum -> toPlaybackOrigin()
		is DomainGenreCollection -> PlaybackOrigin(
			type = PlaybackOriginType.Genre,
			id = id,
			title = name,
			coverArtId = coverArtId
		)
		is DomainPlaylist -> toPlaybackOrigin()
	}
