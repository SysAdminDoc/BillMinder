package com.sysadmindoc.billminder.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.ActionBuilders.stringExtra
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import java.text.DateFormat
import java.util.Date

class BillMinderTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        Futures.immediateFuture(
            Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
                .setTileTimeline(
                    Timeline.fromLayoutElement(
                        tileLayout(this, requestParams.deviceConfiguration, WearSnapshotStore.read(this))
                    )
                )
                .build()
        )

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) =
        Futures.immediateFuture(Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun tileLayout(
        context: Context,
        deviceConfiguration: DeviceParameters,
        snapshot: WearBillSnapshot
    ) = materialScope(
        context = context,
        deviceConfiguration = deviceConfiguration,
        allowDynamicTheme = false
    ) {
        val hasBill = snapshot.billId != WearProtocol.NO_BILL
        val action = clickable(
            id = "mark_paid",
            action = launchAction(
                ComponentName(context, TileMarkPaidActivity::class.java),
                mapOf(WearProtocol.EXTRA_BILL_ID to stringExtra(snapshot.billId.toString()))
            )
        )
        primaryLayout(
            titleSlot = { text("BillMinder".layoutString) },
            mainSlot = {
                val mainText = if (hasBill) {
                    val dueLabel = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(snapshot.dueDate))
                    "${snapshot.name} · ${snapshot.amount.formatCurrency(snapshot.currency)} · $dueLabel"
                } else {
                    "All bills paid"
                }
                text(mainText.layoutString)
            },
            bottomSlot = if (hasBill) {
                {
                    textEdgeButton(
                        onClick = action,
                        modifier = LayoutModifier.contentDescription("Mark bill paid")
                    ) { text("Mark paid".layoutString) }
                }
            } else {
                null
            }
        )
    }

    private fun Double.formatCurrency(currency: String): String =
        "%.2f %s".format(java.util.Locale.getDefault(), this, currency)

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val FRESHNESS_MILLIS = 15 * 60 * 1000L
    }
}
