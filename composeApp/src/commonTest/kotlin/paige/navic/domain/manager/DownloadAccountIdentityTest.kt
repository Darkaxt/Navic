package paige.navic.domain.manager

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.set
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadAccountIdentityTest {
	@Test
	fun normalizedServerAndExactUsernameDefineOwnerWithoutPassword() {
		val settings = MapSettings().apply {
			this["instanceUrl"] = "https://MUSIC.example:443/app/"
			this["username"] = "alice"
			this["password"] = "old"
		}
		val original = DownloadAccountIdentity(settings)
		settings["password"] = "new"
		assertEquals(original.ownerId.value, DownloadAccountIdentity(settings).ownerId.value)
		assertEquals(original.ownerId.value, musicDownloadOwnerId("music.example", "alice"))
		assertNotEquals(original.ownerId.value, musicDownloadOwnerId("music.example", "Alice"))
		assertNotEquals(original.ownerId.value, musicDownloadOwnerId("other.example", "alice"))
		assertTrue(original.ownerId.value!!.matches(Regex("[a-f0-9]{64}")))
	}

	@Test
	fun startupLegacyBindingNeverChangesAfterLoginOrRestart() {
		val settings = MapSettings()
		val identity = DownloadAccountIdentity(settings)
		assertNull(identity.startupOwnerId)
		assertNull(identity.legacyOwnerId)
		identity.activate("https://music.example", "alice")
		settings["instanceUrl"] = "https://music.example"
		settings["username"] = "alice"
		assertNull(identity.startupOwnerId)
		assertNull(DownloadAccountIdentity(settings).legacyOwnerId)
	}

	@Test
	fun loggedInUpgradeRetainsOriginalBindingThroughSwitchAndLogout() {
		val settings = MapSettings().apply {
			this["instanceUrl"] = "https://music.example"
			this["username"] = "alice"
		}
		val identity = DownloadAccountIdentity(settings)
		val alice = identity.ownerId.value
		identity.deactivate()
		assertNull(identity.ownerId.value)
		identity.activate("https://music.example", "bob")
		settings["username"] = "bob"
		assertNotEquals(alice, identity.ownerId.value)
		assertEquals(alice, DownloadAccountIdentity(settings).legacyOwnerId)
		identity.activate("https://music.example", "alice")
		assertEquals(alice, identity.ownerId.value)
	}
}
