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
	val extras = descriptor.toReadaloudMediaExtras()
	putStringMap("headers", descriptor.requestHeaders)
	putString("resourceKey", extras.resourceKey)
	putString("href", extras.href)
	putString("title", extras.title)
	putString("chapterLabel", extras.chapterLabel)
	putString("sectionLabel", extras.sectionLabel)
	putString("narrator", extras.narrator)
	putString("author", extras.author)
	extras.trackNumber?.let { putInt("trackNumber", it) }
	extras.discNumber?.let { putInt("discNumber", it) }
	extras.durationMs?.let { putLong("durationMs", it) }
	putString("codec", extras.codec)
	extras.bitrateKbps?.let { putInt("bitrateKbps", it) }
	extras.sampleRateHz?.let { putLong("sampleRateHz", it) }
	extras.channels?.let { putInt("channels", it) }
	putString("qualityLabel", extras.qualityLabel)
	putString("sourceProvider", extras.sourceProvider)
	putString("sourceRelease", extras.sourceRelease)
	putString("sourceUrl", extras.sourceUrl)
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
