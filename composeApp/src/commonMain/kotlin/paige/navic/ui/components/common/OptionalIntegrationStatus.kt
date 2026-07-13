package paige.navic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_optional_integration_disabled
import navic.composeapp.generated.resources.info_optional_integration_empty
import navic.composeapp.generated.resources.info_optional_integration_malformed
import navic.composeapp.generated.resources.info_optional_integration_misconfigured
import navic.composeapp.generated.resources.info_optional_integration_stale
import navic.composeapp.generated.resources.info_optional_integration_unauthorized
import navic.composeapp.generated.resources.info_optional_integration_unavailable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.models.OptionalIntegrationFailureKind
import paige.navic.domain.models.OptionalIntegrationResult
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Info

enum class OptionalIntegrationStatusSeverity {
	Neutral,
	Warning,
	Error
}

data class OptionalIntegrationStatusPolicy(
	val message: StringResource,
	val severity: OptionalIntegrationStatusSeverity
)

fun optionalIntegrationStatusPolicy(
	result: OptionalIntegrationResult<*>?
): OptionalIntegrationStatusPolicy? = when (result) {
	null,
	is OptionalIntegrationResult.Available -> null
	OptionalIntegrationResult.Empty -> OptionalIntegrationStatusPolicy(
		message = Res.string.info_optional_integration_empty,
		severity = OptionalIntegrationStatusSeverity.Neutral
	)
	is OptionalIntegrationResult.Stale -> OptionalIntegrationStatusPolicy(
		message = Res.string.info_optional_integration_stale,
		severity = OptionalIntegrationStatusSeverity.Warning
	)
	is OptionalIntegrationResult.Unavailable -> OptionalIntegrationStatusPolicy(
		message = result.failure.kind.statusMessage(),
		severity = OptionalIntegrationStatusSeverity.Error
	)
}

@Composable
fun OptionalIntegrationStatus(
	result: OptionalIntegrationResult<*>?,
	modifier: Modifier = Modifier
) {
	val policy = optionalIntegrationStatusPolicy(result) ?: return
	val colors = policy.statusColors()
	Row(
		modifier = modifier
			.fillMaxWidth()
			.background(colors.container)
			.padding(horizontal = 12.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp)
	) {
		Icon(
			imageVector = Icons.Outlined.Info,
			contentDescription = null,
			tint = colors.content
		)
		Text(
			text = stringResource(policy.message),
			style = MaterialTheme.typography.bodyMedium,
			color = colors.content
		)
	}
}

private data class OptionalIntegrationStatusColors(
	val container: Color,
	val content: Color
)

@Composable
private fun OptionalIntegrationStatusPolicy.statusColors(): OptionalIntegrationStatusColors =
	when (severity) {
		OptionalIntegrationStatusSeverity.Neutral -> OptionalIntegrationStatusColors(
			container = MaterialTheme.colorScheme.surfaceContainerHigh,
			content = MaterialTheme.colorScheme.onSurfaceVariant
		)
		OptionalIntegrationStatusSeverity.Warning -> OptionalIntegrationStatusColors(
			container = MaterialTheme.colorScheme.tertiaryContainer,
			content = MaterialTheme.colorScheme.onTertiaryContainer
		)
		OptionalIntegrationStatusSeverity.Error -> OptionalIntegrationStatusColors(
			container = MaterialTheme.colorScheme.errorContainer,
			content = MaterialTheme.colorScheme.onErrorContainer
		)
	}

private fun OptionalIntegrationFailureKind.statusMessage(): StringResource = when (this) {
	OptionalIntegrationFailureKind.Disabled -> Res.string.info_optional_integration_disabled
	OptionalIntegrationFailureKind.Misconfigured -> Res.string.info_optional_integration_misconfigured
	OptionalIntegrationFailureKind.Unauthorized -> Res.string.info_optional_integration_unauthorized
	OptionalIntegrationFailureKind.Malformed -> Res.string.info_optional_integration_malformed
	OptionalIntegrationFailureKind.Unavailable -> Res.string.info_optional_integration_unavailable
}
