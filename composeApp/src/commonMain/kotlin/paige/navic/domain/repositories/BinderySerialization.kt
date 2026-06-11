package paige.navic.domain.repositories

import kotlinx.serialization.json.Json

internal val BinderyJson = Json {
	ignoreUnknownKeys = true
	isLenient = true
}
