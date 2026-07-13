package paige.navic.shared

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.media3.session.MediaController
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.playbackVolumeMultiplier
import paige.navic.domain.models.replayGainLoudnessBoostMillibels
import paige.navic.domain.models.replayGainVolumeMultiplier
import paige.navic.domain.models.systemEqualizerAudioSessionId
import paige.navic.util.core.Logger

internal interface AndroidAudioEffectsController {
	fun refresh(player: MediaController?, song: DomainSong?, forceMuted: Boolean)
	fun refreshVolume(player: MediaController?, song: DomainSong?, forceMuted: Boolean)
	fun targetVolume(song: DomainSong?, forceMuted: Boolean): Float
	fun openSystemEqualizer(player: MediaController?): Boolean
}

internal class DefaultAndroidAudioEffectsController(
	private val application: Application,
	private val preferenceManager: PreferenceManager
) : AndroidAudioEffectsController {
	override fun refresh(player: MediaController?, song: DomainSong?, forceMuted: Boolean) {
		runCatching {
			applyReplayGain(player, song, forceMuted)
			PlaybackService.refreshAudioEffects(application)
		}.onFailure { error -> Logger.w("MediaPlayer", "Failed to refresh audio effects", error) }
	}

	override fun refreshVolume(player: MediaController?, song: DomainSong?, forceMuted: Boolean) {
		runCatching { applyReplayGain(player, song, forceMuted) }
			.onFailure { error -> Logger.w("MediaPlayer", "Failed to refresh playback volume", error) }
	}

	override fun targetVolume(song: DomainSong?, forceMuted: Boolean): Float =
		playbackVolumeMultiplier(
			playbackVolumePercent = preferenceManager.playbackVolumePercent,
			replayGainVolumeMultiplier = replayGainVolumeMultiplier(
				replayGain = song?.replayGain,
				mode = preferenceManager.replayGainMode,
				loudnessBoostEnabled = preferenceManager.replayGainLoudnessBoost
			),
			forceMuted = forceMuted
		)

	override fun openSystemEqualizer(player: MediaController?): Boolean {
		val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
			systemEqualizerAudioSessionId(player?.audioSessionId)?.let { audioSessionId ->
				putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
			}
			putExtra(AudioEffect.EXTRA_PACKAGE_NAME, application.packageName)
			putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		return try {
			application.startActivity(intent)
			true
		} catch (error: ActivityNotFoundException) {
			Logger.w("MediaPlayer", "System equalizer not available", error)
			false
		}
	}

	private fun applyReplayGain(player: MediaController?, song: DomainSong?, forceMuted: Boolean) {
		player?.volume = targetVolume(song, forceMuted)
		PlaybackService.setReplayGainLoudnessBoost(
			application,
			replayGainLoudnessBoostMillibels(
				replayGain = song?.replayGain,
				mode = preferenceManager.replayGainMode,
				loudnessBoostEnabled = preferenceManager.replayGainLoudnessBoost
			)
		)
	}
}
