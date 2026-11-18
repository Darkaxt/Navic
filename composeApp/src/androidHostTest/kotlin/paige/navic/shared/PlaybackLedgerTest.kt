package paige.navic.shared

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PlaybackLedgerTest {
	private var now = 0L
	private fun recorder() = PlaybackLedgerRecorder(elapsed = { now }, wall = { 1_700_000_000_000L + now })

	@Test
	fun aSongThatFinishesShortOfTheServerLengthIsFlaggedEndedEarly() {
		val recorder = recorder()
		recorder.begin("s1", "Song", "Artist", serverDurationMs = 240_000, source = "stream", isPlaying = true, positionMs = 0)
		now += 30_000
		recorder.onPlayerDuration(31_000)
		val entry = recorder.end(ledgerEndReason(Player.DISCONTINUITY_REASON_AUTO_TRANSITION), 31_000, streamDetail = "opus192")!!

		assertEquals("finished", entry.endReason)
		assertEquals(30_000, entry.playedMs)
		assertEquals("stream:opus192", entry.source)
		assertEquals(listOf("ended-early", "short-stream"), entry.flags)
		assertNull(recorder.currentSongId)
	}

	@Test
	fun playedTimeStallsErrorsAndSilenceAccumulatePerSong() {
		val recorder = recorder()
		recorder.begin("s2", "Song", null, serverDurationMs = 100_000, source = "local", isPlaying = false, positionMs = 0)
		recorder.onPlaybackState(buffering = false, ready = true, playWhenReady = true)
		recorder.onIsPlaying(true, 0)
		now += 10_000
		recorder.onIsPlaying(false, 10_000)
		recorder.onPlaybackState(buffering = true, ready = false, playWhenReady = true)
		now += 3_000
		recorder.onPlaybackState(buffering = false, ready = true, playWhenReady = true)
		recorder.onIsPlaying(true, 10_000)
		recorder.onError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED")
		recorder.onSilence(lengthMs = 4_000, atMs = 15_000)
		now += 5_000
		val entry = recorder.end("skipped", endPositionMs = null, streamDetail = "opus192")!!

		assertEquals(15_000, entry.playedMs)
		assertEquals(1, entry.stalls)
		assertEquals(3_000, entry.stallMs)
		assertEquals(15_000, entry.endPositionMs, "estimated from the last anchor while playing")
		assertEquals("local", entry.source, "stream detail only refines streamed songs")
		assertEquals(listOf(PlaybackLedgerSilence(15_000, 4_000)), entry.silences)
		assertEquals(listOf("stalled", "silence", "error"), entry.flags)
	}

	@Test
	fun ledgerTextIsReadable() {
		val text = formatPlaybackLedger(
			listOf(
				PlaybackLedgerEntry(
					startedAt = 0, songId = "abc", title = "Song", artist = "Artist", source = "stream:opus192",
					serverDurationMs = 225_000, playerDurationMs = 31_000, playedMs = 31_000, endPositionMs = 31_000,
					endReason = "finished", stalls = 1, stallMs = 3_200,
					silences = listOf(PlaybackLedgerSilence(70_000, 4_000)), flags = listOf("ended-early")
				)
			),
			ZoneOffset.UTC
		)

		assertContains(text, "1970-01-01 00:00:00 | Song - Artist | played 0:31 | stopped at 0:31 of 3:45 (player 0:31) | finished")
		assertContains(text, "stalls 1 (3.2s) | silence 4.0s at ~1:10 | FLAGS ended-early | id abc")
	}

	@Test
	fun silenceMeterReportsLongDigitalSilenceAndPassesAudioThrough() {
		val spans = mutableListOf<Long>()
		val meter = PlaybackSilenceMeter(minSilenceMs = 2_000).apply { onSilentSpan = { spans += it } }
		meter.configure(AudioFormat(1_000, 1, C.ENCODING_PCM_16BIT))
		meter.flush()

		fun feed(frames: Int, value: Short): ByteBuffer {
			val input = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder())
			repeat(frames) { input.putShort(value) }
			input.flip()
			meter.queueInput(input)
			return meter.output
		}

		feed(1_500, 3)
		feed(10, 4_000)
		assertTrue(spans.isEmpty(), "1.5 s of silence is below the threshold")
		feed(2_500, 0)
		val output = feed(10, 4_000)
		assertEquals(listOf(2_500L), spans)
		assertEquals(20, output.remaining())
		assertEquals(4_000.toShort(), output.getShort(0))
	}

	@Test
	fun playbackServiceRecordsTheLedgerWithTheSilenceMeter() {
		val service = File("src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt").readText()
		val factory = File("src/androidMain/kotlin/paige/navic/shared/AndroidMediaItemFactory.android.kt").readText()

		assertContains(service, "ExoPlayer.Builder(this, SilenceMeteringRenderersFactory(this, silenceMeter))")
		assertContains(service, "AndroidPlaybackLedger(player, playbackLedgerStore)")
		assertContains(service, "playbackLedger?.onStreamOpen(served.songId, served.quality, fullyCached)")
		assertContains(service, "playbackLedger?.close()")
		assertContains(factory, ".setDurationMs(song.duration.inWholeMilliseconds.takeIf { it > 0 })")
	}
}
