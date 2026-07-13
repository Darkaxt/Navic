package paige.navic.ui.components.sheets

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class Material3SheetCompatibilitySourceTest {
	@Test
	fun invisibleMaterial3AccessIsConfinedToVersionPinnedAdapter() {
		val wrapper = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/components/sheets/ModalBottomSheet.kt"
		).readText()
		val adapter = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/components/sheets/Material3SheetMotionCompatibility.kt"
		).readText()

		assertFalse(wrapper.contains("INVISIBLE_REFERENCE"))
		assertFalse(wrapper.contains("showMotionSpec"))
		assertFalse(wrapper.contains("hideMotionSpec"))
		assertContains(wrapper, "sheetState.applyNavicSheetMotionSpecs()")
		assertContains(adapter, "@Suppress(\"INVISIBLE_REFERENCE\")")
		assertContains(adapter, "material3 = 1.11.0-alpha07")
		assertContains(adapter, "showMotionSpec = SheetShowMotionSpec")
		assertContains(adapter, "hideMotionSpec = SheetHideMotionSpec")
	}

	private fun sourceFile(path: String): File =
		listOf(File(path), File("../$path")).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}
