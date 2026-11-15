package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.async
import org.koin.compose.koinInject
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderSessionLease
import paige.navic.reader.WordSyncPublicationVerifier
import paige.navic.reader.androidWordSyncPublicationVerifierOrNull
import paige.navic.reader.binderyReaderPublicationResolver
import paige.navic.reader.readerDirectPublicationUrl
import paige.navic.reader.readerPreferredShellCoverUrl
import paige.navic.reader.readerPublicationResourceLogLabel
import paige.navic.reader.toPublicationResourceRequest
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
		// Saved progress is network-only; look it up while the publication resolves instead of before it.
		val savedProgressLookup = async { repository.savedReaderProgressFor(reader) }
		val preferredShellCoverUrl = reader.readerPreferredShellCoverUrl()
		val directUrl = reader.readerDirectPublicationUrl()
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
			val savedProgress = savedProgressLookup.await()
			currentOnPublicationReady(directUrl, preferredShellCoverUrl, null, savedProgress, null)
			return@LaunchedEffect
		}
		val request = reader.toPublicationResourceRequest()
		val externalShellCoverHref = request.externalShellCoverHref
		Logger.i(
			ReaderPublicationRuntimeLogTag,
			"Preparing reader publication kind=${reader.kind} bookId=${reader.bookId} " +
				"resource=${readerPublicationResourceLogLabel(reader.resourceHref)} " +
				"source=${readerPublicationResourceLogLabel(reader.publicationUrl)}"
		)
		runCatching {
			val resolved = binderyReaderPublicationResolver(context, repository).resolve(request)
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
				val savedProgress = savedProgressLookup.await()
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
