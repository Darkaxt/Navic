package paige.navic.domain.manager

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import paige.navic.data.database.dao.ArtworkColorDao
import paige.navic.data.database.entities.ArtworkColorEntity

class ArtworkColorManager(
	private val artworkColorDao: ArtworkColorDao
) {
	private val mutex = Mutex()
	private val colorCache = mutableMapOf<String, Color>()

	suspend fun getColor(artworkKey: String): Color? {
		mutex.withLock { colorCache[artworkKey] }?.let { return it }

		val color = artworkColorDao.getColor(artworkKey)?.let { Color(it.color) } ?: return null

		mutex.withLock {
			colorCache[artworkKey] = color
		}
		return color
	}

	suspend fun putColor(artworkKey: String, color: Color) {
		mutex.withLock {
			colorCache[artworkKey] = color
		}
		artworkColorDao.upsertColor(ArtworkColorEntity(artworkKey, color.toArgb()))
	}
}
