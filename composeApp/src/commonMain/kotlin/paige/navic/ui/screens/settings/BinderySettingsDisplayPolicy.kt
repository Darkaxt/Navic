package paige.navic.ui.screens.settings

import androidx.compose.runtime.Immutable
import paige.navic.domain.repositories.BinderyConnectionResult
import paige.navic.domain.repositories.BinderyServiceStatus
import paige.navic.domain.repositories.binderyOpdsBaseUrlConfigurationError

@Immutable
sealed interface BinderyConnectionState {
	data object Disabled : BinderyConnectionState
	data object MissingOpdsUrl : BinderyConnectionState
	data object MissingApiKey : BinderyConnectionState
	data object Testing : BinderyConnectionState
	data object NotTested : BinderyConnectionState
	data object Unauthorized : BinderyConnectionState
	data object Forbidden : BinderyConnectionState
	data class InvalidOpdsUrl(val message: String) : BinderyConnectionState
	data class Connected(
		val navigationCount: Int,
		val audiobooksAvailable: Boolean
	) : BinderyConnectionState
	data class Failed(val message: String) : BinderyConnectionState
}

@Immutable
enum class BinderyStatusType {
	OpdsUrl,
	ApiKey,
	Audiobooks,
	Authors,
	Collections,
	Series,
	Search,
	Navigation,
	ProgressSync,
	Pagination
}

@Immutable
sealed interface BinderyStatusValue {
	data object Configured : BinderyStatusValue
	data object NotConfigured : BinderyStatusValue
	data object Enabled : BinderyStatusValue
	data object Disabled : BinderyStatusValue
	data object Unsupported : BinderyStatusValue
	data class Count(val value: Int) : BinderyStatusValue
}

@Immutable
data class BinderyStatusRow(
	val type: BinderyStatusType,
	val value: BinderyStatusValue
)

fun binderyConnectionState(
	enabled: Boolean,
	opdsUrl: String,
	apiKey: String,
	connectionResult: BinderyConnectionResult?,
	isTestingConnection: Boolean
): BinderyConnectionState =
	when {
		!enabled -> BinderyConnectionState.Disabled
		isTestingConnection -> BinderyConnectionState.Testing
		opdsUrl.isBlank() -> BinderyConnectionState.MissingOpdsUrl
		binderyOpdsBaseUrlConfigurationError(opdsUrl) != null ->
			BinderyConnectionState.InvalidOpdsUrl(binderyOpdsBaseUrlConfigurationError(opdsUrl).orEmpty())
		apiKey.isBlank() -> BinderyConnectionState.MissingApiKey
		connectionResult == null -> BinderyConnectionState.NotTested
		connectionResult == BinderyConnectionResult.Disabled -> BinderyConnectionState.Disabled
		connectionResult == BinderyConnectionResult.MissingOpdsUrl -> BinderyConnectionState.MissingOpdsUrl
		connectionResult == BinderyConnectionResult.MissingApiKey -> BinderyConnectionState.MissingApiKey
		connectionResult == BinderyConnectionResult.Unauthorized -> BinderyConnectionState.Unauthorized
		connectionResult == BinderyConnectionResult.Forbidden -> BinderyConnectionState.Forbidden
		connectionResult is BinderyConnectionResult.InvalidOpdsUrl ->
			BinderyConnectionState.InvalidOpdsUrl(connectionResult.message)
		connectionResult is BinderyConnectionResult.Connected ->
			BinderyConnectionState.Connected(
				navigationCount = connectionResult.navigationCount,
				audiobooksAvailable = connectionResult.audiobooksAvailable
			)
		connectionResult is BinderyConnectionResult.Failed ->
			BinderyConnectionState.Failed(connectionResult.message)
		else -> BinderyConnectionState.NotTested
	}

fun binderyStatusRows(status: BinderyServiceStatus): List<BinderyStatusRow> =
	listOf(
		BinderyStatusRow(
			type = BinderyStatusType.OpdsUrl,
			value = if (status.opdsUrlConfigured) {
				BinderyStatusValue.Configured
			} else {
				BinderyStatusValue.NotConfigured
			}
		),
		BinderyStatusRow(
			type = BinderyStatusType.ApiKey,
			value = if (status.apiKeyConfigured) {
				BinderyStatusValue.Configured
			} else {
				BinderyStatusValue.NotConfigured
			}
		),
		BinderyStatusRow(
			type = BinderyStatusType.Audiobooks,
			value = if (status.hasAudiobooks) BinderyStatusValue.Enabled else BinderyStatusValue.Disabled
		),
		BinderyStatusRow(
			type = BinderyStatusType.Authors,
			value = if (status.hasAuthors) BinderyStatusValue.Enabled else BinderyStatusValue.Disabled
		),
		BinderyStatusRow(
			type = BinderyStatusType.Collections,
			value = if (status.hasCollections) BinderyStatusValue.Enabled else BinderyStatusValue.Disabled
		),
		BinderyStatusRow(
			type = BinderyStatusType.Series,
			value = if (status.hasSeries) BinderyStatusValue.Enabled else BinderyStatusValue.Disabled
		),
		BinderyStatusRow(
			type = BinderyStatusType.Search,
			value = if (status.hasSearch) BinderyStatusValue.Enabled else BinderyStatusValue.Disabled
		),
		BinderyStatusRow(
			type = BinderyStatusType.Navigation,
			value = BinderyStatusValue.Count(status.navigationCount)
		),
		BinderyStatusRow(
			type = BinderyStatusType.ProgressSync,
			value = if (status.progressSyncSupported) BinderyStatusValue.Enabled else BinderyStatusValue.Unsupported
		),
		BinderyStatusRow(
			type = BinderyStatusType.Pagination,
			value = if (status.paginationSupported) BinderyStatusValue.Enabled else BinderyStatusValue.Unsupported
		)
	)
