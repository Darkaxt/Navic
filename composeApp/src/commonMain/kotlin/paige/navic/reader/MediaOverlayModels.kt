package paige.navic.reader

import kotlin.math.roundToLong

data class MediaOverlayClip(
	val audioResource: String,
	val textResource: String,
	val fragmentId: String?,
	val startSeconds: Double,
	val endSeconds: Double,
	val label: String? = null
) {
	val textHref: String
		get() = if (fragmentId.isNullOrBlank()) textResource else "$textResource#$fragmentId"

	fun toReaderOverlayFragment(): ReaderOverlayFragment =
		ReaderOverlayFragment(
			resourceHref = audioResource,
			fragmentId = fragmentId,
			textHref = textResource,
			clipBeginSeconds = startSeconds,
			clipEndSeconds = endSeconds,
			label = label
		)
}

data class MediaOverlaySeekTarget(
	val audioResource: String,
	val positionMs: Long,
	val clip: MediaOverlayClip
)

data class StorytellerAudioResource(
	val id: String? = null,
	val href: String,
	val mediaType: String? = null,
	val durationMs: Long? = null,
	val label: String? = null,
	val chapterLabel: String? = null,
	val sectionLabel: String? = null,
	val narrator: String? = null,
	val author: String? = null,
	val trackNumber: Int? = null,
	val discNumber: Int? = null,
	val codec: String? = null,
	val bitrateKbps: Int? = null,
	val sampleRateHz: Long? = null,
	val channels: Int? = null,
	val qualityLabel: String? = null,
	val sourceProviderLabel: String? = null,
	val sourceUrl: String? = null
)

data class StorytellerReadaloudPackage(
	val timeline: MediaOverlayTimeline,
	val audioResources: List<StorytellerAudioResource> = emptyList()
)

data class MediaOverlayTimeline(
	val clips: List<MediaOverlayClip>,
	val durationSeconds: Double? = clips.maxOfOrNull(MediaOverlayClip::endSeconds)
) {
	fun activeClip(
		audioResource: String,
		positionMs: Long
	): MediaOverlayClip? {
		val normalizedAudioResource = normalizedMediaOverlayResource(audioResource)
		val positionSeconds = positionMs.coerceAtLeast(0L) / 1000.0
		return clips.firstOrNull { clip ->
			normalizedMediaOverlayResource(clip.audioResource) == normalizedAudioResource &&
				positionSeconds >= clip.startSeconds &&
				positionSeconds < clip.endSeconds
		}
	}

	fun readerCommandForAudioPosition(
		audioResource: String,
		positionMs: Long,
		syncEnabled: Boolean
	): ReaderBridgeCommand? =
		if (!syncEnabled) {
			null
		} else {
			activeClip(audioResource, positionMs)
				?.toReaderOverlayFragment()
				?.let(ReaderBridgeCommand::ApplyOverlayFragment)
		}

	fun seekTargetForText(href: String): MediaOverlaySeekTarget? {
		val (resource, fragment) = href.splitMediaOverlayFragment()
		val normalizedResource = normalizedMediaOverlayResource(resource)
		return clips.firstOrNull { clip ->
			normalizedMediaOverlayResource(clip.textResource) == normalizedResource &&
				(fragment == null || clip.fragmentId == fragment)
		}?.let { clip ->
			MediaOverlaySeekTarget(
				audioResource = clip.audioResource,
				positionMs = (clip.startSeconds * 1000.0).roundToLong(),
				clip = clip
			)
		}
	}

	fun activeLabelForPlaybackPosition(
		plan: ReadaloudPlaybackPlan,
		position: ReadaloudPlaybackPosition
	): String? {
		val audioResource = plan.audioResourceFor(position) ?: return null
		return activeClip(audioResource, position.positionMs)
			?.label
			?.trim()
			?.takeIf { it.isNotEmpty() }
	}
}

fun StorytellerReadaloudPackage.toReadaloudAudioSession(
	id: String?,
	title: String,
	author: String? = null,
	narrator: String? = null,
	audioHrefResolver: (String) -> String = { it }
): ReadaloudAudioSession =
	ReadaloudAudioSession(
		id = id,
		title = title,
		author = author,
		narrator = narrator,
		kind = ReaderPublicationKind.Readaloud,
		tracks = resolvedAudioResources().mapIndexed { index, resource ->
			val resourceKey = resource.id ?: normalizedMediaOverlayResource(resource.href)
			val displayTitle = resource.chapterLabel
				?: resource.label
				?: resource.href.substringAfterLast('/').takeIf { it.isNotBlank() }
				?: resourceKey
			ReadaloudAudioTrack(
				id = resourceKey,
				resourceKey = resourceKey,
				href = audioHrefResolver(resource.href),
				title = displayTitle,
				displayTitle = displayTitle,
				sectionLabel = resource.sectionLabel ?: resource.label,
				trackNumber = resource.trackNumber ?: index + 1,
				discNumber = resource.discNumber,
				narrator = resource.narrator ?: narrator,
				author = resource.author ?: author,
				durationMs = resource.durationMs ?: timeline.durationMsForAudioResource(resource.href),
				codec = resource.codec ?: resource.mediaType?.audioCodecLabel(),
				bitrateKbps = resource.bitrateKbps,
				sampleRateHz = resource.sampleRateHz,
				channels = resource.channels,
				qualityLabel = resource.qualityLabel,
				sourceProviderLabel = resource.sourceProviderLabel,
				sourceUrl = resource.sourceUrl
			)
		}
	)

internal fun normalizedMediaOverlayResource(resource: String): String {
	val raw = resource.trim()
		.substringBefore('#')
		.replace('\\', '/')
		.trimStart('/')
	if (raw.isBlank()) return raw
	val stack = mutableListOf<String>()
	raw.split('/')
		.filter { it.isNotBlank() && it != "." }
		.forEach { segment ->
			if (segment == "..") {
				if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
			} else {
				stack += segment
			}
		}
	return stack.joinToString("/")
}

private fun String.splitMediaOverlayFragment(): Pair<String, String?> {
	val resource = substringBefore('#')
	val fragment = substringAfter('#', "")
		.trim()
		.takeIf { it.isNotEmpty() }
	return resource to fragment
}

private fun StorytellerReadaloudPackage.resolvedAudioResources(): List<StorytellerAudioResource> =
	if (audioResources.isNotEmpty()) {
		audioResources
	} else {
		timeline.clips
			.distinctBy { clip -> normalizedMediaOverlayResource(clip.audioResource) }
			.map { clip ->
				StorytellerAudioResource(
					href = clip.audioResource,
					durationMs = timeline.durationMsForAudioResource(clip.audioResource)
				)
			}
	}

private fun MediaOverlayTimeline.durationMsForAudioResource(audioResource: String): Long? {
	val normalizedAudioResource = normalizedMediaOverlayResource(audioResource)
	return clips
		.filter { clip -> normalizedMediaOverlayResource(clip.audioResource) == normalizedAudioResource }
		.maxOfOrNull(MediaOverlayClip::endSeconds)
		?.let { seconds -> (seconds * 1000.0).roundToLong() }
}

private fun String.audioCodecLabel(): String? =
	trim()
		.takeIf { it.startsWith("audio/", ignoreCase = true) }
		?.substringAfter('/')
		?.trim()
		?.takeIf { it.isNotEmpty() }

private fun ReadaloudPlaybackPlan.audioResourceFor(position: ReadaloudPlaybackPosition): String? =
	mediaItems.getOrNull(position.trackIndex)?.uri
		?: position.mediaId?.let { mediaId -> mediaItems.firstOrNull { item -> item.mediaId == mediaId }?.uri }
