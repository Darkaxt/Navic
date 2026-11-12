package paige.navic.shared

import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.russhwolf.settings.MapSettings
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.domain.manager.AppLogManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.util.core.Logger
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AndroidPlaybackErrorDiagnosticsTest {
	@Test
	fun exceptionCauseSurvivesGeneralLoggingBeingDisabled() {
		val preferences = PreferenceManager(MapSettings())
		val logs = AppLogManager(preferences)
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		logs.start()
		try {
			AndroidPlaybackDiagnosticsLogger().onPlayerError(
				player,
				PlaybackException("Unexpected runtime error", IllegalStateException("renderer reset failed https://server/stream?t=secret"),
					PlaybackException.ERROR_CODE_TIMEOUT),
				null
			)
			assertTrue(AppLogManager(preferences).exportText().contains("renderer reset failed"))
			assertTrue(!AppLogManager(preferences).exportText().contains("secret"))
		} finally {
			Logger.configureIssueLogSink(null)
			player.release()
		}
	}
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AndroidPlaybackStallDiagnosticsTest {
	@Test
	fun notPlayingWhilePlayWhenReadyIsLoggedAsStallNotUserPause() {
		val preferences = PreferenceManager(MapSettings())
		val logs = AppLogManager(preferences)
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		logs.start()
		try {
			val diagnostics = AndroidPlaybackDiagnosticsLogger()
			diagnostics.onPlayWhenReadyChanged(player, true, androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST, null, null)
			player.playWhenReady = true
			diagnostics.onIsPlayingChanged(player, false, null, null)
			player.playWhenReady = false
			diagnostics.onIsPlayingChanged(player, false, null, null)
			val text = AppLogManager(preferences).exportText()
			assertTrue(text.contains("stalled reason=idle"), text)
			assertTrue(text.contains("paused reason=user-request"), text)
		} finally {
			Logger.configureIssueLogSink(null)
			player.release()
		}
	}
}
