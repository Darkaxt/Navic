package paige.navic.domain.repositories

/** Aurral 2.x: no `api/health/bootstrap`, so no advertised app API version. */
const val AURRAL_LEGACY_API_VERSION = 1

const val AURRAL_REQUEST_CANCEL_UNSUPPORTED_MESSAGE = "This Aurral version can't cancel requests."

/** Aurral 3 (app API 2) removed `DELETE api/requests/...`; its replacements delete library items. */
fun aurralSupportsRequestCancel(apiVersion: Int): Boolean = apiVersion < 2

/** Aurral 3 reads artist monitoring only from `monitoring: { monitored }`. */
fun aurralUsesMonitoringBody(apiVersion: Int): Boolean = apiVersion >= 2
