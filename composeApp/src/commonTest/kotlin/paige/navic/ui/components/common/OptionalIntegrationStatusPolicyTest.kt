package paige.navic.ui.components.common

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_optional_integration_disabled
import navic.composeapp.generated.resources.info_optional_integration_empty
import navic.composeapp.generated.resources.info_optional_integration_malformed
import navic.composeapp.generated.resources.info_optional_integration_misconfigured
import navic.composeapp.generated.resources.info_optional_integration_stale
import navic.composeapp.generated.resources.info_optional_integration_unauthorized
import navic.composeapp.generated.resources.info_optional_integration_unavailable
import paige.navic.domain.models.OptionalIntegrationFailure
import paige.navic.domain.models.OptionalIntegrationFailureKind
import paige.navic.domain.models.OptionalIntegrationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OptionalIntegrationStatusPolicyTest {
	@Test
	fun availableContentNeedsNoStatus() {
		assertNull(optionalIntegrationStatusPolicy(OptionalIntegrationResult.Available("live")))
	}

	@Test
	fun emptyAndStaleUseNeutralAndWarningMessages() {
		assertEquals(
			OptionalIntegrationStatusPolicy(
				message = Res.string.info_optional_integration_empty,
				severity = OptionalIntegrationStatusSeverity.Neutral
			),
			optionalIntegrationStatusPolicy(OptionalIntegrationResult.Empty)
		)
		assertEquals(
			OptionalIntegrationStatusPolicy(
				message = Res.string.info_optional_integration_stale,
				severity = OptionalIntegrationStatusSeverity.Warning
			),
			optionalIntegrationStatusPolicy(
				OptionalIntegrationResult.Stale(
					data = "cached",
					failure = failure(OptionalIntegrationFailureKind.Unavailable)
				)
			)
		)
	}

	@Test
	fun failuresUseDistinctErrorMessages() {
		val expected = mapOf(
			OptionalIntegrationFailureKind.Disabled to Res.string.info_optional_integration_disabled,
			OptionalIntegrationFailureKind.Misconfigured to Res.string.info_optional_integration_misconfigured,
			OptionalIntegrationFailureKind.Unauthorized to Res.string.info_optional_integration_unauthorized,
			OptionalIntegrationFailureKind.Malformed to Res.string.info_optional_integration_malformed,
			OptionalIntegrationFailureKind.Unavailable to Res.string.info_optional_integration_unavailable
		)

		expected.forEach { (kind, message) ->
			assertEquals(
				OptionalIntegrationStatusPolicy(
					message = message,
					severity = OptionalIntegrationStatusSeverity.Error
				),
				optionalIntegrationStatusPolicy(OptionalIntegrationResult.Unavailable(failure(kind)))
			)
		}
	}

	private fun failure(kind: OptionalIntegrationFailureKind): OptionalIntegrationFailure =
		OptionalIntegrationFailure(kind = kind, message = "private diagnostic")
}
