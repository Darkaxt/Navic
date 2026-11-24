package paige.navic.ui.screens.bindery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_open
import navic.composeapp.generated.resources.action_reader_cache_this_chapter
import navic.composeapp.generated.resources.action_reader_cache_whole_book
import navic.composeapp.generated.resources.action_reader_import_progress
import navic.composeapp.generated.resources.info_reader_cache_coverage
import navic.composeapp.generated.resources.info_reader_cache_coverage_none
import navic.composeapp.generated.resources.info_reader_cache_coverage_ready
import navic.composeapp.generated.resources.info_reader_cache_coverage_unknown
import navic.composeapp.generated.resources.info_reader_cache_scope_question
import navic.composeapp.generated.resources.label_reader_open_audio_language
import navic.composeapp.generated.resources.label_reader_open_book_language
import navic.composeapp.generated.resources.option_ebook_only
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.isReaderBookPrepared
import paige.navic.reader.ReaderPageCacheScope
import paige.navic.reader.ReaderPreparationOrientation
import paige.navic.reader.decodeReaderReadingProgress
import paige.navic.reader.readerPageCacheScopeForBook
import paige.navic.reader.readerSettingsForBook
import paige.navic.reader.setReaderPageCacheScopeForBook
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.reader.rememberReaderPageCacheCoverage
import kotlin.math.roundToInt

/**
 * The one sheet an ebook opens through: page-cache scope (chapter or book) with how much is already
 * cached, the book and audiobook languages (remembered per book), carrying progress over from another
 * edition, then the ebook-only or Whispersync audiobook opens. The reader then opens without questions.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun BinderyReaderOpenSheet(
	bookId: String,
	bookTitle: String,
	initialRow: BinderyBookVersionRow,
	editions: List<BinderyBookVersionRow>,
	opdsBaseUrl: String,
	readerLaunchTargetAspectRatio: Double?,
	onDismissRequest: () -> Unit,
	onOpenReader: (Screen.Reader) -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val allEditions = remember(editions, initialRow) {
		(listOf(initialRow) + editions).distinctBy { row -> row.id }
	}
	val progresses = remember(bookId) {
		decodeReaderReadingProgress(preferenceManager.readerReadingProgressJson)
			.filter { progress -> progress.bookId.trim() == bookId.trim() }
	}
	val rememberedChoice = remember(bookId) {
		binderyBookLanguageChoice(preferenceManager.binderyBookLanguageChoicesJson, bookId)
	}
	var row by remember(initialRow) { mutableStateOf(initialRow) }
	var audioLanguage by remember(bookId) { mutableStateOf(rememberedChoice.audio) }
	var cacheScope by remember(bookId) { mutableStateOf(preferenceManager.readerPageCacheScopeForBook(bookId)) }
	val bookLanguages = binderyEditionLanguages(allEditions)
	val allMatches = row.whispersyncAudiobookLaunchMatches()
	val audioLanguages = binderyMatchLanguages(allMatches)
	val matches = binderyMatchesForLanguage(allMatches, audioLanguage)
	val ebookDestination = binderyReaderDestinationForVersionRow(
		row = row,
		bookId = bookId,
		bookTitle = bookTitle,
		opdsBaseUrl = opdsBaseUrl,
		fullscreenCoverTargetAspectRatio = readerLaunchTargetAspectRatio
	)
	val matchDestinations = matches.map { match ->
		match to binderyWhispersyncReaderDestinationForMatch(
			ebookRow = row,
			match = match,
			bookId = bookId,
			bookTitle = bookTitle,
			opdsBaseUrl = opdsBaseUrl,
			fullscreenCoverTargetAspectRatio = readerLaunchTargetAspectRatio
		)
	}
	val importSuggestion = remember(row.id, progresses) {
		binderyProgressImportSuggestion(row, allEditions, progresses)
	}
	var importProgress by remember(row.id) { mutableStateOf(importSuggestion?.selectedByDefault == true) }
	val coverage = rememberReaderPageCacheCoverage(
		listOfNotNull(ebookDestination) + matchDestinations.mapNotNull { (_, destination) -> destination }
	)
	val fullyPrepared = preferenceManager.isReaderBookPrepared(
		bookId,
		preferenceManager.readerSettingsForBook(bookId),
		ReaderPreparationOrientation.forAspectRatio(readerLaunchTargetAspectRatio)
			?: ReaderPreparationOrientation.Landscape
	)

	fun open(destination: Screen.Reader) {
		preferenceManager.binderyBookLanguageChoicesJson = binderyBookLanguageChoicesJsonWithUpdate(
			json = preferenceManager.binderyBookLanguageChoicesJson,
			bookId = bookId,
			choice = BinderyBookLanguageChoice(
				ebook = row.language ?: rememberedChoice.ebook,
				audio = audioLanguage
			)
		)
		val suggestion = importSuggestion
		onOpenReader(
			if (importProgress && suggestion != null) {
				destination.copy(startCfi = null, startHref = null, startProgress = suggestion.fraction)
			} else {
				destination
			}
		)
	}

	ModalBottomSheet(onDismissRequest = onDismissRequest) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 20.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text(
				text = bookTitle,
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = listOfNotNull(row.title, row.subtitle).joinToString(" · "),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			if (bookLanguages.size > 1) {
				BinderyReaderOpenLanguageRow(
					label = stringResource(Res.string.label_reader_open_book_language),
					languages = bookLanguages,
					selected = row.language,
					onSelect = { language ->
						row = binderyEditionForLanguage(allEditions, language, row, progresses)
					}
				)
			}
			if (audioLanguages.size > 1) {
				BinderyReaderOpenLanguageRow(
					label = stringResource(Res.string.label_reader_open_audio_language),
					languages = audioLanguages,
					selected = audioLanguage?.takeIf { it in audioLanguages },
					onSelect = { language -> audioLanguage = language }
				)
			}
			Text(
				text = stringResource(Res.string.info_reader_cache_scope_question),
				style = MaterialTheme.typography.labelLarge
			)
			SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
				listOf(
					ReaderPageCacheScope.PerChapter to Res.string.action_reader_cache_this_chapter,
					ReaderPageCacheScope.FullBook to Res.string.action_reader_cache_whole_book
				).forEachIndexed { index, (scope, label) ->
					SegmentedButton(
						shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
						onClick = {
							cacheScope = scope
							preferenceManager.setReaderPageCacheScopeForBook(bookId, scope)
						},
						selected = cacheScope == scope,
						label = { Text(stringResource(label)) }
					)
				}
			}
			Text(
				text = when {
					fullyPrepared -> stringResource(Res.string.info_reader_cache_coverage_ready)
					coverage == null -> ""
					coverage.cachedPages == 0 -> stringResource(Res.string.info_reader_cache_coverage_none)
					coverage.totalPages > 0 -> stringResource(
						Res.string.info_reader_cache_coverage,
						coverage.cachedPages.toString(),
						coverage.totalPages.toString(),
						"${coverage.percent}%"
					)
					else -> stringResource(Res.string.info_reader_cache_coverage_unknown, coverage.cachedPages.toString())
				},
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			importSuggestion?.let { suggestion ->
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically
				) {
					Checkbox(checked = importProgress, onCheckedChange = { importProgress = it })
					Text(
						text = stringResource(
							Res.string.action_reader_import_progress,
							"${(suggestion.fraction * 100.0).roundToInt()}%",
							suggestion.sourceTitle
						),
						style = MaterialTheme.typography.bodyMedium,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
			Button(
				onClick = { ebookDestination?.let(::open) },
				enabled = ebookDestination != null,
				modifier = Modifier.fillMaxWidth()
			) {
				Text(stringResource(if (matches.isEmpty()) Res.string.action_open else Res.string.option_ebook_only))
			}
			matchDestinations.forEach { (match, destination) ->
				Surface(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(8.dp),
					color = MaterialTheme.colorScheme.surfaceContainerHighest
				) {
					Row(
						modifier = Modifier.padding(12.dp),
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(
							modifier = Modifier.weight(1f),
							verticalArrangement = Arrangement.spacedBy(3.dp)
						) {
							Text(
								text = match.oppositeTitle,
								style = MaterialTheme.typography.titleSmall,
								maxLines = 2,
								overflow = TextOverflow.Ellipsis
							)
							Text(
								text = listOfNotNull(
									"Ready",
									match.oppositeLanguage?.let(::binderyLanguageDisplayName),
									match.coveragePercent?.let { "Coverage $it%" },
									match.scorePercent?.let { "Score $it%" }
								).joinToString("  "),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Button(
							onClick = { destination?.let(::open) },
							enabled = destination != null
						) {
							Text(stringResource(Res.string.action_open))
						}
					}
				}
			}
			TextButton(
				onClick = onDismissRequest,
				modifier = Modifier.align(Alignment.End)
			) {
				Text(stringResource(Res.string.action_cancel))
			}
		}
	}
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BinderyReaderOpenLanguageRow(
	label: String,
	languages: List<String>,
	selected: String?,
	onSelect: (String) -> Unit
) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(text = label, style = MaterialTheme.typography.labelLarge)
		FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			languages.forEach { language ->
				FilterChip(
					selected = language == selected,
					onClick = { onSelect(language) },
					label = { Text(binderyLanguageDisplayName(language)) }
				)
			}
		}
	}
}
