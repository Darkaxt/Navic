package paige.navic.ui.screens.reader

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal const val ReaderRenderAssetRoot = "reader"
private const val ReaderRenderAssetIdentityFileName = "reader-render-asset-identity"
private const val ReaderRenderAssetIdentityPrefix = "assets-v1-"

/**
 * Content digest of the bundled reader assets (JS, CSS, fonts, textures). Paths are sorted and
 * each entry hashes its path, byte length and bytes, so a rename, edit, addition or removal yields
 * a new digest, while identical assets on a later install yield the same one.
 */
internal fun readerRenderAssetDigest(
	paths: Collection<String>,
	open: (String) -> InputStream
): String {
	val digest = MessageDigest.getInstance("SHA-256")
	val buffer = ByteArray(64 * 1024)
	paths.sorted().forEach { path ->
		val content = MessageDigest.getInstance("SHA-256")
		var length = 0L
		open(path).use { input ->
			while (true) {
				val read = input.read(buffer)
				if (read < 0) break
				content.update(buffer, 0, read)
				length += read
			}
		}
		digest.update("$path\u0000$length\u0000".encodeToByteArray())
		digest.update(content.digest())
	}
	return ReaderRenderAssetIdentityPrefix + digest.digest()
		.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

/**
 * Raster alias token: reader rendering inputs that no raster key fingerprints. Kotlin-side render
 * changes bump [ReaderPageRasterSchemaVersion]; asset changes change this digest. Unlike the
 * package update time it replaces, an app update with unchanged reader assets keeps prepared pages.
 * Hashing runs once per install (cached on disk against the package update time) and once per
 * process otherwise; callers run it off the main thread.
 */
internal object ReaderRenderAssetIdentity {
	@Volatile
	private var cached: String? = null

	fun token(context: Context): String = cached ?: synchronized(this) {
		cached ?: compute(context.applicationContext).also { token -> cached = token }
	}

	private fun compute(context: Context): String {
		val installStamp = runCatching {
			context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
		}.getOrNull()
		val file = File(context.noBackupFilesDir, ReaderRenderAssetIdentityFileName)
		if (installStamp != null) {
			runCatching { file.readText().lines() }.getOrNull()
				?.takeIf { lines ->
					lines.size >= 2 && lines[0] == installStamp &&
						lines[1].startsWith(ReaderRenderAssetIdentityPrefix)
				}
				?.let { lines -> return lines[1] }
		}
		val assets = context.assets
		val token = readerRenderAssetDigest(listAssetFiles(context, ReaderRenderAssetRoot)) { path ->
			assets.open(path)
		}
		if (installStamp != null) {
			runCatching {
				val temp = File(file.parentFile, "$ReaderRenderAssetIdentityFileName.tmp")
				temp.writeText("$installStamp\n$token\n")
				if (!temp.renameTo(file)) temp.delete()
			}
		}
		return token
	}

	private fun listAssetFiles(context: Context, root: String): List<String> {
		val files = mutableListOf<String>()
		val pending = ArrayDeque(listOf(root))
		while (pending.isNotEmpty()) {
			val path = pending.removeFirst()
			val children = context.assets.list(path).orEmpty()
			if (children.isEmpty()) {
				// AssetManager.list is empty for files and for empty directories alike.
				if (runCatching { context.assets.open(path).close() }.isSuccess) files += path
			} else {
				children.forEach { child -> pending += "$path/$child" }
			}
		}
		return files
	}
}
