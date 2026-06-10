package paige.navic.reader

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind

private const val ReaderReadingProgressMaxEntries = 200
private const val ReaderProgressStartThreshold = 0.005
private const val ReaderProgressAdvanceThreshold = 0.01

private val ReaderReadingProgressJson = Json {
	ignoreUnknownKeys = true
	encodeDefaults = false
}

fun BinderyReadingProgress.toReaderStartLocator(): ReaderLocator? {
	val safeCfi = cfi?.trim()?.takeIf { it.isNotEmpty() }
	val safeHref = textHref.toReaderHref(fragmentId)
	val safeProgress = progressFraction?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0)
	return ReaderLocator(
		href = safeHref,
		cfi = safeCfi,
		progress = safeProgress
	).takeIf { it.cfi != null || it.href != null || it.progress != null }
}

fun BinderyReadingProgress.toReaderStartLocatorFor(
	resourceHref: String,
	kind: ReaderPublicationKind
): ReaderLocator? {
	val safeResourceHref = canonicalReaderResourceHref(resourceHref) ?: return null
	val progressResourceHref = canonicalReaderResourceHref(this.resourceHref)
	val matchesResource = progressResourceHref == null || progressResourceHref == safeResourceHref
	val matchesKind = this.kind == kind.toBinderyReadingProgressKind()
	return if (matchesResource && matchesKind) {
		toReaderStartLocator()
	} else {
		null
	}
}

fun BinderyReadingProgress.toReaderStartLocatorForReader(
	bookId: String,
	resourceHref: String,
	kind: ReaderPublicationKind
): ReaderLocator? =
	toReaderStartLocatorFor(resourceHref = resourceHref, kind = kind)
		?: toReaderProgressOnlyStartLocatorFor(bookId = bookId, kind = kind)

data class ReaderReadingProgressState(
	val progresses: List<BinderyReadingProgress> = emptyList()
) {
	fun progressFor(
		bookId: String,
		resourceHref: String,
		kind: ReaderPublicationKind
	): BinderyReadingProgress? {
		val key = ReaderReadingProgressKey.from(
			bookId = bookId,
			resourceHref = resourceHref,
			kind = kind.toBinderyReadingProgressKind()
		) ?: return null
		return progresses.firstOrNull { it.readingProgressKey == key }
	}

	fun startLocatorFor(
		bookId: String,
		resourceHref: String,
		kind: ReaderPublicationKind
	): ReaderLocator? {
		progressFor(
			bookId = bookId,
			resourceHref = resourceHref,
			kind = kind
		)?.toReaderStartLocatorFor(
			resourceHref = resourceHref,
			kind = kind
		)?.let { return it }

		return progresses.firstNotNullOfOrNull { progress ->
			progress.toReaderProgressOnlyStartLocatorFor(
				bookId = bookId,
				kind = kind
			)
		}
	}

	fun upsert(progress: BinderyReadingProgress): ReaderReadingProgressState {
		val key = progress.readingProgressKey ?: return this
		return copy(
			progresses = (listOf(progress) + progresses.filterNot { it.readingProgressKey == key })
				.take(ReaderReadingProgressMaxEntries)
		)
	}
}

fun bestReaderStartLocator(
	remoteStartLocator: ReaderLocator?,
	localStartLocator: ReaderLocator?
): ReaderLocator? {
	if (remoteStartLocator == null) return localStartLocator
	if (localStartLocator == null) return remoteStartLocator

	val localProgress = localStartLocator.safeProgress()
	val remoteProgress = remoteStartLocator.safeProgress()
	if (localProgress != null && localProgress > ReaderProgressStartThreshold) {
		if (remoteStartLocator.isReaderStartPlaceholder()) return localStartLocator
		if (
			remoteProgress != null &&
			localProgress > remoteProgress + ReaderProgressAdvanceThreshold
		) {
			return localStartLocator
		}
	}
	return remoteStartLocator
}

data class ReaderProgressSaveGate(
	val publicationReady: Boolean = false,
	val firstPostReadyRelocationHandled: Boolean = false
) {
	fun reset(): ReaderProgressSaveGate = copy(
		publicationReady = false,
		firstPostReadyRelocationHandled = false
	)

	fun onReaderEvent(event: ReaderBridgeEvent): ReaderProgressSaveDecision =
		when (event) {
			ReaderBridgeEvent.PublicationReady -> ReaderProgressSaveDecision(
				state = copy(
					publicationReady = true,
					firstPostReadyRelocationHandled = false
				)
			)
			is ReaderBridgeEvent.LocationChanged -> {
				val shouldSkipStartupPlaceholder = publicationReady &&
					!firstPostReadyRelocationHandled &&
					event.locator.isReaderStartPlaceholder()
				ReaderProgressSaveDecision(
					state = if (publicationReady) {
						copy(firstPostReadyRelocationHandled = true)
					} else {
						this
					},
					locatorToSave = event.locator.takeIf { publicationReady && !shouldSkipStartupPlaceholder }
				)
			}
			else -> ReaderProgressSaveDecision(state = this)
		}
}

data class ReaderProgressSaveDecision(
	val state: ReaderProgressSaveGate,
	val locatorToSave: ReaderLocator? = null
)

fun ReaderLocator.toBinderyReadingProgress(
	bookId: String,
	resourceHref: String,
	kind: ReaderPublicationKind,
	alias: String? = null
): BinderyReadingProgress? {
	val safeBookId = bookId.trim().takeIf { it.isNotEmpty() } ?: return null
	val safeResourceHref = canonicalReaderResourceHref(resourceHref) ?: return null
	val hrefParts = href.splitReaderHref()
	val safeCfi = cfi?.trim()?.takeIf { it.isNotEmpty() }
	val safeProgress = progress?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0)
	return BinderyReadingProgress(
		bookId = safeBookId,
		alias = alias?.trim()?.takeIf { it.isNotEmpty() },
		kind = kind.toBinderyReadingProgressKind(),
		resourceHref = safeResourceHref,
		textHref = hrefParts.textHref,
		cfi = safeCfi,
		fragmentId = hrefParts.fragmentId,
		progressFraction = safeProgress
	).takeIf {
		it.textHref != null ||
			it.fragmentId != null ||
			it.cfi != null ||
			it.progressFraction != null
	}
}

fun encodeReaderReadingProgress(progresses: List<BinderyReadingProgress>): String =
	ReaderReadingProgressJson.encodeToString(progresses.filter { it.readingProgressKey != null })

fun decodeReaderReadingProgress(json: String): List<BinderyReadingProgress> =
	runCatching {
		ReaderReadingProgressJson.decodeFromString<List<BinderyReadingProgress>>(json)
			.filter { it.readingProgressKey != null }
	}.getOrDefault(emptyList())

private data class ReaderHrefParts(
	val textHref: String? = null,
	val fragmentId: String? = null
)

private data class ReaderReadingProgressKey(
	val bookId: String,
	val resourceHref: String,
	val kind: BinderyReadingProgressKind
) {
	companion object {
		fun from(
			bookId: String?,
			resourceHref: String?,
			kind: BinderyReadingProgressKind?
		): ReaderReadingProgressKey? {
			val safeBookId = bookId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
			val safeResourceHref = canonicalReaderResourceHref(resourceHref) ?: return null
			return ReaderReadingProgressKey(
				bookId = safeBookId,
				resourceHref = safeResourceHref,
				kind = kind ?: BinderyReadingProgressKind.Ebook
			)
		}
	}
}

private val BinderyReadingProgress.readingProgressKey: ReaderReadingProgressKey?
	get() = ReaderReadingProgressKey.from(
		bookId = bookId,
		resourceHref = resourceHref,
		kind = kind
	)

private fun String?.toReaderHref(fragmentId: String?): String? {
	val safeTextHref = this?.trim()?.takeIf { it.isNotEmpty() }
	val safeFragmentId = fragmentId?.trim()?.takeIf { it.isNotEmpty() }
	return when {
		safeTextHref != null && safeFragmentId != null && !safeTextHref.contains("#") ->
			"$safeTextHref#$safeFragmentId"
		safeTextHref != null -> safeTextHref
		safeFragmentId != null -> "#$safeFragmentId"
		else -> null
	}
}

private fun String?.splitReaderHref(): ReaderHrefParts {
	val safeHref = this?.trim()?.takeIf { it.isNotEmpty() } ?: return ReaderHrefParts()
	val baseHref = safeHref.substringBefore("#").takeIf { it.isNotEmpty() }
	val fragment = safeHref.substringAfter("#", missingDelimiterValue = "").takeIf { it.isNotEmpty() }
	return ReaderHrefParts(
		textHref = baseHref,
		fragmentId = fragment
	)
}

private fun ReaderPublicationKind.toBinderyReadingProgressKind(): BinderyReadingProgressKind =
	when (this) {
		ReaderPublicationKind.Ebook -> BinderyReadingProgressKind.Ebook
		ReaderPublicationKind.Readaloud -> BinderyReadingProgressKind.Readaloud
	}

private fun BinderyReadingProgress.matchesReaderBookKind(
	bookId: String,
	kind: ReaderPublicationKind
): Boolean {
	val safeBookId = bookId.trim().takeIf { it.isNotEmpty() } ?: return false
	return this.bookId.trim() == safeBookId && this.kind == kind.toBinderyReadingProgressKind()
}

private fun BinderyReadingProgress.toReaderProgressOnlyStartLocatorFor(
	bookId: String,
	kind: ReaderPublicationKind
): ReaderLocator? {
	if (!matchesReaderBookKind(bookId = bookId, kind = kind)) return null
	val safeProgress = progressFraction?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: return null
	return ReaderLocator(progress = safeProgress)
}

private fun ReaderLocator.safeProgress(): Double? =
	progress?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0)

private fun ReaderLocator.isReaderStartPlaceholder(): Boolean {
	val progressIsStart = safeProgress()?.let { it <= ReaderProgressStartThreshold } ?: true
	if (!progressIsStart) return false
	if (!cfi.isNullOrBlank()) return false
	val normalizedHref = href?.trim()?.lowercase()
	return normalizedHref.isNullOrBlank() ||
		normalizedHref.contains("cover") ||
		normalizedHref.contains("titlepage") ||
		normalizedHref.endsWith("/nav.xhtml") ||
		normalizedHref.endsWith("nav.xhtml")
}

fun canonicalReaderResourceHref(value: String?): String? {
	val safeValue = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val withoutFragment = safeValue.substringBefore("#")
	val withoutQuery = withoutFragment.substringBefore("?")
	val path = if (withoutQuery.contains("://")) {
		val afterScheme = withoutQuery.substringAfter("://")
		afterScheme.substringAfter("/", missingDelimiterValue = "")
			.takeIf { it.isNotEmpty() }
			?.let { "/$it" }
			?: withoutQuery
	} else {
		withoutQuery
	}
	return path.trim().trimEnd('/').takeIf { it.isNotEmpty() }
}
