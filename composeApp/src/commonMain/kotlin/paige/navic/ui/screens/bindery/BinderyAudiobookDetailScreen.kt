package paige.navic.ui.screens.bindery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_play
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyAudiobookVersion
import paige.navic.domain.repositories.binderyApiKeyHeaders
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.domain.repositories.binderyRequestHeadersForUrl
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.Audiobooks
import paige.navic.icons.outlined.Book
import paige.navic.icons.outlined.Link
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.toFileSize
import kotlin.math.roundToLong

@OptIn(
	ExperimentalMaterial3Api::class,
	ExperimentalMaterial3ExpressiveApi::class,
	ExperimentalLayoutApi::class
)
@Composable
fun BinderyAudiobookDetailScreen(
	audiobookId: String,
	title: String
) {
	val viewModel = koinViewModel<BinderyAudiobookDetailViewModel>(
		key = "bindery-audiobook-detail-$audiobookId",
		parameters = { parametersOf(audiobookId) }
	)
	val detailState by viewModel.detailState.collectAsStateWithLifecycle()
	val detail = detailState.data
	val preferenceManager = koinInject<PreferenceManager>()
	val binderyConfigured = shouldLoadBinderyUi(
		binderyEnabled = preferenceManager.binderyEnabled,
		opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
		apiKey = preferenceManager.binderyApiKey
	)
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val uriHandler = LocalUriHandler.current
	val displayTitle = detail?.displayTitle() ?: title
	val imageUrl = detail?.coverUrl?.let { binderyEndpoint(preferenceManager.binderyOpdsBaseUrl, it) }
	val binderyHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
	val imageRequestHeaders = binderyRequestHeadersForUrl(
		baseUrl = preferenceManager.binderyOpdsBaseUrl,
		url = imageUrl,
		requestHeaders = binderyHeaders
	)

	LaunchedEffect(binderyConfigured, audiobookId) {
		if (binderyConfigured) {
			viewModel.refresh(fullRefresh = false)
		}
	}

	Scaffold(
		topBar = {
			RootTopBar(
				title = { Text(displayTitle) },
				scrollBehavior = scrollBehavior
			)
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			RootBottomBar(scrolled = scrollManager.isTriggered)
		}
	) { innerPadding ->
		Box(Modifier.fillMaxSize()) {
			PullToRefreshBox(
				modifier = Modifier
					.padding(top = innerPadding.calculateTopPadding())
					.background(MaterialTheme.colorScheme.surface),
				finished = !binderyConfigured || detailState !is UiState.Loading,
				onRefresh = { if (binderyConfigured) viewModel.refresh(fullRefresh = true) },
				key = detailState
			) {
				LazyColumn(
					modifier = Modifier
						.fillMaxSize()
						.nestedScroll(scrollBehavior.nestedScrollConnection),
					contentPadding = PaddingValues(
						start = 16.dp,
						top = 16.dp,
						end = 16.dp,
						bottom = innerPadding.calculateBottomPadding() + 24.dp
					),
					verticalArrangement = Arrangement.spacedBy(16.dp)
				) {
					when {
						!binderyConfigured -> item("bindery-audiobook-disabled") {
							ContentUnavailable(
								icon = Icons.Outlined.Audiobooks,
								label = displayTitle
							)
						}
						detail == null -> item("bindery-audiobook-loading") {
							ContentUnavailable(
								icon = Icons.Outlined.Audiobooks,
								label = displayTitle
							)
						}
						else -> {
							item("bindery-audiobook-hero") {
								BinderyAudiobookDetailHero(
									detail = detail,
									imageUrl = imageUrl,
									imageRequestHeaders = imageRequestHeaders,
									onPlay = {
										val bookId = detail.bookId?.toString() ?: return@BinderyAudiobookDetailHero
										platformContext.clickSound()
										backStack.add(
											Screen.BinderyAudiobookPlayer(
												bookId = bookId,
												title = detail.displayTitle(),
												audiobookId = audiobookId
											)
										)
									},
									onOpenBook = {
										val bookId = detail.bookId?.toString() ?: return@BinderyAudiobookDetailHero
										platformContext.clickSound()
										backStack.add(Screen.BinderyBook(bookId, detail.displayTitle()))
									},
									onOpenAudible = {
										detail.audibleSourceUrl?.trim()?.takeIf { it.isNotEmpty() }?.let(uriHandler::openUri)
									}
								)
							}
						}
					}
				}
			}
		}
	}

	ErrorSnackbar(
		error = (detailState as? UiState.Error)?.error,
		onClearError = viewModel::clearError
	)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BinderyAudiobookDetailHero(
	detail: BinderyAudiobookVersion,
	imageUrl: String?,
	imageRequestHeaders: Map<String, String>,
	onPlay: () -> Unit,
	onOpenBook: () -> Unit,
	onOpenAudible: () -> Unit
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(16.dp),
			verticalAlignment = Alignment.Top
		) {
			CoverArt(
				coverArtId = null,
				imageUrl = imageUrl,
				imageRequestHeaders = imageRequestHeaders,
				contentDescription = detail.displayTitle(),
				fallbackKind = "Audiobook",
				modifier = Modifier.width(142.dp).aspectRatio(1f),
				square = false,
				contentScale = ContentScale.Crop,
				shadowElevation = 6.dp,
				shape = RoundedCornerShape(8.dp)
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					BinderyDetailChip("Audiobook version")
					if (!detail.audibleSourceUrl.isNullOrBlank()) {
						BinderyDetailChip("Audible")
					}
				}
				Text(
					text = detail.displayTitle(),
					style = MaterialTheme.typography.headlineSmall,
					fontWeight = FontWeight.SemiBold,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis
				)
				detail.displayAuthor()?.let { author ->
					Text(
						text = author,
						style = MaterialTheme.typography.labelLarge,
						color = MaterialTheme.colorScheme.primary,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis
					)
				}
				detail.narrator?.trim()?.takeIf { it.isNotEmpty() }?.let { narrator ->
					Text(
						text = "Narrated by $narrator",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 4,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
		}
		BinderyAudiobookMetadataGrid(detail)
		if (detail.categories.isNotEmpty()) {
			FlowRow(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				detail.categories.take(12).forEach { category ->
					BinderyDetailChip(category)
				}
			}
		}
		detail.description?.plainDescription()?.takeIf { it.isNotEmpty() }?.let { description ->
			Text(
				text = description,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
				lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
			)
		}
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			detail.qualityChips().forEach { chip -> BinderyDetailChip(chip) }
		}
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			Button(onClick = onPlay) {
				Icon(Icons.Filled.Play, contentDescription = null, modifier = Modifier.size(18.dp))
				Text(stringResource(Res.string.action_play), modifier = Modifier.padding(start = 8.dp))
			}
			OutlinedButton(onClick = onOpenBook) {
				Icon(Icons.Outlined.Book, contentDescription = null, modifier = Modifier.size(18.dp))
				Text("Book", modifier = Modifier.padding(start = 8.dp))
			}
			if (!detail.audibleSourceUrl.isNullOrBlank()) {
				OutlinedButton(onClick = onOpenAudible) {
					Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
					Text("Audible", modifier = Modifier.padding(start = 8.dp))
				}
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BinderyAudiobookMetadataGrid(detail: BinderyAudiobookVersion) {
	val rows = listOfNotNull(
		detail.seriesLabel()?.let { "Series" to it },
		detail.releaseDate?.trim()?.takeIf { it.isNotEmpty() }?.let { "Release date" to it },
		detail.language?.trim()?.takeIf { it.isNotEmpty() }?.let { "Language" to it },
		detail.formatDisplay()?.let { "Format" to it },
		detail.durationMs?.takeIf { it > 0L }?.let { "Length" to audiobookVersionDurationLabel(it) },
		detail.audibleAsin?.trim()?.takeIf { it.isNotEmpty() }?.let { "Audible ASIN" to it },
		detail.publisher?.trim()?.takeIf { it.isNotEmpty() }?.let { "Publisher" to it },
		detail.studio?.trim()?.takeIf { it.isNotEmpty() }?.let { "Studio" to it },
		detail.copyright?.trim()?.takeIf { it.isNotEmpty() }?.let { "Copyright" to it }
	)
	if (rows.isEmpty()) return

	FlowRow(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		rows.forEach { (label, value) ->
			Surface(
				modifier = Modifier.fillMaxWidth(.48f),
				shape = RoundedCornerShape(6.dp),
				color = MaterialTheme.colorScheme.surfaceContainerHighest
			) {
				Column(
					modifier = Modifier.padding(12.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp)
				) {
					Text(
						text = label.uppercase(),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Text(
						text = value,
						style = MaterialTheme.typography.bodyMedium,
						maxLines = 3,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
		}
	}
}

@Composable
private fun BinderyDetailChip(label: String) {
	Surface(
		shape = RoundedCornerShape(5.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHighest
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelSmall,
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
		)
	}
}

private fun BinderyAudiobookVersion.displayTitle(): String =
	audibleTitle?.trim()?.takeIf { it.isNotEmpty() }
		?: title?.trim()?.takeIf { it.isNotEmpty() }
		?: "Audiobook"

private fun BinderyAudiobookVersion.displayAuthor(): String? =
	audibleAuthor?.trim()?.takeIf { it.isNotEmpty() }

private fun BinderyAudiobookVersion.seriesLabel(): String? =
	listOfNotNull(
		seriesTitle?.trim()?.takeIf { it.isNotEmpty() },
		seriesPosition?.trim()?.takeIf { it.isNotEmpty() }?.let { "Book $it" }
	).joinToString(separator = ", ").takeIf { it.isNotBlank() }

private fun BinderyAudiobookVersion.formatDisplay(): String? =
	formatLabel?.trim()?.takeIf { it.isNotEmpty() }
		?: editionType?.displayToken()

private fun BinderyAudiobookVersion.qualityChips(): List<String> = listOfNotNull(
	sizeBytes?.takeIf { it > 0L }?.toFileSize(),
	codec?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
	bitrateBps?.takeIf { it > 0L }?.let { "${(it / 1000L).coerceAtLeast(1L)} kbps" },
	sampleRateHz?.takeIf { it > 0L }?.let { sampleRateLabel(it) },
	channels?.takeIf { it > 0 }?.let { channels -> if (channels == 1) "1 ch" else "$channels ch" },
	resourceCount?.takeIf { it > 0 }?.let { count -> if (count == 1) "1 file" else "$count files" },
	durationMs?.takeIf { it > 0L }?.let(::audiobookVersionDurationLabel)
)

private fun audiobookVersionDurationLabel(durationMs: Long): String {
	val totalSeconds = (durationMs / 1000.0).roundToLong().coerceAtLeast(0L)
	val hours = totalSeconds / 3600L
	val minutes = (totalSeconds % 3600L) / 60L
	val seconds = totalSeconds % 60L
	return buildString {
		if (hours > 0L) append("${hours}h")
		if (minutes > 0L || hours > 0L) {
			if (isNotEmpty()) append(' ')
			append("${minutes}m")
		}
		if (hours == 0L && minutes == 0L) append("${seconds}s")
	}
}

private fun sampleRateLabel(sampleRateHz: Long): String {
	val khz = sampleRateHz.toDouble() / 1000.0
	return if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else "${((khz * 10).roundToLong() / 10.0)} kHz"
}

private fun String.plainDescription(): String =
	replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
		.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
		.replace(Regex("<[^>]+>"), "")
		.replace("&amp;", "&")
		.replace("&quot;", "\"")
		.replace("&#39;", "'")
		.replace(Regex("[ \t]+"), " ")
		.replace(Regex("\\n{3,}"), "\n\n")
		.trim()

private fun String.displayToken(): String =
	trim()
		.replace(Regex("[_-]+"), " ")
		.replace(Regex("\\s+"), " ")
		.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
