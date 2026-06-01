package paige.navic.ui.components.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.aurral_logo_pulse
import navic.composeapp.generated.resources.bindery_logo_pulse
import navic.composeapp.generated.resources.lastfm_logo_pulse
import navic.composeapp.generated.resources.lidaclips_logo_pulse
import navic.composeapp.generated.resources.musicbrainz_logo_color_pulse
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Lyrics

enum class IntegrationLoadingIndicator {
	LidaClips,
	Aurral,
	MusicBrainz,
	LastFm,
	Bindery,
	Lyrics
}

internal enum class IntegrationLoadingIndicatorIconKind {
	Raster,
	Vector
}

internal fun integrationLoadingIndicatorIconKind(
	indicator: IntegrationLoadingIndicator
): IntegrationLoadingIndicatorIconKind =
	when (indicator) {
		IntegrationLoadingIndicator.LidaClips,
		IntegrationLoadingIndicator.Aurral,
		IntegrationLoadingIndicator.MusicBrainz,
		IntegrationLoadingIndicator.LastFm,
		IntegrationLoadingIndicator.Bindery -> IntegrationLoadingIndicatorIconKind.Raster
		IntegrationLoadingIndicator.Lyrics -> IntegrationLoadingIndicatorIconKind.Vector
	}

internal fun integrationLoadingIndicatorOverlayTopPadding(
	statusBarTop: Dp,
	extraTop: Dp = 8.dp
): Dp = statusBarTop + extraTop

fun integrationLoadingIndicators(
	lidaClipsLoading: Boolean = false,
	aurralLoading: Boolean = false,
	musicBrainzLoading: Boolean = false,
	lastFmLoading: Boolean = false,
	binderyLoading: Boolean = false,
	lyricsLoading: Boolean = false
): List<IntegrationLoadingIndicator> = buildList {
	if (lidaClipsLoading) add(IntegrationLoadingIndicator.LidaClips)
	if (aurralLoading) add(IntegrationLoadingIndicator.Aurral)
	if (musicBrainzLoading) add(IntegrationLoadingIndicator.MusicBrainz)
	if (lastFmLoading) add(IntegrationLoadingIndicator.LastFm)
	if (binderyLoading) add(IntegrationLoadingIndicator.Bindery)
	if (lyricsLoading) add(IntegrationLoadingIndicator.Lyrics)
}

@Composable
fun IntegrationLoadingIndicatorStrip(
	indicators: List<IntegrationLoadingIndicator>,
	modifier: Modifier = Modifier
) {
	val visibleIndicators = indicators.distinct()
	if (visibleIndicators.isEmpty()) return

	val transition = rememberInfiniteTransition(label = "integrationLoadingPulse")
	val pulseAlpha by transition.animateFloat(
		initialValue = .38f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 850),
			repeatMode = RepeatMode.Reverse
		),
		label = "integrationLoadingPulseAlpha"
	)

	Surface(
		modifier = modifier.semantics {
			contentDescription = visibleIndicators.joinToString(", ") { indicator ->
				"${indicator.label} loading"
			}
		},
		shape = ContinuousCapsule,
		color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .74f),
		contentColor = MaterialTheme.colorScheme.onSurface,
		shadowElevation = 2.dp
	) {
		Row(
			modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			visibleIndicators.forEach { indicator ->
				IntegrationLoadingIndicatorIcon(
					indicator = indicator,
					alpha = pulseAlpha
				)
			}
		}
	}
}

@Composable
private fun IntegrationLoadingIndicatorIcon(
	indicator: IntegrationLoadingIndicator,
	alpha: Float
) {
	Box(
		modifier = Modifier
			.size(24.dp)
			.alpha(alpha),
		contentAlignment = Alignment.Center
	) {
		val drawable = indicator.rasterResource
		if (drawable != null) {
			Image(
				painter = painterResource(drawable),
				contentDescription = null,
				contentScale = ContentScale.Fit,
				modifier = Modifier.size(22.dp)
			)
		} else {
			Icon(
				imageVector = indicator.imageVector,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(20.dp)
			)
		}
	}
}

private val IntegrationLoadingIndicator.rasterResource: DrawableResource?
	get() = when (this) {
		IntegrationLoadingIndicator.LidaClips -> Res.drawable.lidaclips_logo_pulse
		IntegrationLoadingIndicator.Aurral -> Res.drawable.aurral_logo_pulse
		IntegrationLoadingIndicator.MusicBrainz -> Res.drawable.musicbrainz_logo_color_pulse
		IntegrationLoadingIndicator.LastFm -> Res.drawable.lastfm_logo_pulse
		IntegrationLoadingIndicator.Bindery -> Res.drawable.bindery_logo_pulse
		IntegrationLoadingIndicator.Lyrics -> null
	}

private val IntegrationLoadingIndicator.imageVector: ImageVector
	get() = when (this) {
		IntegrationLoadingIndicator.LidaClips,
		IntegrationLoadingIndicator.Aurral,
		IntegrationLoadingIndicator.MusicBrainz,
		IntegrationLoadingIndicator.LastFm,
		IntegrationLoadingIndicator.Bindery,
		IntegrationLoadingIndicator.Lyrics -> Icons.Outlined.Lyrics
	}

private val IntegrationLoadingIndicator.label: String
	get() = when (this) {
		IntegrationLoadingIndicator.LidaClips -> "LidaClips"
		IntegrationLoadingIndicator.Aurral -> "Aurral"
		IntegrationLoadingIndicator.MusicBrainz -> "MusicBrainz"
		IntegrationLoadingIndicator.LastFm -> "Last.fm"
		IntegrationLoadingIndicator.Bindery -> "Bindery"
		IntegrationLoadingIndicator.Lyrics -> "Lyrics"
	}
