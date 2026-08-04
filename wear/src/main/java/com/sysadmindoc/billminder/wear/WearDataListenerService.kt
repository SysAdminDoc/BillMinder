package com.sysadmindoc.billminder.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.tiles.TileService

class WearDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != WearProtocol.SNAPSHOT_PATH) {
                continue
            }
            WearSnapshotStore.write(this, DataMapItem.fromDataItem(event.dataItem).dataMap)
            TileService.getUpdater(this).requestUpdate(BillMinderTileService::class.java)
        }
    }
}
