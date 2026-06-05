package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
