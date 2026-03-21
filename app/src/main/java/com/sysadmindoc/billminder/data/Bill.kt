package com.sysadmindoc.billminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BillCategory(val label: String) {
    RENT("Rent/Mortgage"),
    UTILITIES("Utilities"),
    INSURANCE("Insurance"),
    PHONE("Phone/Internet"),
    SUBSCRIPTION("Subscription"),
    LOAN("Loan/Credit"),
    MEDICAL("Medical"),
    TRANSPORTATION("Transportation"),
    GROCERIES("Groceries"),
    EDUCATION("Education"),
    ENTERTAINMENT("Entertainment"),
    CHILDCARE("Childcare"),
    OTHER("Other");

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
    TWO_WEEKS("2 weeks before", 14),
    ONE_MONTH("1 month before", 30);
}

enum class SortMode(val label: String) {
    DUE_DATE("Due Date"),
    AMOUNT_ASC("Amount (Low-High)"),
    AMOUNT_DESC("Amount (High-Low)"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    CATEGORY("Category");
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
    val color: Long = 0xFF89B4FA,
    val paymentUrl: String = "",
    val tags: String = ""
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val amount: Double,
    val paidAt: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val note: String = "",
    val confirmationNumber: String = ""
)
