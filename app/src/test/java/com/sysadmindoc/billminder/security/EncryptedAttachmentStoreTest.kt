package com.sysadmindoc.billminder.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EncryptedAttachmentStoreTest {

    // One context for the whole test. Re-reading ApplicationProvider per access re-enters
    // Robolectric's cache-directory setup, which loses a file written immediately after the
    // directory is first created.
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val workspace: File = EncryptedAttachmentStore.cacheWorkspace(context)

    @Before
    fun createWorkspace() {
        workspace.mkdirs()
        workspace.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun plantViewCopy(name: String = "view-fixture"): File =
        File(workspace, name).apply { writeText("receipt plaintext") }

    @Test
    fun storedNamesRejectPathTraversal() {
        assertFalse(EncryptedAttachmentStore.isSafeStoredName(""))
        assertFalse(EncryptedAttachmentStore.isSafeStoredName("../receipt.bin"))
        assertFalse(EncryptedAttachmentStore.isSafeStoredName("nested/receipt.bin"))
        assertFalse(EncryptedAttachmentStore.isSafeStoredName("nested\\receipt.bin"))
    }

    @Test
    fun storedNamesAcceptGeneratedFileNames() {
        assertTrue(EncryptedAttachmentStore.isSafeStoredName("8f2c8e0d-8ac2-4f09-9fe8-9d69f8f7d2f1.bin"))
    }

    @Test
    fun releasingAViewedReceiptLeavesNoPlaintextBehind() {
        val copy = plantViewCopy()
        assertTrue("fixture was not written", copy.isFile)

        assertTrue(EncryptedAttachmentStore.releaseCachedView(context, copy))

        assertFalse("plaintext survived its viewing", copy.exists())
        assertEquals(
            "the cache workspace still holds a decrypted receipt",
            emptyList<String>(),
            workspace.listFiles().orEmpty().map { it.name }
        )
    }

    @Test
    fun releasingTwiceStillReportsNoPlaintextRemains() {
        val copy = plantViewCopy()
        assertTrue(EncryptedAttachmentStore.releaseCachedView(context, copy))

        // The screen releases on resume and again on dispose; the second call must not look like a
        // failure just because the first one already did the work.
        assertTrue(EncryptedAttachmentStore.releaseCachedView(context, copy))
    }

    /**
     * The guard is asserted through its decision rather than through a planted victim file. A
     * refusal is what stops the delete, and it is observable on its own; building a fixture under
     * `filesDir` to watch it survive proved unreliable on Robolectric for Windows, where a file
     * written there reported `exists() == false` immediately afterwards.
     */
    @Test
    fun releaseRefusesAnyPathOutsideTheCacheWorkspace() {
        val storedReceipt = File(context.filesDir, "attachments").resolve("live-receipt.bin")
        assertFalse(
            "release accepted a path outside the workspace",
            EncryptedAttachmentStore.releaseCachedView(context, storedReceipt)
        )

        val traversal = EncryptedAttachmentStore.cacheWorkspace(context)
            .resolve("../../files/attachments/live-receipt.bin")
        assertFalse(
            "release accepted a traversal path that escapes the workspace",
            EncryptedAttachmentStore.releaseCachedView(context, traversal)
        )

        val siblingDirectory = EncryptedAttachmentStore.cacheWorkspace(context)
            .resolve("../other-cache/view-fixture")
        assertFalse(
            "release accepted a path in a neighbouring cache directory",
            EncryptedAttachmentStore.releaseCachedView(context, siblingDirectory)
        )
    }

    @Test
    fun startupClearRemovesEveryLeftoverPlaintext() = runBlocking {
        plantViewCopy("view-one")
        plantViewCopy("view-two")
        assertEquals(
            "planted fixtures missing before clear; workspace=${workspace.path}",
            listOf("view-one", "view-two"),
            workspace.listFiles().orEmpty().map { it.name }.sorted()
        )

        assertEquals(2, EncryptedAttachmentStore.clearCache(context))
        assertEquals(
            emptyList<String>(),
            workspace.listFiles().orEmpty().map { it.name }
        )
    }
}
