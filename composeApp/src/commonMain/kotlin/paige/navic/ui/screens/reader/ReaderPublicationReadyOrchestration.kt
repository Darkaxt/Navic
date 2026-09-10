package paige.navic.ui.screens.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.data.remote.bindery.binderyApiKeyHeaders
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.domain.repositories.BinderyWordSyncReference
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.WhispersyncSidecar
import paige.navic.reader.WhispersyncSyncLogTag
import paige.navic.reader.WordSyncPublicationVerifier
import paige.navic.shared.AudiobookPlaybackManager
import paige.navic.ui.core.EnrichmentRequestOwner
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.bindery.binderyAudiobookPlaybackPlan
import paige.navic.ui.screens.bindery.binderyAudiobookResumeProgressForWhispersyncReader
import paige.navic.util.core.Logger

internal data class ReaderPublicationReadyOrchestrationRequest(
	val reader: Screen.Reader,
	val attachment: ReaderWhispersyncLaunchAttachment,
	val wordSyncVerifier: WordSyncPublicationVerifier?,
	val needsWordSyncRecovery: Boolean,
	val audiobookIdentity: String?,
	val playbackSpeed: Float,
	val callbacks: ReaderPublicationReadyOrchestrationCallbacks
)

internal data class ReaderPublicationReadyOrchestrationCallbacks(
	val onWordSyncRecovered: (WordSyncPublicationVerifier, BinderyWordSyncReference) -> Unit,
	val onSidecarLoaded: (WhispersyncSidecar) -> Unit,
	val onSidecarUnavailable: () -> Unit,
	val onPlaybackPlanChanged: (ReadaloudPlaybackPlan?) -> Unit,
	val onAudiobookUnavailable: () -> Unit
)

internal class ReaderPublicationReadyOrchestration(
	private val coroutineScope: CoroutineScope,
	private val binderyRepository: BinderyRepository,
	private val audiobookPlaybackManager: AudiobookPlaybackManager,
	private val preferenceManager: PreferenceManager,
	private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
	private val bookSyncRequestOwner = EnrichmentRequestOwner()
	private val manifestRequestOwner = EnrichmentRequestOwner()
	private var currentReader: Screen.Reader? = null

	fun attach(reader: Screen.Reader) {
		currentReader = reader
	}

	fun detach(reader: Screen.Reader) {
		if (currentReader == reader) currentReader = null
		bookSyncRequestOwner.cancel()
		manifestRequestOwner.cancel()
	}

	fun close() {
		currentReader = null
		bookSyncRequestOwner.cancel()
		manifestRequestOwner.cancel()
	}

	fun handle(request: ReaderPublicationReadyOrchestrationRequest) {
		val reader = request.reader
		val attachment = request.attachment
		val callbacks = request.callbacks
		val verifier = request.wordSyncVerifier
		if (currentReader != reader) return
		if (request.needsWordSyncRecovery && verifier != null) {
			bookSyncRequestOwner.launch(coroutineScope) {
				val bookSyncResult = withContext(ioDispatcher) {
					binderyRepository.getBookSync(
						bookId = reader.bookId,
						forceRefresh = true
					)
				}
				bookSyncResult.rethrowCancellation()
				bookSyncResult.fold(
					onSuccess = { bookSync ->
						val resolution = bookSync.wordSyncReferenceResolutionForLaunch(
							bookId = reader.bookId,
							attachment = attachment
						)
						val recoveredReference = resolution.reference
						if (recoveredReference == null) {
							Logger.w(
								WhispersyncSyncLogTag,
								"WordSync reference state=unavailable matched=false active=false " +
									"reason=${resolution.reason.logValue} count=${resolution.candidateCount}"
							)
						} else {
							bookSyncRequestOwner.commit(reader) {
								callbacks.onWordSyncRecovered(verifier, recoveredReference)
							}
							Logger.i(
								WhispersyncSyncLogTag,
								"WordSync reference state=resolved matched=true active=true " +
									"reason=book-sync count=1"
							)
						}
					},
					onFailure = {
						Logger.w(
							WhispersyncSyncLogTag,
							"WordSync reference state=unavailable matched=false active=false " +
								"reason=load-failed count=0"
						)
					}
				)
			}
		}

		manifestRequestOwner.launch(coroutineScope) {
			val sidecarResult = withContext(ioDispatcher) {
				binderyRepository.getWhispersyncSidecar(attachment.sidecarPath)
			}
			sidecarResult.rethrowCancellation()
			val sidecar = sidecarResult.getOrNull()
			if (!manifestRequestOwner.commit(reader) {
				if (sidecar != null) callbacks.onSidecarLoaded(sidecar)
				else callbacks.onSidecarUnavailable()
			}) return@launch
			if (!manifestRequestOwner.commit(reader) {}) return@launch

			val manifestResult = withContext(ioDispatcher) {
				binderyRepository.getWhispersyncAudiobookManifest(
					bookId = reader.bookId,
					audiobookId = attachment.audiobookId,
					audiobookBookFileId = attachment.audiobookBookFileId,
					audiobookManifestHref = sidecar?.audiobookManifestHref
				)
			}
			manifestResult.rethrowCancellation()
			manifestResult.fold(
				onSuccess = { manifest ->
					val requestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
					val versionIdentity = request.audiobookIdentity ?: attachment.audiobookBookFileId
					val resumeProgress = binderyAudiobookResumeProgressForWhispersyncReader(
						audiobookProgressJson = preferenceManager.binderyAudiobookProgressJson,
						companionProgressJson = preferenceManager.binderyWhispersyncCompanionProgressJson,
						bookId = reader.bookId,
						versionRowId = versionIdentity,
						manifest = manifest,
						audiobookBookFileId = attachment.audiobookBookFileId
					)
					val playbackPlan = binderyAudiobookPlaybackPlan(
						manifest = manifest,
						versionRowId = versionIdentity,
						opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
						requestHeaders = requestHeaders,
						resumeProgress = resumeProgress,
						progressBookId = reader.bookId,
						audiobookBookFileId = attachment.audiobookBookFileId
					)
					if (!manifestRequestOwner.commit(reader) {
						callbacks.onPlaybackPlanChanged(playbackPlan)
					}) return@fold
					if (!manifestRequestOwner.commit(reader) {
						audiobookPlaybackManager.load(
							playbackPlan = playbackPlan,
							bookId = reader.bookId,
							bookTitle = attachment.audiobookTitle ?: reader.title,
							versionRowId = versionIdentity,
							coverUrl = null,
							coverCacheKey = null,
							imageRequestHeaders = requestHeaders,
							playWhenReady = false
						)
					}) return@fold
					manifestRequestOwner.commit(reader) {
						audiobookPlaybackManager.dispatch(
							ReaderReadaloudPlaybackCommand.SetSpeed(request.playbackSpeed)
						)
					}
				},
				onFailure = {
					if (!manifestRequestOwner.commit(reader) {
						callbacks.onPlaybackPlanChanged(null)
					}) return@fold
					manifestRequestOwner.commit(reader) {
						callbacks.onAudiobookUnavailable()
					}
				}
			)
		}
	}

	private suspend fun EnrichmentRequestOwner.commit(
		reader: Screen.Reader,
		block: () -> Unit
	): Boolean {
		var admitted = false
		val owned = commit {
			if (currentReader == reader) {
				admitted = true
				block()
			}
		}
		return owned && admitted
	}
}

internal fun Result<*>.rethrowCancellation() {
	(exceptionOrNull() as? CancellationException)?.let { throw it }
}
