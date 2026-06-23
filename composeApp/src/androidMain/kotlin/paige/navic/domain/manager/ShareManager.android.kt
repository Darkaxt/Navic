package paige.navic.domain.manager

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual class ShareManager(
	private val context: Context
) {
	private val dispatcher = Dispatchers.IO

	actual suspend fun shareImage(bitmap: ImageBitmap, fileName: String) {
		val androidBitmap = bitmap.asAndroidBitmap()

		val imageFolder = File(context.cacheDir, "shared_images")
		imageFolder.mkdirs()
		val file = File(imageFolder, fileName)

		try {
			withContext(dispatcher) {
				FileOutputStream(file).use { out ->
					androidBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
			return
		}

		val contentUri = FileProvider.getUriForFile(
			context,
			"${context.packageName}.fileprovider",
			file
		)

		val intent = Intent(Intent.ACTION_SEND).apply {
			type = "image/png"
			putExtra(Intent.EXTRA_STREAM, contentUri)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		val chooser = Intent.createChooser(intent, "Share Image")
		chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		context.startActivity(chooser)
	}

	actual suspend fun shareFile(fileName: String, mimeType: String, bytes: ByteArray) {
		val safeFileName = fileName.sanitizedSharedFileName()
		val fileFolder = File(context.cacheDir, "shared_files")
		fileFolder.mkdirs()
		val file = File(fileFolder, safeFileName)

		withContext(dispatcher) {
			FileOutputStream(file).use { outputStream ->
				outputStream.write(bytes)
			}
		}

		val contentUri = FileProvider.getUriForFile(
			context,
			"${context.packageName}.fileprovider",
			file
		)

		val intent = Intent(Intent.ACTION_SEND).apply {
			type = mimeType.ifBlank { "application/octet-stream" }
			putExtra(Intent.EXTRA_STREAM, contentUri)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		val chooser = Intent.createChooser(intent, "Share File")
		chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		try {
			context.startActivity(chooser)
		} catch (_: ActivityNotFoundException) {
			val viewIntent = Intent(Intent.ACTION_VIEW).apply {
				setDataAndType(contentUri, mimeType.ifBlank { "application/octet-stream" })
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			}
			try {
				context.startActivity(viewIntent)
			} catch (_: ActivityNotFoundException) {
				return
			}
		}
	}

	actual suspend fun shareString(string: String) {
		val intent = Intent(Intent.ACTION_SEND).apply {
			type = "text/plain"
			putExtra(Intent.EXTRA_TEXT, string)
		}

		val chooser = Intent.createChooser(intent, "Share")
		chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		context.startActivity(chooser)
	}
}

private fun String.sanitizedSharedFileName(): String =
	trim()
		.takeIf { it.isNotEmpty() }
		?.replace(Regex("""[\\/:*?"<>|]+"""), " ")
		?.replace(Regex("\\s+"), " ")
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: "navic-file"
