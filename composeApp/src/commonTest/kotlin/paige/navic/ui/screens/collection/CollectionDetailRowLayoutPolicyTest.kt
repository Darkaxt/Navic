package paige.navic.ui.screens.collection

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionDetailRowLayoutPolicyTest {
	@Test
	fun localAndAurralAlbumRowsUseSameLeadingWidth() {
		assertEquals(25.dp, collectionDetailAlbumTrackLeadingWidth())
	}
}
