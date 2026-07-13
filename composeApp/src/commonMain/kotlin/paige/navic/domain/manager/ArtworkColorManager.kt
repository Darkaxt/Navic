package paige.navic.domain.manager

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import paige.navic.data.database.dao.ArtworkColorDao
import paige.navic.data.database.entities.ArtworkColorEntity
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal val ArtworkColorCacheTtl = 30.days
internal const val ArtworkColorCacheMaxEntries = 2_048

private data class CachedArtworkColor(
	val sourceIdentity: String,
	val color: Color,
	val updatedAtEpochMillis: Long
)

class ArtworkColorManager(
	private val artworkColorDao: ArtworkColorDao,
	private val currentTimeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
	private val mutex = Mutex()
	private val colorCache = mutableMapOf<String, CachedArtworkColor>()

	suspend fun getColor(artworkKey: String, sourceIdentity: String): Color? {
		val now = currentTimeMillis()
		mutex.withLock { colorCache[artworkKey] }
			?.takeIf { entry -> entry.isValidFor(sourceIdentity, now) }
			?.let { entry -> return entry.color }

		val stored = artworkColorDao.getColor(artworkKey) ?: return null
		if (!stored.isValidFor(sourceIdentity, now)) {
			mutex.withLock { colorCache.remove(artworkKey) }
			artworkColorDao.deleteColor(artworkKey)
			return null
		}
		val cached = stored.toCachedArtworkColor()

		mutex.withLock {
			colorCache[artworkKey] = cached
		}
		return cached.color
	}

	suspend fun putColor(artworkKey: String, sourceIdentity: String, color: Color) {
		val updatedAt = currentTimeMillis()
		val entry = ArtworkColorEntity(
			artworkKey = artworkKey,
			sourceIdentity = sourceIdentity,
			color = color.toArgb(),
			updatedAtEpochMillis = updatedAt
		)
		mutex.withLock {
			colorCache[artworkKey] = entry.toCachedArtworkColor()
		}
		artworkColorDao.upsertColor(entry)
		prune(updatedAt)
	}

	suspend fun clear() {
		mutex.withLock { colorCache.clear() }
		artworkColorDao.clearAll()
	}

	suspend fun prune(nowEpochMillis: Long = currentTimeMillis()) {
		val cutoff = nowEpochMillis - ArtworkColorCacheTtl.inWholeMilliseconds
		artworkColorDao.deleteOlderThan(cutoff)
		artworkColorDao.trimToNewest(ArtworkColorCacheMaxEntries)
		mutex.withLock {
			colorCache.entries.removeAll { (_, entry) -> entry.updatedAtEpochMillis < cutoff }
		}
	}
}

private fun CachedArtworkColor.isValidFor(sourceIdentity: String, nowEpochMillis: Long): Boolean =
	this.sourceIdentity == sourceIdentity &&
		nowEpochMillis - updatedAtEpochMillis <= ArtworkColorCacheTtl.inWholeMilliseconds

private fun ArtworkColorEntity.isValidFor(sourceIdentity: String, nowEpochMillis: Long): Boolean =
	this.sourceIdentity == sourceIdentity &&
		nowEpochMillis - updatedAtEpochMillis <= ArtworkColorCacheTtl.inWholeMilliseconds

private fun ArtworkColorEntity.toCachedArtworkColor(): CachedArtworkColor =
	CachedArtworkColor(
		sourceIdentity = sourceIdentity,
		color = Color(color),
		updatedAtEpochMillis = updatedAtEpochMillis
	)
