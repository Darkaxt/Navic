package paige.navic.shared

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Pass-through PCM stage that reports stretches of near-digital silence (every channel below
 * about -72 dBFS) lasting at least [minSilenceMs]. It sees decoded audio slightly ahead of what
 * is audible, and nothing at all while audio offload is active.
 */
@UnstableApi
internal class PlaybackSilenceMeter(private val minSilenceMs: Long = 2_000) : BaseAudioProcessor() {
	@Volatile var onSilentSpan: ((lengthMs: Long) -> Unit)? = null
	private var silentFrames = 0L

	override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
		if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
			inputAudioFormat
		} else {
			AudioFormat.NOT_SET
		}

	override fun queueInput(inputBuffer: ByteBuffer) {
		val size = inputBuffer.remaining()
		if (size == 0) return
		measure(inputBuffer)
		replaceOutputBuffer(size).put(inputBuffer).flip()
	}

	private fun measure(buffer: ByteBuffer) {
		val format = inputAudioFormat
		val channels = format.channelCount.coerceAtLeast(1)
		val float = format.encoding == C.ENCODING_PCM_FLOAT
		val sampleBytes = if (float) 4 else 2
		var index = buffer.position()
		val end = buffer.limit() - format.bytesPerFrame + 1
		while (index < end) {
			var silent = true
			for (channel in 0 until channels) {
				val at = index + channel * sampleBytes
				val quiet = if (float) abs(buffer.getFloat(at)) <= SilentFloat else abs(buffer.getShort(at).toInt()) <= SilentPcm16
				if (!quiet) { silent = false; break }
			}
			if (silent) silentFrames++ else reportSpan()
			index += format.bytesPerFrame
		}
	}

	private fun reportSpan() {
		val frames = silentFrames
		silentFrames = 0
		val rate = inputAudioFormat.sampleRate
		if (frames <= 0 || rate <= 0) return
		val lengthMs = frames * 1_000 / rate
		if (lengthMs >= minSilenceMs) onSilentSpan?.invoke(lengthMs)
	}

	override fun onQueueEndOfStream() = reportSpan()
	override fun onFlush() { silentFrames = 0 }
	override fun onReset() { silentFrames = 0 }

	private companion object {
		const val SilentPcm16 = 8
		const val SilentFloat = 8f / 32_768f
	}
}

/** The default renderers with [meter] ahead of the default audio processor chain. */
@UnstableApi
internal class SilenceMeteringRenderersFactory(
	context: Context,
	private val meter: PlaybackSilenceMeter
) : DefaultRenderersFactory(context) {
	override fun buildAudioSink(
		context: Context,
		enableFloatOutput: Boolean,
		enableAudioTrackPlaybackParams: Boolean
	): AudioSink = DefaultAudioSink.Builder(context)
		.setEnableFloatOutput(enableFloatOutput)
		.setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
		.setAudioProcessors(arrayOf(meter))
		.build()
}
