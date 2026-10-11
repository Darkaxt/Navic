package paige.navic.shared

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import paige.navic.domain.manager.PlaybackAccountBoundary
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.restoredShuffleOrder
import paige.navic.ui.core.PlayerUiState
import paige.navic.ui.core.forPlaybackOwner
import paige.navic.ui.core.restoredPlayerStateForPreferences
import paige.navic.util.core.Logger

@androidx.annotation.OptIn(UnstableApi::class)
internal class AndroidPlaybackResumption(
	parentScope: CoroutineScope,
	private val player: ExoPlayer,
	private val accountBoundary: PlaybackAccountBoundary,
	private val persistentQueue: () -> Boolean,
	private val loadState: suspend () -> String?,
	private val mediaItemForSong: (DomainSong) -> MediaItem,
	private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default
) : AutoCloseable {
	private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
	private var pendingJob: Job? = null
	private var pendingFuture: SettableFuture<MediaSession.MediaItemsWithStartPosition>? = null
	private var pendingSettings: Pair<PlaybackAccountBoundary.Lease, PlayerUiState>? = null
	private var generation = 0L
	private var savedQueueEligible = true
	private var rejectingCompletion = false
	private var completingPlayback: Pair<PlaybackAccountBoundary.Lease, Long>? = null
	private var closed = false
	private val overriddenSettings = mutableSetOf<Int>()
	private val unregisterAccount = accountBoundary.register {
		invalidate()
		overriddenSettings.clear()
		savedQueueEligible = true
	}

	fun allowsFrameworkPlaybackMutation(): Boolean = !closed && !rejectingCompletion &&
		(completingPlayback?.let { (account, revision) -> accountBoundary.isCurrent(account) && generation == revision } != false)

	fun onPlayerCommand(command: Int) {
		if (command == Player.COMMAND_SET_REPEAT_MODE || command == Player.COMMAND_SET_SHUFFLE_MODE ||
			command == Player.COMMAND_SET_SPEED_AND_PITCH) {
			overriddenSettings += command
			return
		}
		if (command !in invalidatingCommands) return
		invalidate()
		if (command == Player.COMMAND_CHANGE_MEDIA_ITEMS || command == Player.COMMAND_SET_MEDIA_ITEM) {
			// A clear/replacement beats disk state, including a save that has not completed yet.
			savedQueueEligible = false
		}
	}

	fun invalidate() {
		generation++
		pendingSettings = null
		pendingFuture?.let { future -> rejectedCompletion { future.cancel(false) } }
		pendingFuture = null
		pendingJob?.cancel()
		pendingJob = null
	}

	fun request(isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
		if (closed) return SettableFuture.create<MediaSession.MediaItemsWithStartPosition>().apply {
			setException(IllegalStateException("Playback service is closed"))
		}
		if (isForPlayback) invalidate()
		val requestGeneration = generation
		val account = accountBoundary.capture()
		val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
		if (isForPlayback) pendingFuture = future
		val job = scope.launch {
			try {
				check(!closed && savedQueueEligible && persistentQueue()) { "No eligible saved queue" }
				check(account.ownerId != null) { "No active playback account" }
				val state = withContext(mappingDispatcher) {
					val json = loadState()?.takeIf { it.isNotBlank() } ?: error("No saved playback state")
					val decoded = Json.decodeFromString<PlayerUiState>(json)
					decoded.forPlaybackOwner(account.ownerId, accountBoundary.legacyOwnerId)
						?.let { restoredPlayerStateForPreferences(it, true, false) }
						?: error("Saved playback state does not belong to the current account")
				}
				check(state.queue.isNotEmpty()) { "Saved queue is empty" }
				check(state.repeatMode in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL) { "Invalid saved repeat mode" }
				val index = state.currentIndex.takeIf(state.queue.indices::contains) ?: 0
				val songs = if (isForPlayback) state.queue else listOf(state.queue[index])
				val items = withContext(mappingDispatcher) {
					songs.map { song -> ensureActive(); mediaItemForSong(song) }
				}
				check(!closed && savedQueueEligible && persistentQueue() && accountBoundary.isCurrent(account)) {
					"Saved playback request was revoked"
				}
				if (isForPlayback) {
					check(requestGeneration == generation && player.mediaItemCount == 0) { "A newer queue owns the player" }
					pendingSettings = account to state
				}
				val progress = state.progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
				val position = (progress * state.queue[index].duration.inWholeMilliseconds.coerceAtLeast(0L)).toLong()
				Logger.i("PlaybackRestore", "Service resumption playback=$isForPlayback queueSize=${items.size} index=$index positionMs=$position")
				check(Looper.myLooper() == player.applicationLooper) { "Resumption must complete on the player thread" }
				val previousCompletion = completingPlayback
				if (isForPlayback) completingPlayback = account to requestGeneration
				try {
					future.set(MediaSession.MediaItemsWithStartPosition(items, if (isForPlayback) index else 0, position))
				} finally {
					completingPlayback = previousCompletion
				}
			} catch (cancelled: CancellationException) {
				rejectedCompletion { future.cancel(false) }
				throw cancelled
			} catch (error: Exception) {
				Logger.w("PlaybackRestore", "Service resumption rejected; retained state is unchanged", error)
				rejectedCompletion { future.setException(error) }
			} finally {
				if (pendingFuture === future) {
					pendingFuture = null
					pendingJob = null
				}
			}
		}
		if (isForPlayback && !future.isDone) pendingJob = job
		return future
	}

	fun onPlaylistInstalled(items: List<MediaItem>) {
		val (account, state) = pendingSettings ?: return
		pendingSettings = null
		if (!accountBoundary.isCurrent(account) || items.map { it.mediaId } != state.queue.map { it.id }) return
		if (Player.COMMAND_SET_REPEAT_MODE !in overriddenSettings) player.repeatMode = state.repeatMode
		if (Player.COMMAND_SET_SPEED_AND_PITCH !in overriddenSettings) {
			player.playbackParameters = PlaybackParameters(state.playbackSpeed, state.playbackPitch)
		}
		if (Player.COMMAND_SET_SHUFFLE_MODE !in overriddenSettings) {
			if (state.isShuffleEnabled) {
				restoredShuffleOrder(items.size, player.currentMediaItemIndex, state.upcomingIndexes, state.shuffleOrder)?.let { order ->
					player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), 0L))
				}
			}
			player.shuffleModeEnabled = state.isShuffleEnabled
		}
		savedQueueEligible = false
	}

	private inline fun rejectedCompletion(complete: () -> Unit) {
		check(Looper.myLooper() == player.applicationLooper) { "Resumption must complete on the player thread" }
		// Media3 1.10.1 synchronously invokes its play action even for failed/cancelled futures.
		val previous = rejectingCompletion
		rejectingCompletion = true
		try { complete() } finally { rejectingCompletion = previous }
	}

	override fun close() {
		if (closed) return
		closed = true
		invalidate()
		unregisterAccount()
		scope.cancel()
	}

	private companion object {
		val invalidatingCommands = setOf(
			Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP, Player.COMMAND_PREPARE,
			Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
			Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS,
			Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT,
			Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
			Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS
		)
	}
}
