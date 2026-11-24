package paige.navic.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import paige.navic.domain.repositories.BinderyBookSync
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.reader.ReaderIllustrationStorage
import paige.navic.reader.canonicalReaderResourceHref
import paige.navic.reader.prefetchReaderIllustrations
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.bindery.binderyBookVersionRows
import paige.navic.ui.screens.bindery.binderyPrefetchReaderDestinations
import paige.navic.ui.screens.reader.whispersyncLaunchAttachment
import paige.navic.ui.screens.reader.wordSyncReferenceForLaunch
import paige.navic.util.core.Logger
import kotlin.coroutines.cancellation.CancellationException

private const val BinderyReaderPrefetchTag = "BinderyReaderPrefetch"

/** Puts a reader publication (and its shell cover) into the persistent on-disk reader cache. */
fun interface ReaderPublicationPrefetch {
	suspend fun ensureOnDisk(reader: Screen.Reader)
}

/**
 * Silently pre-downloads what a Bindery reader open would otherwise wait on: the publication and cover,
 * the Whispersync sidecar and audiobook manifest, the WordSync index and chapters, then (best-effort)
 * chapter illustration plates. Audio still streams
 * and pages are never pre-rendered. One serial worker; the batch stops at the first failure because every
 * failed Bindery request marks the integration down.
 */
class BinderyReaderPrefetcher(
	private val repository: BinderyRepository,
	private val publication: ReaderPublicationPrefetch,
	private val canPrefetch: (requireUnmetered: Boolean) -> Boolean,
	/** Where chapter illustration plates are stored (#74); null skips them. */
	private val illustrations: ReaderIllustrationStorage? = null,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
	private val lock = Mutex()
	private val queue = ArrayDeque<PrefetchItem>()
	private val knownKeys = mutableSetOf<String>()
	private var worker: Job? = null

	/** High-intent readers (the user is looking at them) jump the queue and may use metered networks. */
	suspend fun enqueue(readers: List<Screen.Reader>, highIntent: Boolean) {
		val requireUnmetered = !highIntent
		if (readers.isEmpty() || !canPrefetch(requireUnmetered)) return
		val items = readers.map { reader -> PrefetchItem.Reader(reader, requireUnmetered) }
		lock.withLock {
			val fresh = items.filter { item -> knownKeys.add(item.key) }
			if (highIntent) {
				queue.addAll(0, fresh)
			} else {
				queue.addAll(fresh)
			}
			startWorkerLocked()
		}
	}

	/** Books seen only in a listing: metadata is loaded like the book screen does, unmetered networks only. */
	suspend fun enqueueBooks(
		bookIds: List<String>,
		opdsBaseUrl: String,
		languageFilter: String?,
		aspectRatio: Double?
	) {
		if (bookIds.isEmpty() || !canPrefetch(true)) return
		val items = bookIds
			.mapNotNull { bookId -> bookId.trim().takeIf { it.isNotEmpty() } }
			.map { bookId -> PrefetchItem.Book(bookId, opdsBaseUrl, languageFilter, aspectRatio) }
		lock.withLock {
			queue.addAll(items.filter { item -> knownKeys.add(item.key) })
			startWorkerLocked()
		}
	}

	private fun startWorkerLocked() {
		if (worker != null || queue.isEmpty()) return
		worker = scope.launch { drain() }
	}

	private suspend fun drain() {
		while (true) {
			val item = lock.withLock {
				queue.removeFirstOrNull() ?: run {
					worker = null
					null
				}
			} ?: return
			if (!canPrefetch(item.requireUnmetered)) {
				lock.withLock { knownKeys.remove(item.key) }
				continue
			}
			val completed = try {
				when (item) {
					is PrefetchItem.Reader -> prefetchReader(item.reader)
					is PrefetchItem.Book -> prefetchBook(item)
				}
			} catch (cancellation: CancellationException) {
				throw cancellation
			} catch (error: Throwable) {
				Logger.w(
					BinderyReaderPrefetchTag,
					"Prefetch failed error=${error::class.simpleName ?: "unknown"}"
				)
				false
			}
			if (!completed) abandonBatch(item)
		}
	}

	private suspend fun abandonBatch(failed: PrefetchItem) {
		val dropped = lock.withLock {
			knownKeys.remove(failed.key)
			queue.forEach { item -> knownKeys.remove(item.key) }
			queue.size.also { queue.clear() }
		}
		Logger.w(
			BinderyReaderPrefetchTag,
			"Prefetch batch stopped at first failure bookId=${failed.bookId} dropped=$dropped"
		)
	}

	private suspend fun prefetchBook(item: PrefetchItem.Book): Boolean {
		val bookId = item.bookId
		val manifest = repository.getManifest(bookId).getOrElse { return false }
		val audiobooks = repository.getAudiobookVersions(bookId).getOrElse { emptyList() }
		val resources = repository.getBookResources(bookId).getOrElse { BinderyResourceCatalog(title = "Resources") }
		val sync = repository.getBookSync(bookId).getOrElse {
			manifest.sync ?: BinderyBookSync(bookId = bookId.toLongOrNull())
		}
		val rows = binderyBookVersionRows(
			manifest = manifest,
			resourceCatalog = resources,
			languageFilter = item.languageFilter,
			bookId = bookId,
			audiobookVersions = audiobooks,
			bookSync = sync
		)
		val readers = binderyPrefetchReaderDestinations(
			rows = rows,
			bookId = bookId,
			bookTitle = manifest.title,
			opdsBaseUrl = item.opdsBaseUrl,
			fullscreenCoverTargetAspectRatio = item.aspectRatio
		)
		for (reader in readers) {
			val key = reader.prefetchKey()
			if (!lock.withLock { knownKeys.add(key) }) continue
			if (!prefetchReader(reader)) {
				lock.withLock { knownKeys.remove(key) }
				return false
			}
		}
		return true
	}

	/**
	 * Mirrors what ReaderScreen loads on open, in the same order, so every step is served from disk later.
	 * Illustration plates come last and are best-effort: they never fail the item or the batch.
	 */
	private suspend fun prefetchReader(reader: Screen.Reader): Boolean {
		if (!prefetchReaderContent(reader)) return false
		illustrations?.let { storage -> prefetchReaderIllustrations(repository, storage, reader.bookId) }
		return true
	}

	private suspend fun prefetchReaderContent(reader: Screen.Reader): Boolean {
		if (!ensurePublicationOnDisk(reader)) return false
		val attachment = reader.whispersyncLaunchAttachment()
		if (attachment == null) {
			Logger.i(BinderyReaderPrefetchTag, "Prefetched publication bookId=${reader.bookId} whispersync=none")
			return true
		}
		val sidecar = repository.getWhispersyncSidecar(attachment.sidecarPath).getOrElse { return false }
		repository.getWhispersyncAudiobookManifest(
			bookId = reader.bookId,
			audiobookId = attachment.audiobookId,
			audiobookBookFileId = attachment.audiobookBookFileId,
			audiobookManifestHref = sidecar.audiobookManifestHref
		).getOrElse { return false }
		val reference = attachment.wordSync?.takeIf { reference ->
			attachment.audiobookBookFileId.toLongOrNull() == reference.identity.audiobookBookFileId
		} ?: repository.getBookSync(reader.bookId)
			.getOrElse { return false }
			.wordSyncReferenceForLaunch(bookId = reader.bookId, attachment = attachment)
		if (reference == null) {
			Logger.i(BinderyReaderPrefetchTag, "Prefetched publication bookId=${reader.bookId} whispersync=sidecar")
			return true
		}
		val index = repository.getWordSyncIndex(
			identity = reference.identity,
			discovery = reference.discovery
		).getOrElse { return false }
		for (chapter in index.chapters) {
			repository.getWordSyncChapter(identity = reference.identity, chapter = chapter)
				.getOrElse { return false }
		}
		Logger.i(
			BinderyReaderPrefetchTag,
			"Prefetched publication bookId=${reader.bookId} whispersync=wordsync chapters=${index.chapters.size}"
		)
		return true
	}

	private suspend fun ensurePublicationOnDisk(reader: Screen.Reader): Boolean =
		try {
			publication.ensureOnDisk(reader)
			true
		} catch (cancellation: CancellationException) {
			throw cancellation
		} catch (error: Throwable) {
			Logger.w(
				BinderyReaderPrefetchTag,
				"Prefetch publication failed bookId=${reader.bookId} error=${error::class.simpleName ?: "unknown"}"
			)
			false
		}

	private sealed interface PrefetchItem {
		val key: String
		val bookId: String
		val requireUnmetered: Boolean

		class Reader(val reader: Screen.Reader, override val requireUnmetered: Boolean) : PrefetchItem {
			override val key: String = reader.prefetchKey()
			override val bookId: String = reader.bookId
		}

		class Book(
			override val bookId: String,
			val opdsBaseUrl: String,
			val languageFilter: String?,
			val aspectRatio: Double?
		) : PrefetchItem {
			override val key: String = "book|$bookId"
			override val requireUnmetered: Boolean = true
		}
	}
}

private fun Screen.Reader.prefetchKey(): String =
	listOf(
		bookId.trim(),
		canonicalReaderResourceHref(resourceHref) ?: resourceHref.trim(),
		fullscreenCoverUrl.orEmpty(),
		whispersyncSidecarUrl.orEmpty()
	).joinToString(separator = "|")
