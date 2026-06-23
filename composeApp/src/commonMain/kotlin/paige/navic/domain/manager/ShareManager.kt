package paige.navic.domain.manager

import androidx.compose.ui.graphics.ImageBitmap

expect class ShareManager {
	suspend fun shareImage(bitmap: ImageBitmap, fileName: String)
	suspend fun shareFile(fileName: String, mimeType: String, bytes: ByteArray)
	suspend fun shareString(string: String)
}
