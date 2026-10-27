package paige.navic.shared

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import paige.navic.R
import paige.navic.util.core.Logger

internal const val PrepareUserPlaybackAction = "paige.navic.action.PREPARE_USER_PLAYBACK"
const val ResumeUserPlaybackAction = "paige.navic.action.RESUME_USER_PLAYBACK"
private const val PreparationNotificationId = 4201
private const val PreparationChannel = "playback_preparation"

internal fun isPlaybackApplicationVisible(): Boolean {
    val state = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(state)
    return state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}

@Suppress("DEPRECATION")
internal fun hasNativePlaybackForeground(context: Context): Boolean =
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(Int.MAX_VALUE).any {
            it.service.className == PlaybackService::class.java.name && it.foreground
        }

/** Same-process request leases; cancellation never starts/restarts a service. */
internal object PlaybackForegroundRequests {
    private var nextId = 0L
    private val active = MutableStateFlow<Set<Long>>(emptySet())
    val requests = active.asStateFlow()
    val isPending: Boolean get() = active.value.isNotEmpty()

    fun acquire(context: Context): AutoCloseable {
        val visible = isPlaybackApplicationVisible()
        check(visible || hasNativePlaybackForeground(context)) {
            "A visible user action is required to start background playback"
        }
        return acquire {
            if (visible) ContextCompat.startForegroundService(context,
                Intent(context, PlaybackService::class.java).setAction(PrepareUserPlaybackAction))
        }
    }

    internal fun acquire(start: () -> Unit): AutoCloseable {
        val id = ++nextId
        active.value = active.value + id
        try { start() } catch (error: Exception) { active.value = active.value - id; throw error }
        return AutoCloseable { active.value = active.value - id }
    }
}

/** Owns only the real preparation interval; Media3 owns normal playback. */
internal class AndroidPlaybackForeground(
    private val service: Service,
    private val smallIcon: Int,
    private val hasPlaybackIntent: () -> Boolean,
    private val refreshNotification: () -> Unit
) {
    private val notifications = service.getSystemService(NotificationManager::class.java)
    var ownsPreparation: Boolean = false
        private set
    val blocksProviderUpdates: Boolean get() = ownsPreparation

    fun onStartRequested() {
        // Even a request cancelled before service creation must fulfil Android's start obligation.
        if (!hasNativePlaybackForeground(service)) showPreparation()
        onRequestsChanged()
    }

    fun onRequestsChanged() {
        if (PlaybackForegroundRequests.isPending) {
            if (!hasNativePlaybackForeground(service)) showPreparation()
        } else if (ownsPreparation) {
            if (hasPlaybackIntent()) refreshNotification() else releasePreparation()
        }
    }

    fun updateNotification(startInForegroundRequired: Boolean, updateMedia3: () -> Unit) {
        if (ownsPreparation && (PlaybackForegroundRequests.isPending ||
                (hasPlaybackIntent() && !startInForegroundRequired))) return
        updateMedia3()
        if (ownsPreparation && startInForegroundRequired) {
            // Default Media3 updates synchronously on the player's main looper.
            notifications.cancel(PreparationNotificationId)
            ownsPreparation = false
            Logger.i("PlaybackLifecycle", "Preparation handed off to Media3 foreground playback")
        } else if (ownsPreparation && !hasPlaybackIntent()) releasePreparation()
    }

    private fun showPreparation() {
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
            NotificationChannel(PreparationChannel, service.getString(R.string.playback_preparing_channel),
                NotificationManager.IMPORTANCE_LOW))
        val launch = service.packageManager.getLaunchIntentForPackage(service.packageName)
        val notification = NotificationCompat.Builder(service, PreparationChannel)
            .setSmallIcon(smallIcon)
            .setContentTitle(service.getString(R.string.playback_preparing_title))
            .setContentText(service.getString(R.string.playback_preparing_message))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .apply { if (launch != null) setContentIntent(PendingIntent.getActivity(service, 4201,
                launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) }
            .build()
        ServiceCompat.startForeground(service, PreparationNotificationId, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        ownsPreparation = true
        Logger.i("PlaybackLifecycle", "Foreground preparation acquired pending=${PlaybackForegroundRequests.isPending}")
    }

    private fun releasePreparation() {
        if (!ownsPreparation) return
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notifications.cancel(PreparationNotificationId)
        ownsPreparation = false
        service.stopSelf()
        Logger.i("PlaybackLifecycle", "Foreground preparation released without changing the queue")
    }

    fun close() { notifications.cancel(PreparationNotificationId) }
}

internal fun shouldKeepMusicService(
    preparing: Boolean, foreground: Boolean, playWhenReady: Boolean,
    ended: Boolean, automaticResumePending: Boolean
): Boolean = preparing || foreground && (!ended && playWhenReady || automaticResumePending)
