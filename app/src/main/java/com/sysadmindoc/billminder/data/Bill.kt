package com.sysadmindoc.billminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BillCategory(val label: String, val icon: String) {
    RENT("Rent/Mortgage", "home"),
    UTILITIES("Utilities", "bolt"),
    INSURANCE("Insurance", "shield"),
    PHONE("Phone/Internet", "wifi"),
    SUBSCRIPTION("Subscription", "repeat"),
    LOAN("Loan/Credit", "credit_card"),
    MEDICAL("Medical", "medical"),
    TRANSPORTATION("Transportation", "car"),
    GROCERIES("Groceries", "shopping_cart"),
    OTHER("Other", "receipt");

    companion object {
        fun fromLabel(label: String): BillCategory =
            entries.find { it.label == label } ?: OTHER
    }
}

enum class Recurrence(val label: String) {
    WEEKLY("Weekly"),
    BIWEEKLY("Bi-Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly"),
    ONE_TIME("One-Time");
}

enum class ReminderTiming(val label: String, val days: Int) {
    DAY_OF("Day of", 0),
    ONE_DAY("1 day before", 1),
    TWO_DAYS("2 days before", 2),
    THREE_DAYS("3 days before", 3),
    ONE_WEEK("1 week before", 7),
    TWO_WEEKS("2 weeks before", 14);
}

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val amount: Double,
    val dueDay: Int,
    val dueMonth: Int? = null,
    val dueYear: Int? = null,
    val category: BillCategory = BillCategory.OTHER,
    val recurrence: Recurrence = Recurrence.MONTHLY,
    val isAutoPay: Boolean = false,
    val notes: String = "",
    val reminderTiming: ReminderTiming = ReminderTiming.ONE_DAY,
    val secondReminderTiming: ReminderTiming? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val color: Long = 0xFF89B4FA
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val amount: Double,
    val paidAt: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val note: String = ""
)
