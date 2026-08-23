package paige.navic.reader

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyReadingProgressKind
import kotlin.math.abs
import kotlin.time.Instant

private const val ReaderReadingProgressMaxEntries = 200
private const val ReaderProgressStartThreshold = 0.005
private const val ReaderProgressNamedStartThreshold = 0.03
private const val ReaderProgressAdvanceThreshold = 0.01
private const val ReaderProgressConflictThreshold = 0.01
private const val ReaderProgressComparisonEpsilon = 0.000000001

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
	val progressResourceHref = canonicalReaderResourceHref(this.progressResourceHref())
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
		val progress = startProgressFor(
			bookId = bookId,
			resourceHref = resourceHref,
			kind = kind
		) ?: return null
		return progress.toReaderStartLocatorFor(
			resourceHref = resourceHref,
			kind = kind
		) ?: progress.toReaderProgressOnlyStartLocatorFor(bookId = bookId, kind = kind)
	}

	fun startProgressFor(
		bookId: String,
		resourceHref: String,
		kind: ReaderPublicationKind
	): BinderyReadingProgress? {
		progressFor(bookId = bookId, resourceHref = resourceHref, kind = kind)
			?.takeIf { progress ->
				progress.toReaderStartLocatorFor(resourceHref = resourceHref, kind = kind) != null
			}
			?.let { return it }

		return progresses.firstOrNull { progress ->
			progress.toReaderProgressOnlyStartLocatorFor(bookId = bookId, kind = kind) != null
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

enum class ReaderStartLocatorSource {
	Remote,
	Local
}

enum class ReaderStartLocatorSelectionPolicy {
	OnlyCandidate,
	NewerTimestamp,
	EqualTimestampRemote,
	LegacyMissingTimestamp
}

data class ReaderStartLocatorCandidate(
	val source: ReaderStartLocatorSource,
	val locator: ReaderLocator,
	val updatedAt: String? = null
)

data class ReaderStartLocatorConflict(
	val remoteCandidate: ReaderStartLocatorCandidate,
	val localCandidate: ReaderStartLocatorCandidate,
	val selectedSource: ReaderStartLocatorSource,
	val policy: ReaderStartLocatorSelectionPolicy
)

data class ReaderStartLocatorDecision(
	val selectedLocator: ReaderLocator? = null,
	val selectedSource: ReaderStartLocatorSource? = null,
	val policy: ReaderStartLocatorSelectionPolicy? = null,
	val conflict: ReaderStartLocatorConflict? = null
)

fun resolveReaderStartLocator(
	remoteCandidate: ReaderStartLocatorCandidate?,
	localCandidate: ReaderStartLocatorCandidate?
): ReaderStartLocatorDecision {
	if (remoteCandidate == null && localCandidate == null) return ReaderStartLocatorDecision()
	if (remoteCandidate == null) return localCandidate.toOnlyReaderStartLocatorDecision()
	if (localCandidate == null) return remoteCandidate.toOnlyReaderStartLocatorDecision()

	val remoteTimestamp = remoteCandidate.updatedAt.readerProgressTimestampMillis()
	val localTimestamp = localCandidate.updatedAt.readerProgressTimestampMillis()
	val selection = when {
		remoteTimestamp != null && localTimestamp != null && remoteTimestamp > localTimestamp ->
			remoteCandidate to ReaderStartLocatorSelectionPolicy.NewerTimestamp
		remoteTimestamp != null && localTimestamp != null && localTimestamp > remoteTimestamp ->
			localCandidate to ReaderStartLocatorSelectionPolicy.NewerTimestamp
		remoteTimestamp != null && localTimestamp != null ->
			remoteCandidate to ReaderStartLocatorSelectionPolicy.EqualTimestampRemote
		else -> legacyReaderStartLocatorCandidate(remoteCandidate, localCandidate) to
			ReaderStartLocatorSelectionPolicy.LegacyMissingTimestamp
	}
	val (selectedCandidate, policy) = selection
	val conflict = ReaderStartLocatorConflict(
		remoteCandidate = remoteCandidate,
		localCandidate = localCandidate,
		selectedSource = selectedCandidate.source,
		policy = policy
	).takeIf {
		val remoteProgress = remoteCandidate.locator.safeProgress()
		val localProgress = localCandidate.locator.safeProgress()
		remoteProgress != null && localProgress != null &&
			abs(remoteProgress - localProgress) >
			ReaderProgressConflictThreshold + ReaderProgressComparisonEpsilon
	}
	return ReaderStartLocatorDecision(
		selectedLocator = selectedCandidate.locator,
		selectedSource = selectedCandidate.source,
		policy = policy,
		conflict = conflict
	)
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

private fun ReaderStartLocatorCandidate?.toOnlyReaderStartLocatorDecision(): ReaderStartLocatorDecision =
	ReaderStartLocatorDecision(
		selectedLocator = this?.locator,
		selectedSource = this?.source,
		policy = ReaderStartLocatorSelectionPolicy.OnlyCandidate
	)

private fun legacyReaderStartLocatorCandidate(
	remoteCandidate: ReaderStartLocatorCandidate,
	localCandidate: ReaderStartLocatorCandidate
): ReaderStartLocatorCandidate =
	if (
		bestReaderStartLocator(
			remoteStartLocator = remoteCandidate.locator,
			localStartLocator = localCandidate.locator
		) == localCandidate.locator
	) {
		localCandidate
	} else {
		remoteCandidate
	}

private fun String?.readerProgressTimestampMillis(): Long? {
	val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	value.toLongOrNull()?.let { return it }
	return runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrNull()
}

data class ReaderProgressSaveGate(
	val publicationReady: Boolean = false,
	val firstPostReadyRelocationHandled: Boolean = false,
	val readableLocationSaved: Boolean = false
) {
	fun reset(): ReaderProgressSaveGate = copy(
		publicationReady = false,
		firstPostReadyRelocationHandled = false,
		readableLocationSaved = false
	)

	fun onReaderEvent(event: ReaderBridgeEvent): ReaderProgressSaveDecision =
		when (event) {
			ReaderBridgeEvent.PublicationReady -> ReaderProgressSaveDecision(
				state = copy(
					publicationReady = true,
					firstPostReadyRelocationHandled = false,
					readableLocationSaved = false
				)
			)
			is ReaderBridgeEvent.LocationChanged -> {
				val shouldSkipStartupPlaceholder = publicationReady &&
					event.locator.isReaderStartPlaceholder()
				ReaderProgressSaveDecision(
					state = if (publicationReady) {
						copy(
							firstPostReadyRelocationHandled = true,
							readableLocationSaved = readableLocationSaved || !shouldSkipStartupPlaceholder
						)
					} else {
						this
					},
					locatorToSave = event.locator.takeIf { publicationReady && !shouldSkipStartupPlaceholder }
				)
			}
			else -> ReaderProgressSaveDecision(state = this)
		}

	fun onEngineEvent(event: ReaderEngineEvent): ReaderProgressSaveDecision =
		when (event) {
			ReaderEngineEvent.PublicationReady -> onReaderEvent(ReaderBridgeEvent.PublicationReady)
			is ReaderEngineEvent.Relocated -> onReaderEvent(
				ReaderBridgeEvent.LocationChanged(
					locator = event.locator,
					foliateSessionId = event.foliateSessionId,
					tocTitle = event.tocTitle,
					pageTurnSettleToken = event.pageTurnSettleToken,
					pageTurnSettleSessionId = event.pageTurnSettleSessionId,
					pageTurnSettleRasterGeneration = event.pageTurnSettleRasterGeneration,
					pageTurnSettleTextureGeneration = event.pageTurnSettleTextureGeneration,
					causalSequence = event.causalSequence,
					destinationCommitIdentity = event.destinationCommitIdentity
				)
			)
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
		resourceKey = safeResourceHref.toBinderyResourceKey(),
		href = safeResourceHref,
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
		resourceHref = progressResourceHref(),
		kind = kind
	)

private fun BinderyReadingProgress.progressResourceHref(): String? =
	resourceHref?.trim()?.takeIf { it.isNotEmpty() }
		?: href?.trim()?.takeIf { it.isNotEmpty() }
		?: progressResourceKeyHref()

private fun BinderyReadingProgress.progressResourceKeyHref(): String? {
	val safeResourceKey = resourceKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	if (safeResourceKey.startsWith("/") || safeResourceKey.contains("://")) {
		return safeResourceKey
	}
	val safeBookId = bookId.trim().takeIf { it.isNotEmpty() } ?: return safeResourceKey
	return "/opds/books/$safeBookId/resources/$safeResourceKey"
}

private fun String.toBinderyResourceKey(): String? {
	val resourceTail = substringAfter("/resources/", missingDelimiterValue = "")
		.trim('/')
		.takeIf { it.isNotEmpty() }
	if (resourceTail != null) return resourceTail
	return substringAfterLast("/")
		.trim()
		.takeIf { it.isNotEmpty() }
}

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
	if (!cfi.isNullOrBlank()) return false
	val normalizedHref = href?.trim()?.lowercase()
	val progress = safeProgress()
	val progressIsStart = progress?.let { it <= ReaderProgressStartThreshold } ?: true
	val progressIsNamedStart = progress?.let { it <= ReaderProgressNamedStartThreshold } ?: true
	val hrefLooksLikeStart =
		normalizedHref?.contains("cover") == true ||
			normalizedHref?.contains("titlepage") == true ||
			normalizedHref?.endsWith("/nav.xhtml") == true ||
			normalizedHref?.endsWith("nav.xhtml") == true
	return normalizedHref.isNullOrBlank() && progressIsStart ||
		hrefLooksLikeStart && progressIsNamedStart
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
