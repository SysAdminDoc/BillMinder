package com.sysadmindoc.billminder.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedAttachmentStoreTest {
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
}
