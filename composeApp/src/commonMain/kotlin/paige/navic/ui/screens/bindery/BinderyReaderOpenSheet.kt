package paige.navic.ui.screens.bindery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_reader_cache_this_chapter
import navic.composeapp.generated.resources.action_reader_cache_whole_book
import navic.composeapp.generated.resources.action_reader_open_read
import navic.composeapp.generated.resources.action_reader_open_read_and_listen
import navic.composeapp.generated.resources.ic_whispersync
import navic.composeapp.generated.resources.info_reader_cache_coverage
import navic.composeapp.generated.resources.info_reader_cache_coverage_none
import navic.composeapp.generated.resources.info_reader_cache_coverage_ready
import navic.composeapp.generated.resources.info_reader_cache_coverage_unknown
import navic.composeapp.generated.resources.info_reader_open_not_started
import navic.composeapp.generated.resources.info_reader_open_read_fraction
import navic.composeapp.generated.resources.label_reader_open_audio_language
import navic.composeapp.generated.resources.label_reader_open_book_language
import navic.composeapp.generated.resources.label_reader_open_cache_type
import navic.composeapp.generated.resources.label_reader_open_media_type
import navic.composeapp.generated.resources.label_reader_open_narrator
import navic.composeapp.generated.resources.label_reader_open_start_at
import navic.composeapp.generated.resources.label_reader_open_this_edition
import navic.composeapp.generated.resources.option_reader_open_audiobook
import navic.composeapp.generated.resources.option_reader_open_ebook
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.data.remote.bindery.binderyApiKeyHeaders
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.isReaderBookPrepared
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Book
import paige.navic.reader.ReaderPageCacheScope
import paige.navic.reader.ReaderPreparationOrientation
import paige.navic.reader.decodeReaderReadingProgress
import paige.navic.reader.readerPageCacheScopeForBook
import paige.navic.reader.readerSettingsForBook
import paige.navic.reader.setReaderPageCacheScopeForBook
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.reader.ReaderPageCacheCoverage
import paige.navic.ui.screens.reader.rememberReaderPageCacheCoverage
import kotlin.math.roundToInt

/**
 * The one sheet an ebook opens through. A header (cover, edition, how far it has been read), then one
 * compact line per choice: media type, narrator, languages, where to start (this edition or the
 * position carried over from a more recently read edition), and the cache type with how much is
 * already cached. One button opens the reader, which then starts without questions. Choices are
 * remembered per book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BinderyReaderOpenSheet(
	bookId: String,
	bookTitle: String,
	coverImageUrl: String?,
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
	var wantsAudiobook by remember(bookId) { mutableStateOf(rememberedChoice.audiobook ?: true) }
	var narratorIndex by remember(bookId) { mutableStateOf(0) }
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
	val matchDestinations = matches.mapNotNull { match ->
		binderyWhispersyncReaderDestinationForMatch(
			ebookRow = row,
			match = match,
			bookId = bookId,
			bookTitle = bookTitle,
			opdsBaseUrl = opdsBaseUrl,
			fullscreenCoverTargetAspectRatio = readerLaunchTargetAspectRatio
		)?.let { destination -> match to destination }
	}
	val audiobook = wantsAudiobook && matchDestinations.isNotEmpty()
	val selectedMatch = matchDestinations.getOrNull(narratorIndex) ?: matchDestinations.firstOrNull()
	val destination = if (audiobook) selectedMatch?.second else ebookDestination
	val ownFraction = remember(row.id, progresses) { binderyEditionProgressFraction(row, progresses) }
	val importSuggestion = remember(row.id, progresses) {
		binderyProgressImportSuggestion(row, allEditions, progresses)
	}
	var startFromOtherEdition by remember(row.id) { mutableStateOf(importSuggestion?.selectedByDefault == true) }
	val coverage = rememberReaderPageCacheCoverage(
		listOfNotNull(ebookDestination) + matchDestinations.map { (_, target) -> target }
	)
	val fullyPrepared = preferenceManager.isReaderBookPrepared(
		bookId,
		preferenceManager.readerSettingsForBook(bookId),
		ReaderPreparationOrientation.forAspectRatio(readerLaunchTargetAspectRatio)
			?: ReaderPreparationOrientation.Landscape
	)

	fun open(target: Screen.Reader) {
		preferenceManager.binderyBookLanguageChoicesJson = binderyBookLanguageChoicesJsonWithUpdate(
			json = preferenceManager.binderyBookLanguageChoicesJson,
			bookId = bookId,
			choice = BinderyBookLanguageChoice(
				ebook = row.language ?: rememberedChoice.ebook,
				audio = audioLanguage,
				audiobook = if (matchDestinations.isNotEmpty()) wantsAudiobook else rememberedChoice.audiobook
			)
		)
		val suggestion = importSuggestion
		onOpenReader(
			if (startFromOtherEdition && suggestion != null) {
				target.copy(startCfi = null, startHref = null, startProgress = suggestion.fraction)
			} else {
				target
			}
		)
	}

	ModalBottomSheet(onDismissRequest = onDismissRequest) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			BinderyReaderOpenHeader(
				bookTitle = bookTitle,
				coverImageUrl = coverImageUrl,
				row = row,
				ownFraction = ownFraction
			)
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				if (matchDestinations.isNotEmpty()) {
					BinderyReaderOpenLine(stringResource(Res.string.label_reader_open_media_type)) {
						BinderyReaderOpenSegments(
							options = listOf(
								stringResource(Res.string.option_reader_open_ebook),
								stringResource(Res.string.option_reader_open_audiobook)
							),
							selectedIndex = if (audiobook) 1 else 0,
							onSelect = { index -> wantsAudiobook = index == 1 }
						)
					}
				}
				if (audiobook && matchDestinations.size > 1) {
					BinderyReaderOpenLine(stringResource(Res.string.label_reader_open_narrator)) {
						BinderyReaderOpenChips(
							labels = matchDestinations.map { (match, _) -> match.oppositeTitle },
							selectedIndex = matchDestinations.indexOf(selectedMatch),
							onSelect = { index -> narratorIndex = index }
						)
					}
				}
				if (bookLanguages.size > 1) {
					BinderyReaderOpenLine(stringResource(Res.string.label_reader_open_book_language)) {
						BinderyReaderOpenChips(
							labels = bookLanguages.map(::binderyLanguageDisplayName),
							selectedIndex = bookLanguages.indexOf(row.language),
							onSelect = { index ->
								row = binderyEditionForLanguage(allEditions, bookLanguages[index], row, progresses)
								narratorIndex = 0
							}
						)
					}
				}
				if (audiobook && audioLanguages.size > 1) {
					BinderyReaderOpenLine(stringResource(Res.string.label_reader_open_audio_language)) {
						BinderyReaderOpenChips(
							labels = audioLanguages.map(::binderyLanguageDisplayName),
							selectedIndex = audioLanguages.indexOf(audioLanguage),
							onSelect = { index ->
								audioLanguage = audioLanguages[index]
								narratorIndex = 0
							}
						)
					}
				}
				importSuggestion?.let { suggestion ->
					BinderyReaderOpenLine(stringResource(Res.string.label_reader_open_start_at)) {
						BinderyReaderOpenSegments(
							options = listOf(
								stringResource(Res.string.label_reader_open_this_edition) + " · " +
									binderyPercentLabel(suggestion.ownFraction ?: 0.0),
								suggestion.sourceTitle + " · " + binderyPercentLabel(suggestion.fraction)
							),
							selectedIndex = if (startFromOtherEdition) 1 else 0,
							onSelect = { index -> startFromOtherEdition = index == 1 }
						)
					}
				}
				BinderyReaderOpenLine(stringResource(Res.string.label_reader_open_cache_type)) {
					Row(
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						BinderyReaderOpenSegments(
							options = listOf(
								stringResource(Res.string.action_reader_cache_this_chapter),
								stringResource(Res.string.action_reader_cache_whole_book)
							),
							selectedIndex = if (cacheScope == ReaderPageCacheScope.FullBook) 1 else 0,
							onSelect = { index ->
								val scope = if (index == 1) ReaderPageCacheScope.FullBook else ReaderPageCacheScope.PerChapter
								cacheScope = scope
								preferenceManager.setReaderPageCacheScopeForBook(bookId, scope)
							}
						)
						BinderyReaderOpenCoverageLabel(coverage = coverage, fullyPrepared = fullyPrepared)
					}
				}
			}
			Button(
				onClick = { destination?.let(::open) },
				enabled = destination != null,
				modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
				contentPadding = ButtonDefaults.ButtonWithIconContentPadding
			) {
				if (audiobook) {
					Icon(
						painter = painterResource(Res.drawable.ic_whispersync),
						contentDescription = null,
						tint = Color.Unspecified,
						modifier = Modifier.size(22.dp)
					)
				} else {
					Icon(Icons.Outlined.Book, contentDescription = null, modifier = Modifier.size(22.dp))
				}
				Text(
					text = stringResource(
						if (audiobook) Res.string.action_reader_open_read_and_listen else Res.string.action_reader_open_read
					),
					style = MaterialTheme.typography.titleMedium,
					modifier = Modifier.padding(start = 10.dp)
				)
			}
		}
	}
}

@Composable
private fun BinderyReaderOpenHeader(
	bookTitle: String,
	coverImageUrl: String?,
	row: BinderyBookVersionRow,
	ownFraction: Double?
) {
	val preferenceManager = koinInject<PreferenceManager>()
	Row(
		horizontalArrangement = Arrangement.spacedBy(18.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		CoverArt(
			coverArtId = null,
			imageUrl = coverImageUrl,
			imageRequestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey),
			contentDescription = bookTitle,
			fallbackKind = "Book",
			modifier = Modifier.width(72.dp).aspectRatio(2f / 3f),
			square = false,
			contentScale = ContentScale.Fit
		)
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(4.dp)
		) {
			Text(
				text = bookTitle,
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.SemiBold,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = listOfNotNull(
					row.title.trim().takeIf { it.isNotEmpty() },
					row.language?.let(::binderyLanguageDisplayName)
				).joinToString(" · "),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Row(
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.padding(top = 6.dp)
			) {
				LinearProgressIndicator(
					progress = { (ownFraction ?: 0.0).toFloat() },
					modifier = Modifier.weight(1f),
					drawStopIndicator = {}
				)
				Text(
					text = ownFraction?.let { fraction ->
						stringResource(Res.string.info_reader_open_read_fraction, binderyPercentLabel(fraction))
					} ?: stringResource(Res.string.info_reader_open_not_started),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
}

/** "Label:  [choice]" on one line. */
@Composable
private fun BinderyReaderOpenLine(
	label: String,
	content: @Composable () -> Unit
) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Text(
			text = "$label:",
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.width(140.dp)
		)
		content()
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinderyReaderOpenSegments(
	options: List<String>,
	selectedIndex: Int,
	onSelect: (Int) -> Unit
) {
	SingleChoiceSegmentedButtonRow {
		options.forEachIndexed { index, label ->
			SegmentedButton(
				shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
				onClick = { onSelect(index) },
				selected = index == selectedIndex,
				label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BinderyReaderOpenChips(
	labels: List<String>,
	selectedIndex: Int,
	onSelect: (Int) -> Unit
) {
	FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		labels.forEachIndexed { index, label ->
			FilterChip(
				selected = index == selectedIndex,
				onClick = { onSelect(index) },
				label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
			)
		}
	}
}

@Composable
private fun BinderyReaderOpenCoverageLabel(
	coverage: ReaderPageCacheCoverage?,
	fullyPrepared: Boolean
) {
	val label = when {
		fullyPrepared -> stringResource(Res.string.info_reader_cache_coverage_ready)
		coverage == null -> null
		coverage.cachedPages == 0 -> stringResource(Res.string.info_reader_cache_coverage_none)
		coverage.totalPages > 0 -> stringResource(
			Res.string.info_reader_cache_coverage,
			coverage.cachedPages.toString(),
			coverage.totalPages.toString(),
			"${coverage.percent}%"
		)
		else -> stringResource(Res.string.info_reader_cache_coverage_unknown, coverage.cachedPages.toString())
	} ?: return
	Text(
		text = label,
		style = MaterialTheme.typography.bodySmall,
		color = if (fullyPrepared) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
	)
}

private fun binderyPercentLabel(fraction: Double): String = "${(fraction * 100.0).roundToInt()}%"
