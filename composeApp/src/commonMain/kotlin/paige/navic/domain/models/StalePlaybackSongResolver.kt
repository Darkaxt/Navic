package paige.navic.domain.models

import dev.zt64.subsonic.api.model.SubsonicErrorCode
import dev.zt64.subsonic.api.model.SubsonicException
import kotlin.coroutines.cancellation.CancellationException

sealed interface StalePlaybackProbeResolution {
	data object Current : StalePlaybackProbeResolution
	data object Missing : StalePlaybackProbeResolution
	data object Ambiguous : StalePlaybackProbeResolution
	data class Replacement(
		val song: DomainSong,
		val strength: StalePlaybackMatchStrength
	) : StalePlaybackProbeResolution
	data class ServiceUnavailable(val error: Throwable) : StalePlaybackProbeResolution
	data class Unresolved(val error: Throwable) : StalePlaybackProbeResolution
}

class StalePlaybackSongResolver(
	private val fetchSongById: suspend (String) -> Unit,
	private val loadCurrentSongs: suspend () -> List<DomainSong>
) {
	suspend fun resolve(staleSong: DomainSong): StalePlaybackProbeResolution {
		try {
			fetchSongById(staleSong.id)
			return StalePlaybackProbeResolution.Current
		} catch (error: Throwable) {
			if (error is CancellationException) throw error
			if (
				error !is SubsonicException ||
				error.code != SubsonicErrorCode.DATA_NOT_FOUND
			) {
				return if (classifyNavidromeFailure(error) == NavidromeFailureDisposition.ServiceUnavailable) {
					StalePlaybackProbeResolution.ServiceUnavailable(error)
				} else {
					StalePlaybackProbeResolution.Unresolved(error)
				}
			}
		}

		val currentSongs = try {
			loadCurrentSongs().filterNot { song -> song.id == staleSong.id }
		} catch (error: Throwable) {
			if (error is CancellationException) throw error
			return if (classifyNavidromeFailure(error) == NavidromeFailureDisposition.ServiceUnavailable) {
				StalePlaybackProbeResolution.ServiceUnavailable(error)
			} else {
				StalePlaybackProbeResolution.Unresolved(error)
			}
		}

		return when (
			val resolution = resolveStalePlaybackSong(
				staleSong = staleSong,
				currentSongs = currentSongs
			)
		) {
			StalePlaybackSongResolution.Current -> StalePlaybackProbeResolution.Missing
			StalePlaybackSongResolution.Missing -> StalePlaybackProbeResolution.Missing
			StalePlaybackSongResolution.Ambiguous -> StalePlaybackProbeResolution.Ambiguous
			is StalePlaybackSongResolution.Replacement -> StalePlaybackProbeResolution.Replacement(
				song = resolution.song,
				strength = resolution.strength
			)
		}
	}
}
