package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.koinInject
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.reader.BinderyReaderPublicationResolver
import paige.navic.reader.ReaderPublicationCachePathPrefix
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderPublicationResourceRequest
import paige.navic.reader.ReaderWebRuntime
import paige.navic.reader.readerPublicationCacheRoot
import paige.navic.reader.readerPublicationResourceLogLabel
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.Logger

private const val ReaderPublicationRuntimeLogTag = "ReaderPublicationRuntime"

@Composable
actual fun ReaderPublicationRuntimeHost(
	reader: Screen.Reader,
	onPublicationReady: (String, String?) -> Unit,
	onError: (String) -> Unit
) {
	if (reader.kind == ReaderPublicationKind.Readaloud && reader.mediaOverlayEnabled) return

	val context = LocalContext.current
	val repository = koinInject<BinderyRepository>()
	val currentOnPublicationReady by rememberUpdatedState(onPublicationReady)
	val currentOnError by rememberUpdatedState(onError)

	LaunchedEffect(reader.bookId, reader.resourceHref, reader.publicationUrl, reader.kind, reader.mediaOverlayEnabled) {
		val directUrl = reader.publicationUrl.takeIf {
			reader.resourceHref.isBlank() || it.isLocalReaderPublicationUrl()
		}
		if (directUrl != null) {
			Logger.i(
				ReaderPublicationRuntimeLogTag,
				"Reader publication uses direct url kind=${reader.kind} " +
					"url=${readerPublicationResourceLogLabel(directUrl)} " +
					"shellCover=unavailable"
			)
			currentOnPublicationReady(directUrl, null)
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
				cacheRoot = readerPublicationCacheRoot(context)
			).resolve(
				ReaderPublicationResourceRequest(
					bookId = reader.bookId,
					title = reader.title,
					resourceHref = reader.resourceHref,
					sourceUrl = reader.publicationUrl,
					kind = reader.kind,
					format = reader.publicationFormat,
					mediaOverlayEnabled = reader.mediaOverlayEnabled
				)
			)
			Logger.i(
				ReaderPublicationRuntimeLogTag,
				"Reader publication prepared url=${readerPublicationResourceLogLabel(resolved.publicationUrl)} " +
					"cache=${if (resolved.fromCache) "hit" else "miss"} " +
					"cacheKey=${resolved.cacheKey} " +
					"shellCover=${if (resolved.shellCoverUrl.isNullOrBlank()) "missing" else "present"} " +
					"fileBytes=${resolved.publicationFile.length()}"
			)
			resolved
		}.fold(
			onSuccess = { resolved ->
				currentOnPublicationReady(resolved.publicationUrl, resolved.shellCoverUrl)
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
