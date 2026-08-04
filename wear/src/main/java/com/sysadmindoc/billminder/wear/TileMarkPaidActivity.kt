package com.sysadmindoc.billminder.wear

import android.app.Activity
import android.os.Bundle
import com.google.android.gms.wearable.Wearable

class TileMarkPaidActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val billId = intent.getStringExtra(WearProtocol.EXTRA_BILL_ID)?.toLongOrNull()
        val snapshot = WearSnapshotStore.read(this)
        if (billId == null || billId != snapshot.billId) {
            finish()
            return
        }

        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    Wearable.getMessageClient(this).sendMessage(
                        node.id,
                        WearProtocol.MARK_PAID_PATH,
                        billId.toString().toByteArray(Charsets.UTF_8)
                    )
                }
                finish()
            }
            .addOnFailureListener { finish() }
    }
}
