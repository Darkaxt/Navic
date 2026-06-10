package paige.navic.ui.components.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ir.mahozad.multiplatform.wavyslider.WaveDirection
import ir.mahozad.multiplatform.wavyslider.WaveVelocity
import ir.mahozad.multiplatform.wavyslider.material3.Track
import ir.mahozad.multiplatform.wavyslider.material3.WaveAnimationSpecs
import ir.mahozad.multiplatform.wavyslider.material3.WaveVelocity
import ir.mahozad.multiplatform.wavyslider.material3.WavySlider
import paige.navic.domain.models.nowPlayingProgressHorizontalPaddingDp
import paige.navic.domain.models.settings.NowPlayingProgressWidth
import paige.navic.domain.models.settings.NowPlayingSliderStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackProgressSlider(
	value: Float,
	onValueChange: (Float) -> Unit,
	isPlaying: Boolean,
	enabled: Boolean,
	sliderStyle: NowPlayingSliderStyle,
	progressWidth: NowPlayingProgressWidth,
	modifier: Modifier = Modifier
) {
	val normalizedValue = value.coerceIn(0f, 1f)
	val waveHeight by animateDpAsState(if (isPlaying) 6.dp else 0.dp)
	val horizontalPadding = nowPlayingProgressHorizontalPaddingDp(
		sliderStyle = sliderStyle,
		progressWidth = progressWidth
	).dp
	val sliderModifier = modifier.padding(horizontal = horizontalPadding)

	when (sliderStyle) {
		NowPlayingSliderStyle.Flat -> {
			Slider(
				value = normalizedValue,
				onValueChange = onValueChange,
				modifier = sliderModifier,
				enabled = enabled
			)
		}
		NowPlayingSliderStyle.Squiggly,
		NowPlayingSliderStyle.Yoyo -> {
			val isYoyo = sliderStyle == NowPlayingSliderStyle.Yoyo
			WavySlider(
				value = normalizedValue,
				onValueChange = onValueChange,
				modifier = sliderModifier,
				waveHeight = waveHeight,
				thumb = {
					SliderDefaults.Thumb(
						enabled = enabled,
						thumbSize = if (isYoyo) {
							DpSize(20.dp, 20.dp)
						} else {
							DpSize(4.dp, 32.dp)
						},
						interactionSource = remember { MutableInteractionSource() }
					)
				},
				track = { sliderState ->
					SliderDefaults.Track(
						sliderState = sliderState,
						thumbTrackGapSize = if (isYoyo) 0.dp else 6.dp,
						waveLength = if (isYoyo) 32.dp else 26.dp,
						waveHeight = waveHeight,
						animationSpecs = SliderDefaults.WaveAnimationSpecs.copy(
							waveAppearanceAnimationSpec = snap()
						),
						waveVelocity = if (isYoyo) {
							WaveVelocity(14.dp, WaveDirection.TAIL)
						} else {
							SliderDefaults.WaveVelocity
						}
					)
				},
				enabled = enabled
			)
		}
		NowPlayingSliderStyle.Slim -> {
			SlimSlider(
				value = normalizedValue,
				onValueChange = onValueChange,
				modifier = sliderModifier,
				enabled = enabled
			)
		}
	}
}
