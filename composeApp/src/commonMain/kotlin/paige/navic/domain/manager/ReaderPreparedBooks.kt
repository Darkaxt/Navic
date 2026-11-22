package paige.navic.domain.manager

/** #66: whether "Prepare pages" completed for this Bindery book (any reader settings). */
fun PreferenceManager.isReaderBookPrepared(bookId: String): Boolean =
	bookId.isNotBlank() && readerPreparedBookIds.lineSequence().any { line -> line == bookId }

fun PreferenceManager.markReaderBookPrepared(bookId: String) {
	if (bookId.isBlank() || isReaderBookPrepared(bookId)) return
	readerPreparedBookIds = (readerPreparedBookIds.lineSequence().filter(String::isNotBlank) + bookId)
		.joinToString(separator = "\n")
}
