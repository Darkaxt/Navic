package paige.navic.domain.repositories

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class BinderyExternalTextDnsTest {
	@Test
	fun approvedHostReturnsTheSameValidatedPublicAddressObjects() {
		val publicAddress = address(8, 8, 8, 8)
		val dns = ApprovedExternalTextDns(
			allowedHosts = AudioBookBayProviderPageHosts,
			delegate = StaticDns(listOf(publicAddress))
		)

		val resolved = dns.lookup("audiobookbay.lu")

		assertEquals(1, resolved.size)
		assertSame(publicAddress, resolved.single())
	}

	@Test
	fun dnsRejectsRedirectOrHostChangeBeforeDelegating() {
		val delegate = StaticDns(listOf(address(8, 8, 8, 8)))
		val dns = ApprovedExternalTextDns(AudioBookBayProviderPageHosts, delegate)

		assertFailsWith<UnknownHostException> { dns.lookup("cdn.audiobookbay.lu") }
		assertFailsWith<UnknownHostException> { dns.lookup("evil.example") }
		assertEquals(emptyList(), delegate.lookups)
	}

	@Test
	fun dnsRejectsEmptyPrivateLocalAndMixedAnswers() {
		val rejectedAnswers = listOf(
			emptyList(),
			listOf(address(127, 0, 0, 1)),
			listOf(address(10, 0, 0, 1)),
			listOf(address(169, 254, 169, 254)),
			listOf(address(172, 16, 0, 1)),
			listOf(address(192, 168, 1, 1)),
			listOf(address(0xfe, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)),
			listOf(address(0xfd, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)),
			listOf(address(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)),
			listOf(address(8, 8, 8, 8), address(192, 168, 1, 1))
		)

		rejectedAnswers.forEach { answers ->
			val dns = ApprovedExternalTextDns(AudioBookBayProviderPageHosts, StaticDns(answers))
			assertFailsWith<UnknownHostException>(answers.joinToString { it.hostAddress.orEmpty() }) {
				dns.lookup("audiobookbay.lu")
			}
		}
	}

	@Test
	fun providerHttpClientPinsDnsAndDisablesRedirects() {
		val dns = ApprovedExternalTextDns(
			AudioBookBayProviderPageHosts,
			StaticDns(listOf(address(8, 8, 8, 8)))
		)

		val client = createApprovedExternalTextOkHttpClient(dns)

		assertSame(dns, client.dns)
		assertFalse(client.followRedirects)
		assertFalse(client.followSslRedirects)
	}

	private fun address(vararg bytes: Int): InetAddress =
		InetAddress.getByAddress(ByteArray(bytes.size) { index -> bytes[index].toByte() })
}

private class StaticDns(
	private val answers: List<InetAddress>
) : Dns {
	val lookups = mutableListOf<String>()

	override fun lookup(hostname: String): List<InetAddress> {
		lookups += hostname
		return answers
	}
}
