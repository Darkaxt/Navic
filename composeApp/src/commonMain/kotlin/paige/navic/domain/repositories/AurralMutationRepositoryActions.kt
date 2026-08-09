package paige.navic.domain.repositories

import paige.navic.data.remote.aurral.*
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.IntegrationService
import paige.navic.util.core.Logger

private const val TAG = "AurralMutationRepositoryActions"

internal class AurralMutationRepositoryActions(
	private val preferenceManager: PreferenceManager,
	private val apiClient: AurralApiClient,
	private val auth: AurralRepositoryAuth,
	private val localState: AurralRepositoryLocalState,
	private val confirmationQueueManager: AurralConfirmationQueueManager,
	private val metadataCache: AurralMetadataCache,
	private val nowMillis: () -> Long,
	private val confirmationWorkerEnabled: Boolean
) {
	suspend fun cancelAcquisitionRequest(item: AurralAcquisitionQueueItem): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val target = aurralAcquisitionDeleteTarget(item)
			?: return Result.failure(IllegalStateException("Aurral request delete target is unavailable."))
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)

		return runCatching {
			apiClient.cancelAcquisitionRequest(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				target = target
			)
			localState.removeAlbumRequest(item.albumMbid, item.albumName)
			clearAurralMetadataCache(baseUrl)
			Unit
		}.onFailure { error ->
			Logger.w(TAG, "Aurral acquisition cancel failed for ${item.albumName}", error)
		}.recordAurralAvailability()
	}

	suspend fun retryAcquisitionRequest(item: AurralAcquisitionQueueItem): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val albumMbid = item.albumMbid?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album MusicBrainz ID is required."))
		val artistMbid = item.artistMbid?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val albumName = item.albumName.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album title is required."))
		val artistName = item.artistName.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist name is required."))
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)
		val payload = AurralAlbumRequestPayload(
			albumMbid = albumMbid,
			albumName = albumName,
			artistMbid = artistMbid,
			artistName = artistName,
			triggerSearch = true
		)

		return runCatching {
			apiClient.requestAlbum(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				payload = payload
			)
			localState.rememberOptimisticAlbumRequest(payload)
			clearAurralMetadataCache(baseUrl)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral acquisition retry failed for $albumName", error)
		}.recordAurralAvailability()
	}

	suspend fun requestAlbum(
		artist: DomainArtist,
		releaseGroup: AurralReleaseGroup
	): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val artistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val albumMbid = releaseGroup.id.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album MusicBrainz ID is required."))
		val albumName = releaseGroup.title.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album title is required."))
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)
		val payload = AurralAlbumRequestPayload(
			albumMbid = albumMbid,
			albumName = albumName,
			artistMbid = artistMbid,
			artistName = artist.name,
			triggerSearch = true
		)

		return runCatching {
			apiClient.requestAlbum(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				payload = payload
			)
			localState.rememberOptimisticAlbumRequest(payload)
			clearAurralMetadataCache(baseUrl)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral album request failed for $albumName", error)
		}.recordAurralAvailability()
	}

	suspend fun requestAlbum(album: AurralAlbumSearchItem): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val albumMbid = album.id.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album MusicBrainz ID is required."))
		val albumName = album.title.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Album title is required."))
		val artistMbid = album.artistMbid.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val artistName = album.artistName.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist name is required."))
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)
		val payload = AurralAlbumRequestPayload(
			albumMbid = albumMbid,
			albumName = albumName,
			artistMbid = artistMbid,
			artistName = artistName,
			triggerSearch = true
		)

		return runCatching {
			apiClient.requestAlbum(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				payload = payload
			)
			localState.rememberOptimisticAlbumRequest(payload)
			clearAurralMetadataCache(baseUrl)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral album request failed for $albumName", error)
		}.recordAurralAvailability()
	}

	suspend fun monitorArtist(artist: DomainArtist): Result<Unit> =
		setArtistMonitoring(artist, monitored = true)

	suspend fun setArtistMonitoring(
		artist: DomainArtist,
		monitored: Boolean
	): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val artistMbid = artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist name is required."))
		return setArtistMonitoringByAurralId(artistMbid, artistName, monitored)
	}

	suspend fun monitorDiscoveredArtist(artist: AurralDiscoverArtist): Result<Unit> {
		val artistMbid = artist.id.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist MusicBrainz ID is required."))
		val artistName = artist.name.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Artist name is required."))
		return setArtistMonitoringByAurralId(artistMbid, artistName, monitored = true)
	}

	private suspend fun setArtistMonitoringByAurralId(
		artistMbid: String,
		artistName: String,
		monitored: Boolean
	): Result<Unit> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = aurralApiRequestHeaders(baseUrl)
		val payload = AurralArtistMonitorPayload(
			foreignArtistId = artistMbid,
			artistName = artistName,
			monitorOption = if (monitored) "all" else "none",
			monitored = monitored
		)
		val confirmationId = aurralArtistMonitoringConfirmationId(artistMbid)

		confirmationQueueManager.upsert(
			AurralConfirmationQueueItem(
				id = confirmationId,
				type = AurralConfirmationType.ArtistMonitoring,
				status = AurralConfirmationStatus.Pending,
				title = artistName,
				artistMbid = artistMbid,
				expectedMonitored = monitored,
				message = if (monitored) {
					"Waiting for Aurral to confirm artist monitoring."
				} else {
					"Waiting for Aurral to confirm monitoring stopped."
				},
				updatedAtMillis = nowMillis()
			)
		)
		return runCatching {
			apiClient.monitorArtist(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				artistMbid = artistMbid,
				payload = payload
			)
			if (confirmationWorkerEnabled) {
				confirmationQueueManager.startArtistMonitoringConfirmationWorker(
					confirmationId = confirmationId,
					baseUrl = baseUrl,
					requestHeaders = requestHeaders,
					artistMbid = artistMbid,
					artistName = artistName,
					monitored = monitored,
					payload = payload
				)
			}
			clearAurralMetadataCache(baseUrl)
		}.onFailure { error ->
			confirmationQueueManager.upsert(
				AurralConfirmationQueueItem(
					id = confirmationId,
					type = AurralConfirmationType.ArtistMonitoring,
					status = AurralConfirmationStatus.Failed,
					title = artistName,
					artistMbid = artistMbid,
					expectedMonitored = monitored,
					message = error.message ?: error::class.simpleName ?: "Aurral confirmation failed.",
					updatedAtMillis = nowMillis()
				)
			)
			Logger.w(TAG, "Aurral artist monitoring failed for $artistName", error)
		}.recordAurralAvailability()
	}

	private suspend fun clearAurralMetadataCache(baseUrl: String) {
		runCatching {
			metadataCache.clearBaseUrl(baseUrl)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral metadata cache clear failed", error)
		}
	}

	private suspend fun aurralApiRequestHeaders(baseUrl: String): Map<String, String> =
		auth.apiRequestHeaders(baseUrl)

	private fun <T> Result<T>.recordAurralAvailability(): Result<T> =
		onSuccess {
			preferenceManager.markIntegrationServiceAvailable(IntegrationService.Aurral)
		}.onFailure {
			preferenceManager.markIntegrationServiceDown(IntegrationService.Aurral)
		}
}
