package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.IdentityHashMap

/** No implicit ceiling: production passes the Cache manager limit, which defaults to "No limit" (#57). */
internal const val ReaderPageRasterDiskMaxBytes = Long.MAX_VALUE
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
	val diskByteLimit: Long,
	val decodedEntries: Int,
	val uniqueDecodedBitmaps: Int,
	val uniqueDecodedBitmapLimit: Int,
	val pendingDecodedReleases: Int,
	val activeEncodePins: Int,
	val encodePinnedIdentities: Int
)

internal class ReaderPageRasterCache<T : Any>(
	private val root: File,
	private val codec: ReaderPageRasterCodec<T>,
	private val maxDiskBytes: Long = ReaderPageRasterDiskMaxBytes,
	private val maxDecodedEntries: Int = ReaderPageRasterDecodedMaxEntries,
	private val clock: () -> Long = System::currentTimeMillis,
	private val onDiagnostic: (String) -> Unit = {},
	private val onOwnershipMutated: () -> Unit = {}
) {
	private data class EncodedWindowProtection(
		val profile: ReaderPageRasterProfile,
		val centerPageOrdinal: Int,
		val pinnedPageOrdinals: Set<Int>
	)

	private enum class DecodedCacheOwnerState {
		Active,
		ReleasePendingForEncode,
		Releasing,
		Released
	}

	private class DecodedCacheOwner<T : Any>(
		val value: T,
		var state: DecodedCacheOwnerState = DecodedCacheOwnerState.Active,
		var entryReferences: Int = 0
	)

	private class DecodedCacheEntry<T : Any>(
		val key: ReaderPageRasterKey,
		val metadata: ReaderPageRasterMetadata,
		val owner: DecodedCacheOwner<T>
	)

	private data class DecodedDiskRaster<T : Any>(
		val entry: ReaderPageRasterManifestEntry,
		val value: T
	)

	private class DecodedCacheEncodePin<T : Any>(
		val value: T
	) {
		var released = false
	}

	private sealed interface DecodedCacheEncodeAdmission<out T : Any> {
		data class Pinned<T : Any>(
			val pin: DecodedCacheEncodePin<T>
		) : DecodedCacheEncodeAdmission<T>

		data object IdentityReleasing : DecodedCacheEncodeAdmission<Nothing>
		data object CacheClosed : DecodedCacheEncodeAdmission<Nothing>
	}

	private enum class DecodedCacheAdoption {
		Adopted,
		ConsumedByStore,
		Caller,
		RetryAfterRelease
	}

	private val diskByteLimit = maxDiskBytes.coerceAtLeast(0L)
	private val uniqueDecodedBitmapLimit = maxDecodedEntries.coerceAtLeast(0)
	private val storageAvailable = !root.exists() || !Files.isSymbolicLink(root.toPath())
	private val manifest = ReaderPageRasterManifest(root)
	private val entries = linkedMapOf<String, ReaderPageRasterManifestEntry>()
	private val entryRevisions = mutableMapOf<String, Long>()
	private val decoded =
		LinkedHashMap<String, DecodedCacheEntry<T>>(0, 0.75f, true)
	private val decodedOwners = IdentityHashMap<T, DecodedCacheOwner<T>>()
	private val decodedEncodePins = IdentityHashMap<T, Int>()
	private var nextInProcessRevision = 1L
	private var protectedChapter: ReaderPageRasterChapterKey? = null
	private var encodedWindowProtection: EncodedWindowProtection? = null
	private var protectedDecodedPageIndices = emptySet<Int>()
	private var activeEncodePins = 0
	private var pendingDecodedReleases = 0
	private var decodedReleaseFailure: Throwable? = null
	private var decodedClosed = false

	init {
		if (storageAvailable) {
			root.mkdirs()
			cleanupTemporaryAndSymlinkFiles()
			manifest.read().forEach { entry ->
				val path = root.resolve(entry.rasterFileName)
				if (entry.key.schemaVersion == ReaderPageRasterSchemaVersion &&
					path.isRegularRaster(entry.byteSize)
				) {
					entries[entry.key.digest] = entry.copy(
						lastAccessEpochMillis = maxOf(
							entry.lastAccessEpochMillis,
							path.lastModified()
						)
					)
				}
			}
			val retained = retainedWithinDiskLimit(entries.values)
			entries.clear()
			retained.forEach { entry -> entries[entry.key.digest] = entry }
			deleteOrphanRasters()
			persistManifest()
			assertDiskBoundLocked()
		}
	}

	fun write(
		key: ReaderPageRasterKey,
		metadata: ReaderPageRasterMetadata,
		value: T,
		mode: ReaderPageRasterWriteMode = ReaderPageRasterWriteMode.AdoptDecoded,
		commitFence: ReaderPageRasterCommitFence =
			ReaderPageRasterCommitFence { action -> action() }
	): ReaderPageRasterWriteResult =
		withDecodedEncodeAdmission(mode, value) {
			if (!storageAvailable) {
				return@withDecodedEncodeAdmission writeFailed(
					key,
					"storage-unavailable"
				)
			}
			if (key.schemaVersion != ReaderPageRasterSchemaVersion) {
				return@withDecodedEncodeAdmission writeFailed(
					key,
					"schema-mismatch-${key.schemaVersion}"
				)
			}
			if (diskByteLimit <= 0L) {
				return@withDecodedEncodeAdmission writeFailed(
					key,
					"disk-cache-disabled"
				)
			}
			root.mkdirs()
			val temporary = root.resolve("${key.digest}.${System.nanoTime()}.tmp")
			val encoded = runCatching { codec.encode(value, temporary) }
				.getOrDefault(false)
			if (!encoded || !temporary.isFile || temporary.length() <= 0L) {
				temporary.delete()
				return@withDecodedEncodeAdmission writeFailed(key, "encode-failed")
			}
			runCatching {
				FileOutputStream(temporary, true).use { output -> output.fd.sync() }
			}.getOrElse {
				temporary.delete()
				return@withDecodedEncodeAdmission writeFailed(
					key,
					"sync-failed-${it.javaClass.simpleName}"
				)
			}
			val byteSize = temporary.length()
			if (byteSize > diskByteLimit) {
				temporary.delete()
				return@withDecodedEncodeAdmission writeFailed(
					key,
					"raster-exceeds-cache-limit",
					byteSize
				)
			}
			val contentDigest = temporary.sha256().take(16)
			val lockRequestedAt = System.nanoTime()
			val result = try {
				commitFence.commit {
					commitWrite(
						key = key,
						metadata = metadata,
						value = value,
						mode = mode,
						temporary = temporary,
						contentDigest = contentDigest,
						byteSize = byteSize,
						lockRequestedAt = lockRequestedAt
					)
				}
			} catch (failure: Throwable) {
				temporary.delete()
				throw failure
			}
			temporary.delete()
			result
		}

	private fun commitWrite(
		key: ReaderPageRasterKey,
		metadata: ReaderPageRasterMetadata,
		value: T,
		mode: ReaderPageRasterWriteMode,
		temporary: File,
		contentDigest: String,
		byteSize: Long,
		lockRequestedAt: Long
	): ReaderPageRasterWriteResult {
		val scheduled = mutableListOf<DecodedCacheOwner<T>>()
		val persisted = synchronized(this) {
			val lockWaitMillis = elapsedMillis(lockRequestedAt)
			if (lockWaitMillis >= ReaderPageRasterContentionDiagnosticMillis) {
				onDiagnostic(
					"write-lock-contention key=${key.digest.take(12)} " +
						"waitMillis=$lockWaitMillis encodedBytes=$byteSize"
				)
			}
			val target = root.resolve("${key.digest}-$contentDigest.png")
			val targetExisted = target.isFile
			val promotion = runCatching {
				readerPageRasterPromote(temporary, target)
			}
			if (promotion.isFailure) {
				temporary.delete()
				return@synchronized writeFailed(
					key,
					"promote-failed-${
						promotion.exceptionOrNull()?.javaClass?.simpleName.orEmpty()
					}",
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
			val proposed = entries.toMutableMap().apply {
				put(key.digest, entry)
			}
			val retained = retainedWithinDiskLimit(proposed.values)
			val retainedIds = retained.mapTo(mutableSetOf()) { retainedEntry ->
				retainedEntry.key.digest
			}
			if (key.digest !in retainedIds) {
				if (!targetExisted) target.delete()
				return@synchronized ReaderPageRasterWriteResult(
					persisted = false,
					ownership = ReaderPageRasterValueOwnership.Caller,
					failureReason = ReaderPageRasterWriteFailureReason.DiskCapacity
				)
			}
			if (!manifest.write(retained)) {
				if (!targetExisted) target.delete()
				return@synchronized writeFailed(
					key,
					"manifest-write-failed",
					byteSize
				)
			}

			val retiredEntries = entries.values.filter { existing ->
				existing.key.digest !in retainedIds ||
					(existing.key.digest == key.digest &&
						existing.rasterFileName != entry.rasterFileName)
			}
			retiredEntries.forEach { retired ->
				detachDecodedEntryLocked(retired.key.digest, scheduled)
				deleteEntryFile(retired)
			}
			entries.clear()
			retained.forEach { retainedEntry ->
				entries[retainedEntry.key.digest] = retainedEntry
			}
			entryRevisions.keys.retainAll(retainedIds)
			assertDiskBoundLocked()

			val revision = nextInProcessRevision++
			entryRevisions[key.digest] = revision
			ReaderPageRasterWriteResult(
				persisted = true,
				ownership = ReaderPageRasterValueOwnership.Caller,
				receipt = ReaderPageRasterWriteReceipt(
					key = key,
					rasterFileName = target.name,
					inProcessRevision = revision
				)
			)
		}
		releaseDecodedOwners(scheduled)
		if (!persisted.persisted || mode != ReaderPageRasterWriteMode.AdoptDecoded) {
			return persisted
		}
		val ownership = when (adoptDecodedValue(key, metadata, value)) {
			DecodedCacheAdoption.Adopted,
			DecodedCacheAdoption.ConsumedByStore -> ReaderPageRasterValueOwnership.Store
			DecodedCacheAdoption.Caller -> ReaderPageRasterValueOwnership.Caller
			DecodedCacheAdoption.RetryAfterRelease -> error("Decoded adoption retry escaped")
		}
		return persisted.copy(ownership = ownership)
	}

	private fun writeFailed(
		key: ReaderPageRasterKey,
		reason: String,
		encodedBytes: Long = 0L
	): ReaderPageRasterWriteResult {
		onDiagnostic(
			"write-failed key=${key.digest.take(12)} reason=$reason " +
				"encodedBytes=${encodedBytes.coerceAtLeast(0L)} " +
				"availableBytes=${root.usableSpace.coerceAtLeast(0L)}"
		)
		return ReaderPageRasterWriteResult(
			persisted = false,
			ownership = ReaderPageRasterValueOwnership.Caller
		)
	}

	fun contains(key: ReaderPageRasterKey): Boolean = contains(key, expectedMetadata = null)

	fun contains(
		key: ReaderPageRasterKey,
		expectedMetadata: ReaderPageRasterMetadata?
	): Boolean = synchronized(this) {
		if (!storageAvailable) return@synchronized false
		val entry = entries[key.digest]?.takeIf { candidate ->
			candidate.key.identity == key.identity &&
				(expectedMetadata == null || candidate.metadata == expectedMetadata)
		} ?: return@synchronized false
		val path = root.resolve(entry.rasterFileName)
		path.isFile && path.length() > 0L
	}

	/**
	 * The full key of the raster a receipt-verified capture of [issuedDigest] produced, or null.
	 * The alias lives on the manifest entry, so every removal path drops it with its raster.
	 */
	fun aliasedKey(issuedDigest: String, token: String): ReaderPageRasterKey? = synchronized(this) {
		if (!storageAvailable) return@synchronized null
		entries.values
			.firstOrNull { entry -> entry.issuedDigest == issuedDigest }
			?.takeIf { entry ->
				entry.aliasToken == token &&
					root.resolve(entry.rasterFileName).isRegularRaster(entry.byteSize)
			}
			?.key
	}

	fun recordAlias(
		realizedKey: ReaderPageRasterKey,
		issuedDigest: String,
		token: String
	): Boolean = synchronized(this) {
		if (!storageAvailable) return@synchronized false
		val entry = entries[realizedKey.digest]
			?.takeIf { candidate -> candidate.key.identity == realizedKey.identity }
			?: return@synchronized false
		if (entry.issuedDigest == issuedDigest && entry.aliasToken == token) {
			return@synchronized true
		}
		val updated = entries.mapValuesTo(
			linkedMapOf<String, ReaderPageRasterManifestEntry>()
		) { (digest, candidate) ->
			when {
				digest == realizedKey.digest ->
					candidate.copy(issuedDigest = issuedDigest, aliasToken = token)
				candidate.issuedDigest == issuedDigest ->
					candidate.copy(issuedDigest = null, aliasToken = null)
				else -> candidate
			}
		}
		if (!manifest.write(updated.values)) return@synchronized false
		entries.putAll(updated)
		true
	}

	fun read(key: ReaderPageRasterKey): ReaderPageRaster<T>? {
		if (!storageAvailable) return null
		val decodedHit = synchronized(this) {
			if (decodedClosed) return@synchronized null
			decoded[key.digest]?.takeIf { entry ->
				entry.key.identity == key.identity &&
					entry.owner.state == DecodedCacheOwnerState.Active
			}?.also { touchLocked(key.digest) }
		}
		if (decodedHit != null) {
			return ReaderPageRaster(
				decodedHit.key,
				decodedHit.metadata,
				decodedHit.owner.value
			)
		}

		val diskRaster = decodeDiskRaster(key) ?: return null
		val entry = diskRaster.entry
		val value = diskRaster.value
		if (uniqueDecodedBitmapLimit <= 0) {
			val current = synchronized(this) {
				(entries[key.digest] == entry && !decodedClosed).also { retained ->
					if (retained) touchLocked(key.digest)
				}
			}
			if (!current) {
				releaseUnadoptedDecodedValue(value)
				return null
			}
			return ReaderPageRaster(key, entry.metadata, value)
		}

		return when (
			adoptDecodedValue(
				key = key,
				metadata = entry.metadata,
				value = value,
				expectedDiskEntry = entry
			)
		) {
			DecodedCacheAdoption.Adopted -> synchronized(this) {
				decoded[key.digest]?.takeIf { current ->
					current.owner.value === value &&
						current.owner.state == DecodedCacheOwnerState.Active
				}?.also { touchLocked(key.digest) }?.let { current ->
					ReaderPageRaster(current.key, current.metadata, current.owner.value)
				}
			}
			DecodedCacheAdoption.ConsumedByStore -> null
			DecodedCacheAdoption.Caller -> {
				releaseUnadoptedDecodedValue(value)
				null
			}
			DecodedCacheAdoption.RetryAfterRelease -> error("Decoded adoption retry escaped")
		}
	}

	private fun decodeDiskRaster(key: ReaderPageRasterKey): DecodedDiskRaster<T>? {
		if (!storageAvailable) return null
		val entry = synchronized(this) {
			if (decodedClosed) return@synchronized null
			entries[key.digest]?.takeIf { candidate ->
				candidate.key.identity == key.identity
			}
		} ?: return null
		val path = root.resolve(entry.rasterFileName)
		if (!path.isRegularRaster(entry.byteSize)) {
			removeEntry(key.digest, entry)
			return null
		}
		val value = runCatching { codec.decode(path) }.getOrNull()
		if (value == null) {
			removeEntry(key.digest, entry)
			return null
		}
		return DecodedDiskRaster(entry, value)
	}

	fun <R : Any> readCopy(
		key: ReaderPageRasterKey,
		copy: (T) -> R?
	): ReaderPageRaster<R>? {
		val lockRequestedAt = System.nanoTime()
		var decodedFound = false
		val decodedCopy = synchronized(this) {
			decoded[key.digest]?.takeIf { entry ->
				entry.key.identity == key.identity &&
					entry.owner.state == DecodedCacheOwnerState.Active
			}?.let { entry ->
				decodedFound = true
				touchLocked(key.digest)
				copy(entry.owner.value)?.let { copied ->
					ReaderPageRaster(key, entry.metadata, copied)
				}
			}
		}
		if (decodedFound) return decodedCopy

		val diskRaster = decodeDiskRaster(key) ?: return null
		val result = if (uniqueDecodedBitmapLimit <= 0) {
			copyTransientDiskRaster(key, diskRaster, copy)
		} else {
			when (
				adoptDecodedValue(
					key = key,
					metadata = diskRaster.entry.metadata,
					value = diskRaster.value,
					expectedDiskEntry = diskRaster.entry
				)
			) {
				DecodedCacheAdoption.Adopted -> synchronized(this) {
					decoded[key.digest]?.takeIf { entry ->
						entry.owner.value === diskRaster.value &&
							entry.owner.state == DecodedCacheOwnerState.Active
					}?.let { entry ->
						copy(entry.owner.value)?.let { copied ->
							ReaderPageRaster(key, entry.metadata, copied)
						}
					}
				}
				DecodedCacheAdoption.ConsumedByStore -> null
				DecodedCacheAdoption.Caller ->
					copyTransientDiskRaster(key, diskRaster, copy)
				DecodedCacheAdoption.RetryAfterRelease ->
					error("Decoded adoption retry escaped")
			}
		}
		val lockWaitMillis = elapsedMillis(lockRequestedAt)
		if (lockWaitMillis >= ReaderPageRasterContentionDiagnosticMillis) {
			onDiagnostic(
				"read-copy-lock-contention key=${key.digest.take(12)} waitMillis=$lockWaitMillis"
			)
		}
		return result
	}

	private fun <R : Any> copyTransientDiskRaster(
		key: ReaderPageRasterKey,
		diskRaster: DecodedDiskRaster<T>,
		copy: (T) -> R?
	): ReaderPageRaster<R>? {
		val current = synchronized(this) {
			(entries[key.digest] == diskRaster.entry && !decodedClosed).also { retained ->
				if (retained) touchLocked(key.digest)
			}
		}
		return try {
			if (!current) null else copy(diskRaster.value)?.let { copied ->
				ReaderPageRaster(key, diskRaster.entry.metadata, copied)
			}
		} finally {
			releaseUnadoptedDecodedValue(diskRaster.value)
		}
	}

	private fun encodedWindowProtection(
		profile: ReaderPageRasterProfile,
		centerPageOrdinal: Int,
		pinnedPageOrdinals: Set<Int>
	): EncodedWindowProtection = EncodedWindowProtection(
		profile = profile,
		centerPageOrdinal = centerPageOrdinal,
		pinnedPageOrdinals = pinnedPageOrdinals.filterTo(linkedSetOf()) { it >= 0 }
	)

	fun stageEncodedWindowProtection(
		profile: ReaderPageRasterProfile,
		centerPageOrdinal: Int,
		pinnedPageOrdinals: Set<Int>
	) {
		require(centerPageOrdinal >= 0)
		synchronized(this) {
			encodedWindowProtection = encodedWindowProtection(
				profile = profile,
				centerPageOrdinal = centerPageOrdinal,
				pinnedPageOrdinals = pinnedPageOrdinals
			)
		}
	}

	fun protectEncodedWindow(
		profile: ReaderPageRasterProfile,
		centerPageOrdinal: Int,
		pinnedPageOrdinals: Set<Int>
	) {
		require(centerPageOrdinal >= 0)
		val protection = encodedWindowProtection(
			profile = profile,
			centerPageOrdinal = centerPageOrdinal,
			pinnedPageOrdinals = pinnedPageOrdinals
		)
		val scheduled = mutableListOf<DecodedCacheOwner<T>>()
		synchronized(this) {
			if (!storageAvailable || encodedWindowProtection == protection) return@synchronized
			encodedWindowProtection = protection
			val retained = retainedWithinDiskLimit(entries.values)
			if (!manifest.write(retained)) return@synchronized
			val retainedIds = retained.mapTo(mutableSetOf()) { entry -> entry.key.digest }
			entries.values
				.filter { entry -> entry.key.digest !in retainedIds }
				.forEach { entry ->
					detachDecodedEntryLocked(entry.key.digest, scheduled)
					deleteEntryFile(entry)
				}
			entries.clear()
			entries.putAll(retained.associateBy { entry -> entry.key.digest })
			entryRevisions.keys.retainAll(entries.keys)
			assertDiskBoundLocked()
		}
		releaseDecodedOwners(scheduled)
	}

	fun clearEncodedWindowProtection() {
		synchronized(this) { encodedWindowProtection = null }
	}

	fun protectChapter(chapter: ReaderPageRasterChapterKey?) {
		if (!storageAvailable) return
		val scheduled = mutableListOf<DecodedCacheOwner<T>>()
		synchronized(this) {
			if (protectedChapter == chapter) return@synchronized
			protectedChapter = chapter
			val retained = retainedWithinDiskLimit(entries.values)
			if (!manifest.write(retained)) return@synchronized
			val retainedIds = retained.mapTo(mutableSetOf()) { entry ->
				entry.key.digest
			}
			entries.values
				.filter { entry -> entry.key.digest !in retainedIds }
				.forEach { entry ->
					detachDecodedEntryLocked(entry.key.digest, scheduled)
					deleteEntryFile(entry)
				}
			entries.clear()
			entries.putAll(retained.associateBy { entry -> entry.key.digest })
			entryRevisions.keys.retainAll(entries.keys)
			assertDiskBoundLocked()
		}
		releaseDecodedOwners(scheduled)
	}

	/**
	 * Chapter retention (#57): drops [publicationHash]'s rasters two or more spine sections behind
	 * [currentSpineIndex] across every pagination, layout and quality, keeping the previous chapter.
	 * The pinned encoded window, the protected chapter and protected decoded pages always survive.
	 */
	fun discardChaptersBehind(publicationHash: String, currentSpineIndex: Int): Int {
		if (!storageAvailable) return 0
		val scheduled = mutableListOf<DecodedCacheOwner<T>>()
		val removed = synchronized(this) {
			val protection = encodedWindowProtection
			val discarded = entries.values.filter { entry ->
				val key = entry.key
				key.publicationHash == publicationHash &&
					key.spineIndex < currentSpineIndex - 1 &&
					key.chapter != protectedChapter &&
					!(decoded.containsKey(key.digest) &&
						key.visualPageOrdinal in protectedDecodedPageIndices) &&
					!(protection != null &&
						key.profile.sameRetentionScope(protection.profile) &&
						key.visualPageOrdinal in protection.pinnedPageOrdinals)
			}
			if (discarded.isEmpty()) return@synchronized 0
			val discardedIds = discarded.mapTo(mutableSetOf()) { entry -> entry.key.digest }
			val retained = entries.values.filterNot { entry -> entry.key.digest in discardedIds }
			if (!manifest.write(retained)) return@synchronized 0
			discarded.forEach { entry ->
				detachDecodedEntryLocked(entry.key.digest, scheduled)
				if (retained.none { it.rasterFileName == entry.rasterFileName }) {
					deleteEntryFile(entry)
				}
			}
			entries.clear()
			entries.putAll(retained.associateBy { entry -> entry.key.digest })
			entryRevisions.keys.retainAll(entries.keys)
			assertDiskBoundLocked()
			discarded.size
		}
		releaseDecodedOwners(scheduled)
		return removed
	}

	fun protectDecodedPageIndices(pageIndices: Set<Int>) {
		synchronized(this) {
			protectedDecodedPageIndices = pageIndices.filterTo(mutableSetOf()) { it >= 0 }
			assertDecodedBoundsLocked()
		}
	}

	fun trimDecodedToProtectedWindow(): Int {
		val scheduled = mutableListOf<DecodedCacheOwner<T>>()
		val removed = synchronized(this) {
			val digests = decoded.entries
				.filter { (_, entry) ->
					entry.key.visualPageOrdinal !in protectedDecodedPageIndices
				}
				.map { (digest, _) -> digest }
			digests.forEach { digest ->
				detachDecodedEntryLocked(digest, scheduled)
			}
			assertDecodedBoundsLocked()
			digests.size
		}
		releaseDecodedOwners(scheduled)
		return removed
	}

	fun remove(key: ReaderPageRasterKey): Boolean = remove(key, expectedMetadata = null)

	fun remove(
		key: ReaderPageRasterKey,
		expectedMetadata: ReaderPageRasterMetadata?
	): Boolean {
		val scheduled = mutableListOf<DecodedCacheOwner<T>>()
		val removed = synchronized(this) {
			val entry = entries[key.digest]?.takeIf { candidate ->
				candidate.key.identity == key.identity &&
					(expectedMetadata == null || candidate.metadata == expectedMetadata)
			} ?: return@synchronized false
			entries.remove(key.digest)
			entryRevisions.remove(key.digest)
			detachDecodedEntryLocked(key.digest, scheduled)
			deleteEntryFile(entry)
			persistManifest()
			assertDiskBoundLocked()
			true
		}
		releaseDecodedOwners(scheduled)
		return removed
	}

	fun rollbackPublication(receipt: ReaderPageRasterWriteReceipt): Boolean {
		val scheduled = mutableListOf<DecodedCacheOwner<T>>()
		val removed = synchronized(this) {
			val entry = entries[receipt.key.digest]?.takeIf { candidate ->
				candidate.key.identity == receipt.key.identity &&
					candidate.rasterFileName == receipt.rasterFileName &&
					entryRevisions[receipt.key.digest] == receipt.inProcessRevision
			} ?: return@synchronized false
			val retained = entries.values.filterNot { candidate ->
				candidate.key.digest == receipt.key.digest
			}
			check(manifest.write(retained)) {
				"Raster publication rollback manifest write failed"
			}
			entries.remove(receipt.key.digest)
			entryRevisions.remove(receipt.key.digest)
			detachDecodedEntryLocked(receipt.key.digest, scheduled)
			if (retained.none { it.rasterFileName == entry.rasterFileName }) {
				deleteEntryFile(entry)
			}
			assertDiskBoundLocked()
			true
		}
		releaseDecodedOwners(scheduled)
		return removed
	}

	fun metrics(): ReaderPageRasterCacheMetrics = synchronized(this) {
		assertDiskBoundLocked()
		assertDecodedBoundsLocked()
		ReaderPageRasterCacheMetrics(
			diskEntries = entries.size,
			diskBytes = diskBytesLocked(),
			diskByteLimit = diskByteLimit,
			decodedEntries = decoded.size,
			uniqueDecodedBitmaps = decodedOwners.size,
			uniqueDecodedBitmapLimit = uniqueDecodedBitmapLimit,
			pendingDecodedReleases = pendingDecodedReleases,
			activeEncodePins = activeEncodePins,
			encodePinnedIdentities = decodedEncodePins.size
		)
	}

	fun close() {
		val scheduled = synchronized(this) {
			if (decodedClosed) return
			check(activeEncodePins == 0) {
				"Decoded raster cache closed with active encode pins"
			}
			check(decodedEncodePins.isEmpty()) {
				"Decoded raster cache closed with pinned identities"
			}
			decodedClosed = true
			mutableListOf<DecodedCacheOwner<T>>().also { owners ->
				decoded.keys.toList().forEach { digest ->
					detachDecodedEntryLocked(digest, owners)
				}
			}
		}
		releaseDecodedOwners(scheduled)
		val failure = synchronized(this) {
			check(decoded.isEmpty())
			check(decodedOwners.isEmpty())
			check(decodedEncodePins.isEmpty())
			check(activeEncodePins == 0)
			check(pendingDecodedReleases == 0)
			decodedReleaseFailure.also { decodedReleaseFailure = null }
		}
		when (failure) {
			null -> Unit
			is RuntimeException -> throw failure
			is Error -> throw failure
			else -> throw IllegalStateException(
				"Unexpected checked decoded raster release failure",
				failure
			)
		}
	}

	internal fun pathFor(key: ReaderPageRasterKey): File = synchronized(this) {
		entries[key.digest]?.let { entry -> root.resolve(entry.rasterFileName) }
			?: root.resolve("${key.digest}.png")
	}

	internal fun manifestPath(): File = manifest.file

	private fun scheduleDecodedOwnerReleaseLocked(
		owner: DecodedCacheOwner<T>,
		scheduled: MutableList<DecodedCacheOwner<T>>
	) {
		check(owner.entryReferences == 0)
		check(
			owner.state == DecodedCacheOwnerState.Active ||
				owner.state == DecodedCacheOwnerState.ReleasePendingForEncode
		)
		if ((decodedEncodePins[owner.value] ?: 0) > 0) {
			owner.state = DecodedCacheOwnerState.ReleasePendingForEncode
			onOwnershipMutated()
			return
		}
		owner.state = DecodedCacheOwnerState.Releasing
		pendingDecodedReleases += 1
		scheduled += owner
		onOwnershipMutated()
	}

	private fun detachDecodedEntryLocked(
		digest: String,
		scheduled: MutableList<DecodedCacheOwner<T>>
	) {
		val entry = decoded.remove(digest) ?: return
		val owner = entry.owner
		check(owner.state == DecodedCacheOwnerState.Active)
		check(owner.entryReferences > 0)
		owner.entryReferences -= 1
		if (owner.entryReferences == 0) {
			scheduleDecodedOwnerReleaseLocked(owner, scheduled)
		} else {
			onOwnershipMutated()
		}
		assertDecodedBoundsLocked()
	}

	private fun acquireDecodedEncodePin(
		value: T
	): DecodedCacheEncodeAdmission<T> = synchronized(this) {
		if (decodedClosed) {
			return@synchronized DecodedCacheEncodeAdmission.CacheClosed
		}
		when (decodedOwners[value]?.state) {
			DecodedCacheOwnerState.Releasing ->
				return@synchronized DecodedCacheEncodeAdmission.IdentityReleasing
			DecodedCacheOwnerState.Released ->
				error("Released decoded owner remained indexed")
			DecodedCacheOwnerState.Active,
			DecodedCacheOwnerState.ReleasePendingForEncode,
			null -> Unit
		}
		decodedEncodePins[value] = (decodedEncodePins[value] ?: 0) + 1
		activeEncodePins += 1
		assertDecodedBoundsLocked()
		onOwnershipMutated()
		DecodedCacheEncodeAdmission.Pinned(DecodedCacheEncodePin(value))
	}

	private fun requirePersistOnlyIdentityAvailable(value: T) {
		synchronized(this) {
			check(!decodedClosed) { "Decoded raster cache is closed" }
			check(decodedOwners[value] == null) {
				"PersistOnly value is already owned by the decoded cache"
			}
			check(decodedEncodePins[value] == null) {
				"PersistOnly value is adopt-pinned by the decoded cache"
			}
		}
	}

	private fun ownershipForPinnedFailure(
		pin: DecodedCacheEncodePin<T>
	): ReaderPageRasterValueOwnership = synchronized(this) {
		check(!pin.released)
		if (decodedOwners[pin.value] != null) {
			ReaderPageRasterValueOwnership.Store
		} else {
			ReaderPageRasterValueOwnership.Caller
		}
	}

	private fun releaseDecodedEncodePin(pin: DecodedCacheEncodePin<T>) {
		val scheduled = mutableListOf<DecodedCacheOwner<T>>()
		synchronized(this) {
			check(!pin.released) { "Decoded encode pin released twice" }
			pin.released = true
			val count = checkNotNull(decodedEncodePins[pin.value])
			if (count == 1) decodedEncodePins.remove(pin.value)
			else decodedEncodePins[pin.value] = count - 1
			activeEncodePins -= 1
			check(activeEncodePins >= 0)
			val owner = decodedOwners[pin.value]
			if (owner?.state == DecodedCacheOwnerState.ReleasePendingForEncode &&
				owner.entryReferences == 0 &&
				decodedEncodePins[pin.value] == null
			) {
				scheduleDecodedOwnerReleaseLocked(owner, scheduled)
			} else {
				onOwnershipMutated()
			}
			assertDecodedBoundsLocked()
		}
		releaseDecodedOwners(scheduled)
	}

	private fun withDecodedEncodeAdmission(
		mode: ReaderPageRasterWriteMode,
		value: T,
		transaction: () -> ReaderPageRasterWriteResult
	): ReaderPageRasterWriteResult {
		if (mode == ReaderPageRasterWriteMode.PersistOnly) {
			requirePersistOnlyIdentityAvailable(value)
			return transaction()
		}

		val pin = when (val admission = acquireDecodedEncodePin(value)) {
			is DecodedCacheEncodeAdmission.Pinned -> admission.pin
			DecodedCacheEncodeAdmission.IdentityReleasing -> {
				onDiagnostic("write-failed reason=encode-identity-releasing")
				return ReaderPageRasterWriteResult(
					persisted = false,
					ownership = ReaderPageRasterValueOwnership.Store,
					failureReason =
						ReaderPageRasterWriteFailureReason.EncodeIdentityReleasing
				)
			}
			DecodedCacheEncodeAdmission.CacheClosed -> {
				return ReaderPageRasterWriteResult(
					persisted = false,
					ownership = ReaderPageRasterValueOwnership.Caller
				)
			}
		}

		return try {
			val result = transaction()
			if (!result.persisted &&
				result.ownership == ReaderPageRasterValueOwnership.Caller &&
				ownershipForPinnedFailure(pin) == ReaderPageRasterValueOwnership.Store
			) {
				result.copy(ownership = ReaderPageRasterValueOwnership.Store)
			} else {
				result
			}
		} finally {
			releaseDecodedEncodePin(pin)
		}
	}

	private fun attachDecodedEntryLocked(
		key: ReaderPageRasterKey,
		metadata: ReaderPageRasterMetadata,
		owner: DecodedCacheOwner<T>
	) {
		check(owner.state == DecodedCacheOwnerState.Active)
		check(decoded[key.digest] == null)
		decoded[key.digest] = DecodedCacheEntry(key, metadata, owner)
		owner.entryReferences += 1
		assertDecodedBoundsLocked()
		onOwnershipMutated()
	}

	private fun adoptDecodedValue(
		key: ReaderPageRasterKey,
		metadata: ReaderPageRasterMetadata,
		value: T,
		expectedDiskEntry: ReaderPageRasterManifestEntry? = null
	): DecodedCacheAdoption {
		if (uniqueDecodedBitmapLimit <= 0) return DecodedCacheAdoption.Caller

		while (true) {
			val scheduled = mutableListOf<DecodedCacheOwner<T>>()
			val decision = synchronized(this) {
				val durableEntry = entries[key.digest]
				if (decodedClosed ||
					durableEntry?.key?.identity != key.identity ||
					(expectedDiskEntry != null && durableEntry != expectedDiskEntry)
				) {
					return@synchronized DecodedCacheAdoption.Caller
				}
				val current = decoded[key.digest]
				val existingOwner = decodedOwners[value]
				if (current?.owner === existingOwner &&
					existingOwner?.state == DecodedCacheOwnerState.Active
				) {
					decoded[key.digest] =
						DecodedCacheEntry(key, metadata, existingOwner)
					return@synchronized DecodedCacheAdoption.Adopted
				}

				if (current != null) {
					detachDecodedEntryLocked(key.digest, scheduled)
					return@synchronized DecodedCacheAdoption.RetryAfterRelease
				}

				when (existingOwner?.state) {
					DecodedCacheOwnerState.Active -> {
						attachDecodedEntryLocked(key, metadata, existingOwner)
						DecodedCacheAdoption.Adopted
					}
					DecodedCacheOwnerState.ReleasePendingForEncode -> {
						check(existingOwner.entryReferences == 0)
						check(decodedEncodePins[value] != null)
						existingOwner.state = DecodedCacheOwnerState.Active
						attachDecodedEntryLocked(key, metadata, existingOwner)
						DecodedCacheAdoption.Adopted
					}
					DecodedCacheOwnerState.Releasing ->
						DecodedCacheAdoption.ConsumedByStore
					DecodedCacheOwnerState.Released ->
						error("Released decoded owner remained indexed")
					null -> {
						if (decodedOwners.size < uniqueDecodedBitmapLimit) {
							val owner = DecodedCacheOwner(value)
							decodedOwners[value] = owner
							attachDecodedEntryLocked(key, metadata, owner)
							DecodedCacheAdoption.Adopted
						} else {
							val eviction = decoded.entries.firstOrNull {
								(_, candidate) ->
								candidate.key.visualPageOrdinal !in
									protectedDecodedPageIndices
							}
							if (eviction == null) {
								DecodedCacheAdoption.Caller
							} else {
								detachDecodedEntryLocked(eviction.key, scheduled)
								DecodedCacheAdoption.RetryAfterRelease
							}
						}
					}
				}
			}
			releaseDecodedOwners(scheduled)
			if (decision != DecodedCacheAdoption.RetryAfterRelease) {
				return decision
			}
		}
	}

	private fun recordDecodedReleaseFailureLocked(failure: Throwable) {
		val first = decodedReleaseFailure
		if (first == null) {
			decodedReleaseFailure = failure
		} else if (failure !== first) {
			first.addSuppressed(failure)
		}
	}

	private fun releaseDecodedOwners(owners: List<DecodedCacheOwner<T>>) {
		owners.forEach { owner ->
			var callbackFailure: Throwable? = null
			try {
				codec.release(owner.value)
			} catch (failure: Throwable) {
				callbackFailure = failure
			} finally {
				synchronized(this) {
					callbackFailure?.let(::recordDecodedReleaseFailureLocked)
					check(owner.state == DecodedCacheOwnerState.Releasing)
					check(decodedOwners.remove(owner.value) === owner)
					owner.state = DecodedCacheOwnerState.Released
					pendingDecodedReleases -= 1
					check(pendingDecodedReleases >= 0)
					assertDecodedBoundsLocked()
					onOwnershipMutated()
				}
			}
		}
	}

	private fun releaseUnadoptedDecodedValue(value: T) {
		try {
			codec.release(value)
		} catch (failure: Throwable) {
			synchronized(this) { recordDecodedReleaseFailureLocked(failure) }
		}
	}

	private fun retainedWithinDiskLimit(
		candidates: Collection<ReaderPageRasterManifestEntry>
	): List<ReaderPageRasterManifestEntry> {
		val protection = encodedWindowProtection
		val ordered = if (protection == null) {
			candidates
				.filter { entry -> entry.key.chapter == protectedChapter }
				.sortedByDescending { entry -> entry.lastAccessEpochMillis }
				.plus(
					candidates
						.filterNot { entry -> entry.key.chapter == protectedChapter }
						.sortedByDescending { entry -> entry.lastAccessEpochMillis }
				)
		} else {
			val activeProfile = candidates.filter { entry ->
				entry.key.profile.sameRetentionScope(protection.profile)
			}
			val pinned = activeProfile
				.filter { entry ->
					entry.key.visualPageOrdinal in protection.pinnedPageOrdinals
				}
				.sortedBy { entry ->
					kotlin.math.abs(entry.key.visualPageOrdinal - protection.centerPageOrdinal)
				}
			val forward = activeProfile
				.filter { entry ->
					entry.key.visualPageOrdinal > protection.centerPageOrdinal &&
						entry.key.visualPageOrdinal !in protection.pinnedPageOrdinals
				}
				.sortedBy { entry -> entry.key.visualPageOrdinal }
			val behind = activeProfile
				.filter { entry ->
					entry.key.visualPageOrdinal <= protection.centerPageOrdinal &&
						entry.key.visualPageOrdinal !in protection.pinnedPageOrdinals
				}
				.sortedByDescending { entry -> entry.key.visualPageOrdinal }
			val otherProfiles = candidates
				.filterNot { entry -> entry.key.profile.sameRetentionScope(protection.profile) }
				.sortedByDescending { entry -> entry.lastAccessEpochMillis }
			pinned + forward + behind + otherProfiles
		}
		val retained = mutableListOf<ReaderPageRasterManifestEntry>()
		var bytes = 0L
		for (entry in ordered) {
			val entryBytes = entry.byteSize.coerceAtLeast(0L)
			val fits = entryBytes > 0L && entryBytes <= diskByteLimit - bytes
			if (!fits) {
				if (protection != null) break
				continue
			}
			retained += entry
			bytes += entryBytes
		}
		return retained
	}

	private fun touchLocked(digest: String) {
		val current = entries[digest] ?: return
		val updated = current.copy(lastAccessEpochMillis = clock())
		entries[digest] = updated
		root.resolve(current.rasterFileName).setLastModified(updated.lastAccessEpochMillis)
	}

	private fun removeEntry(
		digest: String,
		expected: ReaderPageRasterManifestEntry? = null
	) {
		val scheduled = mutableListOf<DecodedCacheOwner<T>>()
		synchronized(this) {
			val entry = entries[digest] ?: return@synchronized
			if (expected != null && entry != expected) return@synchronized
			entries.remove(digest)
			entryRevisions.remove(digest)
			detachDecodedEntryLocked(digest, scheduled)
			deleteEntryFile(entry)
			persistManifest()
			assertDiskBoundLocked()
		}
		releaseDecodedOwners(scheduled)
	}

	private fun assertDiskBoundLocked() {
		check(diskBytesLocked() <= diskByteLimit) {
			"Raster disk cache byte capacity exceeded"
		}
	}

	private fun diskBytesLocked(): Long =
		entries.values.sumOf { entry -> entry.byteSize.coerceAtLeast(0L) }

	private fun assertDecodedBoundsLocked() {
		check(decodedOwners.size <= uniqueDecodedBitmapLimit) {
			"Decoded raster identity capacity exceeded"
		}
		check(pendingDecodedReleases in 0..decodedOwners.size)
		check(activeEncodePins >= 0)
		check(activeEncodePins == decodedEncodePins.values.sum())
		decoded.values.forEach { entry ->
			check(entry.owner.state == DecodedCacheOwnerState.Active)
			check(entry.owner.entryReferences > 0)
			check(decodedOwners[entry.owner.value] === entry.owner)
		}
	}

	private fun deleteEntryFile(entry: ReaderPageRasterManifestEntry) {
		val path = root.resolve(entry.rasterFileName)
		if (!Files.isSymbolicLink(path.toPath())) path.delete()
	}

	private fun cleanupTemporaryAndSymlinkFiles() {
		root.listFiles().orEmpty().forEach { child ->
			if (child.name.endsWith(".tmp") || Files.isSymbolicLink(child.toPath())) {
				child.delete()
			}
		}
	}

	private fun deleteOrphanRasters() {
		val registered = entries.values.mapTo(mutableSetOf()) { entry ->
			entry.rasterFileName
		}
		root.listFiles().orEmpty()
			.filter { file -> file.extension == "png" && file.name !in registered }
			.forEach(File::delete)
	}

	private fun persistManifest(): Boolean = manifest.write(entries.values)

	private fun File.isRegularRaster(expectedSize: Long): Boolean =
		isFile &&
			!Files.isSymbolicLink(toPath()) &&
			length() == expectedSize &&
			expectedSize > 0L

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
		return digest.digest().joinToString(separator = "") { byte ->
			"%02x".format(byte.toInt() and 0xff)
		}
	}

	private fun elapsedMillis(startedAtNanos: Long): Long =
		((System.nanoTime() - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)
}

private const val ReaderPageRasterContentionDiagnosticMillis = 100L

internal object ReaderAndroidPageRasterCodec : ReaderPageRasterCodec<Bitmap> {
	override fun encode(value: Bitmap, target: File): Boolean =
		FileOutputStream(target).use { output ->
			value.compress(Bitmap.CompressFormat.PNG, 100, output)
		}

	override fun decode(source: File): Bitmap? = BitmapFactory.decodeFile(source.path)

	override fun release(value: Bitmap) {
		if (!value.isRecycled) value.recycle()
	}
}
