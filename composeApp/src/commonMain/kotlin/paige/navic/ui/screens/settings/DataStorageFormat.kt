package paige.navic.ui.screens.settings

private const val BYTES_PER_MB = 1024L * 1024L
private const val MB_PER_GB = 1024.0

fun downloadStorageSizeText(bytes: Long): String {
	val megabytes = bytes.coerceAtLeast(0).toDouble() / BYTES_PER_MB
	return if (megabytes >= MB_PER_GB) {
		"${trimmedDecimal(megabytes / MB_PER_GB)} GB"
	} else {
		"${megabytes.toInt()} MB"
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
