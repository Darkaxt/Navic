package paige.navic.domain.repositories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BinderyExternalTextPolicyTest {
	@Test
	fun approvedAudioBookBaySourceUsesCanonicalHttpsHost() {
		val request = approvedExternalTextRequest(
			url = "https://AUDIOBOOKBAY.LU/abss/the-hobbit/?page=1",
			purpose = ExternalTextPurpose.AudioBookBayProviderCover
		)

		assertEquals("audiobookbay.lu", request.host)
		assertEquals(443, request.port)
		assertEquals("https", request.scheme)
	}

	@Test
	fun providerSourcePolicyRejectsUnsupportedOrAmbiguousUrls() {
		val rejectedUrls = listOf(
			"http://audiobookbay.lu/abss/the-hobbit/",
			"ftp://audiobookbay.lu/abss/the-hobbit/",
			"https://audiobookbay.lu:444/abss/the-hobbit/",
			"https://user:password@audiobookbay.lu/abss/the-hobbit/",
			"https://audiobookbay.lu/abss/the-hobbit/#cover",
			"https://audiobookbay.lu./abss/the-hobbit/",
			"https://audiobookbay.lu.evil.example/abss/the-hobbit/",
			"https://evil.example/?next=https://audiobookbay.lu/",
			"file:///etc/passwd"
		)

		rejectedUrls.forEach { url ->
			assertFailsWith<IllegalStateException>(url) {
				approvedExternalTextRequest(url, ExternalTextPurpose.AudioBookBayProviderCover)
			}
		}
	}

	@Test
	fun providerSourcePolicyRejectsLocalAndPrivateIpLiterals() {
		val rejectedUrls = listOf(
			"https://localhost/",
			"https://127.0.0.1/",
			"https://10.0.0.1/",
			"https://172.16.0.1/",
			"https://192.168.1.1/",
			"https://169.254.169.254/latest/meta-data/",
			"https://[::1]/",
			"https://[fe80::1]/",
			"https://[fd00::1]/"
		)

		rejectedUrls.forEach { url ->
			assertFailsWith<IllegalStateException>(url) {
				approvedExternalTextRequest(url, ExternalTextPurpose.AudioBookBayProviderCover)
			}
		}
	}

	@Test
	fun publicAddressPolicyAcceptsPublicIpv4AndIpv6() {
		assertTrue(isPublicExternalAddress(ipv4(8, 8, 8, 8)))
		assertTrue(
			isPublicExternalAddress(
				ipv6(0x26, 0x06, 0x47, 0x00, 0x47, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x11, 0x11)
			)
		)
	}

	@Test
	fun publicAddressPolicyRejectsIpv4LocalPrivateAndReservedRanges() {
		val rejected = listOf(
			ipv4(0, 0, 0, 0),
			ipv4(10, 0, 0, 1),
			ipv4(100, 64, 0, 1),
			ipv4(127, 0, 0, 1),
			ipv4(169, 254, 1, 1),
			ipv4(172, 16, 0, 1),
			ipv4(192, 168, 1, 1),
			ipv4(198, 18, 0, 1),
			ipv4(224, 0, 0, 1),
			ipv4(255, 255, 255, 255)
		)

		rejected.forEach { address -> assertFalse(isPublicExternalAddress(address), address.contentToString()) }
	}

	@Test
	fun publicAddressPolicyRejectsIpv6LocalAndMappedPrivateRanges() {
		val rejected = listOf(
			ipv6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			ipv6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
			ipv6(0xfe, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
			ipv6(0xfd, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
			ipv6(0xff, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
			ipv6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff, 0xff, 192, 168, 1, 1)
		)

		rejected.forEach { address -> assertFalse(isPublicExternalAddress(address), address.contentToString()) }
		assertFalse(isPublicExternalAddress(byteArrayOf(1, 2, 3)))
	}

	private fun ipv4(a: Int, b: Int, c: Int, d: Int): ByteArray =
		byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())

	private fun ipv6(vararg bytes: Int): ByteArray =
		ByteArray(bytes.size) { index -> bytes[index].toByte() }
}
