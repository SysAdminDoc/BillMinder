package com.sysadmindoc.billminder.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bill_payees",
    foreignKeys = [
        ForeignKey(
            entity = Bill::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("billId")]
)
data class BillPayee(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val name: String,
    val sharePercent: Double
)

data class PayeeDraft(
    val name: String,
    val sharePercent: Double
)

object PayeeMath {
    fun totalPercent(payees: List<PayeeDraft>): Double = payees.sumOf { it.sharePercent }

    fun shareAmount(total: Double, sharePercent: Double): Double =
        total * sharePercent / 100.0

    fun isBalanced(payees: List<PayeeDraft>): Boolean =
        payees.isNotEmpty() && kotlin.math.abs(totalPercent(payees) - 100.0) < 0.001
}
