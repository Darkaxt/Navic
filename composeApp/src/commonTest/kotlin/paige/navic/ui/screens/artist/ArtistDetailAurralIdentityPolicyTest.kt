package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.domain.models.DomainArtist

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
}
