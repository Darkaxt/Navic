package paige.navic.domain.models

fun nextLidaClipsServiceStatusRefreshKey(
	enabled: Boolean,
	baseUrl: String,
	apiKey: String
): String? {
	val normalizedBaseUrl = normalizedLidaClipsBaseUrlOrNull(baseUrl)
	if (!enabled || normalizedBaseUrl == null) return null

	return "$normalizedBaseUrl|${apiKey.trim()}"
}
