package paige.navic.shared

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainRadio
import paige.navic.domain.models.DomainSong
import kotlin.time.Duration

internal data class AndroidRadioMediaItem(
	val song: DomainSong,
	val mediaItem: MediaItem
)

internal class AndroidRadioMediaItemFactory {
	fun create(radio: DomainRadio): AndroidRadioMediaItem {
		val radioId = "radio_${radio.name.hashCode()}"
		val song = DomainSong(
			id = radioId,
			title = radio.name,
			artistName = "Live Radio",
			albumId = "radio_album",
			albumTitle = "Live Stream",
			duration = Duration.ZERO,
			trackNumber = 1,
			coverArtId = null,
			artistId = "",
			parentId = "",
			comment = null,
			discNumber = null,
			isrc = emptyList(),
			year = null,
			genre = null,
			genres = emptyList(),
			moods = emptyList(),
			bpm = null,
			contributors = emptyList(),
			playCount = 0,
			userRating = 0,
			averageRating = null,
			bitRate = null,
			bitDepth = null,
			sampleRate = null,
			audioChannelCount = null,
			replayGain = null,
			fileSize = 0,
			fileExtension = "",
			mimeType = "",
			filePath = radio.streamUrl,
			starredAt = null,
			musicBrainzId = null,
			explicitStatus = DomainExplicitStatus.Unknown
		)
		val metadata = MediaMetadata.Builder()
			.setTitle(radio.name)
			.setArtist("Live Radio")
			.setIsPlayable(true)
			.build()
		val mediaItem = MediaItem.Builder()
			.setUri(radio.streamUrl)
			.setMediaId(radioId)
			.setMediaMetadata(metadata)
			.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
			.build()

		return AndroidRadioMediaItem(
			song = song,
			mediaItem = mediaItem
		)
	}
}
