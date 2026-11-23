package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_done
import navic.composeapp.generated.resources.action_retry
import navic.composeapp.generated.resources.info_reader_book_pages_prepared
import navic.composeapp.generated.resources.info_reader_book_preparation_hint
import navic.composeapp.generated.resources.info_reader_book_preparation_incomplete
import navic.composeapp.generated.resources.info_reader_book_preparation_progress
import navic.composeapp.generated.resources.info_reader_book_preparation_landscape_unavailable
import navic.composeapp.generated.resources.info_reader_book_preparation_portrait_unavailable
import navic.composeapp.generated.resources.info_reader_book_preparing
import org.jetbrains.compose.resources.stringResource
import paige.navic.reader.ReaderBookPreparationPhase
import paige.navic.reader.ReaderBookPreparationProgress
import paige.navic.reader.ReaderPreparationOrientation

/**
 * #66 "Prepare pages": a small card at the bottom of the reader while it prepares the whole book.
 * #71: it no longer blocks the reader; turns and menus keep working while pages are prepared. #70: "Both layouts"
 * prepares the other orientation in place, without rotating; when that is unavailable or
 * incomplete, [awaitingOrientation] names it and Done finishes with the layouts prepared so far.
 */
@Composable
internal fun ReaderBookPreparationOverlay(
	progress: ReaderBookPreparationProgress,
	onRetry: () -> Unit,
	onCancel: () -> Unit,
	modifier: Modifier = Modifier,
	awaitingOrientation: ReaderPreparationOrientation? = null,
	onDone: () -> Unit = {}
) {
	Box(modifier = modifier) {
		val awaiting = awaitingOrientation.takeIf {
			progress.phase == ReaderBookPreparationPhase.Complete ||
				progress.phase == ReaderBookPreparationPhase.Incomplete
		}
		Surface(
			shape = RoundedCornerShape(20.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
			contentColor = MaterialTheme.colorScheme.onSurface,
			shadowElevation = 6.dp,
			modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp)
		) {
			Column(
				modifier = Modifier
					.widthIn(min = 260.dp, max = 400.dp)
					.padding(horizontal = 16.dp, vertical = 10.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp)
			) {
				Text(
					text = stringResource(
						when (progress.phase) {
							ReaderBookPreparationPhase.Complete -> Res.string.info_reader_book_pages_prepared
							ReaderBookPreparationPhase.Incomplete -> Res.string.info_reader_book_preparation_incomplete
							ReaderBookPreparationPhase.Starting,
							ReaderBookPreparationPhase.Preparing -> Res.string.info_reader_book_preparing
						}
					),
					style = MaterialTheme.typography.titleSmall
				)
				if (progress.plannedPages > 0) {
					LinearProgressIndicator(
						progress = { progress.fraction },
						modifier = Modifier.fillMaxWidth()
					)
					Text(
						text = stringResource(
							Res.string.info_reader_book_preparation_progress,
							progress.preparedPages,
							progress.plannedPages,
							progress.preparedChapters,
							progress.plannedChapters
						),
						style = MaterialTheme.typography.bodyMedium
					)
				} else {
					LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
				}
				Text(
					text = stringResource(
						when (awaiting) {
							ReaderPreparationOrientation.Portrait -> Res.string.info_reader_book_preparation_portrait_unavailable
							ReaderPreparationOrientation.Landscape -> Res.string.info_reader_book_preparation_landscape_unavailable
							null -> Res.string.info_reader_book_preparation_hint
						}
					),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End
				) {
					if (progress.phase == ReaderBookPreparationPhase.Incomplete) {
						TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
					}
					if (awaiting != null) {
						TextButton(onClick = onDone) { Text(stringResource(Res.string.action_done)) }
					} else {
						TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
					}
				}
			}
		}
	}
}
