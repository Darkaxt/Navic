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
import navic.composeapp.generated.resources.action_retry
import navic.composeapp.generated.resources.info_reader_page_preparation_failed
import navic.composeapp.generated.resources.info_reader_preparing_pages
import org.jetbrains.compose.resources.stringResource
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderPreparationPresentation

@Composable
internal fun ReaderPagePreparationOverlay(
	preparation: ReaderPreparationPresentation,
	diagnostic: ReaderDiagnosticPresentation,
	onRetry: () -> Unit,
	onCancel: () -> Unit,
	modifier: Modifier = Modifier
) {
	val blocking = preparation as? ReaderPreparationPresentation.Blocking
	val failure = diagnostic as? ReaderDiagnosticPresentation.Failure
	if (blocking == null && failure == null) return

	Box(modifier = modifier) {
		Surface(
			shape = RoundedCornerShape(if (blocking != null) 20.dp else 16.dp),
			color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = 0.92f),
			contentColor = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier
				.align(if (failure != null) Alignment.Center else Alignment.BottomCenter)
				.padding(
					horizontal = 20.dp,
					vertical = if (blocking != null) 36.dp else 20.dp
				)
		) {
			Column(
				modifier = Modifier
					.widthIn(min = 220.dp, max = 420.dp)
					.padding(horizontal = 18.dp, vertical = 14.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Text(
					text = stringResource(
						if (failure != null) {
							Res.string.info_reader_page_preparation_failed
						} else {
							Res.string.info_reader_preparing_pages
						}
					),
					style = MaterialTheme.typography.titleSmall
				)
				blocking?.let { progress ->
					if (progress.determinate) {
						LinearProgressIndicator(
							progress = {
								progress.completedCount.toFloat() /
									progress.requiredCount.toFloat()
							},
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
				if (failure?.retryable == true || failure?.cancellable == true) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.End
					) {
						if (failure.cancellable) {
							TextButton(onClick = onCancel) {
								Text(stringResource(Res.string.action_cancel))
							}
						}
						if (failure.retryable) {
							TextButton(onClick = onRetry) {
								Text(stringResource(Res.string.action_retry))
							}
						}
					}
				}
			}
		}
	}
}
