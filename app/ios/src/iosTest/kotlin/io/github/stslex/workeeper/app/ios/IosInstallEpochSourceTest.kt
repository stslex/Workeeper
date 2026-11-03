// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.stslex.workeeper.app.ios

import androidx.compose.ui.test.runComposeUiTest
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProvider
import io.github.stslex.workeeper.core.data.dataStore.core.IosDataStorePathResolver
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.posix.O_DIRECTORY
import platform.posix.O_NOFOLLOW
import platform.posix.O_RDONLY
import platform.posix.chmod
import platform.posix.getpid
import platform.posix.lstat
import platform.posix.open
import platform.posix.stat
import platform.posix.symlink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class IosInstallEpochSourceTest {

    @Test
    fun publicSourceUsesExcludedInstallationDirectory() = runComposeUiTest {
        val device = checkNotNull(environment("WORKEEPER_DATABASE_TEST_SIMULATOR"))
        assertEquals(device, environment("SIMULATOR_UDID"))
        assertTrue(NSHomeDirectory().contains("/$device/data"), "Expected the owned simulator sandbox")
        val nonce = checkNotNull(environment("WORKEEPER_INSTALL_EPOCH_NONCE"))
        val launch = checkNotNull(environment("WORKEEPER_INSTALL_EPOCH_LAUNCH"))
        val support = withContext(Dispatchers.IO) {
            val root = NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
                .first() as NSURL
            val path = checkNotNull(root.path)
            assertTrue(path.startsWith(NSHomeDirectory() + "/"))
            FileSystem.SYSTEM.createDirectories(path.toPath())
            val directory = "$path/$INSTALLATION_DIRECTORY"
            val owner = "$path/.workeeper-epoch-test-owner"
            if (FileSystem.SYSTEM.exists(directory.toPath())) {
                assertEquals(nonce, readText(owner), "Refuse a pre-existing unowned installation directory")
            } else {
                if (FileSystem.SYSTEM.exists(owner.toPath())) assertEquals(nonce, readText(owner))
                writeText(owner, nonce)
            }
            path
        }
        val sibling = "$support/epoch-policy-control-$nonce"
        withContext(Dispatchers.IO) { FileSystem.SYSTEM.createDirectories(sibling.toPath()) }
        val before = withContext(Dispatchers.IO) { listOf(backupPolicy(support), backupPolicy(sibling)) }
        val source = buildIosInstallEpochSource()
        assertTrue(NSThread.isMainThread, "The public suspending entry must be usable from Main")
        val epoch = source.installEpoch()
        var boundaryWasMain: Boolean? = null
        val observedSource = IosInstallEpochSource(ioBoundary = { boundaryWasMain = NSThread.isMainThread })
        assertNull(boundaryWasMain, "Construction must not enter the I/O boundary")
        assertEquals(epoch, observedSource.installEpoch())
        assertEquals(false, boundaryWasMain)
        withContext(Dispatchers.IO) {
            val directory = "$support/$INSTALLATION_DIRECTORY"
            val canonical = FileSystem.SYSTEM.canonicalize("$directory/$INSTALL_EPOCH_FILE".toPath()).toString()
            val bytes = FileSystem.SYSTEM.read(canonical.toPath()) { readByteArray() }
            assertEquals(epoch, InstallEpoch(RestoreOwnerId(bytes.decodeToString(throwOnInvalidSequence = true))))
            assertEquals(36, bytes.size)
            assertEquals('4', epoch.toString()[14])
            assertTrue(installEpochDirectoryIsExcluded(directory))
            assertEquals(before, listOf(backupPolicy(support), backupPolicy(sibling)))
            assertEquals(epoch, buildIosInstallEpochSource().installEpoch())
            println(
                "WORKEEPER_INSTALL_EPOCH_RECEIPT {\"nonce\":\"$nonce\",\"launch\":\"$launch\"," +
                    "\"pid\":${getpid()},\"expected_udid\":\"$device\"," +
                    "\"actual_udid\":\"${environment("SIMULATOR_UDID")}\"," +
                    "\"path\":\"$canonical\",\"sha256\":\"${bytes.toByteString().sha256().hex()}\"," +
                    "\"io_main\":$boundaryWasMain}",
            )
        }
    }

    @Test
    fun independentCallersConvergeOnFirstUse(): Unit = sandboxTest { sandbox ->
        coroutineScope {
            val ready = Channel<Unit>(8)
            val start = CompletableDeferred<Unit>()
            val sources = List(8) { sandbox.source() }
            val callers = sources.map { source ->
                async(Dispatchers.Default) {
                    ready.send(Unit)
                    start.await()
                    source.installEpoch()
                }
            }
            repeat(8) { ready.receive() }
            start.complete(Unit)
            val epochs = callers.awaitAll()
            assertEquals(1, epochs.toSet().size)
            assertEquals(epochs.first().toString(), readText(sandbox.file))
            assertEquals(epochs.first(), sandbox.source().installEpoch())
            assertTrue(installEpochDirectoryIsExcluded(sandbox.directory))
        }
    }

    @Test
    fun publicationKeepsAnExistingWinner(): Unit = sandboxTest { sandbox ->
        val incumbent = sandbox.source().installEpoch()
        val inode = inode(sandbox.file)
        val candidate = InstallEpoch(RestoreOwnerId("00000000-0000-4000-8000-000000007021"))
        assertNotEquals(incumbent, candidate)
        sandbox.directoryDescriptor { descriptor ->
            assertEquals(incumbent, publishIosInstallEpoch(descriptor, sandbox.directory, candidate))
            assertEquals(inode, inode(sandbox.file), "Exclusive publication preserves the incumbent inode")
            assertEquals(incumbent.toString(), readText(sandbox.file), "Exclusive publication preserves winning bytes")
            assertFails {
                publishIosInstallEpoch(
                    descriptor,
                    sandbox.directory,
                    candidate,
                ) { throw CancellationException("fixture") }
            }
        }
        assertEquals(incumbent, sandbox.source().installEpoch())
        assertEquals(listOf(INSTALL_EPOCH_FILE), FileSystem.SYSTEM.list(sandbox.directory.toPath()).map { it.name })
    }

    @Test
    fun invalidStorageIsPreserved(): Unit = sandboxTest { sandbox ->
        FileSystem.SYSTEM.createDirectories(sandbox.directory.toPath())
        for (invalid in listOf("truncated", "z".repeat(36), "00000000-0000-0000-0000-000000000000")) {
            writeText(sandbox.file, invalid)
            assertFails { sandbox.source().installEpoch() }
            assertEquals(invalid, readText(sandbox.file))
            FileSystem.SYSTEM.delete(sandbox.file.toPath())
        }
        FileSystem.SYSTEM.createDirectory(sandbox.file.toPath())
        assertFails { sandbox.source().installEpoch() }
        assertTrue(FileSystem.SYSTEM.metadata(sandbox.file.toPath()).isDirectory)
        FileSystem.SYSTEM.delete(sandbox.file.toPath())
        val external = "${sandbox.externalRoot}/sentinel"
        val valid = "00000000-0000-4000-8000-000000007022"
        writeText(external, valid)
        assertEquals(0, symlink(external, sandbox.file))
        assertFails { sandbox.source().installEpoch() }
        assertEquals(valid, readText(external))
        assertTrue(FileSystem.SYSTEM.metadata(sandbox.file.toPath()).symlinkTarget != null)
        FileSystem.SYSTEM.delete(sandbox.file.toPath())
        FileSystem.SYSTEM.delete(sandbox.directory.toPath())
        assertEquals(0, symlink(sandbox.externalRoot, sandbox.directory))
        assertFails { sandbox.source().installEpoch() }
        assertEquals(valid, readText(external))
        assertFalse(FileSystem.SYSTEM.exists("${sandbox.externalRoot}/$INSTALL_EPOCH_FILE".toPath()))
        FileSystem.SYSTEM.delete(sandbox.directory.toPath())
        val epoch = sandbox.source().installEpoch()
        val originalInode = inode(sandbox.file)
        assertEquals(0, chmod(sandbox.file, 0u))
        try {
            assertFails { sandbox.source().installEpoch() }
        } finally {
            assertEquals(0, chmod(sandbox.file, 0x180u))
        }
        assertEquals(epoch.toString(), readText(sandbox.file))
        assertEquals(originalInode, inode(sandbox.file))
        for (source in listOf(
            IosInstallEpochSource(
                { NSURL.fileURLWithPath(sandbox.root, true) },
                markExcluded = { error("set fixture") },
            ),
            IosInstallEpochSource({ NSURL.fileURLWithPath(sandbox.root, true) }, verifyExcluded = { false }),
            IosInstallEpochSource(
                { NSURL.fileURLWithPath(sandbox.root, true) },
                verifyExcluded = { error("lookup fixture") },
            ),
        )) {
            val before = readText(sandbox.file)
            assertFails { source.installEpoch() }
            assertEquals(before, readText(sandbox.file))
        }
        assertEquals(epoch, sandbox.source().installEpoch())
    }

    @Test
    fun orphanCandidateIsNeverAdopted(): Unit = sandboxTest { sandbox ->
        FileSystem.SYSTEM.createDirectories(sandbox.directory.toPath())
        val orphan = "${sandbox.directory}/.install_epoch-orphan.tmp"
        val abandoned = "00000000-0000-4000-8000-000000007023"
        writeText(orphan, abandoned)
        val sentinel = "${sandbox.directory}/unrelated"
        writeText(sentinel, "preserve me")
        val epoch = sandbox.source().installEpoch()
        assertNotEquals(abandoned, epoch.toString())
        assertEquals(epoch.toString(), readText(sandbox.file))
        assertEquals(abandoned, readText(orphan))
        assertEquals("preserve me", readText(sentinel))
        assertEquals(epoch, sandbox.source().installEpoch())
    }

    @Test
    fun foreignEpochClearsOnlyRestoreDialogs(): Unit = runBlocking(Dispatchers.IO) {
        val rootA = Sandbox()
        val rootB = Sandbox()
        val fixture = IosGraphFixture()
        try {
            val sourceA = rootA.source()
            val epochA = sourceA.installEpoch()
            val first = fixture.graph(installEpochSource = sourceA)
            val dialogA = AppDialog.UndoRestoreSuccess(RestoreOwnerId(Uuid.random().toString()))
            first.commonDataStore.setThemePreference("DARK")
            first.appDialogRepository.publish(dialogA)
            assertEquals(dialogA, first.appDialogRepository.currentDialog.first())
            first.appScopeLifetime.cancelAndJoin()

            val recreated = fixture.graph(installEpochSource = rootA.source())
            assertEquals(epochA, rootA.source().installEpoch())
            assertEquals(dialogA, recreated.appDialogRepository.currentDialog.first())
            val preferences = DataStoreProvider("app_dialogs_prefs", IosDataStorePathResolver()).dataStore
            val pending = booleanPreferencesKey("pending_undo_restore_success")
            val owner = stringPreferencesKey("pending_undo_restore_success_owner")
            val epochKey = stringPreferencesKey("restore_dialog_install_epoch")
            assertEquals(true, preferences.data.first()[pending])
            assertEquals(dialogA.terminalOwner?.value, preferences.data.first()[owner])
            recreated.appScopeLifetime.cancelAndJoin()

            val sourceB = rootB.source()
            val epochB = sourceB.installEpoch()
            assertNotEquals(epochA, epochB)
            assertEquals(epochA.toString(), readText(rootA.file))
            assertEquals(epochB.toString(), readText(rootB.file))
            val graphB = fixture.graph(installEpochSource = sourceB)
            val foreignDialog = graphB.appDialogRepository.currentDialog.first()
            assertEquals(epochB.toString(), readText(rootB.file), "B's independent file remains authoritative")
            assertNotEquals(readText(rootA.file), readText(rootB.file))
            assertNull(foreignDialog, "A foreign installation dialog must be cleared")
            val cleared = preferences.data.first()
            assertEquals(epochB.toString(), cleared[epochKey])
            assertNull(cleared[pending])
            assertNull(cleared[owner])
            assertEquals("DARK", graphB.commonDataStore.themePreference.first())
            val dialogB = AppDialog.UndoRestoreSuccess(RestoreOwnerId(Uuid.random().toString()))
            assertNotEquals(dialogA, dialogB)
            graphB.appDialogRepository.publish(dialogB)
            assertEquals(dialogB, graphB.appDialogRepository.currentDialog.first())
            assertEquals(dialogB.terminalOwner?.value, preferences.data.first()[owner])
            graphB.appDialogRepository.dismiss(dialogB)
            assertNull(graphB.appDialogRepository.currentDialog.first())
            assertNull(preferences.data.first()[pending])
            assertNull(preferences.data.first()[owner])
        } finally {
            fixture.close()
            rootA.close()
            rootB.close()
        }
    }

    private class Sandbox {
        val root = "${NSTemporaryDirectory()}install-epoch-${Uuid.random()}"
        val externalRoot = "$root-external"
        val directory = "$root/$INSTALLATION_DIRECTORY"
        val file = "$directory/$INSTALL_EPOCH_FILE"

        init {
            assertFalse(FileSystem.SYSTEM.exists(root.toPath()))
            FileSystem.SYSTEM.createDirectory(root.toPath())
            assertFalse(FileSystem.SYSTEM.exists(externalRoot.toPath()))
            FileSystem.SYSTEM.createDirectory(externalRoot.toPath())
        }

        fun source() = IosInstallEpochSource(
            applicationSupport = { NSURL.fileURLWithPath(root, true) },
            ioBoundary = { assertFalse(NSThread.isMainThread) },
        )

        fun <T> directoryDescriptor(block: (Int) -> T): T = installEpochDescriptor(
            open(directory, O_RDONLY or O_DIRECTORY or O_NOFOLLOW),
            "test directory",
            directory,
            block,
        )

        fun close() {
            FileSystem.SYSTEM.deleteRecursively(root.toPath())
            FileSystem.SYSTEM.deleteRecursively(externalRoot.toPath())
        }
    }

    private fun sandboxTest(block: suspend (Sandbox) -> Unit): Unit = runBlocking(Dispatchers.IO) {
        val sandbox = Sandbox()
        try {
            block(sandbox)
        } finally {
            sandbox.close()
        }
    }

    private fun inode(path: String): ULong = memScoped {
        val value = alloc<stat>()
        assertEquals(0, lstat(path, value.ptr))
        value.st_ino
    }

    private fun readText(path: String): String = FileSystem.SYSTEM.read(path.toPath()) { readUtf8() }

    private fun writeText(path: String, value: String) = FileSystem.SYSTEM.write(path.toPath()) { writeUtf8(value) }

    private fun backupPolicy(path: String): Any? = NSURL.fileURLWithPath(path, true)
        .resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null)?.get(NSURLIsExcludedFromBackupKey)

    private fun environment(key: String): String? = NSProcessInfo.processInfo.environment[key] as? String
}
