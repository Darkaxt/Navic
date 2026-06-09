package paige.navic.reader

private const val ReaderPublicationLogLabelMaxLength = 160

fun readerPublicationResourceLogLabel(value: String): String {
	val sanitized = value
		.trim()
		.substringBefore("?")
		.substringBefore("#")
		.takeIf { it.isNotBlank() }
		?: return "<blank>"
	if (sanitized.length <= ReaderPublicationLogLabelMaxLength) return sanitized
	val edgeLength = (ReaderPublicationLogLabelMaxLength - 3) / 2
	return sanitized.take(edgeLength) + "..." + sanitized.takeLast(edgeLength)
}
