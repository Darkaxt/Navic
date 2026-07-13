package paige.navic.domain.models

import kotlinx.serialization.SerializationException

enum class OptionalIntegrationFailureKind {
	Disabled,
	Misconfigured,
	Unauthorized,
	Malformed,
	Unavailable
}

data class OptionalIntegrationFailure(
	val kind: OptionalIntegrationFailureKind,
	val message: String
)

sealed interface OptionalIntegrationResult<out T> {
	data class Available<T>(val data: T) : OptionalIntegrationResult<T>
	data object Empty : OptionalIntegrationResult<Nothing>
	data class Stale<T>(
		val data: T,
		val failure: OptionalIntegrationFailure
	) : OptionalIntegrationResult<T>
	data class Unavailable(
		val failure: OptionalIntegrationFailure
	) : OptionalIntegrationResult<Nothing>
}

interface OptionalIntegrationHttpFailure {
	val statusCode: Int
}

class OptionalIntegrationHttpException(
	override val statusCode: Int,
	message: String
) : IllegalStateException(message), OptionalIntegrationHttpFailure

fun <T> optionalIntegrationResult(
	result: Result<T>,
	staleFailure: OptionalIntegrationFailure? = null,
	isEmpty: (T) -> Boolean
): OptionalIntegrationResult<T> = result.fold(
	onSuccess = { data ->
		when {
			staleFailure != null -> OptionalIntegrationResult.Stale(data, staleFailure)
			isEmpty(data) -> OptionalIntegrationResult.Empty
			else -> OptionalIntegrationResult.Available(data)
		}
	},
	onFailure = { error -> OptionalIntegrationResult.Unavailable(optionalIntegrationFailure(error)) }
)

fun optionalIntegrationUnavailable(
	kind: OptionalIntegrationFailureKind,
	message: String
): OptionalIntegrationResult.Unavailable =
	OptionalIntegrationResult.Unavailable(OptionalIntegrationFailure(kind, message))

fun OptionalIntegrationResult<*>.failureOrNull(): OptionalIntegrationFailure? =
	when (this) {
		is OptionalIntegrationResult.Stale -> failure
		is OptionalIntegrationResult.Unavailable -> failure
		else -> null
	}

fun optionalIntegrationFailure(error: Throwable): OptionalIntegrationFailure {
	val causes = error.causes()
	val httpFailure = causes.filterIsInstance<OptionalIntegrationHttpFailure>().firstOrNull()
	if (httpFailure?.statusCode == 401 || httpFailure?.statusCode == 403) {
		return OptionalIntegrationFailure(
			kind = OptionalIntegrationFailureKind.Unauthorized,
			message = error.message.orErrorName()
		)
	}
	if (causes.any { it is SerializationException }) {
		return OptionalIntegrationFailure(
			kind = OptionalIntegrationFailureKind.Malformed,
			message = error.message.orErrorName()
		)
	}
	return OptionalIntegrationFailure(
		kind = OptionalIntegrationFailureKind.Unavailable,
		message = error.message.orErrorName()
	)
}

private fun Throwable.causes(): List<Throwable> {
	val result = mutableListOf<Throwable>()
	val seen = mutableSetOf<Throwable>()
	var current: Throwable? = this
	while (current != null && seen.add(current)) {
		result += current
		current = current.cause
	}
	return result
}

private fun String?.orErrorName(): String =
	this?.takeIf { it.isNotBlank() } ?: "Integration request failed"
