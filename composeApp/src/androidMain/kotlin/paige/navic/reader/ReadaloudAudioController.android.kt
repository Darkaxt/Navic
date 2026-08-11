package paige.navic.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import paige.navic.shared.FutureConnectionOwner
import paige.navic.util.core.Logger

class ReadaloudAudioController(
	private val context: Context,
	private val onPositionChanged: (ReadaloudPlaybackPosition) -> Unit = {},
	private val onTimelineChanged: () -> Unit = {}
) {
	private var controller: MediaController? = null
	private var activePlan: ReadaloudPlaybackPlan? = null
	private var pendingLoad: PendingLoad? = null
	private val positionPulse = ReadaloudPositionPulse(
		isPlaying = { controller?.isPlaying == true },
		publishPosition = ::publishPosition,
		schedule = ::schedulePositionPulse
	)
	private val connectionOwner = FutureConnectionOwner<MediaController>(
		onConnected = { connectedController ->
			controller = connectedController
			Logger.i(ReadaloudPlaybackLogTag, "Readaloud controller connected")
			connectedController.addListener(listener)
			pendingLoad?.let { pending ->
				pendingLoad = null
				load(pending.plan, pending.playWhenReady)
			}
		},
		onConnectionFailed = { error ->
			controller = null
			Logger.e(ReadaloudPlaybackLogTag, "Failed to connect readaloud controller", error)
		},
		onDisconnected = { disconnectedController ->
			if (controller === disconnectedController) controller = null
			onTimelineChanged()
			Logger.w(ReadaloudPlaybackLogTag, "Readaloud controller disconnected; reconnecting")
			positionPulse.stop()
			activePlan?.let { plan ->
				if (pendingLoad == null) pendingLoad = PendingLoad(plan, playWhenReady = false)
				connect()
			}
		},
		releaseFuture = MediaController::releaseFuture
	)
	private val connectionListener = object : MediaController.Listener {
		override fun onDisconnected(disconnectedController: MediaController) {
			connectionOwner.disconnect(disconnectedController)
		}
	}

	private val listener = object : Player.Listener {
		override fun onIsPlayingChanged(isPlaying: Boolean) {
			onTimelineChanged()
			publishPosition()
			positionPulse.update()
		}

		override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
			onTimelineChanged()
			publishPosition()
			positionPulse.update()
		}

		override fun onPlaybackStateChanged(playbackState: Int) {
			onTimelineChanged()
			publishPosition()
			positionPulse.update()
		}

		override fun onPositionDiscontinuity(
			oldPosition: Player.PositionInfo,
			newPosition: Player.PositionInfo,
			reason: Int
		) {
			onTimelineChanged()
			publishPosition()
		}

		override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
			onTimelineChanged()
			publishPosition()
		}

		override fun onPlayerError(error: PlaybackException) {
			val currentController = controller
			Logger.e(
				ReadaloudPlaybackLogTag,
				"Readaloud controller playback error " +
					"mediaId=${currentController?.currentMediaItem?.mediaId} " +
					"index=${currentController?.currentMediaItemIndex} " +
					"code=${error.errorCodeName} message=${error.message}",
				error
			)
			onTimelineChanged()
			publishPosition()
			positionPulse.update()
		}
	}

	init {
		connect()
	}

	fun load(
		session: ReadaloudAudioSession,
		requestHeaders: Map<String, String> = emptyMap(),
		startTrackIndex: Int = 0,
		startPositionMs: Long = 0L,
		playbackSpeed: Float = 1f,
		playWhenReady: Boolean = false
	) {
		load(
			plan = session.toReadaloudPlaybackPlan(
				requestHeaders = requestHeaders,
				startTrackIndex = startTrackIndex,
				startPositionMs = startPositionMs,
				playbackSpeed = playbackSpeed
			),
			playWhenReady = playWhenReady
		)
	}

	fun load(plan: ReadaloudPlaybackPlan, playWhenReady: Boolean = false) {
		activePlan = plan
		val currentController = controller
		if (currentController == null) {
			pendingLoad = PendingLoad(plan, playWhenReady)
			connect()
			return
		}
		currentController.applyPlan(plan, playWhenReady)
	}

	fun play() {
		controller?.play()
		positionPulse.update()
	}

	fun pause() {
		controller?.pause()
		publishPosition()
		positionPulse.update()
	}

	fun stopAndReset() {
		val currentPlan = activePlan
		controller?.pause()
		if (currentPlan != null && currentPlan.mediaItems.isNotEmpty()) {
			controller?.seekTo(
				currentPlan.startTrackIndex.coerceIn(currentPlan.mediaItems.indices),
				currentPlan.startPositionMs.coerceAtLeast(0L)
			)
		} else {
			controller?.seekTo(0L)
		}
		publishPosition()
		positionPulse.update()
	}

	fun seekTo(positionMs: Long) {
		controller?.seekTo(positionMs.coerceAtLeast(0L))
		publishPosition()
	}

	fun seekTo(trackIndex: Int, positionMs: Long) {
		controller?.seekTo(trackIndex.coerceAtLeast(0), positionMs.coerceAtLeast(0L))
		publishPosition()
	}

	fun setPlaybackSpeed(value: Float) {
		controller?.playbackParameters = PlaybackParameters(normalizedReadaloudPlaybackSpeed(value))
		publishPosition()
	}

	fun release() {
		publishPosition()
		positionPulse.stop()
		controller?.removeListener(listener)
		controller = null
		connectionOwner.close()
		pendingLoad = null
		activePlan = null
	}

	private fun connect() {
		if (controller != null) return
		val sessionToken = ReadaloudPlaybackService.newSessionToken(context)
		val future = MediaController.Builder(context, sessionToken)
			.setListener(connectionListener)
			.buildAsync()
		if (!connectionOwner.connect(future)) MediaController.releaseFuture(future)
	}

	private fun MediaController.applyPlan(plan: ReadaloudPlaybackPlan, playWhenReady: Boolean) {
		val event = plan.toReadaloudPlaybackLoadedEvent()
		Logger.i(event.tag, event.message)
		val mediaItems = plan.mediaItems.map(ReadaloudMediaItemDescriptor::toReadaloudMediaItem)
		stop()
		clearMediaItems()
		if (mediaItems.isNotEmpty()) {
			setMediaItems(
				mediaItems,
				plan.startTrackIndex.coerceIn(mediaItems.indices),
				plan.startPositionMs
			)
		}
		playbackParameters = PlaybackParameters(plan.playbackSpeed)
		prepare()
		this.playWhenReady = playWhenReady
		publishPosition()
		positionPulse.update()
	}

	private fun schedulePositionPulse(
		delayMs: Long,
		action: () -> Unit
	): ReadaloudPositionPulseCancellation {
		val handler = Handler(Looper.getMainLooper())
		val runnable = Runnable(action)
		handler.postDelayed(runnable, delayMs)
		return ReadaloudPositionPulseCancellation {
			handler.removeCallbacks(runnable)
		}
	}

	fun currentPosition(): ReadaloudPlaybackPosition? {
		val currentController = controller ?: return null
		val currentPlan = activePlan
		return ReadaloudPlaybackPosition(
			sessionId = currentPlan?.sessionId,
			trackIndex = currentController.currentMediaItemIndex,
			mediaId = currentController.currentMediaItem?.mediaId,
			positionMs = currentController.currentPosition.coerceAtLeast(0L),
			durationMs = currentController.duration.takeIf { it > 0L },
			isPlaying = currentController.isPlaying,
			playbackSpeed = currentController.playbackParameters.speed
		)
	}

	private fun publishPosition() {
		currentPosition()?.let(onPositionChanged)
	}

	private data class PendingLoad(
		val plan: ReadaloudPlaybackPlan,
		val playWhenReady: Boolean
	)

	companion object {
		const val serviceClassName: String = ReadaloudPlaybackService.serviceClassName
	}
}
