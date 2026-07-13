package paige.navic.domain.manager

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreCredentialStore(
	context: Context
) : CredentialStore {
	private val preferences = context.applicationContext.getSharedPreferences(
		SecureCredentialPreferences,
		Context.MODE_PRIVATE
	)

	override fun get(key: String): String? = runCatching {
		val envelope = preferences.getString(key, null) ?: return null
		decrypt(envelope)
	}.getOrNull()

	override fun put(key: String, value: String): Boolean = runCatching {
		val cipher = Cipher.getInstance(CipherTransformation)
		cipher.init(Cipher.ENCRYPT_MODE, secretKey())
		val ciphertext = cipher.doFinal(value.encodeToByteArray())
		val envelope = listOf(
			EnvelopeVersion,
			Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
			Base64.encodeToString(ciphertext, Base64.NO_WRAP)
		).joinToString(EnvelopeSeparator)
		preferences.edit().putString(key, envelope).commit() && get(key) == value
	}.getOrDefault(false)

	override fun remove(key: String): Boolean =
		preferences.edit().remove(key).commit() && !preferences.contains(key)

	private fun decrypt(envelope: String): String {
		val parts = envelope.split(EnvelopeSeparator)
		require(parts.size == 3 && parts[0] == EnvelopeVersion) {
			"Unsupported credential envelope"
		}
		val iv = Base64.decode(parts[1], Base64.NO_WRAP)
		val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
		val cipher = Cipher.getInstance(CipherTransformation)
		cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GcmTagLengthBits, iv))
		return cipher.doFinal(ciphertext).decodeToString()
	}

	private fun secretKey(): SecretKey {
		val keyStore = KeyStore.getInstance(AndroidKeyStoreProvider).apply { load(null) }
		(keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
		val generator = KeyGenerator.getInstance(
			KeyProperties.KEY_ALGORITHM_AES,
			AndroidKeyStoreProvider
		)
		generator.init(
			KeyGenParameterSpec.Builder(
				KeyAlias,
				KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
			)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.setKeySize(256)
				.build()
		)
		return generator.generateKey()
	}

	companion object {
		const val SecureCredentialPreferences = "navic_secure_credentials"
		private const val AndroidKeyStoreProvider = "AndroidKeyStore"
		private const val KeyAlias = "darkaxt.navic.credentials.v1"
		private const val CipherTransformation = "AES/GCM/NoPadding"
		private const val EnvelopeVersion = "v1"
		private const val EnvelopeSeparator = ":"
		private const val GcmTagLengthBits = 128
	}
}
