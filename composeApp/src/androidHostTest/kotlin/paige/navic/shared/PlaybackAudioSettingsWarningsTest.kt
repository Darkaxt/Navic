package paige.navic.shared

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackAudioSettingsWarningsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val notifications get() = application.getSystemService(NotificationManager::class.java)
    private val audio get() = application.getSystemService(AudioManager::class.java)

    @Before fun preparePhone() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        application.getSharedPreferences("playback_audio_warnings", 0).edit().clear().commit()
        shadowOf(audio).setOutputDevices(listOf(AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP).build()))
        setBalance(0f)
        notifications.cancelAll()
    }

    @Test fun reportsReducedChannelWithoutTreatingUnknownDataAsBalanced() {
        assertEquals("right", audioBalanceWarningSide(-0.68f))
        assertEquals("left", audioBalanceWarningSide(0.68f))
        listOf(null, 0f, Float.NaN, Float.POSITIVE_INFINITY, 2f).forEach {
            assertNull(audioBalanceWarningSide(it))
        }
    }

    @Test fun nativeWarningOpensSettingsAndDoesNotRepeatAfterDismissalOrRecreation() {
        setBalance(-0.68f)
        AndroidAudioSettingsWarnings(application, android.R.drawable.ic_media_play) { true }.use { warning ->
            warning.refresh()
            val notification = shadowOf(notifications).getNotification(4202)
            assertTrue(notification.extras.getString("android.title")!!.contains("left"))
            assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, shadowOf(notification.contentIntent).savedIntent.action)
            notifications.cancel(4202)
            warning.refresh()
            assertNull(shadowOf(notifications).getNotification(4202))
        }
        AndroidAudioSettingsWarnings(application, android.R.drawable.ic_media_play) { true }.use { warning ->
            warning.refresh()
            assertNull(shadowOf(notifications).getNotification(4202))
            setBalance(0f)
            warning.refresh()
            setBalance(0.68f)
            warning.refresh()
            assertTrue(shadowOf(notifications).getNotification(4202).extras.getString("android.title")!!.contains("right"))
        }
    }

    @Test fun noWarningForPausedPlaybackSpeakerOrDeniedPermission() {
        setBalance(-0.68f)
        AndroidAudioSettingsWarnings(application, android.R.drawable.ic_media_play) { false }.use {
            it.refresh()
            assertNull(shadowOf(notifications).getNotification(4202))
        }
        shadowOf(audio).setOutputDevices(emptyList())
        AndroidAudioSettingsWarnings(application, android.R.drawable.ic_media_play) { true }.use {
            it.refresh()
            assertNull(shadowOf(notifications).getNotification(4202))
        }
        preparePhone()
        setBalance(-0.68f)
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AndroidAudioSettingsWarnings(application, android.R.drawable.ic_media_play) { true }.use {
            it.refresh()
            assertNull(shadowOf(notifications).getNotification(4202))
            shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            it.refresh()
            assertTrue(shadowOf(notifications).getNotification(4202) != null)
        }
    }

    // Android persists settings as strings; Robolectric 4.16's typed setters do not.
    private fun setBalance(value: Float) {
        Settings.System.putString(application.contentResolver, AudioBalanceSetting, value.toString())
    }
}
