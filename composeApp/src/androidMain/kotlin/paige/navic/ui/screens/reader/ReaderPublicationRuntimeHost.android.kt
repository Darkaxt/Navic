package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.koinInject
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.reader.BinderyReaderPublicationResolver
import paige.navic.reader.ReaderPublicationCachePathPrefix
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderPublicationResourceRequest
import paige.navic.reader.ReaderSessionLease
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.WordSyncPublicationVerifier
import paige.navic.reader.androidWordSyncPublicationVerifierOrNull
import paige.navic.reader.readerManagedStorageRoot
import paige.navic.reader.readerPublicationResourceLogLabel
import paige.navic.reader.toReaderStartLocatorForReader
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.Logger

private const val ReaderPublicationRuntimeLogTag = "ReaderPublicationRuntime"

@Composable
actual fun ReaderPublicationRuntimeHost(
	reader: Screen.Reader,
	onPublicationReady: (
		String,
		String?,
		String?,
		BinderyReadingProgress?,
		WordSyncPublicationVerifier?
	) -> Unit,
	onError: (String) -> Unit
) {
	if (reader.kind == ReaderPublicationKind.Readaloud && reader.mediaOverlayEnabled) return

	val context = LocalContext.current
	val repository = koinInject<BinderyRepository>()
	val currentOnPublicationReady by rememberUpdatedState(onPublicationReady)
	val currentOnError by rememberUpdatedState(onError)
	val sessionLeases = remember { mutableListOf<ReaderSessionLease>() }

	DisposableEffect(sessionLeases) {
		onDispose {
			sessionLeases.forEach(ReaderSessionLease::release)
		}
	}

	LaunchedEffect(
		reader.bookId,
		reader.resourceHref,
		reader.publicationUrl,
		reader.kind,
		reader.mediaOverlayEnabled,
		reader.fullscreenCoverUrl
	) {
		val savedProgress = repository.savedReaderProgressFor(reader)
		val preferredShellCoverUrl = reader.fullscreenCoverUrl?.trim()?.takeIf { it.isNotEmpty() }
		val externalShellCoverHref = preferredShellCoverUrl?.takeUnless { it.isLocalReaderPublicationUrl() }
		val directUrl = reader.publicationUrl.takeIf {
			reader.resourceHref.isBlank() || it.isLocalReaderPublicationUrl()
		}
		if (directUrl != null) {
			val directShellCoverLog = if (preferredShellCoverUrl == null) {
				"shellCover=unavailable"
			} else {
				"shellCover=external"
			}
			Logger.i(
				ReaderPublicationRuntimeLogTag,
				"Reader publication uses direct url kind=${reader.kind} " +
					"url=${readerPublicationResourceLogLabel(directUrl)} " +
					directShellCoverLog
			)
			currentOnPublicationReady(directUrl, preferredShellCoverUrl, null, savedProgress, null)
			return@LaunchedEffect
		}
		Logger.i(
			ReaderPublicationRuntimeLogTag,
			"Preparing reader publication kind=${reader.kind} bookId=${reader.bookId} " +
				"resource=${readerPublicationResourceLogLabel(reader.resourceHref)} " +
				"source=${readerPublicationResourceLogLabel(reader.publicationUrl)}"
		)
		runCatching {
			val resolved = BinderyReaderPublicationResolver(
				fetchResourceBytes = { path ->
					Logger.i(
						ReaderPublicationRuntimeLogTag,
						"Fetching reader publication resource path=${readerPublicationResourceLogLabel(path)}"
					)
					repository.getResourceBytes(path).getOrThrow().also { bytes ->
						Logger.i(
							ReaderPublicationRuntimeLogTag,
							"Fetched reader publication resource path=${readerPublicationResourceLogLabel(path)} " +
								"bytes=${bytes.size}"
						)
					}
				},
				cacheRoot = readerManagedStorageRoot(context)
			).resolve(
				ReaderPublicationResourceRequest(
					bookId = reader.bookId,
					title = reader.title,
					resourceHref = reader.resourceHref,
					sourceUrl = reader.publicationUrl,
					kind = reader.kind,
					format = reader.publicationFormat,
					mediaOverlayEnabled = reader.mediaOverlayEnabled,
					externalShellCoverHref = externalShellCoverHref
				)
			)
			Logger.i(
				ReaderPublicationRuntimeLogTag,
				"Reader publication prepared url=${readerPublicationResourceLogLabel(resolved.publicationUrl)} " +
					"cache=${if (resolved.fromCache) "hit" else "miss"} " +
					"cacheKey=${resolved.cacheKey} " +
					"shellCover=${if (resolved.shellCoverUrl.isNullOrBlank()) "missing" else "present"} " +
					"shellCoverTint=${if (resolved.shellCoverTint.isNullOrBlank()) "missing" else "present"} " +
					"fileBytes=${resolved.publicationFile.length()}"
			)
			resolved
		}.fold(
			onSuccess = { resolved ->
				sessionLeases += resolved.sessionLease
				val shellCoverUrl = if (externalShellCoverHref == null) {
					preferredShellCoverUrl ?: resolved.shellCoverUrl
				} else {
					resolved.shellCoverUrl
				}
				currentOnPublicationReady(
						resolved.publicationUrl,
						shellCoverUrl,
						resolved.shellCoverTint,
						savedProgress,
						androidWordSyncPublicationVerifierOrNull(
							publicationFile = resolved.publicationFile,
							format = reader.publicationFormat
						)
					)
			},
			onFailure = { error ->
				Logger.e(
					ReaderPublicationRuntimeLogTag,
					"Reader publication preparation failed kind=${reader.kind} bookId=${reader.bookId} " +
						"resource=${readerPublicationResourceLogLabel(reader.resourceHref)}",
					error
				)
				currentOnError(error.message ?: "Unable to load reader publication.")
			}
		)
	}
}

private fun String.isLocalReaderPublicationUrl(): Boolean =
	startsWith("file:", ignoreCase = true) ||
	startsWith("content:", ignoreCase = true) ||
	startsWith("${ReaderWebRuntime.AssetLoaderOrigin}$ReaderPublicationCachePathPrefix", ignoreCase = true)

private suspend fun BinderyRepository.savedReaderProgressFor(reader: Screen.Reader): BinderyReadingProgress? {
	if (reader.bookId.isBlank() || reader.resourceHref.isBlank()) return null
	return getReadingProgress(bookId = reader.bookId)
		.onFailure { error ->
			Logger.w(
				ReaderPublicationRuntimeLogTag,
				"Reader saved progress lookup failed bookId=${reader.bookId} " +
					"resource=${readerPublicationResourceLogLabel(reader.resourceHref)}",
				error
			)
		}
		.getOrNull()
		?.takeIf { progress ->
			progress.toReaderStartLocatorForReader(
				bookId = reader.bookId,
				resourceHref = reader.resourceHref,
				kind = reader.kind
			) != null
		}
}
