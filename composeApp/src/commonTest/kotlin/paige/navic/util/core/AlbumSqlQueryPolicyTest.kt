package paige.navic.util.core

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.models.DomainAlbumListType

class AlbumSqlQueryPolicyTest {
	@Test
	fun genreAlbumListsSortByYearFromRecentToOldest() {
		val parts = DomainAlbumListType.ByGenre("Pop").toSqlQueryParts()

		assertEquals(
			"(genre = ? OR genres = ? OR genres LIKE ? OR genres LIKE ? OR genres LIKE ?)",
			parts.where
		)
		assertEquals(
			"CASE WHEN year IS NULL THEN 1 ELSE 0 END ASC, year DESC, LOWER(name) ASC",
			parts.orderBy
		)
	}
}
