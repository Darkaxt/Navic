package paige.navic.shared

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import paige.navic.domain.manager.PlaybackAccountBoundary
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import paige.navic.ui.core.PlayerUiState
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidPlaybackResumptionTest {

	@Test
	fun controllerPlayRestores409ItemsAndSettingsWithoutAViewModel() = runTest {
		val state = snapshot(409).copy(currentIndex = 37, progress = .6f,
			repeatMode = Player.REPEAT_MODE_ALL, playbackSpeed = 1.25f, playbackPitch = .95f)
		Fixture(this, StandardTestDispatcher(testScheduler), { Json.encodeToString(state) }).use { fixture ->
			fixture.controller.play()
			fixture.drainController()
			runCurrent()
			fixture.drainController()
			assertEquals(409, fixture.player.mediaItemCount)
			assertEquals("song-37", fixture.player.currentMediaItem?.mediaId)
			assertEquals(60_000L, fixture.player.currentPosition)
			assertTrue(fixture.player.playWhenReady)
			assertEquals(Player.REPEAT_MODE_ALL, fixture.player.repeatMode)
			assertEquals(1.25f, fixture.player.playbackParameters.speed)
			assertEquals(.95f, fixture.player.playbackParameters.pitch)
			assertEquals("file", fixture.player.currentMediaItem?.localConfiguration?.uri?.scheme)
		}
	}

	@Test
	fun controllerPauseCancelsDelayedResumptionWithoutFrameworkAutoplay() = runTest {
		val loaded = CompletableDeferred<String?>()
		Fixture(this, StandardTestDispatcher(testScheduler), { loaded.await() }).use { fixture ->
			fixture.controller.play()
			fixture.drainController()
			runCurrent()
			fixture.controller.pause()
			fixture.drainController()
			loaded.complete(Json.encodeToString(snapshot(409)))
			runCurrent()
			fixture.drainController()
			assertEquals(0, fixture.player.mediaItemCount)
			assertFalse(fixture.player.playWhenReady)
		}
	}

	@Test
	fun clearPreventsNewPlayFromReadingAnObsoletePersistedQueue() = runTest {
		Fixture(this, StandardTestDispatcher(testScheduler), { Json.encodeToString(snapshot(3)) }).use { fixture ->
			fixture.controller.clearMediaItems()
			fixture.drainController()
			fixture.controller.play()
			fixture.drainController()
			runCurrent()
			fixture.drainController()
			assertEquals(0, fixture.player.mediaItemCount)
			assertFalse(fixture.player.playWhenReady)
		}
	}

	@Test
	fun replacementDuringLoadIsNotOverwrittenOrStartedByTheOldRequest() = runTest {
		val loaded = CompletableDeferred<String?>()
		Fixture(this, StandardTestDispatcher(testScheduler), { loaded.await() }).use { fixture ->
			fixture.controller.play()
			fixture.drainController()
			runCurrent()
			fixture.controller.setMediaItem(item(song("replacement")), 17_000L)
			fixture.drainController()
			loaded.complete(Json.encodeToString(snapshot(409)))
			runCurrent()
			fixture.drainController()
			assertEquals(1, fixture.player.mediaItemCount)
			assertEquals("replacement", fixture.player.currentMediaItem?.mediaId)
			assertEquals(17_000L, fixture.player.currentPosition)
			assertFalse(fixture.player.playWhenReady)
		}
	}

	@Test
	fun revokedAccountCannotCompleteItsPendingResumption() = runTest {
		val loaded = CompletableDeferred<String?>()
		Fixture(this, StandardTestDispatcher(testScheduler), { loaded.await() }).use { fixture ->
			fixture.controller.play()
			fixture.drainController()
			runCurrent()
			fixture.boundary.transitionTo("other")
			loaded.complete(Json.encodeToString(snapshot(3)))
			runCurrent()
			fixture.drainController()
			assertEquals(0, fixture.player.mediaItemCount)
			assertFalse(fixture.player.playWhenReady)
		}
	}

	@Test
	fun newerPlayAfterPauseSupersedesThePendingRequestAndStillResumes() = runTest {
		val loaded = CompletableDeferred<String?>()
		Fixture(this, StandardTestDispatcher(testScheduler), { loaded.await() }).use { fixture ->
			fixture.controller.play()
			fixture.drainController()
			runCurrent()
			fixture.controller.pause()
			fixture.drainController()
			fixture.controller.play()
			fixture.drainController()
			runCurrent()
			loaded.complete(Json.encodeToString(snapshot(3)))
			runCurrent()
			fixture.drainController()
			assertEquals(3, fixture.player.mediaItemCount)
			assertTrue(fixture.player.playWhenReady)
		}
	}

	@Test
	fun disposalCannotFinishAQueuedPlayRequest() = runTest {
		val loaded = CompletableDeferred<String?>()
		Fixture(this, StandardTestDispatcher(testScheduler), { loaded.await() }).use { fixture ->
			fixture.controller.play()
			fixture.drainController()
			runCurrent()
			fixture.resumption.close()
			loaded.complete(Json.encodeToString(snapshot(3)))
			runCurrent()
			fixture.drainController()
			assertEquals(0, fixture.player.mediaItemCount)
			assertFalse(fixture.player.playWhenReady)
		}
	}

	@Test
	fun missingMalformedDisabledAndForeignStateNeverAutoplay() = runTest {
		for ((json, enabled) in listOf(null to true, "not-json" to true,
			Json.encodeToString(snapshot(3)) to false,
			Json.encodeToString(snapshot(3).copy(playbackOwnerId = "other")) to true)) {
			Fixture(this, StandardTestDispatcher(testScheduler), { json }, { enabled }).use { fixture ->
				fixture.controller.play()
				fixture.drainController()
				runCurrent()
				fixture.drainController()
				assertEquals(0, fixture.player.mediaItemCount)
				assertFalse(fixture.player.playWhenReady)
			}
		}
	}

	@Test
	fun shuffleIsAppliedAfterTheFrameworkInstallsThePlaylist() = runTest {
		val state = snapshot(4).copy(currentIndex = 2, isShuffleEnabled = true,
			shuffleOrder = listOf(2, 0, 3, 1), upcomingIndexes = listOf(0, 3, 1))
		Fixture(this, StandardTestDispatcher(testScheduler), { Json.encodeToString(state) }).use { fixture ->
			fixture.controller.play()
			fixture.drainController()
			runCurrent()
			fixture.drainController()
			assertTrue(fixture.player.shuffleModeEnabled)
			fixture.player.seekToNextMediaItem()
			assertEquals("song-0", fixture.player.currentMediaItem?.mediaId)
			fixture.player.seekToNextMediaItem()
			assertEquals("song-3", fixture.player.currentMediaItem?.mediaId)
		}
	}

	@Test
	fun metadataOnlyResumptionDoesNotPopulateOrStartTheEngine() = runTest {
		Fixture(this, StandardTestDispatcher(testScheduler), { Json.encodeToString(snapshot(4).copy(currentIndex = 2)) }).use { fixture ->
			val result = fixture.resumption.request(isForPlayback = false)
			runCurrent()
			assertTrue(result.isDone)
			assertEquals(listOf("song-2"), result.get().mediaItems.map { it.mediaId })
			assertEquals(0, fixture.player.mediaItemCount)
			assertFalse(fixture.player.playWhenReady)
		}
	}

	@Test
	fun newPlaybackSettingsDuringLoadWinWithoutCancellingPlay() = runTest {
		val loaded = CompletableDeferred<String?>()
		Fixture(this, StandardTestDispatcher(testScheduler), { loaded.await() }).use { fixture ->
			fixture.controller.play()
			fixture.drainController()
			runCurrent()
			fixture.controller.setPlaybackSpeed(1.5f)
			fixture.controller.repeatMode = Player.REPEAT_MODE_ALL
			fixture.controller.shuffleModeEnabled = true
			fixture.controller.volume = .5f
			fixture.drainController()
			loaded.complete(Json.encodeToString(snapshot(4)))
			runCurrent()
			fixture.drainController()
			assertEquals(4, fixture.player.mediaItemCount)
			assertTrue(fixture.player.playWhenReady)
			assertEquals(1.5f, fixture.player.playbackParameters.speed)
			assertEquals(Player.REPEAT_MODE_ALL, fixture.player.repeatMode)
			assertTrue(fixture.player.shuffleModeEnabled)
			assertEquals(.5f, fixture.player.volume)
		}
	}

	@Test
	fun stopDuringFrameworkPlaylistInstallationStillPreventsTheOldPlayAction() = runTest {
		Fixture(this, StandardTestDispatcher(testScheduler), { Json.encodeToString(snapshot(3)) }).use { fixture ->
			var stopped = false
			fixture.player.addListener(object : Player.Listener {
				override fun onTimelineChanged(timeline: Timeline, reason: Int) {
					if (!timeline.isEmpty && !stopped) {
						stopped = true
						fixture.resumption.onPlayerCommand(Player.COMMAND_STOP)
						fixture.player.stop()
					}
				}
			})
			fixture.controller.play()
			fixture.drainController()
			runCurrent()
			fixture.drainController()
			assertTrue(stopped)
			assertFalse(fixture.player.playWhenReady)
			assertEquals(Player.STATE_IDLE, fixture.player.playbackState)
		}
	}

	private class Fixture(
		scope: CoroutineScope,
		dispatcher: kotlinx.coroutines.CoroutineDispatcher,
		load: suspend () -> String?,
		enabled: () -> Boolean = { true }
	) : AutoCloseable {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		val boundary = PlaybackAccountBoundary("account", dispatcher = dispatcher)
		val resumption = AndroidPlaybackResumption(scope, player, boundary, enabled, load, ::item, dispatcher)
		private val session = MediaSession.Builder(ApplicationProvider.getApplicationContext(),
			stablePlaybackSessionPlayer(player, resumption::allowsFrameworkPlaybackMutation, resumption::onPlaylistInstalled))
			.setId(UUID.randomUUID().toString())
			.setCallback(object : MediaSession.Callback {
				@Suppress("OVERRIDE_DEPRECATION")
				override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
					resumption.onPlayerCommand(playerCommand)
					return SessionResult.RESULT_SUCCESS
				}
				override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo,
					isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = resumption.request(isForPlayback)
			}).build()
		val controller: MediaController
		init {
			val connected = MediaController.Builder(ApplicationProvider.getApplicationContext(), session.token).buildAsync()
			try {
				drainController()
				assertTrue(connected.isDone, "The real controller must connect before issuing commands")
				controller = connected.get()
			} catch (error: Throwable) {
				resumption.close()
				MediaController.releaseFuture(connected)
				session.release()
				player.release()
				throw error
			}
		}
		fun drainController() { shadowOf(Looper.getMainLooper()).idle() }
		override fun close() {
			resumption.close()
			controller.release()
			session.release()
			player.release()
		}
	}

	companion object {
		private fun snapshot(count: Int) = PlayerUiState(queue = List(count) { song("song-$it") }, currentIndex = 0,
			isPaused = true, playbackOwnerId = "account")
		private fun item(song: DomainSong) = MediaItem.Builder().setMediaId(song.id).setUri("file:///music/${song.id}.flac").build()
		private fun song(id: String) = DomainSong(id = id, title = id, artistName = "Artist", artistId = null,
			albumTitle = null, albumId = null, parentId = null, comment = null, trackNumber = null, discNumber = null,
			isrc = emptyList(), year = null, genre = null, genres = emptyList(), moods = emptyList(), duration = 100.seconds,
			bpm = null, contributors = emptyList(), userRating = null, averageRating = null, bitRate = null,
			bitDepth = null, sampleRate = null, audioChannelCount = null, replayGain = null, fileSize = 100,
			fileExtension = "flac", mimeType = "audio/flac", filePath = null, starredAt = null, coverArtId = null,
			musicBrainzId = null, explicitStatus = DomainExplicitStatus.Unknown)
	}
}
