package paige.navic.domain.manager

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.set
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import paige.navic.data.database.DownloadDatabase
import paige.navic.data.database.CacheDatabase
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.remote.SubsonicClientFactory
import paige.navic.di.DownloadDatabaseMigration5To6
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountDownloadRegistryTest {
	private lateinit var database: DownloadDatabase
	private lateinit var dao: DownloadDao
	private val owner = MutableStateFlow<String?>("alice")
	private lateinit var registry: AccountDownloadRegistry

	@Before
	fun setUp() {
		database = Room.inMemoryDatabaseBuilder<DownloadDatabase>(RuntimeEnvironment.getApplication())
			.setDriver(AndroidSQLiteDriver()).build()
		dao = database.downloadDao()
		registry = AccountDownloadRegistry(dao, owner)
	}

	@After
	fun tearDown() { database.close() }

	@Test
	fun roomValidatesUpgradeAndReopenKeepsUnownedLegacyInaccessible(): Unit = runBlocking {
		val file = File.createTempFile("navic-account-upgrade-", ".db")
		val schemaFile = listOf(
			File("schemas/paige.navic.data.database.DownloadDatabase/5.json"),
			File("composeApp/schemas/paige.navic.data.database.DownloadDatabase/5.json")
		).first { it.isFile }
		try {
			val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
			org.sqlite.JDBC().connect("jdbc:sqlite:${file.absolutePath}", java.util.Properties()).use { connection ->
				connection.createStatement().use { statement ->
					schema.getValue("entities").jsonArray.forEach { entity ->
						val table = entity.jsonObject
						statement.execute(table.getValue("createSql").jsonPrimitive.content.replace(
							"\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content
						))
						table["indices"]?.jsonArray?.forEach { index ->
							statement.execute(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace(
								"\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content
							))
						}
					}
					statement.execute("INSERT INTO DownloadEntity VALUES ('same', 'DOWNLOADED', 1.0, '/legacy.flac', 7, 42, 0)")
					statement.execute("PRAGMA user_version = 5")
				}
			}
			fun open(legacyOwner: String?) = Room.databaseBuilder<DownloadDatabase>(
				RuntimeEnvironment.getApplication(), file.absolutePath
			).setDriver(AndroidSQLiteDriver()).addMigrations(DownloadDatabaseMigration5To6(legacyOwner)).build()
			val upgraded = open(null)
			try {
				assertEquals("", upgraded.downloadDao().getAllDownloadsForMigration().single().ownerId)
			} finally { upgraded.close() }
			val reopened = open("later")
			try {
				val scoped = AccountDownloadRegistry(reopened.downloadDao(), MutableStateFlow("later"))
				assertTrue(scoped.getAllDownloadsList().isEmpty())
				assertNull(scoped.getDownloadById("same"))
				assertEquals("/legacy.flac", reopened.downloadDao().getAllDownloadsForMigration().single().filePath)
			} finally { reopened.close() }
		} finally {
			file.delete()
			File(file.absolutePath + "-wal").delete()
			File(file.absolutePath + "-shm").delete()
		}
	}

	@Test
	fun logoutRevokesOwnerBeforeJoiningWorkersAndRetainsAudioRows(): Unit = runBlocking {
		val cache = Room.inMemoryDatabaseBuilder<CacheDatabase>(RuntimeEnvironment.getApplication())
			.setDriver(AndroidSQLiteDriver()).build()
		val settings = MapSettings().apply {
			this["instanceUrl"] = "https://music.example"
			this["username"] = "alice"
			this["password"] = "secret"
		}
		val identity = DownloadAccountIdentity(settings)
		val originalOwner = identity.ownerId.value!!
		val lifetime = AuthenticatedSessionLifetime()
		val session = SessionManager(settings, PreferenceManager(settings), cache.syncActionDao(),
			lifetime, SubsonicClientFactory(), ArtworkColorManager(cache.artworkColorDao()), identity,
			PlaybackAccountBoundary(identity.startupOwnerId, identity.legacyOwnerId, UnconfinedTestDispatcher()))
		dao.insertDownload(row(originalOwner))
		val scoped = AccountDownloadRegistry(dao, session.ownerId)
		val started = CompletableDeferred<Unit>()
		val cancelling = CompletableDeferred<Unit>()
		val allowJoin = CompletableDeferred<Unit>()
		try {
			lifetime.currentScope()!!.launch {
				try { started.complete(Unit); awaitCancellation() }
				finally { withContext(NonCancellable) { cancelling.complete(Unit); allowJoin.await() } }
			}
			started.await()
			val logout = async { session.logout() }
			cancelling.await()
			assertNull(session.ownerId.value)
			assertTrue(scoped.getAllDownloadsList().isEmpty())
			assertFalse(logout.isCompleted)
			allowJoin.complete(Unit)
			logout.await()
			assertEquals(originalOwner, session.startupOwnerId)
			assertEquals(originalOwner, dao.getAllDownloadsForMigration().single().ownerId)
			assertNull(DownloadAccountIdentity(settings).ownerId.value)
		} finally { allowJoin.complete(Unit); lifetime.endSession(); cache.close() }
	}

	@Test
	fun cancelledLoginFinishesAcceptedAccountTransitionAfterOutgoingWorkersJoin(): Unit = runBlocking {
		val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
			createContext("/") { exchange ->
				val body = """{"subsonic-response":{"status":"ok","version":"1.16.1"}}""".toByteArray()
				exchange.responseHeaders.set("Content-Type", "application/json")
				exchange.sendResponseHeaders(200, body.size.toLong())
				exchange.responseBody.use { it.write(body) }
			}
			start()
		}
		val url = "http://127.0.0.1:${server.address.port}"
		val cache = Room.inMemoryDatabaseBuilder<CacheDatabase>(RuntimeEnvironment.getApplication())
			.setDriver(AndroidSQLiteDriver()).build()
		val settings = MapSettings().apply {
			this["instanceUrl"] = url
			this["username"] = "alice"
			this["password"] = "old"
		}
		val identity = DownloadAccountIdentity(settings)
		val lifetime = AuthenticatedSessionLifetime()
		val session = SessionManager(settings, PreferenceManager(settings), cache.syncActionDao(),
			lifetime, SubsonicClientFactory(), ArtworkColorManager(cache.artworkColorDao()), identity,
			PlaybackAccountBoundary(identity.startupOwnerId, identity.legacyOwnerId, UnconfinedTestDispatcher()))
		val outgoing = lifetime.currentScope()!!
		val started = CompletableDeferred<Unit>()
		val cancelling = CompletableDeferred<Unit>()
		val allowJoin = CompletableDeferred<Unit>()
		try {
			outgoing.launch {
				try { started.complete(Unit); awaitCancellation() }
				finally { withContext(NonCancellable) { cancelling.complete(Unit); allowJoin.await() } }
			}
			started.await()
			val login = launch { session.login(url, "bob", "new") }
			cancelling.await()
			assertNull(session.ownerId.value)
			assertNull(lifetime.currentScope())
			login.cancel()
			assertFalse(login.isCompleted)
			allowJoin.complete(Unit)
			login.join()
			assertEquals(musicDownloadOwnerId(url, "bob"), session.ownerId.value)
			assertEquals("bob", settings.getString("username", ""))
			assertEquals("new", settings.getString("password", ""))
			assertTrue(session.isLoggedIn.value)
			assertTrue(assertNotNull(lifetime.currentScope()) !== outgoing)
		} finally {
			allowJoin.complete(Unit)
			withContext(NonCancellable) { lifetime.endSession() }
			cache.close()
			server.stop(0)
		}
	}

	@Test
	fun collidingIdsRetainDistinctAudioThroughLogoutAndReturn(): Unit = runBlocking {
		val files = listOf("alice", "bob").map { account ->
			File.createTempFile("navic-account-$account-", ".audio").apply { writeText(account) }
		}
		try {
			dao.insertDownload(row("alice", path = files[0].absolutePath))
			dao.insertDownload(row("bob", path = files[1].absolutePath))
			assertEquals("alice", File(assertNotNull(registry.getDownloadById("same")).filePath!!).readText())
			owner.value = null
			assertNull(registry.getDownloadById("same"))
			assertEquals(emptyList(), registry.getAllDownloadsList())
			assertEquals(emptyList(), registry.getAllDownloads().first())
			owner.value = "bob"
			assertEquals("bob", File(assertNotNull(registry.getDownloadById("same")).filePath!!).readText())
			owner.value = "alice"
			assertEquals("alice", File(assertNotNull(registry.getDownloadById("same")).filePath!!).readText())
			assertEquals(2, dao.getAllDownloadsForMigration().size)
		} finally { files.forEach { it.delete() } }
	}

	@Test
	fun rowsAndCountsSwitchWhileCollectedAndOwnerlessLegacyNeverAppears(): Unit = runBlocking {
		dao.insertDownload(row("alice"))
		dao.insertDownload(row("bob"))
		dao.insertDownload(row(""))
		val emissions = Channel<List<DownloadEntity>>(Channel.UNLIMITED)
		val collector = launch { registry.getAllDownloads().collect { emissions.send(it) } }
		suspend fun awaitOwner(expected: String): List<DownloadEntity> {
			while (true) {
				val rows = emissions.receive()
				if (rows.isNotEmpty()) {
					assertTrue(rows.all { it.ownerId == expected })
					return rows
				}
			}
		}
		try {
			awaitOwner("alice")
			assertEquals(1, registry.getDownloadsCount().first { it > 0 })
			owner.value = null
			assertTrue(emissions.receive().isEmpty())
			assertEquals(0, registry.getDownloadsCount().first())
			owner.value = "bob"
			awaitOwner("bob")
			assertFalse(registry.owns(row("alice")))
			assertFalse(registry.owns(row("")))
			assertTrue(registry.owns(row("bob")))
		} finally { collector.cancelAndJoin(); emissions.close() }
	}

	@Test
	fun everyMutationFailsClosedWithoutOwner(): Unit = runBlocking {
		dao.insertDownload(row("alice", status = DownloadStatus.DOWNLOADING))
		dao.insertDownload(row("", status = DownloadStatus.DOWNLOADING))
		val before = dao.getAllDownloadsForMigration()
		owner.value = null
		assertEquals(0L, registry.enqueueFreshIntent("same", 10))
		assertNull(registry.claimNextQueuedDownload())
		assertEquals(0, registry.recoverInterruptedDownloads())
		assertEquals(0, registry.cancelPendingIntent("same"))
		assertEquals(0, registry.retryFailedIntent("same", 1, 10))
		assertEquals(0, registry.updateProgressIfCurrent("same", 1, DownloadStatus.DOWNLOADING, .5f))
		assertEquals(0, registry.completeIfCurrent("same", 1, DownloadStatus.DOWNLOADED, 1f, "/new"))
		assertEquals(0, registry.requeueIfCurrent("same", 1))
		registry.deleteDownload("same")
		assertEquals(0, registry.deleteDownloadIfCurrent("same", 1, DownloadStatus.DOWNLOADING, "/alice.audio"))
		assertEquals(0, registry.deleteFailedDownloadIfCurrent("same", 1))
		registry.clearAllDownloads()
		assertEquals(before, dao.getAllDownloadsForMigration())
	}

	@Test
	fun queueClaimExcludesLiveSongAndAllMutationPredicatesIncludeOwner(): Unit = runBlocking {
		dao.insertDownload(row("bob", status = DownloadStatus.DOWNLOADING))
		val bob = dao.getDownloadById("bob", "same")
		registry.enqueueFreshIntent("same", 1)
		registry.enqueueFreshIntent("next", 2)
		val next = assertNotNull(registry.claimNextQueuedDownload(setOf("same")))
		assertEquals("next", next.songId)
		assertEquals(DownloadStatus.QUEUED, registry.getDownloadById("same")!!.status)
		val same = assertNotNull(registry.claimNextQueuedDownload())
		assertEquals(1, registry.updateProgressIfCurrent("same", same.intentGeneration, DownloadStatus.DOWNLOADING, .5f))
		assertEquals(1, registry.completeIfCurrent("same", same.intentGeneration, DownloadStatus.FAILED, 0f, null))
		assertEquals(1, registry.retryFailedIntent("same", same.intentGeneration, 3))
		assertNotNull(registry.claimNextQueuedDownload())
		assertEquals(1, registry.requeueIfCurrent("same", same.intentGeneration))
		assertEquals(1, registry.recoverInterruptedDownloads())
		assertEquals(1, registry.cancelPendingIntent("same"))
		registry.deleteDownload("same")
		registry.clearAllDownloads()
		assertEquals(bob, dao.getDownloadById("bob", "same"))
		assertEquals(listOf(bob), dao.getAllDownloadsForMigration())
	}

	@Test
	fun staleDeletionCannotRemoveFreshGenerationOrOtherOwner(): Unit = runBlocking {
		dao.insertDownload(row("bob"))
		val old = registry.enqueueFreshIntent("same", 1)
		val fresh = registry.enqueueFreshIntent("same", 2)
		assertEquals(0, registry.deleteDownloadIfCurrent("same", old, DownloadStatus.QUEUED, null))
		assertEquals(fresh, registry.getDownloadById("same")!!.intentGeneration)
		assertEquals(1, registry.deleteDownloadIfCurrent("same", fresh, DownloadStatus.QUEUED, null))
		assertNull(registry.getDownloadById("same"))
		assertNotNull(dao.getDownloadById("bob", "same"))
	}

	@Test
	fun delayedDuplicateDeletionCannotRemoveRecreatedGenerationWithNewPublishedPath(): Unit = runBlocking {
		val oldFile = File.createTempFile("navic-old-publication-", ".audio").apply { writeText("old audio") }
		val newFile = File.createTempFile("navic-new-publication-", ".audio").apply { delete() }
		val captured = row("alice", path = oldFile.absolutePath)
		dao.insertDownload(captured)
		val capturedByDuplicate = CompletableDeferred<Unit>()
		val releaseDuplicate = CompletableDeferred<Unit>()
		suspend fun deleteCaptured(): Boolean = deletePublishedAudio(
			deleteRow = { registry.deleteDownloadIfCurrent(captured.songId, captured.intentGeneration, captured.status, captured.filePath) == 1 },
			deleteFiles = { oldFile.delete(); Unit }
		)
		val duplicate = async {
			capturedByDuplicate.complete(Unit)
			releaseDuplicate.await()
			deleteCaptured()
		}
		try {
			capturedByDuplicate.await()
			assertTrue(deleteCaptured())
			assertFalse(oldFile.exists())
			val generation = registry.enqueueFreshIntent("same", 42)
			assertEquals(captured.intentGeneration, generation)
			assertNotNull(registry.claimNextQueuedDownload())
			assertTrue(publishAudioDownload(
				finalPath = newFile.absolutePath,
				write = { File(it).writeText("new audio") },
				isUsable = { File(it).length() > 0 },
				move = { source, target -> File(source).renameTo(File(target)) },
				complete = { registry.completeIfCurrent("same", generation, DownloadStatus.DOWNLOADED, 1f, it) == 1 },
				delete = { File(it).delete(); Unit }
			))
			releaseDuplicate.complete(Unit)
			assertFalse(duplicate.await())
			val current = assertNotNull(registry.getDownloadById("same"))
			assertEquals(DownloadStatus.DOWNLOADED, current.status)
			assertEquals(newFile.absolutePath, current.filePath)
			assertEquals("new audio", newFile.readText())
		} finally {
			releaseDuplicate.complete(Unit)
			duplicate.cancelAndJoin()
			oldFile.delete()
			newFile.delete()
			File(newFile.absolutePath + ".part").delete()
		}
	}

	@Test
	fun cancelledNullPathSnapshotCannotDeleteNewQueuedActiveOrCompletedWork(): Unit = runBlocking {
		dao.insertDownload(row("alice", status = DownloadStatus.NOT_DOWNLOADED, path = null).copy(cancelled = true))
		assertEquals(1, registry.deleteDownloadIfCurrent("same", 1, DownloadStatus.NOT_DOWNLOADED, null))
		assertEquals(1L, registry.enqueueFreshIntent("same", 42))
		assertEquals(0, registry.deleteDownloadIfCurrent("same", 1, DownloadStatus.NOT_DOWNLOADED, null))
		assertNotNull(registry.claimNextQueuedDownload())
		assertEquals(0, registry.deleteDownloadIfCurrent("same", 1, DownloadStatus.NOT_DOWNLOADED, null))
		assertEquals(1, registry.completeIfCurrent("same", 1, DownloadStatus.DOWNLOADED, 1f, "/new.audio"))
		assertEquals(0, registry.deleteDownloadIfCurrent("same", 1, DownloadStatus.NOT_DOWNLOADED, null))
		assertEquals("/new.audio", registry.getDownloadById("same")!!.filePath)
	}

	@Test
	fun publicationRetainsCommittedBytesWhenOwnerIsRevokedBeforeAcknowledgment(): Unit = runBlocking {
		dao.insertDownload(row("alice", status = DownloadStatus.DOWNLOADING))
		dao.insertDownload(row("bob"))
		val committed = CompletableDeferred<Unit>()
		val releaseAcknowledgment = CompletableDeferred<Unit>()
		val delayedDao = object : DownloadDao by dao {
			override suspend fun completeIfCurrent(
				ownerId: String, songId: String, generation: Long,
				status: DownloadStatus, progress: Float, filePath: String?
			): Int {
				val result = dao.completeIfCurrent(ownerId, songId, generation, status, progress, filePath)
				assertEquals(1, result)
				committed.complete(Unit)
				releaseAcknowledgment.await()
				return result
			}
		}
		val scoped = AccountDownloadRegistry(delayedDao, owner)
		val finalFile = File.createTempFile("navic-committed-account-", ".audio").apply { delete() }
		var acknowledgment: Int? = null
		val publisher = launch {
			publishAudioDownload(
				finalPath = finalFile.absolutePath,
				write = { File(it).writeText("alice audio") },
				isUsable = { File(it).length() > 0 },
				move = { source, target -> File(source).renameTo(File(target)) },
				complete = { path ->
					scoped.completeIfCurrent("same", 1, DownloadStatus.DOWNLOADED, 1f, path)
						.also { acknowledgment = it } == 1
				},
				delete = { File(it).delete(); Unit }
			)
		}
		try {
			committed.await()
			owner.value = null
			publisher.cancel()
			assertNull(scoped.getDownloadById("same"))
			releaseAcknowledgment.complete(Unit)
			publisher.join()
			assertEquals(1, acknowledgment)
			assertEquals("alice audio", finalFile.readText())
			assertFalse(File(finalFile.absolutePath + ".part").exists())
			val alice = assertNotNull(dao.getDownloadById("alice", "same"))
			assertEquals(DownloadStatus.DOWNLOADED, alice.status)
			assertEquals(finalFile.absolutePath, alice.filePath)
			owner.value = "bob"
			assertEquals("/bob.audio", scoped.getDownloadById("same")!!.filePath)
			owner.value = "alice"
			assertEquals(finalFile.absolutePath, scoped.getDownloadById("same")!!.filePath)
		} finally {
			releaseAcknowledgment.complete(Unit)
			publisher.cancelAndJoin()
			finalFile.delete()
			File(finalFile.absolutePath + ".part").delete()
		}
	}

	@Test
	fun publicationRejectedAfterOwnerRevocationDoesNotChangeOldRow(): Unit = runBlocking {
		dao.insertDownload(row("alice", status = DownloadStatus.DOWNLOADING))
		val before = dao.getDownloadById("alice", "same")
		owner.value = null
		val files = mutableMapOf<String, String>()
		assertFalse(publishAudioDownload(
			finalPath = "rejected.audio",
			write = { files[it] = "uncommitted audio" },
			isUsable = { files[it]?.isNotEmpty() == true },
			move = { source, target -> files[target] = files.remove(source)!!; true },
			complete = { registry.completeIfCurrent("same", 1, DownloadStatus.DOWNLOADED, 1f, it) == 1 },
			delete = { files.remove(it); Unit }
		))
		assertTrue(files.isEmpty())
		assertEquals(before, dao.getDownloadById("alice", "same"))
	}

	@Test
	fun failedDeletionRejectsSameGenerationRetryAndOtherOwners(): Unit = runBlocking {
		dao.insertDownload(row("alice", status = DownloadStatus.FAILED))
		dao.insertDownload(row("bob", status = DownloadStatus.FAILED))
		assertEquals(0, registry.deleteFailedDownloadIfCurrent("same", 0))
		assertEquals(1, registry.retryFailedIntent("same", 1, 42))
		assertEquals(1L, registry.getDownloadById("same")!!.intentGeneration)
		assertEquals(0, registry.deleteFailedDownloadIfCurrent("same", 1))
		assertNotNull(registry.claimNextQueuedDownload())
		assertEquals(0, registry.deleteFailedDownloadIfCurrent("same", 1))
		assertEquals(1, registry.completeIfCurrent("same", 1, DownloadStatus.FAILED, 0f, null))
		assertEquals(1, registry.deleteFailedDownloadIfCurrent("same", 1))
		assertNull(registry.getDownloadById("same"))
		assertEquals(DownloadStatus.FAILED, dao.getDownloadById("bob", "same")!!.status)
	}

	@Test
	fun mutationCapturesOwnerBeforeSuspensionAndCannotWriteNewAccountsRow(): Unit = runBlocking {
		dao.insertDownload(row("alice"))
		dao.insertDownload(row("bob"))
		val entered = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val suspendedDao = object : DownloadDao by dao {
			override suspend fun deleteDownload(ownerId: String, songId: String) {
				entered.complete(Unit)
				release.await()
				dao.deleteDownload(ownerId, songId)
			}
		}
		val operation = async { AccountDownloadRegistry(suspendedDao, owner).deleteDownload("same") }
		entered.await()
		owner.value = "bob"
		release.complete(Unit)
		operation.await()
		assertNotNull(dao.getDownloadById("bob", "same"))
		assertNull(dao.getDownloadById("alice", "same"))
	}

	@Test
	fun suspendedReadDoesNotDeliverOldRowsAfterSwitchAndCancellationDoesNotWrite(): Unit = runBlocking {
		dao.insertDownload(row("alice"))
		dao.insertDownload(row("bob"))
		val entered = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val suspendedDao = object : DownloadDao by dao {
			override suspend fun getAllDownloadsList(ownerId: String): List<DownloadEntity> {
				val rows = dao.getAllDownloadsList(ownerId)
				entered.complete(Unit)
				release.await()
				return rows
			}
		}
		val operation = async { AccountDownloadRegistry(suspendedDao, owner).getAllDownloadsList() }
		entered.await()
		owner.value = "bob"
		release.complete(Unit)
		assertTrue(operation.await().isEmpty())
		val blocked = CompletableDeferred<Unit>()
		val cancelDao = object : DownloadDao by dao {
			override suspend fun clearAllDownloads(ownerId: String) {
				blocked.await()
				dao.clearAllDownloads(ownerId)
			}
		}
		val deletion = launch(start = CoroutineStart.UNDISPATCHED) {
			AccountDownloadRegistry(cancelDao, owner).clearAllDownloads()
		}
		owner.value = null
		deletion.cancelAndJoin()
		assertEquals(2, dao.getAllDownloadsForMigration().size)
	}

	private fun row(owner: String, status: DownloadStatus = DownloadStatus.DOWNLOADED, path: String? = "/$owner.audio") =
		DownloadEntity("same", status, 1f, path, intentGeneration = 1, ownerId = owner)
}
