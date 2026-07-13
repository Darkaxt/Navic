package paige.navic.reader

import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.domain.repositories.BinderySourceReleaseMetadata
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
	val bookFileId: String? = null,
	val href: String,
	val title: String,
	val displayTitle: String,
	val format: String? = null,
	val artifactType: String? = null,
	val deliveryPolicy: String? = null,
	val origin: String? = null,
	val version: String? = null,
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
	val sourceReleaseLabel: String? = null,
	val sourceUrl: String? = null,
	val findingId: String? = null,
	val findingHref: String? = null,
	val overlayResourceHref: String? = null
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
	val bookFileId: String? = null,
	val qualityLabel: String? = null,
	val sourceProviderLabel: String? = null,
	val sourceReleaseLabel: String? = null,
	val sourceUrl: String? = null,
	val format: String? = null,
	val artifactType: String? = null,
	val deliveryPolicy: String? = null,
	val origin: String? = null,
	val version: String? = null,
	val findingId: String? = null,
	val findingHref: String? = null,
	val codec: String? = null,
	val bitrateKbps: Int? = null,
	val sampleRateHz: Long? = null,
	val channels: Int? = null,
	val durationMs: Long? = null,
	val overlayResourceHref: String? = null
)

data class ReadaloudMediaExtras(
	val resourceKey: String?,
	val bookFileId: String?,
	val href: String,
	val title: String,
	val chapterLabel: String?,
	val sectionLabel: String?,
	val format: String?,
	val artifactType: String?,
	val deliveryPolicy: String?,
	val origin: String?,
	val version: String?,
	val narrator: String?,
	val author: String?,
	val trackNumber: Int?,
	val discNumber: Int?,
	val durationMs: Long?,
	val codec: String?,
	val bitrateKbps: Int?,
	val sampleRateHz: Long?,
	val channels: Int?,
	val qualityLabel: String?,
	val sourceProvider: String?,
	val sourceRelease: String?,
	val sourceUrl: String?,
	val findingId: String?,
	val findingHref: String?
)

data class ReadaloudPlaybackMetadataLabels(
	val chapterLabel: String? = null,
	val sectionLabel: String? = null,
	val narratorLabel: String? = null,
	val qualityLabel: String? = null,
	val sourceProviderLabel: String? = null,
	val sourceReleaseLabel: String? = null,
	val sourceUrlLabel: String? = null,
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
		bookFileId = bookFileId,
		qualityLabel = qualityLabel,
		sourceProviderLabel = sourceProviderLabel,
		sourceReleaseLabel = sourceReleaseLabel,
		sourceUrl = sourceUrl,
		format = format,
		artifactType = artifactType,
		deliveryPolicy = deliveryPolicy,
		origin = origin,
		version = version,
		findingId = findingId,
		findingHref = findingHref,
		overlayResourceHref = overlayResourceHref,
		codec = codec,
		bitrateKbps = bitrateKbps,
		sampleRateHz = sampleRateHz,
		channels = channels,
		durationMs = durationMs
	)

fun ReadaloudMediaItemDescriptor.toReadaloudMediaExtras(): ReadaloudMediaExtras =
	ReadaloudMediaExtras(
		resourceKey = resourceKey,
		bookFileId = bookFileId,
		href = uri,
		title = title,
		chapterLabel = title,
		sectionLabel = subtitle,
		format = format,
		artifactType = artifactType,
		deliveryPolicy = deliveryPolicy,
		origin = origin,
		version = version,
		narrator = artist,
		author = albumArtist,
		trackNumber = trackNumber,
		discNumber = discNumber,
		durationMs = durationMs,
		codec = codec,
		bitrateKbps = bitrateKbps,
		sampleRateHz = sampleRateHz,
		channels = channels,
		qualityLabel = qualityLabel,
		sourceProvider = sourceProviderLabel,
		sourceRelease = sourceReleaseLabel,
		sourceUrl = sourceUrl,
		findingId = findingId,
		findingHref = findingHref
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

fun ReadaloudMediaItemDescriptor.toReadaloudPlaybackMetadataLabels(): ReadaloudPlaybackMetadataLabels =
	ReadaloudPlaybackMetadataLabels(
		chapterLabel = title.trimLabel(),
		sectionLabel = subtitle.trimLabel(),
		narratorLabel = artist.trimLabel(),
		qualityLabel = qualityLabel.trimLabel(),
		sourceProviderLabel = sourceProviderLabel.trimLabel(),
		sourceReleaseLabel = sourceReleaseLabel.trimLabel(),
		sourceUrlLabel = sourceUrl.trimLabel(),
		formatLabel = audioFormatLabel()
	)

private fun ReadaloudMediaItemDescriptor.audioFormatLabel(): String? =
	listOfNotNull(
		codec.trimLabel(),
		bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" },
		sampleRateHz?.audioSampleRateLabel(),
		channels?.audioChannelLayoutLabel()
	).joinToString(separator = " / ").takeIf { it.isNotBlank() }

private fun Long.audioSampleRateLabel(): String? {
	if (this <= 0L) return null
	if (this < 1_000L) return "$this Hz"
	val tenthsOfKhz = (toDouble() / 100.0).roundToLong()
	val whole = tenthsOfKhz / 10
	val decimal = tenthsOfKhz % 10
	return if (decimal == 0L) "$whole kHz" else "$whole.$decimal kHz"
}

private fun Int.audioChannelLayoutLabel(): String? =
	when {
		this <= 0 -> null
		this == 1 -> "mono"
		this == 2 -> "stereo"
		else -> "$this ch"
	}

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
				item.bookFileId?.let { append(" bookFileId=").append(it) }
				item.sourceProviderLabel?.let { append(" provider=").append(it) }
				item.sourceReleaseLabel?.let { append(" release=").append(it) }
				item.sourceUrl?.let { append(" sourceUrl=").append(it) }
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
		bookFileId = metadata.bookFileId,
		href = href,
		title = title,
		displayTitle = displayTitle,
		format = metadata.format,
		artifactType = metadata.artifactType,
		deliveryPolicy = metadata.deliveryPolicy,
		origin = metadata.origin,
		version = metadata.version,
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
		sourceReleaseLabel = sourceRelease.sourceReleaseLabel(metadata.editionSuffix ?: metadata.version),
		sourceUrl = sourceRelease?.sourceUrl ?: metadata.sourceUrl,
		findingId = metadata.findingId,
		findingHref = metadata.findingHref
	)
}

private fun BinderySourceReleaseMetadata?.sourceReleaseLabel(editionSuffix: String?): String? =
	listOfNotNull(
		this?.edition.trimLabel() ?: this?.editionType.trimLabel() ?: editionSuffix.trimLabel(),
		this?.format.trimLabel()
	)
		.distinct()
		.joinToString(separator = " / ")
		.takeIf { it.isNotBlank() }
