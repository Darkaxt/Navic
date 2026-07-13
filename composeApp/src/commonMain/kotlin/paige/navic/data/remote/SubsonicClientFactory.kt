package paige.navic.data.remote

import dev.zt64.subsonic.client.SubsonicAuth
import dev.zt64.subsonic.client.SubsonicClient
import io.ktor.client.plugins.UserAgent
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
			install(UserAgent) { agent = "Navic" }
			if (requestHeaders.isNotEmpty()) {
				defaultRequest {
					requestHeaders.forEach { (key, value) -> header(key, value) }
				}
			}
		}
	)
}
