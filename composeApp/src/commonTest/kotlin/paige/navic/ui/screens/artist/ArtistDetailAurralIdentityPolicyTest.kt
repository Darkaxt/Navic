package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.repositories.AurralArtistSearchResult
import paige.navic.domain.repositories.AurralDiscoverArtist

class ArtistDetailAurralIdentityPolicyTest {
	@Test
	fun fallbackIdentityUsesMusicBrainzWhenPresent() {
		assertEquals(
			"mbid-123",
			artistDetailAurralFallbackIdentities(
				DomainArtist(
					id = "local-id",
					name = "Local Artist",
					musicBrainzId = " mbid-123 "
				)
			).single().mbid
		)
	}

	@Test
	fun fallbackIdentityUsesAurralNameLookupWhenMusicBrainzIsMissing() {
		val identity = artistDetailAurralFallbackIdentities(
			DomainArtist(
				id = "local-id",
				name = " Local Artist ",
				artistImageUrl = "https://aurral.example.com/local.jpg"
			)
		).single()

		assertEquals("name:local-artist", identity.mbid)
		assertEquals("Local Artist", identity.name)
		assertEquals("https://aurral.example.com/local.jpg", identity.imageUrl)
	}

	@Test
	fun nameLookupCandidateDoesNotPretendPseudoIdIsMusicBrainzId() {
		val identity = artistDetailAurralFallbackIdentities(
			DomainArtist(id = "local-id", name = "Local Artist")
		).single()
		val candidate = artistDetailAurralCandidateArtist(
			artist = DomainArtist(id = "local-id", name = "Wrong Name"),
			identity = identity
		)

		assertEquals("Local Artist", candidate.name)
		assertNull(candidate.musicBrainzId)
	}

	@Test
	fun searchImageUsesExactMusicBrainzMatchWhenDiscoveryDoesNotCarryTheArtist() {
		val imageUrl = artistDetailAurralSearchImageUrl(
			artistMbid = "52bb713d-b0c9-4bf6-9f58-392388d5cc11",
			artistName = "John Powell",
			search = AurralArtistSearchResult(
				artists = listOf(
					AurralDiscoverArtist(
						id = "unrelated",
						name = "John Williams",
						imageUrl = "https://aurral.example.com/williams.jpg",
						detailsIdVerified = true
					),
					AurralDiscoverArtist(
						id = "52bb713d-b0c9-4bf6-9f58-392388d5cc11",
						name = "John Powell",
						imageUrl = "https://aurral.example.com/powell.jpg",
						detailsIdVerified = true
					)
				)
			)
		)

		assertEquals("https://aurral.example.com/powell.jpg", imageUrl)
	}
}
