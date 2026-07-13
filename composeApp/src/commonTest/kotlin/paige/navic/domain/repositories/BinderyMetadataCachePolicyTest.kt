package paige.navic.domain.repositories

import paige.navic.data.remote.bindery.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class BinderyMetadataCachePolicyTest {
	@Test
	fun cacheKeySeparatesCredentialsWithoutPersistingPlaintext() {
		val firstFingerprint = binderyApiKeyFingerprint("first-secret")
		val secondFingerprint = binderyApiKeyFingerprint("second-secret")
		val firstKey = binderyMetadataCacheKey(
			baseUrl = "https://bindery.example.com/opds",
			payloadType = BinderyMetadataPayloadType.Catalog,
			path = "/opds/books",
			apiKeyFingerprint = firstFingerprint
		)
		val secondKey = binderyMetadataCacheKey(
			baseUrl = "https://bindery.example.com/opds",
			payloadType = BinderyMetadataPayloadType.Catalog,
			path = "/opds/books",
			apiKeyFingerprint = secondFingerprint
		)

		assertNotEquals(firstFingerprint, secondFingerprint)
		assertNotEquals(firstKey, secondKey)
		assertFalse(firstKey.contains("first-secret"))
		assertFalse(secondKey.contains("second-secret"))
	}

	@Test
	fun fingerprintAndCacheKeyNormalizeInputsButKeepOriginsSeparate() {
		assertEquals(binderyApiKeyFingerprint("secret"), binderyApiKeyFingerprint(" secret "))
		val fingerprint = binderyApiKeyFingerprint("secret")
		val first = binderyMetadataCacheKey(
			baseUrl = " https://one.example.com/opds/ ",
			payloadType = BinderyMetadataPayloadType.Manifest,
			path = " 3693 ",
			apiKeyFingerprint = fingerprint
		)
		val normalizedFirst = binderyMetadataCacheKey(
			baseUrl = "https://one.example.com/opds",
			payloadType = BinderyMetadataPayloadType.Manifest,
			path = "3693",
			apiKeyFingerprint = fingerprint
		)
		val second = binderyMetadataCacheKey(
			baseUrl = "https://two.example.com/opds",
			payloadType = BinderyMetadataPayloadType.Manifest,
			path = "3693",
			apiKeyFingerprint = fingerprint
		)

		assertEquals(first, normalizedFirst)
		assertNotEquals(first, second)
	}
}
