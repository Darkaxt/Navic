package paige.navic.data.database.mappers

import paige.navic.data.database.entities.PlaybackOriginEntity
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOrigin
import paige.navic.domain.models.PlaybackOriginType
import kotlin.time.Instant

fun PlaybackOrigin.toEntity(
	totalPlayedMillis: Long,
	lastPlayedAt: Instant
): PlaybackOriginEntity =
	PlaybackOriginEntity(
		originKey = key,
		type = type.name,
		itemId = id,
		title = title,
		subtitle = subtitle,
		coverArtId = coverArtId,
		totalPlayedMillis = totalPlayedMillis,
		lastPlayedAt = lastPlayedAt
	)

fun PlaybackOriginEntity.toDomainModel(): DomainMostPlayedShortcut? {
	val originType = PlaybackOriginType.entries.firstOrNull { it.name == type } ?: return null
	return DomainMostPlayedShortcut(
		type = originType,
		id = itemId,
		title = title,
		subtitle = subtitle,
		coverArtId = coverArtId,
		totalPlayedMillis = totalPlayedMillis,
		lastPlayedAt = lastPlayedAt
	)
}
