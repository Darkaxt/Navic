package paige.navic.data.remote

import dev.zt64.subsonic.client.SubsonicAuth
import dev.zt64.subsonic.client.SubsonicClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

class SubsonicClientFactory {
	fun create(
		instanceUrl: String,
		username: String,
		password: String,
		requestHeaders: Map<String, String>
	): SubsonicClient = SubsonicClient.Companion(
		baseUrl = instanceUrl,
		auth = SubsonicAuth.Token(username = username, password = password),
		client = "Navic",
		clientConfig = {
			installNavicNetworkBaseline()
			if (requestHeaders.isNotEmpty()) {
				defaultRequest {
					requestHeaders.forEach { (key, value) -> header(key, value) }
				}
			}
		}
	)
}
