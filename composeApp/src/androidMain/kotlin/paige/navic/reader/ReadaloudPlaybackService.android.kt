package paige.navic.reader

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.binderyApiKeyHeaders
import paige.navic.domain.repositories.binderyRequestHeadersForUrl
import paige.navic.ui.components.common.CoilBitmapLoader
import paige.navic.util.core.Logger
import paige.navic.util.core.ResourceProvider

class ReadaloudPlaybackService : MediaSessionService(), KoinComponent {
	private var mediaSession: MediaSession? = null
	private var exoPlayer: ExoPlayer? = null
	private val preferenceManager: PreferenceManager by inject()
	private val resourceProvider: ResourceProvider by inject()

	@OptIn(UnstableApi::class)
	override fun onCreate() {
		super.onCreate()
		val loadControl = DefaultLoadControl.Builder()
			.setBufferDurationsMs(
				/* minBufferMs = */ 32_000,
				/* maxBufferMs = */ 96_000,
				/* bufferForPlaybackMs = */ 1_500,
				/* bufferForPlaybackAfterRebufferMs = */ 4_000
			)
			.setBackBuffer(30_000, true)
			.build()
		val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
			.build()
			.apply { setSmallIcon(resourceProvider.icNavic) }
		val baseDataSourceFactory = DefaultDataSource.Factory(this, DefaultHttpDataSource.Factory())
		val dataSourceFactory = ResolvingDataSource.Factory(baseDataSourceFactory) { dataSpec ->
			val headers = binderyRequestHeadersForUrl(
				baseUrl = preferenceManager.binderyOpdsBaseUrl,
				url = dataSpec.uri.toString(),
				requestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
			)
			dataSpec.withAdditionalHeaders(headers)
		}
		val player = ExoPlayer.Builder(this)
			.setLoadControl(loadControl)
			.setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
			.setHandleAudioBecomingNoisy(true)
			.setWakeMode(C.WAKE_MODE_NETWORK)
			.build()
			.apply {
				setAudioAttributes(
					AudioAttributes.Builder()
						.setUsage(C.USAGE_MEDIA)
						.setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
						.build(),
					preferenceManager.respectAudioFocus
				)
				setMediaNotificationProvider(notificationProvider)
				addListener(object : Player.Listener {
					override fun onPlayerError(error: PlaybackException) {
						Logger.e(
							ReadaloudPlaybackLogTag,
							"Readaloud service playback error " +
								"mediaId=${currentMediaItem?.mediaId} " +
								"index=$currentMediaItemIndex " +
								"code=${error.errorCodeName} message=${error.message}",
							error
						)
					}
				})
			}
		exoPlayer = player
		val mediaSessionBuilder = MediaSession.Builder(this, player)
			.setId(sessionId)
			.setBitmapLoader(
				CoilBitmapLoader(this) { uri ->
					binderyRequestHeadersForUrl(
						baseUrl = preferenceManager.binderyOpdsBaseUrl,
						url = uri.toString(),
						requestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
					)
				}
			)
		sessionPendingIntent()?.let(mediaSessionBuilder::setSessionActivity)
		mediaSession = mediaSessionBuilder.build()
	}

	override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
		mediaSession

	override fun onTaskRemoved(rootIntent: Intent?) {
		if (exoPlayer?.isPlaying != true) {
			stopSelf()
		}
	}

	override fun onDestroy() {
		stopForeground(STOP_FOREGROUND_REMOVE)
		mediaSession?.release()
		mediaSession = null
		exoPlayer?.release()
		exoPlayer = null
		super.onDestroy()
	}

	private fun sessionPendingIntent(): PendingIntent? {
		val sessionIntent = applicationContext.packageManager
			.getLaunchIntentForPackage(applicationContext.packageName)
			?.apply {
				flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
			}
		return sessionIntent?.let { intent ->
			PendingIntent.getActivity(
				this,
				0,
				intent,
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
			)
		}
	}

	companion object {
		const val serviceClassName: String = "paige.navic.reader.ReadaloudPlaybackService"
		const val sessionId: String = "navic-readaloud"

		fun newSessionToken(context: Context): SessionToken =
			SessionToken(context, ComponentName(context, ReadaloudPlaybackService::class.java))
	}
}
