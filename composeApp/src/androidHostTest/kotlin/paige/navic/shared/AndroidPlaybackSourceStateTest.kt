package paige.navic.shared

import androidx.core.net.toUri
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.data.remote.SubsonicClientFactory
import paige.navic.domain.models.AurralFlowSongIdPrefix
import paige.navic.domain.models.settings.StreamingQuality
import paige.navic.ui.core.PlayerUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AndroidPlaybackSourceStateTest {
	@Test
	fun queuedWifiSourceAdoptsRealClientCellularHighParametersWithoutChangingCurrentPlayback() {
		SubsonicClientFactory().create("http://localhost", "test", "test", emptyMap()).use { client ->
			val item = MediaItem.Builder().setMediaId("song")
				.setUri(client.getStreamUrl("song", 0, null))
				.setMediaMetadata(MediaMetadata.Builder().setTitle("Retained title").build()).build()
			val high = StreamingQuality.High
			val highUri = client.getStreamUrl("song", high.bitrateAndroid, high.containerAndroid).toUri()
			assertNull(playbackSourceUpdate(item, null, true, false) { error("Current source must stay intact") })
			val updated = playbackSourceUpdate(item, null, false, false) { highUri }!!
			assertEquals("opus", updated.localConfiguration!!.uri.getQueryParameter("format"))
			assertEquals("192", updated.localConfiguration!!.uri.getQueryParameter("maxBitRate"))
			assertEquals(192, updated.requestedPlaybackBitrateKbps())
			assertEquals(item.mediaId, updated.mediaId)
			assertEquals(item.mediaMetadata, updated.mediaMetadata)
			assertNull(playbackSourceUpdate(updated, null, false, false) { highUri })
			assertNull(playbackSourceUpdate(updated, null, false, false) {
				client.getStreamUrl("song", high.bitrateAndroid, high.containerAndroid).toUri()
			}, "Repeated quality observations must settle without replacing unchanged sources")
			assertEquals(highUri, playbackSourceUpdate(item, null, true, true) { highUri }!!.localConfiguration!!.uri)
		}
	}

	@Test
	fun completedCacheWinsAndOriginalCachedAudioHasNoNetworkBitrateHint() {
		val item = MediaItem.Builder().setMediaId("song")
			.setUri("https://server/rest/stream?id=song&maxBitRate=192&format=opus").build()
		val cached = playbackSourceUpdate(item, "/music/song.flac", false, false) {
			error("No remote URL needed for completed cache")
		}!!
		assertEquals("file", cached.localConfiguration!!.uri.scheme)
		assertNull(cached.requestedPlaybackBitrateKbps())
		assertNull(playbackSourceUpdate(cached, "/music/song.flac", false, false) { error("No remote URL") })
		val missing = playbackSourceUpdate(cached, null, false, false) { item.localConfiguration!!.uri }!!
		assertEquals(item.localConfiguration!!.uri, missing.localConfiguration!!.uri)
	}

	@Test
	fun directRadioAndFlowSourcesAreNotConvertedIntoNavidromeRequests() {
		for (id in listOf("radio_1", "${AurralFlowSongIdPrefix}1")) {
			val item = MediaItem.Builder().setMediaId(id).setUri("https://direct/audio").build()
			assertNull(playbackSourceUpdate(item, null, false, false) { error("Not a Navidrome song") })
		}
	}

	@Test
	fun missingSelectedAudioClearsThePreviousDecoderDetails() {
		val previous = PlayerUiState(playbackMimeType = "audio/flac", playbackBitrate = 1681000,
			playbackSampleRate = 96000)
		val cleared = previous.withPlaybackTracks(Tracks.EMPTY)
		assertNull(cleared.playbackMimeType)
		assertNull(cleared.playbackBitrate)
		assertNull(cleared.playbackSampleRate)
		val format = Format.Builder().setSampleMimeType("audio/opus").setSampleRate(48000).build()
		val tracks = Tracks(listOf(Tracks.Group(TrackGroup(format), false, intArrayOf(4), booleanArrayOf(true))))
		val current = cleared.withPlaybackTracks(tracks)
		assertEquals("audio/opus", current.playbackMimeType)
		assertEquals(48000, current.playbackSampleRate)
		assertNull(current.playbackBitrate)
	}
}
