package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ReaderRendererBusyPopupOwnerTest {
	@Test
	fun detachedPopupContentInheritsEveryAnchorViewTreeOwnerBeforeComposition() {
		val source = readerRendererBusyPopupSource()
		val construction = source
			.substringAfter("val contentView = ComposeView(anchor.context).apply {")
			.substringBefore("\n\t\tPopupWindow(")
		val inheritance = source.substringAfter(
			"internal fun inheritReaderRendererBusyPopupOwners("
		)

		assertContains(construction, "inheritReaderRendererBusyPopupOwners(anchor, this)")
		assertTrue(
			construction.indexOf("inheritReaderRendererBusyPopupOwners(anchor, this)") <
				construction.indexOf("setParentCompositionContext(parentComposition)")
		)
		assertTrue(
			construction.indexOf("inheritReaderRendererBusyPopupOwners(anchor, this)") <
				construction.indexOf("setContent {")
		)
		assertContains(
			inheritance,
			"contentView.setViewTreeLifecycleOwner(anchor.findViewTreeLifecycleOwner())"
		)
		assertContains(
			inheritance,
			"contentView.setViewTreeSavedStateRegistryOwner(anchor.findViewTreeSavedStateRegistryOwner())"
		)
		assertContains(
			inheritance,
			"contentView.setViewTreeViewModelStoreOwner(anchor.findViewTreeViewModelStoreOwner())"
		)
		assertContains(inheritance, "anchor.findViewTreeOnBackPressedDispatcherOwner()")
		assertContains(inheritance, "contentView::setViewTreeOnBackPressedDispatcherOwner")
	}
}

private fun readerRendererBusyPopupSource(): String {
	var current: File? = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(
			root,
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderRendererBusyPopup.android.kt"
		)
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate ReaderRendererBusyPopup.android.kt")
}
