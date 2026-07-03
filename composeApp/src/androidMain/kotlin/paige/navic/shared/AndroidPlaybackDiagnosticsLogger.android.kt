package paige.navic.shared

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.PlaybackDiagnosticsLogTag
import paige.navic.domain.models.playbackDiagnosticMessage
import paige.navic.util.core.Logger

internal class AndroidPlaybackDiagnosticsLogger {
	private var lastPlayWhenReadyReason: String? = null
	private var lastSuppressionReason: String = "none"
	private var lastRecoveryDownloadSignature: String? = null

	fun onPlayWhenReadyChanged(
		player: Player,
		playWhenReady: Boolean,
		reason: Int,
		currentSong: DomainSong?,
		pendingRecoverySongId: String?
	) {
		lastPlayWhenReadyReason = playWhenReadyReasonLabel(reason)
		Logger.i(
			PlaybackDiagnosticsLogTag,
			playbackDiagnosticMessage(
				"play-when-ready-changed",
				"playWhenReady" to playWhenReady,
				"reason" to lastPlayWhenReadyReason,
				"songId" to (currentSong?.id ?: player.currentMediaItem?.mediaId),
				"index" to player.currentMediaItemIndex,
				"state" to playbackStateLabel(player.playbackState),
				"suppression" to lastSuppressionReason,
				"pendingRecoverySongId" to pendingRecoverySongId
			)
		)
	}

	fun onPlaybackSuppressionReasonChanged(
		player: Player,
		reason: Int,
		currentSong: DomainSong?,
		pendingRecoverySongId: String?
	) {
		lastSuppressionReason = playbackSuppressionReasonLabel(reason)
		Logger.i(
			PlaybackDiagnosticsLogTag,
			playbackDiagnosticMessage(
				"playback-suppression-changed",
				"reason" to lastSuppressionReason,
				"songId" to (currentSong?.id ?: player.currentMediaItem?.mediaId),
				"index" to player.currentMediaItemIndex,
				"playWhenReady" to player.playWhenReady,
				"state" to playbackStateLabel(player.playbackState),
				"pendingRecoverySongId" to pendingRecoverySongId
			)
		)
	}

	fun onIsPlayingChanged(
		player: Player,
		isPlaying: Boolean,
		currentSong: DomainSong?,
		pendingRecoverySongId: String?
	) {
		if (isPlaying) return
		Logger.i(
			PlaybackDiagnosticsLogTag,
			playbackDiagnosticMessage(
				"paused",
				"reason" to (lastPlayWhenReadyReason ?: "unknown"),
				"songId" to (currentSong?.id ?: player.currentMediaItem?.mediaId),
				"title" to currentSong?.title,
				"index" to player.currentMediaItemIndex,
				"playWhenReady" to player.playWhenReady,
				"state" to playbackStateLabel(player.playbackState),
				"suppression" to lastSuppressionReason,
				"pendingRecoverySongId" to pendingRecoverySongId
			)
		)
	}

	fun onPlayerError(player: Player, error: PlaybackException, currentSong: DomainSong?) {
		Logger.i(
			PlaybackDiagnosticsLogTag,
			playbackDiagnosticMessage(
				"playback-error",
				"code" to error.errorCodeName,
				"message" to error.message,
				"songId" to (currentSong?.id ?: player.currentMediaItem?.mediaId),
				"title" to currentSong?.title,
				"index" to player.currentMediaItemIndex,
				"state" to playbackStateLabel(player.playbackState),
				"playWhenReady" to player.playWhenReady
			)
		)
	}

	fun onRecoveryPending(song: DomainSong, positionMs: Long, shouldResume: Boolean) {
		Logger.i(
			PlaybackDiagnosticsLogTag,
			playbackDiagnosticMessage(
				"recovery-pending",
				"songId" to song.id,
				"title" to song.title,
				"positionMs" to positionMs,
				"shouldResume" to shouldResume
			)
		)
	}

	fun onRecoveryDownloadStatus(songId: String?, download: DownloadEntity?, recoveryActive: Boolean) {
		if (!recoveryActive || songId == null) {
			lastRecoveryDownloadSignature = null
			return
		}
		val status = download?.status ?: DownloadStatus.NOT_DOWNLOADED
		val progressPercent = download
			?.takeIf { status == DownloadStatus.DOWNLOADING }
			?.progress
			?.coerceIn(0f, 1f)
			?.let { (it * 100).toInt() }
		val progressBucket = progressPercent?.let { (it / 10) * 10 }
		val signature = "$songId:$status:${progressBucket ?: -1}"
		if (signature == lastRecoveryDownloadSignature) return
		lastRecoveryDownloadSignature = signature
		Logger.i(
			PlaybackDiagnosticsLogTag,
			playbackDiagnosticMessage(
				"recovery-download-status",
				"songId" to songId,
				"status" to status.name,
				"progressPercent" to progressPercent,
				"fileReady" to (download?.filePath != null)
			)
		)
	}

	fun onRecoveryLocalFileReady(songId: String, positionMs: Long, shouldResume: Boolean, source: String) {
		Logger.i(
			PlaybackDiagnosticsLogTag,
			playbackDiagnosticMessage(
				"recovery-local-file-ready",
				"songId" to songId,
				"positionMs" to positionMs,
				"shouldResume" to shouldResume,
				"source" to source
			)
		)
	}

	fun onRecoveryCleared(songId: String?, reason: String) {
		lastRecoveryDownloadSignature = null
		Logger.i(
			PlaybackDiagnosticsLogTag,
			playbackDiagnosticMessage(
				"recovery-cleared",
				"songId" to songId,
				"reason" to reason
			)
		)
	}
}

private fun playbackStateLabel(state: Int): String = when (state) {
	Player.STATE_IDLE -> "idle"
	Player.STATE_BUFFERING -> "buffering"
	Player.STATE_READY -> "ready"
	Player.STATE_ENDED -> "ended"
	else -> "unknown-$state"
}

private fun playWhenReadyReasonLabel(reason: Int): String = when (reason) {
	Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "user-request"
	Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "audio-focus-loss"
	Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "audio-becoming-noisy"
	Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "remote"
	Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "end-of-media-item"
	Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "suppressed-too-long"
	else -> "unknown-$reason"
}

private fun playbackSuppressionReasonLabel(reason: Int): String = when (reason) {
	Player.PLAYBACK_SUPPRESSION_REASON_NONE -> "none"
	Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> "transient-audio-focus-loss"
	Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_ROUTE -> "unsuitable-audio-route"
	Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT -> "unsuitable-audio-output"
	Player.PLAYBACK_SUPPRESSION_REASON_SCRUBBING -> "scrubbing"
	else -> "unknown-$reason"
}
