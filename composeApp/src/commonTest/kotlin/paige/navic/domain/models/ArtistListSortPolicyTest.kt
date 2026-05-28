package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class ArtistListSortPolicyTest {
	@Test
	fun artistSortOptionsMatchArtistTopBarOrder() {
		assertEquals(
			listOf(
				DomainArtistListType.AlphabeticalByName,
				DomainArtistListType.Starred,
				DomainArtistListType.Random
			),
			artistListSortOptions()
		)
	}

	@Test
	fun artistListDirectionCanReverseCurrentSortOrder() {
		val artists = listOf(
			artist("a"),
			artist("b"),
			artist("c")
		)

		assertEquals(
			listOf("c", "b", "a"),
			artists.applyArtistListDirection(reversed = true).map { it.id }
		)
		assertEquals(
			listOf("a", "b", "c"),
			artists.applyArtistListDirection(reversed = false).map { it.id }
		)
	}

	private fun artist(id: String) = DomainArtist(
		id = id,
		name = id
	)
}
