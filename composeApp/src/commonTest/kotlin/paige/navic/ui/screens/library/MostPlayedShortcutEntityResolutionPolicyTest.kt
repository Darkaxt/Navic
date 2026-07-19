package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType

class MostPlayedShortcutEntityResolutionPolicyTest {
	@Test
	fun artistLookupIdentitiesAreBoundedToVisibleArtistShortcuts() {
		val lookup = mostPlayedArtistLookupIdentities(
			listOf(
				mostPlayedShortcut(PlaybackOriginType.Artist, "artist-id", " Classical  Crossover "),
				mostPlayedShortcut(PlaybackOriginType.Artist, "artist-id", "Classical Crossover"),
				mostPlayedShortcut(PlaybackOriginType.Genre, "genre-id", "Ignored Genre")
			)
		)

		assertEquals(listOf("artist-id"), lookup.ids)
		assertEquals(listOf("classical crossover"), lookup.normalizedNames)
	}

	@Test
	fun staleArtistShortcutIdResolvesToCurrentLocalArtistIdByName() {
		val resolved = mostPlayedShortcutsWithResolvedLocalArtists(
			shortcuts = listOf(
				mostPlayedShortcut(
					type = PlaybackOriginType.Artist,
					id = "stale-iu-id",
					title = "IU"
				)
			),
			artists = listOf(
				MostPlayedShortcutArtistArtwork(
					id = "current-iu-id",
					name = "IU",
					coverArtId = null,
					artistImageUrl = "https://aurral.example.com/iu.webp"
				)
			)
		)

		assertEquals("current-iu-id", resolved.single().id)
		assertEquals("IU", resolved.single().title)
	}

	@Test
	fun missingArtistShortcutIsSuppressedInsteadOfOpeningBrokenArtistDetail() {
		val resolved = mostPlayedShortcutsWithResolvedLocalArtists(
			shortcuts = listOf(
				mostPlayedShortcut(
					type = PlaybackOriginType.Artist,
					id = "missing-artist-id",
					title = "Missing Artist"
				)
			),
			artists = emptyList()
		)

		assertEquals(emptyList(), resolved)
	}

	@Test
	fun nonArtistShortcutsAreNotSuppressedByArtistResolution() {
		val shortcut = mostPlayedShortcut(
			type = PlaybackOriginType.Album,
			id = "album-id",
			title = "Album"
		)

		val resolved = mostPlayedShortcutsWithResolvedLocalArtists(
			shortcuts = listOf(shortcut),
			artists = emptyList()
		)

		assertEquals(listOf(shortcut), resolved)
	}

	private fun mostPlayedShortcut(
		type: PlaybackOriginType,
		id: String,
		title: String
	) = DomainMostPlayedShortcut(
		type = type,
		id = id,
		title = title,
		subtitle = null,
		coverArtId = null,
		totalPlayedMillis = 1_000L,
		lastPlayedAt = Instant.fromEpochMilliseconds(1_000L)
	)
}
