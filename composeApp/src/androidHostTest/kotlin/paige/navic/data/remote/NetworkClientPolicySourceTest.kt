package paige.navic.data.remote

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NetworkClientPolicySourceTest {
	@Test
	fun productionClientsUseTheSharedFactoryAndSubsonicBaseline() {
		val sourceRoot = sourceRoot()
		val directConstruction = sourceRoot.walkTopDown()
			.filter { file ->
				file.isFile &&
					file.extension == "kt" &&
					file.name != "NetworkClientFactory.kt"
			}
			.flatMap { file ->
				file.readLines().asSequence().mapIndexedNotNull { index, line ->
					if (DIRECT_HTTP_CLIENT_CONSTRUCTION.containsMatchIn(line)) {
						"${file.relativeTo(sourceRoot).invariantSeparatorsPath}:${index + 1}"
					} else {
						null
					}
				}
			}
			.toList()

		assertEquals(emptyList(), directConstruction)
		assertContains(
			File(sourceRoot, "data/remote/SubsonicClientFactory.kt").readText(),
			"installNavicNetworkBaseline"
		)
		val managerModule = File(sourceRoot, "di/ManagerModule.kt").readText()
		assertContains(managerModule, "single { NetworkClientFactory() }")
		assertFalse("singleOf(::NetworkClientFactory)" in managerModule)
	}

	private fun sourceRoot(): File = listOf(
		File("src/commonMain/kotlin/paige/navic"),
		File("composeApp/src/commonMain/kotlin/paige/navic")
	).firstOrNull { it.isDirectory }
		?: error("Unable to locate common source root")

	private companion object {
		val DIRECT_HTTP_CLIENT_CONSTRUCTION = Regex("""\bHttpClient\s*(?:\(|\{)""")
	}
}
