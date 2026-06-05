package paige.navic.ui.screens.artist.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_less
import navic.composeapp.generated.resources.action_more
import org.jetbrains.compose.resources.stringResource
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.MarqueeText
import paige.navic.ui.screens.artist.ArtistBiographyScrollFades
import paige.navic.ui.screens.artist.artistBiographyScrollFades
import paige.navic.ui.screens.artist.artistBiographyDisplayText
import paige.navic.ui.screens.artist.shouldShowArtistBiographyToggle
import paige.navic.util.ui.easedVerticalGradient

@Composable
fun ArtistDetailScreenHeading(
	artistName: String,
	coverArtId: String?,
	imageUrl: String?,
	imageRequestHeaders: Map<String, String> = emptyMap(),
	imageDiagnosticLabel: String? = null,
	subtitle: String?,
	innerPadding: PaddingValues,
	scrolled: Boolean,
	artworkResolving: Boolean = false
) {
	val layoutDirection = LocalLayoutDirection.current
	val progress by animateFloatAsState(if (scrolled) 0f else 1f)
	var biographyExpanded by rememberSaveable(artistName, subtitle) { mutableStateOf(false) }
	val biographyScrollState = rememberScrollState()
	BoxWithConstraints(
		modifier = Modifier.fillMaxWidth()
	) {
		val headingHeight = (400.dp / (maxWidth / 300.dp)) + innerPadding.calculateTopPadding()
		Column(
			modifier = Modifier.fillMaxWidth(),
			horizontalAlignment = Alignment.Start
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(headingHeight)
					.background(MaterialTheme.colorScheme.surfaceContainer)
			) {
				CoverArt(
					coverArtId = coverArtId,
					imageUrl = imageUrl,
					imageRequestHeaders = imageRequestHeaders,
					imageDiagnosticLabel = imageDiagnosticLabel,
					modifier = Modifier.fillMaxSize(),
					shape = RectangleShape,
					square = false,
					artworkResolving = artworkResolving
				)
				Box(
					modifier = Modifier
						.fillMaxSize()
						.background(
							Brush.linearGradient(
								0.025f to MaterialTheme.colorScheme.background,
								1.0f to Color.Transparent,
								start = Offset(0f, Float.POSITIVE_INFINITY),
								end = Offset(0f, 0f)
							)
						)
				)

				Column(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.padding(horizontal = 20.dp)
						.padding(start = innerPadding.calculateStartPadding(layoutDirection))
						.padding(end = innerPadding.calculateEndPadding(layoutDirection)),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					MarqueeText(
						text = artistName,
						style = MaterialTheme.typography.displaySmall.copy(
							fontWeight = FontWeight.Bold,
						),
						modifier = Modifier
							.fillMaxWidth()
							.alpha(progress)
							.scale(progress)
					)
				}
			}
			artistBiographyDisplayText(subtitle, expanded = biographyExpanded)?.let { biography ->
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 20.dp)
						.padding(start = innerPadding.calculateStartPadding(layoutDirection))
						.padding(end = innerPadding.calculateEndPadding(layoutDirection))
						.padding(top = 12.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					if (biographyExpanded) {
						ScrollableArtistBiographyText(
							text = biography,
							scrollState = biographyScrollState
						)
					} else {
						Text(
							text = biography,
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurface,
							modifier = Modifier.widthIn(max = 500.dp)
						)
					}
					if (shouldShowArtistBiographyToggle(subtitle)) {
						Text(
							text = stringResource(
								if (biographyExpanded) Res.string.action_less else Res.string.action_more
							),
							style = MaterialTheme.typography.labelLarge,
							color = MaterialTheme.colorScheme.primary,
							modifier = Modifier.clickable {
								biographyExpanded = !biographyExpanded
							}
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ScrollableArtistBiographyText(
	text: String,
	scrollState: ScrollState
) {
	val fades = artistBiographyScrollFades(
		scrollValue = scrollState.value,
		maxScrollValue = scrollState.maxValue
	)
	Box(
		modifier = Modifier
			.widthIn(max = 500.dp)
			.heightIn(max = 160.dp)
	) {
		Text(
			text = text,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(scrollState)
		)
		ArtistBiographyGradientFades(fades)
	}
}

@Composable
private fun BoxScope.ArtistBiographyGradientFades(
	fades: ArtistBiographyScrollFades
) {
	val fadeColor = MaterialTheme.colorScheme.background.copy(alpha = .96f)
	if (fades.showTop) {
		Box(
			modifier = Modifier
				.align(Alignment.TopStart)
				.fillMaxWidth()
				.height(28.dp)
				.background(
					Brush.easedVerticalGradient(
						startY = 0f,
						endY = Float.POSITIVE_INFINITY,
						color = fadeColor
					)
				)
		)
	}
	if (fades.showBottom) {
		Box(
			modifier = Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.height(28.dp)
				.background(
					Brush.easedVerticalGradient(
						color = fadeColor
					)
				)
		)
	}
}
