package paige.navic.domain.repositories

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import paige.navic.domain.models.ArtistCreditContext
import paige.navic.domain.models.ArtistCreditResolution
import paige.navic.domain.models.ArtistCreditResolutionReason
import paige.navic.domain.models.artistCreditCleanName
import paige.navic.domain.models.artistCreditIdentityKey
import paige.navic.domain.models.splitArtistCredit
import paige.navic.util.core.Logger
import kotlin.time.Clock

private const val TAG = "ArtistCreditResolution"
private const val ARTIST_CREDIT_CACHE_BASE_URL = "navic:artist-credit"

interface ArtistCreditLookup {
	suspend fun exactArtistName(name: String): String?
	suspend fun albumArtistNames(albumTitle: String?): List<String>
}

class AurralArtistCreditLookup(
	private val aurralRepository: AurralRepository
) : ArtistCreditLookup {
	override suspend fun exactArtistName(name: String): String? {
		val normalizedName = artistCreditIdentityKey(name)
		if (normalizedName.isEmpty()) return null
		return aurralRepository.searchArtists(name, limit = ARTIST_SEARCH_LIMIT)
			.getOrNull()
			?.artists
			.orEmpty()
			.firstOrNull { artist -> artistCreditIdentityKey(artist.name) == normalizedName }
			?.name
			?.artistCreditCleanName()
			?.takeIf { it.isNotEmpty() }
	}

	override suspend fun albumArtistNames(albumTitle: String?): List<String> =
		emptyList()

	private companion object {
		const val ARTIST_SEARCH_LIMIT = 5
	}
}

class ArtistCreditResolutionRepository(
	private val metadataCache: AurralMetadataCache,
	private val lookup: ArtistCreditLookup,
	private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
	private val memoryCache = mutableMapOf<String, ArtistCreditResolution>()
	private val _revision = MutableStateFlow(0)
	val revision = _revision.asStateFlow()

	suspend fun cachedResolution(context: ArtistCreditContext): ArtistCreditResolution? {
		val cacheKey = artistCreditResolutionCacheKey(context)
		memoryCache[cacheKey]?.let { return it }
		return runCatching {
			metadataCache.get(cacheKey)
				?.takeIf { it.payloadType == AurralMetadataPayloadType.ArtistCreditResolution }
				?.decodeArtistCreditResolution()
				?.also { resolution -> memoryCache[cacheKey] = resolution }
		}.onFailure { error ->
			Logger.w(TAG, "Artist credit cache read failed for ${context.originalCredit}", error)
		}.getOrNull()
	}

	suspend fun resolveAndCache(context: ArtistCreditContext): ArtistCreditResolution? {
		cachedResolution(context)?.let { return it }
		val resolution = runCatching {
			resolveArtistCreditWithLookup(context)
		}.onFailure { error ->
			Logger.w(TAG, "Artist credit resolution failed for ${context.originalCredit}", error)
		}.getOrNull() ?: return null
		persist(context, resolution)
		return resolution
	}

	private suspend fun persist(
		context: ArtistCreditContext,
		resolution: ArtistCreditResolution
	) {
		val cacheKey = artistCreditResolutionCacheKey(context)
		memoryCache[cacheKey] = resolution
		runCatching {
			metadataCache.put(
				AurralMetadataCacheRecord(
					cacheKey = cacheKey,
					baseUrl = ARTIST_CREDIT_CACHE_BASE_URL,
					payloadType = AurralMetadataPayloadType.ArtistCreditResolution,
					path = artistCreditResolutionCachePath(context),
					payloadJson = AURRAL_JSON.encodeToString(resolution.toPayload()),
					updatedAtMillis = nowMillis()
				)
			)
			_revision.value += 1
		}.onFailure { error ->
			Logger.w(TAG, "Artist credit cache write failed for ${context.originalCredit}", error)
		}
	}

	private suspend fun resolveArtistCreditWithLookup(
		context: ArtistCreditContext
	): ArtistCreditResolution? {
		val originalCredit = context.originalCredit.artistCreditCleanName()
		if (originalCredit.isEmpty()) return null

		val structuredArtists = context.structuredArtistNames.cleanArtistCreditNames()
		if (isUsefulStructuredArtistCredit(structuredArtists, originalCredit)) {
			return ArtistCreditResolution(
				displayNames = structuredArtists,
				reason = ArtistCreditResolutionReason.StructuredArtists,
				confidence = 1.0
			)
		}

		lookup.exactArtistName(originalCredit)
			?.artistCreditCleanName()
			?.takeIf { it.isNotEmpty() }
			?.let { exact ->
				return ArtistCreditResolution(
					displayNames = listOf(exact),
					reason = ArtistCreditResolutionReason.ExactFullCredit,
					confidence = 0.98
				)
			}

		val splitCandidates = splitArtistCredit(originalCredit)
		if (splitCandidates.size <= 1) return null

		val albumArtists = lookup.albumArtistNames(context.albumTitle).cleanArtistCreditNames()
		if (albumArtists.isNotEmpty() && splitCandidates.sameArtistSet(albumArtists)) {
			return ArtistCreditResolution(
				displayNames = albumArtists,
				reason = ArtistCreditResolutionReason.AlbumContext,
				confidence = 0.97
			)
		}

		val resolvedCandidates = splitCandidates.map { candidate ->
			lookup.exactArtistName(candidate)
				?.artistCreditCleanName()
				?.takeIf { it.isNotEmpty() }
		}
		if (resolvedCandidates.any { it == null }) return null

		return ArtistCreditResolution(
			displayNames = resolvedCandidates.filterNotNull().cleanArtistCreditNames(),
			reason = ArtistCreditResolutionReason.ValidatedSplit,
			confidence = 0.92
		)
	}

	private suspend fun isUsefulStructuredArtistCredit(
		structuredArtists: List<String>,
		originalCredit: String
	): Boolean {
		if (structuredArtists.size > 1) return true
		val only = structuredArtists.singleOrNull() ?: return false
		return artistCreditIdentityKey(only) != artistCreditIdentityKey(originalCredit) &&
			lookup.exactArtistName(only) != null
	}
}

@Serializable
private data class ArtistCreditResolutionPayload(
	val displayNames: List<String> = emptyList(),
	val reason: String = ArtistCreditResolutionReason.ValidatedSplit.name,
	val confidence: Double = 0.0
)

private fun ArtistCreditResolution.toPayload(): ArtistCreditResolutionPayload =
	ArtistCreditResolutionPayload(
		displayNames = displayNames,
		reason = reason.name,
		confidence = confidence
	)

private fun AurralMetadataCacheRecord.decodeArtistCreditResolution(): ArtistCreditResolution? =
	runCatching {
		val payload = AURRAL_JSON.decodeFromString<ArtistCreditResolutionPayload>(payloadJson)
		ArtistCreditResolution(
			displayNames = payload.displayNames,
			reason = ArtistCreditResolutionReason.entries
				.firstOrNull { reason -> reason.name == payload.reason }
				?: ArtistCreditResolutionReason.ValidatedSplit,
			confidence = payload.confidence
		)
	}.getOrNull()

private fun artistCreditResolutionCacheKey(context: ArtistCreditContext): String =
	aurralMetadataCacheKey(
		baseUrl = ARTIST_CREDIT_CACHE_BASE_URL,
		payloadType = AurralMetadataPayloadType.ArtistCreditResolution,
		path = artistCreditResolutionCachePath(context)
	)

private fun artistCreditResolutionCachePath(context: ArtistCreditContext): String =
	stableArtistCreditHash(
		listOf(
			context.originalCredit,
			context.albumTitle.orEmpty(),
			context.trackTitle.orEmpty(),
			context.structuredArtistNames.joinToString("\u001F")
		).joinToString("\u001E") { artistCreditIdentityKey(it) }
	)

private fun stableArtistCreditHash(value: String): String {
	var hash = 1125899906842597L
	value.forEach { char ->
		hash = hash * 31 + char.code
	}
	return hash.toString()
}

private fun List<String>.sameArtistSet(other: List<String>): Boolean =
	map { artistCreditIdentityKey(it) }.toSet() == other.map { artistCreditIdentityKey(it) }.toSet()

private fun List<String>.cleanArtistCreditNames(): List<String> {
	val seen = mutableSetOf<String>()
	return map { it.artistCreditCleanName() }
		.filter { it.isNotEmpty() }
		.filter { seen.add(artistCreditIdentityKey(it)) }
}
