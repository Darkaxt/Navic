package paige.navic.domain.models

import androidx.compose.runtime.Immutable

@Immutable
data class LastFmTopTrack(
	val name: String,
	val rank: Int,
	val playCount: Long?,
	val url: String?
)
