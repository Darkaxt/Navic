package paige.navic.reader

import paige.navic.domain.manager.PreferenceManager

fun PreferenceManager.readerAnnotationState(): ReaderAnnotationState =
	ReaderAnnotationState(decodeReaderAnnotations(readerAnnotationsJson))

fun PreferenceManager.setReaderAnnotationState(state: ReaderAnnotationState) {
	readerAnnotationsJson = encodeReaderAnnotations(state.annotations)
}

fun PreferenceManager.readerBookmarkState(): ReaderBookmarkState =
	ReaderBookmarkState(decodeReaderBookmarks(readerBookmarksJson))

fun PreferenceManager.setReaderBookmarkState(state: ReaderBookmarkState) {
	readerBookmarksJson = encodeReaderBookmarks(state.bookmarks)
}

fun PreferenceManager.persistReaderMarksIfChanged(
	previous: ReaderControllerState,
	next: ReaderControllerState
) {
	if (previous.annotations != next.annotations) {
		setReaderAnnotationState(next.annotations)
	}
	if (previous.bookmarks != next.bookmarks) {
		setReaderBookmarkState(next.bookmarks)
	}
}
