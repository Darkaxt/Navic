package paige.navic.data.database.mappers

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import dev.zt64.subsonic.api.model.Album as ApiAlbum

/**
 * Navidrome omits `coverArt` for albums without any artwork. One such album in a
 * getAlbumList2 page used to fail the whole library sync (and login).
 */
class AlbumWithoutCoverArtMappingTest {
	@Test
	fun albumWithoutCoverArtDecodesAndMapsToEmptyCoverArt() {
		val album = Json { ignoreUnknownKeys = true }.decodeFromString<ApiAlbum>(
			"""{"id":"556Azz6y6HRpQ7oE1SaeTT","name":"Gravity","artist":"Steven Price","songCount":3,"duration":600,"created":"2026-01-01T00:00:00Z"}"""
		)

		val entity = album.toEntity()

		assertEquals("", entity.coverArtId)
		assertEquals("Gravity", entity.name)
	}
}
