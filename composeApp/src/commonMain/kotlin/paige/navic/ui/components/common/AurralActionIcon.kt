package paige.navic.ui.components.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Aurral

enum class AurralActionIconOverlay {
	None,
	Crossed,
	QuestionMark,
	Progress
}

@Composable
fun AurralActionIcon(
	overlay: AurralActionIconOverlay,
	contentDescription: String?,
	modifier: Modifier = Modifier,
	size: Dp = 24.dp,
	tint: Color = MaterialTheme.colorScheme.onSurface,
	overlayColor: Color = MaterialTheme.colorScheme.primary,
	progressColor: Color = MaterialTheme.colorScheme.primary
) {
	Box(
		modifier = modifier.size(size),
		contentAlignment = Alignment.Center
	) {
		Icon(
			imageVector = Icons.Outlined.Aurral,
			contentDescription = contentDescription,
			tint = tint,
			modifier = Modifier.size(width = size, height = size * .75f)
		)
		when (overlay) {
			AurralActionIconOverlay.Crossed -> {
				Canvas(modifier = Modifier.size(size)) {
					drawLine(
						color = overlayColor,
						start = Offset(
							x = this.size.width * .2f,
							y = this.size.height * .2f
						),
						end = Offset(
							x = this.size.width * .8f,
							y = this.size.height * .8f
						),
						strokeWidth = 2.4.dp.toPx(),
						cap = StrokeCap.Round
					)
				}
			}

			AurralActionIconOverlay.QuestionMark -> {
				Text(
					text = "?",
					color = MaterialTheme.colorScheme.onPrimary,
					fontSize = 9.sp,
					fontWeight = FontWeight.Bold,
					textAlign = TextAlign.Center,
					modifier = Modifier
						.align(Alignment.TopEnd)
						.size(14.dp)
						.clip(CircleShape)
						.background(overlayColor)
				)
			}

			AurralActionIconOverlay.Progress -> {
				CircularProgressIndicator(
					modifier = Modifier
						.align(Alignment.TopEnd)
						.size(14.dp),
					strokeWidth = 2.dp,
					color = progressColor
				)
			}

			AurralActionIconOverlay.None -> Unit
		}
	}
}
