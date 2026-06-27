package paige.navic.domain.models

data class ArtistCreditContext(
	val originalCredit: String,
	val albumTitle: String? = null,
	val trackTitle: String? = null,
	val structuredArtistNames: List<String> = emptyList(),
	val sourceId: String? = null
)

data class ArtistCreditResolution(
	val displayNames: List<String>,
	val reason: ArtistCreditResolutionReason,
	val confidence: Double
)

enum class ArtistCreditResolutionReason {
	StructuredArtists,
	ExactFullCredit,
	AlbumContext,
	ValidatedSplit
}

fun artistCreditDisplayNames(
	context: ArtistCreditContext,
	cachedResolution: ArtistCreditResolution?
): List<String> =
	cachedResolution
		?.displayNames
		?.cleanArtistCreditNames()
		?.takeIf { it.isNotEmpty() }
		?: listOf(context.originalCredit.artistCreditCleanName()).filter { it.isNotEmpty() }

fun resolveArtistCredit(
	context: ArtistCreditContext,
	exactArtistName: (String) -> String?,
	albumArtistNames: (String?) -> List<String>
): ArtistCreditResolution? {
	val originalCredit = context.originalCredit.artistCreditCleanName()
	if (originalCredit.isEmpty()) return null

	val structuredArtists = context.structuredArtistNames.cleanArtistCreditNames()
	if (structuredArtists.isUsefulStructuredArtistCredit(originalCredit, exactArtistName)) {
		return ArtistCreditResolution(
			displayNames = structuredArtists,
			reason = ArtistCreditResolutionReason.StructuredArtists,
			confidence = 1.0
		)
	}

	exactArtistName(originalCredit)?.artistCreditCleanName()?.takeIf { it.isNotEmpty() }?.let { exact ->
		return ArtistCreditResolution(
			displayNames = listOf(exact),
			reason = ArtistCreditResolutionReason.ExactFullCredit,
			confidence = 0.98
		)
	}

	val splitCandidates = splitArtistCredit(originalCredit)
	if (splitCandidates.size <= 1) return null

	val albumArtists = albumArtistNames(context.albumTitle).cleanArtistCreditNames()
	if (albumArtists.isNotEmpty() && splitCandidates.sameArtistSet(albumArtists)) {
		return ArtistCreditResolution(
			displayNames = albumArtists,
			reason = ArtistCreditResolutionReason.AlbumContext,
			confidence = 0.97
		)
	}

	val resolvedCandidates = splitCandidates.map { candidate ->
		exactArtistName(candidate)?.artistCreditCleanName()?.takeIf { it.isNotEmpty() }
	}
	if (resolvedCandidates.any { it == null }) return null

	return ArtistCreditResolution(
		displayNames = resolvedCandidates.filterNotNull().cleanArtistCreditNames(),
		reason = ArtistCreditResolutionReason.ValidatedSplit,
		confidence = 0.92
	)
}

fun splitArtistCredit(credit: String): List<String> {
	var working = credit.artistCreditCleanName()
	ArtistCreditSplitPatterns.forEach { pattern ->
		working = pattern.replace(working, ArtistCreditSplitMarker)
	}
	return working
		.split(ArtistCreditSplitMarker)
		.cleanArtistCreditNames()
}

private fun List<String>.isUsefulStructuredArtistCredit(
	originalCredit: String,
	exactArtistName: (String) -> String?
): Boolean {
	if (size > 1) return true
	val only = singleOrNull() ?: return false
	return !only.sameArtistIdentity(originalCredit) && exactArtistName(only) != null
}

private fun List<String>.sameArtistSet(other: List<String>): Boolean =
	map { artistCreditIdentityKey(it) }.toSet() == other.map { artistCreditIdentityKey(it) }.toSet()

private fun List<String>.cleanArtistCreditNames(): List<String> {
	val seen = mutableSetOf<String>()
	return map { it.artistCreditCleanName() }
		.filter { it.isNotEmpty() }
		.filter { seen.add(artistCreditIdentityKey(it)) }
}

internal fun String.artistCreditCleanName(): String =
	trim().replace(Regex("""\s+"""), " ")

private fun String.sameArtistIdentity(other: String): Boolean =
	artistCreditIdentityKey(this) == artistCreditIdentityKey(other)

internal fun artistCreditIdentityKey(value: String): String =
	value.artistCreditCleanName()
		.replace('’', '\'')
		.lowercase()

private const val ArtistCreditSplitMarker = "\u001F"

private val ArtistCreditSplitPatterns = listOf(
	Regex("""\s+(feat\.?|featuring|ft\.?|with)\s+""", RegexOption.IGNORE_CASE),
	Regex("""\s+[xX]\s+"""),
	Regex("""\s*&\s*"""),
	Regex("""\s*,\s*"""),
	Regex("""\s*•\s*"""),
	Regex("""\s*;\s*"""),
	Regex("""\s+/\s+""")
)
