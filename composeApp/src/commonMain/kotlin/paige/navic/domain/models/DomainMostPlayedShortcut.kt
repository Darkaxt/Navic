package paige.navic.domain.models

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

@Immutable
data class DomainMostPlayedShortcut(
	val type: PlaybackOriginType,
	val id: String,
	val title: String,
	val subtitle: String?,
	val coverArtId: String?,
	val totalPlayedMillis: Long,
	val lastPlayedAt: Instant
)
