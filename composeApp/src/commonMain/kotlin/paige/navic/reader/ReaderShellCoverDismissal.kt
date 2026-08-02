package paige.navic.reader

private const val ReaderShellCoverDismissalReasonPrefix = "shell-cover-dismiss:"

internal fun readerShellCoverDismissalReason(requestId: Long): String =
	"$ReaderShellCoverDismissalReasonPrefix$requestId"

private fun readerShellCoverDismissalRequestId(reason: String?): Long? {
	val normalizedReason = reason?.trim().orEmpty()
	if (!normalizedReason.startsWith(ReaderShellCoverDismissalReasonPrefix)) return null
	return normalizedReason
		.removePrefix(ReaderShellCoverDismissalReasonPrefix)
		.toLongOrNull()
}

internal fun readerNativeShellCoverReturnLocatorKey(locator: ReaderLocator?): String? {
	locator ?: return null
	val href = locator.href
		?.trim()
		?.replace('\\', '/')
		.orEmpty()
	val pageIndex = locator.pageIndex?.takeIf { it >= 0 }?.toString().orEmpty()
	val pageCount = locator.pageCount?.takeIf { it > 0 }?.toString().orEmpty()
	val chapterPageIndex = locator.chapterPageIndex?.takeIf { it >= 0 }?.toString().orEmpty()
	val chapterPageCount = locator.chapterPageCount?.takeIf { it > 0 }?.toString().orEmpty()
	return listOf(
		href.substringBefore('#').substringBefore('?'),
		pageIndex,
		pageCount,
		chapterPageIndex,
		chapterPageCount
	).joinToString("|")
}

internal fun readerRelocationAcknowledgesShellCoverDismissal(
	shellCoverVisible: Boolean,
	pendingRequest: ReaderShellCoverDismissalRequest?,
	foliateSessionId: String,
	locator: ReaderLocator
): Boolean {
	if (!shellCoverVisible) return false
	val request = pendingRequest ?: return false
	val acknowledgedRequestId = readerShellCoverDismissalRequestId(locator.reason)
		?: return false
	if (acknowledgedRequestId != request.requestId) return false
	if (
		request.foliateSessionId != null &&
		request.foliateSessionId != foliateSessionId
	) return false
	if (!readerShellCoverDismissalLocatorMatches(request.locator, locator)) return false
	val href = locator.href?.trim().orEmpty()
	return href.isBlank() || !readerHrefLooksLikeNativeShellCoverBoundary(href)
}

private fun readerShellCoverDismissalLocatorMatches(
	expected: ReaderLocator,
	actual: ReaderLocator
): Boolean {
	val expectedCfi = expected.cfi?.trim()?.takeIf { it.isNotEmpty() }
	if (expectedCfi != null && expectedCfi != actual.cfi?.trim()) return false

	val expectedHref = readerTocHrefKey(expected.href)
	val actualHref = readerTocHrefKey(actual.href)
	if (expectedHref != null && expectedHref != actualHref) return false

	if (expected.pageIndex != null && expected.pageIndex != actual.pageIndex) return false
	if (
		expected.chapterPageIndex != null &&
		expected.chapterPageIndex != actual.chapterPageIndex
	) return false

	val hasStablePositionIdentity = expectedCfi != null ||
		expected.pageIndex != null ||
		expected.chapterPageIndex != null
	if (!hasStablePositionIdentity) {
		val expectedProgress = expected.progress?.takeIf(Double::isFinite)
		if (
			expectedProgress != null &&
			actual.progress?.takeIf(Double::isFinite)?.let {
				kotlin.math.abs(expectedProgress - it) <= 0.000001
			} != true
		) return false
	}
	return true
}
