package com.sysadmindoc.billminder

import android.app.Application
import com.sysadmindoc.billminder.data.BackupManager
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.notification.NotificationHelper
import com.sysadmindoc.billminder.security.EncryptedAttachmentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BillMinderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        sweepReceiptFiles()
    }

    /**
     * Removes decrypted copies left in the cache and encrypted receipts no payment refers to any
     * more, so a crash mid-view or a delete that never got its undo cannot leave bytes on disk.
     */
    private fun sweepReceiptFiles() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                BackupManager.clearTemporaryFiles(this@BillMinderApp)
                EncryptedAttachmentStore.clearCache(this@BillMinderApp)
                val referenced = BillDatabase.getDatabase(this@BillMinderApp)
                    .billDao()
                    .getReferencedAttachments()
                    .toSet()
                EncryptedAttachmentStore.purgeOrphans(this@BillMinderApp, referenced)
            }
        }
    }
}
