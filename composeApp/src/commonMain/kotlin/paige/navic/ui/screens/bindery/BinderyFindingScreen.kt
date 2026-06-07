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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_provider_source
import navic.composeapp.generated.resources.title_bindery_files
import navic.composeapp.generated.resources.title_bindery_mapped_books
import navic.composeapp.generated.resources.title_bindery_provider_notes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.queueTotalDurationLabel
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyFindingFile
import paige.navic.domain.repositories.BinderyFindingMapping
import paige.navic.domain.repositories.BinderyFindingMetadata
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.binderyApiKeyHeaders
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Book
import paige.navic.ui.components.common.BinderyIntegrationServices
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
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
fun BinderyFindingScreen(
	path: String,
	title: String
) {
	val viewModel = koinViewModel<BinderyCatalogViewModel>(
		key = "bindery-finding-$path",
		parameters = { parametersOf(path) }
	)
	val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
	val actionError by viewModel.actionError.collectAsStateWithLifecycle()
	val actionInFlight by viewModel.actionInFlight.collectAsStateWithLifecycle()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
	val preferenceManager = koinInject<PreferenceManager>()
	val platformContext = LocalPlatformContext.current
	val backStack = LocalNavStack.current
	val binderyConfigured = shouldLoadBinderyUi(
		binderyEnabled = preferenceManager.binderyEnabled,
		opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
		apiKey = preferenceManager.binderyApiKey
	)
	val binderyIndicators = integrationLoadingIndicators(
		binderyLoading = binderyConfigured && catalogState is UiState.Loading
	)
	val imageRequestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
	val resolvedTitle = catalogState.data?.title?.takeIf { it.isNotBlank() } ?: title

	LaunchedEffect(
		binderyConfigured,
		preferenceManager.binderyOpdsBaseUrl,
		preferenceManager.binderyApiKey,
		preferenceManager.binderyLanguageFilter,
		path
	) {
		if (binderyConfigured) {
			viewModel.refreshCatalog(
				fullRefresh = false,
				languageFilter = normalizedBinderyLanguageFilter(preferenceManager.binderyLanguageFilter),
				queryMode = BinderyAvailabilityQueryMode.Detail
			)
		} else {
			viewModel.clearCatalog()
		}
	}

	Scaffold(
		topBar = {
			RootTopBar(
				title = { Text(resolvedTitle) },
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
				finished = !binderyConfigured || catalogState !is UiState.Loading,
				onRefresh = {
					if (binderyConfigured) {
						viewModel.refreshCatalog(
							fullRefresh = true,
							languageFilter = normalizedBinderyLanguageFilter(preferenceManager.binderyLanguageFilter),
							queryMode = BinderyAvailabilityQueryMode.Detail
						)
					} else {
						viewModel.clearCatalog()
					}
				},
				key = catalogState
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
						!binderyConfigured -> item("bindery-finding-disabled") {
							ContentUnavailable(
								icon = Icons.Outlined.Book,
								label = resolvedTitle
							)
						}
						catalogState.data == null -> item("bindery-finding-placeholder") {
							BinderyFindingHeroPlaceholder(resolvedTitle)
						}
						else -> {
							val catalog = catalogState.data ?: return@LazyColumn
							val finding = catalog.finding
							item("bindery-finding-hero") {
								BinderyFindingHero(
									catalog = catalog,
									finding = finding,
									baseUrl = preferenceManager.binderyOpdsBaseUrl,
									imageRequestHeaders = imageRequestHeaders,
									actionInFlight = actionInFlight,
									onAction = { link ->
										viewModel.performAction(
											link = link,
											languageFilter = normalizedBinderyLanguageFilter(
												preferenceManager.binderyLanguageFilter
											),
											queryMode = BinderyAvailabilityQueryMode.Detail
										)
									}
								)
							}
							finding?.let { metadata ->
								val rows = metadata.infoRows()
								if (rows.isNotEmpty()) {
									item("bindery-finding-metadata") {
										BinderyFindingMetadataGrid(rows)
									}
								}
								if (metadata.mappings.isNotEmpty()) {
									item("bindery-finding-mapped-title") {
										BinderyFindingSectionTitle(stringResource(Res.string.title_bindery_mapped_books))
									}
									itemsIndexed(
										metadata.mappings,
										key = { index, mapping -> binderyFindingMappingRowKey(mapping, index) }
									) { _, mapping ->
										BinderyFindingMappingRow(
											mapping = mapping,
											onOpenBook = { bookId, bookTitle ->
												platformContext.clickSound()
												backStack.add(
													Screen.BinderyBook(
														bookId = binderyBookRouteId(bookId),
														title = bookTitle
													)
												)
											}
										)
									}
								}
								if (metadata.files.isNotEmpty()) {
									item("bindery-finding-files-title") {
										BinderyFindingSectionTitle(stringResource(Res.string.title_bindery_files))
									}
									itemsIndexed(
										metadata.files,
										key = { index, file -> binderyFindingFileRowKey(file, index) }
									) { _, file ->
										BinderyFindingFileRow(file)
									}
								}
								metadata.providerComments?.takeIf { it.isNotBlank() }?.let { notes ->
									item("bindery-finding-notes-title") {
										BinderyFindingSectionTitle(stringResource(Res.string.title_bindery_provider_notes))
									}
									item("bindery-finding-notes") {
										Text(
											text = notes,
											style = MaterialTheme.typography.bodyMedium,
											color = MaterialTheme.colorScheme.onSurfaceVariant
										)
									}
								}
							}
						}
					}
				}
			}
			IntegrationLoadingIndicatorStrip(
				indicators = binderyIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = binderyIndicators,
					relevantServices = BinderyIntegrationServices
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = innerPadding.calculateTopPadding() + 8.dp)
			)
		}
	}

	ErrorSnackbar(
		error = (catalogState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)
	ErrorSnackbar(
		error = actionError,
		onClearError = viewModel::clearActionError
	)
}

@Composable
private fun BinderyFindingHeroPlaceholder(title: String) {
	BinderyFindingHero(
		catalog = BinderyCatalog(title = title),
		finding = null,
		baseUrl = "",
		imageRequestHeaders = emptyMap(),
		actionInFlight = emptySet(),
		onAction = {}
	)
}

@Composable
private fun BinderyFindingHero(
	catalog: BinderyCatalog,
	finding: BinderyFindingMetadata?,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	actionInFlight: Set<String>,
	onAction: (BinderyLink) -> Unit
) {
	val uriHandler = LocalUriHandler.current
	val imageHref = catalog.images.firstOrNull()?.href ?: finding?.coverUrl
	val action = catalog.primaryAction()
	val sourceUrl = catalog.links.firstOrNull { link ->
		link.rel.any { rel -> rel.equals("alternate", ignoreCase = true) }
	}?.href ?: finding?.sourceUrl

	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.Top
	) {
		CoverArt(
			coverArtId = null,
			imageUrl = imageHref?.let { binderyEndpoint(baseUrl, it) },
			imageRequestHeaders = imageRequestHeaders,
			contentDescription = catalog.title,
			fallbackKind = "Finding",
			modifier = Modifier.width(132.dp).aspectRatio(2f / 3f),
			square = false,
			contentScale = ContentScale.Fit
		)
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = catalog.title,
				style = MaterialTheme.typography.headlineSmall,
				maxLines = 4,
				overflow = TextOverflow.Ellipsis
			)
			finding?.displaySubtitle()?.let { subtitle ->
				Text(
					text = subtitle,
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.primary,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis
				)
			}
			if (action != null) {
				BinderyActionButton(
					action = action,
					loading = action.link.href in actionInFlight,
					onAction = onAction
				)
			}
			sourceUrl?.takeIf { it.isNotBlank() }?.let { url ->
				TextButton(
					onClick = { uriHandler.openUri(url) },
					contentPadding = PaddingValues(horizontal = 0.dp)
				) {
					Text(stringResource(Res.string.action_provider_source))
				}
			}
		}
	}
}

@Composable
private fun BinderyFindingMetadataGrid(rows: List<Pair<String, String>>) {
	FlowRow(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		rows.forEach { (label, value) ->
			Surface(
				shape = RoundedCornerShape(6.dp),
				color = MaterialTheme.colorScheme.surfaceContainerHighest
			) {
				Column(
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
					verticalArrangement = Arrangement.spacedBy(2.dp)
				) {
					Text(
						text = label,
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Text(
						text = value,
						style = MaterialTheme.typography.labelLarge,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
		}
	}
}

@Composable
private fun BinderyFindingSectionTitle(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.titleMediumEmphasized,
		fontWeight = FontWeight(600),
		modifier = Modifier.fillMaxWidth()
	)
}

@Composable
private fun BinderyFindingMappingRow(
	mapping: BinderyFindingMapping,
	onOpenBook: (String, String) -> Unit
) {
	val bookId = mapping.bookId
	val title = mapping.bookTitle ?: bookId ?: "Book"
	Surface(
		onClick = {
			if (!bookId.isNullOrBlank()) {
				onOpenBook(bookId, title)
			}
		},
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(8.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHighest
	) {
		Column(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp)
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleSmall,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			listOfNotNull(
				mapping.authorName,
				mapping.mediaType?.displayToken(),
				mapping.targetLanguage?.uppercase(),
				mapping.acquisitionStatus?.displayToken(),
				mapping.confidence?.roundToLong()?.let { "$it%" }
			).joinToString(separator = " / ")
				.takeIf { it.isNotBlank() }
				?.let { subtitle ->
					Text(
						text = subtitle,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
		}
	}
}

@Composable
private fun BinderyFindingFileRow(file: BinderyFindingFile) {
	Surface(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(8.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHighest
	) {
		Column(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp)
		) {
			Text(
				text = file.name ?: file.href ?: "File",
				style = MaterialTheme.typography.titleSmall,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			listOfNotNull(
				file.language?.uppercase(),
				file.format?.uppercase(),
				file.durationSeconds?.roundToLong()?.let(::queueTotalDurationLabel),
				file.sizeBytes?.toFileSize(),
				file.bitrateBps?.let(::bitrateLabel),
				file.sampleRateHz?.let(::sampleRateLabel)
			).joinToString(separator = " / ")
				.takeIf { it.isNotBlank() }
				?.let { subtitle ->
					Text(
						text = subtitle,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
		}
	}
}

private fun BinderyFindingMetadata.infoRows(): List<Pair<String, String>> =
	listOfNotNull(
		"Media" to mediaType?.displayToken(),
		"Language" to language?.uppercase(),
		"Format" to format?.uppercase(),
		"Provider" to provider,
		"Source" to providerKind?.displayToken(),
		"Author" to author,
		"Publisher" to publisher,
		"Edition" to edition,
		"Narrator" to narrator,
		"Published" to publishedDate,
		"Availability" to availabilityStatus?.displayToken(),
		"Reason" to availabilityReason?.displayToken(),
		"Protocol" to protocol?.displayToken(),
		fileCount?.let { "Files" to it.toString() },
		sizeBytes?.let { "Size" to it.toFileSize() },
		bitrateBps?.let { "Bitrate" to bitrateLabel(it) },
		sampleRateHz?.let { "Sample rate" to sampleRateLabel(it) }
	).mapNotNull { (label, value) ->
		value?.takeIf { it.isNotBlank() }?.let { label to it }
	}

private fun bitrateLabel(value: Long): String =
	"${(value / 1000).coerceAtLeast(1)} kbps"

private fun sampleRateLabel(value: Long): String =
	if (value >= 1000) {
		"${value / 1000.0} kHz"
	} else {
		"$value Hz"
	}

private fun String.displayToken(): String =
	trim()
		.replace('-', ' ')
		.replace('_', ' ')
		.split(Regex("\\s+"))
		.filter { it.isNotEmpty() }
		.joinToString(separator = " ") { token ->
			token.replaceFirstChar { char ->
				if (char.isLowerCase()) char.titlecase() else char.toString()
			}
		}
