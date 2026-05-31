package paige.navic.ui.screens.aurral

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.LocalPlatformContext
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.ui.components.common.AurralAcquisitionProgressBar
import paige.navic.ui.components.common.AurralOwnershipStatusDot
import paige.navic.ui.components.common.CoverArt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarouselItemScope.AurralRecommendedAlbumItem(
	album: AurralAlbumSearchItem,
	imageRequestHeaders: Map<String, String>,
	ownershipStatus: AurralOwnershipStatus = aurralSearchAlbumOwnershipStatus(album),
	onClick: () -> Unit
) {
	AurralAlbumSearchCard(
		album = album,
		imageRequestHeaders = imageRequestHeaders,
		ownershipStatus = ownershipStatus,
		onClick = onClick
	)
}

@Composable
fun AurralAlbumSearchCard(
	album: AurralAlbumSearchItem,
	imageRequestHeaders: Map<String, String>,
	ownershipStatus: AurralOwnershipStatus = aurralSearchAlbumOwnershipStatus(album),
	modifier: Modifier = Modifier,
	onClick: () -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val year = album.releaseDate
		?.trim()
		?.take(4)
		?.takeIf { value -> value.length == 4 && value.all { it.isDigit() } }
	val progress = album.status?.let(::aurralAcquisitionProgress)
	val colorFilter = remember(ownershipStatus) {
		if (ownershipStatus == AurralOwnershipStatus.Missing) {
			ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
		} else {
			null
		}
	}

	Column(modifier.fillMaxWidth()) {
		Box(Modifier.fillMaxWidth()) {
			CoverArt(
				coverArtId = null,
				imageUrl = album.coverUrl,
				imageCacheKey = "aurral-recommendation-${album.id}",
				imageRequestHeaders = imageRequestHeaders,
				contentDescription = album.title,
				fallbackKind = album.primaryType ?: "Album",
				modifier = Modifier.fillMaxWidth(),
				shape = RectangleShape,
				colorFilter = colorFilter,
				onClick = {
					platformContext.clickSound()
					onClick()
				}
			)
			progress?.let {
				AurralAcquisitionProgressBar(
					progress = it,
					modifier = Modifier.align(Alignment.BottomCenter)
				)
			}
			AurralOwnershipStatusDot(
				status = ownershipStatus,
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(8.dp)
			)
		}
		Text(
			text = album.title,
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.Medium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
		)
		Text(
			text = year ?: album.artistName,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp)
		)
	}
}
