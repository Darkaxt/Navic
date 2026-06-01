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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import paige.navic.icons.outlined.Close
import paige.navic.icons.outlined.Lyrics
import paige.navic.domain.models.IntegrationService
import paige.navic.domain.models.visibleFailedIntegrationServices

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

fun integrationFailedIndicators(
	failedServices: Set<IntegrationService>,
	enabledServices: Set<IntegrationService>,
	loadingIndicators: List<IntegrationLoadingIndicator>
): List<IntegrationLoadingIndicator> =
	visibleFailedIntegrationServices(
		failedServices = failedServices,
		enabledServices = enabledServices,
		loadingServices = loadingIndicators.mapNotNull { indicator ->
			indicator.integrationServiceOrNull
		}.toSet()
	).map(IntegrationService::toLoadingIndicator)

@Composable
fun IntegrationLoadingIndicatorStrip(
	indicators: List<IntegrationLoadingIndicator>,
	failedIndicators: List<IntegrationLoadingIndicator> = emptyList(),
	modifier: Modifier = Modifier
) {
	val visibleIndicators = indicators.distinct()
	val visibleFailedIndicators = failedIndicators
		.distinct()
		.filterNot(visibleIndicators::contains)
	if (visibleIndicators.isEmpty() && visibleFailedIndicators.isEmpty()) return

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
			contentDescription = buildList {
				visibleIndicators.forEach { indicator -> add("${indicator.label} loading") }
				visibleFailedIndicators.forEach { indicator -> add("${indicator.label} unavailable") }
			}.joinToString(", ")
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
			visibleFailedIndicators.forEach { indicator ->
				IntegrationLoadingIndicatorIcon(
					indicator = indicator,
					alpha = .92f,
					failed = true
				)
			}
		}
	}
}

@Composable
private fun IntegrationLoadingIndicatorIcon(
	indicator: IntegrationLoadingIndicator,
	alpha: Float,
	failed: Boolean = false
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
		if (failed) {
			Surface(
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.offset(x = 2.dp, y = 2.dp)
					.size(12.dp),
				shape = CircleShape,
				color = MaterialTheme.colorScheme.error,
				contentColor = MaterialTheme.colorScheme.onError
			) {
				Icon(
					imageVector = Icons.Outlined.Close,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onError,
					modifier = Modifier.padding(2.dp)
				)
			}
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

private val IntegrationLoadingIndicator.integrationServiceOrNull: IntegrationService?
	get() = when (this) {
		IntegrationLoadingIndicator.LidaClips -> IntegrationService.LidaClips
		IntegrationLoadingIndicator.Aurral -> IntegrationService.Aurral
		IntegrationLoadingIndicator.MusicBrainz -> IntegrationService.MusicBrainz
		IntegrationLoadingIndicator.LastFm -> IntegrationService.LastFm
		IntegrationLoadingIndicator.Bindery -> IntegrationService.Bindery
		IntegrationLoadingIndicator.Lyrics -> null
	}

private fun IntegrationService.toLoadingIndicator(): IntegrationLoadingIndicator =
	when (this) {
		IntegrationService.LidaClips -> IntegrationLoadingIndicator.LidaClips
		IntegrationService.Aurral -> IntegrationLoadingIndicator.Aurral
		IntegrationService.MusicBrainz -> IntegrationLoadingIndicator.MusicBrainz
		IntegrationService.LastFm -> IntegrationLoadingIndicator.LastFm
		IntegrationService.Bindery -> IntegrationLoadingIndicator.Bindery
	}
