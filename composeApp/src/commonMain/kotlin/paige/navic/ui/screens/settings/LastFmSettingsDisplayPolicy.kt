package paige.navic.ui.screens.settings

import androidx.compose.runtime.Immutable
import paige.navic.domain.repositories.LastFmConnectionResult
import paige.navic.domain.repositories.LastFmServiceStatus

@Immutable
sealed interface LastFmConnectionState {
	data object Disabled : LastFmConnectionState
	data object MissingApiKey : LastFmConnectionState
	data object NotTested : LastFmConnectionState
	data object Testing : LastFmConnectionState
	data object InvalidApiKey : LastFmConnectionState
	data class Connected(val sampleArtistCount: Int) : LastFmConnectionState
	data class Failed(val message: String) : LastFmConnectionState
}

@Immutable
enum class LastFmStatusType {
	ApiKey,
	Integration,
	ArtistTopTracks,
	AccountFeatures,
	ValidationSample
}

@Immutable
sealed interface LastFmStatusValue {
	data object Configured : LastFmStatusValue
	data object NotConfigured : LastFmStatusValue
	data object Enabled : LastFmStatusValue
	data object Disabled : LastFmStatusValue
	data object Unsupported : LastFmStatusValue
	data class Count(val value: Int) : LastFmStatusValue
}

@Immutable
data class LastFmStatusRow(
	val type: LastFmStatusType,
	val value: LastFmStatusValue
)

fun lastFmConnectionState(
	enabled: Boolean,
	apiKey: String,
	connectionResult: LastFmConnectionResult?,
	isTestingConnection: Boolean
): LastFmConnectionState =
	when {
		!enabled -> LastFmConnectionState.Disabled
		isTestingConnection -> LastFmConnectionState.Testing
		apiKey.isBlank() -> LastFmConnectionState.MissingApiKey
		connectionResult == null -> LastFmConnectionState.NotTested
		connectionResult is LastFmConnectionResult.Connected ->
			LastFmConnectionState.Connected(connectionResult.sampleArtistCount)
		connectionResult is LastFmConnectionResult.Failed ->
			LastFmConnectionState.Failed(connectionResult.message)
		connectionResult == LastFmConnectionResult.Disabled -> LastFmConnectionState.Disabled
		connectionResult == LastFmConnectionResult.InvalidApiKey -> LastFmConnectionState.InvalidApiKey
		connectionResult == LastFmConnectionResult.MissingApiKey -> LastFmConnectionState.MissingApiKey
		else -> LastFmConnectionState.NotTested
	}

fun lastFmStatusRows(status: LastFmServiceStatus): List<LastFmStatusRow> =
	buildList {
		add(
			LastFmStatusRow(
				type = LastFmStatusType.ApiKey,
				value = if (status.apiKeyConfigured) {
					LastFmStatusValue.Configured
				} else {
					LastFmStatusValue.NotConfigured
				}
			)
		)
		add(
			LastFmStatusRow(
				type = LastFmStatusType.Integration,
				value = if (status.enabled) {
					LastFmStatusValue.Enabled
				} else {
					LastFmStatusValue.Disabled
				}
			)
		)
		add(
			LastFmStatusRow(
				type = LastFmStatusType.ArtistTopTracks,
				value = if (status.artistTopTracksEnabled) {
					LastFmStatusValue.Enabled
				} else {
					LastFmStatusValue.Disabled
				}
			)
		)
		if (status.enabled && status.apiKeyConfigured) {
			add(
				LastFmStatusRow(
					type = LastFmStatusType.AccountFeatures,
					value = LastFmStatusValue.Unsupported
				)
			)
		}
		status.sampleArtistCount?.let { count ->
			add(
				LastFmStatusRow(
					type = LastFmStatusType.ValidationSample,
					value = LastFmStatusValue.Count(count)
				)
			)
		}
	}
