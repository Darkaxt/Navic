package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest

internal const val ReaderPageRasterDiskMaxBytes = 384L * 1024L * 1024L
internal const val ReaderPageRasterDecodedMaxEntries = 5

internal interface ReaderPageRasterCodec<T : Any> {
	fun encode(value: T, target: File): Boolean
	fun decode(source: File): T?
	fun release(value: T)
}

internal data class ReaderPageRaster<T : Any>(
	val key: ReaderPageRasterKey,
	val metadata: ReaderPageRasterMetadata,
	val value: T
)

internal data class ReaderPageRasterCacheMetrics(
	val diskEntries: Int,
	val diskBytes: Long,
	val decodedEntries: Int
)

internal class ReaderPageRasterCache<T : Any>(
	private val root: File,
	private val codec: ReaderPageRasterCodec<T>,
	private val maxDiskBytes: Long = ReaderPageRasterDiskMaxBytes,
	private val maxDecodedEntries: Int = ReaderPageRasterDecodedMaxEntries,
	private val clock: () -> Long = System::currentTimeMillis,
	private val onDiagnostic: (String) -> Unit = {}
) {
	private val storageAvailable = !root.exists() || !Files.isSymbolicLink(root.toPath())
	private val manifest = ReaderPageRasterManifest(root)
	private val entries = linkedMapOf<String, ReaderPageRasterManifestEntry>()
	private val decoded = LinkedHashMap<String, ReaderPageRaster<T>>(0, 0.75f, true)
	private var protectedChapter: ReaderPageRasterChapterKey? = null

	init {
		if (storageAvailable) {
			root.mkdirs()
			cleanupTemporaryAndSymlinkFiles()
			manifest.read().forEach { entry ->
				val path = root.resolve(entry.rasterFileName)
				if (entry.key.schemaVersion == ReaderPageRasterSchemaVersion && path.isRegularRaster(entry.byteSize)) {
					entries[entry.key.digest] = entry.copy(
						lastAccessEpochMillis = maxOf(entry.lastAccessEpochMillis, path.lastModified())
					)
				}
			}
			deleteOrphanRasters()
			persistManifest()
		}
	}

	fun write(key: ReaderPageRasterKey, metadata: ReaderPageRasterMetadata, value: T): Boolean {
		if (!storageAvailable) return writeFailed(key, "storage-unavailable")
		if (key.schemaVersion != ReaderPageRasterSchemaVersion) {
			return writeFailed(key, "schema-mismatch-${key.schemaVersion}")
		}
		if (maxDiskBytes <= 0L) return writeFailed(key, "disk-cache-disabled")
		root.mkdirs()
		val temporary = root.resolve("${key.digest}.${System.nanoTime()}.tmp")
		val encoded = runCatching { codec.encode(value, temporary) }.getOrDefault(false)
		if (!encoded || !temporary.isFile || temporary.length() <= 0L) {
			temporary.delete()
			return writeFailed(key, "encode-failed")
		}
		runCatching { FileOutputStream(temporary, true).use { output -> output.fd.sync() } }
			.getOrElse {
				temporary.delete()
				return writeFailed(key, "sync-failed-${it.javaClass.simpleName}")
		}
		val byteSize = temporary.length()
		if (byteSize > maxDiskBytes && key.chapter != protectedChapter) {
			temporary.delete()
			return writeFailed(key, "raster-exceeds-cache-limit", byteSize)
		}
		val contentDigest = temporary.sha256().take(16)
		val lockRequestedAt = System.nanoTime()
		val published = synchronized(this) {
			val lockWaitMillis = elapsedMillis(lockRequestedAt)
			if (lockWaitMillis >= ReaderPageRasterContentionDiagnosticMillis) {
				onDiagnostic(
					"write-lock-contention key=${key.digest.take(12)} " +
						"waitMillis=$lockWaitMillis encodedBytes=$byteSize"
				)
			}
			val target = root.resolve("${key.digest}-$contentDigest.png")
			val targetExisted = target.isFile
			val promotion = runCatching { readerPageRasterPromote(temporary, target) }
			if (promotion.isFailure) {
				temporary.delete()
				return@synchronized writeFailed(
					key,
					"promote-failed-${promotion.exceptionOrNull()?.javaClass?.simpleName.orEmpty()}",
					byteSize
				)
			}

			val entry = ReaderPageRasterManifestEntry(
				key = key,
				rasterFileName = target.name,
				byteSize = byteSize,
				lastAccessEpochMillis = clock(),
				metadata = metadata
			)
			val proposed = entries.toMutableMap().apply { put(key.digest, entry) }
			val retained = retainedWithinDiskLimit(proposed.values)
			if (!manifest.write(retained)) {
				if (!targetExisted) target.delete()
				return@synchronized writeFailed(key, "manifest-write-failed", byteSize)
			}

			val retainedIds = retained.mapTo(mutableSetOf()) { retainedEntry -> retainedEntry.key.digest }
			val retiredEntries = entries.values.filter { existing ->
				existing.key.digest !in retainedIds ||
					(existing.key.digest == key.digest && existing.rasterFileName != entry.rasterFileName)
			}
			retiredEntries.forEach { retired ->
				decoded.remove(retired.key.digest)?.takeIf { raster -> raster.value !== value }?.let { raster ->
					codec.release(raster.value)
				}
				deleteEntryFile(retired)
			}
			entries.clear()
			retained.forEach { retainedEntry -> entries[retainedEntry.key.digest] = retainedEntry }
			putDecoded(ReaderPageRaster(key, metadata, value))
			key.digest in entries
		}
		return published
	}

	private fun writeFailed(
		key: ReaderPageRasterKey,
		reason: String,
		encodedBytes: Long = 0L
	): Boolean {
		onDiagnostic(
			"write-failed key=${key.digest.take(12)} reason=$reason " +
				"encodedBytes=${encodedBytes.coerceAtLeast(0L)} " +
				"availableBytes=${root.usableSpace.coerceAtLeast(0L)}"
		)
		return false
	}

	@Synchronized
	fun contains(key: ReaderPageRasterKey): Boolean {
		if (!storageAvailable) return false
		val entry = entries[key.digest]?.takeIf { candidate -> candidate.key.identity == key.identity } ?: return false
		val path = root.resolve(entry.rasterFileName)
		return path.isFile && path.length() > 0L
	}

	@Synchronized
	fun read(key: ReaderPageRasterKey): ReaderPageRaster<T>? {
		if (!storageAvailable) return null
		decoded[key.digest]?.let { raster ->
			touch(key.digest)
			return raster
		}
		val entry = entries[key.digest]?.takeIf { candidate -> candidate.key.identity == key.identity } ?: return null
		val path = root.resolve(entry.rasterFileName)
		if (!path.isRegularRaster(entry.byteSize)) {
			removeEntry(key.digest)
			return null
		}
		val value = runCatching { codec.decode(path) }.getOrNull()
		if (value == null) {
			removeEntry(key.digest)
			return null
		}
		val raster = ReaderPageRaster(key, entry.metadata, value)
		putDecoded(raster)
		touch(key.digest)
		return raster
	}

	fun <R : Any> readCopy(
		key: ReaderPageRasterKey,
		copy: (T) -> R?
	): ReaderPageRaster<R>? {
		val lockRequestedAt = System.nanoTime()
		return synchronized(this) {
			val lockWaitMillis = elapsedMillis(lockRequestedAt)
			if (lockWaitMillis >= ReaderPageRasterContentionDiagnosticMillis) {
				onDiagnostic(
					"read-copy-lock-contention key=${key.digest.take(12)} waitMillis=$lockWaitMillis"
				)
			}
			val wasDecoded = decoded.containsKey(key.digest)
			val raster = read(key) ?: return@synchronized null
			try {
				copy(raster.value)?.let { copied -> ReaderPageRaster(key, raster.metadata, copied) }
			} finally {
				if (!wasDecoded && maxDecodedEntries <= 0) codec.release(raster.value)
			}
		}
	}

	@Synchronized
	fun retainProfile(profile: ReaderPageRasterProfile): Int {
		if (!storageAvailable) return 0
		val obsolete = entries.values.filter { entry ->
			entry.key.publicationHash == profile.publicationHash && entry.key.profile != profile
		}
		if (obsolete.isEmpty()) return 0
		val obsoleteIds = obsolete.mapTo(mutableSetOf()) { entry -> entry.key.digest }
		val retained = entries.values.filterNot { entry -> entry.key.digest in obsoleteIds }
		if (!manifest.write(retained)) return 0
		obsolete.forEach { entry ->
			decoded.remove(entry.key.digest)?.let { raster -> codec.release(raster.value) }
			deleteEntryFile(entry)
			entries.remove(entry.key.digest)
		}
		return obsolete.size
	}

	@Synchronized
	fun protectChapter(chapter: ReaderPageRasterChapterKey?) {
		if (!storageAvailable || protectedChapter == chapter) return
		protectedChapter = chapter
		val retained = retainedWithinDiskLimit(entries.values)
		if (!manifest.write(retained)) return
		val retainedIds = retained.mapTo(mutableSetOf()) { entry -> entry.key.digest }
		entries.values.filter { entry -> entry.key.digest !in retainedIds }.forEach { entry ->
			decoded.remove(entry.key.digest)?.let { raster -> codec.release(raster.value) }
			deleteEntryFile(entry)
		}
		entries.clear()
		retained.forEach { entry -> entries[entry.key.digest] = entry }
	}

	@Synchronized
	fun remove(key: ReaderPageRasterKey): Boolean {
		val entry = entries[key.digest]?.takeIf { candidate -> candidate.key.identity == key.identity } ?: return false
		entries.remove(key.digest)
		decoded.remove(key.digest)?.let { raster -> codec.release(raster.value) }
		deleteEntryFile(entry)
		persistManifest()
		return true
	}

	@Synchronized
	fun metrics(): ReaderPageRasterCacheMetrics = ReaderPageRasterCacheMetrics(
		diskEntries = entries.size,
		diskBytes = entries.values.sumOf { entry -> entry.byteSize.coerceAtLeast(0L) },
		decodedEntries = decoded.size
	)

	@Synchronized
	fun close() {
		decoded.values
			.distinctBy { raster -> System.identityHashCode(raster.value) }
			.forEach { raster -> codec.release(raster.value) }
		decoded.clear()
	}

	@Synchronized
	internal fun pathFor(key: ReaderPageRasterKey): File =
		entries[key.digest]?.let { entry -> root.resolve(entry.rasterFileName) }
			?: root.resolve("${key.digest}.png")
	internal fun manifestPath(): File = manifest.file

	private fun retainedWithinDiskLimit(candidates: Collection<ReaderPageRasterManifestEntry>): List<ReaderPageRasterManifestEntry> {
		val protected = candidates
			.filter { entry -> entry.key.chapter == protectedChapter }
			.sortedByDescending { entry -> entry.lastAccessEpochMillis }
		val retained = protected.toMutableList()
		var bytes = protected.sumOf { entry -> entry.byteSize.coerceAtLeast(0L) }
		candidates
			.filterNot { entry -> entry.key.chapter == protectedChapter }
			.sortedByDescending { entry -> entry.lastAccessEpochMillis }
			.forEach { entry ->
				val entryBytes = entry.byteSize.coerceAtLeast(0L)
				if (bytes <= maxDiskBytes - entryBytes) {
					retained += entry
					bytes += entryBytes
				}
			}
		return retained
	}

	private fun putDecoded(raster: ReaderPageRaster<T>) {
		if (maxDecodedEntries <= 0) return
		decoded.put(raster.key.digest, raster)?.takeIf { previous -> previous.value !== raster.value }?.let { previous ->
			codec.release(previous.value)
		}
		while (decoded.size > maxDecodedEntries) {
			val eldest = decoded.entries.iterator().next()
			decoded.remove(eldest.key)
			codec.release(eldest.value.value)
		}
	}

	private fun touch(digest: String) {
		val current = entries[digest] ?: return
		val updated = current.copy(lastAccessEpochMillis = clock())
		entries[digest] = updated
		root.resolve(current.rasterFileName).setLastModified(updated.lastAccessEpochMillis)
	}

	private fun removeEntry(digest: String) {
		val entry = entries.remove(digest) ?: return
		decoded.remove(digest)?.let { raster -> codec.release(raster.value) }
		deleteEntryFile(entry)
		persistManifest()
	}

	private fun deleteEntryFile(entry: ReaderPageRasterManifestEntry) {
		val path = root.resolve(entry.rasterFileName)
		if (!Files.isSymbolicLink(path.toPath())) path.delete()
	}

	private fun cleanupTemporaryAndSymlinkFiles() {
		root.listFiles().orEmpty().forEach { child ->
			if (child.name.endsWith(".tmp") || Files.isSymbolicLink(child.toPath())) child.delete()
		}
	}

	private fun deleteOrphanRasters() {
		val registered = entries.values.mapTo(mutableSetOf()) { entry -> entry.rasterFileName }
		root.listFiles().orEmpty()
			.filter { file -> file.extension == "png" && file.name !in registered }
			.forEach(File::delete)
	}

	private fun persistManifest(): Boolean = manifest.write(entries.values)

	private fun File.isRegularRaster(expectedSize: Long): Boolean =
		isFile && !Files.isSymbolicLink(toPath()) && length() == expectedSize && expectedSize > 0L


	private fun File.sha256(): String {
		val digest = MessageDigest.getInstance("SHA-256")
		inputStream().use { input ->
			val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
			while (true) {
				val count = input.read(buffer)
				if (count < 0) break
				digest.update(buffer, 0, count)
			}
		}
		return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
	}

	private fun elapsedMillis(startedAtNanos: Long): Long =
		((System.nanoTime() - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)
}

private const val ReaderPageRasterContentionDiagnosticMillis = 100L

internal object ReaderAndroidPageRasterCodec : ReaderPageRasterCodec<Bitmap> {
	override fun encode(value: Bitmap, target: File): Boolean =
		FileOutputStream(target).use { output -> value.compress(Bitmap.CompressFormat.PNG, 100, output) }

	override fun decode(source: File): Bitmap? = BitmapFactory.decodeFile(source.path)

	override fun release(value: Bitmap) {
		if (!value.isRecycled) value.recycle()
	}
}
