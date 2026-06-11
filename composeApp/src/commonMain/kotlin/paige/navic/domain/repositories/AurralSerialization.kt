package paige.navic.domain.repositories

import kotlinx.serialization.json.Json

internal val AURRAL_JSON = Json {
	ignoreUnknownKeys = true
	isLenient = true
}
