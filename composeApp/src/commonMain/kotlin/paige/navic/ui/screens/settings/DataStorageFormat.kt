package paige.navic.ui.screens.settings

private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = 1024L * 1024L
private const val MB_PER_GB = 1024.0

fun downloadStorageSizeText(bytes: Long): String =
	storageSizeText(bytes)

fun imageCacheStorageSizeText(bytes: Long): String =
	storageSizeText(bytes)

fun storageSizeText(bytes: Long): String {
	val safeBytes = bytes.coerceAtLeast(0)
	return when {
		safeBytes < BYTES_PER_KB -> "$safeBytes B"
		safeBytes < BYTES_PER_MB -> {
			val kibibytes = safeBytes.toDouble() / BYTES_PER_KB
			"${trimmedDecimal(kibibytes)} KB"
		}
		else -> {
			val megabytes = safeBytes.toDouble() / BYTES_PER_MB
			if (megabytes >= MB_PER_GB) {
				"${trimmedDecimal(megabytes / MB_PER_GB)} GB"
			} else {
				"${trimmedDecimal(megabytes)} MB"
			}
		}
	}
}

private fun trimmedDecimal(value: Double): String {
	val hundredths = (value * 100).toLong()
	val whole = hundredths / 100
	val fraction = hundredths % 100
	return when {
		fraction == 0L -> whole.toString()
		fraction % 10 == 0L -> "$whole.${fraction / 10}"
		else -> "$whole.${fraction.toString().padStart(2, '0')}"
	}
}
