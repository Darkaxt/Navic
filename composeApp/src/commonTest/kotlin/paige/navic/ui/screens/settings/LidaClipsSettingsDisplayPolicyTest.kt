package paige.navic.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.repositories.LidaClipsHealthCheck
import paige.navic.domain.repositories.LidaClipsRecentFailure

class LidaClipsSettingsDisplayPolicyTest {
	@Test
	fun recentClipTitlePrefersTrackThenTitleThenFileName() {
		assertEquals(
			"Track title",
			lidaClipsRecentClipTitle(
				lidaClip(
					track = " Track title ",
					title = "Clip title",
					fileName = "clip.mp4"
				)
			)
		)
		assertEquals(
			"Clip title",
			lidaClipsRecentClipTitle(
				lidaClip(
					track = " ",
					title = " Clip title ",
					fileName = "clip.mp4"
				)
			)
		)
		assertEquals(
			"clip.mp4",
			lidaClipsRecentClipTitle(
				lidaClip(
					track = null,
					title = " ",
					fileName = " clip.mp4 "
				)
			)
		)
		assertEquals(
			"Music video",
			lidaClipsRecentClipTitle(
				lidaClip(
					track = null,
					title = " ",
					fileName = null
				)
			)
		)
	}

	@Test
	fun recentClipSubtitleSkipsSyntheticUnknownArtist() {
		assertEquals(
			"Album - Official",
			lidaClipsRecentClipSubtitle(
				lidaClip(
					artist = "[Unknown Artist]",
					album = " Album ",
					qualityTier = " Official "
				)
			)
		)
		assertEquals(
			"Real Artist - Album - Fallback",
			lidaClipsRecentClipSubtitle(
				lidaClip(
					artist = " Real Artist ",
					album = " Album ",
					qualityTier = " Fallback "
				)
			)
		)
		assertNull(
			lidaClipsRecentClipSubtitle(
				lidaClip(
					artist = "[Unknown Artist]",
					album = " ",
					qualityTier = null
				)
			)
		)
	}

	@Test
	fun recentFailureTitleSkipsSyntheticUnknownArtistButKeepsTrackOrAlbum() {
		assertEquals(
			"Track title",
			lidaClipsRecentFailureTitle(
				failure = failure(
					artist = "[Unknown Artist]",
					track = " Track title ",
					album = " Album title "
				),
				unknownTrackText = "Unknown track",
				lidarrTrackText = null
			)
		)
		assertEquals(
			"Album title",
			lidaClipsRecentFailureTitle(
				failure = failure(
					artist = "[Unknown Artist]",
					track = " ",
					album = " Album title "
				),
				unknownTrackText = "Unknown track",
				lidarrTrackText = null
			)
		)
		assertEquals(
			"Real Artist - Track title",
			lidaClipsRecentFailureTitle(
				failure = failure(
					artist = " Real Artist ",
					track = " Track title ",
					album = " Album title "
				),
				unknownTrackText = "Unknown track",
				lidarrTrackText = null
			)
		)
	}

	@Test
	fun recentFailureTitleFallsBackToLidarrTrackThenUnknownTrack() {
		assertEquals(
			"Lidarr track #42",
			lidaClipsRecentFailureTitle(
				failure = failure(lidarrTrackId = 42),
				unknownTrackText = "Unknown track",
				lidarrTrackText = "Lidarr track #42"
			)
		)
		assertEquals(
			"Unknown track",
			lidaClipsRecentFailureTitle(
				failure = failure(),
				unknownTrackText = "Unknown track",
				lidarrTrackText = null
			)
		)
	}

	@Test
	fun healthFailureDisplayUsesErrorThenAddressThenPath() {
		assertEquals(
			LidaClipsHealthFailureDisplay(name = "media scanner", detail = "boom"),
			lidaClipsHealthFailureDisplay(
				LidaClipsHealthCheck(
					name = "media_scanner",
					ok = false,
					error = " boom ",
					address = "http://service",
					path = "/health",
					skipped = false
				)
			)
		)
		assertEquals(
			LidaClipsHealthFailureDisplay(name = "disk", detail = "/clips"),
			lidaClipsHealthFailureDisplay(
				LidaClipsHealthCheck(
					name = "disk",
					ok = false,
					error = " ",
					address = null,
					path = " /clips ",
					skipped = false
				)
			)
		)
	}

	private fun lidaClip(
		title: String = "Clip title",
		artist: String? = "Artist",
		album: String? = "Album",
		track: String? = "Track title",
		qualityTier: String? = "Official",
		fileName: String? = "clip.mp4"
	) = DomainLidaClip(
		id = 1,
		navidromeSongId = "song-1",
		title = title,
		artist = artist,
		album = album,
		track = track,
		durationSeconds = 180,
		mimeType = "video/mp4",
		score = 1f,
		qualityTier = qualityTier,
		fileName = fileName,
		streamUrl = "https://clips.remaxku.eu/api/v1/stream/1"
	)

	private fun failure(
		lidarrTrackId: Int? = null,
		artist: String? = null,
		album: String? = null,
		track: String? = null
	) = LidaClipsRecentFailure(
		lidarrTrackId = lidarrTrackId,
		artist = artist,
		album = album,
		track = track,
		reason = null,
		retryAfter = null,
		updatedAt = null
	)
}
