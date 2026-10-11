package paige.navic.shared

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.domain.manager.PlaybackAccountBoundary
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import paige.navic.ui.core.PlayerUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidPlaybackTransportCoordinatorTest {

	@Test
	fun newestNavigationWaitsForInitialStateAndEngineBeforeActing() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		val ready = CompletableDeferred<Unit>()
		val state = MutableStateFlow(snapshot())
		val connected = MutableStateFlow<ExoPlayer?>(player)
		val boundary = PlaybackAccountBoundary("account", dispatcher = StandardTestDispatcher(testScheduler))
		val commands = mutableListOf<String>()
		val coordinator = coordinator(this, connected, state, boundary, { ready.await() }) {
			player.setMediaItems(items(), 0, 60_000L); true
		}
		try {
			coordinator.run("resume", false, {}) { commands += "resume"; it.play() }
			runCurrent()
			assertEquals(0, player.mediaItemCount)
			coordinator.run("next", false, {}) { commands += "next"; it.seekToNextMediaItem() }
			runCurrent()
			ready.complete(Unit)
			runCurrent()
			assertEquals(listOf("next"), commands)
			assertEquals("song-1", player.currentMediaItem?.mediaId)
			assertFalse(player.playWhenReady)
			assertFalse(state.value.isLoading)
		} finally { coordinator.cancel(); player.release() }
	}

	@Test
	fun pauseCancellationWhileWaitingCannotRestoreOrStartPlayback() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		val ready = CompletableDeferred<Unit>()
		val state = MutableStateFlow(snapshot())
		val connected = MutableStateFlow<ExoPlayer?>(player)
		val boundary = PlaybackAccountBoundary("account", dispatcher = StandardTestDispatcher(testScheduler))
		var restores = 0
		val coordinator = coordinator(this, connected, state, boundary, { ready.await() }) { restores++; true }
		try {
			coordinator.run("resume", false, {}) { it.play() }
			runCurrent()
			coordinator.cancel()
			ready.complete(Unit)
			runCurrent()
			assertEquals(0, restores)
			assertEquals(2, state.value.queue.size)
			assertFalse(player.playWhenReady)
		} finally { coordinator.cancel(); player.release() }
	}

	@Test
	fun reconnectionDuringRestoreAppliesTheCommandOnlyToTheNewPlayer() = runTest {
		val old = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		val new = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		val mapped = CompletableDeferred<Unit>()
		val state = MutableStateFlow(snapshot())
		val connected = MutableStateFlow<ExoPlayer?>(old)
		val boundary = PlaybackAccountBoundary("account", dispatcher = StandardTestDispatcher(testScheduler))
		var restores = 0
		val coordinator = coordinator(this, connected, state, boundary, {}) {
			restores++
			val target = connected.value!!
			mapped.await()
			target.setMediaItems(items(), 0, 60_000L)
			true
		}
		try {
			coordinator.run("resume", false, {}) { it.play() }
			runCurrent()
			connected.value = new
			mapped.complete(Unit)
			runCurrent()
			assertEquals(2, restores)
			assertFalse(old.playWhenReady)
			assertTrue(new.playWhenReady)
			assertEquals(60_000L, new.currentPosition)
		} finally { coordinator.cancel(); old.release(); new.release() }
	}

	@Test
	fun cancellationInsideNavigationPreventsItsPreservedPlayIntent() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		val state = MutableStateFlow(snapshot().copy(isPaused = false))
		val connected = MutableStateFlow<ExoPlayer?>(player)
		val boundary = PlaybackAccountBoundary("account", dispatcher = StandardTestDispatcher(testScheduler))
		val coordinator = coordinator(this, connected, state, boundary, {}) {
			player.setMediaItems(items(), 0, 60_000L); true
		}
		try {
			coordinator.run("next", true, {}) { it.seekToNextMediaItem(); coordinator.cancel() }
			runCurrent()
			assertEquals("song-1", player.currentMediaItem?.mediaId)
			assertFalse(player.playWhenReady)
		} finally { coordinator.cancel(); player.release() }
	}

	@Test
	fun accountRevocationDuringRestoreCannotExecuteTheCommand() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		val mapped = CompletableDeferred<Unit>()
		val state = MutableStateFlow(snapshot())
		val connected = MutableStateFlow<ExoPlayer?>(player)
		val boundary = PlaybackAccountBoundary("account", dispatcher = StandardTestDispatcher(testScheduler))
		val coordinator = coordinator(this, connected, state, boundary, {}) { mapped.await(); true }
		try {
			coordinator.run("resume", false, {}) { it.play() }
			runCurrent()
			boundary.transitionTo("other")
			mapped.complete(Unit)
			runCurrent()
			assertFalse(player.playWhenReady)
			assertFalse(state.value.isLoading)
		} finally { coordinator.cancel(); player.release() }
	}

	private fun coordinator(scope: CoroutineScope, connected: MutableStateFlow<ExoPlayer?>,
		state: MutableStateFlow<PlayerUiState>, boundary: PlaybackAccountBoundary,
		initial: suspend () -> Unit, restore: suspend (PlayerUiState) -> Boolean) =
		AndroidPlaybackTransportCoordinator(scope, { connected.value }, connected, state, boundary, initial,
			restore, {}, { false }, { error("Unexpected playback failure") })

	private fun items() = (0..1).map { MediaItem.Builder().setMediaId("song-$it").setUri("file:///music/$it.flac").build() }
	private fun snapshot() = PlayerUiState(queue = (0..1).map { song("song-$it") }, currentIndex = 0,
		isPaused = true, playbackOwnerId = "account")
	private fun song(id: String) = DomainSong(id = id, title = id, artistName = "Artist", artistId = null,
		albumTitle = null, albumId = null, parentId = null, comment = null, trackNumber = null, discNumber = null,
		isrc = emptyList(), year = null, genre = null, genres = emptyList(), moods = emptyList(), duration = 100.seconds,
		bpm = null, contributors = emptyList(), userRating = null, averageRating = null, bitRate = null,
		bitDepth = null, sampleRate = null, audioChannelCount = null, replayGain = null, fileSize = 100,
		fileExtension = "flac", mimeType = "audio/flac", filePath = null, starredAt = null, coverArtId = null,
		musicBrainzId = null, explicitStatus = DomainExplicitStatus.Unknown)
}
