package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
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
	val currentReader by rememberUpdatedState(reader)
	val sessionLeases = remember(reader) { mutableListOf<ReaderSessionLease>() }

	DisposableEffect(sessionLeases) {
		onDispose {
			sessionLeases.forEach(ReaderSessionLease::release)
		}
	}

	LaunchedEffect(reader) {
		val operationReader = reader
		val operationOnPublicationReady = onPublicationReady
		val operationOnError = onError
		val savedProgress = repository.savedReaderProgressFor(operationReader)
		val preferredShellCoverUrl = operationReader.fullscreenCoverUrl?.trim()?.takeIf { it.isNotEmpty() }
		val externalShellCoverHref = preferredShellCoverUrl?.takeUnless { it.isLocalReaderPublicationUrl() }
		val directUrl = operationReader.publicationUrl.takeIf {
			operationReader.resourceHref.isBlank() || it.isLocalReaderPublicationUrl()
		}
		if (directUrl != null) {
			Logger.i(
				ReaderPublicationRuntimeLogTag,
				"Reader publication uses direct url kind=${operationReader.kind} " +
					"shellCoverPresent=${preferredShellCoverUrl != null}"
			)
			currentCoroutineContext().ensureActive()
			if (currentReader == operationReader) {
				operationOnPublicationReady(directUrl, preferredShellCoverUrl, null, savedProgress, null)
			}
			return@LaunchedEffect
		}
		Logger.i(
			ReaderPublicationRuntimeLogTag,
			"Preparing reader publication kind=${operationReader.kind}"
		)
		val resolved = try {
			BinderyReaderPublicationResolver(
				fetchResourceBytes = { path ->
					Logger.i(
						ReaderPublicationRuntimeLogTag,
						"Fetching reader publication resource"
					)
					repository.getResourceBytes(path).getOrThrow().also { bytes ->
						Logger.i(
							ReaderPublicationRuntimeLogTag,
							"Fetched reader publication resource bytes=${bytes.size}"
						)
					}
				},
				cacheRoot = readerManagedStorageRoot(context)
			).resolve(
				ReaderPublicationResourceRequest(
					bookId = operationReader.bookId,
					title = operationReader.title,
					resourceHref = operationReader.resourceHref,
					sourceUrl = operationReader.publicationUrl,
					kind = operationReader.kind,
					format = operationReader.publicationFormat,
					mediaOverlayEnabled = operationReader.mediaOverlayEnabled,
					externalShellCoverHref = externalShellCoverHref
				)
			)
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (_: Throwable) {
			currentCoroutineContext().ensureActive()
			if (currentReader == operationReader) {
				Logger.e(
					ReaderPublicationRuntimeLogTag,
					"Reader publication preparation failed kind=${operationReader.kind}"
				)
				operationOnError("Unable to load reader publication.")
			}
			return@LaunchedEffect
		}

		val operationContext = currentCoroutineContext()
		if (!operationContext.isActive || currentReader != operationReader) {
			resolved.sessionLease.release()
			operationContext.ensureActive()
			return@LaunchedEffect
		}
		sessionLeases += resolved.sessionLease
		Logger.i(
			ReaderPublicationRuntimeLogTag,
			"Reader publication prepared fromCache=${resolved.fromCache} " +
				"shellCoverPresent=${!resolved.shellCoverUrl.isNullOrBlank()} " +
				"shellCoverTintPresent=${!resolved.shellCoverTint.isNullOrBlank()} " +
				"fileBytes=${resolved.publicationFile.length()}"
		)
		val shellCoverUrl = if (externalShellCoverHref == null) {
			preferredShellCoverUrl ?: resolved.shellCoverUrl
		} else {
			resolved.shellCoverUrl
		}
		currentCoroutineContext().ensureActive()
		if (currentReader != operationReader) {
			sessionLeases.remove(resolved.sessionLease)
			resolved.sessionLease.release()
			return@LaunchedEffect
		}
		operationOnPublicationReady(
			resolved.publicationUrl,
			shellCoverUrl,
			resolved.shellCoverTint,
			savedProgress,
			androidWordSyncPublicationVerifierOrNull(
				publicationFile = resolved.publicationFile,
				format = operationReader.publicationFormat
			)
		)
	}
}

private fun String.isLocalReaderPublicationUrl(): Boolean =
	startsWith("file:", ignoreCase = true) ||
	startsWith("content:", ignoreCase = true) ||
	startsWith("${ReaderWebRuntime.AssetLoaderOrigin}$ReaderPublicationCachePathPrefix", ignoreCase = true)

private suspend fun BinderyRepository.savedReaderProgressFor(reader: Screen.Reader): BinderyReadingProgress? {
	if (reader.bookId.isBlank() || reader.resourceHref.isBlank()) return null
	val result = getReadingProgress(bookId = reader.bookId)
	val failure = result.exceptionOrNull()
	if (failure is CancellationException) throw failure
	return result
		.onFailure {
			Logger.w(
				ReaderPublicationRuntimeLogTag,
				"Reader saved progress lookup failed"
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
