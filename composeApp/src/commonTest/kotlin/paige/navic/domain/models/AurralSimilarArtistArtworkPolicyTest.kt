package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AurralSimilarArtistArtworkPolicyTest {
	@Test
	fun similarArtistRowsUseCachedLocalArtistImageWhenAurralImageIsMissing() {
		val rows = aurralSimilarArtistRows(
			enrichment = AurralArtistEnrichment(
				artistMbid = "source",
				artistName = "Source",
				similarArtists = listOf(
					AurralSimilarArtist(
						id = "mbid-iu",
						name = "IU",
						imageUrl = null
					)
				)
			),
			allLocalArtists = listOf(
				DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = "ar-local-iu",
					artistImageUrl = "https://aurral.example.com/artist/iu.webp",
					musicBrainzId = "mbid-iu"
				)
			),
			localSimilarArtists = emptyList()
		)

		assertEquals("https://aurral.example.com/artist/iu.webp", rows.single().artist.imageUrl)
		assertEquals("ar-local-iu", rows.single().localCoverArtId)
	}

	@Test
	fun similarArtistRowsUseExternalAurralCandidateImageWhenArtistIsNotLocal() {
		val rows = aurralSimilarArtistRows(
			enrichment = AurralArtistEnrichment(
				artistMbid = "source",
				artistName = "Source",
				similarArtists = listOf(
					AurralSimilarArtist(
						id = "mbid-heize",
						name = "Heize",
						imageUrl = null
					)
				)
			),
			allLocalArtists = emptyList(),
			localSimilarArtists = emptyList(),
			externalArtists = listOf(
				AurralSimilarArtist(
					id = "MBID-HEIZE",
					name = "Heize",
					imageUrl = "https://aurral.example.com/artist/heize.webp"
				)
			)
		)

		assertEquals("https://aurral.example.com/artist/heize.webp", rows.single().artist.imageUrl)
	}

	@Test
	fun similarArtistRowsUseExternalAurralCandidateBeforeLocalArtistImage() {
		val rows = aurralSimilarArtistRows(
			enrichment = AurralArtistEnrichment(
				artistMbid = "source",
				artistName = "Source",
				similarArtists = listOf(
					AurralSimilarArtist(
						id = "mbid-jason-ross",
						name = "Jason Ross",
						imageUrl = null
					)
				)
			),
			allLocalArtists = listOf(
				DomainArtist(
					id = "local-jason-ross",
					name = "Jason Ross",
					coverArtId = "navidrome-artist-cover",
					artistImageUrl = "https://navidrome.example.com/protected/jason-ross.jpg",
					musicBrainzId = "mbid-jason-ross"
				)
			),
			localSimilarArtists = emptyList(),
			externalArtists = listOf(
				AurralSimilarArtist(
					id = "MBID-JASON-ROSS",
					name = "Jason Ross",
					imageUrl = "https://aurral.example.com/artist/jason-ross.webp"
				)
			)
		)

		assertEquals("https://aurral.example.com/artist/jason-ross.webp", rows.single().artist.imageUrl)
	}

	@Test
	fun similarArtistRowsReplaceNavidromeImageWithExternalAurralCandidate() {
		val rows = aurralSimilarArtistRows(
			enrichment = AurralArtistEnrichment(
				artistMbid = "source",
				artistName = "Source",
				similarArtists = listOf(
					AurralSimilarArtist(
						id = "mbid-iu",
						name = "IU",
						imageUrl = "https://navidrome.example.com/rest/getArtistImage?id=mbid-iu"
					)
				)
			),
			allLocalArtists = emptyList(),
			localSimilarArtists = emptyList(),
			externalArtists = listOf(
				AurralSimilarArtist(
					id = "MBID-IU",
					name = "IU",
					imageUrl = "https://aurral.example.com/artist/iu.webp"
				)
			)
		)

		assertEquals("https://aurral.example.com/artist/iu.webp", rows.single().artist.imageUrl)
	}

	@Test
	fun similarArtistRowsDoNotFallbackToLocalNavidromeArtistImage() {
		val rows = aurralSimilarArtistRows(
			enrichment = AurralArtistEnrichment(
				artistMbid = "source",
				artistName = "Source",
				similarArtists = listOf(
					AurralSimilarArtist(
						id = "mbid-jason-ross",
						name = "Jason Ross",
						imageUrl = null
					)
				)
			),
			allLocalArtists = listOf(
				DomainArtist(
					id = "local-jason-ross",
					name = "Jason Ross",
					coverArtId = "navidrome-artist-cover",
					artistImageUrl = "https://navidrome.example.com/rest/getArtistImage?id=jason-ross",
					musicBrainzId = "mbid-jason-ross"
				)
			),
			localSimilarArtists = emptyList()
		)

		assertNull(rows.single().artist.imageUrl)
		assertEquals("local-jason-ross", rows.single().localArtistId)
		assertEquals("navidrome-artist-cover", rows.single().localCoverArtId)
	}

	@Test
	fun localSimilarArtistRowsDoNotUseNavidromeArtistImageAsVisibleArtwork() {
		val rows = aurralSimilarArtistRows(
			enrichment = AurralArtistEnrichment(
				artistMbid = "source",
				artistName = "Source"
			),
			allLocalArtists = emptyList(),
			localSimilarArtists = listOf(
				DomainArtist(
					id = "local-jason-ross",
					name = "Jason Ross",
					coverArtId = "navidrome-artist-cover",
					artistImageUrl = "https://navidrome.example.com/rest/getArtistImage?id=jason-ross",
					musicBrainzId = "mbid-jason-ross"
				)
			)
		)

		assertNull(rows.single().artist.imageUrl)
		assertEquals("local-jason-ross", rows.single().localArtistId)
		assertEquals("navidrome-artist-cover", rows.single().localCoverArtId)
	}
}
