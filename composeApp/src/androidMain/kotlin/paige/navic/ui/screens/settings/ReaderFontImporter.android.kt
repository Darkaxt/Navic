package paige.navic.ui.screens.settings

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderImportedFont
import paige.navic.reader.ReaderImportedFontCache
import paige.navic.reader.readerPublicationCacheRoot

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
		ReaderImportedFontCache(readerPublicationCacheRoot(context))
	}
	var cachedFontBytesState by remember(fontCache) {
		mutableLongStateOf(fontCache.cachedFontsByteSize())
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

	return remember(launcher, fontCache) {
		object : ReaderFontImporter {
			override val supported: Boolean = true
			override val cachedFontBytes: Long
				get() = cachedFontBytesState

			override fun launch() {
				launcher.launch(arrayOf("*/*"))
			}

			override fun clearImportedFonts() {
				scope.launch {
					cachedFontBytesState = withContext(Dispatchers.IO) {
						fontCache.clearImportedFonts()
						fontCache.cachedFontsByteSize()
					}
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
