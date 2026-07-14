package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderPageBitmapQuality
import kotlin.math.roundToInt

enum class ReaderPlayLikeCurlReferenceMode {
	Reference,
	Diagnostic
}

internal interface ReaderPlayLikeCurlBitmapSource : ReaderPlayLikeCurlRasterLoader<Bitmap> {
	val sourceIdentity: String
	val pageCount: Int
	val quality: ReaderPageBitmapQuality
}

internal class ReaderPlayLikeCurlAssetBitmapSource(
	context: Context
) : ReaderPlayLikeCurlBitmapSource {
	private val applicationContext = context.applicationContext

	override val sourceIdentity: String = "playlikecurl-reference-assets"
	override val pageCount: Int = 8
	override val quality: ReaderPageBitmapQuality = ReaderPageBitmapQuality.Native

	override suspend fun load(key: ReaderPlayLikeCurlRasterKey): Bitmap? = withContext(Dispatchers.IO) {
		val assetPath = "playlikecurl-reference/${key.profile.orientation.assetDirectory}/page${key.pageIndex + 1}.png"
		applicationContext.decodeScaledAsset(assetPath, key.profile.quality)
	}
}

internal class ReaderPlayLikeCurlDiagnosticBitmapSource : ReaderPlayLikeCurlBitmapSource {
	override val sourceIdentity: String = "playlikecurl-diagnostic-pages"
	override val pageCount: Int = 8
	override val quality: ReaderPageBitmapQuality = ReaderPageBitmapQuality.Balanced

	override suspend fun load(key: ReaderPlayLikeCurlRasterKey): Bitmap = withContext(Dispatchers.Default) {
		val nativeWidth = if (key.profile.orientation == ReaderPlayLikeCurlOrientation.Portrait) 1080 else 1920
		val nativeHeight = if (key.profile.orientation == ReaderPlayLikeCurlOrientation.Portrait) 1662 else 1080
		val width = (nativeWidth * key.profile.quality.scale).roundToInt().coerceAtLeast(1)
		val height = (nativeHeight * key.profile.quality.scale).roundToInt().coerceAtLeast(1)
		Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
			val canvas = Canvas(bitmap)
			val hue = (key.pageIndex * 41f) % 360f
			canvas.drawColor(Color.HSVToColor(floatArrayOf(hue, 0.48f, 0.78f)))
			val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
				color = Color.WHITE
				textAlign = Paint.Align.CENTER
				textSize = width.coerceAtMost(height) * 0.12f
				typeface = android.graphics.Typeface.DEFAULT_BOLD
			}
			canvas.drawText("PAGE ${key.pageIndex + 1}", width / 2f, height * 0.48f, paint)
			paint.textSize *= 0.38f
			canvas.drawText(key.profile.orientation.name.uppercase(), width / 2f, height * 0.56f, paint)
		}
	}
}

internal fun ReaderPlayLikeCurlBitmapSource.profile(
	orientation: ReaderPlayLikeCurlOrientation
): ReaderPlayLikeCurlRasterProfile = ReaderPlayLikeCurlRasterProfile(
	sourceIdentity = sourceIdentity,
	orientation = orientation,
	quality = quality
)

private fun Context.decodeScaledAsset(
	assetPath: String,
	quality: ReaderPageBitmapQuality
): Bitmap? {
	val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
	assets.open(assetPath).use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
	if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

	val sampleSize = when (quality) {
		ReaderPageBitmapQuality.Low -> 4
		ReaderPageBitmapQuality.Balanced -> 2
		ReaderPageBitmapQuality.High,
		ReaderPageBitmapQuality.Native -> 1
	}
	val decoded = assets.open(assetPath).use { stream ->
		BitmapFactory.decodeStream(
			stream,
			null,
			BitmapFactory.Options().apply { inSampleSize = sampleSize }
		)
	} ?: return null
	val targetWidth = (bounds.outWidth * quality.scale).roundToInt().coerceAtLeast(1)
	val targetHeight = (bounds.outHeight * quality.scale).roundToInt().coerceAtLeast(1)
	if (decoded.width == targetWidth && decoded.height == targetHeight) return decoded
	return Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also { scaled ->
		if (scaled !== decoded) decoded.recycle()
	}
}

private val ReaderPlayLikeCurlOrientation.assetDirectory: String
	get() = name.lowercase()
