package paige.navic.shared

import paige.navic.data.database.entities.ArtistPhotoCacheEntity
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.PlaybackArtistPhotoCacheEntry
import paige.navic.domain.models.PlaybackArtworkResolution
import paige.navic.domain.models.resolvedPlaybackArtistPhoto
import paige.navic.domain.models.resolvedPlaybackArtwork
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.data.remote.aurral.aurralAbsoluteImageUrl

internal class AndroidPlaybackArtworkResolver(
	private val preferenceManager: PreferenceManager,
	private val musicBrainzArtworkRepository: MusicBrainzArtworkRepository
) {
	private var artistPhotoCacheEntries: List<ArtistPhotoCacheEntity> = emptyList()

	fun updateArtistPhotoCache(entries: List<ArtistPhotoCacheEntity>) {
		artistPhotoCacheEntries = entries
	}

	fun resolve(song: DomainSong): PlaybackArtworkResolution {
		val artistPhoto = resolvedPlaybackArtistPhoto(
			artistId = song.artistId,
			artistName = song.artistName,
			entries = artistPhotoCacheEntries.map { entry ->
				entry.toPlaybackArtistPhotoCacheEntry(preferenceManager.aurralBaseUrl)
			}
		)
		val musicBrainzArtwork = musicBrainzArtworkRepository.artworkBySongId.value[song.id]
		val serverCoverLoadFailed = song.id in musicBrainzArtworkRepository.serverCoverLoadFailedSongIds.value
		return resolvedPlaybackArtwork(
			serverCoverArtId = song.coverArtId,
			aurralArtistImageUrl = artistPhoto?.imageUrl,
			aurralArtistCacheKey = artistPhoto?.cacheKey,
			musicBrainzArtworkUrl = musicBrainzArtwork?.imageUrl,
			musicBrainzArtworkCacheKey = musicBrainzArtwork?.sourceMbid?.let { "musicbrainz:$it" },
			artworkSourcePriority = preferenceManager.artistArtworkPriority,
			aurralArtworkEnabled = preferenceManager.aurralEnabled,
			musicBrainzArtworkEnabled = preferenceManager.musicBrainzArtworkFallbackEnabled,
			serverCoverLoadFailed = serverCoverLoadFailed
		)
	}
}

private fun ArtistPhotoCacheEntity.toPlaybackArtistPhotoCacheEntry(
	aurralBaseUrl: String
): PlaybackArtistPhotoCacheEntry =
	PlaybackArtistPhotoCacheEntry(
		cacheKey = cacheKey,
		artistId = artistId,
		sourceArtistId = sourceArtistId,
		name = name,
		normalizedName = normalizedName,
		imageUrl = aurralAbsoluteImageUrl(aurralBaseUrl, imageUrl) ?: imageUrl,
		source = source,
		updatedAtMillis = updatedAtMillis
	)
