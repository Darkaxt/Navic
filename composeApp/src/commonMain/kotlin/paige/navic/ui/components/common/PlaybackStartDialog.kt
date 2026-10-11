package paige.navic.ui.components.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_preparing_playback
import navic.composeapp.generated.resources.info_starting_playback
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.models.NowPlayingArtworkRevealDurationMs
import paige.navic.domain.models.NowPlayingArtworkRotationDurationMs
import paige.navic.shared.PlaybackStartFeedback
import paige.navic.shared.PlaybackStartPhase

@Composable
fun PlaybackStartDialog(feedback: PlaybackStartFeedback, onCancel: () -> Unit) {
	val status by feedback.state.collectAsStateWithLifecycle()
	val visibility = remember { MutableTransitionState(false) }
	visibility.targetState = status?.visible == true
	var phase by remember { mutableStateOf(PlaybackStartPhase.Preparing) }
	status?.let { phase = it.phase }
	if (!visibility.currentState && !visibility.targetState && visibility.isIdle) return

	// The native modal supplies the dim scrim and isolates touch/accessibility focus.
	Dialog(
		onDismissRequest = onCancel,
		properties = DialogProperties(dismissOnBackPress = true,
			dismissOnClickOutside = false, usePlatformDefaultWidth = false)
	) {
		AnimatedVisibility(
			visibleState = visibility,
			enter = fadeIn(tween(NowPlayingArtworkRevealDurationMs)),
			exit = fadeOut(tween(NowPlayingArtworkRevealDurationMs)),
			modifier = Modifier.fillMaxSize()
		) { PlaybackPreparationVinyl(phase) }
	}
}

@Composable
private fun PlaybackPreparationVinyl(phase: PlaybackStartPhase) {
	val message = stringResource(when (phase) {
		PlaybackStartPhase.Preparing -> Res.string.info_preparing_playback
		PlaybackStartPhase.Starting -> Res.string.info_starting_playback
	})
	val rotation by rememberInfiniteTransition().animateFloat(
		initialValue = 0f, targetValue = 360f,
		animationSpec = infiniteRepeatable(tween(NowPlayingArtworkRotationDurationMs, easing = LinearEasing), RepeatMode.Restart)
	)
	BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
		val diameter = minOf(maxWidth, maxHeight, 360.dp)
		Box(
			Modifier.size(diameter).testTag("playback-preparation-vinyl").semantics {
				paneTitle = message
				liveRegion = LiveRegionMode.Polite
				progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
			}, contentAlignment = Alignment.Center
		) {
			Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }
				.background(Color(0xff18191b), CircleShape)) {
				MaterialTheme(colorScheme = darkColorScheme()) {
					VinylRecordOverlay(Modifier.fillMaxSize(), labelRadiusFraction = .66f, grooveStartRadiusFraction = .70f)
				}
				Canvas(Modifier.fillMaxSize()) {
					val inset = size.width * .10f
					for (angle in listOf(-35f, 145f)) drawArc(
						Color.White.copy(alpha = .07f), angle, 28f, false,
						Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2),
						style = Stroke(size.width * .12f)
					)
				}
			}
			Box(Modifier.size(diameter * .66f).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
				contentAlignment = Alignment.Center) {
				Text(message, color = MaterialTheme.colorScheme.onPrimaryContainer,
					autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp, stepSize = 1.sp),
					lineHeight = 1.25.em, letterSpacing = 0.sp, textAlign = TextAlign.Center,
					modifier = Modifier.padding(12.dp).testTag("playback-preparation-message"))
			}
		}
	}
}
