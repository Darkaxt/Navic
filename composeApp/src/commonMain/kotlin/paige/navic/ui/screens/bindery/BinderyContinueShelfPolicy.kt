package paige.navic.ui.screens.bindery

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import paige.navic.domain.repositories.BinderyAudiobookVersion
import paige.navic.domain.repositories.BinderyBookSync
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.reader.canonicalReaderResourceHref
import paige.navic.reader.WhispersyncAudioSeekTarget
import paige.navic.ui.navigation.Screen
import kotlin.math.roundToInt

private const val ContinueShelfLimit = 12
private const val MaxStoredWhispersyncCompanionProgressEntries = 80
private const val ContinueListeningSquareAspectRatio = 1f
private const val ContinueListeningPortraitAspectRatio = 2f / 3f
private const val ContinueListeningPortraitHeightThreshold = 1.2f

private val BinderyWhispersyncCompanionProgressJson = Json {
	ignoreUnknownKeys = true
	encodeDefaults = true
}

@Serializable
data class BinderyWhispersyncCompanionProgress(
	val bookId: String,
	val ebookResourceHref: String,
	val audiobookId: String,
	val audiobookBookFileId: String,
	val artifactId: String,
	val progressFraction: Double,
	val audioResource: String? = null,
	val audioPositionMs: Long? = null,
	val audioTrackIndex: Int? = null,
	val updatedAtMs: Long
)

@Serializable
private data class BinderyWhispersyncCompanionProgressStore(
	val entries: List<BinderyWhispersyncCompanionProgress> = emptyList()
)

data class BinderyContinueListeningItem(
	val key: String,
	val bookId: String,
	val audiobookId: String,
	val title: String,
	val subtitle: String?,
	val imageHref: String?,
	val updatedAtMs: Long,
	val destination: Screen.BinderyAudiobookPlayer
)

data class BinderyContinueReadingItem(
	val key: String,
	val bookId: String,
	val resourceHref: String,
	val title: String,
	val subtitle: String?,
	val imageHref: String?,
	val updatedAtMs: Long,
	val row: BinderyBookVersionRow,
	val ebookDestination: Screen.Reader
)

sealed interface BinderyContinueReadingLaunchDecision {
	data class OpenEbook(
		val destination: Screen.Reader
	) : BinderyContinueReadingLaunchDecision

	data class AskWhispersync(
		val ebookDestination: Screen.Reader,
		val ebookRow: BinderyBookVersionRow,
		val matches: List<BinderyWhispersyncMatch>
	) : BinderyContinueReadingLaunchDecision
}

fun binderyContinueListeningCoverAspectRatio(
	width: Int?,
	height: Int?
): Float {
	val safeWidth = width?.takeIf { it > 0 } ?: return ContinueListeningSquareAspectRatio
	val safeHeight = height?.takeIf { it > 0 } ?: return ContinueListeningSquareAspectRatio
	return if (safeHeight.toFloat() / safeWidth.toFloat() >= ContinueListeningPortraitHeightThreshold) {
		ContinueListeningPortraitAspectRatio
	} else {
		ContinueListeningSquareAspectRatio
	}
}

fun binderyContinueListeningItems(
	progresses: List<BinderyAudiobookPlaybackProgress>,
	companionProgresses: List<BinderyWhispersyncCompanionProgress> = emptyList(),
	manifestsByBookId: Map<String, BinderyManifest>,
	audiobookDetailsById: Map<String, BinderyAudiobookVersion>,
	maxItems: Int = ContinueShelfLimit
): List<BinderyContinueListeningItem> {
	val realKeys = progresses
		.map { progress -> progress.bookId to progress.versionRowId }
		.toSet()
	val playbackItems = progresses
		.filter { progress -> progress.positionMs > 0L }
		.sortedByDescending(BinderyAudiobookPlaybackProgress::updatedAtMs)
		.mapNotNull { progress ->
			val audiobookId = progress.versionRowId.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val bookId = progress.bookId.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val detail = audiobookDetailsById[audiobookId] ?: return@mapNotNull null
			val manifest = manifestsByBookId[bookId]
			val title = detail.title
				?.trim()
				?.takeIf { it.isNotEmpty() }
				?: manifest?.title?.trim()?.takeIf { it.isNotEmpty() }
				?: "Audiobook"
			val subtitle = listOfNotNull(
				detail.narrator?.trim()?.takeIf { it.isNotEmpty() },
				progress.listenProgressLabel()
			).joinToString(separator = " / ").takeIf { it.isNotBlank() }
			BinderyContinueListeningItem(
				key = "continue-listening:$bookId:$audiobookId",
				bookId = bookId,
				audiobookId = audiobookId,
				title = title,
				subtitle = subtitle,
				imageHref = detail.coverUrl?.trim()?.takeIf { it.isNotEmpty() }
					?: manifest.firstImageHref(),
				updatedAtMs = progress.updatedAtMs,
				destination = Screen.BinderyAudiobookPlayer(
					bookId = bookId,
					title = title,
					audiobookId = audiobookId
				)
			)
		}
	val companionItems = companionProgresses
		.filter { progress -> progress.progressFraction.isFinite() }
		.filter { progress -> progress.progressFraction > 0.0 }
		.filterNot { progress -> progress.bookId to progress.audiobookId in realKeys }
		.sortedByDescending(BinderyWhispersyncCompanionProgress::updatedAtMs)
		.mapNotNull { progress ->
			val audiobookId = progress.audiobookId.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val bookId = progress.bookId.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val detail = audiobookDetailsById[audiobookId] ?: return@mapNotNull null
			val manifest = manifestsByBookId[bookId]
			val title = detail.title
				?.trim()
				?.takeIf { it.isNotEmpty() }
				?: manifest?.title?.trim()?.takeIf { it.isNotEmpty() }
				?: "Audiobook"
			val subtitle = listOfNotNull(
				detail.narrator?.trim()?.takeIf { it.isNotEmpty() },
				"Whispersync",
				progress.progressPercentLabel()
			).joinToString(separator = " / ").takeIf { it.isNotBlank() }
			BinderyContinueListeningItem(
				key = "continue-listening:$bookId:$audiobookId",
				bookId = bookId,
				audiobookId = audiobookId,
				title = title,
				subtitle = subtitle,
				imageHref = detail.coverUrl?.trim()?.takeIf { it.isNotEmpty() }
					?: manifest.firstImageHref(),
				updatedAtMs = progress.updatedAtMs,
				destination = Screen.BinderyAudiobookPlayer(
					bookId = bookId,
					title = title,
					audiobookId = audiobookId
				)
			)
		}
	return (playbackItems + companionItems)
		.sortedByDescending(BinderyContinueListeningItem::updatedAtMs)
		.take(maxItems.coerceAtLeast(1))
}

fun binderyContinueReadingItems(
	progresses: List<BinderyReadingProgress>,
	manifestsByBookId: Map<String, BinderyManifest>,
	resourcesByBookId: Map<String, BinderyResourceCatalog>,
	audiobookVersionsByBookId: Map<String, List<BinderyAudiobookVersion>>,
	syncByBookId: Map<String, BinderyBookSync>,
	languageFilter: String?,
	opdsBaseUrl: String,
	maxItems: Int = ContinueShelfLimit
): List<BinderyContinueReadingItem> =
	progresses
		.filter { progress -> progress.kind == BinderyReadingProgressKind.Ebook }
		.filter { progress -> (progress.progressFraction ?: 0.0) > 0.0 }
		.mapIndexedNotNull { index, progress ->
			val bookId = progress.bookId.trim().takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
			val resourceHref = progress.readerResourceHref() ?: return@mapIndexedNotNull null
			val manifest = manifestsByBookId[bookId] ?: return@mapIndexedNotNull null
			val rows = binderyBookVersionRows(
				manifest = manifest,
				resourceCatalog = resourcesByBookId[bookId],
				languageFilter = languageFilter,
				bookId = bookId,
				audiobookVersions = audiobookVersionsByBookId[bookId].orEmpty(),
				bookSync = syncByBookId[bookId]
			)
			val row = rows.firstOrNull { version ->
				version.kind == BinderyBookVersionKind.Ebook &&
					canonicalReaderResourceHref(version.id) == resourceHref
			} ?: return@mapIndexedNotNull null
			val destination = binderyReaderDestinationForVersionRow(
				row = row,
				bookId = bookId,
				bookTitle = manifest.title,
				opdsBaseUrl = opdsBaseUrl
			) ?: return@mapIndexedNotNull null
			BinderyContinueReadingItem(
				key = "continue-reading:$bookId:$resourceHref",
				bookId = bookId,
				resourceHref = resourceHref,
				title = manifest.title,
				subtitle = listOfNotNull(
					row.title.trim().takeIf { it.isNotEmpty() && it != manifest.title },
					progress.progressPercentLabel()
				).joinToString(separator = " / ").takeIf { it.isNotBlank() },
				imageHref = manifest.firstImageHref(),
				updatedAtMs = progress.updatedAtSortValue(index),
				row = row,
				ebookDestination = destination
			)
		}
		.sortedByDescending(BinderyContinueReadingItem::updatedAtMs)
		.take(maxItems.coerceAtLeast(1))

fun binderyContinueReadingLaunchDecision(
	item: BinderyContinueReadingItem,
	opdsBaseUrl: String? = null,
	fullscreenCoverTargetAspectRatio: Double? = null
): BinderyContinueReadingLaunchDecision {
	val matches = item.row.whispersyncAudiobookLaunchMatches()
	val ebookDestination = item.aspectAwareEbookDestination(
		opdsBaseUrl = opdsBaseUrl,
		fullscreenCoverTargetAspectRatio = fullscreenCoverTargetAspectRatio
	)
	return if (matches.isEmpty()) {
		BinderyContinueReadingLaunchDecision.OpenEbook(ebookDestination)
	} else {
		BinderyContinueReadingLaunchDecision.AskWhispersync(
			ebookDestination = ebookDestination,
			ebookRow = item.row,
			matches = matches
		)
	}
}

fun binderyContinueReadingWhispersyncDestination(
	decision: BinderyContinueReadingLaunchDecision.AskWhispersync,
	match: BinderyWhispersyncMatch,
	opdsBaseUrl: String,
	fullscreenCoverTargetAspectRatio: Double? = null
): Screen.Reader? =
	binderyWhispersyncReaderDestinationForMatch(
		ebookRow = decision.ebookRow,
		match = match,
		bookId = decision.ebookDestination.bookId,
		bookTitle = decision.ebookDestination.title,
		opdsBaseUrl = opdsBaseUrl,
		fullscreenCoverTargetAspectRatio = fullscreenCoverTargetAspectRatio
	)

private fun BinderyContinueReadingItem.aspectAwareEbookDestination(
	opdsBaseUrl: String?,
	fullscreenCoverTargetAspectRatio: Double?
): Screen.Reader {
	val baseUrl = opdsBaseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return ebookDestination
	return binderyReaderDestinationForVersionRow(
		row = row,
		bookId = bookId,
		bookTitle = title,
		opdsBaseUrl = baseUrl,
		fullscreenCoverTargetAspectRatio = fullscreenCoverTargetAspectRatio
	) ?: ebookDestination
}

fun binderyWhispersyncCompanionProgressEntries(json: String): List<BinderyWhispersyncCompanionProgress> =
	decodeBinderyWhispersyncCompanionProgressStore(json).entries

fun binderyWhispersyncCompanionProgressJsonWithUpdate(
	json: String,
	progress: BinderyWhispersyncCompanionProgress,
	maxEntries: Int = MaxStoredWhispersyncCompanionProgressEntries
): String {
	val normalized = progress.copy(
		bookId = progress.bookId.trim(),
		ebookResourceHref = canonicalReaderResourceHref(progress.ebookResourceHref).orEmpty(),
		audiobookId = progress.audiobookId.trim(),
		audiobookBookFileId = progress.audiobookBookFileId.trim(),
		artifactId = progress.artifactId.trim(),
		progressFraction = progress.progressFraction.coerceIn(0.0, 1.0),
		audioResource = progress.audioResource?.trim()?.takeIf { it.isNotEmpty() },
		audioPositionMs = progress.audioPositionMs?.coerceAtLeast(0L),
		audioTrackIndex = progress.audioTrackIndex?.takeIf { it >= 0 },
		updatedAtMs = progress.updatedAtMs.coerceAtLeast(0L)
	)
	if (
		normalized.bookId.isBlank() ||
		normalized.ebookResourceHref.isBlank() ||
		normalized.audiobookId.isBlank() ||
		normalized.audiobookBookFileId.isBlank() ||
		normalized.artifactId.isBlank() ||
		!normalized.progressFraction.isFinite()
	) {
		return json
	}
	val existingEntries = decodeBinderyWhispersyncCompanionProgressStore(json).entries
	val matchingExisting = existingEntries.firstOrNull { entry ->
		entry.bookId == normalized.bookId &&
			entry.ebookResourceHref == normalized.ebookResourceHref &&
			entry.audiobookId == normalized.audiobookId
	}
	val nextProgress = if (
		normalized.audioPositionMs == null &&
		matchingExisting?.audioPositionMs != null
	) {
		matchingExisting
	} else {
		normalized
	}
	val entries = existingEntries
		.filterNot { entry ->
			entry.bookId == normalized.bookId &&
				entry.ebookResourceHref == normalized.ebookResourceHref &&
				entry.audiobookId == normalized.audiobookId
		}
		.plus(nextProgress)
		.sortedByDescending(BinderyWhispersyncCompanionProgress::updatedAtMs)
		.take(maxEntries.coerceAtLeast(1))
	return BinderyWhispersyncCompanionProgressJson.encodeToString(
		BinderyWhispersyncCompanionProgressStore(entries = entries)
	)
}

fun binderyWhispersyncCompanionProgressForReader(
	reader: Screen.Reader,
	progress: BinderyReadingProgress,
	updatedAtMs: Long,
	audioSeekTarget: WhispersyncAudioSeekTarget? = null
): BinderyWhispersyncCompanionProgress? {
	if (progress.kind != BinderyReadingProgressKind.Ebook) return null
	val progressResourceHref = progress.readerResourceHref() ?: return null
	val readerResourceHref = canonicalReaderResourceHref(reader.resourceHref) ?: return null
	if (progress.bookId.trim() != reader.bookId.trim() || progressResourceHref != readerResourceHref) return null
	val fraction = progress.progressFraction
		?.takeIf(Double::isFinite)
		?.coerceIn(0.0, 1.0)
		?: return null
	val audiobookId = reader.whispersyncAudiobookId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val audiobookBookFileId = reader.whispersyncAudiobookBookFileId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val artifactId = reader.whispersyncArtifactId?.trim()?.takeIf { it.isNotEmpty() }
		?: reader.whispersyncSidecarUrl?.derivedWhispersyncArtifactId()
		?: return null
	return BinderyWhispersyncCompanionProgress(
		bookId = reader.bookId.trim(),
		ebookResourceHref = readerResourceHref,
		audiobookId = audiobookId,
		audiobookBookFileId = audiobookBookFileId,
		artifactId = artifactId,
		progressFraction = fraction,
		audioResource = audioSeekTarget?.audioResource
			?.trim()
			?.takeIf { it.isNotEmpty() },
		audioPositionMs = audioSeekTarget?.positionMs?.coerceAtLeast(0L),
		audioTrackIndex = audioSeekTarget?.segment
			?.audioTrackIndex
			?.takeIf { it >= 0 },
		updatedAtMs = updatedAtMs.coerceAtLeast(0L)
	)
}

private fun BinderyReadingProgress.readerResourceHref(): String? =
	canonicalReaderResourceHref(resourceHref)
		?: canonicalReaderResourceHref(href)
		?: canonicalReaderResourceHref(resourceKeyHref())

private fun BinderyReadingProgress.resourceKeyHref(): String? {
	val safeResourceKey = resourceKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	if (safeResourceKey.startsWith("/") || safeResourceKey.contains("://")) {
		return safeResourceKey
	}
	val safeBookId = bookId.trim().takeIf { it.isNotEmpty() } ?: return safeResourceKey
	return "/opds/books/$safeBookId/resources/$safeResourceKey"
}

private fun String.derivedWhispersyncArtifactId(): String? {
	val path = substringBefore('#')
		.substringBefore('?')
		.trim()
		.trimEnd('/')
	return path.substringAfterLast('/', missingDelimiterValue = path)
		.trim()
		.takeIf { it.isNotEmpty() }
}

private fun decodeBinderyWhispersyncCompanionProgressStore(
	json: String
): BinderyWhispersyncCompanionProgressStore =
	if (json.isBlank()) {
		BinderyWhispersyncCompanionProgressStore()
	} else {
		runCatching {
			BinderyWhispersyncCompanionProgressJson
				.decodeFromString<BinderyWhispersyncCompanionProgressStore>(json)
		}.getOrDefault(BinderyWhispersyncCompanionProgressStore())
	}

private fun BinderyAudiobookPlaybackProgress.listenProgressLabel(): String =
	listOfNotNull(
		"Track ${trackIndex.coerceAtLeast(0) + 1}",
		positionMs.takeIf { it > 0L }?.let(::shortDurationLabel)
	).joinToString(separator = " / ").takeIf { it.isNotBlank() } ?: "In progress"

private fun BinderyReadingProgress.progressPercentLabel(): String? =
	progressFraction
		?.takeIf(Double::isFinite)
		?.coerceIn(0.0, 1.0)
		?.let { fraction -> "${(fraction * 100.0).roundToInt()}%" }

private fun BinderyWhispersyncCompanionProgress.progressPercentLabel(): String =
	"${(progressFraction.coerceIn(0.0, 1.0) * 100.0).roundToInt()}%"

private fun BinderyReadingProgress.updatedAtSortValue(index: Int): Long =
	updatedAt?.trim()?.toLongOrNull() ?: (Long.MAX_VALUE - index)

private fun BinderyManifest?.firstImageHref(): String? =
	this?.images?.firstNotNullOfOrNull { link ->
		link.href.trim().takeIf { it.isNotEmpty() }
	} ?: this?.properties?.firstNonBlankValue("image", "cover")

private fun Map<String, String>.firstNonBlankValue(vararg keys: String): String? =
	keys.firstNotNullOfOrNull { desiredKey ->
		entries.firstOrNull { (key, value) ->
			key.equals(desiredKey, ignoreCase = true) && value.isNotBlank()
		}?.value?.trim()
	}

private fun shortDurationLabel(durationMs: Long): String {
	val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
	val hours = totalSeconds / 3600L
	val minutes = (totalSeconds % 3600L) / 60L
	val seconds = totalSeconds % 60L
	return buildString {
		if (hours > 0L) append("${hours}h")
		if (minutes > 0L || hours > 0L) {
			if (isNotEmpty()) append(' ')
			append("${minutes}m")
		}
		if (hours == 0L && minutes == 0L) append("${seconds}s")
	}
}
