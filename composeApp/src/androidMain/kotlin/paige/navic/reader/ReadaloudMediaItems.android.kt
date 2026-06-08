package paige.navic.reader

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

fun ReadaloudAudioTrack.toReadaloudMediaItem(
	sessionTitle: String,
	sessionAuthor: String?,
	sessionNarrator: String?,
	requestHeaders: Map<String, String> = emptyMap()
): MediaItem {
	val descriptor = toReadaloudMediaItemDescriptor(
		sessionTitle = sessionTitle,
		sessionAuthor = sessionAuthor,
		sessionNarrator = sessionNarrator,
		requestHeaders = requestHeaders
	)
	return descriptor.toReadaloudMediaItem()
}

fun ReadaloudMediaItemDescriptor.toReadaloudMediaItem(): MediaItem {
	val extras = Bundle().apply {
		putReadaloudMediaExtras(this@toReadaloudMediaItem)
	}
	val metadata = MediaMetadata.Builder()
		.setTitle(title)
		.setSubtitle(subtitle)
		.setArtist(artist)
		.setAlbumTitle(albumTitle)
		.setAlbumArtist(albumArtist)
		.setTrackNumber(trackNumber)
		.setDiscNumber(discNumber)
		.setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
		.setExtras(extras)
		.build()
	return MediaItem.Builder()
		.setMediaId(mediaId)
		.setUri(uri.toUri())
		.setMediaMetadata(metadata)
		.setRequestMetadata(
			MediaItem.RequestMetadata.Builder()
				.setExtras(Bundle().apply { putStringMap("headers", requestHeaders) })
				.build()
		)
		.build()
}

private fun Bundle.putReadaloudMediaExtras(descriptor: ReadaloudMediaItemDescriptor) {
	putStringMap("headers", descriptor.requestHeaders)
	putString("resourceKey", descriptor.resourceKey)
	putString("qualityLabel", descriptor.qualityLabel)
	putString("sourceProvider", descriptor.sourceProviderLabel)
	putString("codec", descriptor.codec)
	descriptor.bitrateKbps?.let { putInt("bitrateKbps", it) }
	descriptor.sampleRateHz?.let { putLong("sampleRateHz", it) }
	descriptor.channels?.let { putInt("channels", it) }
	descriptor.durationMs?.let { putLong("durationMs", it) }
}

fun Bundle.putStringMap(key: String, value: Map<String, String>) {
	putBundle(
		key,
		Bundle().apply {
			value.forEach { (mapKey, mapValue) -> putString(mapKey, mapValue) }
		}
	)
}

fun Bundle.getStringMap(key: String): Map<String, String> =
	getBundle(key)
		?.keySet()
		.orEmpty()
		.associateWith { mapKey -> getBundle(key)?.getString(mapKey).orEmpty() }
