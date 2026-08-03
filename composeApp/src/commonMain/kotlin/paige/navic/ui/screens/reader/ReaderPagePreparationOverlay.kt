package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_retry
import navic.composeapp.generated.resources.info_reader_page_preparation_failed
import navic.composeapp.generated.resources.info_reader_preparing_pages
import org.jetbrains.compose.resources.stringResource
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationPresentation
import paige.navic.reader.ReaderPagePreparationState

@Composable
internal fun ReaderPagePreparationOverlay(
	state: ReaderPagePreparationState,
	onRetry: () -> Unit,
	modifier: Modifier = Modifier
) {
	if (state.presentation == ReaderPagePreparationPresentation.Hidden) return

	Box(modifier = modifier) {
		Surface(
			shape = RoundedCornerShape(
				if (state.presentation == ReaderPagePreparationPresentation.Cover) 20.dp else 16.dp
			),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = 0.92f),
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(
					horizontal = 20.dp,
					vertical = if (state.presentation == ReaderPagePreparationPresentation.Cover) 36.dp else 20.dp
				)
		) {
			Column(
				modifier = Modifier
					.widthIn(min = 220.dp, max = 420.dp)
					.padding(horizontal = 18.dp, vertical = 14.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Text(
					text = if (state.phase == ReaderPagePreparationPhase.Failed) {
						stringResource(Res.string.info_reader_page_preparation_failed)
					} else {
						stringResource(Res.string.info_reader_preparing_pages)
					},
					style = MaterialTheme.typography.titleSmall
				)
				state.activePageLabel?.let { label ->
					Text(
						text = label,
						style = MaterialTheme.typography.labelMedium,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
				if (state.showsProgress) {
					if (state.hasDeterminateProgress) {
						LinearProgressIndicator(
							progress = { state.progress.coerceIn(0f, 1f) },
							modifier = Modifier.fillMaxWidth(),
							color = MaterialTheme.colorScheme.primary,
							trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
						)
					} else {
						LinearProgressIndicator(
							modifier = Modifier.fillMaxWidth(),
							color = MaterialTheme.colorScheme.primary,
							trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
						)
					}
				}
				state.error?.takeIf { it.isNotBlank() }?.let { error ->
					Text(
						text = error,
						style = MaterialTheme.typography.bodySmall,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis
					)
				}
				if (state.retryable) {
					TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
						Text(stringResource(Res.string.action_retry))
					}
				}
			}
		}
	}
}
