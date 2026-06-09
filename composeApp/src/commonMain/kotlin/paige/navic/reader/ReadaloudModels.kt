package paige.navic.reader

import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.util.core.AppLogLevel
import paige.navic.util.core.LoggerEvent
import kotlin.math.roundToLong

data class ReadaloudAudioSession(
	val id: String?,
	val title: String,
	val author: String? = null,
	val narrator: String? = null,
	val kind: ReaderPublicationKind,
	val tracks: List<ReadaloudAudioTrack>
)

data class ReadaloudAudioTrack(
	val id: String,
	val resourceKey: String? = null,
	val href: String,
	val title: String,
	val displayTitle: String,
	val sectionLabel: String? = null,
	val trackNumber: Int? = null,
	val discNumber: Int? = null,
	val narrator: String? = null,
	val author: String? = null,
	val durationMs: Long? = null,
	val codec: String? = null,
	val bitrateKbps: Int? = null,
	val sampleRateHz: Long? = null,
	val channels: Int? = null,
	val qualityLabel: String? = null,
	val sourceProviderLabel: String? = null,
	val sourceUrl: String? = null
) {
	val subtitleLabel: String?
		get() = listOfNotNull(
			narrator,
			qualityLabel,
			sourceProviderLabel
		).joinToString(separator = " / ").takeIf { it.isNotBlank() }
}

data class ReadaloudMediaItemDescriptor(
	val mediaId: String,
	val uri: String,
	val title: String,
	val subtitle: String?,
	val artist: String?,
	val albumTitle: String,
	val albumArtist: String?,
	val trackNumber: Int?,
	val discNumber: Int?,
	val requestHeaders: Map<String, String>,
	val resourceKey: String? = null,
	val qualityLabel: String? = null,
	val sourceProviderLabel: String? = null,
	val codec: String? = null,
	val bitrateKbps: Int? = null,
	val sampleRateHz: Long? = null,
	val channels: Int? = null,
	val durationMs: Long? = null
)

data class ReadaloudPlaybackMetadataLabels(
	val chapterLabel: String? = null,
	val sectionLabel: String? = null,
	val narratorLabel: String? = null,
	val qualityLabel: String? = null,
	val sourceProviderLabel: String? = null,
	val formatLabel: String? = null
)

data class ReadaloudPlaybackPlan(
	val sessionId: String?,
	val title: String,
	val kind: ReaderPublicationKind,
	val mediaItems: List<ReadaloudMediaItemDescriptor>,
	val startTrackIndex: Int,
	val startPositionMs: Long,
	val playbackSpeed: Float
)

data class ReadaloudPlaybackPosition(
	val sessionId: String?,
	val trackIndex: Int,
	val mediaId: String?,
	val positionMs: Long,
	val durationMs: Long?,
	val isPlaying: Boolean,
	val playbackSpeed: Float
)

fun ReadaloudAudioTrack.toReadaloudMediaItemDescriptor(
	sessionTitle: String,
	sessionAuthor: String?,
	sessionNarrator: String?,
	requestHeaders: Map<String, String> = emptyMap()
): ReadaloudMediaItemDescriptor =
	ReadaloudMediaItemDescriptor(
		mediaId = "readaloud:${resourceKey ?: id}",
		uri = href,
		title = displayTitle,
		subtitle = sectionLabel ?: subtitleLabel,
		artist = narrator ?: sessionNarrator ?: author ?: sessionAuthor,
		albumTitle = sessionTitle,
		albumArtist = sessionAuthor,
		trackNumber = trackNumber,
		discNumber = discNumber,
		requestHeaders = requestHeaders,
		resourceKey = resourceKey ?: id,
		qualityLabel = qualityLabel,
		sourceProviderLabel = sourceProviderLabel,
		codec = codec,
		bitrateKbps = bitrateKbps,
		sampleRateHz = sampleRateHz,
		channels = channels,
		durationMs = durationMs
	)

fun ReadaloudAudioSession.toReadaloudPlaybackPlan(
	requestHeaders: Map<String, String> = emptyMap(),
	startTrackIndex: Int = 0,
	startPositionMs: Long = 0L,
	playbackSpeed: Float = 1f
): ReadaloudPlaybackPlan {
	val clampedTrackIndex = if (tracks.isEmpty()) {
		0
	} else {
		startTrackIndex.coerceIn(0, tracks.lastIndex)
	}
	val activeDurationMs = tracks.getOrNull(clampedTrackIndex)?.durationMs
	val clampedPositionMs = startPositionMs.coerceAtLeast(0L)
		.let { position ->
			activeDurationMs?.takeIf { it > 0L }?.let(position::coerceAtMost) ?: position
		}
	return ReadaloudPlaybackPlan(
		sessionId = id,
		title = title,
		kind = kind,
		mediaItems = tracks.map { track ->
			track.toReadaloudMediaItemDescriptor(
				sessionTitle = title,
				sessionAuthor = author,
				sessionNarrator = narrator,
				requestHeaders = requestHeaders
			)
		},
		startTrackIndex = clampedTrackIndex,
		startPositionMs = clampedPositionMs,
		playbackSpeed = normalizedReadaloudPlaybackSpeed(playbackSpeed)
	)
}

fun normalizedReadaloudPlaybackSpeed(value: Float): Float =
	if (value.isNaN() || value.isInfinite()) {
		1f
	} else {
		value.coerceIn(0.5f, 3f)
	}

fun ReadaloudPlaybackPlan.metadataLabelsForPlaybackPosition(
	position: ReadaloudPlaybackPosition
): ReadaloudPlaybackMetadataLabels? =
	mediaItemFor(position)?.toReadaloudPlaybackMetadataLabels()

private fun ReadaloudPlaybackPlan.mediaItemFor(
	position: ReadaloudPlaybackPosition
): ReadaloudMediaItemDescriptor? =
	mediaItems.getOrNull(position.trackIndex)
		?: position.mediaId?.let { mediaId -> mediaItems.firstOrNull { item -> item.mediaId == mediaId } }

private fun ReadaloudMediaItemDescriptor.toReadaloudPlaybackMetadataLabels(): ReadaloudPlaybackMetadataLabels =
	ReadaloudPlaybackMetadataLabels(
		chapterLabel = title.trimLabel(),
		sectionLabel = subtitle.trimLabel(),
		narratorLabel = artist.trimLabel(),
		qualityLabel = qualityLabel.trimLabel(),
		sourceProviderLabel = sourceProviderLabel.trimLabel(),
		formatLabel = audioFormatLabel()
	)

private fun ReadaloudMediaItemDescriptor.audioFormatLabel(): String? =
	listOfNotNull(
		codec.trimLabel(),
		bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" }
	).joinToString(separator = " / ").takeIf { it.isNotBlank() }

private fun String?.trimLabel(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

fun ReadaloudPlaybackPlan.toReadaloudPlaybackLoadedEvent(): LoggerEvent {
	val firstItem = mediaItems.firstOrNull()
	return LoggerEvent(
		level = AppLogLevel.Info,
		tag = ReadaloudPlaybackLogTag,
		message = buildString {
			append("Loaded readaloud playback plan")
			sessionId?.let { append(" session=").append(it) }
			append(" title=").append(title)
			append(" kind=").append(kind)
			append(" items=").append(mediaItems.size)
			append(" startTrack=").append(startTrackIndex)
			append(" startPositionMs=").append(startPositionMs)
			append(" speed=").append(playbackSpeed)
			firstItem?.let { item ->
				append(" firstMediaId=").append(item.mediaId)
				item.resourceKey?.let { append(" firstResource=").append(it) }
				item.sourceProviderLabel?.let { append(" provider=").append(it) }
				item.codec?.let { append(" codec=").append(it) }
				item.bitrateKbps?.let { append(" bitrateKbps=").append(it) }
			}
		}
	)
}

const val ReadaloudPlaybackLogTag = "ReadaloudPlayback"

fun readaloudAudioSessionFromBindery(
	manifest: BinderyManifest,
	readingOrder: List<BinderyReadingOrderItem>,
	kind: ReaderPublicationKind = ReaderPublicationKind.Readaloud
): ReadaloudAudioSession {
	val tracks = readingOrder
		.filter { item -> item.type?.startsWith("audio/", ignoreCase = true) == true }
		.mapIndexed { index, item -> item.toReadaloudAudioTrack(index) }
	return ReadaloudAudioSession(
		id = manifest.id,
		title = manifest.title,
		author = manifest.author,
		narrator = tracks.firstNotNullOfOrNull(ReadaloudAudioTrack::narrator),
		kind = kind,
		tracks = tracks
	)
}

private fun BinderyReadingOrderItem.toReadaloudAudioTrack(index: Int): ReadaloudAudioTrack {
	val metadata = metadata
	val audio = metadata.audio
	val sourceRelease = metadata.sourceRelease
	val resourceKey = metadata.resourceKey
	val durationMs = metadata.durationMs
		?: durationSeconds?.takeIf { it > 0.0 }?.let { (it * 1000.0).roundToLong() }
	val displayTitle = metadata.chapterLabel
		?: metadata.sectionLabel
		?: title
	return ReadaloudAudioTrack(
		id = resourceKey ?: href.ifBlank { "track-$index" },
		resourceKey = resourceKey,
		href = href,
		title = title,
		displayTitle = displayTitle,
		sectionLabel = metadata.sectionLabel,
		trackNumber = metadata.trackNumber,
		discNumber = metadata.discNumber,
		narrator = metadata.narrator ?: sourceRelease?.narrator ?: sourceRelease?.readBy,
		author = metadata.author,
		durationMs = durationMs,
		codec = audio?.codec,
		bitrateKbps = audio?.bitrateKbps,
		sampleRateHz = audio?.sampleRateHz,
		channels = audio?.channels,
		qualityLabel = audio?.qualityLabel,
		sourceProviderLabel = sourceRelease?.provider ?: metadata.sourceProvider,
		sourceUrl = sourceRelease?.sourceUrl
	)
}
