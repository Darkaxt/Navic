package paige.navic.ui.screens.genre

import paige.navic.domain.models.DomainGenreListType
import paige.navic.domain.models.DomainGenreSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class GenreListSortPolicyTest {
	private val genres = listOf(
		genre("rock", albumCount = 5, songCount = 40),
		genre("Ambient", albumCount = 2, songCount = 90),
		genre("jazz", albumCount = 5, songCount = 10),
		genre("Indie Rock", albumCount = 1, songCount = 12)
	)

	@Test
	fun sortOptionsKeepAlbumCountAsTheFirstDefault() {
		assertEquals(
			listOf(
				DomainGenreListType.AlbumCount,
				DomainGenreListType.SongCount,
				DomainGenreListType.AlphabeticalByName
			),
			genreListSortOptions()
		)
	}

	@Test
	fun albumCountSortsLargestFirstWithNameTieBreak() {
		assertEquals(
			listOf("jazz", "rock", "Ambient", "Indie Rock"),
			names(DomainGenreListType.AlbumCount)
		)
	}

	@Test
	fun songCountSortsLargestFirst() {
		assertEquals(
			listOf("Ambient", "rock", "Indie Rock", "jazz"),
			names(DomainGenreListType.SongCount)
		)
	}

	@Test
	fun alphabeticalSortIgnoresCase() {
		assertEquals(
			listOf("Ambient", "Indie Rock", "jazz", "rock"),
			names(DomainGenreListType.AlphabeticalByName)
		)
	}

	@Test
	fun reversedFlipsTheSelectedOrder() {
		assertEquals(
			listOf("rock", "jazz", "Indie Rock", "Ambient"),
			names(DomainGenreListType.AlphabeticalByName, reversed = true)
		)
	}

	@Test
	fun searchFiltersByCaseInsensitiveTrimmedSubstring() {
		assertEquals(
			listOf("Indie Rock", "rock"),
			names(DomainGenreListType.AlphabeticalByName, query = "  ROCK ")
		)
		assertEquals(
			emptyList<String>(),
			names(DomainGenreListType.AlbumCount, query = "polka")
		)
	}

	@Test
	fun blankSearchKeepsEveryGenre() {
		assertEquals(4, names(DomainGenreListType.AlbumCount, query = "   ").size)
	}

	private fun names(
		sorting: DomainGenreListType,
		reversed: Boolean = false,
		query: String = ""
	): List<String> = visibleGenreSummaries(genres, sorting, reversed, query).map { it.name }

	private fun genre(name: String, albumCount: Int, songCount: Int) = DomainGenreSummary(
		name = name,
		albumCount = albumCount,
		songCount = songCount,
		coverArtIds = emptyList()
	)
}
