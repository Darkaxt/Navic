package paige.navic.data.remote

import io.ktor.client.plugins.api.createClientPlugin
import kotlin.time.Instant

/**
 * subsonic-client passes `scrobble`'s `time` as a `kotlin.time.Instant`, which Ktor writes as ISO
 * text (`2026-11-18T21:04:05.123Z`). The Subsonic API wants milliseconds since the epoch;
 * Navidrome logs "Ignoring invalid time param" and records the scrobble at the moment it arrives,
 * so plays queued offline land at sync time. The parameter is rewritten before the request goes out.
 */
internal val SubsonicScrobbleTimeFix = createClientPlugin("SubsonicScrobbleTimeFix") {
	onRequest { request, _ ->
		if (!isSubsonicScrobblePath(request.url.pathSegments.lastOrNull().orEmpty())) return@onRequest
		val times = request.url.parameters.getAll("time") ?: return@onRequest
		request.url.parameters.remove("time")
		request.url.parameters.appendAll("time", times.map(::subsonicEpochMillisTime))
	}
}

internal fun isSubsonicScrobblePath(path: String): Boolean =
	path.substringAfterLast('/').removeSuffix(".view") == "scrobble"

internal fun subsonicEpochMillisTime(value: String): String =
	if (value.toLongOrNull() != null) {
		value
	} else {
		runCatching { Instant.parse(value).toEpochMilliseconds().toString() }.getOrDefault(value)
	}
