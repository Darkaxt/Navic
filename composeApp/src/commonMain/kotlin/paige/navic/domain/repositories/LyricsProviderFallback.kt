package paige.navic.domain.repositories

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun <S, T : Any> fetchFirstAvailableLyrics(
	sources: List<S>,
	cachedResult: T? = null,
	fetch: suspend (S) -> T?
): T? {
	var firstFailure: Exception? = null
	for (source in sources) {
		currentCoroutineContext().ensureActive()
		try {
			val result = fetch(source)
			currentCoroutineContext().ensureActive()
			if (result != null) return result
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (error: Exception) {
			if (firstFailure == null) firstFailure = error
		}
	}
	currentCoroutineContext().ensureActive()
	// Absence is resolved only when no source has an unresolved failure.
	return cachedResult ?: firstFailure?.let { throw it }
}

internal suspend fun HttpResponse.lyricsContentOrNull(): String? = when {
	status.isSuccess() -> bodyAsText()
	status == HttpStatusCode.NotFound -> null
	else -> throw IllegalStateException("Lyrics provider returned HTTP ${status.value}")
}
