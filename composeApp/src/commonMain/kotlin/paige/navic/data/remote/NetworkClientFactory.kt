package paige.navic.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal const val NAVIC_USER_AGENT = "Navic"

internal object NetworkJson {
	val compatible = Json {
		ignoreUnknownKeys = true
	}
	val tolerant = Json(compatible) {
		isLenient = true
	}
}

internal fun HttpClientConfig<*>.installNavicNetworkBaseline(
	userAgent: String = NAVIC_USER_AGENT
) {
	install(UserAgent) {
		agent = userAgent
	}
}

class NetworkClientFactory(
	private val engineFactory: (() -> HttpClientEngine)? = null
) {
	fun create(
		json: Json? = null,
		userAgent: String = NAVIC_USER_AGENT,
		configure: HttpClientConfig<*>.() -> Unit = {}
	): HttpClient {
		val clientPolicy: HttpClientConfig<*>.() -> Unit = {
			installNavicNetworkBaseline(userAgent)
			json?.let { serialization ->
				install(ContentNegotiation) {
					json(serialization)
				}
			}
			configure(this)
		}
		return engineFactory?.let { HttpClient(it(), clientPolicy) }
			?: HttpClient(clientPolicy)
	}
}
