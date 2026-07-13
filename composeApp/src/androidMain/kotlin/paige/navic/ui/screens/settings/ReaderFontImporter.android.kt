package paige.navic.ui.screens.settings

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderCachedRemoteFont
import paige.navic.reader.ReaderImportedFont
import paige.navic.reader.ReaderImportedFontCache
import paige.navic.reader.ReaderRemoteFontDownloadState
import paige.navic.reader.ReaderRemoteFontDownloadStatusCompleted
import paige.navic.reader.ReaderRemoteFontDownloadStatusDownloading
import paige.navic.reader.ReaderRemoteFontDownloadStatusFailed
import paige.navic.reader.ReaderRemoteFontDownloadStatusNone
import paige.navic.reader.ReaderRemoteFontDownloadStatusPaused
import paige.navic.reader.ReaderRemoteFontManifestEntry
import paige.navic.reader.readerManagedStorageRoot
import java.io.ByteArrayOutputStream
import java.net.URL

@Composable
actual fun rememberReaderFontImporter(
	onImported: (ReaderImportedFont) -> Unit,
	onError: (String) -> Unit
): ReaderFontImporter {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val currentOnImported by rememberUpdatedState(onImported)
	val currentOnError by rememberUpdatedState(onError)
	val fontCache = remember(context) {
		ReaderImportedFontCache(readerManagedStorageRoot(context))
	}
	var cachedFontBytesState by remember(fontCache) {
		mutableLongStateOf(fontCache.cachedFontsByteSize())
	}
	var remoteFontsState by remember(fontCache) {
		mutableStateOf(emptyList<ReaderRemoteFontManifestEntry>())
	}
	var cachedRemoteFontsState by remember(fontCache) {
		mutableStateOf(fontCache.listRemoteFonts())
	}
	var remoteFontDownloadsState by remember(fontCache) {
		mutableStateOf(emptyMap<String, ReaderRemoteFontDownloadState>())
	}
	val remoteFontJobs = remember(fontCache) {
		mutableMapOf<String, Job>()
	}
	var remoteFontLoadingState by remember(fontCache) {
		mutableStateOf(false)
	}
	var remoteFontErrorState by remember(fontCache) {
		mutableStateOf<String?>(null)
	}
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		scope.launch {
			runCatching {
				withContext(Dispatchers.IO) {
					val resolver = context.contentResolver
					val input = resolver.openInputStream(uri)
						?: throw IllegalArgumentException("Unable to open selected font.")
					fontCache.importFont(
						input = input,
						displayName = resolver.readerFontDisplayName(uri),
						mimeType = resolver.getType(uri)
					)
				}
			}.fold(
				onSuccess = { imported ->
					cachedFontBytesState = withContext(Dispatchers.IO) {
						fontCache.cachedFontsByteSize()
					}
					currentOnImported(imported)
				},
				onFailure = { error ->
					currentOnError(error.message ?: "Unable to import selected font.")
				}
			)
		}
	}

	fun refreshCacheSize() {
		scope.launch {
			cachedFontBytesState = withContext(Dispatchers.IO) {
				fontCache.cachedFontsByteSize()
			}
		}
	}

	fun updateRemoteFontDownload(state: ReaderRemoteFontDownloadState) {
		remoteFontDownloadsState = remoteFontDownloadsState + (state.fontId to state)
	}

	fun remoteFontDownloadBytes(
		fontId: String,
		url: String,
		knownSize: Int
	): ByteArray {
		if (remoteFontJobs[fontId]?.isActive == false) {
			throw CancellationException("Remote font download stopped.")
		}
		val connection = URL(url).openConnection()
		val totalBytes = connection.contentLengthLong
			.takeIf { size -> size > 0L }
			?: knownSize.toLong().takeIf { size -> size > 0L }
			?: -1L
		val output = ByteArrayOutputStream()
		connection.getInputStream().use { input ->
			val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
			var receivedBytes = 0L
			while (true) {
				if (remoteFontJobs[fontId]?.isActive == false) {
					throw CancellationException("Remote font download stopped.")
				}
				val read = input.read(buffer)
				if (read < 0) break
				output.write(buffer, 0, read)
				receivedBytes += read
				val progress = if (totalBytes > 0L) {
					(receivedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
				} else {
					0.0
				}
				scope.launch {
					updateRemoteFontDownload(
						ReaderRemoteFontDownloadState(
							fontId = fontId,
							filePath = url,
							status = ReaderRemoteFontDownloadStatusDownloading,
							progress = progress
						)
					)
				}
			}
		}
		return output.toByteArray()
	}

	return remember(launcher, fontCache) {
		object : ReaderFontImporter {
			override val supported: Boolean = true
			override val cachedFontBytes: Long
				get() = cachedFontBytesState
			override val remoteFonts: List<ReaderRemoteFontManifestEntry>
				get() = remoteFontsState
			override val cachedRemoteFonts: List<ReaderCachedRemoteFont>
				get() = cachedRemoteFontsState
			override val remoteFontDownloads: Map<String, ReaderRemoteFontDownloadState>
				get() = remoteFontDownloadsState
			override val remoteFontLoading: Boolean
				get() = remoteFontLoadingState
			override val remoteFontError: String?
				get() = remoteFontErrorState

			override fun launch() {
				launcher.launch(arrayOf("*/*"))
			}

			override fun clearImportedFonts() {
				scope.launch {
					cachedFontBytesState = withContext(Dispatchers.IO) {
						fontCache.clearImportedFonts()
						fontCache.cachedFontsByteSize()
					}
					cachedRemoteFontsState = withContext(Dispatchers.IO) {
						fontCache.listRemoteFonts()
					}
				}
			}

			override fun refreshRemoteFonts() {
				scope.launch {
					remoteFontLoadingState = true
					remoteFontErrorState = null
					runCatching {
						withContext(Dispatchers.IO) {
							fontCache.fetchRemoteFontManifest { url -> URL(url).readText() }
						}
					}.fold(
						onSuccess = { fonts ->
							remoteFontsState = fonts
							cachedRemoteFontsState = withContext(Dispatchers.IO) {
								fontCache.listRemoteFonts()
							}
						},
						onFailure = { error ->
							remoteFontErrorState = error.message ?: "Unable to load remote font catalog."
							currentOnError(remoteFontErrorState.orEmpty())
						}
					)
					remoteFontLoadingState = false
				}
			}

			override fun downloadRemoteFont(font: ReaderRemoteFontManifestEntry) {
				remoteFontJobs[font.id]?.cancel()
				val job = scope.launch {
					remoteFontLoadingState = true
					remoteFontErrorState = null
					updateRemoteFontDownload(
						ReaderRemoteFontDownloadState(
							fontId = font.id,
							filePath = font.files.firstOrNull().orEmpty(),
							status = ReaderRemoteFontDownloadStatusDownloading,
							progress = 0.0
						)
					)
					runCatching {
						withContext(Dispatchers.IO) {
							fontCache.downloadRemoteFont(font) { url ->
								remoteFontDownloadBytes(font.id, url, font.size)
							}
						}
					}.fold(
						onSuccess = { cached ->
							updateRemoteFontDownload(
								ReaderRemoteFontDownloadState(
									fontId = font.id,
									filePath = cached.fonts.firstOrNull()?.url.orEmpty(),
									status = ReaderRemoteFontDownloadStatusCompleted,
									progress = 1.0
								)
							)
							cachedRemoteFontsState = withContext(Dispatchers.IO) {
								fontCache.listRemoteFonts()
							}
							cachedFontBytesState = withContext(Dispatchers.IO) {
								fontCache.cachedFontsByteSize()
							}
							cached.fonts.firstOrNull()?.let { imported ->
								currentOnImported(imported.copy(family = cached.family))
							}
						},
						onFailure = { error ->
							if (error is CancellationException) {
								val current = remoteFontDownloadsState[font.id]
								if (current?.status == ReaderRemoteFontDownloadStatusDownloading) {
									updateRemoteFontDownload(
										current.copy(status = ReaderRemoteFontDownloadStatusPaused)
									)
								}
							} else {
								remoteFontErrorState = error.message ?: "Unable to download remote font."
								updateRemoteFontDownload(
									ReaderRemoteFontDownloadState(
										fontId = font.id,
										filePath = font.files.firstOrNull().orEmpty(),
										status = ReaderRemoteFontDownloadStatusFailed,
										progress = remoteFontDownloadsState[font.id]?.progress ?: 0.0,
										error = remoteFontErrorState
									)
								)
								currentOnError(remoteFontErrorState.orEmpty())
							}
						}
					)
					remoteFontJobs.remove(font.id)
					remoteFontLoadingState = remoteFontJobs.any { entry -> entry.value.isActive }
				}
				remoteFontJobs[font.id] = job
			}

			override fun pauseRemoteFontDownload(id: String) {
				remoteFontJobs[id]?.cancel(CancellationException("Remote font download paused."))
				remoteFontDownloadsState[id]?.let { download ->
					updateRemoteFontDownload(download.copy(status = ReaderRemoteFontDownloadStatusPaused))
				}
				remoteFontLoadingState = remoteFontJobs.any { entry -> entry.key != id && entry.value.isActive }
			}

			override fun resumeRemoteFontDownload(font: ReaderRemoteFontManifestEntry) {
				if (remoteFontDownloadsState[font.id]?.status == ReaderRemoteFontDownloadStatusPaused) {
					downloadRemoteFont(font)
				}
			}

			override fun cancelRemoteFontDownload(id: String) {
				remoteFontJobs[id]?.cancel(CancellationException("Remote font download canceled."))
				remoteFontDownloadsState[id]?.let { download ->
					updateRemoteFontDownload(
						download.copy(
							status = ReaderRemoteFontDownloadStatusNone,
							progress = 0.0,
							error = null
						)
					)
				}
				remoteFontLoadingState = remoteFontJobs.any { entry -> entry.value.isActive }
			}

			override fun deleteRemoteFont(id: String) {
				scope.launch {
					withContext(Dispatchers.IO) {
						fontCache.deleteRemoteFont(id)
					}
					cachedRemoteFontsState = withContext(Dispatchers.IO) {
						fontCache.listRemoteFonts()
					}
					refreshCacheSize()
				}
			}
		}
	}
}

private fun ContentResolver.readerFontDisplayName(uri: Uri): String? =
	query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
		if (!cursor.moveToFirst()) return@use null
		val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
		if (index < 0) null else cursor.getString(index)
	}
