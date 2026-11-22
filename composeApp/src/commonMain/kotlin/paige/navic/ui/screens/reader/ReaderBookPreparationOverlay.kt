package paige.navic.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_done
import navic.composeapp.generated.resources.action_retry
import navic.composeapp.generated.resources.info_reader_book_pages_prepared
import navic.composeapp.generated.resources.info_reader_book_preparation_hint
import navic.composeapp.generated.resources.info_reader_book_preparation_incomplete
import navic.composeapp.generated.resources.info_reader_book_preparation_progress
import navic.composeapp.generated.resources.info_reader_book_preparation_turn_landscape
import navic.composeapp.generated.resources.info_reader_book_preparation_turn_portrait
import navic.composeapp.generated.resources.info_reader_book_preparing
import org.jetbrains.compose.resources.stringResource
import paige.navic.reader.ReaderBookPreparationPhase
import paige.navic.reader.ReaderBookPreparationProgress
import paige.navic.reader.ReaderPreparationOrientation

/**
 * #66 "Prepare pages": covers the reader while it prepares the whole book and consumes every
 * gesture, so no page turn or menu interrupts the background batches. #68: when "Both layouts"
 * still lacks [awaitingOrientation] and the system did not rotate the reader (Android 16 ignores
 * orientation requests on large screens), it asks the user to turn the device or finish with Done.
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
	Box(
		modifier = modifier
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
			.pointerInput(Unit) {
				awaitEachGesture {
					awaitFirstDown(requireUnconsumed = false).consume()
					do {
						val event = awaitPointerEvent()
						event.changes.forEach { change -> change.consume() }
					} while (event.changes.any { change -> change.pressed })
				}
			}
	) {
		val awaiting = awaitingOrientation.takeIf { progress.phase == ReaderBookPreparationPhase.Complete }
		Surface(
			shape = RoundedCornerShape(20.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.align(Alignment.Center).padding(20.dp)
		) {
			Column(
				modifier = Modifier
					.widthIn(min = 260.dp, max = 440.dp)
					.padding(horizontal = 20.dp, vertical = 16.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp)
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
					style = MaterialTheme.typography.titleMedium
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
							ReaderPreparationOrientation.Portrait -> Res.string.info_reader_book_preparation_turn_portrait
							ReaderPreparationOrientation.Landscape -> Res.string.info_reader_book_preparation_turn_landscape
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
