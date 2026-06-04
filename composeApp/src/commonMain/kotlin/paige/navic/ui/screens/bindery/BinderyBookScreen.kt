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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_less
import navic.composeapp.generated.resources.action_more
import navic.composeapp.generated.resources.info_bindery_no_versions
import navic.composeapp.generated.resources.info_bindery_version_available
import navic.composeapp.generated.resources.title_audiobook_available_versions
import navic.composeapp.generated.resources.title_audiobook_subjects
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.queueTotalDurationLabel
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.binderyApiKeyHeaders
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Audiobooks
import paige.navic.icons.outlined.Book
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
import kotlin.math.roundToLong

@OptIn(
	ExperimentalMaterial3Api::class,
	ExperimentalMaterial3ExpressiveApi::class,
	ExperimentalLayoutApi::class
)
@Composable
fun BinderyBookScreen(
	bookId: String,
	title: String
) {
	val viewModel = koinViewModel<BinderyBookViewModel>(
		key = "bindery-book-$bookId",
		parameters = { parametersOf(bookId) }
	)
	val bookState by viewModel.bookState.collectAsStateWithLifecycle()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
	val preferenceManager = koinInject<PreferenceManager>()
	val binderyConfigured = shouldLoadBinderyUi(
		binderyEnabled = preferenceManager.binderyEnabled,
		opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
		apiKey = preferenceManager.binderyApiKey
	)
	val binderyIndicators = integrationLoadingIndicators(
		binderyLoading = binderyConfigured && bookState is UiState.Loading
	)
	val imageRequestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
	val data = bookState.data
	val titleText = data?.manifest?.title?.takeIf { it.isNotBlank() } ?: title
	val languageFilter = normalizedBinderyLanguageFilter(preferenceManager.binderyLanguageFilter)

	LaunchedEffect(
		binderyConfigured,
		preferenceManager.binderyOpdsBaseUrl,
		preferenceManager.binderyApiKey,
		bookId
	) {
		if (binderyConfigured) {
			viewModel.refreshBook(false)
		} else {
			viewModel.clearBook()
		}
	}

	Scaffold(
		topBar = {
			RootTopBar(
				title = { Text(titleText) },
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
				finished = !binderyConfigured || bookState !is UiState.Loading,
				onRefresh = {
					if (binderyConfigured) {
						viewModel.refreshBook(true)
					} else {
						viewModel.clearBook()
					}
				},
				key = bookState
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
						!binderyConfigured -> item("bindery-book-disabled") {
							ContentUnavailable(
								icon = Icons.Outlined.Book,
								label = titleText
							)
						}
						data == null -> item("bindery-book-loading-placeholder") {
							BinderyBookLoadingPlaceholder(titleText)
						}
						else -> {
							item("bindery-book-hero") {
								BinderyBookHero(
									manifest = data.manifest,
									baseUrl = preferenceManager.binderyOpdsBaseUrl,
									imageRequestHeaders = imageRequestHeaders
								)
							}
							if (data.manifest.subjects.isNotEmpty()) {
								item("bindery-book-subjects") {
									BinderyBookSubjectSection(data.manifest.subjects)
								}
							}
							item("bindery-book-versions-title") {
								BinderyBookSectionTitle(stringResource(Res.string.title_audiobook_available_versions))
							}
							val versionRows = binderyBookVersionRows(data.manifest, data.resources, languageFilter)
							if (versionRows.isEmpty()) {
								item("bindery-book-no-versions") {
									ContentUnavailable(
										icon = Icons.Outlined.Audiobooks,
										label = stringResource(Res.string.info_bindery_no_versions)
									)
								}
							} else {
								items(versionRows, key = { row -> row.id }) { row ->
									BinderyBookVersionListItem(row)
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
					loadingIndicators = binderyIndicators
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = innerPadding.calculateTopPadding() + 8.dp)
			)
		}
	}

	ErrorSnackbar(
		error = (bookState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)
}

@Composable
private fun BinderyBookLoadingPlaceholder(title: String) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.Top
	) {
		CoverArt(
			coverArtId = null,
			imageUrl = null,
			contentDescription = title,
			fallbackKind = "Book",
			modifier = Modifier.width(132.dp).aspectRatio(2f / 3f),
			square = false,
			contentScale = ContentScale.Fit
		)
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.headlineSmall,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

@Composable
private fun BinderyBookHero(
	manifest: BinderyManifest,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>
) {
	var expanded by rememberSaveable(manifest.id, manifest.title) { mutableStateOf(false) }
	val imageHref = manifest.images.firstOrNull()?.href
	val metadataText = manifest.metadataText()
	val description = manifest.description?.trim()?.takeIf { it.isNotEmpty() }
	var descriptionHasOverflow by rememberSaveable(manifest.id, manifest.title, description) {
		mutableStateOf(false)
	}

	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.Top
	) {
		CoverArt(
			coverArtId = null,
			imageUrl = imageHref?.let { binderyEndpoint(baseUrl, it) },
			imageRequestHeaders = imageRequestHeaders,
			contentDescription = manifest.title,
			fallbackKind = "Book",
			modifier = Modifier.width(132.dp).aspectRatio(2f / 3f),
			square = false,
			contentScale = ContentScale.Fit
		)
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = manifest.title,
				style = MaterialTheme.typography.headlineSmall,
				maxLines = 4,
				overflow = TextOverflow.Ellipsis
			)
			metadataText?.let {
				Text(
					text = it,
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.primary,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis
				)
			}
			description?.let {
				Text(
					text = it,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = if (expanded) Int.MAX_VALUE else 8,
					overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
					onTextLayout = { result ->
						if (!expanded) {
							descriptionHasOverflow = result.hasVisualOverflow
						}
					}
				)
				if (descriptionHasOverflow || expanded) {
					TextButton(
						onClick = { expanded = !expanded },
						modifier = Modifier.padding(top = 0.dp)
					) {
						Text(stringResource(if (expanded) Res.string.action_less else Res.string.action_more))
					}
				}
			}
		}
	}
}

@Composable
private fun BinderyBookSubjectSection(subjects: List<String>) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		BinderyBookSectionTitle(stringResource(Res.string.title_audiobook_subjects))
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			subjects.take(12).forEach { subject ->
				Surface(
					shape = RoundedCornerShape(percent = 50),
					color = MaterialTheme.colorScheme.surfaceContainerHighest
				) {
					Text(
						text = subject,
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
					)
				}
			}
		}
	}
}

@Composable
private fun BinderyBookSectionTitle(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.titleMediumEmphasized,
		fontWeight = FontWeight(600),
		modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)
	)
}

@Composable
private fun BinderyBookVersionListItem(row: BinderyBookVersionRow) {
	Surface(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(8.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHighest
	) {
		Row(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				imageVector = row.kind.icon(),
				contentDescription = null,
				modifier = Modifier.size(24.dp),
				tint = MaterialTheme.colorScheme.primary
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp)
			) {
				Text(
					text = row.title,
					style = MaterialTheme.typography.titleSmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				row.subtitle?.let {
					Text(
						text = it,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
			Text(
				text = stringResource(Res.string.info_bindery_version_available),
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.primary
			)
		}
	}
}

private fun BinderyBookVersionKind.icon(): ImageVector =
	when (this) {
		BinderyBookVersionKind.Audiobook -> Icons.Outlined.Audiobooks
		BinderyBookVersionKind.Ebook -> Icons.Outlined.Book
	}

private fun BinderyManifest.metadataText(): String? =
	listOfNotNull(
		author,
		published?.take(4)?.takeIf { year -> year.length == 4 && year.all(Char::isDigit) },
		durationSeconds?.roundToLong()?.let(::queueTotalDurationLabel)
	).joinToString(separator = " / ").takeIf { it.isNotBlank() }
