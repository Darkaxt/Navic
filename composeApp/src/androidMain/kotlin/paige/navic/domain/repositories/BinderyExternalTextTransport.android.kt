package paige.navic.domain.repositories

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import okhttp3.Dns
import okhttp3.OkHttpClient
import paige.navic.data.remote.installNavicNetworkBaseline
import java.net.InetAddress
import java.net.UnknownHostException

internal actual fun platformExternalTextTransport(): ExternalTextTransport =
	AndroidExternalTextTransport()

internal class ApprovedExternalTextDns(
	allowedHosts: Set<String>,
	private val delegate: Dns = Dns.SYSTEM
) : Dns {
	private val allowedHosts = allowedHosts.map(String::lowercase).toSet()

	override fun lookup(hostname: String): List<InetAddress> {
		if (hostname.lowercase() !in allowedHosts) {
			throw UnknownHostException("External provider host is not approved.")
		}
		val addresses = delegate.lookup(hostname)
		if (addresses.isEmpty() || addresses.any { address -> !isPublicExternalAddress(address.address) }) {
			throw UnknownHostException("External provider host did not resolve exclusively to public addresses.")
		}
		return addresses
	}
}

internal fun createApprovedExternalTextOkHttpClient(dns: Dns): OkHttpClient =
	OkHttpClient.Builder()
		.dns(dns)
		.followRedirects(false)
		.followSslRedirects(false)
		.build()

private class AndroidExternalTextTransport : ExternalTextTransport {
	private val client = HttpClient(OkHttp) {
		followRedirects = false
		installNavicNetworkBaseline(userAgent = "Navic/1.0 provider-cover-resolver")
		install(HttpTimeout) {
			requestTimeoutMillis = 45_000
			connectTimeoutMillis = 10_000
			socketTimeoutMillis = 45_000
		}
		engine {
			preconfigured = createApprovedExternalTextOkHttpClient(
				ApprovedExternalTextDns(AudioBookBayProviderPageHosts)
			)
		}
	}

	override suspend fun get(request: ApprovedExternalTextRequest): ExternalTextTransportResponse {
		val response = client.get(request.url) {
			accept(ContentType.Text.Html)
		}
		return ExternalTextTransportResponse(
			status = response.status,
			body = response.bodyAsText()
		)
	}
}
