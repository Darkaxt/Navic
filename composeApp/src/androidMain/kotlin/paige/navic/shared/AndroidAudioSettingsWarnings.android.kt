package paige.navic.shared

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import paige.navic.R
import kotlin.math.abs

internal const val AudioBalanceSetting = "master_balance"
private const val WarningChannel = "playback_warnings"
private const val BalanceNotificationId = 4202
private const val ResumeNotificationId = 4203

internal fun audioBalanceWarningSide(balance: Float?): String? = when {
    balance == null || !balance.isFinite() || abs(balance) > 1f || balance == 0f -> null
    balance < 0 -> "right"
    else -> "left"
}

internal class AndroidAudioSettingsWarnings(
    private val context: Context, private val smallIcon: Int,
    private val hasPlaybackIntent: () -> Boolean
) : AutoCloseable {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("playback_audio_warnings", Context.MODE_PRIVATE)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = refresh()
    }

    init {
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
            NotificationChannel(WarningChannel, context.getString(R.string.playback_warning_channel),
                NotificationManager.IMPORTANCE_LOW))
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(AudioBalanceSetting), false, observer)
        audio.registerAudioDeviceCallback(devices, Handler(Looper.getMainLooper()))
    }

    fun refresh() {
        val balance = try {
            Settings.System.getString(context.contentResolver, AudioBalanceSetting)?.toFloatOrNull()
        } catch (_: SecurityException) { null }
        if (balance == 0f) {
            notifications.cancel(BalanceNotificationId)
            preferences.edit().remove("last_balance_bits").apply()
            return
        }
        val reducedSide = audioBalanceWarningSide(balance) ?: return
        val hasHeadphones = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET)
        }
        if (!hasPlaybackIntent() || !hasHeadphones || !canPost()) return
        val bits = checkNotNull(balance).toBits()
        if (preferences.contains("last_balance_bits") && preferences.getInt("last_balance_bits", 0) == bits) return
        val settings = PendingIntent.getActivity(context, BalanceNotificationId,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (reducedSide == "right") R.string.playback_balance_left_title else R.string.playback_balance_right_title
        val message = if (reducedSide == "right") R.string.playback_balance_left_message else R.string.playback_balance_right_message
        notifications.notify(BalanceNotificationId, builder().setContentTitle(context.getString(title))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(message)))
            .setContentText(context.getString(message)).setContentIntent(settings)
            .addAction(0, context.getString(R.string.playback_audio_settings), settings).build())
        preferences.edit().putInt("last_balance_bits", bits).apply()
    }

    fun showResumeRequired() {
        if (!canPost()) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.action = ResumeUserPlaybackAction
        val action = PendingIntent.getActivity(context, ResumeNotificationId, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifications.notify(ResumeNotificationId, builder()
            .setContentTitle(context.getString(R.string.playback_resume_title))
            .setContentText(context.getString(R.string.playback_resume_message))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.playback_resume_message)))
            .setContentIntent(action).build())
    }

    fun dismissResumeRequired() = notifications.cancel(ResumeNotificationId)

    private fun builder() = NotificationCompat.Builder(context, WarningChannel).setSmallIcon(smallIcon)
        .setSilent(true).setOnlyAlertOnce(true).setAutoCancel(true)

    private fun canPost() = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun close() {
        context.contentResolver.unregisterContentObserver(observer)
        audio.unregisterAudioDeviceCallback(devices)
    }
}
