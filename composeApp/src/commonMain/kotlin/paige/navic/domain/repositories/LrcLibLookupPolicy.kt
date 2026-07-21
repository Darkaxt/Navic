package paige.navic.domain.repositories

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.time.Duration

@Serializable
internal data class LrcLibCandidate(
	val id: Int? = null,
	val name: String? = null,
	val trackName: String? = null,
	val artistName: String? = null,
	val albumName: String? = null,
	val duration: Double? = null,
	val instrumental: Boolean = false,
	val plainLyrics: String? = null,
	val syncedLyrics: String? = null
)

internal fun normalizedLrcLibSearchUrl(configuredUrl: String): String =
	configuredUrl.trim().trimEnd('/').let { url ->
		if (url.endsWith(LRC_LIB_EXACT_PATH)) {
			url.removeSuffix(LRC_LIB_EXACT_PATH) + LRC_LIB_SEARCH_PATH
		} else {
			url
		}
	}

internal fun lrcLibExactUrl(configuredUrl: String): String =
	configuredUrl.trim().trimEnd('/').let { url ->
		if (url.endsWith(LRC_LIB_SEARCH_PATH)) {
			url.removeSuffix(LRC_LIB_SEARCH_PATH) + LRC_LIB_EXACT_PATH
		} else {
			url
		}
	}

internal fun relaxedLrcLibTrackName(trackName: String): String {
	val trimmed = trackName.trim()
	val withoutTrailingQualifiers = trailingParentheticalQualifiers.replace(trimmed, "").trim()
	return withoutTrailingQualifiers.ifEmpty { trimmed }
}

internal fun lrcLibDurationSeconds(duration: Duration): Long = duration.inWholeSeconds

internal fun selectLrcLibCandidate(
	candidates: List<LrcLibCandidate>,
	trackName: String,
	artistName: String,
	albumName: String?,
	durationSeconds: Long
): LrcLibCandidate? {
	val expectedTrack = normalizedLrcLibMetadata(trackName)
	val expectedArtist = normalizedLrcLibMetadata(artistName)
	val expectedAlbum = normalizedLrcLibMetadata(albumName)

	return candidates.asSequence()
		.filter(LrcLibCandidate::hasLyrics)
		.filter { candidate ->
			normalizedLrcLibMetadata(candidate.trackName) == expectedTrack &&
				normalizedLrcLibMetadata(candidate.artistName) == expectedArtist
		}
		.sortedWith(
			compareByDescending<LrcLibCandidate> { candidate ->
				expectedAlbum.isNotEmpty() && normalizedLrcLibMetadata(candidate.albumName) == expectedAlbum
			}
				.thenBy { candidate ->
					candidate.duration?.let { abs(it - durationSeconds) } ?: Double.MAX_VALUE
				}
				.thenByDescending { candidate -> !candidate.syncedLyrics.isNullOrBlank() }
				.thenBy { candidate -> candidate.id ?: Int.MAX_VALUE }
		)
		.firstOrNull()
}

private fun LrcLibCandidate.hasLyrics(): Boolean =
	!syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()

private fun normalizedLrcLibMetadata(value: String?): String =
	value.orEmpty()
		.lowercase()
		.map { character -> if (character.isLetterOrDigit()) character else ' ' }
		.joinToString("")
		.splitToSequence(' ')
		.filter(String::isNotBlank)
		.joinToString(" ")

private val trailingParentheticalQualifiers = Regex("(?:\\s*\\([^)]*\\)\\s*)+$")
private const val LRC_LIB_EXACT_PATH = "/api/get"
private const val LRC_LIB_SEARCH_PATH = "/api/search"
