package paige.navic.domain.repositories

import paige.navic.data.remote.aurral.*

import paige.navic.domain.manager.PreferenceManager

internal class AurralRepositoryAuth(
	private val preferenceManager: PreferenceManager,
	private val apiClient: AurralApiClient
) {
	private var authenticatedHeadersCache: AurralAuthenticatedHeadersCacheEntry? = null

	suspend fun apiRequestHeaders(baseUrl: String): Map<String, String> {
		val fallbackHeaders = preferenceManager.aurralRequestHeadersMap()
		val username = preferenceManager.aurralUsername.trim().takeIf { it.isNotEmpty() }
			?: return fallbackHeaders
		val password = preferenceManager.aurralPassword.trim().takeIf { it.isNotEmpty() }
			?: return fallbackHeaders
		val cacheKey = aurralAuthenticatedHeadersCacheKey(
			baseUrl = baseUrl,
			username = username,
			password = password,
			fallbackHeaders = fallbackHeaders
		)
		authenticatedHeadersCache?.takeIf { it.key == cacheKey }?.let { return it.headers }
		val bearerHeaders = aurralBearerAuthHeaders(
			loginSessionToken(
				baseUrl = baseUrl,
				requestHeaders = fallbackHeaders
			)
		)
		if (bearerHeaders.isNotEmpty()) {
			authenticatedHeadersCache = AurralAuthenticatedHeadersCacheEntry(
				key = cacheKey,
				headers = bearerHeaders
			)
			return bearerHeaders
		}
		return fallbackHeaders
	}

	fun bearerTokenFromHeaders(requestHeaders: Map<String, String>): String? {
		val authorization = requestHeaders.entries.firstOrNull { (key, _) ->
			key.equals("Authorization", ignoreCase = true)
		}?.value?.trim().orEmpty()
		return authorization
			.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
			?.drop("Bearer ".length)
			?.trim()
			?.takeIf { it.isNotEmpty() }
	}

	suspend fun loginSessionToken(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): String? {
		val username = preferenceManager.aurralUsername.trim().takeIf { it.isNotEmpty() }
			?: return null
		val password = preferenceManager.aurralPassword.trim().takeIf { it.isNotEmpty() }
			?: return null
		return runCatching {
			apiClient.login(
				baseUrl = baseUrl,
				requestHeaders = requestHeaders,
				username = username,
				password = password
			)?.token?.trim()?.takeIf { it.isNotEmpty() }
		}.getOrNull()
	}
}
