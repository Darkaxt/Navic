package paige.navic.ui.screens.reader

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_reader_cache_this_chapter
import navic.composeapp.generated.resources.action_reader_cache_whole_book
import navic.composeapp.generated.resources.info_reader_book_cache_progress
import navic.composeapp.generated.resources.action_retry
import navic.composeapp.generated.resources.info_reader_page_preparation_failed
import navic.composeapp.generated.resources.info_reader_preparation_detail
import navic.composeapp.generated.resources.info_reader_preparation_measuring
import navic.composeapp.generated.resources.info_reader_preparation_opening_hint
import navic.composeapp.generated.resources.info_reader_preparing_book
import org.jetbrains.compose.resources.stringResource
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationPresentation
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPaginationProfileStatus

/** Same colour as the native preparation shield underneath, so the screen never flashes. */
private val ReaderOpeningPreparationBackground = Color(0xFF202329)
private val ReaderOpeningPreparationText = Color(0xFFE8E6E1)
private val ReaderOpeningPreparationMuted = Color(0xFFA9ABB0)
private val ReaderOpeningPreparationAccent = Color(0xFFD9B36C)

/**
 * The book is not entered until its opening pages exist: blocking preparation (first open, or a
 * cache that must be rebuilt) is a screen of its own, between launch and the book, never a card
 * over the reading page. It follows the cache scope chosen on the open sheet and opens the book by itself.
 */
internal fun readerOpeningPreparationVisible(state: ReaderPagePreparationState): Boolean =
	state.presentation == ReaderPagePreparationPresentation.Cover && !state.layoutReviewPending

@Composable
internal fun ReaderOpeningPreparationScreen(
	title: String,
	state: ReaderPagePreparationState,
	profile: ReaderPaginationProfileStatus,
	onRetry: () -> Unit,
	modifier: Modifier = Modifier,
	/** "Book" cache scope: the share of the whole book already cached, shown under the chapter's bar. */
	bookCacheFraction: Float? = null
) {
	val failed = state.phase == ReaderPagePreparationPhase.Failed
	val ready = state.phase == ReaderPagePreparationPhase.Ready
	val measuringTotal = profile.totalSections?.takeIf { profile.status == "measuring" && it > 0 }
	Box(modifier = modifier.background(ReaderOpeningPreparationBackground)) {
		Column(
			modifier = Modifier
				.align(Alignment.Center)
				.widthIn(min = 280.dp, max = 480.dp)
				.padding(horizontal = 32.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleLarge,
				color = ReaderOpeningPreparationText,
				textAlign = TextAlign.Center,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = stringResource(
					if (failed) Res.string.info_reader_page_preparation_failed else Res.string.info_reader_preparing_book
				),
				style = MaterialTheme.typography.titleSmall,
				color = ReaderOpeningPreparationMuted
			)
			if (!failed) {
				val progress = when {
					ready -> 1f
					measuringTotal != null -> profile.progressFraction
					state.hasDeterminateProgress -> state.progress
					else -> null
				}
				if (bookCacheFraction != null) {
					ReaderOpeningPreparationBar(
						label = stringResource(Res.string.action_reader_cache_this_chapter),
						progress = progress
					)
					ReaderOpeningPreparationBar(
						label = stringResource(Res.string.action_reader_cache_whole_book),
						progress = bookCacheFraction
					)
				} else if (progress != null) {
					LinearProgressIndicator(
						progress = { progress.coerceIn(0f, 1f) },
						modifier = Modifier.fillMaxWidth(),
						color = ReaderOpeningPreparationAccent,
						trackColor = ReaderOpeningPreparationAccent.copy(alpha = 0.2f)
					)
				} else {
					LinearProgressIndicator(
						modifier = Modifier.fillMaxWidth(),
						color = ReaderOpeningPreparationAccent,
						trackColor = ReaderOpeningPreparationAccent.copy(alpha = 0.2f)
					)
				}
				val detail = when {
					measuringTotal != null -> stringResource(
						Res.string.info_reader_preparation_measuring,
						(profile.completedSections ?: 0).coerceIn(0, measuringTotal),
						measuringTotal
					)
					state.hasDeterminateProgress -> stringResource(
						Res.string.info_reader_preparation_detail,
						state.completedCount,
						state.requiredCount,
						"${(state.progress.coerceIn(0f, 1f) * 100f).toInt()}%"
					)
					else -> null
				}
				detail?.let {
					Text(text = it, style = MaterialTheme.typography.bodyMedium, color = ReaderOpeningPreparationText)
				}
				state.activePageLabel?.takeIf { measuringTotal == null }?.let { label ->
					Text(text = label, style = MaterialTheme.typography.labelMedium, color = ReaderOpeningPreparationMuted)
				}
				Text(
					text = stringResource(Res.string.info_reader_preparation_opening_hint),
					style = MaterialTheme.typography.bodySmall,
					color = ReaderOpeningPreparationMuted,
					textAlign = TextAlign.Center
				)
			} else {
				state.error?.takeIf { it.isNotBlank() }?.let { error ->
					Text(
						text = error,
						style = MaterialTheme.typography.bodySmall,
						color = ReaderOpeningPreparationMuted,
						textAlign = TextAlign.Center,
						maxLines = 3,
						overflow = TextOverflow.Ellipsis
					)
				}
				TextButton(onClick = onRetry) {
					Text(stringResource(Res.string.action_retry), color = ReaderOpeningPreparationAccent)
				}
			}
		}
	}
}

/** One labelled bar of the opening screen ("Chapter", "Book"); indeterminate while [progress] is unknown. */
@Composable
private fun ReaderOpeningPreparationBar(label: String, progress: Float?) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = ReaderOpeningPreparationMuted,
			modifier = Modifier.widthIn(min = 64.dp)
		)
		if (progress != null) {
			LinearProgressIndicator(
				progress = { progress.coerceIn(0f, 1f) },
				modifier = Modifier.weight(1f),
				color = ReaderOpeningPreparationAccent,
				trackColor = ReaderOpeningPreparationAccent.copy(alpha = 0.2f)
			)
			Text(
				text = "${(progress.coerceIn(0f, 1f) * 100f).toInt()}%",
				style = MaterialTheme.typography.labelMedium,
				color = ReaderOpeningPreparationText,
				modifier = Modifier.widthIn(min = 40.dp)
			)
		} else {
			LinearProgressIndicator(
				modifier = Modifier.weight(1f),
				color = ReaderOpeningPreparationAccent,
				trackColor = ReaderOpeningPreparationAccent.copy(alpha = 0.2f)
			)
		}
	}
}

/**
 * "Book" cache scope, after the book has opened: the rest of the book is cached in the background,
 * and this small pill shows how far that is, without buttons, until the whole book is cached.
 */
@Composable
internal fun ReaderBookCacheProgressPill(fraction: Float, modifier: Modifier = Modifier) {
	Box(modifier = modifier) {
		Surface(
			shape = RoundedCornerShape(16.dp),
			color = ReaderOpeningPreparationBackground.copy(alpha = 0.88f),
			modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
		) {
			Column(
				modifier = Modifier.widthIn(min = 180.dp).padding(horizontal = 14.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp)
			) {
				Text(
					text = stringResource(
						Res.string.info_reader_book_cache_progress,
						"${(fraction.coerceIn(0f, 1f) * 100f).toInt()}%"
					),
					style = MaterialTheme.typography.labelMedium,
					color = ReaderOpeningPreparationText
				)
				LinearProgressIndicator(
					progress = { fraction.coerceIn(0f, 1f) },
					modifier = Modifier.fillMaxWidth(),
					color = ReaderOpeningPreparationAccent,
					trackColor = ReaderOpeningPreparationAccent.copy(alpha = 0.2f),
					drawStopIndicator = {}
				)
			}
		}
	}
}
