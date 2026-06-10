package paige.navic.ui.screens.bindery

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReadaloudMediaItemDescriptor
import paige.navic.reader.ReadaloudPlaybackPosition
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.readaloudAudioSessionFromBindery
import paige.navic.reader.toReadaloudPlaybackPlan

private const val FinishedFinalTrackToleranceMs = 1_000L
private const val AutosavePositionDeltaMs = 2_000L
private const val MaxStoredAudiobookProgressEntries = 80

private val BinderyAudiobookProgressJson = Json {
	ignoreUnknownKeys = true
	encodeDefaults = true
}

enum class BinderyAudiobookTransportControl {
	SeekBackward30,
	SeekBackward10,
	PlayPause,
	SeekForward10,
	SeekForward30
}

data class BinderyAudiobookChapter(
	val index: Int,
	val href: String,
	val title: String,
	val durationMs: Long?,
	val subtitle: String? = null
)

@Serializable
data class BinderyAudiobookPlaybackProgress(
	val bookId: String,
	val versionRowId: String,
	val trackIndex: Int,
	val mediaId: String? = null,
	val positionMs: Long,
	val durationMs: Long? = null,
	val updatedAtMs: Long
)

private data class BinderyAudiobookStartPosition(
	val trackIndex: Int,
	val positionMs: Long
)

@Serializable
private data class BinderyAudiobookPlaybackProgressStore(
	val entries: List<BinderyAudiobookPlaybackProgress> = emptyList()
)

fun binderyAudiobookTransportControls(): List<BinderyAudiobookTransportControl> =
	listOf(
		BinderyAudiobookTransportControl.SeekBackward30,
		BinderyAudiobookTransportControl.SeekBackward10,
		BinderyAudiobookTransportControl.PlayPause,
		BinderyAudiobookTransportControl.SeekForward10,
		BinderyAudiobookTransportControl.SeekForward30
	)

fun binderyAudiobookChapters(
	manifest: BinderyManifest,
	versionRowId: String
): List<BinderyAudiobookChapter> =
	selectedBinderyAudiobookReadingOrder(manifest, versionRowId)
		.mapIndexed { index, item ->
			BinderyAudiobookChapter(
				index = index,
				href = item.href,
				title = item.audiobookChapterTitle(index),
				durationMs = item.durationMs(),
				subtitle = item.audiobookChapterSubtitle()
			)
		}

fun binderyAudiobookPlaybackPlan(
	manifest: BinderyManifest,
	versionRowId: String,
	opdsBaseUrl: String,
	requestHeaders: Map<String, String>,
	playbackSpeed: Float = 1f,
	resumeProgress: BinderyAudiobookPlaybackProgress? = null
): ReadaloudPlaybackPlan {
	val absoluteReadingOrder = selectedBinderyAudiobookReadingOrder(manifest, versionRowId)
		.map { item -> item.copy(href = binderyEndpoint(opdsBaseUrl, item.href)) }
	val basePlan = readaloudAudioSessionFromBindery(
		manifest = manifest,
		readingOrder = absoluteReadingOrder,
		kind = ReaderPublicationKind.Readaloud
	).toReadaloudPlaybackPlan(
		requestHeaders = requestHeaders,
		playbackSpeed = playbackSpeed
	)
	val start = resumeProgress
		?.takeIf { progress -> progress.bookId == manifest.id && progress.versionRowId == versionRowId }
		?.let { progress -> binderyAudiobookStartPosition(progress, basePlan.mediaItems) }
	return start?.let { target ->
		basePlan.copy(
			startTrackIndex = target.trackIndex,
			startPositionMs = target.positionMs
		)
	} ?: basePlan
}

fun selectedBinderyAudiobookReadingOrder(
	manifest: BinderyManifest,
	versionRowId: String
): List<BinderyReadingOrderItem> {
	val audioItems = manifest.readingOrder.filter(BinderyReadingOrderItem::isAudiobookAudio)
	val selectedBookFileId = versionRowId.selectedAudiobookBookFileId()
	val selectedItems = selectedBookFileId?.let { bookFileId ->
		audioItems.filter { item -> item.bookFileId().equals(bookFileId, ignoreCase = true) }
	}.orEmpty()
	return selectedItems.ifEmpty { audioItems }
}

fun binderyAudiobookSavedProgress(
	json: String,
	bookId: String,
	versionRowId: String
): BinderyAudiobookPlaybackProgress? =
	decodeBinderyAudiobookProgressStore(json)
		.entries
		.filter { progress -> progress.bookId == bookId && progress.versionRowId == versionRowId }
		.maxByOrNull(BinderyAudiobookPlaybackProgress::updatedAtMs)

fun binderyAudiobookProgressJsonWithUpdate(
	json: String,
	progress: BinderyAudiobookPlaybackProgress,
	maxEntries: Int = MaxStoredAudiobookProgressEntries
): String {
	val normalized = progress.copy(
		trackIndex = progress.trackIndex.coerceAtLeast(0),
		mediaId = progress.mediaId?.trim()?.takeIf { it.isNotEmpty() },
		positionMs = progress.positionMs.coerceAtLeast(0L),
		durationMs = progress.durationMs?.takeIf { it > 0L },
		updatedAtMs = progress.updatedAtMs.coerceAtLeast(0L)
	)
	val entries = decodeBinderyAudiobookProgressStore(json)
		.entries
		.filterNot { entry -> entry.bookId == normalized.bookId && entry.versionRowId == normalized.versionRowId }
		.plus(normalized)
		.sortedByDescending(BinderyAudiobookPlaybackProgress::updatedAtMs)
		.take(maxEntries.coerceAtLeast(1))
	return BinderyAudiobookProgressJson.encodeToString(
		BinderyAudiobookPlaybackProgressStore(entries = entries)
	)
}

fun binderyAudiobookProgressForPosition(
	bookId: String,
	versionRowId: String,
	position: ReadaloudPlaybackPosition,
	updatedAtMs: Long
): BinderyAudiobookPlaybackProgress? {
	if (bookId.isBlank() || versionRowId.isBlank()) return null
	if (position.sessionId != null && position.sessionId != bookId) return null
	return BinderyAudiobookPlaybackProgress(
		bookId = bookId,
		versionRowId = versionRowId,
		trackIndex = position.trackIndex.coerceAtLeast(0),
		mediaId = position.mediaId?.trim()?.takeIf { it.isNotEmpty() },
		positionMs = position.positionMs.coerceAtLeast(0L),
		durationMs = position.durationMs?.takeIf { it > 0L },
		updatedAtMs = updatedAtMs.coerceAtLeast(0L)
	)
}

fun shouldAutosaveBinderyAudiobookProgress(
	previous: BinderyAudiobookPlaybackProgress?,
	next: BinderyAudiobookPlaybackProgress
): Boolean {
	if (previous == null) return true
	if (previous.bookId != next.bookId || previous.versionRowId != next.versionRowId) return true
	if (previous.trackIndex != next.trackIndex || previous.mediaId != next.mediaId) return true
	if (previous.durationMs != next.durationMs) return true
	return kotlin.math.abs(previous.positionMs - next.positionMs) >= AutosavePositionDeltaMs
}

private fun BinderyReadingOrderItem.isAudiobookAudio(): Boolean =
	type?.startsWith("audio/", ignoreCase = true) == true ||
		properties.firstNonBlankValue("kind", "mediaType", "format")
			?.contains("audio", ignoreCase = true) == true

private fun String.selectedAudiobookBookFileId(): String? =
	removePrefix("audiobook:")
		.trim()
		.takeIf { it.isNotEmpty() && it != this && !it.equals("audiobook", ignoreCase = true) }

private fun BinderyReadingOrderItem.bookFileId(): String? =
	properties.firstNonBlankValue("bookFileId")?.takeIf { it != "0" }

private fun BinderyReadingOrderItem.durationMs(): Long? =
	metadata.durationMs
		?: durationSeconds?.takeIf { it > 0.0 }?.let { (it * 1000.0).roundToLong() }

private fun BinderyReadingOrderItem.audiobookChapterTitle(index: Int): String =
	metadata.chapterLabel
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: metadata.sectionLabel?.trim()?.takeIf { it.isNotEmpty() }
		?: title.trim().takeIf { it.isNotEmpty() }
		?: "Chapter ${index + 1}"

private fun BinderyReadingOrderItem.audiobookChapterSubtitle(): String? =
	listOfNotNull(
		metadata.narrator,
		metadata.audio?.qualityLabel,
		metadata.sourceRelease?.provider ?: metadata.sourceProvider
	)
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.joinToString(separator = " / ")
		.takeIf { it.isNotBlank() }

private fun binderyAudiobookStartPosition(
	progress: BinderyAudiobookPlaybackProgress,
	mediaItems: List<ReadaloudMediaItemDescriptor>
): BinderyAudiobookStartPosition? {
	if (mediaItems.isEmpty()) return null
	val trackIndex = progress.mediaId
		?.let { mediaId -> mediaItems.indexOfFirst { item -> item.mediaId == mediaId } }
		?.takeIf { it >= 0 }
		?: progress.trackIndex.takeIf { it in mediaItems.indices }
		?: return null
	val itemDurationMs = mediaItems[trackIndex].durationMs ?: progress.durationMs
	val positionMs = itemDurationMs
		?.let { duration -> progress.positionMs.coerceIn(0L, duration) }
		?: progress.positionMs.coerceAtLeast(0L)
	if (
		trackIndex == mediaItems.lastIndex &&
		itemDurationMs != null &&
		itemDurationMs - positionMs <= FinishedFinalTrackToleranceMs
	) {
		return BinderyAudiobookStartPosition(trackIndex = 0, positionMs = 0L)
	}
	return BinderyAudiobookStartPosition(
		trackIndex = trackIndex,
		positionMs = positionMs
	)
}

private fun decodeBinderyAudiobookProgressStore(json: String): BinderyAudiobookPlaybackProgressStore =
	if (json.isBlank()) {
		BinderyAudiobookPlaybackProgressStore()
	} else {
		runCatching {
			BinderyAudiobookProgressJson.decodeFromString<BinderyAudiobookPlaybackProgressStore>(json)
		}.getOrDefault(BinderyAudiobookPlaybackProgressStore())
	}

private fun Map<String, String>.firstNonBlankValue(vararg keys: String): String? =
	keys.firstNotNullOfOrNull { key ->
		entries.firstOrNull { (entryKey, value) ->
			entryKey.equals(key, ignoreCase = true) && value.isNotBlank()
		}?.value
	}
