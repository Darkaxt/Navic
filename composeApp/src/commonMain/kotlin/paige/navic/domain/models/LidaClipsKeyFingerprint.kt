package paige.navic.domain.models

private const val FNV_1A_64_OFFSET_BASIS = -3750763034362895579L
private const val FNV_1A_64_PRIME = 1099511628211L

internal fun lidaClipsKeyFingerprint(value: String): String {
	var hash = FNV_1A_64_OFFSET_BASIS
	value.encodeToByteArray().forEach { byte ->
		hash = hash xor (byte.toLong() and 0xff)
		hash *= FNV_1A_64_PRIME
	}
	return "fnv1a64:${hash.toString(16)}"
}
