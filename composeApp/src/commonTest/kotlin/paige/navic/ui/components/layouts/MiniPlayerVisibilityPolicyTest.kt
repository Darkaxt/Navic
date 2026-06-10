package paige.navic.ui.components.layouts

import paige.navic.ui.navigation.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiniPlayerVisibilityPolicyTest {
	@Test
	fun hidesMusicMiniPlayerOnAudiobookSurfaces() {
		assertFalse(
			shouldShowMiniPlayerForRoute(
				screen = Screen.Audiobooks,
				playbackKind = MiniPlayerPlaybackKind.Music,
				binderyEnabled = true
			)
		)
		assertFalse(
			shouldShowMiniPlayerForRoute(
				screen = Screen.BinderyBook("3816", "The Hobbit"),
				playbackKind = MiniPlayerPlaybackKind.None,
				binderyEnabled = true
			)
		)
	}

	@Test
	fun keepsMiniPlayerVisibleOnAudiobookSurfacesWhenAudiobookIsPlaying() {
		assertTrue(
			shouldShowMiniPlayerForRoute(
				screen = Screen.BinderyBook("3816", "The Hobbit"),
				playbackKind = MiniPlayerPlaybackKind.Audiobook,
				binderyEnabled = true
			)
		)
	}

	@Test
	fun selectsAudiobookMiniPlayerOnAudiobookSurfaceWhenSessionExists() {
		assertEquals(
			MiniPlayerPlaybackKind.Audiobook,
			rootMiniPlayerPlaybackKind(
				screen = Screen.Audiobooks,
				hasMusicPlayback = true,
				audiobookAvailable = true,
				audiobookPlaying = false,
				binderyEnabled = true
			)
		)
	}

	@Test
	fun keepsMiniPlayersScopedToTheirAreaEvenWhenOtherPlaybackIsActive() {
		assertEquals(
			MiniPlayerPlaybackKind.Music,
			rootMiniPlayerPlaybackKind(
				screen = Screen.Library(),
				hasMusicPlayback = true,
				audiobookAvailable = true,
				audiobookPlaying = true,
				binderyEnabled = true
			)
		)
		assertEquals(
			MiniPlayerPlaybackKind.None,
			rootMiniPlayerPlaybackKind(
				screen = Screen.Library(),
				hasMusicPlayback = false,
				audiobookAvailable = true,
				audiobookPlaying = true,
				binderyEnabled = true
			)
		)
	}

	@Test
	fun keepsMusicMiniPlayerVisibleOutsideAudiobookSurfaces() {
		assertTrue(
			shouldShowMiniPlayerForRoute(
				screen = Screen.Library(),
				playbackKind = MiniPlayerPlaybackKind.Music,
				binderyEnabled = true
			)
		)
		assertTrue(
			shouldShowMiniPlayerForRoute(
				screen = Screen.Settings.Playback,
				playbackKind = MiniPlayerPlaybackKind.Music,
				binderyEnabled = true
			)
		)
	}

	@Test
	fun disabledBinderyDoesNotTreatStaleAudiobookRoutesAsAudiobookSurfaces() {
		assertTrue(
			shouldShowMiniPlayerForRoute(
				screen = Screen.Audiobooks,
				playbackKind = MiniPlayerPlaybackKind.Music,
				binderyEnabled = false
			)
		)
	}
}
