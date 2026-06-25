package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackErrorNoticePolicyTest {
	@Test
	fun missingSourceErrorsShowSongNotFound() {
		assertEquals(
			PlaybackErrorNotice.SongNotFound,
			playbackErrorNotice(
				errorCodeName = "ERROR_CODE_IO_FILE_NOT_FOUND",
				message = "Source error"
			)
		)
		assertEquals(
			PlaybackErrorNotice.SongNotFound,
			playbackErrorNotice(
				errorCodeName = null,
				message = "Stream request failed: HTTP 404 Not Found"
			)
		)
	}

	@Test
	fun invalidAudioOrCachedDownloadErrorsShowFailedDownload() {
		assertEquals(
			PlaybackErrorNotice.FailedDownload,
			playbackErrorNotice(
				errorCodeName = "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED",
				message = "Source error",
				details = listOf("None of the available extractors could read the stream")
			)
		)
		assertEquals(
			PlaybackErrorNotice.FailedDownload,
			playbackErrorNotice(
				errorCodeName = null,
				message = "Stream request returned non-audio content for song: text/html"
			)
		)
	}

	@Test
	fun unknownPlaybackErrorsUseGenericMessage() {
		assertEquals(
			PlaybackErrorNotice.FailedToPlaySong,
			playbackErrorNotice(
				errorCodeName = "ERROR_CODE_DECODING_FAILED",
				message = "Decoder failed"
			)
		)
	}
}
