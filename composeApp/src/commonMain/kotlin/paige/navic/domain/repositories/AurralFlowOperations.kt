package paige.navic.domain.repositories

import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.util.core.Logger

private const val TAG = "AurralFlowOperations"

internal class AurralFlowOperations(
	private val preferenceManager: PreferenceManager,
	private val apiClient: AurralApiClient,
	private val authSession: AurralAuthSession
) {
	suspend fun createFlow(
		name: String,
		size: Int,
		scheduleDay: Int
	): Result<AurralFlowActionResult> {
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = authSession.requestHeaders(baseUrl)
		val payload = runCatching {
			aurralDefaultFlowCreatePayload(
				name = name,
				size = size,
				scheduleDay = scheduleDay
			)
		}.getOrElse { error ->
			return Result.failure(IllegalStateException(error.message ?: "Flow details are invalid."))
		}

		return runCatching {
			apiClient.createFlow(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				payload = payload
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow creation failed for ${payload.name}", error)
		}
	}

	suspend fun setFlowEnabled(
		flowId: String,
		enabled: Boolean
	): Result<AurralFlowActionResult> {
		val trimmedFlowId = flowId.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Flow ID is required."))
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = authSession.requestHeaders(baseUrl)

		return runCatching {
			apiClient.setFlowEnabled(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				flowId = trimmedFlowId,
				enabled = enabled
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow enable update failed for $trimmedFlowId", error)
		}
	}

	suspend fun startFlow(
		flowId: String,
		limit: Int
	): Result<AurralFlowActionResult> {
		val trimmedFlowId = flowId.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Flow ID is required."))
		if (!preferenceManager.aurralEnabled) {
			return Result.failure(IllegalStateException(AURRAL_DISABLED_MESSAGE))
		}
		val safeLimit = limit.takeIf { it > 0 } ?: 30
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = authSession.requestHeaders(baseUrl)

		return runCatching {
			apiClient.startFlow(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				flowId = trimmedFlowId,
				limit = safeLimit
			)
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow start failed for $trimmedFlowId", error)
		}
	}

	suspend fun getFlowPlayableSongs(
		flowId: String,
		limit: Int
	): Result<List<DomainSong>> {
		val trimmedFlowId = flowId.trim().takeIf { it.isNotEmpty() }
			?: return Result.failure(IllegalStateException("Flow ID is required."))
		if (!preferenceManager.aurralEnabled) return Result.success(emptyList())
		val safeLimit = limit.takeIf { it > 0 } ?: 200
		val baseUrlError = aurralBaseUrlConfigurationError(preferenceManager.aurralBaseUrl)
		if (baseUrlError != null) return Result.failure(IllegalStateException(baseUrlError))
		val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
			?: return Result.failure(IllegalStateException(AURRAL_BASE_URL_REQUIRED_MESSAGE))
		val requestHeaders = authSession.requestHeaders(baseUrl)

		return runCatching {
			val readyJobs = apiClient.fetchFlowJobs(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				flowId = trimmedFlowId,
				limit = safeLimit
			).filter { it.status.equals("done", ignoreCase = true) }
			if (readyJobs.isEmpty()) return@runCatching emptyList()

			val sessionToken = authSession.bearerTokenFromHeaders(requestHeaders)
				?: authSession.loginSessionToken(
					baseUrl = baseUrl,
					requestHeaders = requestHeaders
				)
			val streamToken = if (sessionToken == null && requestHeaders.isNotEmpty()) {
				runCatching {
					apiClient.fetchStreamToken(baseUrl, requestHeaders)?.token?.trim()?.takeIf { it.isNotEmpty() }
				}.getOrNull()
			} else {
				null
			}
			val allowUnauthenticatedStream = sessionToken == null &&
				streamToken == null &&
				requestHeaders.isEmpty()

			readyJobs.mapNotNull { job ->
				job.toDomainSong(
					baseUrl = baseUrl,
					sessionToken = sessionToken,
					streamToken = streamToken,
					allowUnauthenticatedStream = allowUnauthenticatedStream
				)
			}
		}.onFailure { error ->
			Logger.w(TAG, "Aurral Flow playable songs failed for $trimmedFlowId", error)
		}
	}
}
