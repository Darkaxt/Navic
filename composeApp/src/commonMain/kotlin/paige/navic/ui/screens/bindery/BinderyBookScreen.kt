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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_less
import navic.composeapp.generated.resources.action_more
import navic.composeapp.generated.resources.action_play
import navic.composeapp.generated.resources.ic_whispersync
import navic.composeapp.generated.resources.info_bindery_no_versions
import navic.composeapp.generated.resources.title_audiobook_ebooks
import navic.composeapp.generated.resources.title_audiobooks
import navic.composeapp.generated.resources.title_audiobook_subjects
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.queueTotalDurationLabel
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.binderyApiKeyHeaders
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.Audiobooks
import paige.navic.icons.outlined.Book
import paige.navic.icons.outlined.Info
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.BinderyIntegrationServices
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.components.sheets.ModalBottomSheet
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.Logger
import kotlin.math.roundToLong

private const val BinderyBookScreenTag = "BinderyBookScreen"

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
	val actionInFlight by viewModel.actionInFlight.collectAsStateWithLifecycle()
	val actionError by viewModel.actionError.collectAsStateWithLifecycle()
	val titleText = data?.manifest?.title?.takeIf { it.isNotBlank() } ?: title
	val languageFilter = normalizedBinderyLanguageFilter(preferenceManager.binderyLanguageFilter)
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	var syncSheetRow by remember { mutableStateOf<BinderyBookVersionRow?>(null) }
	val versionRows = data?.let { bookData ->
		binderyBookVersionRows(
			manifest = bookData.manifest,
			resourceCatalog = bookData.resources,
			languageFilter = languageFilter,
			bookId = bookId,
			audiobookVersions = bookData.audiobooks,
			bookSync = bookData.sync
		)
	}.orEmpty()
	val versionGroups = binderyBookVersionGroups(versionRows)

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

	LaunchedEffect(
		data?.manifest?.id,
		data?.manifest?.readingOrder?.size,
		data?.resources?.resources?.size,
		data?.audiobooks?.size,
		data?.sync?.syncPairs?.size,
		versionRows.size
	) {
		val bookData = data ?: return@LaunchedEffect
		Logger.i(
			BinderyBookScreenTag,
				"Bindery book loaded bookId=$bookId " +
				"readingOrder=${bookData.manifest.readingOrder.size} " +
				"resources=${bookData.resources.resources.size} " +
				"audiobooks=${bookData.audiobooks.size} " +
				"syncPairs=${bookData.sync.syncPairs.size} " +
				"versions=${versionRows.size} " +
				"audiobookRows=${versionGroups.audiobooks.size} " +
				"ebookRows=${versionGroups.ebooks.size}"
		)
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
									BinderyBookSubjectSection(
										subjects = data.manifest.subjects,
										onSubjectClick = { subject ->
											binderySubjectSearchDestination(subject)?.let { destination ->
												platformContext.clickSound()
												backStack.add(destination)
											}
										}
									)
								}
							}
							if (versionGroups.isEmpty) {
								item("bindery-book-no-findings") {
									ContentUnavailable(
										icon = Icons.Outlined.Audiobooks,
										label = stringResource(Res.string.info_bindery_no_versions)
									)
								}
							}
							if (versionGroups.audiobooks.isNotEmpty()) {
								item("bindery-book-audiobooks-title") {
									BinderyBookSectionTitle(stringResource(Res.string.title_audiobooks))
								}
								itemsIndexed(
									versionGroups.audiobooks,
									key = { index, row -> binderyBookVersionRowLazyKey(row, index) }
								) { _, row ->
									BinderyBookVersionListItem(
										row = row,
										bookId = bookId,
										bookTitle = data.manifest.title,
										opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
										readaloudMediaOverlayEnabled = preferenceManager.readerMediaOverlayEnabled,
										onOpenReader = { destination ->
											platformContext.clickSound()
											backStack.add(destination)
										},
										onOpenAudiobook = { destination ->
											platformContext.clickSound()
											backStack.add(destination)
										},
										onOpenAudiobookDetail = { destination ->
											platformContext.clickSound()
											backStack.add(destination)
										},
										onOpenWhispersync = { row ->
											platformContext.clickSound()
											syncSheetRow = row
										}
									)
								}
							}
							if (versionGroups.ebooks.isNotEmpty()) {
								item("bindery-book-ebooks-title") {
									BinderyBookSectionTitle(stringResource(Res.string.title_audiobook_ebooks))
								}
								itemsIndexed(
									versionGroups.ebooks,
									key = { index, row -> binderyBookVersionRowLazyKey(row, index) }
								) { _, row ->
									BinderyBookVersionListItem(
										row = row,
										bookId = bookId,
										bookTitle = data.manifest.title,
										opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
										readaloudMediaOverlayEnabled = preferenceManager.readerMediaOverlayEnabled,
										onOpenReader = { destination ->
											platformContext.clickSound()
											backStack.add(destination)
										},
										onOpenAudiobook = { destination ->
											platformContext.clickSound()
											backStack.add(destination)
										},
										onOpenAudiobookDetail = { destination ->
											platformContext.clickSound()
											backStack.add(destination)
										},
										onOpenWhispersync = { row ->
											platformContext.clickSound()
											syncSheetRow = row
										}
									)
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
		error = actionError ?: (bookState as? UiState.Error)?.error,
		onClearError = {
			viewModel.clearActionError()
			viewModel.clearError()
		}
	)

	syncSheetRow?.let { row ->
		BinderyWhispersyncMatchesSheet(
			row = row,
			onDismissRequest = { syncSheetRow = null },
			onOpenSidecar = {
				// The timing layer is not wired yet; this keeps the pairing UI discoverable without starting partial playback.
			}
		)
	}
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

	Box(
		modifier = Modifier.fillMaxWidth(),
	) {
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
}

@Composable
private fun BinderyBookSubjectSection(
	subjects: List<String>,
	onSubjectClick: (String) -> Unit
) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		BinderyBookSectionTitle(stringResource(Res.string.title_audiobook_subjects))
		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			subjects.take(12).forEach { subject ->
				Surface(
					onClick = { onSubjectClick(subject) },
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
private fun BinderyBookVersionListItem(
	row: BinderyBookVersionRow,
	bookId: String,
	bookTitle: String,
	opdsBaseUrl: String,
	readaloudMediaOverlayEnabled: Boolean,
	onOpenReader: (Screen.Reader) -> Unit,
	onOpenAudiobook: (Screen.BinderyAudiobookPlayer) -> Unit,
	onOpenAudiobookDetail: (Screen.BinderyAudiobookDetail) -> Unit,
	onOpenWhispersync: (BinderyBookVersionRow) -> Unit
) {
	val readerDestination = binderyReaderDestinationForVersionRow(
		row = row,
		bookId = bookId,
		bookTitle = bookTitle,
		opdsBaseUrl = opdsBaseUrl,
		readaloudMediaOverlayEnabled = readaloudMediaOverlayEnabled
	)
	val audiobookDestination = if (row.routingAction() == BinderyBookVersionRoutingAction.OpenAudiobook) {
		row.audiobookId?.let { audiobookId ->
			Screen.BinderyAudiobookPlayer(
				bookId = bookId,
				title = bookTitle,
				audiobookId = audiobookId
			)
		}
	} else {
		null
	}
	val audiobookDetailDestination = row.audiobookId?.let { audiobookId ->
		Screen.BinderyAudiobookDetail(
			audiobookId = audiobookId,
			title = row.title
		)
	}
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
				imageVector = when (row.kind) {
					BinderyBookVersionKind.Audiobook -> Icons.Outlined.Audiobooks
					BinderyBookVersionKind.Readaloud,
					BinderyBookVersionKind.Ebook -> Icons.Outlined.Book
				},
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(24.dp)
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp)
			) {
				Row(
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = row.title,
						style = MaterialTheme.typography.titleSmall,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false)
					)
					if (row.syncMatches.isNotEmpty()) {
						IconButton(
							onClick = { onOpenWhispersync(row) },
							modifier = Modifier.size(32.dp)
						) {
							Icon(
								painter = painterResource(Res.drawable.ic_whispersync),
								contentDescription = "Whispersync matches",
								tint = Color.Unspecified,
								modifier = Modifier.size(26.dp)
							)
						}
					}
				}
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
			audiobookDetailDestination?.let { destination ->
				IconButton(onClick = { onOpenAudiobookDetail(destination) }) {
					Icon(
						imageVector = Icons.Outlined.Info,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
			IconButton(
				onClick = {
					when {
						audiobookDestination != null -> onOpenAudiobook(audiobookDestination)
						readerDestination != null -> onOpenReader(readerDestination)
					}
				},
				enabled = audiobookDestination != null || readerDestination != null
			) {
				Icon(
					imageVector = Icons.Filled.Play,
					contentDescription = stringResource(Res.string.action_play)
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinderyWhispersyncMatchesSheet(
	row: BinderyBookVersionRow,
	onDismissRequest: () -> Unit,
	onOpenSidecar: (BinderyWhispersyncMatch) -> Unit
) {
	ModalBottomSheet(onDismissRequest = onDismissRequest) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text(
				text = "Whispersync matches",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold
			)
			Text(
				text = row.title,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			row.syncMatches.forEach { match ->
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
									match.coveragePercent?.let { "Coverage $it%" },
									match.scorePercent?.let { "Score $it%" }
								).joinToString("  "),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						OutlinedButton(onClick = { onOpenSidecar(match) }) {
							Text("Sidecar")
						}
					}
				}
			}
			Button(
				onClick = onDismissRequest,
				modifier = Modifier.align(Alignment.End)
			) {
				Text("Done")
			}
		}
	}
}

@Composable
private fun BinderyBookFindingCandidateListItem(
	row: BinderyBookFindingRow,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	onOpenFinding: (BinderyCatalogCard.Finding) -> Unit,
	actionInFlight: Set<String>,
	onReaderAction: (BinderyLink) -> Unit,
	languageFilter: String?
) {
	val card = row.card
	val visualPolicy = binderyCatalogCardVisualPolicy(card)
	val action = row.readerOpdsAction
	Surface(
		onClick = { onOpenFinding(card) },
		modifier = Modifier
			.fillMaxWidth()
			.alpha(card.availabilityAlpha(languageFilter)),
		shape = RoundedCornerShape(8.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHighest
	) {
		Row(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			CoverArt(
				coverArtId = null,
				imageUrl = card.imageUrl?.let { binderyEndpoint(baseUrl, it) },
				imageRequestHeaders = imageRequestHeaders,
				contentDescription = row.title,
				fallbackKind = if (row.kind == BinderyBookFindingKind.Audiobook) "Audiobook" else "Book",
				modifier = Modifier.width(42.dp).aspectRatio(visualPolicy.coverAspectRatio),
				square = false,
				contentScale = if (visualPolicy.imageContentScaleFit) ContentScale.Fit else ContentScale.Crop
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp)
			) {
				Text(
					text = row.title,
					style = MaterialTheme.typography.titleSmall,
					maxLines = 2,
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
			IconButton(onClick = { onOpenFinding(card) }) {
				Icon(
					imageVector = Icons.Outlined.Info,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			IconButton(
				onClick = {
					action?.link?.let(onReaderAction)
				},
				enabled = action != null && action.link.href !in actionInFlight
			) {
				if (action != null && action.link.href in actionInFlight) {
					androidx.compose.material3.CircularProgressIndicator(
						modifier = Modifier.size(20.dp),
						strokeWidth = 2.dp
					)
				} else {
					Icon(
						imageVector = Icons.Filled.Play,
						contentDescription = stringResource(
							when (row.readerAction) {
								BinderyBookFindingRowAction.Play -> Res.string.action_play
							}
						)
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

private fun BinderyManifest.metadataText(): String? =
	listOfNotNull(
		author,
		published?.take(4)?.takeIf { year -> year.length == 4 && year.all(Char::isDigit) },
		durationSeconds?.roundToLong()?.let(::queueTotalDurationLabel)
	).joinToString(separator = " / ").takeIf { it.isNotBlank() }
