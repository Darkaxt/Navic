package paige.navic.ui.components.sheets

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import paige.navic.util.ui.SheetHideMotionSpec
import paige.navic.util.ui.SheetShowMotionSpec

// Pinned contract: gradle/libs.versions.toml material3 = 1.11.0-alpha07.
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("INVISIBLE_REFERENCE")
internal fun SheetState.applyNavicSheetMotionSpecs() {
	showMotionSpec = SheetShowMotionSpec
	hideMotionSpec = SheetHideMotionSpec
}
