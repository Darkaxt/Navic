package paige.navic.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.ui.screens.aurral.AurralDiscoveryCollectionKind

class LibraryRowOrderPolicyTest {
	@Test
	fun defaultEffectiveOrderMatchesCurrentLibraryLayout() {
		assertEquals(
			listOf(
				LibraryRowId.QuickPicks,
				LibraryRowId.MostPlayed,
				LibraryRowId.NewestAlbums,
				LibraryRowId.StarredAlbums,
				LibraryRowId.RecentAlbums,
				LibraryRowId.Stations,
				LibraryRowId.Playlists,
				LibraryRowId.MoodMixes,
				LibraryRowId.GenreMixes,
				LibraryRowId.Artists,
				LibraryRowId.Genres,
				LibraryRowId.AurralRecentlyAdded,
				LibraryRowId.AurralRecentReleases,
				LibraryRowId.AurralRecommended,
				LibraryRowId.AurralBasedOnLibrary,
				LibraryRowId.AurralGlobalTop,
				LibraryRowId.AurralGenreRows,
				LibraryRowId.AurralTags
			),
			effectiveLibraryRowOrder("")
		)
	}

	@Test
	fun customOrderIsPreservedAndMissingRowsAppendVisibleAtTheEnd() {
		val savedOrder = libraryRowOrderPreference(
			listOf(
				LibraryRowId.Artists,
				LibraryRowId.Playlists,
				LibraryRowId.QuickPicks
			)
		)

		val effective = effectiveLibraryRowOrder(savedOrder)

		assertEquals(LibraryRowId.Artists, effective[0])
		assertEquals(LibraryRowId.Playlists, effective[1])
		assertEquals(LibraryRowId.QuickPicks, effective[2])
		assertTrue(effective.drop(3).contains(LibraryRowId.MostPlayed))
		assertTrue(effective.drop(3).contains(LibraryRowId.AurralTags))
		assertEquals(DefaultLibraryRowOrder.size, effective.size)
	}

	@Test
	fun hiddenRowsAreRemovedFromRenderPlanButRemainInSettingsOrder() {
		val hidden = libraryRowHiddenPreference(
			setOf(LibraryRowId.QuickPicks, LibraryRowId.AurralTags)
		)

		val settingsRows = effectiveLibraryRowOrder("")
		val renderRows = visibleLibraryRows("", hidden)

		assertTrue(settingsRows.contains(LibraryRowId.QuickPicks))
		assertTrue(settingsRows.contains(LibraryRowId.AurralTags))
		assertFalse(renderRows.contains(LibraryRowId.QuickPicks))
		assertFalse(renderRows.contains(LibraryRowId.AurralTags))
	}

	@Test
	fun obsoleteSavedIdsAreIgnored() {
		val savedOrder = "obsolete|${LibraryRowId.Genres.preferenceId}|${LibraryRowId.Artists.preferenceId}|obsolete"
		val hidden = "obsolete|${LibraryRowId.Genres.preferenceId}"

		assertEquals(
			listOf(LibraryRowId.Genres, LibraryRowId.Artists),
			effectiveLibraryRowOrder(savedOrder).take(2)
		)
		assertFalse(visibleLibraryRows(savedOrder, hidden).contains(LibraryRowId.Genres))
		assertTrue(visibleLibraryRows(savedOrder, hidden).contains(LibraryRowId.Artists))
	}

	@Test
	fun aurralCollectionKindsMapToManagedRows() {
		assertEquals(
			LibraryRowId.AurralRecentlyAdded,
			libraryRowIdForAurralKind(AurralDiscoveryCollectionKind.RecentlyAddedArtists)
		)
		assertEquals(
			LibraryRowId.AurralRecentReleases,
			libraryRowIdForAurralKind(AurralDiscoveryCollectionKind.RecentReleases)
		)
		assertEquals(
			LibraryRowId.AurralRecommended,
			libraryRowIdForAurralKind(AurralDiscoveryCollectionKind.RecommendedArtists)
		)
		assertEquals(
			LibraryRowId.AurralBasedOnLibrary,
			libraryRowIdForAurralKind(AurralDiscoveryCollectionKind.BasedOnArtists)
		)
		assertEquals(
			LibraryRowId.AurralGlobalTop,
			libraryRowIdForAurralKind(AurralDiscoveryCollectionKind.GlobalTopArtists)
		)
		assertEquals(
			LibraryRowId.AurralGenreRows,
			libraryRowIdForAurralKind(AurralDiscoveryCollectionKind.GenreArtists)
		)
		assertEquals(
			LibraryRowId.AurralTags,
			libraryRowIdForAurralKind(AurralDiscoveryCollectionKind.TopTags)
		)
	}

	@Test
	fun rowMoveReturnsStablePreferenceOrder() {
		val moved = moveLibraryRow(
			rows = DefaultLibraryRowOrder,
			fromIndex = 0,
			toIndex = 2
		)

		assertEquals(
			listOf(
				LibraryRowId.MostPlayed,
				LibraryRowId.NewestAlbums,
				LibraryRowId.QuickPicks
			),
			moved.take(3)
		)
		assertEquals(libraryRowOrderPreference(moved), libraryRowOrderPreference(moved))
	}
}
