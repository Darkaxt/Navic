package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.*
import paige.navic.reader.*

/** Wire parsing only. Synthetic messages do not establish Live layout, initial semantic
 * admission, Native allocation, physical readiness or production installation. */
class ReaderEngineResolvedLayoutTransportHostTest {
    @Test fun originalDestinationDecoderCarriesOnlyNumericResolvedLayoutMetadata() {
        val event = decode(validLayout())
        val layout = assertNotNull(layout(event), "Actual destination result must retain subordinate layout metadata")
        for ((key, expected) in listOf("LayoutGeneration" to 7L, "ViewGeneration" to 3L,
            "NavigationGeneration" to 5L, "SectionIndex" to 0, "PageIndex" to 1, "PageCount" to 8,
            "Width" to 560.0, "Height" to 420.0)) {
            val actual = layout.javaClass.getMethod("get$key").invoke(layout)
            assertTrue(actual == expected, "Only exact numeric resolved-layout fields may cross the wire")
        }
        assertTrue(event.commandId == "public-synthetic-layout-query")
        assertTrue(event.foliateSessionId == "public-synthetic-layout-session")
        assertEquals(11L, event.destinationCommitIdentity.commitSequence)
    }

    @Test fun missingLayoutDoesNotUpgradeSemanticQueryToLayoutReadiness() {
        assertNull(layout(decode(null)))
        assertNull(layout(decode(JsonNull)))
    }

    @Test fun malformedLayoutCannotBorrowAValidSemanticDestination() {
        for (key in validLayout().keys) {
            assertNull(layout(decode(JsonObject(validLayout() - key))), "Incomplete layout cannot authorize allocation")
            assertNull(layout(decode(JsonObject(validLayout() + (key to JsonPrimitive("7"))))),
                "String-coerced metadata cannot authorize allocation")
        }
        assertNull(layout(decode(JsonPrimitive(true))))
        assertNull(layout(decode(JsonArray(emptyList()))))
        assertNull(layout(decode(JsonObject(validLayout() + ("unapproved" to JsonPrimitive("public-synthetic-extra"))))))
    }

    @Test fun invalidGenerationsAndCoordinatesCannotAuthorizeLayout() {
        for ((key, values) in mapOf(
            "layoutGeneration" to listOf(-1L, 0L, 9_007_199_254_740_992L),
            "viewGeneration" to listOf(-1L, 0L, 9_007_199_254_740_992L),
            "navigationGeneration" to listOf(-1L, 9_007_199_254_740_992L),
            "sectionIndex" to listOf(-1L, 2_147_483_648L),
            "pageIndex" to listOf(-1L, 8L, 2_147_483_648L),
            "pageCount" to listOf(-1L, 0L, 1L, 2_147_483_648L)
        )) for (value in values) {
            assertNull(layout(decode(JsonObject(validLayout() + (key to JsonPrimitive(value))))),
                "Invalid generation/coordinate cannot authorize layout")
        }
        for (key in listOf("layoutGeneration", "viewGeneration", "navigationGeneration", "sectionIndex", "pageIndex", "pageCount")) {
            assertNull(layout(decode(JsonObject(validLayout() + (key to JsonPrimitive(1.5))))))
        }
    }

    @Test fun invalidGeometryCannotAuthorizeLayout() {
        for (key in listOf("width", "height")) for (value in listOf(0.0, -1.0)) {
            assertNull(layout(decode(JsonObject(validLayout() + (key to JsonPrimitive(value))))))
        }
    }

    private fun validLayout() = buildJsonObject {
        put("layoutGeneration", 7L); put("viewGeneration", 3L); put("navigationGeneration", 5L)
        put("sectionIndex", 0); put("pageIndex", 1); put("pageCount", 8)
        put("width", 560.0); put("height", 420.0)
    }
    private fun decode(metadata: JsonElement?): ReaderBridgeEvent.CurrentDestinationResolved {
        val result = decodeReaderBridgeEvent(buildJsonObject {
            put("type", "currentDestinationResolved"); put("commandId", "public-synthetic-layout-query")
            put("foliateSessionId", "public-synthetic-layout-session")
            put("destinationFoliateSessionId", "public-synthetic-layout-session")
            put("destinationCommitSequence", 11L); put("pageIndex", 1)
            metadata?.let { put("resolvedLayout", it) }
        }.toString())
        assertTrue(result is ReaderBridgeEvent.CurrentDestinationResolved, "Subordinate layout must not fabricate or erase semantic query identity")
        return result
    }
    private fun layout(event: ReaderBridgeEvent.CurrentDestinationResolved): Any? {
        val getter = event.javaClass.methods.singleOrNull { it.name == "getResolvedLayout" && it.parameterCount == 0 }
        assertNotNull(getter, "Actual original destination decoder needs a subordinate resolved-layout carrier")
        return getter.invoke(event)
    }
}
