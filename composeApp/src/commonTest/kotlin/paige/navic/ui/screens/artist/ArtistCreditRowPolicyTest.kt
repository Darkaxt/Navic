package paige.navic.ui.screens.artist

import paige.navic.domain.models.ArtistCreditResolution
import paige.navic.domain.models.ArtistCreditResolutionReason
import paige.navic.domain.models.DomainArtist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtistCreditRowPolicyTest {
	@Test
	fun resolvedCompositeArtistCreditIsReplacedByResolvedArtists() {
		val artists = listOf(
			DomainArtist(id = "composite", name = "Anyma & LISA")
		)

		val rows = artistCreditResolvedRows(artists) { artist ->
			if (artist.name == "Anyma & LISA") {
				ArtistCreditResolution(
					displayNames = listOf("Anyma", "LISA"),
					reason = ArtistCreditResolutionReason.ValidatedSplit,
					confidence = 0.92
				)
			} else {
				null
			}
		}

		assertEquals(listOf("Anyma", "LISA"), rows.map { it.name })
		assertFalse(rows.any { it.name == "Anyma & LISA" })
	}

	@Test
	fun resolvedCompositeReusesExistingLocalArtistRows() {
		val artists = listOf(
			DomainArtist(id = "composite", name = "Anyma & LISA"),
			DomainArtist(id = "artist-anyma", name = "Anyma", albumCount = 4)
		)

		val rows = artistCreditResolvedRows(artists) { artist ->
			if (artist.name == "Anyma & LISA") {
				ArtistCreditResolution(
					displayNames = listOf("Anyma", "LISA"),
					reason = ArtistCreditResolutionReason.ValidatedSplit,
					confidence = 0.92
				)
			} else {
				null
			}
		}

		assertEquals(listOf("Anyma", "LISA"), rows.map { it.name })
		assertTrue(rows.first { it.name == "Anyma" }.albumCount == 4)
		assertEquals("name:lisa", rows.first { it.name == "LISA" }.id)
	}

	@Test
	fun unresolvedCompositeArtistCreditStaysRaw() {
		val artists = listOf(
			DomainArtist(id = "group", name = "Chase & Status")
		)

		val rows = artistCreditResolvedRows(artists) { null }

		assertEquals(listOf("Chase & Status"), rows.map { it.name })
	}
}
