package paige.navic.ui.screens.bindery

import kotlin.math.roundToLong
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.readaloudAudioSessionFromBindery
import paige.navic.reader.toReadaloudPlaybackPlan

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
	playbackSpeed: Float = 1f
): ReadaloudPlaybackPlan {
	val absoluteReadingOrder = selectedBinderyAudiobookReadingOrder(manifest, versionRowId)
		.map { item -> item.copy(href = binderyEndpoint(opdsBaseUrl, item.href)) }
	return readaloudAudioSessionFromBindery(
		manifest = manifest,
		readingOrder = absoluteReadingOrder,
		kind = ReaderPublicationKind.Readaloud
	).toReadaloudPlaybackPlan(
		requestHeaders = requestHeaders,
		playbackSpeed = playbackSpeed
	)
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

private fun Map<String, String>.firstNonBlankValue(vararg keys: String): String? =
	keys.firstNotNullOfOrNull { key ->
		entries.firstOrNull { (entryKey, value) ->
			entryKey.equals(key, ignoreCase = true) && value.isNotBlank()
		}?.value
	}
