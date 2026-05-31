package paige.navic.ui.screens.search

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralDiscoverArtist

class SearchDisplayPolicyTest {
	@Test
	fun combinedSearchResultsAppendAurralArtistsAndAlbumsAfterLocalResults() {
		val localArtist = DomainArtist(id = "local-artist", name = "Local Artist")
		val aurralArtist = AurralDiscoverArtist(id = "aurral-artist", name = "Aurral Artist")
		val aurralAlbum = AurralAlbumSearchItem(
			id = "aurral-album",
			title = "Aurral Album",
			artistName = "Aurral Artist",
			artistMbid = "aurral-artist"
		)

		assertEquals(
			listOf(localArtist, aurralArtist, aurralAlbum),
			combinedSearchResults(
				localResults = listOf(localArtist),
				aurralArtists = listOf(aurralArtist),
				aurralAlbums = listOf(aurralAlbum)
			)
		)
	}

	@Test
	fun searchBucketsIncludeAurralResultsInMatchingCategories() {
		val localArtist = DomainArtist(id = "local-artist", name = "Local Artist")
		val aurralArtist = AurralDiscoverArtist(id = "aurral-artist", name = "Aurral Artist")
		val aurralAlbum = AurralAlbumSearchItem(
			id = "aurral-album",
			title = "Aurral Album",
			artistName = "Aurral Artist",
			artistMbid = "aurral-artist"
		)
		val results = listOf(localArtist, aurralArtist, aurralAlbum)

		val allBuckets = searchResultBuckets(results, SearchCategory.ALL)
		assertEquals(listOf(localArtist), allBuckets.artists)
		assertEquals(listOf(aurralArtist), allBuckets.aurralArtists)
		assertEquals(listOf(aurralAlbum), allBuckets.aurralAlbums)

		val artistBuckets = searchResultBuckets(results, SearchCategory.ARTISTS)
		assertEquals(listOf(localArtist), artistBuckets.artists)
		assertEquals(listOf(aurralArtist), artistBuckets.aurralArtists)
		assertEquals(emptyList(), artistBuckets.aurralAlbums)

		val albumBuckets = searchResultBuckets(results, SearchCategory.ALBUMS)
		assertEquals(emptyList(), albumBuckets.artists)
		assertEquals(emptyList(), albumBuckets.aurralArtists)
		assertEquals(listOf(aurralAlbum), albumBuckets.aurralAlbums)
	}
}
