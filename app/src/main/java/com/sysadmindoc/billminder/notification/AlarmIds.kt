package com.sysadmindoc.billminder.notification

import android.net.Uri

/**
 * Every pending intent and notification the app owns, named. Adding a slot here is what keeps two
 * different jobs for the same bill from sharing an identity.
 */
enum class AlarmSlot {
    PRIMARY_REMINDER,
    SECOND_REMINDER,
    OVERDUE_ALARM,
    SNOOZED_REMINDER,
    CASCADE_FOLLOW_UP,
    CASCADE_URGENT,
    OPEN_BILL,
    FULL_SCREEN,
    MARK_PAID,
    SNOOZE_ONE_HOUR,
    SNOOZE_TOMORROW,
    REMINDER_DISMISSED,
    OVERDUE_OPEN_BILL,
    OVERDUE_FULL_SCREEN,
    REMINDER_NOTIFICATION,
    OVERDUE_NOTIFICATION
}

/**
 * Identities for alarms and notifications.
 *
 * The old scheme added a constant to the bill id and truncated the result to an int, so bill 1's
 * second reminder and bill 50001's first reminder shared a request code, and any bill id past
 * `Int.MAX_VALUE` collapsed onto another. Identity now comes from a per-slot data URI, which
 * `PendingIntent` compares, and integer ids reserve their top bits for the slot.
 */
object AlarmIds {

    private const val SCHEME = "billminder"

    /** Unique per bill and slot. `PendingIntent` matching includes the data URI, extras do not. */
    fun uri(billId: Long, slot: AlarmSlot): Uri =
        Uri.parse("$SCHEME://${slot.name.lowercase()}/$billId")

    /**
     * A stable int for a bill and slot. The top byte is the slot, so two slots can never collide,
     * and the remaining bits fold the whole 64-bit id rather than truncating it.
     */
    fun code(billId: Long, slot: AlarmSlot): Int =
        (slot.ordinal shl 24) or (billId.hashCode() and 0x00FFFFFF)

    /** Notification id for a bill and slot. */
    fun notificationId(billId: Long, slot: AlarmSlot): Int = code(billId, slot)

    /** Every notification the app can post for one bill. */
    fun allNotificationIds(billId: Long): List<Int> = listOf(
        notificationId(billId, AlarmSlot.REMINDER_NOTIFICATION),
        notificationId(billId, AlarmSlot.OVERDUE_NOTIFICATION)
    )
}
