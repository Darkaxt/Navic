package paige.navic.shared

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.ui.core.PlayerUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidPlaybackStateSynchronizerTest {

	@Test
	fun emptyEngineReconciles409SongsBeforeResumeAndNextWithoutLosingPosition() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			val state = snapshot(409).copy(currentIndex = 37, progress = .6f, repeatMode = Player.REPEAT_MODE_ALL,
				playbackSpeed = 1.25f, playbackPitch = .95f)
			val synchronizer = DefaultAndroidPlaybackStateSynchronizer(this, { player }, ::item, {},
				StandardTestDispatcher(testScheduler))
			assertTrue(synchronizer.restoreForCommand(state))
			assertEquals(409, player.mediaItemCount)
			assertEquals("song-37", player.currentMediaItem?.mediaId)
			assertEquals(60_000L, player.currentPosition)
			assertFalse(player.playWhenReady, "Restoration alone must not override the command's intent")
			assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
			assertEquals(1.25f, player.playbackParameters.speed)
			assertEquals(.95f, player.playbackParameters.pitch)
			player.play()
			assertTrue(player.playWhenReady)
			player.seekToNextMediaItem()
			assertEquals("song-38", player.currentMediaItem?.mediaId)
		} finally { player.release() }
	}

	@Test
	fun cancellationDuringMappingCannotResurrectClearedOrReplacedQueue() = runTest {
		for (replacement in listOf(false, true)) {
			val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
			try {
				val mapper = PausedMappingDispatcher()
				val synchronizer = DefaultAndroidPlaybackStateSynchronizer(this, { player }, ::item, {},
					mapper)
				val restore = async { synchronizer.restoreForCommand(snapshot(409)) }
				runCurrent()
				synchronizer.cancelPendingRestore()
				if (replacement) player.setMediaItem(item(song("replacement"))) else player.clearMediaItems()
				mapper.runCurrent()
				runCurrent()
				assertFalse(restore.await())
				assertEquals(if (replacement) "replacement" else null, player.currentMediaItem?.mediaId)
				assertFalse(player.playWhenReady)
			} finally { player.release() }
		}
	}

	@Test
	fun disconnectedEngineIsRejectedAndNewControllerCanReconcileRetainedState() = runTest {
		val old = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		val new = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			var active: Player = old
			val mapper = PausedMappingDispatcher()
			val synchronizer = DefaultAndroidPlaybackStateSynchronizer(this, { active }, ::item, {},
				mapper)
			val pending = async { synchronizer.restoreForCommand(snapshot(3)) }
			runCurrent()
			active = new
			mapper.runCurrent()
			runCurrent()
			assertFalse(pending.await())
			assertEquals(0, old.mediaItemCount)
			val retry = async { synchronizer.restoreForCommand(snapshot(3)) }
			runCurrent(); mapper.runCurrent(); runCurrent()
			assertTrue(retry.await())
			assertEquals(3, new.mediaItemCount)
		} finally { old.release(); new.release() }
	}

	@Test
	fun existingLiveQueueIsNotOverwrittenByStaleUiSnapshot() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			player.setMediaItem(item(song("live")))
			val synchronizer = DefaultAndroidPlaybackStateSynchronizer(this, { player }, ::item, {})
			assertTrue(synchronizer.restoreForCommand(snapshot(409)))
			assertEquals("live", player.currentMediaItem?.mediaId)
			assertEquals(1, player.mediaItemCount)
		} finally { player.release() }
	}

	@Test
	fun liveQueueInstalledDuringMappingWinsWithoutRewinding() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			val mapper = PausedMappingDispatcher()
			val synchronizer = DefaultAndroidPlaybackStateSynchronizer(this, { player }, ::item, {},
				mapper)
			val pending = async { synchronizer.restoreForCommand(snapshot(409)) }
			runCurrent()
			player.setMediaItem(item(song("live")), 17_000L)
			mapper.runCurrent()
			runCurrent()
			assertTrue(pending.await())
			assertEquals("live", player.currentMediaItem?.mediaId)
			assertEquals(17_000L, player.currentPosition)
		} finally { player.release() }
	}

	@Test
	fun cancellingTheCommandCoroutineLeavesTheEngineEmptyAndPaused() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			val mapper = PausedMappingDispatcher()
			val synchronizer = DefaultAndroidPlaybackStateSynchronizer(this, { player }, ::item, {},
				mapper)
			val pending = async { synchronizer.restoreForCommand(snapshot(409).copy(isPaused = false)) }
			runCurrent()
			pending.cancel()
			mapper.runCurrent()
			runCurrent()
			assertTrue(pending.isCancelled)
			assertEquals(0, player.mediaItemCount)
			assertFalse(player.playWhenReady)
		} finally { player.release() }
	}

	@Test
	fun retainedShuffleOrderAndInvalidProgressAreRestoredDeterministically() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			val synchronizer = DefaultAndroidPlaybackStateSynchronizer(this, { player }, ::item, {},
				StandardTestDispatcher(testScheduler))
			val state = snapshot(4).copy(currentIndex = 2, progress = Float.NaN, isShuffleEnabled = true,
				shuffleOrder = listOf(2, 0, 3, 1), upcomingIndexes = listOf(0, 3, 1))
			assertTrue(synchronizer.restoreForCommand(state))
			assertEquals(0L, player.currentPosition)
			assertTrue(player.shuffleModeEnabled)
			player.seekToNextMediaItem()
			assertEquals("song-0", player.currentMediaItem?.mediaId)
			player.seekToNextMediaItem()
			assertEquals("song-3", player.currentMediaItem?.mediaId)
		} finally { player.release() }
	}

	@Test
	fun failedMappingDoesNotInstallAPartialQueueAndCanBeRetried() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			var fail = true
			val synchronizer = DefaultAndroidPlaybackStateSynchronizer(this, { player }, { song ->
				if (fail && song.id == "song-2") error("mapping failure")
				item(song)
			}, {}, StandardTestDispatcher(testScheduler))
			assertFailsWith<IllegalStateException> { synchronizer.restoreForCommand(snapshot(4)) }
			assertEquals(0, player.mediaItemCount)
			fail = false
			assertTrue(synchronizer.restoreForCommand(snapshot(4)))
			assertEquals(4, player.mediaItemCount)
		} finally { player.release() }
	}

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

	private class PausedMappingDispatcher : CoroutineDispatcher() {
		private val tasks = ArrayDeque<Runnable>()
		override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
		fun runCurrent() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
	}
}
