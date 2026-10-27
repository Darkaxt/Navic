package paige.navic.shared

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackForegroundOwnershipTest {
    class TestService : Service() {
        override fun onBind(intent: Intent?): IBinder? = null
    }

    @Test fun staleBackgroundPlayCannotAdvanceEngineButUserAdmissionCanResumeRetainedQueue() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        var allowed = false
        val sessionPlayer = stablePlaybackSessionPlayer(player, allowsPlaybackStart = { allowed })
        try {
            player.setMediaItem(MediaItem.Builder().setMediaId("retained").setUri("file:///retained.flac").build(), 60_000L)
            sessionPlayer.play()
            sessionPlayer.playWhenReady = true
            assertFalse(player.playWhenReady)
            assertEquals("retained", player.currentMediaItem?.mediaId)
            assertEquals(60_000L, player.currentPosition)
            allowed = true
            sessionPlayer.play()
            assertTrue(player.playWhenReady)
            sessionPlayer.pause()
            assertFalse(player.playWhenReady)
        } finally { player.release() }
    }

    @Test fun bufferingAndSuppressionKeepServiceButPausedTaskRemovalDoesNot() {
        assertTrue(shouldKeepMusicService(false, true, true, false, false))
        assertTrue(shouldKeepMusicService(true, false, false, false, false))
        assertTrue(shouldKeepMusicService(false, true, false, false, true))
        assertFalse(shouldKeepMusicService(false, true, false, false, false))
        assertFalse(shouldKeepMusicService(false, false, true, false, false))
        assertFalse(shouldKeepMusicService(false, true, true, true, false))
    }

    @Test fun staleLeaseCleanupCannotReleaseAnotherPreparationAndFailureCannotLeak() {
        var starts = 0
        val old = PlaybackForegroundRequests.acquire { starts++ }
        val replacement = PlaybackForegroundRequests.acquire { starts++ }
        try {
            old.close()
            old.close()
            assertTrue(PlaybackForegroundRequests.isPending)
            assertEquals(2, starts, "Cancelling must not start another service")
        } finally { old.close(); replacement.close() }
        assertFalse(PlaybackForegroundRequests.isPending)
        assertFailsWith<IllegalStateException> {
            PlaybackForegroundRequests.acquire { error("Framework rejected the start") }
        }
        assertFalse(PlaybackForegroundRequests.isPending)
    }

    @Test fun cancellationReleasesNativePreparationWithoutChangingPlayerOrQueue() {
        val controller = Robolectric.buildService(TestService::class.java).create()
        val service = controller.get()
        val foreground = AndroidPlaybackForeground(service, android.R.drawable.ic_media_play, { false }, {})
        val request = PlaybackForegroundRequests.acquire {}
        try {
            foreground.onStartRequested()
            assertTrue(foreground.ownsPreparation)
            assertEquals(4201, shadowOf(service).lastForegroundNotificationId)
            var media3Updates = 0
            foreground.updateNotification(false) { media3Updates++ }
            assertEquals(0, media3Updates, "An idle Media3 callback must not demote active preparation")
            request.close()
            foreground.onRequestsChanged()
            assertFalse(foreground.ownsPreparation)
            assertTrue(shadowOf(service).isForegroundStopped)
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally { request.close(); foreground.close(); controller.destroy() }
    }

    @Test fun completedPreparationWaitsForRealMedia3ForegroundHandoff() {
        val controller = Robolectric.buildService(TestService::class.java).create()
        val service = controller.get()
        val foreground = AndroidPlaybackForeground(service, android.R.drawable.ic_media_play, { true }, {})
        val request = PlaybackForegroundRequests.acquire {}
        try {
            foreground.onStartRequested()
            request.close()
            foreground.onRequestsChanged()
            foreground.updateNotification(false) { error("Cached idle state cannot end the foreground interval") }
            assertTrue(foreground.ownsPreparation)
            foreground.updateNotification(true) {
                service.startForeground(1001, NotificationCompat.Builder(service, "playback_preparation")
                    .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Playing").build())
            }
            assertFalse(foreground.ownsPreparation)
            assertFalse(foreground.blocksProviderUpdates)
            assertEquals(1001, shadowOf(service).lastForegroundNotificationId)
            assertFalse(shadowOf(service).isForegroundStopped)
        } finally { request.close(); foreground.close(); controller.destroy() }
    }
}
