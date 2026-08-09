package paige.navic.reader

private object ReaderUpstreamReferenceResources

internal fun readerUpstreamReferenceText(upstream: String, relativePath: String): String {
	require(upstream == "anx-reader" || upstream == "komikku") {
		"Unsupported reader reference: $upstream"
	}
	require(relativePath.isNotBlank() && !relativePath.startsWith('/') && ".." !in relativePath.split('/')) {
		"Invalid reader reference path: $relativePath"
	}
	val storedRelativePath = if (relativePath.endsWith(".kt")) "$relativePath.txt" else relativePath
	val resourcePath = "reader-references/$upstream/$storedRelativePath"
	val stream = ReaderUpstreamReferenceResources::class.java
		.getResourceAsStream("/$resourcePath")
		?: error("Could not locate pinned reader reference: $resourcePath")
	return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
