package paige.navic.shared

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidPlaybackAutoResumeCoordinatorTest {
	@Test
	fun secondaryControllerDisconnectPreservesResumeUnlessPlaybackSessionWasLost() = runTest {
		for (gap in listOf(true, false)) {
			for (sessionLost in listOf(false, true)) {
				val fake = TestPlayer()
				var owner: Any? = Any()
				val coordinator = AndroidPlaybackAutoResumeCoordinator(this, fake.player, { owner }, { 1 }, { true })
				fake.player.addListener(coordinator)
				val service = PlaybackService()
				PlaybackService::class.java.getDeclaredField("automaticResume").apply {
					isAccessible = true
					set(service, coordinator)
				}
				val context = ApplicationProvider.getApplicationContext<android.content.Context>()
				val sessionPlayer = ExoPlayer.Builder(context).build()
				val callback = PlaybackService::class.java.declaredClasses
					.single { it.simpleName == "PlaybackSessionCallback" }
					.getDeclaredConstructor(PlaybackService::class.java, ExoPlayer::class.java)
					.apply { isAccessible = true }
					.newInstance(service, sessionPlayer) as MediaSession.Callback
				val mediaSession = MediaSession.Builder(context, sessionPlayer).setCallback(callback).build()
				try {
					if (gap) coordinator.onMediaItemTransition(fake.item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
					else coordinator.onVolumeChanged(0)
					runCurrent()
					val secondary = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
						"secondary.observer", 2, 2, 0, 0, false, Bundle.EMPTY, false
					)
					callback.onDisconnected(mediaSession, secondary)
					if (sessionLost) owner = null
					if (gap) {
						advanceTimeBy(1_000)
						runCurrent()
					} else coordinator.onVolumeChanged(5)
					assertEquals(if (sessionLost) 0 else 1, fake.playCalls, "gap=$gap sessionLost=$sessionLost")
				} finally {
					coordinator.invalidate()
					mediaSession.release()
					sessionPlayer.release()
				}
			}
		}
	}

	@Test
	fun unmodifiedGapWaitsConfiguredDurationAndResumesOnce() = runTest {
		val fake = TestPlayer()
		val session = Any()
		val coordinator = AndroidPlaybackAutoResumeCoordinator(this, fake.player, { session }, { 3 }, { true })
		fake.player.addListener(coordinator)
		coordinator.onMediaItemTransition(fake.item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
		runCurrent()
		assertFalse(fake.playing)
		advanceTimeBy(2_999)
		assertFalse(fake.playing)
		advanceTimeBy(1)
		runCurrent()
		assertTrue(fake.playing)
		assertEquals(1, fake.playCalls)
	}

	@Test
	fun explicitCommandsCannotBeOverriddenByEitherAutomaticResumePath() = runTest {
		for (gap in listOf(true, false)) {
			for (command in listOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_CHANGE_MEDIA_ITEMS,
				Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_STOP)) {
				val fake = TestPlayer()
				val session = Any()
				val coordinator = AndroidPlaybackAutoResumeCoordinator(this, fake.player, { session }, { 3 }, { true })
				fake.player.addListener(coordinator)
				if (gap) coordinator.onMediaItemTransition(fake.item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
				else coordinator.onVolumeChanged(0)
				runCurrent()
				assertFalse(fake.playing)
				coordinator.onPlayerCommand(command)
				if (command == Player.COMMAND_PLAY_PAUSE) {
					fake.player.play()
					coordinator.onPlayerCommand(command)
					fake.player.pause()
				}
				// Queue replacement deliberately retains the same index and even the same MediaItem.
				val callsBeforeResumeEvent = fake.playCalls
				if (gap) { advanceTimeBy(3_000); runCurrent() } else coordinator.onVolumeChanged(5)
				assertFalse(fake.playing, "gap=$gap command=$command")
				assertEquals(callsBeforeResumeEvent, fake.playCalls)
			}
		}
	}

	@Test
	fun volumeRestoreResumesOnceButNewSessionOrPlaylistRejectsIt() = runTest {
		for (change in listOf("none", "session", "playlist")) {
			val fake = TestPlayer()
			var session: Any = Any()
			val coordinator = AndroidPlaybackAutoResumeCoordinator(this, fake.player, { session }, { 0 }, { true })
			fake.player.addListener(coordinator)
			coordinator.onVolumeChanged(0)
			if (change == "session") session = Any()
			if (change == "playlist") coordinator.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
			coordinator.onVolumeChanged(5)
			coordinator.onVolumeChanged(6)
			assertEquals(if (change == "none") 1 else 0, fake.playCalls, change)
		}
	}

	@Test
	fun redundantPauseAndControllerLossCancelPendingGapWithoutAPlayerStateChange() = runTest {
		for (controllerLost in listOf(true, false)) {
			val fake = TestPlayer()
			val session = Any()
			val coordinator = AndroidPlaybackAutoResumeCoordinator(this, fake.player, { session }, { 1 }, { true })
			fake.player.addListener(coordinator)
			coordinator.onMediaItemTransition(fake.item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
			runCurrent()
			if (controllerLost) coordinator.invalidate() else coordinator.onPlayerCommand(Player.COMMAND_PLAY_PAUSE)
			advanceTimeBy(1_000)
			runCurrent()
			assertEquals(0, fake.playCalls)
		}
	}

	private class TestPlayer {
		var playing = true
		var playCalls = 0
		val item = MediaItem.Builder().setMediaId("song").build()
		private val listeners = mutableListOf<Player.Listener>()
		val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
			when (method.name) {
				"isPlaying", "getPlayWhenReady" -> playing
				"getCurrentMediaItem" -> item
				"getCurrentMediaItemIndex" -> 0
				"getMediaItemCount" -> 1
				"getPlaybackState" -> Player.STATE_READY
				"addListener" -> { listeners += args!![0] as Player.Listener; null }
				"play", "pause" -> {
					val next = method.name == "play"
					if (next) playCalls++
					if (playing != next) {
						playing = next
						listeners.forEach { it.onPlayWhenReadyChanged(next, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
					}
					null
				}
				else -> error("Unexpected Player call: ${method.name}")
			}
		} as Player
	}
}
